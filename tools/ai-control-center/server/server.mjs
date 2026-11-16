import { createServer as createHttpServer } from "node:http";
import { createHash, randomBytes, timingSafeEqual } from "node:crypto";
import { readFile } from "node:fs/promises";
import { extname, resolve, sep } from "node:path";
import { fileURLToPath } from "node:url";

const MAX_BODY_BYTES = 64 * 1024;
const MAX_MESSAGES = 20;
const MAX_MESSAGE_CHARS = 12_000;
const MAX_OUTPUT_TOKENS = 1_024;
const RATE_LIMIT_WINDOW_MS = 60_000;
const RATE_LIMIT_REQUESTS = 10;
const LOGIN_WINDOW_MS = 15 * 60_000;
const LOGIN_ATTEMPTS = 5;
const SESSION_TTL_MS = 12 * 60 * 60_000;
const PROVIDERS = {
  openrouter: { label: "OpenRouter", envKey: "OPENROUTER_API_KEY", model: "openrouter/free", endpoint: "https://openrouter.ai/api/v1/chat/completions", kind: "openai" },
  groq: { label: "Groq", envKey: "GROQ_API_KEY", model: "openai/gpt-oss-20b", endpoint: "https://api.groq.com/openai/v1/chat/completions", kind: "openai" },
  gemini: { label: "Gemini", envKey: "GEMINI_API_KEY", model: "gemini-3.8-flash", endpoint: "https://generativelanguage.googleapis.com/v1beta/models/", kind: "gemini" }
};
const ACTIVE_PROVIDERS = { openrouter: PROVIDERS.openrouter, groq: PROVIDERS.groq };
const DEFAULT_PROVIDER = "openrouter";
const STATIC_ROOT = resolve(fileURLToPath(new URL("../", import.meta.url)));

function json(response, status, payload, extraHeaders = {}) {
  response.writeHead(status, {
    "content-type": "application/json; charset=utf-8",
    "cache-control": "no-store",
    "x-content-type-options": "nosniff",
    "referrer-policy": "no-referrer",
    ...extraHeaders
  });
  response.end(JSON.stringify(payload));
}

function safeEqual(left, right) {
  if (typeof left !== "string" || typeof right !== "string") return false;
  const a = Buffer.from(left);
  const b = Buffer.from(right);
  return a.length === b.length && timingSafeEqual(a, b);
}

function readJson(request) {
  return new Promise((resolveBody, reject) => {
    let size = 0;
    let tooLarge = false;
    const chunks = [];
    request.on("data", (chunk) => {
      size += chunk.length;
      if (size > MAX_BODY_BYTES) {
        tooLarge = true;
        chunks.length = 0;
        return;
      }
      if (!tooLarge) chunks.push(chunk);
    });
    request.on("end", () => {
      if (tooLarge) {
        reject(Object.assign(new Error("Request body too large"), { status: 413 }));
        return;
      }
      try {
        resolveBody(JSON.parse(Buffer.concat(chunks).toString("utf8")));
      } catch {
        reject(Object.assign(new Error("Invalid JSON"), { status: 400 }));
      }
    });
    request.on("error", reject);
  });
}

function validateMessages(messages) {
  if (!Array.isArray(messages) || messages.length < 1 || messages.length > MAX_MESSAGES) return false;
  return messages.every((message) =>
    message &&
    (message.role === "user" || message.role === "assistant") &&
    typeof message.content === "string" &&
    message.content.trim().length > 0 &&
    message.content.length <= MAX_MESSAGE_CHARS
  );
}

function cookieValue(header, name) {
  const prefix = `${name}=`;
  for (const part of (header ?? "").split(";")) {
    const trimmed = part.trim();
    if (trimmed.startsWith(prefix)) return trimmed.slice(prefix.length);
  }
  return "";
}

function sessionCookie(value, secure, maxAge) {
  return `nexora_session=${value}; HttpOnly; SameSite=Strict; Path=/; Max-Age=${maxAge}${secure ? "; Secure" : ""}`;
}

function isSameOrigin(request) {
  const origin = request.headers.origin;
  if (!origin) return true;
  const host = request.headers.host;
  if (!host) return false;
  try {
    return new URL(origin).host === host;
  } catch {
    return false;
  }
}

function contentType(path) {
  return ({
    ".html": "text/html; charset=utf-8",
    ".css": "text/css; charset=utf-8",
    ".js": "text/javascript; charset=utf-8",
    ".svg": "image/svg+xml"
  })[extname(path)] ?? "application/octet-stream";
}

async function serveStatic(pathname, response) {
  let relative;
  try {
    relative = pathname === "/" ? "index.html" : decodeURIComponent(pathname).replace(/^\/+/, "");
  } catch {
    return json(response, 400, { error: "Invalid path" });
  }
  if (!/^[a-zA-Z0-9_./-]+$/.test(relative) || relative.includes("..")) {
    return json(response, 404, { error: "Not found" });
  }
  const target = resolve(STATIC_ROOT, relative);
  if (target !== STATIC_ROOT && !target.startsWith(STATIC_ROOT + sep)) {
    return json(response, 404, { error: "Not found" });
  }
  if (![".html", ".css", ".js", ".svg"].includes(extname(target))) {
    return json(response, 404, { error: "Not found" });
  }
  try {
    const content = await readFile(target);
    response.writeHead(200, {
      "content-type": contentType(target),
      "cache-control": extname(target) === ".html" ? "no-store" : "public, max-age=300",
      "x-content-type-options": "nosniff",
      "referrer-policy": "no-referrer",
      "content-security-policy": "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; connect-src 'self'; object-src 'none'; base-uri 'self'; frame-ancestors 'none'"
    });
    response.end(content);
  } catch {
    json(response, 404, { error: "Not found" });
  }
}

export function createControlCenterServer({
  env = process.env,
  fetchImpl = globalThis.fetch,
  redisFetchImpl = globalThis.fetch,
  now = Date.now,
  logger = console
} = {}) {
  const sessions = new Map();
  const chatRequestsByAddress = new Map();
  const loginAttemptsByAddress = new Map();
  const secureCookies = env.NODE_ENV === "production";
  const redisUrl = env.UPSTASH_REDIS_REST_URL?.replace(/\/+$/, "");
  const redisToken = env.UPSTASH_REDIS_REST_TOKEN;
  const redisConfigured = Boolean(redisUrl && redisToken);
  if (Boolean(redisUrl) !== Boolean(redisToken)) throw new Error("Both Upstash Redis REST URL and token must be configured together");
  if (redisConfigured && !redisUrl.startsWith("https://")) throw new Error("Upstash Redis REST URL must use HTTPS");
  if (env.NODE_ENV === "production" && !redisConfigured) throw new Error("Production requires persistent Upstash Redis storage");
  async function redisCommand(command) {
    const response = await redisFetchImpl(redisUrl, { method: "POST", headers: { authorization: "Bearer " + redisToken, "content-type": "application/json" }, body: JSON.stringify(command), signal: AbortSignal.timeout(5000) });
    const payload = await response.json().catch(() => null);
    if (!response.ok || payload?.error) throw new Error("Persistent storage request failed");
    return payload?.result;
  }
  async function incrementRateLimit(key, store, windowMs) {
    if (redisConfigured) {
      const script = "local count = redis.call('INCR', KEYS[1]); if count == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[1]); end; return count";
      return Number(await redisCommand(["EVAL", script, "1", key, String(windowMs)]));
    }
    const currentTime = now();
    const previous = store.get(key);
    const active = previous && currentTime - previous.startedAt < windowMs ? previous : { startedAt: currentTime, count: 0 };
    active.count += 1;
    store.set(key, active);
    return active.count;
  }

  async function currentSession(request) {
    const raw = cookieValue(request.headers.cookie, "nexora_session");
    if (!raw) return null;
    const key = createHash("sha256").update(raw).digest("hex");
    let expiresAt;
    if (redisConfigured) {
      const stored = await redisCommand(["GET", "nexora:session:" + key]);
      expiresAt = stored === null ? null : Number(stored);
    } else { expiresAt = sessions.get(key); }
    if (!expiresAt || expiresAt <= now()) {
      if (redisConfigured) await redisCommand(["DEL", "nexora:session:" + key]);
      else sessions.delete(key);
      return null;
    }
    return key;
  }
  return createHttpServer(async (request, response) => {
    try {
    let url;
    try {
      url = new URL(request.url ?? "/", "http://localhost");
    } catch {
      return json(response, 400, { error: "Invalid URL" });
    }

    if (request.method === "GET" && url.pathname === "/api/health") {
      return json(response, 200, { status: "ok", service: "nexora-ai-control-center" });
    }

    if (request.method === "POST" && url.pathname.startsWith("/api/") && !isSameOrigin(request)) {
      return json(response, 403, { error: "Cross-origin request rejected" });
    }

    if (request.method === "POST" && url.pathname === "/api/login") {
      const password = env.NEXORA_ADMIN_PASSWORD;
      if (typeof password !== "string" || password.length < 16) {
        return json(response, 503, { error: "Admin password is not configured securely" });
      }
      const address = request.socket.remoteAddress ?? "unknown";
      let body;
      try {
        body = await readJson(request);
      } catch {
        return json(response, 400, { error: "Invalid request" });
      }
      const attempts = await incrementRateLimit("nexora:login:" + address, loginAttemptsByAddress, LOGIN_WINDOW_MS);
      if (attempts > LOGIN_ATTEMPTS) return json(response, 429, { error: "Too many login attempts. Try again later." });
      if (!safeEqual(body?.password, password)) {
        return json(response, 401, { error: "Incorrect password" });
      }
      if (redisConfigured) await redisCommand(["DEL", "nexora:login:" + address]);
      else loginAttemptsByAddress.delete(address);
      const session = randomBytes(32).toString("base64url");
      const key = createHash("sha256").update(session).digest("hex");
      const expiresAt = now() + SESSION_TTL_MS;
      if (redisConfigured) await redisCommand(["SET", "nexora:session:" + key, String(expiresAt), "EX", String(Math.ceil(SESSION_TTL_MS / 1000))]);
      else sessions.set(key, expiresAt);
      return json(response, 200, { authenticated: true }, {
        "set-cookie": sessionCookie(session, secureCookies, Math.floor(SESSION_TTL_MS / 1000))
      });
    }

    if (request.method === "POST" && url.pathname === "/api/logout") {
      const key = await currentSession(request);
      if (key) {
        if (redisConfigured) await redisCommand(["DEL", "nexora:session:" + key]);
        else sessions.delete(key);
      }
      return json(response, 200, { authenticated: false }, {
        "set-cookie": sessionCookie("", secureCookies, 0)
      });
    }

    if (request.method === "GET" && url.pathname === "/api/session") {
      return json(response, 200, { authenticated: Boolean(await currentSession(request)) });
    }

    if (url.pathname === "/mcp") {
      if (request.method !== "POST") {
        response.setHeader("allow", "POST");
        return json(response, 405, { error: "Method not allowed" });
      }
      const expectedToken = env.NEXORA_MCP_TOKEN;
      const authorization = request.headers.authorization ?? "";
      const suppliedToken = authorization.startsWith("Bearer ") ? authorization.slice(7) : "";
      if (typeof expectedToken !== "string" || expectedToken.length < 32 || !safeEqual(suppliedToken, expectedToken)) {
        response.setHeader("www-authenticate", "Bearer");
        return json(response, 401, { error: "MCP authentication required" });
      }
      // MCP uses an explicit Bearer credential, not the browser session cookie; no CSRF origin check is needed here.
      let rpc;
      try { rpc = await readJson(request); }
      catch { return json(response, 400, { error: "Invalid JSON-RPC request" }); }
      if (rpc?.jsonrpc !== "2.0" || typeof rpc.method !== "string") {
        return json(response, 400, { jsonrpc: "2.0", id: rpc?.id ?? null, error: { code: -32600, message: "Invalid Request" } });
      }
      if (rpc.method === "notifications/initialized" || rpc.method.startsWith("notifications/")) {
        response.writeHead(202, { "cache-control": "no-store" });
        return response.end();
      }
      const rpcId = rpc.id ?? null;
      if (rpc.method === "initialize") {
        return json(response, 200, {
          jsonrpc: "2.0", id: rpcId,
          result: {
            protocolVersion: "2025-03-26",
            capabilities: { tools: {} },
            serverInfo: { name: "nexora-ai-control-center", version: "1.0.0" }
          }
        });
      }
      if (rpc.method === "ping") return json(response, 200, { jsonrpc: "2.0", id: rpcId, result: {} });
      if (rpc.method === "tools/list") {
        return json(response, 200, {
          jsonrpc: "2.0", id: rpcId,
          result: { tools: [{
            name: "nexora_chat",
            description: "Send a prompt to an authorized NEXORA AI provider and return its response. This tool only chats; it cannot change deployments, secrets, or repository files.",
            inputSchema: {
              type: "object", additionalProperties: false,
              properties: {
                provider: { type: "string", enum: ["openrouter", "groq"], description: "AI provider to receive the prompt." },
                prompt: { type: "string", minLength: 1, maxLength: 12000, description: "Instruction or question to send to the selected AI." }
              },
              required: ["prompt"]
            }
          }] }
        });
      }
      if (rpc.method === "tools/call") {
        const toolName = rpc.params?.name;
        const args = rpc.params?.arguments ?? {};
        if (toolName !== "nexora_chat") {
          return json(response, 200, { jsonrpc: "2.0", id: rpcId, result: { isError: true, content: [{ type: "text", text: "Unknown NEXORA tool." }] } });
        }
        const providerId = args.provider ?? DEFAULT_PROVIDER;
        const prompt = args.prompt;
        if (!["openrouter", "groq"].includes(providerId) || typeof prompt !== "string" || !prompt.trim() || prompt.length > MAX_MESSAGE_CHARS) {
          return json(response, 200, { jsonrpc: "2.0", id: rpcId, result: { isError: true, content: [{ type: "text", text: "Invalid provider or prompt. Use OpenRouter/Groq and a prompt up to 12,000 characters." }] } });
        }
        const provider = ACTIVE_PROVIDERS[providerId];
        const apiKey = env[provider.envKey];
        if (!apiKey) {
          return json(response, 200, { jsonrpc: "2.0", id: rpcId, result: { isError: true, content: [{ type: "text", text: provider.label + " is not configured." }] } });
        }
        const address = request.socket.remoteAddress ?? "unknown";
        const requests = await incrementRateLimit("nexora:chat:" + address, chatRequestsByAddress, RATE_LIMIT_WINDOW_MS);
        if (requests > RATE_LIMIT_REQUESTS) {
          return json(response, 200, { jsonrpc: "2.0", id: rpcId, result: { isError: true, content: [{ type: "text", text: "NEXORA rate limit reached; try again in a minute." }] } });
        }
        try {
          const upstream = await fetchImpl(provider.endpoint, {
            method: "POST",
            headers: { authorization: "Bearer " + apiKey, "content-type": "application/json", ...(providerId === "openrouter" ? { "x-title": "NEXORA AI Control Center" } : {}) },
            body: JSON.stringify({ model: provider.model, messages: [{ role: "user", content: prompt }], max_tokens: MAX_OUTPUT_TOKENS, stream: false }),
            signal: AbortSignal.timeout(30_000)
          });
          const payload = await upstream.json().catch(() => null);
          if (!upstream.ok) {
            logger.error?.("[nexora-mcp] provider returned an HTTP error", { provider: providerId, model: provider.model, status: upstream.status });
            return json(response, 200, { jsonrpc: "2.0", id: rpcId, result: { isError: true, content: [{ type: "text", text: provider.label + " could not complete the request (HTTP " + upstream.status + ")." }] } });
          }
          const answer = payload?.choices?.[0]?.message?.content;
          if (typeof answer !== "string" || !answer.trim()) {
            return json(response, 200, { jsonrpc: "2.0", id: rpcId, result: { isError: true, content: [{ type: "text", text: "Unexpected response from " + provider.label + "." }] } });
          }
          return json(response, 200, { jsonrpc: "2.0", id: rpcId, result: { content: [{ type: "text", text: JSON.stringify({ provider: providerId, model: provider.model, answer, usage: payload.usage ?? null }) }] } });
        } catch (error) {
          logger.error?.("[nexora-mcp] provider request failed", {
            provider: providerId, model: provider.model,
            errorName: typeof error?.name === "string" ? error.name : "Error",
            errorCode: typeof error?.code === "string" ? error.code : null,
            causeCode: typeof error?.cause?.code === "string" ? error.cause.code : null
          });
          return json(response, 200, { jsonrpc: "2.0", id: rpcId, result: { isError: true, content: [{ type: "text", text: "Unable to reach " + provider.label + "." }] } });
        }
      }
      return json(response, 200, { jsonrpc: "2.0", id: rpcId, error: { code: -32601, message: "Method not found" } });
    }

    if (url.pathname.startsWith("/api/")) {
      if (!["GET", "POST"].includes(request.method ?? "")) {
        response.setHeader("allow", "GET, POST");
        return json(response, 405, { error: "Method not allowed" });
      }
      if (!await currentSession(request)) return json(response, 401, { error: "Authentication required" });

      if (request.method === "GET" && url.pathname === "/api/status") {
        const providers = Object.fromEntries(Object.entries(ACTIVE_PROVIDERS).map(([id, provider]) => [
          id, { name: provider.label, configured: Boolean(env[provider.envKey]), model: provider.model }
        ]));
        return json(response, 200, {
          provider: DEFAULT_PROVIDER,
          configured: providers[DEFAULT_PROVIDER].configured,
          providers,
          connectedCount: Object.values(providers).filter((provider) => provider.configured).length,
          providerCount: Object.keys(ACTIVE_PROVIDERS).length,
          modelPolicy: "fixed-per-provider",
          sessionStorage: redisConfigured ? "persistent" : "in-memory"
        });
      }

      if (request.method === "POST" && url.pathname === "/api/chat") {
        const address = request.socket.remoteAddress ?? "unknown";
        const requests = await incrementRateLimit("nexora:chat:" + address, chatRequestsByAddress, RATE_LIMIT_WINDOW_MS);
        if (requests > RATE_LIMIT_REQUESTS) return json(response, 429, { error: "Rate limit reached; try again in a minute" });
        let body;
        try { body = await readJson(request); }
        catch (error) { return json(response, error.status ?? 400, { error: "Invalid request body" }); }
        if (!validateMessages(body?.messages)) return json(response, 400, { error: "messages must contain 1–20 user/assistant text messages, each up to 12,000 characters" });
        const providerId = typeof body.provider === "string" ? body.provider : DEFAULT_PROVIDER;
        const provider = ACTIVE_PROVIDERS[providerId];
        if (!provider) return json(response, 400, { error: "Unsupported AI provider" });
        const apiKey = env[provider.envKey];
        if (!apiKey) return json(response, 503, { error: provider.label + " is not configured on the server" });
        try {
          let upstream;
          if (provider.kind === "gemini") {
            const contents = body.messages.map((message) => ({ role: message.role === "assistant" ? "model" : "user", parts: [{ text: message.content }] }));
            const requestUrl = provider.endpoint + provider.model + ":generateContent";
            const requestBody = JSON.stringify({ contents, generationConfig: { maxOutputTokens: MAX_OUTPUT_TOKENS } });
            // Gemini can return 503 or take unusually long under temporary capacity pressure.
            // Create a fresh timeout signal per attempt and retry bounded transient failures.
            for (let attempt = 0; attempt < 3; attempt += 1) {
              try {
                upstream = await fetchImpl(requestUrl, {
                  method: "POST",
                  headers: { "content-type": "application/json", "x-goog-api-key": apiKey },
                  body: requestBody,
                  signal: AbortSignal.timeout(60_000)
                });
                if (upstream.status !== 503 || attempt === 2) break;
                logger.warn?.("[nexora-ai] Gemini temporarily unavailable; retrying", { attempt: attempt + 1, status: 503 });
              } catch (error) {
                const timedOut = error?.name === "TimeoutError" || error?.name === "AbortError" || error?.code === "ETIMEDOUT";
                if (!timedOut || attempt === 2) throw error;
                logger.warn?.("[nexora-ai] Gemini request timed out; retrying", { attempt: attempt + 1 });
              }
              await new Promise((resolveDelay) => setTimeout(resolveDelay, 1_000 * (2 ** attempt)));
            }
          } else {
            upstream = await fetchImpl(provider.endpoint, {
              method: "POST",
              headers: { authorization: "Bearer " + apiKey, "content-type": "application/json", ...(providerId === "openrouter" ? { "x-title": "NEXORA AI Control Center" } : {}) },
              body: JSON.stringify({ model: provider.model, messages: body.messages, max_tokens: MAX_OUTPUT_TOKENS, stream: false }),
              signal: AbortSignal.timeout(30_000)
            });
          }
          const result = await upstream.json().catch(() => null);
          if (!upstream.ok) {
            logger.error?.("[nexora-ai] provider returned an HTTP error", {
              provider: providerId,
              model: provider.model,
              status: upstream.status
            });
            return json(response, upstream.status >= 500 ? 502 : upstream.status, { error: "The AI provider could not complete the request" });
          }
          const answer = provider.kind === "gemini"
            ? result?.candidates?.[0]?.content?.parts?.map((part) => part.text ?? "").join("")
            : result?.choices?.[0]?.message?.content;
          if (typeof answer !== "string" || !answer.trim()) return json(response, 502, { error: "Unexpected provider response" });
          return json(response, 200, { provider: providerId, model: result.model ?? provider.model, answer, usage: result.usage ?? result.usageMetadata ?? null });
        } catch (error) {
          // Log only safe transport metadata. Never log request headers, API keys, or prompt contents.
          logger.error?.("[nexora-ai] provider request failed", {
            provider: providerId,
            model: provider.model,
            errorName: typeof error?.name === "string" ? error.name : "Error",
            errorCode: typeof error?.code === "string" ? error.code : null,
            causeCode: typeof error?.cause?.code === "string" ? error.cause.code : null
          });
          return json(response, 502, { error: "Unable to reach the AI provider" });
        }
      }
      return json(response, 404, { error: "Not found" });
    }

    if (request.method === "GET") return serveStatic(url.pathname, response);
    response.setHeader("allow", "GET");
    return json(response, 405, { error: "Method not allowed" });
    } catch {
      if (!response.headersSent) return json(response, 503, { error: "A server dependency is temporarily unavailable" });
      response.destroy();
    }
  });
}

if (import.meta.url === `file://${process.argv[1]}`) {
  const port = Number.parseInt(process.env.PORT ?? "8787", 10);
  const server = createControlCenterServer();
  server.listen(port, process.env.HOST ?? "127.0.0.1", () => {
    process.stdout.write(`NEXORA control center listening on port ${port}\n`);
  });
}

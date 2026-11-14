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
const FREE_MODEL = "openrouter/free";
const OPENROUTER_URL = "https://openrouter.ai/api/v1/chat/completions";
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
  now = Date.now
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

    if (url.pathname.startsWith("/api/")) {
      if (!["GET", "POST"].includes(request.method ?? "")) {
        response.setHeader("allow", "GET, POST");
        return json(response, 405, { error: "Method not allowed" });
      }
      if (!await currentSession(request)) return json(response, 401, { error: "Authentication required" });

      if (request.method === "GET" && url.pathname === "/api/status") {
        return json(response, 200, {
          provider: "OpenRouter",
          configured: Boolean(env.OPENROUTER_API_KEY),
          modelPolicy: "free-only",
          model: FREE_MODEL,
          paidModelsEnabled: false,
          sessionStorage: redisConfigured ? "persistent" : "in-memory"
        });
      }

      if (request.method === "POST" && url.pathname === "/api/chat") {
        if (!env.OPENROUTER_API_KEY) {
          return json(response, 503, { error: "OpenRouter is not configured on the server" });
        }
        const address = request.socket.remoteAddress ?? "unknown";
        const requests = await incrementRateLimit("nexora:chat:" + address, chatRequestsByAddress, RATE_LIMIT_WINDOW_MS);
        if (requests > RATE_LIMIT_REQUESTS) {
          return json(response, 429, { error: "Rate limit reached; try again in a minute" });
        }

        let body;
        try {
          body = await readJson(request);
        } catch (error) {
          return json(response, error.status ?? 400, { error: "Invalid request body" });
        }
        if (!validateMessages(body?.messages)) {
          return json(response, 400, {
            error: "messages must contain 1–20 user/assistant text messages, each up to 12,000 characters"
          });
        }

        try {
          const upstream = await fetchImpl(OPENROUTER_URL, {
            method: "POST",
            headers: {
              authorization: `Bearer ${env.OPENROUTER_API_KEY}`,
              "content-type": "application/json",
              "x-title": "NEXORA AI Control Center"
            },
            body: JSON.stringify({
              model: FREE_MODEL,
              messages: body.messages,
              max_tokens: MAX_OUTPUT_TOKENS,
              stream: false
            }),
            signal: AbortSignal.timeout(30_000)
          });
          const result = await upstream.json().catch(() => null);
          if (!upstream.ok) {
            return json(response, upstream.status >= 500 ? 502 : upstream.status, {
              error: "The AI provider could not complete the request"
            });
          }
          const answer = result?.choices?.[0]?.message?.content;
          if (typeof answer !== "string") return json(response, 502, { error: "Unexpected provider response" });
          return json(response, 200, {
            model: result.model ?? FREE_MODEL,
            answer,
            usage: result.usage ?? null
          });
        } catch {
          return json(response, 502, { error: "Unable to reach the AI provider" });
        }
      }
      return json(response, 404, { error: "Not found" });
    }

    if (request.method === "GET") return serveStatic(url.pathname, response);
    response.setHeader("allow", "GET");
    return json(response, 405, { error: "Method not allowed" });
  });
}

if (import.meta.url === `file://${process.argv[1]}`) {
  const port = Number.parseInt(process.env.PORT ?? "8787", 10);
  const server = createControlCenterServer();
  server.listen(port, process.env.HOST ?? "127.0.0.1", () => {
    process.stdout.write(`NEXORA control center listening on port ${port}\n`);
  });
}

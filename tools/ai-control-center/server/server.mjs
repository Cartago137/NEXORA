import { createServer as createHttpServer } from "node:http";
import { timingSafeEqual } from "node:crypto";

const MAX_BODY_BYTES = 64 * 1024;
const MAX_MESSAGES = 20;
const MAX_MESSAGE_CHARS = 12_000;
const MAX_OUTPUT_TOKENS = 1_024;
const RATE_LIMIT_WINDOW_MS = 60_000;
const RATE_LIMIT_REQUESTS = 10;
const FREE_MODEL = "openrouter/free";
const OPENROUTER_URL = "https://openrouter.ai/api/v1/chat/completions";

function json(response, status, payload) {
  response.writeHead(status, {
    "content-type": "application/json; charset=utf-8",
    "cache-control": "no-store",
    "x-content-type-options": "nosniff"
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
  return new Promise((resolve, reject) => {
    let size = 0;
    const chunks = [];
    request.on("data", (chunk) => {
      size += chunk.length;
      if (size > MAX_BODY_BYTES) {
        reject(Object.assign(new Error("Request body too large"), { status: 413 }));
        request.destroy();
        return;
      }
      chunks.push(chunk);
    });
    request.on("end", () => {
      try {
        resolve(JSON.parse(Buffer.concat(chunks).toString("utf8")));
      } catch {
        reject(Object.assign(new Error("Invalid JSON"), { status: 400 }));
      }
    });
    request.on("error", reject);
  });
}

function validateMessages(messages) {
  if (!Array.isArray(messages) || messages.length < 1 || messages.length > MAX_MESSAGES) {
    return false;
  }
  return messages.every((message) =>
    message &&
    (message.role === "user" || message.role === "assistant") &&
    typeof message.content === "string" &&
    message.content.trim().length > 0 &&
    message.content.length <= MAX_MESSAGE_CHARS
  );
}

export function createControlCenterServer({
  env = process.env,
  fetchImpl = globalThis.fetch,
  now = Date.now
} = {}) {
  const requestsByAddress = new Map();

  return createHttpServer(async (request, response) => {
    const url = new URL(request.url ?? "/", "http://localhost");

    if (request.method === "GET" && url.pathname === "/api/health") {
      return json(response, 200, { status: "ok", service: "nexora-ai-control-center" });
    }

    if (!["GET", "POST"].includes(request.method ?? "")) {
      response.setHeader("allow", "GET, POST");
      return json(response, 405, { error: "Method not allowed" });
    }

    const configuredToken = env.NEXORA_CONTROL_TOKEN;
    if (typeof configuredToken !== "string" || configuredToken.length < 32) {
      return json(response, 503, { error: "Control center authentication is not configured" });
    }

    const authorization = request.headers.authorization ?? "";
    const suppliedToken = authorization.startsWith("Bearer ")
      ? authorization.slice("Bearer ".length)
      : "";
    if (!safeEqual(suppliedToken, configuredToken)) {
      return json(response, 401, { error: "Unauthorized" });
    }

    if (request.method === "GET" && url.pathname === "/api/status") {
      return json(response, 200, {
        provider: "OpenRouter",
        configured: Boolean(env.OPENROUTER_API_KEY),
        modelPolicy: "free-only",
        model: FREE_MODEL,
        paidModelsEnabled: false
      });
    }

    if (request.method === "POST" && url.pathname === "/api/chat") {
      if (!env.OPENROUTER_API_KEY) {
        return json(response, 503, { error: "OpenRouter is not configured on the server" });
      }

      const address = request.socket.remoteAddress ?? "unknown";
      const currentTime = now();
      const previous = requestsByAddress.get(address);
      const active = previous && currentTime - previous.startedAt < RATE_LIMIT_WINDOW_MS
        ? previous
        : { startedAt: currentTime, count: 0 };
      active.count += 1;
      requestsByAddress.set(address, active);
      if (active.count > RATE_LIMIT_REQUESTS) {
        return json(response, 429, { error: "Rate limit reached; try again in a minute" });
      }

      let body;
      try {
        body = await readJson(request);
      } catch (error) {
        return json(response, error.status ?? 400, { error: error.status === 413 ? "Request too large" : "Invalid JSON" });
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
        if (typeof answer !== "string") {
          return json(response, 502, { error: "The AI provider returned an unexpected response" });
        }

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
  });
}

if (import.meta.url === `file://${process.argv[1]}`) {
  const port = Number.parseInt(process.env.PORT ?? "8787", 10);
  const server = createControlCenterServer();
  server.listen(port, process.env.HOST ?? "127.0.0.1", () => {
    process.stdout.write(`NEXORA control center API listening on 127.0.0.1:${port}\n`);
  });
}

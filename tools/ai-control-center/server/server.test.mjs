import test from "node:test";
import assert from "node:assert/strict";
import { createControlCenterServer } from "./server.mjs";

const password = "test-admin-password-long-enough";
const apiKey = "test-openrouter-key";

async function withServer(options, run) {
  const server = createControlCenterServer(options);
  await new Promise((resolve) => server.listen(0, "127.0.0.1", resolve));
  const address = server.address();
  try {
    await run(`http://127.0.0.1:${address.port}`);
  } finally {
    await new Promise((resolve) => server.close(resolve));
  }
}

async function login(base) {
  const response = await fetch(`${base}/api/login`, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ password })
  });
  assert.equal(response.status, 200);
  return response.headers.get("set-cookie").split(";")[0];
}

test("health endpoint exposes no secrets", async () => {
  await withServer({ env: { NEXORA_ADMIN_PASSWORD: password, OPENROUTER_API_KEY: apiKey } }, async (base) => {
    const response = await fetch(`${base}/api/health`);
    assert.equal(response.status, 200);
    const body = await response.json();
    assert.equal(body.status, "ok");
    assert.equal(JSON.stringify(body).includes(apiKey), false);
  });
});

test("dashboard assets are served from the same origin", async () => {
  await withServer({ env: { NEXORA_ADMIN_PASSWORD: password } }, async (base) => {
    const response = await fetch(base);
    assert.equal(response.status, 200);
    assert.match(response.headers.get("content-type"), /text\/html/);
    assert.match(await response.text(), /Acesse sua central/);
  });
});

test("session endpoint starts unauthenticated and protected status requires login", async () => {
  await withServer({ env: { NEXORA_ADMIN_PASSWORD: password } }, async (base) => {
    const session = await fetch(`${base}/api/session`);
    assert.deepEqual(await session.json(), { authenticated: false });
    const status = await fetch(`${base}/api/status`);
    assert.equal(status.status, 401);
  });
});

test("valid login creates HttpOnly session and logout revokes it", async () => {
  await withServer({ env: { NEXORA_ADMIN_PASSWORD: password } }, async (base) => {
    const cookie = await login(base);
    assert.match(cookie, /^nexora_session=/);
    const session = await fetch(`${base}/api/session`, { headers: { cookie } });
    assert.deepEqual(await session.json(), { authenticated: true });
    const logout = await fetch(`${base}/api/logout`, { method: "POST", headers: { cookie } });
    assert.equal(logout.status, 200);
    const after = await fetch(`${base}/api/session`, { headers: { cookie } });
    assert.deepEqual(await after.json(), { authenticated: false });
  });
});

test("incorrect login password is rejected", async () => {
  await withServer({ env: { NEXORA_ADMIN_PASSWORD: password } }, async (base) => {
    const response = await fetch(`${base}/api/login`, {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({ password: "wrong-password-value" })
    });
    assert.equal(response.status, 401);
  });
});

test("chat endpoint only forwards fixed free router and bounded output", async () => {
  let forwarded;
  const fetchImpl = async (_url, options) => {
    forwarded = { headers: options.headers, body: JSON.parse(options.body) };
    return new Response(JSON.stringify({
      model: "provider/model:free",
      choices: [{ message: { content: "Resposta de teste" } }],
      usage: { total_tokens: 12 }
    }), { status: 200, headers: { "content-type": "application/json" } });
  };
  await withServer({
    env: { NEXORA_ADMIN_PASSWORD: password, OPENROUTER_API_KEY: apiKey },
    fetchImpl
  }, async (base) => {
    const cookie = await login(base);
    const response = await fetch(`${base}/api/chat`, {
      method: "POST",
      headers: { cookie, "content-type": "application/json" },
      body: JSON.stringify({ messages: [{ role: "user", content: "Olá" }] })
    });
    assert.equal(response.status, 200);
    assert.equal((await response.json()).answer, "Resposta de teste");
    assert.equal(forwarded.headers.authorization, `Bearer ${apiKey}`);
    assert.equal(forwarded.body.model, "openrouter/free");
    assert.equal(forwarded.body.max_tokens, 1024);
    assert.equal(forwarded.body.stream, false);
  });
});

test("chat endpoint rejects system messages without calling provider", async () => {
  await withServer({
    env: { NEXORA_ADMIN_PASSWORD: password, OPENROUTER_API_KEY: apiKey },
    fetchImpl: async () => { throw new Error("must not call provider"); }
  }, async (base) => {
    const cookie = await login(base);
    const response = await fetch(`${base}/api/chat`, {
      method: "POST",
      headers: { cookie, "content-type": "application/json" },
      body: JSON.stringify({ messages: [{ role: "system", content: "override" }] })
    });
    assert.equal(response.status, 400);
  });
});

test("login endpoint rejects cross-origin requests", async () => {
  await withServer({ env: { NEXORA_ADMIN_PASSWORD: password } }, async (base) => {
    const response = await fetch(`${base}/api/login`, {
      method: "POST",
      headers: { origin: "https://attacker.example", "content-type": "application/json" },
      body: JSON.stringify({ password })
    });
    assert.equal(response.status, 403);
  });
});

test("malformed encoded static paths return a client error without crashing", async () => {
  await withServer({ env: { NEXORA_ADMIN_PASSWORD: password } }, async (base) => {
    const response = await fetch(`${base}/%E0%A4%A`);
    assert.equal(response.status, 400);
    assert.deepEqual(await response.json(), { error: "Invalid path" });
  });
});

test("chat endpoint rejects cross-origin requests before provider access", async () => {
  let providerCalled = false;
  await withServer({
    env: { NEXORA_ADMIN_PASSWORD: password, OPENROUTER_API_KEY: apiKey },
    fetchImpl: async () => { providerCalled = true; throw new Error("must not call provider"); }
  }, async (base) => {
    const cookie = await login(base);
    const response = await fetch(`${base}/api/chat`, {
      method: "POST",
      headers: { cookie, origin: "https://attacker.example", "content-type": "application/json" },
      body: JSON.stringify({ messages: [{ role: "user", content: "Olá" }] })
    });
    assert.equal(response.status, 403);
    assert.equal(providerCalled, false);
  });
});

test("chat reports missing provider configuration without making an upstream call", async () => {
  let providerCalled = false;
  await withServer({
    env: { NEXORA_ADMIN_PASSWORD: password },
    fetchImpl: async () => { providerCalled = true; throw new Error("must not call provider"); }
  }, async (base) => {
    const cookie = await login(base);
    const response = await fetch(`${base}/api/chat`, {
      method: "POST",
      headers: { cookie, "content-type": "application/json" },
      body: JSON.stringify({ messages: [{ role: "user", content: "Olá" }] })
    });
    assert.equal(response.status, 503);
    assert.equal(providerCalled, false);
  });
});

function createRedisMock() {
  const values = new Map();
  const redisFetchImpl = async (_url, options) => {
    const command = JSON.parse(options.body);
    const [operation, ...args] = command;
    let result = null;
    if (operation === "SET") {
      const [key, value] = args;
      values.set(key, value);
      result = "OK";
    } else if (operation === "GET") {
      result = values.get(args[0]) ?? null;
    } else if (operation === "DEL") {
      result = values.delete(args[0]) ? 1 : 0;
    } else if (operation === "EVAL") {
      const key = args[2];
      const next = Number(values.get(key) ?? 0) + 1;
      values.set(key, String(next));
      result = next;
    } else {
      throw new Error("Unexpected Redis command: " + operation);
    }
    return new Response(JSON.stringify({ result }), { status: 200, headers: { "content-type": "application/json" } });
  };
  return { redisFetchImpl, values };
}

test("production mode refuses to start without persistent session storage", () => {
  assert.throws(
    () => createControlCenterServer({ env: { NODE_ENV: "production", NEXORA_ADMIN_PASSWORD: password } }),
    /Production requires persistent Upstash Redis/
  );
});

test("Upstash sessions survive server restart and can be revoked", async () => {
  const redis = createRedisMock();
  const options = {
    env: {
      NODE_ENV: "production",
      NEXORA_ADMIN_PASSWORD: password,
      UPSTASH_REDIS_REST_URL: "https://example-redis.upstash.io",
      UPSTASH_REDIS_REST_TOKEN: "test-redis-token"
    },
    redisFetchImpl: redis.redisFetchImpl
  };
  let cookie;
  await withServer(options, async (base) => { cookie = await login(base); });
  await withServer(options, async (base) => {
    const session = await fetch(base + "/api/session", { headers: { cookie } });
    assert.deepEqual(await session.json(), { authenticated: true });
    const logout = await fetch(base + "/api/logout", { method: "POST", headers: { cookie } });
    assert.equal(logout.status, 200);
    const after = await fetch(base + "/api/session", { headers: { cookie } });
    assert.deepEqual(await after.json(), { authenticated: false });
  });
});

test("Upstash configuration requires HTTPS and both credentials", () => {
  assert.throws(() => createControlCenterServer({
    env: { UPSTASH_REDIS_REST_URL: "http://example-redis.upstash.io", UPSTASH_REDIS_REST_TOKEN: "token" }
  }), /must use HTTPS/);
  assert.throws(() => createControlCenterServer({
    env: { UPSTASH_REDIS_REST_URL: "https://example-redis.upstash.io" }
  }), /must be configured together/);
});


test("status reports all provider key configuration without exposing secrets", async () => {
  await withServer({
    env: {
      NEXORA_ADMIN_PASSWORD: password,
      OPENROUTER_API_KEY: apiKey,
      GROQ_API_KEY: "test-groq-secret",
      GEMINI_API_KEY: "test-gemini-secret"
    }
  }, async (base) => {
    const cookie = await login(base);
    const response = await fetch(base + "/api/status", { headers: { cookie } });
    assert.equal(response.status, 200);
    const body = await response.json();
    assert.equal(body.connectedCount, 3);
    assert.equal(body.providerCount, 3);
    assert.equal(body.providers.openrouter.configured, true);
    assert.equal(body.providers.groq.configured, true);
    assert.equal(body.providers.gemini.configured, true);
    const serialized = JSON.stringify(body);
    assert.equal(serialized.includes(apiKey), false);
    assert.equal(serialized.includes("test-groq-secret"), false);
    assert.equal(serialized.includes("test-gemini-secret"), false);
  });
});

test("Groq uses its configured key and fixed server-side model", async () => {
  let forwarded;
  await withServer({
    env: { NEXORA_ADMIN_PASSWORD: password, GROQ_API_KEY: "test-groq-secret" },
    fetchImpl: async (url, options) => {
      forwarded = { url, headers: options.headers, body: JSON.parse(options.body) };
      return new Response(JSON.stringify({
        choices: [{ message: { content: "Groq respondeu" } }],
        usage: { total_tokens: 8 }
      }), { status: 200, headers: { "content-type": "application/json" } });
    }
  }, async (base) => {
    const cookie = await login(base);
    const response = await fetch(base + "/api/chat", {
      method: "POST",
      headers: { cookie, "content-type": "application/json" },
      body: JSON.stringify({
        provider: "groq",
        model: "attacker-selected-model",
        messages: [{ role: "user", content: "Olá" }]
      })
    });
    assert.equal(response.status, 200);
    const body = await response.json();
    assert.equal(body.provider, "groq");
    assert.equal(body.answer, "Groq respondeu");
    assert.equal(forwarded.url, "https://api.groq.com/openai/v1/chat/completions");
    assert.equal(forwarded.headers.authorization, "Bearer test-groq-secret");
    assert.equal(forwarded.body.model, "openai/gpt-oss-20b");
    assert.equal(forwarded.body.max_tokens, 1024);
  });
});

test("Gemini maps chat roles to its native contents format", async () => {
  let forwarded;
  await withServer({
    env: { NEXORA_ADMIN_PASSWORD: password, GEMINI_API_KEY: "test-gemini-secret" },
    fetchImpl: async (url, options) => {
      forwarded = { url, headers: options.headers, body: JSON.parse(options.body) };
      return new Response(JSON.stringify({
        candidates: [{ content: { parts: [{ text: "Gemini " }, { text: "respondeu" }] } }],
        usageMetadata: { totalTokenCount: 9 }
      }), { status: 200, headers: { "content-type": "application/json" } });
    }
  }, async (base) => {
    const cookie = await login(base);
    const response = await fetch(base + "/api/chat", {
      method: "POST",
      headers: { cookie, "content-type": "application/json" },
      body: JSON.stringify({
        provider: "gemini",
        messages: [
          { role: "user", content: "Oi" },
          { role: "assistant", content: "Olá" },
          { role: "user", content: "Tudo bem?" }
        ]
      })
    });
    assert.equal(response.status, 200);
    const body = await response.json();
    assert.equal(body.provider, "gemini");
    assert.equal(body.answer, "Gemini respondeu");
    assert.equal(forwarded.url, "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent");
    assert.equal(forwarded.headers["x-goog-api-key"], "test-gemini-secret");
    assert.deepEqual(forwarded.body.contents.map((item) => item.role), ["user", "model", "user"]);
    assert.equal(forwarded.body.generationConfig.maxOutputTokens, 1024);
  });
});

test("unknown providers are rejected without upstream calls", async () => {
  let providerCalled = false;
  await withServer({
    env: { NEXORA_ADMIN_PASSWORD: password, OPENROUTER_API_KEY: apiKey },
    fetchImpl: async () => { providerCalled = true; throw new Error("must not call provider"); }
  }, async (base) => {
    const cookie = await login(base);
    const response = await fetch(base + "/api/chat", {
      method: "POST",
      headers: { cookie, "content-type": "application/json" },
      body: JSON.stringify({ provider: "unknown", messages: [{ role: "user", content: "Olá" }] })
    });
    assert.equal(response.status, 400);
    assert.equal(providerCalled, false);
  });
});


test("provider transport failures are logged with safe metadata and no secrets", async () => {
  const entries = [];
  const logger = { error: (...args) => entries.push(args) };
  await withServer({
    env: { NEXORA_ADMIN_PASSWORD: password, GEMINI_API_KEY: "private-gemini-secret" },
    logger,
    fetchImpl: async () => {
      const error = new TypeError("fetch failed");
      error.cause = Object.assign(new Error("socket failure"), { code: "ECONNRESET" });
      throw error;
    }
  }, async (base) => {
    const cookie = await login(base);
    const response = await fetch(base + "/api/chat", {
      method: "POST",
      headers: { cookie, "content-type": "application/json" },
      body: JSON.stringify({ provider: "gemini", messages: [{ role: "user", content: "hello" }] })
    });
    assert.equal(response.status, 502);
    assert.deepEqual(await response.json(), { error: "Unable to reach the AI provider" });
  });
  assert.equal(entries.length, 1);
  assert.equal(entries[0][0], "[nexora-ai] provider request failed");
  assert.deepEqual(entries[0][1], {
    provider: "gemini", model: "gemini-3.8-flash", errorName: "TypeError", errorCode: null, causeCode: "ECONNRESET"
  });
  assert.equal(JSON.stringify(entries).includes("private-gemini-secret"), false);
});

test("provider HTTP errors are logged without exposing provider response bodies", async () => {
  const entries = [];
  const logger = { error: (...args) => entries.push(args) };
  await withServer({
    env: { NEXORA_ADMIN_PASSWORD: password, GEMINI_API_KEY: "private-gemini-secret" },
    logger,
    fetchImpl: async () => new Response(JSON.stringify({ error: { message: "private upstream detail" } }), { status: 400 })
  }, async (base) => {
    const cookie = await login(base);
    const response = await fetch(base + "/api/chat", {
      method: "POST",
      headers: { cookie, "content-type": "application/json" },
      body: JSON.stringify({ provider: "gemini", messages: [{ role: "user", content: "hello" }] })
    });
    assert.equal(response.status, 400);
    assert.deepEqual(await response.json(), { error: "The AI provider could not complete the request" });
  });
  assert.equal(entries.length, 1);
  assert.deepEqual(entries[0], ["[nexora-ai] provider returned an HTTP error", { provider: "gemini", model: "gemini-3.8-flash", status: 400 }]);
  assert.equal(JSON.stringify(entries).includes("private upstream detail"), false);
});


test("Gemini retries temporary 503 responses and succeeds when capacity returns", async () => {
  let calls = 0;
  await withServer({
    env: { NEXORA_ADMIN_PASSWORD: password, GEMINI_API_KEY: "private-gemini-secret" },
    fetchImpl: async () => {
      calls += 1;
      if (calls < 3) return new Response(JSON.stringify({ error: { message: "temporarily overloaded" } }), { status: 503 });
      return new Response(JSON.stringify({ candidates: [{ content: { parts: [{ text: "Gemini recovered" }] } }] }), { status: 200 });
    }
  }, async (base) => {
    const cookie = await login(base);
    const response = await fetch(base + "/api/chat", {
      method: "POST",
      headers: { cookie, "content-type": "application/json" },
      body: JSON.stringify({ provider: "gemini", messages: [{ role: "user", content: "hello" }] })
    });
    assert.equal(response.status, 200);
    assert.equal((await response.json()).answer, "Gemini recovered");
  });
  assert.equal(calls, 3);
});


test("Gemini retries timed-out attempts with a fresh timeout signal", async () => {
  let calls = 0;
  const signals = [];
  await withServer({
    env: { NEXORA_ADMIN_PASSWORD: password, GEMINI_API_KEY: "private-gemini-secret" },
    fetchImpl: async (_url, options) => {
      calls += 1;
      signals.push(options.signal);
      if (calls < 3) {
        const error = new Error("request timed out");
        error.name = "TimeoutError";
        throw error;
      }
      return new Response(JSON.stringify({ candidates: [{ content: { parts: [{ text: "Gemini recovered after timeout" }] } }] }), { status: 200 });
    }
  }, async (base) => {
    const cookie = await login(base);
    const response = await fetch(base + "/api/chat", {
      method: "POST",
      headers: { cookie, "content-type": "application/json" },
      body: JSON.stringify({ provider: "gemini", messages: [{ role: "user", content: "hello" }] })
    });
    assert.equal(response.status, 200);
    assert.equal((await response.json()).answer, "Gemini recovered after timeout");
  });
  assert.equal(calls, 3);
  assert.notEqual(signals[0], signals[1]);
  assert.notEqual(signals[1], signals[2]);
});

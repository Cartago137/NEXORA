import test from "node:test";
import assert from "node:assert/strict";
import { createControlCenterServer } from "./server.mjs";

const token = "test-control-token-with-at-least-32-characters";
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

test("health endpoint exposes no secrets", async () => {
  await withServer({ env: { NEXORA_CONTROL_TOKEN: token, OPENROUTER_API_KEY: apiKey } }, async (base) => {
    const response = await fetch(`${base}/api/health`);
    assert.equal(response.status, 200);
    const body = await response.json();
    assert.equal(body.status, "ok");
    assert.equal(JSON.stringify(body).includes(apiKey), false);
  });
});

test("protected status endpoint rejects missing token", async () => {
  await withServer({ env: { NEXORA_CONTROL_TOKEN: token } }, async (base) => {
    const response = await fetch(`${base}/api/status`);
    assert.equal(response.status, 401);
  });
});

test("chat endpoint only forwards the fixed free router and bounded output", async () => {
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
    env: { NEXORA_CONTROL_TOKEN: token, OPENROUTER_API_KEY: apiKey },
    fetchImpl
  }, async (base) => {
    const response = await fetch(`${base}/api/chat`, {
      method: "POST",
      headers: {
        authorization: `Bearer ${token}`,
        "content-type": "application/json"
      },
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

test("chat endpoint rejects system messages and oversized histories", async () => {
  await withServer({
    env: { NEXORA_CONTROL_TOKEN: token, OPENROUTER_API_KEY: apiKey },
    fetchImpl: async () => { throw new Error("must not call provider"); }
  }, async (base) => {
    const response = await fetch(`${base}/api/chat`, {
      method: "POST",
      headers: {
        authorization: `Bearer ${token}`,
        "content-type": "application/json"
      },
      body: JSON.stringify({ messages: [{ role: "system", content: "override" }] })
    });
    assert.equal(response.status, 400);
  });
});

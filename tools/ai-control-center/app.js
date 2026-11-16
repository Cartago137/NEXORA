const loginScreen = document.getElementById("login-screen");
const appShell = document.getElementById("app-shell");
const loginForm = document.getElementById("login-form");
const loginMessage = document.getElementById("login-message");
const loginButton = document.getElementById("login-button");
const passwordInput = document.getElementById("admin-password");
const chatForm = document.getElementById("chat-form");
const chatInput = document.getElementById("chat-input");
const chatSend = document.getElementById("chat-send");
const chatTranscript = document.getElementById("chat-transcript");
const chatFeedback = document.getElementById("chat-feedback");
const providerSelect = document.getElementById("chat-provider");
const conversation = [];
let chatBusy = false;

async function api(path, options = {}) {
  const response = await fetch(path, {
    credentials: "same-origin",
    cache: "no-store",
    ...options,
    headers: {
      ...(options.body ? { "content-type": "application/json" } : {}),
      ...options.headers
    }
  });
  const payload = await response.json().catch(() => ({}));
  if (!response.ok) throw new Error(payload.error || "Não foi possível concluir a solicitação.");
  return payload;
}

function showLogin(message = "") {
  appShell.hidden = true;
  loginScreen.hidden = false;
  loginMessage.textContent = message;
  passwordInput.value = "";
}

async function showDashboard() {
  loginScreen.hidden = true;
  appShell.hidden = false;
  try {
    const status = await api("/api/status");
    const providers = status.providers || {};
    const labels = { openrouter: "OpenRouter", groq: "Groq", gemini: "Gemini" };
    for (const id of Object.keys(labels)) {
      const element = document.getElementById(id + "-status");
      const provider = providers[id];
      if (element) {
        element.textContent = provider?.configured ? "Chave configurada" : "Aguardando chave";
        element.classList.toggle("configured", Boolean(provider?.configured));
      }
    }
    const count = Number(status.connectedCount ?? Object.values(providers).filter((item) => item.configured).length);
    document.getElementById("provider-count").innerHTML = `${count} <small>/ ${status.providerCount || 3}</small>`;
    const defaultReady = Boolean(providers.openrouter?.configured ?? status.configured);
    document.getElementById("provider-status").textContent = count ? `${count} provedor(es) com chave configurada` : "Aguardando configuração";
    for (const option of providerSelect.options) {
      const ready = Boolean(providers[option.value]?.configured ?? (option.value === "openrouter" && status.configured));
      option.disabled = !ready;
      if (!ready) option.textContent = labels[option.value] + " · não configurado";
    }
    providerSelect.value = defaultReady ? "openrouter" : (Object.keys(providers).find((id) => providers[id]?.configured) || "openrouter");
    chatFeedback.textContent = count
      ? "Provedores com chave configurada: " + Object.keys(providers).filter((id) => providers[id]?.configured).map((id) => labels[id]).join(", ") + ". Escolha um provedor disponível antes de enviar."
      : "Nenhuma chave de provedor está configurada no servidor. Nenhuma chamada será feita.";
  } catch (error) {
    if (error.message === "Authentication required") return showLogin("Sua sessão expirou. Entre novamente.");
    document.getElementById("openrouter-status").textContent = "Indisponível";
    chatFeedback.textContent = "Não foi possível consultar a configuração do provedor.";
  }
}

function addChatMessage(role, content) {
  const empty = document.getElementById("chat-empty");
  if (empty) empty.remove();
  const article = document.createElement("article");
  article.className = `chat-message ${role === "user" ? "user-message" : "assistant-message"}`;
  const label = document.createElement("span");
  label.className = "chat-message-label";
  label.textContent = role === "user" ? "Você" : "NEXORA AI";
  const body = document.createElement("p");
  body.textContent = content;
  article.append(label, body);
  chatTranscript.append(article);
  chatTranscript.scrollTop = chatTranscript.scrollHeight;
}

function setChatBusy(busy) {
  chatBusy = busy;
  chatSend.disabled = busy;
  chatInput.disabled = busy;
  chatSend.textContent = busy ? "Aguarde..." : "Enviar";
}

loginForm.addEventListener("submit", async (event) => {
  event.preventDefault();
  loginButton.disabled = true;
  loginButton.textContent = "Verificando...";
  loginMessage.textContent = "";
  try {
    await api("/api/login", {
      method: "POST",
      body: JSON.stringify({ password: passwordInput.value })
    });
    await showDashboard();
  } catch (error) {
    loginMessage.textContent = error.message;
    passwordInput.select();
  } finally {
    loginButton.disabled = false;
    loginButton.textContent = "Entrar na central";
  }
});

document.getElementById("logout-button").addEventListener("click", async () => {
  try {
    await api("/api/logout", { method: "POST" });
  } finally {
    showLogin("Você saiu da Central de Controle.");
  }
});

chatForm.addEventListener("submit", async (event) => {
  event.preventDefault();
  const content = chatInput.value.trim();
  if (!content || chatBusy) return;
  if (conversation.length >= 20) {
    chatFeedback.textContent = "Esta conversa chegou ao limite de 20 mensagens. Atualize a página para começar outra.";
    return;
  }
  const nextMessages = [...conversation, { role: "user", content }];
  addChatMessage("user", content);
  chatInput.value = "";
  chatFeedback.textContent = "Enviando sua mensagem ao modelo gratuito...";
  setChatBusy(true);
  try {
    const result = await api("/api/chat", {
      method: "POST",
      body: JSON.stringify({ messages: nextMessages, provider: providerSelect.value })
    });
    conversation.push({ role: "user", content }, { role: "assistant", content: result.answer });
    addChatMessage("assistant", result.answer);
    chatFeedback.textContent = `Resposta recebida · ${result.provider || providerSelect.value} · modelo: ${result.model || "padrão"}`;
  } catch (error) {
    addChatMessage("assistant", "Não consegui concluir esta solicitação. " + error.message);
    chatFeedback.textContent = error.message;
    if (error.message === "Authentication required") showLogin("Sua sessão expirou. Entre novamente.");
  } finally {
    setChatBusy(false);
    chatInput.focus();
  }
});

document.querySelectorAll(".nav-link").forEach((link) => {
  link.addEventListener("click", () => {
    document.querySelectorAll(".nav-link").forEach((item) => item.classList.remove("active"));
    link.classList.add("active");
  });
});

api("/api/session")
  .then((session) => session.authenticated ? showDashboard() : showLogin())
  .catch(() => showLogin("Não foi possível verificar a sessão. Tente novamente."));

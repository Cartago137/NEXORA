const loginScreen = document.getElementById("login-screen");
const appShell = document.getElementById("app-shell");
const loginForm = document.getElementById("login-form");
const loginMessage = document.getElementById("login-message");
const loginButton = document.getElementById("login-button");
const passwordInput = document.getElementById("admin-password");

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
    const providerStatus = document.getElementById("openrouter-status");
    const providerHint = document.getElementById("provider-status");
    providerStatus.textContent = status.configured ? "Chave configurada" : "Aguardando chave";
    providerHint.textContent = status.configured ? "Integração pronta para teste" : "Aguardando configuração";
  } catch (error) {
    if (error.message === "Authentication required") return showLogin("Sua sessão expirou. Entre novamente.");
    document.getElementById("openrouter-status").textContent = "Indisponível";
  }
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

document.querySelectorAll(".nav-link").forEach((link) => {
  link.addEventListener("click", () => {
    document.querySelectorAll(".nav-link").forEach((item) => item.classList.remove("active"));
    link.classList.add("active");
  });
});

api("/api/session")
  .then((session) => session.authenticated ? showDashboard() : showLogin())
  .catch(() => showLogin("Não foi possível verificar a sessão. Tente novamente."));

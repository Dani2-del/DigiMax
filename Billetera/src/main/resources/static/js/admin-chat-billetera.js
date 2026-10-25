(() => {
  const messagesContainer = document.querySelector("#walletAdminChatMessages");
  const form = document.querySelector("#walletAdminChatForm");
  const textInput = document.querySelector("#walletAdminChatText");
  const submitButton = document.querySelector("#walletAdminChatSubmit");
  const errorBox = document.querySelector("#walletAdminChatError");
  if (!messagesContainer || !form || !textInput || !submitButton || !errorBox) return;

  const messagesUrl = messagesContainer.dataset.messagesUrl;
  const currentAuthorId = messagesContainer.dataset.authorId;
  let loadingMessages = false;

  function showError(message) {
    errorBox.textContent = message;
    errorBox.hidden = false;
  }

  function responseError(response, body) {
    if (body && typeof body.detail === "string") return body.detail;
    if (body && typeof body.message === "string") return body.message;
    return `No se pudo completar la solicitud (HTTP ${response.status}).`;
  }

  async function readResponse(response) {
    const contentType = response.headers.get("content-type") || "";
    if (!contentType.includes("json")) {
      if (!response.ok) return null;
      throw new Error("El servidor devolvió una respuesta inválida.");
    }
    return response.json();
  }

  function renderMessages(messages) {
    const atBottom = messagesContainer.scrollHeight
      - messagesContainer.scrollTop
      - messagesContainer.clientHeight < 32;
    const fragment = document.createDocumentFragment();
    if (messages.length === 0) {
      const empty = document.createElement("p");
      empty.className = "muted wallet-admin-chat-empty";
      empty.textContent = "Aún no hay mensajes. Inicia la conversación.";
      fragment.append(empty);
    } else {
      for (const message of messages) {
        const article = document.createElement("article");
        article.className = "wallet-admin-chat-message";
        if (message.authorId === currentAuthorId) article.classList.add("is-own");

        const heading = document.createElement("div");
        heading.className = "wallet-admin-chat-message-heading";
        const author = document.createElement("strong");
        author.textContent = message.authorName || "Usuario";
        const role = document.createElement("span");
        role.textContent = message.authorRole || "Equipo";
        const time = document.createElement("time");
        time.textContent = message.createdAt || "";
        heading.append(author, role, time);

        const text = document.createElement("p");
        text.textContent = message.text || "";
        article.append(heading, text);
        fragment.append(article);
      }
    }
    messagesContainer.replaceChildren(fragment);
    if (atBottom) messagesContainer.scrollTop = messagesContainer.scrollHeight;
  }

  async function refreshMessages() {
    if (loadingMessages) return;
    loadingMessages = true;
    try {
      const response = await fetch(messagesUrl, {
        headers: { Accept: "application/json" },
        cache: "no-store"
      });
      const body = await readResponse(response);
      if (!response.ok) throw new Error(responseError(response, body));
      if (!Array.isArray(body)) throw new Error("El servidor devolvió una respuesta de mensajes inválida.");
      errorBox.hidden = true;
      renderMessages(body);
    } catch (error) {
      showError(error instanceof Error ? error.message : "No se pudieron cargar los mensajes.");
    } finally {
      loadingMessages = false;
    }
  }

  form.addEventListener("submit", async event => {
    event.preventDefault();
    errorBox.hidden = true;
    submitButton.disabled = true;
    try {
      const response = await fetch(form.action, {
        method: "POST",
        headers: { Accept: "application/json" },
        body: new URLSearchParams(new FormData(form))
      });
      const body = await readResponse(response);
      if (!response.ok) throw new Error(responseError(response, body));
      textInput.value = "";
      await refreshMessages();
      textInput.focus();
    } catch (error) {
      showError(error instanceof Error ? error.message : "No se pudo enviar el mensaje.");
    } finally {
      submitButton.disabled = false;
    }
  });

  refreshMessages();
  window.setInterval(refreshMessages, 5000);
})();

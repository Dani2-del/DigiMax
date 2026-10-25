(() => {
  const messagesContainer = document.querySelector("#adminChatMessages");
  const form = document.querySelector("#adminChatForm");
  const textInput = document.querySelector("#adminChatText");
  const submitButton = document.querySelector("#adminChatSubmit");
  const errorBox = document.querySelector("#adminChatError");
  if (!messagesContainer || !form || !textInput || !submitButton || !errorBox) return;

  const messagesUrl = messagesContainer.dataset.messagesUrl;
  const currentAuthorId = messagesContainer.dataset.authorId;
  let loadingMessages = false;

  function showError(message) {
    errorBox.textContent = message;
    errorBox.hidden = false;
  }

  function messageError(response, body) {
    if (body && typeof body.detail === "string") return body.detail;
    if (body && typeof body.message === "string") return body.message;
    return `No se pudo completar la solicitud (HTTP ${response.status}).`;
  }

  function renderMessages(messages) {
    const shouldStickToBottom = messagesContainer.scrollHeight
      - messagesContainer.scrollTop
      - messagesContainer.clientHeight < 32;
    const fragment = document.createDocumentFragment();
    if (messages.length === 0) {
      const empty = document.createElement("p");
      empty.className = "muted admin-chat-empty";
      empty.textContent = "Aún no hay mensajes. Inicia la conversación.";
      fragment.append(empty);
    } else {
      messages.forEach(message => {
        const article = document.createElement("article");
        article.className = "admin-chat-message";
        if (message.authorId === currentAuthorId) article.classList.add("is-own");

        const heading = document.createElement("div");
        heading.className = "admin-chat-message-heading";
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
      });
    }
    messagesContainer.replaceChildren(fragment);
    if (shouldStickToBottom) messagesContainer.scrollTop = messagesContainer.scrollHeight;
  }

  async function refreshMessages() {
    if (loadingMessages) return;
    loadingMessages = true;
    try {
      const response = await fetch(messagesUrl, {
        headers: { Accept: "application/json" },
        cache: "no-store"
      });
      const body = await response.json();
      if (!response.ok) throw new Error(messageError(response, body));
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
      const body = await response.json();
      if (!response.ok) throw new Error(messageError(response, body));
      form.reset();
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

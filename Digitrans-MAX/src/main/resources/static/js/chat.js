(() => {
    const messageList = document.getElementById("chatMessages");
    const form = document.getElementById("chatCompose");
    const textInput = document.getElementById("chatText");
    const imageInput = document.getElementById("chatImage");
    const imagePickLabel = document.querySelector('label[for="chatImage"]');
    const imageName = document.getElementById("chatImageName");
    const imagePreview = document.getElementById("chatImagePreview");
    const imagePreviewImg = document.getElementById("chatImagePreviewImg");
    const errorBox = document.getElementById("chatError");
    const submitButton = document.getElementById("chatSubmit");
    const cancelEditButton = document.getElementById("chatCancelEdit");
    if (!messageList || !form || !textInput || !imageInput) return;

    const messagesUrl = messageList.dataset.messagesUrl;
    let editingId = null;
    let previewUrl = null;
    let refreshing = false;

    function messageUrl(id, suffix = "") {
        return `${messagesUrl}/${encodeURIComponent(id)}${suffix}`;
    }

    function showError(message) {
        errorBox.textContent = message;
        errorBox.hidden = !message;
    }

    function createMessage(message) {
        const article = document.createElement("article");
        article.className = "chat-message";
        article.dataset.messageId = message.id;
        article.dataset.snapshot = JSON.stringify(message);
        article.dataset.text = message.text || "";

        const meta = document.createElement("div");
        meta.className = "chat-message-meta";
        const author = document.createElement("strong");
        author.textContent = message.authorName || "Usuario";
        const role = document.createElement("span");
        role.className = "chat-role";
        role.textContent = message.authorRole;
        const date = document.createElement("time");
        date.textContent = message.createdAt;
        meta.append(author, role, date);
        if (message.edited) {
            const edited = document.createElement("span");
            edited.className = "chat-edited";
            edited.textContent = "Editado";
            meta.append(edited);
        }
        article.append(meta);

        if (message.hasImage) {
            const image = document.createElement("img");
            image.className = "chat-message-image";
            image.src = messageUrl(message.id, "/imagen");
            image.alt = "Imagen compartida en el chat";
            image.loading = "lazy";
            article.append(image);
        }
        if (message.text) {
            const text = document.createElement("p");
            text.textContent = message.text;
            article.append(text);
        }
        if (message.canEdit) {
            const actions = document.createElement("div");
            actions.className = "chat-message-actions";
            const editButton = document.createElement("button");
            editButton.type = "button";
            editButton.className = "chat-edit";
            editButton.textContent = "Editar";
            actions.append(editButton);
            article.append(actions);
        }
        return article;
    }

    function renderMessages(messages) {
        const nearBottom = messageList.scrollHeight - messageList.scrollTop - messageList.clientHeight < 80;
        const previousMessages = new Map(
            Array.from(messageList.querySelectorAll(".chat-message"),
                article => [article.dataset.messageId, article])
        );

        if (messages.length === 0) {
            messageList.replaceChildren();
            const empty = document.createElement("p");
            empty.className = "chat-empty";
            empty.textContent = "Aún no hay mensajes. ¡Inicia la conversación!";
            messageList.append(empty);
            return;
        }

        messageList.querySelector(".chat-empty")?.remove();
        for (const message of messages) {
            const existing = previousMessages.get(message.id);
            const snapshot = JSON.stringify(message);
            if (!existing) {
                messageList.append(createMessage(message));
            } else if (existing.dataset.snapshot !== snapshot) {
                existing.replaceWith(createMessage(message));
            }
            previousMessages.delete(message.id);
        }
        previousMessages.forEach(article => article.remove());

        while (messageList.querySelectorAll(".chat-message").length > 100) {
            messageList.querySelector(".chat-message")?.remove();
        }
        if (nearBottom) messageList.scrollTop = messageList.scrollHeight;
    }

    async function refreshMessages() {
        if (refreshing) return;
        refreshing = true;
        try {
            const response = await fetch(messagesUrl, {
                headers: { Accept: "application/json" },
                cache: "no-store"
            });
            if (!response.ok) throw new Error(`HTTP ${response.status}`);
            renderMessages(await response.json());
        } catch (error) {
            console.error("No se pudo actualizar el chat.", error);
        } finally {
            refreshing = false;
        }
    }

    function clearImageSelection() {
        imageInput.value = "";
        imageName.textContent = "";
        imagePreview.hidden = true;
        imagePreviewImg.removeAttribute("src");
        if (previewUrl) URL.revokeObjectURL(previewUrl);
        previewUrl = null;
    }

    function finishEditing() {
        editingId = null;
        textInput.value = "";
        imageInput.disabled = false;
        imagePickLabel.hidden = false;
        cancelEditButton.hidden = true;
        submitButton.innerHTML = 'Enviar mensaje <span aria-hidden="true">→</span>';
        clearImageSelection();
    }

    function beginEditing(article) {
        editingId = article.dataset.messageId;
        textInput.value = article.dataset.text;
        imageInput.disabled = true;
        imagePickLabel.hidden = true;
        cancelEditButton.hidden = false;
        submitButton.textContent = "Guardar cambios";
        clearImageSelection();
        textInput.focus();
        form.scrollIntoView({ behavior: "smooth", block: "nearest" });
        showError("");
    }

    messageList.addEventListener("click", event => {
        const button = event.target.closest(".chat-edit");
        if (button) beginEditing(button.closest(".chat-message"));
    });

    document.querySelectorAll("[data-emoji]").forEach(button => {
        button.addEventListener("click", () => {
            const start = textInput.selectionStart;
            const end = textInput.selectionEnd;
            textInput.setRangeText(button.dataset.emoji, start, end, "end");
            textInput.focus();
        });
    });

    imageInput.addEventListener("change", () => {
        const image = imageInput.files[0];
        clearErrorForImage();
        if (!image) {
            clearImageSelection();
            return;
        }
        if (image.size > 5 * 1024 * 1024
                || !["image/png", "image/jpeg", "image/webp"].includes(image.type)) {
            clearImageSelection();
            showError("Selecciona una imagen PNG, JPEG o WebP de máximo 5 MB.");
            return;
        }
        if (previewUrl) URL.revokeObjectURL(previewUrl);
        previewUrl = URL.createObjectURL(image);
        imagePreviewImg.src = previewUrl;
        imageName.textContent = image.name;
        imagePreview.hidden = false;
    });

    function clearErrorForImage() {
        showError("");
    }

    document.getElementById("chatImageRemove").addEventListener("click", clearImageSelection);
    cancelEditButton.addEventListener("click", finishEditing);

    form.addEventListener("submit", async event => {
        event.preventDefault();
        showError("");
        const texto = textInput.value.trim();
        const imagen = imageInput.files[0];
        if (editingId && !texto) {
            showError("El mensaje debe contener texto.");
            return;
        }
        if (!editingId && !texto && !imagen) {
            showError("Escribe un mensaje o selecciona una imagen.");
            return;
        }

        submitButton.disabled = true;
        try {
            const response = editingId
                ? await fetch(messageUrl(editingId), {
                    method: "PUT",
                    headers: { "Content-Type": "application/json", Accept: "application/json" },
                    body: JSON.stringify({ texto })
                })
                : await fetch(form.action, {
                    method: "POST",
                    headers: { Accept: "application/json" },
                    body: new FormData(form)
                });

            const result = await response.json();
            if (!response.ok) throw new Error(result.error || "No se pudo enviar el mensaje.");
            if (editingId) finishEditing();
            else {
                textInput.value = "";
                clearImageSelection();
            }
            showError("");
            await refreshMessages();
            messageList.scrollTop = messageList.scrollHeight;
        } catch (error) {
            showError(error.message || "No se pudo enviar el mensaje. Inténtalo de nuevo.");
        } finally {
            submitButton.disabled = false;
        }
    });

    refreshMessages();
    window.setInterval(refreshMessages, 5000);
})();

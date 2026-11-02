(() => {
  const app = document.getElementById("users-app");
  if (!app) return;

  const filters = document.getElementById("users-filters");
  const rows = document.getElementById("users-rows");
  const emptyState = document.getElementById("users-empty");
  const message = document.getElementById("users-message");
  const count = document.getElementById("users-count");
  const pageLabel = document.getElementById("users-page");
  const previous = document.getElementById("users-previous");
  const next = document.getElementById("users-next");
  const dialog = document.getElementById("user-editor");
  const editorForm = document.getElementById("user-editor-form");
  const globalAdmin = app.dataset.globalAdmin === "true";
  const state = { page: 0, totalPages: 0, users: [] };
  const roleNames = {
    ROLE_USER: "Usuario",
    ROLE_MODERATOR: "Moderador"
  };
  const csrfToken = document.querySelector('meta[name="csrf-token"]').content;
  const csrfHeader = document.querySelector('meta[name="csrf-header"]').content;
  let latestLoad = 0;

  function showMessage(text, isError = false) {
    message.textContent = text;
    message.classList.toggle("error", isError);
    message.hidden = !text;
  }

  function cell(text) {
    const element = document.createElement("td");
    element.textContent = text || "—";
    return element;
  }

  function renderUsers(users) {
    rows.replaceChildren();
    emptyState.hidden = users.length > 0;
    users.forEach((user) => {
      const row = document.createElement("tr");
      const nameCell = document.createElement("td");
      nameCell.textContent = user.name || "Sin nombre";
      const email = document.createElement("span");
      email.className = "subtext";
      email.textContent = user.email || "";
      nameCell.append(email);
      row.append(nameCell, cell(roleNames[user.role] || user.role));

      const activeCell = document.createElement("td");
      const activeStatus = document.createElement("span");
      activeStatus.className = `status ${user.active ? "status-activo" : "status-inactivo"}`;
      activeStatus.textContent = user.active ? "Activo" : "Inactivo";
      activeCell.append(activeStatus);
      row.append(activeCell, cell(user.verified ? "Verificado" : "Sin verificar"));

      const registered = user.registeredAt
        ? new Date(user.registeredAt).toLocaleDateString("es-CO")
        : "—";
      row.append(cell(registered));
      const actionCell = document.createElement("td");
      const edit = document.createElement("button");
      edit.type = "button";
      edit.className = "table-action";
      edit.textContent = "Editar";
      edit.addEventListener("click", () => openEditor(user));
      actionCell.append(edit);
      row.append(actionCell);
      rows.append(row);
    });
  }

  async function responseJson(response) {
    const body = await response.text();
    let data = {};
    if (body) {
      try {
        data = JSON.parse(body);
      } catch {
        data = {};
      }
    }
    if (!response.ok) {
      throw new Error(data.message || `La solicitud falló (HTTP ${response.status}).`);
    }
    return data;
  }

  async function loadUsers() {
    const requestId = ++latestLoad;
    showMessage("");
    const formData = new FormData(filters);
    const payload = {
      search: formData.get("search"),
      role: formData.get("role"),
      active: formData.get("active") === "" ? null : formData.get("active") === "true",
      verified: formData.get("verified") === "" ? null : formData.get("verified") === "true",
      registeredFrom: formData.get("registeredFrom") || null,
      registeredTo: formData.get("registeredTo") || null,
      page: state.page,
      size: 20,
      sort: formData.get("sort")
    };
    try {
      const response = await fetch("/admin/usuarios/buscar", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          [csrfHeader]: csrfToken
        },
        body: JSON.stringify(payload)
      });
      if (requestId !== latestLoad) return;
      const result = await responseJson(response);
      if (requestId !== latestLoad) return;
      state.users = result.content || [];
      state.totalPages = result.totalPages || 0;
      renderUsers(state.users);
      count.textContent = `${result.totalElements || 0} cuentas encontradas`;
      pageLabel.textContent = state.totalPages
        ? `Página ${state.page + 1} de ${state.totalPages}`
        : "Sin resultados";
      previous.disabled = state.page <= 0;
      next.disabled = state.page + 1 >= state.totalPages;
    } catch (error) {
      if (requestId !== latestLoad) return;
      renderUsers([]);
      state.totalPages = 0;
      count.textContent = "";
      pageLabel.textContent = "";
      showMessage(error.message || "No se pudieron cargar los usuarios.", true);
    }
  }

  function openEditor(user) {
    editorForm.dataset.userId = user.id;
    editorForm.elements.name.value = user.name || "";
    editorForm.elements.email.value = user.email || "";
    editorForm.elements.name.readOnly = !globalAdmin;
    editorForm.elements.email.readOnly = !globalAdmin;
    editorForm.elements.active.value = String(user.active);
    editorForm.elements.active.disabled = !globalAdmin;
    document.getElementById("user-active-field").hidden = !globalAdmin;
    const roleSelect = editorForm.elements.role;
    roleSelect.replaceChildren();
    const allowedRoles = ["ROLE_USER", "ROLE_MODERATOR"];
    allowedRoles.forEach((role) => {
      const option = document.createElement("option");
      option.value = role;
      option.textContent = roleNames[role];
      roleSelect.append(option);
    });
    roleSelect.value = user.role;
    dialog.showModal();
  }

  filters.addEventListener("submit", (event) => {
    event.preventDefault();
    state.page = 0;
    loadUsers();
  });
  filters.addEventListener("reset", () => {
    window.setTimeout(() => {
      state.page = 0;
      loadUsers();
    });
  });
  previous.addEventListener("click", () => {
    if (state.page > 0) {
      state.page -= 1;
      loadUsers();
    }
  });
  next.addEventListener("click", () => {
    if (state.page + 1 < state.totalPages) {
      state.page += 1;
      loadUsers();
    }
  });
  document.getElementById("user-editor-close").addEventListener("click", () => dialog.close());
  editorForm.addEventListener("submit", async (event) => {
    event.preventDefault();
    const formData = new FormData(editorForm);
    const role = formData.get("role");
    const user = state.users.find((candidate) => candidate.id === editorForm.dataset.userId);
    if (!user) return;
    const update = {
      name: formData.get("name"),
      email: formData.get("email"),
      role,
      active: globalAdmin ? formData.get("active") === "true" : user.active,
      serviceAssignment: role === "ROLE_MODERATOR" ? "RECARGAS_MOVIL" : null
    };
    const submit = editorForm.querySelector('button[type="submit"]');
    submit.disabled = true;
    try {
      const response = await fetch(`/admin/usuarios/${encodeURIComponent(user.id)}`, {
        method: "PATCH",
        headers: {
          "Content-Type": "application/json",
          [csrfHeader]: csrfToken
        },
        body: JSON.stringify(update)
      });
      await responseJson(response);
      dialog.close();
      await loadUsers();
      showMessage("Los cambios del usuario se guardaron correctamente.");
    } catch (error) {
      showMessage(error.message || "No se pudieron guardar los cambios.", true);
    } finally {
      submit.disabled = false;
    }
  });

  loadUsers();
})();

(() => {
  const selectorBusqueda = document.getElementById('recipientSearch');
  const contenedor = document.querySelector('.announcement-recipient-list');
  const opcionesDestino = document.querySelectorAll('input[name="tipoDestinatarios"]');
  const filtrosRol = document.querySelectorAll('input[name="roles"]');
  const formulario = document.querySelector('.announcement-form');
  const contador = document.getElementById('recipientCount');
  if (!selectorBusqueda || !contenedor || opcionesDestino.length === 0
      || filtrosRol.length === 0 || !formulario) return;

  const destinatarios = [...contenedor.querySelectorAll('.announcement-recipient')];
  const casillas = destinatarios.map((item) => item.querySelector('input[name="usuarios"]'));

  function sincronizarDestinatarios() {
    const seleccionados = formulario.querySelector('input[name="tipoDestinatarios"]:checked');
    const enviarPorTipo = seleccionados && seleccionados.value === 'roles';
    const rolesSeleccionados = new Set(
      [...filtrosRol].filter((filtro) => filtro.checked).map((filtro) => filtro.value),
    );
    const busqueda = selectorBusqueda.value.trim().toLocaleLowerCase();
    let cantidadVisible = 0;

    destinatarios.forEach((elemento, indice) => {
      const casilla = casillas[indice];
      const rolIncluido = rolesSeleccionados.has(elemento.dataset.role);
      const coincideBusqueda = elemento.dataset.search.toLocaleLowerCase().includes(busqueda);
      elemento.hidden = !rolIncluido || !coincideBusqueda;
      casilla.disabled = Boolean(enviarPorTipo) || !rolIncluido;
      if (enviarPorTipo || !rolIncluido) casilla.checked = false;
      if (rolIncluido) cantidadVisible++;
    });

    if (contador) {
      contador.textContent = `${cantidadVisible} cuentas de los tipos seleccionados`;
    }
  }

  selectorBusqueda.addEventListener('input', sincronizarDestinatarios);

  opcionesDestino.forEach((opcion) => opcion.addEventListener('change', sincronizarDestinatarios));
  filtrosRol.forEach((filtro) => filtro.addEventListener('change', sincronizarDestinatarios));
  sincronizarDestinatarios();
})();

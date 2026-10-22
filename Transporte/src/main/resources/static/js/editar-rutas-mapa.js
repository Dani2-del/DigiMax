(() => {
  const palette = ['#ed4f24', '#087e8b', '#34764a', '#7356a5'];
  const routes = window.TRANSCARIBE_RUTAS || [];
  const categorySelect = document.getElementById('routeCategory');
  const routeSelect = document.getElementById('routeCode');
  const directionSelect = document.getElementById('routeDirection');
  const status = document.getElementById('routeEditorStatus');
  const warning = document.getElementById('routeEditorWarning');
  const referenceLink = document.getElementById('officialMapLink');
  const undoButton = document.getElementById('undoPoint');
  const clearButton = document.getElementById('clearDraft');
  const saveButton = document.getElementById('savePath');
  const loadButton = document.getElementById('loadSavedPath');
  const deleteButton = document.getElementById('deletePath');
  const map = L.map('routeMap').setView([10.4, -75.5], 12);
  const stopLayers = L.layerGroup().addTo(map);
  const pathLayers = L.layerGroup().addTo(map);
  const csvCache = new Map();
  let currentStops = [];
  let draftPath = [];
  let savedPathExists = false;

  L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
    maxZoom: 19,
    attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
  }).addTo(map);

  function parseCsv(text) {
    const rows = [];
    let row = [];
    let field = '';
    let quoted = false;

    for (let index = 0; index < text.length; index += 1) {
      const char = text[index];
      if (quoted) {
        if (char === '"' && text[index + 1] === '"') {
          field += '"';
          index += 1;
        } else if (char === '"') {
          quoted = false;
        } else {
          field += char;
        }
      } else if (char === '"' && field.length === 0) {
        quoted = true;
      } else if (char === ',') {
        row.push(field);
        field = '';
      } else if (char === '\n') {
        row.push(field.replace(/\r$/, ''));
        rows.push(row);
        row = [];
        field = '';
      } else {
        field += char;
      }
    }
    if (field.length > 0 || row.length > 0) {
      row.push(field.replace(/\r$/, ''));
      rows.push(row);
    }
    if (rows.length < 2) return [];

    const headers = rows[0].map((header) => {
      const cleaned = header.replace(/^\uFEFF/, '').trim();
      const normalized = cleaned.toLowerCase();
      if (['lat', 'latitud', 'latitude'].includes(normalized)) return 'lat';
      if (['lng', 'lon', 'longitud', 'longitude'].includes(normalized)) return 'lng';
      return cleaned;
    });
    return rows.slice(1)
      .filter((values) => values.some((value) => value.trim() !== ''))
      .map((values) => Object.fromEntries(headers.map((header, index) => [header, values[index] || ''])));
  }

  async function loadStops(route) {
    if (csvCache.has(route.code)) return csvCache.get(route.code);
    const response = await fetch(`/Rutas/${route.file.split('/').map(encodeURIComponent).join('/')}`);
    if (!response.ok) {
      throw new Error(`No se pudo leer el archivo de paradas (${response.status}).`);
    }
    const data = parseCsv(await response.text());
    csvCache.set(route.code, data);
    return data;
  }

  function selectedRoute() {
    return routes.find((route) => route.code === routeSelect.value);
  }

  function setStatus(message) {
    status.textContent = message;
  }

  function setWarning(message) {
    warning.textContent = message;
    warning.hidden = !message;
  }

  function clearLayers() {
    stopLayers.clearLayers();
    pathLayers.clearLayers();
  }

  function renderPath() {
    pathLayers.clearLayers();
    const route = selectedRoute();
    if (!route || draftPath.length === 0) return;
    const color = palette[Math.max(0, routeSelect.selectedIndex - 1) % palette.length];
    draftPath.forEach((point, index) => {
      L.circleMarker([point.latitud, point.longitud], {
        radius: index === 0 || index === draftPath.length - 1 ? 7 : 5,
        color: '#fff',
        weight: 2,
        fillColor: color,
        fillOpacity: 1
      }).bindTooltip(`Punto ${index + 1}`).addTo(pathLayers);
    });
    if (draftPath.length >= 2) {
      L.polyline(draftPath.map((point) => [point.latitud, point.longitud]), {
        color,
        weight: 5,
        opacity: 0.9
      }).addTo(pathLayers);
    }
    undoButton.disabled = draftPath.length === 0;
    clearButton.disabled = draftPath.length === 0;
    saveButton.disabled = draftPath.length < 2;
  }

  function drawStops(stops, direction) {
    stopLayers.clearLayers();
    stops.forEach((stop, index) => {
      const color = index === 0 ? '#20804a' : index === stops.length - 1 ? '#c43d27' : '#f7a51a';
      const marker = L.circleMarker([stop.latitude, stop.longitude], {
        radius: 8,
        color: '#fff',
        weight: 2,
        fillColor: color,
        fillOpacity: 1
      });
      marker.bindPopup(`${index + 1}. ${stop.Ubicacion_del_Paradero || stop.Paraderos || 'Parada sin nombre'} (${direction})`);
      marker.on('click', (event) => L.DomEvent.stopPropagation(event));
      marker.addTo(stopLayers);
    });
  }

  async function loadSavedPath(routeCode, direction) {
    const response = await fetch(`/mapa-rutas/api/rutas/${encodeURIComponent(routeCode)}`);
    if (!response.ok) throw new Error(`No se pudo consultar el trazado guardado (${response.status}).`);
    const savedGeometries = await response.json();
    const geometry = savedGeometries.find((item) => item.sentido === direction);
    savedPathExists = Boolean(geometry);
    loadButton.disabled = !geometry;
    deleteButton.disabled = !geometry;
    if (geometry) {
      draftPath = geometry.coordenadas.map((point) => ({
        latitud: point.latitud,
        longitud: point.longitud
      }));
    } else {
      draftPath = [];
    }
    renderPath();
    return Boolean(geometry);
  }

  async function refreshRoute() {
    clearLayers();
    draftPath = [];
    savedPathExists = false;
    directionSelect.replaceChildren(new Option('Selecciona una ruta', ''));
    directionSelect.disabled = true;
    referenceLink.hidden = true;
    undoButton.disabled = true;
    clearButton.disabled = true;
    saveButton.disabled = true;
    loadButton.disabled = true;
    deleteButton.disabled = true;
    setWarning('');

    const route = selectedRoute();
    if (!route) {
      setStatus('Selecciona una ruta con archivo de paradas.');
      return;
    }
    if (!route.file) {
      setStatus(`${route.code} no tiene un CSV de paradas para guiar el trazado.`);
      setWarning('Esta ruta se podrá editar cuando esté listo su archivo de paradas con coordenadas.');
      return;
    }
    if (route.reference) {
      referenceLink.href = `/images/Rutas/${encodeURIComponent(route.reference)}`;
      referenceLink.hidden = false;
    }

    try {
      setStatus(`Cargando paradas de ${route.code}…`);
      const rows = (await loadStops(route))
        .filter((row) => row.Codigo_Rutas.trim().toUpperCase() === route.code);
      const directions = [...new Set(rows.map((row) => row.Sentido.trim().toUpperCase()).filter(Boolean))];
      if (directions.length === 0) {
        throw new Error(`El CSV de ${route.code} no contiene sentidos de viaje.`);
      }
      directions.forEach((direction) => directionSelect.add(new Option(direction, direction)));
      directionSelect.disabled = false;
      directionSelect.dataset.rows = JSON.stringify(rows);
      directionSelect.value = directions[0];
      await refreshDirection();
    } catch (error) {
      setStatus('No fue posible cargar la ruta.');
      setWarning(error.message);
    }
  }

  async function refreshDirection() {
    clearLayers();
    draftPath = [];
    const route = selectedRoute();
    const direction = directionSelect.value;
    if (!route || !direction) return;

    const rows = JSON.parse(directionSelect.dataset.rows || '[]')
      .filter((row) => row.Sentido.trim().toUpperCase() === direction)
      .sort((a, b) => Number(a.Gid) - Number(b.Gid));
    currentStops = rows.flatMap((row) => {
      if (!row.lat.trim() || !row.lng.trim()) return [];
      const latitude = Number(row.lat);
      const longitude = Number(row.lng);
      if (!Number.isFinite(latitude) || !Number.isFinite(longitude)
          || latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180
          || (latitude === 0 && longitude === 0)) return [];
      return [{ ...row, latitude, longitude }];
    });

    drawStops(currentStops, direction);
    try {
      const loaded = await loadSavedPath(route.code, direction);
      const bounds = [
        ...currentStops.map((point) => [point.latitude, point.longitude]),
        ...draftPath.map((point) => [point.latitud, point.longitud])
      ];
      if (bounds.length > 0) map.fitBounds(bounds, { padding: [32, 32], maxZoom: 16 });
      const missingStops = rows.length - currentStops.length;
      setStatus(`${route.code} · ${direction}: ${currentStops.length} paradas de referencia${loaded ? ` · trazado guardado (${draftPath.length} puntos)` : ' · sin trazado vial guardado'}.`);
      if (missingStops > 0) {
        setWarning(`${missingStops} parada(s) de esta dirección no tienen coordenadas; no se muestran.`);
      }
    } catch (error) {
      setStatus('No fue posible cargar el trazado guardado.');
      setWarning(error.message);
    }
  }

  function refreshRouteOptions() {
    const category = categorySelect.value;
    const available = routes.filter((route) => (!category || route.category === category) && route.file);
    routeSelect.replaceChildren(new Option('Selecciona una ruta', ''));
    available.forEach((route) => routeSelect.add(new Option(`${route.code} · ${route.category}`, route.code)));
    void refreshRoute();
  }

  map.on('click', (event) => {
    if (!selectedRoute() || !directionSelect.value) return;
    draftPath.push({
      latitud: event.latlng.lat,
      longitud: event.latlng.lng
    });
    renderPath();
    setStatus(`${selectedRoute().code} · ${directionSelect.value}: ${draftPath.length} punto(s) del trazado en edición.`);
    setWarning('');
  });

  categorySelect.addEventListener('change', refreshRouteOptions);
  routeSelect.addEventListener('change', () => void refreshRoute());
  directionSelect.addEventListener('change', () => void refreshDirection());
  undoButton.addEventListener('click', () => {
    draftPath.pop();
    renderPath();
  });
  clearButton.addEventListener('click', () => {
    draftPath = [];
    renderPath();
    setStatus('Trazado en edición limpio. Haz clic en el mapa para empezar otra vez.');
  });
  loadButton.addEventListener('click', () => void refreshDirection());
  saveButton.addEventListener('click', async () => {
    const route = selectedRoute();
    const direction = directionSelect.value;
    if (!route || !direction || draftPath.length < 2) return;
    saveButton.disabled = true;
    setStatus('Guardando el trazado…');
    try {
      const response = await fetch(
        `/admin/mapa-rutas/api/rutas/${encodeURIComponent(route.code)}/${encodeURIComponent(direction)}`,
        {
          method: 'PUT',
          credentials: 'same-origin',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ coordenadas: draftPath })
        }
      );
      if (!response.ok) {
        const details = await response.text();
        throw new Error(details || `El servidor respondió ${response.status}.`);
      }
      const result = await response.json();
      draftPath = result.coordenadas.map((point) => ({
        latitud: point.latitud,
        longitud: point.longitud
      }));
      savedPathExists = true;
      loadButton.disabled = false;
      deleteButton.disabled = false;
      renderPath();
      setStatus(`Trazado de ${route.code} · ${direction} guardado con ${draftPath.length} puntos.`);
      setWarning('');
    } catch (error) {
      setStatus('No se pudo guardar el trazado.');
      setWarning(error.message);
      saveButton.disabled = draftPath.length < 2;
    }
  });
  deleteButton.addEventListener('click', async () => {
    const route = selectedRoute();
    const direction = directionSelect.value;
    if (!route || !direction || !savedPathExists
        || !window.confirm(`¿Eliminar el trazado guardado de ${route.code} · ${direction}?`)) return;
    try {
      const response = await fetch(
        `/admin/mapa-rutas/api/rutas/${encodeURIComponent(route.code)}/${encodeURIComponent(direction)}`,
        { method: 'DELETE', credentials: 'same-origin' }
      );
      if (!response.ok) throw new Error(`El servidor respondió ${response.status}.`);
      draftPath = [];
      savedPathExists = false;
      renderPath();
      loadButton.disabled = true;
      deleteButton.disabled = true;
      setStatus(`Se eliminó el trazado guardado de ${route.code} · ${direction}.`);
    } catch (error) {
      setStatus('No se pudo eliminar el trazado.');
      setWarning(error.message);
    }
  });

  refreshRouteOptions();
})();

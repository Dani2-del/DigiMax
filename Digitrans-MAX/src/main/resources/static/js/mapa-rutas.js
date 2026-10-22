(() => {
  const palette = ['#ed4f24', '#087e8b', '#34764a', '#7356a5'];
  const routes = window.TRANSCARIBE_RUTAS || [];

  const categorySelect = document.getElementById('routeCategory');
  const routeSelect = document.getElementById('routeCode');
  const directionsField = document.getElementById('routeDirections');
  const status = document.getElementById('routeMapStatus');
  const warning = document.getElementById('routeMapWarning');
  const map = L.map('routeMap').setView([10.4, -75.5], 12);
  const routeLayers = L.layerGroup().addTo(map);
  const csvCache = new Map();
  const geometryCache = new Map();
  let directionsSignature = '';

  L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
    maxZoom: 19,
    attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
  }).addTo(map);

  function parseCsv(text) {
    const rows = [];
    let row = [];
    let field = '';
    let quoted = false;

    for (let i = 0; i < text.length; i += 1) {
      const char = text[i];
      if (quoted) {
        if (char === '"' && text[i + 1] === '"') {
          field += '"';
          i += 1;
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

  async function loadRouteRows(route) {
    if (csvCache.has(route.code)) return csvCache.get(route.code);
    const response = await fetch(`/Rutas/${route.file.split('/').map(encodeURIComponent).join('/')}`);
    if (!response.ok) {
      throw new Error(`No se pudo leer el archivo de la ruta ${route.code} (${response.status}).`);
    }
    const rows = parseCsv(await response.text());
    csvCache.set(route.code, rows);
    return rows;
  }

  async function loadRouteGeometries(code) {
    if (geometryCache.has(code)) return geometryCache.get(code);
    const response = await fetch(`/mapa-rutas/api/rutas/${encodeURIComponent(code)}`);
    if (!response.ok) {
      throw new Error(`No se pudieron cargar los trazados guardados (${response.status}).`);
    }
    const rows = await response.json();
    const geometries = new Map(rows.map((row) => [row.sentido, row.coordenadas]));
    geometryCache.set(code, geometries);
    return geometries;
  }

  function currentRoute() {
    return routes.find((route) => route.code === routeSelect.value);
  }

  function refreshRouteOptions(preferredCode) {
    const category = categorySelect.value;
    const available = routes.filter((route) => !category || route.category === category);
    routeSelect.replaceChildren(new Option('Selecciona una ruta', ''));
    available.forEach((route) => {
      const label = route.file ? `${route.code} · ${route.category}` : `${route.code} · pendiente de coordenadas`;
      routeSelect.add(new Option(label, route.code));
    });

    if (preferredCode && available.some((route) => route.code === preferredCode)) {
      routeSelect.value = preferredCode;
    } else {
      routeSelect.value = '';
    }
    void renderSelectedRoute();
  }

  function selectedDirections() {
    return [...directionsField.querySelectorAll('input:checked')].map((input) => input.value);
  }

  function setNotice(message) {
    warning.textContent = message;
    warning.hidden = !message;
  }

  function popupContent(route, direction, point, number) {
    const container = document.createElement('div');
    const title = document.createElement('strong');
    title.textContent = `${route.code} · ${direction} · Parada ${number}`;
    const name = document.createElement('div');
    name.textContent = point.Ubicacion_del_Paradero || point.Paraderos || 'Parada sin nombre';
    container.append(title, name);
    return container;
  }

  function drawDirection(route, direction, points, path, color) {
    if (path.length > 1) {
      L.polyline(path.map((point) => [point.latitud, point.longitud]), {
        color,
        weight: 5,
        opacity: 0.85
      }).addTo(routeLayers);
    }

    points.forEach((point, index) => {
      const icon = L.divIcon({
        className: '',
        html: `<span class="route-stop-marker" style="background:${color}">${index + 1}</span>`,
        iconSize: [25, 25],
        iconAnchor: [12, 12]
      });
      L.marker([point.latitude, point.longitude], { icon })
        .bindPopup(popupContent(route, direction, point, index + 1))
        .addTo(routeLayers);
    });
    return points.map((point) => [point.latitude, point.longitude]);
  }

  function configureDirections(directions) {
    const signature = directions.join('|');
    if (signature === directionsSignature) return;
    directionsSignature = signature;
    directionsField.replaceChildren();
    directionsField.hidden = directions.length === 0;
    if (directions.length === 0) return;

    const legend = document.createElement('legend');
    legend.textContent = 'Sentidos visibles';
    directionsField.append(legend);
    directions.forEach((direction, index) => {
      const label = document.createElement('label');
      label.className = 'route-direction-option';
      const checkbox = document.createElement('input');
      checkbox.type = 'checkbox';
      checkbox.value = direction;
      checkbox.checked = true;
      checkbox.addEventListener('change', () => void renderSelectedRoute());
      const swatch = document.createElement('span');
      swatch.className = 'route-direction-swatch';
      swatch.style.backgroundColor = palette[index % palette.length];
      label.append(checkbox, swatch, document.createTextNode(direction));
      directionsField.append(label);
    });
  }

  async function renderSelectedRoute() {
    routeLayers.clearLayers();
    setNotice('');
    const route = currentRoute();
    if (!route) {
      directionsField.replaceChildren();
      directionsField.hidden = true;
      directionsSignature = '';
      status.textContent = 'Selecciona una ruta para ver sus paradas.';
      return;
    }
    if (!route.file) {
      directionsField.replaceChildren();
      directionsField.hidden = true;
      directionsSignature = '';
      status.textContent = `${route.code} todavía no tiene un archivo de paradas con coordenadas.`;
      setNotice('Esta ruta se podrá agregar al mapa cuando esté listo su archivo de coordenadas.');
      return;
    }

    status.textContent = `Cargando paradas de ${route.code}…`;
    try {
      const rows = (await loadRouteRows(route))
        .filter((row) => row.Codigo_Rutas.trim().toUpperCase() === route.code);
      if (rows.length === 0) {
        throw new Error(`El archivo no contiene registros para ${route.code}.`);
      }
      const directions = [...new Set(rows.map((row) => row.Sentido.trim().toUpperCase()).filter(Boolean))];
      configureDirections(directions);
      let geometries = new Map();
      let geometryError = '';
      try {
        geometries = await loadRouteGeometries(route.code);
      } catch (error) {
        geometryError = error.message;
      }
      const visibleDirections = new Set(selectedDirections());
      const boundsPoints = [];
      let missingCoordinates = 0;
      let visibleStops = 0;
      const missingPaths = [];

      directions.forEach((direction, directionIndex) => {
        if (!visibleDirections.has(direction)) return;
        const directionRows = rows
          .filter((row) => row.Sentido.trim().toUpperCase() === direction)
          .sort((a, b) => Number(a.Gid) - Number(b.Gid));
        const validPoints = [];
        directionRows.forEach((row) => {
          if (!row.lat.trim() || !row.lng.trim()) {
            missingCoordinates += 1;
            return;
          }
          const latitude = Number(row.lat);
          const longitude = Number(row.lng);
          if (!Number.isFinite(latitude) || !Number.isFinite(longitude)
              || latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180
              || (latitude === 0 && longitude === 0)) {
            missingCoordinates += 1;
            return;
          }
          validPoints.push({ ...row, latitude, longitude });
        });
        visibleStops += validPoints.length;
        const path = geometries.get(direction);
        if (path && path.length >= 2) {
          boundsPoints.push(...path.map((point) => [point.latitud, point.longitud]));
        } else if (validPoints.length > 0) {
          missingPaths.push(direction);
        }
        boundsPoints.push(...drawDirection(
          route,
          direction,
          validPoints,
          path || [],
          palette[directionIndex % palette.length]
        ));
      });

      status.textContent = `${route.code}: ${visibleStops} paradas con coordenadas visibles en ${visibleDirections.size} sentido(s).`;
      const notices = [];
      if (geometryError) {
        notices.push(`No se pudieron consultar los trazados guardados: ${geometryError}`);
      }
      if (missingCoordinates > 0) {
        notices.push(`${route.code}: ${missingCoordinates} parada(s) no tienen coordenadas válidas en el CSV y no se muestran.`);
      }
      if (missingPaths.length > 0) {
        notices.push(`${route.code}: falta dibujar el trazado vial de ${missingPaths.join(', ')}; se muestran solo las paradas.`);
      }
      setNotice(notices.join(' '));
      if (boundsPoints.length > 0) {
        map.fitBounds(boundsPoints, { padding: [32, 32], maxZoom: 15 });
      }
    } catch (error) {
      status.textContent = 'No fue posible cargar la ruta.';
      setNotice(error.message);
    }
  }

  categorySelect.addEventListener('change', () => refreshRouteOptions());
  routeSelect.addEventListener('change', () => void renderSelectedRoute());
  refreshRouteOptions();
})();

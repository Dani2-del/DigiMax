(function () {
    const contenido = document.getElementById('analyticsContent');
    const carga = document.getElementById('analyticsLoading');
    const error = document.getElementById('analyticsError');
    const numberFormat = new Intl.NumberFormat('es-CO');
    const currencyFormat = new Intl.NumberFormat('es-CO', {
        style: 'currency',
        currency: 'COP',
        maximumFractionDigits: 0
    });
    const compactCurrencyFormat = new Intl.NumberFormat('es-CO', {
        notation: 'compact',
        maximumFractionDigits: 1
    });
    const roleLabels = {
        ROLE_USER: 'Usuarios',
        ROLE_ADMIN: 'Administradores',
        ROLE_CONDUCTOR: 'Conductores'
    };
    const roleColors = ['#c45a24', '#4777c8', '#1a9b84', '#d9a441'];
    const transactionLabels = {
        Pasaje: 'Pasajes',
        Recarga: 'Recargas',
        Otros: 'Otros'
    };
    const transactionColors = ['#e87632', '#4d82ce', '#aab3bd'];

    function element(tag, className, text) {
        const node = document.createElement(tag);
        if (className) node.className = className;
        if (text !== undefined) node.textContent = text;
        return node;
    }

    function renderHorizontalChart(containerId, points, valueKey, formatter, colorSet) {
        const container = document.getElementById(containerId);
        container.replaceChildren();
        if (!points || points.length === 0) {
            container.appendChild(element('p', 'analytics-empty', 'Todavía no hay datos para mostrar.'));
            return;
        }

        const max = Math.max(...points.map(point => Number(point[valueKey]) || 0), 1);
        points.forEach(function (point, index) {
            const row = element('div', 'horizontal-chart-row');
            const header = element('div', 'horizontal-chart-labels');
            header.append(
                element('span', 'horizontal-chart-label', point.label),
                element('strong', 'horizontal-chart-value', formatter(point[valueKey]))
            );
            const track = element('div', 'horizontal-chart-track');
            const bar = element('span', 'horizontal-chart-bar');
            bar.style.width = Math.max(2, (Number(point[valueKey]) / max) * 100) + '%';
            bar.style.backgroundColor = colorSet[index % colorSet.length];
            track.appendChild(bar);
            row.append(header, track);
            container.appendChild(row);
        });
    }

    function renderRoles(points) {
        const container = document.getElementById('rolesChart');
        container.replaceChildren();
        if (!points || points.length === 0) {
            container.appendChild(element('p', 'analytics-empty', 'Todavía no hay datos para mostrar.'));
            return;
        }

        const total = points.reduce((sum, point) => sum + Number(point.cantidad || 0), 0);
        let consumed = 0;
        const stops = points.map(function (point, index) {
            const start = consumed;
            consumed += total ? Number(point.cantidad || 0) / total * 100 : 0;
            return roleColors[index % roleColors.length] + ' ' + start + '% ' + consumed + '%';
        });
        const donut = element('div', 'role-donut');
        donut.style.background = total
            ? 'conic-gradient(' + stops.join(',') + ')'
            : '#e8edf2';
        donut.setAttribute('role', 'img');
        donut.setAttribute('aria-label', 'Distribución de usuarios por tipo de cuenta');
        donut.appendChild(element('strong', '', numberFormat.format(total)));

        const legend = element('div', 'role-legend');
        points.forEach(function (point, index) {
            const item = element('div', 'role-legend-item');
            const labelWrap = element('span', 'role-legend-name');
            const swatch = element('span', 'role-legend-swatch');
            swatch.style.backgroundColor = roleColors[index % roleColors.length];
            labelWrap.append(swatch, document.createTextNode(
                roleLabels[point.label] || point.label || 'Sin especificar'));
            item.append(labelWrap, element('strong', '', numberFormat.format(point.cantidad)));
            legend.appendChild(item);
        });
        container.append(donut, legend);
    }

    function renderTransactionCategories(points) {
        const container = document.getElementById('transactionCategoriesChart');
        container.replaceChildren();
        if (!points || points.length === 0) {
            container.appendChild(element('p', 'analytics-empty', 'Todavía no hay transacciones para mostrar.'));
            return;
        }

        const total = points.reduce((sum, point) => sum + Number(point.cantidad || 0), 0);
        let consumed = 0;
        const stops = points.map(function (point, index) {
            const start = consumed;
            consumed += total ? Number(point.cantidad || 0) / total * 100 : 0;
            return transactionColors[index % transactionColors.length] + ' ' + start + '% ' + consumed + '%';
        });
        const chart = element('div', 'role-donut transaction-donut');
        chart.style.background = total
            ? 'conic-gradient(' + stops.join(',') + ')'
            : '#e8edf2';
        chart.setAttribute('role', 'img');
        chart.setAttribute('aria-label', 'Distribución de pasajes y recargas');
        chart.appendChild(element('strong', '', numberFormat.format(total)));

        const legend = element('div', 'role-legend');
        points.forEach(function (point, index) {
            const count = Number(point.cantidad || 0);
            const percent = total ? Math.round(count / total * 100) : 0;
            const item = element('div', 'role-legend-item');
            const name = element('span', 'role-legend-name');
            const swatch = element('span', 'role-legend-swatch');
            swatch.style.backgroundColor = transactionColors[index % transactionColors.length];
            name.append(swatch, document.createTextNode(
                transactionLabels[point.label] || point.label || 'Sin clasificar'));
            const summary = element('span', 'role-legend-summary');
            summary.append(
                element('strong', '', numberFormat.format(count)),
                element('span', '', percent + '%')
            );
            item.append(name, summary);
            legend.appendChild(item);
        });
        container.append(chart, legend);
    }

    function renderMonthly(points) {
        const container = document.getElementById('monthlyChart');
        container.replaceChildren();
        if (!points || points.length === 0) {
            container.appendChild(element('p', 'analytics-empty', 'No hay transacciones en los últimos 12 meses.'));
            return;
        }

        const max = Math.max(...points.map(point => Number(point.monto) || 0), 1);
        const columns = element('div', 'monthly-chart-columns');
        points.forEach(function (point) {
            const amount = Number(point.monto) || 0;
            const column = element('div', 'monthly-chart-column');
            const barArea = element('div', 'monthly-chart-bar-area');
            const bar = element('span', 'monthly-chart-bar');
            const description = point.mes + ': ' + currencyFormat.format(amount)
                + ' · ' + numberFormat.format(point.cantidad) + ' transacciones';
            const shortValue = element('span', 'monthly-chart-value',
                amount > 0 ? compactCurrencyFormat.format(amount) : '');
            const height = amount > 0 ? Math.max(3, amount / max * 100) : 0;
            bar.style.height = height + '%';
            bar.title = description;
            column.setAttribute('role', 'img');
            column.setAttribute('aria-label', description);
            barArea.append(shortValue, bar);
            const date = new Date(point.mes + '-01T12:00:00');
            const label = new Intl.DateTimeFormat('es-CO', { month: 'short' }).format(date)
                .replace('.', '');
            column.append(barArea, element('span', 'monthly-chart-label', label));
            columns.appendChild(column);
        });
        container.appendChild(columns);
    }

    function updatePage(stats) {
        document.getElementById('kpiUsers').textContent = numberFormat.format(stats.totalUsuarios);
        document.getElementById('kpiTransactions').textContent = numberFormat.format(stats.totalTransacciones);
        document.getElementById('kpiAssignments').textContent = numberFormat.format(stats.totalAsignaciones);
        document.getElementById('kpiNotices').textContent = numberFormat.format(stats.totalAvisos);
        renderMonthly(stats.transaccionesPorMes);
        renderRoles(stats.usuariosPorRol);
        renderTransactionCategories(stats.transaccionesPorTipo);
        renderHorizontalChart('scheduleStatesChart', stats.horariosPorEstado,
            'cantidad', numberFormat.format.bind(numberFormat), ['#1a9b84', '#e87632', '#4777c8', '#a17ac4']);
        renderHorizontalChart('topRoutesChart', stats.rutasMasProgramadas,
            'cantidad', numberFormat.format.bind(numberFormat), ['#e87632', '#4777c8', '#27a58a']);
        document.getElementById('analyticsUpdated').textContent =
            'Actualizado: ' + new Intl.DateTimeFormat('es-CO', {
                dateStyle: 'medium',
                timeStyle: 'short'
            }).format(new Date());
    }

    async function loadCharts() {
        carga.hidden = false;
        error.hidden = true;
        contenido.hidden = true;
        try {
            const response = await fetch('/admin/graficas/api', {
                credentials: 'same-origin',
                headers: { Accept: 'application/json' }
            });
            if (!response.ok) {
                throw new Error('El servidor respondió con estado ' + response.status + '.');
            }
            updatePage(await response.json());
            contenido.hidden = false;
        } catch (exception) {
            console.error('No se pudieron cargar las estadísticas del panel.', exception);
            error.querySelector('span').textContent =
                'No se pudieron cargar las estadísticas. Revisa la conexión e inténtalo de nuevo.';
            error.hidden = false;
        } finally {
            carga.hidden = true;
        }
    }

    document.getElementById('refreshCharts').addEventListener('click', loadCharts);
    document.getElementById('retryCharts').addEventListener('click', loadCharts);
    loadCharts();
})();

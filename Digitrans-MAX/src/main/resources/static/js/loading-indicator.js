(function () {
    const overlay = document.getElementById('global-loading-overlay');
    if (!overlay) {
        return;
    }

    const SHOW_DELAY_MS = 180;
    const MINIMUM_VISIBLE_MS = 350;
    let activeActions = 0;
    let shownAt = 0;
    let showTimer;
    let hideTimer;

    function showOverlay() {
        if (activeActions === 0) {
            return;
        }
        overlay.classList.add('is-visible');
        overlay.setAttribute('aria-hidden', 'false');
        shownAt = Date.now();
    }

    function beginLoading() {
        activeActions += 1;
        clearTimeout(hideTimer);

        if (activeActions === 1) {
            showTimer = setTimeout(showOverlay, SHOW_DELAY_MS);
        }

        let completed = false;
        return function finishLoading() {
            if (completed) {
                return;
            }
            completed = true;
            activeActions = Math.max(0, activeActions - 1);

            if (activeActions > 0) {
                return;
            }

            clearTimeout(showTimer);
            if (!overlay.classList.contains('is-visible')) {
                return;
            }

            const remainingVisibleTime = Math.max(0, MINIMUM_VISIBLE_MS - (Date.now() - shownAt));
            hideTimer = setTimeout(function () {
                if (activeActions === 0) {
                    overlay.classList.remove('is-visible');
                    overlay.setAttribute('aria-hidden', 'true');
                }
            }, remainingVisibleTime);
        };
    }

    function isInternalNavigation(link) {
        if (!link || link.hasAttribute('download') || link.target && link.target !== '_self') {
            return false;
        }

        let destination;
        try {
            destination = new URL(link.href, window.location.href);
        } catch (error) {
            return false;
        }

        if (destination.origin !== window.location.origin) {
            return false;
        }

        return !(destination.pathname === window.location.pathname
            && destination.search === window.location.search
            && destination.hash);
    }

    document.addEventListener('click', function (event) {
        const link = event.target.closest('a[href]');
        if (link && !event.defaultPrevented && isInternalNavigation(link)) {
            beginLoading();
            return;
        }

        const button = event.target.closest('button[onclick], [role="button"][onclick]');
        if (button && !event.defaultPrevented
                && /\b(?:window\.)?location(?:\.href)?\s*(?:=|\.assign\(|\.replace\()/i.test(button.getAttribute('onclick'))) {
            beginLoading();
        }
    });

    document.addEventListener('submit', function (event) {
        if (!event.defaultPrevented && event.target.method !== 'dialog') {
            beginLoading();
        }
    });

    if (typeof window.fetch === 'function') {
        const originalFetch = window.fetch;
        window.fetch = function () {
            const finishLoading = beginLoading();
            try {
                return originalFetch.apply(this, arguments).finally(finishLoading);
            } catch (error) {
                finishLoading();
                throw error;
            }
        };

        window.transcaribeDownloadFile = async function (url) {
            const finishLoading = beginLoading();
            let objectUrl;

            try {
                const response = await originalFetch.call(window, url, { credentials: 'same-origin' });
                if (!response.ok) {
                    throw new Error('El servidor respondió con estado ' + response.status + '.');
                }

                const blob = await response.blob();
                const contentDisposition = response.headers.get('Content-Disposition') || '';
                const encodedFilename = contentDisposition.match(/filename\*\s*=\s*UTF-8''([^;]+)/i);
                const plainFilename = contentDisposition.match(/filename\s*=\s*"([^"]+)"|filename\s*=\s*([^;]+)/i);
                let filename = 'reporte.xlsx';

                if (encodedFilename) {
                    filename = decodeURIComponent(encodedFilename[1].trim());
                } else if (plainFilename) {
                    filename = (plainFilename[1] || plainFilename[2]).trim();
                }

                filename = filename.replace(/[\\/:*?"<>|]/g, '_');
                objectUrl = URL.createObjectURL(blob);
                const link = document.createElement('a');
                link.href = objectUrl;
                link.download = filename;
                link.style.display = 'none';
                document.body.appendChild(link);
                link.click();
                link.remove();
            } catch (error) {
                console.error('No se pudo descargar el reporte.', error);
                window.alert('No se pudo generar o descargar el reporte. Inténtalo de nuevo.');
            } finally {
                finishLoading();
                if (objectUrl) {
                    setTimeout(function () {
                        URL.revokeObjectURL(objectUrl);
                    }, 1000);
                }
            }
        };
    }

    if (window.XMLHttpRequest) {
        const originalSend = XMLHttpRequest.prototype.send;
        XMLHttpRequest.prototype.send = function () {
            const finishLoading = beginLoading();
            this.addEventListener('loadend', finishLoading, { once: true });
            try {
                return originalSend.apply(this, arguments);
            } catch (error) {
                finishLoading();
                throw error;
            }
        };
    }

    window.addEventListener('pageshow', function (event) {
        if (event.persisted) {
            activeActions = 0;
            clearTimeout(showTimer);
            clearTimeout(hideTimer);
            overlay.classList.remove('is-visible');
            overlay.setAttribute('aria-hidden', 'true');
        }
    });
})();

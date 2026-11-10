package com.Servicio.Orquestador.controller;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

/** Renders branded errors for browser requests and keeps API errors machine-readable. */
@Controller
public class OrquestadorErrorController implements ErrorController {

    @RequestMapping("/error")
    public ResponseEntity<String> error(HttpServletRequest request) {
        Object statusValue = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        int statusCode = statusValue instanceof Integer code ? code : HttpStatus.INTERNAL_SERVER_ERROR.value();
        HttpStatus status = HttpStatus.resolve(statusCode);
        String errorName = status == null ? "Error" : status.getReasonPhrase();
        String originalPath = String.valueOf(request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI));

        if (originalPath.startsWith("/api/")) {
            String json = "{\"status\":" + statusCode + ",\"error\":\"" + errorName + "\"}";
            return ResponseEntity.status(statusCode).contentType(MediaType.APPLICATION_JSON).body(json);
        }

        String title = titleFor(statusCode);
        String description = descriptionFor(statusCode);
        String html = """
                <!doctype html>
                <html lang="es">
                <head>
                  <meta charset="utf-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1">
                  <meta name="theme-color" content="#080808">
                  <title>%d · Orquestador Digitrans</title>
                  <style>
                    :root { color-scheme: dark; font-family: Inter, "Segoe UI", system-ui, sans-serif; background: #080808; color: #f5f5f5; }
                    * { box-sizing: border-box; }
                    body { min-height: 100vh; margin: 0; display: grid; place-items: center; padding: 24px; background: radial-gradient(ellipse at 50%% 0%%, #292929 0, transparent 48%%), #080808; }
                    main { width: min(100%%, 700px); padding: clamp(32px, 7vw, 64px); overflow: hidden; position: relative; text-align: center; border: 1px solid #393939; border-top: 4px solid #f5f5f5; border-radius: 26px; background: #111; box-shadow: 0 28px 90px #000; animation: enter .55s cubic-bezier(.2,.75,.25,1) both; }
                    main::after { content: ""; position: absolute; width: 230px; height: 230px; right: -115px; top: -145px; border: 1px solid #ffffff20; border-radius: 50%%; box-shadow: 0 0 0 22px #ffffff08, 0 0 0 44px #ffffff05; pointer-events: none; }
                    .brand { display: inline-flex; align-items: center; gap: 10px; color: #f5f5f5; font-size: 14px; font-weight: 850; letter-spacing: .04em; }
                    .mark { width: 34px; height: 34px; display: grid; place-items: center; border-radius: 11px; background: #f5f5f5; color: #111; font-size: 20px; font-weight: 950; }
                    .code { margin: 35px 0 0; font-size: clamp(88px, 20vw, 132px); font-weight: 900; line-height: .95; letter-spacing: -.08em; }
                    h1 { margin: 20px 0 8px; font-size: clamp(24px, 5vw, 34px); letter-spacing: -.04em; }
                    p { margin: 0 auto; max-width: 440px; color: #bcbcbc; line-height: 1.65; }
                    .actions { display: flex; justify-content: center; margin-top: 30px; }
                    a { min-height: 46px; display: inline-flex; align-items: center; justify-content: center; padding: 0 20px; border: 1px solid #f5f5f5; border-radius: 11px; background: #f5f5f5; color: #111; font-weight: 800; text-decoration: none; transition: transform .2s, background .2s, color .2s; }
                    a:hover { transform: translateY(-2px); background: #111; color: #fff; }
                    a:focus-visible { outline: 3px solid #aaa; outline-offset: 4px; }
                    @keyframes enter { from { opacity: 0; transform: translateY(16px) scale(.985); } to { opacity: 1; transform: translateY(0) scale(1); } }
                    @media (prefers-reduced-motion: reduce) { *, *::before, *::after { animation-duration: .01ms !important; transition-duration: .01ms !important; } }
                  </style>
                </head>
                <body>
                  <main>
                    <div class="brand"><span class="mark" aria-hidden="true">D</span><span>Orquestador Digitrans</span></div>
                    <div class="code" aria-label="Código de error %d">%d</div>
                    <h1>%s</h1>
                    <p>%s</p>
                    <div class="actions"><a href="/login.html">Volver al Orquestador</a></div>
                  </main>
                </body>
                </html>
                """.formatted(statusCode, statusCode, statusCode, title, description);

        return ResponseEntity.status(statusCode).contentType(MediaType.TEXT_HTML).body(html);
    }

    private static String titleFor(int code) {
        return switch (code) {
            case 401 -> "Sesión requerida";
            case 403 -> "Acceso restringido";
            case 404 -> "Página no encontrada";
            case 502, 503 -> "Servicio temporalmente no disponible";
            default -> "Ocurrió un error";
        };
    }

    private static String descriptionFor(int code) {
        return switch (code) {
            case 401 -> "Inicia sesión con una cuenta administradora para continuar.";
            case 403 -> "Esta sección está disponible únicamente para administradores.";
            case 404 -> "La dirección que buscas no existe en el Orquestador.";
            case 502, 503 -> "No fue posible validar el acceso con Acceso. Inténtalo nuevamente más tarde.";
            default -> "No se pudo completar la solicitud. Inténtalo de nuevo.";
        };
    }
}

package com.Servicio.Correo.api;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.Map;

@Controller
public class CorreoErrorController implements ErrorController {

    private final String accesoUrl;

    public CorreoErrorController(@Value("${app.acceso.base-url:http://localhost:8080}") String accesoUrl) {
        this.accesoUrl = accesoUrl;
    }

    @RequestMapping("/error")
    public ResponseEntity<?> error(HttpServletRequest request) {
        Object value = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        int code = value instanceof Integer status && status >= 400 && status <= 599
                ? status : HttpStatus.INTERNAL_SERVER_ERROR.value();
        String title = switch (code) {
            case 400 -> "Solicitud inválida";
            case 401, 403 -> "Acceso restringido";
            case 404 -> "Página no encontrada";
            case 405 -> "Acción no disponible";
            case 503 -> "Servicio temporalmente no disponible";
            default -> "Ocurrió un problema";
        };
        String message = code == 404
                ? "Esta dirección no ofrece una página para navegar."
                : "No pudimos procesar la solicitud. Inténtalo de nuevo más tarde.";
        Object originalPath = request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
        if (originalPath instanceof String path && path.startsWith("/api/")) {
            return ResponseEntity.status(code).contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("status", code, "error", title, "message", message));
        }

        String html = """
                <!doctype html><html lang="es"><head><meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <title>%d · Correo Digitrans</title>
                <style>
                :root{color-scheme:dark}*{box-sizing:border-box}body{margin:0;min-height:100vh;display:grid;place-items:center;padding:24px;background:radial-gradient(ellipse at 50%% 0%%,#292929 0,transparent 48%%),#080808;color:#f5f5f5;font:16px Inter,"Segoe UI",system-ui,sans-serif}
                main{width:min(100%%,560px);padding:44px 32px;text-align:center;border:1px solid #393939;border-top:4px solid #f5f5f5;border-radius:22px;background:#111;box-shadow:0 24px 70px #000}
                .brand{font-weight:800;letter-spacing:.04em}.code{margin:30px 0 8px;color:#f5f5f5;font-size:clamp(72px,18vw,112px);font-weight:900;line-height:1}
                h1{margin:12px 0;font-size:28px}p{color:#bcbcbc;line-height:1.6}a{display:inline-block;margin-top:16px;padding:12px 18px;border:1px solid #f5f5f5;border-radius:10px;background:#f5f5f5;color:#111;text-decoration:none;font-weight:750}
                main{animation:card-in .7s cubic-bezier(.2,.75,.25,1) both}.brand,.code,h1,p,a{animation:content-in .55s both}.brand{animation-delay:.12s}.code{animation-delay:.2s}h1{animation-delay:.28s}p{animation-delay:.36s}a{animation-delay:.44s;transition:transform .2s,background .2s,color .2s,box-shadow .2s}a:hover{transform:translateY(-3px);background:#111;color:#fff;box-shadow:0 8px 24px #fff2}a:focus-visible{outline:3px solid #aaa;outline-offset:4px}
                @keyframes card-in{from{opacity:0;transform:translateY(18px) scale(.98)}to{opacity:1;transform:translateY(0) scale(1)}}@keyframes content-in{from{opacity:0;transform:translateY(8px)}to{opacity:1;transform:translateY(0)}}
                @media(prefers-reduced-motion:reduce){*,*::before,*::after{animation-duration:.01ms!important;animation-delay:0ms!important;transition-duration:.01ms!important}}
                </style></head><body><main><div class="brand">Correo Digitrans</div>
                <div class="code">%d</div><h1>%s</h1><p>%s</p>
                <a href="%s/">Volver al inicio</a></main></body></html>
                """.formatted(code, code, title, message, accesoUrl.replaceAll("/+$", ""));
        return ResponseEntity.status(code).contentType(MediaType.TEXT_HTML).body(html);
    }
}

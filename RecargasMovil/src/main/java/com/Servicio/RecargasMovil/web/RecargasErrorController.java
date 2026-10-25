package com.Servicio.RecargasMovil.web;

import java.util.Map;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.boot.webmvc.error.ErrorController;

@Controller
@RequestMapping("/error")
public class RecargasErrorController implements ErrorController {

    private final String accesoUrl;

    public RecargasErrorController(@Value("${app.acceso.base-url}") String accesoUrl) {
        this.accesoUrl = accesoUrl;
    }

    @RequestMapping
    public Object handleError(HttpServletRequest request, HttpServletResponse response, Model model) {
        Object statusAttribute = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        int status = statusAttribute instanceof Integer code ? code : response.getStatus();
        return renderError(status, request, response, model);
    }

    @RequestMapping("/{status}")
    public Object handleStatus(
            @PathVariable int status,
            HttpServletRequest request,
            HttpServletResponse response,
            Model model) {
        return renderError(status, request, response, model);
    }

    private Object renderError(
            int requestedStatus, HttpServletRequest request, HttpServletResponse response, Model model) {
        int status = requestedStatus >= 400 && requestedStatus <= 599 ? requestedStatus : 500;
        ErrorContent content = errorContent(status);
        response.setStatus(status);

        if (isApiError(request)) {
            return ResponseEntity.status(status).body(Map.of(
                    "status", status,
                    "error", content.title(),
                    "message", content.message()));
        }

        model.addAttribute("statusCode", status);
        model.addAttribute("errorTitle", content.title());
        model.addAttribute("errorMessage", content.message());
        model.addAttribute("accesoUrl", accesoUrl);
        model.addAttribute("moduleName", "Recargas móviles Digitrans");
        return "error/general";
    }

    private boolean isApiError(HttpServletRequest request) {
        Object errorUri = request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
        Object forwardUri = request.getAttribute(RequestDispatcher.FORWARD_REQUEST_URI);
        return errorUri instanceof String errorPath && errorPath.startsWith("/api/")
                || forwardUri instanceof String forwardPath && forwardPath.startsWith("/api/");
    }

    private ErrorContent errorContent(int status) {
        return switch (status) {
            case 400 -> new ErrorContent("Solicitud inválida", "Revisa los datos enviados e inténtalo de nuevo.");
            case 401 -> new ErrorContent("Sesión requerida", "Inicia sesión para continuar con esta solicitud.");
            case 403 -> new ErrorContent("Acceso restringido", "No tienes permiso para consultar esta sección.");
            case 404 -> new ErrorContent("Página no encontrada", "No encontramos la página que estás buscando.");
            case 405 -> new ErrorContent("Acción no disponible", "Esta acción no está disponible para esta dirección.");
            case 503 -> new ErrorContent("Servicio no disponible", "Las recargas no están disponibles por el momento.");
            default -> new ErrorContent("Ocurrió un problema", "No pudimos completar la solicitud. Intenta nuevamente más tarde.");
        };
    }

    private record ErrorContent(String title, String message) {
    }
}

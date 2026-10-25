package com.transcaribe.transcaribe.Controller;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import com.transcaribe.transcaribe.Model.SistemaTransporte;

@Controller
@RequestMapping("/error")
public class TransportErrorController implements ErrorController {

    @RequestMapping
    public String handleError(HttpServletRequest request, HttpServletResponse response, Model model) {
        Object statusAttribute = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        int status = statusAttribute instanceof Integer code ? code : response.getStatus();
        return renderError(status, request, response, model);
    }

    @RequestMapping("/{status}")
    public String handleStatus(
            @PathVariable int status,
            HttpServletRequest request,
            HttpServletResponse response,
            Model model) {
        return renderError(status, request, response, model);
    }

    private String renderError(
            int requestedStatus, HttpServletRequest request, HttpServletResponse response, Model model) {
        int status = switch (requestedStatus) {
            case HttpServletResponse.SC_FORBIDDEN,
                    HttpServletResponse.SC_NOT_FOUND,
                    HttpServletResponse.SC_INTERNAL_SERVER_ERROR -> requestedStatus;
            default -> HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
        };
        response.setStatus(status);

        Object selectedSystem = request.getSession(false) == null
                ? null
                : request.getSession(false).getAttribute(SistemaTransporte.SESSION_ATTRIBUTE);
        SistemaTransporte system = selectedSystem instanceof SistemaTransporte selected
                ? selected
                : SistemaTransporte.TRANSCARIBE;
        model.addAttribute("sistemaActual", system.name());

        return switch (status) {
            case HttpServletResponse.SC_FORBIDDEN -> "error/403";
            case HttpServletResponse.SC_NOT_FOUND -> "error/404";
            default -> "error/500";
        };
    }
}

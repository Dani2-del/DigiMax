package com.Servicio.Transporte.web;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Controller
public class ServiceLandingController {

    private final RestClient restClient;
    private final String accesoUrl;

    public ServiceLandingController(@Value("${app.acceso.base-url}") String accesoUrl) {
        this.accesoUrl = accesoUrl;
        this.restClient = RestClient.create();
    }

    @GetMapping("/")
    public String index(HttpServletRequest request, Model model) {
        String token = token(request);
        if (token == null) {
            return "redirect:" + accesoUrl + "/login";
        }

        try {
            Identity identity = restClient.get()
                    .uri(accesoUrl + "/api/auth/me")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .retrieve()
                    .body(Identity.class);
            if (identity == null) {
                model.addAttribute("error", "No fue posible confirmar tu identidad. Intenta ingresar nuevamente.");
                return "servicio-transporte";
            }
            model.addAttribute("name", identity.name());
            model.addAttribute("email", identity.email());
            model.addAttribute("role", identity.role());
            model.addAttribute("accesoUrl", accesoUrl);
        } catch (HttpClientErrorException.Unauthorized | HttpClientErrorException.Forbidden exception) {
            return "redirect:" + accesoUrl + "/login";
        } catch (ResourceAccessException exception) {
            model.addAttribute("error", "El servicio de Acceso no está disponible. Inténtalo más tarde.");
        } catch (RestClientResponseException exception) {
            model.addAttribute("error",
                    "Acceso respondió con un error (HTTP " + exception.getStatusCode().value() + ").");
        }
        return "servicio-transporte";
    }

    private String token(HttpServletRequest request) {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization != null && authorization.startsWith("Bearer ")) {
            return authorization.substring(7);
        }
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if ("JWT_TOKEN".equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }

    private record Identity(String id, String email, String name, String role) {
    }
}

package com.Servicio.Lector.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.client.RestClientException;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

import com.Servicio.Lector.service.RfidService;
import com.Servicio.Lector.service.IdentityDirectoryClient;

@Controller
public class LectorPageController {

    private final RfidService rfidService;
    private final String billeteraUrl;
    private final String accesoUrl;
    private final IdentityDirectoryClient identityDirectory;

    public LectorPageController(
            RfidService rfidService,
            IdentityDirectoryClient identityDirectory,
            @Value("${app.billetera.base-url:http://localhost:8082}") String billeteraUrl,
            @Value("${app.acceso.base-url:http://localhost:8080}") String accesoUrl) {
        this.rfidService = rfidService;
        this.identityDirectory = identityDirectory;
        this.billeteraUrl = billeteraUrl;
        this.accesoUrl = accesoUrl;
    }

    @GetMapping("/")
    public String home(Model model, HttpServletRequest request) {
        var cards = rfidService.listarTarjetas();
        var history = rfidService.listarHistorialPropietarios();
        var userIds = java.util.stream.Stream.concat(
                        cards.stream().map(card -> card.getUsuarioId()),
                        history.stream().map(event -> event.getUsuarioId()))
                .filter(id -> id != null && !id.isBlank()).distinct().toList();
        java.util.Map<String, String> names = java.util.Map.of();
        String token = accessToken(request);
        if (!userIds.isEmpty() && token != null) {
            try {
                names = identityDirectory.names(userIds, token);
            } catch (RestClientException exception) {
                // Keep the page usable when Acceso is temporarily unavailable.
            }
        }
        model.addAttribute("tarjetas", cards);
        model.addAttribute("historialPropietarios", history);
        model.addAttribute("propietariosPorId", names);
        model.addAttribute("adminPanelUrl", billeteraUrl + "/admin/dashboard");
        model.addAttribute("usuarioBilleteraUrl", billeteraUrl + "/");
        model.addAttribute("serviciosUrl", accesoUrl + "/servicios");
        return "lector";
    }

    private static String accessToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie cookie : cookies) {
            if ("JWT_TOKEN".equals(cookie.getName()) && !cookie.getValue().isBlank()) {
                return cookie.getValue();
            }
        }
        return null;
    }
}

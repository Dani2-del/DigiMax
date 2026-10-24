package com.Servicio.Acceso.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ServiciosController {

    private final String transporteUrl;
    private final String recargasUrl;
    private final String billeteraUrl;

    public ServiciosController(
            @Value("${app.services.transporte-url}") String transporteUrl,
            @Value("${app.services.recargas-url}") String recargasUrl,
            @Value("${app.services.billetera-url}") String billeteraUrl) {
        this.transporteUrl = transporteUrl;
        this.recargasUrl = recargasUrl;
        this.billeteraUrl = billeteraUrl;
    }

    @GetMapping("/servicios")
    public String servicios(Model model, Authentication authentication) {
        model.addAttribute("transporteUrl", transporteUrl);
        model.addAttribute("recargasUrl", recargasUrl);
        model.addAttribute("billeteraUrl", billeteraUrl);
        model.addAttribute("esAdministradorGlobal", authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority())));
        return "servicios";
    }
}

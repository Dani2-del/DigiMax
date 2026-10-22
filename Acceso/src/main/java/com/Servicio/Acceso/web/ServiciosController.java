package com.Servicio.Acceso.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ServiciosController {

    private final String transporteUrl;
    private final String recargasUrl;

    public ServiciosController(
            @Value("${app.services.transporte-url}") String transporteUrl,
            @Value("${app.services.recargas-url}") String recargasUrl) {
        this.transporteUrl = transporteUrl;
        this.recargasUrl = recargasUrl;
    }

    @GetMapping("/servicios")
    public String servicios(Model model) {
        model.addAttribute("transporteUrl", transporteUrl);
        model.addAttribute("recargasUrl", recargasUrl);
        return "servicios";
    }
}

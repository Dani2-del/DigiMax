package com.transcaribe.transcaribe.Controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class ServicesNavigationAdvice {

    private final String accesoUrl;

    public ServicesNavigationAdvice(
            @Value("${app.acceso.base-url:http://localhost:8080}") String accesoUrl) {
        this.accesoUrl = accesoUrl;
    }

    @ModelAttribute
    public void addServicesUrl(Model model) {
        model.addAttribute("serviciosUrl", accesoUrl + "/servicios");
    }
}

package com.transcaribe.transcaribe.Controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

@Controller
public class AccountRedirectController {

    private final String accesoUrl;

    public AccountRedirectController(
            @Value("${app.acceso.base-url:http://localhost:8080}") String accesoUrl) {
        this.accesoUrl = accesoUrl;
    }

    @RequestMapping(
            value = {"/perfil", "/perfil/verificar", "/perfil/reenviar-codigo", "/perfil/cancelar-cambio"},
            method = {RequestMethod.GET, RequestMethod.POST})
    public String redirectToAccess() {
        return "redirect:" + accesoUrl + "/cuenta/perfil";
    }
}

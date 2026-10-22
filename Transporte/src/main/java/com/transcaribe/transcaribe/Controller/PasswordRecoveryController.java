package com.transcaribe.transcaribe.Controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

@Controller
public class PasswordRecoveryController {

    private final String accesoUrl;

    public PasswordRecoveryController(@Value("${app.acceso.base-url:http://localhost:8080}") String accesoUrl) {
        this.accesoUrl = accesoUrl;
    }

    @RequestMapping(
            value = {"/olvido-password", "/verificar-codigo-recuperar-password", "/restablecer-password",
                    "/validar-codigo-restablecimiento", "/confirmar-restablecimiento"},
            method = {RequestMethod.GET, RequestMethod.POST})
    public String redirigirAAcceso() {
        return "redirect:" + accesoUrl + "/olvido-password";
    }
}

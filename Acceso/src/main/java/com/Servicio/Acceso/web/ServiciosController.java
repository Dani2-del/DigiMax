package com.Servicio.Acceso.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.security.core.Authentication;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;

import jakarta.servlet.http.HttpServletRequest;
import com.Servicio.Acceso.model.Usuario;
import com.Servicio.Acceso.repository.UsuarioRepository;

@Controller
public class ServiciosController {

    private final String transporteUrl;
    private final String recargasUrl;
    private final String billeteraUrl;
    private final UsuarioRepository usuarios;

    public ServiciosController(
            @Value("${app.services.transporte-url}") String transporteUrl,
            @Value("${app.services.recargas-url}") String recargasUrl,
            @Value("${app.services.billetera-url}") String billeteraUrl,
            UsuarioRepository usuarios) {
        this.transporteUrl = transporteUrl;
        this.recargasUrl = recargasUrl;
        this.billeteraUrl = billeteraUrl;
        this.usuarios = usuarios;
    }

    @GetMapping("/servicios")
    public String servicios(Model model, Authentication authentication, HttpServletRequest request) {
        Object csrfAttribute = request.getAttribute(CsrfToken.class.getName());
        if (csrfAttribute instanceof CsrfToken csrfToken) {
            csrfToken.getToken();
        }
        model.addAttribute("transporteUrl", transporteUrl);
        model.addAttribute("recargasUrl", recargasUrl);
        model.addAttribute("billeteraUrl", billeteraUrl);
        String nombreCuenta = usuarios.findByCorreoIgnoreCase(authentication.getName())
                .map(Usuario::getNombre)
                .filter(StringUtils::hasText)
                .orElse(authentication.getName());
        model.addAttribute("nombreCuenta", nombreCuenta);
        model.addAttribute("esAdministradorGlobal", authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority())));
        return "servicios";
    }
}

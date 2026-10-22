package com.transcaribe.transcaribe.Controller;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

import com.transcaribe.transcaribe.Model.SistemaTransporte;

import jakarta.servlet.http.HttpSession;

@Controller
public class AuthController {

    private final String accesoUrl;

    public AuthController(@Value("${app.acceso.base-url:http://localhost:8080}") String accesoUrl) {
        this.accesoUrl = accesoUrl;
    }

    @GetMapping("/")
    public String index(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return "redirect:" + accesoUrl + "/";
        }
        if (hasRole(authentication, "ROLE_ADMIN")) {
            return "redirect:/admin/seleccionar-entidad";
        }
        if (hasRole(authentication, "ROLE_CONDUCTOR")) {
            return "redirect:/conductor/panel";
        }
        return "redirect:/menu";
    }

    @RequestMapping(value = "/login", method = {RequestMethod.GET, RequestMethod.POST})
    public String login() {
        return "redirect:" + accesoUrl + "/login";
    }

    @RequestMapping(value = "/registro", method = {RequestMethod.GET, RequestMethod.POST})
    public String registro() {
        return "redirect:" + accesoUrl + "/registro";
    }

    @GetMapping("/verificar-otp")
    public String verificarOtp(@RequestParam(required = false) String correo) {
        return "redirect:" + accesoUrl + "/verificar-otp"
                + (correo == null ? "" : "?correo=" + encode(correo));
    }

    @RequestMapping(value = {"/verificar-otp", "/verificar-otp/reenviar"},
            method = {RequestMethod.POST})
    public String verificarOtpPost(@RequestParam(required = false) String correo,
            @RequestParam(required = false) String correoActual) {
        String email = correoActual == null ? correo : correoActual;
        return "redirect:" + accesoUrl + "/verificar-otp"
                + (email == null ? "" : "?correo=" + encode(email));
    }

    @GetMapping("/mapa-rutas")
    public String mapaRutas(Authentication authentication, Model model, HttpSession session) {
        boolean autenticado = authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
        boolean esAdmin = autenticado && authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
        boolean esModerador = autenticado && authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_MODERATOR".equals(authority.getAuthority()));
        if (autenticado && !tieneRutasConfiguradas(session)) {
            return esAdmin || esModerador ? "redirect:/admin/dashboard" : "redirect:/menu";
        }

        String menuUrl = "/";
        String menuLabel = "Volver al inicio";
        if (autenticado) {
            boolean esConductor = authentication.getAuthorities().stream()
                    .anyMatch(authority -> "ROLE_CONDUCTOR".equals(authority.getAuthority()));
            menuUrl = esAdmin ? "/admin/dashboard" : esConductor ? "/conductor/panel" : "/menu";
            menuLabel = esAdmin ? "Volver al panel" : "Volver al menú";
        }
        model.addAttribute("esAdmin", esAdmin);
        model.addAttribute("menuUrl", menuUrl);
        model.addAttribute("menuLabel", menuLabel);
        return "mapa-rutas";
    }

    private boolean tieneRutasConfiguradas(HttpSession session) {
        return session.getAttribute(SistemaTransporte.SESSION_ATTRIBUTE)
                == SistemaTransporte.TRANSCARIBE;
    }

    private boolean hasRole(Authentication authentication, String role) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> role.equals(authority.getAuthority()));
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}

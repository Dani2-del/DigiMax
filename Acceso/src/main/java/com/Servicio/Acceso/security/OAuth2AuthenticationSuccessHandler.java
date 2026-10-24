package com.Servicio.Acceso.security;

import java.io.IOException;
import java.util.Locale;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import com.Servicio.Acceso.model.Usuario;
import com.Servicio.Acceso.repository.UsuarioRepository;
import com.Servicio.Acceso.service.LoginNotificationService;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class OAuth2AuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private final UsuarioRepository usuarios;
    private final JwtService jwtService;
    private final JwtCookieService jwtCookieService;
    private final LoginNotificationService loginNotificationService;

    public OAuth2AuthenticationSuccessHandler(UsuarioRepository usuarios, JwtService jwtService,
            JwtCookieService jwtCookieService,
            LoginNotificationService loginNotificationService) {
        this.usuarios = usuarios;
        this.jwtService = jwtService;
        this.jwtCookieService = jwtCookieService;
        this.loginNotificationService = loginNotificationService;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
            Authentication authentication) throws IOException, ServletException {
        OAuth2User oauthUser = (OAuth2User) authentication.getPrincipal();
        String email = oauthUser.getAttribute("email");
        if (email == null || email.isBlank()) {
            throw new ServletException("El proveedor de identidad no devolvió un correo.");
        }
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        String name = oauthUser.getAttribute("name");
        Usuario usuario = usuarios.findByCorreoIgnoreCase(normalizedEmail).orElseGet(() -> {
            Usuario nuevo = new Usuario(normalizedEmail, "", name == null ? normalizedEmail : name);
            nuevo.setVerificado(true);
            return usuarios.save(nuevo);
        });
        String role = usuario.getRole() == null ? Usuario.ROLE_USER : usuario.getRole();
        if (!role.startsWith("ROLE_")) {
            role = "ROLE_" + role;
        }
        User principal = new User(usuario.getCorreo(), "",
                java.util.List.of(new SimpleGrantedAuthority(role)));
        var accessToken = new UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(accessToken);
        loginNotificationService.notifyLogin(usuario.getCorreo());
        jwtCookieService.addJwtCookie(response, jwtService.generateToken(principal));
        response.sendRedirect("/servicios");
    }
}

package com.Servicio.Acceso.security;

import java.io.IOException;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import com.Servicio.Acceso.repository.UsuarioRepository;
import com.Servicio.Acceso.service.LoginNotificationService;
import com.Servicio.Acceso.service.UniversalWalletProvisioningClient;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final JwtService jwtService;
    private final JwtCookieService jwtCookieService;
    private final LoginNotificationService loginNotificationService;
    private final UsuarioRepository usuarios;
    private final UniversalWalletProvisioningClient walletProvisioningClient;

    public LoginSuccessHandler(JwtService jwtService, JwtCookieService jwtCookieService,
            LoginNotificationService loginNotificationService,
            UsuarioRepository usuarios,
            UniversalWalletProvisioningClient walletProvisioningClient) {
        this.jwtService = jwtService;
        this.jwtCookieService = jwtCookieService;
        this.loginNotificationService = loginNotificationService;
        this.usuarios = usuarios;
        this.walletProvisioningClient = walletProvisioningClient;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
            Authentication authentication) throws IOException, ServletException {
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        var usuario = usuarios.findByCorreoIgnoreCase(userDetails.getUsername())
                .orElseThrow(() -> new IllegalStateException("No se encontró la cuenta autenticada."));
        walletProvisioningClient.provision(usuario.getId());
        loginNotificationService.notifyLogin(userDetails.getUsername());
        jwtCookieService.addJwtCookie(response, jwtService.generateToken(userDetails));
        response.sendRedirect("/servicios");
    }
}

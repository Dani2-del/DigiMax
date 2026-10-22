package com.Servicio.Acceso.service;

import org.springframework.stereotype.Service;

import com.Servicio.Acceso.repository.UsuarioRepository;

@Service
public class LoginNotificationService {

    private final UsuarioRepository usuarios;
    private final EmailService emailService;

    public LoginNotificationService(UsuarioRepository usuarios, EmailService emailService) {
        this.usuarios = usuarios;
        this.emailService = emailService;
    }

    public void notifyLogin(String email) {
        usuarios.findByCorreoIgnoreCase(email)
                .ifPresent(usuario -> emailService.enviarNotificacionLogin(
                        usuario.getCorreo(), usuario.getNombre()));
    }
}

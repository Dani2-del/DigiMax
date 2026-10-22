package com.Servicio.Acceso.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.Servicio.Acceso.model.Usuario;
import com.Servicio.Acceso.repository.UsuarioRepository;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private UsuarioRepository usuarios;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private EmailService emailService;

    private AccountService accountService;

    @BeforeEach
    void setUp() {
        accountService = new AccountService(usuarios, passwordEncoder, emailService);
    }

    @Test
    void registrationCreatesCentralAccountWithoutTransportCard() {
        when(usuarios.findByCorreoIgnoreCase("persona@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("password-segura")).thenReturn("encoded-password");

        AccountService.RegistrationResult result =
                accountService.register("Persona", " Persona@Example.com ", "password-segura");

        ArgumentCaptor<Usuario> account = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarios).save(account.capture());
        Usuario created = account.getValue();
        assertThat(result).isEqualTo(AccountService.RegistrationResult.CREATED);
        assertThat(created.getCorreo()).isEqualTo("persona@example.com");
        assertThat(created.getPasswordHash()).isEqualTo("encoded-password");
        assertThat(created.getTarjetas()).isEmpty();
        assertThat(created.getSitm()).isNull();
        assertThat(created.getCodigoVerificacion()).matches("\\d{6}");
        assertThat(created.getCodigoVerificacionExpira()).isAfter(LocalDateTime.now());
        verify(emailService).enviarCodigoVerificacion(
                "persona@example.com", "Persona", created.getCodigoVerificacion());
    }
}

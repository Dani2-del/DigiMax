package com.Servicio.Acceso.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;

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

    @Mock
    private UniversalWalletProvisioningClient walletProvisioningClient;

    private AccountService accountService;

    @BeforeEach
    void setUp() {
        accountService = new AccountService(usuarios, passwordEncoder, emailService, walletProvisioningClient);
    }

    @Test
    void registrationCreatesCentralIdentityAccount() {
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
        assertThat(created.getRole()).isEqualTo(Usuario.ROLE_USER);
        assertThat(created.getCodigoVerificacion()).matches("\\d{6}");
        assertThat(created.getCodigoVerificacionExpira()).isAfter(LocalDateTime.now());
        verify(emailService).enviarCodigoVerificacion(
                "persona@example.com", "Persona", created.getCodigoVerificacion());
    }

    @Test
    void provisionsUniversalWalletBeforeActivatingAccount() {
        Usuario pending = new Usuario("persona@example.com", "encoded-password", "Persona");
        pending.setId("user-1");
        pending.setCodigoVerificacion("123456");
        pending.setCodigoVerificacionExpira(LocalDateTime.now().plusMinutes(5));
        when(usuarios.findByCorreoIgnoreCase("persona@example.com")).thenReturn(Optional.of(pending));
        when(walletProvisioningClient.provision("user-1")).thenReturn(true);

        assertThat(accountService.verifyAccount("persona@example.com", "123456"))
                .isEqualTo(AccountService.VerificationResult.VERIFIED);

        assertThat(pending.isVerificado()).isTrue();
        verify(walletProvisioningClient).provision("user-1");
        verify(usuarios).save(pending);
    }

    @Test
    void keepsAccountPendingWhenWalletCannotBeProvisioned() {
        Usuario pending = new Usuario("persona@example.com", "encoded-password", "Persona");
        pending.setId("user-1");
        pending.setCodigoVerificacion("123456");
        pending.setCodigoVerificacionExpira(LocalDateTime.now().plusMinutes(5));
        when(usuarios.findByCorreoIgnoreCase("persona@example.com")).thenReturn(Optional.of(pending));
        when(walletProvisioningClient.provision("user-1")).thenReturn(false);

        assertThat(accountService.verifyAccount("persona@example.com", "123456"))
                .isEqualTo(AccountService.VerificationResult.WALLET_PROVISIONING_FAILED);

        assertThat(pending.isVerificado()).isFalse();
        verify(usuarios, never()).save(pending);
    }
}

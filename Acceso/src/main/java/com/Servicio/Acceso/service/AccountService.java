package com.Servicio.Acceso.service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.Servicio.Acceso.model.Usuario;
import com.Servicio.Acceso.repository.UsuarioRepository;

@Service
public class AccountService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int VERIFICATION_MINUTES = 10;
    private static final int RECOVERY_MINUTES = 15;

    private final UsuarioRepository usuarios;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final UniversalWalletProvisioningClient walletProvisioningClient;

    public AccountService(
            UsuarioRepository usuarios,
            PasswordEncoder passwordEncoder,
            EmailService emailService,
            UniversalWalletProvisioningClient walletProvisioningClient) {
        this.usuarios = usuarios;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.walletProvisioningClient = walletProvisioningClient;
    }

    public RegistrationResult register(String name, String email, String password) {
        String normalizedEmail = normalizeEmail(email);
        if (!StringUtils.hasText(name) || !isValidEmail(normalizedEmail) || !isValidPassword(password)) {
            return RegistrationResult.INVALID_INPUT;
        }
        if (usuarios.findByCorreoIgnoreCase(normalizedEmail).isPresent()) {
            return RegistrationResult.EMAIL_IN_USE;
        }

        Usuario usuario = new Usuario(normalizedEmail, passwordEncoder.encode(password), name.trim());
        issueVerificationCode(usuario);
        usuarios.save(usuario);
        emailService.enviarCodigoVerificacion(normalizedEmail, usuario.getNombre(), usuario.getCodigoVerificacion());
        return RegistrationResult.CREATED;
    }

    public VerificationResult verifyAccount(String email, String code) {
        Optional<Usuario> optionalUsuario = usuarios.findByCorreoIgnoreCase(normalizeEmail(email));
        if (optionalUsuario.isEmpty()) {
            return VerificationResult.INVALID_CODE;
        }

        Usuario usuario = optionalUsuario.get();
        if (usuario.isVerificado() || !isCodeValid(code, usuario.getCodigoVerificacion(),
                usuario.getCodigoVerificacionExpira())) {
            return VerificationResult.INVALID_CODE;
        }

        if (!walletProvisioningClient.provision(usuario.getId())) {
            return VerificationResult.WALLET_PROVISIONING_FAILED;
        }
        usuario.setVerificado(true);
        usuario.setCodigoVerificacion(null);
        usuario.setCodigoVerificacionExpira(null);
        usuarios.save(usuario);
        return VerificationResult.VERIFIED;
    }

    public ResendResult resendVerificationCode(String currentEmail, String newEmail) {
        String normalizedEmail = normalizeEmail(newEmail);
        if (!isValidEmail(normalizedEmail)) {
            return ResendResult.INVALID_EMAIL;
        }

        Optional<Usuario> optionalUsuario = usuarios.findByCorreoIgnoreCase(normalizeEmail(currentEmail))
                .filter(usuario -> !usuario.isVerificado());
        if (optionalUsuario.isEmpty()) {
            return ResendResult.ACCOUNT_NOT_PENDING;
        }

        Usuario usuario = optionalUsuario.get();
        Optional<Usuario> accountAtNewEmail = usuarios.findByCorreoIgnoreCase(normalizedEmail);
        if (accountAtNewEmail.isPresent()
                && !accountAtNewEmail.get().getId().equals(usuario.getId())) {
            return ResendResult.EMAIL_IN_USE;
        }

        usuario.setCorreo(normalizedEmail);
        issueVerificationCode(usuario);
        usuarios.save(usuario);
        emailService.enviarCodigoVerificacion(normalizedEmail, usuario.getNombre(), usuario.getCodigoVerificacion());
        return ResendResult.SENT;
    }

    public void requestPasswordRecovery(String email) {
        usuarios.findByCorreoIgnoreCase(normalizeEmail(email)).ifPresent(usuario -> {
            String code = generateCode();
            usuario.setResetToken(code);
            usuario.setTokenExpiry(LocalDateTime.now().plusMinutes(RECOVERY_MINUTES));
            usuarios.save(usuario);
            emailService.enviarCodigoRecuperacion(usuario.getCorreo(), usuario.getNombre(), code);
        });
    }

    public boolean verifyRecoveryCode(String email, String code) {
        return usuarios.findByCorreoIgnoreCase(normalizeEmail(email))
                .filter(usuario -> isCodeValid(code, usuario.getResetToken(), usuario.getTokenExpiry()))
                .isPresent();
    }

    public boolean resetPassword(String email, String code, String newPassword) {
        if (!isValidPassword(newPassword)) {
            return false;
        }
        Optional<Usuario> optionalUsuario = usuarios.findByCorreoIgnoreCase(normalizeEmail(email))
                .filter(usuario -> isCodeValid(code, usuario.getResetToken(), usuario.getTokenExpiry()));
        if (optionalUsuario.isEmpty()) {
            return false;
        }

        Usuario usuario = optionalUsuario.get();
        usuario.setPasswordHash(passwordEncoder.encode(newPassword));
        usuario.setResetToken(null);
        usuario.setTokenExpiry(null);
        usuario.setPendingPasswordHash(null);
        usuarios.save(usuario);
        return true;
    }

    public ChangePasswordResult requestPasswordChange(String email, String currentPassword, String newPassword) {
        Optional<Usuario> optionalUsuario = usuarios.findByCorreoIgnoreCase(normalizeEmail(email));
        if (optionalUsuario.isEmpty() || optionalUsuario.get().getPasswordHash() == null
                || !passwordEncoder.matches(currentPassword, optionalUsuario.get().getPasswordHash())) {
            return ChangePasswordResult.INVALID_CURRENT_PASSWORD;
        }
        if (!isValidPassword(newPassword) || passwordEncoder.matches(newPassword,
                optionalUsuario.get().getPasswordHash())) {
            return ChangePasswordResult.INVALID_NEW_PASSWORD;
        }

        Usuario usuario = optionalUsuario.get();
        String code = generateCode();
        usuario.setCodigoVerificacion(code);
        usuario.setCodigoVerificacionExpira(LocalDateTime.now().plusMinutes(VERIFICATION_MINUTES));
        usuario.setPendingPasswordHash(passwordEncoder.encode(newPassword));
        usuarios.save(usuario);
        emailService.enviarCodigoCambioPassword(usuario.getCorreo(), usuario.getNombre(), code);
        return ChangePasswordResult.CODE_SENT;
    }

    public boolean confirmPasswordChange(String email, String code) {
        Optional<Usuario> optionalUsuario = usuarios.findByCorreoIgnoreCase(normalizeEmail(email))
                .filter(usuario -> usuario.isVerificado()
                        && usuario.getPendingPasswordHash() != null
                        && isCodeValid(code, usuario.getCodigoVerificacion(),
                                usuario.getCodigoVerificacionExpira()));
        if (optionalUsuario.isEmpty()) {
            return false;
        }

        Usuario usuario = optionalUsuario.get();
        usuario.setPasswordHash(usuario.getPendingPasswordHash());
        usuario.setPendingPasswordHash(null);
        usuario.setCodigoVerificacion(null);
        usuario.setCodigoVerificacionExpira(null);
        usuarios.save(usuario);
        return true;
    }

    public ProfileUpdateResult validateProfileUpdate(String email, String name, String newEmail) {
        String normalizedEmail = normalizeEmail(newEmail);
        if (!StringUtils.hasText(name) || !isValidEmail(normalizedEmail)) {
            return ProfileUpdateResult.INVALID_INPUT;
        }
        Optional<Usuario> optionalUsuario = usuarios.findByCorreoIgnoreCase(normalizeEmail(email));
        if (optionalUsuario.isEmpty()) {
            return ProfileUpdateResult.ACCOUNT_NOT_FOUND;
        }
        Optional<Usuario> accountAtNewEmail = usuarios.findByCorreoIgnoreCase(normalizedEmail);
        if (accountAtNewEmail.isPresent()
                && !accountAtNewEmail.get().getId().equals(optionalUsuario.get().getId())) {
            return ProfileUpdateResult.EMAIL_IN_USE;
        }
        return ProfileUpdateResult.READY;
    }

    public boolean updateProfile(String currentEmail, String name, String newEmail) {
        if (validateProfileUpdate(currentEmail, name, newEmail) != ProfileUpdateResult.READY) {
            return false;
        }
        Usuario usuario = usuarios.findByCorreoIgnoreCase(normalizeEmail(currentEmail)).orElseThrow();
        usuario.setNombre(name.trim());
        usuario.setCorreo(normalizeEmail(newEmail));
        usuarios.save(usuario);
        return true;
    }

    private void issueVerificationCode(Usuario usuario) {
        usuario.setCodigoVerificacion(generateCode());
        usuario.setCodigoVerificacionExpira(LocalDateTime.now().plusMinutes(VERIFICATION_MINUTES));
        usuario.setVerificado(false);
    }

    private String generateCode() {
        return String.format("%06d", RANDOM.nextInt(1_000_000));
    }

    private boolean isCodeValid(String providedCode, String storedCode, LocalDateTime expiry) {
        return StringUtils.hasText(providedCode)
                && storedCode != null
                && storedCode.equals(providedCode)
                && expiry != null
                && expiry.isAfter(LocalDateTime.now());
    }

    private boolean isValidPassword(String password) {
        return password != null && password.length() >= 8;
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private boolean isValidEmail(String email) {
        return email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    }

    public enum RegistrationResult {
        CREATED,
        EMAIL_IN_USE,
        INVALID_INPUT
    }

    public enum VerificationResult {
        VERIFIED,
        WALLET_PROVISIONING_FAILED,
        INVALID_CODE
    }

    public enum ResendResult {
        SENT,
        INVALID_EMAIL,
        EMAIL_IN_USE,
        ACCOUNT_NOT_PENDING
    }

    public enum ChangePasswordResult {
        CODE_SENT,
        INVALID_CURRENT_PASSWORD,
        INVALID_NEW_PASSWORD
    }

    public enum ProfileUpdateResult {
        READY,
        INVALID_INPUT,
        EMAIL_IN_USE,
        ACCOUNT_NOT_FOUND
    }
}

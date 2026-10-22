package com.Servicio.Acceso.web;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Locale;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.Servicio.Acceso.model.Usuario;
import com.Servicio.Acceso.repository.UsuarioRepository;
import com.Servicio.Acceso.security.JwtCookieService;
import com.Servicio.Acceso.security.JwtService;
import com.Servicio.Acceso.service.AccountService;
import com.Servicio.Acceso.service.MailDeliveryException;
import com.Servicio.Acceso.service.EmailService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

@Controller
public class AccountProfileController {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final long CODE_LIFETIME_MILLIS = 10 * 60 * 1000;
    private static final int MAX_CODE_ATTEMPTS = 5;
    private static final String PENDING_PROFILE_CHANGE = "pendingProfileChange";

    private final AccountService accountService;
    private final UsuarioRepository usuarios;
    private final EmailService emailService;
    private final UserDetailsService userDetailsService;
    private final JwtService jwtService;
    private final JwtCookieService jwtCookieService;

    public AccountProfileController(AccountService accountService, UsuarioRepository usuarios,
            EmailService emailService, UserDetailsService userDetailsService,
            JwtService jwtService, JwtCookieService jwtCookieService) {
        this.accountService = accountService;
        this.usuarios = usuarios;
        this.emailService = emailService;
        this.userDetailsService = userDetailsService;
        this.jwtService = jwtService;
        this.jwtCookieService = jwtCookieService;
    }

    @GetMapping("/cuenta/perfil")
    public String perfil(@AuthenticationPrincipal UserDetails principal, Model model) {
        Usuario usuario = usuarios.findByCorreoIgnoreCase(principal.getUsername()).orElseThrow();
        model.addAttribute("nombre", usuario.getNombre());
        model.addAttribute("correo", usuario.getCorreo());
        return "cuenta-perfil";
    }

    @PostMapping("/cuenta/perfil")
    public String solicitarCambio(@AuthenticationPrincipal UserDetails principal,
            @RequestParam String nombre,
            @RequestParam String correo,
            HttpSession session,
            Model model) {
        AccountService.ProfileUpdateResult result =
                accountService.validateProfileUpdate(principal.getUsername(), nombre, correo);
        if (result != AccountService.ProfileUpdateResult.READY) {
            model.addAttribute("nombre", nombre);
            model.addAttribute("correo", correo);
            model.addAttribute("error", profileError(result));
            return "cuenta-perfil";
        }

        Usuario usuario = usuarios.findByCorreoIgnoreCase(principal.getUsername()).orElseThrow();
        String code = generateCode();
        try {
            emailService.enviarCodigoCambioPerfil(
                    usuario.getCorreo(), usuario.getNombre(), code);
        } catch (MailDeliveryException exception) {
            model.addAttribute("nombre", nombre);
            model.addAttribute("correo", correo);
            model.addAttribute("error", "No se pudo enviar el código al correo actual. Inténtalo nuevamente.");
            return "cuenta-perfil";
        }

        session.setAttribute(PENDING_PROFILE_CHANGE, new PendingProfileChange(
                usuario.getCorreo(), nombre.trim(), correo.trim().toLowerCase(Locale.ROOT),
                code, Instant.now().toEpochMilli() + CODE_LIFETIME_MILLIS, 0));
        return "redirect:/cuenta/perfil/verificar";
    }

    @GetMapping("/cuenta/perfil/verificar")
    public String mostrarVerificacion(HttpSession session, Model model) {
        PendingProfileChange pending = pendingChange(session);
        if (pending == null || Instant.now().toEpochMilli() >= pending.expiresAt()) {
            session.removeAttribute(PENDING_PROFILE_CHANGE);
            return "redirect:/cuenta/perfil";
        }
        model.addAttribute("correoVerificacion", pending.currentEmail());
        return "cuenta-verificar-perfil";
    }

    @PostMapping("/cuenta/perfil/verificar")
    public String confirmarCambio(@AuthenticationPrincipal UserDetails principal,
            @RequestParam String codigo,
            HttpSession session,
            HttpServletRequest request,
            HttpServletResponse response,
            RedirectAttributes redirectAttributes) {
        PendingProfileChange pending = pendingChange(session);
        if (pending == null || !pending.currentEmail().equalsIgnoreCase(principal.getUsername())
                || Instant.now().toEpochMilli() >= pending.expiresAt()) {
            session.removeAttribute(PENDING_PROFILE_CHANGE);
            redirectAttributes.addFlashAttribute("error", "El código venció. Solicita los cambios nuevamente.");
            return "redirect:/cuenta/perfil";
        }
        if (!MessageDigest.isEqual(pending.code().getBytes(StandardCharsets.UTF_8),
                (codigo == null ? "" : codigo.trim()).getBytes(StandardCharsets.UTF_8))) {
            int attempts = pending.attempts() + 1;
            if (attempts >= MAX_CODE_ATTEMPTS) {
                session.removeAttribute(PENDING_PROFILE_CHANGE);
                redirectAttributes.addFlashAttribute("error", "Se agotaron los intentos. Solicita los cambios de nuevo.");
            } else {
                session.setAttribute(PENDING_PROFILE_CHANGE, pending.withAttempts(attempts));
                redirectAttributes.addFlashAttribute("error",
                        "El código no es correcto. Te quedan " + (MAX_CODE_ATTEMPTS - attempts) + " intentos.");
            }
            return "redirect:/cuenta/perfil/verificar";
        }
        boolean updated;
        try {
            updated = accountService.updateProfile(
                    pending.currentEmail(), pending.name(), pending.newEmail());
        } catch (DuplicateKeyException exception) {
            updated = false;
        }
        if (!updated) {
            session.removeAttribute(PENDING_PROFILE_CHANGE);
            redirectAttributes.addFlashAttribute("error",
                    "No se pudieron guardar los cambios. Verifica que el correo siga disponible.");
            return "redirect:/cuenta/perfil";
        }

        session.removeAttribute(PENDING_PROFILE_CHANGE);
        UserDetails updatedPrincipal = userDetailsService.loadUserByUsername(pending.newEmail());
        var authentication = new UsernamePasswordAuthenticationToken(
                updatedPrincipal, null, updatedPrincipal.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);
        new HttpSessionSecurityContextRepository().saveContext(
                SecurityContextHolder.getContext(), request, response);
        jwtCookieService.addJwtCookie(response, jwtService.generateToken(updatedPrincipal));
        redirectAttributes.addFlashAttribute("mensaje", "Los datos de tu cuenta se actualizaron correctamente.");
        return "redirect:/cuenta/perfil";
    }

    @PostMapping("/cuenta/perfil/reenviar")
    public String reenviarCodigo(@AuthenticationPrincipal UserDetails principal,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        PendingProfileChange pending = pendingChange(session);
        if (pending == null || !pending.currentEmail().equalsIgnoreCase(principal.getUsername())
                || Instant.now().toEpochMilli() >= pending.expiresAt()) {
            session.removeAttribute(PENDING_PROFILE_CHANGE);
            redirectAttributes.addFlashAttribute("error", "Solicita los cambios nuevamente para recibir un código.");
            return "redirect:/cuenta/perfil";
        }
        Usuario usuario = usuarios.findByCorreoIgnoreCase(principal.getUsername()).orElseThrow();
        String code = generateCode();
        try {
            emailService.enviarCodigoCambioPerfil(usuario.getCorreo(), usuario.getNombre(), code);
        } catch (MailDeliveryException exception) {
            redirectAttributes.addFlashAttribute("error", "No se pudo reenviar el código. Inténtalo más tarde.");
            return "redirect:/cuenta/perfil/verificar";
        }
        session.setAttribute(PENDING_PROFILE_CHANGE, new PendingProfileChange(
                pending.currentEmail(), pending.name(), pending.newEmail(), code,
                Instant.now().toEpochMilli() + CODE_LIFETIME_MILLIS, 0));
        redirectAttributes.addFlashAttribute("mensaje", "Enviamos un nuevo código a tu correo actual.");
        return "redirect:/cuenta/perfil/verificar";
    }

    private PendingProfileChange pendingChange(HttpSession session) {
        Object pending = session.getAttribute(PENDING_PROFILE_CHANGE);
        return pending instanceof PendingProfileChange change ? change : null;
    }

    private String generateCode() {
        return String.format(Locale.ROOT, "%06d", RANDOM.nextInt(1_000_000));
    }

    private String profileError(AccountService.ProfileUpdateResult result) {
        return switch (result) {
            case INVALID_INPUT -> "Ingresa un nombre y un correo válidos.";
            case EMAIL_IN_USE -> "Ese correo ya está asociado a otra cuenta.";
            case ACCOUNT_NOT_FOUND -> "No encontramos tu cuenta.";
            case READY -> "";
        };
    }

    private record PendingProfileChange(String currentEmail, String name, String newEmail,
            String code, long expiresAt, int attempts) {

        PendingProfileChange withAttempts(int newAttempts) {
            return new PendingProfileChange(currentEmail, name, newEmail, code, expiresAt, newAttempts);
        }
    }
}

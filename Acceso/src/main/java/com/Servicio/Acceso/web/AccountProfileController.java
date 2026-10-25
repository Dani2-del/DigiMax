package com.Servicio.Acceso.web;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.dao.DuplicateKeyException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import org.springframework.web.client.RestClientException;

import com.Servicio.Acceso.model.Usuario;
import com.Servicio.Acceso.repository.UsuarioRepository;
import com.Servicio.Acceso.security.JwtCookieService;
import com.Servicio.Acceso.security.JwtService;
import com.Servicio.Acceso.service.AccountService;
import com.Servicio.Acceso.service.MailDeliveryException;
import com.Servicio.Acceso.service.EmailService;
import com.Servicio.Acceso.service.TransportSitmClient;
import com.Servicio.Acceso.service.TransportSitmClient.SitmInfo;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

@Controller
public class AccountProfileController {

    private static final Logger LOGGER = LoggerFactory.getLogger(AccountProfileController.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final long CODE_LIFETIME_MILLIS = 10 * 60 * 1000;
    private static final int MAX_CODE_ATTEMPTS = 5;
    private static final String PENDING_PROFILE_CHANGE = "pendingProfileChange";
    private static final Set<String> TRANSPORT_SITMS = Set.of(
            "TRANSCARIBE", "TRANSMILENIO", "TRANSMETRO");

    private final AccountService accountService;
    private final UsuarioRepository usuarios;
    private final EmailService emailService;
    private final UserDetailsService userDetailsService;
    private final JwtService jwtService;
    private final JwtCookieService jwtCookieService;
    private final TransportSitmClient transportSitmClient;

    public AccountProfileController(AccountService accountService, UsuarioRepository usuarios,
            EmailService emailService, UserDetailsService userDetailsService,
            JwtService jwtService, JwtCookieService jwtCookieService,
            TransportSitmClient transportSitmClient) {
        this.accountService = accountService;
        this.usuarios = usuarios;
        this.emailService = emailService;
        this.userDetailsService = userDetailsService;
        this.jwtService = jwtService;
        this.jwtCookieService = jwtCookieService;
        this.transportSitmClient = transportSitmClient;
    }

    @GetMapping("/cuenta/perfil")
    public String perfil(@AuthenticationPrincipal UserDetails principal, Model model) {
        Usuario usuario = usuarios.findByCorreoIgnoreCase(principal.getUsername()).orElseThrow();
        model.addAttribute("nombre", usuario.getNombre());
        model.addAttribute("correo", usuario.getCorreo());
        model.addAttribute("sitms", List.of(
                new SitmOption("TRANSCARIBE", "Transcaribe"),
                new SitmOption("TRANSMILENIO", "TransMilenio"),
                new SitmOption("TRANSMETRO", "Transmetro")));
        model.addAttribute("puedeCambiarSitm", Usuario.ROLE_USER.equals(usuario.getRole())
                || Usuario.ROLE_MODERATOR.equals(usuario.getRole()));
        model.addAttribute("esModeradorTransporte", esModeradorTransporte(usuario));
        try {
            SitmInfo sitmInfo = transportSitmClient.getSitm(jwtService.generateToken(principal));
            model.addAttribute("sitmActual", sitmInfo.currentSitm());
            model.addAttribute("sitmActualNombre", visibleSitm(sitmInfo.currentSitm()));
            model.addAttribute("sitmDisponible", true);
        } catch (RestClientException | IllegalStateException exception) {
            LOGGER.warn("No se pudo consultar el SITM del usuario {} en Transporte.", usuario.getId(), exception);
            model.addAttribute("sitmDisponible", false);
            model.addAttribute("sitmError",
                    "No se pudo consultar tu SITM ahora. Inténtalo nuevamente más tarde.");
        }
        return "cuenta-perfil";
    }

    @PostMapping("/cuenta/perfil/sitm")
    public String cambiarSitm(@AuthenticationPrincipal UserDetails principal,
            @RequestParam String sitm,
            RedirectAttributes redirectAttributes) {
        String requestedSitm = sitm == null ? "" : sitm.trim().toUpperCase(Locale.ROOT);
        if (!TRANSPORT_SITMS.contains(requestedSitm)) {
            redirectAttributes.addFlashAttribute("error", "Selecciona un sistema de transporte válido.");
            return "redirect:/cuenta/perfil";
        }

        Usuario usuario = usuarios.findByCorreoIgnoreCase(principal.getUsername()).orElseThrow();
        String accessToken = jwtService.generateToken(principal);
        boolean transportModerator = esModeradorTransporte(usuario);
        try {
            SitmInfo current = transportSitmClient.getSitm(accessToken);
            if (requestedSitm.equals(current.currentSitm())) {
                redirectAttributes.addFlashAttribute("mensaje", "Ya tienes seleccionado ese SITM.");
                return "redirect:/cuenta/perfil";
            }
            if (transportModerator) {
                emailService.enviarAvisoCambioSitmModerador(
                        usuario.getCorreo(), usuario.getNombre(),
                        visibleSitm(current.currentSitm()), visibleSitm(requestedSitm));
                redirectAttributes.addFlashAttribute("mensaje",
                        "No cambiamos tu SITM porque eres moderador. Te enviamos un correo con la explicación "
                                + "y los pasos para contactar a soporte.");
                return "redirect:/cuenta/perfil";
            }
            transportSitmClient.changeSitm(accessToken, requestedSitm);
            redirectAttributes.addFlashAttribute("mensaje", "Tu SITM se actualizó correctamente.");
        } catch (RestClientException | IllegalStateException exception) {
            LOGGER.warn("No se pudo actualizar el SITM del usuario {}.", usuario.getId(), exception);
            redirectAttributes.addFlashAttribute("error",
                    "No se pudo actualizar tu SITM. Inténtalo nuevamente más tarde.");
        } catch (MailDeliveryException exception) {
            LOGGER.error("No se pudo enviar el aviso de cambio de SITM al moderador {}.",
                    usuario.getId(), exception);
            redirectAttributes.addFlashAttribute("error",
                    "No pudimos enviar el correo de aviso; tu SITM no cambió. Inténtalo más tarde.");
        }
        return "redirect:/cuenta/perfil";
    }

    private static String visibleSitm(String sitm) {
        return switch (sitm == null ? "" : sitm) {
            case "TRANSCARIBE" -> "Transcaribe";
            case "TRANSMILENIO" -> "TransMilenio";
            case "TRANSMETRO" -> "Transmetro";
            default -> "sin asignar";
        };
    }

    private static boolean esModeradorTransporte(Usuario usuario) {
        return Usuario.ROLE_MODERATOR.equals(usuario.getRole())
                && usuario.getServicioAsignado() != null
                && TRANSPORT_SITMS.contains(usuario.getServicioAsignado().toUpperCase(Locale.ROOT));
    }

    public record SitmOption(String code, String name) {
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

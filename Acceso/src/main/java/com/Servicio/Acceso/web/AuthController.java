package com.Servicio.Acceso.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.dao.DuplicateKeyException;

import com.Servicio.Acceso.security.JwtCookieService;
import com.Servicio.Acceso.security.JwtService;
import com.Servicio.Acceso.service.AccountService;
import com.Servicio.Acceso.service.MailDeliveryException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Controller
public class AuthController {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthController.class);

    private final AccountService accountService;
    private final UserDetailsService userDetailsService;
    private final JwtService jwtService;
    private final JwtCookieService jwtCookieService;
    private final boolean googleOAuthEnabled;

    public AuthController(AccountService accountService,
            UserDetailsService userDetailsService,
            JwtService jwtService,
            JwtCookieService jwtCookieService,
            @Value("${app.oauth.google.enabled:false}") boolean googleOAuthEnabled) {
        this.accountService = accountService;
        this.userDetailsService = userDetailsService;
        this.jwtService = jwtService;
        this.jwtCookieService = jwtCookieService;
        this.googleOAuthEnabled = googleOAuthEnabled;
    }

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("googleOAuthEnabled", googleOAuthEnabled);
        return "index";
    }

    @GetMapping("/login")
    public String login(Authentication authentication, Model model) {
        if (isAuthenticated(authentication)) {
            return "redirect:/servicios";
        }
        model.addAttribute("googleOAuthEnabled", googleOAuthEnabled);
        return "usuarios/auth/login";
    }

    @GetMapping("/registro")
    public String registro(Authentication authentication) {
        return isAuthenticated(authentication) ? "redirect:/servicios" : "usuarios/auth/registro";
    }

    @PostMapping("/registro")
    public String registrar(@RequestParam("username") String nombre,
            @RequestParam String correo,
            @RequestParam("password") String contrasena,
            @RequestParam("confirmPassword") String confirmarContrasena,
            Authentication authentication,
            Model model,
            RedirectAttributes redirectAttributes) {
        if (isAuthenticated(authentication)) {
            return "redirect:/servicios";
        }
        if (!contrasena.equals(confirmarContrasena)) {
            model.addAttribute("error", "Las contraseñas no coinciden.");
            return "usuarios/auth/registro";
        }
        try {
            AccountService.RegistrationResult result = accountService.register(nombre, correo, contrasena);
            return switch (result) {
                case CREATED -> "redirect:/verificar-otp?correo=" + encode(correo);
                case EMAIL_IN_USE -> registrationError(model, "Ese correo ya está registrado.");
                case INVALID_INPUT -> registrationError(model,
                        "Revisa el nombre, el correo y la contraseña (mínimo 8 caracteres).");
            };
        } catch (DuplicateKeyException exception) {
            return registrationError(model, "Ese correo ya está registrado.");
        } catch (MailDeliveryException exception) {
            redirectAttributes.addFlashAttribute("error",
                    "Tu cuenta quedó pendiente de verificación, pero no pudimos enviar el código. Revisa que el "
                            + "correo esté escrito correctamente y vuelve a enviarlo. Si vuelve a fallar, contacta "
                            + "al administrador.");
            LOGGER.warn("No se pudo enviar el código de verificación para el registro: {}",
                    causaCorreo(exception));
            return "redirect:/verificar-otp?correo=" + encode(correo);
        }
    }

    @GetMapping("/verificar-otp")
    public String mostrarVerificacion(@RequestParam String correo, Model model) {
        model.addAttribute("correo", correo);
        model.addAttribute("correoNuevo", correo);
        return "usuarios/auth/verificar-codigo";
    }

    @PostMapping("/verificar-otp")
    public String verificarOtp(@RequestParam String correo, @RequestParam String codigo,
            Model model, RedirectAttributes redirectAttributes,
            HttpServletRequest request, HttpServletResponse response) {
        AccountService.VerificationResult result = accountService.verifyAccount(correo, codigo);
        if (result == AccountService.VerificationResult.VERIFIED) {
            autenticarCuentaVerificada(correo, request, response);
            return "redirect:/registro/verificado";
        }
        model.addAttribute("correo", correo);
        model.addAttribute("correoNuevo", correo);
        model.addAttribute("error", "El código es incorrecto o ya venció. Solicita uno nuevo.");
        return "usuarios/auth/verificar-codigo";
    }

    @GetMapping("/registro/verificado")
    public String registroVerificado(Authentication authentication) {
        return isAuthenticated(authentication)
                ? "usuarios/auth/registro-verificado"
                : "redirect:/login";
    }

    @PostMapping("/verificar-otp/reenviar")
    public String reenviarOtp(@RequestParam String correoActual, @RequestParam String correoNuevo,
            Model model) {
        model.addAttribute("correo", correoActual);
        model.addAttribute("correoNuevo", correoNuevo);
        try {
            AccountService.ResendResult result = accountService.resendVerificationCode(correoActual, correoNuevo);
            switch (result) {
                case SENT -> {
                    model.addAttribute("correo", correoNuevo.trim());
                    model.addAttribute("correoNuevo", correoNuevo.trim());
                    model.addAttribute("mensaje", "Enviamos un nuevo código a " + correoNuevo.trim() + ".");
                }
                case INVALID_EMAIL -> model.addAttribute("error", "Ingresa un correo válido.");
                case EMAIL_IN_USE -> model.addAttribute("error", "Ese correo ya está asociado a otra cuenta.");
                case ACCOUNT_NOT_PENDING ->
                        model.addAttribute("error", "No encontramos una cuenta pendiente de verificación.");
            }
        } catch (MailDeliveryException exception) {
            model.addAttribute("error",
                    "No pudimos enviar el código. Revisa la dirección de correo e inténtalo otra vez.");
            LOGGER.warn("No se pudo reenviar el código de verificación: {}", causaCorreo(exception));
        }
        return "usuarios/auth/verificar-codigo";
    }

    private String registrationError(Model model, String error) {
        model.addAttribute("error", error);
        return "usuarios/auth/registro";
    }

    private String encode(String value) {
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
    }

    private String causaCorreo(MailDeliveryException exception) {
        Throwable cause = exception.getCause();
        return cause == null
                ? exception.getClass().getSimpleName()
                : cause.getClass().getSimpleName() + ": " + cause.getMessage();
    }

    private void autenticarCuentaVerificada(
            String correo, HttpServletRequest request, HttpServletResponse response) {
        UserDetails userDetails = userDetailsService.loadUserByUsername(correo);
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                userDetails, null, userDetails.getAuthorities());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        new HttpSessionSecurityContextRepository().saveContext(context, request, response);
        jwtCookieService.addJwtCookie(response, jwtService.generateToken(userDetails));
    }

    private boolean isAuthenticated(Authentication authentication) {
        return authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
    }
}

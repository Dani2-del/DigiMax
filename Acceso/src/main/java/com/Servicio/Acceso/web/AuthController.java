package com.Servicio.Acceso.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.dao.DuplicateKeyException;

import com.Servicio.Acceso.service.AccountService;
import com.Servicio.Acceso.service.MailDeliveryException;

@Controller
public class AuthController {

    private final AccountService accountService;
    private final boolean googleOAuthEnabled;

    public AuthController(AccountService accountService,
            @Value("${app.oauth.google.enabled:false}") boolean googleOAuthEnabled) {
        this.accountService = accountService;
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
                    "La cuenta se creó, pero no pudimos enviar el código. Puedes solicitarlo nuevamente.");
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
            Model model, RedirectAttributes redirectAttributes) {
        AccountService.VerificationResult result = accountService.verifyAccount(correo, codigo);
        if (result == AccountService.VerificationResult.VERIFIED) {
            redirectAttributes.addFlashAttribute("mensaje", "Cuenta activada. Ya puedes iniciar sesión.");
            return "redirect:/login";
        }
        model.addAttribute("correo", correo);
        model.addAttribute("correoNuevo", correo);
        model.addAttribute("error", result == AccountService.VerificationResult.WALLET_PROVISIONING_FAILED
                        ? "No se pudo emitir tu tarjeta universal. Inténtalo de nuevo en unos minutos."
                        : "El código es incorrecto o ya venció. Solicita uno nuevo.");
        return "usuarios/auth/verificar-codigo";
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
            model.addAttribute("error", "No se pudo enviar el correo. Inténtalo de nuevo más tarde.");
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

    private boolean isAuthenticated(Authentication authentication) {
        return authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
    }
}

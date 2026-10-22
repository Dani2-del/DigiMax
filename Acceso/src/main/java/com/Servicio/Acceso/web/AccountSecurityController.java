package com.Servicio.Acceso.web;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.Servicio.Acceso.service.AccountService;
import com.Servicio.Acceso.service.MailDeliveryException;

import jakarta.servlet.http.HttpSession;

@Controller
public class AccountSecurityController {

    private static final String PENDING_PASSWORD_EMAIL = "pendingPasswordEmail";

    private final AccountService accountService;

    public AccountSecurityController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping("/cuenta/seguridad")
    public String mostrarSeguridad() {
        return "cuenta-seguridad";
    }

    @PostMapping("/cuenta/cambiar-password")
    public String solicitarCambio(@AuthenticationPrincipal UserDetails user,
            @RequestParam String passwordActual,
            @RequestParam String nuevaPassword,
            @RequestParam String confirmarPassword,
            HttpSession session,
            Model model) {
        if (!nuevaPassword.equals(confirmarPassword)) {
            model.addAttribute("error", "Las contraseñas no coinciden.");
            return "cuenta-seguridad";
        }
        try {
            AccountService.ChangePasswordResult result =
                    accountService.requestPasswordChange(user.getUsername(), passwordActual, nuevaPassword);
            if (result == AccountService.ChangePasswordResult.CODE_SENT) {
                session.setAttribute(PENDING_PASSWORD_EMAIL, user.getUsername());
                return "redirect:/cuenta/seguridad/verificar";
            }
            model.addAttribute("error", result == AccountService.ChangePasswordResult.INVALID_CURRENT_PASSWORD
                    ? "La contraseña actual no es correcta."
                    : "La nueva contraseña debe tener mínimo 8 caracteres y ser diferente a la actual.");
            return "cuenta-seguridad";
        } catch (MailDeliveryException exception) {
            model.addAttribute("error", "No se pudo enviar el código. Inténtalo de nuevo más tarde.");
            return "cuenta-seguridad";
        }
    }

    @GetMapping("/cuenta/seguridad/verificar")
    public String mostrarVerificacion(HttpSession session, Model model) {
        String email = (String) session.getAttribute(PENDING_PASSWORD_EMAIL);
        if (email == null) {
            return "redirect:/cuenta/seguridad";
        }
        model.addAttribute("correo", email);
        return "cuenta-verificar-password";
    }

    @PostMapping("/cuenta/seguridad/verificar")
    public String confirmarCambio(@AuthenticationPrincipal UserDetails user,
            @RequestParam String codigo,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        String email = (String) session.getAttribute(PENDING_PASSWORD_EMAIL);
        if (email == null || !email.equals(user.getUsername())
                || !accountService.confirmPasswordChange(email, codigo)) {
            redirectAttributes.addFlashAttribute("error", "El código es incorrecto o venció.");
            return "redirect:/cuenta/seguridad/verificar";
        }
        session.removeAttribute(PENDING_PASSWORD_EMAIL);
        redirectAttributes.addFlashAttribute("mensaje", "Contraseña actualizada correctamente.");
        return "redirect:/cuenta/seguridad";
    }
}

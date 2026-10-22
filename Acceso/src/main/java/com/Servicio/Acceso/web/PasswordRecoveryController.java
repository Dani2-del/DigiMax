package com.Servicio.Acceso.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.SessionAttribute;
import org.springframework.web.bind.annotation.SessionAttributes;
import org.springframework.web.bind.support.SessionStatus;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.Servicio.Acceso.service.AccountService;
import com.Servicio.Acceso.service.MailDeliveryException;

import jakarta.servlet.http.HttpSession;

@Controller
@SessionAttributes({"recoveryEmail", "recoveryCode"})
public class PasswordRecoveryController {

    private final AccountService accountService;

    public PasswordRecoveryController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping("/olvido-password")
    public String mostrarOlvidoPassword() {
        return "usuarios/password-recovery/olvido-password";
    }

    @PostMapping("/olvido-password")
    public String procesarOlvidoPassword(@RequestParam("correo") String correo,
            RedirectAttributes redirectAttributes) {
        try {
            accountService.requestPasswordRecovery(correo);
            redirectAttributes.addFlashAttribute("mensaje",
                    "Si existe una cuenta con ese correo, recibirás un código de recuperación.");
            return "redirect:/verificar-codigo-recuperar-password?correo="
                    + java.net.URLEncoder.encode(correo, java.nio.charset.StandardCharsets.UTF_8);
        } catch (MailDeliveryException exception) {
            redirectAttributes.addFlashAttribute("error", "No se pudo enviar el correo. Inténtalo más tarde.");
            return "redirect:/olvido-password";
        }
    }

    @GetMapping("/verificar-codigo-recuperar-password")
    public String mostrarVistaCodigo(@RequestParam("correo") String correo, Model model) {
        model.addAttribute("correo", correo);
        return "usuarios/password-recovery/verificar-codigo-recuperar-password";
    }

    @PostMapping("/validar-codigo-restablecimiento")
    public String validarCodigo(@RequestParam("correo") String correo,
            @RequestParam("codigo") String codigo, Model model) {
        if (accountService.verifyRecoveryCode(correo, codigo)) {
            model.addAttribute("recoveryEmail", correo);
            model.addAttribute("recoveryCode", codigo);
            return "redirect:/restablecer-password";
        }
        model.addAttribute("correo", correo);
        model.addAttribute("error", "Código incorrecto o vencido.");
        return "usuarios/password-recovery/verificar-codigo-recuperar-password";
    }

    @GetMapping("/restablecer-password")
    public String mostrarVistaRestablecer(
            @SessionAttribute(name = "recoveryEmail", required = false) String correo,
            Model model) {
        if (correo == null) {
            return "redirect:/olvido-password";
        }
        model.addAttribute("correo", correo);
        return "usuarios/password-recovery/restablecer-password";
    }

    @PostMapping("/confirmar-restablecimiento")
    public String confirmarRestablecimiento(
            @SessionAttribute(name = "recoveryEmail", required = false) String correo,
            @SessionAttribute(name = "recoveryCode", required = false) String codigo,
            @RequestParam("nuevaPassword") String nuevaPassword,
            @RequestParam("confirmarPassword") String confirmarPassword,
            HttpSession session,
            SessionStatus sessionStatus,
            RedirectAttributes redirectAttributes) {
        if (correo == null || codigo == null || !nuevaPassword.equals(confirmarPassword)
                || !accountService.resetPassword(correo, codigo, nuevaPassword)) {
            session.removeAttribute("recoveryEmail");
            session.removeAttribute("recoveryCode");
            sessionStatus.setComplete();
            redirectAttributes.addFlashAttribute("error",
                    "No se pudo restablecer la contraseña. Solicita un nuevo código e inténtalo otra vez.");
            return "redirect:/olvido-password";
        }
        session.removeAttribute("recoveryEmail");
        session.removeAttribute("recoveryCode");
        sessionStatus.setComplete();
        redirectAttributes.addFlashAttribute("mensaje", "Contraseña actualizada. Ya puedes iniciar sesión.");
        return "redirect:/login";
    }
}

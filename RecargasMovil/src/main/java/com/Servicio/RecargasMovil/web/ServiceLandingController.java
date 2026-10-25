package com.Servicio.RecargasMovil.web;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.Servicio.RecargasMovil.model.OperadorMovil;
import com.Servicio.RecargasMovil.model.PaqueteMovil;
import com.Servicio.RecargasMovil.security.AuthenticatedIdentity;
import com.Servicio.RecargasMovil.security.RemoteAccessAuthenticationFilter;
import com.Servicio.RecargasMovil.service.InsufficientWalletBalanceException;
import com.Servicio.RecargasMovil.service.RecargasService;
import com.Servicio.RecargasMovil.service.WalletClient;

import jakarta.servlet.http.HttpServletRequest;

@Controller
public class ServiceLandingController {

    private final RecargasService recargasService;
    private final WalletClient walletClient;
    private final String accesoUrl;
    private final String billeteraUrl;

    public ServiceLandingController(
            RecargasService recargasService,
            WalletClient walletClient,
            @Value("${app.acceso.base-url}") String accesoUrl,
            @Value("${app.billetera.base-url}") String billeteraUrl) {
        this.recargasService = recargasService;
        this.walletClient = walletClient;
        this.accesoUrl = accesoUrl;
        this.billeteraUrl = billeteraUrl;
    }

    @GetMapping("/")
    public String index(
            @AuthenticationPrincipal AuthenticatedIdentity identity,
            HttpServletRequest request,
            Model model) {
        model.addAttribute("identity", identity);
        var operators = recargasService.operadoresActivos();
        var packages = recargasService.paquetesActivos();
        model.addAttribute("operadores", operators);
        model.addAttribute("paquetes", packages);
        model.addAttribute("operadoresPorId", operators.stream()
                .collect(Collectors.toMap(OperadorMovil::getId, OperadorMovil::getNombre)));
        model.addAttribute("movilExitoId", operators.stream()
                .filter(operator -> "Móvil Éxito".equalsIgnoreCase(operator.getNombre()))
                .map(OperadorMovil::getId)
                .findFirst()
                .orElse(null));
        model.addAttribute("customRechargeOperationId", UUID.randomUUID().toString());
        model.addAttribute("lineas", recargasService.lineasDelUsuario(identity.id()));
        model.addAttribute("recargas", recargasService.historialDelUsuario(identity.id()));
        model.addAttribute("operationIds", packages.stream()
                .collect(Collectors.toMap(PaqueteMovil::getId, ignored -> UUID.randomUUID().toString())));
        model.addAttribute("accesoUrl", accesoUrl);
        model.addAttribute("billeteraUrl", billeteraUrl);
        model.addAttribute("walletError", false);
        model.addAttribute("walletMissing", false);
        model.addAttribute("walletFrozen", false);
        try {
            var wallet = walletClient.getWallet(token(request));
            model.addAttribute("wallet", wallet);
            model.addAttribute("walletFrozen", wallet.isFrozen());
        } catch (WalletClient.WalletNotFoundException exception) {
            model.addAttribute("walletMissing", true);
        } catch (ResourceAccessException exception) {
            model.addAttribute("walletError", true);
            model.addAttribute("error",
                    "La billetera universal no está disponible. No se puede solicitar una recarga ahora.");
        } catch (RestClientResponseException exception) {
            model.addAttribute("walletError", true);
            model.addAttribute("error", "Billetera respondió con un error (HTTP "
                    + exception.getStatusCode().value() + ").");
        } catch (IllegalStateException exception) {
            model.addAttribute("walletError", true);
            model.addAttribute("error", exception.getMessage());
        }
        return "servicio-recargas";
    }

    @PostMapping("/lineas")
    public String addLine(
            @AuthenticationPrincipal AuthenticatedIdentity identity,
            @RequestParam String operadorId,
            @RequestParam String telefono,
            @RequestParam(required = false) String alias,
            RedirectAttributes redirectAttributes) {
        try {
            recargasService.guardarLinea(identity.id(), operadorId, telefono, alias);
            redirectAttributes.addFlashAttribute("mensaje", "La línea quedó guardada en tu perfil de Recargas.");
        } catch (IllegalArgumentException exception) {
            redirectAttributes.addFlashAttribute("error", exception.getMessage());
        } catch (ResourceAccessException exception) {
            redirectAttributes.addFlashAttribute("error",
                    "No se pudo confirmar la respuesta de Billetera. La operación queda pendiente; revisa su estado antes de reintentar.");
        } catch (RestClientResponseException exception) {
            redirectAttributes.addFlashAttribute("error", "Billetera respondió con un error (HTTP "
                    + exception.getStatusCode().value()
                    + "). La operación queda pendiente; revisa su estado antes de reintentar.");
        }
        return "redirect:/";
    }

    @PostMapping("/recargar")
    public String recharge(
            @AuthenticationPrincipal AuthenticatedIdentity identity,
            HttpServletRequest request,
            @RequestParam String lineaId,
            @RequestParam String paqueteId,
            @RequestParam String operationId,
            RedirectAttributes redirectAttributes) {
        try {
            var recharge = recargasService.solicitarRecarga(
                    identity.id(), lineaId, paqueteId, operationId, token(request));
            redirectAttributes.addFlashAttribute("mensaje",
                    recharge.getEstado().equals("SIMULADA")
                            ? "Recarga simulada completada. El costo se descontó de tu tarjeta universal."
                            : "La operación no se completó. Revisa su estado antes de volver a intentarlo.");
        } catch (InsufficientWalletBalanceException exception) {
            redirectAttributes.addFlashAttribute("error", exception.getMessage());
        } catch (WalletClient.WalletNotFoundException exception) {
            redirectAttributes.addFlashAttribute("error", exception.getMessage());
        } catch (WalletClient.WalletFrozenException exception) {
            redirectAttributes.addFlashAttribute("error", exception.getMessage());
        } catch (IllegalArgumentException exception) {
            redirectAttributes.addFlashAttribute("error", exception.getMessage());
        } catch (ResourceAccessException exception) {
            redirectAttributes.addFlashAttribute("error",
                    "No se pudo confirmar la respuesta de Billetera. La operación sigue pendiente; vuelve a revisar su estado.");
        } catch (RestClientResponseException exception) {
            redirectAttributes.addFlashAttribute("error", "Billetera respondió con un error (HTTP "
                    + exception.getStatusCode().value()
                    + "). La operación sigue pendiente; vuelve a revisar su estado.");
        }
        return "redirect:/";
    }

    @PostMapping("/recargar/saldo")
    public String rechargeBalance(
            @AuthenticationPrincipal AuthenticatedIdentity identity,
            HttpServletRequest request,
            @RequestParam String operadorId,
            @RequestParam String lineaId,
            @RequestParam BigDecimal monto,
            @RequestParam String operationId,
            RedirectAttributes redirectAttributes) {
        try {
            var recharge = recargasService.solicitarRecargaDinero(
                    identity.id(), operadorId, lineaId, monto, operationId, token(request));
            redirectAttributes.addFlashAttribute("mensaje",
                    recharge.getEstado().equals("SIMULADA")
                            ? "Recarga simulada completada. No se envió saldo al operador."
                            : "La operación no se completó. Revisa su estado antes de volver a intentarlo.");
        } catch (InsufficientWalletBalanceException exception) {
            redirectAttributes.addFlashAttribute("error", exception.getMessage());
        } catch (WalletClient.WalletNotFoundException exception) {
            redirectAttributes.addFlashAttribute("error", exception.getMessage());
        } catch (WalletClient.WalletFrozenException exception) {
            redirectAttributes.addFlashAttribute("error", exception.getMessage());
        } catch (IllegalArgumentException exception) {
            redirectAttributes.addFlashAttribute("error", exception.getMessage());
        } catch (ResourceAccessException exception) {
            redirectAttributes.addFlashAttribute("error",
                    "No se pudo confirmar la respuesta de Billetera. La operación sigue pendiente; revisa su estado antes de reintentar.");
        } catch (RestClientResponseException exception) {
            redirectAttributes.addFlashAttribute("error", "Billetera respondió con un error (HTTP "
                    + exception.getStatusCode().value()
                    + "). La operación sigue pendiente; revisa su estado antes de reintentar.");
        }
        return "redirect:/";
    }

    @PostMapping("/recargar/{operationId}/reintentar")
    public String retryRecharge(
            @AuthenticationPrincipal AuthenticatedIdentity identity,
            HttpServletRequest request,
            @PathVariable String operationId,
            RedirectAttributes redirectAttributes) {
        try {
            var recharge = recargasService.reintentarRecarga(identity.id(), operationId, token(request));
            redirectAttributes.addFlashAttribute("mensaje", "Estado de recarga: " + recharge.getEstado());
        } catch (InsufficientWalletBalanceException exception) {
            redirectAttributes.addFlashAttribute("error", exception.getMessage());
        } catch (WalletClient.WalletNotFoundException exception) {
            redirectAttributes.addFlashAttribute("error", exception.getMessage());
        } catch (WalletClient.WalletFrozenException exception) {
            redirectAttributes.addFlashAttribute("error", exception.getMessage());
        } catch (IllegalArgumentException exception) {
            redirectAttributes.addFlashAttribute("error", exception.getMessage());
        }
        return "redirect:/";
    }

    private static String token(HttpServletRequest request) {
        Object value = request.getAttribute(RemoteAccessAuthenticationFilter.TOKEN_ATTRIBUTE);
        if (value instanceof String token && !token.isBlank()) {
            return token;
        }
        throw new IllegalStateException("No se encontró un token de Acceso válido para esta operación.");
    }
}

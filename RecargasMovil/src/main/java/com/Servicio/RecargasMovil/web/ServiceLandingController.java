package com.Servicio.RecargasMovil.web;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.Servicio.RecargasMovil.model.OperadorMovil;
import com.Servicio.RecargasMovil.model.PaqueteMovil;
import com.Servicio.RecargasMovil.model.RecargaMovil;
import com.Servicio.RecargasMovil.security.AuthenticatedIdentity;
import com.Servicio.RecargasMovil.security.RemoteAccessAuthenticationFilter;
import com.Servicio.RecargasMovil.service.RecargasService;
import com.Servicio.RecargasMovil.service.ReciboPdfGenerator;
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
            @RequestAttribute("_csrf") CsrfToken csrfToken,
            HttpServletRequest request,
            Model model) {
        csrfToken.getToken();
        model.addAttribute("_csrf", csrfToken);
        model.addAttribute("identity", identity);
        var operators = recargasService.operadoresActivos();
        var packages = recargasService.paquetesActivos();
        model.addAttribute("operadores", operators);
        model.addAttribute("paquetes", packages);
        model.addAttribute("operadoresPorId", operators.stream()
                .collect(Collectors.toMap(OperadorMovil::getId, OperadorMovil::getNombre)));
        model.addAttribute("customRechargeOperationId", UUID.randomUUID().toString());
        var userLines = recargasService.lineasDelUsuario(identity.id());
        model.addAttribute("lineas", userLines);
        model.addAttribute("recargas", recargasService.historialDelUsuario(identity.id()));
        model.addAttribute("operationIds", packages.stream()
                .collect(Collectors.toMap(PaqueteMovil::getId, ignored -> UUID.randomUUID().toString())));
        var activeOperatorIds = operators.stream()
                .map(OperadorMovil::getId)
                .collect(Collectors.toSet());
        model.addAttribute("lineasRecargables", userLines.stream()
                .filter(line -> activeOperatorIds.contains(line.getOperadorId()))
                .toList());
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
                    "No se pudo cargar el saldo de Billetera. Puedes continuar con el pago simulado de una recarga.");
        } catch (RestClientResponseException exception) {
            model.addAttribute("walletError", true);
            model.addAttribute("error", "No se pudo consultar Billetera (HTTP "
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
            @RequestParam String lineaId,
            @RequestParam String paqueteId,
            @RequestParam String operationId,
            RedirectAttributes redirectAttributes) {
        try {
            var recharge = recargasService.solicitarRecarga(
                    identity.id(), lineaId, paqueteId, operationId, identity.email());
            return "redirect:/transacciones/" + recharge.getOperacionId() + "?cargando=true";
        } catch (RecargasService.OperacionPendienteAnteriorException exception) {
            redirectAttributes.addFlashAttribute("error", exception.getMessage());
        } catch (IllegalArgumentException exception) {
            redirectAttributes.addFlashAttribute("error", exception.getMessage());
        } catch (ResourceAccessException exception) {
            redirectAttributes.addFlashAttribute("error",
                    "Pagos no está disponible. La operación queda pendiente y puedes revisar su estado sin crear otro pago.");
        } catch (RestClientResponseException exception) {
            redirectAttributes.addFlashAttribute("error", "Pagos respondió con un error (HTTP "
                    + exception.getStatusCode().value()
                    + "). La operación queda pendiente; vuelve a revisar su estado.");
        }
        return "redirect:/";
    }

    @PostMapping("/recargar/saldo")
    public String rechargeBalance(
            @AuthenticationPrincipal AuthenticatedIdentity identity,
            @RequestParam String lineaId,
            @RequestParam BigDecimal monto,
            @RequestParam String operationId,
            RedirectAttributes redirectAttributes) {
        try {
            var recharge = recargasService.solicitarRecargaDinero(
                    identity.id(), lineaId, monto, operationId, identity.email());
            return "redirect:/transacciones/" + recharge.getOperacionId() + "?cargando=true";
        } catch (RecargasService.OperacionPendienteAnteriorException exception) {
            redirectAttributes.addFlashAttribute("error", exception.getMessage());
        } catch (IllegalArgumentException exception) {
            redirectAttributes.addFlashAttribute("error", exception.getMessage());
        } catch (ResourceAccessException exception) {
            redirectAttributes.addFlashAttribute("error",
                    "Pagos no está disponible. La operación queda pendiente y puedes revisar su estado sin crear otro pago.");
        } catch (RestClientResponseException exception) {
            redirectAttributes.addFlashAttribute("error", "Pagos respondió con un error (HTTP "
                    + exception.getStatusCode().value()
                    + "). La operación queda pendiente; revisa su estado antes de reintentar.");
        }
        return "redirect:/";
    }

    @PostMapping("/recargar/{operationId}/reintentar")
    public String retryRecharge(
            @AuthenticationPrincipal AuthenticatedIdentity identity,
            @PathVariable String operationId,
            RedirectAttributes redirectAttributes) {
        try {
            var recharge = recargasService.reintentarRecarga(identity.id(), operationId, identity.email());
            return "redirect:/transacciones/" + recharge.getOperacionId();
        } catch (RecargasService.OperacionPendienteAnteriorException exception) {
            redirectAttributes.addFlashAttribute("error", exception.getMessage());
        } catch (IllegalArgumentException exception) {
            redirectAttributes.addFlashAttribute("error", exception.getMessage());
        } catch (ResourceAccessException exception) {
            redirectAttributes.addFlashAttribute("error",
                    "No se pudo consultar Pagos. La operación conserva su estado actual; inténtalo más tarde.");
        } catch (RestClientResponseException exception) {
            redirectAttributes.addFlashAttribute("error", "Pagos respondió con un error (HTTP "
                    + exception.getStatusCode().value() + ").");
        }
        return "redirect:/";
    }

    @GetMapping("/transacciones/{operationId}")
    public String transactionResult(
            @AuthenticationPrincipal AuthenticatedIdentity identity,
            @PathVariable String operationId,
            @RequestParam(defaultValue = "false") boolean cargando,
            @RequestAttribute("_csrf") CsrfToken csrfToken,
            Model model) {
        RecargaMovil recharge = buscarRecarga(identity.id(), operationId);
        boolean successful = RecargaMovil.STATUS_SIMULATED.equals(recharge.getEstado());
        boolean pending = RecargaMovil.STATUS_PAYMENT_PENDING.equals(recharge.getEstado())
                || RecargaMovil.STATUS_PROCESSING.equals(recharge.getEstado());
        boolean showLoading = pending || cargando;
        csrfToken.getToken();
        model.addAttribute("identity", identity);
        model.addAttribute("recarga", recharge);
        model.addAttribute("exitosa", successful);
        model.addAttribute("cargando", showLoading);
        model.addAttribute("menuUrl", urlMenuRecargas(recharge));
        model.addAttribute("_csrf", csrfToken);
        model.addAttribute("mensajeResultado", successful
                ? "Transacción exitosa. El pago simulado fue aprobado y la operación quedó registrada."
                : mensajeEstadoRecarga(recharge));
        model.addAttribute("reciboUrl", "/transacciones/" + operationId + "/recibo.pdf");
        return "resultado-transaccion";
    }

    @GetMapping("/transacciones/{operationId}/recibo.pdf")
    public ResponseEntity<byte[]> downloadReceipt(
            @AuthenticationPrincipal AuthenticatedIdentity identity,
            @PathVariable String operationId) {
        RecargaMovil recharge = buscarRecarga(identity.id(), operationId);
        List<String> lines = List.of(
                "DIGITRANS | RECARGAS MOVILES",
                "RECIBO DE TRANSACCION",
                "Estado: " + estadoRecarga(recharge),
                "Operacion: " + safeText(recharge.getOperacionId(), "No disponible"),
                "Fecha: " + fechaRecarga(recharge),
                "Cliente: " + safeText(identity.name(), "No disponible"),
                "Operador: " + safeText(recharge.getOperador(), "No disponible"),
                "Telefono: " + safeText(recharge.getTelefono(), "No disponible"),
                "Paquete: " + safeText(recharge.getPaquete(), "No disponible"),
                "Valor: COP " + (recharge.getMonto() == null ? "0" : recharge.getMonto().toPlainString()),
                "Estado de pago: " + safeText(recharge.getEstadoPago(), "No disponible"),
                "Referencia de pago: " + safeText(recharge.getIdPagoProveedor(), "No disponible"),
                "Recibo de operación simulada. No se envió una recarga al operador ni se activó el paquete.");
        byte[] pdf = ReciboPdfGenerator.generar(lines);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=recibo-recargas-" + safeFilename(operationId) + ".pdf")
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(pdf);
    }

    private RecargaMovil buscarRecarga(String usuarioId, String operationId) {
        return recargasService.historialDelUsuario(usuarioId).stream()
                .filter(item -> operationId.equals(item.getOperacionId()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private static String estadoRecarga(RecargaMovil recharge) {
        if (RecargaMovil.STATUS_SIMULATED.equals(recharge.getEstado())) {
            return "EXITOSA (SIMULADA)";
        }
        if (RecargaMovil.STATUS_REJECTED.equals(recharge.getEstado())) {
            return "RECHAZADA";
        }
        if (RecargaMovil.STATUS_PAYMENT_PENDING.equals(recharge.getEstado())) {
            return "PENDIENTE";
        }
        return safeText(recharge.getEstado(), "EN PROCESO");
    }

    private static String mensajeEstadoRecarga(RecargaMovil recharge) {
        if (RecargaMovil.STATUS_REJECTED.equals(recharge.getEstado())) {
            return "El pago fue rechazado o cancelado. La recarga no se completó.";
        }
        if (RecargaMovil.STATUS_PAYMENT_PENDING.equals(recharge.getEstado())
                || RecargaMovil.STATUS_PROCESSING.equals(recharge.getEstado())) {
            return "La operación sigue pendiente. Puedes consultar nuevamente su estado desde el historial.";
        }
        return "La operación quedó registrada.";
    }

    private static String fechaRecarga(RecargaMovil recharge) {
        return recharge.getFecha() == null ? "No disponible"
                : recharge.getFecha().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"));
    }

    private static String safeText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String urlMenuRecargas(RecargaMovil recharge) {
        String description = recharge.getPaquete();
        return description != null && description.startsWith("Recarga de saldo $")
                ? "/#recargar-saldo"
                : "/#paquetes";
    }

    private static String safeFilename(String value) {
        return value.replaceAll("[^A-Za-z0-9_-]", "_");
    }

    private static String token(HttpServletRequest request) {
        Object value = request.getAttribute(RemoteAccessAuthenticationFilter.TOKEN_ATTRIBUTE);
        if (value instanceof String token && !token.isBlank()) {
            return token;
        }
        throw new IllegalStateException("No se encontró un token de Acceso válido para esta operación.");
    }
}

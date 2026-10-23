package com.Servicio.Billetera.web;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.Servicio.Billetera.model.BilleteraUniversal;
import com.Servicio.Billetera.model.MovimientoBilletera;
import com.Servicio.Billetera.security.AuthenticatedIdentity;
import com.Servicio.Billetera.service.BilleteraService;
import com.Servicio.Billetera.service.SaldoInsuficienteException;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Controller
@Validated
public class BilleteraController {

    private final BilleteraService billeteraService;
    private final String accesoUrl;

    public BilleteraController(
            BilleteraService billeteraService,
            @Value("${app.acceso.base-url}") String accesoUrl) {
        this.billeteraService = billeteraService;
        this.accesoUrl = accesoUrl;
    }

    @GetMapping("/")
    public String index(@AuthenticationPrincipal AuthenticatedIdentity identity, Model model) {
        BilleteraUniversal wallet = billeteraService.obtener(identity.id());
        model.addAttribute("identity", identity);
        model.addAttribute("wallet", wallet);
        model.addAttribute("movimientos", billeteraService.historial(identity.id()));
        model.addAttribute("accesoUrl", accesoUrl);
        return "billetera";
    }

    @PostMapping("/saldo/abonar")
    public String addSimulatedBalance(
            @AuthenticationPrincipal AuthenticatedIdentity identity,
            @RequestParam @NotNull @DecimalMin("1.00") @DecimalMax("1000000.00") BigDecimal monto,
            @RequestParam String metodoPago,
            RedirectAttributes redirectAttributes) {
        try {
            billeteraService.abonarSimulado(
                    identity.id(), monto, UUID.randomUUID().toString(), metodoPago);
            redirectAttributes.addFlashAttribute("mensaje",
                    "Carga simulada con " + metodoPagoLegible(metodoPago)
                            + " completada. No se realizó ningún pago real.");
        } catch (IllegalArgumentException exception) {
            redirectAttributes.addFlashAttribute("error", exception.getMessage());
        }
        return "redirect:/";
    }

    private static String metodoPagoLegible(String metodoPago) {
        return "TARJETA_DEBITO".equals(metodoPago) ? "tarjeta débito" : "PSE";
    }

    @RestController
    @RequestMapping("/api/wallet/me")
    public static class BilleteraApiController {

        private final BilleteraService billeteraService;

        public BilleteraApiController(BilleteraService billeteraService) {
            this.billeteraService = billeteraService;
        }

        @GetMapping
        public WalletResponse wallet(@AuthenticationPrincipal AuthenticatedIdentity identity) {
            return WalletResponse.from(billeteraService.obtener(identity.id()));
        }

        @GetMapping("/movements")
        public List<MovementResponse> movements(@AuthenticationPrincipal AuthenticatedIdentity identity) {
            return billeteraService.historial(identity.id()).stream()
                    .map(MovementResponse::from)
                    .toList();
        }

        @PostMapping("/debits")
        public ResponseEntity<?> debit(
                @AuthenticationPrincipal AuthenticatedIdentity identity,
                @Valid @RequestBody DebitRequest request) {
            try {
                BilleteraUniversal wallet = billeteraService.debitar(
                        identity.id(), request.amount(), request.operationId(), request.description());
                return ResponseEntity.ok(WalletResponse.from(wallet));
            } catch (SaldoInsuficienteException exception) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(new ApiError(exception.getMessage()));
            } catch (IllegalArgumentException exception) {
                return ResponseEntity.badRequest().body(new ApiError(exception.getMessage()));
            }
        }
    }

    public record DebitRequest(
            @NotBlank @Size(max = 100) String operationId,
            @NotNull @DecimalMin("0.01") @DecimalMax("1000000.00") BigDecimal amount,
            @NotBlank @Size(max = 160) String description) {
    }

    public record WalletResponse(String userId, String cardNumber, BigDecimal balance) {
        private static WalletResponse from(BilleteraUniversal wallet) {
            return new WalletResponse(wallet.getUsuarioId(), wallet.getNumeroTarjeta(), wallet.getSaldo());
        }
    }

    public record MovementResponse(
            String operationId, String type, String description, BigDecimal amount, java.time.LocalDateTime date) {
        private static MovementResponse from(MovimientoBilletera movement) {
            return new MovementResponse(movement.getOperacionId(), movement.getTipo(),
                    movement.getDescripcion(), movement.getMonto(), movement.getFecha());
        }
    }

    public record ApiError(String message) {
    }
}

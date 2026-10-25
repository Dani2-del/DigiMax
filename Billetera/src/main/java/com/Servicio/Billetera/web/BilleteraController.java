package com.Servicio.Billetera.web;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.Optional;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
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
import com.Servicio.Billetera.model.DisenoTarjeta;
import com.Servicio.Billetera.model.EstadoTarjeta;
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
        Optional<BilleteraUniversal> wallet = billeteraService.buscar(identity.id());
        model.addAttribute("identity", identity);
        model.addAttribute("wallet", wallet.orElse(null));
        model.addAttribute("walletExists", wallet.isPresent());
        model.addAttribute("disenos", DisenoTarjeta.values());
        model.addAttribute("puedeAdministrar",
                SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                        .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority())
                                || "MODERATOR_BILLETERA".equals(authority.getAuthority())));
        model.addAttribute("movimientos", wallet.map(BilleteraUniversal::getMovimientos)
                .map(movimientos -> movimientos.stream()
                        .sorted(Comparator.comparing(MovimientoBilletera::getFecha,
                                Comparator.nullsLast(Comparator.reverseOrder())))
                        .toList())
                .orElse(List.of()));
        model.addAttribute("accesoUrl", accesoUrl);
        model.addAttribute("adminUrl", "/admin/dashboard");
        return "billetera";
    }

    @PostMapping("/tarjeta/crear")
    public String createWallet(
            @AuthenticationPrincipal AuthenticatedIdentity identity,
            RedirectAttributes redirectAttributes) {
        billeteraService.crear(identity.id());
        redirectAttributes.addFlashAttribute("mensaje",
                "Tu tarjeta Digitrans se creó correctamente.");
        return "redirect:/";
    }

    @PostMapping("/tarjeta/congelar")
    public String freezeWallet(
            @AuthenticationPrincipal AuthenticatedIdentity identity,
            RedirectAttributes redirectAttributes) {
        try {
            billeteraService.congelar(identity.id());
            redirectAttributes.addFlashAttribute("mensaje",
                    "Tu tarjeta está congelada. No se pueden realizar transacciones mientras permanezca así.");
        } catch (BilleteraService.WalletNotFoundException exception) {
            redirectAttributes.addFlashAttribute("error", exception.getMessage());
        }
        return "redirect:/";
    }

    @PostMapping("/tarjeta/descongelar")
    public String unfreezeWallet(
            @AuthenticationPrincipal AuthenticatedIdentity identity,
            RedirectAttributes redirectAttributes) {
        try {
            billeteraService.descongelar(identity.id());
            redirectAttributes.addFlashAttribute("mensaje", "Tu tarjeta Digitrans está activa nuevamente.");
        } catch (BilleteraService.WalletNotFoundException exception) {
            redirectAttributes.addFlashAttribute("error", exception.getMessage());
        }
        return "redirect:/";
    }

    @PostMapping("/tarjeta/desactivar")
    public String deactivateWallet(
            @AuthenticationPrincipal AuthenticatedIdentity identity,
            @RequestParam(defaultValue = "false") boolean confirmacionFinal,
            RedirectAttributes redirectAttributes) {
        if (!confirmacionFinal) {
            redirectAttributes.addFlashAttribute("error",
                    "Confirma la desactivación de tu tarjeta antes de continuar.");
            return "redirect:/";
        }
        try {
            billeteraService.desactivar(identity.id());
            redirectAttributes.addFlashAttribute("mensaje",
                    "La tarjeta fue desactivada. Su saldo e historial se conservaron y se trasladarán cuando crees otra.");
        } catch (BilleteraService.WalletNotFoundException exception) {
            redirectAttributes.addFlashAttribute("error", exception.getMessage());
        }
        return "redirect:/";
    }

    @PostMapping("/tarjeta/diseno")
    public String changeCardDesign(
            @AuthenticationPrincipal AuthenticatedIdentity identity,
            @RequestParam DisenoTarjeta diseno,
            RedirectAttributes redirectAttributes) {
        try {
            billeteraService.cambiarDiseno(identity.id(), diseno);
            redirectAttributes.addFlashAttribute("mensaje", "Se actualizó el diseño de tu tarjeta.");
        } catch (IllegalArgumentException | BilleteraService.WalletNotFoundException exception) {
            redirectAttributes.addFlashAttribute("error", exception.getMessage());
        }
        return "redirect:/";
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
        } catch (BilleteraService.WalletNotFoundException exception) {
            redirectAttributes.addFlashAttribute("error",
                    "Activa tu tarjeta Digitrans antes de cargar saldo.");
        } catch (BilleteraService.WalletFrozenException exception) {
            redirectAttributes.addFlashAttribute("error", exception.getMessage());
        }
        return "redirect:/";
    }

    private static String metodoPagoLegible(String metodoPago) {
        return switch (metodoPago) {
            case "TARJETA_DEBITO" -> "tarjeta débito";
            case "TARJETA_CREDITO_DEBITO" -> "tarjeta crédito/débito";
            default -> "PSE";
        };
    }

    @RestController
    @RequestMapping("/api/wallet/me")
    public static class BilleteraApiController {

        private final BilleteraService billeteraService;

        public BilleteraApiController(BilleteraService billeteraService) {
            this.billeteraService = billeteraService;
        }

        @GetMapping
        public ResponseEntity<?> wallet(@AuthenticationPrincipal AuthenticatedIdentity identity) {
            return billeteraService.buscar(identity.id())
                    .<ResponseEntity<?>>map(wallet -> ResponseEntity.ok(WalletResponse.from(wallet)))
                    .orElseGet(() -> ResponseEntity.notFound().build());
        }

        @PostMapping
        public WalletResponse createWallet(@AuthenticationPrincipal AuthenticatedIdentity identity) {
            return WalletResponse.from(billeteraService.crear(identity.id()));
        }

        @PostMapping("/freeze")
        public ResponseEntity<?> freeze(
                @AuthenticationPrincipal AuthenticatedIdentity identity) {
            try {
                return ResponseEntity.ok(WalletResponse.from(billeteraService.congelar(identity.id())));
            } catch (BilleteraService.WalletNotFoundException exception) {
                return ResponseEntity.notFound().build();
            }
        }

        @PostMapping("/unfreeze")
        public ResponseEntity<?> unfreeze(
                @AuthenticationPrincipal AuthenticatedIdentity identity) {
            try {
                return ResponseEntity.ok(WalletResponse.from(billeteraService.descongelar(identity.id())));
            } catch (BilleteraService.WalletNotFoundException exception) {
                return ResponseEntity.notFound().build();
            }
        }

        @PostMapping("/deactivate")
        public ResponseEntity<?> deactivate(
                @AuthenticationPrincipal AuthenticatedIdentity identity) {
            try {
                billeteraService.desactivar(identity.id());
                return ResponseEntity.noContent().build();
            } catch (BilleteraService.WalletNotFoundException exception) {
                return ResponseEntity.notFound().build();
            }
        }

        @PostMapping("/design")
        public ResponseEntity<?> changeDesign(
                @AuthenticationPrincipal AuthenticatedIdentity identity,
                @Valid @RequestBody DesignRequest request) {
            try {
                return ResponseEntity.ok(
                        WalletResponse.from(billeteraService.cambiarDiseno(identity.id(), request.design())));
            } catch (IllegalArgumentException exception) {
                return ResponseEntity.badRequest().body(new ApiError(exception.getMessage()));
            } catch (BilleteraService.WalletNotFoundException exception) {
                return ResponseEntity.notFound().build();
            }
        }

        @GetMapping("/movements")
        public ResponseEntity<?> movements(@AuthenticationPrincipal AuthenticatedIdentity identity) {
            return billeteraService.buscar(identity.id())
                    .<ResponseEntity<?>>map(wallet -> ResponseEntity.ok(wallet.getMovimientos().stream()
                            .map(MovementResponse::from)
                            .toList()))
                    .orElseGet(() -> ResponseEntity.notFound().build());
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
            } catch (BilleteraService.WalletNotFoundException exception) {
                return ResponseEntity.notFound().build();
            } catch (BilleteraService.WalletFrozenException exception) {
                return ResponseEntity.status(HttpStatus.LOCKED)
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

    public record DesignRequest(@NotNull DisenoTarjeta design) {
    }

    public record WalletResponse(
            String userId, String cardNumber, BigDecimal balance, EstadoTarjeta status, DisenoTarjeta design) {
        private static WalletResponse from(BilleteraUniversal wallet) {
            return new WalletResponse(wallet.getUsuarioId(), wallet.getNumeroTarjeta(), wallet.getSaldo(),
                    wallet.getEstado(), wallet.getDiseno());
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

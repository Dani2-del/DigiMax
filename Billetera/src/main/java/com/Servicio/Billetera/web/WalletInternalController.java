package com.Servicio.Billetera.web;

import java.math.BigDecimal;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.Servicio.Billetera.model.BilleteraUniversal;
import com.Servicio.Billetera.model.EstadoTarjeta;
import com.Servicio.Billetera.service.BilleteraService;
import com.Servicio.Billetera.service.SaldoInsuficienteException;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Service-to-service debit API. It knows wallets and balances, not RFID tags. */
@RestController
@RequestMapping("/internal/wallets")
public class WalletInternalController {

    private final BilleteraService billeteraService;

    public WalletInternalController(BilleteraService billeteraService) {
        this.billeteraService = billeteraService;
    }

    @PostMapping("/{userId}/debits")
    public ResponseEntity<?> debit(
            @PathVariable String userId,
            @Valid @RequestBody DebitRequest request) {
        try {
            BilleteraUniversal wallet = billeteraService.debitar(
                    userId, request.amount(), request.operationId(), request.description());
            return ResponseEntity.ok(new DebitResponse(
                    wallet.getUsuarioId(), wallet.getNumeroTarjeta(), wallet.getSaldo(),
                    wallet.getEstado().name(), wallet.getDiseno().name()));
        } catch (SaldoInsuficienteException exception) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new BilleteraController.ApiError(exception.getMessage()));
        } catch (BilleteraService.WalletNotFoundException exception) {
            return ResponseEntity.notFound().build();
        } catch (BilleteraService.WalletFrozenException exception) {
            return ResponseEntity.status(HttpStatus.LOCKED)
                    .body(new BilleteraController.ApiError(exception.getMessage()));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest()
                    .body(new BilleteraController.ApiError(exception.getMessage()));
        }
    }

    public record DebitRequest(
            @NotBlank @Size(max = 100) String operationId,
            @NotNull @DecimalMin("0.01") @DecimalMax("1000000.00") BigDecimal amount,
            @NotBlank @Size(max = 160) String description) { }

    public record DebitResponse(
            String userId, String cardNumber, BigDecimal balance, String status, String design) { }
}

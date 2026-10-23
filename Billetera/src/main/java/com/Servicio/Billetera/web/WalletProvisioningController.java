package com.Servicio.Billetera.web;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.Servicio.Billetera.model.BilleteraUniversal;
import com.Servicio.Billetera.service.BilleteraService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/internal/wallets")
public class WalletProvisioningController {

    private final BilleteraService billeteraService;
    private final String provisioningKey;

    public WalletProvisioningController(
            BilleteraService billeteraService,
            @Value("${app.internal.provisioning-key:}") String provisioningKey) {
        this.billeteraService = billeteraService;
        this.provisioningKey = provisioningKey;
    }

    @PostMapping
    public ResponseEntity<?> provision(
            @RequestHeader(name = "X-Billetera-Provisioning-Key", required = false) String requestKey,
            @Valid @RequestBody ProvisionRequest request) {
        if (provisioningKey.isBlank() || requestKey == null
                || !MessageDigest.isEqual(
                        provisioningKey.getBytes(StandardCharsets.UTF_8),
                        requestKey.getBytes(StandardCharsets.UTF_8))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new ApiError("Credencial de aprovisionamiento inválida."));
        }
        BilleteraUniversal wallet = billeteraService.obtener(request.userId());
        return ResponseEntity.ok(new ProvisionResponse(wallet.getUsuarioId(), wallet.getNumeroTarjeta()));
    }

    public record ProvisionRequest(@NotBlank @Size(max = 100) String userId) {
    }

    public record ProvisionResponse(String userId, String cardNumber) {
    }

    public record ApiError(String message) {
    }
}

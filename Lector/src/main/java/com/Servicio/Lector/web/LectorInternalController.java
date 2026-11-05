package com.Servicio.Lector.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import java.math.BigDecimal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.ResourceAccessException;

import com.Servicio.Lector.model.TarjetaRfid;
import com.Servicio.Lector.service.RfidService;
import com.Servicio.Lector.service.RfidService.NoAvailableCardException;
import com.Servicio.Lector.service.RfidService.CardOwnerMismatchException;
import com.Servicio.Lector.service.RfidService.ScanResult;
import com.Servicio.Lector.service.RfidService.UnlinkedTagException;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Private service-to-service endpoints used by Billetera. */
@RestController
@RequestMapping("/internal/rfid")
public class LectorInternalController {

    private final RfidService rfidService;

    public LectorInternalController(RfidService rfidService) {
        this.rfidService = rfidService;
    }

    @PostMapping("/profiles/{userId}/claim")
    public ResponseEntity<?> claim(@PathVariable String userId) {
        try {
            return ResponseEntity.ok(ProfileResponse.from(rfidService.reclamar(userId)));
        } catch (NoAvailableCardException exception) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ErrorResponse("No hay tarjetas RFID disponibles en este momento."));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(new ErrorResponse(exception.getMessage()));
        }
    }

    @GetMapping("/profiles/{userId}")
    public ResponseEntity<?> profile(@PathVariable String userId) {
        try {
            return ResponseEntity.ok(ProfileResponse.from(rfidService.perfil(userId)));
        } catch (UnlinkedTagException exception) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/profiles/{userId}")
    public ResponseEntity<?> unlink(@PathVariable String userId) {
        try {
            rfidService.desvincular(userId);
            return ResponseEntity.noContent().build();
        } catch (UnlinkedTagException exception) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/inventory")
    public ResponseEntity<?> register(@Valid @RequestBody InventoryRequest request) {
        try {
            TarjetaRfid card = rfidService.registrarDisponible(request.uid());
            return ResponseEntity.ok(ProfileResponse.from(card));
        } catch (RfidService.TagAlreadyLinkedException exception) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ErrorResponse("Ese UID ya está asignado a un usuario."));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(new ErrorResponse(exception.getMessage()));
        }
    }

    @PostMapping("/lecturas")
    public ResponseEntity<?> readForTransport(@Valid @RequestBody TransportReadRequest request) {
        try {
            ScanResult result = rfidService.cobrar(request.uid(), request.sistema(),
                    request.operationId(), request.userId(), request.monto());
            return ResponseEntity.ok(result);
        } catch (CardOwnerMismatchException exception) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new ErrorResponse("La tarjeta RFID pertenece a otra cuenta."));
        } catch (UnlinkedTagException exception) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse("La tarjeta RFID no está vinculada a una cuenta."));
        } catch (RfidService.RepeatedReadException exception) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(new ErrorResponse("Espera un momento antes de volver a presentar la tarjeta."));
        } catch (RfidService.DebitRejectedException exception) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ErrorResponse(exception.getMessage()));
        } catch (RfidService.WalletNotFoundException exception) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse("No se encontró una Billetera activa para el titular."));
        } catch (ResourceAccessException exception) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(new ErrorResponse("No se pudo conectar con Billetera."));
        } catch (IllegalStateException exception) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(new ErrorResponse(exception.getMessage()));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(new ErrorResponse(exception.getMessage()));
        }
    }

    public record InventoryRequest(@NotBlank @Size(max = 40) String uid) { }
    public record TransportReadRequest(@NotBlank @Size(max = 40) String uid,
            @NotBlank @Size(max = 64) String userId,
            @NotBlank @Size(max = 64) String operationId,
            @NotBlank @Size(max = 20) String sistema,
            @jakarta.validation.constraints.NotNull
            @jakarta.validation.constraints.DecimalMin("0.01")
            @jakarta.validation.constraints.DecimalMax("1000000") BigDecimal monto) { }
    public record ProfileResponse(String uid, String userId, boolean active, boolean available) {
        static ProfileResponse from(TarjetaRfid card) {
            return new ProfileResponse(card.getUid(), card.getUsuarioId(), card.isActiva(), card.isDisponible());
        }
    }
    public record ErrorResponse(String message) { }
}

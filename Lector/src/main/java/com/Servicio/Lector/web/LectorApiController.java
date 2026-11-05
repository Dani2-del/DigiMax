package com.Servicio.Lector.web;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.ResourceAccessException;

import com.Servicio.Lector.model.TarjetaRfid;
import com.Servicio.Lector.service.RfidService;
import com.Servicio.Lector.service.RfidService.DebitRejectedException;
import com.Servicio.Lector.service.RfidService.RepeatedReadException;
import com.Servicio.Lector.service.RfidService.ScanResult;
import com.Servicio.Lector.service.RfidService.TagAlreadyLinkedException;
import com.Servicio.Lector.service.RfidService.UnlinkedTagException;
import com.Servicio.Lector.service.RfidService.WalletNotFoundException;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/rfid")
public class LectorApiController {

    private final RfidService rfidService;

    public LectorApiController(RfidService rfidService) {
        this.rfidService = rfidService;
    }

    @GetMapping("/tarjetas")
    public List<TarjetaRfid> tarjetas() {
        return rfidService.listarTarjetas();
    }

    @PostMapping("/inventario")
    public ResponseEntity<?> registrar(@Valid @RequestBody InventoryRequest request) {
        try {
            TarjetaRfid card = rfidService.registrarDisponible(request.uid());
            return ResponseEntity.ok(new MessageResponse(
                    "Tarjeta agregada al inventario disponible.", card.getUid(), card.getUsuarioId()));
        } catch (TagAlreadyLinkedException exception) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ErrorResponse("Ese UID ya está vinculado a otro usuario."));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(new ErrorResponse(exception.getMessage()));
        }
    }

    @PostMapping("/lecturas")
    public ResponseEntity<?> leer(@Valid @RequestBody ScanRequest request) {
        try {
            return ResponseEntity.ok(rfidService.cobrar(request.uid(), request.sistema(), null, null));
        } catch (RepeatedReadException exception) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(new ErrorResponse("Ya se leyó esa tarjeta. Espera un momento antes de volver a acercarla."));
        } catch (UnlinkedTagException exception) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse("Esta tarjeta todavía no está vinculada a un usuario."));
        } catch (WalletNotFoundException exception) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse("No se encontró una billetera activa para esta tarjeta."));
        } catch (DebitRejectedException exception) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ErrorResponse(exception.getMessage()));
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
    public record ScanRequest(@NotBlank @Size(max = 40) String uid, String sistema) { }
    public record MessageResponse(String message, String uid, String userId) { }
    public record ErrorResponse(String message) { }
}

package com.Servicio.Pagos.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.Servicio.Pagos.dominio.EstadoPago;
import com.Servicio.Pagos.proveedor.ProveedorPago;
import com.Servicio.Pagos.proveedor.ProveedorSimulado;
import com.Servicio.Pagos.proveedor.ResultadoInicio;
import com.Servicio.Pagos.proveedor.SimuladorPago;
import com.Servicio.Pagos.proveedor.SolicitudPago;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/pagos")
public class PagosController {

    private final ProveedorPago proveedorPago;
    private final ObjectProvider<SimuladorPago> simuladorPagoProvider;

    public PagosController(
            ProveedorPago proveedorPago, ObjectProvider<SimuladorPago> simuladorPagoProvider) {
        this.proveedorPago = proveedorPago;
        this.simuladorPagoProvider = simuladorPagoProvider;
    }

    @PostMapping
    public ResponseEntity<ResultadoInicio> iniciar(@Valid @RequestBody SolicitudPago solicitud) {
        return ResponseEntity.status(HttpStatus.CREATED).body(proveedorPago.iniciar(solicitud));
    }

    @GetMapping("/{idProveedor}")
    public ResponseEntity<?> consultar(@PathVariable String idProveedor) {
        try {
            EstadoPago estado = proveedorPago.consultar(idProveedor);
            return ResponseEntity.ok(new EstadoPagoResponse(idProveedor, estado));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiError(HttpStatus.NOT_FOUND.value(), exception.getMessage()));
        }
    }

    @PostMapping("/{idProveedor}/aprobar")
    public ResponseEntity<?> aprobar(@PathVariable String idProveedor) {
        return simularResultado(idProveedor, EstadoPago.APROBADO);
    }

    @PostMapping("/{idProveedor}/rechazar")
    public ResponseEntity<?> rechazar(@PathVariable String idProveedor) {
        return simularResultado(idProveedor, EstadoPago.RECHAZADO);
    }

    @PostMapping("/{idProveedor}/cancelar")
    public ResponseEntity<?> cancelar(@PathVariable String idProveedor) {
        return simularResultado(idProveedor, EstadoPago.CANCELADO);
    }

    private ResponseEntity<?> simularResultado(String idProveedor, EstadoPago estadoFinal) {
        SimuladorPago simulador = simuladorPagoProvider.getIfAvailable();
        if (simulador == null) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(new ApiError(HttpStatus.SERVICE_UNAVAILABLE.value(),
                            "La simulación de pagos no está habilitada."));
        }
        try {
            EstadoPago estado = simulador.simularResultado(idProveedor, estadoFinal);
            return ResponseEntity.ok(new EstadoPagoResponse(idProveedor, estado));
        } catch (ProveedorSimulado.PagoNoEncontradoException exception) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiError(HttpStatus.NOT_FOUND.value(), exception.getMessage()));
        } catch (ProveedorSimulado.TransicionPagoInvalidaException exception) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ApiError(HttpStatus.CONFLICT.value(), exception.getMessage()));
        }
    }

    public record EstadoPagoResponse(String idProveedor, EstadoPago estado) {
    }

    public record ApiError(int status, String message) {
    }
}

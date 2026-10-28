package com.Servicio.Pagos.web;

import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.Servicio.Pagos.proveedor.ProveedorSimulado;

@RestControllerAdvice
public class PagosExceptionHandler {

    @ExceptionHandler(ProveedorSimulado.ReferenciaPagoDuplicadaException.class)
    public ResponseEntity<PagosController.ApiError> manejarReferenciaDuplicada(
            ProveedorSimulado.ReferenciaPagoDuplicadaException exception) {
        return error(HttpStatus.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<PagosController.ApiError> manejarValidacion(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .distinct()
                .collect(Collectors.joining("; "));
        return error(HttpStatus.BAD_REQUEST, message);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<PagosController.ApiError> manejarJsonInvalido(
            HttpMessageNotReadableException exception) {
        return error(HttpStatus.BAD_REQUEST, "El cuerpo de la solicitud no contiene un JSON válido.");
    }

    private ResponseEntity<PagosController.ApiError> error(HttpStatus status, String message) {
        return ResponseEntity.status(status)
                .body(new PagosController.ApiError(status.value(), message));
    }
}

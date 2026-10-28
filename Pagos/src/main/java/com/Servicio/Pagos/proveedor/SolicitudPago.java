package com.Servicio.Pagos.proveedor;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SolicitudPago(
        @NotBlank
        @Size(max = 100)
        String referencia,
        @NotNull
        @DecimalMin(value = "0.00", inclusive = false)
        @Digits(integer = 12, fraction = 2)
        BigDecimal monto,
        @NotBlank
        @Pattern(regexp = "[A-Z]{3}", message = "debe ser un código de moneda de 3 letras mayúsculas")
        String moneda,
        @NotBlank
        @Size(max = 250)
        String descripcion,
        @NotBlank
        @Email
        @Size(max = 254)
        String pagador) {
}

package com.Servicio.Pagos.proveedor;

import com.Servicio.Pagos.dominio.EstadoPago;

public record ResultadoInicio(
        String idProveedor,
        EstadoPago estado,
        String urlPago) {
}

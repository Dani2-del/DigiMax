package com.Servicio.Pagos.proveedor;

import com.Servicio.Pagos.dominio.EstadoPago;

public interface SimuladorPago {

    EstadoPago simularResultado(String idProveedor, EstadoPago estadoFinal);
}

package com.Servicio.Pagos.proveedor;

import com.Servicio.Pagos.dominio.EstadoPago;

public interface ProveedorPago {

    String nombre();

    ResultadoInicio iniciar(SolicitudPago solicitud);

    EstadoPago consultar(String idProveedor);
}

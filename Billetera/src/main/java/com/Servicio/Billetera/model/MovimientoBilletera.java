package com.Servicio.Billetera.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class MovimientoBilletera {

    private String operacionId;
    private String tipo;
    private String descripcion;
    private String servicio;
    private BigDecimal monto;
    private LocalDateTime fecha;
    private String idPagoProveedor;
    private String estadoPago;
    private String metodoPago;
    private String correoPagador;

    public MovimientoBilletera() {
    }

    public MovimientoBilletera(String operacionId, String tipo, String descripcion,
            BigDecimal monto, LocalDateTime fecha) {
        this.operacionId = operacionId;
        this.tipo = tipo;
        this.descripcion = descripcion;
        this.monto = monto;
        this.fecha = fecha;
    }

    public String getOperacionId() { return operacionId; }
    public void setOperacionId(String operacionId) { this.operacionId = operacionId; }

    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }

    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }

    public String getServicio() {
        if (servicio != null && !servicio.isBlank()) {
            return servicio;
        }
        String detalle = descripcion == null ? "" : descripcion.toLowerCase(java.util.Locale.ROOT);
        if (detalle.contains("transcaribe")) return "Transcaribe";
        if (detalle.contains("transmilenio")) return "TransMilenio";
        if (detalle.contains("transmetro")) return "Transmetro";
        if (detalle.contains("paquete")) return "Paquetes y recargas";
        if (detalle.contains("pasaje")) return "Transporte";
        if ("ABONO".equalsIgnoreCase(tipo) || "ABONO_PENDIENTE".equalsIgnoreCase(tipo)
                || "ABONO_RECHAZADO".equalsIgnoreCase(tipo)) return "Billetera";
        if ("AJUSTE_ADMIN".equalsIgnoreCase(tipo) || "RETIRO_ADMIN".equalsIgnoreCase(tipo)
                || "CAMBIO_ESTADO".equalsIgnoreCase(tipo)) return "Soporte Billetera";
        return "Otros";
    }
    public void setServicio(String servicio) { this.servicio = servicio; }

    public String getCategoria() {
        if ("ABONO".equalsIgnoreCase(tipo)) return "RECARGA";
        if ("ABONO_PENDIENTE".equalsIgnoreCase(tipo)) return "PENDIENTE";
        if ("ABONO_RECHAZADO".equalsIgnoreCase(tipo)) return "RECHAZADA";
        if ("AJUSTE_ADMIN".equalsIgnoreCase(tipo)) return "AJUSTE";
        if ("RETIRO_ADMIN".equalsIgnoreCase(tipo)) return "AJUSTE";
        if ("CAMBIO_ESTADO".equalsIgnoreCase(tipo)) return "SOPORTE";
        if ("CONSUMO".equalsIgnoreCase(tipo)) return "PAGO";
        return tipo == null ? "OTRO" : tipo;
    }

    public BigDecimal getMonto() { return monto; }
    public void setMonto(BigDecimal monto) { this.monto = monto; }

    public LocalDateTime getFecha() { return fecha; }
    public void setFecha(LocalDateTime fecha) { this.fecha = fecha; }

    public String getIdPagoProveedor() { return idPagoProveedor; }
    public void setIdPagoProveedor(String idPagoProveedor) { this.idPagoProveedor = idPagoProveedor; }

    public String getEstadoPago() { return estadoPago; }
    public void setEstadoPago(String estadoPago) { this.estadoPago = estadoPago; }

    public String getMetodoPago() { return metodoPago; }
    public void setMetodoPago(String metodoPago) { this.metodoPago = metodoPago; }

    public String getCorreoPagador() { return correoPagador; }
    public void setCorreoPagador(String correoPagador) { this.correoPagador = correoPagador; }
}

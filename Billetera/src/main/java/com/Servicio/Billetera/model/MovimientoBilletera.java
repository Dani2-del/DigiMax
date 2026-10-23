package com.Servicio.Billetera.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class MovimientoBilletera {

    private String operacionId;
    private String tipo;
    private String descripcion;
    private BigDecimal monto;
    private LocalDateTime fecha;

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

    public BigDecimal getMonto() { return monto; }
    public void setMonto(BigDecimal monto) { this.monto = monto; }

    public LocalDateTime getFecha() { return fecha; }
    public void setFecha(LocalDateTime fecha) { this.fecha = fecha; }
}

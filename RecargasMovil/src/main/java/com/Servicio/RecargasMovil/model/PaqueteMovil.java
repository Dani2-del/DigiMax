package com.Servicio.RecargasMovil.model;

import java.math.BigDecimal;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "paquetes_movil")
public class PaqueteMovil {

    @Id
    private String id;

    private String operadorId;
    private String nombre;
    private String descripcion;
    private BigDecimal precio;
    private CategoriaPaquete categoria = CategoriaPaquete.PAQUETE;
    private boolean activo = true;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getOperadorId() { return operadorId; }
    public void setOperadorId(String operadorId) { this.operadorId = operadorId; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }

    public BigDecimal getPrecio() { return precio; }
    public void setPrecio(BigDecimal precio) { this.precio = precio; }

    public CategoriaPaquete getCategoria() {
        return categoria == null ? CategoriaPaquete.PAQUETE : categoria;
    }
    public void setCategoria(CategoriaPaquete categoria) { this.categoria = categoria; }

    public boolean isActivo() { return activo; }
    public void setActivo(boolean activo) { this.activo = activo; }
}

package com.Servicio.RecargasMovil.model;

import java.time.LocalDateTime;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "lineas_movil")
public class LineaMovil {

    @Id
    private String id;

    @Indexed
    private String usuarioId;

    private String operadorId;
    private String telefono;
    private String alias;
    private LocalDateTime creadaEn = LocalDateTime.now();

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUsuarioId() { return usuarioId; }
    public void setUsuarioId(String usuarioId) { this.usuarioId = usuarioId; }

    public String getOperadorId() { return operadorId; }
    public void setOperadorId(String operadorId) { this.operadorId = operadorId; }

    public String getTelefono() { return telefono; }
    public void setTelefono(String telefono) { this.telefono = telefono; }

    public String getAlias() { return alias; }
    public void setAlias(String alias) { this.alias = alias; }

    public LocalDateTime getCreadaEn() { return creadaEn; }
    public void setCreadaEn(LocalDateTime creadaEn) { this.creadaEn = creadaEn; }
}

package com.transcaribe.transcaribe.Model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import com.transcaribe.transcaribe.Model.SistemaTransporte;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Document(collection = "avisos_administrativos")
public class AvisoAdministrativo {

    @Id
    private String id;

    @Field("asunto")
    private String asunto;

    @Field("mensaje")
    private String mensaje;

    @Field("imagen")
    private byte[] imagen;

    @Field("imagen_tipo_contenido")
    private String imagenTipoContenido;

    @Field("fecha_creacion")
    private LocalDateTime fechaCreacion;

    @Field("sistema_transporte")
    private SistemaTransporte sistemaTransporte = SistemaTransporte.TRANSCARIBE;

    @Field("destinatarios")
    private List<String> destinatarios = new ArrayList<>();

    @Field("descartada_por")
    private List<String> descartadaPor = new ArrayList<>();

    public AvisoAdministrativo() {
    }

    public AvisoAdministrativo(String asunto, String mensaje, List<String> destinatarios) {
        this.asunto = asunto;
        this.mensaje = mensaje;
        this.destinatarios = new ArrayList<>(destinatarios);
        this.fechaCreacion = LocalDateTime.now();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getAsunto() { return asunto; }
    public void setAsunto(String asunto) { this.asunto = asunto; }

    public String getMensaje() { return mensaje; }
    public void setMensaje(String mensaje) { this.mensaje = mensaje; }

    public byte[] getImagen() { return imagen; }
    public void setImagen(byte[] imagen) { this.imagen = imagen; }

    public String getImagenTipoContenido() { return imagenTipoContenido; }
    public void setImagenTipoContenido(String imagenTipoContenido) {
        this.imagenTipoContenido = imagenTipoContenido;
    }

    public LocalDateTime getFechaCreacion() { return fechaCreacion; }
    public void setFechaCreacion(LocalDateTime fechaCreacion) { this.fechaCreacion = fechaCreacion; }

    public SistemaTransporte getSistemaTransporte() {
        return sistemaTransporte == null ? SistemaTransporte.TRANSCARIBE : sistemaTransporte;
    }
    public void setSistemaTransporte(SistemaTransporte sistemaTransporte) {
        this.sistemaTransporte = sistemaTransporte;
    }

    public List<String> getDestinatarios() { return destinatarios; }
    public void setDestinatarios(List<String> destinatarios) { this.destinatarios = destinatarios; }

    public List<String> getDescartadaPor() {
        if (descartadaPor == null) {
            descartadaPor = new ArrayList<>();
        }
        return descartadaPor;
    }

    public void setDescartadaPor(List<String> descartadaPor) { this.descartadaPor = descartadaPor; }
}

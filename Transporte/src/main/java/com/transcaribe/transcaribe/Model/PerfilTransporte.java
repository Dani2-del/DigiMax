package com.transcaribe.transcaribe.Model;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Document(collection = "perfiles_transporte")
public class PerfilTransporte {

    @Id
    private String id;

    @Indexed(unique = true)
    @Field("usuarioId")
    private String usuarioId;

    @Field("sistema_asignado")
    private SistemaTransporte sistemaAsignado;

    @Field("sitm")
    private SistemaTransporte sitm;

    @Field("tarjetas")
    private List<Tarjeta> tarjetas = new ArrayList<>();

    @Field("rutas_favoritas")
    private List<String> rutasFavoritas = new ArrayList<>();

    @Field("preferencias_notificacion_rutas")
    private List<PreferenciaRutaNotificacion> preferenciasNotificacionRutas = new ArrayList<>();

    @Field("bus_asignado")
    private String busAsignado;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUsuarioId() { return usuarioId; }
    public void setUsuarioId(String usuarioId) { this.usuarioId = usuarioId; }

    public SistemaTransporte getSistemaAsignado() { return sistemaAsignado; }
    public void setSistemaAsignado(SistemaTransporte sistemaAsignado) {
        this.sistemaAsignado = sistemaAsignado;
    }

    public SistemaTransporte getSitm() { return sitm; }
    public void setSitm(SistemaTransporte sitm) { this.sitm = sitm; }

    public List<Tarjeta> getTarjetas() {
        if (tarjetas == null) {
            tarjetas = new ArrayList<>();
        }
        return tarjetas;
    }
    public void setTarjetas(List<Tarjeta> tarjetas) { this.tarjetas = tarjetas; }

    public List<String> getRutasFavoritas() {
        if (rutasFavoritas == null) {
            rutasFavoritas = new ArrayList<>();
        }
        return rutasFavoritas;
    }
    public void setRutasFavoritas(List<String> rutasFavoritas) { this.rutasFavoritas = rutasFavoritas; }

    public List<PreferenciaRutaNotificacion> getPreferenciasNotificacionRutas() {
        if (preferenciasNotificacionRutas == null) {
            preferenciasNotificacionRutas = new ArrayList<>();
        }
        return preferenciasNotificacionRutas;
    }
    public void setPreferenciasNotificacionRutas(
            List<PreferenciaRutaNotificacion> preferenciasNotificacionRutas) {
        this.preferenciasNotificacionRutas = preferenciasNotificacionRutas;
    }

    public String getBusAsignado() { return busAsignado; }
    public void setBusAsignado(String busAsignado) { this.busAsignado = busAsignado; }
}

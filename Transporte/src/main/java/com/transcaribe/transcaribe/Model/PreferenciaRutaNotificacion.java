package com.transcaribe.transcaribe.Model;

import java.time.LocalTime;

import org.springframework.data.mongodb.core.mapping.Field;

public class PreferenciaRutaNotificacion {

    @Field("ruta")
    private String ruta;

    @Field("notificaciones_activas")
    private boolean notificacionesActivas;

    @Field("hora_desde")
    private LocalTime horaDesde = LocalTime.of(6, 0);

    @Field("hora_hasta")
    private LocalTime horaHasta = LocalTime.of(8, 0);

    public PreferenciaRutaNotificacion() {
    }

    public PreferenciaRutaNotificacion(String ruta, boolean notificacionesActivas,
            LocalTime horaDesde, LocalTime horaHasta) {
        this.ruta = ruta;
        this.notificacionesActivas = notificacionesActivas;
        this.horaDesde = horaDesde;
        this.horaHasta = horaHasta;
    }

    public String getRuta() { return ruta; }
    public void setRuta(String ruta) { this.ruta = ruta; }

    public boolean isNotificacionesActivas() { return notificacionesActivas; }
    public void setNotificacionesActivas(boolean notificacionesActivas) {
        this.notificacionesActivas = notificacionesActivas;
    }

    public LocalTime getHoraDesde() { return horaDesde; }
    public void setHoraDesde(LocalTime horaDesde) { this.horaDesde = horaDesde; }

    public LocalTime getHoraHasta() { return horaHasta; }
    public void setHoraHasta(LocalTime horaHasta) { this.horaHasta = horaHasta; }

    public boolean incluyeHora(LocalTime hora) {
        return notificacionesActivas
                && ruta != null
                && hora != null
                && horaDesde != null
                && horaHasta != null
                && !horaDesde.isAfter(horaHasta)
                && !hora.isBefore(horaDesde)
                && !hora.isAfter(horaHasta);
    }
}

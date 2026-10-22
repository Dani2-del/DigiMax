package com.transcaribe.transcaribe.dto;

import java.util.List;

public class GuardarGeometriaRutaRequest {

    private List<CoordenadaRequest> coordenadas;

    public List<CoordenadaRequest> getCoordenadas() { return coordenadas; }
    public void setCoordenadas(List<CoordenadaRequest> coordenadas) { this.coordenadas = coordenadas; }

    public static class CoordenadaRequest {
        private Double latitud;
        private Double longitud;

        public Double getLatitud() { return latitud; }
        public void setLatitud(Double latitud) { this.latitud = latitud; }
        public Double getLongitud() { return longitud; }
        public void setLongitud(Double longitud) { this.longitud = longitud; }
    }
}

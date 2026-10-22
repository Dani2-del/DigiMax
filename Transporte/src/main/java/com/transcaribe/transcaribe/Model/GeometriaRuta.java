package com.transcaribe.transcaribe.Model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Document(collection = "geometrias_rutas")
@CompoundIndex(name = "ruta_sentido_unico", def = "{'codigo_ruta': 1, 'sentido': 1}", unique = true)
public class GeometriaRuta {

    @Id
    private String id;

    @Field("codigo_ruta")
    private String codigoRuta;

    @Field("sentido")
    private String sentido;

    @Field("coordenadas")
    private List<Coordenada> coordenadas = new ArrayList<>();

    @Field("actualizada_en")
    private Instant actualizadaEn;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getCodigoRuta() { return codigoRuta; }
    public void setCodigoRuta(String codigoRuta) { this.codigoRuta = codigoRuta; }
    public String getSentido() { return sentido; }
    public void setSentido(String sentido) { this.sentido = sentido; }
    public List<Coordenada> getCoordenadas() {
        if (coordenadas == null) {
            coordenadas = new ArrayList<>();
        }
        return coordenadas;
    }
    public void setCoordenadas(List<Coordenada> coordenadas) { this.coordenadas = coordenadas; }
    public Instant getActualizadaEn() { return actualizadaEn; }
    public void setActualizadaEn(Instant actualizadaEn) { this.actualizadaEn = actualizadaEn; }

    public record Coordenada(double latitud, double longitud) {}
}

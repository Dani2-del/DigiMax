package com.transcaribe.transcaribe.Model;

import java.util.List;
import java.util.Locale;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Document(collection = "buses")
public class Bus {

    public static final String TIPO_TRONCAL = "TRONCAL";
    public static final String TIPO_PETRONCAL = "PETRONCAL";
    public static final String TIPO_ALIMENTADOR = "ALIMENTADOR";

    @Id
    private String id;

    @Field("placa")
    @Indexed(unique = true)
    private String placa;

    @Field("tipo")
    private String tipo = TIPO_TRONCAL;

    @Field("sistema_transporte")
    private SistemaTransporte sistemaTransporte = SistemaTransporte.TRANSCARIBE;

    @Field("rutas_disponibles")
    private List<String> rutas;

    @Field("activo")
    private boolean activo = true;

    public Bus() {}

    public Bus(String placa, List<String> rutas) {
        this(placa, rutas, TIPO_TRONCAL);
    }

    public Bus(String placa, List<String> rutas, String tipo) {
        this.placa = placa;
        this.rutas = rutas;
        this.tipo = normalizarTipo(tipo);
    }

    public Bus(String placa, List<String> rutas, String tipo, SistemaTransporte sistemaTransporte) {
        this(placa, rutas, tipo);
        this.sistemaTransporte = sistemaTransporte == null
                ? SistemaTransporte.TRANSCARIBE : sistemaTransporte;
    }

    public String getId() {
        return this.id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setPlaca(String placa) {
        this.placa = placa;
    }

    public void setTipo(String tipo) {
        this.tipo = normalizarTipo(tipo);
    }

    public void setRutas(List<String> rutas) {
        this.rutas = rutas;
    }

    public boolean isActivo() {
        return this.activo;
    }

    public boolean getActivo() {
        return this.activo;
    }

    public void setActivo(boolean activo) {
        this.activo = activo;
    }

    public List<String> getRutas() { return rutas; }
    public String getPlaca() { return placa; }
    public String getTipo() { return tipo; }
    public SistemaTransporte getSistemaTransporte() {
        return sistemaTransporte == null ? SistemaTransporte.TRANSCARIBE : sistemaTransporte;
    }
    public void setSistemaTransporte(SistemaTransporte sistemaTransporte) {
        this.sistemaTransporte = sistemaTransporte;
    }

    public static String normalizarTipo(String tipo) {
        if (tipo == null || tipo.trim().isEmpty()) {
            return TIPO_TRONCAL;
        }

        String valor = tipo.trim().toUpperCase(Locale.ROOT);
        if (TIPO_TRONCAL.equals(valor) || TIPO_PETRONCAL.equals(valor) || TIPO_ALIMENTADOR.equals(valor)) {
            return valor;
        }

        return TIPO_TRONCAL;
    }
}

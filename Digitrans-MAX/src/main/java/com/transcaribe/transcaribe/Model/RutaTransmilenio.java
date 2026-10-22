package com.transcaribe.transcaribe.Model;

public class RutaTransmilenio {
    private final String id;
    private final String codigoLinea;
    private final String nombre;
    private final String codigoRuta;
    private final String origen;
    private final String destino;
    private final String horarioHabil;
    private final String horarioSabado;
    private final String horarioFestivo;

    public RutaTransmilenio(String id, String codigoLinea, String nombre, String codigoRuta,
                            String origen, String destino, String horarioHabil,
                            String horarioSabado, String horarioFestivo) {
        this.id = id;
        this.codigoLinea = codigoLinea;
        this.nombre = nombre;
        this.codigoRuta = codigoRuta;
        this.origen = origen;
        this.destino = destino;
        this.horarioHabil = horarioHabil;
        this.horarioSabado = horarioSabado;
        this.horarioFestivo = horarioFestivo;
    }

    public String getId() { return id; }
    public String getCodigoLinea() { return codigoLinea; }
    public String getNombre() { return nombre; }
    public String getCodigoRuta() { return codigoRuta; }
    public String getOrigen() { return origen; }
    public String getDestino() { return destino; }
    public String getHorarioHabil() { return horarioHabil; }
    public String getHorarioSabado() { return horarioSabado; }
    public String getHorarioFestivo() { return horarioFestivo; }

    public String getEtiqueta() {
        return codigoLinea + " · " + origen + " → " + destino;
    }
}

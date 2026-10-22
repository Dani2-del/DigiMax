package com.transcaribe.transcaribe.Model;

import java.util.List;

/** Ruta troncal del catálogo de Transmetro (datos de día laboral). */
public record RutaTransmetro(
        String codigo,
        String tipo,
        boolean activa,
        List<String> paradas,
        List<String> horarios) {

    public RutaTransmetro {
        paradas = List.copyOf(paradas);
        horarios = List.copyOf(horarios);
    }

    public String getEtiqueta() {
        return codigo + " · " + paradas.get(0) + " → " + paradas.get(paradas.size() - 1);
    }
}

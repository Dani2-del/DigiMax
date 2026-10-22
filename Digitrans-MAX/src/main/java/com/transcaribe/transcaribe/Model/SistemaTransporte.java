package com.transcaribe.transcaribe.Model;

import java.util.List;

public enum SistemaTransporte {
    TRANSCARIBE("Transcaribe"),
    TRANSMILENIO("TransMilenio"),
    TRANSMETRO("Transmetro");

    public static final String SESSION_ATTRIBUTE = "sistemaTransporteActual";

    private final String nombre;

    SistemaTransporte(String nombre) {
        this.nombre = nombre;
    }

    public String getNombre() {
        return nombre;
    }

    public static List<SistemaTransporte> disponibles() {
        return List.of(TRANSCARIBE, TRANSMILENIO, TRANSMETRO);
    }

    public boolean estaDisponible() {
        return true;
    }

    public boolean tieneRutasConfiguradas() {
        return this == TRANSCARIBE;
    }

    public boolean permiteSimularViaje() {
        return this == TRANSCARIBE || this == TRANSMILENIO || this == TRANSMETRO;
    }

    public boolean permiteGestionConductores() {
        return this == TRANSCARIBE || this == TRANSMILENIO || this == TRANSMETRO;
    }
}

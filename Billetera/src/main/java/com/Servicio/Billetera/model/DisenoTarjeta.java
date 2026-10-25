package com.Servicio.Billetera.model;

public enum DisenoTarjeta {
    OCEANO("Océano"),
    BOSQUE("Bosque"),
    ATARDECER("Atardecer"),
    LAVANDA("Lavanda"),
    RUBI("Rubí"),
    CARBON("Carbón"),
    ESMERALDA("Esmeralda"),
    COBRE("Cobre"),
    GLACIAR("Glaciar"),
    GALAXIA("Galaxia");

    private final String nombre;

    DisenoTarjeta(String nombre) {
        this.nombre = nombre;
    }

    public String getNombre() {
        return nombre;
    }
}

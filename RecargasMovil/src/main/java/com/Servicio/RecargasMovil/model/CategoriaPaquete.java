package com.Servicio.RecargasMovil.model;

public enum CategoriaPaquete {
    PAQUETE("Paquetes"),
    PREPAGO("Prepago"),
    POSTPAGO("Pospago"),
    RECARGA_DINERO("Recargas en dinero"),
    BOLSA_DATOS("Bolsitas de datos"),
    MOVISTAR_PRO("Paquetes PRO"),
    OTROS_PAQUETES("Otros paquetes"),
    ROAMING("Roaming");

    private final String nombreVisible;

    CategoriaPaquete(String nombreVisible) {
        this.nombreVisible = nombreVisible;
    }

    public String getNombreVisible() {
        return nombreVisible;
    }
}

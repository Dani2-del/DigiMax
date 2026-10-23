package com.Servicio.RecargasMovil.service;

public class InsufficientWalletBalanceException extends RuntimeException {

    public InsufficientWalletBalanceException() {
        super("El saldo de la tarjeta universal no alcanza para este paquete.");
    }
}

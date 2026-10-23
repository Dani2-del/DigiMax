package com.Servicio.Billetera.service;

public class SaldoInsuficienteException extends RuntimeException {

    public SaldoInsuficienteException() {
        super("El saldo de la tarjeta universal es insuficiente.");
    }
}

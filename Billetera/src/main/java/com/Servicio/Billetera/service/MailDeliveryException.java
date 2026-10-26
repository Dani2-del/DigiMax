package com.Servicio.Billetera.service;

public class MailDeliveryException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public MailDeliveryException(String message) {
        super(message);
    }

    public MailDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}

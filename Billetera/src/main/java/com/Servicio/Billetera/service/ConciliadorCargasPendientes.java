package com.Servicio.Billetera.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ConciliadorCargasPendientes {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConciliadorCargasPendientes.class);

    private final BilleteraService billeteraService;

    public ConciliadorCargasPendientes(BilleteraService billeteraService) {
        this.billeteraService = billeteraService;
    }

    @Scheduled(fixedDelayString = "${app.pagos.revision-pendientes-ms:5000}", initialDelay = 2000)
    void conciliarCargasPendientes() {
        try {
            billeteraService.revisarCargasPendientesVencidas();
        } catch (RuntimeException exception) {
            LOGGER.warn("No se pudieron revisar las cargas pendientes; se intentará nuevamente.", exception);
        }
    }
}

package com.Servicio.RecargasMovil.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ConciliadorPagosPendientes {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConciliadorPagosPendientes.class);

    private final RecargasService recargasService;

    public ConciliadorPagosPendientes(RecargasService recargasService) {
        this.recargasService = recargasService;
    }

    @Scheduled(fixedDelayString = "${app.pagos.revision-pendientes-ms:5000}", initialDelay = 2000)
    void conciliarPagosPendientes() {
        try {
            recargasService.revisarPagosPendientesVencidos();
        } catch (RuntimeException exception) {
            LOGGER.warn("No se pudieron revisar los pagos pendientes de Recargas; se intentará nuevamente.",
                    exception);
        }
    }
}

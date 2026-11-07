package com.Servicio.Orquestador.service;

import com.Servicio.Orquestador.config.ModulosConfig;
import com.Servicio.Orquestador.config.ModulosConfig.ModuloConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class ArranqueInicial implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ArranqueInicial.class);

    private final ModulosConfig config;
    private final ProcesoService procesos;
    private final int timeoutSegundos;

    public ArranqueInicial(ModulosConfig config, ProcesoService procesos,
                           @Value("${orquestador.arranque-timeout-segundos:120}") int timeoutSegundos) {
        this.config = config;
        this.procesos = procesos;
        this.timeoutSegundos = timeoutSegundos;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        for (ModuloConfig modulo : config.todos()) {
            procesos.iniciarManual(modulo.nombre());
            if (procesos.esperarDisponible(modulo.nombre(), timeoutSegundos)) {
                log.info("{} respondio por HTTP en el puerto {}", modulo.nombre(), modulo.puerto());
            } else {
                log.warn("{} no respondio por HTTP en {} segundos; se continuara con el siguiente modulo",
                        modulo.nombre(), timeoutSegundos);
            }
        }
    }
}

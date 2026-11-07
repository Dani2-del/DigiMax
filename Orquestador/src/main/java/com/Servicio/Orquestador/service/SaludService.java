package com.Servicio.Orquestador.service;

import com.Servicio.Orquestador.config.ModulosConfig;
import com.Servicio.Orquestador.config.ModulosConfig.ModuloConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SaludService {

    private static final Logger log = LoggerFactory.getLogger(SaludService.class);

    private final ModulosConfig config;
    private final ProcesoService procesos;

    public SaludService(ModulosConfig config, ProcesoService procesos) {
        this.config = config;
        this.procesos = procesos;
    }

    @Scheduled(fixedDelayString = "${orquestador.intervalo-ms:10000}",
               initialDelayString = "${orquestador.espera-inicial-ms:30000}")
    public void revisar() {
        for (ModuloConfig m : config.todos()) {
            try {
                procesos.verificar(m.nombre());
            } catch (Exception ex) {
                log.error("Error verificando {}", m.nombre(), ex);
            }
        }
    }
}

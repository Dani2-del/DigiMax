package com.transcaribe.transcaribe.Repository;

import com.transcaribe.transcaribe.Model.HorarioConductor;
import com.transcaribe.transcaribe.Model.SistemaTransporte;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;

public interface HorarioConductorRepositoryCustom {

    Page<HorarioConductor> buscarHistorial(SistemaTransporte sistema, String conductorId, String ruta,
                                           LocalDate desde, LocalDate hasta, List<String> rutasPermitidas,
                                           Pageable pageable);

    List<String> buscarIdsConductoresHistorial(SistemaTransporte sistema, List<String> rutasPermitidas);

    /**
     * Horarios programados (no desactivados) de un servicio, ya filtrados y paginados en la base de datos.
     */
    Page<HorarioConductor> buscarProgramados(SistemaTransporte sistema, String conductorId, String ruta,
                                             LocalDate desde, LocalDate hasta, Pageable pageable);
}

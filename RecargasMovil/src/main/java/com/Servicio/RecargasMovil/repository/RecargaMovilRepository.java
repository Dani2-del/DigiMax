package com.Servicio.RecargasMovil.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.Servicio.RecargasMovil.model.RecargaMovil;

public interface RecargaMovilRepository extends MongoRepository<RecargaMovil, String> {

    List<RecargaMovil> findByUsuarioIdOrderByFechaDesc(String usuarioId);

    List<RecargaMovil> findByEstadoAndFechaLessThanEqualAndIdPagoProveedorIsNotNullOrderByFechaAsc(
            String estado, LocalDateTime fechaLimite);

    long countByEstado(String estado);
}

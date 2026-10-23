package com.Servicio.RecargasMovil.repository;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.Servicio.RecargasMovil.model.RecargaMovil;

public interface RecargaMovilRepository extends MongoRepository<RecargaMovil, String> {

    List<RecargaMovil> findTop20ByUsuarioIdOrderByFechaDesc(String usuarioId);
}

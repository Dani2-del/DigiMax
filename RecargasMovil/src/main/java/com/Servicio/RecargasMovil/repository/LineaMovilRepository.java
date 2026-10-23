package com.Servicio.RecargasMovil.repository;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.Servicio.RecargasMovil.model.LineaMovil;

public interface LineaMovilRepository extends MongoRepository<LineaMovil, String> {

    List<LineaMovil> findByUsuarioIdOrderByCreadaEnDesc(String usuarioId);
}

package com.Servicio.RecargasMovil.repository;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.Servicio.RecargasMovil.model.PaqueteMovil;

public interface PaqueteMovilRepository extends MongoRepository<PaqueteMovil, String> {

    List<PaqueteMovil> findByOperadorIdAndActivoTrueOrderByPrecioAsc(String operadorId);

    List<PaqueteMovil> findAllByOrderByNombreAsc();
}

package com.Servicio.RecargasMovil.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.Servicio.RecargasMovil.model.PaqueteMovil;

public interface PaqueteMovilRepository extends MongoRepository<PaqueteMovil, String> {

    List<PaqueteMovil> findByOperadorIdAndActivoTrueOrderByPrecioAsc(String operadorId);

    Optional<PaqueteMovil> findByOperadorIdAndNombreIgnoreCase(String operadorId, String nombre);

    List<PaqueteMovil> findAllByOrderByNombreAsc();
}

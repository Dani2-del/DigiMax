package com.Servicio.RecargasMovil.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.Servicio.RecargasMovil.model.OperadorMovil;

public interface OperadorMovilRepository extends MongoRepository<OperadorMovil, String> {

    List<OperadorMovil> findByActivoTrueOrderByNombreAsc();

    Optional<OperadorMovil> findByNombreIgnoreCase(String nombre);
}

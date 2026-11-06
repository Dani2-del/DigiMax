package com.Servicio.Lector.repository;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.Servicio.Lector.model.HistorialPropietarioRfid;

public interface HistorialPropietarioRfidRepository
        extends MongoRepository<HistorialPropietarioRfid, String> {

    List<HistorialPropietarioRfid> findAllByOrderByFechaDesc();
    boolean existsByUid(String uid);
}

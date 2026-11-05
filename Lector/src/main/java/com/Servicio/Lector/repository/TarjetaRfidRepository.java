package com.Servicio.Lector.repository;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.Servicio.Lector.model.TarjetaRfid;

public interface TarjetaRfidRepository extends MongoRepository<TarjetaRfid, String> {

    List<TarjetaRfid> findAllByOrderByVinculadaEnDesc();

    java.util.Optional<TarjetaRfid> findFirstByUsuarioId(String usuarioId);
}

package com.Servicio.Billetera.repository;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.Servicio.Billetera.model.VerificacionTarjeta;

public interface VerificacionTarjetaRepository extends MongoRepository<VerificacionTarjeta, String> {
}

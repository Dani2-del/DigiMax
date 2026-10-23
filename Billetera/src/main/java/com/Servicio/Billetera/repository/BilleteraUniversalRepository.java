package com.Servicio.Billetera.repository;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.Servicio.Billetera.model.BilleteraUniversal;

public interface BilleteraUniversalRepository extends MongoRepository<BilleteraUniversal, String> {
}

package com.transcaribe.transcaribe.Repository;

import com.transcaribe.transcaribe.Model.GeometriaRuta;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface GeometriaRutaRepository extends MongoRepository<GeometriaRuta, String> {

    Optional<GeometriaRuta> findByCodigoRutaAndSentido(String codigoRuta, String sentido);

    List<GeometriaRuta> findByCodigoRutaOrderBySentidoAsc(String codigoRuta);
}

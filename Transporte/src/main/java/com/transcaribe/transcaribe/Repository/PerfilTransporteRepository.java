package com.transcaribe.transcaribe.Repository;

import java.util.Optional;
import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import com.transcaribe.transcaribe.Model.PerfilTransporte;
import com.transcaribe.transcaribe.Model.SistemaTransporte;

public interface PerfilTransporteRepository extends MongoRepository<PerfilTransporte, String> {

    Optional<PerfilTransporte> findByUsuarioId(String usuarioId);

    @Query("{ 'tarjetas.numeroTarjeta': ?0 }")
    Optional<PerfilTransporte> findByNumeroTarjetaEnLista(String numeroTarjeta);

    @Query("{ 'tarjetas': { '$elemMatch': { 'numeroTarjeta': ?0, 'sistemaTransporte': ?1 } } }")
    Optional<PerfilTransporte> findByNumeroTarjetaEnListaYSistema(
            String numeroTarjeta, SistemaTransporte sistemaTransporte);

    @Query("{ 'rutas_favoritas': { $regex: ?0, $options: 'i' } }")
    List<PerfilTransporte> findByRutaFavoritaIgnoreCase(String rutaRegex);
}

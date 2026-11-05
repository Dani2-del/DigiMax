package com.transcaribe.transcaribe.Repository;

import java.util.Optional;
import java.util.List;
import java.util.Collection;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.transcaribe.transcaribe.Model.PerfilTransporte;
import com.transcaribe.transcaribe.Model.SistemaTransporte;

public interface PerfilTransporteRepository extends MongoRepository<PerfilTransporte, String> {

    Optional<PerfilTransporte> findByUsuarioId(String usuarioId);

    List<PerfilTransporte> findByUsuarioIdIn(Collection<String> usuarioIds);

    @Query(value = "{ 'usuarioId': { '$in': ?0 } }",
            fields = "{ '_id': 1, 'usuarioId': 1, 'sitm': 1, 'sistema_asignado': 1 }")
    List<PerfilTransporte> findMembershipByUsuarioIdIn(Collection<String> usuarioIds);

    @Query(value = "{ '$or': [ { 'sitm': ?0 }, { 'sistema_asignado': ?1 } ] }",
            fields = "{ '_id': 1, 'usuarioId': 1, 'sitm': 1, 'sistema_asignado': 1 }")
    Page<PerfilTransporte> findMembershipBySitmOrSistemaAsignado(
            SistemaTransporte sitm, SistemaTransporte sistemaAsignado, Pageable pageable);

    @Query("{ 'tarjetas.numeroTarjeta': ?0 }")
    Optional<PerfilTransporte> findByNumeroTarjetaEnLista(String numeroTarjeta);

    @Query("{ 'tarjetas': { '$elemMatch': { 'numeroTarjeta': ?0, 'sistemaTransporte': ?1 } } }")
    Optional<PerfilTransporte> findByNumeroTarjetaEnListaYSistema(
            String numeroTarjeta, SistemaTransporte sistemaTransporte);

    @Query("{ 'rutas_favoritas': { $regex: ?0, $options: 'i' } }")
    List<PerfilTransporte> findByRutaFavoritaIgnoreCase(String rutaRegex);
}

package com.Servicio.Acceso.repository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import com.Servicio.Acceso.model.Usuario;
import com.Servicio.Acceso.model.SistemaTransporte;

public interface UsuarioRepository extends MongoRepository<Usuario, String> {

    Optional<Usuario> findByCorreo(String correo);

    Optional<Usuario> findByCorreoIgnoreCase(String correo);

    // Búsqueda paginada por nombre o correo (usada en el dashboard admin)
    Page<Usuario> findByNombreContainingIgnoreCaseOrCorreoContainingIgnoreCase(
            String nombre, String correo, Pageable pageable);

    List<Usuario> findByNombreContainingIgnoreCaseOrCorreoContainingIgnoreCase(
            String nombre, String correo);

    List<Usuario> findByRoleAndActivoTrue(String role);

    long countByActivoTrue();

    long countByVerificadoFalse();

    long countByRole(String role);

    Optional<Usuario> findByIdAndActivoTrue(String id);

    @Query("{}")
    Stream<Usuario> streamAll();

    @Query("{ '$or': [ " +
            "{ 'role': 'ROLE_MODERATOR', 'sistema_asignado': ?0 }, " +
            "{ 'role': { '$ne': 'ROLE_MODERATOR' }, 'sitm': ?0 } " +
            "] }")
    Stream<Usuario> streamBySistemaTransporte(SistemaTransporte sistemaTransporte);

    @Query("{ 'rutas_favoritas': { $regex: ?0, $options: 'i' } }")
    List<Usuario> findByRutaFavoritaIgnoreCase(String rutaRegex);

    @Query("{ 'tarjetas.numeroTarjeta': ?0 }")
    Optional<Usuario> findByNumeroTarjetaEnLista(String numeroTarjeta);

    @Query("{ 'tarjetas': { '$elemMatch': { 'numeroTarjeta': ?0, 'sistemaTransporte': ?1 } } }")
    Optional<Usuario> findByNumeroTarjetaEnListaYSistema(
            String numeroTarjeta, SistemaTransporte sistemaTransporte);
}

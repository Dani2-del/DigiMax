package com.transcaribe.transcaribe.Repository;

import com.transcaribe.transcaribe.Model.AvisoAdministrativo;
import com.transcaribe.transcaribe.Model.SistemaTransporte;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Stream;

public interface AvisoAdministrativoRepository extends MongoRepository<AvisoAdministrativo, String> {

    @Query(value = "{ 'destinatarios': ?0, 'descartada_por': { $ne: ?0 } }",
            sort = "{ 'fecha_creacion': -1 }")
    List<AvisoAdministrativo> findPendientesParaUsuario(String usuarioId);

    @Query(value = "{ 'destinatarios': ?0, 'descartada_por': { '$ne': ?0 }, 'sistema_transporte': ?1 }",
            sort = "{ 'fecha_creacion': -1 }")
    List<AvisoAdministrativo> findPendientesParaUsuarioYSistema(String usuarioId, SistemaTransporte sistema);

    @Query(value = "{ 'destinatarios': ?0, 'descartada_por': { '$ne': ?0 }, 'sistema_transporte': { '$exists': false } }",
            sort = "{ 'fecha_creacion': -1 }")
    List<AvisoAdministrativo> findPendientesLegadosParaUsuario(String usuarioId);

    List<AvisoAdministrativo> findTop2ByOrderByFechaCreacionDesc();

    List<AvisoAdministrativo> findTop2BySistemaTransporteOrderByFechaCreacionDesc(SistemaTransporte sistema);

    @Query(value = "{ 'sistema_transporte': { '$exists': false } }",
            sort = "{ 'fecha_creacion': -1 }")
    List<AvisoAdministrativo> findTop2LegadosByOrderByFechaCreacionDesc();

    List<AvisoAdministrativo> findAllByOrderByFechaCreacionDesc();

    List<AvisoAdministrativo> findAllBySistemaTransporteOrderByFechaCreacionDesc(SistemaTransporte sistema);

    @Query(value = "{ 'sistema_transporte': { '$exists': false } }",
            sort = "{ 'fecha_creacion': -1 }")
    List<AvisoAdministrativo> findAllLegadosByOrderByFechaCreacionDesc();

    @Query(value = "{}", sort = "{ 'fecha_creacion': -1 }")
    Stream<AvisoAdministrativo> streamAll();

    @Query(value = "{ 'fecha_creacion': { '$gte': ?0, '$lt': ?1 } }",
            sort = "{ 'fecha_creacion': -1 }")
    Stream<AvisoAdministrativo> streamByFechaCreacionEnRango(LocalDateTime inicio, LocalDateTime fin);

    @Query(value = "{ 'destinatarios': { '$in': ?0 } }",
            sort = "{ 'fecha_creacion': -1 }")
    Stream<AvisoAdministrativo> streamByDestinatariosIn(List<String> destinatarioIds);

    @Query(value = "{ 'destinatarios': { '$in': ?0 }, 'fecha_creacion': { '$gte': ?1, '$lt': ?2 } }",
            sort = "{ 'fecha_creacion': -1 }")
    Stream<AvisoAdministrativo> streamByDestinatariosInAndFechaCreacionEnRango(
            List<String> destinatarioIds, LocalDateTime inicio, LocalDateTime fin);
}

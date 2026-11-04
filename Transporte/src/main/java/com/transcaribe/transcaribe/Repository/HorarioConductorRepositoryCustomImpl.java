package com.transcaribe.transcaribe.Repository;

import com.transcaribe.transcaribe.Model.HorarioConductor;
import com.transcaribe.transcaribe.Model.SistemaTransporte;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class HorarioConductorRepositoryCustomImpl implements HorarioConductorRepositoryCustom {

    private final MongoTemplate mongoTemplate;

    public HorarioConductorRepositoryCustomImpl(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public Page<HorarioConductor> buscarHistorial(SistemaTransporte sistema, String conductorId,
                                                  String ruta, LocalDate desde, LocalDate hasta,
                                                  List<String> rutasPermitidas, Pageable pageable) {
        Criteria criteria = construirFiltroHistorial(
                sistema, conductorId, ruta, desde, hasta, rutasPermitidas);
        Query pageQuery = Query.query(criteria).with(pageable);
        Query countQuery = Query.query(criteria);
        return new PageImpl<>(
                mongoTemplate.find(pageQuery, HorarioConductor.class),
                pageable,
                mongoTemplate.count(countQuery, HorarioConductor.class));
    }

    @Override
    public Page<HorarioConductor> buscarProgramados(SistemaTransporte sistema, String conductorId,
                                                    String ruta, LocalDate desde, LocalDate hasta,
                                                    Pageable pageable) {
        List<Criteria> filtros = filtrosPorSistema(sistema);
        filtros.add(Criteria.where("estado").ne(HorarioConductor.ESTADO_DESACTIVADA));
        if (StringUtils.hasText(conductorId)) {
            filtros.add(Criteria.where("conductorId").is(conductorId));
        }
        if (StringUtils.hasText(ruta)) {
            filtros.add(Criteria.where("ruta").regex(Pattern.quote(ruta.trim()), "i"));
        }
        if (desde != null || hasta != null) {
            Criteria fecha = Criteria.where("fecha");
            if (desde != null) {
                fecha.gte(desde);
            }
            if (hasta != null) {
                fecha.lte(hasta);
            }
            filtros.add(fecha);
        }
        Criteria criteria = new Criteria().andOperator(filtros.toArray(Criteria[]::new));
        Query pageQuery = Query.query(criteria).with(pageable);
        return new PageImpl<>(
                mongoTemplate.find(pageQuery, HorarioConductor.class),
                pageable,
                mongoTemplate.count(Query.query(criteria), HorarioConductor.class));
    }

    @Override
    public List<String> buscarIdsConductoresHistorial(
            SistemaTransporte sistema, List<String> rutasPermitidas) {
        Query query = Query.query(
                construirFiltroHistorial(sistema, null, null, null, null, rutasPermitidas));
        return mongoTemplate.query(HorarioConductor.class)
                .distinct("conductorId")
                .matching(query)
                .as(String.class)
                .all().stream()
                .filter(StringUtils::hasText)
                .toList();
    }

    static Criteria construirFiltroHistorial(SistemaTransporte sistema, String conductorId, String ruta,
                                             LocalDate desde, LocalDate hasta,
                                             List<String> rutasPermitidas) {
        List<Criteria> filtros = filtrosPorSistema(sistema);
        agregarFiltroRutasDelSistema(filtros, sistema, rutasPermitidas);
        if (StringUtils.hasText(conductorId)) {
            filtros.add(Criteria.where("conductorId").is(conductorId));
        }
        if (StringUtils.hasText(ruta)) {
            filtros.add(Criteria.where("ruta").regex(Pattern.quote(ruta.trim()), "i"));
        }
        if (desde != null || hasta != null) {
            Criteria fecha = Criteria.where("fecha");
            if (desde != null) {
                fecha.gte(desde);
            }
            if (hasta != null) {
                fecha.lte(hasta);
            }
            filtros.add(fecha);
        }
        return new Criteria().andOperator(filtros.toArray(Criteria[]::new));
    }

    private static void agregarFiltroRutasDelSistema(List<Criteria> filtros, SistemaTransporte sistema,
                                                      List<String> rutasPermitidas) {
        if (sistema != SistemaTransporte.TRANSCARIBE) {
            filtros.add(Criteria.where("ruta").in(rutasPermitidas == null ? List.of() : rutasPermitidas));
        }
    }

    private static List<Criteria> filtrosPorSistema(SistemaTransporte sistema) {
        if (sistema == null) {
            throw new IllegalArgumentException("El sistema de transporte es obligatorio.");
        }
        List<Criteria> filtros = new ArrayList<>();
        if (sistema == SistemaTransporte.TRANSCARIBE) {
            filtros.add(new Criteria().orOperator(
                    Criteria.where("sistemaTransporte").is(sistema),
                    Criteria.where("sistemaTransporte").is(null)));
        } else {
            filtros.add(Criteria.where("sistemaTransporte").is(sistema));
        }
        return filtros;
    }
}

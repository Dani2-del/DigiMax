package com.transcaribe.transcaribe.Controller;

import com.transcaribe.transcaribe.Model.Usuario;
import com.transcaribe.transcaribe.Model.SistemaTransporte;
import com.transcaribe.transcaribe.Repository.UsuarioRepository;
import com.transcaribe.transcaribe.service.AvisoAdministrativoService;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import jakarta.servlet.http.HttpSession;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/admin/graficas")
public class AdminChartsController {

    private static final ZoneId ZONA_CARTAGENA = ZoneId.of("America/Bogota");
    private final MongoTemplate mongoTemplate;
    private final UsuarioRepository usuarioRepository;
    private final AvisoAdministrativoService avisoAdministrativoService;

    public AdminChartsController(MongoTemplate mongoTemplate, UsuarioRepository usuarioRepository,
                                 AvisoAdministrativoService avisoAdministrativoService) {
        this.mongoTemplate = mongoTemplate;
        this.usuarioRepository = usuarioRepository;
        this.avisoAdministrativoService = avisoAdministrativoService;
    }

    @GetMapping
    public String mostrarGraficas(Model model, Authentication authentication, HttpSession session) {
        SistemaTransporte sistemaActual = (SistemaTransporte) session.getAttribute(
                SistemaTransporte.SESSION_ATTRIBUTE);
        usuarioRepository.findByCorreo(authentication.getName()).ifPresent(usuario ->
                model.addAttribute("alertasAdmin",
                        avisoAdministrativoService.obtenerPendientes(usuario.getId(), sistemaActual)));
        model.addAttribute("alertaReturnTo", "/admin/graficas");
        return "admin/graficas";
    }

    @GetMapping("/api")
    @ResponseBody
    public ResponseEntity<EstadisticasAdmin> obtenerEstadisticas(HttpSession session) {
        SistemaTransporte sistemaActual = (SistemaTransporte) session.getAttribute(
                SistemaTransporte.SESSION_ATTRIBUTE);
        Date inicioPeriodo = Date.from(LocalDate.now(ZONA_CARTAGENA)
                .minusMonths(11)
                .withDayOfMonth(1)
                .atStartOfDay(ZONA_CARTAGENA)
                .toInstant());

        List<ChartPoint> usuariosPorRol = aggregateUsuarios(List.of(
                new Document("$group", new Document("_id", "$role")
                        .append("cantidad", new Document("$sum", 1))),
                new Document("$sort", new Document("cantidad", -1))
        ), sistemaActual);

        Document filtroPeriodo = new Document("$and", List.of(
                filtroSistema(sistemaActual),
                new Document("fecha", new Document("$gte", inicioPeriodo))));
        List<Document> transaccionesMensuales = mongoTemplate.getCollection("transacciones")
                .aggregate(List.of(
                        new Document("$match", filtroPeriodo),
                        new Document("$group", new Document("_id", new Document("$dateToString",
                                new Document("format", "%Y-%m")
                                        .append("date", "$fecha")
                                        .append("timezone", ZONA_CARTAGENA.getId())))
                                .append("cantidad", new Document("$sum", 1))
                                .append("monto", new Document("$sum", "$monto"))),
                        new Document("$sort", new Document("_id", 1))
                ), Document.class)
                .into(new ArrayList<>());

        List<ChartPoint> transaccionesPorTipo = aggregateTransacciones(List.of(
                new Document("$project", new Document("categoria",
                        new Document("$switch", new Document("branches", List.of(
                                new Document("case", new Document("$regexMatch",
                                        new Document("input", new Document("$toLower",
                                                new Document("$ifNull", List.of("$tipo", ""))))
                                                .append("regex", "pasaje")))
                                        .append("then", "Pasaje"),
                                new Document("case", new Document("$regexMatch",
                                        new Document("input", new Document("$toLower",
                                                new Document("$ifNull", List.of("$tipo", ""))))
                                                .append("regex", "recarga")))
                                        .append("then", "Recarga")
                        )).append("default", "Otros")))),
                new Document("$group", new Document("_id", "$categoria")
                        .append("cantidad", new Document("$sum", 1))),
                new Document("$sort", new Document("cantidad", -1))
        ), sistemaActual);

        List<ChartPoint> horariosPorEstado = !sistemaActual.permiteGestionConductores()
                ? List.of() : aggregate("horarios_conductor", List.of(
                new Document("$match", filtroHorariosSistema(sistemaActual)),
                new Document("$group", new Document("_id", "$estado")
                        .append("cantidad", new Document("$sum", 1))),
                new Document("$sort", new Document("cantidad", -1))
        ));

        List<ChartPoint> rutasMasProgramadas = !sistemaActual.permiteGestionConductores()
                ? List.of() : aggregate("horarios_conductor", List.of(
                new Document("$match", filtroHorariosSistema(sistemaActual)),
                new Document("$match", new Document("ruta",
                        new Document("$type", "string").append("$ne", ""))),
                new Document("$group", new Document("_id", "$ruta")
                        .append("cantidad", new Document("$sum", 1))),
                new Document("$sort", new Document("cantidad", -1)),
                new Document("$limit", 8)
        ));

        Map<String, MonthlyPoint> mesesConDatos = new HashMap<>();
        transaccionesMensuales.forEach(document -> {
            String mes = String.valueOf(document.get("_id"));
            mesesConDatos.put(mes, new MonthlyPoint(
                    mes, numberValue(document.get("cantidad")), numberValue(document.get("monto"))));
        });
        YearMonth primerMes = YearMonth.now(ZONA_CARTAGENA).minusMonths(11);
        List<MonthlyPoint> transaccionesPorMes = new ArrayList<>(12);
        for (int i = 0; i < 12; i++) {
            String mes = primerMes.plusMonths(i).toString();
            transaccionesPorMes.add(mesesConDatos.getOrDefault(mes, new MonthlyPoint(mes, 0, 0)));
        }

        EstadisticasAdmin stats = new EstadisticasAdmin(
                mongoTemplate.getCollection("usuarios").countDocuments(filtroUsuariosSistema(sistemaActual)),
                mongoTemplate.getCollection("transacciones").countDocuments(filtroSistema(sistemaActual)),
                !sistemaActual.permiteGestionConductores() ? 0L
                        : mongoTemplate.getCollection("horarios_conductor")
                                .countDocuments(filtroHorariosSistema(sistemaActual)),
                mongoTemplate.getCollection("avisos_administrativos")
                        .countDocuments(filtroAvisosSistema(sistemaActual)),
                usuariosPorRol,
                transaccionesPorMes,
                transaccionesPorTipo,
                horariosPorEstado,
                rutasMasProgramadas
        );

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(stats);
    }

    private List<ChartPoint> aggregateTransacciones(List<Document> pipeline, SistemaTransporte sistemaActual) {
        List<Document> pipelineConSistema = new ArrayList<>();
        pipelineConSistema.add(new Document("$match", filtroSistema(sistemaActual)));
        pipelineConSistema.addAll(pipeline);
        return aggregate("transacciones", pipelineConSistema);
    }

    private List<ChartPoint> aggregateUsuarios(List<Document> pipeline, SistemaTransporte sistemaActual) {
        List<Document> pipelineConSistema = new ArrayList<>();
        pipelineConSistema.add(new Document("$match", filtroUsuariosSistema(sistemaActual)));
        pipelineConSistema.addAll(pipeline);
        return aggregate("usuarios", pipelineConSistema);
    }

    private Document filtroUsuariosSistema(SistemaTransporte sistemaActual) {
        return new Document("$or", List.of(
                new Document("sitm", sistemaActual.name()),
                new Document("sistema_asignado", sistemaActual.name())));
    }

    private Document filtroSistema(SistemaTransporte sistemaActual) {
        if (sistemaActual == SistemaTransporte.TRANSCARIBE) {
            return new Document("$or", List.of(
                    new Document("sistema_transporte", SistemaTransporte.TRANSCARIBE.name()),
                    new Document("sistema_transporte", null)));
        }
        return new Document("sistema_transporte", sistemaActual.name());
    }

    private Document filtroAvisosSistema(SistemaTransporte sistemaActual) {
        if (sistemaActual == SistemaTransporte.TRANSCARIBE) {
            return new Document("$or", List.of(
                    new Document("sistema_transporte", SistemaTransporte.TRANSCARIBE.name()),
                    new Document("sistema_transporte", null)));
        }
        return new Document("sistema_transporte", sistemaActual.name());
    }

    private Document filtroHorariosSistema(SistemaTransporte sistemaActual) {
        if (sistemaActual == SistemaTransporte.TRANSCARIBE) {
            return new Document("$or", List.of(
                    new Document("sistema_transporte", SistemaTransporte.TRANSCARIBE.name()),
                    new Document("sistema_transporte", null)));
        }
        return new Document("sistema_transporte", sistemaActual.name());
    }

    private List<ChartPoint> aggregate(String collection, List<Document> pipeline) {
        return mongoTemplate.getCollection(collection)
                .aggregate(pipeline, Document.class)
                .into(new ArrayList<>())
                .stream()
                .map(document -> new ChartPoint(
                        document.get("_id") == null ? "Sin especificar" : String.valueOf(document.get("_id")),
                        numberValue(document.get("cantidad")),
                        numberValue(document.get("monto"))))
                .toList();
    }

    private double numberValue(Object value) {
        return value instanceof Number number ? number.doubleValue() : 0;
    }

    public record ChartPoint(String label, double cantidad, double monto) {}

    public record MonthlyPoint(String mes, double cantidad, double monto) {}

    public record EstadisticasAdmin(long totalUsuarios, long totalTransacciones, long totalAsignaciones,
                                    long totalAvisos, List<ChartPoint> usuariosPorRol,
                                    List<MonthlyPoint> transaccionesPorMes,
                                    List<ChartPoint> transaccionesPorTipo,
                                    List<ChartPoint> horariosPorEstado,
                                    List<ChartPoint> rutasMasProgramadas) {}
}

package com.Servicio.RecargasMovil.web;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.Servicio.RecargasMovil.model.CategoriaPaquete;
import com.Servicio.RecargasMovil.model.OperadorMovil;
import com.Servicio.RecargasMovil.model.PaqueteMovil;
import com.Servicio.RecargasMovil.model.RecargaMovil;
import com.Servicio.RecargasMovil.repository.OperadorMovilRepository;
import com.Servicio.RecargasMovil.repository.PaqueteMovilRepository;
import com.Servicio.RecargasMovil.repository.RecargaMovilRepository;
import com.Servicio.RecargasMovil.service.RecargasExcelReportService;
import com.Servicio.RecargasMovil.service.RecargasService;

@Controller
@RequestMapping("/admin")
public class AdminCatalogController {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdminCatalogController.class);
    private static final int PAGE_SIZE = 10;

    private final RecargasService recargasService;
    private final OperadorMovilRepository operadores;
    private final PaqueteMovilRepository paquetes;
    private final RecargaMovilRepository recargas;
    private final MongoTemplate mongoTemplate;
    private final RecargasExcelReportService excelReportService;
    private final String accesoUrl;

    public AdminCatalogController(
            RecargasService recargasService,
            OperadorMovilRepository operadores,
            PaqueteMovilRepository paquetes,
            RecargaMovilRepository recargas,
            MongoTemplate mongoTemplate,
            RecargasExcelReportService excelReportService,
            @Value("${app.acceso.base-url}") String accesoUrl) {
        this.recargasService = recargasService;
        this.operadores = operadores;
        this.paquetes = paquetes;
        this.recargas = recargas;
        this.mongoTemplate = mongoTemplate;
        this.excelReportService = excelReportService;
        this.accesoUrl = accesoUrl;
    }

    @ModelAttribute("serviciosUrl")
    public String serviciosUrl() {
        return accesoUrl + "/servicios";
    }

    @GetMapping({"", "/", "/catalogo"})
    public String dashboard(Model model) {
        addSummary(model);
        return "admin-catalogo";
    }

    @GetMapping("/reportes")
    public String reports(Model model) {
        model.addAttribute("operadores", operadores.findAll());
        return "admin-reportes";
    }

    @GetMapping("/reporte/excel")
    public ResponseEntity<StreamingResponseBody> downloadExcelReport(
            @RequestParam(defaultValue = "") String estado,
            @RequestParam(defaultValue = "") String operadorId,
            @RequestParam(defaultValue = "") String tipo,
            @RequestParam(name = "columnas", required = false) List<String> columnKeys,
            @RequestParam(required = false) LocalDate desde,
            @RequestParam(required = false) LocalDate hasta,
            @RequestParam(required = false) String nombreReporte) {
        List<RecargasExcelReportService.ReportColumn> columns =
                RecargasExcelReportService.ReportColumn.fromKeys(columnKeys);
        List<OperadorMovil> allOperators = operadores.findAll();
        boolean invalidDateRange = desde != null && hasta != null && desde.isAfter(hasta);
        boolean invalidStatus = !estado.isBlank()
                && !Set.of(RecargaMovil.STATUS_PROCESSING, RecargaMovil.STATUS_SIMULATED,
                        RecargaMovil.STATUS_REJECTED, RecargaMovil.STATUS_PAYMENT_PENDING).contains(estado);
        boolean invalidType = !tipo.isBlank() && !Set.of("saldo", "paquete").contains(tipo);
        boolean invalidOperator = !operadorId.isBlank()
                && allOperators.stream().noneMatch(operator -> operadorId.equals(operator.getId()));
        if (columns.isEmpty() || invalidDateRange || invalidStatus || invalidType || invalidOperator) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Selecciona al menos una columna y revisa los filtros del reporte.");
        }

        String operatorName = allOperators.stream()
                .filter(operator -> operadorId.equals(operator.getId()))
                .map(OperadorMovil::getNombre)
                .findFirst()
                .orElse("");
        Query query = new Query(rechargeCriteria("", estado, operatorName, tipo, desde, hasta))
                .with(Sort.by(Sort.Direction.DESC, "fecha"));
        String filename = reportFilename(nombreReporte);
        StreamingResponseBody file = output -> {
            try (Stream<RecargaMovil> rows = mongoTemplate.stream(query, RecargaMovil.class)) {
                excelReportService.writeReport(rows.iterator(), columns, output);
            } catch (IOException | RuntimeException exception) {
                LOGGER.error("No se pudo generar el reporte Excel de Recargas.", exception);
                throw exception;
            }
        };

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(filename, StandardCharsets.UTF_8)
                                .build().toString())
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(file);
    }

    @GetMapping("/operaciones")
    public String operations(
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "") String estado,
            @RequestParam(defaultValue = "") String operadorId,
            @RequestParam(defaultValue = "") String tipo,
            @RequestParam(required = false) LocalDate desde,
            @RequestParam(required = false) LocalDate hasta,
            @RequestParam(defaultValue = "0") int pagina,
            Model model) {
        var allOperators = operadores.findAll();
        int pageIndex = Math.max(0, pagina);
        boolean invalidDates = desde != null && hasta != null && desde.isAfter(hasta);
        boolean invalidStatus = !estado.isBlank()
                && !Set.of(RecargaMovil.STATUS_PROCESSING, RecargaMovil.STATUS_SIMULATED,
                        RecargaMovil.STATUS_REJECTED, RecargaMovil.STATUS_PAYMENT_PENDING).contains(estado);
        boolean invalidType = !tipo.isBlank() && !Set.of("saldo", "paquete").contains(tipo);
        boolean invalidOperator = !operadorId.isBlank()
                && allOperators.stream().noneMatch(operator -> operadorId.equals(operator.getId()));
        boolean invalidFilters = invalidDates || invalidStatus || invalidType || invalidOperator;
        PageRequest pageRequest = rechargePageRequest(pageIndex);
        Page<RecargaMovil> rechargePage = invalidFilters
                ? Page.empty(pageRequest)
                : findRecharges(q, estado, operadorId, tipo, desde, hasta, pageIndex);

        model.addAttribute("operadores", allOperators);
        model.addAttribute("recargasPage", rechargePage);
        model.addAttribute("filtroQ", q);
        model.addAttribute("filtroEstado", estado);
        model.addAttribute("filtroOperador", operadorId);
        model.addAttribute("filtroTipo", tipo);
        model.addAttribute("filtroDesde", desde);
        model.addAttribute("filtroHasta", hasta);
        model.addAttribute("pagina", pageIndex);
        model.addAttribute("errorFiltros", invalidFilters
                ? "Revisa los filtros: el rango de fechas, el operador, el estado o el tipo no son válidos."
                : null);
        addSummary(model);
        return "admin-operaciones";
    }

    @GetMapping("/operadores")
    public String operators(Model model) {
        model.addAttribute("operadores", operadores.findAll());
        addSummary(model);
        return "admin-operadores";
    }

    @GetMapping("/paquetes")
    public String packages(Model model) {
        var allPackages = paquetes.findAllByOrderByNombreAsc();
        Map<String, String> operatorNames = operadores.findAll().stream()
                .collect(Collectors.toMap(OperadorMovil::getId, OperadorMovil::getNombre));
        model.addAttribute("paquetes", allPackages);
        model.addAttribute("operatorNames", operatorNames);
        addSummary(model);
        return "admin-paquetes";
    }

    @GetMapping("/paquetes/nuevo")
    public String newPackage(Model model) {
        model.addAttribute("paquete", new PaqueteMovil());
        model.addAttribute("categorias", packageCategories());
        model.addAttribute("modoEdicion", false);
        model.addAttribute("operadoresActivos", recargasService.operadoresActivos());
        return "admin-paquete-nuevo";
    }

    @GetMapping("/paquetes/{paqueteId}/editar")
    public String editPackage(@PathVariable String paqueteId, Model model, RedirectAttributes redirectAttributes) {
        PaqueteMovil mobilePackage = paquetes.findById(paqueteId).orElse(null);
        if (mobilePackage == null) {
            redirectAttributes.addFlashAttribute("error", "No se encontró el paquete seleccionado.");
            return "redirect:/admin/paquetes";
        }
        model.addAttribute("paquete", mobilePackage);
        model.addAttribute("categorias", packageCategories());
        model.addAttribute("modoEdicion", true);
        model.addAttribute("operadoresActivos", recargasService.operadoresActivos());
        return "admin-paquete-editar";
    }

    @PostMapping({"/operadores", "/catalogo/operadores"})
    public String createOperator(
            @RequestParam String nombre,
            RedirectAttributes redirectAttributes) {
        try {
            recargasService.crearOperador(nombre);
            redirectAttributes.addFlashAttribute("mensaje", "Operador creado.");
        } catch (IllegalArgumentException exception) {
            redirectAttributes.addFlashAttribute("error", exception.getMessage());
        }
        return "redirect:/admin/operadores";
    }

    @PostMapping({"/paquetes", "/catalogo/paquetes"})
    public String createPackage(
            @RequestParam String operadorId,
            @RequestParam String nombre,
            @RequestParam(required = false) String descripcion,
            @RequestParam BigDecimal precio,
            @RequestParam(defaultValue = "PAQUETE") CategoriaPaquete categoria,
            RedirectAttributes redirectAttributes) {
        try {
            recargasService.crearPaquete(operadorId, nombre, descripcion, precio, categoria);
            redirectAttributes.addFlashAttribute("mensaje", "Paquete creado.");
        } catch (IllegalArgumentException | ArithmeticException exception) {
            redirectAttributes.addFlashAttribute("error", exception.getMessage());
            return "redirect:/admin/paquetes/nuevo";
        }
        return "redirect:/admin/paquetes";
    }

    @PostMapping("/paquetes/{paqueteId}/editar")
    public String updatePackage(
            @PathVariable String paqueteId,
            @RequestParam String operadorId,
            @RequestParam String nombre,
            @RequestParam(required = false) String descripcion,
            @RequestParam BigDecimal precio,
            @RequestParam CategoriaPaquete categoria,
            RedirectAttributes redirectAttributes) {
        try {
            recargasService.actualizarPaquete(paqueteId, operadorId, nombre, descripcion, precio, categoria);
            redirectAttributes.addFlashAttribute("mensaje", "Paquete actualizado.");
        } catch (IllegalArgumentException | ArithmeticException exception) {
            redirectAttributes.addFlashAttribute("error", exception.getMessage());
            return "redirect:/admin/paquetes/" + paqueteId + "/editar";
        }
        return "redirect:/admin/paquetes";
    }

    @PostMapping({"/operadores/estado", "/catalogo/operadores/estado"})
    public String toggleOperator(
            @RequestParam String operadorId,
            RedirectAttributes redirectAttributes) {
        OperadorMovil operator = operadores.findById(operadorId).orElse(null);
        if (operator == null) {
            redirectAttributes.addFlashAttribute("error", "No se encontró el operador seleccionado.");
        } else {
            operator.setActivo(!operator.isActivo());
            operadores.save(operator);
            redirectAttributes.addFlashAttribute("mensaje",
                    operator.isActivo() ? "Operador activado." : "Operador desactivado.");
        }
        return "redirect:/admin/operadores";
    }

    @PostMapping({"/paquetes/estado", "/catalogo/paquetes/estado"})
    public String togglePackage(
            @RequestParam String paqueteId,
            RedirectAttributes redirectAttributes) {
        PaqueteMovil mobilePackage = paquetes.findById(paqueteId).orElse(null);
        if (mobilePackage == null) {
            redirectAttributes.addFlashAttribute("error", "No se encontró el paquete seleccionado.");
        } else {
            mobilePackage.setActivo(!mobilePackage.isActivo());
            paquetes.save(mobilePackage);
            redirectAttributes.addFlashAttribute("mensaje",
                    mobilePackage.isActivo() ? "Paquete activado." : "Paquete desactivado.");
        }
        return "redirect:/admin/paquetes";
    }

    private void addSummary(Model model) {
        var allPackages = paquetes.findAllByOrderByNombreAsc();
        model.addAttribute("totalRecargas", recargas.count());
        model.addAttribute("recargasProcesando", recargas.countByEstado(RecargaMovil.STATUS_PROCESSING));
        model.addAttribute("recargasPagoPendiente",
                recargas.countByEstado(RecargaMovil.STATUS_PAYMENT_PENDING));
        model.addAttribute("recargasSimuladas", recargas.countByEstado(RecargaMovil.STATUS_SIMULATED));
        model.addAttribute("recargasRechazadas", recargas.countByEstado(RecargaMovil.STATUS_REJECTED));
        model.addAttribute("totalOperadores", operadores.count());
        model.addAttribute("totalPaquetes", allPackages.size());
        model.addAttribute("paquetesActivos", allPackages.stream().filter(PaqueteMovil::isActivo).count());
    }

    private Page<RecargaMovil> findRecharges(
            String q,
            String estado,
            String operadorId,
            String tipo,
            LocalDate desde,
            LocalDate hasta,
            int pageIndex) {
        String operatorName = operadorId.isBlank()
                ? ""
                : operadores.findById(operadorId).map(OperadorMovil::getNombre).orElse("");
        Criteria criteria = rechargeCriteria(q, estado, operatorName, tipo, desde, hasta);
        Query countQuery = new Query(criteria);
        Query pageQuery = new Query(criteria).with(rechargePageRequest(pageIndex));
        List<RecargaMovil> content = mongoTemplate.find(pageQuery, RecargaMovil.class);
        long total = mongoTemplate.count(countQuery, RecargaMovil.class);
        return new PageImpl<>(content, rechargePageRequest(pageIndex), total);
    }

    private Criteria rechargeCriteria(
            String q,
            String estado,
            String operatorName,
            String tipo,
            LocalDate desde,
            LocalDate hasta) {
        List<Criteria> filters = new ArrayList<>();
        if (!q.isBlank()) {
            Pattern searchPattern = Pattern.compile(
                    ".*" + Pattern.quote(q.trim()) + ".*", Pattern.CASE_INSENSITIVE);
            filters.add(new Criteria().orOperator(
                    Criteria.where("operacionId").regex(searchPattern),
                    Criteria.where("usuarioId").regex(searchPattern),
                    Criteria.where("telefono").regex(searchPattern),
                    Criteria.where("operador").regex(searchPattern),
                    Criteria.where("paquete").regex(searchPattern),
                    Criteria.where("idPagoProveedor").regex(searchPattern)));
        }
        if (!estado.isBlank()) {
            filters.add(Criteria.where("estado").is(estado));
        }
        if (!operatorName.isBlank()) {
            filters.add(Criteria.where("operador").is(operatorName));
        }
        if ("saldo".equals(tipo)) {
            filters.add(Criteria.where("paquete").regex("^Recarga de saldo", "i"));
        } else if ("paquete".equals(tipo)) {
            filters.add(Criteria.where("paquete").not().regex("^Recarga de saldo", "i"));
        }
        if (desde != null || hasta != null) {
            Criteria dateFilter = Criteria.where("fecha");
            if (desde != null) {
                dateFilter.gte(desde.atStartOfDay());
            }
            if (hasta != null) {
                dateFilter.lt(hasta.plusDays(1).atStartOfDay());
            }
            filters.add(dateFilter);
        }

        Criteria criteria = filters.isEmpty()
                ? new Criteria()
                : filters.size() == 1 ? filters.get(0) : new Criteria().andOperator(filters);
        return criteria;
    }

    private static String reportFilename(String requestedName) {
        String filename = requestedName == null || requestedName.isBlank()
                ? "Reporte_Recargas_" + LocalDate.now()
                : requestedName.trim()
                        .replaceAll("[\\p{Cntrl}<>:\"/\\\\|?*]", "_")
                        .replaceAll("[. ]+$", "");
        if (filename.length() > 100) {
            filename = filename.substring(0, 100).replaceAll("[. ]+$", "");
        }
        if (filename.isBlank()) {
            filename = "Reporte_Recargas_" + LocalDate.now();
        }
        if (!filename.toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            filename += ".xlsx";
        }
        return filename;
    }

    private static PageRequest rechargePageRequest(int pageIndex) {
        return PageRequest.of(pageIndex, PAGE_SIZE, Sort.by(Sort.Direction.DESC, "fecha"));
    }

    private static List<CategoriaPaquete> packageCategories() {
        return java.util.Arrays.stream(CategoriaPaquete.values())
                .filter(category -> category != CategoriaPaquete.RECARGA_DINERO)
                .toList();
    }
}

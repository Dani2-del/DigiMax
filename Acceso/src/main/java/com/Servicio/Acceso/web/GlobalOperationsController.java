package com.Servicio.Acceso.web;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import com.Servicio.Acceso.model.Usuario;
import com.Servicio.Acceso.repository.UsuarioRepository;
import com.Servicio.Acceso.service.GlobalOperationsService;
import com.Servicio.Acceso.service.GlobalOperationsService.GlobalTransaction;
import com.Servicio.Acceso.service.GlobalOperationsService.TransactionSnapshot;

@Controller
@RequestMapping("/admin/global")
public class GlobalOperationsController {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final ZoneId REPORT_ZONE = ZoneId.of("America/Bogota");
    private static final int EXCEL_MAX_DATA_ROWS = 1_048_575;
    private static final int TRANSACTION_PAGE_SIZE = 10;
    private static final Set<String> SERVICE_CODES = Set.of(
            "TRANSCARIBE", "TRANSMILENIO", "TRANSMETRO", "BILLETERA", "RECARGAS_MOVIL");
    private static final Map<String, String> SERVICES = Map.of(
            "TRANSCARIBE", "Transcaribe",
            "TRANSMILENIO", "TransMilenio",
            "TRANSMETRO", "Transmetro",
            "BILLETERA", "Billetera",
            "RECARGAS_MOVIL", "Recargas móviles");
    private static final List<String> SERVICE_ORDER = List.of(
            "TRANSCARIBE", "TRANSMILENIO", "TRANSMETRO", "BILLETERA", "RECARGAS_MOVIL");

    private final UsuarioRepository usuarios;
    private final GlobalOperationsService operations;
    private final String transporteUrl;

    public GlobalOperationsController(
            UsuarioRepository usuarios,
            GlobalOperationsService operations,
            @Value("${app.services.transporte-url}") String transporteUrl) {
        this.usuarios = usuarios;
        this.operations = operations;
        this.transporteUrl = transporteUrl;
    }

    @GetMapping("/transacciones")
    public String transactions(
            @RequestParam(required = false) LocalDate desde,
            @RequestParam(required = false) LocalDate hasta,
            @RequestParam(required = false) List<String> servicios,
            @RequestParam(defaultValue = "0") int page,
            Model model) {
        Set<String> selected = normalizeServices(servicios);
        if (!validRange(desde, hasta)) {
            model.addAttribute("errorFiltro", "La fecha inicial no puede ser posterior a la fecha final.");
            desde = null;
            hasta = null;
        }
        TransactionSnapshot snapshot = operations.transactions(selected, desde, hasta);
        int totalPages = (int) Math.ceil((double) snapshot.transactions().size() / TRANSACTION_PAGE_SIZE);
        int pageIndex = Math.max(0, Math.min(page, Math.max(0, totalPages - 1)));
        addTransactionModel(model, snapshot, desde, hasta, selected, pageIndex, totalPages);
        model.addAttribute("title", "Transacciones globales");
        return "admin/global-transacciones";
    }

    @GetMapping("/analitica")
    public String analytics(Model model) {
        LocalDate today = LocalDate.now(REPORT_ZONE);
        LocalDate firstMonth = YearMonth.from(today).minusMonths(11).atDay(1);
        TransactionSnapshot snapshot = operations.transactions(SERVICE_CODES, firstMonth, today);

        List<MonthlyMetric> monthly = monthlyMetrics(snapshot.transactions());
        Map<String, Long> transactionCounts = snapshot.transactions().stream()
                .collect(Collectors.groupingBy(GlobalTransaction::service, Collectors.counting()));
        List<ServiceMetric> serviceMetrics = SERVICE_ORDER.stream()
                .map(code -> new ServiceMetric(SERVICES.get(code), transactionCounts.getOrDefault(code, 0L),
                        snapshot.transactions().stream()
                                .filter(transaction -> transaction.service().equals(code))
                                .map(GlobalTransaction::amount)
                                .reduce(BigDecimal.ZERO, BigDecimal::add)))
                .toList();
        Map<String, Long> roleCounts = usuarios.findAll().stream()
                .collect(Collectors.groupingBy(user -> user.getRole() == null ? "Sin rol" : user.getRole(),
                        Collectors.counting()));
        long maxMonthly = monthly.stream().mapToLong(MonthlyMetric::count).max().orElse(0);
        long maxService = serviceMetrics.stream().mapToLong(ServiceMetric::count).max().orElse(0);
        model.addAttribute("monthly", monthly.stream()
                .map(point -> point.withBar(maxMonthly == 0 ? 0 : (int) Math.max(5, point.count() * 100 / maxMonthly)))
                .toList());
        model.addAttribute("services", serviceMetrics.stream()
                .map(point -> point.withBar(maxService == 0 ? 0 : (int) Math.max(5, point.count() * 100 / maxService)))
                .toList());
        model.addAttribute("roles", roleCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(entry -> new RoleMetric(entry.getKey(), entry.getValue()))
                .toList());
        model.addAttribute("totalTransacciones", snapshot.total());
        model.addAttribute("montoTotal", snapshot.totalAmount());
        model.addAttribute("erroresFuentes", snapshot.unavailableServices());
        model.addAttribute("fechaActualizacion", LocalDateTime.now().format(DATE_FORMAT));
        return "admin/global-analitica";
    }

    @GetMapping("/chats")
    public String chats(Model model) {
        model.addAttribute("transportChats", List.of(
                new ChatLink("Transcaribe", transporteUrl + "/admin/chat/TRANSCARIBE", true),
                new ChatLink("TransMilenio", transporteUrl + "/admin/chat/TRANSMILENIO", true),
                new ChatLink("Transmetro", transporteUrl + "/admin/chat/TRANSMETRO", true)));
        model.addAttribute("plannedChats", List.of("Billetera", "Recargas móviles"));
        return "admin/global-chats";
    }

    @GetMapping("/reportes")
    public String reports(Model model) {
        model.addAttribute("services", SERVICE_ORDER.stream()
                .map(code -> new ServiceOption(code, SERVICES.get(code))).toList());
        model.addAttribute("defaultServices", SERVICE_ORDER);
        return "admin/global-reportes";
    }

    @PostMapping("/reportes/excel")
    public ResponseEntity<byte[]> exportExcel(
            @RequestParam(required = false) List<String> servicios,
            @RequestParam(required = false) List<String> conjuntos,
            @RequestParam(required = false) LocalDate desde,
            @RequestParam(required = false) LocalDate hasta) {
        Set<String> selectedServices = normalizeServices(servicios);
        Set<String> selectedData = conjuntos == null ? Set.of()
                : conjuntos.stream().map(value -> value.toUpperCase(Locale.ROOT))
                        .collect(Collectors.toCollection(LinkedHashSet::new));
        if (selectedData.isEmpty() || !Set.of("USUARIOS", "TRANSACCIONES").containsAll(selectedData)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Selecciona al menos un conjunto válido: usuarios o transacciones.");
        }
        if (servicios != null && servicios.stream()
                .map(value -> value.toUpperCase(Locale.ROOT)).anyMatch(value -> !SERVICE_CODES.contains(value))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La selección contiene un servicio inválido.");
        }
        if (selectedData.contains("TRANSACCIONES") && (servicios == null || selectedServices.isEmpty())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Selecciona al menos un servicio para exportar transacciones.");
        }
        if (!validRange(desde, hasta)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La fecha inicial no puede ser posterior a la fecha final.");
        }

        List<Usuario> userRows = selectedData.contains("USUARIOS")
                ? usuarios.findAll().stream()
                        .filter(user -> inDateRange(user.getFechaRegistro(), desde, hasta))
                        .sorted(Comparator.comparing(Usuario::getNombre,
                                Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                        .toList()
                : List.of();
        TransactionSnapshot snapshot = selectedData.contains("TRANSACCIONES")
                ? operations.transactions(selectedServices, desde, hasta)
                : new TransactionSnapshot(List.of(), Map.of());
        if (!snapshot.unavailableServices().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "No se generó el Excel porque no se pudieron consultar todos los servicios seleccionados: "
                            + String.join(", ", snapshot.unavailableServices().keySet()));
        }

        Map<String, Usuario> usersById = selectedData.contains("TRANSACCIONES")
                ? usuarios.findAll().stream().collect(Collectors.toMap(
                        Usuario::getId, user -> user, (first, ignored) -> first))
                : Map.of();
        if (userRows.size() > EXCEL_MAX_DATA_ROWS
                || snapshot.transactions().size() > EXCEL_MAX_DATA_ROWS) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,
                    "El resultado supera el máximo de filas por hoja de Excel. Reduce el rango de fechas.");
        }
        byte[] workbook = createWorkbook(
                selectedData, selectedServices, userRows, snapshot.transactions(), usersById, desde, hasta);
        String filename = "Digitrans_Global_" + LocalDate.now(REPORT_ZONE) + ".xlsx";
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(workbook);
    }

    private void addTransactionModel(
            Model model, TransactionSnapshot snapshot, LocalDate desde, LocalDate hasta, Set<String> selected,
            int pageIndex, int totalPages) {
        Map<String, Long> counts = snapshot.transactions().stream()
                .collect(Collectors.groupingBy(GlobalTransaction::service, Collectors.counting()));
        Map<String, Usuario> usersById = snapshot.transactions().stream()
                .map(GlobalTransaction::userId)
                .filter(userId -> userId != null && !userId.isBlank())
                .distinct()
                .collect(Collectors.collectingAndThen(Collectors.toList(), ids -> {
                    Map<String, Usuario> found = new LinkedHashMap<>();
                    usuarios.findAll().stream().filter(user -> ids.contains(user.getId()))
                            .forEach(user -> found.put(user.getId(), user));
                    return found;
                }));
        int fromIndex = pageIndex * TRANSACTION_PAGE_SIZE;
        int toIndex = Math.min(fromIndex + TRANSACTION_PAGE_SIZE, snapshot.transactions().size());
        model.addAttribute("transactions", snapshot.transactions().subList(fromIndex, toIndex).stream()
                .map(transaction -> enrich(transaction, usersById.get(transaction.userId())))
                .toList());
        model.addAttribute("pageIndex", pageIndex);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("hasPreviousPage", pageIndex > 0);
        model.addAttribute("hasNextPage", pageIndex + 1 < totalPages);
        model.addAttribute("firstTransaction", snapshot.transactions().isEmpty() ? 0 : fromIndex + 1);
        model.addAttribute("lastTransaction", toIndex);
        model.addAttribute("totalTransacciones", snapshot.total());
        model.addAttribute("montoTotal", snapshot.totalAmount());
        model.addAttribute("serviceMetrics", SERVICE_ORDER.stream()
                .map(code -> new ServiceMetric(SERVICES.get(code), counts.getOrDefault(code, 0L),
                        snapshot.transactions().stream()
                                .filter(transaction -> transaction.service().equals(code))
                                .map(GlobalTransaction::amount)
                                .reduce(BigDecimal.ZERO, BigDecimal::add)))
                .toList());
        model.addAttribute("sourceErrors", snapshot.unavailableServices());
        model.addAttribute("desde", desde);
        model.addAttribute("hasta", hasta);
        model.addAttribute("selectedServices", selected);
        model.addAttribute("serviceNames", SERVICES);
        model.addAttribute("serviceOptions", SERVICE_ORDER.stream()
                .map(code -> new ServiceOption(code, SERVICES.get(code))).toList());
    }

    private static List<MonthlyMetric> monthlyMetrics(List<GlobalTransaction> transactions) {
        Map<YearMonth, Long> counts = new LinkedHashMap<>();
        YearMonth first = YearMonth.from(LocalDate.now()).minusMonths(11);
        for (int month = 0; month < 12; month++) {
            counts.put(first.plusMonths(month), 0L);
        }
        transactions.stream()
                .filter(transaction -> transaction.occurredAt() != null)
                .forEach(transaction -> counts.computeIfPresent(
                        YearMonth.from(transaction.occurredAt()), (ignored, count) -> count + 1));
        return counts.entrySet().stream()
                .map(entry -> new MonthlyMetric(entry.getKey().toString(), entry.getValue(), 0))
                .toList();
    }

    private static Set<String> normalizeServices(List<String> services) {
        if (services == null || services.isEmpty()) return SERVICE_CODES;
        Set<String> normalized = services.stream().map(value -> value.toUpperCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
        if (!SERVICE_CODES.containsAll(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La selección contiene un servicio inválido.");
        }
        return normalized;
    }

    private static boolean validRange(LocalDate from, LocalDate through) {
        return from == null || through == null || !from.isAfter(through);
    }

    private static boolean inDateRange(LocalDateTime value, LocalDate from, LocalDate through) {
        if (from == null && through == null) return true;
        return value != null && (from == null || !value.toLocalDate().isBefore(from))
                && (through == null || !value.toLocalDate().isAfter(through));
    }

    private static GlobalTransaction enrich(GlobalTransaction transaction, Usuario user) {
        if (user == null) return transaction;
        return new GlobalTransaction(
                transaction.operationId(), transaction.service(), transaction.type(), transaction.description(),
                transaction.userId(),
                transaction.userName().isBlank() ? user.getNombre() : transaction.userName(),
                transaction.userEmail().isBlank() ? user.getCorreo() : transaction.userEmail(),
                transaction.amount(), transaction.status(), transaction.occurredAt(), transaction.source());
    }

    private static byte[] createWorkbook(
            Set<String> dataSets,
            Set<String> services,
            List<Usuario> users,
            List<GlobalTransaction> transactions,
            Map<String, Usuario> usersById,
            LocalDate from,
            LocalDate through) {
        try (SXSSFWorkbook workbook = new SXSSFWorkbook(100);
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            workbook.setCompressTempFiles(true);
            if (dataSets.contains("USUARIOS")) {
                writeUsersSheet(workbook, users);
            }
            if (dataSets.contains("TRANSACCIONES")) {
                writeTransactionsSheet(workbook, transactions, usersById);
            }
            writeFiltersSheet(workbook, dataSets, services, from, through);
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("No se pudo generar el archivo Excel global.", exception);
        }
    }

    private static void writeUsersSheet(SXSSFWorkbook workbook, List<Usuario> users) {
        var sheet = workbook.createSheet("Usuarios");
        Row header = sheet.createRow(0);
        String[] columns = {"ID", "Nombre", "Correo", "Rol", "Servicio asignado", "Activo", "Verificado", "Registro"};
        writeHeaders(header, columns);
        for (int index = 0; index < users.size(); index++) {
            Usuario user = users.get(index);
            Row row = sheet.createRow(index + 1);
            set(row, 0, user.getId());
            set(row, 1, user.getNombre());
            set(row, 2, user.getCorreo());
            set(row, 3, user.getRole());
            set(row, 4, user.getServicioAsignado());
            set(row, 5, user.isActivo() ? "Sí" : "No");
            set(row, 6, user.isVerificado() ? "Sí" : "No");
            set(row, 7, user.getFechaRegistro() == null ? "" : user.getFechaRegistro().format(DATE_FORMAT));
        }
        sizeColumns(sheet, columns.length);
    }

    private static void writeTransactionsSheet(
            SXSSFWorkbook workbook, List<GlobalTransaction> transactions, Map<String, Usuario> usersById) {
        var sheet = workbook.createSheet("Transacciones");
        Row header = sheet.createRow(0);
        String[] columns = {"Operación", "Servicio", "Tipo", "Descripción", "Usuario", "Correo",
                "Monto COP", "Estado", "Fecha", "Origen"};
        writeHeaders(header, columns);
        for (int index = 0; index < transactions.size(); index++) {
            GlobalTransaction transaction = transactions.get(index);
            Usuario user = usersById.get(transaction.userId());
            Row row = sheet.createRow(index + 1);
            set(row, 0, transaction.operationId());
            set(row, 1, SERVICES.getOrDefault(transaction.service(), transaction.service()));
            set(row, 2, transaction.type());
            set(row, 3, transaction.description());
            set(row, 4, transaction.userName().isBlank() && user != null ? user.getNombre() : transaction.userName());
            set(row, 5, transaction.userEmail().isBlank() && user != null ? user.getCorreo() : transaction.userEmail());
            row.createCell(6).setCellValue(transaction.amount().doubleValue());
            set(row, 7, transaction.status());
            set(row, 8, transaction.occurredAt() == null ? "" : transaction.occurredAt().format(DATE_FORMAT));
            set(row, 9, transaction.source());
        }
        sizeColumns(sheet, columns.length);
    }

    private static void writeFiltersSheet(
            SXSSFWorkbook workbook, Set<String> dataSets, Set<String> services,
            LocalDate from, LocalDate through) {
        var sheet = workbook.createSheet("Parámetros");
        String[][] values = {
                {"Conjunto de datos", List.of("USUARIOS", "TRANSACCIONES").stream()
                        .filter(dataSets::contains).collect(Collectors.joining(", "))},
                {"Servicios", dataSets.contains("TRANSACCIONES")
                        ? SERVICE_ORDER.stream().filter(services::contains)
                                .map(SERVICES::get).collect(Collectors.joining(", "))
                        : "No aplica"},
                {"Desde", from == null ? "Sin límite" : from.toString()},
                {"Hasta", through == null ? "Sin límite" : through.toString()},
                {"Generado", LocalDateTime.now(REPORT_ZONE).format(DATE_FORMAT)},
                {"Criterio", "Operaciones únicas por ID; se conserva una fila por registro sin ID"}
        };
        for (int index = 0; index < values.length; index++) {
            Row row = sheet.createRow(index);
            set(row, 0, values[index][0]);
            set(row, 1, values[index][1]);
        }
        sheet.setColumnWidth(0, 6200);
        sheet.setColumnWidth(1, 18000);
    }

    private static void writeHeaders(Row header, String[] columns) {
        for (int index = 0; index < columns.length; index++) {
            set(header, index, columns[index]);
        }
    }

    private static void set(Row row, int column, String value) {
        Cell cell = row.createCell(column);
        cell.setCellValue(value == null ? "" : value);
    }

    private static void sizeColumns(org.apache.poi.ss.usermodel.Sheet sheet, int count) {
        for (int index = 0; index < count; index++) {
            sheet.setColumnWidth(index, index == 3 ? 11000 : 6200);
        }
        sheet.createFreezePane(0, 1);
    }

    public record ServiceOption(String code, String name) {
    }

    public record ServiceMetric(String name, long count, BigDecimal amount, int barPercent) {
        public ServiceMetric(String name, long count, BigDecimal amount) {
            this(name, count, amount, 0);
        }

        ServiceMetric withBar(int percent) {
            return new ServiceMetric(name, count, amount, percent);
        }
    }

    public record MonthlyMetric(String label, long count, int barPercent) {
        MonthlyMetric withBar(int percent) {
            return new MonthlyMetric(label, count, percent);
        }
    }

    public record RoleMetric(String role, long count) {
    }

    public record ChatLink(String name, String url, boolean available) {
    }
}

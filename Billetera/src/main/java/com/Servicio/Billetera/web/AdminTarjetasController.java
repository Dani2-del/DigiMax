package com.Servicio.Billetera.web;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.Comparator;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.Servicio.Billetera.model.BilleteraUniversal;
import com.Servicio.Billetera.model.EstadoTarjeta;
import com.Servicio.Billetera.model.MovimientoBilletera;
import com.Servicio.Billetera.security.AuthenticatedIdentity;
import com.Servicio.Billetera.service.BilleteraService;
import com.Servicio.Billetera.service.BilleteraExcelReportService;
import com.Servicio.Billetera.service.BilleteraExcelReportService.MovementReportRow;
import com.Servicio.Billetera.service.SaldoInsuficienteException;
import com.Servicio.Billetera.service.WalletUserDirectoryClient;
import com.Servicio.Billetera.service.WalletUserDirectoryClient.UserSummary;

@Controller
@RequestMapping("/admin")
public class AdminTarjetasController {

    private final BilleteraService billeteraService;
    private final WalletUserDirectoryClient userDirectory;
    private final BilleteraExcelReportService excelReportService;
    @Value("${app.acceso.base-url}")
    private String accesoUrl;

    private static final int MOVEMENT_PAGE_SIZE = 5;

    public AdminTarjetasController(
            BilleteraService billeteraService,
            WalletUserDirectoryClient userDirectory,
            BilleteraExcelReportService excelReportService) {
        this.billeteraService = billeteraService;
        this.userDirectory = userDirectory;
        this.excelReportService = excelReportService;
    }

    @ModelAttribute("serviciosUrl")
    public String serviciosUrl() {
        return accesoUrl + "/servicios";
    }

    @GetMapping("/dashboard")
    public String dashboard(
            @AuthenticationPrincipal AuthenticatedIdentity identity,
            Model model) {
        List<BilleteraUniversal> wallets = billeteraService.billeterasAdministrativas();
        long activeCount = wallets.stream()
                .filter(BilleteraUniversal::isActivadaPorUsuario)
                .filter(wallet -> wallet.getEstado() == EstadoTarjeta.ACTIVA)
                .count();
        long frozenCount = wallets.stream()
                .filter(BilleteraUniversal::isActivadaPorUsuario)
                .filter(wallet -> wallet.getEstado() == EstadoTarjeta.CONGELADA)
                .count();
        BigDecimal totalBalance = wallets.stream()
                .filter(BilleteraUniversal::isActivadaPorUsuario)
                .filter(wallet -> wallet.getEstado() != EstadoTarjeta.DESACTIVADA)
                .map(BilleteraUniversal::getSaldo)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        model.addAttribute("identity", identity);
        model.addAttribute("totalCuentas", wallets.size());
        model.addAttribute("tarjetasActivas", activeCount);
        model.addAttribute("tarjetasCongeladas", frozenCount);
        model.addAttribute("saldoAdministrado", totalBalance);
        return "admin-dashboard-billetera";
    }

    @GetMapping({"", "/"})
    public String adminHome() {
        return "redirect:/admin/dashboard";
    }

    @GetMapping("/reportes")
    public String reports(Model model) {
        List<BilleteraUniversal> wallets = billeteraService.billeterasAdministrativas();
        model.addAttribute("tipos", movementTypes(wallets));
        model.addAttribute("servicios", movementServices(wallets));
        return "admin-reportes-billetera";
    }

    @GetMapping("/reporte/excel")
    public ResponseEntity<byte[]> downloadExcelReport(
            @RequestParam(name = "columnas", required = false) List<String> columnKeys,
            @RequestParam(defaultValue = "") String tipo,
            @RequestParam(defaultValue = "") String servicio,
            @RequestParam(required = false) LocalDate desde,
            @RequestParam(required = false) LocalDate hasta) {
        List<BilleteraExcelReportService.ReportColumn> columns =
                BilleteraExcelReportService.ReportColumn.fromKeys(columnKeys);
        if (columns.isEmpty() || (desde != null && hasta != null && desde.isAfter(hasta))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Selecciona al menos una columna y revisa el rango de fechas.");
        }

        List<BilleteraUniversal> wallets = billeteraService.billeterasAdministrativas();
        if ((!tipo.isBlank() && !movementTypes(wallets).contains(tipo))
                || (!servicio.isBlank() && !movementServices(wallets).contains(servicio))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El tipo o servicio seleccionado no es válido.");
        }
        List<MovementReportRow> reportRows = wallets.stream()
                .flatMap(wallet -> wallet.getMovimientos().stream()
                        .map(movement -> new MovementReportRow(wallet.getUsuarioId(), movement)))
                .filter(row -> tipo.isBlank()
                        || tipo.equalsIgnoreCase(row.movement().getTipo()))
                .filter(row -> servicio.isBlank()
                        || servicio.equalsIgnoreCase(row.movement().getServicio()))
                .filter(row -> inDateRange(row.movement().getFecha(), desde, hasta))
                .sorted(Comparator.comparing(
                        (MovementReportRow row) -> row.movement().getFecha(),
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        try {
            byte[] content = excelReportService.createReport(reportRows, columns);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            ContentDisposition.attachment()
                                    .filename("Reporte_Billetera_" + LocalDate.now() + ".xlsx",
                                            StandardCharsets.UTF_8)
                                    .build().toString())
                    .contentType(MediaType.parseMediaType(
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .contentLength(content.length)
                    .body(content);
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("No se pudo generar el reporte Excel de Billetera.", exception);
        }
    }

    @GetMapping("/cuentas")
    public String index(
            @RequestParam(defaultValue = "") String buscar,
            @RequestParam(required = false) BigDecimal saldoDesde,
            @RequestParam(required = false) BigDecimal saldoHasta,
            @AuthenticationPrincipal AuthenticatedIdentity identity,
            HttpServletRequest request,
            Model model) {
        List<BilleteraUniversal> allWallets = billeteraService.billeterasAdministrativas();
        String query = buscar.trim();
        String normalizedQuery = query.toLowerCase(Locale.ROOT);
        String token = accessToken(request);
        boolean invalidBalanceRange = (saldoDesde != null && saldoDesde.signum() < 0)
                || (saldoHasta != null && saldoHasta.signum() < 0)
                || (saldoDesde != null && saldoHasta != null && saldoDesde.compareTo(saldoHasta) > 0);
        Map<String, BilleteraUniversal> walletsById = allWallets.stream()
                .collect(Collectors.toMap(BilleteraUniversal::getUsuarioId, Function.identity()));
        Map<String, WalletRow> accountRows = new LinkedHashMap<>();
        allWallets.stream()
                .filter(wallet -> !invalidBalanceRange)
                .filter(wallet -> !query.isBlank()
                        && (contains(wallet.getUsuarioId(), normalizedQuery)
                        || contains(wallet.getNumeroTarjeta(), normalizedQuery)))
                .filter(wallet -> cumpleFiltroSaldo(wallet, saldoDesde, saldoHasta))
                .limit(5)
                .forEach(wallet -> accountRows.put(wallet.getUsuarioId(), toWalletRow(
                        wallet, userDirectory.findById(wallet.getUsuarioId(), token))));

        int page = 0;
        int totalPages;
        if (accountRows.isEmpty() && !invalidBalanceRange) {
            do {
                WalletUserDirectoryClient.UserPage userPage = userDirectory.search(query, page, token);
                for (UserSummary user : userPage.content()) {
                    BilleteraUniversal wallet = walletsById.get(user.id());
                    if (wallet != null && cumpleFiltroSaldo(wallet, saldoDesde, saldoHasta)) {
                        accountRows.putIfAbsent(user.id(), toWalletRow(wallet, user));
                    }
                    if (accountRows.size() >= 5) {
                        break;
                    }
                }
                totalPages = userPage.totalPages();
                page++;
            } while (page < totalPages && accountRows.size() < 5);
        }
        model.addAttribute("identity", identity);
        model.addAttribute("cuentas", List.copyOf(accountRows.values()));
        model.addAttribute("buscar", buscar);
        model.addAttribute("saldoDesde", saldoDesde);
        model.addAttribute("saldoHasta", saldoHasta);
        model.addAttribute("totalCuentas", allWallets.size());
        model.addAttribute("resultadosCuentas", accountRows.size());
        model.addAttribute("errorFiltroSaldo", invalidBalanceRange);
        return "admin-moderador-billetera";
    }

    @GetMapping("/movimientos")
    public String movements(
            @RequestParam(defaultValue = "") String buscar,
            @RequestParam(defaultValue = "") String tipo,
            @RequestParam(defaultValue = "") String servicio,
            @RequestParam(required = false) LocalDate desde,
            @RequestParam(required = false) LocalDate hasta,
            @RequestParam(defaultValue = "0") int page,
            @AuthenticationPrincipal AuthenticatedIdentity identity,
            HttpServletRequest request,
            Model model) {
        List<BilleteraUniversal> wallets = billeteraService.billeterasAdministrativas();
        List<WalletMovementRow> allMovements = wallets.stream()
                .flatMap(wallet -> wallet.getMovimientos().stream()
                        .map(movement -> new WalletMovementRow(
                                wallet.getUsuarioId(), wallet.getUsuarioId(), "", movement)))
                .toList();

        boolean invalidDateRange = desde != null && hasta != null && desde.isAfter(hasta);
        if (invalidDateRange) {
            desde = null;
            hasta = null;
        }
        LocalDate fromDate = desde;
        LocalDate throughDate = hasta;
        String token = accessToken(request);
        String query = buscar == null ? "" : buscar.trim();
        Map<String, UserSummary> matchedUsers = query.isBlank()
                ? Map.of() : searchUsers(query, token);
        Set<String> matchedUserIds = matchedUsers.keySet();

        List<WalletMovementRow> filtered = allMovements.stream()
                .filter(row -> tipo.isBlank() || tipo.equalsIgnoreCase(row.movement().getTipo()))
                .filter(row -> servicio.isBlank()
                        || servicio.equalsIgnoreCase(row.movement().getServicio()))
                .filter(row -> inDateRange(row.movement().getFecha(), fromDate, throughDate))
                .filter(row -> query.isBlank()
                        || matchedUserIds.contains(row.userId())
                        || matchesMovement(row, query))
                .sorted(Comparator.comparing(
                        (WalletMovementRow row) -> row.movement().getFecha(),
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        int totalPages = (int) Math.ceil((double) filtered.size() / MOVEMENT_PAGE_SIZE);
        int pageIndex = Math.max(0, Math.min(page, Math.max(0, totalPages - 1)));
        int fromIndex = pageIndex * MOVEMENT_PAGE_SIZE;
        int toIndex = Math.min(fromIndex + MOVEMENT_PAGE_SIZE, filtered.size());
        List<WalletMovementRow> visible = filtered.subList(fromIndex, toIndex).stream()
                .map(row -> enrichMovement(row, matchedUsers, token))
                .toList();

        model.addAttribute("identity", identity);
        model.addAttribute("movimientos", visible);
        model.addAttribute("buscar", buscar);
        model.addAttribute("tipoSeleccionado", tipo);
        model.addAttribute("servicioSeleccionado", servicio);
        model.addAttribute("desde", desde);
        model.addAttribute("hasta", hasta);
        model.addAttribute("tipos", allMovements.stream()
                .map(row -> row.movement().getTipo())
                .filter(value -> value != null && !value.isBlank())
                .distinct().sorted().toList());
        model.addAttribute("servicios", allMovements.stream()
                .map(row -> row.movement().getServicio())
                .filter(value -> value != null && !value.isBlank())
                .distinct().sorted().toList());
        model.addAttribute("totalMovimientos", filtered.size());
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("pageIndex", pageIndex);
        model.addAttribute("hasPreviousPage", pageIndex > 0);
        model.addAttribute("hasNextPage", pageIndex + 1 < totalPages);
        model.addAttribute("firstMovement", filtered.isEmpty() ? 0 : fromIndex + 1);
        model.addAttribute("lastMovement", toIndex);
        model.addAttribute("errorFiltroFecha", invalidDateRange);
        return "admin-movimientos-billetera";
    }

    private Map<String, UserSummary> searchUsers(String query, String token) {
        Map<String, UserSummary> matches = new LinkedHashMap<>();
        int page = 0;
        WalletUserDirectoryClient.UserPage result;
        do {
            result = userDirectory.search(query, page, token);
            result.content().forEach(user -> matches.put(user.id(), user));
            page++;
        } while (page < result.totalPages());
        return matches;
    }

    private WalletMovementRow enrichMovement(
            WalletMovementRow row, Map<String, UserSummary> matchedUsers, String token) {
        UserSummary user = matchedUsers.get(row.userId());
        if (user == null) {
            user = userDirectory.findById(row.userId(), token);
        }
        String name = user == null || user.name() == null || user.name().isBlank()
                ? row.userId() : user.name();
        String email = user == null || user.email() == null ? "" : user.email();
        return new WalletMovementRow(row.userId(), name, email, row.movement());
    }

    private static boolean matchesMovement(WalletMovementRow row, String query) {
        String normalized = query.toLowerCase(Locale.ROOT);
        MovimientoBilletera movement = row.movement();
        return contains(row.userId(), normalized)
                || contains(movement.getOperacionId(), normalized)
                || contains(movement.getTipo(), normalized)
                || contains(movement.getDescripcion(), normalized)
                || contains(movement.getServicio(), normalized);
    }

    private static boolean inDateRange(
            java.time.LocalDateTime date, LocalDate from, LocalDate through) {
        if (from == null && through == null) {
            return true;
        }
        return date != null
                && (from == null || !date.toLocalDate().isBefore(from))
                && (through == null || !date.toLocalDate().isAfter(through));
    }

    private static List<String> movementTypes(List<BilleteraUniversal> wallets) {
        return wallets.stream()
                .flatMap(wallet -> wallet.getMovimientos().stream())
                .map(MovimientoBilletera::getTipo)
                .filter(value -> value != null && !value.isBlank())
                .distinct().sorted().toList();
    }

    private static List<String> movementServices(List<BilleteraUniversal> wallets) {
        return wallets.stream()
                .flatMap(wallet -> wallet.getMovimientos().stream())
                .map(MovimientoBilletera::getServicio)
                .filter(value -> value != null && !value.isBlank())
                .distinct().sorted().toList();
    }

    @GetMapping("/cuentas/{usuarioId}")
    public String selectedAccount(
            @PathVariable String usuarioId,
            @AuthenticationPrincipal AuthenticatedIdentity identity,
            HttpServletRequest request,
            Model model) {
        BilleteraUniversal wallet = billeteraService.billeteraAdministrativa(usuarioId);
        UserSummary user = userDirectory.findById(usuarioId, accessToken(request));
        model.addAttribute("identity", identity);
        model.addAttribute("usuarioSeleccionado", user);
        model.addAttribute("tarjetas", List.of(wallet));
        return "admin-billetera";
    }

    @PostMapping("/cuentas/{usuarioId}/ajustar")
    public String adjustBalance(
            @PathVariable String usuarioId,
            @AuthenticationPrincipal AuthenticatedIdentity identity,
            @RequestParam String accion,
            @RequestParam BigDecimal monto,
            @RequestParam String motivo,
            RedirectAttributes redirectAttributes) {
        try {
            boolean add = switch (accion) {
                case "ABONAR" -> true;
                case "DESCONTAR" -> false;
                default -> throw new IllegalArgumentException("Selecciona abonar o descontar.");
            };
            BilleteraUniversal updated = billeteraService.ajustarSaldoAdministrativamente(
                    usuarioId, identity.email(), monto, add, motivo, UUID.randomUUID().toString());
            redirectAttributes.addFlashAttribute("mensaje",
                    "Ajuste aplicado. Nuevo saldo: $ " + updated.getSaldo().toPlainString() + " COP.");
        } catch (IllegalArgumentException | BilleteraService.WalletNotFoundException
                | SaldoInsuficienteException exception) {
            redirectAttributes.addFlashAttribute("error", exception.getMessage());
        }
        return "redirect:/admin/cuentas/" + usuarioId;
    }

    @PostMapping("/cuentas/{usuarioId}/estado")
    public String updateCardStatus(
            @PathVariable String usuarioId,
            @AuthenticationPrincipal AuthenticatedIdentity identity,
            @RequestParam EstadoTarjeta estado,
            @RequestParam String motivo,
            RedirectAttributes redirectAttributes) {
        try {
            billeteraService.cambiarEstadoAdministrativamente(
                    usuarioId, estado, identity.email(), motivo);
            redirectAttributes.addFlashAttribute("mensaje",
                    estado == EstadoTarjeta.CONGELADA
                            ? "Tarjeta congelada por soporte."
                            : "Tarjeta desbloqueada por soporte.");
        } catch (IllegalArgumentException | BilleteraService.WalletNotFoundException exception) {
            redirectAttributes.addFlashAttribute("error", exception.getMessage());
        }
        return "redirect:/admin/cuentas/" + usuarioId;
    }

    @GetMapping("/tarjetas-desactivadas")
    public String archivedCards(Model model) {
        model.addAttribute("tarjetas", billeteraService.tarjetasDesactivadasAdmin());
        return "admin-tarjetas-desactivadas";
    }

    private static boolean contains(String value, String query) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(query);
    }

    private static String accessToken(HttpServletRequest request) {
        Object token = request.getAttribute(
                com.Servicio.Billetera.security.RemoteAccessAuthenticationFilter.TOKEN_ATTRIBUTE);
        if (!(token instanceof String accessToken) || accessToken.isBlank()) {
            throw new IllegalStateException("No se encontró la sesión para consultar el directorio de usuarios.");
        }
        return accessToken;
    }

    private static boolean cumpleFiltroSaldo(
            BilleteraUniversal wallet, BigDecimal saldoDesde, BigDecimal saldoHasta) {
        return (saldoDesde == null || wallet.getSaldo().compareTo(saldoDesde) >= 0)
                && (saldoHasta == null || wallet.getSaldo().compareTo(saldoHasta) <= 0);
    }

    private static WalletRow toWalletRow(BilleteraUniversal wallet, UserSummary user) {
        return new WalletRow(wallet.getUsuarioId(),
                user == null || user.name() == null ? wallet.getUsuarioId() : user.name(),
                user == null || user.email() == null ? "" : user.email(),
                wallet.getNumeroTarjeta(),
                wallet.getSaldo(),
                estadoTarjeta(wallet));
    }

    private static String estadoTarjeta(BilleteraUniversal wallet) {
        if (!wallet.isActivadaPorUsuario()) {
            return "Pendiente";
        }
        return switch (wallet.getEstado()) {
            case ACTIVA -> "Activa";
            case CONGELADA -> "Congelada";
            case DESACTIVADA -> "Desactivada";
        };
    }

    public record WalletRow(
            String usuarioId,
            String nombre,
            String correo,
            String numeroTarjeta,
            BigDecimal saldo,
            String estado) {
    }

    public record WalletMovementRow(
            String userId,
            String userName,
            String userEmail,
            MovimientoBilletera movement) {
    }
}

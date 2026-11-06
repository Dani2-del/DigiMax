package com.Servicio.Billetera.web;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.Comparator;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.ResourceAccessException;
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

    private static final Logger LOGGER = LoggerFactory.getLogger(AdminTarjetasController.class);

    private final BilleteraService billeteraService;
    private final WalletUserDirectoryClient userDirectory;
    private final BilleteraExcelReportService excelReportService;
    @Value("${app.acceso.base-url}")
    private String accesoUrl;
    @Value("${app.lector.base-url:http://localhost:8086}")
    private String lectorUrl;

    private static final int MOVEMENT_PAGE_SIZE = 5;
    private static final int ACCOUNT_PAGE_SIZE = 10;
    private static final int ARCHIVED_CARD_PAGE_SIZE = 10;

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
        BilleteraService.DashboardSummary summary = billeteraService.dashboardSummary();
        model.addAttribute("identity", identity);
        model.addAttribute("totalCuentas", summary.totalCuentas());
        model.addAttribute("tarjetasActivas", summary.tarjetasActivas());
        model.addAttribute("tarjetasCongeladas", summary.tarjetasCongeladas());
        model.addAttribute("saldoAdministrado", summary.saldoAdministrado());
        model.addAttribute("lectorUrl", lectorUrl);
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
        String query = buscar.trim();
        int requestedPage;
        try {
            requestedPage = Integer.parseInt(request.getParameter("page"));
        } catch (NumberFormatException exception) {
            requestedPage = 0;
        }
        requestedPage = Math.max(0, requestedPage);
        boolean invalidBalanceRange = (saldoDesde != null && saldoDesde.signum() < 0)
                || (saldoHasta != null && saldoHasta.signum() < 0)
                || (saldoDesde != null && saldoHasta != null && saldoDesde.compareTo(saldoHasta) > 0);
        Map<String, UserSummary> usersById = new LinkedHashMap<>();
        List<String> eligibleWalletIds = invalidBalanceRange ? List.of()
                : billeteraService.idsCuentasAdministrativas(saldoDesde, saldoHasta);
        int pageIndex = Math.min(requestedPage,
                Math.max(0, (eligibleWalletIds.size() - 1) / ACCOUNT_PAGE_SIZE));
        AccountPage accountPage = query.isBlank()
                ? accountsForVisibleWalletUsers(
                        eligibleWalletIds, usersById, request, saldoDesde, saldoHasta, pageIndex)
                : searchVisibleWalletUsers(
                        query, eligibleWalletIds, usersById, request, saldoDesde, saldoHasta, pageIndex);
        List<WalletRow> accountRows = accountPage.accounts().stream()
                .map(wallet -> toWalletRow(wallet, usersById.get(wallet.getUsuarioId())))
                .toList();
        model.addAttribute("identity", identity);
        model.addAttribute("cuentas", accountRows);
        model.addAttribute("buscar", buscar);
        model.addAttribute("saldoDesde", saldoDesde);
        model.addAttribute("saldoHasta", saldoHasta);
        model.addAttribute("totalCuentas", billeteraService.totalCuentasAdministrativas());
        model.addAttribute("resultadosCuentas", accountRows.size());
        model.addAttribute("pageIndex", pageIndex);
        model.addAttribute("hasPreviousPage", pageIndex > 0);
        model.addAttribute("hasNextPage", accountPage.hasNext());
        model.addAttribute("errorFiltroSaldo", invalidBalanceRange);
        return "admin-moderador-billetera";
    }

    private record AccountPage(List<BilleteraUniversal> accounts, boolean hasNext) {}

    private AccountPage accountsForVisibleWalletUsers(
            List<String> walletIds,
            Map<String, UserSummary> usersById,
            HttpServletRequest request,
            BigDecimal saldoDesde,
            BigDecimal saldoHasta,
            int pageIndex) {
        List<String> orderedWalletIds = walletIds.stream().distinct()
                .sorted(Comparator.reverseOrder()).toList();
        List<String> visibleWalletIds = new java.util.ArrayList<>();
        String token = orderedWalletIds.isEmpty() ? null : accessToken(request);
        long requiredCount = ((long) pageIndex + 1) * ACCOUNT_PAGE_SIZE + 1;
        for (int start = 0; start < orderedWalletIds.size()
                && visibleWalletIds.size() < requiredCount; start += 200) {
            List<String> batch = orderedWalletIds.subList(
                    start, Math.min(start + 200, orderedWalletIds.size()));
            Map<String, UserSummary> batchUsers = loadVisibleWalletUsers(batch, token);
            batchUsers.forEach(usersById::putIfAbsent);
            for (String userId : batch) {
                if (batchUsers.containsKey(userId) && visibleWalletIds.size() < requiredCount) {
                    visibleWalletIds.add(userId);
                }
            }
        }
        return pageOfWalletIds(visibleWalletIds, pageIndex, saldoDesde, saldoHasta);
    }

    private AccountPage searchVisibleWalletUsers(
            String query,
            List<String> walletIds,
            Map<String, UserSummary> usersById,
            HttpServletRequest request,
            BigDecimal saldoDesde,
            BigDecimal saldoHasta,
            int pageIndex) {
        String token = accessToken(request);
        Set<String> eligibleWalletIds = Set.copyOf(walletIds);
        int requiredCount = (int) Math.min(Integer.MAX_VALUE,
                ((long) pageIndex + 1) * ACCOUNT_PAGE_SIZE + 1);
        List<BilleteraUniversal> directMatches = billeteraService.buscarCuentasAdministrativas(
                query, List.of(), saldoDesde, saldoHasta, Math.max(200, requiredCount));
        if (!directMatches.isEmpty()) {
            loadVisibleWalletUsers(
                    directMatches.stream().map(BilleteraUniversal::getUsuarioId).distinct().toList(), token)
                    .forEach(usersById::putIfAbsent);
        }
        List<BilleteraUniversal> visibleAccounts = directMatches.stream()
                .filter(wallet -> usersById.containsKey(wallet.getUsuarioId()))
                .toList();
        Set<String> selectedIds = visibleAccounts.stream()
                .map(BilleteraUniversal::getUsuarioId)
                .collect(Collectors.toSet());
        List<UserSummary> matchingUsers = new java.util.ArrayList<>();
        int searchPage = 0;
        WalletUserDirectoryClient.UserPage userPage;
        do {
            userPage = userDirectory.search(query, searchPage, token);
            for (UserSummary user : userPage.content()) {
                if (eligibleWalletIds.contains(user.id())
                        && isVisibleWalletUser(user)
                        && selectedIds.add(user.id())) {
                    matchingUsers.add(user);
                    if (visibleAccounts.size() + matchingUsers.size() >= requiredCount) {
                        break;
                    }
                }
            }
            searchPage++;
        } while (searchPage < userPage.totalPages()
                && visibleAccounts.size() + matchingUsers.size() < requiredCount);
        matchingUsers.forEach(user -> usersById.put(user.id(), user));
        Map<String, BilleteraUniversal> walletsById = matchingUsers.isEmpty() ? Map.of()
                : billeteraService.buscarCuentasAdministrativas(
                                "", matchingUsers.stream().map(UserSummary::id).toList(),
                                saldoDesde, saldoHasta, matchingUsers.size()).stream()
                        .collect(Collectors.toMap(BilleteraUniversal::getUsuarioId, Function.identity()));
        List<BilleteraUniversal> orderedMatches = matchingUsers.stream()
                .map(user -> walletsById.get(user.id()))
                .filter(java.util.Objects::nonNull)
                .toList();
        List<BilleteraUniversal> results = java.util.stream.Stream
                .concat(visibleAccounts.stream(), orderedMatches.stream()).toList();
        return pageOfAccounts(results, pageIndex);
    }

    private AccountPage pageOfWalletIds(
            List<String> walletIds, int page, BigDecimal saldoDesde, BigDecimal saldoHasta) {
        long offset = (long) page * ACCOUNT_PAGE_SIZE;
        if (offset >= walletIds.size()) {
            return new AccountPage(List.of(), false);
        }
        int start = (int) offset;
        int end = Math.min(start + ACCOUNT_PAGE_SIZE, walletIds.size());
        List<BilleteraUniversal> accounts = billeteraService.buscarCuentasAdministrativas(
                "", walletIds.subList(start, end), saldoDesde, saldoHasta, ACCOUNT_PAGE_SIZE);
        return new AccountPage(accounts, walletIds.size() > end);
    }

    private AccountPage pageOfAccounts(List<BilleteraUniversal> accounts, int page) {
        long offset = (long) page * ACCOUNT_PAGE_SIZE;
        if (offset >= accounts.size()) {
            return new AccountPage(List.of(), false);
        }
        int start = (int) offset;
        int end = Math.min(start + ACCOUNT_PAGE_SIZE, accounts.size());
        return new AccountPage(accounts.subList(start, end), accounts.size() > end);
    }

    private Map<String, UserSummary> loadVisibleWalletUsers(List<String> userIds, String token) {
        Map<String, UserSummary> users = new LinkedHashMap<>();
        for (int start = 0; start < userIds.size(); start += 200) {
            List<String> batch = userIds.subList(start, Math.min(start + 200, userIds.size()));
            userDirectory.findByIds(batch, token).stream()
                    .filter(AdminTarjetasController::isVisibleWalletUser)
                    .forEach(user -> users.put(user.id(), user));
        }
        return users;
    }

    private static boolean isVisibleWalletUser(UserSummary user) {
        return user != null && ("ROLE_USER".equals(user.role())
                || ("ROLE_MODERATOR".equals(user.role())
                        && "BILLETERA".equals(user.serviceAssignment())));
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
        BilleteraService.MovementPage movementPage = billeteraService.movimientosAdministrativos(
                tipo, servicio, fromDate, throughDate, query, List.copyOf(matchedUserIds),
                page, MOVEMENT_PAGE_SIZE);
        long totalMovimientos = movementPage.total();
        int totalPages = (int) Math.ceil((double) totalMovimientos / MOVEMENT_PAGE_SIZE);
        int pageIndex = movementPage.pageIndex();
        int firstMovement = totalMovimientos == 0 ? 0 : pageIndex * MOVEMENT_PAGE_SIZE + 1;
        List<WalletMovementRow> rows = movementPage.movements().stream()
                .map(row -> new WalletMovementRow(
                        row.userId(), row.userId(), "", row.movement()))
                .toList();
        Map<String, UserSummary> pageUsers = new LinkedHashMap<>(matchedUsers);
        List<String> usersToLoad = rows.stream()
                .map(WalletMovementRow::userId)
                .filter(userId -> !pageUsers.containsKey(userId))
                .distinct()
                .toList();
        if (!usersToLoad.isEmpty()) {
            userDirectory.findByIds(usersToLoad, token)
                    .stream()
                    .filter(AdminTarjetasController::isVisibleWalletUser)
                    .forEach(user -> pageUsers.put(user.id(), user));
        }
        List<WalletMovementRow> visible = rows.stream()
                .map(row -> enrichMovement(row, pageUsers))
                .toList();
        int lastMovement = firstMovement == 0 ? 0 : firstMovement + visible.size() - 1;

        model.addAttribute("identity", identity);
        model.addAttribute("movimientos", visible);
        model.addAttribute("buscar", buscar);
        model.addAttribute("tipoSeleccionado", tipo);
        model.addAttribute("servicioSeleccionado", servicio);
        model.addAttribute("desde", desde);
        model.addAttribute("hasta", hasta);
        model.addAttribute("tipos", billeteraService.tiposMovimientoAdministrativos());
        model.addAttribute("servicios", billeteraService.serviciosMovimientoAdministrativos());
        model.addAttribute("totalMovimientos", totalMovimientos);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("pageIndex", pageIndex);
        model.addAttribute("hasPreviousPage", pageIndex > 0);
        model.addAttribute("hasNextPage", pageIndex + 1 < totalPages);
        model.addAttribute("firstMovement", firstMovement);
        model.addAttribute("lastMovement", lastMovement);
        model.addAttribute("errorFiltroFecha", invalidDateRange);
        return "admin-movimientos-billetera";
    }

    private Map<String, UserSummary> searchUsers(String query, String token) {
        Map<String, UserSummary> matches = new LinkedHashMap<>();
        int page = 0;
        WalletUserDirectoryClient.UserPage result;
        do {
            result = userDirectory.search(query, page, token);
            result.content().stream()
                    .filter(AdminTarjetasController::isVisibleWalletUser)
                    .forEach(user -> matches.put(user.id(), user));
            page++;
        } while (page < result.totalPages());
        return matches;
    }

    private static WalletMovementRow enrichMovement(
            WalletMovementRow row, Map<String, UserSummary> matchedUsers) {
        UserSummary user = matchedUsers.get(row.userId());
        String name = user == null || user.name() == null || user.name().isBlank()
                ? row.userId() : user.name();
        String email = user == null || user.email() == null ? "" : user.email();
        return new WalletMovementRow(row.userId(), name, email, row.movement());
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
        UserSummary user = visibleWalletUser(usuarioId, request);
        BilleteraUniversal wallet = billeteraService.billeteraAdministrativa(usuarioId);
        model.addAttribute("identity", identity);
        model.addAttribute("usuarioSeleccionado", user);
        boolean esPropiaCuenta = identity != null && identity.id() != null
                && identity.id().equals(user.id());
        model.addAttribute("esPropiaCuenta", esPropiaCuenta);
        model.addAttribute("puedeCambiarRol", !esPropiaCuenta && identity != null
                && ("ROLE_ADMIN".equals(identity.role()) || "ROLE_MODERATOR".equals(identity.role())));
        model.addAttribute("tarjetas", List.of(wallet));
        return "admin-billetera";
    }

    @PostMapping("/cuentas/{usuarioId}/rol")
    public String updateUserRole(
            @PathVariable String usuarioId,
            HttpServletRequest request,
            @RequestParam String rol,
            @RequestParam(name = "confirmar", required = false) boolean confirmar,
            RedirectAttributes redirectAttributes) {
        try {
            if (!confirmar) {
                throw new IllegalArgumentException("Confirma el cambio de rol para continuar.");
            }
            if (!"ROLE_USER".equals(rol) && !"ROLE_MODERATOR".equals(rol)) {
                throw new IllegalArgumentException("El rol seleccionado no es válido para Billetera.");
            }
            UserSummary user = visibleWalletUser(usuarioId, request);
            String service = "ROLE_MODERATOR".equals(rol) ? "BILLETERA" : null;
            userDirectory.updateRole(user, rol, service, accessToken(request));
            redirectAttributes.addFlashAttribute("mensaje", "Rol actualizado correctamente.");
        } catch (IllegalArgumentException exception) {
            redirectAttributes.addFlashAttribute("error", exception.getMessage());
        } catch (RestClientResponseException exception) {
            String serverMessage = exception.getResponseBodyAsString();
            java.util.regex.Matcher message = java.util.regex.Pattern
                    .compile("\\\"message\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"")
                    .matcher(serverMessage);
            redirectAttributes.addFlashAttribute("error",
                    message.find() ? message.group(1)
                            : "Acceso rechazó el cambio de rol (HTTP " + exception.getStatusCode().value() + ").");
        } catch (ResourceAccessException exception) {
            LOGGER.error("Acceso no respondió al actualizar el rol de la cuenta {}.", usuarioId, exception);
            redirectAttributes.addFlashAttribute("error",
                    "Acceso no respondió a tiempo. Intenta guardar el cambio nuevamente.");
        } catch (RestClientException exception) {
            LOGGER.error("Billetera no pudo procesar la respuesta de Acceso al actualizar la cuenta {}.",
                    usuarioId, exception);
            redirectAttributes.addFlashAttribute("error",
                    "Acceso respondió, pero Billetera no pudo procesar el cambio de rol.");
        }
        return "redirect:/admin/cuentas/" + usuarioId;
    }

    @PostMapping("/cuentas/{usuarioId}/ajustar")
    public String adjustBalance(
            @PathVariable String usuarioId,
            @AuthenticationPrincipal AuthenticatedIdentity identity,
            HttpServletRequest request,
            @RequestParam String accion,
            @RequestParam BigDecimal monto,
            @RequestParam String motivo,
            RedirectAttributes redirectAttributes) {
        visibleWalletUser(usuarioId, request);
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
            HttpServletRequest request,
            @RequestParam EstadoTarjeta estado,
            @RequestParam String motivo,
            RedirectAttributes redirectAttributes) {
        visibleWalletUser(usuarioId, request);
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
    public String archivedCards(
            @RequestParam(defaultValue = "0") int page,
            Model model) {
        BilleteraService.ArchivedCardPage archivedPage = billeteraService.tarjetasDesactivadasAdmin(
                page, ARCHIVED_CARD_PAGE_SIZE);
        int firstCard = archivedPage.cards().isEmpty()
                ? 0 : archivedPage.pageIndex() * ARCHIVED_CARD_PAGE_SIZE + 1;
        int lastCard = firstCard == 0 ? 0 : firstCard + archivedPage.cards().size() - 1;
        model.addAttribute("tarjetas", archivedPage.cards());
        model.addAttribute("pageIndex", archivedPage.pageIndex());
        model.addAttribute("hasPreviousPage", archivedPage.hasPrevious());
        model.addAttribute("hasNextPage", archivedPage.hasNext());
        model.addAttribute("firstCard", firstCard);
        model.addAttribute("lastCard", lastCard);
        return "admin-tarjetas-desactivadas";
    }

    private static String accessToken(HttpServletRequest request) {
        Object token = request.getAttribute(
                com.Servicio.Billetera.security.RemoteAccessAuthenticationFilter.TOKEN_ATTRIBUTE);
        if (!(token instanceof String accessToken) || accessToken.isBlank()) {
            throw new IllegalStateException("No se encontró la sesión para consultar el directorio de usuarios.");
        }
        return accessToken;
    }

    private UserSummary visibleWalletUser(String userId, HttpServletRequest request) {
        UserSummary user = userDirectory.findById(userId, accessToken(request));
        if (!isVisibleWalletUser(user)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return user;
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

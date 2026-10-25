package com.Servicio.Billetera.web;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.Servicio.Billetera.model.BilleteraUniversal;
import com.Servicio.Billetera.model.EstadoTarjeta;
import com.Servicio.Billetera.security.AuthenticatedIdentity;
import com.Servicio.Billetera.service.BilleteraService;
import com.Servicio.Billetera.service.SaldoInsuficienteException;
import com.Servicio.Billetera.service.WalletUserDirectoryClient;
import com.Servicio.Billetera.service.WalletUserDirectoryClient.UserSummary;

@Controller
@RequestMapping("/admin")
public class AdminTarjetasController {

    private final BilleteraService billeteraService;
    private final WalletUserDirectoryClient userDirectory;

    public AdminTarjetasController(
            BilleteraService billeteraService, WalletUserDirectoryClient userDirectory) {
        this.billeteraService = billeteraService;
        this.userDirectory = userDirectory;
    }

    @GetMapping("/dashboard")
    public String dashboard(@AuthenticationPrincipal AuthenticatedIdentity identity, Model model) {
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

    @GetMapping
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
}

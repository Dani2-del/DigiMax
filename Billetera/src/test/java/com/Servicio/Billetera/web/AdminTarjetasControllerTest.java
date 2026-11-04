package com.Servicio.Billetera.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.server.ResponseStatusException;

import com.Servicio.Billetera.model.BilleteraUniversal;
import com.Servicio.Billetera.model.MovimientoBilletera;
import com.Servicio.Billetera.security.AuthenticatedIdentity;
import com.Servicio.Billetera.security.RemoteAccessAuthenticationFilter;
import com.Servicio.Billetera.service.BilleteraExcelReportService;
import com.Servicio.Billetera.service.BilleteraExcelReportService.MovementReportRow;
import com.Servicio.Billetera.service.BilleteraService;
import com.Servicio.Billetera.service.WalletUserDirectoryClient;
import com.Servicio.Billetera.service.WalletUserDirectoryClient.UserPage;
import com.Servicio.Billetera.service.WalletUserDirectoryClient.UserSummary;
import com.Servicio.Billetera.web.AdminTarjetasController.WalletMovementRow;

@ExtendWith(MockitoExtension.class)
class AdminTarjetasControllerTest {

    @Mock
    private BilleteraService billeteraService;

    @Mock
    private WalletUserDirectoryClient userDirectory;

    @Mock
    private BilleteraExcelReportService excelReportService;

    @InjectMocks
    private AdminTarjetasController controller;

    @Test
    void dashboardUsesAggregatedWalletSummaryInsteadOfLoadingEveryWallet() {
        when(billeteraService.dashboardSummary()).thenReturn(
                new BilleteraService.DashboardSummary(12, 8, 2, new BigDecimal("12500.00")));
        ExtendedModelMap model = new ExtendedModelMap();

        assertThat(controller.dashboard(
                new AuthenticatedIdentity("admin", "admin@example.com", "Admin", "ROLE_ADMIN"), model))
                .isEqualTo("admin-dashboard-billetera");

        assertThat(model.getAttribute("totalCuentas")).isEqualTo(12L);
        assertThat(model.getAttribute("tarjetasActivas")).isEqualTo(8L);
        assertThat(model.getAttribute("tarjetasCongeladas")).isEqualTo(2L);
        assertThat(model.getAttribute("saldoAdministrado")).isEqualTo(new BigDecimal("12500.00"));
        verify(billeteraService, never()).billeterasAdministrativas();
    }

    @Test
    void adminRootOpensTheDashboardInsteadOfTheAccountSearch() {
        assertThat(controller.adminHome()).isEqualTo("redirect:/admin/dashboard");
    }

    @Test
    void accountSearchLoadsOnlyFiveWalletsAndSkipsDirectorySearchWithoutAQuery() {
        BilleteraUniversal wallet = walletWithMovements(0);
        when(billeteraService.idsCuentasAdministrativas(null, null)).thenReturn(List.of("user-0"));
        when(userDirectory.findByIds(List.of("user-0"), "access-token"))
                .thenReturn(List.of(new UserSummary("user-0", "User Zero", "zero@example.com")));
        when(billeteraService.buscarCuentasAdministrativas("", List.of("user-0"), null, null, 5))
                .thenReturn(List.of(wallet));
        when(billeteraService.totalCuentasAdministrativas()).thenReturn(27L);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(RemoteAccessAuthenticationFilter.TOKEN_ATTRIBUTE, "access-token");
        ExtendedModelMap model = new ExtendedModelMap();

        assertThat(controller.index(
                "", null, null,
                new AuthenticatedIdentity("admin", "admin@example.com", "Admin", "ROLE_ADMIN"),
                request, model)).isEqualTo("admin-moderador-billetera");

        @SuppressWarnings("unchecked")
        List<AdminTarjetasController.WalletRow> accounts =
                (List<AdminTarjetasController.WalletRow>) model.getAttribute("cuentas");
        assertThat(accounts).hasSize(1);
        assertThat(accounts.get(0).nombre()).isEqualTo("User Zero");
        assertThat(model.getAttribute("totalCuentas")).isEqualTo(27L);
        verify(userDirectory, never()).search(
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyString());
        verify(billeteraService, never()).billeterasAdministrativas();
    }

    @Test
    void accountSearchMatchesWalletsByAccessUserNameAndPreservesDirectoryOrder() {
        UserSummary firstMatch = new UserSummary("user-1", "User One", "one@example.com");
        UserSummary secondMatch = new UserSummary("user-0", "User Zero", "zero@example.com");
        when(billeteraService.buscarCuentasAdministrativas("User", List.of(), null, null, 200))
                .thenReturn(List.of());
        when(billeteraService.idsCuentasAdministrativas(null, null))
                .thenReturn(List.of("user-0", "user-1"));
        when(userDirectory.search("User", 0, "access-token"))
                .thenReturn(new UserPage(List.of(firstMatch, secondMatch), 2, 1, 0, 100));
        when(billeteraService.buscarCuentasAdministrativas(
                "", List.of("user-1", "user-0"), null, null, 2))
                .thenReturn(List.of(walletWithMovements(0), walletWithMovements(1)));
        when(billeteraService.totalCuentasAdministrativas()).thenReturn(2L);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(RemoteAccessAuthenticationFilter.TOKEN_ATTRIBUTE, "access-token");
        ExtendedModelMap model = new ExtendedModelMap();

        controller.index(
                "User", null, null,
                new AuthenticatedIdentity("admin", "admin@example.com", "Admin", "ROLE_ADMIN"),
                request, model);

        @SuppressWarnings("unchecked")
        List<AdminTarjetasController.WalletRow> accounts =
                (List<AdminTarjetasController.WalletRow>) model.getAttribute("cuentas");
        assertThat(accounts).extracting(AdminTarjetasController.WalletRow::usuarioId)
                .containsExactly("user-1", "user-0");
        assertThat(accounts).extracting(AdminTarjetasController.WalletRow::nombre)
                .containsExactly("User One", "User Zero");
    }

    @Test
    void accountListExcludesAdministratorsAndModeratorsFromOtherServices() {
        List<String> walletIds = List.of("admin-1", "other-moderator", "user-1", "user-2");
        when(billeteraService.idsCuentasAdministrativas(null, null)).thenReturn(walletIds);
        when(userDirectory.findByIds(walletIds, "access-token")).thenReturn(List.of(
                new UserSummary("admin-1", "Admin", "admin@example.com", "ROLE_ADMIN", null),
                new UserSummary("other-moderator", "Other Moderator", "other@example.com",
                        "ROLE_MODERATOR", "TRANSCARIBE"),
                new UserSummary("user-1", "User", "user@example.com"),
                new UserSummary("user-2", "Wallet Moderator", "wallet@example.com",
                        "ROLE_MODERATOR", "BILLETERA")));
        when(billeteraService.buscarCuentasAdministrativas(
                "", List.of("user-1", "user-2"), null, null, 5))
                .thenReturn(List.of(walletWithMovements(1), walletWithMovements(2)));
        when(billeteraService.totalCuentasAdministrativas()).thenReturn(4L);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(RemoteAccessAuthenticationFilter.TOKEN_ATTRIBUTE, "access-token");
        ExtendedModelMap model = new ExtendedModelMap();

        controller.index(
                "", null, null,
                new AuthenticatedIdentity("admin", "admin@example.com", "Admin", "ROLE_ADMIN"),
                request, model);

        @SuppressWarnings("unchecked")
        List<AdminTarjetasController.WalletRow> accounts =
                (List<AdminTarjetasController.WalletRow>) model.getAttribute("cuentas");
        assertThat(accounts).extracting(AdminTarjetasController.WalletRow::usuarioId)
                .containsExactly("user-1", "user-2");
        verify(userDirectory, never()).search(
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void directAccountAccessRejectsAnAdministratorIdentity() {
        when(userDirectory.findById("admin-1", "access-token"))
                .thenReturn(new UserSummary("admin-1", "Admin", "admin@example.com", "ROLE_ADMIN", null));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(RemoteAccessAuthenticationFilter.TOKEN_ATTRIBUTE, "access-token");

        assertThatThrownBy(() -> controller.selectedAccount(
                "admin-1",
                new AuthenticatedIdentity("admin", "admin@example.com", "Admin", "ROLE_ADMIN"),
                request, new ExtendedModelMap()))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(exception -> ((ResponseStatusException) exception).getStatusCode().value())
                .isEqualTo(404);
        verify(billeteraService, never()).billeteraAdministrativa("admin-1");
    }

    @Test
    void reportsPageProvidesAvailableMovementFilters() {
        when(billeteraService.billeterasAdministrativas())
                .thenReturn(List.of(walletWithMovements(0)));
        ExtendedModelMap model = new ExtendedModelMap();

        assertThat(controller.reports(model)).isEqualTo("admin-reportes-billetera");
        assertThat(model.getAttribute("tipos")).isEqualTo(List.of("ABONO"));
        assertThat(model.getAttribute("servicios")).isEqualTo(List.of("Billetera"));
    }

    @Test
    void excelReportReturnsWorkbookWithSelectedFilters() throws Exception {
        when(billeteraService.billeterasAdministrativas())
                .thenReturn(List.of(walletWithMovements(0)));
        when(excelReportService.createReport(
                org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(new byte[] { 1, 2, 3 });

        var response = controller.downloadExcelReport(
                List.of("usuario", "operacion"), "ABONO", "Billetera",
                LocalDate.of(2026, 10, 25), LocalDate.of(2026, 10, 25));

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getHeaders().getContentType())
                .isEqualTo(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                .contains("attachment")
                .contains("Reporte_Billetera_");
        assertThat(response.getBody()).containsExactly(1, 2, 3);
        org.mockito.ArgumentCaptor<List<MovementReportRow>> rows =
                org.mockito.ArgumentCaptor.forClass(List.class);
        org.mockito.ArgumentCaptor<List<BilleteraExcelReportService.ReportColumn>> columns =
                org.mockito.ArgumentCaptor.forClass(List.class);
        org.mockito.Mockito.verify(excelReportService).createReport(rows.capture(), columns.capture());
        assertThat(rows.getValue()).hasSize(4);
        assertThat(columns.getValue()).extracting(BilleteraExcelReportService.ReportColumn::name)
                .containsExactly("USER", "OPERATION");
    }

    @Test
    void excelReportRejectsNoColumnsAndInvalidDateRange() {
        assertThatThrownBy(() -> controller.downloadExcelReport(
                List.of(), "", "", null, null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400");
        assertThatThrownBy(() -> controller.downloadExcelReport(
                List.of("monto"), "", "",
                LocalDate.of(2026, 10, 26), LocalDate.of(2026, 10, 25)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400");
    }

    @Test
    void movementsPagePaginatesFiveMostRecentEntries() {
        List<BilleteraService.MovementDocument> movementDocuments = IntStream.range(5, 8)
                .mapToObj(index -> movementDocument(1, index - 4))
                .toList();
        when(billeteraService.movimientosAdministrativos(
                "", "", null, null, "", List.of(), 1, 5))
                .thenReturn(new BilleteraService.MovementPage(8, 1, movementDocuments));
        when(billeteraService.tiposMovimientoAdministrativos()).thenReturn(List.of("ABONO"));
        when(billeteraService.serviciosMovimientoAdministrativos()).thenReturn(List.of("Billetera"));
        when(userDirectory.findByIds(List.of("user-1"), "access-token"))
                .thenReturn(List.of(new UserSummary("user-1", "User user-1", "user@example.com")));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(RemoteAccessAuthenticationFilter.TOKEN_ATTRIBUTE, "access-token");
        ExtendedModelMap model = new ExtendedModelMap();

        assertThat(controller.movements(
                "", "", "", null, null, 1,
                new AuthenticatedIdentity("admin", "admin@example.com", "Admin", "ROLE_ADMIN"),
                request, model)).isEqualTo("admin-movimientos-billetera");

        @SuppressWarnings("unchecked")
        List<WalletMovementRow> page = (List<WalletMovementRow>) model.getAttribute("movimientos");
        assertThat(page).hasSize(3);
        assertThat(page).extracting(entry -> entry.movement().getOperacionId())
                .containsExactly("operation-5", "operation-6", "operation-7");
        assertThat(model.getAttribute("totalMovimientos")).isEqualTo(8L);
        assertThat(model.getAttribute("totalPages")).isEqualTo(2);
        assertThat(model.getAttribute("firstMovement")).isEqualTo(6);
        assertThat(model.getAttribute("lastMovement")).isEqualTo(8);
        assertThat(model.getAttribute("hasPreviousPage")).isEqualTo(true);
        assertThat(model.getAttribute("hasNextPage")).isEqualTo(false);
        verify(billeteraService, never()).billeterasAdministrativas();
    }

    @Test
    void movementsPageAppliesSearchTypeServiceAndDateFilters() {
        when(userDirectory.search("Carga 0", 0, "access-token"))
                .thenReturn(new UserPage(List.of(), 0, 0, 0, 100));
        when(billeteraService.movimientosAdministrativos(
                "ABONO", "Billetera", LocalDate.of(2026, 10, 25), LocalDate.of(2026, 10, 25),
                "Carga 0", List.of(), 0, 5))
                .thenReturn(new BilleteraService.MovementPage(
                        1, 0, List.of(movementDocument(0, 0))));
        when(billeteraService.tiposMovimientoAdministrativos()).thenReturn(List.of("ABONO"));
        when(billeteraService.serviciosMovimientoAdministrativos()).thenReturn(List.of("Billetera"));
        when(userDirectory.findByIds(List.of("user-0"), "access-token"))
                .thenReturn(List.of(new UserSummary("user-0", "User Zero", "zero@example.com")));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(RemoteAccessAuthenticationFilter.TOKEN_ATTRIBUTE, "access-token");
        ExtendedModelMap model = new ExtendedModelMap();

        controller.movements(
                "Carga 0", "ABONO", "Billetera",
                LocalDate.of(2026, 10, 25), LocalDate.of(2026, 10, 25), 0,
                new AuthenticatedIdentity("admin", "admin@example.com", "Admin", "ROLE_ADMIN"),
                request, model);

        @SuppressWarnings("unchecked")
        List<WalletMovementRow> results = (List<WalletMovementRow>) model.getAttribute("movimientos");
        assertThat(results).hasSize(1);
        assertThat(results.get(0).movement().getOperacionId()).isEqualTo("operation-0");
        assertThat(results.get(0).userName()).isEqualTo("User Zero");
        assertThat(model.getAttribute("totalMovimientos")).isEqualTo(1L);
    }

    @Test
    void movementsPageFindsTransactionsUsingUserNameOrEmailSearch() {
        when(userDirectory.search("zero@example.com", 0, "access-token"))
                .thenReturn(new UserPage(
                        List.of(new UserSummary("user-0", "User Zero", "zero@example.com")),
                        1, 1, 0, 100));
        when(billeteraService.movimientosAdministrativos(
                "", "", null, null, "zero@example.com", List.of("user-0"), 0, 5))
                .thenReturn(new BilleteraService.MovementPage(
                        4, 0, IntStream.range(0, 4)
                                .mapToObj(index -> movementDocument(0, index))
                                .toList()));
        when(billeteraService.tiposMovimientoAdministrativos()).thenReturn(List.of("ABONO"));
        when(billeteraService.serviciosMovimientoAdministrativos()).thenReturn(List.of("Billetera"));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(RemoteAccessAuthenticationFilter.TOKEN_ATTRIBUTE, "access-token");
        ExtendedModelMap model = new ExtendedModelMap();

        controller.movements(
                "zero@example.com", "", "", null, null, 0,
                new AuthenticatedIdentity("admin", "admin@example.com", "Admin", "ROLE_ADMIN"),
                request, model);

        @SuppressWarnings("unchecked")
        List<WalletMovementRow> results = (List<WalletMovementRow>) model.getAttribute("movimientos");
        assertThat(results).hasSize(4);
        assertThat(results).extracting(WalletMovementRow::userEmail)
                .containsOnly("zero@example.com");
    }

    private static BilleteraUniversal walletWithMovements(int userIndex) {
        BilleteraUniversal wallet = new BilleteraUniversal();
        wallet.setUsuarioId("user-" + userIndex);
        wallet.setMovimientos(IntStream.range(0, 4)
                .mapToObj(movementIndex -> {
                    int operationIndex = userIndex * 4 + movementIndex;
                    MovimientoBilletera movement = new MovimientoBilletera(
                            "operation-" + operationIndex, "ABONO", "Carga " + operationIndex,
                            BigDecimal.valueOf(operationIndex), LocalDateTime.of(2026, 10, 25, 9, 0)
                                    .minusMinutes(operationIndex));
                    movement.setServicio("Billetera");
                    return movement;
                })
                .toList());
        return wallet;
    }

    private static BilleteraService.MovementDocument movementDocument(int userIndex, int movementIndex) {
        return new BilleteraService.MovementDocument(
                "user-" + userIndex, walletWithMovements(userIndex).getMovimientos().get(movementIndex));
    }
}

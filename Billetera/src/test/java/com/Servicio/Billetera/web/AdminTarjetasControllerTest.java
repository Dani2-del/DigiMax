package com.Servicio.Billetera.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
    void adminRootOpensTheDashboardInsteadOfTheAccountSearch() {
        assertThat(controller.adminHome()).isEqualTo("redirect:/admin/dashboard");
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
        List<BilleteraUniversal> wallets = IntStream.range(0, 2)
                .mapToObj(userIndex -> walletWithMovements(userIndex))
                .toList();
        when(billeteraService.billeterasAdministrativas()).thenReturn(wallets);
        when(userDirectory.findById(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq("access-token")))
                .thenAnswer(invocation -> new UserSummary(
                        invocation.getArgument(0), "User " + invocation.getArgument(0), "user@example.com"));

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
        assertThat(model.getAttribute("totalMovimientos")).isEqualTo(8);
        assertThat(model.getAttribute("totalPages")).isEqualTo(2);
        assertThat(model.getAttribute("firstMovement")).isEqualTo(6);
        assertThat(model.getAttribute("lastMovement")).isEqualTo(8);
        assertThat(model.getAttribute("hasPreviousPage")).isEqualTo(true);
        assertThat(model.getAttribute("hasNextPage")).isEqualTo(false);
    }

    @Test
    void movementsPageAppliesSearchTypeServiceAndDateFilters() {
        List<BilleteraUniversal> wallets = List.of(walletWithMovements(0));
        when(billeteraService.billeterasAdministrativas()).thenReturn(wallets);
        when(userDirectory.search("Carga 0", 0, "access-token"))
                .thenReturn(new UserPage(List.of(), 0, 0, 0, 100));
        when(userDirectory.findById("user-0", "access-token"))
                .thenReturn(new UserSummary("user-0", "User Zero", "zero@example.com"));

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
        assertThat(model.getAttribute("totalMovimientos")).isEqualTo(1);
    }

    @Test
    void movementsPageFindsTransactionsUsingUserNameOrEmailSearch() {
        List<BilleteraUniversal> wallets = List.of(walletWithMovements(0));
        when(billeteraService.billeterasAdministrativas()).thenReturn(wallets);
        when(userDirectory.search("zero@example.com", 0, "access-token"))
                .thenReturn(new UserPage(
                        List.of(new UserSummary("user-0", "User Zero", "zero@example.com")),
                        1, 1, 0, 100));

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
}

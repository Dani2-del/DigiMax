package com.Servicio.Acceso.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.server.ResponseStatusException;

import com.Servicio.Acceso.model.Usuario;
import com.Servicio.Acceso.repository.UsuarioRepository;
import com.Servicio.Acceso.service.GlobalOperationsService;
import com.Servicio.Acceso.service.GlobalOperationsService.GlobalTransaction;
import com.Servicio.Acceso.service.GlobalOperationsService.TransactionSnapshot;

@ExtendWith(MockitoExtension.class)
class GlobalOperationsControllerTest {

    @Mock
    private UsuarioRepository usuarios;

    @Mock
    private GlobalOperationsService operations;

    private GlobalOperationsController controller;

    @BeforeEach
    void setUp() {
        controller = new GlobalOperationsController(
                usuarios, operations, "http://localhost:8081",
                "http://localhost:8082", "http://localhost:8083");
    }

    @Test
    void exposesOperationalChatLinksForAllConnectedServices() {
        var model = new org.springframework.ui.ExtendedModelMap();

        assertThat(controller.chats(model)).isEqualTo("admin/global-chats");
        assertThat((List<GlobalOperationsController.ChatLink>) model.getAttribute("transportChats"))
                .extracting(GlobalOperationsController.ChatLink::url)
                .containsExactly(
                        "http://localhost:8081/admin/chat/TRANSCARIBE",
                        "http://localhost:8081/admin/chat/TRANSMILENIO",
                        "http://localhost:8081/admin/chat/TRANSMETRO");
        assertThat((List<GlobalOperationsController.ChatLink>) model.getAttribute("serviceChats"))
                .extracting(GlobalOperationsController.ChatLink::url)
                .containsExactly(
                        "http://localhost:8082/admin/chat",
                        "http://localhost:8083/admin/chat");
    }

    @Test
    void buildsCircularAnalyticsForServicesAndRoles() {
        LocalDateTime occurredAt = LocalDateTime.now();
        List<GlobalTransaction> transactions = List.of(
                new GlobalTransaction("operation-1", "BILLETERA", "ABONO", "Carga",
                        "user-1", "", "", new BigDecimal("1000"), "APROBADO", occurredAt, "Billetera"),
                new GlobalTransaction("operation-2", "BILLETERA", "ABONO", "Carga",
                        "user-1", "", "", new BigDecimal("2000"), "APROBADO", occurredAt, "Billetera"),
                new GlobalTransaction("operation-3", "RECARGAS_MOVIL", "SIMULADA", "Recarga",
                        "user-2", "", "", new BigDecimal("3000"), "APROBADO", occurredAt, "RecargasMovil"));
        Usuario admin = user();
        admin.setRole(Usuario.ROLE_ADMIN);
        when(operations.transactions(any(), nullable(java.time.LocalDate.class), nullable(java.time.LocalDate.class)))
                .thenReturn(new TransactionSnapshot(transactions, Map.of()));
        when(usuarios.findAll()).thenReturn(List.of(user(), admin));
        var model = new ExtendedModelMap();

        assertThat(controller.analytics(model)).isEqualTo("admin/global-analitica");

        assertThat(model.getAttribute("serviceTotal")).isEqualTo(3L);
        assertThat(model.getAttribute("roleTotal")).isEqualTo(2L);
        assertThat((String) model.getAttribute("serviceDonutStyle"))
                .contains("conic-gradient", "#8a63c7 0.00% 66.67%", "#df5967 66.67% 100.00%");
        assertThat((String) model.getAttribute("roleDonutStyle")).contains("conic-gradient");
        assertThat((List<GlobalOperationsController.DistributionMetric>) model.getAttribute("services"))
                .filteredOn(service -> service.name().equals("Billetera"))
                .extracting(GlobalOperationsController.DistributionMetric::count)
                .containsExactly(2L);
        assertThat((List<GlobalOperationsController.RoleMetric>) model.getAttribute("roles"))
                .extracting(GlobalOperationsController.RoleMetric::count)
                .containsExactly(1L, 1L);
    }

    @Test
    void usesEmptyDonutWhenThereIsNoAnalyticsData() {
        when(operations.transactions(any(), nullable(java.time.LocalDate.class), nullable(java.time.LocalDate.class)))
                .thenReturn(new TransactionSnapshot(List.of(), Map.of()));
        when(usuarios.findAll()).thenReturn(List.of());
        var model = new ExtendedModelMap();

        controller.analytics(model);

        assertThat(model.getAttribute("serviceTotal")).isEqualTo(0L);
        assertThat(model.getAttribute("roleTotal")).isEqualTo(0L);
        assertThat(model.getAttribute("serviceDonutStyle"))
                .isEqualTo("background: conic-gradient(#e9eef3 0% 100%)");
        assertThat(model.getAttribute("roleDonutStyle"))
                .isEqualTo("background: conic-gradient(#e9eef3 0% 100%)");
    }

    @Test
    void exportsSelectedUsersWithoutIncludingPasswordHashes() throws Exception {
        Usuario user = user();
        when(usuarios.findAll()).thenReturn(List.of(user));

        var response = controller.exportExcel(
                null, List.of("USUARIOS"), null, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(response.getBody()))) {
            assertThat(workbook.getSheet("Usuarios").getRow(1).getCell(2).getStringCellValue())
                    .isEqualTo(user.getCorreo());
            assertThat(workbook.getSheet("Usuarios").getRow(1).getCell(2).getStringCellValue())
                    .doesNotContain("password-hash");
            assertThat(workbook.getSheet("Transacciones")).isNull();
            assertThat(workbook.getSheet("Parámetros")).isNotNull();
        }
    }

    @Test
    void transactionExportUsesSelectedServicesAndDateRange() throws Exception {
        Usuario user = user();
        LocalDateTime date = LocalDateTime.of(2026, 10, 24, 10, 30);
        var transaction = new GlobalTransaction(
                "operation-1", "RECARGAS_MOVIL", "SIMULADA", "Paquete de datos",
                user.getId(), "", "", new BigDecimal("5000"), "SIMULADA", date, "RecargasMovil");
        when(operations.transactions(any(), nullable(java.time.LocalDate.class), nullable(java.time.LocalDate.class)))
                .thenReturn(new TransactionSnapshot(List.of(transaction), Map.of()));
        when(usuarios.findAll()).thenReturn(List.of(user));

        var response = controller.exportExcel(
                List.of("RECARGAS_MOVIL"), List.of("TRANSACCIONES"), null, null);

        try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(response.getBody()))) {
            var sheet = workbook.getSheet("Transacciones");
            assertThat(sheet.getRow(1).getCell(1).getStringCellValue()).isEqualTo("Recargas móviles");
            assertThat(sheet.getRow(1).getCell(4).getStringCellValue()).isEqualTo(user.getNombre());
            assertThat(sheet.getRow(1).getCell(6).getNumericCellValue()).isEqualTo(5000);
            assertThat(workbook.getSheet("Parámetros").getRow(1).getCell(1).getStringCellValue())
                    .isEqualTo("Recargas móviles");
        }
    }

    @Test
    void doesNotCreatePartialReportWhenSelectedDataSourceIsUnavailable() {
        when(operations.transactions(any(), nullable(java.time.LocalDate.class), nullable(java.time.LocalDate.class)))
                .thenReturn(new TransactionSnapshot(List.of(), Map.of("Billetera", "No disponible")));

        assertThatThrownBy(() -> controller.exportExcel(
                List.of("BILLETERA"), List.of("TRANSACCIONES"), null, null))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(exception -> ((ResponseStatusException) exception).getStatusCode())
                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void rejectsTransactionExportWithoutSelectedService() {
        assertThatThrownBy(() -> controller.exportExcel(
                null, List.of("TRANSACCIONES"), null, null))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(exception -> ((ResponseStatusException) exception).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void displaysTransactionsInPagesOfTenAndClampsPageToAvailableRange() {
        List<GlobalTransaction> transactions = IntStream.range(0, 12)
                .mapToObj(index -> new GlobalTransaction(
                        "operation-" + index, "BILLETERA", "ABONO", "Movimiento",
                        null, "", "", BigDecimal.ZERO, "COMPLETADA",
                        LocalDateTime.of(2026, 10, 24, 10, 0).minusMinutes(index), "Billetera"))
                .toList();
        when(operations.transactions(any(), nullable(java.time.LocalDate.class), nullable(java.time.LocalDate.class)))
                .thenReturn(new TransactionSnapshot(transactions, Map.of()));
        var firstPage = new ExtendedModelMap();
        var lastPage = new ExtendedModelMap();

        controller.transactions(null, null, null, 0, firstPage);
        controller.transactions(null, null, null, 1, lastPage);

        assertThat(firstPage.getAttribute("transactions")).isEqualTo(transactions.subList(0, 10));
        assertThat(firstPage.getAttribute("hasPreviousPage")).isEqualTo(false);
        assertThat(firstPage.getAttribute("hasNextPage")).isEqualTo(true);
        assertThat(firstPage.getAttribute("totalPages")).isEqualTo(2);
        assertThat(lastPage.getAttribute("transactions")).isEqualTo(transactions.subList(10, 12));
        assertThat(lastPage.getAttribute("hasPreviousPage")).isEqualTo(true);
        assertThat(lastPage.getAttribute("hasNextPage")).isEqualTo(false);
        assertThat(lastPage.getAttribute("firstTransaction")).isEqualTo(11);
        assertThat(lastPage.getAttribute("lastTransaction")).isEqualTo(12);
    }

    private static Usuario user() {
        Usuario user = new Usuario("user@example.com", "password-hash", "User Example");
        user.setId("user-1");
        return user;
    }
}

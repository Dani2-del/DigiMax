package com.Servicio.RecargasMovil.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.ui.ConcurrentModel;

import com.Servicio.RecargasMovil.model.OperadorMovil;
import com.Servicio.RecargasMovil.model.PaqueteMovil;
import com.Servicio.RecargasMovil.model.RecargaMovil;
import com.Servicio.RecargasMovil.repository.OperadorMovilRepository;
import com.Servicio.RecargasMovil.repository.PaqueteMovilRepository;
import com.Servicio.RecargasMovil.repository.RecargaMovilRepository;
import com.Servicio.RecargasMovil.service.RecargasExcelReportService;
import com.Servicio.RecargasMovil.service.RecargasService;

@ExtendWith(MockitoExtension.class)
class AdminCatalogControllerTest {

    @Mock
    private RecargasService recargasService;

    @Mock
    private OperadorMovilRepository operadores;

    @Mock
    private PaqueteMovilRepository paquetes;

    @Mock
    private RecargaMovilRepository recargas;

    @Mock
    private MongoTemplate mongoTemplate;

    @Mock
    private RecargasExcelReportService excelReportService;

    private AdminCatalogController controller;

    @BeforeEach
    void setUp() {
        controller = new AdminCatalogController(
                recargasService, operadores, paquetes, recargas, mongoTemplate,
                excelReportService, "http://acceso.test");
    }

    @Test
    void dashboardShowsOnlyTheSummaryView() {
        when(paquetes.findAllByOrderByNombreAsc()).thenReturn(List.of());
        ConcurrentModel model = new ConcurrentModel();

        String view = controller.dashboard(model);

        assertThat(view).isEqualTo("admin-catalogo");
        assertThat(model.getAttribute("totalRecargas")).isEqualTo(0L);
        assertThat(model.getAttribute("paquetesActivos")).isEqualTo(0L);
        assertThat(model.getAttribute("recargasPage")).isNull();
    }

    @Test
    void packageCreateAndEditHaveSeparateViews() {
        ConcurrentModel newPackageModel = new ConcurrentModel();
        when(recargasService.operadoresActivos()).thenReturn(List.of());

        assertThat(controller.newPackage(newPackageModel)).isEqualTo("admin-paquete-nuevo");

        PaqueteMovil mobilePackage = new PaqueteMovil();
        mobilePackage.setId("package-1");
        when(paquetes.findById("package-1")).thenReturn(java.util.Optional.of(mobilePackage));
        ConcurrentModel editPackageModel = new ConcurrentModel();

        assertThat(controller.editPackage("package-1", editPackageModel, new org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap()))
                .isEqualTo("admin-paquete-editar");
        assertThat(editPackageModel.getAttribute("paquete")).isSameAs(mobilePackage);
    }

    @Test
    void operatorAndPackageListsHaveSeparateViews() {
        when(operadores.findAll()).thenReturn(List.of());
        when(paquetes.findAllByOrderByNombreAsc()).thenReturn(List.of());
        assertThat(controller.operators(new ConcurrentModel())).isEqualTo("admin-operadores");
        assertThat(controller.packages(new ConcurrentModel())).isEqualTo("admin-paquetes");
    }

    @Test
    void showsFilteredPagedRechargeOperationsOnTheirOwnPage() {
        when(operadores.findAll()).thenReturn(List.of());
        when(paquetes.findAllByOrderByNombreAsc()).thenReturn(List.of());
        RecargaMovil recharge = new RecargaMovil();
        recharge.setOperacionId("operation-1");
        when(mongoTemplate.find(any(Query.class), eq(RecargaMovil.class))).thenReturn(List.of(recharge));
        when(mongoTemplate.count(any(Query.class), eq(RecargaMovil.class))).thenReturn(11L);
        ConcurrentModel model = new ConcurrentModel();

        String view = controller.operations(
                "claro", "SIMULADA", "", "saldo",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), 1, model);

        assertThat(view).isEqualTo("admin-operaciones");
        assertThat(model.getAttribute("recargasPage")).isInstanceOf(org.springframework.data.domain.Page.class);
        var rechargePage = (org.springframework.data.domain.Page<?>) model.getAttribute("recargasPage");
        assertThat(rechargePage.getContent()).hasSize(1);
        assertThat(rechargePage.getContent().get(0)).isSameAs(recharge);
        assertThat(rechargePage.getNumber()).isEqualTo(1);
        assertThat(rechargePage.getSize()).isEqualTo(10);
        assertThat(rechargePage.getTotalElements()).isEqualTo(11);
        assertThat(model.getAttribute("errorFiltros")).isNull();
        verify(mongoTemplate).find(any(Query.class), eq(RecargaMovil.class));
    }

    @Test
    void reportPageOffersOperatorFilters() {
        OperadorMovil operator = new OperadorMovil();
        operator.setId("operator-1");
        operator.setNombre("Claro");
        when(operadores.findAll()).thenReturn(List.of(operator));

        assertThat(controller.reports(new ConcurrentModel())).isEqualTo("admin-reportes");
    }

    @Test
    void excelReportStreamsAllMatchingRechargeRecords() throws Exception {
        when(operadores.findAll()).thenReturn(List.of());
        when(mongoTemplate.stream(any(Query.class), eq(RecargaMovil.class)))
                .thenReturn(Stream.empty());

        var response = controller.downloadExcelReport(
                "", "", "", List.of("operacion"), null, null, "Informe Recargas");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        response.getBody().writeTo(output);

        assertThat(response.getHeaders().getContentType().toString())
                .isEqualTo("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        assertThat(response.getHeaders().getFirst("Content-Disposition"))
                .contains("Informe Recargas.xlsx");
        verify(excelReportService).writeReport(any(), any(), eq(output));
    }

    @Test
    void excelReportRejectsInvalidDateRange() {
        when(operadores.findAll()).thenReturn(List.of());

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> controller.downloadExcelReport(
                "", "", "", List.of("operacion"),
                LocalDate.of(2026, 2, 1), LocalDate.of(2026, 1, 1), null))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        verify(mongoTemplate, never()).stream(any(Query.class), eq(RecargaMovil.class));
    }

    @Test
    void excelReportRequiresAtLeastOneSelectedColumn() {
        when(operadores.findAll()).thenReturn(List.of());

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> controller.downloadExcelReport(
                "", "", "", List.of(), null, null, null))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        verify(mongoTemplate, never()).stream(any(Query.class), eq(RecargaMovil.class));
    }

    @Test
    void rejectsInvalidDateRangeInsteadOfShowingUnfilteredOperations() {
        when(operadores.findAll()).thenReturn(List.of());
        when(paquetes.findAllByOrderByNombreAsc()).thenReturn(List.of());
        ConcurrentModel model = new ConcurrentModel();

        controller.operations(
                "", "", "", "",
                LocalDate.of(2026, 2, 1), LocalDate.of(2026, 1, 1), 0, model);

        assertThat(model.getAttribute("errorFiltros")).isNotNull();
        var rechargePage = (org.springframework.data.domain.Page<?>) model.getAttribute("recargasPage");
        assertThat(rechargePage.getContent()).isEmpty();
        verify(mongoTemplate, never()).find(any(Query.class), eq(RecargaMovil.class));
        verify(mongoTemplate, never()).count(any(Query.class), eq(RecargaMovil.class));
    }

    @Test
    void operatorFilterUsesStoredOperatorNameInRechargeRecords() {
        when(paquetes.findAllByOrderByNombreAsc()).thenReturn(List.of());
        OperadorMovil operator = new OperadorMovil();
        operator.setId("operator-1");
        operator.setNombre("Claro");
        when(operadores.findAll()).thenReturn(List.of(operator));
        when(operadores.findById("operator-1")).thenReturn(java.util.Optional.of(operator));
        when(mongoTemplate.find(any(Query.class), eq(RecargaMovil.class))).thenReturn(List.of());
        when(mongoTemplate.count(any(Query.class), eq(RecargaMovil.class))).thenReturn(0L);
        ConcurrentModel model = new ConcurrentModel();

        controller.operations("", "", "operator-1", "", null, null, 0, model);

        assertThat(model.getAttribute("errorFiltros")).isNull();
        verify(operadores).findById("operator-1");
        verify(mongoTemplate).find(any(Query.class), eq(RecargaMovil.class));
    }
}

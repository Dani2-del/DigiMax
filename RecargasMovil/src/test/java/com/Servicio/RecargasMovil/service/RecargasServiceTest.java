package com.Servicio.RecargasMovil.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.Servicio.RecargasMovil.model.CategoriaPaquete;
import com.Servicio.RecargasMovil.model.LineaMovil;
import com.Servicio.RecargasMovil.model.OperadorMovil;
import com.Servicio.RecargasMovil.model.PaqueteMovil;
import com.Servicio.RecargasMovil.model.RecargaMovil;
import com.Servicio.RecargasMovil.repository.LineaMovilRepository;
import com.Servicio.RecargasMovil.repository.OperadorMovilRepository;
import com.Servicio.RecargasMovil.repository.PaqueteMovilRepository;
import com.Servicio.RecargasMovil.repository.RecargaMovilRepository;

@ExtendWith(MockitoExtension.class)
class RecargasServiceTest {

    private static final String PAYER = "persona@example.com";
    private static final PagosClient.ResultadoPago PENDING =
            new PagosClient.ResultadoPago("payment-1", "PENDIENTE", null);

    @Mock
    private OperadorMovilRepository operadores;

    @Mock
    private PaqueteMovilRepository paquetes;

    @Mock
    private LineaMovilRepository lineas;

    @Mock
    private RecargaMovilRepository recargas;

    @Mock
    private PagosClient pagosClient;

    private RecargasService service;

    @BeforeEach
    void setUp() {
        service = new RecargasService(operadores, paquetes, lineas, recargas, pagosClient);
    }

    @Test
    void iniciaPagoPendienteYGuardaSuReferenciaParaUnPaquete() {
        prepararPaquete();
        when(pagosClient.iniciar(eq("operation-1"), eq(new BigDecimal("5000.00")),
                anyString(), eq(PAYER))).thenReturn(PENDING);
        when(recargas.save(any(RecargaMovil.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        RecargaMovil result = service.solicitarRecarga(
                "user-1", "line-1", "package-1", "operation-1", PAYER);

        assertThat(result.getEstado()).isEqualTo(RecargaMovil.STATUS_PAYMENT_PENDING);
        assertThat(result.getEstadoPago()).isEqualTo("PENDIENTE");
        assertThat(result.getIdPagoProveedor()).isEqualTo("payment-1");
        assertThat(result.getPagador()).isEqualTo(PAYER);
        assertThat(result.getMonto()).isEqualByComparingTo("5000");
        assertThat(result.getTelefono()).isEqualTo("+573001234567");
        verify(pagosClient).iniciar(eq("operation-1"), eq(new BigDecimal("5000.00")),
                anyString(), eq(PAYER));
    }

    @Test
    void noIniciaPagoParaUnaLineaDeOtroUsuario() {
        when(recargas.findById("operation-1")).thenReturn(Optional.empty());
        when(lineas.findById("line-1"))
                .thenReturn(Optional.of(line("line-1", "other-user", "operator-1")));

        assertThatThrownBy(() -> service.solicitarRecarga(
                "user-1", "line-1", "package-1", "operation-1", PAYER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Selecciona una línea tuya.");

        verify(pagosClient, never()).iniciar(any(), any(), any(), any());
        verify(recargas, never()).insert(any(RecargaMovil.class));
    }

    @Test
    void devuelveLaRecargaCompletadaSinCrearOtroPago() {
        RecargaMovil completed = new RecargaMovil();
        completed.setOperacionId("operation-1");
        completed.setUsuarioId("user-1");
        completed.setEstado(RecargaMovil.STATUS_SIMULATED);
        when(recargas.findById("operation-1")).thenReturn(Optional.of(completed));

        RecargaMovil result = service.solicitarRecarga(
                "user-1", "line-1", "package-1", "operation-1", PAYER);

        assertThat(result).isSameAs(completed);
        verify(pagosClient, never()).iniciar(any(), any(), any(), any());
        verify(pagosClient, never()).consultar(any());
    }

    @Test
    void marcaComoRechazadaUnaRecargaSiElPagoSeRechaza() {
        RecargaMovil pending = pendingRecharge();
        when(recargas.findById("operation-1")).thenReturn(Optional.of(pending));
        when(pagosClient.consultar("payment-1"))
                .thenReturn(new PagosClient.ResultadoPago("payment-1", "RECHAZADO", null));
        when(recargas.save(any(RecargaMovil.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        RecargaMovil result = service.reintentarRecarga("user-1", "operation-1", PAYER);

        assertThat(result.getEstado()).isEqualTo(RecargaMovil.STATUS_REJECTED);
        assertThat(result.getEstadoPago()).isEqualTo("RECHAZADO");
    }

    @Test
    void procesaLaRecargaSoloCuandoElPagoPendienteSeAprueba() {
        RecargaMovil pending = pendingRecharge();
        when(recargas.findById("operation-1")).thenReturn(Optional.of(pending));
        when(pagosClient.consultar("payment-1"))
                .thenReturn(new PagosClient.ResultadoPago("payment-1", "APROBADO", null));
        when(recargas.save(any(RecargaMovil.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        RecargaMovil result = service.reintentarRecarga("user-1", "operation-1", PAYER);

        assertThat(result.getEstado()).isEqualTo(RecargaMovil.STATUS_SIMULATED);
        assertThat(result.getEstadoPago()).isEqualTo("APROBADO");
        verify(pagosClient).consultar("payment-1");
        verify(pagosClient, never()).iniciar(any(), any(), any(), any());
    }

    @Test
    void conservaLaRecargaPendienteMientrasElPagoNoSeResuelva() {
        RecargaMovil pending = pendingRecharge();
        when(recargas.findById("operation-1")).thenReturn(Optional.of(pending));
        when(pagosClient.consultar("payment-1")).thenReturn(PENDING);
        when(recargas.save(any(RecargaMovil.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        RecargaMovil result = service.reintentarRecarga("user-1", "operation-1", PAYER);

        assertThat(result.getEstado()).isEqualTo(RecargaMovil.STATUS_PAYMENT_PENDING);
        assertThat(result.getEstadoPago()).isEqualTo("PENDIENTE");
    }

    @Test
    void noCreaUnPagoParaUnaOperacionPendienteDelFlujoAnteriorDeBilletera() {
        RecargaMovil legacy = new RecargaMovil();
        legacy.setOperacionId("operation-1");
        legacy.setUsuarioId("user-1");
        legacy.setEstado(RecargaMovil.STATUS_PROCESSING);
        when(recargas.findById("operation-1")).thenReturn(Optional.of(legacy));

        assertThatThrownBy(() -> service.reintentarRecarga("user-1", "operation-1", PAYER))
                .isInstanceOf(RecargasService.OperacionPendienteAnteriorException.class)
                .hasMessageContaining("Verifica su movimiento");

        verify(pagosClient, never()).iniciar(any(), any(), any(), any());
    }

    @Test
    void guardaUnaLineaConDiezDigitosQueEmpiezaPorTres() {
        when(operadores.findById("claro-1")).thenReturn(Optional.of(operator("claro-1")));
        when(lineas.save(any(LineaMovil.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        LineaMovil result = service.guardarLinea("user-1", "claro-1", "3000000000", "Mi línea");

        assertThat(result.getTelefono()).isEqualTo("3000000000");
        verify(lineas).save(any(LineaMovil.class));
    }

    @Test
    void rechazaNumerosQueNoCumplenElFormatoMovilColombiano() {
        when(operadores.findById("claro-1")).thenReturn(Optional.of(operator("claro-1")));

        for (String invalidPhone : List.of(
                "2000000000", "300000000", "30000000000", "300000000a",
                "+573000000000", "300 0000000")) {
            assertThatThrownBy(() -> service.guardarLinea(
                    "user-1", "claro-1", invalidPhone, "Mi línea"))
                    .as("número inválido: %s", invalidPhone)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Escribe un número móvil de 10 dígitos que empiece por 3.");
        }

        verify(lineas, never()).save(any(LineaMovil.class));
    }

    @Test
    void registraUnaRecargaDeSaldoComoPagoPendiente() {
        OperadorMovil operator = operator("claro-1");
        operator.setNombre("Claro");
        when(recargas.findById("operation-2")).thenReturn(Optional.empty());
        when(operadores.findById("claro-1")).thenReturn(Optional.of(operator));
        when(lineas.findById("line-1"))
                .thenReturn(Optional.of(line("line-1", "user-1", "claro-1")));
        when(recargas.insert(any(RecargaMovil.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(pagosClient.iniciar(eq("operation-2"), eq(new BigDecimal("25000.00")),
                anyString(), eq(PAYER))).thenReturn(PENDING);
        when(recargas.save(any(RecargaMovil.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        RecargaMovil result = service.solicitarRecargaDinero(
                "user-1", "line-1", new BigDecimal("25000"), "operation-2", PAYER);

        assertThat(result.getEstado()).isEqualTo(RecargaMovil.STATUS_PAYMENT_PENDING);
        assertThat(result.getMonto()).isEqualByComparingTo("25000");
        assertThat(result.getPaquete()).isEqualTo("Recarga de saldo $25000");
    }

    @Test
    void rechazaOtraLineaDeUsuarioAntesDeIniciarElPagoDeSaldo() {
        when(recargas.findById("operation-2")).thenReturn(Optional.empty());
        when(lineas.findById("line-1"))
                .thenReturn(Optional.of(line("line-1", "other-user", "claro-1")));

        assertThatThrownBy(() -> service.solicitarRecargaDinero(
                "user-1", "line-1", new BigDecimal("25000"), "operation-2", PAYER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Selecciona una línea tuya.");

        verify(pagosClient, never()).iniciar(any(), any(), any(), any());
        verify(recargas, never()).insert(any(RecargaMovil.class));
    }

    @Test
    void excluyeLasDenominacionesAntiguasDeRecargaDelCatalogoDePaquetes() {
        OperadorMovil claro = operator("claro-1");
        PaqueteMovil dataPackage = mobilePackage("package-1", "claro-1", "Datos");
        PaqueteMovil oldRechargeDenomination =
                mobilePackage("recharge-1", "claro-1", "Recarga de $1.000");
        oldRechargeDenomination.setCategoria(CategoriaPaquete.RECARGA_DINERO);
        when(operadores.findByActivoTrueOrderByNombreAsc()).thenReturn(List.of(claro));
        when(paquetes.findByOperadorIdAndActivoTrueOrderByPrecioAsc("claro-1"))
                .thenReturn(List.of(oldRechargeDenomination, dataPackage));

        assertThat(service.paquetesActivos()).containsExactly(dataPackage);
    }

    @Test
    void devuelveElHistorialCompletoDelUsuarioEnOrdenDelRepositorio() {
        List<RecargaMovil> history = List.of(new RecargaMovil(), new RecargaMovil());
        when(recargas.findByUsuarioIdOrderByFechaDesc("user-1")).thenReturn(history);

        assertThat(service.historialDelUsuario("user-1")).containsExactlyElementsOf(history);
        verify(recargas).findByUsuarioIdOrderByFechaDesc("user-1");
    }

    @Test
    void rechazaMontosPersonalizadosInvalidosAntesDeCrearLaRecarga() {
        assertThatThrownBy(() -> service.solicitarRecargaDinero(
                "user-1", "line-1", new BigDecimal("1500"), "operation-3", PAYER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("incrementos de $1.000");

        verify(pagosClient, never()).iniciar(any(), any(), any(), any());
        verify(recargas, never()).insert(any(RecargaMovil.class));
    }

    @Test
    void rechazaUnPlanPospagoEnElFlujoDeRecargaPrepago() {
        OperadorMovil claro = operator("claro-1");
        claro.setNombre("Claro");
        when(recargas.findById("operation-4")).thenReturn(Optional.empty());
        when(lineas.findById("line-1"))
                .thenReturn(Optional.of(line("line-1", "user-1", "claro-1")));
        when(operadores.findById("claro-1")).thenReturn(Optional.of(claro));
        PaqueteMovil postpaidPlan = mobilePackage("postpaid-1", "claro-1", "Conectados");
        postpaidPlan.setCategoria(CategoriaPaquete.POSTPAGO);
        when(paquetes.findById("postpaid-1")).thenReturn(Optional.of(postpaidPlan));

        assertThatThrownBy(() -> service.solicitarRecarga(
                "user-1", "line-1", "postpaid-1", "operation-4", PAYER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("oferta prepago");

        verify(pagosClient, never()).iniciar(any(), any(), any(), any());
        verify(recargas, never()).insert(any(RecargaMovil.class));
    }

    private void prepararPaquete() {
        when(recargas.findById("operation-1")).thenReturn(Optional.empty());
        when(lineas.findById("line-1"))
                .thenReturn(Optional.of(line("line-1", "user-1", "operator-1")));
        when(operadores.findById("operator-1")).thenReturn(Optional.of(operator("operator-1")));
        when(paquetes.findById("package-1"))
                .thenReturn(Optional.of(mobilePackage("package-1", "operator-1", "Datos")));
        when(recargas.insert(any(RecargaMovil.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private RecargaMovil pendingRecharge() {
        RecargaMovil recharge = new RecargaMovil();
        recharge.setOperacionId("operation-1");
        recharge.setUsuarioId("user-1");
        recharge.setPaquete("Datos");
        recharge.setMonto(new BigDecimal("5000.00"));
        recharge.setEstado(RecargaMovil.STATUS_PAYMENT_PENDING);
        recharge.setIdPagoProveedor("payment-1");
        recharge.setEstadoPago("PENDIENTE");
        recharge.setPagador(PAYER);
        return recharge;
    }

    private static OperadorMovil operator(String id) {
        OperadorMovil operator = new OperadorMovil();
        operator.setId(id);
        operator.setNombre("Operador Demo");
        operator.setActivo(true);
        return operator;
    }

    private static LineaMovil line(String id, String userId, String operatorId) {
        LineaMovil line = new LineaMovil();
        line.setId(id);
        line.setUsuarioId(userId);
        line.setOperadorId(operatorId);
        line.setTelefono("+573001234567");
        return line;
    }

    private static PaqueteMovil mobilePackage(String id, String operatorId, String name) {
        PaqueteMovil mobilePackage = new PaqueteMovil();
        mobilePackage.setId(id);
        mobilePackage.setOperadorId(operatorId);
        mobilePackage.setNombre(name);
        mobilePackage.setPrecio(new BigDecimal("5000.00"));
        mobilePackage.setActivo(true);
        return mobilePackage;
    }
}

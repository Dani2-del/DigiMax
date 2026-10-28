package com.Servicio.Pagos.proveedor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.Servicio.Pagos.dominio.EstadoPago;

class ProveedorSimuladoTest {

    private ProveedorSimulado proveedor;

    @BeforeEach
    void setUp() {
        proveedor = new ProveedorSimulado(10);
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        proveedor.cerrarAprobadorAutomatico();
    }

    @Test
    void iniciaPagoNormalComoPendiente() {
        ResultadoInicio resultado = proveedor.iniciar(solicitud("25000"));

        assertThat(resultado.idProveedor()).isNotBlank();
        assertThat(resultado.estado()).isEqualTo(EstadoPago.PENDIENTE);
        assertThat(resultado.urlPago()).isNull();
    }

    @Test
    void rechazaMontoSuperiorAlMaximo() {
        ResultadoInicio resultado = proveedor.iniciar(solicitud("1000000.01"));

        assertThat(resultado.estado()).isEqualTo(EstadoPago.RECHAZADO);
    }

    @Test
    void consultaEstadoDePagoExistente() {
        ResultadoInicio resultado = proveedor.iniciar(solicitud("25000"));

        assertThat(proveedor.consultar(resultado.idProveedor())).isEqualTo(EstadoPago.PENDIENTE);
    }

    @Test
    void fallaAlConsultarIdInexistente() {
        assertThatThrownBy(() -> proveedor.consultar("id-inexistente"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No existe un pago");
    }

    @Test
    void montoEnElLimiteSiguePendiente() {
        ResultadoInicio resultado = proveedor.iniciar(solicitud("1000000"));

        assertThat(resultado.estado()).isEqualTo(EstadoPago.PENDIENTE);
    }

    @Test
    void solicitudConLaMismaReferenciaDevuelveElPagoExistente() {
        ResultadoInicio primero = proveedor.iniciar(solicitud("25000"));

        ResultadoInicio repetido = proveedor.iniciar(solicitud("25000.00"));

        assertThat(repetido).isEqualTo(primero);
    }

    @Test
    void noPermiteReutilizarUnaReferenciaConDatosDePagoDistintos() {
        proveedor.iniciar(solicitud("25000"));

        assertThatThrownBy(() -> proveedor.iniciar(solicitud("30000")))
                .isInstanceOf(ProveedorSimulado.ReferenciaPagoDuplicadaException.class)
                .hasMessageContaining("ya está asociada");
    }

    @Test
    void rechazaMontoNoPositivo() {
        assertThatThrownBy(() -> proveedor.iniciar(solicitud("0")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void simulaTransicionDePendienteAAprobado() {
        ResultadoInicio iniciado = proveedor.iniciar(solicitud("25000"));

        assertThat(proveedor.simularResultado(iniciado.idProveedor(), EstadoPago.APROBADO))
                .isEqualTo(EstadoPago.APROBADO);
        assertThat(proveedor.consultar(iniciado.idProveedor())).isEqualTo(EstadoPago.APROBADO);
    }

    @Test
    void apruebaAutomaticamenteDespuesDeLaDemoraConfigurada() throws InterruptedException {
        proveedor.cerrarAprobadorAutomatico();
        proveedor = new ProveedorSimulado(1);
        ResultadoInicio iniciado = proveedor.iniciar(solicitud("25000"));

        assertThat(proveedor.consultar(iniciado.idProveedor())).isEqualTo(EstadoPago.PENDIENTE);

        Thread.sleep(1200);

        assertThat(proveedor.consultar(iniciado.idProveedor())).isEqualTo(EstadoPago.APROBADO);
    }

    @Test
    void noSobrescribeUnaDecisionManualAntesDeLaAprobacionAutomatica() throws InterruptedException {
        proveedor.cerrarAprobadorAutomatico();
        proveedor = new ProveedorSimulado(1);
        ResultadoInicio iniciado = proveedor.iniciar(solicitud("25000"));
        proveedor.simularResultado(iniciado.idProveedor(), EstadoPago.RECHAZADO);

        Thread.sleep(1200);

        assertThat(proveedor.consultar(iniciado.idProveedor())).isEqualTo(EstadoPago.RECHAZADO);
    }

    @Test
    void noPermiteModificarUnPagoQueYaFinalizo() {
        ResultadoInicio iniciado = proveedor.iniciar(solicitud("25000"));
        proveedor.simularResultado(iniciado.idProveedor(), EstadoPago.APROBADO);

        assertThatThrownBy(() -> proveedor.simularResultado(iniciado.idProveedor(), EstadoPago.RECHAZADO))
                .isInstanceOf(ProveedorSimulado.TransicionPagoInvalidaException.class)
                .hasMessage("No se puede cambiar el pago de APROBADO a RECHAZADO.");
        assertThat(proveedor.consultar(iniciado.idProveedor())).isEqualTo(EstadoPago.APROBADO);
    }

    @Test
    void noPermiteSimularEstadoNoFinal() {
        ResultadoInicio iniciado = proveedor.iniciar(solicitud("25000"));

        assertThatThrownBy(() -> proveedor.simularResultado(iniciado.idProveedor(), EstadoPago.PENDIENTE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("APROBADO, RECHAZADO o CANCELADO");
    }

    private SolicitudPago solicitud(String monto) {
        return new SolicitudPago(
                "recarga-1",
                new BigDecimal(monto),
                "COP",
                "Recarga de transporte",
                "persona@example.com");
    }
}

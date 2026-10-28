package com.Servicio.Pagos.proveedor;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import jakarta.annotation.PreDestroy;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.Servicio.Pagos.dominio.EstadoPago;

@Component
@ConditionalOnProperty(name = "pagos.proveedor", havingValue = "simulado", matchIfMissing = true)
public class ProveedorSimulado implements ProveedorPago, SimuladorPago {

    private static final BigDecimal MONTO_MAXIMO = new BigDecimal("1000000");

    private final ConcurrentMap<String, PagoRegistrado> pagos = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, String> pagosPorReferencia = new ConcurrentHashMap<>();
    private final ScheduledExecutorService aprobadorAutomatico =
            Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "pagos-aprobacion-automatica");
                thread.setDaemon(true);
                return thread;
            });
    private final long demoraAprobacionSegundos;

    public ProveedorSimulado(
            @Value("${pagos.simulacion.aprobacion-automatica-segundos:10}")
            long demoraAprobacionSegundos) {
        if (demoraAprobacionSegundos < 0) {
            throw new IllegalArgumentException("La demora de aprobación no puede ser negativa.");
        }
        this.demoraAprobacionSegundos = demoraAprobacionSegundos;
    }

    @Override
    public String nombre() {
        return "simulado";
    }

    @Override
    public ResultadoInicio iniciar(SolicitudPago solicitud) {
        if (solicitud == null || solicitud.monto() == null || solicitud.monto().signum() <= 0) {
            throw new IllegalArgumentException("La solicitud y el monto del pago deben ser válidos.");
        }

        String idProveedor = pagosPorReferencia.computeIfAbsent(solicitud.referencia(), referencia -> {
            String id = UUID.randomUUID().toString();
            EstadoPago estado = solicitud.monto().compareTo(MONTO_MAXIMO) > 0
                    ? EstadoPago.RECHAZADO
                    : EstadoPago.PENDIENTE;
            ResultadoInicio resultado = new ResultadoInicio(id, estado, null);
            pagos.put(id, new PagoRegistrado(solicitud, resultado));
            if (estado == EstadoPago.PENDIENTE) {
                aprobadorAutomatico.schedule(
                        () -> aprobarSiSiguePendiente(id),
                        demoraAprobacionSegundos,
                        TimeUnit.SECONDS);
            }
            return id;
        });
        PagoRegistrado pago = obtenerPagoRegistrado(idProveedor);
        if (!mismaSolicitud(pago.solicitud(), solicitud)) {
            throw new ReferenciaPagoDuplicadaException(solicitud.referencia());
        }
        return pago.resultado();
    }

    @Override
    public EstadoPago consultar(String idProveedor) {
        return obtenerPago(idProveedor).estado();
    }

    @Override
    public EstadoPago simularResultado(String idProveedor, EstadoPago estadoFinal) {
        if (estadoFinal != EstadoPago.APROBADO
                && estadoFinal != EstadoPago.RECHAZADO
                && estadoFinal != EstadoPago.CANCELADO) {
            throw new IllegalArgumentException(
                    "El resultado simulado debe ser APROBADO, RECHAZADO o CANCELADO.");
        }

        return pagos.compute(idProveedor, (id, actual) -> {
            if (actual == null) {
                throw new PagoNoEncontradoException();
            }
            if (actual.resultado().estado() != EstadoPago.PENDIENTE) {
                throw new TransicionPagoInvalidaException(actual.resultado().estado(), estadoFinal);
            }
            ResultadoInicio resultado = new ResultadoInicio(
                    actual.resultado().idProveedor(), estadoFinal, actual.resultado().urlPago());
            return new PagoRegistrado(actual.solicitud(), resultado);
        }).resultado().estado();
    }

    private ResultadoInicio obtenerPago(String idProveedor) {
        return obtenerPagoRegistrado(idProveedor).resultado();
    }

    private PagoRegistrado obtenerPagoRegistrado(String idProveedor) {
        PagoRegistrado pago = pagos.get(idProveedor);
        if (pago == null) {
            throw new PagoNoEncontradoException();
        }
        return pago;
    }

    private void aprobarSiSiguePendiente(String idProveedor) {
        pagos.computeIfPresent(idProveedor, (id, actual) -> {
            if (actual.resultado().estado() != EstadoPago.PENDIENTE) {
                return actual;
            }
            ResultadoInicio resultado = new ResultadoInicio(
                    actual.resultado().idProveedor(), EstadoPago.APROBADO, actual.resultado().urlPago());
            return new PagoRegistrado(actual.solicitud(), resultado);
        });
    }

    @PreDestroy
    void cerrarAprobadorAutomatico() {
        aprobadorAutomatico.shutdownNow();
    }

    private static boolean mismaSolicitud(SolicitudPago original, SolicitudPago nueva) {
        return original.referencia().equals(nueva.referencia())
                && original.monto().compareTo(nueva.monto()) == 0
                && original.moneda().equals(nueva.moneda())
                && original.descripcion().equals(nueva.descripcion())
                && original.pagador().equals(nueva.pagador());
    }

    private record PagoRegistrado(SolicitudPago solicitud, ResultadoInicio resultado) {
    }

    public static class PagoNoEncontradoException extends IllegalArgumentException {
        private static final long serialVersionUID = 1L;

        public PagoNoEncontradoException() {
            super("No existe un pago con el ID de proveedor indicado.");
        }
    }

    public static class TransicionPagoInvalidaException extends IllegalStateException {
        private static final long serialVersionUID = 1L;

        public TransicionPagoInvalidaException(EstadoPago estadoActual, EstadoPago estadoSolicitado) {
            super("No se puede cambiar el pago de " + estadoActual + " a " + estadoSolicitado + ".");
        }
    }

    public static class ReferenciaPagoDuplicadaException extends IllegalArgumentException {
        private static final long serialVersionUID = 1L;

        public ReferenciaPagoDuplicadaException(String referencia) {
            super("La referencia " + referencia + " ya está asociada a una solicitud de pago diferente.");
        }
    }
}

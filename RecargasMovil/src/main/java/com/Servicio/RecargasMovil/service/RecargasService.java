package com.Servicio.RecargasMovil.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.Servicio.RecargasMovil.model.LineaMovil;
import com.Servicio.RecargasMovil.model.OperadorMovil;
import com.Servicio.RecargasMovil.model.CategoriaPaquete;
import com.Servicio.RecargasMovil.model.PaqueteMovil;
import com.Servicio.RecargasMovil.model.RecargaMovil;
import com.Servicio.RecargasMovil.repository.LineaMovilRepository;
import com.Servicio.RecargasMovil.repository.OperadorMovilRepository;
import com.Servicio.RecargasMovil.repository.PaqueteMovilRepository;
import com.Servicio.RecargasMovil.repository.RecargaMovilRepository;
import com.Servicio.RecargasMovil.service.PagosClient.ResultadoPago;

@Service
public class RecargasService {

    private static final Pattern PHONE_NUMBER = Pattern.compile("^\\+?[1-9][0-9]{7,14}$");
    private static final BigDecimal MAX_PACKAGE_PRICE = new BigDecimal("1000000");

    private final OperadorMovilRepository operadores;
    private final PaqueteMovilRepository paquetes;
    private final LineaMovilRepository lineas;
    private final RecargaMovilRepository recargas;
    private final PagosClient pagosClient;

    public RecargasService(
            OperadorMovilRepository operadores,
            PaqueteMovilRepository paquetes,
            LineaMovilRepository lineas,
            RecargaMovilRepository recargas,
            PagosClient pagosClient) {
        this.operadores = operadores;
        this.paquetes = paquetes;
        this.lineas = lineas;
        this.recargas = recargas;
        this.pagosClient = pagosClient;
    }

    public List<OperadorMovil> operadoresActivos() {
        return operadores.findByActivoTrueOrderByNombreAsc();
    }

    public List<PaqueteMovil> paquetesActivos() {
        return operadoresActivos().stream()
                .flatMap(operator -> paquetes
                        .findByOperadorIdAndActivoTrueOrderByPrecioAsc(operator.getId()).stream())
                .filter(mobilePackage -> mobilePackage.getCategoria() != CategoriaPaquete.RECARGA_DINERO)
                .toList();
    }

    public List<LineaMovil> lineasDelUsuario(String usuarioId) {
        return lineas.findByUsuarioIdOrderByCreadaEnDesc(usuarioId);
    }

    public List<RecargaMovil> historialDelUsuario(String usuarioId) {
        return recargas.findByUsuarioIdOrderByFechaDesc(usuarioId);
    }

    public OperadorMovil crearOperador(String nombre) {
        String normalizedName = requiredText(nombre, "El nombre del operador es obligatorio.", 80);
        if (operadores.findByNombreIgnoreCase(normalizedName).isPresent()) {
            throw new IllegalArgumentException("Ya existe un operador con ese nombre.");
        }
        OperadorMovil operator = new OperadorMovil();
        operator.setNombre(normalizedName);
        operator.setActivo(true);
        try {
            return operadores.save(operator);
        } catch (DuplicateKeyException exception) {
            throw new IllegalArgumentException("Ya existe un operador con ese nombre.");
        }
    }

    public PaqueteMovil crearPaquete(
            String operadorId, String nombre, String descripcion, BigDecimal precio) {
        return crearPaquete(operadorId, nombre, descripcion, precio, CategoriaPaquete.PAQUETE);
    }

    public PaqueteMovil crearPaquete(
            String operadorId,
            String nombre,
            String descripcion,
            BigDecimal precio,
            CategoriaPaquete categoria) {
        OperadorMovil operator = operadores.findById(operadorId)
                .filter(OperadorMovil::isActivo)
                .orElseThrow(() -> new IllegalArgumentException("Selecciona un operador activo."));
        PackageDetails details = validatePackageDetails(nombre, descripcion, precio, categoria);
        PaqueteMovil mobilePackage = new PaqueteMovil();
        mobilePackage.setOperadorId(operator.getId());
        mobilePackage.setNombre(details.name());
        mobilePackage.setDescripcion(details.description());
        mobilePackage.setPrecio(details.price());
        mobilePackage.setCategoria(details.category());
        mobilePackage.setActivo(true);
        return paquetes.save(mobilePackage);
    }

    public PaqueteMovil actualizarPaquete(
            String paqueteId,
            String operadorId,
            String nombre,
            String descripcion,
            BigDecimal precio,
            CategoriaPaquete categoria) {
        PaqueteMovil mobilePackage = paquetes.findById(paqueteId)
                .orElseThrow(() -> new IllegalArgumentException("No se encontró el paquete seleccionado."));
        OperadorMovil operator = operadores.findById(operadorId)
                .filter(OperadorMovil::isActivo)
                .orElseThrow(() -> new IllegalArgumentException("Selecciona un operador activo."));
        PackageDetails details = validatePackageDetails(nombre, descripcion, precio, categoria);

        mobilePackage.setOperadorId(operator.getId());
        mobilePackage.setNombre(details.name());
        mobilePackage.setDescripcion(details.description());
        mobilePackage.setPrecio(details.price());
        mobilePackage.setCategoria(details.category());
        return paquetes.save(mobilePackage);
    }

    private static PackageDetails validatePackageDetails(
            String nombre, String descripcion, BigDecimal precio, CategoriaPaquete categoria) {
        String normalizedName = requiredText(nombre, "El nombre del paquete es obligatorio.", 80);
        String normalizedDescription = descripcion == null ? "" : descripcion.trim();
        if (normalizedDescription.length() > 200) {
            throw new IllegalArgumentException("La descripción no puede superar 200 caracteres.");
        }
        if (precio == null || precio.compareTo(BigDecimal.ZERO) <= 0
                || precio.compareTo(MAX_PACKAGE_PRICE) > 0) {
            throw new IllegalArgumentException("El precio debe ser mayor que cero y no superar $1.000.000 COP.");
        }
        if (categoria == null || categoria == CategoriaPaquete.RECARGA_DINERO) {
            throw new IllegalArgumentException("Selecciona una categoría de paquete válida.");
        }
        return new PackageDetails(
                normalizedName,
                normalizedDescription,
                precio.setScale(2, java.math.RoundingMode.HALF_UP),
                categoria);
    }

    public LineaMovil guardarLinea(
            String usuarioId, String operadorId, String telefono, String alias) {
        OperadorMovil operator = operadores.findById(operadorId)
                .filter(OperadorMovil::isActivo)
                .orElseThrow(() -> new IllegalArgumentException("Selecciona un operador activo."));
        String normalizedPhone = telefono == null ? "" : telefono.replaceAll("[\\s()-]", "");
        if (!PHONE_NUMBER.matcher(normalizedPhone).matches()) {
            throw new IllegalArgumentException("Escribe un número móvil válido, con código de país si corresponde.");
        }
        String normalizedAlias = alias == null || alias.isBlank() ? "Mi línea" : alias.trim();
        if (normalizedAlias.length() > 40) {
            throw new IllegalArgumentException("El nombre de la línea no puede superar 40 caracteres.");
        }
        LineaMovil line = new LineaMovil();
        line.setUsuarioId(usuarioId);
        line.setOperadorId(operator.getId());
        line.setTelefono(normalizedPhone);
        line.setAlias(normalizedAlias);
        return lineas.save(line);
    }

    public RecargaMovil solicitarRecarga(
            String usuarioId, String lineaId, String paqueteId, String operacionId, String pagador) {
        String operationKey = requiredText(operacionId, "Identificador de operación inválido.", 100);
        RecargaMovil previous = recargas.findById(operationKey).orElse(null);
        if (previous != null) {
            if (!usuarioId.equals(previous.getUsuarioId())) {
                throw new IllegalArgumentException("La operación no pertenece a tu cuenta.");
            }
            if (!requiereRevision(previous)) {
                return previous;
            }
            return procesar(previous, pagador);
        }

        LineaMovil line = lineas.findById(lineaId)
                .filter(storedLine -> usuarioId.equals(storedLine.getUsuarioId()))
                .orElseThrow(() -> new IllegalArgumentException("Selecciona una línea tuya."));
        OperadorMovil operator = operadores.findById(line.getOperadorId())
                .filter(OperadorMovil::isActivo)
                .orElseThrow(() -> new IllegalArgumentException("El operador de la línea ya no está disponible."));
        PaqueteMovil mobilePackage = paquetes.findById(paqueteId)
                .filter(PaqueteMovil::isActivo)
                .filter(plan -> operator.getId().equals(plan.getOperadorId()))
                .filter(plan -> plan.getCategoria() != CategoriaPaquete.POSTPAGO)
                .orElseThrow(() -> new IllegalArgumentException(
                        "El plan no corresponde a una oferta prepago disponible para esa línea."));

        RecargaMovil recharge = new RecargaMovil();
        recharge.setOperacionId(operationKey);
        recharge.setUsuarioId(usuarioId);
        recharge.setLineaId(line.getId());
        recharge.setTelefono(line.getTelefono());
        recharge.setOperador(operator.getNombre());
        recharge.setPaquete(mobilePackage.getNombre());
        recharge.setMonto(mobilePackage.getPrecio());
        recharge.setEstado(RecargaMovil.STATUS_PROCESSING);
        recharge.setEstadoPago(RecargaMovil.PAYMENT_INITIATING);
        recharge.setPagador(requiredText(pagador, "El correo del pagador no es válido.", 254));
        recharge.setFecha(LocalDateTime.now());
        try {
            recargas.insert(recharge);
        } catch (DuplicateKeyException exception) {
            RecargaMovil existing = recargas.findById(operationKey)
                    .filter(item -> usuarioId.equals(item.getUsuarioId()))
                    .orElseThrow(() -> exception);
            return procesar(existing, pagador);
        }
        return procesar(recharge, pagador);
    }

    public RecargaMovil solicitarRecargaDinero(
            String usuarioId,
            String lineaId,
            BigDecimal monto,
            String operacionId,
            String pagador) {
        String operationKey = requiredText(operacionId, "Identificador de operación inválido.", 100);
        RecargaMovil previous = recargas.findById(operationKey).orElse(null);
        if (previous != null) {
            if (!usuarioId.equals(previous.getUsuarioId())) {
                throw new IllegalArgumentException("La operación no pertenece a tu cuenta.");
            }
            if (!requiereRevision(previous)) {
                return previous;
            }
            return procesar(previous, pagador);
        }

        if (monto == null || monto.scale() > 0
                || monto.compareTo(new BigDecimal("1000")) < 0
                || monto.compareTo(new BigDecimal("100000")) > 0
                || monto.remainder(new BigDecimal("1000")).compareTo(BigDecimal.ZERO) != 0) {
            throw new IllegalArgumentException(
                    "El monto debe estar entre $1.000 y $100.000 COP, en incrementos de $1.000.");
        }

        LineaMovil line = lineas.findById(lineaId)
                .filter(storedLine -> usuarioId.equals(storedLine.getUsuarioId()))
                .orElseThrow(() -> new IllegalArgumentException("Selecciona una línea tuya."));
        OperadorMovil operator = operadores.findById(line.getOperadorId())
                .filter(OperadorMovil::isActivo)
                .orElseThrow(() -> new IllegalArgumentException(
                        "El operador de la línea ya no está disponible."));

        RecargaMovil recharge = new RecargaMovil();
        recharge.setOperacionId(operationKey);
        recharge.setUsuarioId(usuarioId);
        recharge.setLineaId(line.getId());
        recharge.setTelefono(line.getTelefono());
        recharge.setOperador(operator.getNombre());
        recharge.setPaquete("Recarga de saldo $" + monto.toPlainString());
        recharge.setMonto(monto.setScale(2, java.math.RoundingMode.UNNECESSARY));
        recharge.setEstado(RecargaMovil.STATUS_PROCESSING);
        recharge.setEstadoPago(RecargaMovil.PAYMENT_INITIATING);
        recharge.setPagador(requiredText(pagador, "El correo del pagador no es válido.", 254));
        recharge.setFecha(LocalDateTime.now());
        try {
            recargas.insert(recharge);
        } catch (DuplicateKeyException exception) {
            RecargaMovil existing = recargas.findById(operationKey)
                    .filter(item -> usuarioId.equals(item.getUsuarioId()))
                    .orElseThrow(() -> exception);
            return procesar(existing, pagador);
        }
        return procesar(recharge, pagador);
    }

    public RecargaMovil reintentarRecarga(String usuarioId, String operationId, String pagador) {
        RecargaMovil recharge = recargas.findById(operationId)
                .filter(item -> usuarioId.equals(item.getUsuarioId()))
                .orElseThrow(() -> new IllegalArgumentException("No se encontró esa recarga."));
        if (!requiereRevision(recharge)) {
            return recharge;
        }
        return procesar(recharge, pagador);
    }

    private RecargaMovil procesar(RecargaMovil recharge, String pagador) {
        if (!StringUtils.hasText(recharge.getPagador())) {
            recharge.setPagador(requiredText(pagador, "El correo del pagador no es válido.", 254));
        }
        if (!StringUtils.hasText(recharge.getIdPagoProveedor())
                && !RecargaMovil.PAYMENT_INITIATING.equals(recharge.getEstadoPago())) {
            throw new OperacionPendienteAnteriorException();
        }
        ResultadoPago resultado = StringUtils.hasText(recharge.getIdPagoProveedor())
                ? pagosClient.consultar(recharge.getIdPagoProveedor())
                : pagosClient.iniciar(
                        recharge.getOperacionId(),
                        recharge.getMonto(),
                        descripcionPago(recharge),
                        recharge.getPagador());
        recharge.setIdPagoProveedor(resultado.idProveedor());
        recharge.setEstadoPago(resultado.estado());
        recharge.setEstado(switch (resultado.estado()) {
            case "PENDIENTE" -> RecargaMovil.STATUS_PAYMENT_PENDING;
            case "APROBADO" -> RecargaMovil.STATUS_SIMULATED;
            case "RECHAZADO", "CANCELADO" -> RecargaMovil.STATUS_REJECTED;
            default -> throw new IllegalStateException(
                    "Pagos devolvió un estado de pago no reconocido: " + resultado.estado());
        });
        return recargas.save(recharge);
    }

    private static boolean requiereRevision(RecargaMovil recharge) {
        return RecargaMovil.STATUS_PROCESSING.equals(recharge.getEstado())
                || RecargaMovil.STATUS_PAYMENT_PENDING.equals(recharge.getEstado());
    }

    private static String descripcionPago(RecargaMovil recharge) {
        return "Recarga móvil " + recharge.getPaquete() + " · " + recharge.getOperador()
                + " para " + ocultarTelefono(recharge.getTelefono());
    }

    private static String requiredText(String value, String error, int maxLength) {
        if (!StringUtils.hasText(value) || value.trim().length() > maxLength) {
            throw new IllegalArgumentException(error);
        }
        return value.trim();
    }

    private static String ocultarTelefono(String telefono) {
        if (telefono == null || telefono.length() <= 4) {
            return "****";
        }
        return "****" + telefono.substring(telefono.length() - 4);
    }

    public static class OperacionPendienteAnteriorException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public OperacionPendienteAnteriorException() {
            super("Esta operación pendiente pertenece al flujo anterior de Billetera. "
                    + "Verifica su movimiento antes de iniciar otro pago.");
        }
    }

    private record PackageDetails(String name, String description, BigDecimal price, CategoriaPaquete category) {
    }
}

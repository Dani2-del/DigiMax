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
import com.Servicio.RecargasMovil.model.PaqueteMovil;
import com.Servicio.RecargasMovil.model.RecargaMovil;
import com.Servicio.RecargasMovil.repository.LineaMovilRepository;
import com.Servicio.RecargasMovil.repository.OperadorMovilRepository;
import com.Servicio.RecargasMovil.repository.PaqueteMovilRepository;
import com.Servicio.RecargasMovil.repository.RecargaMovilRepository;
import com.Servicio.RecargasMovil.service.WalletClient.Wallet;

@Service
public class RecargasService {

    private static final Pattern PHONE_NUMBER = Pattern.compile("^\\+?[1-9][0-9]{7,14}$");
    private static final BigDecimal MAX_PACKAGE_PRICE = new BigDecimal("1000000");

    private final OperadorMovilRepository operadores;
    private final PaqueteMovilRepository paquetes;
    private final LineaMovilRepository lineas;
    private final RecargaMovilRepository recargas;
    private final WalletClient walletClient;

    public RecargasService(
            OperadorMovilRepository operadores,
            PaqueteMovilRepository paquetes,
            LineaMovilRepository lineas,
            RecargaMovilRepository recargas,
            WalletClient walletClient) {
        this.operadores = operadores;
        this.paquetes = paquetes;
        this.lineas = lineas;
        this.recargas = recargas;
        this.walletClient = walletClient;
    }

    public List<OperadorMovil> operadoresActivos() {
        return operadores.findByActivoTrueOrderByNombreAsc();
    }

    public List<PaqueteMovil> paquetesActivos() {
        return operadoresActivos().stream()
                .flatMap(operator -> paquetes
                        .findByOperadorIdAndActivoTrueOrderByPrecioAsc(operator.getId()).stream())
                .toList();
    }

    public List<LineaMovil> lineasDelUsuario(String usuarioId) {
        return lineas.findByUsuarioIdOrderByCreadaEnDesc(usuarioId);
    }

    public List<RecargaMovil> historialDelUsuario(String usuarioId) {
        return recargas.findTop20ByUsuarioIdOrderByFechaDesc(usuarioId);
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
        OperadorMovil operator = operadores.findById(operadorId)
                .filter(OperadorMovil::isActivo)
                .orElseThrow(() -> new IllegalArgumentException("Selecciona un operador activo."));
        String normalizedName = requiredText(nombre, "El nombre del paquete es obligatorio.", 80);
        String normalizedDescription = descripcion == null ? "" : descripcion.trim();
        if (normalizedDescription.length() > 200) {
            throw new IllegalArgumentException("La descripción no puede superar 200 caracteres.");
        }
        if (precio == null || precio.compareTo(BigDecimal.ZERO) <= 0
                || precio.compareTo(MAX_PACKAGE_PRICE) > 0) {
            throw new IllegalArgumentException("El precio debe ser mayor que cero y no superar $1.000.000 COP.");
        }
        PaqueteMovil mobilePackage = new PaqueteMovil();
        mobilePackage.setOperadorId(operator.getId());
        mobilePackage.setNombre(normalizedName);
        mobilePackage.setDescripcion(normalizedDescription);
        mobilePackage.setPrecio(precio.setScale(2, java.math.RoundingMode.HALF_UP));
        mobilePackage.setActivo(true);
        return paquetes.save(mobilePackage);
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
            String usuarioId, String lineaId, String paqueteId, String operacionId, String token) {
        String operationKey = requiredText(operacionId, "Identificador de operación inválido.", 100);
        RecargaMovil previous = recargas.findById(operationKey).orElse(null);
        if (previous != null) {
            if (!usuarioId.equals(previous.getUsuarioId())) {
                throw new IllegalArgumentException("La operación no pertenece a tu cuenta.");
            }
            if (!RecargaMovil.STATUS_PROCESSING.equals(previous.getEstado())) {
                return previous;
            }
            return procesar(previous, token);
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
                .orElseThrow(() -> new IllegalArgumentException("El paquete no corresponde a esa línea."));

        RecargaMovil recharge = new RecargaMovil();
        recharge.setOperacionId(operationKey);
        recharge.setUsuarioId(usuarioId);
        recharge.setLineaId(line.getId());
        recharge.setTelefono(line.getTelefono());
        recharge.setOperador(operator.getNombre());
        recharge.setPaquete(mobilePackage.getNombre());
        recharge.setMonto(mobilePackage.getPrecio());
        recharge.setEstado(RecargaMovil.STATUS_PROCESSING);
        recharge.setFecha(LocalDateTime.now());
        try {
            recargas.insert(recharge);
        } catch (DuplicateKeyException exception) {
            RecargaMovil existing = recargas.findById(operationKey)
                    .filter(item -> usuarioId.equals(item.getUsuarioId()))
                    .orElseThrow(() -> exception);
            return procesar(existing, token);
        }
        return procesar(recharge, token);
    }

    public RecargaMovil reintentarRecarga(String usuarioId, String operationId, String token) {
        RecargaMovil recharge = recargas.findById(operationId)
                .filter(item -> usuarioId.equals(item.getUsuarioId()))
                .orElseThrow(() -> new IllegalArgumentException("No se encontró esa recarga."));
        if (!RecargaMovil.STATUS_PROCESSING.equals(recharge.getEstado())) {
            return recharge;
        }
        return procesar(recharge, token);
    }

    private RecargaMovil procesar(RecargaMovil recharge, String token) {
        try {
            Wallet wallet = walletClient.debit(token, recharge.getOperacionId(),
                    recharge.getMonto(), "Paquete " + recharge.getOperador() + " para "
                            + ocultarTelefono(recharge.getTelefono()));
            if (wallet == null) {
                throw new IllegalStateException("Billetera no devolvió el resultado del débito.");
            }
            recharge.setEstado(RecargaMovil.STATUS_SIMULATED);
            return recargas.save(recharge);
        } catch (InsufficientWalletBalanceException exception) {
            recharge.setEstado(RecargaMovil.STATUS_REJECTED);
            recargas.save(recharge);
            throw exception;
        }
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
}

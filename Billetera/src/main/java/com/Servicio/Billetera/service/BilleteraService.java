package com.Servicio.Billetera.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import com.Servicio.Billetera.model.BilleteraUniversal;
import com.Servicio.Billetera.model.MovimientoBilletera;
import com.Servicio.Billetera.repository.BilleteraUniversalRepository;

@Service
public class BilleteraService {

    private static final int MAX_MOVIMIENTOS_GUARDADOS = 500;
    private static final int MAX_INTENTOS_NUMERO_TARJETA = 3;
    private static final BigDecimal MONTO_MAXIMO_SIMULADO = new BigDecimal("1000000");

    private final BilleteraUniversalRepository billeteras;
    private final MongoOperations mongoOperations;

    public BilleteraService(
            BilleteraUniversalRepository billeteras,
            MongoOperations mongoOperations) {
        this.billeteras = billeteras;
        this.mongoOperations = mongoOperations;
    }

    public BilleteraUniversal obtener(String usuarioId) {
        if (usuarioId == null || usuarioId.isBlank()) {
            throw new IllegalArgumentException("Se requiere una identidad válida para obtener la billetera.");
        }
        return billeteras.findById(usuarioId).orElseGet(() -> crearBilletera(usuarioId));
    }

    public BilleteraUniversal abonarSimulado(
            String usuarioId, BigDecimal monto, String operacionId, String metodoPago) {
        validarMovimiento(monto, operacionId);
        String descripcion = switch (metodoPago == null ? "" : metodoPago) {
            case "PSE" -> "Carga simulada con PSE";
            case "TARJETA_DEBITO" -> "Carga simulada con tarjeta débito";
            default -> throw new IllegalArgumentException("Selecciona PSE o tarjeta débito.");
        };
        return aplicarMovimiento(usuarioId, monto, operacionId, "ABONO", descripcion, false);
    }

    public BilleteraUniversal debitar(
            String usuarioId, BigDecimal monto, String operacionId, String descripcion) {
        validarMovimiento(monto, operacionId);
        return aplicarMovimiento(usuarioId, monto, operacionId, "CONSUMO", descripcion, true);
    }

    public List<MovimientoBilletera> historial(String usuarioId) {
        return obtener(usuarioId).getMovimientos().stream()
                .sorted(Comparator.comparing(MovimientoBilletera::getFecha,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    private BilleteraUniversal crearBilletera(String usuarioId) {
        for (int attempt = 0; attempt < MAX_INTENTOS_NUMERO_TARJETA; attempt++) {
            BilleteraUniversal wallet = new BilleteraUniversal();
            wallet.setUsuarioId(usuarioId);
            wallet.setNumeroTarjeta("UNI-" + UUID.randomUUID().toString()
                    .replace("-", "").substring(0, 12).toUpperCase());
            try {
                return billeteras.insert(wallet);
            } catch (DuplicateKeyException exception) {
                var existing = billeteras.findById(usuarioId);
                if (existing.isPresent()) {
                    return existing.get();
                }
                if (attempt + 1 == MAX_INTENTOS_NUMERO_TARJETA) {
                    throw exception;
                }
            }
        }
        throw new IllegalStateException("No se pudo emitir la tarjeta universal.");
    }

    private BilleteraUniversal aplicarMovimiento(
            String usuarioId, BigDecimal monto, String operacionId,
            String tipo, String descripcion, boolean debito) {
        BilleteraUniversal wallet = obtener(usuarioId);
        String safeDescription = descripcion == null || descripcion.isBlank()
                ? (debito ? "Consumo simulado" : "Abono simulado")
                : descripcion.trim();
        MovimientoBilletera movement = new MovimientoBilletera(
                operacionId.trim(), tipo, safeDescription, monto, LocalDateTime.now());

        Criteria criteria = Criteria.where("_id").is(usuarioId)
                .and("movimientos.operacionId").ne(movement.getOperacionId());
        if (debito) {
            criteria.and("saldo").gte(monto);
        }
        Update update = new Update()
                .inc("saldo", debito ? monto.negate() : monto)
                .push("movimientos")
                .atPosition(0)
                .slice(MAX_MOVIMIENTOS_GUARDADOS)
                .each(movement);
        BilleteraUniversal updated = mongoOperations.findAndModify(
                Query.query(criteria),
                update,
                FindAndModifyOptions.options().returnNew(true),
                BilleteraUniversal.class);
        if (updated != null) {
            return updated;
        }

        BilleteraUniversal current = billeteras.findById(usuarioId).orElse(wallet);
        MovimientoBilletera previous = current.getMovimientos().stream()
                .filter(item -> movement.getOperacionId().equals(item.getOperacionId()))
                .findFirst()
                .orElse(null);
        if (previous != null) {
            if (previous.getMonto().compareTo(monto) != 0 || !tipo.equals(previous.getTipo())) {
                throw new IllegalArgumentException("El identificador de operación ya se usó con otros datos.");
            }
            return current;
        }
        if (debito) {
            throw new SaldoInsuficienteException();
        }
        throw new IllegalStateException("No se pudo actualizar el saldo de la tarjeta universal.");
    }

    private static void validarMovimiento(BigDecimal monto, String operacionId) {
        if (monto == null || monto.compareTo(BigDecimal.ZERO) <= 0
                || monto.compareTo(MONTO_MAXIMO_SIMULADO) > 0) {
            throw new IllegalArgumentException("El monto debe ser mayor que cero y no superar $1.000.000 COP.");
        }
        if (operacionId == null || operacionId.isBlank() || operacionId.length() > 100) {
            throw new IllegalArgumentException("Se requiere un identificador de operación válido.");
        }
    }
}

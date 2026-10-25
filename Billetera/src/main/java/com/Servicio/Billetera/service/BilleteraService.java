package com.Servicio.Billetera.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import com.Servicio.Billetera.model.BilleteraUniversal;
import com.Servicio.Billetera.model.DisenoTarjeta;
import com.Servicio.Billetera.model.EstadoTarjeta;
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
        return buscar(usuarioId).orElseThrow(WalletNotFoundException::new);
    }

    public Optional<BilleteraUniversal> buscar(String usuarioId) {
        if (usuarioId == null || usuarioId.isBlank()) {
            throw new IllegalArgumentException("Se requiere una identidad válida para obtener la billetera.");
        }
        return billeteras.findById(usuarioId)
                .filter(BilleteraUniversal::isActivadaPorUsuario)
                .filter(wallet -> wallet.getEstado() != EstadoTarjeta.DESACTIVADA);
    }

    public BilleteraUniversal crear(String usuarioId) {
        if (usuarioId == null || usuarioId.isBlank()) {
            throw new IllegalArgumentException("Se requiere una identidad válida para emitir la billetera.");
        }
        BilleteraUniversal existing = billeteras.findById(usuarioId).orElse(null);
        if (existing != null) {
            if (existing.getEstado() == EstadoTarjeta.CONGELADA) {
                return existing;
            }
            if (existing.getEstado() == EstadoTarjeta.DESACTIVADA) {
                String newCardNumber = generarNumeroTarjeta();
                BilleteraUniversal reissued = mongoOperations.findAndModify(
                        Query.query(Criteria.where("_id").is(usuarioId)
                                .and("estado").is(EstadoTarjeta.DESACTIVADA.name())
                                .and("tarjetasDesactivadas.saldoTransferido").is(false)),
                        new Update().set("numeroTarjeta", newCardNumber)
                                .set("estado", EstadoTarjeta.ACTIVA)
                                .set("diseno", DisenoTarjeta.OCEANO)
                                .set("activadaPorUsuario", true)
                                .set("tarjetasDesactivadas.$.saldoTransferido", true),
                        FindAndModifyOptions.options().returnNew(true), BilleteraUniversal.class);
                if (reissued != null) {
                    return reissued;
                }
                BilleteraUniversal legacyReissued = mongoOperations.findAndModify(
                        Query.query(Criteria.where("_id").is(usuarioId)
                                .and("estado").is(EstadoTarjeta.DESACTIVADA.name())),
                        new Update().set("numeroTarjeta", newCardNumber)
                                .set("estado", EstadoTarjeta.ACTIVA)
                                .set("diseno", DisenoTarjeta.OCEANO)
                                .set("activadaPorUsuario", true),
                        FindAndModifyOptions.options().returnNew(true), BilleteraUniversal.class);
                if (legacyReissued != null) {
                    return legacyReissued;
                }
                return billeteras.findById(usuarioId).orElseThrow(WalletNotFoundException::new);
            }
            if (!existing.isActivadaPorUsuario()) {
                BilleteraUniversal accepted = mongoOperations.findAndModify(
                        Query.query(Criteria.where("_id").is(usuarioId)
                                .and("activadaPorUsuario").is(false)),
                        new Update().set("activadaPorUsuario", true),
                        FindAndModifyOptions.options().returnNew(true), BilleteraUniversal.class);
                if (accepted != null) {
                    return accepted;
                }
                BilleteraUniversal current = billeteras.findById(usuarioId)
                        .orElseThrow(WalletNotFoundException::new);
                if (current.getEstado() == EstadoTarjeta.DESACTIVADA) {
                    return crear(usuarioId);
                }
                if (!current.isActivadaPorUsuario()) {
                    current.setActivadaPorUsuario(true);
                    return billeteras.save(current);
                }
                return current;
            }
            return existing;
        }
        return crearBilletera(usuarioId);
    }

    public BilleteraUniversal congelar(String usuarioId) {
        obtener(usuarioId);
        BilleteraUniversal wallet = mongoOperations.findAndModify(
                Query.query(Criteria.where("_id").is(usuarioId)
                        .and("estado").nin(EstadoTarjeta.DESACTIVADA.name())),
                new Update().set("estado", EstadoTarjeta.CONGELADA),
                FindAndModifyOptions.options().returnNew(true), BilleteraUniversal.class);
        if (wallet != null) {
            return wallet;
        }
        BilleteraUniversal current = billeteras.findById(usuarioId).orElseThrow(WalletNotFoundException::new);
        if (current.getEstado() == EstadoTarjeta.CONGELADA) {
            return current;
        }
        throw new WalletNotFoundException();
    }

    public BilleteraUniversal descongelar(String usuarioId) {
        obtener(usuarioId);
        BilleteraUniversal wallet = mongoOperations.findAndModify(
                Query.query(Criteria.where("_id").is(usuarioId)
                        .and("estado").is(EstadoTarjeta.CONGELADA.name())),
                new Update().set("estado", EstadoTarjeta.ACTIVA),
                FindAndModifyOptions.options().returnNew(true), BilleteraUniversal.class);
        if (wallet != null) {
            return wallet;
        }
        BilleteraUniversal current = billeteras.findById(usuarioId).orElseThrow(WalletNotFoundException::new);
        if (current.getEstado() == EstadoTarjeta.ACTIVA) {
            return current;
        }
        throw new WalletNotFoundException();
    }

    public BilleteraUniversal desactivar(String usuarioId) {
        obtener(usuarioId);
        BilleteraUniversal deactivated = mongoOperations.findAndModify(
                Query.query(Criteria.where("_id").is(usuarioId)
                        .and("activadaPorUsuario").is(true)
                        .and("estado").nin(EstadoTarjeta.DESACTIVADA.name())),
                new Update().set("estado", EstadoTarjeta.DESACTIVADA),
                FindAndModifyOptions.options().returnNew(true),
                BilleteraUniversal.class);
        if (deactivated == null) {
            throw new WalletNotFoundException();
        }
        BilleteraUniversal.TarjetaDesactivada archived =
                new BilleteraUniversal.TarjetaDesactivada();
        archived.setNumeroTarjeta(deactivated.getNumeroTarjeta());
        archived.setSaldoAlDesactivar(deactivated.getSaldo());
        archived.setDiseno(deactivated.getDiseno());
        archived.setDesactivadaEn(LocalDateTime.now());
        archived.setSaldoTransferido(false);
        BilleteraUniversal archivedWallet = mongoOperations.findAndModify(
                Query.query(Criteria.where("_id").is(usuarioId)
                        .and("estado").is(EstadoTarjeta.DESACTIVADA.name())),
                new Update().push("tarjetasDesactivadas", archived),
                FindAndModifyOptions.options().returnNew(true),
                BilleteraUniversal.class);
        if (archivedWallet == null) {
            throw new IllegalStateException("No se pudo guardar el historial de la tarjeta desactivada.");
        }
        return archivedWallet;
    }

    public BilleteraUniversal cambiarDiseno(String usuarioId, DisenoTarjeta diseno) {
        if (diseno == null) {
            throw new IllegalArgumentException("Selecciona un diseño de tarjeta válido.");
        }
        obtener(usuarioId);
        BilleteraUniversal wallet = mongoOperations.findAndModify(
                Query.query(Criteria.where("_id").is(usuarioId)
                        .and("estado").nin(EstadoTarjeta.DESACTIVADA.name())),
                new Update().set("diseno", diseno),
                FindAndModifyOptions.options().returnNew(true), BilleteraUniversal.class);
        if (wallet == null) {
            throw new WalletNotFoundException();
        }
        return wallet;
    }

    public List<TarjetaDesactivadaAdmin> tarjetasDesactivadasAdmin() {
        return billeteras.findAll().stream()
                .flatMap(wallet -> wallet.getTarjetasDesactivadas().stream()
                        .map(card -> new TarjetaDesactivadaAdmin(
                                wallet.getUsuarioId(), card.getNumeroTarjeta(),
                                card.getSaldoAlDesactivar(), card.getDiseno(),
                                card.getDesactivadaEn(), card.isSaldoTransferido())))
                .sorted(Comparator.comparing(TarjetaDesactivadaAdmin::desactivadaEn,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    public List<BilleteraUniversal> billeterasAdministrativas() {
        return billeteras.findAll().stream()
                .sorted(Comparator.comparing(BilleteraUniversal::getUsuarioId,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    public BilleteraUniversal billeteraAdministrativa(String usuarioId) {
        if (usuarioId == null || usuarioId.isBlank()) {
            throw new IllegalArgumentException("Se requiere una identidad válida para consultar la billetera.");
        }
        return billeteras.findById(usuarioId).orElseThrow(WalletNotFoundException::new);
    }

    public BilleteraUniversal ajustarSaldoAdministrativamente(
            String usuarioId, String actor, BigDecimal monto, boolean abonar,
            String motivo, String operacionId) {
        if (monto == null || monto.scale() > 2 || monto.compareTo(BigDecimal.ZERO) <= 0
                || monto.compareTo(MONTO_MAXIMO_SIMULADO) > 0) {
            throw new IllegalArgumentException(
                    "El monto debe ser mayor que cero, tener máximo dos decimales y no superar $1.000.000 COP.");
        }
        String safeReason = validarMotivo(motivo);
        String operationKey = validarOperacionId(operacionId);
        BilleteraUniversal initial = billeteras.findById(usuarioId)
                .filter(BilleteraUniversal::isActivadaPorUsuario)
                .filter(wallet -> wallet.getEstado() != EstadoTarjeta.DESACTIVADA)
                .orElseThrow(WalletNotFoundException::new);
        String type = abonar ? "AJUSTE_ADMIN" : "RETIRO_ADMIN";
        MovimientoBilletera movement = new MovimientoBilletera(operationKey, type,
                "Ajuste por " + actor + ": " + safeReason, monto, LocalDateTime.now());
        movement.setServicio("Soporte Billetera");
        Criteria criteria = Criteria.where("_id").is(usuarioId)
                .and("activadaPorUsuario").is(true)
                .and("movimientos.operacionId").ne(operationKey)
                .and("estado").nin(EstadoTarjeta.DESACTIVADA.name());
        if (!abonar) {
            criteria.and("saldo").gte(monto);
        }
        Update update = new Update()
                .inc("saldo", abonar ? monto : monto.negate())
                .push("movimientos").atPosition(0).slice(MAX_MOVIMIENTOS_GUARDADOS).each(movement);
        BilleteraUniversal updated = mongoOperations.findAndModify(
                Query.query(criteria), update, FindAndModifyOptions.options().returnNew(true),
                BilleteraUniversal.class);
        if (updated != null) {
            return updated;
        }

        BilleteraUniversal current = billeteras.findById(usuarioId)
                .orElseThrow(WalletNotFoundException::new);
        if (current.getEstado() == EstadoTarjeta.DESACTIVADA || !current.isActivadaPorUsuario()) {
            throw new WalletNotFoundException();
        }
        MovimientoBilletera previous = current.getMovimientos().stream()
                .filter(item -> operationKey.equals(item.getOperacionId()))
                .findFirst().orElse(null);
        if (previous != null) {
            if (previous.getMonto().compareTo(monto) != 0 || !type.equals(previous.getTipo())) {
                throw new IllegalArgumentException("El identificador de operación ya se usó con otros datos.");
            }
            return current;
        }
        if (!abonar && current.getSaldo().compareTo(monto) < 0) {
            throw new SaldoInsuficienteException();
        }
        throw new IllegalStateException("No se pudo aplicar el ajuste administrativo.");
    }

    public BilleteraUniversal cambiarEstadoAdministrativamente(
            String usuarioId, EstadoTarjeta estado, String actor, String motivo) {
        if (estado != EstadoTarjeta.ACTIVA && estado != EstadoTarjeta.CONGELADA) {
            throw new IllegalArgumentException("El estado solicitado no está disponible para soporte.");
        }
        String safeReason = validarMotivo(motivo);
        BilleteraUniversal initial = billeteras.findById(usuarioId)
                .filter(BilleteraUniversal::isActivadaPorUsuario)
                .orElseThrow(WalletNotFoundException::new);
        if (initial.getEstado() == EstadoTarjeta.DESACTIVADA) {
            throw new WalletNotFoundException();
        }
        if (initial.getEstado() == estado) {
            return initial;
        }
        MovimientoBilletera movement = new MovimientoBilletera(
                UUID.randomUUID().toString(), "CAMBIO_ESTADO",
                "Estado cambiado a " + estado + " por " + actor + ": " + safeReason,
                BigDecimal.ZERO, LocalDateTime.now());
        movement.setServicio("Soporte Billetera");
        BilleteraUniversal updated = mongoOperations.findAndModify(
                Query.query(Criteria.where("_id").is(usuarioId)
                        .and("activadaPorUsuario").is(true)
                        .and("estado").nin(EstadoTarjeta.DESACTIVADA.name(), estado.name())),
                new Update().set("estado", estado)
                        .push("movimientos").atPosition(0).slice(MAX_MOVIMIENTOS_GUARDADOS).each(movement),
                FindAndModifyOptions.options().returnNew(true), BilleteraUniversal.class);
        if (updated != null) {
            return updated;
        }
        BilleteraUniversal current = billeteras.findById(usuarioId)
                .orElseThrow(WalletNotFoundException::new);
        if (current.getEstado() == estado) {
            return current;
        }
        throw new WalletNotFoundException();
    }

    public BilleteraUniversal abonarSimulado(
            String usuarioId, BigDecimal monto, String operacionId, String metodoPago) {
        validarMovimiento(monto, operacionId);
        String descripcion = switch (metodoPago == null ? "" : metodoPago) {
            case "PSE" -> "Carga simulada con PSE";
            case "TARJETA_DEBITO" -> "Carga simulada con tarjeta débito";
            case "TARJETA_CREDITO_DEBITO" -> "Carga simulada con tarjeta crédito/débito";
            default -> throw new IllegalArgumentException("Selecciona PSE o tarjeta crédito/débito.");
        };
        return aplicarMovimiento(usuarioId, monto, operacionId, "ABONO", descripcion, "Billetera", false);
    }

    public BilleteraUniversal debitar(
            String usuarioId, BigDecimal monto, String operacionId, String descripcion) {
        validarMovimiento(monto, operacionId);
        String servicio = new MovimientoBilletera(null, "CONSUMO", descripcion, monto, null).getServicio();
        return aplicarMovimiento(usuarioId, monto, operacionId, "CONSUMO", descripcion, servicio, true);
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
            wallet.setActivadaPorUsuario(true);
            wallet.setNumeroTarjeta(generarNumeroTarjeta());
            try {
                return billeteras.insert(wallet);
            } catch (DuplicateKeyException exception) {
                var existing = billeteras.findById(usuarioId);
                if (existing.isPresent()) {
                    BilleteraUniversal walletExists = existing.get();
                    if (walletExists.getEstado() == EstadoTarjeta.DESACTIVADA) {
                        return crear(usuarioId);
                    }
                    if (!walletExists.isActivadaPorUsuario()) {
                        walletExists.setActivadaPorUsuario(true);
                        return billeteras.save(walletExists);
                    }
                    return walletExists;
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
            String tipo, String descripcion, String servicio, boolean debito) {
        BilleteraUniversal wallet = obtener(usuarioId);
        if (wallet.getEstado() == EstadoTarjeta.CONGELADA) {
            throw new WalletFrozenException();
        }
        String safeDescription = descripcion == null || descripcion.isBlank()
                ? (debito ? "Consumo simulado" : "Abono simulado")
                : descripcion.trim();
        MovimientoBilletera movement = new MovimientoBilletera(
                operacionId.trim(), tipo, safeDescription, monto, LocalDateTime.now());
        movement.setServicio(servicio);

        Criteria criteria = Criteria.where("_id").is(usuarioId)
                .and("movimientos.operacionId").ne(movement.getOperacionId())
                .and("estado").nin(
                        EstadoTarjeta.CONGELADA.name(), EstadoTarjeta.DESACTIVADA.name());
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
        if (current.getEstado() == EstadoTarjeta.CONGELADA) {
            throw new WalletFrozenException();
        }
        if (current.getEstado() == EstadoTarjeta.DESACTIVADA) {
            throw new WalletNotFoundException();
        }
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

    private static String validarMotivo(String motivo) {
        if (motivo == null || motivo.isBlank() || motivo.trim().length() < 5
                || motivo.trim().length() > 200) {
            throw new IllegalArgumentException("Escribe un motivo de soporte de 5 a 200 caracteres.");
        }
        return motivo.trim();
    }

    private static String validarOperacionId(String operacionId) {
        if (operacionId == null || operacionId.isBlank() || operacionId.length() > 100) {
            throw new IllegalArgumentException("Se requiere un identificador de operación válido.");
        }
        return operacionId.trim();
    }

    private static String generarNumeroTarjeta() {
        return "UNI-" + UUID.randomUUID().toString()
                .replace("-", "").substring(0, 12).toUpperCase();
    }

    public record TarjetaDesactivadaAdmin(
            String usuarioId,
            String numeroTarjeta,
            BigDecimal saldoAlDesactivar,
            DisenoTarjeta diseno,
            LocalDateTime desactivadaEn,
            boolean saldoTransferido) {
    }

    public static class WalletNotFoundException extends RuntimeException {
        public WalletNotFoundException() {
            super("No existe una tarjeta universal para la cuenta solicitada.");
        }
    }

    public static class WalletFrozenException extends RuntimeException {
        public WalletFrozenException() {
            super("La tarjeta Digitrans está congelada y no puede realizar transacciones.");
        }
    }
}

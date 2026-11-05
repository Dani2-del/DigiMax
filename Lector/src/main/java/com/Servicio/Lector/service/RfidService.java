package com.Servicio.Lector.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import com.Servicio.Lector.model.TarjetaRfid;
import com.Servicio.Lector.repository.TarjetaRfidRepository;

@Service
public class RfidService {

    private static final Pattern UID_PATTERN = Pattern.compile("^[A-Z0-9]{4,20}$");
    private static final long READ_COOLDOWN_MILLIS = 2_000;

    private final TarjetaRfidRepository tarjetas;
    private final MongoOperations mongoOperations;
    private final BilleteraClient billeteraClient;
    private final BigDecimal tarifaTranscaribe;
    private final BigDecimal tarifaTransmilenio;
    private final BigDecimal tarifaTransmetro;
    private final ConcurrentHashMap<String, Long> lastReads = new ConcurrentHashMap<>();

    public RfidService(
            TarjetaRfidRepository tarjetas,
            MongoOperations mongoOperations,
            BilleteraClient billeteraClient,
            @Value("${app.lector.tarifa.transcaribe:3900}") BigDecimal tarifaTranscaribe,
            @Value("${app.lector.tarifa.transmilenio:3550}") BigDecimal tarifaTransmilenio,
            @Value("${app.lector.tarifa.transmetro:3700}") BigDecimal tarifaTransmetro) {
        this.tarjetas = tarjetas;
        this.mongoOperations = mongoOperations;
        this.billeteraClient = billeteraClient;
        this.tarifaTranscaribe = tarifaTranscaribe;
        this.tarifaTransmilenio = tarifaTransmilenio;
        this.tarifaTransmetro = tarifaTransmetro;
    }

    /** Registers an unowned physical card in Lector's inventory. */
    public TarjetaRfid registrarDisponible(String rawUid) {
        String uid = normalize(rawUid);
        TarjetaRfid current = tarjetas.findById(uid).orElse(null);
        if (current != null) {
            if (!current.isDisponible()) throw new TagAlreadyLinkedException();
            current.setActiva(true);
            return tarjetas.save(current);
        }
        TarjetaRfid card = new TarjetaRfid();
        card.setUid(uid);
        card.setUsuarioId(null);
        card.setActiva(true);
        card.setVinculadaEn(LocalDateTime.now());
        return tarjetas.save(card);
    }

    /** Atomically assigns one inventory card to the requesting wallet user. */
    public synchronized TarjetaRfid reclamar(String userId) {
        if (userId == null || userId.isBlank()) throw new IllegalArgumentException("Falta el ID de usuario.");
        String id = userId.trim();
        TarjetaRfid existing = tarjetas.findFirstByUsuarioId(id).orElse(null);
        if (existing != null && existing.isActiva()) return existing;
        Query available = Query.query(Criteria.where("usuarioId").is(null).and("activa").is(true))
                .with(Sort.by(Sort.Direction.ASC, "vinculadaEn"));
        TarjetaRfid claimed = mongoOperations.findAndModify(available,
                new Update().set("usuarioId", id).set("vinculadaEn", LocalDateTime.now()),
                FindAndModifyOptions.options().returnNew(true), TarjetaRfid.class);
        if (claimed == null) throw new NoAvailableCardException();
        return claimed;
    }

    public TarjetaRfid perfil(String userId) {
        return tarjetas.findFirstByUsuarioId(userId).filter(TarjetaRfid::isActiva)
                .orElseThrow(UnlinkedTagException::new);
    }

    public void desvincular(String userId) {
        TarjetaRfid card = perfil(userId);
        card.setUsuarioId(null);
        card.setVinculadaEn(LocalDateTime.now());
        tarjetas.save(card);
    }

    public List<TarjetaRfid> listarTarjetas() {
        return tarjetas.findAllByOrderByVinculadaEnDesc();
    }

    public ScanResult cobrar(String rawUid) {
        return cobrar(rawUid, "TRANSCARIBE", UUID.randomUUID().toString(), null);
    }

    public ScanResult cobrar(String rawUid, String sistema, String operationId, String expectedUserId) {
        return cobrar(rawUid, sistema, operationId, expectedUserId, null);
    }

    public ScanResult cobrar(String rawUid, String sistema, String operationId, String expectedUserId,
            BigDecimal requestedFare) {
        String uid = normalize(rawUid);
        long now = System.currentTimeMillis();
        Long previous = lastReads.put(uid, now);
        if (previous != null && now - previous < READ_COOLDOWN_MILLIS) {
            throw new RepeatedReadException();
        }
        TarjetaRfid card = tarjetas.findById(uid)
                .filter(TarjetaRfid::isActiva)
                .filter(tarjeta -> !tarjeta.isDisponible())
                .orElseThrow(UnlinkedTagException::new);
        if (expectedUserId != null && !expectedUserId.equals(card.getUsuarioId())) {
            throw new CardOwnerMismatchException();
        }
        String normalizedSystem = sistema == null || sistema.isBlank()
                ? "TRANSCARIBE" : sistema.trim().toUpperCase(Locale.ROOT);
        BigDecimal configuredFare = switch (normalizedSystem) {
            case "TRANSCARIBE" -> tarifaTranscaribe;
            case "TRANSMILENIO" -> tarifaTransmilenio;
            case "TRANSMETRO" -> tarifaTransmetro;
            default -> throw new IllegalArgumentException("Sistema de transporte no válido.");
        };
        BigDecimal fare = requestedFare == null ? configuredFare : requestedFare;
        if (fare.signum() <= 0 || fare.compareTo(new BigDecimal("1000000")) > 0) {
            throw new IllegalArgumentException("La tarifa del viaje no es válida.");
        }
        String safeOperationId = operationId == null || operationId.isBlank()
                ? "RFID-" + UUID.randomUUID() : operationId;
        try {
            BilleteraClient.DebitResult result = billeteraClient.debit(
                    card.getUsuarioId(), fare, safeOperationId,
                    "Pasaje RFID " + normalizedSystem);
            return new ScanResult("APROBADO", "Cobro aprobado.", uid,
                    card.getUsuarioId(), result.cardNumber(), fare, result.balance());
        } catch (org.springframework.web.client.RestClientResponseException exception) {
            String message = exception.getResponseBodyAsString();
            if (exception.getStatusCode().value() == 404) {
                throw new WalletNotFoundException();
            }
            if (exception.getStatusCode().value() == 423) {
                throw new DebitRejectedException("La tarjeta de Billetera está congelada.");
            }
            if (exception.getStatusCode().value() == 409) {
                throw new DebitRejectedException("Saldo insuficiente para pagar el pasaje.");
            }
            throw new DebitRejectedException(message == null || message.isBlank()
                    ? "Billetera rechazó el cobro (HTTP " + exception.getStatusCode().value() + ")."
                    : "Billetera rechazó el cobro.");
        }
    }

    public static String normalize(String rawUid) {
        String uid = rawUid == null ? "" : rawUid.trim().toUpperCase(Locale.ROOT);
        if (!UID_PATTERN.matcher(uid).matches()) {
            throw new IllegalArgumentException("El UID debe tener entre 4 y 20 letras o números.");
        }
        return uid;
    }

    public record ScanResult(
            String status, String message, String uid, String userId,
            String cardNumber, BigDecimal charged, BigDecimal balance) { }

    public static class TagAlreadyLinkedException extends RuntimeException { }
    public static class NoAvailableCardException extends RuntimeException { }
    public static class CardOwnerMismatchException extends RuntimeException { }
    public static class UnlinkedTagException extends RuntimeException { }
    public static class WalletNotFoundException extends RuntimeException { }
    public static class RepeatedReadException extends RuntimeException { }
    public static class DebitRejectedException extends RuntimeException {
        public DebitRejectedException(String message) { super(message); }
    }
}

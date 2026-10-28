package com.Servicio.Acceso.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.bson.Document;
import org.bson.types.Decimal128;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.mongodb.ConnectionString;
import com.mongodb.MongoException;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;

import jakarta.annotation.PreDestroy;

@Service
public class GlobalOperationsService {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalOperationsService.class);
    private static final ZoneId ZONE = ZoneId.of("America/Bogota");
    private static final Set<String> SUPPORTED_SERVICES = Set.of(
            "TRANSCARIBE", "TRANSMILENIO", "TRANSMETRO", "BILLETERA", "RECARGAS_MOVIL");

    private final MongoClient transporteClient;
    private final MongoClient billeteraClient;
    private final MongoClient recargasClient;
    private final MongoDatabase transporteDatabase;
    private final MongoDatabase billeteraDatabase;
    private final MongoDatabase recargasDatabase;

    public GlobalOperationsService(
            @Value("${app.global.mongo.transporte-uri}") String transporteUri,
            @Value("${app.global.mongo.billetera-uri}") String billeteraUri,
            @Value("${app.global.mongo.recargas-uri}") String recargasUri) {
        transporteClient = client(transporteUri);
        billeteraClient = client(billeteraUri);
        recargasClient = client(recargasUri);
        transporteDatabase = database(transporteClient, transporteUri, "digitrans_transporte");
        billeteraDatabase = database(billeteraClient, billeteraUri, "digitrans_billetera");
        recargasDatabase = database(recargasClient, recargasUri, "digitrans_recargas");
    }

    public TransactionSnapshot transactions(
            Set<String> services, LocalDate from, LocalDate through) {
        Set<String> selected = services == null || services.isEmpty()
                ? SUPPORTED_SERVICES : services.stream()
                        .map(service -> service.toUpperCase(Locale.ROOT))
                        .filter(SUPPORTED_SERVICES::contains)
                        .collect(java.util.stream.Collectors.toUnmodifiableSet());
        List<GlobalTransaction> transactions = new ArrayList<>();
        Map<String, String> unavailable = new LinkedHashMap<>();

        if (selected.contains("TRANSCARIBE") || selected.contains("TRANSMILENIO")
                || selected.contains("TRANSMETRO")) {
            readTransportTransactions(transactions, unavailable, selected, from, through);
        }
        if (selected.contains("BILLETERA") || selected.contains("TRANSCARIBE")
                || selected.contains("TRANSMILENIO") || selected.contains("TRANSMETRO")
                || selected.contains("RECARGAS_MOVIL")) {
            readWalletMovements(transactions, unavailable, selected, from, through);
        }
        if (selected.contains("RECARGAS_MOVIL")) {
            readMobileRecharges(transactions, unavailable, from, through);
        }

        List<GlobalTransaction> unique = deduplicate(transactions);
        unique.sort(Comparator.comparing(
                GlobalTransaction::occurredAt, Comparator.nullsLast(Comparator.reverseOrder())));
        return new TransactionSnapshot(List.copyOf(unique), Map.copyOf(unavailable));
    }

    private void readTransportTransactions(List<GlobalTransaction> output, Map<String, String> unavailable,
            Set<String> services, LocalDate from, LocalDate through) {
        readCollection("Transporte", transporteDatabase.getCollection("transacciones"),
                unavailable, document -> {
                    String service = normalizeTransportService(first(document, "sistema_transporte", "sistemaTransporte"));
                    if (!services.contains(service)) {
                        return;
                    }
                    LocalDateTime date = date(first(document, "fecha"));
                    if (inRange(date, from, through)) {
                        output.add(new GlobalTransaction(
                                text(first(document, "operation_id", "operationId", "operacion_id", "operacionId")),
                                service,
                                text(first(document, "tipo", "type")),
                                text(first(document, "tipo", "description")),
                                text(first(document, "usuario_id", "usuarioId", "userId")),
                                text(first(document, "usuario_nombre", "usuarioNombre", "userName")),
                                text(first(document, "usuario_correo", "usuarioCorreo", "userEmail")),
                                amount(first(document, "monto", "amount")),
                                text(first(document, "estado", "status"), "REGISTRADA"),
                                date,
                                "Transporte"));
                    }
                });
    }

    private void readWalletMovements(List<GlobalTransaction> output, Map<String, String> unavailable,
            Set<String> services, LocalDate from, LocalDate through) {
        readCollection("Billetera", billeteraDatabase.getCollection("billeteras_universales"),
                unavailable, wallet -> {
                    String userId = text(first(wallet, "_id", "usuarioId", "userId"));
                    Object rawMovements = first(wallet, "movimientos", "movements");
                    if (!(rawMovements instanceof List<?> movements)) {
                        return;
                    }
                    for (int index = 0; index < movements.size(); index++) {
                        if (!(movements.get(index) instanceof Document movement)) {
                            continue;
                        }
                        if (isTopUpAttempt(text(first(movement, "tipo", "type")))) {
                            continue;
                        }
                        String service = normalizeWalletService(first(movement, "servicio", "service"),
                                first(movement, "tipo", "type"), first(movement, "descripcion", "description"));
                        if (!services.contains(service)) {
                            continue;
                        }
                        LocalDateTime date = date(first(movement, "fecha", "date"));
                        if (!inRange(date, from, through)) {
                            continue;
                        }
                        output.add(new GlobalTransaction(
                                text(first(movement, "operacionId", "operationId", "operacion_id")),
                                service,
                                text(first(movement, "tipo", "type")),
                                text(first(movement, "descripcion", "description")),
                                userId,
                                "",
                                "",
                                amount(first(movement, "monto", "amount")),
                                "COMPLETADA",
                                date,
                                "Billetera"));
                    }
                });
    }

    static boolean isTopUpAttempt(String type) {
        return "ABONO_PENDIENTE".equals(type) || "ABONO_RECHAZADO".equals(type);
    }

    private void readMobileRecharges(List<GlobalTransaction> output, Map<String, String> unavailable,
            LocalDate from, LocalDate through) {
        readCollection("Recargas móviles", recargasDatabase.getCollection("recargas_movil"),
                unavailable, recharge -> {
                    LocalDateTime date = date(first(recharge, "fecha"));
                    if (!inRange(date, from, through)) {
                        return;
                    }
                    String description = String.join(" · ", List.of(
                            text(first(recharge, "operador"), ""),
                            text(first(recharge, "paquete"), "")).stream()
                            .filter(value -> !value.isBlank()).toList());
                    output.add(new GlobalTransaction(
                            text(first(recharge, "_id", "operacionId", "operationId")),
                            "RECARGAS_MOVIL",
                            "RECARGA",
                            description,
                            text(first(recharge, "usuarioId", "usuario_id", "userId")),
                            "",
                            "",
                            amount(first(recharge, "monto", "amount")),
                            text(first(recharge, "estado", "status"), "REGISTRADA"),
                            date,
                            "RecargasMovil"));
                });
    }

    private void readCollection(String serviceName, MongoCollection<Document> collection,
            Map<String, String> unavailable, java.util.function.Consumer<Document> consumer) {
        try (var cursor = collection.find().iterator()) {
            while (cursor.hasNext()) {
                consumer.accept(cursor.next());
            }
        } catch (MongoException exception) {
            LOGGER.error("No se pudieron consultar los datos globales de {}.", serviceName, exception);
            unavailable.put(serviceName, "No se pudo consultar la base de datos de " + serviceName + ".");
        }
    }

    static List<GlobalTransaction> deduplicate(List<GlobalTransaction> transactions) {
        Map<String, GlobalTransaction> byOperation = new LinkedHashMap<>();
        List<GlobalTransaction> withoutOperationId = new ArrayList<>();
        for (GlobalTransaction transaction : transactions) {
            if (transaction.operationId() == null || transaction.operationId().isBlank()) {
                withoutOperationId.add(transaction);
                continue;
            }
            byOperation.merge(transaction.operationId(), transaction,
                    (existing, candidate) -> priority(candidate) > priority(existing) ? candidate : existing);
        }
        List<GlobalTransaction> result = new ArrayList<>(byOperation.values());
        result.addAll(withoutOperationId);
        return result;
    }

    private static int priority(GlobalTransaction transaction) {
        if ("RecargasMovil".equals(transaction.source())) return 3;
        if ("Transporte".equals(transaction.source())) return 2;
        return "BILLETERA".equals(transaction.service()) ? 2 : 1;
    }

    private static MongoDatabase database(MongoClient client, String uri, String fallbackName) {
        String databaseName = new ConnectionString(uri).getDatabase();
        return client.getDatabase(databaseName == null || databaseName.isBlank() ? fallbackName : databaseName);
    }

    private static MongoClient client(String uri) {
        ConnectionString connectionString = new ConnectionString(uri);
        MongoClientSettings settings = MongoClientSettings.builder()
                .applyConnectionString(connectionString)
                .applyToClusterSettings(cluster ->
                        cluster.serverSelectionTimeout(3, TimeUnit.SECONDS))
                .applyToSocketSettings(socket -> socket
                        .connectTimeout(2, TimeUnit.SECONDS)
                        .readTimeout(5, TimeUnit.SECONDS))
                .build();
        return MongoClients.create(settings);
    }

    private static Object first(Document document, String... keys) {
        for (String key : keys) {
            if (document.containsKey(key)) {
                return document.get(key);
            }
        }
        return null;
    }

    private static String normalizeTransportService(Object value) {
        String service = text(value, "TRANSCARIBE").toUpperCase(Locale.ROOT);
        return SUPPORTED_SERVICES.contains(service) ? service : "TRANSCARIBE";
    }

    private static String normalizeWalletService(Object rawService, Object rawType, Object rawDescription) {
        String service = text(rawService, "").toUpperCase(Locale.ROOT);
        if (service.contains("TRANSMILENIO")) return "TRANSMILENIO";
        if (service.contains("TRANSMETRO")) return "TRANSMETRO";
        if (service.contains("TRANSCARIBE")) return "TRANSCARIBE";
        if (service.contains("RECARGA") || service.contains("PAQUETE")) return "RECARGAS_MOVIL";
        if (service.contains("BILLETERA") || service.contains("SOPORTE")) return "BILLETERA";

        String text = (text(rawType, "") + " " + text(rawDescription, "")).toLowerCase(Locale.ROOT);
        if (text.contains("transmilenio")) return "TRANSMILENIO";
        if (text.contains("transmetro")) return "TRANSMETRO";
        if (text.contains("transcaribe") || text.contains("pasaje")) return "TRANSCARIBE";
        if (text.contains("paquete") || text.contains("recarga")) return "RECARGAS_MOVIL";
        return "BILLETERA";
    }

    private static String text(Object value) {
        return text(value, "");
    }

    private static String text(Object value, String fallback) {
        return value == null ? fallback : String.valueOf(value);
    }

    private static BigDecimal amount(Object value) {
        if (value instanceof Decimal128 decimal) return decimal.bigDecimalValue();
        if (value instanceof BigDecimal decimal) return decimal;
        if (value instanceof Number number) return BigDecimal.valueOf(number.doubleValue());
        return BigDecimal.ZERO;
    }

    private static LocalDateTime date(Object value) {
        if (value instanceof Date date) {
            return LocalDateTime.ofInstant(date.toInstant(), ZONE);
        }
        if (value instanceof LocalDateTime dateTime) return dateTime;
        if (value instanceof String text) {
            try {
                return LocalDateTime.parse(text);
            } catch (java.time.format.DateTimeParseException ignored) {
                return null;
            }
        }
        return null;
    }

    private static boolean inRange(LocalDateTime date, LocalDate from, LocalDate through) {
        if (from == null && through == null) {
            return true;
        }
        return date != null
                && (from == null || !date.toLocalDate().isBefore(from))
                && (through == null || !date.toLocalDate().isAfter(through));
    }

    @PreDestroy
    public void close() {
        transporteClient.close();
        billeteraClient.close();
        recargasClient.close();
    }

    public record GlobalTransaction(
            String operationId,
            String service,
            String type,
            String description,
            String userId,
            String userName,
            String userEmail,
            BigDecimal amount,
            String status,
            LocalDateTime occurredAt,
            String source) {
    }

    public record TransactionSnapshot(
            List<GlobalTransaction> transactions,
            Map<String, String> unavailableServices) {
        public long total() {
            return transactions.size();
        }

        public BigDecimal totalAmount() {
            return transactions.stream()
                    .map(GlobalTransaction::amount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }
    }
}

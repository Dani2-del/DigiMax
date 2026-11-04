package com.Servicio.Acceso.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
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
import org.bson.conversions.Bson;
import org.bson.types.Decimal128;
import org.bson.types.ObjectId;
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
import com.mongodb.client.MongoIterable;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;

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

    public List<String> mobileRechargeUserIds() {
        Set<String> userIds = new java.util.TreeSet<>();
        collectDistinctUserIds("lineas_movil", userIds);
        collectDistinctUserIds("recargas_movil", userIds);
        return List.copyOf(userIds);
    }

    public boolean hasMobileRechargeActivity(String userId) {
        if (userId == null || userId.isBlank()) {
            return false;
        }
        return hasUserId("lineas_movil", userId) || hasUserId("recargas_movil", userId);
    }

    public boolean hasWalletAccount(String userId) {
        if (userId == null || userId.isBlank()) {
            return false;
        }
        Bson idFilter = Filters.eq("_id", userId);
        if (ObjectId.isValid(userId)) {
            idFilter = Filters.or(idFilter, Filters.eq("_id", new ObjectId(userId)));
        }
        return billeteraDatabase.getCollection("billeteras_universales")
                .countDocuments(idFilter) > 0;
    }

    private void collectDistinctUserIds(String collectionName, Set<String> userIds) {
        MongoCollection<Document> collection = recargasDatabase.getCollection(collectionName);
        for (String field : List.of("usuarioId", "usuario_id", "userId")) {
            collection.distinct(field, String.class)
                    .forEach(value -> {
                        if (value != null && !value.isBlank()) {
                            userIds.add(value);
                        }
                    });
        }
    }

    private boolean hasUserId(String collectionName, String userId) {
        MongoCollection<Document> collection = recargasDatabase.getCollection(collectionName);
        Bson userIdFilter = Filters.or(
                Filters.eq("usuarioId", userId),
                Filters.eq("usuario_id", userId),
                Filters.eq("userId", userId));
        return collection.find(userIdFilter)
                .projection(Projections.include("_id"))
                .limit(1)
                .first() != null;
    }

    private void readTransportTransactions(List<GlobalTransaction> output, Map<String, String> unavailable,
            Set<String> services, LocalDate from, LocalDate through) {
        MongoCollection<Document> collection = transporteDatabase.getCollection("transacciones");
        var query = collection.find();
        Bson dateFilter = dateFilter("fecha", from, through);
        if (dateFilter != null) {
            query.filter(dateFilter);
        }
        query.projection(Projections.include(
                "_id", "operation_id", "operationId", "operacion_id", "operacionId",
                "sistema_transporte", "sistemaTransporte", "tipo", "type", "fecha",
                "usuario_id", "usuarioId", "userId", "usuario_nombre", "usuarioNombre", "userName",
                "usuario_correo", "usuarioCorreo", "userEmail", "monto", "amount", "estado", "status"));
        readCollection("Transporte", query, unavailable, document -> {
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
        MongoCollection<Document> collection = billeteraDatabase.getCollection("billeteras_universales");
        List<Bson> walletDateFilters = java.util.stream.Stream.of(
                dateFilter("movimientos.fecha", from, through),
                dateFilter("movimientos.date", from, through),
                dateFilter("movements.fecha", from, through),
                dateFilter("movements.date", from, through))
                .filter(java.util.Objects::nonNull)
                .toList();
        List<Bson> movementDateFilters = java.util.stream.Stream.of(
                dateFilter("_movements.fecha", from, through),
                dateFilter("_movements.date", from, through))
                .filter(java.util.Objects::nonNull)
                .toList();
        List<Document> pipeline = new ArrayList<>();
        if (!walletDateFilters.isEmpty()) {
            pipeline.add(new Document("$match", Filters.or(walletDateFilters)));
        }
        pipeline.add(new Document("$project", new Document("_id", 1)
                .append("_movements", new Document("$ifNull",
                        List.of("$movimientos", "$movements")))));
        pipeline.add(new Document("$unwind", "$_movements"));
        if (!movementDateFilters.isEmpty()) {
            pipeline.add(new Document("$match", Filters.or(movementDateFilters)));
        }
        pipeline.add(new Document("$project", new Document("userId", "$_id")
                .append("movement", "$_movements")));
        readCollection("Billetera", collection.aggregate(pipeline), unavailable, wallet -> {
                    String userId = text(first(wallet, "userId", "_id"));
                    Object rawMovement = wallet.get("movement");
                    if (!(rawMovement instanceof Document movement)) {
                        return;
                    }
                    if (isTopUpAttempt(text(first(movement, "tipo", "type")))) {
                        return;
                    }
                    String service = normalizeWalletService(first(movement, "servicio", "service"),
                            first(movement, "tipo", "type"), first(movement, "descripcion", "description"));
                    if (!services.contains(service)) {
                        return;
                    }
                    LocalDateTime date = date(first(movement, "fecha", "date"));
                    if (!inRange(date, from, through)) {
                        return;
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
                });
    }

    static boolean isTopUpAttempt(String type) {
        return "ABONO_PENDIENTE".equals(type) || "ABONO_RECHAZADO".equals(type);
    }

    private void readMobileRecharges(List<GlobalTransaction> output, Map<String, String> unavailable,
            LocalDate from, LocalDate through) {
        MongoCollection<Document> collection = recargasDatabase.getCollection("recargas_movil");
        var query = collection.find();
        Bson dateFilter = dateFilter("fecha", from, through);
        if (dateFilter != null) {
            query.filter(dateFilter);
        }
        query.projection(Projections.include(
                "_id", "operacionId", "operationId", "fecha", "operador", "paquete",
                "usuarioId", "usuario_id", "userId", "monto", "amount", "estado", "status"));
        readCollection("Recargas móviles", query, unavailable, recharge -> {
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

    private void readCollection(String serviceName, MongoIterable<Document> documents,
            Map<String, String> unavailable, java.util.function.Consumer<Document> consumer) {
        try (var cursor = documents.iterator()) {
            while (cursor.hasNext()) {
                consumer.accept(cursor.next());
            }
        } catch (MongoException exception) {
            LOGGER.error("No se pudieron consultar los datos globales de {}.", serviceName, exception);
            unavailable.put(serviceName, "No se pudo consultar la base de datos de " + serviceName + ".");
        }
    }

    private static Bson dateFilter(String field, LocalDate from, LocalDate through) {
        if (from == null && through == null) {
            return null;
        }
        List<Bson> dateBounds = new ArrayList<>();
        List<Bson> stringBounds = new ArrayList<>();
        if (from != null) {
            LocalDate lowerBound = from.minusDays(1);
            dateBounds.add(Filters.gte(field, Date.from(
                    lowerBound.atStartOfDay().toInstant(ZoneOffset.UTC))));
            stringBounds.add(Filters.gte(field, lowerBound.atStartOfDay().toString()));
        }
        if (through != null) {
            LocalDate upperBound = through.plusDays(2);
            dateBounds.add(Filters.lt(field, Date.from(
                    upperBound.atStartOfDay().toInstant(ZoneOffset.UTC))));
            stringBounds.add(Filters.lt(field, upperBound.atStartOfDay().toString()));
        }
        return Filters.or(Filters.and(dateBounds), Filters.and(stringBounds));
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

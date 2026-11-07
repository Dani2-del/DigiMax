package com.Servicio.Orquestador.service;

import com.Servicio.Orquestador.config.ModulosConfig;
import com.Servicio.Orquestador.config.ModulosConfig.ModuloConfig;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;

@Service
public class ProcesoService {

    private static final Logger log = LoggerFactory.getLogger(ProcesoService.class);

    private static final int MAX_REINICIOS = 5;                     // reinicios permitidos...
    private static final Duration VENTANA = Duration.ofMinutes(10); // ...dentro de esta ventana
    private static final long PAUSA_REINICIO_MS = 3000;

    public enum Estado { DETENIDO, ARRANCANDO, ACTIVO, NO_RESPONDE, BLOQUEADO }

    public record EstadoDto(String nombre, String estado, int puerto, int reinicios, long segundosActivo) {}

    private static class EstadoModulo {
        final ModuloConfig config;
        Process proceso;
        Instant inicio;
        int fallos;
        int totalReinicios;
        Estado estado = Estado.DETENIDO;
        final Deque<Instant> reinicios = new ArrayDeque<>();

        EstadoModulo(ModuloConfig config) {
            this.config = config;
        }

        long segundosActivo() {
            if (proceso == null || inicio == null) return 0;
            return Duration.between(inicio, Instant.now()).getSeconds();
        }
    }

    private final ModulosConfig config;
    private final Path raiz;
    private final Path logsDir;
    private final int gracia;
    private final int maxFallos;
    private final Map<String, EstadoModulo> estados = new LinkedHashMap<>();

    public ProcesoService(ModulosConfig config,
                          @Value("${orquestador.raiz:..}") String raizProp,
                          @Value("${orquestador.gracia-segundos:120}") int gracia,
                          @Value("${orquestador.max-fallos:3}") int maxFallos) throws IOException {
        this.config = config;
        this.raiz = resolverRaizProyecto(raizProp);
        this.logsDir = this.raiz.resolve("Orquestador").resolve("logs");
        this.gracia = gracia;
        this.maxFallos = maxFallos;
        Files.createDirectories(logsDir);
        for (ModuloConfig m : config.todos()) {
            estados.put(m.nombre(), new EstadoModulo(m));
        }
    }

    private static Path resolverRaizProyecto(String raizConfigurada) {
        Path configurada = Path.of(raizConfigurada).toAbsolutePath().normalize();
        if (esRaizDigiMax(configurada)) return configurada;

        // VS Code puede ejecutar la clase con DigiMAX como directorio actual,
        // mientras que Gradle la ejecuta desde Orquestador/.
        Path actual = Path.of("").toAbsolutePath().normalize();
        if (esRaizDigiMax(actual)) return actual;

        Path padre = actual.getParent();
        if (padre != null && esRaizDigiMax(padre)) return padre;

        throw new IllegalStateException("No se encontró la raíz de DigiMAX desde " + actual
                + ". Configura orquestador.raiz con la ruta del proyecto.");
    }

    private static boolean esRaizDigiMax(Path ruta) {
        return Files.isDirectory(ruta.resolve("Orquestador"))
                && Files.isDirectory(ruta.resolve("Acceso"));
    }

    // ---------- Operaciones manuales (API) ----------

    public synchronized void iniciarManual(String nombre) throws IOException {
        EstadoModulo e = obtener(nombre);
        e.reinicios.clear();   // un arranque manual limpia el bloqueo por reinicios
        arrancar(e);
    }

    public synchronized void reiniciarManual(String nombre) throws IOException, InterruptedException {
        EstadoModulo e = obtener(nombre);
        matar(e);
        Thread.sleep(PAUSA_REINICIO_MS);
        e.reinicios.clear();
        e.totalReinicios++;
        arrancar(e);
    }

    public synchronized void detener(String nombre) {
        EstadoModulo e = obtener(nombre);
        matar(e);
        e.estado = Estado.DETENIDO;
    }

    public boolean esperarDisponible(String nombre, int timeoutSegundos) throws InterruptedException {
        long limite = System.nanoTime() + Duration.ofSeconds(timeoutSegundos).toNanos();
        EstadoModulo e;
        synchronized (this) {
            e = obtener(nombre);
        }
        while (System.nanoTime() < limite) {
            synchronized (this) {
                if (e.proceso == null || !e.proceso.isAlive()) return false;
            }
            if (httpResponde(e.config.puerto())) {
                synchronized (this) {
                    e.fallos = 0;
                    e.estado = Estado.ACTIVO;
                }
                return true;
            }
            Thread.sleep(1000);
        }
        return false;
    }

    public synchronized List<EstadoDto> resumen() {
        return estados.values().stream()
                .map(e -> new EstadoDto(
                        e.config.nombre(),
                        e.estado.name(),
                        e.config.puerto(),
                        e.totalReinicios,
                        e.segundosActivo()))
                .toList();
    }

    // ---------- Vigilancia (la llama SaludService) ----------

    public synchronized void verificar(String nombre) throws IOException, InterruptedException {
        EstadoModulo e = obtener(nombre);
        if (e.estado == Estado.DETENIDO || e.estado == Estado.BLOQUEADO) return;

        if (e.proceso == null || !e.proceso.isAlive()) {
            log.warn("{} terminó inesperadamente", nombre);
            reinicioAutomatico(e, "el proceso terminó");
            return;
        }

        // Spring Boot tarda en arrancar: no juzgar el módulo durante el periodo de gracia
        if (e.segundosActivo() < gracia) return;

        if (httpResponde(e.config.puerto())) {
            e.fallos = 0;
            e.estado = Estado.ACTIVO;
        } else {
            e.fallos++;
            e.estado = Estado.NO_RESPONDE;
            log.warn("{} no responde en el puerto {} ({}/{})",
                    nombre, e.config.puerto(), e.fallos, maxFallos);
            if (e.fallos >= maxFallos) {
                reinicioAutomatico(e, "puerto " + e.config.puerto() + " sin respuesta");
            }
        }
    }

    @PreDestroy
    public synchronized void detenerTodos() {
        for (EstadoModulo e : estados.values()) {
            matar(e);
            e.estado = Estado.DETENIDO;
        }
        log.info("Todos los módulos detenidos");
    }

    // ---------- Internos ----------

    private void reinicioAutomatico(EstadoModulo e, String motivo) throws IOException, InterruptedException {
        Instant ahora = Instant.now();
        e.reinicios.removeIf(t -> t.isBefore(ahora.minus(VENTANA)));

        if (e.reinicios.size() >= MAX_REINICIOS) {
            matar(e);
            e.estado = Estado.BLOQUEADO;
            log.error("{} bloqueado: {} reinicios en {} minutos. Revisa logs/{}.log",
                    e.config.nombre(), MAX_REINICIOS, VENTANA.toMinutes(), e.config.nombre());
            return;
        }

        e.reinicios.addLast(ahora);
        e.totalReinicios++;
        log.warn("Reiniciando {}: {}", e.config.nombre(), motivo);
        matar(e);
        Thread.sleep(PAUSA_REINICIO_MS);
        arrancar(e);
    }

    private void arrancar(EstadoModulo e) throws IOException {
        if (e.proceso != null && e.proceso.isAlive()) return;

        ModuloConfig m = e.config;
        ProcessBuilder pb = new ProcessBuilder("cmd.exe", "/c", m.comando())
                .directory(raiz.resolve(m.directorio()).toFile())
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.appendTo(
                        logsDir.resolve(m.nombre() + ".log").toFile()));

        e.proceso = pb.start();
        e.inicio = Instant.now();
        e.fallos = 0;
        e.estado = Estado.ARRANCANDO;
        log.info("Iniciado {} (PID {})", m.nombre(), e.proceso.pid());
    }

    // cmd.exe lanza gradlew/mvnw, que a su vez lanza java: hay que matar todo el árbol
    private void matar(EstadoModulo e) {
        if (e.proceso != null) {
            e.proceso.descendants().forEach(ProcessHandle::destroyForcibly);
            e.proceso.destroyForcibly();
        }
        e.proceso = null;
        matarProcesoQueEscucha(e.config.puerto());
    }

    /**
     * En Windows Gradle puede dejar el proceso Spring Boot fuera del árbol del
     * wrapper que lanzó el módulo. Como respaldo, se termina el PID que posee
     * el socket LISTEN del puerto configurado para ese módulo.
     */
    private void matarProcesoQueEscucha(int puerto) {
        if (!System.getProperty("os.name", "").toLowerCase().contains("win")) return;

        Set<Long> pids = new HashSet<>();
        try {
            Process netstat = new ProcessBuilder("netstat", "-ano", "-p", "tcp")
                    .redirectErrorStream(true).start();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(netstat.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] columns = line.trim().split("\\s+");
                    if (columns.length < 5) continue;
                    String localAddress = columns[1];
                    String state = columns[3];
                    if ("LISTENING".equalsIgnoreCase(state)
                            && localAddress.endsWith(":" + puerto)) {
                        try {
                            pids.add(Long.parseLong(columns[4]));
                        } catch (NumberFormatException ignored) {
                            // Ignora filas de encabezado o salida no estándar.
                        }
                    }
                }
            }
            netstat.waitFor();
        } catch (IOException ex) {
            log.warn("No se pudo consultar el proceso del puerto {}", puerto, ex);
            return;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return;
        }

        long pidOrquestador = ProcessHandle.current().pid();
        for (long pid : pids) {
            if (pid == pidOrquestador) continue;
            try {
                Process cierre = new ProcessBuilder("taskkill", "/PID", Long.toString(pid), "/T", "/F")
                        .redirectErrorStream(true).start();
                boolean termino = cierre.waitFor(10, java.util.concurrent.TimeUnit.SECONDS);
                if (!termino) {
                    cierre.destroyForcibly();
                    log.warn("Timeout al cerrar PID {} del puerto {}", pid, puerto);
                } else if (cierre.exitValue() == 0) {
                    log.info("Cerrado PID {} que escuchaba en el puerto {}", pid, puerto);
                } else {
                    log.debug("taskkill no cerró PID {} del puerto {} (puede que ya terminara)", pid, puerto);
                }
            } catch (IOException ex) {
                log.warn("No se pudo cerrar PID {} del puerto {}", pid, puerto, ex);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private EstadoModulo obtener(String nombre) {
        EstadoModulo e = estados.get(nombre);
        if (e == null) throw new IllegalArgumentException("Módulo desconocido: " + nombre);
        return e;
    }

    private static boolean httpResponde(int puerto) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL("http://127.0.0.1:" + puerto + "/").openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(1000);
            connection.setReadTimeout(1500);
            connection.setInstanceFollowRedirects(true);
            return connection.getResponseCode() < 500;
        } catch (IOException ex) {
            return false;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }
}

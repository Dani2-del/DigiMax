package com.Servicio.Orquestador.config;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ModulosConfig {

    public record ModuloConfig(String nombre, String directorio, String comando, int puerto) {}

    // El orden respeta los servicios base y las dependencias de pagos, billetera y lector.
    private final List<ModuloConfig> modulos = List.of(
            new ModuloConfig("Acceso",        "Acceso",        ".\\gradlew.bat --no-daemon bootRun", 8080),
            new ModuloConfig("Correo",        "Correo",        ".\\gradlew.bat --no-daemon bootRun", 8084),
            new ModuloConfig("Pagos",         "Pagos",         "..\\Transporte\\mvnw.cmd -f pom.xml spring-boot:run", 8085),
            new ModuloConfig("Transporte",    "Transporte",    ".\\mvnw.cmd spring-boot:run",         8081),
            new ModuloConfig("Billetera",     "Billetera",     ".\\gradlew.bat --no-daemon bootRun", 8082),
            new ModuloConfig("RecargasMovil", "RecargasMovil", ".\\gradlew.bat --no-daemon bootRun", 8083),
            new ModuloConfig("Lector",        "Lector",        ".\\gradlew.bat --no-daemon bootRun", 8086)
    );

    public List<ModuloConfig> todos() {
        return modulos;
    }

    public boolean existe(String nombre) {
        return modulos.stream().anyMatch(m -> m.nombre().equals(nombre));
    }
}

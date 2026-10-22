package com.transcaribe.transcaribe.service;

import com.transcaribe.transcaribe.Model.RutaTransmetro;
import jakarta.annotation.PostConstruct;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class CatalogoRutasTransmetroService {
    private static final Map<String, String> NOMBRES_ESTACIONES = Map.ofEntries(
            Map.entry("portal_soledad", "Portal de Soledad"),
            Map.entry("pacho_galan", "Pacho Galán"),
            Map.entry("pedro_ramaya", "Pedro Ramayá Beltrán"),
            Map.entry("joaquin_barrios", "Joaquín Barrios Polo / Estadio Metropolitano"),
            Map.entry("buenos_aires", "Buenos Aires"),
            Map.entry("la_ocho", "La Ocho"),
            Map.entry("la_catorce", "La Catorce"),
            Map.entry("la_veintiuna", "La Veintiuna"),
            Map.entry("atlantico", "Atlántico"),
            Map.entry("chiquinquira", "Chiquinquirá"),
            Map.entry("la_arenosa", "La Arenosa"),
            Map.entry("parque_cultural", "Parque Cultural del Caribe"),
            Map.entry("barrio_abajo", "Barrio Abajo"),
            Map.entry("la_catedral", "La Catedral"),
            Map.entry("alfredo_correa", "Alfredo Correa De Andréis"),
            Map.entry("esthercita_forero", "Esthercita Forero"),
            Map.entry("joe_arroyo", "Joe Arroyo"));

    private List<RutaTransmetro> rutas = List.of();

    @PostConstruct
    void cargarCatalogo() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new ClassPathResource("data/transmetro-rutas.csv").getInputStream(),
                StandardCharsets.UTF_8))) {
            reader.readLine(); // encabezado
            List<RutaTransmetro> cargadas = new ArrayList<>();
            String fila;
            while ((fila = reader.readLine()) != null) {
                if (fila.isBlank()) continue;
                String[] campos = fila.split(",", -1);
                if (campos.length != 5) {
                    throw new IllegalStateException("Fila inválida en el catálogo de Transmetro: " + fila);
                }
                cargadas.add(new RutaTransmetro(
                        campos[0].trim(), campos[1].trim(), "activa".equalsIgnoreCase(campos[2].trim()),
                        Arrays.stream(campos[3].split(";"))
                                .map(String::trim)
                                .map(clave -> NOMBRES_ESTACIONES.getOrDefault(clave, clave))
                                .toList(),
                        Arrays.stream(campos[4].split("\\|")).map(String::trim).toList()));
            }
            rutas = List.copyOf(cargadas);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo cargar el catálogo de rutas de Transmetro.", e);
        }
    }

    public List<RutaTransmetro> obtenerRutas() {
        return rutas;
    }

    public List<RutaTransmetro> obtenerRutasActivas() {
        return rutas.stream().filter(RutaTransmetro::activa).toList();
    }

    public java.util.Optional<RutaTransmetro> obtenerRutaAleatoriaActiva() {
        List<RutaTransmetro> activas = obtenerRutasActivas();
        if (activas.isEmpty()) return java.util.Optional.empty();
        return java.util.Optional.of(activas.get(ThreadLocalRandom.current().nextInt(activas.size())));
    }
}

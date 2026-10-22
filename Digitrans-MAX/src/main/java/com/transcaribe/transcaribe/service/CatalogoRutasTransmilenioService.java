package com.transcaribe.transcaribe.service;

import com.transcaribe.transcaribe.Model.RutaTransmilenio;
import jakarta.annotation.PostConstruct;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class CatalogoRutasTransmilenioService {
    private List<RutaTransmilenio> rutasActivas = List.of();

    @PostConstruct
    void cargarCatalogo() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new ClassPathResource("data/transmilenio-servicios.csv").getInputStream(),
                StandardCharsets.UTF_8))) {
            String encabezado = reader.readLine();
            if (encabezado == null) {
                throw new IllegalStateException("El catálogo de servicios de TransMilenio está vacío.");
            }
            List<String> nombresColumnas = parsearFila(encabezado);
            Map<String, Integer> columnas = new LinkedHashMap<>();
            for (int i = 0; i < nombresColumnas.size(); i++) {
                columnas.put(nombresColumnas.get(i).replace("\uFEFF", "").trim(), i);
            }

            List<RutaTransmilenio> cargadas = new ArrayList<>();
            String fila;
            while ((fila = reader.readLine()) != null) {
                if (fila.isBlank()) continue;
                List<String> valores = parsearFila(fila);
                // comp_sitp=1 identifica servicios del componente troncal TransMilenio;
                // se excluyen servicios zonales/mixtos incluidos en el mismo archivo.
                if (!"1".equals(valor(valores, columnas, "est_ruta").trim())
                        || !"1".equals(valor(valores, columnas, "comp_sitp").trim())) continue;
                String id = valor(valores, columnas, "globalid").trim();
                String codigoLinea = valor(valores, columnas, "cod_linea").trim();
                String nombre = valor(valores, columnas, "nom_ruta").trim();
                String codigoRuta = valor(valores, columnas, "cod_ruta").trim();
                String origen = valor(valores, columnas, "orig_ruta").trim();
                String destino = valor(valores, columnas, "dest_ruta").trim();
                if (id.isEmpty() || codigoLinea.isEmpty() || origen.isEmpty() || destino.isEmpty()) continue;
                cargadas.add(new RutaTransmilenio(id, codigoLinea, nombre, codigoRuta, origen, destino,
                        limpiarHorario(valor(valores, columnas, "hor_habil")),
                        limpiarHorario(valor(valores, columnas, "hor_sab")),
                        limpiarHorario(valor(valores, columnas, "hor_fest"))));
            }

            rutasActivas = List.copyOf(cargadas);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo cargar el catálogo de rutas de TransMilenio.", e);
        }
    }

    public List<RutaTransmilenio> obtenerRutasActivas() {
        return rutasActivas;
    }

    public Optional<RutaTransmilenio> buscarPorEtiqueta(String etiqueta) {
        if (etiqueta == null) return Optional.empty();
        return rutasActivas.stream()
                .filter(ruta -> ruta.getEtiqueta().equals(etiqueta))
                .findFirst();
    }

    public Optional<RutaTransmilenio> obtenerRutaAleatoria() {
        if (rutasActivas.isEmpty()) return Optional.empty();
        int indice = ThreadLocalRandom.current().nextInt(rutasActivas.size());
        return Optional.of(rutasActivas.get(indice));
    }

    private static String valor(List<String> valores, Map<String, Integer> columnas, String nombre) {
        Integer indice = columnas.get(nombre);
        return indice == null || indice >= valores.size() ? "" : valores.get(indice);
    }

    private static String limpiarHorario(String horario) {
        String limpio = horario == null ? "" : horario.replace('\u00a0', ' ').trim();
        return limpio.isBlank() ? "Horario no publicado" : limpio;
    }

    private static List<String> parsearFila(String fila) {
        List<String> campos = new ArrayList<>();
        StringBuilder campo = new StringBuilder();
        boolean entreComillas = false;
        for (int i = 0; i < fila.length(); i++) {
            char caracter = fila.charAt(i);
            if (caracter == '"') {
                if (entreComillas && i + 1 < fila.length() && fila.charAt(i + 1) == '"') {
                    campo.append('"');
                    i++;
                } else {
                    entreComillas = !entreComillas;
                }
            } else if (caracter == ',' && !entreComillas) {
                campos.add(campo.toString());
                campo.setLength(0);
            } else {
                campo.append(caracter);
            }
        }
        campos.add(campo.toString());
        return campos;
    }
}

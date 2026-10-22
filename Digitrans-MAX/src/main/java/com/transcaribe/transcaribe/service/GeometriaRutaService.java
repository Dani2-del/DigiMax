package com.transcaribe.transcaribe.service;

import com.transcaribe.transcaribe.Model.GeometriaRuta;
import com.transcaribe.transcaribe.Repository.GeometriaRutaRepository;
import com.transcaribe.transcaribe.dto.GuardarGeometriaRutaRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;


import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class GeometriaRutaService {

    private static final int MAX_COORDENADAS = 10_000;
    private static final Pattern CODIGO_RUTA = Pattern.compile("[A-Z][0-9]{3,4}[A-Z]?");
    private static final Set<String> SENTIDOS = Set.of("IDA", "VUELTA", "CIRCULAR");

    private final GeometriaRutaRepository repository;

    public GeometriaRutaService(GeometriaRutaRepository repository) {
        this.repository = repository;
    }

    public List<GeometriaRuta> buscarPorRuta(String codigoRuta) {
        return repository.findByCodigoRutaOrderBySentidoAsc(normalizarCodigo(codigoRuta));
    }

    public GeometriaRuta guardar(String codigoRuta, String sentido, GuardarGeometriaRutaRequest request) {
        String codigo = normalizarCodigo(codigoRuta);
        String sentidoNormalizado = normalizarSentido(sentido);
        if (request == null || request.getCoordenadas() == null
                || request.getCoordenadas().size() < 2
                || request.getCoordenadas().size() > MAX_COORDENADAS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El trazado debe contener entre 2 y " + MAX_COORDENADAS + " coordenadas.");
        }

        List<GeometriaRuta.Coordenada> coordenadas = request.getCoordenadas().stream()
                .map(this::validarCoordenada)
                .toList();
        GeometriaRuta geometria = repository.findByCodigoRutaAndSentido(codigo, sentidoNormalizado)
                .orElseGet(GeometriaRuta::new);
        geometria.setCodigoRuta(codigo);
        geometria.setSentido(sentidoNormalizado);
        geometria.setCoordenadas(coordenadas);
        geometria.setActualizadaEn(Instant.now());
        return repository.save(geometria);
    }

    public void eliminar(String codigoRuta, String sentido) {
        repository.findByCodigoRutaAndSentido(normalizarCodigo(codigoRuta), normalizarSentido(sentido))
                .ifPresent(repository::delete);
    }

    private String normalizarCodigo(String codigo) {
        if (codigo == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El código de ruta es obligatorio.");
        }
        String normalizado = codigo.trim().toUpperCase(Locale.ROOT);
        if (!CODIGO_RUTA.matcher(normalizado).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El código de ruta no es válido.");
        }
        return normalizado;
    }

    private String normalizarSentido(String sentido) {
        if (sentido == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El sentido de la ruta es obligatorio.");
        }
        String normalizado = sentido.trim().toUpperCase(Locale.ROOT);
        if (!SENTIDOS.contains(normalizado)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El sentido de la ruta no es válido.");
        }
        return normalizado;
    }

    private GeometriaRuta.Coordenada validarCoordenada(GuardarGeometriaRutaRequest.CoordenadaRequest punto) {
        if (punto == null || punto.getLatitud() == null || punto.getLongitud() == null
                || !Double.isFinite(punto.getLatitud()) || punto.getLatitud() < -90 || punto.getLatitud() > 90
                || !Double.isFinite(punto.getLongitud()) || punto.getLongitud() < -180 || punto.getLongitud() > 180) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Cada coordenada debe tener latitud y longitud válidas.");
        }
        return new GeometriaRuta.Coordenada(punto.getLatitud(), punto.getLongitud());
    }
}

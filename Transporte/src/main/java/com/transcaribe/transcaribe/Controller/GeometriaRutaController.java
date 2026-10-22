package com.transcaribe.transcaribe.Controller;

import com.transcaribe.transcaribe.Model.GeometriaRuta;
import com.transcaribe.transcaribe.Model.SistemaTransporte;
import com.transcaribe.transcaribe.service.GeometriaRutaService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/mapa-rutas/api")
public class GeometriaRutaController {

    private final GeometriaRutaService geometriaRutaService;

    public GeometriaRutaController(GeometriaRutaService geometriaRutaService) {
        this.geometriaRutaService = geometriaRutaService;
    }

    @GetMapping("/rutas/{codigoRuta}")
    public List<GeometriaRuta> obtenerGeometrias(
            @PathVariable String codigoRuta, HttpSession session) {
        if (session.getAttribute(SistemaTransporte.SESSION_ATTRIBUTE) != SistemaTransporte.TRANSCARIBE) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return geometriaRutaService.buscarPorRuta(codigoRuta);
    }
}

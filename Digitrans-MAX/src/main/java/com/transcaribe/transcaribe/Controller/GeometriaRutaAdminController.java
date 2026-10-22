package com.transcaribe.transcaribe.Controller;

import com.transcaribe.transcaribe.Model.GeometriaRuta;
import com.transcaribe.transcaribe.dto.GuardarGeometriaRutaRequest;
import com.transcaribe.transcaribe.service.GeometriaRutaService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/mapa-rutas/api")
public class GeometriaRutaAdminController {

    private final GeometriaRutaService geometriaRutaService;

    public GeometriaRutaAdminController(GeometriaRutaService geometriaRutaService) {
        this.geometriaRutaService = geometriaRutaService;
    }

    @PutMapping("/rutas/{codigoRuta}/{sentido}")
    public GeometriaRuta guardarGeometria(@PathVariable String codigoRuta,
                                           @PathVariable String sentido,
                                           @RequestBody GuardarGeometriaRutaRequest request) {
        return geometriaRutaService.guardar(codigoRuta, sentido, request);
    }

    @DeleteMapping("/rutas/{codigoRuta}/{sentido}")
    public void eliminarGeometria(@PathVariable String codigoRuta, @PathVariable String sentido) {
        geometriaRutaService.eliminar(codigoRuta, sentido);
    }
}

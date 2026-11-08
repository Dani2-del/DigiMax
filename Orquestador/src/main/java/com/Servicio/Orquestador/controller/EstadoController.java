package com.Servicio.Orquestador.controller;

import com.Servicio.Orquestador.config.ModulosConfig;
import com.Servicio.Orquestador.service.ProcesoService;
import com.Servicio.Orquestador.service.ProcesoService.EstadoDto;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

@RestController
@RequestMapping("/api")
public class EstadoController {

    private final ModulosConfig config;
    private final ProcesoService procesos;
    private final String apiToken;
    private final int timeoutSegundos;

    public EstadoController(ModulosConfig config, ProcesoService procesos,
                            @Value("${orquestador.api-token:}") String apiToken,
                            @Value("${orquestador.arranque-timeout-segundos:120}") int timeoutSegundos) {
        this.config = config;
        this.procesos = procesos;
        this.apiToken = apiToken;
        this.timeoutSegundos = timeoutSegundos;
    }

    @GetMapping("/estado")
    public List<EstadoDto> estado() {
        return procesos.resumen();
    }

    @PostMapping("/ui/servicios/iniciar-todos")
    public List<EstadoDto> iniciarTodosDesdePanel(
            @RequestHeader(value = "Origin", required = false) String origin,
            @RequestHeader(value = "Sec-Fetch-Site", required = false) String fetchSite) throws Exception {
        validarOrigenLocal(origin, fetchSite);
        for (var modulo : config.todos()) {
            procesos.iniciarManual(modulo.nombre());
            if (!procesos.esperarDisponible(modulo.nombre(), timeoutSegundos)) {
                throw new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT,
                        modulo.nombre() + " no respondio a tiempo en el puerto " + modulo.puerto());
            }
        }
        return procesos.resumen();
    }

    @PostMapping("/ui/servicios/detener-todos")
    public List<EstadoDto> detenerTodosDesdePanel(
            @RequestHeader(value = "Origin", required = false) String origin,
            @RequestHeader(value = "Sec-Fetch-Site", required = false) String fetchSite) {
        validarOrigenLocal(origin, fetchSite);
        procesos.detenerServicios();
        return procesos.resumen();
    }

    @PostMapping("/modulos/{nombre}/iniciar")
    public List<EstadoDto> iniciar(@PathVariable("nombre") String nombre,
                                   @RequestHeader(value = "X-Orquestador-Token", required = false) String token)
            throws IOException {
        validarToken(token);
        validar(nombre);
        procesos.iniciarManual(nombre);
        return procesos.resumen();
    }

    @PostMapping("/modulos/{nombre}/detener")
    public List<EstadoDto> detener(@PathVariable("nombre") String nombre,
                                   @RequestHeader(value = "X-Orquestador-Token", required = false) String token) {
        validarToken(token);
        validar(nombre);
        procesos.detener(nombre);
        return procesos.resumen();
    }

    @PostMapping("/modulos/{nombre}/reiniciar")
    public List<EstadoDto> reiniciar(@PathVariable("nombre") String nombre,
                                     @RequestHeader(value = "X-Orquestador-Token", required = false) String token)
            throws Exception {
        validarToken(token);
        validar(nombre);
        procesos.reiniciarManual(nombre);
        return procesos.resumen();
    }

    @PostMapping("/ui/modulos/{nombre}/reiniciar")
    public List<EstadoDto> reiniciarDesdePanel(
            @PathVariable("nombre") String nombre,
            @RequestHeader(value = "Origin", required = false) String origin,
            @RequestHeader(value = "Sec-Fetch-Site", required = false) String fetchSite) throws Exception {
        validarOrigenLocal(origin, fetchSite);
        validar(nombre);
        procesos.reiniciarManual(nombre);
        return procesos.resumen();
    }

    @PostMapping("/ui/modulos/{nombre}/detener")
    public List<EstadoDto> detenerDesdePanel(
            @PathVariable("nombre") String nombre,
            @RequestHeader(value = "Origin", required = false) String origin,
            @RequestHeader(value = "Sec-Fetch-Site", required = false) String fetchSite) {
        validarOrigenLocal(origin, fetchSite);
        validar(nombre);
        procesos.detener(nombre);
        return procesos.resumen();
    }

    @PostMapping("/ui/modulos/{nombre}/iniciar")
    public List<EstadoDto> iniciarDesdePanel(
            @PathVariable("nombre") String nombre,
            @RequestHeader(value = "Origin", required = false) String origin,
            @RequestHeader(value = "Sec-Fetch-Site", required = false) String fetchSite) throws IOException {
        validarOrigenLocal(origin, fetchSite);
        validar(nombre);
        procesos.iniciarManual(nombre);
        return procesos.resumen();
    }

    private void validar(String nombre) {
        if (!config.existe(nombre)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Módulo no existe: " + nombre);
        }
    }

    private void validarToken(String token) {
        if (apiToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Set ORQUESTADOR_API_TOKEN to enable manual module control.");
        }
        boolean valido = token != null && MessageDigest.isEqual(
                apiToken.getBytes(StandardCharsets.UTF_8), token.getBytes(StandardCharsets.UTF_8));
        if (!valido) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid Orchestrator token.");
        }
    }

    private void validarOrigenLocal(String origin, String fetchSite) {
        boolean origenPermitido = "http://localhost:8087".equals(origin)
                || "http://127.0.0.1:8087".equals(origin);
        if (!origenPermitido || (fetchSite != null && !"same-origin".equals(fetchSite))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solicitud local no válida.");
        }
    }
}

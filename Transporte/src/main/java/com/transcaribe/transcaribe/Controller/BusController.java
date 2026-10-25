package com.transcaribe.transcaribe.Controller;

import com.transcaribe.transcaribe.Model.Usuario;
import com.transcaribe.transcaribe.Model.Tarjeta;
import com.transcaribe.transcaribe.Model.SistemaTransporte;
import com.transcaribe.transcaribe.Model.RutaTransmilenio;
import com.transcaribe.transcaribe.Model.RutaTransmetro;
import com.transcaribe.transcaribe.Repository.BusRepository;
import com.transcaribe.transcaribe.Repository.UsuarioRepository;
import com.transcaribe.transcaribe.service.BusService;
import com.transcaribe.transcaribe.service.CatalogoRutasTransmilenioService;
import com.transcaribe.transcaribe.service.CatalogoRutasTransmetroService;
import com.transcaribe.transcaribe.service.PlataformaService;
import com.transcaribe.transcaribe.service.EmailService;
import com.transcaribe.transcaribe.Util.TarifaTranscaribe;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import jakarta.servlet.http.HttpSession;
import java.util.UUID;

@Controller
@RequestMapping("/api/buses")
public class BusController {

    @Autowired
    private BusService busService;

    @Autowired
    private PlataformaService service;

    @Autowired
    private BusRepository busRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private CatalogoRutasTransmilenioService catalogoRutasTransmilenioService;

    @Autowired
    private CatalogoRutasTransmetroService catalogoRutasTransmetroService;

    @Autowired
    private EmailService emailService;

    @PostMapping("/pagar/{usuarioId}/{busId}")
    @ResponseBody
    public ResponseEntity<String> pagar(
            @PathVariable String usuarioId, 
            @PathVariable String busId,
            @RequestParam String numeroTarjeta,
            @RequestParam(required = false) String operationId,
            HttpSession session) {
        try {
            SistemaTransporte sistema = obtenerSistemaActual(session);
            if (sistema == null) {
                return ResponseEntity.badRequest().body("Selecciona un sistema de transporte válido.");
            }
            if (sistema != SistemaTransporte.TRANSCARIBE) {
                return ResponseEntity.status(403)
                        .body("Las operaciones de buses están disponibles por ahora solo para Transcaribe.");
            }
            String resultado = busService.pagarPasajeConTarjetaEspecifica(
                    usuarioId, busId, numeroTarjeta, sistema,
                    operationId == null || operationId.isBlank() ? UUID.randomUUID().toString() : operationId);
            if (resultado.contains("está congelada")) {
                return ResponseEntity.status(423).body(resultado);
            }
            if (resultado.contains("Saldo insuficiente") || resultado.contains("no es válida")) {
                return ResponseEntity.badRequest().body(resultado);
            }
            try {
                Usuario usuarioDestino = service.buscarPorId(usuarioId);
                if (usuarioDestino != null) {
                    double costoPasaje = (double) TarifaTranscaribe.paraSistema(sistema);
                    String saldoRestante = resultado.split("\\$")[1]; 
                    emailService.enviarNotificacionGasto(
                        usuarioDestino.getCorreo(),
                        usuarioDestino.getNombre(),
                        costoPasaje,
                        saldoRestante
                    );

                    Tarjeta tarjetaUsada = usuarioDestino.getTarjetas().stream()
                        .filter(t -> t.getNumeroTarjeta().equals(numeroTarjeta)
                                && t.getSistemaTransporte() == sistema)
                        .findFirst()
                        .orElse(null);
                    if (tarjetaUsada != null) {
                        service.verificarYNotificarLimiteSaldo(usuarioDestino, tarjetaUsada);
                    }
                }
            } catch (Exception e) {
                System.err.println("Error al enviar el correo de ticket: " + e.getMessage());
            }         
            return ResponseEntity.ok(resultado);
            
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error al procesar el pago: " + e.getMessage());
        }
    }

    @PostMapping("/setup")
    @ResponseBody
    public ResponseEntity<String> setup(HttpSession session) {
        if (obtenerSistemaActual(session) != SistemaTransporte.TRANSCARIBE) {
            return ResponseEntity.status(403)
                    .body("La configuración de buses está disponible solo para Transcaribe.");
        }
        busService.crearBusDePrueba();
        return ResponseEntity.ok("Bus de prueba creado exitosamente con sus rutas originales");
    }
    @PostMapping("/simular-viaje")
    public String simularViaje(@RequestParam String usuarioId,
                               @RequestParam String numeroTarjeta, 
                               @RequestParam(required = false) String operationId,
                               RedirectAttributes redirectAttributes,
                               HttpSession session) {
        
        try {
            SistemaTransporte sistema = obtenerSistemaActual(session);
            if (sistema == null) {
                return "redirect:/menu";
            }
            if (sistema != SistemaTransporte.TRANSCARIBE
                    && sistema != SistemaTransporte.TRANSMILENIO
                    && sistema != SistemaTransporte.TRANSMETRO) {
                redirectAttributes.addFlashAttribute("error",
                        "Las operaciones de buses están disponibles por ahora solo para Transcaribe.");
                return "redirect:/menu";
            }
            usuarioId = usuarioRepository.findByCorreo(
                    SecurityContextHolder.getContext().getAuthentication().getName())
                    .orElseThrow(() -> new RuntimeException("Usuario no encontrado")).getId();
            String safeOperationId = operationId == null || operationId.isBlank()
                    ? UUID.randomUUID().toString()
                    : operationId;
            String resultado;
            if (sistema == SistemaTransporte.TRANSMILENIO) {
                RutaTransmilenio ruta = catalogoRutasTransmilenioService.obtenerRutaAleatoria().orElse(null);
                if (ruta == null) {
                    redirectAttributes.addFlashAttribute("error", "No hay rutas activas de TransMilenio configuradas.");
                    return "redirect:/menu";
                }
                resultado = busService.simularViajeTransmilenio(
                        usuarioId, numeroTarjeta, ruta, safeOperationId);
            } else if (sistema == SistemaTransporte.TRANSMETRO) {
                RutaTransmetro ruta = catalogoRutasTransmetroService.obtenerRutaAleatoriaActiva()
                        .orElse(null);
                if (ruta == null) {
                    redirectAttributes.addFlashAttribute("error", "No hay rutas activas de Transmetro configuradas.");
                    return "redirect:/menu";
                }
                resultado = busService.simularViajeTransmetro(
                        usuarioId, numeroTarjeta, ruta, safeOperationId);
            } else {
                var buses = busRepository.findAll().stream()
                        .filter(bus -> bus.getSistemaTransporte() == SistemaTransporte.TRANSCARIBE)
                        .toList();
                if (buses.isEmpty()) {
                    redirectAttributes.addFlashAttribute("error", "No hay buses configurados. Ejecuta el setup primero.");
                    return "redirect:/menu";
                }
                String busId = buses.get(0).getId();
                resultado = busService.pagarPasajeConTarjetaEspecifica(
                        usuarioId, busId, numeroTarjeta, sistema, safeOperationId);
            }

            if (resultado.contains("Cobro exitoso")) {
                try {
                    Usuario u = service.buscarPorId(usuarioId);
                    if (u != null) {
                        String saldoRestante = resultado.split("\\$")[1];
                        emailService.enviarNotificacionGasto(
                            u.getCorreo(), 
                            u.getNombre(), 
                            (double) TarifaTranscaribe.paraSistema(sistema),
                            saldoRestante
                        );

                        Tarjeta tarjetaUsada = u.getTarjetas().stream()
                            .filter(t -> t.getNumeroTarjeta().equals(numeroTarjeta)
                                    && t.getSistemaTransporte() == sistema)
                            .findFirst()
                            .orElse(null);
                        if (tarjetaUsada != null) {
                            service.verificarYNotificarLimiteSaldo(u, tarjetaUsada);
                        }
                    }
                } catch (Exception ex) {
                    System.err.println("Error correo simulación: " + ex.getMessage());
                }
                redirectAttributes.addFlashAttribute("mensaje", resultado + " ✅");
            } else if (resultado.startsWith("Pago ya procesado")) {
                redirectAttributes.addFlashAttribute("mensaje", resultado);
            } else {
                redirectAttributes.addFlashAttribute("error", resultado + " ❌");
            }

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error en la simulación: " + e.getMessage());
        }

        return "redirect:/menu";
    }

    private SistemaTransporte obtenerSistemaActual(HttpSession session) {
        Object sistema = session.getAttribute(SistemaTransporte.SESSION_ATTRIBUTE);
        return sistema instanceof SistemaTransporte seleccionado && seleccionado.estaDisponible()
                ? seleccionado
                : null;
    }
}

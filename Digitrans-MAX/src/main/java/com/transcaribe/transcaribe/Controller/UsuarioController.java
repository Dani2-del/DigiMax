package com.transcaribe.transcaribe.Controller;

import com.transcaribe.transcaribe.Model.Usuario;
import com.transcaribe.transcaribe.Model.SistemaTransporte;
import com.transcaribe.transcaribe.Model.Transaccion;
import com.transcaribe.transcaribe.Model.PreferenciaRutaNotificacion;
import com.transcaribe.transcaribe.Repository.UsuarioRepository;
import com.transcaribe.transcaribe.service.ReciboPdfService;
import com.transcaribe.transcaribe.service.PlataformaService;
import com.transcaribe.transcaribe.service.TransaccionService;
import com.transcaribe.transcaribe.service.BusService;
import com.transcaribe.transcaribe.service.AvisoAdministrativoService;
import com.transcaribe.transcaribe.Util.TarifaTranscaribe;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.server.ResponseStatusException;
import jakarta.servlet.http.HttpSession;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;

@Controller
public class UsuarioController {
    @Autowired
    private PlataformaService service;

    @Autowired
    private UsuarioRepository usuarioRepository;
    
    @Autowired
    private TransaccionService transaccionService;

    @Autowired
    private BusService busService;

    @Autowired
    private ReciboPdfService reciboPdfService;

    @Autowired
    private AvisoAdministrativoService avisoAdministrativoService;

    @ModelAttribute
    public void agregarTemaDelSistema(Model model, HttpSession session) {
        Object sistema = session.getAttribute(SistemaTransporte.SESSION_ATTRIBUTE);
        if (sistema instanceof SistemaTransporte sistemaActual && sistemaActual.estaDisponible()) {
            model.addAttribute("sistemaActual", sistemaActual);
            model.addAttribute("esTransmilenio", sistemaActual == SistemaTransporte.TRANSMILENIO);
            model.addAttribute("esTransmetro", sistemaActual == SistemaTransporte.TRANSMETRO);
            model.addAttribute("tieneRutasConfiguradas", sistemaActual.tieneRutasConfiguradas());
        }
    }

    private Usuario obtenerUsuarioLogueado() {
        String correo = SecurityContextHolder.getContext().getAuthentication().getName();
        return usuarioRepository.findByCorreo(correo).orElse(null);
    }

    @GetMapping("/menu")
    public String menu(
            Model model,
            HttpSession session) {
        Usuario usuario = obtenerUsuarioLogueado();
        
        if (usuario == null) {
            return "redirect:/login";
        }

        if (!usuario.isVerificado()) {
            return "redirect:/verificar-otp?correo=" + usuario.getCorreo();
        }

        boolean esModerador = Usuario.ROLE_MODERATOR.equals(usuario.getRole());
        boolean esAdmin = Usuario.ROLE_ADMIN.equals(usuario.getRole());
        Object sistemaEnSesion = session.getAttribute(SistemaTransporte.SESSION_ATTRIBUTE);
        SistemaTransporte sistemaAdmin = sistemaEnSesion instanceof SistemaTransporte seleccionado
                && seleccionado.estaDisponible() ? seleccionado : usuario.getSitm();
        SistemaTransporte sistemaCuenta = esModerador
                ? usuario.getSistemaAsignado()
                : esAdmin ? sistemaAdmin
                : usuario.getSitm();
        if (sistemaCuenta == null || !sistemaCuenta.estaDisponible()) {
            if (esModerador) {
                return "redirect:/error/403";
            }
            if (esAdmin) {
                sistemaCuenta = SistemaTransporte.TRANSCARIBE;
            } else {
                model.addAttribute("sistemasTransporte", SistemaTransporte.disponibles());
                model.addAttribute("usuario", usuario);
                return "usuarios/cuenta/seleccionar-sistema";
            }
        }
        SistemaTransporte sistemaActual = sistemaCuenta;
        session.setAttribute(SistemaTransporte.SESSION_ATTRIBUTE, sistemaActual);

        List<com.transcaribe.transcaribe.Model.Tarjeta> tarjetasSistema =
                usuario.getTarjetas().stream()
                        .filter(tarjeta -> tarjeta.getSistemaTransporte() == sistemaActual)
                        .toList();
        model.addAttribute("usuario", usuario);
        model.addAttribute("tarjetasSistemaActual", tarjetasSistema);
        model.addAttribute("saldoSistemaActual", tarjetasSistema.stream()
                .map(com.transcaribe.transcaribe.Model.Tarjeta::getSaldo)
                .filter(saldo -> saldo != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        model.addAttribute("sistemaActual", sistemaActual);
        model.addAttribute("esAdministradorGlobal", esAdmin);
        model.addAttribute("esTransmilenio", sistemaActual == SistemaTransporte.TRANSMILENIO);
        model.addAttribute("esTransmetro", sistemaActual == SistemaTransporte.TRANSMETRO);
        model.addAttribute("tieneRutasConfiguradas", sistemaActual.tieneRutasConfiguradas());
        model.addAttribute("permiteSimularViaje", sistemaActual.permiteSimularViaje());
        if (sistemaActual.permiteSimularViaje()) {
            model.addAttribute("valorPasaje", TarifaTranscaribe.paraSistema(sistemaActual));
        }
        model.addAttribute("sistemasTransporte", SistemaTransporte.disponibles());
        model.addAttribute("alertasAdmin", avisoAdministrativoService.obtenerPendientes(usuario.getId(), sistemaActual));
        model.addAttribute("alertaReturnTo", "/menu");
        return "usuarios/cuenta/menu";
    }

    @PostMapping("/menu/sistema")
    public String seleccionarSistema(@RequestParam SistemaTransporte sistema, HttpSession session) {
        Usuario usuario = obtenerUsuarioLogueado();
        if (usuario == null) {
            return "redirect:/login";
        }
        boolean administrador = Usuario.ROLE_ADMIN.equals(usuario.getRole());
        boolean primeraSeleccionUsuario = Usuario.ROLE_USER.equals(usuario.getRole())
                && (usuario.getSitm() == null || !usuario.getSitm().estaDisponible());
        if (!administrador && !primeraSeleccionUsuario) {
            return "redirect:/menu";
        }
        if (sistema == null || !sistema.estaDisponible()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sistema de transporte no disponible.");
        }
        if (primeraSeleccionUsuario) {
            usuario.setSitm(sistema);
            usuarioRepository.save(usuario);
        }
        session.setAttribute(SistemaTransporte.SESSION_ATTRIBUTE, sistema);
        return "redirect:/menu";
    }

    @GetMapping("/historial")
    public String historial(@RequestParam(defaultValue = "") String buscar,
                            @RequestParam(defaultValue = "") String tipo,
                            @RequestParam(defaultValue = "false") boolean buscarPorTarjeta,
                            @RequestParam(required = false) Integer tarjetaSeleccionada,
                            @RequestParam(defaultValue = "0") int page,
                            Model model,
                            HttpSession session) {
        Usuario usuario = obtenerUsuarioLogueado();
        
        if (usuario == null) {
            return "redirect:/login";
        }
        SistemaTransporte sistemaActual = obtenerSistemaActual(session);
        if (sistemaActual == null) {
            return "redirect:/menu";
        }

        List<com.transcaribe.transcaribe.Model.Tarjeta> tarjetasSistema =
                usuario.getTarjetas().stream()
                        .filter(tarjeta -> tarjeta.getSistemaTransporte() == sistemaActual)
                        .toList();
        boolean filtrarPorTarjeta = buscarPorTarjeta || tarjetaSeleccionada != null;
        model.addAttribute("usuario", usuario);
        model.addAttribute("tarjetasSistemaActual", tarjetasSistema);
        model.addAttribute("sistemaActual", sistemaActual);
        List<com.transcaribe.transcaribe.Model.Transaccion> filtradas =
                transaccionService.obtenerTransaccionesPorUsuario(usuario).stream()
                        .filter(t -> t.getSistemaTransporte() == sistemaActual)
                        .filter(t -> buscar.isBlank()
                                || (t.getTipo() != null && t.getTipo().toLowerCase(Locale.ROOT)
                                        .contains(buscar.trim().toLowerCase(Locale.ROOT))))
                        .filter(t -> !filtrarPorTarjeta
                                || (tarjetaSeleccionada != null
                                        && tarjetaSeleccionada >= 0
                                        && tarjetaSeleccionada < tarjetasSistema.size()
                                        && coincideTarjeta(
                                                t, tarjetasSistema.get(tarjetaSeleccionada).getNumeroTarjeta())))
                        .filter(t -> tipo.isBlank() || coincideTipo(t.getTipo(), tipo))
                        .toList();
        int totalPages = Math.max(1, (int) Math.ceil(filtradas.size() / 10.0));
        int paginaSegura = Math.min(Math.max(page, 0), totalPages - 1);
        int desde = paginaSegura * 10;
        int hasta = Math.min(desde + 10, filtradas.size());
        model.addAttribute("transacciones", filtradas.subList(desde, hasta));
        model.addAttribute("buscar", buscar);
        model.addAttribute("tipoSeleccionado", tipo);
        model.addAttribute("buscarPorTarjeta", filtrarPorTarjeta);
        model.addAttribute("tarjetaSeleccionada", tarjetaSeleccionada);
        model.addAttribute("currentPage", paginaSegura);
        model.addAttribute("totalPages", totalPages);
        return "usuarios/cuenta/historial";
    }

    private SistemaTransporte obtenerSistemaActual(HttpSession session) {
        Object sistema = session.getAttribute(SistemaTransporte.SESSION_ATTRIBUTE);
        return sistema instanceof SistemaTransporte seleccionado && seleccionado.estaDisponible()
                ? seleccionado
                : null;
    }

    private boolean coincideTarjeta(Transaccion transaccion, String numeroTarjeta) {
        if (coincideNumeroTarjeta(transaccion.getTarjetaTranscaribe(), numeroTarjeta)) {
            return true;
        }

        String tipoTransaccion = transaccion.getTipo();
        if (tipoTransaccion == null) {
            return false;
        }

        int inicioNumero = tipoTransaccion.indexOf('[');
        int finNumero = tipoTransaccion.indexOf(']', inicioNumero + 1);
        if (inicioNumero >= 0 && finNumero > inicioNumero) {
            return coincideNumeroTarjeta(
                    tipoTransaccion.substring(inicioNumero + 1, finNumero), numeroTarjeta);
        }

        int inicioTarjeta = tipoTransaccion.toLowerCase(Locale.ROOT).indexOf("tarjeta:");
        if (inicioTarjeta >= 0) {
            return coincideNumeroTarjeta(
                    tipoTransaccion.substring(inicioTarjeta + "tarjeta:".length()), numeroTarjeta);
        }

        int inicioPasaje = tipoTransaccion.toLowerCase(Locale.ROOT).indexOf("pasaje:");
        return inicioPasaje >= 0 && coincideNumeroTarjeta(
                tipoTransaccion.substring(inicioPasaje + "pasaje:".length()), numeroTarjeta);
    }

    private boolean coincideNumeroTarjeta(String numeroGuardado, String numeroTarjeta) {
        if (numeroGuardado == null || numeroTarjeta == null) {
            return false;
        }

        String numeroGuardadoNormalizado = numeroGuardado.replaceAll("\\D", "");
        String numeroTarjetaNormalizado = numeroTarjeta.replaceAll("\\D", "");
        if (numeroGuardadoNormalizado.isEmpty() || numeroTarjetaNormalizado.isEmpty()) {
            return false;
        }

        return numeroGuardado.contains("*")
                ? numeroTarjetaNormalizado.endsWith(numeroGuardadoNormalizado)
                : numeroGuardadoNormalizado.equals(numeroTarjetaNormalizado);
    }

    @GetMapping("/historial/{id}/recibo")
    public ResponseEntity<byte[]> descargarRecibo(
            @PathVariable String id, HttpSession session) throws IOException {
        Usuario usuario = obtenerUsuarioLogueado();
        if (usuario == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        SistemaTransporte sistemaActual = obtenerSistemaActual(session);
        if (sistemaActual == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }

        Transaccion transaccion = transaccionService.obtenerTransaccionDeUsuario(id, usuario)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (transaccion.getSistemaTransporte() != sistemaActual) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        byte[] pdf = reciboPdfService.generarRecibo(transaccion);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename("recibo-" + transaccion.getId() + ".pdf", StandardCharsets.UTF_8)
                .build());
        headers.setContentLength(pdf.length);
        headers.setCacheControl("no-store");
        return new ResponseEntity<>(pdf, headers, HttpStatus.OK);
    }

    private boolean coincideTipo(String tipoTransaccion, String filtro) {
        if (tipoTransaccion == null) {
            return false;
        }
        String tipoNormalizado = tipoTransaccion.toLowerCase(Locale.ROOT);
        return switch (filtro.toLowerCase(Locale.ROOT)) {
            case "recarga" -> tipoNormalizado.contains("recarga");
            case "pasaje" -> tipoNormalizado.contains("pasaje");
            default -> true;
        };
    }

    @GetMapping("/rutas")
    public String rutas(@RequestParam(defaultValue = "") String buscar,
                        @RequestParam(defaultValue = "0") int page,
                        Model model,
                        HttpSession session) {
        Usuario usuario = obtenerUsuarioLogueado();

        if (usuario == null) {
            return "redirect:/login";
        }
        if (!tieneRutasConfiguradas(session)) {
            return "redirect:/menu";
        }

        model.addAttribute("usuario", usuario);
        List<PreferenciaRutaNotificacion> rutasFavoritasConPreferencia = usuario.getRutasFavoritas().stream()
                .map(usuario::obtenerPreferenciaNotificacionRuta)
                .toList();
        model.addAttribute("rutasFavoritasConPreferencia", rutasFavoritasConPreferencia);
        List<String> rutas = busService.obtenerTodasLasRutas().stream()
                .filter(r -> buscar.isBlank() || r.toLowerCase(Locale.ROOT)
                        .contains(buscar.trim().toLowerCase(Locale.ROOT)))
                .toList();
        int totalPages = Math.max(1, (int) Math.ceil(rutas.size() / 20.0));
        int paginaSegura = Math.min(Math.max(page, 0), totalPages - 1);
        int desde = paginaSegura * 20;
        int hasta = Math.min(desde + 20, rutas.size());
        model.addAttribute("rutasDisponibles", rutas.subList(desde, hasta));
        model.addAttribute("buscar", buscar);
        model.addAttribute("currentPage", paginaSegura);
        model.addAttribute("totalPages", totalPages);
        return "usuarios/cuenta/rutas";
    }

    @PostMapping("/rutas/favorito/agregar")
    public String agregarFavorito(@RequestParam String ruta, HttpSession session) {
        Usuario usuario = obtenerUsuarioLogueado();

        if (usuario == null) {
            return "redirect:/login";
        }
        if (!tieneRutasConfiguradas(session)) {
            return "redirect:/menu";
        }

        usuario.agregarRutaFavorita(ruta);
        usuarioRepository.save(usuario);

        return "redirect:/rutas";
    }

    @PostMapping("/rutas/favorito/eliminar")
    public String eliminarFavorito(@RequestParam String ruta, HttpSession session) {
        Usuario usuario = obtenerUsuarioLogueado();

        if (usuario == null) {
            return "redirect:/login";
        }
        if (!tieneRutasConfiguradas(session)) {
            return "redirect:/menu";
        }

        usuario.eliminarRutaFavorita(ruta);
        usuarioRepository.save(usuario);

        return "redirect:/rutas";
    }

    @PostMapping("/rutas/favorito/notificaciones")
    public String guardarPreferenciaNotificacion(
            @RequestParam String ruta,
            @RequestParam(defaultValue = "false") boolean notificacionesActivas,
            @RequestParam String horaDesde,
            @RequestParam String horaHasta,
            org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes,
            HttpSession session) {
        Usuario usuario = obtenerUsuarioLogueado();
        if (usuario == null) {
            return "redirect:/login";
        }
        if (!tieneRutasConfiguradas(session)) {
            return "redirect:/menu";
        }
        if (!usuario.getRutasFavoritas().contains(ruta)) {
            redirectAttributes.addFlashAttribute("error", "Solo puedes configurar notificaciones para rutas favoritas.");
            return "redirect:/rutas";
        }

        try {
            LocalTime desde = LocalTime.parse(horaDesde);
            LocalTime hasta = LocalTime.parse(horaHasta);
            if (!desde.isBefore(hasta)) {
                redirectAttributes.addFlashAttribute("error",
                        "La hora inicial debe ser anterior a la hora final.");
                return "redirect:/rutas";
            }

            usuario.guardarPreferenciaNotificacionRuta(
                    new PreferenciaRutaNotificacion(ruta, notificacionesActivas, desde, hasta));
            usuarioRepository.save(usuario);
            redirectAttributes.addFlashAttribute("mensaje",
                    notificacionesActivas
                            ? "Horario de notificaciones guardado para " + ruta + "."
                            : "Notificaciones desactivadas para " + ruta + ".");
        } catch (DateTimeParseException e) {
            redirectAttributes.addFlashAttribute("error", "Selecciona un rango horario válido.");
        }
        return "redirect:/rutas";
    }

    private boolean tieneRutasConfiguradas(HttpSession session) {
        return session.getAttribute(SistemaTransporte.SESSION_ATTRIBUTE) == SistemaTransporte.TRANSCARIBE;
    }
}

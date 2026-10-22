package com.transcaribe.transcaribe.Controller;

import com.transcaribe.transcaribe.Model.Tarjeta;
import com.transcaribe.transcaribe.Model.SistemaTransporte;
import com.transcaribe.transcaribe.Model.Transaccion;
import com.transcaribe.transcaribe.Model.Usuario;
import com.transcaribe.transcaribe.Repository.UsuarioRepository;
import com.transcaribe.transcaribe.service.PlataformaService;
import com.transcaribe.transcaribe.service.TransaccionService;
import com.transcaribe.transcaribe.service.EmailService;
import com.transcaribe.transcaribe.Util.TarifaTranscaribe;

import java.util.Optional;
import java.util.List;
import java.math.BigDecimal;

import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Controlador principal para la gestión de tarjetas del sistema Transcaribe.
 * Administra vistas de saldo, recargas, límites de saldo mínimo y desvinculación de tarjetas.
 */
@Controller
public class TarjetaController {

    @Autowired
    private PlataformaService service;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private EmailService emailService;

    @Autowired
    private TransaccionService transaccionService;

    @ModelAttribute
    public void agregarTemaDelSistema(Model model, HttpSession session) {
        Object sistema = session.getAttribute(SistemaTransporte.SESSION_ATTRIBUTE);
        if (sistema instanceof SistemaTransporte sistemaActual && sistemaActual.estaDisponible()) {
            model.addAttribute("sistemaActual", sistemaActual);
            model.addAttribute("esTransmilenio", sistemaActual == SistemaTransporte.TRANSMILENIO);
            model.addAttribute("esTransmetro", sistemaActual == SistemaTransporte.TRANSMETRO);
        }
    }

    /**
     * Obtiene la instancia del usuario autenticado actualmente en la sesión.
     * 
     * @return Usuario logueado o null si no se halla en la base de datos.
     */
    private Usuario obtenerUsuarioLogueado() {
        String correo = SecurityContextHolder.getContext().getAuthentication().getName();
        return usuarioRepository.findByCorreo(correo).orElse(null);
    }

    /**
     * Renderiza el formulario de recarga de saldo.
     * 
     * @param model Modelo de Spring UI
     * @return Vista HTML 'recarga'
     */
    @GetMapping("/recarga")
    public String recarga(Model model, HttpSession session) {
        Usuario usuario = obtenerUsuarioLogueado();
        if (usuario == null) {
            return "redirect:/login";
        }
        SistemaTransporte sistema = obtenerSistemaActual(session);
        if (sistema == null) {
            return "redirect:/menu";
        }
        prepararDatosFormularioRecarga(model, usuario, sistema);
        model.addAttribute("cantidadPasajes", 1);
        return "usuarios/tarjetas/recarga";
    }

    /**
     * Procesa la solicitud POST de recarga mediante PSE o Tarjeta de Crédito.
     */
    @PostMapping("/recargar")
    public String recargar(
            @RequestParam(defaultValue = "RAPIDA") String modoRecarga,
            @RequestParam(required = false) Integer cantidadPasajes,
            @RequestParam(required = false) Long monto,
            @RequestParam String numeroTarjeta,
            @RequestParam String metodoPago,
            @RequestParam(required = false) String correoPSE,
            @RequestParam(required = false) String contrasenaPSE,
            @RequestParam(required = false) String numeroTarjetaPago,
            @RequestParam(required = false) String fechaVencimientoPago,
            @RequestParam(required = false) String cvvPago,
            Model model,
            HttpSession session) {

        Usuario usuario = obtenerUsuarioLogueado();
        if (usuario == null) {
            return "redirect:/login";
        }
        SistemaTransporte sistema = obtenerSistemaActual(session);
        if (sistema == null) {
            return "redirect:/menu";
        }

        boolean recargaRapida = "RAPIDA".equalsIgnoreCase(modoRecarga);
        if (recargaRapida && sistema == SistemaTransporte.TRANSMETRO) {
            model.addAttribute("error", "La recarga por cantidad de pasajes aún no está habilitada para Transmetro.");
            prepararFormularioRecarga(model, usuario, sistema, "NORMAL", cantidadPasajes, monto);
            return "usuarios/tarjetas/recarga";
        }
        if (!recargaRapida && !"NORMAL".equalsIgnoreCase(modoRecarga)) {
            model.addAttribute("error", "Selecciona un tipo de recarga válido.");
            prepararFormularioRecarga(model, usuario, sistema, modoRecarga, cantidadPasajes, monto);
            return "usuarios/tarjetas/recarga";
        }
        if (recargaRapida && (cantidadPasajes == null || cantidadPasajes <= 0)) {
            model.addAttribute("error", "La cantidad de pasajes debe ser al menos 1.");
            prepararFormularioRecarga(model, usuario, sistema, modoRecarga, cantidadPasajes, monto);
            return "usuarios/tarjetas/recarga";
        }
        if (!recargaRapida && (monto == null || monto <= 0)) {
            model.addAttribute("error", "El monto de la recarga debe ser mayor que cero.");
            prepararFormularioRecarga(model, usuario, sistema, modoRecarga, cantidadPasajes, monto);
            return "usuarios/tarjetas/recarga";
        }

        if (!"pse".equalsIgnoreCase(metodoPago) && !"tarjetaCredito".equalsIgnoreCase(metodoPago)) {
            model.addAttribute("error", "Selecciona un método de pago válido.");
            prepararFormularioRecarga(model, usuario, sistema, modoRecarga, cantidadPasajes, monto);
            return "usuarios/tarjetas/recarga";
        }

        // Validación de datos requeridos por pasarela
        if ("pse".equalsIgnoreCase(metodoPago)) {
            if (correoPSE == null || correoPSE.isBlank() || contrasenaPSE == null || contrasenaPSE.isBlank()) {
                model.addAttribute("error", "Por favor completa los datos de PSE.");
                prepararFormularioRecarga(model, usuario, sistema, modoRecarga, cantidadPasajes, monto);
                return "usuarios/tarjetas/recarga";
            }
        } else if ("tarjetaCredito".equalsIgnoreCase(metodoPago)) {
            if (numeroTarjetaPago == null || numeroTarjetaPago.isBlank() || 
                fechaVencimientoPago == null || fechaVencimientoPago.isBlank() || 
                cvvPago == null || cvvPago.isBlank()) {
                model.addAttribute("error", "Por favor completa los datos de la tarjeta de crédito.");
                prepararFormularioRecarga(model, usuario, sistema, modoRecarga, cantidadPasajes, monto);
                return "usuarios/tarjetas/recarga";
            }
        }

        // Ejecución del servicio de recarga
        boolean pagoPse = "pse".equalsIgnoreCase(metodoPago);
        String cuentaPse = pagoPse ? correoPSE.trim() : null;
        String metodoPagoRegistro = pagoPse ? "PSE" : "Tarjeta de crédito/débito";
        Optional<Transaccion> transaccion = recargaRapida
                ? service.recargarPasajesEnTarjetaConRecibo(
                        usuario, numeroTarjeta, cantidadPasajes, metodoPagoRegistro, cuentaPse, sistema)
                : service.recargarEnTarjetaConRecibo(
                        usuario, numeroTarjeta, monto.doubleValue(), metodoPagoRegistro, cuentaPse, sistema);

        if (transaccion.isEmpty()) {
            model.addAttribute("error", "Error en la recarga. Verifique los datos e intente de nuevo.");
            prepararFormularioRecarga(model, usuario, sistema, modoRecarga, cantidadPasajes, monto);
            return "usuarios/tarjetas/recarga";
        }

        // Envío asíncrono de comprobante de recarga por correo
        try {
            Usuario usuarioActualizado = usuarioRepository.findById(usuario.getId()).orElse(usuario);
            String saldoActual = usuarioActualizado.getTarjetas().stream()
                    .filter(t -> t.getNumeroTarjeta().equals(numeroTarjeta))
                    .findFirst()
                    .map(t -> t.getSaldo().toString())
                    .orElse("0.00");

            emailService.enviarNotificacionRecarga(
                    usuario.getCorreo(),
                    usuario.getNombre(),
                    transaccion.get().getMonto(),
                    saldoActual
            );
        } catch (Exception e) {
            System.err.println("Error al enviar notificación de recarga: " + e.getMessage());
        }

        model.addAttribute("transaccion", transaccion.get());
        model.addAttribute("usuario", usuario);
        model.addAttribute("sistemaActual", sistema);
        return "usuarios/tarjetas/Recarga-exitosa";
    }

    private void prepararFormularioRecarga(Model model, Usuario usuario, SistemaTransporte sistema,
            String modoRecarga,
            Integer cantidadPasajes, Long monto) {
        prepararDatosFormularioRecarga(model, usuario, sistema);
        model.addAttribute("modoRecarga", modoRecarga);
        model.addAttribute("cantidadPasajes", cantidadPasajes == null ? 1 : cantidadPasajes);
        model.addAttribute("monto", monto);
    }

    private void prepararDatosFormularioRecarga(Model model, Usuario usuario, SistemaTransporte sistema) {
        if (usuario != null) {
            model.addAttribute("usuario", usuario);
            model.addAttribute("tarjetas", obtenerTarjetasDelSistema(usuario, sistema));
        }
        model.addAttribute("sistemaActual", sistema);
        boolean esTransmetro = sistema == SistemaTransporte.TRANSMETRO;
        model.addAttribute("esTransmetro", esTransmetro);
        if (!esTransmetro) {
            model.addAttribute("valorPasaje", TarifaTranscaribe.paraSistema(sistema));
        }
    }

    /**
     * Renderiza la vista de administración de tarjetas del usuario.
     * 
     * @param model Modelo de Spring UI
     * @return Vista HTML 'tarjetas'
     */
    @GetMapping("/tarjetas")
    public String tarjetas(Model model, HttpSession session) {
        Usuario usuario = obtenerUsuarioLogueado();
        if (usuario == null) {
            return "redirect:/login";
        }
        SistemaTransporte sistema = obtenerSistemaActual(session);
        if (sistema == null) {
            return "redirect:/menu";
        }
        model.addAttribute("usuario", usuario);
        model.addAttribute("tarjetasSistemaActual", obtenerTarjetasDelSistema(usuario, sistema));
        model.addAttribute("sistemaActual", sistema);
        return "usuarios/tarjetas/tarjetas";
    }

    /**
     * Asocia un nuevo número de tarjeta al usuario en sesión.
     */
    @PostMapping("/tarjetas/agregar")
    public String agregarTarjeta(
            @RequestParam String nuevoNumero,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        Usuario usuario = obtenerUsuarioLogueado();
        if (usuario == null) {
            return "redirect:/login";
        }

        SistemaTransporte sistema = obtenerSistemaActual(session);
        if (sistema == null) {
            return "redirect:/menu";
        }

        String resultado = service.agregarNuevaTarjetaConValidacion(
                usuario.getId(), nuevoNumero, sistema);
        
        if ("OK".equals(resultado)) {
            redirectAttributes.addFlashAttribute("mensaje", "Tarjeta añadida correctamente ✅");
        } else {
            redirectAttributes.addFlashAttribute("error", resultado + " ❌");
        }
        return "redirect:/tarjetas";
    }

    /**
     * Elimina una tarjeta vinculada respetando el mínimo de una tarjeta por usuario.
     */
    @PostMapping("/tarjetas/eliminar")
    public String eliminarTarjeta(
            @RequestParam String numeroTarjeta, 
            RedirectAttributes redirectAttributes,
            HttpSession session) {

        Usuario usuario = obtenerUsuarioLogueado();
        if (usuario == null) {
            return "redirect:/login";
        }

        SistemaTransporte sistema = obtenerSistemaActual(session);
        if (sistema == null) {
            return "redirect:/menu";
        }
        boolean perteneceAlSistema = obtenerTarjetasDelSistema(usuario, sistema).stream()
                .anyMatch(tarjeta -> numeroTarjeta.equals(tarjeta.getNumeroTarjeta()));
        boolean eliminado = perteneceAlSistema
                && service.eliminarTarjetaDeUsuario(usuario.getId(), numeroTarjeta, sistema);
        
        if (eliminado) {
            redirectAttributes.addFlashAttribute("mensaje", "Tarjeta eliminada ✅");
        } else {
            redirectAttributes.addFlashAttribute("error", "No se pudo eliminar (debe quedar al menos una tarjeta) ❌");
        }
        return "redirect:/tarjetas";
    }

    /**
     * Renderiza el formulario para establecer la alerta de límite de saldo mínimo.
     */
    @GetMapping("/limite-saldo")
    public String mostrarFormularioLimiteSaldo(Model model, HttpSession session) {
        Usuario usuario = obtenerUsuarioLogueado();
        if (usuario == null) {
            return "redirect:/login";
        }
        SistemaTransporte sistema = obtenerSistemaActual(session);
        if (sistema == null) {
            return "redirect:/menu";
        }
        model.addAttribute("usuario", usuario);
        model.addAttribute("tarjetas", obtenerTarjetasDelSistema(usuario, sistema));
        model.addAttribute("sistemaActual", sistema);
        return "usuarios/tarjetas/limite-saldo";
    }

    /**
     * Endpoint HTTP POST para actualizar la alerta de saldo mínimo desde la interfaz Web.
     */
    @PostMapping("/limite-saldo")
    public String configurarLimiteSaldoWeb(
            @RequestParam String numeroTarjeta,
            @RequestParam BigDecimal limiteSaldo,
            RedirectAttributes redirectAttributes,
            HttpSession session) {

        Usuario usuario = obtenerUsuarioLogueado();
        if (usuario == null) {
            return "redirect:/login";
        }

        SistemaTransporte sistema = obtenerSistemaActual(session);
        if (sistema == null) {
            return "redirect:/menu";
        }
        boolean perteneceAlSistema = obtenerTarjetasDelSistema(usuario, sistema).stream()
                .anyMatch(tarjeta -> numeroTarjeta.equals(tarjeta.getNumeroTarjeta()));
        boolean exito = perteneceAlSistema
                && service.establecerLimiteSaldo(usuario, numeroTarjeta, limiteSaldo, sistema);

        if (exito) {
            redirectAttributes.addFlashAttribute("mensaje", "¡Alerta de saldo mínimo configurada correctamente! 🔔");
        } else {
            redirectAttributes.addFlashAttribute("error", "No se pudo configurar el límite para esta tarjeta. ❌");
        }

        return "redirect:/limite-saldo";
    }

    private SistemaTransporte obtenerSistemaActual(HttpSession session) {
        Object sistema = session.getAttribute(SistemaTransporte.SESSION_ATTRIBUTE);
        return sistema instanceof SistemaTransporte seleccionado && seleccionado.estaDisponible()
                ? seleccionado
                : null;
    }

    private List<Tarjeta> obtenerTarjetasDelSistema(Usuario usuario, SistemaTransporte sistema) {
        return usuario.getTarjetas().stream()
                .filter(tarjeta -> tarjeta.getSistemaTransporte() == sistema)
                .toList();
    }

    /**
     * Formatea el número de tarjeta enmascarando todos salvo los últimos 4 dígitos.
     */
    private String ocultarNumeroTarjeta(String numeroTarjeta) {
        if (numeroTarjeta == null || numeroTarjeta.length() < 4) {
            return "****";
        }
        return "****" + numeroTarjeta.substring(numeroTarjeta.length() - 4);
    }

    
}

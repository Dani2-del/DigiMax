package com.transcaribe.transcaribe.Controller;

import com.transcaribe.transcaribe.Model.Usuario;
import com.transcaribe.transcaribe.Model.SistemaTransporte;
import com.transcaribe.transcaribe.Repository.UsuarioRepository;
import com.transcaribe.transcaribe.service.AvisoAdministrativoService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import jakarta.servlet.http.HttpSession;

import java.util.List;

@Controller
@RequestMapping("/admin/avisos")
public class AvisoAdministrativoController {

    private final AvisoAdministrativoService avisoService;
    private final UsuarioRepository usuarioRepository;

    public AvisoAdministrativoController(AvisoAdministrativoService avisoService,
                                         UsuarioRepository usuarioRepository) {
        this.avisoService = avisoService;
        this.usuarioRepository = usuarioRepository;
    }

    @GetMapping
    public String mostrarPanel(Model model, Authentication authentication, HttpSession session) {
        SistemaTransporte sistema = sistemaActual(session);
        if (sistema == null) return "redirect:/admin/seleccionar-entidad";
        Usuario administrador = usuarioRepository.findByCorreo(authentication.getName())
                .orElseThrow(() -> new IllegalStateException("No se encontró la cuenta del administrador."));
        model.addAttribute("destinatarios", avisoService.obtenerDestinatariosElegibles(sistema));
        model.addAttribute("avisosRecientes", avisoService.obtenerAvisosRecientes(sistema));
        model.addAttribute("nombreAdministrador", administrador.getNombre());
        model.addAttribute("sistemaActual", sistema);
        return "admin/avisos";
    }

    @GetMapping("/historial")
    public String mostrarHistorial(Model model, HttpSession session) {
        SistemaTransporte sistema = sistemaActual(session);
        if (sistema == null) return "redirect:/admin/seleccionar-entidad";
        model.addAttribute("avisos", avisoService.obtenerHistorialAvisos(sistema));
        model.addAttribute("sistemaActual", sistema);
        return "admin/avisos-historial";
    }

    @PostMapping
    public String enviarAviso(@RequestParam String asunto,
                              @RequestParam String mensaje,
                              @RequestParam String tipoDestinatarios,
                              @RequestParam(required = false) List<String> roles,
                              @RequestParam(required = false) List<String> usuarios,
                              @RequestParam(name = "imagen", required = false) MultipartFile imagen,
                              RedirectAttributes redirectAttributes,
                              HttpSession session) {
        try {
            boolean paraTodos = "roles".equals(tipoDestinatarios);
            if (!paraTodos && !"seleccionados".equals(tipoDestinatarios)) {
                throw new IllegalArgumentException("Selecciona destinatarios por tipo de cuenta o de forma individual.");
            }
            AvisoAdministrativoService.ResultadoEnvio resultado =
                    avisoService.crearAviso(asunto, mensaje, paraTodos, roles, usuarios, imagen,
                            sistemaActual(session));
            String reporte = "Aviso publicado para " + resultado.totalDestinatarios()
                    + " cuenta(s). El envío de " + resultado.correosProgramados()
                    + " correo(s) continuará en segundo plano.";
            redirectAttributes.addFlashAttribute("mensajeAviso", reporte);
        } catch (IllegalArgumentException exception) {
            redirectAttributes.addFlashAttribute("errorAviso", exception.getMessage());
        }
        return "redirect:/admin/avisos";
    }

    private SistemaTransporte sistemaActual(HttpSession session) {
        Object sistema = session.getAttribute(SistemaTransporte.SESSION_ATTRIBUTE);
        return sistema instanceof SistemaTransporte seleccionado && seleccionado.estaDisponible()
                ? seleccionado : null;
    }
}

package com.Servicio.Acceso.web;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.Servicio.Acceso.model.AdminAuditEvent;
import com.Servicio.Acceso.model.Usuario;
import com.Servicio.Acceso.repository.AdminAuditEventRepository;
import com.Servicio.Acceso.repository.UsuarioRepository;
import com.Servicio.Acceso.service.GlobalOperationsService;

import org.springframework.security.core.Authentication;

@Controller
@RequestMapping("/admin/global")
public class GlobalAdminController {

    private static final int PAGE_SIZE = 25;
    private static final Set<String> ALLOWED_ROLES = Set.of(
            Usuario.ROLE_USER, Usuario.ROLE_ADMIN, Usuario.ROLE_MODERATOR, Usuario.ROLE_CONDUCTOR);
    private static final Set<String> MODERATOR_SERVICES = Set.of(
            "TRANSCARIBE", "TRANSMILENIO", "TRANSMETRO", "RECARGAS_MOVIL", "BILLETERA");

    private final UsuarioRepository usuarios;
    private final AdminAuditEventRepository auditEvents;
    private final GlobalOperationsService globalOperations;

    public GlobalAdminController(
            UsuarioRepository usuarios,
            AdminAuditEventRepository auditEvents,
            GlobalOperationsService globalOperations) {
        this.usuarios = usuarios;
        this.auditEvents = auditEvents;
        this.globalOperations = globalOperations;
    }

    @GetMapping
    public String dashboard(Model model, Authentication authentication) {
        model.addAttribute("totalUsuarios", usuarios.count());
        model.addAttribute("usuariosActivos", usuarios.countByActivoTrue());
        model.addAttribute("usuariosNoVerificados", usuarios.countByVerificadoFalse());
        model.addAttribute("totalAdministradores", usuarios.countByRole(Usuario.ROLE_ADMIN));
        model.addAttribute("totalModeradores", usuarios.countByRole(Usuario.ROLE_MODERATOR));
        var transactions = globalOperations.transactions(
                Set.of("TRANSCARIBE", "TRANSMILENIO", "TRANSMETRO", "BILLETERA", "RECARGAS_MOVIL"),
                null, null);
        model.addAttribute("totalTransaccionesGlobales", transactions.total());
        model.addAttribute("erroresTransaccionesGlobales", transactions.unavailableServices());
        model.addAttribute("administrador", authentication.getName());
        model.addAttribute("auditoriaReciente", auditEvents.findTop8ByOrderByCreatedAtDesc());
        return "admin/global-dashboard";
    }

    @GetMapping("/usuarios")
    public String usuarios(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "") String search,
            Model model) {
        int safePage = Math.max(0, page);
        String normalizedSearch = search.trim();
        Page<Usuario> result = normalizedSearch.isBlank()
                ? usuarios.findAll(PageRequest.of(safePage, PAGE_SIZE,
                        Sort.by(Sort.Direction.ASC, "nombre")))
                : usuarios.findByNombreContainingIgnoreCaseOrCorreoContainingIgnoreCase(
                        normalizedSearch, normalizedSearch,
                        PageRequest.of(safePage, PAGE_SIZE, Sort.by(Sort.Direction.ASC, "nombre")));
        model.addAttribute("usuarios", result.getContent().stream().map(UserRow::from).toList());
        model.addAttribute("currentPage", result.getNumber());
        model.addAttribute("totalPages", result.getTotalPages());
        model.addAttribute("totalElements", result.getTotalElements());
        model.addAttribute("search", normalizedSearch);
        model.addAttribute("roles", List.of(
                Usuario.ROLE_USER, Usuario.ROLE_MODERATOR, Usuario.ROLE_ADMIN, Usuario.ROLE_CONDUCTOR));
        model.addAttribute("moderatorServices", List.of(
                new ModeratorService("TRANSCARIBE", "Transcaribe"),
                new ModeratorService("TRANSMILENIO", "TransMilenio"),
                new ModeratorService("TRANSMETRO", "Transmetro"),
                new ModeratorService("RECARGAS_MOVIL", "Paquetes y recargas"),
                new ModeratorService("BILLETERA", "Billetera universal")));
        return "admin/global-usuarios";
    }

    @PostMapping("/usuarios/{id}")
    public String actualizarUsuario(
            @PathVariable String id,
            @RequestParam String nombre,
            @RequestParam String correo,
            @RequestParam String role,
            @RequestParam(defaultValue = "") String servicioAsignado,
            @RequestParam(defaultValue = "false") boolean activo,
            @RequestParam(defaultValue = "false") boolean primeraConfirmacion,
            @RequestParam(defaultValue = "false") boolean segundaConfirmacion,
            @RequestParam String motivo,
            Authentication authentication,
            RedirectAttributes redirectAttributes) {
        Usuario usuario = usuarios.findById(id).orElse(null);
        if (usuario == null) {
            redirectAttributes.addFlashAttribute("error", "No se encontró esa cuenta.");
            return "redirect:/admin/global/usuarios";
        }
        if (!primeraConfirmacion || !segundaConfirmacion) {
            redirectAttributes.addFlashAttribute("error",
                    "Debes confirmar dos veces antes de aplicar cambios administrativos.");
            return "redirect:/admin/global/usuarios";
        }
        if (!ALLOWED_ROLES.contains(role) || !StringUtils.hasText(nombre)
                || !StringUtils.hasText(correo) || !correo.contains("@")
                || !StringUtils.hasText(motivo)) {
            redirectAttributes.addFlashAttribute("error",
                    "Revisa el nombre, correo, rol y motivo antes de guardar.");
            return "redirect:/admin/global/usuarios";
        }
        String normalizedService = servicioAsignado.trim().toUpperCase(Locale.ROOT);
        if (Usuario.ROLE_MODERATOR.equals(role) && !MODERATOR_SERVICES.contains(normalizedService)) {
            redirectAttributes.addFlashAttribute("error",
                    "Selecciona el servicio que administrará esta cuenta moderadora.");
            return "redirect:/admin/global/usuarios";
        }

        boolean editingSelf = usuario.getCorreo().equalsIgnoreCase(authentication.getName());
        if (editingSelf && (!Usuario.ROLE_ADMIN.equals(role) || !activo)) {
            redirectAttributes.addFlashAttribute("error",
                    "No puedes quitarte a ti mismo el acceso de administrador.");
            return "redirect:/admin/global/usuarios";
        }
        String normalizedEmail = correo.trim().toLowerCase(Locale.ROOT);
        if (usuarios.findByCorreoIgnoreCase(normalizedEmail)
                .filter(existing -> !existing.getId().equals(id)).isPresent()) {
            redirectAttributes.addFlashAttribute("error", "Ese correo ya pertenece a otra cuenta.");
            return "redirect:/admin/global/usuarios";
        }

        String previous = String.format("nombre=%s, correo=%s, rol=%s, activo=%s",
                usuario.getNombre(), usuario.getCorreo(), usuario.getRole(), usuario.isActivo());
        String previousService = usuario.getServicioAsignado();
        String updatedService = Usuario.ROLE_MODERATOR.equals(role) ? normalizedService : null;
        String updated = String.format("nombre=%s, correo=%s, rol=%s, activo=%s, servicio=%s",
                nombre.trim(), normalizedEmail, role, activo, updatedService);
        usuario.setNombre(nombre.trim());
        usuario.setCorreo(normalizedEmail);
        usuario.setRole(role);
        usuario.setActivo(activo);
        usuario.setServicioAsignado(updatedService);
        Usuario saved = usuarios.save(usuario);

        AdminAuditEvent event = new AdminAuditEvent();
        event.setAdministratorId(currentAdministratorId(authentication));
        event.setAdministratorEmail(authentication.getName());
        event.setAction("ACTUALIZAR_USUARIO");
        event.setTargetId(saved.getId());
        event.setDetails("Antes: " + previous + ", servicio=" + previousService
                + ". Después: " + updated + ". Motivo: " + motivo.trim());
        event.setCreatedAt(LocalDateTime.now());
        auditEvents.save(event);

        redirectAttributes.addFlashAttribute("mensaje", "Cambios de usuario guardados y auditados.");
        return "redirect:/admin/global/usuarios";
    }

    private String currentAdministratorId(Authentication authentication) {
        return usuarios.findByCorreoIgnoreCase(authentication.getName())
                .map(Usuario::getId)
                .orElse("");
    }

    public record UserRow(String id, String nombre, String correo, String role, boolean activo,
                          boolean verificado, String telefono, String servicioAsignado,
                          String servicioAsignadoNombre) {
        private static UserRow from(Usuario usuario) {
            return new UserRow(usuario.getId(), usuario.getNombre(), usuario.getCorreo(),
                    usuario.getRole(), usuario.isActivo(), usuario.isVerificado(), usuario.getTelefono(),
                    usuario.getServicioAsignado(), serviceName(usuario.getServicioAsignado()));
        }

        private static String serviceName(String service) {
            if (service == null) return null;
            return switch (service) {
                case "TRANSCARIBE" -> "Transcaribe";
                case "TRANSMILENIO" -> "TransMilenio";
                case "TRANSMETRO" -> "Transmetro";
                case "RECARGAS_MOVIL" -> "Paquetes y recargas";
                case "BILLETERA" -> "Billetera universal";
                default -> service;
            };
        }
    }

    public record ModeratorService(String code, String name) {}
}

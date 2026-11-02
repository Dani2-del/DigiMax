package com.Servicio.Acceso.web;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
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
    private final MongoTemplate mongoTemplate;

    public GlobalAdminController(
            UsuarioRepository usuarios,
            AdminAuditEventRepository auditEvents,
            MongoTemplate mongoTemplate) {
        this.usuarios = usuarios;
        this.auditEvents = auditEvents;
        this.mongoTemplate = mongoTemplate;
    }

    @GetMapping
    public String dashboard(Model model, Authentication authentication) {
        model.addAttribute("totalUsuarios", usuarios.count());
        model.addAttribute("usuariosActivos", usuarios.countByActivoTrue());
        model.addAttribute("usuariosNoVerificados", usuarios.countByVerificadoFalse());
        model.addAttribute("totalAdministradores", usuarios.countByRole(Usuario.ROLE_ADMIN));
        model.addAttribute("totalModeradores", usuarios.countByRole(Usuario.ROLE_MODERATOR));
        model.addAttribute("administrador", authentication.getName());
        model.addAttribute("auditoriaReciente", auditEvents.findTop8ByOrderByCreatedAtDesc());
        return "admin/global-dashboard";
    }

    @GetMapping("/modelo-predictivo")
    public String modeloPredictivo(Model model) {
        org.bson.Document metricas = mongoTemplate.findOne(new Query(), org.bson.Document.class, "modelo_metricas");
        model.addAttribute("metricas", metricas);

        String coleccion = "predicciones_recarga";
        long alto = mongoTemplate.count(Query.query(Criteria.where("nivel_riesgo").is("Alto")), coleccion);
        long medio = mongoTemplate.count(Query.query(Criteria.where("nivel_riesgo").is("Medio")), coleccion);
        long bajo = mongoTemplate.count(Query.query(Criteria.where("nivel_riesgo").is("Bajo")), coleccion);
        model.addAttribute("conteoAlto", alto);
        model.addAttribute("conteoMedio", medio);
        model.addAttribute("conteoBajo", bajo);
        model.addAttribute("total", alto + medio + bajo);
        model.addAttribute("donutStyle", estiloDonutRiesgo(alto, medio, bajo));
        model.addAttribute("barrasF1", barrasF1(metricas));


        org.bson.Document referencia = mongoTemplate.findOne(new Query(), org.bson.Document.class, coleccion);
        model.addAttribute("semanaReferencia",
                referencia == null ? null : referencia.getString("semana_referencia"));

        return "admin/global-modelo-predictivo";
    }

    private static String estiloDonutRiesgo(long alto, long medio, long bajo) {
        long total = alto + medio + bajo;
        if (total == 0) {
            return "background: conic-gradient(#e9eef3 0% 100%)";
        }
        double a = alto * 100.0 / total;
        double m = medio * 100.0 / total;
        return String.format(Locale.US,
                "background: conic-gradient(#c2410c 0%% %.2f%%, #d97706 %.2f%% %.2f%%, #94a3b8 %.2f%% 100%%)",
                a, a, a + m, a + m);
    }

    private static List<Map<String, Object>> barrasF1(org.bson.Document metricas) {
        double f1 = numero(metricas, "f1");
        double base = numero(metricas, "f1_linea_base");
        double maximo = Math.max(Math.max(f1, base), 0.0001);
        List<Map<String, Object>> barras = new ArrayList<>();
        barras.add(barra("Modelo", f1, f1 / maximo * 100));
        barras.add(barra("Regla simple", base, base / maximo * 100));
        return barras;
    }

    private static Map<String, Object> barra(String etiqueta, double valor, double porcentaje) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("etiqueta", etiqueta);
        b.put("valor", String.format(Locale.US, "%.3f", valor));
        b.put("porcentaje", String.format(Locale.US, "%.2f", porcentaje));
        return b;
    }

    private static double numero(org.bson.Document doc, String clave) {
        if (doc == null) {
            return 0.0;
        }
        Object v = doc.get(clave);
        return v instanceof Number n ? n.doubleValue() : 0.0;
    }

    @GetMapping("/usuarios")
    public String usuarios(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "") String role,
            @RequestParam(defaultValue = "") String active,
            @RequestParam(defaultValue = "") String verified,
            Model model) {
        String normalizedSearch = search.trim();
        String normalizedRole = role.trim();
        String normalizedActive = active.trim();
        String normalizedVerified = verified.trim();
        boolean validFilters = (normalizedRole.isEmpty() || ALLOWED_ROLES.contains(normalizedRole))
                && (normalizedActive.isEmpty() || Set.of("true", "false").contains(normalizedActive))
                && (normalizedVerified.isEmpty() || Set.of("true", "false").contains(normalizedVerified));
        Query filter = new Query();
        if (!normalizedSearch.isBlank()) {
            String literalSearch = Pattern.quote(normalizedSearch);
            filter.addCriteria(new Criteria().orOperator(
                    Criteria.where("nombre").regex(literalSearch, "i"),
                    Criteria.where("correo").regex(literalSearch, "i")));
        }
        if (!normalizedRole.isEmpty() && ALLOWED_ROLES.contains(normalizedRole)) {
            filter.addCriteria(Criteria.where("role").is(normalizedRole));
        }
        if (Set.of("true", "false").contains(normalizedActive)) {
            filter.addCriteria(Criteria.where("activo").is(Boolean.valueOf(normalizedActive)));
        }
        if (Set.of("true", "false").contains(normalizedVerified)) {
            filter.addCriteria(Criteria.where("verificado").is(Boolean.valueOf(normalizedVerified)));
        }

        long totalElements = validFilters ? mongoTemplate.count(filter, Usuario.class) : 0;
        int totalPages = totalElements == 0 ? 0 : (int) Math.ceil((double) totalElements / PAGE_SIZE);
        int safePage = totalPages == 0 ? 0 : Math.min(Math.max(page, 0), totalPages - 1);
        List<Usuario> pageUsers = validFilters
                ? mongoTemplate.find(Query.of(filter)
                        .with(Sort.by(Sort.Direction.DESC, "fechaRegistro")
                                .and(Sort.by(Sort.Direction.DESC, "id")))
                        .skip((long) safePage * PAGE_SIZE)
                        .limit(PAGE_SIZE), Usuario.class)
                : List.of();

        model.addAttribute("usuarios", pageUsers.stream().map(UserRow::from).toList());
        model.addAttribute("currentPage", safePage);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("totalElements", totalElements);
        model.addAttribute("search", normalizedSearch);
        model.addAttribute("selectedRole", normalizedRole);
        model.addAttribute("selectedActive", normalizedActive);
        model.addAttribute("selectedVerified", normalizedVerified);
        if (!validFilters) {
            model.addAttribute("errorFiltro", "Uno de los filtros seleccionados no es válido.");
        }
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

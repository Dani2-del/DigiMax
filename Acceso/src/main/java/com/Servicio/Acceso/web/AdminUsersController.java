package com.Servicio.Acceso.web;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.Servicio.Acceso.model.Usuario;
import com.Servicio.Acceso.repository.UsuarioRepository;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@RestController
@RequestMapping("/api/admin/users")
public class AdminUsersController {

    private static final int MAX_PAGE_SIZE = 100;
    private static final Set<String> ALLOWED_ROLES = Set.of(
            Usuario.ROLE_USER, Usuario.ROLE_ADMIN, Usuario.ROLE_CONDUCTOR, Usuario.ROLE_MODERATOR);
    private static final Set<String> MODERATOR_SERVICES = Set.of(
            "TRANSCARIBE", "TRANSMILENIO", "TRANSMETRO", "RECARGAS_MOVIL", "BILLETERA");
    private static final Set<String> TRANSPORT_SERVICES = Set.of(
            "TRANSCARIBE", "TRANSMILENIO", "TRANSMETRO");

    private final MongoTemplate mongoTemplate;
    private final UsuarioRepository usuarios;

    public AdminUsersController(MongoTemplate mongoTemplate, UsuarioRepository usuarios) {
        this.mongoTemplate = mongoTemplate;
        this.usuarios = usuarios;
    }

    @GetMapping
    public ResponseEntity<UserPage> listUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "") String role,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) Boolean verified,
            @RequestParam(required = false) LocalDate registeredFrom,
            @RequestParam(required = false) LocalDate registeredTo,
            @RequestParam(defaultValue = "name") String sort) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE
                || (!role.isBlank() && !ALLOWED_ROLES.contains(role))
                || (!"name".equals(sort) && !"recent".equals(sort))
                || (registeredFrom != null && registeredTo != null
                        && registeredFrom.isAfter(registeredTo))) {
            return ResponseEntity.badRequest().build();
        }

        Query filter = new Query();
        String normalizedSearch = search.trim();
        if (!normalizedSearch.isEmpty()) {
            String literalSearch = Pattern.quote(normalizedSearch);
            filter.addCriteria(new Criteria().orOperator(
                    Criteria.where("nombre").regex(literalSearch, "i"),
                    Criteria.where("correo").regex(literalSearch, "i")));
        }
        if (!role.isBlank()) {
            filter.addCriteria(Criteria.where("role").is(role));
        }
        if (active != null) {
            filter.addCriteria(Criteria.where("activo").is(active));
        }
        if (verified != null) {
            filter.addCriteria(Criteria.where("verificado").is(verified));
        }
        if (registeredFrom != null || registeredTo != null) {
            Criteria dateRange = Criteria.where("fechaRegistro");
            if (registeredFrom != null) {
                dateRange.gte(registeredFrom.atStartOfDay());
            }
            if (registeredTo != null) {
                dateRange.lt(registeredTo.plusDays(1).atStartOfDay());
            }
            filter.addCriteria(dateRange);
        }

        long total = mongoTemplate.count(filter, Usuario.class);
        Sort resultSort = "recent".equals(sort)
                ? Sort.by(Sort.Direction.DESC, "fechaRegistro").and(Sort.by(Sort.Direction.ASC, "id"))
                : Sort.by(Sort.Direction.ASC, "nombre").and(Sort.by(Sort.Direction.ASC, "id"));
        Query pageQuery = Query.of(filter)
                .with(resultSort)
                .skip((long) page * size)
                .limit(size);
        List<UserSummary> content = mongoTemplate.find(pageQuery, Usuario.class).stream()
                .map(AdminUsersController::toSummary)
                .toList();
        int pages = total == 0 ? 0 : (int) Math.ceil((double) total / size);

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new UserPage(content, total, pages, page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserSummary> getUser(@PathVariable String id) {
        return usuarios.findById(id)
                .map(AdminUsersController::toSummary)
                .map(user -> ResponseEntity.ok()
                        .cacheControl(CacheControl.noStore())
                        .body(user))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/by-email")
    public ResponseEntity<UserSummary> getUserByEmail(@RequestParam String email) {
        if (!StringUtils.hasText(email)) {
            return ResponseEntity.badRequest().build();
        }
        return usuarios.findByCorreoIgnoreCase(email.trim())
                .map(AdminUsersController::toSummary)
                .map(user -> ResponseEntity.ok()
                        .cacheControl(CacheControl.noStore())
                        .body(user))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PatchMapping("/{id}")
    public ResponseEntity<?> updateUser(
            @PathVariable String id,
            @Valid @RequestBody UpdateUserRequest request,
            @AuthenticationPrincipal UserDetails administrator) {
        if (!ALLOWED_ROLES.contains(request.role())) {
            return ResponseEntity.badRequest().body(new ErrorResponse("El rol seleccionado no es válido."));
        }
        Usuario usuario = usuarios.findById(id).orElse(null);
        if (usuario == null) {
            return ResponseEntity.notFound().build();
        }
        boolean globalAdministrator = hasAuthority(administrator, Usuario.ROLE_ADMIN);
        boolean moderator = hasAuthority(administrator, Usuario.ROLE_MODERATOR);
        if (!globalAdministrator && !moderator) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new ErrorResponse("No tienes permiso para modificar esta cuenta."));
        }

        Usuario requestingUser = null;
        String moderatorService = null;
        if (!globalAdministrator) {
            requestingUser = usuarios.findByCorreoIgnoreCase(administrator.getUsername()).orElse(null);
            moderatorService = requestingUser == null ? null : requestingUser.getServicioAsignado();
            if (requestingUser == null
                    || !Usuario.ROLE_MODERATOR.equals(requestingUser.getRole())
                    || !StringUtils.hasText(moderatorService)
                    || !TRANSPORT_SERVICES.contains(moderatorService)
                    || Objects.equals(requestingUser.getId(), usuario.getId())
                    || Usuario.ROLE_ADMIN.equals(usuario.getRole())
                    || (Usuario.ROLE_MODERATOR.equals(usuario.getRole())
                        && !moderatorService.equals(usuario.getServicioAsignado()))) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(new ErrorResponse("Solo puedes cambiar roles dentro de tu servicio de transporte."));
            }
            if (!Set.of(Usuario.ROLE_USER, Usuario.ROLE_CONDUCTOR, Usuario.ROLE_MODERATOR)
                    .contains(request.role())
                    || !request.name().trim().equals(usuario.getNombre())
                    || !request.email().trim().equalsIgnoreCase(usuario.getCorreo())
                    || !request.active().equals(usuario.isActivo())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(new ErrorResponse("Los moderadores solo pueden cambiar roles de usuarios de su servicio."));
            }
            if (Usuario.ROLE_MODERATOR.equals(request.role())
                    && !moderatorService.equalsIgnoreCase(request.serviceAssignment())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(new ErrorResponse("Solo puedes asignar moderadores a tu servicio de transporte."));
            }
        }

        String serviceAssignment = StringUtils.hasText(request.serviceAssignment())
                ? request.serviceAssignment().trim().toUpperCase(Locale.ROOT)
                : usuario.getServicioAsignado();
        if (Usuario.ROLE_MODERATOR.equals(request.role())
                && (!StringUtils.hasText(serviceAssignment) || !MODERATOR_SERVICES.contains(serviceAssignment))) {
            return ResponseEntity.badRequest()
                    .body(new ErrorResponse("Selecciona un servicio válido para el moderador."));
        }

        String normalizedEmail = request.email().trim().toLowerCase(Locale.ROOT);
        boolean editingSelf = globalAdministrator
                && usuario.getCorreo().equalsIgnoreCase(administrator.getUsername());
        if (editingSelf && (!Usuario.ROLE_ADMIN.equals(request.role()) || !request.active())) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ErrorResponse("No puedes quitarte a ti mismo el acceso de administrador."));
        }
        if (usuarios.findByCorreoIgnoreCase(normalizedEmail)
                .filter(existing -> !existing.getId().equals(usuario.getId())).isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ErrorResponse("Ese correo ya está asociado a otra cuenta."));
        }

        usuario.setNombre(request.name().trim());
        usuario.setCorreo(normalizedEmail);
        usuario.setRole(request.role());
        usuario.setActivo(request.active());
        usuario.setServicioAsignado(Usuario.ROLE_MODERATOR.equals(request.role())
                ? serviceAssignment : null);
        try {
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.noStore())
                    .body(toSummary(usuarios.save(usuario)));
        } catch (DuplicateKeyException exception) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ErrorResponse("Ese correo ya está asociado a otra cuenta."));
        }
    }

    private static boolean hasAuthority(UserDetails user, String authority) {
        return user != null && user.getAuthorities().stream()
                .anyMatch(granted -> authority.equals(granted.getAuthority()));
    }

    private static UserSummary toSummary(Usuario usuario) {
        String role = StringUtils.hasText(usuario.getRole()) ? usuario.getRole() : Usuario.ROLE_USER;
        if (!role.startsWith("ROLE_")) {
            role = "ROLE_" + role;
        }
        return new UserSummary(usuario.getId(), usuario.getNombre(), usuario.getCorreo(), role,
                usuario.isActivo(), usuario.isVerificado(), usuario.getFechaRegistro(), usuario.getTelefono(),
                usuario.getServicioAsignado());
    }

    public record UserSummary(
            String id,
            String name,
            String email,
            String role,
            boolean active,
            boolean verified,
            LocalDateTime registeredAt,
            String phone,
            String serviceAssignment) {
    }

    public record UserPage(List<UserSummary> content, long totalElements, int totalPages, int page, int size) {
    }

    public record UpdateUserRequest(
            @NotBlank String name,
            @NotBlank @Email String email,
            @NotBlank String role,
            @NotNull Boolean active,
            String serviceAssignment) {
    }

    public record ErrorResponse(String message) {
    }
}

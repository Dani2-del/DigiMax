package com.transcaribe.transcaribe.Controller;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Pattern;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Sort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import com.transcaribe.transcaribe.Model.Usuario;
import com.transcaribe.transcaribe.Model.SistemaTransporte;
import com.transcaribe.transcaribe.Model.Transaccion;
import com.transcaribe.transcaribe.Repository.BusRepository;
import com.transcaribe.transcaribe.Repository.UsuarioRepository;
import com.transcaribe.transcaribe.service.ExcelReportService;
import com.transcaribe.transcaribe.service.PlataformaService;
import com.transcaribe.transcaribe.service.AvisoAdministrativoService;
import com.transcaribe.transcaribe.service.AccessIdentityClient;
import org.springframework.http.ContentDisposition;

@Controller
@RequestMapping("/admin")
public class AdminController {

    private final UsuarioRepository usuarioRepository;
    private final PlataformaService plataformaService;
    private final ExcelReportService excelService;
    private final BusRepository busRepository;
    private final MongoTemplate mongoTemplate;
    private final AccessIdentityClient accessIdentityClient;

    @Autowired
    private AvisoAdministrativoService avisoAdministrativoService;

    public AdminController(UsuarioRepository usuarioRepository,
                           PlataformaService plataformaService,
                           ExcelReportService excelService,
                           BusRepository busRepository,
                           MongoTemplate mongoTemplate,
                           AccessIdentityClient accessIdentityClient) {
        this.usuarioRepository = usuarioRepository;
        this.plataformaService = plataformaService;
        this.excelService = excelService;
        this.busRepository = busRepository;
        this.mongoTemplate = mongoTemplate;
        this.accessIdentityClient = accessIdentityClient;
    }

    private static final int TAMANO_PAGINA = 20;
    private static final String ENTIDAD_PENDIENTE_CONFIRMACION = "entidadPendienteConfirmacion";

    @GetMapping("/seleccionar-entidad")
    public String seleccionarEntidad(Model model,
                                     @RequestParam(defaultValue = "false") boolean confirmar,
                                     jakarta.servlet.http.HttpSession session,
                                     org.springframework.security.core.Authentication authentication) {
        Usuario administrador = usuarioRepository.findByCorreo(authentication.getName()).orElse(null);
        if (administrador == null || !Usuario.ROLE_ADMIN.equals(administrador.getRole())) {
            return "redirect:/error/403";
        }
        model.addAttribute("entidades", SistemaTransporte.disponibles());
        model.addAttribute("nombre", administrador.getNombre());
        Object pending = session.getAttribute(ENTIDAD_PENDIENTE_CONFIRMACION);
        if (confirmar && pending instanceof SistemaTransporte sistema) {
            model.addAttribute("requiereSegundaConfirmacion", true);
            model.addAttribute("entidadPendiente", sistema);
        } else {
            session.removeAttribute(ENTIDAD_PENDIENTE_CONFIRMACION);
            model.addAttribute("requiereSegundaConfirmacion", false);
        }
        return "admin/seleccionar-entidad";
    }

    @GetMapping("/chat/{sistema}")
    public String abrirChatDesdeConsolaGlobal(
            @PathVariable SistemaTransporte sistema,
            org.springframework.security.core.Authentication authentication,
            jakarta.servlet.http.HttpSession session) {
        Usuario administrador = usuarioRepository.findByCorreo(authentication.getName())
                .filter(Usuario::isActivo)
                .filter(usuario -> Usuario.ROLE_ADMIN.equals(usuario.getRole()))
                .orElse(null);
        if (administrador == null || sistema == null || !sistema.estaDisponible()) {
            return "redirect:/error/403";
        }
        session.setAttribute(SistemaTransporte.SESSION_ATTRIBUTE, sistema);
        return "redirect:/chat";
    }

    @PostMapping("/contexto")
    public String cambiarEntidadAdministrativa(@RequestParam SistemaTransporte sistema,
                                               jakarta.servlet.http.HttpSession session,
                                               @RequestParam(defaultValue = "false") boolean primeraConfirmacion,
                                               org.springframework.security.core.Authentication authentication) {
        Usuario administrador = usuarioRepository.findByCorreo(authentication.getName()).orElse(null);
        if (administrador == null || !Usuario.ROLE_ADMIN.equals(administrador.getRole())
                || sistema == null || !sistema.estaDisponible() || !primeraConfirmacion) {
            return "redirect:/admin/seleccionar-entidad?error=true";
        }
        session.setAttribute(ENTIDAD_PENDIENTE_CONFIRMACION, sistema);
        return "redirect:/admin/seleccionar-entidad?confirmar=true";
    }

    @PostMapping("/contexto/confirmar")
    public String confirmarCambioEntidad(@RequestParam SistemaTransporte sistema,
                                         @RequestParam(defaultValue = "false") boolean segundaConfirmacion,
                                         jakarta.servlet.http.HttpSession session,
                                         org.springframework.security.core.Authentication authentication) {
        Usuario administrador = usuarioRepository.findByCorreo(authentication.getName()).orElse(null);
        Object pending = session.getAttribute(ENTIDAD_PENDIENTE_CONFIRMACION);
        if (administrador == null || !Usuario.ROLE_ADMIN.equals(administrador.getRole())
                || !segundaConfirmacion || !(pending instanceof SistemaTransporte entidadPendiente)
                || entidadPendiente != sistema || !sistema.estaDisponible()) {
            session.removeAttribute(ENTIDAD_PENDIENTE_CONFIRMACION);
            return "redirect:/admin/seleccionar-entidad?error=true";
        }
        session.removeAttribute(ENTIDAD_PENDIENTE_CONFIRMACION);
        session.setAttribute(SistemaTransporte.SESSION_ATTRIBUTE, sistema);
        return "redirect:/admin/dashboard";
    }


    @GetMapping("/mapa-rutas/editar")
    public String editarGeometriasRuta() {
        return "admin/editar-rutas-mapa";
    }

    @GetMapping("/dashboard")
    public String mostrarDashboard(Model model,
                                   org.springframework.security.core.Authentication authentication,
                                   jakarta.servlet.http.HttpSession session) {
        SistemaTransporte sistemaActual = (SistemaTransporte) session.getAttribute(
                SistemaTransporte.SESSION_ATTRIBUTE);
        model.addAttribute("totalUsuarios", contarUsuariosSistema(sistemaActual));
        model.addAttribute("sistemaActual", sistemaActual);
        model.addAttribute("esTransmilenio", sistemaActual == SistemaTransporte.TRANSMILENIO);
        model.addAttribute("tieneRutasConfiguradas", sistemaActual.tieneRutasConfiguradas());
        model.addAttribute("permiteGestionConductores", sistemaActual.permiteGestionConductores());
        model.addAttribute("esAdministradorGlobal", authentication.getAuthorities().stream()
                .anyMatch(autoridad -> "ROLE_ADMIN".equals(autoridad.getAuthority())));
        usuarioRepository.findByCorreo(authentication.getName()).ifPresent(usuario ->
                model.addAttribute("alertasAdmin",
                        avisoAdministrativoService.obtenerPendientes(usuario.getId(), sistemaActual)));
        model.addAttribute("alertaReturnTo", "/admin/dashboard");
        return "admin/inicio";
    }

    @GetMapping("/usuarios")
    public String mostrarUsuarios(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "") String role,
            @RequestParam(defaultValue = "") String fechaDesde,
            @RequestParam(defaultValue = "") String fechaHasta,
            @RequestParam(defaultValue = "") String estado,
            @RequestParam(defaultValue = "") String verificacion,
            Model model,
            jakarta.servlet.http.HttpSession session,
            org.springframework.security.core.Authentication authentication) {
        SistemaTransporte sistemaActual = (SistemaTransporte) session.getAttribute(
                SistemaTransporte.SESSION_ATTRIBUTE);
        String busqueda = search == null ? "" : search.trim();
        LocalDate desde = parseFechaUsuario(fechaDesde);
        LocalDate hasta = parseFechaUsuario(fechaHasta);
        boolean filtrosValidos = true;

        if (!fechaDesde.isBlank() && desde == null) {
            model.addAttribute("errorFiltro", "La fecha inicial no tiene un formato válido.");
            filtrosValidos = false;
        }
        if (!fechaHasta.isBlank() && hasta == null) {
            model.addAttribute("errorFiltro", "La fecha final no tiene un formato válido.");
            filtrosValidos = false;
        }
        if (desde != null && hasta != null && desde.isAfter(hasta)) {
            model.addAttribute("errorFiltro", "La fecha inicial no puede ser posterior a la fecha final.");
            filtrosValidos = false;
        }

        Set<String> rolesValidos = Set.of(
                "", Usuario.ROLE_USER, Usuario.ROLE_CONDUCTOR, Usuario.ROLE_MODERATOR);
        if (!rolesValidos.contains(role)) {
            model.addAttribute("errorFiltro", "El rol seleccionado no es válido.");
            filtrosValidos = false;
        }
        if (!Set.of("", "activos", "inactivos").contains(estado)) {
            model.addAttribute("errorFiltro", "El estado de cuenta seleccionado no es válido.");
            filtrosValidos = false;
        }
        if (!Set.of("", "verificados", "pendientes").contains(verificacion)) {
            model.addAttribute("errorFiltro", "El estado de verificación seleccionado no es válido.");
            filtrosValidos = false;
        }

        String busquedaNormalizada = busqueda.toLowerCase(Locale.ROOT);
        List<Usuario> usuariosSistema = usuarioRepository.findAll().stream()
                .filter(AdminController::esPersonalTransporte)
                .filter(usuario -> perteneceASistema(usuario, sistemaActual))
                .toList();
        List<Usuario> usuariosFiltrados = filtrosValidos
                ? usuariosSistema.stream()
                        .filter(usuario -> busquedaNormalizada.isBlank()
                                || contieneIgnoreCase(usuario.getNombre(), busquedaNormalizada)
                                || contieneIgnoreCase(usuario.getCorreo(), busquedaNormalizada))
                        .filter(usuario -> role.isBlank() || role.equals(usuario.getRole()))
                        .filter(usuario -> estado.isBlank()
                                || ("activos".equals(estado) == usuario.isActivo()))
                        .filter(usuario -> verificacion.isBlank()
                                || ("verificados".equals(verificacion) == usuario.isVerificado()))
                        .filter(usuario -> desde == null || (usuario.getFechaRegistro() != null
                                && !usuario.getFechaRegistro().toLocalDate().isBefore(desde)))
                        .filter(usuario -> hasta == null || (usuario.getFechaRegistro() != null
                                && !usuario.getFechaRegistro().toLocalDate().isAfter(hasta)))
                        .sorted(Comparator.comparing(Usuario::getNombre,
                                Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                        .toList()
                : List.of();
        long totalFiltrado = usuariosFiltrados.size();
        int totalPaginas = Math.max(1, (int) Math.ceil((double) totalFiltrado / TAMANO_PAGINA));
        int paginaSegura = Math.min(Math.max(page, 0), totalPaginas - 1);
        int inicio = paginaSegura * TAMANO_PAGINA;
        int fin = Math.min(inicio + TAMANO_PAGINA, usuariosFiltrados.size());
        List<Usuario> usuarios = usuariosFiltrados.subList(inicio, fin);

        model.addAttribute("usuarios", usuarios);
        model.addAttribute("currentPage", paginaSegura);
        model.addAttribute("totalPages", totalPaginas);
        model.addAttribute("totalUsuarios", usuariosSistema.size());
        model.addAttribute("sistemaActual", sistemaActual);
        boolean administradorGlobal = authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
        boolean moderadorTransporte = authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_MODERATOR".equals(authority.getAuthority()));
        model.addAttribute("esAdministradorGlobal", administradorGlobal);
        model.addAttribute("puedeGestionarRoles", administradorGlobal || moderadorTransporte);
        model.addAttribute("sistemasTransporte", SistemaTransporte.disponibles());
        model.addAttribute("totalFiltrado", totalFiltrado);
        model.addAttribute("search", search != null ? search : "");
        model.addAttribute("tieneRutasConfiguradas", sistemaActual.tieneRutasConfiguradas());
        model.addAttribute("roleSeleccionado", role);
        model.addAttribute("fechaDesde", fechaDesde);
        model.addAttribute("fechaHasta", fechaHasta);
        model.addAttribute("estadoSeleccionado", estado);
        model.addAttribute("verificacionSeleccionada", verificacion);
        model.addAttribute("buses", busRepository.findAll().stream()
                .filter(bus -> bus.getSistemaTransporte() == sistemaActual).toList());
        return "admin/dashboard";
    }

    private long contarUsuariosSistema(SistemaTransporte sistemaActual) {
        try (var usuarios = usuarioRepository.streamBySistemaTransporte(sistemaActual)) {
            return usuarios.filter(AdminController::esPersonalTransporte).count();
        }
    }

    private static boolean esPersonalTransporte(Usuario usuario) {
        return Usuario.ROLE_USER.equals(usuario.getRole())
                || Usuario.ROLE_MODERATOR.equals(usuario.getRole())
                || Usuario.ROLE_CONDUCTOR.equals(usuario.getRole());
    }

    private Criteria criterioSistemaUsuario(SistemaTransporte sistemaActual) {
        List<Criteria> criterios = new ArrayList<>();
        criterios.add(new Criteria().andOperator(
                Criteria.where("role").is(Usuario.ROLE_MODERATOR),
                Criteria.where("sistemaAsignado").is(sistemaActual)));
        criterios.add(new Criteria().andOperator(
                Criteria.where("role").ne(Usuario.ROLE_MODERATOR),
                Criteria.where("sitm").is(sistemaActual)));
        // Los conductores creados antes de asignar SITM pertenecían a Transcaribe.
        if (sistemaActual == SistemaTransporte.TRANSCARIBE) {
            criterios.add(new Criteria().andOperator(
                    Criteria.where("role").is(Usuario.ROLE_CONDUCTOR),
                    Criteria.where("sitm").is(null)));
        }

        return new Criteria().orOperator(criterios.toArray(Criteria[]::new));
    }

    private static boolean contieneIgnoreCase(String valor, String busqueda) {
        return valor != null && valor.toLowerCase(Locale.ROOT).contains(busqueda);
    }

    private LocalDate parseFechaUsuario(String fecha) {
        if (fecha == null || fecha.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(fecha);
        } catch (DateTimeParseException exception) {
            return null;
        }
    }

    static Criteria criterioRangoFechaRegistro(LocalDate desde, LocalDate hasta) {
        Criteria rangoFecha = Criteria.where("fechaRegistro");
        if (desde != null) {
            rangoFecha.gte(desde.atStartOfDay());
        }
        if (hasta != null) {
            LocalDateTime limiteExclusivo = hasta.plusDays(1).atStartOfDay();
            rangoFecha.lt(limiteExclusivo);
        }
        return rangoFecha;
    }

    @GetMapping("/transacciones")
    public String mostrarTransacciones(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "") String usuario,
            @RequestParam(defaultValue = "") String tipo,
            @RequestParam(required = false) Double montoMin,
            @RequestParam(required = false) Double montoMax,
            Model model,
            jakarta.servlet.http.HttpSession session) {
        SistemaTransporte sistemaActual = (SistemaTransporte) session.getAttribute(
                SistemaTransporte.SESSION_ATTRIBUTE);
        int paginaSolicitada = Math.max(page, 0);
        Sort orden = Sort.by(Sort.Direction.DESC, "fecha");
        PageRequest pageable = PageRequest.of(paginaSolicitada, TAMANO_PAGINA, orden);

        String usuarioBuscado = usuario.trim();
        String tipoBuscado = tipo.trim().toLowerCase(Locale.ROOT);
        boolean rangoInvalido = (montoMin != null && montoMin < 0)
                || (montoMax != null && montoMax < 0)
                || (montoMin != null && montoMax != null && montoMin > montoMax);
        boolean tipoInvalido = !List.of("", "recarga", "pasaje", "otros").contains(tipoBuscado);
        List<Transaccion> contenido = List.of();
        long total = 0;

        if (!rangoInvalido && !tipoInvalido) {
            Query filtro = construirFiltroTransacciones(
                    usuarioBuscado, tipoBuscado, montoMin, montoMax, sistemaActual);
            total = mongoTemplate.count(filtro, Transaccion.class);
            int totalPaginas = (int) Math.ceil((double) total / TAMANO_PAGINA);
            if (totalPaginas > 0 && paginaSolicitada >= totalPaginas) {
                paginaSolicitada = totalPaginas - 1;
            }
            pageable = PageRequest.of(paginaSolicitada, TAMANO_PAGINA, orden);
            contenido = mongoTemplate.find(filtro.with(pageable), Transaccion.class);
        }

        Page<Transaccion> resultado = new PageImpl<>(contenido, pageable, total);
        model.addAttribute("transacciones", resultado.getContent());
        model.addAttribute("currentPage", resultado.getNumber());
        model.addAttribute("totalPages", resultado.getTotalPages());
        model.addAttribute("totalTransacciones", resultado.getTotalElements());
        model.addAttribute("sistemaActual", sistemaActual);
        model.addAttribute("usuarioBuscado", usuario);
        model.addAttribute("tipoBuscado", tipoBuscado);
        model.addAttribute("montoMin", montoMin);
        model.addAttribute("montoMax", montoMax);
        model.addAttribute("errorFiltro", rangoInvalido
                ? "Verifica los montos: deben ser positivos y el mínimo no puede superar el máximo."
                : tipoInvalido
                        ? "El tipo de transacción seleccionado no es válido."
                        : null);
        return "admin/transacciones";
    }

    private Query construirFiltroTransacciones(
            String usuario, String tipo, Double montoMin, Double montoMax,
            SistemaTransporte sistemaActual) {
        Query filtro = new Query();
        if (sistemaActual == SistemaTransporte.TRANSCARIBE) {
            filtro.addCriteria(new Criteria().orOperator(
                    Criteria.where("sistemaTransporte").is(SistemaTransporte.TRANSCARIBE),
                    Criteria.where("sistemaTransporte").is(null)));
        } else {
            filtro.addCriteria(Criteria.where("sistemaTransporte").is(sistemaActual));
        }
        if (!usuario.isBlank()) {
            List<String> usuarioIds = usuarioRepository
                    .findByNombreContainingIgnoreCaseOrCorreoContainingIgnoreCase(usuario, usuario)
                    .stream()
                    .map(Usuario::getId)
                    .filter(Objects::nonNull)
                    .toList();
            filtro.addCriteria(Criteria.where("usuario_id").in(usuarioIds));
        }
        switch (tipo) {
            case "recarga" -> filtro.addCriteria(Criteria.where("tipo").regex("recarga", "i"));
            case "pasaje" -> filtro.addCriteria(Criteria.where("tipo").regex("pasaje", "i"));
            case "otros" -> filtro.addCriteria(new Criteria().andOperator(
                    Criteria.where("tipo").not().regex("recarga", "i"),
                    Criteria.where("tipo").not().regex("pasaje", "i")));
            default -> { }
        }
        if (montoMin != null || montoMax != null) {
            Criteria monto = Criteria.where("monto");
            if (montoMin != null) monto.gte(montoMin);
            if (montoMax != null) monto.lte(montoMax);
            filtro.addCriteria(monto);
        }
        return filtro;
    }

    @GetMapping("/reporte/excel")
    public ResponseEntity<StreamingResponseBody> descargarExcel(
            @RequestParam(defaultValue = "todo") String tipo,
            @RequestParam(required = false) Integer anio,
            @RequestParam(required = false) Integer mes,
            @RequestParam(required = false) Integer dia,
            @RequestParam(required = false) String nombreReporte,
            @RequestParam(defaultValue = "true") boolean incluirUsuarios,
            @RequestParam(defaultValue = "true") boolean incluirTransacciones,
            @RequestParam(defaultValue = "false") boolean incluirRutas,
            @RequestParam(defaultValue = "false") boolean incluirAvisos,
            jakarta.servlet.http.HttpSession session) {

        if (!incluirUsuarios && !incluirTransacciones && !incluirRutas && !incluirAvisos) {
            return ResponseEntity.badRequest().build();
        }

        SistemaTransporte sistemaActual = (SistemaTransporte) session.getAttribute(
                SistemaTransporte.SESSION_ATTRIBUTE);
        if (sistemaActual == null || !sistemaActual.estaDisponible()) {
            return ResponseEntity.badRequest().build();
        }
        if (!sistemaActual.permiteGestionConductores() && incluirRutas) {
            return ResponseEntity.badRequest().build();
        }

        String nombrePredeterminado = switch (tipo) {
            case "anual"   -> "Reporte_" + sistemaActual.getNombre() + "_" + anio + ".xlsx";
            case "mensual" -> "Reporte_" + sistemaActual.getNombre() + "_" + anio + "-" + String.format("%02d", mes) + ".xlsx";
            case "diario"  -> "Reporte_" + sistemaActual.getNombre() + "_" + anio + "-" + String.format("%02d", mes) + "-" + String.format("%02d", dia) + ".xlsx";
            default        -> "Reporte_" + sistemaActual.getNombre() + ".xlsx";
        };
        String nombreArchivo = nombreReporte == null || nombreReporte.isBlank()
                ? nombrePredeterminado
                : nombreReporte.trim()
                        .replaceAll("[\\p{Cntrl}<>:\"/\\\\|?*]", "_")
                        .replaceAll("[. ]+$", "");
        if (nombreArchivo.length() > 100) {
            nombreArchivo = nombreArchivo.substring(0, 100).replaceAll("[. ]+$", "");
        }
        if (nombreArchivo.isBlank()) {
            nombreArchivo = "Reporte_" + sistemaActual.getNombre();
        }
        if (!nombreArchivo.toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            nombreArchivo += ".xlsx";
        }

        List<Usuario> directorioUsuarios = incluirUsuarios || incluirRutas || incluirAvisos
                ? usuarioRepository.findAll()
                : List.of();
        StreamingResponseBody file = out -> excelService.generarReporte(
                tipo, anio, mes, dia, incluirUsuarios, incluirTransacciones,
                incluirRutas, incluirAvisos, sistemaActual, directorioUsuarios, out);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(nombreArchivo, StandardCharsets.UTF_8)
                                .build().toString())
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(file);
    }

    @PostMapping("/charge")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> chargePassenger(
            @RequestParam String userId, @RequestParam double amount,
            jakarta.servlet.http.HttpSession session) {
        Map<String, Object> response = new HashMap<>();
        SistemaTransporte sistemaActual = (SistemaTransporte) session.getAttribute(
                SistemaTransporte.SESSION_ATTRIBUTE);
        if (sistemaActual == null || !sistemaActual.tieneRutasConfiguradas()) {
            response.put("status", "error");
            response.put("message", "El cobro de pasajes desde el panel está disponible solo para Transcaribe.");
            return ResponseEntity.status(403).body(response);
        }
        Usuario pasajero = usuarioRepository.findById(userId).orElse(null);
        if (pasajero == null || pasajero.getSitm() != sistemaActual) {
            response.put("status", "error");
            response.put("message", "El usuario no pertenece al servicio seleccionado.");
            return ResponseEntity.status(404).body(response);
        }
        boolean ok = plataformaService.cobrarPasajePorAdmin(userId, amount, sistemaActual);
        if (!ok) {
            response.put("status", "error");
            response.put("message", "Saldo insuficiente o usuario no encontrado");
            return ResponseEntity.badRequest().body(response);
        }
        Usuario actualizado = usuarioRepository.findById(userId).get();
        response.put("status", "ok");
        response.put("usuarioSaldo", actualizado.getSaldoTotal());
        if (!actualizado.getTarjetas().isEmpty())
            response.put("tarjetaSaldo", actualizado.getTarjetas().get(0).getSaldo());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/editUser")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> editUser(
            @RequestParam String userId,
            @RequestParam String nombre,
            @RequestParam String correo,
            @RequestParam String role,
            @RequestParam(required = false) SistemaTransporte sistemaAsignado,
            org.springframework.security.core.Authentication authentication,
            jakarta.servlet.http.HttpSession session) {
        Map<String, Object> response = new HashMap<>();
        try {
            Optional<Usuario> opt = usuarioRepository.findById(userId);
            SistemaTransporte sistemaActual = (SistemaTransporte) session.getAttribute(
                    SistemaTransporte.SESSION_ATTRIBUTE);
            boolean administradorGlobal = authentication.getAuthorities().stream()
                    .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
            boolean moderadorTransporte = authentication.getAuthorities().stream()
                    .anyMatch(authority -> "ROLE_MODERATOR".equals(authority.getAuthority()));
            Set<String> rolesPermitidos = Set.of(
                    Usuario.ROLE_USER, Usuario.ROLE_CONDUCTOR, Usuario.ROLE_MODERATOR);
            if (!rolesPermitidos.contains(role)) {
                response.put("status", "error");
                response.put("message", "El rol seleccionado no es válido.");
                return ResponseEntity.badRequest().body(response);
            }
            if (Usuario.ROLE_CONDUCTOR.equals(role)
                    && (sistemaActual == null || !sistemaActual.permiteGestionConductores())) {
                response.put("status", "error");
                response.put("message", "La gestión de conductores no está disponible para el servicio seleccionado.");
                return ResponseEntity.badRequest().body(response);
            }
            if (Usuario.ROLE_MODERATOR.equals(role)
                    && (sistemaAsignado == null || !sistemaAsignado.estaDisponible())) {
                response.put("status", "error");
                response.put("message", "Selecciona un servicio válido para el moderador.");
                return ResponseEntity.badRequest().body(response);
            }
            if (!administradorGlobal && sistemaAsignado != null && sistemaAsignado != sistemaActual) {
                response.put("status", "error");
                response.put("message", "Solo puedes asignar moderadores al servicio que estás gestionando.");
                return ResponseEntity.status(403).body(response);
            }
            if (opt.isPresent() && perteneceASistema(opt.get(), sistemaActual)) {
                Usuario u = opt.get();
                if (!esPersonalTransporte(u)) {
                    response.put("status", "error");
                    response.put("message", "Solo se pueden administrar moderadores y conductores del servicio.");
                    return ResponseEntity.status(404).body(response);
                }
                if (u.isModeradorExterno()) {
                    response.put("status", "error");
                    response.put("message",
                            "Las cuentas moderadoras de otros servicios no se pueden editar desde Transporte.");
                    return ResponseEntity.status(409).body(response);
                }
                if (!administradorGlobal && (!moderadorTransporte
                        || sistemaActual == null
                        || !Objects.equals(nombre, u.getNombre())
                        || !Objects.equals(correo, u.getCorreo())
                        || (Usuario.ROLE_MODERATOR.equals(role) && sistemaAsignado != sistemaActual)
                        || authentication.getName().equalsIgnoreCase(u.getCorreo()))) {
                    response.put("status", "error");
                    response.put("message",
                            "Solo puedes cambiar roles de usuarios de tu servicio y asignar moderadores a ese mismo servicio.");
                    return ResponseEntity.status(403).body(response);
                }
                boolean cambioRol = !Objects.equals(role, u.getRole());
                if (administradorGlobal || (moderadorTransporte && cambioRol)) {
                    accessIdentityClient.updateUser(userId, nombre, correo, role, u.isActivo(),
                            Usuario.ROLE_MODERATOR.equals(role) ? sistemaAsignado.name() : null);
                }
                if (administradorGlobal) {
                    u.setNombre(nombre);
                    u.setCorreo(correo);
                }
                boolean eraModerador = Usuario.ROLE_MODERATOR.equals(u.getRole());
                u.setRole(role);
                if (Usuario.ROLE_CONDUCTOR.equals(role)) {
                    u.setSitm(sistemaActual);
                    if (sistemaActual != SistemaTransporte.TRANSCARIBE) {
                        u.setBusAsignado(null);
                    }
                }
                u.setSistemaAsignado(Usuario.ROLE_MODERATOR.equals(role) ? sistemaAsignado : null);
                if (eraModerador && !Usuario.ROLE_MODERATOR.equals(role) && u.getSitm() == null) {
                    u.setSitm(sistemaActual);
                }

                usuarioRepository.save(u);
                response.put("status", "ok");
                return ResponseEntity.ok(response);
            } else {
                response.put("status", "error");
                response.put("message", "Usuario no encontrado");
                return ResponseEntity.status(404).body(response);
            }
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", "Error: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    @PostMapping("/deleteUser")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> deleteUser(
            @RequestParam String userId,
            org.springframework.security.core.Authentication authentication,
            jakarta.servlet.http.HttpSession session) {
        Map<String, Object> response = new HashMap<>();
        try {
            boolean administradorGlobal = authentication.getAuthorities().stream()
                    .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
            if (!administradorGlobal) {
                response.put("status", "error");
                response.put("message", "Solo un administrador global puede desactivar cuentas.");
                return ResponseEntity.status(403).body(response);
            }
            SistemaTransporte sistemaActual = (SistemaTransporte) session.getAttribute(
                    SistemaTransporte.SESSION_ATTRIBUTE);
            Usuario usuario = usuarioRepository.findById(userId).orElse(null);
            if (!perteneceASistema(usuario, sistemaActual)) {
                response.put("status", "error");
                response.put("message", "El usuario no pertenece al servicio seleccionado.");
                return ResponseEntity.status(404).body(response);
            }
            usuarioRepository.delete(usuario);
            response.put("status", "ok");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("status", "error");
            return ResponseEntity.status(500).body(response);
        }
    }

    private boolean perteneceASistema(Usuario usuario, SistemaTransporte sistemaActual) {
        return usuario != null && sistemaActual != null
                && (Usuario.ROLE_MODERATOR.equals(usuario.getRole())
                    ? usuario.getSistemaAsignado() == sistemaActual
                    : usuario.getSitm() == sistemaActual
                        || (Usuario.ROLE_CONDUCTOR.equals(usuario.getRole())
                            && usuario.getSitm() == null
                            && sistemaActual == SistemaTransporte.TRANSCARIBE));
    }
}

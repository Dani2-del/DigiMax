package com.transcaribe.transcaribe.Controller;
 
import com.transcaribe.transcaribe.Model.Bus;
import com.transcaribe.transcaribe.Model.HorarioConductor;
import com.transcaribe.transcaribe.Model.Usuario;
import com.transcaribe.transcaribe.Model.SistemaTransporte;
import com.transcaribe.transcaribe.Repository.BusRepository;
import com.transcaribe.transcaribe.Repository.HorarioConductorRepository;
import com.transcaribe.transcaribe.Repository.UsuarioRepository;
import com.transcaribe.transcaribe.service.EmailService;
import com.transcaribe.transcaribe.service.CatalogoRutasTransmilenioService;
import com.transcaribe.transcaribe.service.CatalogoRutasTransmetroService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
 
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
 
@Controller
@RequestMapping("/admin/horarios")
public class HorarioController {

    private static final int TAMANO_PAGINA_HISTORIAL = 5;
 
    @Autowired
    private HorarioConductorRepository horarioRepository;
 
    @Autowired
    private UsuarioRepository usuarioRepository;
 
    @Autowired
    private BusRepository busRepository;

    @Autowired
    private EmailService emailService;

    @Autowired
    private CatalogoRutasTransmilenioService catalogoRutasTransmilenioService;

    @Autowired
    private CatalogoRutasTransmetroService catalogoRutasTransmetroService;
 
    @GetMapping
    public String menuHorarios(Model model, HttpSession session,
                               org.springframework.security.core.Authentication authentication) {
        SistemaTransporte sistema = sistemaActual(session);
        model.addAttribute("sistemaActual", sistema);
        model.addAttribute("esTransmilenio", sistema == SistemaTransporte.TRANSMILENIO);
        model.addAttribute("esTransmetro", sistema == SistemaTransporte.TRANSMETRO);
        boolean esModerador = authentication != null && usuarioRepository.findByCorreo(authentication.getName())
                .map(usuario -> Usuario.ROLE_MODERATOR.equals(usuario.getRole())).orElse(false);
        model.addAttribute("esModerador", esModerador);
        return "admin/horarios";
    }

    @GetMapping("/programar")
    public String verHorarios(@RequestParam(defaultValue = "") String buscarRuta,
                              @RequestParam(defaultValue = "") String conductorId,
                              @RequestParam(defaultValue = "") String periodo,
                              @RequestParam(required = false) String fechaFiltro,
                              @RequestParam(defaultValue = "0") int page,
                              Model model, HttpSession session) {
        SistemaTransporte sistema = sistemaActual(session);
        List<Usuario> conductores = usuarioRepository.findByRoleAndActivoTrue(Usuario.ROLE_CONDUCTOR)
                .stream().filter(c -> perteneceAlSistema(c, sistema)).toList();
        List<Bus> buses = busRepository.findAll().stream()
                .filter(bus -> bus.getSistemaTransporte() == sistema && bus.isActivo()).toList();
 
        Map<String, String> nombreConductor = new HashMap<>();
        for (Usuario c : conductores) {
            nombreConductor.put(c.getId(), c.getNombre() != null ? c.getNombre() : c.getCorreo());
        }

        Map<String, List<String>> busRutasPorId = new HashMap<>();
        for (Bus b : buses) {
            busRutasPorId.put(b.getId(), b.getRutas() != null ? b.getRutas() : new ArrayList<>());
        }

        model.addAttribute("conductores", conductores);
        model.addAttribute("buses", buses);
        model.addAttribute("sistemaActual", sistema);
        model.addAttribute("esTransmilenio", sistema == SistemaTransporte.TRANSMILENIO);
        model.addAttribute("esTransmetro", sistema == SistemaTransporte.TRANSMETRO);
        model.addAttribute("esSistemaTroncal", sistema == SistemaTransporte.TRANSMILENIO
                || sistema == SistemaTransporte.TRANSMETRO);
        List<String> rutasDisponibles = sistema == SistemaTransporte.TRANSMILENIO
                ? catalogoRutasTransmilenioService.obtenerRutasActivas().stream()
                        .map(com.transcaribe.transcaribe.Model.RutaTransmilenio::getEtiqueta).toList()
                : sistema == SistemaTransporte.TRANSMETRO
                ? catalogoRutasTransmetroService.obtenerRutasActivas().stream()
                        .map(com.transcaribe.transcaribe.Model.RutaTransmetro::getEtiqueta).toList()
                : busRepository.findAll().stream()
                        .filter(bus -> bus.getSistemaTransporte() == sistema)
                        .filter(bus -> bus.getRutas() != null)
                        .flatMap(bus -> bus.getRutas().stream()).distinct().sorted().toList();
        model.addAttribute("rutasDisponibles", rutasDisponibles);
        List<HorarioConductor> horariosActivos = horarioRepository.findAllByOrderByFechaDescHoraInicioDesc()
                .stream()
                .filter(h -> h.getSistemaTransporte() == sistema)
                .filter(h -> !HorarioConductor.ESTADO_DESACTIVADA.equals(h.getEstado()))
                .toList();
        LocalDate fechaReferencia = parseFecha(fechaFiltro);
        List<HorarioConductor> horariosFiltrados = horariosActivos.stream()
                .filter(h -> buscarRuta.isBlank() || (h.getRuta() != null
                        && h.getRuta().toLowerCase().contains(buscarRuta.trim().toLowerCase())))
                .filter(h -> conductorId.isBlank() || conductorId.equals(h.getConductorId()))
                .filter(h -> coincidePeriodo(h.getFecha(), periodo, fechaReferencia))
                .toList();
        int totalPages = Math.max(1, (int) Math.ceil(horariosFiltrados.size() / 5.0));
        int paginaSegura = Math.min(Math.max(page, 0), totalPages - 1);
        int desde = paginaSegura * 5;
        int hasta = Math.min(desde + 5, horariosFiltrados.size());
        model.addAttribute("horarios", horariosFiltrados.subList(desde, hasta));
        model.addAttribute("buscarRuta", buscarRuta);
        model.addAttribute("conductorSeleccionado", conductorId);
        model.addAttribute("periodoSeleccionado", periodo);
        model.addAttribute("fechaFiltro", fechaFiltro == null ? "" : fechaFiltro);
        model.addAttribute("currentPage", paginaSegura);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("nombreConductor", nombreConductor);
        model.addAttribute("busRutasPorId", busRutasPorId);
 
        return "admin/programar-ruta";
    }

    @GetMapping("/buses")
    public String verBuses(@RequestParam(defaultValue = "") String buscarBus,
                           @RequestParam(defaultValue = "") String tipoBus,
                           @RequestParam(defaultValue = "0") int busPage,
                           Model model, HttpSession session,
                           org.springframework.security.core.Authentication authentication) {
        SistemaTransporte sistema = sistemaActual(session);
        boolean esModerador = authentication != null && usuarioRepository.findByCorreo(authentication.getName())
                .map(usuario -> Usuario.ROLE_MODERATOR.equals(usuario.getRole())).orElse(false);
        List<Bus> busesFiltrados = busRepository.findAll().stream()
                .filter(bus -> bus.getSistemaTransporte() == sistema)
                .filter(bus -> buscarBus.isBlank() || (bus.getPlaca() != null
                        && bus.getPlaca().toLowerCase().contains(buscarBus.trim().toLowerCase())))
                .filter(bus -> tipoBus.isBlank() || (bus.getTipo() != null
                        && tipoBus.trim().equalsIgnoreCase(bus.getTipo().trim())))
                .toList();
        int totalPaginas = Math.max(1, (int) Math.ceil(busesFiltrados.size() / 5.0));
        int paginaSegura = Math.min(Math.max(busPage, 0), totalPaginas - 1);
        int desde = paginaSegura * 5;
        int hasta = Math.min(desde + 5, busesFiltrados.size());

        model.addAttribute("busesRegistrados", busesFiltrados.subList(desde, hasta));
        model.addAttribute("sistemaActual", sistema);
        model.addAttribute("esTransmilenio", sistema == SistemaTransporte.TRANSMILENIO);
        model.addAttribute("esTransmetro", sistema == SistemaTransporte.TRANSMETRO);
        model.addAttribute("esSistemaTroncal", sistema == SistemaTransporte.TRANSMILENIO
                || sistema == SistemaTransporte.TRANSMETRO);
        model.addAttribute("esModerador", esModerador);
        List<String> rutasDisponibles = sistema == SistemaTransporte.TRANSMILENIO
                ? catalogoRutasTransmilenioService.obtenerRutasActivas().stream()
                        .map(com.transcaribe.transcaribe.Model.RutaTransmilenio::getEtiqueta).toList()
                : sistema == SistemaTransporte.TRANSMETRO
                ? catalogoRutasTransmetroService.obtenerRutasActivas().stream()
                        .map(com.transcaribe.transcaribe.Model.RutaTransmetro::getEtiqueta).toList()
                : busRepository.findAll().stream()
                        .filter(bus -> bus.getSistemaTransporte() == sistema)
                        .filter(bus -> bus.getRutas() != null)
                        .flatMap(bus -> bus.getRutas().stream()).distinct().sorted().toList();
        model.addAttribute("rutasDisponibles", rutasDisponibles);
        model.addAttribute("buscarBus", buscarBus);
        model.addAttribute("tipoBusSeleccionado", tipoBus);
        model.addAttribute("currentBusPage", paginaSegura);
        model.addAttribute("totalBusPages", totalPaginas);
        return "admin/buses";
    }

    private boolean coincidePeriodo(LocalDate fecha, String periodo, LocalDate referencia) {
        if (periodo == null || periodo.isBlank() || referencia == null || fecha == null) {
            return periodo == null || periodo.isBlank();
        }
        return switch (periodo) {
            case "dia" -> fecha.equals(referencia);
            case "semana" -> {
                WeekFields semanas = WeekFields.ISO;
                yield fecha.get(semanas.weekBasedYear()) == referencia.get(semanas.weekBasedYear())
                        && fecha.get(semanas.weekOfWeekBasedYear()) == referencia.get(semanas.weekOfWeekBasedYear());
            }
            case "mes" -> fecha.getYear() == referencia.getYear() && fecha.getMonth() == referencia.getMonth();
            default -> true;
        };
    }

    @GetMapping("/historial")
    public String verHistorial(@RequestParam(required = false) String conductorId,
                               @RequestParam(required = false) String ruta,
                               @RequestParam(required = false) String fechaDesde,
                               @RequestParam(required = false) String fechaHasta,
                               @RequestParam(defaultValue = "0") int page,
                               Model model, HttpSession session) {
        SistemaTransporte sistema = sistemaActual(session);
        List<Usuario> conductores = usuarioRepository.findByRoleAndActivoTrue(Usuario.ROLE_CONDUCTOR)
                .stream().filter(c -> perteneceAlSistema(c, sistema)).toList();
        List<HorarioConductor> historial = horarioRepository.findAllByOrderByFechaAscHoraInicioAsc();
        LocalDate desde = parseFecha(fechaDesde);
        LocalDate hasta = parseFecha(fechaHasta);

        if (fechaDesde != null && !fechaDesde.isBlank() && desde == null) {
            model.addAttribute("error", "La fecha inicial no tiene un formato válido.");
        }
        if (fechaHasta != null && !fechaHasta.isBlank() && hasta == null) {
            model.addAttribute("error", "La fecha final no tiene un formato válido.");
        }
        if (desde != null && hasta != null && desde.isAfter(hasta)) {
            model.addAttribute("error", "La fecha inicial no puede ser posterior a la fecha final.");
        }

        String rutaFiltro = ruta == null ? "" : ruta.trim().toLowerCase();
        List<HorarioConductor> horariosFiltrados = historial.stream()
                .filter(h -> h.getSistemaTransporte() == sistema)
                .filter(h -> conductorId == null || conductorId.isBlank() || conductorId.equals(h.getConductorId()))
                .filter(h -> rutaFiltro.isEmpty() || (h.getRuta() != null && h.getRuta().toLowerCase().contains(rutaFiltro)))
                .filter(h -> desde == null || (h.getFecha() != null && !h.getFecha().isBefore(desde)))
                .filter(h -> hasta == null || (h.getFecha() != null && !h.getFecha().isAfter(hasta)))
                .toList();

        int totalPages = Math.max(1,
                (int) Math.ceil((double) horariosFiltrados.size() / TAMANO_PAGINA_HISTORIAL));
        int paginaSegura = Math.min(Math.max(page, 0), totalPages - 1);
        int desdeIndice = paginaSegura * TAMANO_PAGINA_HISTORIAL;
        int hastaIndice = Math.min(desdeIndice + TAMANO_PAGINA_HISTORIAL, horariosFiltrados.size());
        List<HorarioConductor> horariosPagina = horariosFiltrados.subList(desdeIndice, hastaIndice);

        Map<String, String> nombreConductor = new HashMap<>();
        Map<String, String> busConductor = new HashMap<>();
        Map<String, String> busPorHorario = new HashMap<>();
        for (Usuario conductor : conductores) {
            nombreConductor.put(conductor.getId(),
                    conductor.getNombre() != null ? conductor.getNombre() : conductor.getCorreo());
            String busId = conductor.getBusAsignado();
            if (busId != null && !busId.isBlank()) {
                busRepository.findById(busId).ifPresent(bus -> busConductor.put(conductor.getId(), bus.getPlaca()));
            }
        }
        for (HorarioConductor horario : horariosPagina) {
            String busId = busIdDelHorario(horario);
            if (busId != null) {
                busRepository.findById(busId).ifPresentOrElse(
                        bus -> busPorHorario.put(horario.getId(), bus.getPlaca()),
                        () -> {
                            if (horario.getBusPlaca() != null && !horario.getBusPlaca().isBlank()) {
                                busPorHorario.put(horario.getId(), horario.getBusPlaca());
                            }
                        });
            } else if (horario.getBusPlaca() != null && !horario.getBusPlaca().isBlank()) {
                busPorHorario.put(horario.getId(), horario.getBusPlaca());
            }
        }

        model.addAttribute("conductores", conductores);
        model.addAttribute("sistemaActual", sistema);
        model.addAttribute("esTransmilenio", sistema == SistemaTransporte.TRANSMILENIO);
        model.addAttribute("esTransmetro", sistema == SistemaTransporte.TRANSMETRO);
        model.addAttribute("historial", horariosPagina);
        model.addAttribute("nombreConductor", nombreConductor);
        model.addAttribute("busConductor", busConductor);
        model.addAttribute("busPorHorario", busPorHorario);
        model.addAttribute("conductorIdSeleccionado", conductorId == null ? "" : conductorId);
        model.addAttribute("rutaSeleccionada", ruta == null ? "" : ruta);
        model.addAttribute("fechaDesde", fechaDesde == null ? "" : fechaDesde);
        model.addAttribute("fechaHasta", fechaHasta == null ? "" : fechaHasta);
        model.addAttribute("totalHistorial", horariosFiltrados.size());
        model.addAttribute("currentPage", paginaSegura);
        model.addAttribute("totalPages", totalPages);
        return "admin/historial-horarios";
    }

    private LocalDate parseFecha(String fecha) {
        if (fecha == null || fecha.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(fecha);
        } catch (java.time.format.DateTimeParseException e) {
            return null;
        }
    }

    private String nombreConductor(Usuario conductor) {
        return conductor.getNombre() != null && !conductor.getNombre().isBlank()
                ? conductor.getNombre()
                : conductor.getCorreo();
    }

    private List<String> rutasDelConductor(Usuario conductor) {
        if (conductor.getBusAsignado() == null || conductor.getBusAsignado().isBlank()) {
            return new ArrayList<>();
        }
        Bus bus = busRepository.findById(conductor.getBusAsignado()).orElse(null);
        if (bus == null || bus.getRutas() == null) {
            return new ArrayList<>();
        }
        return new ArrayList<>(bus.getRutas());
    }

    private List<String> rutasDelHorario(HorarioConductor horario) {
        String busId = horario.getBusId();
        if (busId != null && !busId.isBlank()) {
            Bus bus = busRepository.findById(busId).orElse(null);
            if (bus != null && bus.getRutas() != null) return new ArrayList<>(bus.getRutas());
        }
        Usuario conductor = usuarioRepository.findById(horario.getConductorId()).orElse(null);
        if (conductor != null) return rutasDelConductor(conductor);
        return new ArrayList<>();
    }

    private boolean seSolapan(LocalTime inicio, LocalTime fin, LocalTime otroInicio, LocalTime otroFin) {
        if (inicio == null || fin == null || otroInicio == null || otroFin == null) return false;
        return inicio.isBefore(otroFin) && otroInicio.isBefore(fin);
    }

    private boolean conductorTieneHorarioSolapado(String conductorId, LocalDate fecha,
                                                   LocalTime inicio, LocalTime fin,
                                                   String horarioExcluido, SistemaTransporte sistema) {
        if (fecha == null || inicio == null || fin == null) return false;
        return horarioRepository.findAll().stream()
                .filter(h -> conductorId.equals(h.getConductorId()))
                .filter(h -> h.getSistemaTransporte() == sistema && fecha.equals(h.getFecha()))
                .filter(h -> horarioExcluido == null || !horarioExcluido.equals(h.getId()))
                .filter(h -> !HorarioConductor.ESTADO_FINALIZADA.equals(h.getEstado())
                        && !HorarioConductor.ESTADO_DESACTIVADA.equals(h.getEstado()))
                .anyMatch(h -> seSolapan(inicio, fin, h.getHoraInicio(), h.getHoraFin()));
    }

    private SistemaTransporte sistemaActual(HttpSession session) {
        Object valor = session.getAttribute(SistemaTransporte.SESSION_ATTRIBUTE);
        return valor instanceof SistemaTransporte sistema && sistema.estaDisponible() ? sistema : null;
    }

    private boolean perteneceAlSistema(Usuario conductor, SistemaTransporte sistema) {
        SistemaTransporte asignado = conductor.getSitm() == null
                ? SistemaTransporte.TRANSCARIBE : conductor.getSitm();
        return sistema != null && asignado == sistema;
    }

    private LocalTime parseHora(String hora) {
        try {
            return hora == null ? null : LocalTime.parse(hora);
        } catch (java.time.format.DateTimeParseException e) {
            return null;
        }
    }

    private String busIdDelHorario(HorarioConductor horario) {
        if (horario.getBusId() != null && !horario.getBusId().isBlank()) {
            return horario.getBusId();
        }
        Usuario conductor = usuarioRepository.findById(horario.getConductorId()).orElse(null);
        return conductor == null ? null : conductor.getBusAsignado();
    }

    private boolean busTieneHorarioSolapado(String busId, LocalDate fecha, LocalTime inicio,
                                             LocalTime fin, String horarioExcluido) {
        return horarioRepository.findAll().stream()
                .filter(h -> busId.equals(busIdDelHorario(h)))
                .filter(h -> fecha.equals(h.getFecha()))
                .filter(h -> horarioExcluido == null || !horarioExcluido.equals(h.getId()))
                .filter(h -> !HorarioConductor.ESTADO_FINALIZADA.equals(h.getEstado())
                        && !HorarioConductor.ESTADO_DESACTIVADA.equals(h.getEstado()))
                .anyMatch(h -> seSolapan(inicio, fin, h.getHoraInicio(), h.getHoraFin()));
    }
 
    @PostMapping("/crear")
    public String crearHorario(@RequestParam String conductorId,
    @RequestParam(required = false) String busId,
    @RequestParam String ruta,
                                @RequestParam String fecha,
                                @RequestParam String horaInicio,
                                @RequestParam String horaFin,
                                RedirectAttributes redirectAttributes, HttpSession session) {
        SistemaTransporte sistema = sistemaActual(session);
        Usuario conductor = usuarioRepository.findById(conductorId).orElse(null);
        if (conductor == null || !Usuario.ROLE_CONDUCTOR.equals(conductor.getRole())
                || !perteneceAlSistema(conductor, sistema)) {
            redirectAttributes.addFlashAttribute("error", "El conductor seleccionado no existe.");
            return "redirect:/admin/horarios/programar";
        }

        Bus bus = busId != null && !busId.isBlank()
                ? busRepository.findById(busId).orElse(null) : null;
        if (bus == null || !bus.isActivo() || bus.getSistemaTransporte() != sistema) {
            redirectAttributes.addFlashAttribute("error", "El bus seleccionado no existe o está inactivo.");
            return "redirect:/admin/horarios/programar";
        }

        LocalDate fechaProgramada;
        LocalTime inicio;
        LocalTime fin;
        try {
            fechaProgramada = LocalDate.parse(fecha);
            inicio = LocalTime.parse(horaInicio);
            fin = LocalTime.parse(horaFin);
        } catch (java.time.format.DateTimeParseException e) {
            redirectAttributes.addFlashAttribute("error", "La fecha o el horario no tienen un formato válido.");
            return "redirect:/admin/horarios/programar";
        }
        if (!inicio.isBefore(fin)) {
            redirectAttributes.addFlashAttribute("error", "La hora de inicio debe ser anterior a la hora de fin.");
            return "redirect:/admin/horarios/programar";
        }
        if (sistema == SistemaTransporte.TRANSMILENIO) {
            ruta = catalogoRutasTransmilenioService.buscarPorEtiqueta(ruta.trim())
                    .map(com.transcaribe.transcaribe.Model.RutaTransmilenio::getEtiqueta).orElse(null);
            if (ruta == null) {
                redirectAttributes.addFlashAttribute("error", "Selecciona un servicio troncal válido de TransMilenio.");
                return "redirect:/admin/horarios/programar";
            }
            if (conductorTieneHorarioSolapado(conductorId, parseFecha(fecha), parseHora(horaInicio),
                    parseHora(horaFin), null, sistema)) {
                redirectAttributes.addFlashAttribute("error", "El conductor ya tiene un horario asignado en ese rango.");
                return "redirect:/admin/horarios/programar";
            }
        }
        if (sistema == SistemaTransporte.TRANSMETRO) {
            String rutaSolicitada = ruta.trim();
            ruta = catalogoRutasTransmetroService.obtenerRutasActivas().stream()
                    .filter(rutaTransmetro -> rutaTransmetro.getEtiqueta().equals(rutaSolicitada))
                    .map(com.transcaribe.transcaribe.Model.RutaTransmetro::getEtiqueta)
                    .findFirst().orElse(null);
            if (ruta == null) {
                redirectAttributes.addFlashAttribute("error", "Selecciona una ruta troncal activa de Transmetro.");
                return "redirect:/admin/horarios/programar";
            }
            if (conductorTieneHorarioSolapado(conductorId, fechaProgramada, inicio, fin, null, sistema)) {
                redirectAttributes.addFlashAttribute("error", "El conductor ya tiene un horario asignado en ese rango.");
                return "redirect:/admin/horarios/programar";
            }
        }
        if (bus.getRutas() == null || !bus.getRutas().contains(ruta.trim())) {
            redirectAttributes.addFlashAttribute("error", "La ruta seleccionada no pertenece al bus elegido.");
            return "redirect:/admin/horarios/programar";
        }
        if (busTieneHorarioSolapado(busId, fechaProgramada, inicio, fin, null)) {
            redirectAttributes.addFlashAttribute("error",
                    "El bus " + bus.getPlaca() + " ya tiene otra ruta programada en ese horario.");
            return "redirect:/admin/horarios/programar";
        }
 
        HorarioConductor horario = new HorarioConductor(
                conductorId,
                bus.getId(),
                ruta.trim(),
                fechaProgramada,
                inicio,
                fin
        );
        horario.setSistemaTransporte(sistema);
        horario.setBusPlaca(bus.getPlaca());
 
        horarioRepository.save(horario);
        emailService.enviarNotificacionHorarioAsignado(
                conductor.getCorreo(),
                nombreConductor(conductor),
                horario.getRuta(),
                horario.getFecha(),
                horario.getHoraInicio(),
                horario.getHoraFin(),
                sistema
        );
        redirectAttributes.addFlashAttribute("mensaje",
                "Horario asignado correctamente. Se envió una notificación al correo del conductor.");
        return "redirect:/admin/horarios/programar";
    }

    @GetMapping("/editar/{id}")
    public String formularioEditarHorario(@PathVariable String id, Model model,
                                          RedirectAttributes redirectAttributes, HttpSession session) {
        SistemaTransporte sistema = sistemaActual(session);
        HorarioConductor horario = horarioRepository.findById(id).orElse(null);
        if (horario == null || horario.getSistemaTransporte() != sistema) {
            redirectAttributes.addFlashAttribute("error", "El horario que intentas editar no existe.");
            return "redirect:/admin/horarios/programar";
        }

        Usuario conductor = usuarioRepository.findById(horario.getConductorId()).orElse(null);
        if (conductor == null || !perteneceAlSistema(conductor, sistema)) {
            redirectAttributes.addFlashAttribute("error", "El conductor de este horario ya no está disponible.");
            return "redirect:/admin/horarios/programar";
        }

        List<String> rutas = rutasDelHorario(horario);
        model.addAttribute("horario", horario);
        model.addAttribute("conductor", conductor);
        model.addAttribute("rutas", rutas);
        List<Bus> buses = busRepository.findAll().stream()
                .filter(bus -> bus.getSistemaTransporte() == sistema && bus.isActivo()).toList();
        model.addAttribute("buses", buses);
        Map<String, List<String>> busRutasPorId = new HashMap<>();
        buses.forEach(bus -> busRutasPorId.put(bus.getId(),
                bus.getRutas() == null ? new ArrayList<>() : bus.getRutas()));
        model.addAttribute("busRutasPorId", busRutasPorId);
        model.addAttribute("busSeleccionadoId", busIdDelHorario(horario));
        model.addAttribute("esTransmilenio", sistema == SistemaTransporte.TRANSMILENIO);
        model.addAttribute("esTransmetro", sistema == SistemaTransporte.TRANSMETRO);
        model.addAttribute("sistemaActual", sistema);
        return "admin/editar-horario";
    }

    @PostMapping("/editar")
    public String editarHorario(@RequestParam String id,
    @RequestParam(required = false) String busId,
    @RequestParam String ruta,
                                 @RequestParam String fecha,
                                 @RequestParam String horaInicio,
                                 @RequestParam String horaFin,
                                 RedirectAttributes redirectAttributes, HttpSession session) {
        SistemaTransporte sistema = sistemaActual(session);
        HorarioConductor horario = horarioRepository.findById(id).orElse(null);
        if (horario == null || horario.getSistemaTransporte() != sistema) {
            redirectAttributes.addFlashAttribute("error", "El horario que intentas editar no existe.");
            return "redirect:/admin/horarios/programar";
        }

        Usuario conductor = usuarioRepository.findById(horario.getConductorId()).orElse(null);
        if (conductor == null || !perteneceAlSistema(conductor, sistema)) {
            redirectAttributes.addFlashAttribute("error", "El conductor de este horario ya no está disponible.");
            return "redirect:/admin/horarios/programar";
        }

        Bus bus = busId != null && !busId.isBlank()
                ? busRepository.findById(busId).orElse(null) : null;
        if (bus == null || !bus.isActivo() || bus.getSistemaTransporte() != sistema) {
            redirectAttributes.addFlashAttribute("error", "El bus seleccionado no existe o está inactivo.");
            return "redirect:/admin/horarios/programar";
        }
        LocalDate fechaProgramada;
        LocalTime inicio;
        LocalTime fin;
        try {
            fechaProgramada = LocalDate.parse(fecha);
            inicio = LocalTime.parse(horaInicio);
            fin = LocalTime.parse(horaFin);
        } catch (java.time.format.DateTimeParseException e) {
            redirectAttributes.addFlashAttribute("error", "La fecha o el horario no tienen un formato válido.");
            return "redirect:/admin/horarios/programar";
        }
        if (!inicio.isBefore(fin)) {
            redirectAttributes.addFlashAttribute("error", "La hora de inicio debe ser anterior a la hora de fin.");
            return "redirect:/admin/horarios/programar";
        }
        if (sistema == SistemaTransporte.TRANSMILENIO) {
            ruta = catalogoRutasTransmilenioService.buscarPorEtiqueta(ruta.trim())
                    .map(com.transcaribe.transcaribe.Model.RutaTransmilenio::getEtiqueta).orElse(null);
            if (ruta == null) {
                redirectAttributes.addFlashAttribute("error", "Selecciona una ruta troncal válida de TransMilenio.");
                return "redirect:/admin/horarios/programar";
            }
            if (conductorTieneHorarioSolapado(conductor.getId(), fechaProgramada, inicio, fin,
                    horario.getId(), sistema)) {
                redirectAttributes.addFlashAttribute("error", "El conductor ya tiene otro horario en ese rango.");
                return "redirect:/admin/horarios/programar";
            }
        }
        if (sistema == SistemaTransporte.TRANSMETRO) {
            String rutaSolicitada = ruta.trim();
            ruta = catalogoRutasTransmetroService.obtenerRutasActivas().stream()
                    .filter(rutaTransmetro -> rutaTransmetro.getEtiqueta().equals(rutaSolicitada))
                    .map(com.transcaribe.transcaribe.Model.RutaTransmetro::getEtiqueta)
                    .findFirst().orElse(null);
            if (ruta == null) {
                redirectAttributes.addFlashAttribute("error", "Selecciona una ruta troncal activa de Transmetro.");
                return "redirect:/admin/horarios/programar";
            }
            if (conductorTieneHorarioSolapado(conductor.getId(), fechaProgramada, inicio, fin,
                    horario.getId(), sistema)) {
                redirectAttributes.addFlashAttribute("error", "El conductor ya tiene otro horario en ese rango.");
                return "redirect:/admin/horarios/programar";
            }
        }
        if (bus.getRutas() == null || !bus.getRutas().contains(ruta.trim())) {
            redirectAttributes.addFlashAttribute("error", "La ruta seleccionada no pertenece al bus elegido.");
            return "redirect:/admin/horarios/programar";
        }
        if (busTieneHorarioSolapado(busId, fechaProgramada, inicio, fin, horario.getId())) {
            redirectAttributes.addFlashAttribute("error",
                    "El bus " + bus.getPlaca() + " ya tiene otra ruta programada en ese horario.");
            return "redirect:/admin/horarios/programar";
        }
 
        horario.setRuta(ruta.trim());
        horario.setSistemaTransporte(sistema);
        horario.setBusId(bus.getId());
        horario.setBusPlaca(bus.getPlaca());
        horario.setFecha(fechaProgramada);
        horario.setHoraInicio(inicio);
        horario.setHoraFin(fin);
        horarioRepository.save(horario);

        emailService.enviarNotificacionHorarioActualizado(
                conductor.getCorreo(),
                nombreConductor(conductor),
                horario.getRuta(),
                horario.getFecha(),
                horario.getHoraInicio(),
                horario.getHoraFin(),
                sistema
        );
        redirectAttributes.addFlashAttribute("mensaje",
                "Horario actualizado correctamente. Se envió una notificación al correo del conductor.");
        return "redirect:/admin/horarios/programar";
    }
 
    @PostMapping("/eliminar")
    public String eliminarHorario(@RequestParam String id, RedirectAttributes redirectAttributes,
                                  HttpSession session) {
        SistemaTransporte sistema = sistemaActual(session);
        HorarioConductor horario = horarioRepository.findById(id).orElse(null);
        if (horario == null || horario.getSistemaTransporte() != sistema) {
            redirectAttributes.addFlashAttribute("error", "El horario que intentas desactivar no existe.");
            return "redirect:/admin/horarios/programar";
        }

        boolean estabaEnCurso = HorarioConductor.ESTADO_EN_CURSO.equals(horario.getEstado());
        horario.setEstado(HorarioConductor.ESTADO_DESACTIVADA);
        horarioRepository.save(horario);
        redirectAttributes.addFlashAttribute("mensaje", estabaEnCurso
                ? "Ruta en curso desactivada. El bus quedó disponible y el registro se conservó en el historial."
                : "Horario desactivado correctamente. El bus quedó disponible y el registro se conservó en el historial.");
        return "redirect:/admin/horarios/programar";
    }
 
    @GetMapping("/buses/nuevo")
    public String formularioNuevoBus(Model model) {
        return "redirect:/admin/horarios/buses";
    }

    @PostMapping("/buses/crear")
    public String crearBus(@RequestParam String placa,
                            @RequestParam(required = false) List<String> rutas,
                            @RequestParam(defaultValue = "TRONCAL", required = false) String tipo,
                            RedirectAttributes redirectAttributes, HttpSession session) {
        SistemaTransporte sistema = sistemaActual(session);
 
        String placaLimpia = placa.trim().toUpperCase();
 
        if (placaLimpia.isBlank() || busRepository.findByPlaca(placaLimpia).isPresent()) {
            redirectAttributes.addFlashAttribute("error", "Ya existe un bus registrado con la placa " + placaLimpia + " ❌");
            return "redirect:/admin/horarios/buses";
        }
 
        List<String> listaRutas = (rutas == null ? List.<String>of() : rutas).stream()
                .flatMap(valor -> Arrays.stream(valor.split(",")))
                .map(String::trim)
                .filter(r -> !r.isEmpty())
                .collect(Collectors.toList());

        if (sistema == SistemaTransporte.TRANSMILENIO) {
            List<String> rutasValidas = catalogoRutasTransmilenioService.obtenerRutasActivas().stream()
                    .map(com.transcaribe.transcaribe.Model.RutaTransmilenio::getEtiqueta).toList();
            if (listaRutas.isEmpty() || !rutasValidas.containsAll(listaRutas)) {
                redirectAttributes.addFlashAttribute("error", "Selecciona al menos un servicio troncal válido.");
                return "redirect:/admin/horarios/buses";
            }
            tipo = Bus.TIPO_TRONCAL;
        }
        if (sistema == SistemaTransporte.TRANSMETRO) {
            List<String> rutasValidas = catalogoRutasTransmetroService.obtenerRutasActivas().stream()
                    .map(com.transcaribe.transcaribe.Model.RutaTransmetro::getEtiqueta).toList();
            if (listaRutas.isEmpty() || !rutasValidas.containsAll(listaRutas)) {
                redirectAttributes.addFlashAttribute("error", "Selecciona al menos una ruta activa de Transmetro.");
                return "redirect:/admin/horarios/buses";
            }
            tipo = Bus.TIPO_TRONCAL;
        }

        Bus nuevoBus = new Bus(placaLimpia, listaRutas, tipo, sistema);
        busRepository.save(nuevoBus);
 
        redirectAttributes.addFlashAttribute("mensaje", "Bus " + placaLimpia + " registrado correctamente ✅");
        return "redirect:/admin/horarios/buses";
    }
 
    @PostMapping("/buses/eliminar")
    public String eliminarBus(@RequestParam String id, RedirectAttributes redirectAttributes,
                              HttpSession session) {
        SistemaTransporte sistema = sistemaActual(session);
        Bus bus = busRepository.findById(id).orElse(null);
        if (bus == null || bus.getSistemaTransporte() != sistema) {
            redirectAttributes.addFlashAttribute("error", "El bus que intentas eliminar ya no existe.");
            return "redirect:/admin/horarios/buses";
        }

        List<Usuario> conductoresAsignados = usuarioRepository.findAll().stream()
                .filter(conductor -> Usuario.ROLE_CONDUCTOR.equals(conductor.getRole()))
                .filter(conductor -> id.equals(conductor.getBusAsignado()))
                .toList();
        List<HorarioConductor> horariosBus = horarioRepository.findAll().stream()
                .filter(horario -> id.equals(busIdDelHorario(horario)))
                .toList();
        List<HorarioConductor> horariosDesactivados = horariosBus.stream()
                .filter(horario -> !HorarioConductor.ESTADO_FINALIZADA.equals(horario.getEstado())
                        && !HorarioConductor.ESTADO_DESACTIVADA.equals(horario.getEstado()))
                .toList();

        for (HorarioConductor horario : horariosBus) {
            if (horario.getBusPlaca() == null || horario.getBusPlaca().isBlank()) {
                horario.setBusPlaca(bus.getPlaca());
            }
        }
        for (HorarioConductor horario : horariosDesactivados) {
            horario.setEstado(HorarioConductor.ESTADO_DESACTIVADA);
        }
        if (!horariosBus.isEmpty()) {
            horarioRepository.saveAll(horariosBus);
        }

        conductoresAsignados.forEach(conductor -> conductor.setBusAsignado(null));
        if (!conductoresAsignados.isEmpty()) {
            usuarioRepository.saveAll(conductoresAsignados);
        }

        busRepository.delete(bus);
        redirectAttributes.addFlashAttribute("mensaje", "Bus " + bus.getPlaca()
                + " eliminado. Se liberaron " + conductoresAsignados.size()
                + " conductor(es) y se desactivaron " + horariosDesactivados.size()
                + " horario(s) asociado(s); el historial se conservó.");
        return "redirect:/admin/horarios/buses";
    }

    @PostMapping("/buses/editar")
    public String editarBus(@RequestParam String id,
                             @RequestParam String placa,
                             @RequestParam(required = false) List<String> rutas,
                             @RequestParam(defaultValue = "TRONCAL", required = false) String tipo,
                             RedirectAttributes redirectAttributes, HttpSession session) {
        SistemaTransporte sistema = sistemaActual(session);

        Bus bus = busRepository.findById(id).orElse(null);
        if (bus == null || bus.getSistemaTransporte() != sistema) {
            redirectAttributes.addFlashAttribute("error", "Ese bus no existe ❌");
            return "redirect:/admin/horarios/buses";
        }

        String placaLimpia = placa.trim().toUpperCase();

        // Si cambió la placa, verificar que no choque con otro bus existente
        if (!placaLimpia.equals(bus.getPlaca()) && busRepository.findByPlaca(placaLimpia).isPresent()) {
            redirectAttributes.addFlashAttribute("error", "Ya existe otro bus con la placa " + placaLimpia + " ❌");
            return "redirect:/admin/horarios/buses";
        }

        List<String> listaRutas = (rutas == null ? List.<String>of() : rutas).stream()
                .flatMap(valor -> Arrays.stream(valor.split(",")))
                .map(String::trim)
                .filter(r -> !r.isEmpty())
                .collect(Collectors.toList());

        if (sistema == SistemaTransporte.TRANSMILENIO) {
            List<String> rutasValidas = catalogoRutasTransmilenioService.obtenerRutasActivas().stream()
                    .map(com.transcaribe.transcaribe.Model.RutaTransmilenio::getEtiqueta).toList();
            if (listaRutas.isEmpty() || !rutasValidas.containsAll(listaRutas)) {
                redirectAttributes.addFlashAttribute("error", "Selecciona al menos un servicio troncal válido.");
                return "redirect:/admin/horarios/buses";
            }
            tipo = Bus.TIPO_TRONCAL;
        }
        if (sistema == SistemaTransporte.TRANSMETRO) {
            List<String> rutasValidas = catalogoRutasTransmetroService.obtenerRutasActivas().stream()
                    .map(com.transcaribe.transcaribe.Model.RutaTransmetro::getEtiqueta).toList();
            if (listaRutas.isEmpty() || !rutasValidas.containsAll(listaRutas)) {
                redirectAttributes.addFlashAttribute("error", "Selecciona al menos una ruta activa de Transmetro.");
                return "redirect:/admin/horarios/buses";
            }
            tipo = Bus.TIPO_TRONCAL;
        }

        bus.setPlaca(placaLimpia);
        bus.setRutas(listaRutas);
        bus.setTipo(tipo);
        bus.setSistemaTransporte(sistema);
        busRepository.save(bus);

        redirectAttributes.addFlashAttribute("mensaje", "Bus " + placaLimpia + " actualizado correctamente ✅");
        return "redirect:/admin/horarios/buses";
    }
}

package com.transcaribe.transcaribe.service;

import com.transcaribe.transcaribe.Model.AvisoAdministrativo;
import com.transcaribe.transcaribe.Model.HorarioConductor;
import com.transcaribe.transcaribe.Model.Transaccion;
import com.transcaribe.transcaribe.Model.SistemaTransporte;
import com.transcaribe.transcaribe.Model.Usuario;
import com.transcaribe.transcaribe.Repository.AvisoAdministrativoRepository;
import com.transcaribe.transcaribe.Repository.HorarioConductorRepository;
import com.transcaribe.transcaribe.Repository.TransaccionRepository;
import com.transcaribe.transcaribe.Repository.UsuarioRepository;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

@Service
public class ExcelReportService {

    private final UsuarioRepository usuarioRepository;
    private final TransaccionRepository transaccionRepository;
    private final HorarioConductorRepository horarioRepository;
    private final AvisoAdministrativoRepository avisoRepository;

    public ExcelReportService(UsuarioRepository usuarioRepository,
                              TransaccionRepository transaccionRepository,
                              HorarioConductorRepository horarioRepository,
                              AvisoAdministrativoRepository avisoRepository) {
        this.usuarioRepository = usuarioRepository;
        this.transaccionRepository = transaccionRepository;
        this.horarioRepository = horarioRepository;
        this.avisoRepository = avisoRepository;
    }

    public void generarReporte(String tipo, Integer anio, Integer mes, Integer dia,
                               boolean incluirUsuarios, boolean incluirTransacciones,
                               boolean incluirRutas, boolean incluirAvisos,
                               OutputStream out) throws IOException {
        generarReporte(tipo, anio, mes, dia, incluirUsuarios, incluirTransacciones,
                incluirRutas, incluirAvisos, null, out);
    }

    public void generarReporte(String tipo, Integer anio, Integer mes, Integer dia,
                               boolean incluirUsuarios, boolean incluirTransacciones,
                               boolean incluirRutas, boolean incluirAvisos,
                               SistemaTransporte sistemaTransporte, OutputStream out) throws IOException {
        generarReporte(tipo, anio, mes, dia, incluirUsuarios, incluirTransacciones,
                incluirRutas, incluirAvisos, sistemaTransporte, null, out);
    }

    public void generarReporte(String tipo, Integer anio, Integer mes, Integer dia,
                               boolean incluirUsuarios, boolean incluirTransacciones,
                               boolean incluirRutas, boolean incluirAvisos,
                               SistemaTransporte sistemaTransporte, List<Usuario> directorioUsuarios,
                               OutputStream out) throws IOException {
        if (sistemaTransporte != null && !sistemaTransporte.estaDisponible()) {
            throw new IllegalArgumentException("El servicio del reporte no está disponible.");
        }
        if (sistemaTransporte != null && !sistemaTransporte.permiteGestionConductores() && incluirRutas) {
            throw new IllegalArgumentException("El historial de rutas de conductores no está habilitado para el servicio seleccionado.");
        }
        SXSSFWorkbook workbook = new SXSSFWorkbook(100);
        workbook.setCompressTempFiles(true);
        try (workbook) {
            CellStyle headerStyle = crearEstiloEncabezado(workbook);
            DateRange rango = buildRango(tipo, anio, mes, dia);
            List<Usuario> usuariosDelSistema = List.of();
            boolean requiereUsuariosSistema = incluirUsuarios || incluirRutas || incluirAvisos;
            if (requiereUsuariosSistema && directorioUsuarios != null) {
                usuariosDelSistema = directorioUsuarios.stream()
                        .filter(usuario -> sistemaTransporte == null
                                || perteneceAlSistema(usuario, sistemaTransporte))
                        .toList();
            } else if (requiereUsuariosSistema && sistemaTransporte != null) {
                try (Stream<Usuario> usuarios = usuarioRepository.streamBySistemaTransporte(sistemaTransporte)) {
                    usuariosDelSistema = usuarios.toList();
                }
            } else if (incluirUsuarios) {
                try (Stream<Usuario> usuarios = usuarioRepository.streamAll()) {
                    usuariosDelSistema = usuarios.toList();
                }
            }
            Map<String, String> nombresUsuarios = new HashMap<>();
            for (Usuario usuario : usuariosDelSistema) {
                nombresUsuarios.put(usuario.getId(), valor(usuario.getNombre()));
            }
            List<String> idsUsuariosDelSistema = usuariosDelSistema.stream()
                    .map(Usuario::getId)
                    .filter(id -> id != null && !id.isBlank())
                    .toList();
            List<String> idsConductoresDelSistema = usuariosDelSistema.stream()
                    .filter(usuario -> Usuario.ROLE_CONDUCTOR.equals(usuario.getRole()))
                    .map(Usuario::getId)
                    .filter(id -> id != null && !id.isBlank())
                    .toList();

            if (incluirUsuarios) {
                escribirUsuarios(workbook, headerStyle, usuariosDelSistema);
            }
            if (incluirTransacciones) {
                escribirTransacciones(workbook, headerStyle, tipo, anio, mes, dia, rango, sistemaTransporte);
            }
            if (incluirRutas) {
                escribirRutas(workbook, headerStyle, tipo, anio, mes, dia, rango,
                        sistemaTransporte == null ? null : idsConductoresDelSistema,
                        nombresUsuarios);
            }
            if (incluirAvisos) {
                escribirAvisos(workbook, headerStyle, tipo, anio, mes, dia, rango,
                        sistemaTransporte == null ? null : idsUsuariosDelSistema,
                        nombresUsuarios);
            }

            workbook.write(out);
        } finally {
            workbook.dispose();
        }
    }

    private static boolean perteneceAlSistema(Usuario usuario, SistemaTransporte sistema) {
        if (Usuario.ROLE_MODERATOR.equals(usuario.getRole())) {
            return usuario.getSistemaAsignado() == sistema;
        }
        return usuario.getSitm() == sistema
                || (Usuario.ROLE_CONDUCTOR.equals(usuario.getRole())
                        && usuario.getSitm() == null
                        && sistema == SistemaTransporte.TRANSCARIBE);
    }

    private CellStyle crearEstiloEncabezado(Workbook workbook) {
        CellStyle headerStyle = workbook.createCellStyle();
        Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerFont.setColor(IndexedColors.WHITE.getIndex());
        headerStyle.setFont(headerFont);
        headerStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return headerStyle;
    }

    private void escribirUsuarios(Workbook workbook, CellStyle headerStyle, List<Usuario> usuarios) {
        Sheet sheet = workbook.createSheet("Resumen Usuarios");
        String[] columns = {"ID", "Nombre", "Correo", "Rol", "Servicio"};
        crearEncabezado(sheet, headerStyle, columns);
        int rowNumber = 1;
        for (Usuario usuario : usuarios) {
            Row row = sheet.createRow(rowNumber++);
            row.createCell(0).setCellValue(valor(usuario.getId()));
            row.createCell(1).setCellValue(valor(usuario.getNombre()));
            row.createCell(2).setCellValue(valor(usuario.getCorreo()));
            row.createCell(3).setCellValue(valor(usuario.getRole()));
            SistemaTransporte sistemaUsuario = Usuario.ROLE_MODERATOR.equals(usuario.getRole())
                    ? usuario.getSistemaAsignado() : usuario.getSitm();
            row.createCell(4).setCellValue(sistemaUsuario == null ? "N/A" : sistemaUsuario.getNombre());
        }
        setColumnWidths(sheet, 18, 28, 36, 20, 22);
    }

    private void escribirTransacciones(Workbook workbook, CellStyle headerStyle,
                                       String tipo, Integer anio, Integer mes, Integer dia,
                                       DateRange rango, SistemaTransporte sistemaTransporte) {
        Sheet sheet = workbook.createSheet(buildNombreHoja(tipo, anio, mes, dia, sistemaTransporte));
        String[] columns = {"Fecha y Hora", "ID Transacción", "Usuario (Correo)", "Nombre", "Tipo", "Monto"};
        crearEncabezado(sheet, headerStyle, columns);
        try (Stream<Transaccion> transacciones = obtenerTransacciones(rango, sistemaTransporte)) {
            Iterator<Transaccion> iterator = transacciones.iterator();
            int rowNumber = 1;
            while (iterator.hasNext()) {
                Transaccion transaccion = iterator.next();
                if (!coincideConFiltro(transaccion.getFecha(), tipo, anio, mes, dia)) {
                    continue;
                }
                Row row = sheet.createRow(rowNumber++);
                row.createCell(0).setCellValue(transaccion.getFecha().toString());
                row.createCell(1).setCellValue(valor(transaccion.getId()));
                Usuario usuario = transaccion.getUsuario();
                row.createCell(2).setCellValue(usuario == null ? "N/A" : valor(usuario.getCorreo()));
                row.createCell(3).setCellValue(usuario == null ? "N/A" : valor(usuario.getNombre()));
                row.createCell(4).setCellValue(valor(transaccion.getTipo()));
                row.createCell(5).setCellValue(transaccion.getMonto());
            }
        }
        setColumnWidths(sheet, 22, 26, 36, 28, 18, 18);
    }

    private Stream<Transaccion> obtenerTransacciones(DateRange rango, SistemaTransporte sistemaTransporte) {
        if (sistemaTransporte == null) {
            return rango == null
                    ? transaccionRepository.streamAll()
                    : transaccionRepository.streamByFechaEnRango(rango.inicio(), rango.fin());
        }
        if (SistemaTransporte.TRANSCARIBE.equals(sistemaTransporte)) {
            return rango == null
                    ? transaccionRepository.streamTranscaribe()
                    : transaccionRepository.streamTranscaribeByFechaEnRango(rango.inicio(), rango.fin());
        }
        return rango == null
                ? transaccionRepository.streamBySistemaTransporte(sistemaTransporte)
                : transaccionRepository.streamBySistemaTransporteAndFechaEnRango(
                        rango.inicio(), rango.fin(), sistemaTransporte);
    }

    private void escribirRutas(Workbook workbook, CellStyle headerStyle,
                               String tipo, Integer anio, Integer mes, Integer dia,
                               DateRange rango, List<String> conductorIds,
                               Map<String, String> nombresUsuarios) {
        Sheet sheet = workbook.createSheet("Rutas conductores");
        String[] columns = {"Fecha", "Hora inicio", "Hora fin", "Conductor", "ID conductor",
                "Ruta", "Sentido", "Placa bus", "ID bus", "Estado"};
        crearEncabezado(sheet, headerStyle, columns);
        try (Stream<HorarioConductor> horarios = obtenerHorarios(rango, conductorIds)) {
            Iterator<HorarioConductor> iterator = horarios.iterator();
            int rowNumber = 1;
            while (iterator.hasNext()) {
                HorarioConductor horario = iterator.next();
                if (!coincideConFiltro(horario.getFecha(), tipo, anio, mes, dia)) {
                    continue;
                }
                Row row = sheet.createRow(rowNumber++);
                row.createCell(0).setCellValue(valor(horario.getFecha()));
                row.createCell(1).setCellValue(valor(horario.getHoraInicio()));
                row.createCell(2).setCellValue(valor(horario.getHoraFin()));
                row.createCell(3).setCellValue(nombreUsuario(horario.getConductorId(), nombresUsuarios));
                row.createCell(4).setCellValue(valor(horario.getConductorId()));
                row.createCell(5).setCellValue(valor(horario.getRuta()));
                row.createCell(6).setCellValue(valor(horario.getSentido()));
                row.createCell(7).setCellValue(valor(horario.getBusPlaca()));
                row.createCell(8).setCellValue(valor(horario.getBusId()));
                row.createCell(9).setCellValue(valor(horario.getEstado()));
            }
        }
        setColumnWidths(sheet, 16, 16, 16, 30, 26, 24, 20, 18, 26, 18);
    }

    private void escribirAvisos(Workbook workbook, CellStyle headerStyle,
                                String tipo, Integer anio, Integer mes, Integer dia,
                                DateRange rango, List<String> usuarioIds,
                                Map<String, String> nombresUsuarios) {
        Sheet sheet = workbook.createSheet("Historial avisos");
        String[] columns = {"Fecha de creación", "ID aviso", "Asunto", "Mensaje",
                "Destinatarios", "Descartado por"};
        crearEncabezado(sheet, headerStyle, columns);
        try (Stream<AvisoAdministrativo> avisos = obtenerAvisos(rango, usuarioIds)) {
            Iterator<AvisoAdministrativo> iterator = avisos.iterator();
            int rowNumber = 1;
            while (iterator.hasNext()) {
                AvisoAdministrativo aviso = iterator.next();
                if (!coincideConFiltro(aviso.getFechaCreacion(), tipo, anio, mes, dia)) {
                    continue;
                }
                Row row = sheet.createRow(rowNumber++);
                row.createCell(0).setCellValue(valor(aviso.getFechaCreacion()));
                row.createCell(1).setCellValue(valor(aviso.getId()));
                row.createCell(2).setCellValue(valor(aviso.getAsunto()));
                row.createCell(3).setCellValue(valor(aviso.getMensaje()));
                row.createCell(4).setCellValue(nombresDestinatarios(
                        aviso.getDestinatarios(), usuarioIds, nombresUsuarios));
                row.createCell(5).setCellValue(nombresDestinatarios(
                        aviso.getDescartadaPor(), usuarioIds, nombresUsuarios));
            }
        }
        setColumnWidths(sheet, 22, 26, 30, 60, 50, 50);
    }

    private void crearEncabezado(Sheet sheet, CellStyle headerStyle, String[] columns) {
        Row header = sheet.createRow(0);
        for (int i = 0; i < columns.length; i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(columns[i]);
            cell.setCellStyle(headerStyle);
        }
    }

    private Stream<HorarioConductor> obtenerHorarios(DateRange rango, List<String> conductorIds) {
        if (conductorIds == null) {
            return rango == null
                    ? horarioRepository.streamAll()
                    : horarioRepository.streamByFechaEnRango(
                            rango.inicio().toLocalDate(), rango.fin().toLocalDate());
        }
        if (conductorIds.isEmpty()) {
            return Stream.empty();
        }
        if (rango == null) {
            return horarioRepository.streamByConductorIdIn(conductorIds);
        }
        return horarioRepository.streamByConductorIdInAndFechaEnRango(
                conductorIds, rango.inicio().toLocalDate(), rango.fin().toLocalDate());
    }

    private Stream<AvisoAdministrativo> obtenerAvisos(DateRange rango, List<String> usuarioIds) {
        if (usuarioIds == null) {
            return rango == null
                    ? avisoRepository.streamAll()
                    : avisoRepository.streamByFechaCreacionEnRango(rango.inicio(), rango.fin());
        }
        if (usuarioIds.isEmpty()) {
            return Stream.empty();
        }
        if (rango == null) {
            return avisoRepository.streamByDestinatariosIn(usuarioIds);
        }
        return avisoRepository.streamByDestinatariosInAndFechaCreacionEnRango(
                usuarioIds, rango.inicio(), rango.fin());
    }

    private String nombreUsuario(String usuarioId, Map<String, String> nombresUsuarios) {
        if (usuarioId == null || usuarioId.isBlank()) {
            return "N/A";
        }
        return nombresUsuarios.getOrDefault(usuarioId, usuarioId);
    }

    private String nombresDestinatarios(Iterable<String> ids, List<String> usuarioIdsDelSistema,
                                        Map<String, String> nombresUsuarios) {
        if (ids == null) {
            return "";
        }
        Set<String> idsDelSistema = usuarioIdsDelSistema == null
                ? null : new HashSet<>(usuarioIdsDelSistema);
        StringBuilder nombres = new StringBuilder();
        for (String id : ids) {
            if (idsDelSistema != null && !idsDelSistema.contains(id)) {
                continue;
            }
            if (nombres.length() > 0) {
                nombres.append("; ");
            }
            nombres.append(nombresUsuarios.getOrDefault(
                    id, usuarioIdsDelSistema == null ? id : "N/A"));
        }
        return nombres.toString();
    }

    private String valor(Object value) {
        return value == null ? "N/A" : value.toString();
    }

    private boolean coincideConFiltro(LocalDateTime fecha, String tipo, Integer anio, Integer mes, Integer dia) {
        return fecha != null && coincideConFiltro(fecha.toLocalDate(), tipo, anio, mes, dia);
    }

    private boolean coincideConFiltro(LocalDate fecha, String tipo, Integer anio, Integer mes, Integer dia) {
        if (fecha == null) return false;
        if ("todo".equals(tipo)) return true;
        if (anio != null && fecha.getYear() != anio) return false;
        if (("mensual".equals(tipo) || "diario".equals(tipo)) && mes != null
                && fecha.getMonthValue() != mes) return false;
        return !"diario".equals(tipo) || dia == null || fecha.getDayOfMonth() == dia;
    }

    private DateRange buildRango(String tipo, Integer anio, Integer mes, Integer dia) {
        if ("todo".equals(tipo) || anio == null) return null;
        try {
            LocalDate inicio;
            if (("mensual".equals(tipo) || "diario".equals(tipo)) && mes != null) {
                inicio = LocalDate.of(anio, mes, 1);
                if ("diario".equals(tipo) && dia != null) {
                    inicio = LocalDate.of(anio, mes, dia);
                    return new DateRange(inicio.atStartOfDay(), inicio.plusDays(1).atStartOfDay());
                }
                return new DateRange(inicio.atStartOfDay(), inicio.plusMonths(1).atStartOfDay());
            }
            inicio = LocalDate.of(anio, 1, 1);
            return new DateRange(inicio.atStartOfDay(), inicio.plusYears(1).atStartOfDay());
        } catch (DateTimeException e) {
            return null;
        }
    }

    private record DateRange(LocalDateTime inicio, LocalDateTime fin) {}

    private String buildNombreHoja(String tipo, Integer anio, Integer mes, Integer dia,
                                   SistemaTransporte sistemaTransporte) {
        return switch (tipo) {
            case "anual" -> "Año " + anio;
            case "mensual" -> "Mes " + mes + "-" + anio;
            case "diario" -> "Día " + dia + "-" + mes + "-" + anio;
            default -> sistemaTransporte == null
                    ? "Historial Global"
                    : "Historial " + sistemaTransporte.getNombre();
        };
    }

    private void setColumnWidths(Sheet sheet, int... widthsInCharacters) {
        for (int i = 0; i < widthsInCharacters.length; i++) {
            sheet.setColumnWidth(i, widthsInCharacters[i] * 256);
        }
    }
}

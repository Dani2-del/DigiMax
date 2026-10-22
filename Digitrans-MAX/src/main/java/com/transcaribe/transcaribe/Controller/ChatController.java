package com.transcaribe.transcaribe.Controller;

import com.transcaribe.transcaribe.Model.ChatMessage;
import com.transcaribe.transcaribe.Model.SistemaTransporte;
import com.transcaribe.transcaribe.Model.Usuario;
import com.transcaribe.transcaribe.Repository.UsuarioRepository;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

@Controller
@RequestMapping("/chat")
public class ChatController {

    private static final int MAX_MESSAGE_LENGTH = 1000;
    private static final int MAX_MESSAGES = 100;
    private static final int MAX_IMAGE_BYTES = 5 * 1024 * 1024;
    private static final Duration EDIT_WINDOW = Duration.ofMinutes(15);
    private static final DateTimeFormatter DISPLAY_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final MongoTemplate mongoTemplate;
    private final UsuarioRepository usuarioRepository;

    public ChatController(MongoTemplate mongoTemplate, UsuarioRepository usuarioRepository) {
        this.mongoTemplate = mongoTemplate;
        this.usuarioRepository = usuarioRepository;
    }

    @GetMapping
    public String mostrarChat(Authentication authentication, HttpSession session, Model model) {
        Usuario participante = obtenerParticipante(authentication, session);
        SistemaTransporte sistema = obtenerSistema(participante, session);
        if (sistema == null) return "redirect:/admin/seleccionar-entidad";
        model.addAttribute("urlPanel", Usuario.ROLE_CONDUCTOR.equals(participante.getRole())
                ? "/conductor/panel" : "/admin/dashboard");
        model.addAttribute("rolParticipante", participante.getRole());
        model.addAttribute("sistemaActual", sistema);
        model.addAttribute("operacionesConductorDisponibles",
                sistema == SistemaTransporte.TRANSCARIBE);
        return "chat/sala";
    }

    @GetMapping("/mensajes")
    @ResponseBody
    public List<MensajeRespuesta> listarMensajes(Authentication authentication, HttpSession session) {
        Usuario participante = obtenerParticipante(authentication, session);
        SistemaTransporte sistema = requerirSistema(participante, session);
        return obtenerMensajes(participante.getId(), sistema);
    }

    @PostMapping(value = "/mensajes", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseBody
    public MensajeRespuesta enviarMensajeApi(@RequestParam(required = false) String texto,
                                             @RequestParam(name = "imagen", required = false) MultipartFile imagen,
                                             Authentication authentication, HttpSession session) {
        Usuario autor = obtenerParticipante(authentication, session);
        SistemaTransporte sistema = requerirSistema(autor, session);
        String contenido = texto == null ? "" : texto.trim();
        if (contenido.length() > MAX_MESSAGE_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El mensaje no puede superar los 1000 caracteres.");
        }
        ImagenChat imagenValidada = validarImagen(imagen);
        if (contenido.isEmpty() && imagenValidada == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Escribe un mensaje o selecciona una imagen.");
        }

        ChatMessage mensaje = new ChatMessage();
        mensaje.setAuthorId(autor.getId());
        mensaje.setAuthorName(autor.getNombre());
        mensaje.setAuthorRole(autor.getRole());
        mensaje.setSistemaTransporte(sistema);
        mensaje.setText(contenido);
        mensaje.setCreatedAt(LocalDateTime.now());
        if (imagenValidada != null) {
            mensaje.setImage(imagenValidada.contenido());
            mensaje.setImageContentType(imagenValidada.tipoContenido());
            mensaje.setHasImage(true);
        }
        mongoTemplate.save(mensaje);
        return crearRespuesta(mensaje, autor.getId());
    }

    @PutMapping("/mensajes/{mensajeId}")
    @ResponseBody
    public MensajeRespuesta editarMensaje(@PathVariable String mensajeId,
                                          @RequestBody EditarMensajeSolicitud solicitud,
                                          Authentication authentication, HttpSession session) {
        Usuario autor = obtenerParticipante(authentication, session);
        SistemaTransporte sistema = requerirSistema(autor, session);
        String contenido = solicitud == null || solicitud.texto() == null
                ? "" : solicitud.texto().trim();
        if (contenido.isEmpty() || contenido.length() > MAX_MESSAGE_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El mensaje debe tener entre 1 y 1000 caracteres.");
        }

        LocalDateTime ahora = LocalDateTime.now();
        Query query = new Query(new Criteria().andOperator(
                Criteria.where("_id").is(mensajeId),
                criterioSala(sistema),
                Criteria.where("authorId").is(autor.getId()),
                Criteria.where("createdAt").gte(ahora.minus(EDIT_WINDOW)).lte(ahora)));
        Update update = new Update().set("text", contenido).set("editedAt", ahora);
        ChatMessage actualizado = mongoTemplate.findAndModify(
                query, update, FindAndModifyOptions.options().returnNew(true), ChatMessage.class);
        if (actualizado == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Solo puedes editar tus propios mensajes durante los primeros 15 minutos.");
        }
        return crearRespuesta(actualizado, autor.getId());
    }

    @GetMapping("/mensajes/{mensajeId}/imagen")
    @ResponseBody
    public ResponseEntity<byte[]> obtenerImagen(@PathVariable String mensajeId,
                                                Authentication authentication, HttpSession session) {
        Usuario participante = obtenerParticipante(authentication, session);
        SistemaTransporte sistema = requerirSistema(participante, session);
        ChatMessage mensaje = mongoTemplate.findById(mensajeId, ChatMessage.class);
        if (mensaje == null || mensaje.getSistemaTransporte() != sistema
                || (!mensaje.isHasImage() && mensaje.getImage() == null)
                || mensaje.getImageContentType() == null || mensaje.getImage() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(mensaje.getImageContentType()))
                .cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .body(mensaje.getImage());
    }

    @ExceptionHandler(ResponseStatusException.class)
    @ResponseBody
    public ResponseEntity<ErrorRespuesta> manejarErrorApi(ResponseStatusException exception) {
        return ResponseEntity.status(exception.getStatusCode())
                .body(new ErrorRespuesta(exception.getReason() == null
                        ? "No se pudo completar la solicitud." : exception.getReason()));
    }

    @PostMapping("/enviar")
    public String enviarMensaje(@RequestParam(required = false) String texto,
                                Authentication authentication, HttpSession session,
                                RedirectAttributes redirectAttributes) {
        Usuario autor = obtenerParticipante(authentication, session);
        SistemaTransporte sistema = requerirSistema(autor, session);
        String contenido = texto == null ? "" : texto.trim();
        if (contenido.isEmpty() || contenido.length() > MAX_MESSAGE_LENGTH) {
            redirectAttributes.addFlashAttribute("errorChat",
                    "El mensaje debe tener entre 1 y 1000 caracteres.");
            return "redirect:/chat";
        }

        ChatMessage mensaje = new ChatMessage();
        mensaje.setAuthorId(autor.getId());
        mensaje.setAuthorName(autor.getNombre());
        mensaje.setAuthorRole(autor.getRole());
        mensaje.setSistemaTransporte(sistema);
        mensaje.setText(contenido);
        mensaje.setCreatedAt(LocalDateTime.now());
        mongoTemplate.save(mensaje);
        return "redirect:/chat";
    }

    private Usuario obtenerParticipante(Authentication authentication, HttpSession session) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        Usuario usuario = usuarioRepository.findByCorreo(authentication.getName())
                .filter(Usuario::isActivo)
                .filter(candidato -> Usuario.ROLE_ADMIN.equals(candidato.getRole())
                        || Usuario.ROLE_MODERATOR.equals(candidato.getRole())
                        || Usuario.ROLE_CONDUCTOR.equals(candidato.getRole()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN));
        if (Usuario.ROLE_MODERATOR.equals(usuario.getRole())) {
            SistemaTransporte asignado = usuario.getSistemaAsignado();
            if (asignado == null || !asignado.estaDisponible()) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN);
            }
            session.setAttribute(SistemaTransporte.SESSION_ATTRIBUTE, asignado);
        }
        return usuario;
    }

    private SistemaTransporte obtenerSistema(Usuario usuario, HttpSession session) {
        if (Usuario.ROLE_MODERATOR.equals(usuario.getRole())) return usuario.getSistemaAsignado();
        if (Usuario.ROLE_CONDUCTOR.equals(usuario.getRole())) {
            SistemaTransporte sitm = usuario.getSitm();
            return sitm != null && sitm.estaDisponible() ? sitm : SistemaTransporte.TRANSCARIBE;
        }
        Object seleccionado = session.getAttribute(SistemaTransporte.SESSION_ATTRIBUTE);
        return seleccionado instanceof SistemaTransporte sistema && sistema.estaDisponible() ? sistema : null;
    }

    private SistemaTransporte requerirSistema(Usuario usuario, HttpSession session) {
        SistemaTransporte sistema = obtenerSistema(usuario, session);
        if (sistema == null) throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "Selecciona un servicio para abrir su chat operativo.");
        return sistema;
    }

    private List<MensajeRespuesta> obtenerMensajes(String participanteId, SistemaTransporte sistema) {
        Query query = new Query(criterioSala(sistema))
                .with(Sort.by(Sort.Direction.DESC, "createdAt"))
                .limit(MAX_MESSAGES);
        query.fields().exclude("image");
        List<ChatMessage> mensajes = mongoTemplate.find(query, ChatMessage.class);
        Collections.reverse(mensajes);
        return mensajes.stream().map(mensaje -> crearRespuesta(mensaje, participanteId)).toList();
    }

    private Criteria criterioSala(SistemaTransporte sistema) {
        if (sistema == SistemaTransporte.TRANSCARIBE) {
            return new Criteria().orOperator(
                    Criteria.where("sistemaTransporte").is(sistema),
                    Criteria.where("sistemaTransporte").exists(false));
        }
        return Criteria.where("sistemaTransporte").is(sistema);
    }

    private MensajeRespuesta crearRespuesta(ChatMessage mensaje, String participanteId) {
        return new MensajeRespuesta(
                mensaje.getId(),
                mensaje.getAuthorName(),
                etiquetaRol(mensaje.getAuthorRole()),
                mensaje.getText(),
                mensaje.getCreatedAt() == null ? "" : mensaje.getCreatedAt().format(DISPLAY_TIME),
                mensaje.isHasImage() || (mensaje.getImage() != null && mensaje.getImage().length > 0),
                mensaje.getEditedAt() != null,
                puedeEditar(mensaje, participanteId));
    }

    private boolean puedeEditar(ChatMessage mensaje, String participanteId) {
        return participanteId != null
                && participanteId.equals(mensaje.getAuthorId())
                && mensaje.getCreatedAt() != null
                && !mensaje.getCreatedAt().isAfter(LocalDateTime.now())
                && !mensaje.getCreatedAt().plus(EDIT_WINDOW).isBefore(LocalDateTime.now());
    }

    private ImagenChat validarImagen(MultipartFile imagen) {
        if (imagen == null || imagen.isEmpty()) {
            return null;
        }
        if (imagen.getSize() > MAX_IMAGE_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La imagen no puede superar los 5 MB.");
        }
        String tipo = imagen.getContentType();
        if (!MediaType.IMAGE_PNG_VALUE.equalsIgnoreCase(tipo)
                && !MediaType.IMAGE_JPEG_VALUE.equalsIgnoreCase(tipo)
                && !"image/webp".equalsIgnoreCase(tipo)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La imagen debe estar en formato PNG, JPEG o WebP.");
        }
        try {
            byte[] contenido = imagen.getBytes();
            if (!esImagenValida(contenido, tipo)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "El archivo seleccionado no es una imagen PNG, JPEG o WebP válida.");
            }
            return new ImagenChat(contenido, tipo.toLowerCase(Locale.ROOT));
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "No se pudo leer la imagen seleccionada.", exception);
        }
    }

    private boolean esImagenValida(byte[] contenido, String tipo) {
        if (MediaType.IMAGE_PNG_VALUE.equalsIgnoreCase(tipo)) {
            return contenido.length >= 8
                    && contenido[0] == (byte) 0x89 && contenido[1] == 0x50
                    && contenido[2] == 0x4E && contenido[3] == 0x47
                    && contenido[4] == 0x0D && contenido[5] == 0x0A
                    && contenido[6] == 0x1A && contenido[7] == 0x0A;
        }
        if (MediaType.IMAGE_JPEG_VALUE.equalsIgnoreCase(tipo)) {
            return contenido.length >= 3
                    && contenido[0] == (byte) 0xFF
                    && contenido[1] == (byte) 0xD8
                    && contenido[2] == (byte) 0xFF;
        }
        return contenido.length >= 12
                && contenido[0] == 'R' && contenido[1] == 'I'
                && contenido[2] == 'F' && contenido[3] == 'F'
                && contenido[8] == 'W' && contenido[9] == 'E'
                && contenido[10] == 'B' && contenido[11] == 'P';
    }

    private String etiquetaRol(String role) {
        if (Usuario.ROLE_ADMIN.equals(role)) return "Administrador";
        if (Usuario.ROLE_MODERATOR.equals(role)) return "Moderador";
        return "Conductor";
    }

    public record MensajeRespuesta(String id, String authorName, String authorRole, String text,
                                   String createdAt, boolean hasImage, boolean edited, boolean canEdit) {
    }

    public record EditarMensajeSolicitud(String texto) {
    }

    public record ErrorRespuesta(String error) {
    }

    private record ImagenChat(byte[] contenido, String tipoContenido) {
    }
}

package com.transcaribe.transcaribe.Controller;

import com.transcaribe.transcaribe.Model.Usuario;
import com.transcaribe.transcaribe.Model.SistemaTransporte;
import com.transcaribe.transcaribe.Repository.UsuarioRepository;
import com.transcaribe.transcaribe.service.AvisoAdministrativoService;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import jakarta.servlet.http.HttpSession;

@Controller
public class AvisoUsuarioController {

    private final AvisoAdministrativoService avisoService;
    private final UsuarioRepository usuarioRepository;

    public AvisoUsuarioController(AvisoAdministrativoService avisoService,
                                  UsuarioRepository usuarioRepository) {
        this.avisoService = avisoService;
        this.usuarioRepository = usuarioRepository;
    }

    @GetMapping("/avisos/{avisoId}/imagen")
    public ResponseEntity<byte[]> obtenerImagen(@PathVariable String avisoId,
                                                Authentication authentication, HttpSession session) {
        Usuario usuario = usuarioRepository.findByCorreo(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        SistemaTransporte sistema = sistemaUsuario(usuario, session);
        try {
            AvisoAdministrativoService.ImagenAviso imagen =
                    avisoService.obtenerImagenParaUsuario(avisoId, usuario.getId(),
                            authentication.getAuthorities().stream()
                                    .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority())), sistema);
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(imagen.tipoContenido()))
                    .cacheControl(CacheControl.noStore())
                    .header("X-Content-Type-Options", "nosniff")
                    .body(imagen.contenido());
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, exception.getMessage());
        } catch (SecurityException exception) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, exception.getMessage());
        }
    }

    @PostMapping("/avisos/{avisoId}/cerrar")
    public String cerrarAviso(@PathVariable String avisoId,
                              @RequestParam(defaultValue = "/menu") String volverA,
                              Authentication authentication, HttpSession session) {
        Usuario usuario = usuarioRepository.findByCorreo(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        SistemaTransporte sistema = sistemaUsuario(usuario, session);
        try {
            avisoService.descartarParaUsuario(avisoId, usuario.getId(), sistema);
        } catch (SecurityException exception) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, exception.getMessage());
        }
        return "redirect:" + rutaPermitida(volverA);
    }

    private SistemaTransporte sistemaUsuario(Usuario usuario, HttpSession session) {
        if (Usuario.ROLE_MODERATOR.equals(usuario.getRole())) return usuario.getSistemaAsignado();
        if (Usuario.ROLE_ADMIN.equals(usuario.getRole())) {
            Object sistema = session.getAttribute(SistemaTransporte.SESSION_ATTRIBUTE);
            return sistema instanceof SistemaTransporte seleccionado && seleccionado.estaDisponible()
                    ? seleccionado : null;
        }
        SistemaTransporte sistema = usuario.getSitm();
        return sistema != null && sistema.estaDisponible() ? sistema : SistemaTransporte.TRANSCARIBE;
    }

    private String rutaPermitida(String ruta) {
        if ("/menu".equals(ruta) || "/choose-view".equals(ruta)
                || "/admin/dashboard".equals(ruta) || "/conductor/panel".equals(ruta)) {
            return ruta;
        }
        return "/menu";
    }
}

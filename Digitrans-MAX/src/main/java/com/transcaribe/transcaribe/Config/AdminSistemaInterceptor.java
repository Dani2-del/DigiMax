package com.transcaribe.transcaribe.Config;

import com.transcaribe.transcaribe.Model.SistemaTransporte;
import com.transcaribe.transcaribe.Model.Usuario;
import com.transcaribe.transcaribe.Repository.UsuarioRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AdminSistemaInterceptor implements HandlerInterceptor {

    private final UsuarioRepository usuarioRepository;

    public AdminSistemaInterceptor(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        Authentication authentication = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return false;
        }

        Usuario usuario = usuarioRepository.findByCorreo(authentication.getName()).orElse(null);
        if (usuario == null) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return false;
        }

        HttpSession session = request.getSession();
        if (Usuario.ROLE_MODERATOR.equals(usuario.getRole())) {
            SistemaTransporte asignado = usuario.getSistemaAsignado();
            if (asignado == null || !asignado.estaDisponible()) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN,
                        "La cuenta moderadora no tiene una entidad válida asignada.");
                return false;
            }
            session.setAttribute(SistemaTransporte.SESSION_ATTRIBUTE, asignado);
            return permitirHerramientasSistema(request, response, asignado);
        }

        Object seleccionado = session.getAttribute(SistemaTransporte.SESSION_ATTRIBUTE);
        if (!(seleccionado instanceof SistemaTransporte sistema) || !sistema.estaDisponible()) {
            response.sendRedirect(request.getContextPath() + "/admin/seleccionar-entidad");
            return false;
        }
        return permitirHerramientasSistema(request, response, sistema);
    }

    private boolean permitirHerramientasSistema(HttpServletRequest request,
                                                HttpServletResponse response,
                                                SistemaTransporte sistema) throws java.io.IOException {
        String ruta = request.getRequestURI().substring(request.getContextPath().length());
        boolean gestionConductores = ruta.equals("/admin/horarios")
                || (ruta.startsWith("/admin/horarios/") && !ruta.startsWith("/admin/horarios/buses"));
        boolean gestionBuses = ruta.startsWith("/admin/horarios/buses");
        boolean mapaEnPreparacion = ruta.equals("/admin/mapa-rutas/editar");
        if ((gestionConductores && !sistema.permiteGestionConductores())
                || (gestionBuses && !sistema.permiteGestionConductores())
                || (mapaEnPreparacion && !sistema.tieneRutasConfiguradas())) {
            response.sendRedirect(request.getContextPath() + "/admin/dashboard?funcion=proximamente");
            return false;
        }
        return true;
    }
}

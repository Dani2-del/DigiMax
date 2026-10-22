package com.transcaribe.transcaribe.service;

import com.transcaribe.transcaribe.Model.HorarioConductor;
import com.transcaribe.transcaribe.Model.PreferenciaRutaNotificacion;
import com.transcaribe.transcaribe.Model.Usuario;
import com.transcaribe.transcaribe.Repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.Pattern;

@Service
public class RutaNotificacionService {

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private EmailService emailService;

    /**
     * Busca a todos los usuarios que tienen la ruta indicada en sus favoritas
     * y les envía un correo notificando que el bus salió.
     * Retorna la cantidad de usuarios notificados.
     */
    public int notificarInicioRuta(HorarioConductor horario, String placaBus) {
        if (horario.getRuta() == null || horario.getHoraInicio() == null) {
            return 0;
        }

        String ruta = horario.getRuta().trim();
        if (ruta.isEmpty()) {
            return 0;
        }
        String rutaRegex = "^\\s*" + Pattern.quote(ruta) + "\\s*$";
        List<Usuario> interesados = usuarioRepository.findByRutaFavoritaIgnoreCase(rutaRegex);
        int notificados = 0;

        for (Usuario usuario : interesados) {
            PreferenciaRutaNotificacion preferencia = usuario.getPreferenciasNotificacionRutas().stream()
                    .filter(item -> item != null && item.getRuta() != null
                            && ruta.equalsIgnoreCase(item.getRuta().trim()))
                    .findFirst()
                    .orElse(null);
            if (preferencia == null || !preferencia.incluyeHora(horario.getHoraInicio())) {
                continue;
            }

            emailService.enviarNotificacionRutaIniciada(
                    usuario.getCorreo(),
                    usuario.getNombre() != null ? usuario.getNombre() : usuario.getCorreo(),
                    ruta,
                    horario.getSentido(),
                    placaBus,
                    horario.getHoraInicio().toString()
            );
            notificados++;
        }

        return notificados;
    }
}

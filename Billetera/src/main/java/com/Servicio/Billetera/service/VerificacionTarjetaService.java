package com.Servicio.Billetera.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.Servicio.Billetera.model.AccionVerificacionTarjeta;
import com.Servicio.Billetera.model.VerificacionTarjeta;
import com.Servicio.Billetera.repository.VerificacionTarjetaRepository;

@Service
public class VerificacionTarjetaService {

    private static final int MAX_INTENTOS = 5;
    private static final int DURACION_MINUTOS = 10;
    private static final int ESPERA_REENVIO_SEGUNDOS = 60;

    private final VerificacionTarjetaRepository verificaciones;
    private final ServicioCorreoBilletera correo;
    private final SecureRandom random = new SecureRandom();

    public VerificacionTarjetaService(
            VerificacionTarjetaRepository verificaciones, ServicioCorreoBilletera correo) {
        this.verificaciones = verificaciones;
        this.correo = correo;
    }

    public void iniciar(
            String usuarioId, String email, String nombre, AccionVerificacionTarjeta accion) {
        if (!StringUtils.hasText(usuarioId) || !StringUtils.hasText(email) || accion == null) {
            throw new IllegalArgumentException("Se requiere una cuenta con correo válido para verificar la tarjeta.");
        }

        LocalDateTime ahora = LocalDateTime.now();
        Optional<VerificacionTarjeta> existing = verificaciones.findById(usuarioId);
        VerificacionTarjeta verificacion = existing.orElseGet(VerificacionTarjeta::new);
        if (existing.isPresent()
                && !verificacion.isConsumida()
                && verificacion.getSolicitadaEn() != null
                && verificacion.getSolicitadaEn().isAfter(ahora.minusSeconds(ESPERA_REENVIO_SEGUNDOS))) {
            throw new IllegalArgumentException("Espera un minuto antes de solicitar otro código.");
        }
        String codigo = String.format("%06d", random.nextInt(1_000_000));
        byte[] saltBytes = new byte[16];
        random.nextBytes(saltBytes);
        String salt = Base64.getEncoder().encodeToString(saltBytes);

        verificacion.setUsuarioId(usuarioId);
        verificacion.setAccion(accion);
        verificacion.setSalt(salt);
        verificacion.setCodigoHash(hash(usuarioId, salt, codigo));
        verificacion.setSolicitadaEn(ahora);
        verificacion.setExpiraEn(ahora.plusMinutes(DURACION_MINUTOS));
        verificacion.setIntentosFallidos(0);
        verificacion.setConsumida(false);
        verificaciones.save(verificacion);

        try {
            correo.enviarCodigoTarjeta(email, nombre, codigo, accion);
        } catch (MailDeliveryException exception) {
            verificacion.setConsumida(true);
            verificacion.setExpiraEn(ahora);
            verificaciones.save(verificacion);
            throw exception;
        }
    }

    public Optional<AccionVerificacionTarjeta> accionPendiente(String usuarioId) {
        return verificaciones.findById(usuarioId)
                .filter(verificacion -> !verificacion.isConsumida())
                .filter(verificacion -> verificacion.getExpiraEn() != null
                        && verificacion.getExpiraEn().isAfter(LocalDateTime.now()))
                .map(VerificacionTarjeta::getAccion);
    }

    public AccionVerificacionTarjeta verificar(String usuarioId, String codigo) {
        VerificacionTarjeta verificacion = verificaciones.findById(usuarioId)
                .orElseThrow(() -> new ErrorVerificacionTarjeta(EstadoErrorVerificacion.REQUERIDA));
        LocalDateTime ahora = LocalDateTime.now();
        if (verificacion.isConsumida()) {
            throw new ErrorVerificacionTarjeta(EstadoErrorVerificacion.REQUERIDA);
        }
        if (verificacion.getExpiraEn() == null || !verificacion.getExpiraEn().isAfter(ahora)) {
            verificaciones.deleteById(usuarioId);
            throw new ErrorVerificacionTarjeta(EstadoErrorVerificacion.EXPIRADA);
        }
        if (verificacion.getIntentosFallidos() >= MAX_INTENTOS) {
            verificaciones.deleteById(usuarioId);
            throw new ErrorVerificacionTarjeta(EstadoErrorVerificacion.BLOQUEADA);
        }
        if (codigo == null || !codigo.matches("\\d{6}")
                || !MessageDigest.isEqual(
                        verificacion.getCodigoHash().getBytes(StandardCharsets.US_ASCII),
                        hash(usuarioId, verificacion.getSalt(), codigo).getBytes(StandardCharsets.US_ASCII))) {
            verificacion.setIntentosFallidos(verificacion.getIntentosFallidos() + 1);
            if (verificacion.getIntentosFallidos() >= MAX_INTENTOS) {
                verificaciones.deleteById(usuarioId);
                throw new ErrorVerificacionTarjeta(EstadoErrorVerificacion.BLOQUEADA);
            }
            verificacion = verificaciones.save(verificacion);
            throw new ErrorVerificacionTarjeta(EstadoErrorVerificacion.CODIGO_INVALIDO);
        }

        verificacion.setConsumida(true);
        try {
            verificaciones.save(verificacion);
        } catch (OptimisticLockingFailureException exception) {
            throw new ErrorVerificacionTarjeta(EstadoErrorVerificacion.REQUERIDA);
        }
        return verificacion.getAccion();
    }

    private static String hash(String usuarioId, String salt, String codigo) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] result = digest.digest(
                    (usuarioId + ":" + salt + ":" + codigo).getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(result);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("No está disponible SHA-256 para verificar la tarjeta.", exception);
        }
    }

    public enum EstadoErrorVerificacion {
        REQUERIDA,
        EXPIRADA,
        CODIGO_INVALIDO,
        BLOQUEADA
    }

    public static class ErrorVerificacionTarjeta extends RuntimeException {
        private static final long serialVersionUID = 1L;
        private final EstadoErrorVerificacion estado;

        public ErrorVerificacionTarjeta(EstadoErrorVerificacion estado) {
            super(switch (estado) {
                case REQUERIDA -> "Solicita un nuevo código de verificación para continuar.";
                case EXPIRADA -> "El código expiró. Solicita uno nuevo.";
                case CODIGO_INVALIDO -> "El código ingresado no es válido.";
                case BLOQUEADA -> "Se agotaron los intentos. Solicita un nuevo código.";
            });
            this.estado = estado;
        }

        public EstadoErrorVerificacion getEstado() {
            return estado;
        }
    }
}

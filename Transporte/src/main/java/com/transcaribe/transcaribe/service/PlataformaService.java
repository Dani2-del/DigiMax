package com.transcaribe.transcaribe.service;

import java.math.BigDecimal;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.transcaribe.transcaribe.Model.Tarjeta;
import com.transcaribe.transcaribe.Model.SistemaTransporte;
import com.transcaribe.transcaribe.Model.Transaccion;
import com.transcaribe.transcaribe.Model.Usuario;
import com.transcaribe.transcaribe.Repository.UsuarioRepository;
import com.transcaribe.transcaribe.Util.TarifaTranscaribe;

/**
 * Servicio de transporte para administrar tarjetas, transacciones y alertas por correo.
 */
@Service
public class PlataformaService {

    private final UsuarioRepository repositorioUsuarios;
    private final TransaccionService servicioTransacciones;
    private final EmailService emailService;

    public PlataformaService(UsuarioRepository repositorioUsuarios,
                             TransaccionService servicioTransacciones,
                             EmailService emailService) {
        this.repositorioUsuarios = repositorioUsuarios;
        this.servicioTransacciones = servicioTransacciones;
        this.emailService = emailService;
    }

    /**
     * Recarga saldo directo a la primera tarjeta asociada del usuario.
     */
    public void recargar(Usuario usuario, double monto) {
        if (usuario != null && usuario.isActivo() && monto > 0 && !usuario.getTarjetas().isEmpty()) {
            Tarjeta t = usuario.getTarjetas().get(0);
            t.setSaldo(t.getSaldo().add(BigDecimal.valueOf(monto)));
            
            repositorioUsuarios.save(usuario);
            servicioTransacciones.registrarTransaccion(usuario, "Recarga Directa", monto);
            emailService.enviarNotificacionRecarga(usuario.getCorreo(), usuario.getNombre(), monto, t.getSaldo().toString());
        }
    }

    /**
     * Descuenta un monto de la primera tarjeta del usuario.
     */


    /**
     * Realiza una recarga de saldo filtrando por el número de tarjeta específico.
     */
    public boolean recargarEnTarjeta(Usuario usuario, String numeroTarjeta, double monto) {
        return recargarEnTarjetaConRecibo(usuario, numeroTarjeta, monto).isPresent();
    }

    public Optional<Transaccion> recargarEnTarjetaConRecibo(
            Usuario usuario, String numeroTarjeta, double monto) {
        return recargarEnTarjetaConRecibo(usuario, numeroTarjeta, monto, null, null);
    }

    public Optional<Transaccion> recargarEnTarjetaConRecibo(
            Usuario usuario, String numeroTarjeta, double monto, String metodoPago, String cuentaPse) {
        return recargarEnTarjetaConRecibo(
                usuario, numeroTarjeta, monto, metodoPago, cuentaPse, SistemaTransporte.TRANSCARIBE);
    }

    public Optional<Transaccion> recargarEnTarjetaConRecibo(
            Usuario usuario, String numeroTarjeta, double monto, String metodoPago, String cuentaPse,
            SistemaTransporte sistemaTransporte) {
        return recargarEnTarjetaConRecibo(
                usuario, numeroTarjeta, monto, metodoPago, cuentaPse, null, sistemaTransporte);
    }

    public Optional<Transaccion> recargarPasajesEnTarjetaConRecibo(
            Usuario usuario, String numeroTarjeta, int cantidadPasajes, String metodoPago, String cuentaPse) {
        return recargarPasajesEnTarjetaConRecibo(
                usuario, numeroTarjeta, cantidadPasajes, metodoPago, cuentaPse, SistemaTransporte.TRANSCARIBE);
    }

    public Optional<Transaccion> recargarPasajesEnTarjetaConRecibo(
            Usuario usuario, String numeroTarjeta, int cantidadPasajes, String metodoPago,
            String cuentaPse, SistemaTransporte sistemaTransporte) {
        if (cantidadPasajes <= 0 || sistemaTransporte == null || !sistemaTransporte.estaDisponible()) {
            return Optional.empty();
        }
        long monto = Math.multiplyExact((long) cantidadPasajes,
                TarifaTranscaribe.paraSistema(sistemaTransporte));
        return recargarEnTarjetaConRecibo(
                usuario, numeroTarjeta, (double) monto, metodoPago, cuentaPse,
                cantidadPasajes, sistemaTransporte);
    }

    private Optional<Transaccion> recargarEnTarjetaConRecibo(
            Usuario usuario, String numeroTarjeta, double monto, String metodoPago,
            String cuentaPse, Integer cantidadPasajes) {
        return recargarEnTarjetaConRecibo(usuario, numeroTarjeta, monto, metodoPago,
                cuentaPse, cantidadPasajes, SistemaTransporte.TRANSCARIBE);
    }

    private Optional<Transaccion> recargarEnTarjetaConRecibo(
            Usuario usuario, String numeroTarjeta, double monto, String metodoPago,
            String cuentaPse, Integer cantidadPasajes, SistemaTransporte sistemaTransporte) {
        if (usuario == null || !usuario.isActivo() || monto <= 0
                || sistemaTransporte == null || !sistemaTransporte.estaDisponible()) {
            return Optional.empty();
        }

        return usuario.getTarjetas().stream()
                .filter(t -> numeroTarjeta.equals(t.getNumeroTarjeta()))
                .filter(t -> t.getSistemaTransporte() == sistemaTransporte)
                .findFirst()
                .map(t -> {
                    t.setSaldo(t.getSaldo().add(BigDecimal.valueOf(monto)));
                    repositorioUsuarios.save(usuario);
                    return servicioTransacciones.registrarTransaccion(
                            usuario,
                            "Recarga Tarjeta: " + ocultarNumeroTarjeta(numeroTarjeta),
                            monto,
                            metodoPago,
                            cuentaPse,
                            ocultarNumeroTarjeta(numeroTarjeta),
                            cantidadPasajes,
                            sistemaTransporte);
                });
    }

    /**
     * Obtiene la primera tarjeta de la lista del usuario.
     */
    public Tarjeta obtenerTarjetaDeUsuario(Usuario usuario) {
        if (usuario == null || usuario.getTarjetas().isEmpty()) return null;
        return usuario.getTarjetas().get(0);
    }

    /**
     * Cobro de pasaje administrativo en la primera tarjeta activa del usuario.
     */



    /**
     * Establece la configuración de límite de saldo mínimo en una tarjeta especificada
     * y comprueba de inmediato si el saldo actual ya se encuentra por debajo del umbral.
     */
    public boolean establecerLimiteSaldo(Usuario usuario, String numeroTarjeta, BigDecimal limiteSaldo) {
        return establecerLimiteSaldo(usuario, numeroTarjeta, limiteSaldo, SistemaTransporte.TRANSCARIBE);
    }

    public boolean establecerLimiteSaldo(
            Usuario usuario, String numeroTarjeta, BigDecimal limiteSaldo, SistemaTransporte sistemaTransporte) {
        if (usuario == null || numeroTarjeta == null || limiteSaldo == null) return false;

        return usuario.getTarjetas().stream()
                .filter(t -> t.getNumeroTarjeta().equals(numeroTarjeta))
                .filter(t -> t.getSistemaTransporte() == sistemaTransporte)
                .findFirst()
                .map(t -> {
                    t.setLimiteSaldo(limiteSaldo);
                    repositorioUsuarios.save(usuario);
                    
                    // Comprobación y envío inmediato si el saldo actual ya es menor o igual al nuevo límite
                    verificarYNotificarLimiteSaldo(usuario, t);
                    
                    return true;
                }).orElse(false);
    }

    /**
     * Evalúa si el saldo actual cayó por debajo del límite configurado y dispara el correo de alerta.
     */
        public void verificarYNotificarLimiteSaldo(Usuario usuario, Tarjeta tarjeta) {
            System.out.println("=== VERIFICANDO LIMITE DE SALDO ===");
            System.out.println("Tarjeta: " + tarjeta.getNumeroTarjeta());
            System.out.println("Saldo Actual: " + tarjeta.getSaldo());
            System.out.println("Límite Configurado: " + tarjeta.getLimiteSaldo());

            if (tarjeta.getLimiteSaldo() == null) {
                System.err.println("⚠️ La tarjeta NO tiene un límite de saldo configurado (es null).");
                return;
            }

            if (tarjeta.getSaldo().compareTo(tarjeta.getLimiteSaldo()) <= 0) {
                System.out.println("✅ El saldo es menor o igual al límite. Intentando enviar correo...");
                try {
                    emailService.enviarAlertaSaldoBajo(
                            usuario.getCorreo(),
                            usuario.getNombre(),
                            tarjeta.getNumeroTarjeta(),
                            tarjeta.getSaldo().toString(),
                            tarjeta.getLimiteSaldo().toString()
                    );
                    System.out.println("📧 Correo de alerta de saldo bajo despachado correctamente a " + usuario.getCorreo());
                } catch (Exception e) {
                    System.err.println("❌ Error al enviar alerta de saldo bajo: " + e.getMessage());
                    e.printStackTrace();
                }
            } else {
                System.out.println("ℹ️ El saldo actual ($" + tarjeta.getSaldo() + ") aún supera el límite ($" + tarjeta.getLimiteSaldo() + ").");
            }
        }

    /**
     * Vincula una nueva tarjeta simple al usuario.
     */
    public boolean agregarNuevaTarjeta(String idUsuario, String nuevoNumero) {
        return agregarNuevaTarjeta(idUsuario, nuevoNumero, SistemaTransporte.TRANSCARIBE);
    }

    public boolean agregarNuevaTarjeta(
            String idUsuario, String nuevoNumero, SistemaTransporte sistemaTransporte) {
        if (sistemaTransporte == null || !sistemaTransporte.estaDisponible()) {
            return false;
        }
        Optional<Usuario> opt = repositorioUsuarios.findById(idUsuario);
        if (opt.isPresent()) {
            Usuario u = opt.get();
            Optional<Usuario> dueñoExistente = repositorioUsuarios
                    .findByNumeroTarjetaEnListaYSistema(nuevoNumero, sistemaTransporte);
            if (dueñoExistente.filter(dueño -> !idUsuario.equals(dueño.getId())).isPresent()) {
                return false;
            }
            boolean existe = u.getTarjetas().stream().anyMatch(t -> t.getNumeroTarjeta().equals(nuevoNumero)
                    && t.getSistemaTransporte() == sistemaTransporte);
            if (!existe) {
                u.agregarTarjeta(nuevoNumero, BigDecimal.ZERO, sistemaTransporte);
                repositorioUsuarios.save(u);
                return true;
            }
        }
        return false;
    }

    /**
     * Oculta los primeros dígitos de la tarjeta dejando visibles únicamente los últimos 4.
     */
    private String ocultarNumeroTarjeta(String numero) {
        if (numero == null || numero.length() < 4) return "****";
        return "******" + numero.substring(numero.length() - 4);
    }

    /**
     * Vincula una tarjeta si el número todavía no existe dentro del SITM seleccionado.
     */
    public String agregarNuevaTarjetaConValidacion(String idUsuario, String nuevoNumero) {
        return agregarNuevaTarjetaConValidacion(idUsuario, nuevoNumero, SistemaTransporte.TRANSCARIBE);
    }

    public String agregarNuevaTarjetaConValidacion(
            String idUsuario, String nuevoNumero, SistemaTransporte sistemaTransporte) {
        if (sistemaTransporte == null || !sistemaTransporte.estaDisponible()) {
            return "Sistema de transporte inválido.";
        }
        Optional<Usuario> dueñoExistente = repositorioUsuarios
                .findByNumeroTarjetaEnListaYSistema(nuevoNumero, sistemaTransporte);
        
        if (dueñoExistente.filter(dueño -> !idUsuario.equals(dueño.getId())).isPresent()) {
            return "La tarjeta ya está registrada por otro usuario.";
        }

        Optional<Usuario> opt = repositorioUsuarios.findById(idUsuario);
        if (opt.isPresent()) {
            Usuario u = opt.get();
            
            boolean yaLaTiene = u.getTarjetas().stream()
                    .anyMatch(t -> t.getNumeroTarjeta().equals(nuevoNumero)
                            && t.getSistemaTransporte() == sistemaTransporte);
            
            if (!yaLaTiene) {
                u.agregarTarjeta(nuevoNumero, BigDecimal.ZERO, sistemaTransporte);
                repositorioUsuarios.save(u);
                return "OK";
            } else {
                return "Ya tienes esta tarjeta agregada.";
            }
        }
        return "Usuario no encontrado.";
    }

    /**
     * Remueve una tarjeta vinculada garantizando que el usuario conserve al menos una.
     */
    public boolean eliminarTarjetaDeUsuario(String usuarioId, String numeroTarjeta) {
        return eliminarTarjetaDeUsuario(usuarioId, numeroTarjeta, SistemaTransporte.TRANSCARIBE);
    }

    public boolean eliminarTarjetaDeUsuario(
            String usuarioId, String numeroTarjeta, SistemaTransporte sistemaTransporte) {
        return repositorioUsuarios.findById(usuarioId).map(usuario -> {
            if (usuario.getTarjetas().size() > 1) {
                boolean removida = usuario.getTarjetas().removeIf(t -> 
                    t.getNumeroTarjeta().equals(numeroTarjeta)
                            && t.getSistemaTransporte() == sistemaTransporte);
                
                if (removida) {
                    repositorioUsuarios.save(usuario); 
                    return true;
                }
            }
            return false; 
        }).orElse(false);
    }

    /**
     * Búsqueda simple de usuario por su identificador único ID.
     */
    public Usuario buscarPorId(String id) {
        return repositorioUsuarios.findById(id).orElse(null);
    }

    /**
     * Realiza el cobro de pasaje validando saldo disponible en la tarjeta especificada.
     */
    public boolean cobrarPasaje(String idUsuario, String numeroTarjeta, double monto) {
        Optional<Usuario> opt = repositorioUsuarios.findById(idUsuario);
        if (opt.isEmpty()) return false;

        Usuario u = opt.get();
        return u.getTarjetas().stream()
                .filter(t -> t.getNumeroTarjeta().equals(numeroTarjeta))
                .findFirst()
                .map(t -> {
                    BigDecimal montoBD = BigDecimal.valueOf(monto);
                    if (t.getSaldo().compareTo(montoBD) >= 0) {
                        // 1. Calculamos y actualizamos el nuevo saldo en la tarjeta
                        BigDecimal nuevoSaldo = t.getSaldo().subtract(montoBD);
                        t.setSaldo(nuevoSaldo);
                        
                        // 2. Persistimos los cambios en la BD
                        repositorioUsuarios.save(u);
                        
                        // 3. Registramos la transacción
                        servicioTransacciones.registrarTransaccion(u, "Gasto pasaje: " + ocultarNumeroTarjeta(numeroTarjeta), monto);
                        
                        // 4. Enviamos correo de confirmación de gasto
                        emailService.enviarNotificacionGasto(u.getCorreo(), u.getNombre(), monto, nuevoSaldo.toString());
                        
                        // 5. Evaluamos el límite con la tarjeta que ya tiene el nuevo saldo
                        verificarYNotificarLimiteSaldo(u, t);
                        return true;
                    }
                    return false;
                }).orElse(false);
    }

    /**
     * Descuenta un monto de la primera tarjeta del usuario.
     */
    public void gastar(Usuario usuario, double monto) {
        if (usuario != null && usuario.isActivo() && !usuario.getTarjetas().isEmpty()) {
            // Buscamos el usuario en BD para evitar problemas de sincronización de datos
            Usuario u = repositorioUsuarios.findById(usuario.getId()).orElse(usuario);
            Tarjeta t = u.getTarjetas().get(0);
            BigDecimal montoBD = BigDecimal.valueOf(monto);
            
            if (t.getSaldo().compareTo(montoBD) >= 0) {
                BigDecimal nuevoSaldo = t.getSaldo().subtract(montoBD);
                t.setSaldo(nuevoSaldo);
                
                repositorioUsuarios.save(u);
                
                servicioTransacciones.registrarTransaccion(u, "Gasto Directo", monto);
                emailService.enviarNotificacionGasto(u.getCorreo(), u.getNombre(), monto, nuevoSaldo.toString());
                
                verificarYNotificarLimiteSaldo(u, t);
            }
        }
    }

    /**
     * Cobro de pasaje administrativo en la primera tarjeta activa del usuario.
     */
    public boolean cobrarPasajePorAdmin(String idUsuario, double monto) {
        Optional<Usuario> opt = repositorioUsuarios.findById(idUsuario);
        if (opt.isEmpty()) return false;

        Usuario u = opt.get();
        if (!u.isActivo() || u.getTarjetas().isEmpty()) return false;

        Tarjeta t = u.getTarjetas().get(0);
        BigDecimal montoBD = BigDecimal.valueOf(monto);

        if (t.getSaldo().compareTo(montoBD) >= 0) {
            BigDecimal nuevoSaldo = t.getSaldo().subtract(montoBD);
            t.setSaldo(nuevoSaldo);
            
            repositorioUsuarios.save(u);
            
            servicioTransacciones.registrarTransaccion(u, "Cobro pasaje (Admin)", monto);
            emailService.enviarNotificacionGasto(u.getCorreo(), u.getNombre(), monto, nuevoSaldo.toString());
            
            verificarYNotificarLimiteSaldo(u, t);
            return true;
        }
        return false;
    }

    public boolean cobrarPasajePorAdmin(String idUsuario, double monto, SistemaTransporte sistemaTransporte) {
        if (sistemaTransporte == null || !sistemaTransporte.estaDisponible()) return false;
        Optional<Usuario> opt = repositorioUsuarios.findById(idUsuario);
        if (opt.isEmpty()) return false;

        Usuario usuario = opt.get();
        if (!usuario.isActivo()) return false;
        Optional<Tarjeta> tarjetaOpt = usuario.getTarjetas().stream()
                .filter(tarjeta -> tarjeta.getSistemaTransporte() == sistemaTransporte)
                .findFirst();
        if (tarjetaOpt.isEmpty()) return false;

        Tarjeta tarjeta = tarjetaOpt.get();
        BigDecimal montoBD = BigDecimal.valueOf(monto);
        if (montoBD.signum() <= 0 || tarjeta.getSaldo().compareTo(montoBD) < 0) return false;

        BigDecimal nuevoSaldo = tarjeta.getSaldo().subtract(montoBD);
        tarjeta.setSaldo(nuevoSaldo);
        repositorioUsuarios.save(usuario);
        servicioTransacciones.registrarTransaccion(
                usuario, "Cobro pasaje (Admin)", monto, null, null, null, null, sistemaTransporte);
        emailService.enviarNotificacionGasto(
                usuario.getCorreo(), usuario.getNombre(), monto, nuevoSaldo.toString());
        verificarYNotificarLimiteSaldo(usuario, tarjeta);
        return true;
    }
}

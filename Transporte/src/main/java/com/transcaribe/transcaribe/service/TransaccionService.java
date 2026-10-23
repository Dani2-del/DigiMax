package com.transcaribe.transcaribe.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.transcaribe.transcaribe.Model.Transaccion;
import com.transcaribe.transcaribe.Model.SistemaTransporte;
import com.transcaribe.transcaribe.Model.Usuario;
import com.transcaribe.transcaribe.Repository.TransaccionRepository;

@Service
public class TransaccionService {

    private final TransaccionRepository repositorioTransacciones;

    public TransaccionService(TransaccionRepository repositorioTransacciones) {
        this.repositorioTransacciones = repositorioTransacciones;
    }

    public Transaccion registrarTransaccion(Usuario usuario, String tipo, double monto) {
        return registrarTransaccion(usuario, tipo, monto, null, null, null);
    }

    public Transaccion registrarTransaccion(Usuario usuario, String tipo, double monto,
            String metodoPago, String cuentaPse, String tarjetaTranscaribe) {
        return registrarTransaccion(usuario, tipo, monto, metodoPago, cuentaPse, tarjetaTranscaribe, null);
    }

    public Transaccion registrarTransaccion(Usuario usuario, String tipo, double monto,
            String metodoPago, String cuentaPse, String tarjetaTranscaribe, Integer cantidadPasajes) {
        return registrarTransaccion(usuario, tipo, monto, metodoPago, cuentaPse,
                tarjetaTranscaribe, cantidadPasajes, SistemaTransporte.TRANSCARIBE);
    }

    public Transaccion registrarTransaccion(Usuario usuario, String tipo, double monto,
            String metodoPago, String cuentaPse, String tarjetaTranscaribe,
            Integer cantidadPasajes, SistemaTransporte sistemaTransporte) {
        Transaccion transaccion = new Transaccion(monto, LocalDateTime.now(), tipo, usuario);
        transaccion.setMetodoPago(metodoPago);
        transaccion.setCuentaPse(cuentaPse);
        transaccion.setTarjetaTranscaribe(tarjetaTranscaribe);
        transaccion.setCantidadPasajes(cantidadPasajes);
        transaccion.setSistemaTransporte(sistemaTransporte);
        return repositorioTransacciones.save(transaccion);
    }

    public List<Transaccion> obtenerTransaccionesPorUsuario(Usuario usuario) {
        return usuario == null || usuario.getId() == null
                ? List.of()
                : repositorioTransacciones.findByUsuarioId(usuario.getId());
    }

    public Optional<Transaccion> obtenerTransaccionDeUsuario(String id, Usuario usuario) {
        if (usuario == null || usuario.getId() == null) {
            return Optional.empty();
        }

        return repositorioTransacciones.findById(id)
                .filter(transaccion -> transaccion.getUsuario() != null
                        && usuario.getId().equals(transaccion.getUsuario().getId()));
    }
}

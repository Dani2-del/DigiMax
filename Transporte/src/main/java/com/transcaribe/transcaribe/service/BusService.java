package com.transcaribe.transcaribe.service;

import com.transcaribe.transcaribe.Model.Bus;
import com.transcaribe.transcaribe.Model.Tarjeta;
import com.transcaribe.transcaribe.Model.Transaccion;
import com.transcaribe.transcaribe.Model.Usuario;
import com.transcaribe.transcaribe.Model.SistemaTransporte;
import com.transcaribe.transcaribe.Repository.BusRepository;
import com.transcaribe.transcaribe.Repository.TransaccionRepository;
import com.transcaribe.transcaribe.Repository.UsuarioRepository;
import com.transcaribe.transcaribe.Util.TarifaTranscaribe;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.stream.Collectors;
import java.math.BigDecimal; 
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import com.transcaribe.transcaribe.Model.RutaTransmilenio;
import com.transcaribe.transcaribe.Model.RutaTransmetro;

@Service 
public class BusService {

    @Autowired
    private BusRepository busRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private TransaccionRepository transaccionRepository;

    @Autowired
    private WalletClient walletClient;

    public void crearBusDePrueba() {
        List<String> rutasCartagena = List.of("T100E", "T101", "T102", "T103", 
                                            "X101", "X102", "X103", "X104", "X105", "X106",
                                            "A101", "A102", "A103", "A104", "A105", "A107",
                                            "A108", "A111", "A114", "A117", "A118", "C001", 
                                            "C016", "C017", "C018");

        Bus nuevoBus = new Bus("B-210", rutasCartagena);
        busRepository.save(nuevoBus);
    }

    public String pagarPasajeConTarjetaEspecifica(String usuarioId, String busId, String numeroTarjeta) {
        return pagarPasajeConTarjetaEspecifica(
                usuarioId, busId, numeroTarjeta, SistemaTransporte.TRANSCARIBE);
    }

    public String pagarPasajeConTarjetaEspecifica(
            String usuarioId, String busId, String numeroTarjeta, SistemaTransporte sistemaTransporte) {
        return pagarPasajeConTarjetaEspecifica(
                usuarioId, busId, numeroTarjeta, sistemaTransporte, UUID.randomUUID().toString());
    }

    public String pagarPasajeConTarjetaEspecifica(
            String usuarioId,
            String busId,
            String numeroTarjeta,
            SistemaTransporte sistemaTransporte,
            String operacionId) {
    Usuario usuario = usuarioRepository.findById(usuarioId)
            .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        Bus bus = busRepository.findById(busId)
            .orElseThrow(() -> new RuntimeException("Bus no encontrado"));

    if (sistemaTransporte != SistemaTransporte.TRANSCARIBE) {
        return "Las operaciones de buses están disponibles por ahora solo para Transcaribe.";
    }
    if (bus.getSistemaTransporte() != sistemaTransporte) return "Este bus pertenece a otro servicio.";
    if (!bus.isActivo()) return "Este bus está inactivo.";
    if (bus.getRutas() == null || bus.getRutas().isEmpty()) {
        return "Este bus no tiene rutas asignadas.";
    }

    Random rand = new Random();
    String rutaSeleccionada = bus.getRutas().get(rand.nextInt(bus.getRutas().size()));

    BigDecimal costo = BigDecimal.valueOf(TarifaTranscaribe.paraSistema(sistemaTransporte));

    if (WalletClient.UNIVERSAL_CARD_OPTION.equals(numeroTarjeta)) {
        return cobrarConTarjetaUniversal(usuario, costo, sistemaTransporte, operacionId,
                "Pasaje Transcaribe: ruta " + rutaSeleccionada);
    }

    Tarjeta tarjetaSeleccionada = usuario.getTarjetas().stream()
            .filter(t -> numeroTarjeta.equals(t.getNumeroTarjeta()))
            .filter(t -> t.getSistemaTransporte() == sistemaTransporte)
            .findFirst()
            .orElse(null);

    if (tarjetaSeleccionada == null) {
        return "La tarjeta seleccionada no es válida.";
    }

    if (tarjetaSeleccionada.getSaldo() != null && tarjetaSeleccionada.getSaldo().compareTo(costo) >= 0) {
        
        BigDecimal nuevoSaldo = tarjetaSeleccionada.getSaldo().subtract(costo);
        tarjetaSeleccionada.setSaldo(nuevoSaldo);
        
        usuarioRepository.save(usuario);

        Transaccion t = new Transaccion(
            costo.doubleValue(), 
            LocalDateTime.now(), 
            "PASAJE [" + numeroTarjeta + "] - Ruta: " + rutaSeleccionada, 
            usuario
        );
        t.setSistemaTransporte(sistemaTransporte);
        transaccionRepository.save(t);

        return "Cobro exitoso. Ruta: " + rutaSeleccionada + 
               ". Tarjeta: " + numeroTarjeta + 
               ". Saldo restante: $" + nuevoSaldo;
    } else {
        return "Saldo insuficiente en la tarjeta " + numeroTarjeta;
    }
}

    public String simularViajeTransmilenio(String usuarioId, String numeroTarjeta,
                                           RutaTransmilenio ruta) {
        return simularViajeTransmilenio(usuarioId, numeroTarjeta, ruta, UUID.randomUUID().toString());
    }

    public String simularViajeTransmilenio(String usuarioId, String numeroTarjeta,
                                           RutaTransmilenio ruta, String operacionId) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        if (!usuario.isActivo()) return "La cuenta está inactiva.";

        BigDecimal costo = BigDecimal.valueOf(
                TarifaTranscaribe.paraSistema(SistemaTransporte.TRANSMILENIO));
        if (WalletClient.UNIVERSAL_CARD_OPTION.equals(numeroTarjeta)) {
            return cobrarConTarjetaUniversal(usuario, costo, SistemaTransporte.TRANSMILENIO, operacionId,
                    "Pasaje TransMilenio: ruta " + ruta.getCodigoLinea());
        }
        Tarjeta tarjeta = usuario.getTarjetas().stream()
                .filter(t -> numeroTarjeta.equals(t.getNumeroTarjeta()))
                .filter(t -> t.getSistemaTransporte() == SistemaTransporte.TRANSMILENIO)
                .findFirst()
                .orElse(null);
        if (tarjeta == null) return "La tarjeta seleccionada no es válida para TransMilenio.";
        if (tarjeta.getSaldo() == null || tarjeta.getSaldo().compareTo(costo) < 0) {
            return "Saldo insuficiente en la tarjeta " + numeroTarjeta;
        }

        BigDecimal nuevoSaldo = tarjeta.getSaldo().subtract(costo);
        tarjeta.setSaldo(nuevoSaldo);
        usuarioRepository.save(usuario);

        Transaccion transaccion = new Transaccion(costo.doubleValue(), LocalDateTime.now(),
                "PASAJE [" + numeroTarjeta + "] - TransMilenio " + ruta.getCodigoLinea()
                        + " (" + ruta.getOrigen() + " → " + ruta.getDestino() + ")", usuario);
        transaccion.setSistemaTransporte(SistemaTransporte.TRANSMILENIO);
        transaccion.setTarjetaTranscaribe(numeroTarjeta);
        transaccionRepository.save(transaccion);

        return "Cobro exitoso. Ruta " + ruta.getCodigoLinea() + ": " + ruta.getOrigen()
                + " → " + ruta.getDestino() + ". Saldo restante: $" + nuevoSaldo;
    }

    public String simularViajeTransmetro(String usuarioId, String numeroTarjeta,
                                         RutaTransmetro ruta) {
        return simularViajeTransmetro(usuarioId, numeroTarjeta, ruta, UUID.randomUUID().toString());
    }

    public String simularViajeTransmetro(String usuarioId, String numeroTarjeta,
                                         RutaTransmetro ruta, String operacionId) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        if (!usuario.isActivo()) return "La cuenta está inactiva.";

        BigDecimal costo = BigDecimal.valueOf(
                TarifaTranscaribe.paraSistema(SistemaTransporte.TRANSMETRO));
        if (WalletClient.UNIVERSAL_CARD_OPTION.equals(numeroTarjeta)) {
            return cobrarConTarjetaUniversal(usuario, costo, SistemaTransporte.TRANSMETRO, operacionId,
                    "Pasaje Transmetro: ruta " + ruta.codigo());
        }
        Tarjeta tarjeta = usuario.getTarjetas().stream()
                .filter(t -> numeroTarjeta.equals(t.getNumeroTarjeta()))
                .filter(t -> t.getSistemaTransporte() == SistemaTransporte.TRANSMETRO)
                .findFirst()
                .orElse(null);
        if (tarjeta == null) return "La tarjeta seleccionada no es válida para Transmetro.";
        if (tarjeta.getSaldo() == null || tarjeta.getSaldo().compareTo(costo) < 0) {
            return "Saldo insuficiente en la tarjeta " + numeroTarjeta;
        }

        BigDecimal nuevoSaldo = tarjeta.getSaldo().subtract(costo);
        tarjeta.setSaldo(nuevoSaldo);
        usuarioRepository.save(usuario);

        Transaccion transaccion = new Transaccion(costo.doubleValue(), LocalDateTime.now(),
                "PASAJE [" + numeroTarjeta + "] - Transmetro " + ruta.codigo()
                        + " (" + ruta.paradas().get(0) + " → "
                        + ruta.paradas().get(ruta.paradas().size() - 1) + ")", usuario);
        transaccion.setSistemaTransporte(SistemaTransporte.TRANSMETRO);
        transaccion.setTarjetaTranscaribe(numeroTarjeta);
        transaccionRepository.save(transaccion);

        return "Cobro exitoso. Ruta " + ruta.codigo() + ": " + ruta.paradas().get(0)
                + " → " + ruta.paradas().get(ruta.paradas().size() - 1)
                + ". Saldo restante: $" + nuevoSaldo;
    }

    private String cobrarConTarjetaUniversal(
            Usuario usuario, BigDecimal monto, SistemaTransporte sistema, String operacionId, String descripcion) {
        if (!usuario.isActivo()) {
            return "La cuenta está inactiva.";
        }
        WalletClient.Wallet wallet;
        try {
            wallet = walletClient.debit(usuario.getId(), monto, operacionId, descripcion);
        } catch (WalletClient.InsufficientWalletBalanceException exception) {
            return exception.getMessage();
        }
        Transaccion transaccion = new Transaccion(
                monto.doubleValue(), LocalDateTime.now(),
                "PASAJE [TARJETA UNIVERSAL] - " + descripcion, usuario);
        transaccion.setSistemaTransporte(sistema);
        transaccion.setTarjetaTranscaribe(WalletClient.UNIVERSAL_CARD_OPTION);
        transaccionRepository.save(transaccion);
        return "Cobro exitoso con tarjeta universal. Saldo restante: $" + wallet.balance();
    }

    public List<String> obtenerTodasLasRutas() {
        return busRepository.findAll().stream()
                .filter(bus -> bus.getSistemaTransporte() == SistemaTransporte.TRANSCARIBE)
                .filter(Bus::isActivo)
                .filter(b -> b.getRutas() != null)
                .flatMap(b -> b.getRutas().stream())
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }
}

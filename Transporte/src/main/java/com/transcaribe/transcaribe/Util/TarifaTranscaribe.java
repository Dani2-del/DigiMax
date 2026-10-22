package com.transcaribe.transcaribe.Util;

import com.transcaribe.transcaribe.Model.SistemaTransporte;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;

public final class TarifaTranscaribe {

    public static final long VALOR_PASAJE_TRANSCARIBE_COP = 3900L;
    public static final long VALOR_PASAJE_TRANSMILENIO_COP = 3550L;
    public static final long VALOR_PASAJE_TRANSMETRO_COP = 3700L;
    public static final long VALOR_PASAJE_TRANSMETRO_DOMINGOS_FESTIVOS_COP = 3800L;
    private static final ZoneId ZONA_COLOMBIA = ZoneId.of("America/Bogota");

    public static long paraSistema(SistemaTransporte sistema) {
        return paraSistema(sistema, LocalDate.now(ZONA_COLOMBIA));
    }

    public static long paraSistema(SistemaTransporte sistema, LocalDate fecha) {
        return switch (sistema) {
            case TRANSCARIBE -> VALOR_PASAJE_TRANSCARIBE_COP;
            case TRANSMILENIO -> VALOR_PASAJE_TRANSMILENIO_COP;
            case TRANSMETRO -> esDomingoOFestivoTransmetro(fecha)
                    ? VALOR_PASAJE_TRANSMETRO_DOMINGOS_FESTIVOS_COP
                    : VALOR_PASAJE_TRANSMETRO_COP;
        };
    }

    public static boolean esDomingoOFestivoTransmetro(LocalDate fecha) {
        return fecha.getDayOfWeek() == DayOfWeek.SUNDAY || FestivosColombia.esFestivo(fecha);
    }

    private TarifaTranscaribe() {
    }
}

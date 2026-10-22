package com.transcaribe.transcaribe.Util;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.MonthDay;
import java.util.HashSet;
import java.util.Set;

/** Calendario de festivos nacionales colombianos y traslados de la Ley 51 de 1983. */
final class FestivosColombia {
    private static final Set<MonthDay> FIJOS = Set.of(
            MonthDay.of(1, 1), MonthDay.of(5, 1), MonthDay.of(7, 20),
            MonthDay.of(8, 7), MonthDay.of(12, 8), MonthDay.of(12, 25));
    private static final Set<MonthDay> TRASLADABLES = Set.of(
            MonthDay.of(1, 6), MonthDay.of(3, 19), MonthDay.of(6, 29),
            MonthDay.of(8, 15), MonthDay.of(10, 12), MonthDay.of(11, 1),
            MonthDay.of(11, 11));

    private FestivosColombia() { }

    static boolean esFestivo(LocalDate fecha) {
        int anio = fecha.getYear();
        Set<LocalDate> festivos = new HashSet<>();
        for (MonthDay dia : FIJOS) festivos.add(dia.atYear(anio));
        for (MonthDay dia : TRASLADABLES) festivos.add(lunesSiguiente(dia.atYear(anio)));

        LocalDate pascua = calcularPascua(anio);
        festivos.add(pascua.minusDays(3)); // Jueves Santo
        festivos.add(pascua.minusDays(2)); // Viernes Santo
        festivos.add(lunesSiguiente(pascua.plusDays(39))); // Ascensión
        festivos.add(lunesSiguiente(pascua.plusDays(60))); // Corpus Christi
        festivos.add(lunesSiguiente(pascua.plusDays(68))); // Sagrado Corazón

        return festivos.contains(fecha);
    }

    private static LocalDate lunesSiguiente(LocalDate fecha) {
        return fecha.getDayOfWeek() == DayOfWeek.MONDAY
                ? fecha : fecha.with(DayOfWeek.MONDAY).plusWeeks(1);
    }

    // Algoritmo gregoriano de Meeus/Jones/Butcher.
    private static LocalDate calcularPascua(int anio) {
        int a = anio % 19, b = anio / 100, c = anio % 100;
        int d = b / 4, e = b % 4, f = (b + 8) / 25, g = (b - f + 1) / 3;
        int h = (19 * a + b - d - g + 15) % 30, i = c / 4, k = c % 4;
        int l = (32 + 2 * e + 2 * i - h - k) % 7, m = (a + 11 * h + 22 * l) / 451;
        int mes = (h + l - 7 * m + 114) / 31;
        int dia = (h + l - 7 * m + 114) % 31 + 1;
        return LocalDate.of(anio, mes, dia);
    }
}

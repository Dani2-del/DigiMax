"""
Construye la tabla usuario-semana para predecir si un pasajero recargara
en los 7 dias siguientes.
- Solo usuarios ROLE_USER.
- Una fila por usuario y por semana (cada lunes), desde que se registro.
- Variables: solo usan recargas ANTERIORES a la fecha de la foto (sin mirar el futuro).
- Etiqueta y: 1 si hubo una recarga exitosa de Billetera en (T, T+7 dias], 0 si no.
Entrada:  usuarios_atlas.csv, eventos_atlas.csv  (en la misma carpeta)
Salida:   tabla_usuario_semana.csv
Requisitos: python -m pip install pandas numpy
Uso:        python construir_tabla_usuario_semana.py
"""
import numpy as np
import pandas as pd

ARCHIVO_USUARIOS = "usuarios_atlas.csv"
ARCHIVO_EVENTOS = "eventos_atlas.csv"
SALIDA = "tabla_usuario_semana_v6.csv"
INICIO = "2024-01-01"
FIN_ETIQUETA = "2026-10-29"   # ultima fecha con datos: la etiqueta necesita 7 dias futuros
ESCALA = 10**10               # clave = indice_usuario * ESCALA + segundos (evita mezclar usuarios)


def main():
    import argparse
    ap = argparse.ArgumentParser()
    ap.add_argument("--usuarios", default=ARCHIVO_USUARIOS)
    ap.add_argument("--eventos", default=ARCHIVO_EVENTOS)
    ap.add_argument("--salida", default=SALIDA)
    ap.add_argument("--fin-etiqueta", default=FIN_ETIQUETA,
                    help="ultimo dia con ventana futura completa (AAAA-MM-DD)")
    args = ap.parse_args()
    usuarios = pd.read_csv(args.usuarios)
    usuarios = usuarios[usuarios["rol"] == "ROLE_USER"].copy()
    usuarios["registro"] = pd.to_datetime(usuarios["fecha_registro"], format="ISO8601", utc=True)
    usuarios = usuarios.reset_index(drop=True)
    usuarios["uidx"] = usuarios.index.astype(np.int64)
    idx_de = dict(zip(usuarios["usuario_id"], usuarios["uidx"]))
    sitm = usuarios["sitm"].fillna("").astype(str).str.upper().values
    reg_seg = ((usuarios["registro"] - pd.Timestamp("1970-01-01", tz="UTC")) // pd.Timedelta("1s")).astype(np.int64)

    e = pd.read_csv(args.eventos)
    e = e[e["usuario_id"].isin(idx_de)].copy()
    e["t"] = ((pd.to_datetime(e["fecha"], format="ISO8601", utc=True) - pd.Timestamp("1970-01-01", tz="UTC")) // pd.Timedelta("1s")).astype(np.int64)
    e["uidx"] = e["usuario_id"].map(idx_de).astype(np.int64)
    if "monto" not in e.columns:
        e["monto"] = 0
    e["monto"] = pd.to_numeric(e["monto"], errors="coerce").fillna(0).astype(np.float64)
    e = e.sort_values(["uidx", "t"])
    fuentes = e["fuente"].values
    # Las consumiciones se mantienen como variables separadas: el evento
    # equivalente puede aparecer también como viaje o recarga móvil.
    actividad = e[fuentes != "consumo_billetera"]
    claves = (actividad["uidx"].values * ESCALA + actividad["t"].values).astype(np.int64)
    fuente_movil = actividad["fuente"].values == "movil"
    claves_tarjeta = claves[actividad["fuente"].values == "billetera"]
    claves_transporte = claves[actividad["fuente"].values == "transporte"]
    consumos = e[fuentes == "consumo_billetera"]
    claves_consumo = (consumos["uidx"].values * ESCALA + consumos["t"].values).astype(np.int64)
    montos_consumo = np.maximum(consumos["monto"].values, 0)
    suma_consumos = np.concatenate(([0.0], np.cumsum(montos_consumo, dtype=np.float64)))

    inicio = pd.Timestamp(INICIO, tz="UTC")
    fin_etiqueta = pd.Timestamp(args.fin_etiqueta, tz="UTC")
    semanas = pd.date_range(inicio, fin_etiqueta, freq="W-MON")
    semanas = semanas[semanas + pd.Timedelta(days=7) <= fin_etiqueta + pd.Timedelta(days=1)]

    dia = 86400
    uidx = usuarios["uidx"].values
    primera = True
    filas_total = 0
    positivas = 0

    for T in semanas:
        Ts = int(T.timestamp())
        # Usuarios ya registrados en la foto
        mask = reg_seg.values <= Ts
        if not mask.any():
            continue
        u = uidx[mask]
        base = u * ESCALA

        def cuenta(a, b):
            """Recargas de cada usuario en (a, b]."""
            return (np.searchsorted(claves, base + b, side="right")
                    - np.searchsorted(claves, base + a, side="right"))

        rec_7 = cuenta(Ts - 7 * dia, Ts)
        rec_14_7 = cuenta(Ts - 14 * dia, Ts - 7 * dia)
        rec_30 = cuenta(Ts - 30 * dia, Ts)
        rec_90 = cuenta(Ts - 90 * dia, Ts)
        rec_180 = cuenta(Ts - 180 * dia, Ts)
        rec_365 = cuenta(Ts - 365 * dia, Ts)

        viaje_7 = (np.searchsorted(claves_transporte, base + Ts, side="right")
                   - np.searchsorted(claves_transporte, base + Ts - 7 * dia, side="right"))
        viaje_30 = (np.searchsorted(claves_transporte, base + Ts, side="right")
                    - np.searchsorted(claves_transporte, base + Ts - 30 * dia, side="right"))
        viaje_90 = (np.searchsorted(claves_transporte, base + Ts, side="right")
                    - np.searchsorted(claves_transporte, base + Ts - 90 * dia, side="right"))
        viaje_365 = (np.searchsorted(claves_transporte, base + Ts, side="right")
                     - np.searchsorted(claves_transporte, base + Ts - 365 * dia, side="right"))
        if len(claves_transporte):
            pos_viaje = np.searchsorted(claves_transporte, base + Ts, side="right") - 1
            viaje_ok = (pos_viaje >= 0) & (claves_transporte[np.clip(pos_viaje, 0, None)] // ESCALA == u)
            viaje_ultima = np.where(viaje_ok,
                                    (Ts - claves_transporte[np.clip(pos_viaje, 0, None)] % ESCALA) // dia,
                                    999)
        else:
            viaje_ultima = np.full(len(u), 999, dtype=np.int64)

        tarjeta_7 = (np.searchsorted(claves_tarjeta, base + Ts, side="right")
                     - np.searchsorted(claves_tarjeta, base + Ts - 7 * dia, side="right"))
        tarjeta_30 = (np.searchsorted(claves_tarjeta, base + Ts, side="right")
                      - np.searchsorted(claves_tarjeta, base + Ts - 30 * dia, side="right"))
        tarjeta_90 = (np.searchsorted(claves_tarjeta, base + Ts, side="right")
                      - np.searchsorted(claves_tarjeta, base + Ts - 90 * dia, side="right"))
        tarjeta_180 = (np.searchsorted(claves_tarjeta, base + Ts, side="right")
                       - np.searchsorted(claves_tarjeta, base + Ts - 180 * dia, side="right"))
        tarjeta_365 = (np.searchsorted(claves_tarjeta, base + Ts, side="right")
                       - np.searchsorted(claves_tarjeta, base + Ts - 365 * dia, side="right"))

        # Ventanas sin solapamiento: permiten distinguir recargas recientes de
        # actividad antigua sin que el modelo deduzca las diferencias entre acumulados.
        tarjeta_8_30 = (np.searchsorted(claves_tarjeta, base + Ts - 7 * dia, side="right")
                        - np.searchsorted(claves_tarjeta, base + Ts - 30 * dia, side="right"))
        tarjeta_31_90 = (np.searchsorted(claves_tarjeta, base + Ts - 30 * dia, side="right")
                         - np.searchsorted(claves_tarjeta, base + Ts - 90 * dia, side="right"))
        tarjeta_91_180 = (np.searchsorted(claves_tarjeta, base + Ts - 90 * dia, side="right")
                          - np.searchsorted(claves_tarjeta, base + Ts - 180 * dia, side="right"))
        tarjeta_historica = (np.searchsorted(claves_tarjeta, base + Ts, side="right")
                             - np.searchsorted(claves_tarjeta, base, side="right"))

        # Recencia específica de recargas de Billetera, separada de viajes y
        # recargas móviles. 999 indica que aún no hay una recarga observada.
        if len(claves_tarjeta):
            pos_recarga = np.searchsorted(claves_tarjeta, base + Ts, side="right") - 1
            recarga_ok = ((pos_recarga >= 0)
                          & (claves_tarjeta[np.clip(pos_recarga, 0, None)] // ESCALA == u))
            ultima_recarga_t = np.where(
                recarga_ok, claves_tarjeta[np.clip(pos_recarga, 0, None)] % ESCALA, 0
            )
            dias_ultima_recarga = np.where(
                recarga_ok, (Ts - ultima_recarga_t) // dia, 999
            )
            pos_recarga_previa = np.maximum(pos_recarga - 1, 0)
            recarga_previa_ok = (recarga_ok & (pos_recarga >= 1)
                                 & (claves_tarjeta[pos_recarga_previa] // ESCALA == u))
            recarga_previa_t = np.where(
                recarga_previa_ok,
                claves_tarjeta[pos_recarga_previa] % ESCALA,
                0,
            )
            dias_entre_recargas = np.where(
                recarga_previa_ok, (ultima_recarga_t - recarga_previa_t) // dia, 999
            )
        else:
            dias_ultima_recarga = np.full(len(u), 999, dtype=np.int64)
            dias_entre_recargas = np.full(len(u), 999, dtype=np.int64)

        viaje_historico = (np.searchsorted(claves_transporte, base + Ts, side="right")
                           - np.searchsorted(claves_transporte, base, side="right"))

        if len(claves_consumo):
            pos_consumo = np.searchsorted(claves_consumo, base + Ts, side="right") - 1
            consumo_ok = ((pos_consumo >= 0)
                          & (claves_consumo[np.clip(pos_consumo, 0, None)] // ESCALA == u))
            ultima_consumo_t = np.where(
                consumo_ok, claves_consumo[np.clip(pos_consumo, 0, None)] % ESCALA, 0
            )
            dias_ultimo_consumo = np.where(
                consumo_ok, (Ts - ultima_consumo_t) // dia, 999
            )
            limites_consumo = {}
            for ventana in (7, 30, 90, 365):
                limites_consumo[ventana] = (
                    np.searchsorted(claves_consumo, base + Ts - ventana * dia, side="right"),
                    np.searchsorted(claves_consumo, base + Ts, side="right"),
                )
            consumo_7, consumo_30, consumo_90, consumo_365 = (
                limites_consumo[d][1] - limites_consumo[d][0] for d in (7, 30, 90, 365)
            )
            gasto_30, gasto_90, gasto_365 = (
                suma_consumos[limites_consumo[d][1]] - suma_consumos[limites_consumo[d][0]]
                for d in (30, 90, 365)
            )
        else:
            consumo_7 = consumo_30 = consumo_90 = consumo_365 = np.zeros(len(u), dtype=np.int64)
            gasto_30 = gasto_90 = gasto_365 = np.zeros(len(u), dtype=np.float64)
            dias_ultimo_consumo = np.full(len(u), 999, dtype=np.int64)

        # Recargas moviles en los ultimos 30 dias (mismo conteo, solo fuente movil)
        ini = np.searchsorted(claves, base + Ts - 30 * dia, side="right")
        fin = np.searchsorted(claves, base + Ts, side="right")
        movil_30 = np.array([fuente_movil[a:b].sum() for a, b in zip(ini, fin)], dtype=np.int32)
        ini_90 = np.searchsorted(claves, base + Ts - 90 * dia, side="right")
        movil_90 = np.array([fuente_movil[a:b].sum() for a, b in zip(ini_90, fin)], dtype=np.int32)

        # Dias desde la ultima recarga (999 si nunca hubo)
        pos_fin = np.searchsorted(claves, base + Ts, side="right") - 1
        ultima_ok = (pos_fin >= 0) & (claves[np.clip(pos_fin, 0, None)] // ESCALA == u)
        ultima_t = np.where(ultima_ok, claves[np.clip(pos_fin, 0, None)] % ESCALA, 0)
        dias_ultima = np.where(ultima_ok, (Ts - ultima_t) // dia, 999)

        antiguedad = (Ts - reg_seg.values[mask]) // dia

        # Las recargas moviles son señales auxiliares; no cuentan como recarga de tarjeta.
        y = ((np.searchsorted(claves_tarjeta, base + Ts + 7 * dia, side="right")
              - np.searchsorted(claves_tarjeta, base + Ts, side="right")) > 0).astype(np.int8)

        tabla = pd.DataFrame({
            "usuario_id": usuarios["usuario_id"].values[mask],
            "semana": T.strftime("%Y-%m-%d"),
            "rec_7d": rec_7,
            "rec_7d_anterior": rec_14_7,
            "rec_30d": rec_30,
            "rec_90d": rec_90,
            "rec_180d": rec_180,
            "rec_365d": rec_365,
            "tarjeta_7d": tarjeta_7,
            "tarjeta_30d": tarjeta_30,
            "tarjeta_90d": tarjeta_90,
            "tarjeta_180d": tarjeta_180,
            "tarjeta_365d": tarjeta_365,
            "tarjeta_8_30d": tarjeta_8_30,
            "tarjeta_31_90d": tarjeta_31_90,
            "tarjeta_91_180d": tarjeta_91_180,
            "tarjeta_historica": tarjeta_historica,
            "dias_desde_ultima_recarga": dias_ultima_recarga,
            "dias_entre_ultimas_recargas": dias_entre_recargas,
            "viaje_historico": viaje_historico,
            "consumo_7d": consumo_7,
            "consumo_30d": consumo_30,
            "consumo_90d": consumo_90,
            "consumo_365d": consumo_365,
            "gasto_billetera_30d": gasto_30,
            "gasto_billetera_90d": gasto_90,
            "gasto_billetera_365d": gasto_365,
            "dias_desde_ultimo_consumo": dias_ultimo_consumo,
            "servicio_transcaribe": (sitm[mask] == "TRANSCARIBE").astype(np.int8),
            "servicio_transmilenio": (sitm[mask] == "TRANSMILENIO").astype(np.int8),
            "servicio_transmetro": (sitm[mask] == "TRANSMETRO").astype(np.int8),
            "movil_30d": movil_30,
            "movil_90d": movil_90,
            "viaje_7d": viaje_7,
            "viaje_30d": viaje_30,
            "viaje_90d": viaje_90,
            "viaje_365d": viaje_365,
            "dias_desde_ultimo_viaje": viaje_ultima,
            "dias_desde_ultima": dias_ultima,
            "antiguedad_dias": antiguedad,
            "tendencia_7d": rec_7 - rec_14_7,
            "y": y,
        })
        tabla.to_csv(args.salida, mode="w" if primera else "a", header=primera, index=False)
        primera = False
        filas_total += len(tabla)
        positivas += int(y.sum())
        if (len(tabla) and T.weekday() == 0 and (T.isocalendar().week % 13 == 0)):
            print(f"  Semanas procesadas: {T.strftime('%Y-%m-%d')} | filas acumuladas: {filas_total}", flush=True)

    print(f"Filas: {filas_total}")
    print(f"Semanas: {len(semanas)} | Proporcion y=1: {positivas / max(filas_total, 1):.3f}")
    print(f"Archivo: {args.salida}")


if __name__ == "__main__":
    main()

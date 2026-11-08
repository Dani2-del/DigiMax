"""
Construye la tabla usuario-semana para predecir si un pasajero recargara
en los 7 dias siguientes.
- Solo usuarios ROLE_USER.
- Una fila por usuario y por semana (cada lunes), desde que se registro.
- Variables: solo usan recargas ANTERIORES a la fecha de la foto (sin mirar el futuro).
- Etiqueta y: 1 si hubo una recarga exitosa de Billetera en (T, T+7 dias], 0 si no.
Entrada:  usuarios_atlas.csv, eventos_atlas.csv  (en la misma carpeta)
Salida:   tabla_usuario_semana_v7.csv (flujo actual de 7 dias)
Requisitos: python -m pip install pandas numpy
Uso:        python construir_tabla_usuario_semana.py
"""
import numpy as np
import pandas as pd

ARCHIVO_USUARIOS = "usuarios_atlas.csv"
ARCHIVO_EVENTOS = "eventos_atlas.csv"
SALIDA = "tabla_usuario_semana_v7.csv"
SALIDA_HORIZONTES = "tabla_usuario_semana_horizontes_v1.csv"
INICIO = "2024-01-01"
ESCALA = 10**10               # clave = indice_usuario * ESCALA + segundos (evita mezclar usuarios)


def main():
    import argparse
    ap = argparse.ArgumentParser()
    ap.add_argument("--usuarios", default=ARCHIVO_USUARIOS)
    ap.add_argument("--eventos", default=ARCHIVO_EVENTOS)
    ap.add_argument("--salida", default=SALIDA)
    ap.add_argument("--fin-etiqueta", default=None,
                    help="ultimo dia con datos (por defecto se detecta en eventos_atlas.csv)")
    ap.add_argument(
        "--horizontes", default="7",
        help="dias futuros a etiquetar, separados por coma; ejemplo: 7,14,30",
    )
    args = ap.parse_args()
    try:
        horizontes = sorted({int(x.strip()) for x in args.horizontes.split(",") if x.strip()})
    except ValueError as exc:
        raise SystemExit("--horizontes debe ser una lista de enteros, por ejemplo 7,14,30") from exc
    if not horizontes or any(h <= 0 for h in horizontes) or 7 not in horizontes:
        raise SystemExit("Incluye horizontes positivos y conserva 7 dias para la columna compatible y.")
    salida = args.salida
    if salida == SALIDA and horizontes != [7]:
        salida = SALIDA_HORIZONTES
    usuarios = pd.read_csv(args.usuarios)
    usuarios = usuarios[usuarios["rol"] == "ROLE_USER"].copy()
    usuarios["registro"] = pd.to_datetime(usuarios["fecha_registro"], format="ISO8601", utc=True)
    usuarios = usuarios.reset_index(drop=True)
    usuarios["uidx"] = usuarios.index.astype(np.int64)
    idx_de = dict(zip(usuarios["usuario_id"], usuarios["uidx"]))
    sitm = usuarios["sitm"].fillna("").astype(str).str.upper().values
    reg_seg = ((usuarios["registro"] - pd.Timestamp("1970-01-01", tz="UTC")) // pd.Timedelta("1s")).astype(np.int64)

    e = pd.read_csv(args.eventos)
    e["fecha_evento"] = pd.to_datetime(e["fecha"], format="ISO8601", utc=True, errors="coerce")
    if args.fin_etiqueta:
        fin_etiqueta = pd.Timestamp(args.fin_etiqueta, tz="UTC")
    else:
        ultima_fecha = e["fecha_evento"].max()
        if pd.isna(ultima_fecha):
            raise SystemExit("No hay fechas validas en eventos_atlas.csv para cerrar las etiquetas.")
        fin_etiqueta = ultima_fecha.normalize()
    e = e[e["usuario_id"].isin(idx_de)].copy()
    e["t"] = ((e["fecha_evento"] - pd.Timestamp("1970-01-01", tz="UTC")) // pd.Timedelta("1s")).astype(np.int64)
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
    semanas = pd.date_range(inicio, fin_etiqueta, freq="W-MON")
    horizonte_maximo = max(horizontes)
    # El flujo tradicional conserva solo etiquetas completas. En el modo
    # multihorizonte también deja las semanas recientes para predicción y marca
    # como NaN las etiquetas cuyo futuro todavía no se observó.
    if horizontes == [7]:
        semanas = semanas[
            semanas + pd.Timedelta(days=horizonte_maximo) <= fin_etiqueta + pd.Timedelta(days=1)
        ]

    dia = 86400
    # Para la cadencia se cuenta como máximo una recarga por día y usuario.
    # Se conserva la hora de la primera recarga del día para no incluir eventos
    # ocurridos después de la fecha de observación.
    if len(claves_tarjeta):
        usuarios_tarjeta = claves_tarjeta // ESCALA
        tiempos_tarjeta = claves_tarjeta % ESCALA
        conservar_dia = np.ones(len(claves_tarjeta), dtype=bool)
        conservar_dia[1:] = (
            (usuarios_tarjeta[1:] != usuarios_tarjeta[:-1])
            | ((tiempos_tarjeta[1:] // dia) != (tiempos_tarjeta[:-1] // dia))
        )
        claves_cadencia = claves_tarjeta[conservar_dia]
    else:
        claves_cadencia = np.empty(0, dtype=np.int64)

    # Estadísticas progresivas: cada posición usa solo los intervalos que ya
    # se observaron al ocurrir esa recarga; nunca consulta eventos posteriores.
    mediana_cadencia_evento = np.zeros(len(claves_cadencia), dtype=np.float32)
    variabilidad_cadencia_evento = np.zeros(len(claves_cadencia), dtype=np.float32)
    intervalos_cadencia_evento = np.zeros(len(claves_cadencia), dtype=np.int16)
    if len(claves_cadencia):
        usuarios_cadencia = claves_cadencia // ESCALA
        dias_cadencia = (claves_cadencia % ESCALA) // dia
        inicios = np.r_[0, np.flatnonzero(np.diff(usuarios_cadencia)) + 1]
        finales = np.r_[inicios[1:], len(claves_cadencia)]
        for inicio_usuario, fin_usuario in zip(inicios, finales):
            dias_usuario = dias_cadencia[inicio_usuario:fin_usuario]
            diferencias = np.diff(dias_usuario).astype(np.float64)
            for indice in range(1, len(dias_usuario)):
                intervalos = diferencias[:indice]
                media = float(intervalos.mean())
                mediana_cadencia_evento[inicio_usuario + indice] = np.median(intervalos)
                variabilidad_cadencia_evento[inicio_usuario + indice] = (
                    float(intervalos.std() / media) if media > 0 else 0.0
                )
                intervalos_cadencia_evento[inicio_usuario + indice] = indice

    uidx = usuarios["uidx"].values
    primera = True
    filas_total = 0
    positivas = {h: 0 for h in horizontes}
    observables = {h: 0 for h in horizontes}

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

        if len(claves_cadencia):
            pos_cadencia = np.searchsorted(claves_cadencia, base + Ts, side="right") - 1
            cadencia_ok = ((pos_cadencia >= 0)
                           & (claves_cadencia[np.clip(pos_cadencia, 0, None)] // ESCALA == u))
            pos_cadencia_segura = np.clip(pos_cadencia, 0, None)
            intervalos_observados = np.where(
                cadencia_ok, intervalos_cadencia_evento[pos_cadencia_segura], 0
            )
            cadencia_mediana = np.where(
                cadencia_ok, mediana_cadencia_evento[pos_cadencia_segura], 0.0
            )
            variabilidad_cadencia = np.where(
                cadencia_ok, variabilidad_cadencia_evento[pos_cadencia_segura], 0.0
            )
            progreso_ciclo = np.divide(
                dias_ultima_recarga,
                cadencia_mediana,
                out=np.zeros(len(u), dtype=np.float64),
                where=(cadencia_mediana > 0) & (dias_ultima_recarga < 999),
            )
        else:
            intervalos_observados = np.zeros(len(u), dtype=np.int16)
            cadencia_mediana = np.zeros(len(u), dtype=np.float32)
            variabilidad_cadencia = np.zeros(len(u), dtype=np.float32)
            progreso_ciclo = np.zeros(len(u), dtype=np.float32)

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
        etiquetas = {}
        for h in horizontes:
            completa = T + pd.Timedelta(days=h) <= fin_etiqueta + pd.Timedelta(days=1)
            if completa:
                etiquetas[h] = (
                    (np.searchsorted(claves_tarjeta, base + Ts + h * dia, side="right")
                     - np.searchsorted(claves_tarjeta, base + Ts, side="right")) > 0
                ).astype(np.int8)
                positivas[h] += int(etiquetas[h].sum())
                observables[h] += len(etiquetas[h])
            else:
                etiquetas[h] = np.full(len(u), np.nan, dtype=np.float32)

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
            "cadencia_mediana_recarga_dias": cadencia_mediana,
            "variabilidad_cadencia_recarga": variabilidad_cadencia,
            "progreso_ciclo_recarga": progreso_ciclo,
            "intervalos_recarga_observados": intervalos_observados,
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
            **{f"y_{h}d": etiquetas[h] for h in horizontes},
            # La columna y conserva el significado historico de recarga en 7 dias.
            "y": etiquetas[7],
        })
        tabla.to_csv(salida, mode="w" if primera else "a", header=primera, index=False)
        primera = False
        filas_total += len(tabla)
        if (len(tabla) and T.weekday() == 0 and (T.isocalendar().week % 13 == 0)):
            print(f"  Semanas procesadas: {T.strftime('%Y-%m-%d')} | filas acumuladas: {filas_total}", flush=True)

    print(f"Filas: {filas_total}")
    print(f"Semanas: {len(semanas)} | rango etiquetado hasta {horizonte_maximo} dias")
    for h in horizontes:
        print(
            f"  Proporcion y_{h}d=1: {positivas[h] / max(observables[h], 1):.3f} "
            f"({observables[h]} etiquetas completas; semanas recientes quedan sin etiqueta)"
        )
    print(f"Archivo: {salida}")


if __name__ == "__main__":
    main()

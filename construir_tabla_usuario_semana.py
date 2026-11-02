"""
Construye la tabla usuario-semana para predecir si un pasajero recargara
en los 7 dias siguientes.
- Solo usuarios ROLE_USER.
- Una fila por usuario y por semana (cada lunes), desde que se registro.
- Variables: solo usan recargas ANTERIORES a la fecha de la foto (sin mirar el futuro).
- Etiqueta y: 1 si hubo recarga en (T, T+7 dias], 0 si no.
Entrada:  usuarios_atlas.csv, eventos_atlas.csv  (en la misma carpeta)
Salida:   tabla_usuario_semana.csv
Requisitos: python -m pip install pandas numpy
Uso:        python construir_tabla_usuario_semana.py
"""
import numpy as np
import pandas as pd

ARCHIVO_USUARIOS = "usuarios_atlas.csv"
ARCHIVO_EVENTOS = "eventos_atlas.csv"
SALIDA = "tabla_usuario_semana.csv"
INICIO = "2024-01-01"
FIN_ETIQUETA = "2026-10-29"   # ultima fecha con datos: la etiqueta necesita 7 dias futuros
ESCALA = 10**10               # clave = indice_usuario * ESCALA + segundos (evita mezclar usuarios)


def main():
    import argparse
    ap = argparse.ArgumentParser()
    ap.add_argument("--usuarios", default=ARCHIVO_USUARIOS)
    ap.add_argument("--eventos", default=ARCHIVO_EVENTOS)
    ap.add_argument("--salida", default=SALIDA)
    args = ap.parse_args()
    usuarios = pd.read_csv(args.usuarios)
    usuarios = usuarios[usuarios["rol"] == "ROLE_USER"].copy()
    usuarios["registro"] = pd.to_datetime(usuarios["fecha_registro"], format="ISO8601", utc=True)
    usuarios = usuarios.reset_index(drop=True)
    usuarios["uidx"] = usuarios.index.astype(np.int64)
    idx_de = dict(zip(usuarios["usuario_id"], usuarios["uidx"]))
    reg_seg = ((usuarios["registro"] - pd.Timestamp("1970-01-01", tz="UTC")) // pd.Timedelta("1s")).astype(np.int64)

    e = pd.read_csv(args.eventos)
    e = e[e["usuario_id"].isin(idx_de)].copy()
    e["t"] = ((pd.to_datetime(e["fecha"], format="ISO8601", utc=True) - pd.Timestamp("1970-01-01", tz="UTC")) // pd.Timedelta("1s")).astype(np.int64)
    e["uidx"] = e["usuario_id"].map(idx_de).astype(np.int64)
    e = e.sort_values(["uidx", "t"])
    claves = (e["uidx"].values * ESCALA + e["t"].values).astype(np.int64)
    fuente_movil = (e["fuente"].values == "movil")

    semanas = pd.date_range(INICIO, FIN_ETIQUETA, freq="W-MON", tz="UTC")
    semanas = semanas[semanas + pd.Timedelta(days=7) <= pd.Timestamp(FIN_ETIQUETA, tz="UTC") + pd.Timedelta(days=1)]

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

        # Recargas moviles en los ultimos 30 dias (mismo conteo, solo fuente movil)
        ini = np.searchsorted(claves, base + Ts - 30 * dia, side="right")
        fin = np.searchsorted(claves, base + Ts, side="right")
        movil_30 = np.array([fuente_movil[a:b].sum() for a, b in zip(ini, fin)], dtype=np.int32)

        # Dias desde la ultima recarga (999 si nunca hubo)
        pos_fin = np.searchsorted(claves, base + Ts, side="right") - 1
        ultima_ok = (pos_fin >= 0) & (claves[np.clip(pos_fin, 0, None)] // ESCALA == u)
        ultima_t = np.where(ultima_ok, claves[np.clip(pos_fin, 0, None)] % ESCALA, 0)
        dias_ultima = np.where(ultima_ok, (Ts - ultima_t) // dia, 999)

        antiguedad = (Ts - reg_seg.values[mask]) // dia

        # Etiqueta: hubo recarga en (T, T+7 dias]
        y = (cuenta(Ts, Ts + 7 * dia) > 0).astype(np.int8)

        tabla = pd.DataFrame({
            "usuario_id": usuarios["usuario_id"].values[mask],
            "semana": T.strftime("%Y-%m-%d"),
            "rec_7d": rec_7,
            "rec_7d_anterior": rec_14_7,
            "rec_30d": rec_30,
            "rec_90d": rec_90,
            "movil_30d": movil_30,
            "dias_desde_ultima": dias_ultima,
            "antiguedad_dias": antiguedad,
            "tendencia_7d": rec_7 - rec_14_7,
            "y": y,
        })
        tabla.to_csv(args.salida, mode="w" if primera else "a", header=primera, index=False)
        primera = False
        filas_total += len(tabla)
        positivas += int(y.sum())

    print(f"Filas: {filas_total}")
    print(f"Semanas: {len(semanas)} | Proporcion y=1: {positivas / max(filas_total, 1):.3f}")
    print(f"Archivo: {args.salida}")


if __name__ == "__main__":
    main()

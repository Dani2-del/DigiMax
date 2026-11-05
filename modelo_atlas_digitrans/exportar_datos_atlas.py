"""
Exporta a CSV los datos del modelo desde MongoDB ATLAS (la base que usa la aplicacion).
Genera:
  usuarios_atlas.csv  -> una fila por usuario (ID, rol, servicio, sitm, fecha de registro)
  eventos_atlas.csv   -> una fila por recarga (ID de usuario, fecha, fuente)
Fuentes de eventos:
  - billetera: cargas de saldo (movimientos tipo ABONO)
  - movil:     recargas moviles con estado SIMULADA (solo señal auxiliar)
Los datos se leen en flujo (cursor), sin cargar todo en memoria.

Requisitos: python -m pip install pymongo
Variable:   MONGO_URI (cadena de Atlas con la contrasena nueva)
Uso:        python exportar_datos_atlas.py
"""
import argparse
import csv
import os
from urllib.parse import urlsplit
from collections import Counter

from pymongo import MongoClient

URI = os.environ.get("DIGITRANS_ATLAS_URI")
PREFIJO = "digitrans"
SUFIJO = "atlas"


def fecha_texto(valor):
    return valor.isoformat() if valor else ""


def validar_uri_atlas(uri):
    """Rechaza URI locales para que la exportacion no use datos equivocados."""
    uri = uri.strip().strip('"').strip("'")
    partes = None
    try:
        partes = urlsplit(uri)
        host = partes.hostname or ""
    except ValueError:
        host = ""
    hosts_atlas = all(
        segmento.rsplit("@", 1)[-1].split(":", 1)[0].endswith(".mongodb.net")
        for segmento in (partes.netloc.split(",") if partes else [])
    )
    if partes is None or partes.scheme not in {"mongodb", "mongodb+srv"} or not host.endswith(".mongodb.net") or not hosts_atlas:
        raise SystemExit(
            "DIGITRANS_ATLAS_URI debe ser una URI de MongoDB Atlas "
            "con host terminado en .mongodb.net; no se aceptan conexiones locales."
        )


def main():
    global PREFIJO, SUFIJO
    ap = argparse.ArgumentParser()
    ap.add_argument("--prefijo", default="digitrans", help="prefijo de las bases a leer")
    ap.add_argument("--salida", default="atlas", help="sufijo de los CSV de salida")
    args = ap.parse_args()
    PREFIJO, SUFIJO = args.prefijo, args.salida
    if not URI:
        raise SystemExit("Falta DIGITRANS_ATLAS_URI con la cadena de conexion de Atlas.")
    validar_uri_atlas(URI)
    cli = MongoClient(URI)
    acceso = cli[PREFIJO + "_acceso"]
    billetera = cli[PREFIJO + "_billetera"]
    recargas = cli[PREFIJO + "_recargas"]
    perfiles = cli[PREFIJO + "_transporte"]["perfiles_transporte"]

    # PerfilTransporte usa usuarioId como vínculo; _id identifica el perfil.
    sitm = {
        str(d["usuarioId"]): d.get("sitm", "")
        for d in perfiles.find(
            {"usuarioId": {"$exists": True}}, {"usuarioId": 1, "sitm": 1}
        ).batch_size(5000)
        if d.get("usuarioId") is not None
    }

    with open(f"usuarios_{SUFIJO}.csv", "w", newline="", encoding="utf-8") as f:
        w = csv.writer(f)
        w.writerow(["usuario_id", "rol", "servicio_asignado", "sitm", "fecha_registro"])
        n = 0
        cursor = acceso["usuarios"].find({}, {"role": 1, "servicio_asignado": 1, "fecha_registro": 1}).batch_size(5000)
        for d in cursor:
            uid = str(d["_id"])
            w.writerow([uid, d.get("role", ""), d.get("servicio_asignado", ""), sitm.get(uid, ""),
                        fecha_texto(d.get("fecha_registro"))])
            n += 1
    print(f"usuarios_{SUFIJO}.csv: {n} usuarios")

    conteo = Counter()
    with open(f"eventos_{SUFIJO}.csv", "w", newline="", encoding="utf-8") as f:
        w = csv.writer(f)
        w.writerow(["usuario_id", "fecha", "fuente"])
        print("Exportando abonos de Billetera desde Atlas...", flush=True)
        # Filtrar en Atlas evita descargar todos los movimientos de cada billetera.
        pipeline = [
            {"$unwind": "$movimientos"},
            {"$match": {"movimientos.tipo": "ABONO", "movimientos.fecha": {"$ne": None}}},
            {"$project": {"_id": 1, "fecha": "$movimientos.fecha"}},
        ]
        cursor = billetera["billeteras_universales"].aggregate(
            pipeline, allowDiskUse=True, batchSize=5000
        )
        for d in cursor:
            w.writerow([str(d["_id"]), fecha_texto(d.get("fecha")), "billetera"])
            conteo["billetera"] += 1
            if conteo["billetera"] % 10000 == 0:
                print(f"  Abonos exportados: {conteo['billetera']}", flush=True)
        print(f"Abonos de Billetera listos: {conteo['billetera']}", flush=True)
        # Recargas moviles simuladas
        print("Exportando recargas móviles simuladas desde Atlas...", flush=True)
        cursor = recargas["recargas_movil"].find(
            {"estado": "SIMULADA", "fecha": {"$ne": None}, "usuarioId": {"$ne": None}},
            {"usuarioId": 1, "fecha": 1},
        ).batch_size(5000)
        for r in cursor:
            w.writerow([r["usuarioId"], fecha_texto(r["fecha"]), "movil"])
            conteo["movil"] += 1
            if conteo["movil"] % 10000 == 0:
                print(f"  Recargas móviles exportadas: {conteo['movil']}", flush=True)

    print(f"eventos_{SUFIJO}.csv: {sum(conteo.values())} recargas "
          f"(billetera: {conteo['billetera']}, movil: {conteo['movil']})")


if __name__ == "__main__":
    main()

"""
Exporta a CSV los datos del modelo desde MongoDB ATLAS (la base que usa la aplicacion).
Genera:
  usuarios_atlas.csv  -> una fila por usuario (ID, rol, servicio, sitm, fecha de registro)
  eventos_atlas.csv   -> una fila por recarga (ID de usuario, fecha, fuente)
Fuentes de recarga:
  - billetera: cargas de saldo (movimientos tipo ABONO)
  - movil:     recargas moviles con estado SIMULADA
Los datos se leen en flujo (cursor), sin cargar todo en memoria.

Requisitos: python -m pip install pymongo
Variable:   MONGO_URI (cadena de Atlas con la contrasena nueva)
Uso:        python exportar_datos_atlas.py
"""
import argparse
import csv
import os
from collections import Counter

from pymongo import MongoClient

URI = os.environ.get("MONGO_URI")
PREFIJO = "digitrans"
SUFIJO = "atlas"


def fecha_texto(valor):
    return valor.isoformat() if valor else ""


def main():
    global PREFIJO, SUFIJO
    ap = argparse.ArgumentParser()
    ap.add_argument("--prefijo", default="digitrans", help="prefijo de las bases a leer")
    ap.add_argument("--salida", default="atlas", help="sufijo de los CSV de salida")
    args = ap.parse_args()
    PREFIJO, SUFIJO = args.prefijo, args.salida
    if not URI:
        raise SystemExit("Falta la variable MONGO_URI con la cadena de Atlas.")
    cli = MongoClient(URI)
    acceso = cli[PREFIJO + "_acceso"]
    billetera = cli[PREFIJO + "_billetera"]
    recargas = cli[PREFIJO + "_recargas"]
    perfiles = cli[PREFIJO + "_transporte"]["perfiles_transporte"]

    # Servicio de transporte por usuario (un valor por perfil, en memoria pequena)
    sitm = {str(d["_id"]): d.get("sitm", "") for d in perfiles.find({}, {"sitm": 1}).batch_size(5000)}

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
        # Cargas de billetera: ABONO dentro de cada billetera
        cursor = billetera["billeteras_universales"].find({}, {"movimientos": 1}).batch_size(1000)
        for b in cursor:
            uid = str(b["_id"])
            for m in b.get("movimientos", []):
                if m.get("tipo") == "ABONO" and m.get("fecha"):
                    w.writerow([uid, fecha_texto(m["fecha"]), "billetera"])
                    conteo["billetera"] += 1
        # Recargas moviles simuladas
        cursor = recargas["recargas_movil"].find({"estado": "SIMULADA"}, {"usuarioId": 1, "fecha": 1}).batch_size(5000)
        for r in cursor:
            if r.get("fecha") and r.get("usuarioId"):
                w.writerow([r["usuarioId"], fecha_texto(r["fecha"]), "movil"])
                conteo["movil"] += 1

    print(f"eventos_{SUFIJO}.csv: {sum(conteo.values())} recargas "
          f"(billetera: {conteo['billetera']}, movil: {conteo['movil']})")


if __name__ == "__main__":
    main()

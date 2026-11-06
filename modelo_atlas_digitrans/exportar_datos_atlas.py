"""
Exporta a CSV los datos del modelo desde MongoDB ATLAS (la base que usa la aplicacion).
Genera:
  usuarios_atlas.csv  -> una fila por usuario (ID, rol, servicio, sitm, fecha de registro)
  eventos_atlas.csv   -> una fila por evento (ID de usuario, fecha, fuente)
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
import time
from datetime import datetime
from urllib.parse import urlsplit
from collections import Counter

from pymongo import MongoClient
from pymongo.errors import AutoReconnect, ConnectionFailure, CursorNotFound, NetworkTimeout

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


def cargar_usuarios_por_operacion(billetera):
    """Indexa cobros de Billetera por ID de operación para enlazar viajes legados."""
    usuarios_por_operacion = {}
    operaciones_ambiguas = set()
    pipeline = [
        {"$unwind": "$movimientos"},
        {"$match": {
            "movimientos.tipo": "CONSUMO",
            "movimientos.operacionId": {"$exists": True, "$nin": [None, ""]},
        }},
        {"$project": {"_id": 1, "operacion_id": "$movimientos.operacionId"}},
    ]
    for movimiento in billetera["billeteras_universales"].aggregate(
        pipeline, allowDiskUse=True, batchSize=5000
    ):
        operacion_id = movimiento.get("operacion_id")
        usuario_id = movimiento.get("_id")
        if operacion_id is None or usuario_id is None:
            continue
        operacion_id = str(operacion_id)
        usuario_id = str(usuario_id)
        usuario_anterior = usuarios_por_operacion.get(operacion_id)
        if usuario_anterior is not None and usuario_anterior != usuario_id:
            operaciones_ambiguas.add(operacion_id)
            usuarios_por_operacion.pop(operacion_id, None)
        elif operacion_id not in operaciones_ambiguas:
            usuarios_por_operacion[operacion_id] = usuario_id
    return usuarios_por_operacion


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
    opciones_mongo = {
        "connectTimeoutMS": 30000,
        "serverSelectionTimeoutMS": 30000,
        "socketTimeoutMS": 120000,
        "retryReads": True,
    }
    cli = MongoClient(URI, **opciones_mongo)
    acceso = cli[PREFIJO + "_acceso"]
    billetera = cli[PREFIJO + "_billetera"]
    recargas = cli[PREFIJO + "_recargas"]
    transporte = cli[PREFIJO + "_transporte"]
    viajes_transporte = transporte["transacciones"]

    # Validate the source before writing large CSVs. A successful Atlas login
    # does not mean Transporte wrote its records to this Atlas database.
    consulta_viajes = {
        "fecha": {"$gte": datetime(2023, 1, 1), "$lte": datetime.now()},
        "tipo": {"$regex": "pasaje", "$options": "i"},
    }
    viajes_en_atlas = viajes_transporte.count_documents(consulta_viajes)
    usuarios_por_operacion = cargar_usuarios_por_operacion(billetera)
    viajes_vinculados = 0
    cursor_diagnostico = viajes_transporte.find(
        consulta_viajes,
        {"usuario_id": 1, "usuario": 1, "operacion_id": 1},
    ).batch_size(5000)
    for viaje in cursor_diagnostico:
        usuario_id = viaje.get("usuario_id")
        if usuario_id is None:
            referencia = viaje.get("usuario")
            usuario_id = getattr(referencia, "id", None)
            if usuario_id is None and isinstance(referencia, dict):
                usuario_id = referencia.get("$id")
        if usuario_id is None:
            usuario_id = usuarios_por_operacion.get(str(viaje.get("operacion_id", "")))
        if usuario_id is not None:
            viajes_vinculados += 1
    print(
        "Diagnóstico Transporte en Atlas: "
        f"{transporte.name}.transacciones tiene "
        f"{viajes_transporte.estimated_document_count()} documentos; "
        f"{viajes_en_atlas} pasajes desde 2023, "
        f"{viajes_vinculados} asociados directamente o por operación de Billetera.",
        flush=True,
    )
    if viajes_vinculados == 0:
        raise SystemExit(
            "Atlas no tiene pasajes atribuibles a usuarios en Transporte ni "
            "operaciones coincidentes en Billetera. "
            "No se generaron ni reemplazaron CSV. Las transacciones nuevas ya "
            "guardan usuario_id. Los viajes universales antiguos pueden vincularse "
            "por operacion_id cuando exista el cobro correspondiente en Billetera; "
            "los viajes de tarjeta física sin ID ni número completo no se asignan "
            "automáticamente."
        )
    perfiles = transporte["perfiles_transporte"]

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

        print("Exportando viajes de Transporte desde Atlas...", flush=True)
        # El modelo necesita como maximo 365 dias previos a 2024-01-01.
        # Acotar por fecha permite aprovechar transacciones_fecha_idx y evita
        # escanear todo el historial antiguo de Transporte.
        fecha_corte = datetime.now()
        consulta_viajes["fecha"]["$lte"] = fecha_corte
        documentos_procesados = 0
        reintentos = 0
        pausas_reintento = (2, 5, 10, 20, 30, 45, 60, 60)
        while True:
            cursor = None
            vistos_en_cursor = 0
            try:
                cursor = transporte["transacciones"].find(
                    consulta_viajes,
                    {"usuario_id": 1, "usuario": 1, "operacion_id": 1, "fecha": 1},
                ).sort("fecha", 1).batch_size(1000)

                for t in cursor:
                    vistos_en_cursor += 1
                    # Si Atlas corta la conexión, se reabre el cursor ordenado y
                    # se saltan los documentos ya escritos para no duplicarlos.
                    if vistos_en_cursor <= documentos_procesados:
                        continue
                    usuario_id = t.get("usuario_id")
                    if usuario_id is None:
                        referencia = t.get("usuario")
                        usuario_id = getattr(referencia, "id", None)
                        if usuario_id is None and isinstance(referencia, dict):
                            usuario_id = referencia.get("$id")
                    if usuario_id is None:
                        usuario_id = usuarios_por_operacion.get(
                            str(t.get("operacion_id", ""))
                        )
                    if usuario_id is not None:
                        w.writerow([str(usuario_id), fecha_texto(t.get("fecha")), "transporte"])
                        conteo["transporte"] += 1
                        if conteo["transporte"] % 2000 == 0:
                            print(f"  Viajes exportados: {conteo['transporte']}", flush=True)
                    documentos_procesados += 1

                break
            except (AutoReconnect, ConnectionFailure, CursorNotFound, NetworkTimeout) as error:
                reintentos += 1
                if reintentos > len(pausas_reintento):
                    raise SystemExit(
                        "Atlas cerró la conexión durante la exportación de viajes. "
                        "El CSV quedó incompleto; vuelve a ejecutar el .bat cuando la conexión esté estable."
                    ) from error
                espera = pausas_reintento[reintentos - 1]
                print(
                    f"  Se perdió la conexión con Atlas durante Transporte. "
                    f"Reintento {reintentos}/{len(pausas_reintento)} en {espera} s "
                    f"(ya escritos: {conteo['transporte']} viajes)...",
                    flush=True,
                )
                if cursor is not None:
                    try:
                        cursor.close()
                    except Exception:
                        pass
                cli.close()
                time.sleep(espera)
                cli = MongoClient(URI, **opciones_mongo)
                transporte = cli[PREFIJO + "_transporte"]

        print(f"Viajes de Transporte listos: {conteo['transporte']}", flush=True)

    print(f"eventos_{SUFIJO}.csv: {sum(conteo.values())} eventos "
          f"(billetera: {conteo['billetera']}, movil: {conteo['movil']}, "
          f"transporte: {conteo['transporte']})")


if __name__ == "__main__":
    main()

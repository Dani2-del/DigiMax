"""Recupera el usuario de viajes legados usando vínculos verificables.

Por defecto solo muestra un resumen. Usa --aplicar para escribir en Atlas.
No asigna viajes usando números de tarjeta enmascarados.
"""
import argparse
import os
from collections import Counter
from getpass import getpass
from urllib.parse import urlsplit

from pymongo import MongoClient, UpdateOne


def obtener_uri():
    uri = os.environ.get("DIGITRANS_ATLAS_URI", "").strip().strip('"').strip("'")
    if not uri:
        uri = getpass("Pega la URI de MongoDB Atlas: ").strip().strip('"').strip("'")
    partes = urlsplit(uri)
    if partes.scheme not in {"mongodb", "mongodb+srv"} or not (partes.hostname or "").endswith(".mongodb.net"):
        raise SystemExit("La URI debe apuntar a MongoDB Atlas (*.mongodb.net).")
    return uri


def usuarios_acceso_por_id(db):
    usuarios = {}
    por_correo = {}
    for doc in db["usuarios"].find({}, {"nombre": 1, "correo": 1}):
        usuario_id = str(doc["_id"])
        correo = str(doc.get("correo") or "").strip().lower()
        usuarios[usuario_id] = {
            "nombre": str(doc.get("nombre") or ""),
            "correo": str(doc.get("correo") or ""),
        }
        if correo:
            por_correo.setdefault(correo, set()).add(usuario_id)
    return usuarios, por_correo


def usuarios_por_operacion(billetera):
    """Devuelve solo operaciones CONSUMO con un único dueño en Billetera."""
    pipeline = [
        {"$unwind": "$movimientos"},
        {"$match": {
            "movimientos.tipo": "CONSUMO",
            "movimientos.operacionId": {"$exists": True, "$nin": [None, ""]},
        }},
        {"$group": {
            "_id": "$movimientos.operacionId",
            "usuarios": {"$addToSet": "$_id"},
        }},
    ]
    por_operacion = {}
    ambiguas = 0
    for doc in billetera["billeteras_universales"].aggregate(
        pipeline, allowDiskUse=True, batchSize=5000
    ):
        duenos = doc.get("usuarios", [])
        if len(duenos) == 1:
            por_operacion[str(doc["_id"])] = str(duenos[0])
        elif len(duenos) > 1:
            ambiguas += 1
    return por_operacion, ambiguas


def usuario_referenciado(doc):
    referencia = doc.get("usuario")
    if referencia is None:
        return None
    usuario_id = getattr(referencia, "id", None)
    if usuario_id is None and isinstance(referencia, dict):
        usuario_id = referencia.get("$id")
    return str(usuario_id) if usuario_id is not None else None


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--aplicar", action="store_true",
        help="escribe usuario_id y los datos de presentación en Atlas",
    )
    args = parser.parse_args()

    client = MongoClient(
        obtener_uri(), connectTimeoutMS=30000,
        serverSelectionTimeoutMS=30000, socketTimeoutMS=120000,
    )
    acceso = client["digitrans_acceso"]
    transporte = client["digitrans_transporte"]["transacciones"]
    billetera = client["digitrans_billetera"]

    usuarios, usuarios_por_correo = usuarios_acceso_por_id(acceso)
    operaciones, operaciones_ambiguas = usuarios_por_operacion(billetera)
    filtro = {
        "tipo": {"$regex": "pasaje", "$options": "i"},
        "$or": [
            {"usuario_id": {"$exists": False}},
            {"usuario_id": None},
            {"usuario_id": ""},
        ],
    }
    proyeccion = {
        "usuario_id": 1, "usuario": 1, "operacion_id": 1,
        "usuario_correo": 1,
    }

    reparaciones = []
    resumen = Counter()
    for transaccion in transporte.find(filtro, proyeccion).batch_size(5000):
        candidatos = {}

        referencia_id = usuario_referenciado(transaccion)
        if referencia_id in usuarios:
            candidatos.setdefault(referencia_id, set()).add("referencia")

        operacion_id = transaccion.get("operacion_id")
        usuario_operacion = operaciones.get(str(operacion_id)) if operacion_id else None
        if usuario_operacion in usuarios:
            candidatos.setdefault(usuario_operacion, set()).add("operacion")

        correo = str(transaccion.get("usuario_correo") or "").strip().lower()
        posibles_por_correo = usuarios_por_correo.get(correo, set())
        if len(posibles_por_correo) == 1:
            candidato_correo = next(iter(posibles_por_correo))
            candidatos.setdefault(candidato_correo, set()).add("correo")

        if len(candidatos) != 1:
            resumen["conflictos" if len(candidatos) > 1 else "sin_vinculo"] += 1
            continue

        usuario_id, fuentes = next(iter(candidatos.items()))
        fuente = "+".join(sorted(fuentes))
        resumen[fuente] += 1
        reparaciones.append((transaccion["_id"], usuario_id, usuarios[usuario_id]))

        if len(reparaciones) >= 1000:
            if args.aplicar:
                guardar_lote(transporte, reparaciones)
            reparaciones.clear()

    if reparaciones and args.aplicar:
        guardar_lote(transporte, reparaciones)

    total_revisados = sum(resumen.values())
    total_vinculados = total_revisados - resumen["sin_vinculo"] - resumen["conflictos"]
    por_operacion = sum(
        cantidad for fuente, cantidad in resumen.items()
        if "operacion" in fuente.split("+")
    )
    por_referencia_correo = sum(
        cantidad for fuente, cantidad in resumen.items()
        if {"referencia", "correo"} & set(fuente.split("+"))
    )
    print(f"Viajes sin usuario revisados: {total_revisados}")
    print(f"Vínculos seguros encontrados: {total_vinculados}")
    print(f"  Por operación de Billetera: {por_operacion}")
    print(f"  Por referencia/correo exacto: {por_referencia_correo}")
    print(f"Sin vínculo seguro: {resumen['sin_vinculo']}")
    print(f"Fuentes en conflicto: {resumen['conflictos']}")
    print(f"Operaciones de Billetera con más de un dueño: {operaciones_ambiguas}")
    if args.aplicar:
        print("Reparación aplicada en Atlas; no se borraron ni reemplazaron transacciones.")
    else:
        print("Vista previa solamente. Para escribir los vínculos, vuelve a ejecutar con --aplicar.")
    client.close()


def guardar_lote(transacciones, reparaciones):
    operaciones = []
    for transaccion_id, usuario_id, datos_usuario in reparaciones:
        operaciones.append(UpdateOne(
            {"_id": transaccion_id, "$or": [
                {"usuario_id": {"$exists": False}}, {"usuario_id": None}, {"usuario_id": ""},
            ]},
            {"$set": {
                "usuario_id": usuario_id,
                "usuario_nombre": datos_usuario["nombre"],
                "usuario_correo": datos_usuario["correo"],
            }},
        ))
    if operaciones:
        transacciones.bulk_write(operaciones, ordered=False)


if __name__ == "__main__":
    main()

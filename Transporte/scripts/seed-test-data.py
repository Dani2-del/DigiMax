from __future__ import annotations

import getpass
import os
import random
import re
import sys
from datetime import date, datetime, time, timedelta, timezone
from decimal import Decimal
from urllib.parse import unquote, urlsplit

import bcrypt
from bson import DBRef, Decimal128
from pymongo import MongoClient, ReplaceOne
from pymongo.errors import PyMongoError


USERS = 10_000
DRIVERS = 100
BATCH_SIZE = 500
FARE_COP = 3_900
SEED = 20261010
TEST_PASSWORD = "DigitransTest123!"
ROUTES = (
    "T100E", "T101", "T102", "T103", "X101", "X102", "X103", "X104",
    "X105", "X106", "A101", "A102", "A103", "A104", "A105", "A107",
    "A108", "A111", "A114", "A117", "A118", "C001", "C016", "C017", "C018",
)
URI_PATTERN = re.compile(r"^mongodb(?:\+srv)?://", re.IGNORECASE)


def get_test_uri() -> tuple[str, str]:
    uri = os.environ.get("SEED_MONGODB_URI")
    if not uri:
        uri = getpass.getpass(
            "URI de MongoDB para la base de pruebas (_test o -test): "
        )
    uri = uri.strip()
    if "<" in uri or ">" in uri or not URI_PATTERN.match(uri):
        raise ValueError("URI invalida. Pega la URI real de Atlas, no el ejemplo con marcadores < >.")

    parsed = urlsplit(uri)
    database = unquote(parsed.path.lstrip("/").split("/", 1)[0])
    if not parsed.hostname or not database:
        raise ValueError("La URI debe incluir el host y el nombre de la base despues de .net/.")
    if database.lower() != "test" and not database.lower().endswith(("_test", "-test")):
        raise ValueError("Carga cancelada: la base debe llamarse 'test' o terminar en '_test'/'-test'.")
    return uri, database


def timestamp(value: datetime) -> datetime:
    return value.replace(tzinfo=timezone.utc)


def date_value(value: date) -> datetime:
    return timestamp(datetime.combine(value, time.min))


def batches(collection, documents) -> None:
    operations = []
    for document in documents:
        operations.append(ReplaceOne({"_id": document["_id"]}, document, upsert=True))
        if len(operations) >= BATCH_SIZE:
            collection.bulk_write(operations, ordered=False)
            operations.clear()
    if operations:
        collection.bulk_write(operations, ordered=False)


def user_id(number: int) -> str:
    return f"seed-user-{number:05d}"


def driver_id(number: int) -> str:
    return f"seed-driver-{number:03d}"


def bus_id(number: int) -> str:
    return f"seed-bus-{number:03d}"


def card_number(number: int) -> str:
    return f"98{number:08d}"


def masked_card(number: int) -> str:
    return "******" + card_number(number)[-4:]


def user_documents(today: date, password_hash: str):
    for number in range(1, USERS + 1):
        rng = random.Random(SEED + number)
        passes = rng.randrange(1, 10)
        trips = rng.randrange(min(5, passes) + 1)
        card = card_number(number)
        registration = today - timedelta(days=rng.randrange(1, 1096))
        yield {
            "_id": user_id(number),
            "correo": f"usuario.{number:05d}@seed.digitrans.test",
            "password_hash": password_hash,
            "nombre": f"Usuario de prueba {number:05d}",
            "fecha_registro": timestamp(datetime.combine(registration, time(12))),
            "role": "ROLE_USER",
            "activo": True,
            "verificado": True,
            "tarjetas": [{
                "numeroTarjeta": card,
                "fechaCreacion": timestamp(datetime.now()),
                "saldo": Decimal128(Decimal((passes - trips) * FARE_COP)),
            }],
            "rutas_favoritas": [],
            "preferencias_notificacion_rutas": [],
        }


def driver_and_bus_documents(today: date, password_hash: str):
    buses = []
    drivers = []
    for number in range(1, DRIVERS + 1):
        assigned_route = ROUTES[(number - 1) % len(ROUTES)]
        assigned_bus = bus_id(number)
        buses.append({
            "_id": assigned_bus,
            "placa": f"SDT{number:05d}",
            "tipo": ("ALIMENTADOR", "TRONCAL", "PETRONCAL")[number % 3],
            "rutas_disponibles": [assigned_route],
            "activo": True,
        })
        rng = random.Random(SEED * 2 + number)
        registration = today - timedelta(days=rng.randrange(30, 1096))
        drivers.append({
            "_id": driver_id(number),
            "correo": f"conductor.{number:03d}@seed.digitrans.test",
            "password_hash": password_hash,
            "nombre": f"Conductor de prueba {number:03d}",
            "fecha_registro": timestamp(datetime.combine(registration, time(12))),
            "role": "ROLE_CONDUCTOR",
            "activo": True,
            "verificado": True,
            "tarjetas": [],
            "rutas_favoritas": [],
            "preferencias_notificacion_rutas": [],
            "bus_asignado": assigned_bus,
        })
    return drivers, buses


def transaction_documents(today: date):
    for number in range(1, USERS + 1):
        rng = random.Random(SEED + number)
        passes = rng.randrange(1, 10)
        trips = rng.randrange(min(5, passes) + 1)
        reload_day = today - timedelta(days=rng.randrange(2, 367))
        reload_date = datetime.combine(
            reload_day, time(rng.randrange(5, 22), rng.randrange(60))
        )
        card = masked_card(number)
        reference = DBRef("usuarios", user_id(number))
        yield {
            "_id": f"seed-tx-user-{number:05d}-00",
            "monto": float(passes * FARE_COP),
            "fecha": timestamp(reload_date),
            "tipo": f"Recarga Tarjeta: {card}",
            "metodo_pago": "SIMULACION",
            "tarjeta_transcaribe": card,
            "cantidad_pasajes": passes,
            "usuario": reference,
        }
        days_until_today = (today - reload_day).days
        for trip in range(1, trips + 1):
            travel_day = reload_day + timedelta(days=rng.randrange(1, days_until_today + 1))
            travel_time = time(rng.randrange(5, 23), rng.randrange(60))
            yield {
                "_id": f"seed-tx-user-{number:05d}-{trip:02d}",
                "monto": float(FARE_COP),
                "fecha": timestamp(datetime.combine(travel_day, travel_time)),
                "tipo": f"Cobro Pasaje Tarjeta: {card}",
                "tarjeta_transcaribe": card,
                "usuario": reference,
            }


def schedule_documents(today: date):
    first_day = today - timedelta(days=180)
    for number in range(1, DRIVERS + 1):
        rng = random.Random(SEED * 3 + number)
        trip_count = rng.randrange(15, 31)
        selected_days = sorted(rng.sample(range(181), trip_count))
        route = ROUTES[(number - 1) % len(ROUTES)]
        for sequence, day_offset in enumerate(selected_days, start=1):
            start_minutes = rng.randrange(5 * 60, 20 * 60)
            duration = rng.randrange(45, 111)
            start = datetime.combine(today, time.min) + timedelta(minutes=start_minutes)
            end = start + timedelta(minutes=duration)
            yield {
                "_id": f"seed-trip-{number:03d}-{sequence:02d}",
                "conductor_id": driver_id(number),
                "bus_id": bus_id(number),
                "bus_placa": f"SDT{number:05d}",
                "ruta": route,
                "sentido": "VUELTA",
                "fecha": date_value(first_day + timedelta(days=day_offset)),
                "hora_inicio": start.strftime("%H:%M:%S"),
                "hora_fin": end.strftime("%H:%M:%S"),
                "estado": "FINALIZADA",
            }


def main() -> int:
    try:
        uri, database_name = get_test_uri()
        with MongoClient(uri, serverSelectionTimeoutMS=15_000) as client:
            client.admin.command("ping")
            database = client[database_name]
            today = date.today()
            password_hash = bcrypt.hashpw(
                TEST_PASSWORD.encode("utf-8"),
                bcrypt.gensalt(rounds=10, prefix=b"2a"),
            ).decode("ascii")

            print(f"Conectado a la base de pruebas '{database_name}'. Cargando datos...")
            batches(database["usuarios"], user_documents(today, password_hash))
            drivers, buses = driver_and_bus_documents(today, password_hash)
            batches(database["buses"], buses)
            batches(database["usuarios"], drivers)
            batches(database["transacciones"], transaction_documents(today))
            batches(database["horarios_conductor"], schedule_documents(today))

            print("Carga completada: 10.000 usuarios, 100 conductores, 100 buses,")
            print("transacciones sintéticas e historiales de 15 a 30 viajes por conductor.")
            print("Correo de ejemplo: usuario.00001@seed.digitrans.test")
            print(f"Contraseña de las cuentas sintéticas: {TEST_PASSWORD}")
        return 0
    except (ValueError, PyMongoError) as error:
        print(f"No se pudo completar la carga: {error}", file=sys.stderr)
        return 1
    except KeyboardInterrupt:
        print("\nCarga interrumpida. Puedes volver a ejecutarla; los IDs son idempotentes.", file=sys.stderr)
        return 130


if __name__ == "__main__":
    raise SystemExit(main())

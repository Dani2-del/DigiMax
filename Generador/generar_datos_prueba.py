"""Reconstruye las bases Digitrans en Atlas con datos de demostraci?n.

Antes del borrado valida y carga operadores/paquetes desde MongoDB local y
respalda administradores, geometr?as y tarjetas RFID libres de Atlas. Despu?s
pide una confirmaci?n escrita, elimina digitrans_* y digitrans_rebuild_*, y
puebla digitrans_* desde cero.
Requisitos: python -m pip install pymongo bcrypt
Variables: MONGO_URI (Atlas), MONGO_LOCAL (por defecto localhost:27017).
"""
import argparse
import base64
import datetime as dt
import getpass
import os
import random
import string
import unicodedata
import uuid
from urllib.parse import urlsplit

import bcrypt
from bson import ObjectId, Decimal128, Int64
from pymongo import MongoClient

from rutas_catalogo import RUTAS_TRANSMILENIO, RUTAS_TRANSMETRO

URI = os.environ.get("MONGO_URI") or os.environ.get("DIGITRANS_ATLAS_URI")
LOCAL_URI = os.environ.get("MONGO_LOCAL", "mongodb://localhost:27017")
PASSWORD = os.environ.get("DIGITRANS_TEST_PASSWORD")
FECHA_INI = dt.datetime(2023, 1, 1)
FECHA_FIN = dt.datetime(2026, 10, 29, 23, 59)
INICIO_HISTORIAL_SINTETICO = FECHA_INI
LOTE = 1000  # usuarios por lote: controla el uso de memoria

SERVICIOS = ["TRANSCARIBE", "TRANSMILENIO", "TRANSMETRO"]
NOMBRE_SERVICIO = {"TRANSCARIBE": "Transcaribe", "TRANSMILENIO": "TransMilenio", "TRANSMETRO": "Transmetro"}
CONDUCTORES = {"TRANSMILENIO": (10000, 12000), "TRANSCARIBE": (400, 500), "TRANSMETRO": (400, 500)}
MODERADORES = {"TRANSMILENIO": (500, 600), "TRANSCARIBE": (25, 50), "TRANSMETRO": (25, 50)}
BUSES = {"TRANSMILENIO": 12573, "TRANSCARIBE": 367, "TRANSMETRO": 364}
RUTAS = {
    "TRANSCARIBE": ["T100E", "T101", "T102", "T103", "X101", "X102", "X103", "X104", "X105", "X106",
                   "A101", "A102", "A103", "A104", "A105", "A107", "A108", "A111", "A114", "A117", "A118",
                   "C001", "C016", "C017", "C018"],
    "TRANSMILENIO": RUTAS_TRANSMILENIO,
    "TRANSMETRO": RUTAS_TRANSMETRO,
}
DISENOS = ["OCEANO", "BOSQUE", "ATARDECER", "LAVANDA", "RUBI", "CARBON", "ESMERALDA", "COBRE", "GLACIAR", "GALAXIA"]
NOMBRES = [
    "Andrés", "Laura", "Carlos", "María", "Juan", "Valentina", "Daniel", "Camila", "Luis", "Sofía", "Mateo", "Paula",
    "Jorge", "Ana", "Diego", "Lucía", "Felipe", "Daniela", "Sebastián", "Carolina", "Santiago", "Isabella", "Nicolás",
    "Mariana", "Alejandro", "Juliana", "Miguel", "Gabriela", "David", "Natalia", "Samuel", "Valeria", "Andrea",
    "Tatiana", "Julián", "Catalina", "Esteban", "Manuela", "Martín", "Salomé", "Gabriel", "Paola", "Rafael", "Melissa",
    "Jhon", "Yurany", "Leidy", "Yesica", "Wilson", "Kevin", "Brayan", "Ximena", "Yeison", "Karen", "Johan", "Lorena",
    "Óscar", "Marcela", "Hernán", "Yuliana", "Adrián", "Alexandra", "Ángela", "Antonio", "Arturo", "Bárbara", "Bianca",
    "Blanca", "Bruno", "Camilo", "Cristian", "Cristina", "Dayana", "Darío", "Eliana", "Emilio", "Emma", "Enrique",
    "Erika", "Estefanía", "Eva", "Fabián", "Fabiola", "Fernanda", "Francisco", "Gerardo", "Gisela", "Guillermo",
    "Heidy", "Hugo", "Ingrid", "Irene", "Isaac", "Ivana", "Javier", "Jazmín", "Jeisson", "Jennifer", "Jesús",
    "Joaquín", "Jonathan", "José", "Josué", "Karla", "Laila", "Lina", "Lisandro", "Lourdes", "Lucas", "Luciana",
    "Luisa", "Manuel", "Marco", "Margarita", "Mario", "Mauricio", "Maximiliano", "Milena", "Mónica", "Nadia", "Nancy",
    "Nataly", "Noelia", "Olga", "Orlando", "Pablo", "Patricia", "Pedro", "Rafaela", "Ramiro", "Renata", "Ricardo",
    "Roberto", "Rocío", "Rodrigo", "Rosa", "Ruth", "Sara", "Sergio", "Silvana", "Simón", "Sonia", "Stefany", "Tomás",
    "Valentín", "Vanessa", "Verónica", "Víctor", "Vicente", "Yadira", "Yolanda", "Yulieth", "Zoe",
]
SEGUNDOS = [
    "Alberto", "Eduardo", "Fernando", "Ricardo", "Enrique", "Hernando", "Luz", "Elena", "Beatriz", "Cecilia", "Rosa",
    "Teresa", "Patricia", "Adriana", "Sandra", "Jimena", "Iván", "Mauricio", "Gustavo", "Hugo", "Julio", "Sergio",
    "Camilo", "Ángel", "Emilio", "Cristian", "Leonardo", "Esperanza", "Dolores", "Amparo", "Nelson", "Edwin", "Yamile",
    "Diana", "Stefany", "Maritza", "Rubén", "Gloria", "Lina", "Pilar", "Alfredo", "Armando", "Bernardo", "Calixto",
    "César", "Clemente", "Constanza", "Dora", "Ernesto", "Eugenia", "Felicia", "Francisca", "Genaro", "Gilberto",
    "Graciela", "Guadalupe", "Heriberto", "Ilda", "Inés", "Isidro", "Jacinto", "Jorgelina", "Josefina", "Juana",
    "Lázaro", "Leticia", "Lucía", "Luz Marina", "Magdalena", "Marina", "Matilde", "Milagros", "Nicanor", "Nora",
    "Osvaldo", "Patricio", "Porfirio", "Raúl", "Remedios", "Reinaldo", "Rigoberto", "Rita", "Roque", "Salomón",
    "Silvia", "Susana", "Tito", "Ulises", "Vicenta", "Wilfredo", "Yesenia", "Zaida", "Zulma",
]
APELLIDOS = [
    "García", "Rodríguez", "Martínez", "Hernández", "López", "González", "Pérez", "Sánchez", "Ramírez", "Torres",
    "Flórez", "Vargas", "Castillo", "Romero", "Herrera", "Morales", "Ospino", "Murillo", "Correa", "Garzón", "Navarro",
    "Caballero", "Mendoza", "Ramos", "Blanco", "Ortiz", "Ruiz", "Jiménez", "Moreno", "Muñoz", "Álvarez", "Gutiérrez",
    "Suárez", "Díaz", "Rojas", "Acosta", "Cárdenas", "Peña", "Castro", "Vega", "Pineda", "Cantillo", "Villalba",
    "Bolívar", "Tapia", "Quintero", "Lozano", "Benítez", "Montes", "Padilla", "Meza", "Llanos", "Restrepo", "Zúñiga",
    "Vanegas", "Barrios", "Coronado", "Mercado", "Pertuz", "Arrieta", "Hoyos", "Fontalvo", "Lora", "Cuadrado",
    "Jaramillo", "Berrío", "Saumeth", "Márquez", "Lobo", "Olivera", "Acevedo", "Agudelo", "Aguilar", "Alarcón",
    "Alcántara", "Almanza", "Alvarado", "Amaya", "Anaya", "Angulo", "Aponte", "Araújo", "Arango", "Arboleda",
    "Arciniegas", "Ardila", "Arias", "Ávila", "Avendaño", "Baena", "Bahamón", "Barreto", "Bautista", "Bedoya",
    "Beltrán", "Bermúdez", "Bernal", "Bonilla", "Bravo", "Briceño", "Buitrago", "Burgos", "Bustos", "Caicedo",
    "Calderón", "Camacho", "Campo", "Cano", "Cardona", "Carrillo", "Carvajal", "Castaño", "Cepeda", "Cerón",
    "Cifuentes", "Cruz", "Cuartas", "Dávila", "Delgado", "Domínguez", "Duarte", "Echeverry", "Escobar", "Espinosa",
    "Estrada", "Fajardo", "Feria", "Fernández", "Figueroa", "Forero", "Franco", "Gallego", "Gaona", "Gamboa", "Garcés",
    "Girón", "Godoy", "Gonzaga", "Granados", "Guerrero", "Guevara", "Gil", "Hidalgo", "Hurtado", "Ibáñez", "Iglesias",
    "Jaimes", "Lara", "Leal", "León", "Londoño", "Luna", "Macías", "Maldonado", "Marín", "Mariño", "Martelo", "Medina",
    "Mejía", "Melo", "Méndez", "Mesa", "Miranda", "Molina", "Montoya", "Mora", "Mosquera", "Narváez", "Nieto",
    "Noriega", "Núñez", "Oliveros", "Orozco", "Ortega", "Osorio", "Pacheco", "Palacio", "Pardo", "Parra", "Paternina",
    "Pava", "Pico", "Pinzón", "Pizarro", "Polo", "Prada", "Puentes", "Quiroga", "Rivera", "Robles", "Rodelo", "Roldán",
    "Rosales", "Rubio", "Salas", "Salcedo", "Salgado", "Sandoval", "Santos", "Sarmiento", "Segura", "Serna", "Silva",
    "Sosa", "Soto",
]
DOMINIOS = ["gmail.com", "hotmail.com", "outlook.com", "yahoo.com"]
ROL = {"USER": "ROLE_USER", "MODERATOR": "ROLE_MODERATOR", "CONDUCTOR": "ROLE_CONDUCTOR"}
CLASE_USUARIO = "com.Servicio.Acceso.model.Usuario"
CLASE_USUARIO_TRANSPORTE = "com.transcaribe.transcaribe.Model.Usuario"
CLASE_PERFIL = "com.transcaribe.transcaribe.Model.PerfilTransporte"
CLASE_TRANSACCION = "com.transcaribe.transcaribe.Model.Transaccion"
CLASE_BUS = "com.transcaribe.transcaribe.Model.Bus"
CLASE_HORARIO = "com.transcaribe.transcaribe.Model.HorarioConductor"
CLASE_LINEA = "com.Servicio.RecargasMovil.model.LineaMovil"
CLASE_RECARGA = "com.Servicio.RecargasMovil.model.RecargaMovil"
CLASE_BILLETERA = "com.Servicio.Billetera.model.BilleteraUniversal"
CLASE_VERIF = "com.Servicio.Billetera.model.VerificacionTarjeta"
PASAJE_TARJETA = 2600
PASAJE_UNIVERSAL = 3900


PREFIJO = "digitrans"
SUFIJOS_BASE = ("acceso", "transporte", "billetera", "recargas", "lector")
PREFIJO_PARCIAL = "digitrans_rebuild"


def a_utc(hora_local):
    """Colombia es UTC-5 todo el año: se suma 5 horas para guardar en UTC."""
    return hora_local + dt.timedelta(hours=5)


def fecha_aleatoria(desde, hasta):
    segundos = int((hasta - desde).total_seconds())
    return desde + dt.timedelta(seconds=random.randint(0, segundos))


def crear_perfil_actividad():
    """Crea hábitos sintéticos compartidos por recargas y uso de transporte.

    El perfil solo se usa durante la generación: no se guarda en Mongo ni se
    exporta como predictor. Los modelos deben inferir el hábito de eventos previos.
    """
    valor = random.random()
    if valor < 0.45:
        return {"cadencia": (110, 150), "omision": 0.06,
                "intentos_transporte": (8, 14), "pasajes_billetera": (6, 12),
                "recargas_moviles": (1, 4)}
    if valor < 0.80:
        return {"cadencia": (175, 235), "omision": 0.10,
                "intentos_transporte": (4, 8), "pasajes_billetera": (3, 7),
                "recargas_moviles": (0, 3)}
    return {"cadencia": (260, 360), "omision": 0.15,
            "intentos_transporte": (1, 4), "pasajes_billetera": (0, 3),
            "recargas_moviles": (0, 2)}


def inicio_actividad(registro):
    return max(registro, INICIO_HISTORIAL_SINTETICO)


def cantidad_en_historial(rango, registro):
    """Escala el número de eventos al tiempo de historial disponible del usuario."""
    inicio = inicio_actividad(registro)
    dias_disponibles = max(0, (FECHA_FIN - inicio).days)
    dias_historial = max(1, (FECHA_FIN - INICIO_HISTORIAL_SINTETICO).days)
    proporcion = min(1.0, dias_disponibles / dias_historial)
    cantidad = random.randint(*rango)
    return max(0, round(cantidad * proporcion))


def fechas_abonos_usuario(registro, perfil):
    """Genera recargas periódicas con ruido y ciclos omitidos para cada usuario."""
    inicio = inicio_actividad(registro)
    cadencia = random.randint(*perfil["cadencia"])
    jitter = max(3, round(cadencia * 0.08))
    proxima = inicio + dt.timedelta(days=random.randint(0, cadencia))
    fechas = []
    while proxima <= FECHA_FIN:
        fecha = proxima + dt.timedelta(days=random.randint(-jitter, jitter))
        fecha = min(FECHA_FIN, max(inicio, fecha))
        if random.random() >= perfil["omision"]:
            fechas.append(fecha)
        variacion = max(3, round(cadencia * 0.08))
        avance = max(30, cadencia + random.randint(-variacion, variacion))
        proxima += dt.timedelta(days=avance)
    return fechas


def digitos(n):
    return "".join(random.choice(string.digits) for _ in range(n))


def hex_mayus(n):
    return "".join(random.choices("0123456789ABCDEF", k=n))


def b64(n):
    return base64.b64encode(random.randbytes(n)).decode()


def sin_tildes(texto):
    limpio = unicodedata.normalize("NFKD", texto)
    return "".join(c for c in limpio if not unicodedata.combining(c)).lower().replace(" ", "")


def generar_nombre():
    """Ejemplo: Laura Sofía Ramírez Mendoza. El segundo nombre es opcional."""
    partes = [random.choice(NOMBRES)]
    if random.random() < 0.5:
        partes.append(random.choice(SEGUNDOS))
    partes += [random.choice(APELLIDOS), random.choice(APELLIDOS)]
    return " ".join(partes)


def correo_realista(nombre_completo):
    """Ejemplo: andres.perez1234@gmail.com, laura_gomez@hotmail.com"""
    partes = nombre_completo.split()
    apellido = partes[-2] if len(partes) >= 4 else partes[-1]
    base = sin_tildes(partes[0]) + random.choice(["", ".", "_"]) + sin_tildes(apellido)
    numero = str(random.randint(1, 9999)) if random.random() < 0.6 else ""
    return f"{base}{numero}@{random.choice(DOMINIOS)}"


class Unicos:
    """Guarda los valores ya usados para no repetir correos, tarjetas, telefonos ni placas."""
    def __init__(self):
        self.v = {"correo": set(), "tarjetaT": set(), "telefono": set(), "placa": set(), "nombre": set()}

    def nuevo(self, clave, generar):
        while True:
            x = generar()
            if x not in self.v[clave]:
                self.v[clave].add(x)
                return x


def insertar(coleccion, docs):
    if docs:
        coleccion.insert_many(docs, ordered=False)


def validar_uri(uri, permitir_local):
    uri = (uri or "").strip().strip('"').strip("'")
    try:
        partes = urlsplit(uri)
        host = (partes.hostname or "").lower()
    except ValueError:
        raise SystemExit("URI MongoDB no v?lida.")
    if partes.scheme not in {"mongodb", "mongodb+srv"}:
        raise SystemExit("La URI debe comenzar con mongodb:// o mongodb+srv://.")
    es_local = host in {"localhost", "127.0.0.1", "::1"}
    if permitir_local and not es_local:
        raise SystemExit("MONGO_LOCAL debe apuntar a MongoDB local (localhost/127.0.0.1).")
    if not permitir_local and (es_local or not host.endswith(".mongodb.net")):
        raise SystemExit("MONGO_URI debe apuntar a MongoDB Atlas (.mongodb.net).")
    return uri


def precargar_datos(cli_destino, cli_local):
    """Valida cat?logos locales y respalda lo que se conserva antes del borrado."""
    local_recargas = cli_local["digitrans_recargas"]
    operadores = list(local_recargas["operadores_movil"].find({}))
    paquetes = list(local_recargas["paquetes_movil"].find({}))
    if not operadores or not paquetes:
        raise SystemExit(
            "Mongo local debe contener digitrans_recargas.operadores_movil y "
            "paquetes_movil; no se borr? ninguna base."
        )
    ids_operadores = {str(o.get("_id")) for o in operadores}
    invalidos = [p for p in paquetes if str(p.get("operadorId")) not in ids_operadores]
    if invalidos:
        raise SystemExit(
            f"Hay {len(invalidos)} paquetes locales cuyo operadorId no existe "
            "en operadores_movil; no se borr? ninguna base."
        )

    origen_acceso = cli_destino["digitrans_acceso"]
    origen_transporte = cli_destino["digitrans_transporte"]
    administradores = list(origen_acceso["usuarios"].find({"role": "ROLE_ADMIN"}))
    if not administradores:
        raise SystemExit(
            "No se encontraron administradores en digitrans_acceso.usuarios; "
            "se cancela para no dejar la plataforma sin acceso administrativo."
        )
    admins_transporte = list(origen_transporte["usuarios"].find({"role": "ROLE_ADMIN"}))
    geometrias = list(origen_transporte["geometrias_rutas"].find({}))
    disponibles = list(cli_destino["digitrans_lector"]["tarjetas_rfid"].find({
        "$or": [{"usuarioId": None}, {"usuarioId": ""}, {"usuarioId": {"$exists": False}}]
    }))
    return operadores, paquetes, administradores, admins_transporte, geometrias, disponibles


def restaurar_datos_esenciales(cli, datos):
    operadores, paquetes, administradores, admins_transporte, geometrias, disponibles = datos
    acceso = cli["digitrans_acceso"]
    transporte = cli["digitrans_transporte"]
    recargas = cli["digitrans_recargas"]
    lector = cli["digitrans_lector"]
    insertar(recargas["operadores_movil"], operadores)
    insertar(recargas["paquetes_movil"], paquetes)
    insertar(transporte["geometrias_rutas"], geometrias)
    insertar(lector["tarjetas_rfid"], disponibles)
    insertar(acceso["usuarios"], administradores)
    correos_transporte = {d.get("correo", "").strip().lower() for d in admins_transporte}
    insertar(transporte["usuarios"], admins_transporte)
    espejos = []
    for admin in administradores:
        correo = admin.get("correo", "")
        if correo.strip().lower() in correos_transporte:
            continue
        espejo = {"_id": admin["_id"], "correo": correo,
                  "password_hash": admin.get("password_hash"),
                  "nombre": admin.get("nombre", "Administrador"),
                  "fecha_registro": admin.get("fecha_registro"), "role": "ROLE_ADMIN",
                  "activo": admin.get("activo", True), "verificado": admin.get("verificado", True),
                  "tarjetas": [], "rutas_favoritas": [], "preferencias_notificacion_rutas": [],
                  "_class": CLASE_USUARIO_TRANSPORTE}
        servicio = admin.get("servicio_asignado")
        if servicio:
            espejo["sistema_asignado"] = servicio
            espejo["sitm"] = servicio
        espejos.append(espejo)
    insertar(transporte["usuarios"], espejos)
    print(f"Cat?logos desde Mongo local: {len(operadores)} operadores, {len(paquetes)} paquetes")
    print(f"Respaldados desde Atlas: {len(administradores)} administradores, "
          f"{len(geometrias)} geometr?as, {len(disponibles)} tarjetas RFID libres")

def crear_usuario(rol, servicio, u, hash_pw, paquetes, operadores, buses_by_service, salida):
    """Genera todos los documentos de un usuario y los agrega a las listas de 'salida'."""
    svc_p = random.choice(SERVICIOS) if rol in ("USER", "MODERATOR") else servicio
    uid = ObjectId()
    uid_s = str(uid)
    nombre = u.nuevo("nombre", generar_nombre)
    correo = u.nuevo("correo", lambda: correo_realista(nombre))
    registro = fecha_aleatoria(FECHA_INI, FECHA_FIN - dt.timedelta(days=30))
    perfil_actividad = crear_perfil_actividad()

    doc_acc = {"_id": uid, "correo": correo, "password_hash": hash_pw, "nombre": nombre,
               "fecha_registro": registro, "role": ROL[rol], "activo": True, "verificado": True,
               "_class": CLASE_USUARIO, "datoPrueba": True}
    if rol != "USER":
        doc_acc["servicio_asignado"] = servicio
    salida["acc"].append(doc_acc)

    # Transporte: perfil enlazado por el mismo ID, con la tarjeta embebida
    tarjeta_t = u.nuevo("tarjetaT", lambda: digitos(10))
    ultimos = tarjeta_t[-4:]
    saldo_t = 0
    cantidad_intentos = cantidad_en_historial(perfil_actividad["intentos_transporte"], registro)
    inicio_transporte = inicio_actividad(registro)
    for f in sorted(fecha_aleatoria(inicio_transporte, FECHA_FIN)
                    for _ in range(cantidad_intentos)):
        if saldo_t > 0 and random.random() < 0.5:
            saldo_t -= PASAJE_TARJETA
            salida["tx"].append({"_id": ObjectId(), "monto": PASAJE_TARJETA, "fecha": f,
                                 "tipo": f"PASAJE [TARJETA TRANSPORTE] - Pasaje {NOMBRE_SERVICIO[svc_p]}: ruta {random.choice(RUTAS[svc_p])}",
                                 "tarjeta_transcaribe": f"******{ultimos}", "operacion_id": str(uuid.uuid4()),
                                 "sistema_transporte": svc_p, "cantidad_pasajes": 1,
                                 "usuario_id": uid_s, "usuario_correo": correo, "usuario_nombre": nombre,
                                 "_class": CLASE_TRANSACCION, "datoPrueba": True})
        else:
            monto = random.choice([5000, 10000, 20000, 30000, 50000])
            saldo_t += monto
            metodo = random.choice(["PSE", "TARJETA"])
            salida["tx"].append({"_id": ObjectId(), "monto": monto, "fecha": f,
                                 "tipo": f"Recarga Tarjeta: ******{ultimos}", "metodo_pago": metodo,
                                 "cuenta_pse": correo if metodo == "PSE" else None,
                                 "tarjeta_transcaribe": f"******{ultimos}", "sistema_transporte": svc_p,
                                 "cantidad_pasajes": monto // PASAJE_TARJETA,
                                 "usuario_id": uid_s, "usuario_correo": correo, "usuario_nombre": nombre,
                                 "_class": CLASE_TRANSACCION, "datoPrueba": True})
    salida["usuario_transporte"].append({
        "_id": uid, "correo": correo, "password_hash": hash_pw, "nombre": nombre,
        "fecha_registro": registro, "role": ROL[rol], "activo": True, "verificado": True,
        "sitm": svc_p, "sistema_asignado": servicio if rol != "USER" else None,
        "tarjetas": [{"numeroTarjeta": tarjeta_t, "fechaCreacion": registro,
                      "saldo": f"{max(saldo_t, 0):.1f}", "sistemaTransporte": svc_p}],
        "rutas_favoritas": [], "preferencias_notificacion_rutas": [],
        "_class": CLASE_USUARIO_TRANSPORTE, "datoPrueba": True,
    })
    salida["perfil"].append({"_id": uid, "usuarioId": uid_s, "sitm": svc_p,
                             "tarjetas": [{"numeroTarjeta": tarjeta_t, "fechaCreacion": registro,
                                           "saldo": f"{max(saldo_t, 0):.1f}", "sistemaTransporte": svc_p}],
                             "rutas_favoritas": [], "preferencias_notificacion_rutas": [],
                             "_class": CLASE_PERFIL, "datoPrueba": True})

    # Lineas moviles: 1 a 4
    lineas = []
    for _ in range(random.randint(1, 4)):
        lid = ObjectId()
        op = random.choice(operadores)
        telf = u.nuevo("telefono", lambda: "3" + digitos(9))
        lineas.append((str(lid), telf, op))
        salida["lin"].append({"_id": lid, "usuarioId": uid_s, "operadorId": str(op["_id"]), "telefono": telf,
                              "alias": op["nombre"], "creadaEn": registro, "_class": CLASE_LINEA, "datoPrueba": True})

    # Billetera: cargas, pasajes con billetera y recargas moviles en orden de fecha
    tarjeta_u = "UNI-" + hex_mayus(12)
    eventos = []
    eventos.extend((fecha, "ABONO", None)
                   for fecha in fechas_abonos_usuario(registro, perfil_actividad))
    cantidad_pasajes = cantidad_en_historial(perfil_actividad["pasajes_billetera"], registro)
    for _ in range(cantidad_pasajes):
        eventos.append((fecha_aleatoria(inicio_actividad(registro), FECHA_FIN), "PASAJE", None))
    cantidad_recargas_moviles = cantidad_en_historial(perfil_actividad["recargas_moviles"], registro)
    for _ in range(cantidad_recargas_moviles):
        lid_s, telf, op = random.choice(lineas)
        candidatos = [p for p in paquetes if p["operadorId"] == str(op["_id"])] or paquetes
        eventos.append((fecha_aleatoria(inicio_actividad(registro), FECHA_FIN), "RECARGA",
                        (lid_s, telf, op, random.choice(candidatos))))
    eventos.sort(key=lambda e: e[0])

    saldo_u = 0
    movs = []
    for f, kind, extra in eventos:
        if kind == "ABONO":
            monto = random.choice([10000, 20000, 50000, 100000])
            saldo_u += monto
            pse = random.random() < 0.6
            movs.append({"operacionId": str(uuid.uuid4()), "tipo": "ABONO",
                         "descripcion": "Carga simulada de saldo con PSE" if pse else "Carga simulada de saldo con tarjeta crédito/débito",
                         "servicio": "Billetera", "monto": Decimal128(str(monto)), "fecha": f,
                         "estadoPago": "APROBADO", "metodoPago": "PSE" if pse else "TARJETA_CREDITO_DEBITO",
                         "correoPagador": correo, "idPagoProveedor": str(uuid.uuid4())})
        elif kind == "PASAJE":
            if saldo_u >= PASAJE_UNIVERSAL:
                saldo_u -= PASAJE_UNIVERSAL
                op_id = str(uuid.uuid4())
                movs.append({"operacionId": op_id, "tipo": "CONSUMO", "descripcion": "Pago de pasaje",
                             "servicio": "Transporte", "monto": Decimal128(str(PASAJE_UNIVERSAL)), "fecha": f})
                salida["tx"].append({"_id": ObjectId(), "monto": PASAJE_UNIVERSAL, "fecha": f,
                                     "tipo": f"PASAJE [TARJETA UNIVERSAL] - Pasaje {NOMBRE_SERVICIO[svc_p]}: ruta {random.choice(RUTAS[svc_p])}",
                                     "tarjeta_transcaribe": "UNIVERSAL", "operacion_id": op_id,
                                     "sistema_transporte": svc_p, "usuario_id": uid_s,
                                     "usuario_correo": correo, "usuario_nombre": nombre,
                                     "_class": CLASE_TRANSACCION, "datoPrueba": True})
        else:
            lid_s, telf, op, paq = extra
            precio = int(paq["precio"].to_decimal())
            rec_id = str(uuid.uuid4())
            if saldo_u >= precio:
                saldo_u -= precio
                estado = "SIMULADA"
                movs.append({"operacionId": rec_id, "tipo": "CONSUMO",
                             "descripcion": f"Paquete {op['nombre']} para ****{telf[-4:]}",
                             "servicio": "Paquetes y recargas", "monto": Decimal128(str(precio)), "fecha": f})
            else:
                estado = "RECHAZADA"
            salida["rec"].append({"_id": rec_id, "usuarioId": uid_s, "lineaId": lid_s, "telefono": telf,
                                  "operador": op["nombre"], "paquete": paq["nombre"],
                                  "monto": Decimal128(str(precio)), "estado": estado, "fecha": f,
                                  "_class": CLASE_RECARGA, "datoPrueba": True})

    salida["bil"].append({"_id": uid, "numero_tarjeta": tarjeta_u, "saldo": Decimal128(str(saldo_u)),
                          "activada_por_usuario": True, "estado_tarjeta": "ACTIVA",
                          "diseno_tarjeta": random.choice(DISENOS), "movimientos": movs,
                          "tarjetas_desactivadas": [], "_class": CLASE_BILLETERA, "datoPrueba": True})
    salida["verif"].append({"_id": uid, "accion": "CREAR", "codigoHash": b64(32), "salt": b64(16),
                            "solicitadaEn": registro, "expiraEn": registro + dt.timedelta(minutes=10),
                            "intentosFallidos": 0, "consumida": True, "version": Int64(1),
                            "_class": CLASE_VERIF, "datoPrueba": True})

    # Conductor: horarios del dia, en un bus de su servicio, con fechas coherentes
    if rol == "CONDUCTOR":
        bus_id, bus_placa = random.choice(buses_by_service[servicio])
        for dia in sorted(fecha_aleatoria(registro, FECHA_FIN) for _ in range(random.randint(20, 30))):
            dia_local = dt.datetime(dia.year, dia.month, dia.day)
            inicio_local = dia_local + dt.timedelta(hours=random.randint(5, 20))
            fin_local = inicio_local + dt.timedelta(hours=2)
            salida["hor"].append({"_id": ObjectId(), "conductor_id": uid_s, "bus_id": bus_id,
                                  "bus_placa": bus_placa, "sistema_transporte": servicio,
                                  "ruta": random.choice(RUTAS[servicio]), "sentido": random.choice(["IDA", "VUELTA"]),
                                  "fecha": a_utc(dia_local), "hora_inicio": a_utc(inicio_local),
                                  "hora_fin": a_utc(fin_local), "estado": "FINALIZADA",
                                  "_class": CLASE_HORARIO, "datoPrueba": True})


def main():
    ap = argparse.ArgumentParser(description="Borra las bases Digitrans y las reconstruye con datos de demostraci?n.")
    ap.add_argument("--escala", type=float, default=0.20,
                    help="proporci?n de los rangos originales (por defecto 20%%, escala usada anteriormente)")
    ap.add_argument("--usuarios", type=int, default=None,
                    help="fija el total de usuarios en vez de sortearlo dentro de la escala")
    ap.add_argument("--semilla", type=int, default=None,
                    help="semilla aleatoria para reproducir la generaci?n")
    args = ap.parse_args()
    if not 0 < args.escala <= 1:
        raise SystemExit("La escala debe ser mayor que 0 y como m?ximo 1.")
    if args.usuarios is not None and args.usuarios < 1:
        raise SystemExit("--usuarios debe ser un entero positivo.")
    if args.semilla is not None:
        random.seed(args.semilla)

    uri_destino = validar_uri(URI or getpass.getpass("Pega la URI de MongoDB Atlas destino (no se mostrar?): "), False)
    uri_local = validar_uri(LOCAL_URI, True)
    opciones = {"connectTimeoutMS": 20000, "serverSelectionTimeoutMS": 20000,
                "socketTimeoutMS": 120000, "retryWrites": True}
    cli = MongoClient(uri_destino, **opciones)
    cli_local = MongoClient(uri_local, connectTimeoutMS=5000, serverSelectionTimeoutMS=5000)
    try:
        cli.admin.command("ping")
        cli_local.admin.command("ping")
        # Todo lo necesario se carga y valida antes del primer drop_database.
        datos_esenciales = precargar_datos(cli, cli_local)
        operadores, paquetes = datos_esenciales[0], datos_esenciales[1]
    except Exception:
        cli.close()
        cli_local.close()
        raise

    e = args.escala
    def rango(lo, hi):
        return random.randint(max(1, round(lo * e)), max(1, round(hi * e)))

    total = args.usuarios if args.usuarios is not None else rango(100000, 200000)
    mods = {s: rango(*MODERADORES[s]) for s in SERVICIOS}
    conds = {s: rango(*CONDUCTORES[s]) for s in SERVICIOS}
    personal = sum(mods.values()) + sum(conds.values())
    if total <= personal:
        cli.close()
        cli_local.close()
        raise SystemExit("El total de --usuarios debe superar la cantidad de moderadores y conductores.")
    password = PASSWORD
    if not password:
        password = getpass.getpass("Clave temporal para las cuentas de prueba (no se mostrar?): ")
        confirmacion = getpass.getpass("Repite la clave temporal: ")
        if not password or password != confirmacion:
            cli.close()
            cli_local.close()
            raise SystemExit("Las claves no coinciden o est?n vac?as; no se escribi? ning?n dato.")
    hash_pw = bcrypt.hashpw(password.encode(), bcrypt.gensalt(10)).decode().replace("$2b$", "$2a$", 1)

    print("Destino Atlas:", urlsplit(uri_destino).hostname)
    print(f"Mongo local: {urlsplit(uri_local).hostname} | operadores={len(operadores)} | paquetes={len(paquetes)}")
    print(f"Escala: {e * 100:.0f}% | usuarios: {total} | moderadores: {mods} | conductores: {conds}")
    print("Habitos sinteticos: 45% frecuentes (recarga cada 110-150 dias), "
          "35% regulares (175-235 dias), 20% ocasionales (260-360 dias).")
    print("El habito compartido influye en recargas y viajes; no se guarda como variable del modelo.")
    print("Se eliminar?n estas bases de Atlas antes de generar:")
    bases_borrar = [f"digitrans_{s}" for s in SUFIJOS_BASE]
    bases_borrar += [f"{PREFIJO_PARCIAL}_{s}" for s in SUFIJOS_BASE]
    for nombre in bases_borrar:
        print("  -", nombre)
    frase = "BORRAR digitrans Y digitrans_rebuild"
    if input(f"Escribe exactamente {frase!r} para continuar: ").strip() != frase:
        cli.close()
        cli_local.close()
        raise SystemExit("Cancelado: no se borr? ninguna base.")

    print("Eliminando bases indicadas en Atlas...")
    for nombre in bases_borrar:
        cli.drop_database(nombre)
    print("Bases eliminadas. Restaurando cat?logos y cuentas preservadas...")
    restaurar_datos_esenciales(cli, datos_esenciales)

    acceso = cli["digitrans_acceso"]
    transporte = cli["digitrans_transporte"]
    billetera = cli["digitrans_billetera"]
    recargas = cli["digitrans_recargas"]
    print(f"Total de usuarios: {total}")
    print(f"Moderadores: {mods} | Conductores: {conds} | Pasajeros: {total - personal}")
    print(f"Operadores locales: {[o['nombre'] for o in operadores]} | Paquetes locales: {len(paquetes)}")

    u = Unicos()
    print("Creando buses...")
    buses_docs, buses_by_service = [], {}
    for srv, n in BUSES.items():
        n = max(1, round(n * e))
        lista = []
        for _ in range(n):
            bid = ObjectId()
            placa = u.nuevo("placa", lambda: random.choice(string.ascii_uppercase) + "-" + digitos(3))
            buses_docs.append({"_id": bid, "placa": placa, "tipo": random.choice(["TRONCAL", "ALIMENTADOR"]),
                               "sistema_transporte": srv,
                               "rutas_disponibles": random.sample(RUTAS[srv], min(random.randint(3, 10), len(RUTAS[srv]))),
                               "activo": True, "_class": CLASE_BUS, "datoPrueba": True})
            lista.append((str(bid), placa))
        buses_by_service[srv] = lista
    insertar(transporte["buses"], buses_docs)
    print(f"Buses insertados: {len(buses_docs)}")
    del buses_docs

    perfiles = []
    for s, n in mods.items():
        perfiles += [("MODERATOR", s)] * n
    for s, n in conds.items():
        perfiles += [("CONDUCTOR", s)] * n
    perfiles += [("USER", random.choice(SERVICIOS)) for _ in range(total - len(perfiles))]
    random.shuffle(perfiles)

    contadores = {"acc": 0, "usuario_transporte": 0, "perfil": 0, "tx": 0, "bil": 0, "verif": 0, "lin": 0, "rec": 0, "hor": 0}
    for inicio in range(0, len(perfiles), LOTE):
        salida = {"acc": [], "usuario_transporte": [], "perfil": [], "tx": [], "bil": [], "verif": [], "lin": [], "rec": [], "hor": []}
        for rol, servicio in perfiles[inicio:inicio + LOTE]:
            crear_usuario(rol, servicio, u, hash_pw, paquetes, operadores, buses_by_service, salida)

        insertar(acceso["usuarios"], salida["acc"])
        insertar(transporte["usuarios"], salida["usuario_transporte"])
        insertar(transporte["perfiles_transporte"], salida["perfil"])
        insertar(transporte["transacciones"], salida["tx"])
        insertar(transporte["horarios_conductor"], salida["hor"])
        insertar(billetera["billeteras_universales"], salida["bil"])
        insertar(billetera["verificaciones_tarjeta"], salida["verif"])
        insertar(recargas["lineas_movil"], salida["lin"])
        insertar(recargas["recargas_movil"], salida["rec"])

        for k in contadores:
            contadores[k] += len(salida[k])
        for k in salida:
            salida[k].clear()
        hechos = min(inicio + LOTE, len(perfiles))
        print(f"Progreso: {hechos}/{len(perfiles)} usuarios ({hechos * 100 // len(perfiles)} %)")

    print("Listo.")
    print(f"Usuarios Acceso: {contadores['acc']} | Usuarios espejo Transporte: {contadores['usuario_transporte']} | Perfiles transporte: {contadores['perfil']} | "
          f"Transacciones transporte: {contadores['tx']} | Recargas moviles: {contadores['rec']} | "
          f"Lineas: {contadores['lin']} | Billeteras: {contadores['bil']} | Horarios: {contadores['hor']}")


if __name__ == "__main__":
    main()

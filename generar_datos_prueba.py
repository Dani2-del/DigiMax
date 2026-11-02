"""
Genera datos de prueba para Digitrans, por lotes.
Requisitos:  python -m pip install pymongo bcrypt
Variables:
  MONGO_URI    destino (local o Atlas). Por defecto mongodb://localhost:27017
  MONGO_LOCAL  origen del catalogo si el destino no lo tiene. Por defecto mongodb://localhost:27017
Uso:         python generar_datos_prueba.py            (pide confirmacion)
             python generar_datos_prueba.py --borrar   (borra antes los datos con datoPrueba=true)
"""
import argparse
import base64
import datetime as dt
import os
import random
import string
import unicodedata
import uuid

import bcrypt
from bson import ObjectId, Decimal128, Int64
from pymongo import MongoClient

URI = os.environ.get("MONGO_URI", "mongodb://localhost:27017")
LOCAL_URI = os.environ.get("MONGO_LOCAL", "mongodb://localhost:27017")
PASSWORD = "DigiMax1234"
FECHA_INI = dt.datetime(2016, 1, 1)
FECHA_FIN = dt.datetime(2026, 10, 29, 23, 59)
LOTE = 1000  # usuarios por lote: controla el uso de memoria

SERVICIOS = ["TRANSCARIBE", "TRANSMILENIO", "TRANSMETRO"]
NOMBRE_SERVICIO = {"TRANSCARIBE": "Transcaribe", "TRANSMILENIO": "TransMilenio", "TRANSMETRO": "Transmetro"}
CONDUCTORES = {"TRANSMILENIO": (10000, 12000), "TRANSCARIBE": (400, 500), "TRANSMETRO": (400, 500)}
MODERADORES = {"TRANSMILENIO": (500, 600), "TRANSCARIBE": (25, 50), "TRANSMETRO": (25, 50)}
BUSES = {"TRANSMILENIO": 12573, "TRANSCARIBE": 367, "TRANSMETRO": 364}
RUTAS = ["T100E", "T101", "T102", "T103", "X101", "X102", "A101", "A102", "A103", "C001", "C016", "C017"]
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


def a_utc(hora_local):
    """Colombia es UTC-5 todo el año: se suma 5 horas para guardar en UTC."""
    return hora_local + dt.timedelta(hours=5)


def fecha_aleatoria(desde, hasta):
    segundos = int((hasta - desde).total_seconds())
    return desde + dt.timedelta(seconds=random.randint(0, segundos))


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


def asegurar_catalogo(cli_destino):
    """Si el destino no tiene operadores o paquetes, los copia desde la base local."""
    local = MongoClient(LOCAL_URI)
    for col in ["operadores_movil", "paquetes_movil"]:
        destino = cli_destino[PREFIJO + "_recargas"][col]
        if destino.count_documents({}) == 0:
            docs = list(local["digitrans_recargas"][col].find({}))
            if docs:
                destino.insert_many(docs)
            print(f"Catalogo copiado al destino: {col} ({len(docs)} documentos)")


def crear_usuario(rol, servicio, u, hash_pw, paquetes, operadores, buses_by_service, salida):
    """Genera todos los documentos de un usuario y los agrega a las listas de 'salida'."""
    svc_p = random.choice(SERVICIOS) if rol in ("USER", "MODERATOR") else servicio
    uid = ObjectId()
    uid_s = str(uid)
    nombre = u.nuevo("nombre", generar_nombre)
    correo = u.nuevo("correo", lambda: correo_realista(nombre))
    registro = fecha_aleatoria(FECHA_INI, FECHA_FIN - dt.timedelta(days=30))

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
    for f in sorted(fecha_aleatoria(registro, FECHA_FIN) for _ in range(random.randint(0, 15))):
        if saldo_t > 0 and random.random() < 0.5:
            saldo_t -= PASAJE_TARJETA
            salida["tx"].append({"_id": ObjectId(), "monto": PASAJE_TARJETA, "fecha": f,
                                 "tipo": f"PASAJE [TARJETA TRANSPORTE] - Pasaje {NOMBRE_SERVICIO[svc_p]}: ruta {random.choice(RUTAS)}",
                                 "tarjeta_transcaribe": f"******{ultimos}", "operacion_id": str(uuid.uuid4()),
                                 "sistema_transporte": svc_p, "cantidad_pasajes": 1,
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
                                 "_class": CLASE_TRANSACCION, "datoPrueba": True})
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
    for _ in range(random.randint(0, 30)):
        eventos.append((fecha_aleatoria(registro, FECHA_FIN), "PASAJE" if random.random() < 0.5 else "ABONO", None))
    for _ in range(random.randint(0, 10)):
        lid_s, telf, op = random.choice(lineas)
        candidatos = [p for p in paquetes if p["operadorId"] == str(op["_id"])] or paquetes
        eventos.append((fecha_aleatoria(registro, FECHA_FIN), "RECARGA", (lid_s, telf, op, random.choice(candidatos))))
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
                                     "tipo": f"PASAJE [TARJETA UNIVERSAL] - Pasaje {NOMBRE_SERVICIO[svc_p]}: ruta {random.choice(RUTAS)}",
                                     "tarjeta_transcaribe": "UNIVERSAL", "operacion_id": op_id,
                                     "sistema_transporte": svc_p, "_class": CLASE_TRANSACCION, "datoPrueba": True})
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
                                  "ruta": random.choice(RUTAS), "sentido": random.choice(["IDA", "VUELTA"]),
                                  "fecha": a_utc(dia_local), "hora_inicio": a_utc(inicio_local),
                                  "hora_fin": a_utc(fin_local), "estado": "FINALIZADA",
                                  "_class": CLASE_HORARIO, "datoPrueba": True})


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--borrar", action="store_true", help="borra antes los datos con datoPrueba=true")
    ap.add_argument("--escala", type=float, default=0.20, help="proporcion de los rangos (0.20 = 20 %%)")
    ap.add_argument("--prefijo", default="digitrans", help="prefijo de las bases (ej. eval_digitrans)")
    ap.add_argument("--semilla", type=int, default=None, help="semilla aleatoria para reproducir o generar un conjunto distinto")
    args = ap.parse_args()
    global PREFIJO
    PREFIJO = args.prefijo
    if args.semilla is not None:
        random.seed(args.semilla)
    print(f"Prefijo de bases: {PREFIJO} | semilla: {args.semilla}")
    e = args.escala
    def rango(lo, hi):
        return random.randint(max(1, round(lo * e)), max(1, round(hi * e)))

    cli = MongoClient(URI)
    acceso = cli[PREFIJO + "_acceso"]
    transporte = cli[PREFIJO + "_transporte"]
    billetera = cli[PREFIJO + "_billetera"]
    recargas = cli[PREFIJO + "_recargas"]

    asegurar_catalogo(cli)

    operadores = list(recargas["operadores_movil"].find({}, {"nombre": 1}))
    paquetes = list(recargas["paquetes_movil"].find({}, {"nombre": 1, "operadorId": 1, "precio": 1}))
    if not operadores or not paquetes:
        raise SystemExit("Falta el catalogo: el destino necesita operadores_movil y paquetes_movil.")

    total = rango(100000, 200000)
    mods = {s: rango(*MODERADORES[s]) for s in SERVICIOS}
    conds = {s: rango(*CONDUCTORES[s]) for s in SERVICIOS}
    personal = sum(mods.values()) + sum(conds.values())
    print(f"Destino: {URI.split('@')[-1]}")
    print(f"Escala: {e * 100:.0f} % de los rangos originales")
    print(f"Total de usuarios: {total}")
    print(f"Moderadores: {mods}  |  Conductores: {conds}  |  Pasajeros: {total - personal}")
    print(f"Operadores: {[o['nombre'] for o in operadores]} | Paquetes: {len(paquetes)}")
    if input("Escribe SI para continuar: ").strip() != "SI":
        raise SystemExit("Cancelado.")

    if args.borrar:
        for col in [acceso["usuarios"], transporte["perfiles_transporte"], transporte["transacciones"],
                    transporte["buses"], transporte["horarios_conductor"], billetera["billeteras_universales"],
                    billetera["verificaciones_tarjeta"], recargas["lineas_movil"], recargas["recargas_movil"]]:
            col.delete_many({"datoPrueba": True})
        print("Datos previos de prueba borrados.")

    hash_pw = bcrypt.hashpw(PASSWORD.encode(), bcrypt.gensalt(10)).decode().replace("$2b$", "$2a$", 1)
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
                               "rutas_disponibles": random.sample(RUTAS, random.randint(3, 10)),
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

    contadores = {"acc": 0, "perfil": 0, "tx": 0, "bil": 0, "verif": 0, "lin": 0, "rec": 0, "hor": 0}
    for inicio in range(0, len(perfiles), LOTE):
        salida = {"acc": [], "perfil": [], "tx": [], "bil": [], "verif": [], "lin": [], "rec": [], "hor": []}
        for rol, servicio in perfiles[inicio:inicio + LOTE]:
            crear_usuario(rol, servicio, u, hash_pw, paquetes, operadores, buses_by_service, salida)

        insertar(acceso["usuarios"], salida["acc"])
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
    print(f"Usuarios: {contadores['acc']} | Perfiles transporte: {contadores['perfil']} | "
          f"Transacciones transporte: {contadores['tx']} | Recargas moviles: {contadores['rec']} | "
          f"Lineas: {contadores['lin']} | Billeteras: {contadores['bil']} | Horarios: {contadores['hor']}")


if __name__ == "__main__":
    main()

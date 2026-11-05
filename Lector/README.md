# Módulo Lector RFID

Servicio local para la demostración de Digitrans. Mantiene el inventario de tarjetas físicas y su perfil propietario en `digitrans_lector`. Billetera puede pedir a Lector que asigne una tarjeta libre; al leerla, Lector solicita a Billetera que debite la tarifa configurada. Billetera no guarda UIDs ni lógica RFID.

## Requisitos

- Java 17 o superior.
- MongoDB local, o una URI de MongoDB configurada en `LECTOR_MONGODB_URI`.
- Billetera iniciada en `http://localhost:8082`.
- La misma variable `LECTOR_API_KEY` configurada en las terminales de Billetera, Transporte y Lector.

## Iniciar

Configura el mismo secreto `LECTOR_API_KEY` en Billetera, Transporte y Lector. Por ejemplo, usa una clave larga generada para tu entorno local. Arranca los servicios en terminales separadas:

```powershell
cd C:\Users\DDRAO\source\DigiMAX\Billetera
$env:LECTOR_API_KEY = "pon-aqui-una-clave-larga-compartida"
.\gradlew bootRun
```

Terminal de Transporte:

```powershell
cd C:\Users\DDRAO\source\DigiMAX\Transporte
$env:LECTOR_API_KEY = "pon-aqui-una-clave-larga-compartida"
.\mvnw.cmd spring-boot:run
```

```powershell
cd C:\Users\DDRAO\source\DigiMAX\Lector
$env:LECTOR_API_KEY = "pon-aqui-una-clave-larga-compartida"
.\gradlew bootRun
```

Abre `http://localhost:8086`. Por defecto el servicio solo escucha en `127.0.0.1`, adecuado para el lector conectado a este computador.

## Demostración

1. En la pantalla local de Lector, registra los UID de las tarjetas físicas en el inventario disponible. El lector JT308 puede escribir el UID en el campo enfocado; también se puede escribir manualmente.
2. La persona inicia sesión en Billetera y elige “Registrar una tarjeta RFID disponible”. Billetera solicita a Lector que reclame atómicamente una tarjeta libre para su ID de Acceso.
3. El perfil RFID queda en la base exclusiva de Lector; no se cambia el saldo ni el modelo de datos de Billetera.
4. En Transporte, elige el modo “Tarjeta física RFID”, enfoca el campo y acerca la tarjeta. Transporte envía el UID y el sistema seleccionado a Lector.
5. Lector valida que el UID pertenezca al usuario autenticado y solicita a Billetera el débito del pasaje. Las tarifas configuradas son Transcaribe COP 3.900, TransMilenio COP 3.550 y Transmetro COP 3.700.
6. La persona puede desvincularla desde Billetera. Lector conserva el UID en inventario y lo deja disponible para otro usuario.

El lector puede comportarse como teclado USB y escribir el UID en el campo enfocado; esto debe confirmarse al conectar el JT308. La ruta `/api/rfid/lecturas` permite probar la misma operación manualmente.

## Variables

| Variable | Valor por defecto | Uso |
| --- | --- | --- |
| `SERVER_PORT` | `8086` | Puerto HTTP de Lector |
| `LECTOR_BIND_ADDRESS` | `127.0.0.1` | Interfaz de red de escucha |
| `LECTOR_MONGODB_URI` | `mongodb://localhost:27017/digitrans_lector` | Base de datos exclusiva de Lector |
| `BILLETERA_BASE_URL` | `http://localhost:8082` | Dirección de Billetera |
| `LECTOR_API_KEY` | Vacía | Clave compartida para llamadas internas entre Billetera, Transporte y Lector |
| `LECTOR_FARE_TRANSCARIBE` | `3900` | Tarifa Transcaribe en COP |
| `LECTOR_FARE_TRANSMILENIO` | `3550` | Tarifa TransMilenio en COP |
| `LECTOR_FARE_TRANSMETRO` | `3700` | Tarifa Transmetro en COP |

La pantalla de vinculación está pensada para uso local de demostración y no implementa inicio de sesión. No publiques el puerto en una red compartida. Las tarjetas EM4100/EM4102 usan un UID que puede copiarse, por lo que no deben tratarse como credenciales seguras para dinero real.

Los archivos Java que están directamente en la raíz de `Lector` son el borrador recibido inicialmente; Gradle compila el módulo nuevo ubicado en `src/main/java`.

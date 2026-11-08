# DigiMAX

**Digitrans** es una plataforma universitaria que reúne en una sola cuenta servicios de transporte, billetera universal y recargas móviles. El proyecto está organizado como un monorepo de aplicaciones Spring Boot independientes que se comunican mediante APIs HTTP. Orquestador coordina el inicio y la supervisión local de los servicios.

## Proyecto académico

Desarrollado en la **Fundación Universitaria Tecnológico Comfenalco** por:

- Danier Alejandro Ospino Murillo
- Juan Sebastián Correa Ramos
- Andrés Sebastián Garzón Ramírez
- Elías Daniel Blanco Meza
- Andrés Felipe Pérez Ferrer

## Módulos

| Módulo | Puerto local | Responsabilidad |
| --- | ---: | --- |
| `Acceso` | 8080 | Registro, inicio de sesión, identidad central, JWT, inicio de sesión con Google y consola global con usuarios, transacciones, analítica, chats y reportes Excel. |
| `Transporte` | 8081 | Experiencia de transporte público, perfiles SITM, tarjetas de transporte, rutas y funciones administrativas. |
| `Billetera` | 8082 | Tarjeta universal, saldo compartido y cargas simuladas mediante Pagos, con movimientos y herramientas de moderación. |
| `RecargasMovil` | 8083 | Catálogos, líneas y recargas simuladas; inicia pagos en Pagos y conserva su resultado en el historial. |
| `Correo` | 8084 | API interna para el envío de correo mediante SMTP. |
| `Pagos` | 8085 | API de prueba para iniciar y consultar pagos simulados en memoria; RecargasMovil la usa como medio de pago. |
| `Lector` | 8086 | Registra tarjetas Digitrans RFID en inventario y consulta tarjetas vinculadas e historial de titulares. |
| `Orquestador` | 8087 | Panel local para supervisar, detener y reiniciar los demás módulos; escribe logs en `Orquestador/logs/`. |

Acceso centraliza la identidad. Los demás módulos validan la sesión mediante el token JWT; Billetera comparte el saldo con Transporte. Billetera y RecargasMovil se comunican con Pagos por HTTP y guardan el ID y estado del pago en sus movimientos. Las cargas y recargas son **simuladas**: no se conectan a medios de pago ni a operadores móviles reales; el saldo de Billetera solo aumenta cuando Pagos informa `APROBADO`, mientras RecargasMovil no descuenta saldo de Billetera.

## Requisitos

- Windows PowerShell
- Java 17
- MongoDB local, disponible en `mongodb://localhost:27017`
- Credenciales OAuth de Google (Google OAuth está habilitado en Acceso)
- Credenciales SMTP y una clave compartida para enviar correos

Los módulos incluyen sus wrappers Gradle o Maven; no es necesario instalar Gradle o Maven globalmente para los comandos de esta guía.

Los módulos Java están configurados con Java 17 como versión objetivo. Orquestador está configurado para iniciar los módulos de DigiMAX en Windows.

## Configuración local

Configura estas variables de entorno para tu usuario o para las terminales donde ejecutarás los servicios. Usa valores propios y no subas credenciales al repositorio.

| Variable | Uso |
| --- | --- |
| `APP_JWT_SECRET` | Clave de firma JWT de Acceso; los demás módulos validan la identidad con Acceso. Debe tener al menos 32 bytes. |
| `MAIL_SERVICE_API_KEY` | Clave compartida entre Correo, Acceso, Transporte y Billetera para proteger la API interna de correo. |
| `GOOGLE_CLIENT_ID` | ID OAuth del cliente de Google usado por Acceso. |
| `GOOGLE_CLIENT_SECRET` | Secreto OAuth del cliente de Google usado por Acceso. |
| `SMTP_USERNAME` | Cuenta remitente SMTP configurada en Correo. |
| `SMTP_PASSWORD` | Contraseña o contraseña de aplicación SMTP. |
| `SMTP_FROM` | Dirección visible del remitente; por defecto usa `SMTP_USERNAME`. |
| `LECTOR_API_KEY` | Clave compartida por Lector, Billetera y Transporte para proteger las llamadas internas de RFID. |
| `ORQUESTADOR_API_TOKEN` | Token requerido por la API de control de Orquestador. |

En Google Cloud Console, registra este URI de redirección para el cliente OAuth local:

```text
http://localhost:8080/login/oauth2/code/google
```

`APP_JWT_SECRET` es necesaria para iniciar Acceso. Como Google OAuth está habilitado por defecto, también debes configurar `GOOGLE_CLIENT_ID` y `GOOGLE_CLIENT_SECRET`. `MAIL_SERVICE_API_KEY` debe configurarse con el mismo valor en Correo, Acceso, Transporte y Billetera. El script para iniciar todo el proyecto comprueba las variables JWT y de correo; configura también las credenciales de Google antes de usarlo. Las credenciales SMTP son necesarias para entregar correos; sin ellas, la integración de correo no podrá enviar mensajes. Billetera requiere que Correo esté disponible para enviar los códigos de activación, congelación, descongelación y desactivación de tarjetas.

Para desarrollo local, `APP_COOKIE_SECURE` usa `false`. En un entorno HTTPS de producción debe configurarse como `true`. No reutilices claves locales en producción.

### Bases de datos

Cada módulo persistente usa una base MongoDB independiente:

| Módulo | Variable opcional | URI predeterminada |
| --- | --- | --- |
| `Acceso` | `ACCESO_MONGODB_URI` | `mongodb://localhost:27017/digitrans_acceso` |
| `Transporte` | `TRANSPORTE_MONGODB_URI` | `mongodb://localhost:27017/digitrans_transporte` |
| `Billetera` | `BILLETERA_MONGODB_URI` | `mongodb://localhost:27017/digitrans_billetera` |
| `RecargasMovil` | `RECARGAS_MONGODB_URI` | `mongodb://localhost:27017/digitrans_recargas` |
| `Lector` | `LECTOR_MONGODB_URI` | `mongodb://localhost:27017/digitrans_lector` |

MongoDB crea cada base cuando el módulo realiza su primera escritura. `Correo` no necesita base de datos. La base antigua `Digitrans_Max` no se modifica ni migra automáticamente.

Para alternar las cuatro bases MongoDB al iniciar toda la plataforma, usa uno de estos comandos desde la raíz:

```powershell
.\Iniciar-Digitrans.bat local
.\Iniciar-Digitrans.bat atlas
```

`local` conecta todos los módulos persistentes a `localhost:27017`, aunque las variables Atlas estén configuradas en Windows. `atlas` requiere que las cuatro variables `ACCESO_MONGODB_URI`, `TRANSPORTE_MONGODB_URI`, `BILLETERA_MONGODB_URI` y `RECARGAS_MONGODB_URI` estén disponibles en el proceso o en las variables de usuario. El script de configuración `configurar_mongodb_atlas.py` guarda estas últimas. El lanzador valida la configuración antes de reiniciar los servicios y les aplica el mismo perfil; no copia ni sincroniza datos entre bases.

Las direcciones de los servicios pueden personalizarse con `ACCESO_BASE_URL`, `BILLETERA_BASE_URL`, `PAGOS_BASE_URL`, `TRANSPORTE_URL`, `RECARGAS_URL` y `MAIL_SERVICE_URL`; sus valores locales predeterminados apuntan a los puertos indicados arriba.

La consola global de Acceso consulta las bases de Transporte, Billetera y Recargas para consolidar transacciones. Usa las URI de MongoDB de cada módulo indicadas en la tabla; si personalizas una base, configura la misma URI en la variable correspondiente para que la consola pueda leer sus datos. El total global consolida operaciones por ID cuando los módulos comparten la misma referencia. Los reportes Excel permiten escoger conjuntos de datos, servicios y fechas.

## Orquestador local

Orquestador usa el puerto `8087` y administra los siete módulos de aplicación. Al abrirlo solo inicia el panel; los otros servicios permanecen detenidos hasta que pulses **Iniciar todos**. El panel permite iniciar todos los servicios en orden, finalizar los servicios sin apagar Orquestador y controlar módulos uno por uno con **Reiniciar** y **Parar**. Después de iniciarlos, Orquestador consulta su estado y reinicia automáticamente los que fallen. Los registros se guardan por módulo en `Orquestador/logs/`.

Para iniciar Orquestador con las bases de datos de MongoDB Atlas, configura las variables de entorno del proyecto y ejecuta desde la raíz:

```powershell
.\Iniciar-Orquestador.ps1
```

El script comprueba las claves requeridas y las URI Atlas, activa el perfil `db-atlas` y ejecuta Orquestador. Requiere `APP_JWT_SECRET`, `MAIL_SERVICE_API_KEY`, `LECTOR_API_KEY` y las URI `ACCESO_MONGODB_URI`, `TRANSPORTE_MONGODB_URI`, `BILLETERA_MONGODB_URI` y `RECARGAS_MONGODB_URI`. También configura `LECTOR_MONGODB_URI` para la base `digitrans_lector` si no está definida. Las credenciales SMTP y OAuth de Google deben estar configuradas para las funciones que las utilizan.

El panel está en <http://localhost:8087>. La API de estado es `GET /api/estado`. La API de control también ofrece `POST /api/modulos/{nombre}/iniciar`, `/detener` y `/reiniciar`; estas rutas requieren el encabezado `X-Orquestador-Token` con el valor de `ORQUESTADOR_API_TOKEN`. El panel local usa una ruta protegida para sus acciones.

Para detener procesos que hayan quedado ocupando los puertos del proyecto, ejecuta desde la raíz:

```powershell
.\Limpiar-Puertos.ps1
```

El script muestra los procesos que escuchan en `8080`–`8087`, pide confirmación y verifica si quedaron puertos ocupados. Si detiene el puerto `8087`, también detiene Orquestador.

Como alternativa al panel de Orquestador, puedes iniciar todos los módulos en terminales separadas con el lanzador existente:

Desde la raíz del repositorio:

```powershell
.\Iniciar-Digitrans.bat
```

El script comprueba las claves JWT y de correo, revisa los puertos y abre una terminal por servicio. Si un puerto está ocupado por un proceso ajeno a DigiMAX, el script se detiene sin cerrarlo.

Para iniciar un módulo individual, entra en su carpeta y ejecuta el wrapper correspondiente:

```powershell
Set-Location .\Acceso
.\gradlew.bat bootRun
```

Otros ejemplos: ejecuta `.\gradlew.bat bootRun` desde `Billetera`, `RecargasMovil` o `Correo`; para Transporte, ejecuta `.\mvnw.cmd spring-boot:run` desde `Transporte`; para Pagos, ejecuta `.\Transporte\mvnw.cmd -f .\Pagos\pom.xml spring-boot:run` desde la raíz.

RecargasMovil inicia un pago en Pagos y deja la recarga en `PAGO_PENDIENTE`. Por ahora, Pagos aprueba automáticamente los pagos simulados después de 10 segundos; luego usa **Revisar pago** en RecargasMovil. Para probar otros resultados, puedes usar `POST http://localhost:8085/api/pagos/{idProveedor}/rechazar` o `/cancelar` antes de la aprobación automática. Solo cuando Pagos responde `APROBADO` la recarga pasa a `SIMULADA`; un pago rechazado o cancelado deja la recarga en `RECHAZADA`. Este flujo no descuenta de Billetera ni envía saldo al operador.

La carga de Billetera permite seleccionar PSE con correo y contraseña ficticios, o tarjeta de débito con número ficticio. Billetera revisa automáticamente las cargas pendientes vencidas sin depender de que el usuario deje la página abierta. Esos campos solo se validan en el navegador y no se envían ni almacenan; el sistema conserva únicamente el método elegido. Las cargas quedan pendientes hasta que Pagos las apruebe automáticamente después de 10 segundos. Luego pulsa **Revisar pago** en Billetera. Para probar otros resultados, puedes llamar a `POST http://localhost:8085/api/pagos/{idProveedor}/rechazar` o `/cancelar` antes de la aprobación automática. El saldo se acredita de forma atómica una sola vez cuando el pago queda `APROBADO`; el historial conserva método, ID y resultado. No ingreses contraseñas PSE ni datos de tarjeta. Reintentar una misma carga usa la referencia original y no crea un segundo pago.

Pagos también expone `POST http://localhost:8085/api/pagos` para iniciar pagos y `GET http://localhost:8085/api/pagos/{idProveedor}` para consultar su estado. Reutilizar la misma referencia con los mismos datos devuelve el pago original; usarla con datos distintos responde `409`. Las acciones manuales de aprobar, rechazar y cancelar son para pruebas locales, no deben exponerse sin protección en producción. La demora automática se configura con `pagos.simulacion.aprobacion-automatica-segundos` en `Pagos/src/main/resources/application.yml`. Los pagos se guardan en memoria y se pierden al reiniciar Pagos.

Con todos los módulos activos, la página principal está en <http://localhost:8080>.

## Pruebas

Ejecuta las pruebas desde la raíz del repositorio con PowerShell:

```powershell
.\Acceso\gradlew.bat -p Acceso test
.\Billetera\gradlew.bat -p Billetera test
.\RecargasMovil\gradlew.bat -p RecargasMovil test
.\Correo\gradlew.bat -p Correo test
.\Lector\gradlew.bat -p Lector test
.\Orquestador\gradlew.bat -p Orquestador test
.\Transporte\mvnw.cmd -f Transporte\pom.xml test
.\Transporte\mvnw.cmd -f Pagos\pom.xml test
```

Para correr una clase específica en Gradle, añade `--tests nombre.completo.DeLaPrueba` antes de `test`, por ejemplo:

```powershell
.\Billetera\gradlew.bat -p Billetera test --tests com.Servicio.Billetera.service.BilleteraServiceTest
```

## Estructura

```text
.
|-- Acceso/
|-- Billetera/
|-- Correo/
|-- Lector/
|-- Orquestador/
|-- Pagos/
|-- RecargasMovil/
|-- Transporte/
|-- Iniciar-Digitrans.bat
|-- Iniciar-Digitrans.ps1
|-- Iniciar-Orquestador.ps1
|-- Limpiar-Puertos.ps1
```
Los secretos y configuraciones locales deben mantenerse fuera del control de versiones. El archivo `.gitignore` excluye archivos `.env`, configuraciones locales y artefactos de compilación.

# DigiMAX

**Digitrans** es una plataforma universitaria que reúne en una sola cuenta servicios de transporte, billetera universal y recargas móviles. El proyecto está organizado como un monorepo de aplicaciones Spring Boot independientes que se comunican entre sí mediante APIs HTTP.

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
| `Billetera` | 8082 | Tarjeta universal, saldo compartido, movimientos, ajustes de diseño y seguridad con códigos de verificación por correo, y herramientas de moderación. |
| `RecargasMovil` | 8083 | Catálogos, líneas y compras simuladas de paquetes y recargas móviles. |
| `Correo` | 8084 | API interna para el envío de correo mediante SMTP. |

Acceso centraliza la identidad. Los demás módulos validan la sesión mediante el token JWT; Billetera comparte el saldo con Transporte y RecargasMovil. Las operaciones de carga, pagos y recargas son **simuladas**: no se conectan a medios de pago ni a operadores móviles reales.

## Requisitos

- Windows PowerShell
- Java 17
- MongoDB local, disponible en `mongodb://localhost:27017`
- Credenciales OAuth de Google (Google OAuth está habilitado en Acceso)
- Credenciales SMTP y una clave compartida para enviar correos

Los módulos incluyen sus wrappers Gradle o Maven; no es necesario instalar Gradle o Maven globalmente para los comandos de esta guía.

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

MongoDB crea cada base cuando el módulo realiza su primera escritura. `Correo` no necesita base de datos. La base antigua `Digitrans_Max` no se modifica ni migra automáticamente.

Las direcciones de los servicios pueden personalizarse con `ACCESO_BASE_URL`, `BILLETERA_BASE_URL`, `TRANSPORTE_URL`, `RECARGAS_URL` y `MAIL_SERVICE_URL`; sus valores locales predeterminados apuntan a los puertos indicados arriba.

La consola global de Acceso consulta las bases de Transporte, Billetera y Recargas para consolidar transacciones. Usa las URI de MongoDB de cada módulo indicadas en la tabla; si personalizas una base, configura la misma URI en la variable correspondiente para que la consola pueda leer sus datos. El total global consolida operaciones por ID cuando los módulos comparten la misma referencia. Los reportes Excel permiten escoger conjuntos de datos, servicios y fechas.

## Iniciar todos los módulos en Windows

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

Otros ejemplos: ejecuta `.\gradlew.bat bootRun` desde `Billetera`, `RecargasMovil` o `Correo`; para Transporte, ejecuta `.\mvnw.cmd spring-boot:run` desde `Transporte`.

Con todos los módulos activos, la página principal está en <http://localhost:8080>.

## Pruebas

Ejecuta las pruebas desde la raíz del repositorio con PowerShell:

```powershell
.\Acceso\gradlew.bat -p Acceso test
.\Billetera\gradlew.bat -p Billetera test
.\RecargasMovil\gradlew.bat -p RecargasMovil test
.\Correo\gradlew.bat -p Correo test
.\Transporte\mvnw.cmd -f Transporte\pom.xml test
```

Para correr una clase específica en Gradle, añade `--tests nombre.completo.DeLaPrueba` antes de `test`, por ejemplo:

```powershell
.\Billetera\gradlew.bat -p Billetera test --tests com.Servicio.Billetera.service.BilleteraServiceTest
```

## Estructura

```text
.
├── Acceso/
├── Billetera/
├── Correo/
├── RecargasMovil/
├── Transporte/
├── Iniciar-Digitrans.bat
└── Iniciar-Digitrans.ps1
```

Los secretos y configuraciones locales deben mantenerse fuera del control de versiones. El archivo `.gitignore` excluye archivos `.env`, configuraciones locales y artefactos de compilación.

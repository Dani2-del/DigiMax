# Digitrans - Recargas en línea de Transcaribe

Aplicación de transporte que permite a los usuarios gestionar tarjetas y recargas de transporte, consultar rutas y acceder a herramientas administrativas y operativas. El registro, el inicio de sesión y la recuperación de cuentas se realizan ahora desde el servicio central `Acceso`.

## Información académica

- **Carrera:** Tecnología en Desarrollo de Software
- **Semestre:** VI
- **Periodo:** 2026-2

### Integrantes

1. Andres Felipe Pérez Ferrer
2. Elías Daniel Blanco Meza
3. Andrés Sebastián Garzón Ramírez
4. Juan Sebastián Correa Ramos
5. Danier Alejandro Ospino Murillo

## Funcionalidades

### Pasajeros

- Autenticación centralizada en `Acceso`; este módulo conserva los datos y operaciones propios del transporte.
- Gestión de tarjetas Transcaribe y recarga de saldo en línea.
- Consulta del saldo y del historial de recargas y transacciones.
- Consulta del mapa, las rutas y sus recorridos.
- Gestión de rutas favoritas y notificaciones relacionadas.
- Recepción de avisos administrativos.

### Conductores

- Panel con información de rutas y horarios asignados.
- Inicio y finalización de recorridos y reporte de novedades.
- Acceso al chat operativo compartido con los administradores.

### Administración

- Gestión de usuarios, buses, rutas y horarios de conductores.
- Consulta de transacciones y generación de reportes.
- Publicación de avisos con destinatarios por tipo de cuenta o selección individual.
- Chat operativo con envío de imágenes, emojis y edición de mensajes propios durante los primeros 15 minutos.

## Tecnologías

- Java 21 y Spring Boot 3.5.
- Spring MVC y Thymeleaf para la aplicación web.
- Spring Security, JWT y OAuth 2.0 para autenticación y control de acceso.
- MongoDB para persistencia.
- El servicio `Correo` centraliza el envío SMTP de notificaciones.
- Apache POI y Apache PDFBox para generación de reportes y documentos.
- HTML, CSS y JavaScript para la interfaz.

## Requisitos

- JDK 21.
- MongoDB accesible desde el entorno donde se ejecuta la aplicación.
- El módulo `Correo` y su configuración SMTP.
- Maven Wrapper incluido en el repositorio.

## Configuración y ejecución local

Ejecuta MongoDB local, `Correo` (puerto 8084) y `Acceso` (puerto 8080) antes de iniciar este módulo. Por defecto, este servicio usa la base local vacía `digitrans_transporte`; Acceso usa por separado `digitrans_acceso`. Puedes cambiar las URI locales mediante `TRANSPORTE_MONGODB_URI` y `ACCESO_MONGODB_URI`, respectivamente; no uses aquí la URI de Atlas. Configura `MAIL_SERVICE_API_KEY` con el mismo valor aleatorio en Correo, Acceso y Transporte; configura `SMTP_USERNAME`, `SMTP_PASSWORD` y, opcionalmente, `SMTP_FROM` únicamente en Correo. Define secretos en variables de entorno, nunca en `application.properties`. Este módulo se ejecuta en el puerto 8081 y consulta Acceso para validar el JWT en cada solicitud autenticada. Las credenciales de registro y Google OAuth pertenecen a Acceso.

En PowerShell, asigna los valores correspondientes en el entorno local y ejecuta:

```powershell
.\mvnw.cmd spring-boot:run
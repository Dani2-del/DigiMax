# Orquestador DigiMAX

Orquestador presenta el panel local de control en el puerto `8087`. Al iniciar la aplicación, únicamente se inicia Orquestador; los otros siete módulos quedan detenidos hasta que se solicite su inicio desde el panel.

## Iniciar

Desde la carpeta `Orquestador`, ejecuta:

```powershell
.\gradlew.bat --no-daemon bootRun
```

También puedes iniciar el módulo y preparar el perfil Atlas desde la raíz del monorepo con `..\Iniciar-Orquestador.ps1`. Abre <http://localhost:8087>.

## Panel

- **Iniciar todos** inicia los módulos en el orden configurado y espera a que cada uno responda antes de continuar.
- **Finalizar todos los servicios** detiene los módulos administrados; Orquestador permanece activo.
- **Reiniciar** y **Parar** permiten controlar cada módulo por separado.
- La supervisión periódica reinicia automáticamente servicios que fallen. Un módulo detenido manualmente permanece detenido.

Los módulos configurados son Acceso (8080), Transporte (8081), Billetera (8082), RecargasMovil (8083), Correo (8084), Pagos (8085) y Lector (8086). Sus logs se guardan en `Orquestador/logs/`.

## API

- `GET /api/estado`
- `POST /api/modulos/{nombre}/iniciar`
- `POST /api/modulos/{nombre}/detener`
- `POST /api/modulos/{nombre}/reiniciar`

Las rutas de control de API requieren el encabezado `X-Orquestador-Token`, cuyo valor se configura mediante `ORQUESTADOR_API_TOKEN`. El panel local usa rutas protegidas para las acciones solicitadas desde el mismo origen.

## Configuración

Orquestador usa Java 17 como versión objetivo. La raíz del monorepo se detecta al iniciar. Configura las variables de entorno que requieren los módulos, incluida la conexión a MongoDB Atlas, antes de iniciarlos. El panel comprueba la respuesta HTTP de cada módulo, lo cual confirma que el servidor web responde, pero no valida todas sus dependencias externas.

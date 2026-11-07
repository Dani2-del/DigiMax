# Orquestador DigiMax

Supervises the seven DigiMax modules: starts them in order, restarts them after failures, and exposes a status API bound to the local interface. Port: 8087.

## Start

Run the following commands from this folder after setting the module environment variables required by your setup.

## Endpoints

- `GET /api/estado`
- `POST /api/modulos/{nombre}/iniciar`
- `POST /api/modulos/{nombre}/detener`
- `POST /api/modulos/{nombre}/reiniciar`

Module logs are written to `Orquestador/logs/`.

Startup waits for an HTTP response from each module before continuing, up to `orquestador.arranque-timeout-segundos`. The check requests `/`: this confirms the HTTP server responds, but does not check its external dependencies.

The control API binds to `127.0.0.1`. The three `POST` actions also require the `X-Orquestador-Token` header. Set `ORQUESTADOR_API_TOKEN` before launching the Orquestador; without it, manual start/stop/restart actions return `503`.

Example in PowerShell:

```powershell
$env:ORQUESTADOR_API_TOKEN = [guid]::NewGuid().ToString('N') + [guid]::NewGuid().ToString('N')
.\gradlew.bat --no-daemon bootRun
```

Send that same value in `X-Orquestador-Token` when calling a control endpoint.

For example, restart Billetera with:

```powershell
Invoke-RestMethod -Method Post `
  -Uri http://127.0.0.1:8087/api/modulos/Billetera/reiniciar `
  -Headers @{ 'X-Orquestador-Token' = $env:ORQUESTADOR_API_TOKEN }
```

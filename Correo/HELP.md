# Correo

`Correo` is Digitrans' internal outbound email service. `Acceso` and the transportation application send it authenticated requests; SMTP credentials and the actual SMTP connection live only here. Incoming Gmail messages are not read by this service.

## Local startup

Generate one shared API key and store it in the user environment. Acceso and Transporte use the same value to authenticate requests to Correo:

```powershell
$bytes = New-Object byte[] 32
$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$rng.GetBytes($bytes)
$env:MAIL_SERVICE_API_KEY = [Convert]::ToBase64String($bytes)
[Environment]::SetEnvironmentVariable('MAIL_SERVICE_API_KEY', $env:MAIL_SERVICE_API_KEY, 'User')
$rng.Dispose()
```

Start Correo in its own PowerShell terminal:

```powershell
Set-Location 'C:\Users\DDRAO\source\DigiMAX\Correo'
$env:MAIL_SERVICE_API_KEY = '<same-random-secret-in-all-calling-services>'
$env:SMTP_USERNAME = '<smtp-account>'
$env:SMTP_PASSWORD = '<smtp-app-password>'
.\gradlew.bat bootRun
```

Use exactly the same generated `MAIL_SERVICE_API_KEY` value in Correo, Acceso, and Transporte. The launcher reads `MAIL_SERVICE_API_KEY` and `APP_JWT_SECRET` from the current user's environment. Store SMTP credentials as user environment variables too; never put real values in `application.properties` or commit them. In deployed environments, expose this API only to trusted internal services and use HTTPS.

SMTP variables belong only in this Correo terminal. Acceso and Transporte do not need SMTP credentials.

| Variable | Required | Default / purpose |
| --- | --- | --- |
| `MAIL_SERVICE_API_KEY` | Yes | Shared internal key used by Correo, Acceso, and Transporte. |
| `SMTP_USERNAME` | Yes for delivery | SMTP account username. |
| `SMTP_PASSWORD` | Yes for delivery | SMTP account password or app password. |
| `SMTP_HOST` / `SMTP_PORT` | No | Defaults to Gmail SMTP on port 587. |
| `SMTP_FROM` | No | Sender address; defaults to `SMTP_USERNAME`. |

The authenticated `POST /api/emails` endpoint accepts recipients, subject, text and/or HTML content, and up to three attachments (5 MB combined). Inline attachments are supported for embedded images.

## Build and tests

```powershell
.\gradlew.bat test
```

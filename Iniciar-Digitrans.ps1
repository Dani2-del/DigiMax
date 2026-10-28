$ErrorActionPreference = 'Stop'

$services = @(
    @{
        Name = 'Acceso'
        Port = 8080
        MainClass = 'com.Servicio.Acceso.AccesoApplication'
        Folder = 'Acceso'
        Command = '.\gradlew.bat --no-daemon bootRun'
    },
    @{
        Name = 'Pagos'
        Port = 8085
        MainClass = 'com.Servicio.Pagos.PagosApplication'
        Folder = 'Pagos'
        Command = '..\Transporte\mvnw.cmd -f ..\Pagos\pom.xml spring-boot:run'
    },
    @{
        Name = 'Billetera'
        Port = 8082
        MainClass = 'com.Servicio.Billetera.BilleteraApplication'
        Folder = 'Billetera'
        Command = '.\gradlew.bat --no-daemon bootRun'
    },
    @{
        Name = 'Transporte'
        Port = 8081
        MainClass = 'com.transcaribe.transcaribe.DigitransMaxApplication'
        Folder = 'Transporte'
        Command = '.\mvnw.cmd spring-boot:run'
    },
    @{
        Name = 'RecargasMovil'
        Port = 8083
        MainClass = 'com.Servicio.RecargasMovil.RecargasMovilApplication'
        Folder = 'RecargasMovil'
        Command = '.\gradlew.bat --no-daemon bootRun'
    },
    @{
        Name = 'Correo'
        Port = 8084
        MainClass = 'com.Servicio.Correo.CorreoApplication'
        Folder = 'Correo'
        Command = '.\gradlew.bat --no-daemon bootRun'
    }
)

function Get-PortOwners([int]$Port) {
    $connections = @(Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue)
    foreach ($processId in @($connections | Select-Object -ExpandProperty OwningProcess -Unique)) {
        if ($processId) {
            Get-CimInstance Win32_Process -Filter "ProcessId = $processId" -ErrorAction SilentlyContinue
        }
    }
}

function Wait-ForPort([int]$Port, [int]$TimeoutSeconds = 120) {
    for ($attempt = 0; $attempt -lt $TimeoutSeconds; $attempt++) {
        if (@(Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue).Count -gt 0) {
            return
        }
        Start-Sleep -Seconds 1
    }
    throw "El servicio no abrió el puerto $Port en $TimeoutSeconds segundos. Revisa la terminal que se abrió."
}

function Wait-ForPortFree([int]$Port, [int]$TimeoutSeconds = 15) {
    for ($attempt = 0; $attempt -lt $TimeoutSeconds; $attempt++) {
        if (@(Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue).Count -eq 0) {
            return
        }
        Start-Sleep -Seconds 1
    }
    throw "El puerto $Port continúa ocupado después de cerrar la instancia anterior."
}

try {
    $root = Split-Path -Parent $MyInvocation.MyCommand.Path
    $jwtSecret = $env:APP_JWT_SECRET
    if ([string]::IsNullOrWhiteSpace($jwtSecret)) {
        $jwtSecret = [Environment]::GetEnvironmentVariable('APP_JWT_SECRET', 'User')
    }
    if ([string]::IsNullOrWhiteSpace($jwtSecret)) {
        throw 'No está configurado APP_JWT_SECRET en las variables de entorno de tu usuario.'
    }
    $env:APP_JWT_SECRET = $jwtSecret
    $mailApiKey = $env:MAIL_SERVICE_API_KEY
    if ([string]::IsNullOrWhiteSpace($mailApiKey)) {
        $mailApiKey = [Environment]::GetEnvironmentVariable('MAIL_SERVICE_API_KEY', 'User')
    }
    if ([string]::IsNullOrWhiteSpace($mailApiKey)) {
        throw 'No está configurado MAIL_SERVICE_API_KEY en las variables de entorno de tu usuario.'
    }
    $env:MAIL_SERVICE_API_KEY = $mailApiKey

    foreach ($service in $services) {
        $owners = @(Get-PortOwners -Port $service.Port)
        foreach ($owner in $owners) {
            if ($owner.CommandLine -notmatch [regex]::Escape($service.MainClass)) {
                throw "El puerto $($service.Port) de $($service.Name) está ocupado por otro proceso (PID $($owner.ProcessId)). No se cerró."
            }
        }
    }

    foreach ($service in $services) {
        $owners = @(Get-PortOwners -Port $service.Port)
        foreach ($owner in $owners) {
            Write-Host "Cerrando instancia anterior de $($service.Name) (PID $($owner.ProcessId), puerto $($service.Port))..."
            Stop-Process -Id $owner.ProcessId -Force
        }
        if ($owners.Count -gt 0) {
            Wait-ForPortFree -Port $service.Port
        }
    }

    foreach ($service in $services) {
        $projectPath = Join-Path $root $service.Folder
        if (-not (Test-Path -LiteralPath $projectPath)) {
            throw "No se encontró la carpeta del módulo $($service.Name): $projectPath"
        }

        $command = "Set-Location -LiteralPath '$projectPath'; $($service.Command)"
        Write-Host "Iniciando $($service.Name) en el puerto $($service.Port)..."
        Start-Process -FilePath 'powershell.exe' `
            -ArgumentList @('-NoExit', '-Command', $command) `
            -WorkingDirectory $projectPath
        Wait-ForPort -Port $service.Port
        Write-Host "$($service.Name) está escuchando en el puerto $($service.Port)."
    }

    Write-Host 'Los seis módulos están iniciados.'
}
catch {
    Write-Error $_
    exit 1
}

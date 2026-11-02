param(
    [ValidateSet('local', 'atlas')]
    [string]$DatabaseProfile
)

$ErrorActionPreference = 'Stop'

$mongoDatabases = [ordered]@{
    ACCESO_MONGODB_URI = 'digitrans_acceso'
    TRANSPORTE_MONGODB_URI = 'digitrans_transporte'
    BILLETERA_MONGODB_URI = 'digitrans_billetera'
    RECARGAS_MONGODB_URI = 'digitrans_recargas'
}

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
    if ([string]::IsNullOrWhiteSpace($DatabaseProfile)) {
        Write-Host 'Selecciona la base de datos para iniciar DigiMAX:'
        Write-Host '  1. Local (localhost:27017)'
        Write-Host '  2. Atlas'
        $selection = (Read-Host 'Elige 1 o 2').Trim()
        switch ($selection) {
            '1' { $DatabaseProfile = 'local' }
            '2' { $DatabaseProfile = 'atlas' }
            default { throw 'Opción inválida. Ejecuta el .bat y elige 1 o 2.' }
        }
    }

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

    $env:SPRING_PROFILES_ACTIVE = "db-$DatabaseProfile"
    if ($DatabaseProfile -eq 'atlas') {
        foreach ($variable in $mongoDatabases.Keys) {
            $uri = [Environment]::GetEnvironmentVariable($variable, 'User')
            if ([string]::IsNullOrWhiteSpace($uri)) {
                $uri = [Environment]::GetEnvironmentVariable($variable, 'Process')
            }
            $expectedDatabase = [regex]::Escape($mongoDatabases[$variable])
            $atlasUriPattern = "^mongodb\+srv://[^/]+/$expectedDatabase(?:\?|$)"
            if ([string]::IsNullOrWhiteSpace($uri) -or $uri -notmatch $atlasUriPattern) {
                throw "No está configurada una URI Atlas válida para $variable. Configúrala en las variables de usuario y vuelve a abrir la terminal."
            }
            [Environment]::SetEnvironmentVariable($variable, $uri, 'Process')
        }
    } else {
        foreach ($variable in $mongoDatabases.Keys) {
            Remove-Item -Path "Env:\$variable" -ErrorAction SilentlyContinue
        }
    }

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

    # Modelo predictivo: publica las predicciones con la misma base que el perfil elegido.
    # No detiene el arranque: si falla, solo muestra una advertencia.
    $carpetaModelo = $env:DIGITRANS_MODELO
    if ([string]::IsNullOrWhiteSpace($carpetaModelo)) {
        $carpetaModelo = Join-Path (Split-Path -Parent $root) 'modelo_digitrans'
    }
    $scriptModelo = Join-Path $carpetaModelo 'publicar_predicciones.py'
    if (Test-Path -LiteralPath $scriptModelo) {
        if ($DatabaseProfile -eq 'atlas') {
            $uriModelo = [Environment]::GetEnvironmentVariable('ACCESO_MONGODB_URI', 'Process')
        }
        else {
            $uriModelo = 'mongodb://localhost:27017'
        }
        $uriAnterior = $env:MONGO_URI
        $env:MONGO_URI = $uriModelo
        Write-Host "Publicando predicciones del modelo predictivo ($DatabaseProfile)..."
        try {
            Push-Location -LiteralPath $carpetaModelo
            python publicar_predicciones.py
            if ($LASTEXITCODE -ne 0) {
                Write-Warning 'El modelo predictivo no se publicó. Revisa el mensaje anterior.'
            }
        }
        catch {
            Write-Warning "No se pudo ejecutar el modelo predictivo: $($_.Exception.Message)"
        }
        finally {
            Pop-Location
            $env:MONGO_URI = $uriAnterior
        }
    }
    else {
        Write-Warning "No se encontró publicar_predicciones.py en $carpetaModelo. Se omite el modelo predictivo."
    }

    Write-Host "Los seis módulos están iniciados usando MongoDB $DatabaseProfile."
}
catch {
    Write-Error $_
    exit 1
}

$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$requiredSecrets = @('APP_JWT_SECRET', 'MAIL_SERVICE_API_KEY', 'LECTOR_API_KEY')
$atlasDatabases = [ordered]@{
    ACCESO_MONGODB_URI = 'digitrans_acceso'
    TRANSPORTE_MONGODB_URI = 'digitrans_transporte'
    BILLETERA_MONGODB_URI = 'digitrans_billetera'
    RECARGAS_MONGODB_URI = 'digitrans_recargas'
}

function Get-AvailableEnvironmentValue([string]$Name) {
    $value = [Environment]::GetEnvironmentVariable($Name, 'Process')
    if ([string]::IsNullOrWhiteSpace($value)) {
        $value = [Environment]::GetEnvironmentVariable($Name, 'User')
    }
    return $value
}

function Save-EnvironmentValue([string]$Name, [string]$Value) {
    [Environment]::SetEnvironmentVariable($Name, $Value, 'User')
    [Environment]::SetEnvironmentVariable($Name, $Value, 'Process')
}

foreach ($name in $requiredSecrets) {
    $value = Get-AvailableEnvironmentValue $name
    if ([string]::IsNullOrWhiteSpace($value)) {
        throw "Falta la variable $name. Configúrala en las variables de entorno de Windows y vuelve a ejecutar este script."
    }
    Save-EnvironmentValue $name $value
}

foreach ($name in $atlasDatabases.Keys) {
    $value = Get-AvailableEnvironmentValue $name
    $database = [regex]::Escape($atlasDatabases[$name])
    if ([string]::IsNullOrWhiteSpace($value) -or $value -notmatch "^mongodb\+srv://[^/]+/$database(?:\?|$)") {
        throw "Falta una URI Atlas válida en $name. Revisa las variables de entorno de Windows."
    }
    Save-EnvironmentValue $name $value
}

$lectorUri = Get-AvailableEnvironmentValue 'LECTOR_MONGODB_URI'
if ([string]::IsNullOrWhiteSpace($lectorUri)) {
    $accesoUri = Get-AvailableEnvironmentValue 'ACCESO_MONGODB_URI'
    $lectorUri = ([regex]'^(mongodb\+srv://[^/]+/)[^/?]+').Replace(
        $accesoUri, '${1}digitrans_lector', 1)
}
if ($lectorUri -notmatch '^mongodb\+srv://[^/]+/digitrans_lector(?:\?|$)') {
    throw 'Falta una URI Atlas válida en LECTOR_MONGODB_URI.'
}
Save-EnvironmentValue 'LECTOR_MONGODB_URI' $lectorUri

foreach ($name in @('GOOGLE_CLIENT_ID', 'GOOGLE_CLIENT_SECRET', 'SMTP_USERNAME', 'SMTP_PASSWORD')) {
    $value = Get-AvailableEnvironmentValue $name
    if (-not [string]::IsNullOrWhiteSpace($value)) {
        Save-EnvironmentValue $name $value
    }
}

Save-EnvironmentValue 'SPRING_PROFILES_ACTIVE' 'db-atlas'

$apiToken = Get-AvailableEnvironmentValue 'ORQUESTADOR_API_TOKEN'
if ([string]::IsNullOrWhiteSpace($apiToken)) {
    $bytes = New-Object byte[] 32
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
    $apiToken = [BitConverter]::ToString($bytes).Replace('-', '').ToLowerInvariant()
}
Save-EnvironmentValue 'ORQUESTADOR_API_TOKEN' $apiToken

Write-Host 'Entorno configurado para MongoDB Atlas. Iniciando Orquestador y sus módulos...'
Push-Location (Join-Path $root 'Orquestador')
try {
    & .\gradlew.bat --no-daemon bootRun
    exit $LASTEXITCODE
}
finally {
    Pop-Location
}

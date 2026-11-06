param(
    [string]$Prefijo = 'digitrans'
)

$ErrorActionPreference = 'Stop'

$directorioScript = Split-Path -Parent $MyInvocation.MyCommand.Path
$directorioAnterior = Get-Location
$uriAnterior = $env:DIGITRANS_ATLAS_URI
$solicitoUri = [string]::IsNullOrWhiteSpace($uriAnterior)
$uriTemporal = $null

try {
    Set-Location $directorioScript

    if ($solicitoUri) {
        $uriSegura = Read-Host 'Pega la URI de MongoDB Atlas' -AsSecureString
        $ptrUri = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($uriSegura)
        try { $uriTemporal = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptrUri) }
        finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptrUri) }
        $env:DIGITRANS_ATLAS_URI = $uriTemporal
    }

    if ([string]::IsNullOrWhiteSpace($env:DIGITRANS_ATLAS_URI)) {
        throw 'No se proporcionó DIGITRANS_ATLAS_URI.'
    }

    $python = Get-Command python -ErrorAction SilentlyContinue
    if (-not $python) { throw 'No se encontró Python en PATH.' }

    $pasos = @(
        @{ Nombre = 'Exportar datos de Atlas'; Script = 'exportar_datos_atlas.py'; Argumentos = @('--prefijo', $Prefijo) },
        @{ Nombre = 'Construir tabla usuario-semana'; Script = 'construir_tabla_usuario_semana.py'; Argumentos = @() },
        @{ Nombre = 'Evaluar modelo'; Script = 'evaluar_final.py'; Argumentos = @() },
        @{ Nombre = 'Publicar predicciones'; Script = 'publicar_predicciones.py'; Argumentos = @('--prefijo', $Prefijo) }
    )

    foreach ($paso in $pasos) {
        Write-Host "`n=== $($paso.Nombre) ===" -ForegroundColor Cyan
        $argumentos = @($paso.Argumentos)
        & $python.Source $paso.Script @argumentos
        if ($LASTEXITCODE -ne 0) {
            throw "Falló $($paso.Script) (código $LASTEXITCODE). Se detuvo el flujo."
        }
    }

    Write-Host "`nModelo ejecutado y publicado correctamente." -ForegroundColor Green
} catch {
    Write-Error $_
    exit 1
} finally {
    Set-Location $directorioAnterior
    if ($solicitoUri) {
        Remove-Item Env:DIGITRANS_ATLAS_URI -ErrorAction SilentlyContinue
        $uriTemporal = $null
    }
}

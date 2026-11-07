$ErrorActionPreference = 'Stop'

$ports = 8080..8087
$listeners = @(
    Get-NetTCPConnection -State Listen -ErrorAction SilentlyContinue |
        Where-Object { $_.LocalPort -in $ports } |
        Sort-Object OwningProcess, LocalPort -Unique
)

if ($listeners.Count -eq 0) {
    Write-Host 'No hay procesos escuchando en los puertos 8080-8087.' -ForegroundColor Green
    exit 0
}

$processes = foreach ($group in ($listeners | Group-Object OwningProcess)) {
    $processId = [int]$group.Name
    $process = Get-Process -Id $processId -ErrorAction SilentlyContinue
    [PSCustomObject]@{
        PID      = $processId
        Proceso  = if ($process) { $process.ProcessName } else { '(desconocido)' }
        Puertos  = (($group.Group | Select-Object -ExpandProperty LocalPort -Unique | Sort-Object) -join ', ')
    }
}

Write-Host 'Procesos que escuchan en los puertos 8080-8087:' -ForegroundColor Cyan
$processes | Format-Table -AutoSize
Write-Host 'Se cerrarán esos procesos. Si Orquestador está en 8087, también se detendrá.' -ForegroundColor Yellow
$confirmation = Read-Host 'Escribe S para continuar'
if ($confirmation -notin @('S', 's', 'SI', 'Si', 'sí', 'Sí')) {
    Write-Host 'Operación cancelada.'
    exit 0
}

foreach ($processInfo in $processes) {
    try {
        Stop-Process -Id $processInfo.PID -Force -ErrorAction Stop
        Write-Host "Detenido PID $($processInfo.PID) ($($processInfo.Proceso)), puerto(s): $($processInfo.Puertos)." -ForegroundColor Green
    }
    catch {
        Write-Warning "No se pudo detener PID $($processInfo.PID): $($_.Exception.Message)"
    }
}

Start-Sleep -Seconds 2
$remaining = @(
    Get-NetTCPConnection -State Listen -ErrorAction SilentlyContinue |
        Where-Object { $_.LocalPort -in $ports } |
        Sort-Object LocalPort -Unique
)

if ($remaining.Count -eq 0) {
    Write-Host 'Puertos 8080-8087 liberados.' -ForegroundColor Green
} else {
    Write-Warning 'Aún hay puertos ocupados:'
    $remaining | Select-Object LocalAddress, LocalPort, OwningProcess | Format-Table -AutoSize
    Write-Host 'Si aparece acceso denegado, ejecuta PowerShell como administrador y vuelve a correr el script.'
}

@echo off
setlocal
cd /d "%~dp0"

powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0ejecutar_modelo.ps1"
set "resultado=%ERRORLEVEL%"

if not "%resultado%"=="0" (
    echo.
    echo El flujo del modelo termino con errores. Revisa el mensaje anterior.
) else (
    echo.
    echo El flujo del modelo termino correctamente.
)

echo.
pause
exit /b %resultado%

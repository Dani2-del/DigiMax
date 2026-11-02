@echo off
REM Inicia el modulo de Acceso (Spring Boot) desde su propia carpeta.
REM Colocar este archivo dentro de la carpeta Acceso (donde estan gradlew.bat y build.gradle).
REM Las contrasenas y claves van en las variables de entorno de Windows, no aqui.

setlocal
cd /d "%~dp0"

echo.
echo ==============================================
echo  Digitrans - Acceso
echo  Aplicacion en: http://localhost:8080
echo  Para detenerla: Ctrl + C
echo ==============================================
echo.

call gradlew.bat bootRun

echo.
echo Acceso se detuvo.
pause

# Modelo predictivo Digitrans

Este módulo estima qué pasajeros activos harán una recarga exitosa de Billetera durante los siguientes **30 días**.

## Carpetas

- `ejecutar_modelo.bat` y `ejecutar_modelo.ps1`: ejecutan el flujo.
- `modelo/`: código Python, configuración y notebook de Colab.
- `generaciones/`: resultados separados por ejecución. Cada corrida crea una carpeta nueva con fecha y hora; los resultados anteriores se conservan.

## Ejecutar

Instala Python 3.12 y las dependencias:

```powershell
python -m pip install -r .\requirements.txt
```

Para generar los archivos y evaluar el modelo sin publicar cambios en Atlas:

```powershell
powershell -ExecutionPolicy Bypass -File .\ejecutar_modelo.ps1 -SoloEvaluar
```

Para ejecutar el flujo completo, incluida la publicación de predicciones y métricas en Atlas:

```powershell
powershell -ExecutionPolicy Bypass -File .\ejecutar_modelo.ps1
```

Si `DIGITRANS_ATLAS_URI` no está configurada, el script la solicita de forma segura. No la guardes en el notebook ni la pegues en archivos del proyecto.

Cada ejecución crea `generaciones\AAAA-MM-DD_HHMMSS\` con los CSV exportados, la tabla usuario-semana, la evaluación y, en el flujo completo, las predicciones y métricas. El flujo completo reemplaza la proyección vigente en Atlas; `-SoloEvaluar` no escribe en Atlas.

## Google Colab

Abre `modelo/Modelo_Predictivo_DigiTrans_Colab.ipynb` y carga el archivo `tabla_usuario_semana_horizontes_v1.csv` desde la carpeta de generación más reciente. El notebook vuelve a entrenar y evaluar en la sesión de Colab, y descarga sus predicciones al final. No publica en Atlas.

Si la tabla contiene información real de usuarios, anonimízala y confirma que puedes procesarla en Colab antes de subirla.

## Comparar configuraciones

Desde la carpeta de una generación que ya tenga la tabla, compara configuraciones sin publicar en Atlas:

```powershell
python ..\..\modelo\comparar_catboost_30d.py
```

La comparación guarda `experimento_catboost_30d.csv` en esa misma carpeta de generación.

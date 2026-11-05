# Modelo de recarga de Billetera

El objetivo es estimar si un usuario realizará una recarga exitosa de su Billetera durante los siete días posteriores a cada lunes observado. Las recargas móviles son señales históricas, pero no cuentan como resultado positivo.

## Ejecución automática en Windows

Desde PowerShell, en esta carpeta, ejecuta:

```powershell
.\ejecutar_modelo.ps1
```

El script pide la URI de Atlas de forma oculta si `DIGITRANS_ATLAS_URI` no está definida en esa ventana. Después ejecuta, en orden, la exportación, construcción de tabla, evaluación y publicación. Si un paso falla, se detiene e indica cuál. La URI solicitada solo se mantiene en el proceso durante esta ejecución.

También puedes definir previamente `$env:DIGITRANS_ATLAS_URI` en la ventana actual; el script la reutiliza sin volver a preguntar.

Si PowerShell bloquea la ejecución del archivo, inicia una sesión puntual así:

```powershell
powershell -ExecutionPolicy Bypass -File .\ejecutar_modelo.ps1
```

El mismo flujo puede ejecutarse paso a paso con `python exportar_datos_atlas.py`, `python construir_tabla_usuario_semana.py`, `python evaluar_final.py` y `python publicar_predicciones.py`.

Instala dependencias si hace falta: `python -m pip install pymongo pandas numpy scikit-learn`.

`evaluar_final.py` usa `tabla_eval.csv` si existe; de lo contrario, usa la ventana temporal reservada en `tabla_usuario_semana_v2.csv` (5 de enero a 19 de octubre de 2026). `tabla_eval.csv` debe tener las mismas columnas y el mismo objetivo.

El modelo actual es regresión logística con conteos históricos hasta 365 días comprimidos con `log1p`. La evaluación disponible produjo F1 0.2410, precisión 0.1704, recall 0.4116 y AUC-ROC 0.7594. La ventana temporal ya se había consultado en una iteración anterior: sirve para comparar esta mejora, pero no es una prueba final completamente intacta.

El usuario de Atlas debe tener permisos de lectura en las bases de origen y permisos de escritura en `digitrans_acceso` para publicar predicciones y métricas.

Los CSV contienen identificadores y fechas. Mantenlos fuera del control de versiones.

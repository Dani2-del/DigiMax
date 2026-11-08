# Modelo predictivo Digitrans

Este módulo estima qué pasajeros activos harán una recarga exitosa de Billetera durante los siguientes **30 días**. Usa el historial disponible antes de cada fecha de referencia y publica los niveles estimados en Atlas.

## Ejecutar en Windows

Instala Python 3.12 y las dependencias:

```powershell
python -m pip install -r requirements.txt
```

Desde esta carpeta, inicia el flujo completo:

```powershell
powershell -ExecutionPolicy Bypass -File .\ejecutar_modelo.ps1
```

El flujo solicita la URI de Atlas de forma oculta si no está configurada en la sesión. Luego exporta los datos, construye la tabla usuario-semana, evalúa el horizonte mensual y publica las predicciones.

## Flujo

1. `exportar_datos_atlas.py`: exporta usuarios y eventos desde Atlas.
2. `construir_tabla_usuario_semana.py`: genera etiquetas de 7, 14 y 30 días y variables históricas.
3. `evaluar_horizontes.py`: compara los horizontes con cortes temporales.
4. `publicar_predicciones.py`: entrena CatBoost para el objetivo de 30 días y actualiza `predicciones_recarga` y `modelo_metricas` en la base `digitrans_acceso`.

Para ejecutar únicamente la publicación con la tabla y evaluación ya generadas:

```powershell
python .\publicar_predicciones.py --prefijo digitrans
```

## Alcance de las métricas

El modelo publica segmentos relativos de riesgo. La precisión y el recall del grupo superior describen la evaluación temporal del conjunto disponible; como los datos actuales son sintéticos, esas cifras **no representan el rendimiento esperado con pasajeros reales**.

Los archivos CSV se generan durante la ejecución y no forman parte de esta entrega. El flujo los vuelve a crear desde Atlas.

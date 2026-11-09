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
3. `evaluar_horizontes.py`: evalúa los horizontes con hasta seis ventanas temporales móviles de 90 días y embargo igual al horizonte. Requiere al menos tres cortes completos.
4. `publicar_predicciones.py`: entrena CatBoost para el objetivo de 30 días y actualiza `predicciones_recarga` y `modelo_metricas` en la base `digitrans_acceso`.

Para ejecutar únicamente la publicación con la tabla y evaluación ya generadas:

```powershell
python .\publicar_predicciones.py --prefijo digitrans
```

Para comparar configuraciones de CatBoost del objetivo elegido de 30 días, después de generar la tabla:

```powershell
python .\comparar_catboost_30d.py
```

La comparación selecciona parámetros con los cortes antiguos y reserva los dos más recientes para la medición final. Genera `experimento_catboost_30d.csv` y no publica ni modifica datos en Atlas. Solo conviene reemplazar la configuración actual si también mejora en ese periodo reservado.

## Cuaderno Google Colab

Abre `Modelo_Predictivo_DigiTrans_Colab.ipynb` en Google Colab y sube `tabla_usuario_semana_horizontes_v1.csv`. El cuaderno explica las variables y la validación temporal, reproduce la evaluación y genera puntuaciones descargables sin escribir en Atlas. Si la tabla contiene información real de usuarios, anonimízala y confirma que puedes procesarla en Colab antes de subirla.

## Alcance de las métricas

El modelo de publicación predice una recarga exitosa de Billetera dentro de 30 días usando eventos exportados desde Atlas. La evaluación usa cortes temporales móviles y reporta precisión y recall en los grupos superiores. Esas cifras reflejan la base exportada; si contiene transacciones de prueba o simuladas, no deben interpretarse como rendimiento en producción. El horizonte publicado se mantiene en 30 días.

Los archivos CSV se generan durante la ejecución y no forman parte de esta entrega. El flujo los vuelve a crear desde Atlas.

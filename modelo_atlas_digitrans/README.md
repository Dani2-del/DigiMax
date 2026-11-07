# Modelo de recarga de Billetera

El objetivo es estimar si un usuario realizará una recarga exitosa de su Billetera durante los siete días posteriores a cada lunes observado. Las recargas móviles son señales históricas, pero no cuentan como resultado positivo.

## Ejecución automática en Windows

Desde PowerShell, en esta carpeta, ejecuta:

```powershell
.\ejecutar_modelo.ps1
```

Para actualizar los datos y evaluar candidatos sin publicar predicciones en Atlas, usa `.\ejecutar_modelo.ps1 -SoloEvaluar`.

El flujo usa `digitrans` como prefijo principal. Puedes indicar `-Prefijo` para evaluar otro conjunto; exportacion y publicacion usan el mismo prefijo.

El script pide la URI de Atlas de forma oculta si `DIGITRANS_ATLAS_URI` no está definida en esa ventana. Después ejecuta, en orden, la exportación, construcción de tabla, evaluación y publicación. Si un paso falla, se detiene e indica cuál. La URI solicitada solo se mantiene en el proceso durante esta ejecución.

También puedes definir previamente `$env:DIGITRANS_ATLAS_URI` en la ventana actual; el script la reutiliza sin volver a preguntar.

Si PowerShell bloquea la ejecución del archivo, inicia una sesión puntual así:

```powershell
powershell -ExecutionPolicy Bypass -File .\ejecutar_modelo.ps1
```

El flujo también puede ejecutarse paso a paso con `python exportar_datos_atlas.py`, `python construir_tabla_usuario_semana.py`, `python evaluar_final.py` y `python publicar_predicciones.py`.

Instala las dependencias del modelo desde esta carpeta: `python -m pip install -r requirements.txt`. CatBoost y LightGBM se comparan como candidatos y la primera evaluación puede tardar más que antes.

`evaluar_final.py` compara regresión logística, boosting de gradiente, CatBoost y LightGBM usando la misma división temporal. Elige el modelo y el umbral por F1 en validación; AUC-PR sirve como desempate. Luego reporta una sola vez el resultado en el periodo posterior, del 5 de enero al 19 de octubre de 2026. Si existe `tabla_eval.csv`, se usa como prueba independiente y debe tener todas las columnas de la versión v6. La versión de evaluación se identifica como `recarga_historial_consumo_transporte_v8_cadencia`.

La tabla v6 agrega el intervalo entre las dos últimas recargas a las variables de la tabla v5. El generador de datos sintéticos crea usuarios ocasionales, regulares y frecuentes con una cadencia individual persistente, variación entre ciclos y algunos ciclos omitidos; el perfil no se guarda como dato del usuario. El objetivo sigue siendo exclusivamente un abono exitoso de Billetera dentro de los siguientes siete días; viajes, recargas móviles y consumos solo son predictores. Tras regenerar las bases, hay que exportar y reconstruir la tabla antes de evaluar.

Las métricas guardadas de v5/v7 son históricas: se calcularon antes de introducir cadencias de recarga y la nueva variable de intervalo. No se deben comparar como si provinieran de la misma generación de datos que v8. Los datos sintéticos sirven para probar el flujo y no representan evidencia de rendimiento con pasajeros reales.

La exportación de viajes consulta desde el 1 de enero de 2023 para aprovechar el índice por fecha. Si Atlas cierra la conexión, el exportador reabre el cursor y reintenta desde el último tramo escrito, sin duplicar los viajes ya exportados.

Los viajes nuevos guardan el `usuario_id` del perfil de Acceso. Para viajes universales antiguos sin ese campo, el exportador puede recuperar la cuenta cuando `operacion_id` coincide de forma única con un movimiento `CONSUMO` de Billetera. No modifica documentos de Atlas. Viajes físicos antiguos sin ID de usuario ni vínculo inequívoco se omiten para evitar asignarlos a la cuenta equivocada.

Para completar esos vínculos históricos en Atlas, ejecuta primero `python reparar_transacciones_transporte.py` desde esta carpeta. El modo predeterminado solo muestra una vista previa. Si los conteos son correctos, ejecuta `python reparar_transacciones_transporte.py --aplicar`; solo escribe los vínculos inequívocos y los datos de presentación del usuario, sin borrar transacciones.

El usuario de Atlas debe tener permisos de lectura en las bases de origen y permisos de escritura en la base `*_acceso` elegida para publicar predicciones y métricas.

Los CSV contienen identificadores y fechas. Mantenlos fuera del control de versiones.

## Reconstruccion de las bases Digitrans

`generar_datos_prueba.py` reconstruye en Atlas `digitrans_acceso`, `digitrans_transporte`, `digitrans_billetera`, `digitrans_recargas` y `digitrans_lector`. Tambien elimina las cinco bases parciales `digitrans_rebuild_*` de la ejecucion anterior. Mongo local no se borra.

Antes de borrar, comprueba que Mongo local responda y que existan `digitrans_recargas.operadores_movil` y `paquetes_movil` con referencias validas. Esos catalogos se copian exclusivamente desde Mongo local. El script tambien conserva en memoria los administradores de Acceso, administradores de Transporte, geometrias de rutas y tarjetas RFID libres de Atlas. Si faltan catalogos locales o administradores, se detiene sin borrar. Antes de escribir, imprime las bases afectadas y exige escribir exactamente `BORRAR digitrans Y digitrans_rebuild`.

La escala predeterminada es 20 %, igual que la generaci?n anterior. Esta vez se borran las bases de destino antes de insertar los datos. Las transacciones generadas incluyen `usuario_id`, correo y nombre, con las cuentas espejo en Acceso y Transporte. Las rutas vienen de `rutas_catalogo.py` y del catalogo de Transcaribe incluido.

Desde esta carpeta, configura la URI local (si Mongo corre en tu equipo) y ejecuta. El script pedira la URI de Atlas sin mostrarla:

```powershell
$env:MONGO_LOCAL = 'mongodb://localhost:27017'
python .\generar_datos_prueba.py
```

Cuando termine, ejecuta el modelo contra las bases principales:

```powershell
.\ejecutar_modelo.ps1
```

Los datos generados son sintéticos. Sus métricas no describen el comportamiento real de pasajeros y no se deben presentar como rendimiento de producción. Para subirlas de forma legítima se necesitan historiales reales, suficientes positivos y patrones temporales que existan en el servicio. No se debe fijar una precisión de 80–90 % cambiando solo el umbral si el recall cae drásticamente.

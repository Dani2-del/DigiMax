# Modelo de recarga de Billetera

El objetivo es estimar si un usuario realizará una recarga exitosa de su Billetera durante los siete días posteriores a cada lunes observado. Las recargas móviles son señales históricas, pero no cuentan como resultado positivo.

## Ejecución automática en Windows

Desde PowerShell, en esta carpeta, ejecuta:

```powershell
.\ejecutar_modelo.ps1
```

El flujo usa `digitrans` como prefijo principal. Puedes indicar `-Prefijo` para evaluar otro conjunto; exportacion y publicacion usan el mismo prefijo.

El script pide la URI de Atlas de forma oculta si `DIGITRANS_ATLAS_URI` no está definida en esa ventana. Después ejecuta, en orden, la exportación, construcción de tabla, evaluación y publicación. Si un paso falla, se detiene e indica cuál. La URI solicitada solo se mantiene en el proceso durante esta ejecución.

También puedes definir previamente `$env:DIGITRANS_ATLAS_URI` en la ventana actual; el script la reutiliza sin volver a preguntar.

Si PowerShell bloquea la ejecución del archivo, inicia una sesión puntual así:

```powershell
powershell -ExecutionPolicy Bypass -File .\ejecutar_modelo.ps1
```

El mismo flujo puede ejecutarse paso a paso con `python exportar_datos_atlas.py`, `python construir_tabla_usuario_semana.py`, `python evaluar_final.py` y `python publicar_predicciones.py`.

Instala dependencias si hace falta: `python -m pip install pymongo pandas numpy scikit-learn`.

`evaluar_final.py` usa `tabla_eval.csv` si existe; de lo contrario, usa la ventana temporal reservada en `tabla_usuario_semana_v3.csv` (5 de enero a 19 de octubre de 2026). `tabla_eval.csv` debe tener las mismas columnas y el mismo objetivo.

El modelo sigue usando regresión logística. Esta versión agrega al historial de recargas el uso reciente de Transporte (cantidad de pasajes en ventanas de 7, 30, 90 y 365 días, y días desde el último viaje). Los viajes son variables predictoras; la etiqueta positiva continúa contando solamente abonos de Billetera. La nueva medición estará disponible después de ejecutar el flujo completo. La evaluación anterior de la ventana temporal dio F1 0.2410, precisión 0.1704, recall 0.4116 y AUC-ROC 0.7594; esa ventana ya se había revisado en una iteración anterior.

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

Los datos generados son sint?ticos; sus m?tricas no describen el comportamiento real de pasajeros.

# Generador de datos Digitrans

Este módulo reconstruye en MongoDB Atlas las bases Digitrans con cuentas y actividad sintéticas para pruebas universitarias. Las rutas de TransMilenio y Transmetro están en `rutas_catalogo.py`; Transcaribe está definido en `generar_datos_prueba.py`.

## Requisitos

- Python 3.12
- MongoDB local activo con `digitrans_recargas.operadores_movil` y `digitrans_recargas.paquetes_movil`
- URI de MongoDB Atlas con permisos para eliminar y crear las bases del proyecto

Instala las dependencias desde esta carpeta:

```powershell
python -m pip install -r requirements.txt
```

## Ejecutar

```powershell
$env:MONGO_LOCAL = 'mongodb://localhost:27017'
python .\generar_datos_prueba.py --usuarios 28369 --escala 0.20 --semilla 42
```

El programa solicita la URI de Atlas y la clave temporal de las cuentas mediante entradas ocultas. Antes de escribir, valida las conexiones y los catálogos locales.

## Bases afectadas

Al confirmar, el generador **elimina y reconstruye** estas bases de Atlas:

- `digitrans_acceso`
- `digitrans_transporte`
- `digitrans_billetera`
- `digitrans_recargas`
- `digitrans_lector`
- las cinco bases parciales `digitrans_rebuild_*`

La confirmación requerida es exactamente `BORRAR digitrans Y digitrans_rebuild`. Si no se escribe esa frase, no borra ni genera datos. Revisa cuidadosamente el destino y los conteos antes de confirmar. No ejecutes el script contra una base con datos que deban conservarse.

Los datos generados son sintéticos y sirven para pruebas; no representan actividad real de pasajeros ni demuestran rendimiento productivo del modelo.

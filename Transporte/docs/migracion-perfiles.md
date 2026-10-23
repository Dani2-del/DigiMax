# Migración de perfiles de Transporte

Acceso y Transporte ya usan bases separadas (`digitrans_acceso` y
`digitrans_transporte`). Acceso conserva las cuentas e identidad; Transporte
almacena los datos propios del servicio en `perfiles_transporte`, vinculados
por `usuarioId`.

El script `../scripts/migrate-transport-profiles.js` prepara un backfill no
destructivo desde `usuarios` hacia `perfiles_transporte`. Solo copia
`sistema_asignado`, `sitm`, tarjetas, rutas favoritas, preferencias de rutas y
bus asignado. No copia correo, nombre, rol, contraseña, estado ni tokens.
Los documentos originales no se eliminan. También remapea al ID de Acceso las
referencias de transacciones (DBRef), horarios de conductor, mensajes de chat,
destinatarios de avisos y usuarios que descartaron avisos.

## Ejecución

1. Haz una copia de seguridad de ambas bases antes de migrar datos.
2. Detén Acceso y Transporte durante la aplicación: el backfill modifica
   referencias de negocio y no debe competir con escrituras de la aplicación.
3. Con `mongosh` conectado a la misma instancia MongoDB de desarrollo, ejecuta
   el script en modo de simulación:

   ```powershell
   mongosh --quiet --file .\Transporte\scripts\migrate-transport-profiles.js
   ```

4. Revisa el resumen. No continúes si hay identidades sin correspondencia,
   ambiguas o perfiles legacy duplicados; resuelve esas cuentas antes de migrar.
5. Con ambos servicios detenidos, cambia temporalmente `APPLY_MIGRATION` a
   `true` y ejecuta el mismo script. El código actual ya lee y escribe los
   perfiles con el ID central de Acceso.
6. Comprueba los contadores de perfiles insertados y referencias remapeadas.
   Después, inicia los servicios y valida inicio de sesión, tarjetas,
   transacciones, horarios, chat, avisos y reportes.
7. Conserva `usuarios` como respaldo legacy hasta confirmar que los datos y
   referencias quedaron correctos. No elimines esa colección como parte de
   esta migración.

El script es idempotente: no sobrescribe perfiles ya creados. La simulación no
escribe datos ni crea índices; al aplicar, crea un índice único para `usuarioId`.
Si se interrumpe una aplicación, deja los servicios detenidos y vuelve a ejecutar
el script con `APPLY_MIGRATION = true`; los perfiles existentes no se sobrescriben
y las referencias ya remapeadas no volverán a coincidir con los IDs antiguos.

Las transacciones migradas guardan `usuario_id` y una instantánea de correo y
nombre, y dejan de depender del DBRef hacia `usuarios`. El código mantiene
lectura de datos de perfil legacy como fallback, pero las nuevas escrituras de
Transporte se guardan en `perfiles_transporte`. No borres los documentos legacy
hasta completar y validar la migración.

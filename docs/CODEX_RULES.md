# CODEX RULES

Reglas permanentes para trabajar con Codex en este proyecto.

- Antes de cualquier ticket, leer `START_HERE.md`.
- Usar `docs/AREAS.md` para identificar el area exacta afectada.
- Abrir solo los archivos necesarios de esa area.
- No revisar todo el repo salvo que el ticket diga explicitamente: "revisar todo el repo".
- No tocar areas no relacionadas.
- Trabajar siempre en tickets chicos.
- Diagnosticar antes de escribir codigo.
- Encontrar causa raiz antes de cambiar archivos.
- Recien despues escribir codigo.
- Abrir solo archivos del area afectada.
- No recorrer todo el repo salvo auditoria explicita.
- No agregar features fuera del ticket.
- Modificar la menor cantidad posible de archivos.
- Prioridad permanente: ahorrar tokens, reducir contexto, evitar cambios innecesarios, hacer fixes faciles de revisar y mantener el repo limpio y escalable.
- Si cambia Android: build/tests/versionCode/commit/push/publicacion DEV.
- Si solo cambian docs: no compilar, no incrementar `versionCode`, no publicar APK, no tocar Android y no tocar Supabase.
- Si solo cambian docs/scripts/SQL: no APK.
- No usar Service Role Key en Android.
- No borrar datos sin confirmacion.
- Errores tecnicos solo en Logcat.
- Usuario ve mensajes simples.

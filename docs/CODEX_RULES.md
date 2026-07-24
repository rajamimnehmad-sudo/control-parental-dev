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
- Si cambia Android: validar cada ticket con build/tests proporcionales. Incrementar y publicar solo el `versionCode` de cada app afectada; Usuario y Admin versionan de forma independiente. Coordinar ambas solo si el cambio entra en las dos APK. Hacer commit, push y publicacion DEV al cierre del lote aprobado.
- Si solo cambian docs: no compilar, no incrementar `versionCode`, no publicar APK, no tocar Android y no tocar Supabase.
- Si solo cambian docs/scripts/SQL: no APK.
- Cada ticket autorizado debe terminar implementado, con las validaciones automaticas proporcionales, PR y fusion a `main`; la prueba fisica o de laboratorio no bloquea el cierre salvo que el usuario la exija para ese ticket. No dejar estados `pendiente PR`, ramas de trabajo o PR borrador cuando no exista un bloqueo real.
- Varios tickets Android relacionados, aprobados y listos deben agruparse en un solo lote y una sola publicacion por app afectada. Nunca publicar varias APK sin que el codigo exacto este antes en GitHub; el lote conserva causa, pruebas y evidencia por ticket.
- Despues de publicar, verificar manifiestos, hashes, paquetes, version y firma; recien entonces actualizar `docs/HANDOFF_ACTUAL.md` y `docs/BACKLOG_PRODUCTO.md` de candidato a publicado.
- Las pruebas fisicas, de laboratorio o cualquier requisito externo pendiente se documentan por separado con su bloqueo exacto. Nunca declararlos ejecutados por inferencia.
- Todo candidato o version de DAG que cambie navegador, carga, WebView o imagenes debe ejecutar antes de entregarse la matriz fija Fravega, Mimo y Cheeky en el mismo telefono objetivo. Registrar `DagPerformance page_visible`, dispositivo, Android, fecha, variante y cualquier timeout/rotura en `docs/compatibility/results/dag-performance-history.md`; una pagina que no completa cuenta como regresion, no como dato ausente.
- Antes de iniciar otro lote, dejar `main` sincronizada, documentacion coherente, los PR del lote fusionados y el checkout sin artefactos generados por la tarea.
- No usar Service Role Key en Android.
- No borrar datos sin confirmacion.
- Errores tecnicos solo en Logcat.
- Usuario ve mensajes simples.

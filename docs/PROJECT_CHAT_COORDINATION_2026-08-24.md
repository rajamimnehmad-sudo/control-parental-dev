# Coordinación multi-chat Glosh

Fecha: 2026-08-24

## Fuente central

Glosh Central / Control Center sigue siendo la coordinación común para todos los chats especializados.

Tracker canónico consumido por la app:

- rama: `build/glosh-control-center-v2`
- archivo: `docs/AI_TASK_TRACKER.json`

GitHub + estado Git actual siguen siendo la verdad para código, commits, ramas, worktrees, PR, tests y evidencia.

## Chats especializados vigentes

### Chrome Fotos + GloshIA

- Área: Chrome oficial, GloshIA de fotos, proxy/data-plane, VPN/transport relacionado, protected surface y gates físicos de esta ruta.
- Frente de escritura activo cuando exista ticket Chrome asignado.
- Codex puede ejecutar código/build/ADB/dispositivo; ChatGPT revisa diff/tests/evidencia antes del cierre final.
- No tocar Remote Installer, Apps UX/UI, DAG/video, Supabase u otros frentes salvo dependencia explícitamente coordinada.

### Glosh Remote / instalación remota Android

- Área: `tools/glosh-remote-spike` y rutas específicas del instalador/conexión remota autorizadas por su ticket.
- Frente separado de Chrome.
- No compartir rutas de escritura con Chrome ni Apps sin coordinación previa.

### UX/UI App Usuario + App Admin

- Área: diseño, flujos, Compose/UI y documentación de experiencia de Usuario/Admin.
- Puede trabajar en análisis, auditoría, wireframes y revisión en paralelo.
- Con dos frentes de código ya escribiendo, UX/UI no abre un tercer owner de escritura hasta liberar un slot o reasignar prioridad en Central.
- No tocar VPN/Chrome/Remote/Supabase salvo ticket separado.

## Regla de sincronización

Cada chat, antes de modificar código:

1. lee Glosh Central;
2. revisa estado Git/repositorio actual;
3. confirma Task ID, owner, base, rutas y dependencias;
4. modifica sólo su área.

Al cambiar estado, prioridad, ruta, bloqueo o cierre, ChatGPT actualiza Central en el mismo ciclo.

Los chats no necesitan comunicarse directamente entre sí: se mancomunan mediante Glosh Central + GitHub.

## Límite de escritura paralelo

Por defecto máximo dos frentes escribiendo código simultáneamente.

Los demás pueden seguir con análisis, UX, documentación, revisión o preparación de tickets sin colisionar.

## Automatizaciones

No se usa vigilancia horaria de Glosh Central como mecanismo principal de coordinación. La coordinación es event-driven:

- leer Central antes de trabajar;
- actualizar Central cuando cambia algo.

Las dos automatizaciones horarias duplicadas `Vigilar Glosh Central` fueron desactivadas el 2026-08-24.

## Estado inmediato

El A23 volvió a estar disponible y Codex reanudó el gate físico pendiente de Chrome. El resultado exacto debe revisarse antes de cambiar el cierre del ticket o autorizar el siguiente full-tunnel.
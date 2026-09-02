# START HERE

Este archivo define la entrada mínima al proyecto. No obliga a releer documentación que no sea necesaria para la tarea.

## Fuentes de verdad

- `AGENTS.md`: reglas operativas transversales y workflow ChatGPT ↔ Codex.
- Glosh Central / Control Center (`docs/AI_TASK_TRACKER.json` en la rama canónica `build/glosh-control-center-v2`): tareas, prioridades, owners, bloqueos y cierres persistentes.
- GitHub: código, commits, ramas, PR y evidencia compartida.

Los documentos históricos no pueden contradecir estas fuentes.

Si Central y GitHub/evidencia parecen discrepar, no elegir una fuente por reflejo: determinar qué dato quedó atrasado, reconciliar Central y recién después continuar con escritura.

## Base funcional y gobernanza vigente

- La base funcional de un ticket puede ser un SHA histórico para preservar código y evidencia validados.
- Esa base funcional no fija las reglas de trabajo.
- Antes de escribir, resolver en GitHub el HEAD remoto vigente de `main` y leer desde ese ref `AGENTS.md`, `START_HERE.md` y `docs/CODEX_RULES.md`, salvo que Central declare explícitamente otro ref canónico de gobernanza.
- Cuando la base funcional y la gobernanza difieran, registrar `GOVERNANCE REF/SHA` en el preflight. Las copias históricas del worktree no prevalecen para workflow/coordinación.
- No copiar ni cherry-pickear gobernanza actual a una rama funcional sólo para cumplir esta regla.
- El `GOVERNANCE SHA` queda fijado durante el lote y se actualiza en el siguiente checkpoint, salvo override explícito de ChatGPT/Central por seguridad o coordinación.

## Preflight obligatorio de código

Antes de modificar código o preparar un ticket Codex que implique escritura:

1. comprobar Central vigente;
2. comprobar GitHub vigente;
3. identificar writers activos;
4. confirmar owner, base SHA, worktree/rama y rutas de la tarea;
5. resolver y registrar `GOVERNANCE REF/SHA` cuando difiera de la base funcional;
6. no iniciar un tercer frente si ya existen 2 writers de código.

Para tareas sólo de lectura o documentación, consultar Central cuando el resultado dependa de estado, prioridad, owner, bloqueo, cierre o trabajo paralelo.

## Lectura mínima por tarea

1. `AGENTS.md`, si no está ya cargado por el agente.
2. El preflight anterior cuando exista escritura de código.
3. `docs/AREAS.md` cuando haya que ubicar/modificar código o evaluar impacto entre módulos.
4. Abrir sólo la documentación especializada que aporte algo:
   - `docs/DEV_FLOW.md`: builds, tests, APKs, versionado o publicación.
   - `docs/CODEX_MAP.md`: ubicación rápida cuando no se conoce la ruta.
   - `docs/HANDOFF_ACTUAL.md`: contexto técnico sólo si Central/GitHub/ticket no alcanzan; puede ser histórico.
   - `docs/BACKLOG_PRODUCTO.md`: planificación/ideas/priorización, no para ejecutar un ticket ya definido.
   - `docs/compatibility/README.md`: compatibilidad/configuración/pruebas Android.
   - `docs/CODEX_RULES.md`: reglas locales/especiales de Codex.

No hacer un barrido obligatorio de todo el corpus antes de cada ticket.

## Forma de trabajo

- Diagnosticar causa raíz antes de escribir código.
- Trabajar en el menor scope que resuelva el objetivo, sin tocar áreas ajenas.
- Preferir lotes coherentes; no fragmentar artificialmente en microtickets ni agrupar temas no relacionados.
- No reabrir una arquitectura cerrada salvo evidencia nueva, regresión o requisito que la invalide.
- Revisar todo el repo sólo para una auditoría que realmente lo necesite.
- Modificar la menor cantidad razonable de archivos, priorizando cohesión y mantenibilidad.
- Reutilizar evidencia/tests previos cuando el nuevo diff no los invalida.
- Usar tests/builds proporcionales al riesgo y al diff.
- Central registra estados persistentes; no generar churn `pending → in_progress → done` para ejecuciones transitorias del mismo ciclo.
- Cada chat especializado de ChatGPT mantiene el estado persistente de su frente; Dirección General audita el conjunto. Codex sólo modifica Central con autorización expresa.
- Cuando un resultado revisado cambie materialmente estado, owner, bloqueo, ruta o cierre, hacer postflight en Central antes del siguiente trabajo dependiente.

## Build, APK y publicación

- Si sólo cambian docs/reglas: no compilar, no incrementar `versionCode`, no generar/publicar APK y no ejecutar gates Android sin motivo.
- Si cambia código Android: seguir `docs/DEV_FLOW.md` y el ticket.
- Un PASS técnico se publica en `review/*-final` cuando ChatGPT necesita auditarlo; ese push no destructivo está preautorizado.
- PR, merge, integración a `main`, publicación DEV de producto, Production, deploy, borrados destructivos y gastos son pasos separados y no se infieren de un PASS técnico.

## Prioridad permanente

- llegar a la meta con el menor número de interacciones;
- minimizar tokens/contexto y trabajo repetido;
- aislar trabajo paralelo;
- mantener evidencia auditable;
- no sacrificar seguridad o corrección por ahorrar una prueba realmente necesaria.

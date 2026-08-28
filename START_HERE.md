# START HERE

Este archivo define la entrada mínima al proyecto. No obliga a releer documentación que no sea necesaria para la tarea.

## Fuentes de verdad

- `AGENTS.md`: reglas operativas transversales y workflow ChatGPT ↔ Codex.
- Glosh Central / Control Center (`docs/AI_TASK_TRACKER.json` en su rama canónica): tareas, prioridades, owners, bloqueos y cierres persistentes.
- GitHub: código, commits, ramas, PR y evidencia compartida.

Los documentos históricos no pueden contradecir estas fuentes.

Si Central y GitHub/evidencia parecen discrepar, no elegir una fuente por reflejo: determinar qué dato quedó atrasado, reconciliar Central y recién después continuar con escritura.

Las ramas son punteros mutables. Toda identidad canónica o congelada debe registrar el SHA completo; ver `docs/GLOSH_CANON_BRANCH_BOUNDARIES.md` para las anclas reconciliadas y sus límites de evidencia.

## Preflight obligatorio de código

Antes de modificar código o preparar un ticket Codex que implique escritura:

1. comprobar Central vigente;
2. comprobar GitHub vigente;
3. identificar writers activos;
4. confirmar owner, base SHA y rutas de la tarea;
5. no iniciar un tercer frente si ya existen 2 writers de código.

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

# START HERE

Este archivo define la entrada minima al proyecto. No obliga a releer documentacion que no sea necesaria para la tarea.

## Fuentes de verdad

- `AGENTS.md`: reglas operativas transversales y workflow ChatGPT ↔ Codex.
- Glosh Central / Control Center (`docs/AI_TASK_TRACKER.json` en su rama canonica): tareas, prioridades, owners, bloqueos y cierres.
- GitHub: codigo, commits, ramas, PR y evidencia compartida.

Si dos documentos historicos contradicen estas fuentes, prevalecen las fuentes anteriores.

## Lectura minima por tarea

1. `AGENTS.md`, si no esta ya cargado por el agente.
2. Glosh Central solo cuando la tarea dependa de estado actual, prioridad, owner, bloqueo, cierre o trabajo paralelo.
3. `docs/AREAS.md` cuando haya que ubicar/modificar codigo o evaluar impacto entre modulos.
4. Abrir solamente la documentacion especializada que aporte algo a la tarea:
   - `docs/DEV_FLOW.md`: builds, tests, APKs, versionado o publicacion.
   - `docs/CODEX_MAP.md`: ubicacion rapida cuando no se conoce la ruta.
   - `docs/HANDOFF_ACTUAL.md`: contexto tecnico actual solo si Central/GitHub/ticket no alcanzan.
   - `docs/BACKLOG_PRODUCTO.md`: planificacion/ideas/priorizacion, no para ejecutar un ticket ya definido.
   - `docs/compatibility/README.md`: compatibilidad/configuracion/pruebas Android.
   - `docs/CODEX_RULES.md`: reglas locales/especiales de Codex cuando corresponda.

No hacer un barrido obligatorio de todos estos documentos antes de cada ticket.

## Forma de trabajo

- Diagnosticar causa raiz antes de escribir codigo.
- Trabajar en el menor scope que resuelva el objetivo, sin tocar areas ajenas.
- Preferir lotes coherentes que cierren un problema completo; no fragmentar artificialmente en microtickets ni agrupar temas no relacionados.
- No reabrir una arquitectura ya cerrada salvo evidencia nueva, una regresion o un requisito que la invalide.
- Revisar todo el repo solo para una auditoria que realmente lo necesite; en tareas normales abrir rutas/archivos del area afectada.
- Modificar la menor cantidad razonable de archivos, priorizando cohesion y mantenibilidad sobre minimizacion mecanica.
- Reutilizar evidencia/tests previos cuando el nuevo diff no invalida esa evidencia; no repetir gates caros por costumbre.
- Usar tests/builds proporcionales al riesgo y al diff. Un gate global ajeno/preexistente no bloquea automaticamente un scope que esta limpio y validado.
- Central registra estados persistentes; no generar churn `pending → in_progress → done` para ejecuciones Codex transitorias del mismo ciclo.

## Build, APK y publicacion

- Si solo cambian docs/reglas: no compilar, no incrementar `versionCode`, no generar/publicar APK y no ejecutar gates Android sin motivo.
- Si cambia codigo Android: seguir `docs/DEV_FLOW.md` y el ticket. Build/test y APK fisica pueden hacerse desde el worktree validado.
- Un PASS tecnico se publica en una rama `review/*-final` cuando ChatGPT necesita auditarlo; este push de review esta preautorizado.
- PR, merge, integracion a `main`, publicacion DEV de producto, Production, deploy, borrados destructivos y gastos son pasos separados y no se infieren de un PASS tecnico.

## Prioridad permanente

- llegar a la meta con el menor numero de interacciones;
- minimizar tokens/contexto y trabajo repetido;
- aislar trabajo paralelo;
- mantener evidencia auditable;
- no sacrificar seguridad/correccion por ahorrar una prueba realmente necesaria.

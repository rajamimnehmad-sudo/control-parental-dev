# AGENTS.md

## Coordinacion vigente

Antes de cualquier tarea, tratar como fuentes vigentes:

1. **Glosh Central / Control Center**: `build/glosh-control-center-v2` + `docs/AI_TASK_TRACKER.json` para tareas, prioridades, owners, bloqueos, estados y ruta actual.
2. **GitHub + estado Git local actual**: fuente compartida de verdad para codigo, commits, ramas, PR y evidencia. El trabajo local no publicado puede ser mas nuevo; verificarlo antes de asumir que falta.
3. `docs/CURRENT_COORDINATION.md`: reglas operativas resumidas para entrar al repo sin usar snapshots historicos como estado actual.

`docs/HANDOFF_ACTUAL.md` tiene un snapshot tecnico con corte 2026-08-11 y **no debe tratarse como coordinacion vigente**. Usarlo solo como referencia historica cuando corresponda.

Antes de cualquier tarea en este repositorio:

1. Leer `docs/CURRENT_COORDINATION.md` y `START_HERE.md`.
2. Revisar Glosh Central / Control Center y el estado Git actual antes de modificar codigo.
3. Usar `docs/AREAS.md` para identificar el area exacta afectada.
4. Abrir solo los archivos necesarios de esa area.
5. No revisar todo el repo salvo pedido explicito.
6. No tocar areas no relacionadas.
7. Diagnosticar causa raiz antes de escribir codigo.
8. Modificar la menor cantidad posible de archivos.
9. Si solo cambian docs, no compilar, no incrementar versionCode y no publicar APK.

Para planificacion, captura de ideas o seleccion de tickets:

- Leer `docs/BACKLOG_PRODUCTO.md` como memoria de producto, no como estado operativo actual.
- Leer Glosh Central para decidir que esta activo ahora.
- No escribir codigo hasta que el ticket tenga owner unico, alcance vigente y autorizacion correspondiente.

## Trabajo paralelo

- Un unico owner de escritura por tarea.
- Por defecto, maximo dos frentes escribiendo codigo simultaneamente.
- Aislar trabajo paralelo por rama/worktree cuando corresponda.
- Verificar Task ID, base SHA, owner, alcance, rutas permitidas y dependencias antes de escribir.
- No pisar trabajo ajeno ni limpiar el entorno para despejarlo.
- Prohibido usar reset, stash, rebase, force-push, limpieza masiva o revertir cambios desconocidos para resolver colisiones.
- Cambios ajenos no relacionados no obligan a detenerse; una colision real si.

## Cierre y coordinacion

- Codex termina tecnicamente una tarea como PASS, BLOCKED o FAILED.
- PASS de Codex no equivale a cierre final hasta revision de ChatGPT sobre diff, tests y evidencia.
- Codex no modifica Glosh Central salvo autorizacion explicita del ticket.
- Todo cambio material de ruta, prioridad, estado, tarea, bloqueo o cierre debe reflejarse en Glosh Central en el mismo ciclo.
- No realizar push, PR, merge, Production, deploy, borrados destructivos, gastos ni otras acciones sensibles sin autorizacion explicita del usuario.

## Esfuerzo de razonamiento

- Usar el menor esfuerzo suficiente para cada tarea.
- Si una tarea requiere esfuerzo medio, alto o superior por riesgo, seguridad o complejidad, indicarlo antes de comenzar cuando corresponda.
- No pedir esfuerzo mayor para consultas, lectura o planificacion que puedan resolverse correctamente con esfuerzo bajo.

## Autorizaciones historicas

Las autorizaciones de tickets viejos no son autorizaciones permanentes. En particular, cualquier permiso anterior para commit/push/publicacion DEV debe considerarse historico y volver a validarse contra Glosh Central y las instrucciones actuales del usuario antes de reutilizarse.

## Flujo local

- Antes de modificar codigo, hacer un control Git liviano: `status`, rama, worktrees y commits recientes.
- Buscar ramas o commits sueltos en profundidad solo si aparece una inconsistencia o el ticket lo requiere.
- Mantener cambios aislados y faciles de revisar.
- Los APK de entrega deben construirse desde una base explicitamente aprobada y con versionCode coordinado; no asumir que un worktree temporal es la base de entrega.
- Proponer respaldo remoto cuando exista riesgo concreto de perdida o al cerrar un hito estable; el usuario decide si se sube.

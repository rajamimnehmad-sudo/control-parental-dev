# AGENTS.md

Antes de cualquier tarea en este repositorio:

1. Leer START_HERE.md.
2. Revisar Glosh Central / Control Center vigente (`docs/AI_TASK_TRACKER.json` en su rama canonica) cuando la tarea afecte estado, prioridad, owner, bloqueo, cierre o trabajo paralelo.
3. Usar docs/AREAS.md para identificar el area exacta afectada.
4. Abrir solo los archivos necesarios de esa area.
5. No revisar todo el repo salvo pedido explicito.
6. No tocar areas no relacionadas.
7. Diagnosticar causa raiz antes de escribir codigo.
8. Modificar la menor cantidad razonable de archivos.
9. Si solo cambian docs/reglas, no compilar, no incrementar versionCode y no publicar APK.

Para planificacion, captura de ideas o seleccion de tickets:

- Leer `docs/BACKLOG_PRODUCTO.md` solo cuando realmente se este planificando/priorizando.
- Tratar Glosh Central / Control Center como verdad de coordinacion y GitHub como verdad compartida de codigo, commits, ramas, PR y evidencia.
- `docs/HANDOFF_ACTUAL.md` puede aportar contexto tecnico, pero no debe contradecir el estado vigente de Central/GitHub.
- No escribir codigo sin una tarea/autorizacion vigente del usuario o de ChatGPT Central dentro del alcance ya autorizado.

## Esfuerzo de razonamiento

- Usar el menor esfuerzo suficiente para cada tarea.
- ChatGPT Central elige el esfuerzo Codex segun riesgo, complejidad y necesidad de entorno.
- Reservar esfuerzos altos para lotes complejos, seguridad, gates fisicos o cierres integrales; no gastar esfuerzo alto en lectura, planificacion o publicacion mecanica de evidencia.

## Coordinacion y trabajo paralelo

- Glosh Central / Control Center es la fuente central de coordinacion para tareas, prioridades, owners, bloqueos y estados.
- GitHub es la fuente compartida de verdad para codigo, commits, ramas, PR y evidencia.
- Antes de modificar codigo, revisar el estado actual del repositorio y Central; no asumir que informacion anterior sigue vigente.
- Cada tarea de codigo tiene un unico owner de escritura.
- Por defecto, maximo 2 frentes pueden estar escribiendo codigo simultaneamente. Otros frentes pueden revisar, analizar, ejecutar CI, pruebas o dispositivos.
- Cuando exista trabajo paralelo, aislar cada tarea en rama/worktree propio y especificar cuando corresponda Task ID, base SHA y rutas permitidas.
- Cambios ajenos no relacionados no obligan a detenerse. Ante una colision real, detenerse antes de pisar cambios.
- Nunca usar reset, stash, rebase, force-push, limpieza masiva, reformateo global ni revertir cambios desconocidos para despejar el entorno.
- Codex termina tecnicamente una tarea como PASS, BLOCKED o FAILED. PASS no significa cierre definitivo hasta que ChatGPT revise diff/codigo, tests y evidencia necesaria.
- Codex no modifica Glosh Central salvo autorizacion explicita del ticket. ChatGPT Central mantiene y sincroniza el estado final del proyecto.
- Todo cambio material/persistente de ruta, prioridad, estado, tarea, bloqueo o cierre debe reflejarse en Glosh Central en el mismo ciclo.
- No sincronizar `in_progress` por ejecuciones transitorias de Codex que empiezan y terminan dentro del mismo ciclo/handoff. En esos casos la tarea puede permanecer `pending` mientras corre y Central se actualiza una sola vez al recibir/revisar el resultado.
- Usar `in_progress` solo cuando el estado activo tenga valor persistente de coordinacion: trabajo que cruza interacciones/ciclos, owner que seguira ocupando rutas, frente largo o necesidad real de advertir a otros agentes sobre rutas reservadas.
- La ausencia de `in_progress` no elimina el owner unico ni la obligacion de Codex de verificar owner/rutas/worktrees antes de escribir.

## Workflow ChatGPT ↔ Codex y ramas review

Regla transversal permanente del proyecto, autorizada por el usuario el 2026-08-25:

- ChatGPT resuelve directamente todo lo viable: analisis, arquitectura, revision, UX/UI, documentacion, definicion de gates y cambios que no requieren el entorno local.
- Codex se reserva principalmente para trabajo que necesita Mac/local: codigo, compilaciones, tests, ADB, dispositivos, emuladores, scripts, entrenamiento y benchmarks.
- Los tickets deben agrupar lotes coherentes para evitar interacciones puntuales innecesarias.

### Ticket delta por defecto

- Todo ticket Codex hereda automaticamente `AGENTS.md`, `START_HERE.md`, `docs/CODEX_RULES.md` y las reglas generales del proyecto. NO repetir esas prohibiciones/reglas dentro de cada prompt.
- El ticket debe contener solo el delta necesario: Task ID/objetivo; owner/base/worktree/rutas cuando sean materialmente necesarios; cambios o restricciones especificas del lote; gates particulares; criterio de PASS/handoff.
- No repetir contexto historico que Codex puede obtener de Central/GitHub ni listas extensas de reglas globales ya vigentes.
- Si una tarea es de alto riesgo (seguridad, navegador, dispositivo, datos, release) se puede ampliar el ticket tanto como sea necesario; la compacidad nunca reemplaza controles tecnicos reales.

### PASS y review

- Cuando un lote Codex termina en PASS tecnico y existe codigo que ChatGPT debe revisar, el MISMO ticket debe dejar commit(s) cohesivos y publicar una rama remota aislada `review/...-final` al estado validado, verificando el SHA remoto.
- La evidencia documental separada es proporcional: se exige cuando aporta trazabilidad real (seguridad, navegador/medios, dispositivo, performance, migraciones, release o cierre relevante). Un fix rutinario con diff/tests suficientes no necesita crear un documento largo solo por costumbre.
- El push no destructivo de ramas `review/*` y ramas de preservacion necesarias para revision/handoff queda PREAUTORIZADO de forma permanente dentro de Glosh. No hace falta pedir un OK nuevo en cada ticket.
- No abrir una interaccion Codex adicional solo para hacer un push de review que podia haberse realizado al final del mismo ticket.
- Si el resultado es BLOCKED o FAILED, puede publicarse una rama de preservacion/review cuando sea necesario para que ChatGPT inspeccione el estado exacto, sin ocultar el fallo ni ampliar scope.

### Handoff compacto por defecto

Codex no debe recontar el ticket ni enumerar informacion que ChatGPT obtiene del diff. El handoff normal puede limitarse a:

- `STATUS`.
- `BASE`.
- `FUNCTIONAL SHA`.
- `REMOTE REVIEW BRANCH` + `REMOTE HEAD`.
- `VALIDATION` (tests/gates relevantes en una linea o resumen corto).
- `PHYSICAL` solo si hubo gate fisico/lab.
- `RESIDUALS` o `BLOCKER` solo si existen.

Agregar version/APK/hash, evidencia, rollback, archivos o detalles tecnicos solo cuando sean relevantes para ese lote o cuando hubo una desviacion del scope esperado.

### Revision ChatGPT

- ChatGPT revisa siempre el diff remoto exacto y el codigo circundante critico que el diff pueda afectar.
- La profundidad es proporcional al riesgo: no releer archivos/areas enteras sin necesidad; en seguridad/navegador/datos/lifecycle se amplia la auditoria cuando corresponde.
- ChatGPT decide PASS FINAL / follow-up y sincroniza Central una sola vez por cambio material.
- PR, merge, cambios directos a `main` que no sean documentacion de reglas expresamente solicitada, Production, deploy, publicaciones de producto, borrados destructivos y gastos siguen requiriendo autorizacion especifica/controlada. La preautorizacion anterior NO los incluye.

## Flujo local vigente

- Antes de modificar codigo, hacer un control Git liviano (`status`, rama, worktrees, commits recientes y owner/rutas de la tarea). Profundizar solo si aparece una inconsistencia real.
- Trabajar en rama/worktree aislado cuando exista riesgo de concurrencia o cuando el ticket lo indique.
- Versionar, compilar y probar desde el worktree/rama de la tarea; no integrar automaticamente a `main` local/remoto como requisito de cierre tecnico.
- Una rama `review/*-final` es una superficie de auditoria y preservacion, no una autorizacion de merge.
- Los APK de gates fisicos pueden construirse desde el worktree validado cuando el ticket lo exige; la entrega/release final de producto sigue su gate de integracion correspondiente.

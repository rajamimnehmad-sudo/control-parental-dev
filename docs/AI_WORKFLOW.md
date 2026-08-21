# AI WORKFLOW — Glosh

## Modo operativo oficial

El flujo normal es **manual y controlado**:

**Usuario pide → ChatGPT diseña el ticket → usuario copia el ticket en Codex → Codex ejecuta → Codex deja PR/handoff en GitHub → usuario dice “ya” en ChatGPT → ChatGPT audita → siguiente ticket.**

El autorun local queda fuera del flujo normal. No se debe depender de `launchd`, watchers ni cambios en `docs/AI_NEXT_TICKET.md` para iniciar trabajo. `AI_NEXT_TICKET.md` puede conservarse como referencia histórica/coordinación, pero ChatGPT no lo usa como mecanismo de ejecución salvo decisión explícita futura del usuario.

## Roles

### Usuario / owner
- Define objetivos de producto y negocio.
- Copia en Codex el ticket que ChatGPT entrega en el chat.
- Autoriza Production, publicación, gastos, borrados destructivos, merges importantes y pruebas físicas cuando sean necesarias.
- Cuando Codex termina, normalmente solo necesita decir **“ya”** en ChatGPT.

### ChatGPT / jefe técnico central
- Mantiene visión global, arquitectura, prioridades y máximo de frentes activos.
- Decide si Codex realmente hace falta.
- Escribe tickets completos, listos para copiar, con alcance, esfuerzo, permisos, límites y criterio de cierre.
- Revisa el trabajo real de Codex en GitHub: diff, archivos, tests, PR y handoff; no confía solo en el resumen.
- Aprueba, rechaza o emite una corrección.
- Mantiene Control Center y backlog.

### Codex / ejecutor local
- Ejecuta únicamente el ticket que el usuario pega en la sesión.
- Trabaja en la Mac y usa código/tests/Gradle/scripts/ADB solo cuando el ticket lo autoriza.
- No inventa el siguiente trabajo.
- Antes de detenerse deja evidencia suficiente en GitHub para que ChatGPT pueda auditar sin copiar reportes manualmente.

### GitHub
- Fuente compartida de verdad para código, ramas, PRs y handoffs.
- `main` representa estado aprobado/integrado; no trabajar directamente allí sin autorización.

## Separación de verdades

Para evitar fuentes superpuestas:

- **Glosh Central / tracker estructurado:** verdad sobre intención, prioridad, asignación, estado y bloqueos.
- **GitHub:** verdad sobre código compartido, commits, ramas, PR y evidencia publicada.
- **Worktree local:** trabajo provisional; no se considera compartido ni cerrado hasta quedar publicado/evidenciado.

Los handoffs explican. GitHub demuestra. Glosh Central coordina.

## Regla permanente — Glosh Central / Control Center

Glosh Central es el tablero vivo y visible de la ruta del proyecto. **Todo cambio material de ruta debe reflejarse en Glosh Central en el mismo ciclo de trabajo.**

El dato operativo canónico de Glosh Central debe ser **estructurado y pequeño** (actualmente `docs/AI_TASK_TRACKER.json`); la UI lo representa. No usar un documento narrativo grande como tracker concurrente.

Esto incluye como mínimo:
- tareas nuevas o eliminadas;
- cambio de prioridad;
- cambio de estado (`pending`, `in_progress`, `blocked`, `done`);
- bloqueos y desbloqueos;
- cierres/aprobaciones;
- cambio del frente activo o del siguiente lote;
- cambios del workflow que alteren cómo se trabaja.

“Mismo ciclo” significa:
1. al crear/asignar/cambiar materialmente una tarea, ChatGPT actualiza Central en ese mismo turno de coordinación;
2. al auditar un resultado técnico, ChatGPT actualiza su estado final en ese mismo turno;
3. un `PASS` de Codex no convierte por sí solo una tarea en `done`: queda como evidencia técnica hasta revisión central.

Reglas:
1. ChatGPT actualiza el tracker/Control Center directamente cuando tenga acceso por GitHub; no abrir una sesión Codex solo para mantener el tablero.
2. Codex consulta Central antes de trabajar, pero no lo modifica salvo autorización explícita del ticket.
3. Glosh Central es de solo lectura para el usuario: refleja la ruta decidida, no ejecuta acciones de producto.
4. El detalle debe mantenerse compacto; evidencia larga queda en PR/handoff/documentación correspondiente.
5. Si por una limitación técnica no puede actualizarse el tablero en ese momento, ChatGPT debe decirlo explícitamente y corregirlo en cuanto vuelva a estar disponible.

## Coordinación paralela — contrato mínimo por tarea

Para tareas con escritura de código, el ticket debe incluir cuando corresponda:

- `task_id` inmutable;
- `coordination_revision` o revisión equivalente;
- objetivo observable y criterio PASS/FAIL;
- owner único de escritura;
- branch y **base SHA exacto** cuando el riesgo de colisión lo justifique;
- worktree previsto cuando haya trabajo paralelo;
- rutas/áreas permitidas y, si aporta seguridad, rutas prohibidas;
- dependencias o bloqueos relevantes;
- permisos sensibles del ticket (push/PR/deploy/Production/APK/gastos);
- recursos exclusivos si aplica (S22, A23, emulador, migraciones, Supabase, hotspot compartido).

No convertir cada ticket en una ficha burocrática de 20 campos: usar solo los campos que reducen riesgo real para ese trabajo.

### Reglas de escritura paralela

1. Una tarea tiene un único owner de escritura.
2. Cada tarea de código debe usar rama propia; con trabajo paralelo o checkout principal sucio, usar worktree propio.
3. No escribir en el checkout principal si contiene cambios ajenos/no relacionados.
4. Dos tareas pueden compartir área solo si no comparten archivos/contratos modificables o existe coordinación explícita.
5. Hotspots compartidos (`settings.gradle.kts`, Gradle raíz/catálogos, contratos/modelos comunes, políticas compartidas, migraciones, versionado y coordinación central) requieren reserva/serialización explícita.
6. Antes del primer cambio, y de nuevo justo antes de tocar archivos si pasó tiempo relevante, Codex valida **su asignación concreta**: Task ID/revisión, base, owner, rutas, dependencias y autorizaciones. No hace falta releer todo el tablero si cambió una tarea ajena.
7. Si cambia alcance, base, owner, rutas, dependencia o autorización de su tarea, se detiene y reporta.
8. Un cambio fuera de las rutas permitidas exige ampliar el ticket antes de editar.
9. Nunca usar `reset`, `stash`, `rebase`, force-push, limpieza masiva, reformateo global ni revertir cambios desconocidos para despejar el entorno. Si trabajo ajeno interfiere, preservar y reportar.
10. PR y branch deben incluir el Task ID cuando sea práctico.
11. Una tarea no puede tener dos owners de escritura activos.
12. Ramas/reservas abandonadas pueden marcarse vencidas, pero nunca borrarse automáticamente.

### Cantidad de frentes

Default operativo:
- máximo **2 frentes de escritura** simultáneos;
- pueden coexistir frentes adicionales de lectura/revisión, CI, benchmark o dispositivo si no pisan recursos/archivos;
- 3–4 frentes escribiendo solo cuando el aislamiento es inequívoco.

La integración de contratos compartidos se serializa: un único frente/integrador resuelve el cruce final.

## Regla permanente — Preservar datos útiles para GloshIA

Un **reset operativo** nunca incluye automáticamente datos que puedan servir para entrenar, calibrar, evaluar o auditar GloshIA.

Se consideran preservables, aunque ya no pertenezcan a usuarios/dispositivos activos:
- datasets y muestras;
- imágenes/objetos de calibración permitidos por el flujo de privacidad;
- etiquetas `show` / `hide` / `unsure` u otras etiquetas humanas;
- sesiones, items y owner reviews de GloshIA;
- métricas, scores, thresholds, resultados de benchmark y evidencia de calibración;
- outboxes/recibos/evidencia remota que permitan reconstruir o mejorar el dataset;
- documentación o artefactos que permitan reproducir entrenamiento/evaluación.

Reglas:
1. Cuando el usuario pida “reset”, “borrar todo”, “volver a cero” o equivalente, interpretar por defecto **reset de operación**: comunidades, cuentas operativas, admins comunitarios, dispositivos, licencias, códigos, pedidos, sesiones de dispositivo y telemetría puramente operacional.
2. **No borrar material útil para GloshIA** salvo autorización explícita separada que mencione también datos de entrenamiento/calibración/IA.
3. Antes de cualquier limpieza destructiva, clasificar tablas/buckets en `operativo borrable` vs `IA/evidencia preservable` y excluir este segundo grupo.
4. Si existe duda razonable sobre si un dato puede servir para GloshIA, **preservarlo** y reportarlo.
5. Si alguna limpieza borra por error evidencia de IA, detener nuevas limpiezas de IA, registrar el incidente, buscar copias/exportaciones/backups/evidencia en GitHub/Supabase y actualizar Control Center.

## Formato de ticket

Cada ticket entregado por ChatGPT debe ser autocontenido y listo para copiar en Codex. Debe incluir, cuando corresponda:

- identificador y objetivo;
- revisión/asignación si hay trabajo paralelo;
- rama/base SHA o regla para crearla;
- worktree cuando sea necesario;
- alcance exacto y áreas/rutas permitidas;
- `Esfuerzo Codex`;
- permisos y prohibiciones;
- tests/gates mínimos;
- criterio de cierre;
- handoff requerido.

No obligar a Codex a releer un backlog enorme ni documentación histórica si el ticket ya contiene lo necesario. Puede leer archivos del repo únicamente cuando son relevantes para ejecutar el trabajo.

## Esfuerzo Codex

ChatGPT elige el menor esfuerzo suficiente:

- **Bajo / low:** documentación, inspecciones simples, cambios mecánicos o riesgo bajo.
- **Medio / medium:** implementación normal bien especificada. Default.
- **Alto / high:** debugging difícil, integración compleja, concurrencia/estado o refactor importante.
- **Extra alto / xhigh:** seguridad crítica, arquitectura, migraciones complejas o fallos especialmente difíciles.

No usar `high/xhigh` por costumbre. Si Codex considera insuficiente el esfuerzo indicado, debe reportarlo en vez de ampliarlo por su cuenta.

## Regla de costo y eficiencia

Este workflow existe para abaratar el desarrollo, no encarecerlo.

1. ChatGPT resuelve directamente con GitHub/web/conectores todo lo que no requiera la Mac/Codex.
2. Agrupar trabajo relacionado en lotes coherentes antes que microtickets.
3. Contexto mínimo: no cargar backlog, handoffs históricos ni áreas no relacionadas por costumbre.
4. Tests estrechos por módulos afectados; gate final solo cuando aporta evidencia nueva.
5. No repetir evidencia válida si el cambio no toca esa capa.
6. No compilar/enviar APK por cada cambio pequeño.
7. Usar el menor esfuerzo de Codex suficiente.
8. Handoffs cortos: estado, evidencia, riesgos y próximo paso.
9. Si una automatización o proceso aumenta el consumo sin beneficio proporcional, simplificarlo o retirarlo.
10. Las tareas de coordinación que ChatGPT puede resolver sin Codex tienen costo objetivo de **0 sesiones Codex**.

La optimización del runner queda documentada como trabajo experimental previo, pero no define el flujo normal.

## Cierre obligatorio de Codex

### Ticket con cambios de código

Codex debe:
1. trabajar en rama `review/<ticket>` o la rama indicada;
2. hacer commits coherentes;
3. subir la rama solo si el ticket autoriza push;
4. abrir/actualizar PR solo si el ticket lo autoriza;
5. dejar resumen de archivos/áreas, tests/comandos, resultado, riesgos, branch/commit y pruebas físicas pendientes.

El resultado técnico de Codex se reporta como `PASS`, `BLOCKED` o `FAILED`; **no equivale a aprobación/cierre central**.

### Ticket read-only / auditoría

Puede actualizar únicamente `docs/AI_CODEX_HANDOFF.md` en `coordination/ai-control` como excepción de coordinación, si el ticket lo autoriza, sin alterar el estado auditado.

Todo handoff debe indicar:
- ticket ejecutado;
- PASS / BLOCKED / FAILED;
- rama + HEAD;
- resumen y evidencia mínima;
- riesgos/bloqueos;
- siguiente acción propuesta, sin ejecutarla.

Después Codex se detiene.

## Cuando el usuario diga “ya” en ChatGPT

ChatGPT revisa directamente:
1. `docs/AI_CODEX_HANDOFF.md`;
2. PR/rama del ticket;
3. diff/código real relevante;
4. tests/evidencia;
5. aprueba, pide corrección o prepara el siguiente ticket listo para copiar.

El usuario no necesita pegar el reporte de Codex salvo que GitHub falle.

## Manejo de bloqueos

Ante error inesperado, conflicto o problema arquitectónico Codex no hace reescrituras amplias a ciegas. Preserva evidencia, deja handoff y se detiene para decisión de ChatGPT.

## Pruebas físicas y APKs

Una prueba física se pide solo si aporta evidencia que no puede obtenerse automáticamente y después de gates automáticos pertinentes.

Para el Samsung S22 Ultra, el canal preferido de APK sigue siendo Taildrop/Tailscale. **ChatGPT avisa antes de cualquier envío** y no se envía hasta que el usuario confirme que el dispositivo está listo.

No Production, deploy, gastos, borrados destructivos ni merges importantes sin autorización explícita del usuario.

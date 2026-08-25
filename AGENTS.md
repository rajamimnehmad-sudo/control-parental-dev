# AGENTS.md

Antes de cualquier tarea en este repositorio:

1. Leer START_HERE.md.
2. Revisar Glosh Central / Control Center vigente (`docs/AI_TASK_TRACKER.json` en su rama canonica) cuando la tarea afecte estado, prioridad, owner, bloqueo, cierre o trabajo paralelo.
3. Usar docs/AREAS.md para identificar el area exacta afectada.
4. Abrir solo los archivos necesarios de esa area.
5. No revisar todo el repo salvo pedido explicito.
6. No tocar areas no relacionadas.
7. Diagnosticar causa raiz antes de escribir codigo.
8. Modificar la menor cantidad posible de archivos.
9. Si solo cambian docs, no compilar, no incrementar versionCode y no publicar APK.

Para planificacion, captura de ideas o seleccion de tickets:

- Leer `docs/BACKLOG_PRODUCTO.md`.
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
- Codex termina tecnicamente una tarea como PASS, BLOCKED o FAILED. PASS no significa cierre definitivo hasta que ChatGPT revise diff/codigo, tests y evidencia.
- Codex no modifica Glosh Central salvo autorizacion explicita del ticket. ChatGPT Central mantiene y sincroniza el estado final del proyecto.
- Todo cambio material de ruta, prioridad, estado, tarea, bloqueo o cierre debe reflejarse en Glosh Central en el mismo ciclo.

## Workflow ChatGPT ↔ Codex y ramas review

Regla transversal permanente del proyecto, autorizada por el usuario el 2026-08-25:

- ChatGPT resuelve directamente todo lo viable: analisis, arquitectura, revision, UX/UI, documentacion, definicion de gates y cambios que no requieren el entorno local.
- Codex se reserva principalmente para trabajo que necesita Mac/local: codigo, compilaciones, tests, ADB, dispositivos, emuladores, scripts, entrenamiento y benchmarks.
- Los tickets deben agrupar lotes coherentes para evitar interacciones puntuales innecesarias.
- Cuando un lote Codex termina en PASS tecnico y existe codigo/evidencia que ChatGPT debe revisar, el MISMO ticket debe:
  1. dejar commits locales cohesivos (preferentemente funcional + evidencia);
  2. publicar una rama remota aislada `review/...-final` que apunte exactamente al estado validado;
  3. verificar el SHA remoto;
  4. informar base SHA, functional SHA, final/evidence SHA, rama, gates y evidencia.
- El push no destructivo de ramas `review/*` y ramas de preservacion necesarias para revision/handoff queda PREAUTORIZADO de forma permanente dentro de Glosh. No hace falta pedir un OK nuevo en cada ticket.
- No abrir una interaccion Codex adicional solo para hacer un push de review que podia haberse realizado al final del mismo ticket.
- Si el resultado es BLOCKED o FAILED, puede publicarse una rama de preservacion/review cuando sea necesario para que ChatGPT pueda inspeccionar el estado exacto, sin ocultar el fallo ni continuar ampliando scope.
- ChatGPT revisa el diff remoto, archivos criticos, tests y evidencia; despues decide PASS FINAL / follow-up y sincroniza Central en el mismo ciclo.
- PR, merge, cambios directos a `main` que no sean documentacion de reglas expresamente solicitada, Production, deploy, publicaciones de producto, borrados destructivos y gastos siguen requiriendo autorizacion especifica/controlada. La preautorizacion anterior NO los incluye.

## Ticket Android autorizado por el usuario (2026-07-14)

- Area: `feature-accessibility`, barrera antimanipulacion tipo Rimon.
- Implementar en tickets pequenos, empezando por navegacion segura desde Ajustes protegidos.
- Se permite modificar Android, ejecutar tests/builds, incrementar el `versionCode` de cada app afectada, hacer commit/push y publicar esas APKs solo en DEV. Usuario y Admin versionan de forma independiente; coordinar ambos solo cuando el cambio entra en las dos apps.
- Para alertas remotas se permite usar exclusivamente Supabase DEV `syeycayasyufedwoprea`.
- No tocar Production, no borrar datos y no incluir Service Role Key en Android.

## Flujo local vigente

- Antes de modificar codigo, hacer un control Git liviano (`status`, rama, worktrees, commits recientes y owner/rutas de la tarea). Profundizar solo si aparece una inconsistencia real.
- Trabajar en rama/worktree aislado cuando exista riesgo de concurrencia o cuando el ticket lo indique.
- Versionar, compilar y probar desde el worktree/rama de la tarea; no integrar automaticamente a `main` local/remoto como requisito de cierre tecnico.
- Una rama `review/*-final` es una superficie de auditoria y preservacion, no una autorizacion de merge.
- Los APK de gates fisicos pueden construirse desde el worktree validado cuando el ticket lo exige; la entrega/release final de producto sigue su gate de integracion correspondiente.

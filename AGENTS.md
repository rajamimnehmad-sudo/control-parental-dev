# AGENTS.md

Antes de cualquier tarea en este repositorio:

1. Leer `START_HERE.md`.
2. Si la tarea modifica código o prepara un ticket Codex que implique escritura, ejecutar el preflight obligatorio: Glosh Central vigente + GitHub vigente + writers activos + owner/base/rutas de la tarea.
3. Si la tarea es sólo lectura o documentación, consultar Central cuando dependa de estado, prioridad, owner, bloqueo, cierre o trabajo paralelo.
4. Usar `docs/AREAS.md` para identificar el área exacta afectada.
5. Abrir sólo los archivos necesarios de esa área; no revisar todo el repo salvo auditoría explícita.
6. No tocar áreas no relacionadas.
7. Diagnosticar causa raíz antes de escribir código.
8. Modificar la menor cantidad razonable de archivos, sin sacrificar cohesión ni mantenibilidad.
9. Si sólo cambian docs/reglas, no compilar, no incrementar `versionCode` y no publicar APK.

Para planificación, captura de ideas o selección de tickets:

- Leer `docs/BACKLOG_PRODUCTO.md` sólo cuando realmente se esté planificando o priorizando. Ese path puede ser un stub histórico; un snapshot archivado no es estado vigente.
- Tratar Glosh Central / Control Center como verdad de coordinación y GitHub como verdad compartida de código, commits, ramas, PR y evidencia.
- `docs/HANDOFF_ACTUAL.md` puede aportar contexto histórico, pero nunca contradecir Central/GitHub y puede ser sólo un stub archivado.
- No escribir código sin una tarea o autorización vigente del usuario o del ChatGPT dueño del frente dentro del alcance ya autorizado.

## Fuentes de verdad, preflight y reconciliación

- Glosh Central / Control Center es la fuente de verdad para tareas, prioridades, owners, bloqueos y estados persistentes.
- GitHub es la fuente compartida de verdad para código, commits, ramas, PR y evidencia.
- **Preflight obligatorio de código:** antes de modificar código o emitir un ticket Codex que implique escritura, comprobar Central vigente, GitHub vigente, writers activos y owner/base/rutas de la tarea. Si ya existen 2 frentes escribiendo código, no iniciar un tercero.
- No asumir que contexto, SHAs, owners, ramas o estados de un prompt anterior siguen vigentes.
- **Reconciliación de fuentes:** si Central y GitHub/evidencia parecen contradecirse, no asumir que una fuente invalida globalmente a la otra. Determinar qué dato quedó desactualizado, reconciliar el estado persistente en Central y recién después continuar.
- Si la discrepancia no puede resolverse con evidencia disponible, detener la escritura y reportar la contradicción exacta.

## Esfuerzo de razonamiento

- Usar el menor esfuerzo suficiente para cada tarea.
- ChatGPT Central elige el esfuerzo Codex según riesgo, complejidad y necesidad de entorno.
- Reservar esfuerzos altos para lotes complejos, seguridad, concurrencia, gates físicos o cierres integrales; no gastarlos en lectura, planificación o publicación mecánica de evidencia.

## Coordinación y trabajo paralelo

- Cada tarea de código tiene un único owner de escritura.
- Por defecto, máximo 2 frentes pueden escribir código simultáneamente. Otros frentes pueden revisar, analizar, ejecutar CI, pruebas o dispositivos.
- Cuando exista trabajo paralelo, aislar cada tarea en rama/worktree propio y especificar, cuando corresponda, Task ID, base SHA y rutas permitidas.
- Cambios ajenos no relacionados no obligan a detenerse. Ante una colisión real sobre las mismas rutas o semántica, detenerse antes de pisar trabajo ajeno.
- Nunca usar reset, stash, rebase, force-push, limpieza masiva, reformateo global ni revertir cambios desconocidos para despejar el entorno.
- **Owner de coordinación:** cada chat especializado de ChatGPT mantiene sincronizado en Glosh Central el estado persistente de su propio frente cuando tenga capacidad para hacerlo. Dirección General audita la coordinación transversal. Codex no modifica Central salvo autorización expresa del ticket.
- Todo cambio material y persistente de ruta, prioridad, tarea, owner, bloqueo, estado o cierre debe reflejarse en Central en el mismo ciclo.
- No generar churn `pending → in_progress → done` para ejecuciones transitorias que empiezan y terminan dentro del mismo ciclo/handoff.
- Usar `in_progress` sólo cuando el estado activo tenga valor persistente: trabajo que cruza interacciones/ciclos, owner que seguirá ocupando rutas, frente largo o necesidad real de advertir rutas reservadas.
- La ausencia de `in_progress` no elimina el owner único ni la obligación de verificar owner/rutas/worktrees antes de escribir.
- **Postflight obligatorio:** cuando un resultado revisado cambie materialmente estado, owner, bloqueo, ruta o cierre, sincronizar Central antes de emitir el siguiente trabajo de código dependiente. Esto no obliga a registrar estados transitorios del mismo ciclo.

## Workflow ChatGPT ↔ Codex y ramas review

Regla transversal permanente del proyecto:

- ChatGPT resuelve directamente todo lo viable: análisis, arquitectura, revisión, UX/UI, documentación, definición de gates y cambios que no requieren entorno local.
- Codex se reserva principalmente para trabajo que necesita Mac/local: código con entorno, compilaciones, tests, ADB, dispositivos, emuladores, scripts, entrenamiento y benchmarks.
- Preferir lotes coherentes que cierren un problema completo. No fragmentar artificialmente en microtickets ni microcommits y no agrupar temas no relacionados.
- Los cambios de gobernanza/documentación que forman una misma decisión deben agruparse en un lote y, cuando sea posible, en un commit cohesivo.

### Lotes Codex grandes y autónomos

- **Ticket DELTA significa prompt corto, no tarea pequeña.** El ticket describe sólo el delta específico, pero por defecto debe perseguir un objetivo terminal amplio y coherente.
- Dentro del mismo objetivo, root cause y scope autorizado, Codex continúa autónomamente los ciclos necesarios: diagnóstico → fix → tests → gate físico cuando corresponda → fix, sin handoffs intermedios por cada bug localizado.
- Codex puede corregir autónomamente bugs descubiertos durante el lote si siguen dentro del mismo frente/objetivo/root cause/scope, no requieren decisión funcional o de producto, no cambian materialmente arquitectura o base, no tocan rutas expresamente fuera de scope, no chocan con otro writer y no implican una acción sensible.
- No hacer handoff intermedio mientras se mantengan esas condiciones. El lote termina en PASS técnico completo o en BLOCKED/FAILED cuando aparece una causa realmente distinta, cambio material de arquitectura/scope/base/owner/rutas, colisión de writer, decisión funcional o acción sensible.
- STOP obligatorio antes de modificar modelo/thresholds/release authority u otra ruta expresamente fuera del scope; antes de PR/merge/main/Production/deploy/borrado/gasto; y ante cualquier cambio sensible no autorizado.
- Si una modificación tardía puede afectar escenarios ya validados, revalidar de forma focalizada esos escenarios sobre el HEAD/APK definitivo. No repetir gates que el cambio final no pueda afectar.
- Al cierre se aplican las reglas normales de PASS/review: commits cohesivos, tests/evidencia proporcionales, validación física cuando corresponda, `review/*-final` si PASS y un único handoff compacto. En BLOCKED/FAILED sólo crear rama de preservación/review si existe código o evidencia que realmente deba quedar disponible.
- ChatGPT interviene principalmente en decisiones funcionales, cambios arquitectónicos/materiales, acciones sensibles, bloqueos reales y cierres finales; no debe interrumpir el lote para revisar cada microarreglo técnico reversible contenido dentro del scope autorizado.

### Ticket delta por defecto

- Todo ticket Codex hereda automáticamente `AGENTS.md`, `START_HERE.md`, `docs/CODEX_RULES.md` y las reglas generales del proyecto. No repetir prohibiciones o contexto global, incluida la política de continuación autónoma.
- Un ticket debe contener sólo lo material: Task ID/objetivo; owner/base/worktree/rutas cuando hagan falta; restricciones específicas; gates particulares y criterio de cierre.
- No repetir historia que Codex puede obtener de Central/GitHub.
- Si una tarea es de alto riesgo —seguridad, navegador, dispositivo, datos o release— el ticket puede ampliarse lo necesario; la compacidad nunca reemplaza controles reales.

### PASS y review

- Codex termina técnicamente como PASS, BLOCKED o FAILED. PASS no es cierre definitivo hasta que ChatGPT revise el diff remoto, el código circundante crítico, tests y evidencia necesaria.
- Cuando un lote termina en PASS técnico y existe código revisable, el mismo ticket debe dejar commit(s) cohesivos, publicar una rama remota aislada `review/*-final`, verificar el SHA remoto y hacer handoff.
- La evidencia documental es proporcional: se exige cuando aporta trazabilidad real —seguridad, navegador/medios, dispositivo, performance, migraciones, release o cierre relevante—, no por rutina.
- El push no destructivo de `review/*` y ramas de preservación necesarias para revisión/handoff está preautorizado. No abrir otra interacción sólo para hacer un push que podía realizarse en el mismo ticket.
- Preferir una sola rama `review/*-final` por cierre. Sufijos `-copy`, `-check`, `-v2` o ramas intermedias sólo cuando preservan un estado realmente distinto.
- Si el resultado es BLOCKED o FAILED, puede publicarse una rama de preservación/review cuando sea necesaria para inspeccionar el estado exacto, sin ocultar el fallo ni ampliar scope.
- Ramas superseded no se borran automáticamente; sólo se eliminan con autorización explícita después de confirmar que su contenido único está preservado.

### Handoff compacto por defecto

Codex no debe recontar el ticket ni enumerar información derivable del diff. El handoff normal puede limitarse a:

- `STATUS`.
- `BASE`.
- `FUNCTIONAL SHA`.
- `REMOTE REVIEW BRANCH` y `REMOTE HEAD`.
- `VALIDATION`: resumen corto de tests/gates.
- `PHYSICAL`: sólo si hubo gate físico/lab.
- `RESIDUALS` o `BLOCKER`: sólo si existen.

Agregar versión/APK/hash, evidencia, rollback, archivos o detalle técnico sólo cuando sea material para el cierre o exista una desviación del scope.

### Revisión ChatGPT

- ChatGPT revisa siempre el diff remoto exacto y el código circundante crítico que pueda verse afectado.
- La profundidad es proporcional al riesgo; no releer áreas enteras sin necesidad.
- ChatGPT decide PASS FINAL o follow-up y sincroniza Central una sola vez por cambio material.
- Ningún ticket dependiente debe emitirse antes del postflight cuando el resultado cambió estado, owner, bloqueo, ruta o cierre.

## Código

- Priorizar cohesión, claridad y mantenibilidad.
- 500–600 líneas es una señal de revisión, no una cuota. Si un archivo supera ese rango, evaluar responsabilidades y dividir sólo cuando mejore la cohesión.
- Al agregar funcionalidad a un archivo grande, considerar primero una pieza separada si la nueva responsabilidad lo justifica.
- No reformatear ni refactorizar áreas ajenas para despejar un ticket.

## Seguridad y acciones sensibles

- No realizar Production, deploy, merge, PR, publicación de producto, borrados destructivos, gastos ni otras acciones sensibles sin autorización explícita/controlada.
- Otros pushes distintos de `review/*` o preservación requieren autorización cuando no estén cubiertos por el alcance vigente.
- No borrar datos, datasets, evidencia, calibraciones ni trabajo local desconocido sin autorización específica.
- Una rama `review/*-final` es una superficie de auditoría y preservación, no autorización de merge.

## Flujo local vigente

- Antes de modificar código, hacer un control Git liviano: status, rama, worktrees, commits recientes y owner/base/rutas de la tarea. Profundizar sólo si aparece una inconsistencia real.
- Trabajar en rama/worktree aislado cuando exista riesgo de concurrencia o el ticket lo indique.
- Versionar, compilar y probar desde el worktree/rama de la tarea; no integrar automáticamente a `main` local/remoto como requisito del cierre técnico.
- Los APK de gates físicos pueden construirse desde el worktree validado cuando el ticket lo exige; la entrega/release final sigue su gate separado.

## Decisiones

- Preguntar al usuario cuando exista una duda funcional real, una decisión de producto, riesgo relevante o acción sensible.
- Si la decisión es técnica, reversible y claramente contenida dentro del objetivo autorizado, elegir la opción segura y continuar sin generar una interacción innecesaria.

# AI WORKFLOW — Glosh

## Objetivo

Que el usuario pueda operar el proyecto con el circuito mínimo:

**Usuario pide → ChatGPT dirige → Codex ejecuta → GitHub deja evidencia → ChatGPT audita → siguiente ticket.**

El usuario no debe copiar reportes manualmente entre Codex y ChatGPT. Cuando Codex termine, el usuario debería poder decir únicamente **“ya”** y ChatGPT debe poder reconstruir el estado desde GitHub.

## Roles

### Usuario / owner
- Define objetivos de producto y negocio.
- Autoriza Production, publicación, gastos, borrados, merges importantes y pruebas físicas cuando sean necesarias.

### ChatGPT / jefe técnico central
- Define arquitectura, prioridades, tickets y criterios de aceptación.
- Revisa código real, diffs, tests, evidencia y handoffs.
- Aprueba/rechaza el trabajo y decide el siguiente ticket.
- Mantiene la ruta técnica y el Control Center.

### Codex / ejecutor técnico local
- Trabaja en la Mac.
- Modifica código cuando el ticket lo autoriza.
- Corre Gradle/tests/scripts/ADB.
- Compila APKs y diagnostica fallos.
- Deja evidencia suficiente en GitHub para que ChatGPT pueda auditar sin que el usuario copie/pegue nada.

### GitHub
- Fuente compartida de verdad para código, ramas, PRs, tickets y handoffs.
- `main` representa estado aprobado/integrado; no usarlo para trabajo directo sin autorización.

## Regla de cierre obligatoria

**Todo ticket debe dejar una huella auditable en GitHub antes de que Codex se detenga.**

### Ticket con cambios de código

Codex debe:
1. trabajar en una rama `review/<ticket>` o la rama explícitamente indicada;
2. dejar commits separados y coherentes;
3. subir la rama;
4. abrir o actualizar una PR contra la base indicada cuando corresponda;
5. usar descripción estandarizada con:
   - resumen del cambio;
   - archivos/áreas tocadas;
   - tests y comandos ejecutados;
   - resultado exacto;
   - riesgos pendientes;
   - branch + commit;
   - pruebas físicas pendientes, si aplican.

### Ticket SOLO LECTURA / auditoría / inventario

Aunque el ticket prohíba cambios de producto, **se permite una única excepción de coordinación**:

- crear o actualizar `docs/AI_CODEX_HANDOFF.md` en la rama `coordination/ai-control`;
- commit y push únicamente de ese archivo de reporte;
- no tocar ningún otro archivo de producto/documentación;
- no alterar el working tree que está siendo auditado;
- si escribir desde ese worktree implicara riesgo, usar un worktree temporal limpio o la API/CLI de GitHub sin cambiar el estado auditado.

El handoff debe contener:
- ticket ejecutado;
- fecha/hora;
- rama + HEAD observados;
- resumen;
- evidencia/comandos relevantes;
- hallazgos;
- riesgos/bloqueos;
- siguiente acción propuesta, sin ejecutarla si no está autorizada.

Después debe detenerse.

## Trigger de auditoría

Cuando el usuario diga **“ya”**, ChatGPT debe revisar directamente:
1. la PR/rama `review/<ticket>` si hubo cambios de código;
2. `docs/AI_CODEX_HANDOFF.md` en `coordination/ai-control`;
3. código/diff/tests reales que correspondan;
4. aprobar, pedir corrección o emitir el siguiente ticket.

El usuario no tiene que copiar el reporte de Codex salvo que GitHub falle.

## Manejo de bloqueos

Si Codex encuentra un error inesperado de compilación/tests, incompatibilidad o problema arquitectónico:
- no hacer reescrituras amplias a ciegas;
- preservar evidencia;
- dejar handoff en GitHub con el bloqueo exacto;
- detenerse para que ChatGPT diagnostique y decida.

## Pruebas físicas

El usuario solo prueba un APK cuando:
1. tests automatizados relevantes pasaron;
2. Codex validó técnicamente y por ADB todo lo posible;
3. ChatGPT auditó el cambio;
4. la prueba física aporta evidencia que no puede obtenerse automáticamente.

## Handoffs limpios

No acumular basura histórica.

Cada handoff debe ser corto y vigente:
- estado actual;
- qué cerró;
- qué queda;
- riesgos vigentes;
- próximo paso;
- referencias mínimas a evidencia.

Logs largos, pruebas históricas y evidencia detallada deben quedar fuera del handoff o vinculados desde él.

## No repetir evidencia válida

Si una capa ya fue demostrada y el nuevo cambio no la afecta, no repetir suites o pruebas completas sin una razón concreta.

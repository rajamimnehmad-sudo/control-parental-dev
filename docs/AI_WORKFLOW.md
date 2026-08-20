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

## Regla permanente — Glosh Central / Control Center

Glosh Central es el tablero vivo y visible de la ruta del proyecto. **Todo cambio material de ruta debe reflejarse en Glosh Central en el mismo ciclo de trabajo.**

Esto incluye como mínimo:
- tareas nuevas o eliminadas;
- cambio de prioridad;
- cambio de estado (`pending`, `in_progress`, `blocked`, `done`);
- bloqueos y desbloqueos;
- cierres/aprobaciones;
- cambio del frente activo o del siguiente lote;
- cambios del workflow que alteren cómo se trabaja.

Reglas:
1. ChatGPT actualiza el tracker/Control Center directamente cuando tenga acceso por GitHub; no abrir una sesión Codex solo para mantener el tablero.
2. La actualización debe ocurrir en el mismo ciclo en que ChatGPT decide o audita el cambio, no quedar como tarea futura por costumbre.
3. Glosh Central es de solo lectura para el usuario: refleja la ruta decidida, no ejecuta acciones de producto.
4. El detalle debe mantenerse compacto: título + contexto breve + estado/prioridad; evidencia larga queda en PR/handoff/documentación correspondiente.
5. Si por una limitación técnica no puede actualizarse el tablero en ese momento, ChatGPT debe decirlo explícitamente y corregirlo en cuanto vuelva a estar disponible.

## Formato de ticket

Cada ticket entregado por ChatGPT debe ser autocontenido y listo para copiar en Codex. Debe incluir, cuando corresponda:

- identificador y objetivo;
- rama/base o regla para crearla;
- alcance exacto y áreas permitidas;
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
3. subir la rama;
4. abrir/actualizar PR cuando corresponda;
5. dejar resumen de archivos/áreas, tests/comandos, resultado, riesgos, branch/commit y pruebas físicas pendientes.

### Ticket read-only / auditoría

Puede actualizar únicamente `docs/AI_CODEX_HANDOFF.md` en `coordination/ai-control` como excepción de coordinación, sin alterar el estado auditado.

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

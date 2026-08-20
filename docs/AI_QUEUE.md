# AI QUEUE — Glosh

## Próximo lote reservado

### AI-AUTO-HANDOFF-01

**Estado:** QUEUED — no ejecutar mientras haya un ticket vigente en curso.

**Objetivo:** instalar en la Mac el runner local definido en `docs/AI_AUTORUN_SPEC.md` para que cada cambio nuevo de `docs/AI_NEXT_TICKET.md` dispare automáticamente `codex exec`, con single-flight, deduplicación, permisos mínimos, logs acotados y servicio `launchd`.

**Orden:** promover a `docs/AI_NEXT_TICKET.md` inmediatamente después de que ChatGPT audite/cierre el ticket que está ejecutándose actualmente.

**Regla:** no interrumpir ni modificar la ejecución actual para adelantar este bootstrap.

**Resultado esperado:** una vez cerrado `AI-AUTO-HANDOFF-01`, el usuario deja de tener que escribir `ya` en Codex. ChatGPT publica ticket nuevo y la Mac lo ejecuta automáticamente.

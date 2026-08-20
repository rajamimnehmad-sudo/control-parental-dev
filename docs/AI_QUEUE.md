# AI QUEUE — Glosh

## Próximo ajuste reservado

### AI-AUTORUN-HARDEN-03

**Estado:** QUEUED — ejecutar después de cerrar la corrección/smoke actual del autorun; no reemplazar el ticket vigente mientras esté en curso.

**Esfuerzo Codex:** medium

**Objetivo:** dejar el autorun definitivamente endurecido con dos mejoras coordinadas:

1. asegurar que el fetch actualice explícitamente `refs/remotes/origin/coordination/ai-control`, evitando referencias stale;
2. leer `**Esfuerzo Codex:** low|medium|high|xhigh` de cada ticket y lanzar `codex exec -c model_reasoning_effort=<nivel>`, con fallback `medium`.

**Gates:** regresión del fetch/ref remoto, parser de esfuerzo, fallback medium, self-test de los cuatro niveles, deduplicación/single-flight intactos, reinstall idempotente y smoke real de un ticket read-only.

**Límites:** no tocar producto, Production, Supabase, APKs ni worktree Android original.

**Resultado esperado:** una vez cerrado este ajuste, ChatGPT decide el esfuerzo por ticket y la Mac aplica automáticamente ese nivel al iniciar Codex.

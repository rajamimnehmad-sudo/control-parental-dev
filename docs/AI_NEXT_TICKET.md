# AI NEXT TICKET

## AI-AUTORUN-EFFICIENCY-03

**Tipo:** infraestructura local / eficiencia Codex
**Prioridad:** importante
**Responsable:** Codex
**Revisor:** ChatGPT / jefe técnico central
**Esfuerzo Codex:** Medio

Leer primero `docs/AI_WORKFLOW.md`, `docs/AI_AUTORUN_SPEC.md` y este ticket desde `coordination/ai-control`.

## Contexto

`AI-AUTORUN-SMOKE-02` demostró inicio automático real, deduplicación y salida 0. Sin embargo el smoke read-only informó aproximadamente **42.500 tokens**, demasiado para una tarea mínima.

Este ticket debe optimizar el autorun antes de volver a lotes grandes. No tocar producto.

## Objetivos

### A — Esfuerzo por ticket

El runner debe leer el campo `Esfuerzo Codex:` del ticket y mapearlo a la configuración real de Codex CLI para esa ejecución.

Valores admitidos en tickets:
- `Bajo` → `low`
- `Medio` → `medium`
- `Alto` → `high`
- `Extra alto` → `xhigh`

Si falta o es inválido: usar `medium` y dejar log claro, sin bloquear el ticket.

Antes de implementar, inspeccionar `codex exec --help` y la configuración efectiva del CLI instalado. Usar el mecanismo soportado por esa versión (preferentemente config por ejecución, p. ej. `model_reasoning_effort`) y no depender de una suposición si el CLI muestra otra sintaxis.

### B — Reducir contexto del autorun

Diagnosticar por qué una tarea read-only mínima consumió ~42.500 tokens y reducir el contexto de arranque sin quitar seguridad ni información necesaria.

Principios:
- Codex debe comenzar con el ticket vigente y las reglas mínimas de workflow.
- No cargar por defecto backlog enorme, handoffs históricos, evidencia vieja o documentación de áreas no relacionadas.
- Leer `START_HERE.md`, `AGENTS.md`, `docs/AREAS.md` u otros archivos adicionales solo cuando las reglas del repo/CLI realmente lo exijan o el ticket lo necesite.
- Si el CLI auto-inyecta contexto desde el cwd, evaluar de forma segura si conviene lanzar desde un directorio/worktree de coordinación mínimo y otorgar acceso explícito al repo de producto mediante una opción soportada; no romper sandbox ni capacidad de editar el repo.
- No copiar secretos ni `.env` a un launcher.
- Mantener el ticket como fuente de alcance; cada ticket debe indicar las áreas concretas a leer cuando haga falta.
- No sacrificar calidad en tickets de Alto/Extra alto: la optimización busca quitar contexto irrelevante, no información necesaria.

No hacer cambios especulativos: medir/inspeccionar primero el comportamiento del CLI local y elegir la mejora más simple que reduzca contexto.

### C — Medición

Agregar al log/handoff, cuando la CLI lo exponga de forma fiable:
- esfuerzo solicitado/aplicado;
- tokens o uso reportado por ejecución;
- ticket id/SHA;
- sin registrar secretos.

Si la CLI no ofrece un dato estable de tokens, no inventarlo; registrar `unavailable`.

## Gates

1. `bash -n` y self-tests existentes PASS.
2. Nueva prueba del parser de esfuerzo para Bajo/Medio/Alto/Extra alto + default inválido.
3. Deduplicación y single-flight siguen PASS.
4. Reinstalación atómica del runner instalado.
5. Smoke read-only nuevo, automático y de alcance mínimo, ejecutado una sola vez.
6. Comparar uso/tokens del nuevo smoke contra los ~42.500 del smoke anterior si la métrica está disponible. Objetivo: reducción material; si no se logra, dejar causa concreta y no maquillar el resultado.
7. `git diff --check` PASS.

## Límites

- NO tocar código Android, DAG, Chrome Visual, Super Admin ni backend.
- NO Production, deploy, Supabase, merge, APK, ADB ni prueba física.
- NO instalar herramientas pesadas.
- NO leer/versionar secretos.
- Mantener PR #98 como rama de trabajo del autorun si es la base más limpia; commit separado para este ajuste.

## Handoff

Actualizar `docs/AI_CODEX_HANDOFF.md` con:
- `AI-AUTORUN-EFFICIENCY-03`;
- PASS / NEEDS-FIX / BLOCKED;
- rama, HEAD, PR y commit;
- causa principal del costo/contexto alto encontrada;
- cambio aplicado para reducir contexto;
- soporte de `Esfuerzo Codex` y mapeo real usado;
- evidencia de parser/gates;
- resultado del smoke automático y comparación de tokens/uso si disponible;
- confirmación de servicio activo y cero cambios de producto.

Después: **DETENERSE**.

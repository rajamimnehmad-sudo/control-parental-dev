# AI NEXT TICKET

## AI-AUTORUN-MEASURE-04

**Tipo:** medición read-only de eficiencia
**Prioridad:** normal
**Responsable:** Codex
**Revisor:** ChatGPT / jefe técnico central
**Esfuerzo Codex:** Bajo

## Autorización explícita

Se autoriza **una única ejecución real de Codex** para medir el consumo del autorun optimizado. Esta autorización cubre únicamente esta corrida mínima y su consumo normal de créditos/tokens. No autoriza ninguna otra acción paga, retry generativo ni trabajo de producto.

## Objetivo

Medir el uso real de tokens de una ejecución mínima después de las optimizaciones de `AI-AUTORUN-EFFICIENCY-03` y compararlo con el smoke previo de aproximadamente 42.500 tokens.

## Alcance

- No modificar código ni archivos de producto.
- No analizar el repositorio ni cargar backlog/handoffs históricos.
- No ejecutar tests, Gradle, builds ni herramientas de producto.
- No tocar Production, Supabase, Vercel, APKs, ADB, dispositivos ni worktrees de producto.
- Ejecutar solamente la respuesta mínima necesaria para demostrar que `codex exec` inició con el esfuerzo aplicado `low` y que la telemetría de uso del runner funciona.
- No reintentar si la ejecución falla.

## Cierre

Actualizar únicamente `docs/AI_CODEX_HANDOFF.md` en `coordination/ai-control` con un reporte breve que incluya:

- ticket `AI-AUTORUN-MEASURE-04`;
- PASS / FAILED;
- confirmación de inicio automático;
- esfuerzo solicitado y aplicado;
- `input_tokens`, `cached_input_tokens`, `output_tokens` y total, si la CLI los reporta de forma fiable; si no, `unavailable`;
- comparación porcentual contra ~42.500 tokens;
- confirmación de cero cambios de producto.

Después: **DETENERSE**.

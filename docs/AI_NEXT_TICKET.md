# AI NEXT TICKET

## AI-AUTO-HANDOFF-01

**Tipo:** infraestructura local / automatización Codex
**Prioridad:** importante
**Responsable:** Codex
**Revisor:** ChatGPT / jefe técnico central

> Leer primero `docs/AI_WORKFLOW.md` y `docs/AI_AUTORUN_SPEC.md` en `coordination/ai-control`.

## Objetivo

Instalar en la Mac el runner local que elimine el `ya` manual del lado de Codex.

Circuito final esperado:

**ChatGPT publica un ticket nuevo en `docs/AI_NEXT_TICKET.md` → la Mac detecta el cambio → ejecuta `codex exec` automáticamente → Codex ejecuta exactamente ese ticket → deja PR/handoff → se detiene.**

Este ticket es el **último arranque manual de Codex** previsto para el flujo normal.

## Límites

- NO tocar Production.
- NO desplegar.
- NO mergear PRs.
- NO instalar Docker ni infraestructura pesada.
- NO leer/versionar secretos, `.env`, tokens ni credenciales.
- NO alterar el worktree Android original `work/chrome-visual` ni su estado sucio preexistente.
- NO iniciar automáticamente otro ticket durante este mismo ticket.
- NO usar `danger-full-access` como configuración por defecto.

## Implementación requerida

Seguir `docs/AI_AUTORUN_SPEC.md` y construir una solución nativa/liviana para macOS.

### 1. Runner

Crear scripts/configuración versionables bajo una ubicación clara, preferentemente `tools/ai-autorun/`, con:

- watcher/poller de `coordination/ai-control:docs/AI_NEXT_TICKET.md`;
- detección por SHA/contenido;
- ejecución no interactiva mediante `codex exec`;
- prompt que obligue a leer `AI_WORKFLOW.md` + `AI_NEXT_TICKET.md` y ejecutar solo el ticket vigente;
- sandbox mínimo suficiente (`workspace-write` para tickets de edición);
- stop al terminar/bloquearse.

### 2. Estado y lock fuera del repo

Persistir localmente, fuera del working tree de producto:

- último SHA detectado;
- último SHA ejecutado;
- título/id del ticket;
- estado `idle/running/completed/blocked/failed`;
- PID si aplica;
- timestamps;
- lock single-flight.

El mismo SHA no puede disparar dos ejecuciones automáticas.

### 3. launchd

Instalar un LaunchAgent del usuario:

- arranque automático al login;
- no requiere terminal abierta;
- polling liviano 30–60 s o equivalente;
- instalación idempotente;
- consumo mínimo en idle.

### 4. Comandos operativos

Proveer una interfaz simple para:

- `install`
- `status`
- `start`
- `stop`
- `run-once`
- `uninstall`

`status` debe mostrar servicio, último ticket detectado/ejecutado, estado, PID y ruta del log.

### 5. Logs

- logs locales acotados/rotados;
- sin secretos;
- errores claros;
- no loop de reintentos;
- como máximo un retry para error transitorio inequívoco.

## Gate de prueba obligatorio

No usar un ticket real de producto para probar el watcher.

Crear un mecanismo de self-test inocuo que demuestre:

1. servicio activo;
2. detección de una señal de prueba exactamente una vez;
3. single-flight;
4. segunda lectura del mismo SHA no relanza;
5. stop/start/status;
6. launchd sigue cargado después de reinstalación idempotente;
7. no toca el worktree Android original.

Si probar `codex exec` real con un ticket de prueba consume créditos innecesarios o puede generar trabajo no deseado, usar un modo de test explícito del runner que sustituya el ejecutor por un stub y, además, verificar por separado que el comando real `codex exec` existe y es invocable. No gastar créditos solo para demostrar el watcher si no aporta evidencia adicional.

## Integración con el flujo actual

Al terminar:

- el runner debe quedar **instalado y activo**;
- NO debe ejecutar de nuevo `AI-AUTO-HANDOFF-01` al observar el mismo SHA;
- marcar este SHA como ya procesado antes de dejar el servicio en operación normal, para evitar autorrepetición;
- el próximo cambio futuro de `AI_NEXT_TICKET.md` debe poder disparar Codex automáticamente.

## Validación

Ejecutar:

- tests del runner/self-test;
- shellcheck/lint equivalente si está disponible sin instalar software pesado;
- `status` final;
- prueba de install idempotente;
- prueba de stop/start;
- `git diff --check` sobre los archivos versionados.

## Handoff obligatorio

Reemplazar `docs/AI_CODEX_HANDOFF.md` en `coordination/ai-control` con:

- `AI-AUTO-HANDOFF-01`;
- PASS / NEEDS-FIX / BLOCKED;
- archivos creados/modificados;
- ubicación exacta de LaunchAgent, state, lock y logs;
- comandos install/status/start/stop/uninstall;
- evidencia de idempotencia, deduplicación y single-flight;
- versión/comando de Codex CLI detectado;
- confirmación de que el servicio queda activo;
- confirmación de que el SHA de este ticket quedó marcado como procesado y no se autoejecutará;
- rollback/uninstall;
- cualquier permiso de macOS que haya requerido intervención manual;
- confirmación de que Production y el worktree Android original quedaron intactos.

Después: **DETENERSE**.

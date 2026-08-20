# AI AUTORUN — Glosh

## Objetivo

Eliminar el `ya` manual del lado de Codex sin convertir el proyecto en una cadena autónoma sin control.

Circuito objetivo:

**ChatGPT publica ticket nuevo → Mac lo detecta → Codex ejecuta → GitHub recibe PR/handoff → Codex se detiene → ChatGPT audita → ChatGPT publica siguiente ticket.**

El usuario conserva decisiones de producto, autorizaciones sensibles y pruebas físicas.

## Trigger

Fuente única:

- repo: `rajamimnehmad-sudo/control-parental-dev`
- rama: `coordination/ai-control`
- archivo: `docs/AI_NEXT_TICKET.md`

El runner local debe detectar un cambio de commit/contenido de ese archivo y ejecutar el ticket solamente si:

1. el ticket es nuevo respecto del último ticket procesado;
2. el archivo contiene un ticket vigente y explícito;
3. no hay otra ejecución de Codex activa por este runner;
4. el mismo ticket no figura como terminado/bloqueado en el estado local del runner.

La lectura remota debe actualizar de forma explícita `refs/remotes/origin/coordination/ai-control`; no depender solo de `FETCH_HEAD` ni de una referencia remota local potencialmente stale.

## Ejecución Codex

Usar la CLI actual en modo no interactivo:

`codex exec`

El prompt debe ordenar:

- leer `docs/AI_WORKFLOW.md` y `docs/AI_NEXT_TICKET.md` desde `coordination/ai-control`;
- ejecutar exactamente el ticket vigente;
- respetar branch/base/permisos/prohibiciones del ticket;
- dejar PR/handoff obligatorio;
- detenerse al terminar o bloquearse;
- no iniciar ningún ticket posterior.

Permisos: usar el mínimo suficiente. Para tickets que editan el repo, preferir sandbox `workspace-write`. No usar `danger-full-access` como configuración por defecto.

## Esfuerzo Codex por ticket

El ticket debe declarar:

`**Esfuerzo Codex:** low | medium | high | xhigh`

El runner debe:

1. leer ese campo de `AI_NEXT_TICKET.md`;
2. aceptar únicamente `low`, `medium`, `high`, `xhigh`;
3. usar `medium` si el campo falta o es inválido;
4. registrar el esfuerzo efectivo en estado/log;
5. lanzar Codex con un override por ejecución, equivalente a:

`codex exec -c model_reasoning_effort=<nivel> ...`

Routing económico vigente:

- `low` -> `gpt-5.6-luna`;
- `medium` -> `gpt-5.6-terra`;
- `high` / `xhigh` -> `gpt-5.6-sol`.

El perfil por defecto es `lean`: `CODEX_HOME` aislado con enlace a la
autenticación existente, sin copiar secretos; plugins/apps/herramientas ajenas
desactivadas; ejecución efímera; ticket completo incluido en el prompt. Solo un
ticket que declare `**Perfil Codex:** full` puede cargar la configuración
personal completa. Smoke/medición corren fuera del repo con hechos Git mínimos
precalculados.

Mapa semántico definido por ChatGPT:

- Bajo = `low`
- Medio = `medium` (default)
- Alto = `high`
- Extra alto = `xhigh`

Codex no debe autoelevar ni reducir este valor. Si considera que el nivel asignado es insuficiente para continuar con seguridad/calidad, debe dejarlo como observación/bloqueo para que ChatGPT decida.

No publicar tickets Codex para coordinación que ChatGPT pueda resolver
directamente con GitHub. El costo idle del watcher es cero tokens; una sesión de
modelo solo se justifica cuando hace falta trabajo local real.

## Guardas obligatorias

### No duplicar

Persistir fuera del repo de producto un estado pequeño con al menos:

- último SHA de `AI_NEXT_TICKET.md` ejecutado;
- identificador/título del ticket;
- esfuerzo Codex efectivo;
- estado `running`, `completed`, `blocked`, `failed`;
- hora de inicio/fin;
- PID/session id si aplica.

Un mismo SHA/ticket nunca debe ejecutarse dos veces automáticamente.

### Single-flight

Debe existir lock local. Si Codex ya está trabajando, un nuevo cambio de ticket queda pendiente pero no inicia en paralelo.

### Sin encadenado autónomo

Codex nunca decide el siguiente ticket. Solo un cambio nuevo publicado por ChatGPT en `AI_NEXT_TICKET.md` puede disparar otra ejecución.

### Acciones sensibles

El runner NO concede por sí solo autorización para:

- Production;
- deploy/publicación;
- merge importante;
- borrado destructivo;
- gastos;
- instalación de software pesado;
- prueba física;
- envío de APK.

Esas acciones siguen requiriendo autorización explícita del usuario cuando corresponda.

### Fallos

Si `codex exec` sale con error, timeout, auth faltante, conflicto de git o ticket ambiguo:

- no reintentar en loop;
- máximo un reintento automático solo para error transitorio claramente identificable;
- después marcar `failed/blocked`;
- dejar log local acotado;
- no modificar `AI_NEXT_TICKET.md`;
- no iniciar otro ticket.

## Implementación Mac

Preferencia: servicio nativo liviano de macOS con `launchd`, sin Docker ni daemon pesado.

Componentes esperados:

1. script pequeño bajo `tools/ai-autorun/` o ubicación equivalente;
2. polling liviano de GitHub cada 30–60 s o mecanismo equivalente confiable;
3. `launchd` para iniciar al login y mantener el watcher;
4. lock/state/log fuera del working tree de producto o en una ruta ignorada;
5. comandos de `status`, `start`, `stop`, `run-once` y `uninstall`;
6. instalación idempotente;
7. consumo prácticamente nulo cuando no hay ticket nuevo.

No depender de que una terminal quede abierta.

## Repos / worktrees

El runner debe conocer el repo principal pero no debe trabajar directamente sobre un worktree sucio salvo que el ticket lo ordene de forma explícita.

Debe dejar que cada ticket determine rama/base/worktree. Si el ticket exige worktree temporal limpio, Codex lo crea.

## Observabilidad mínima

Comando de estado debe mostrar:

- servicio activo/inactivo;
- último ticket detectado;
- último ticket ejecutado;
- esfuerzo Codex efectivo;
- estado actual;
- última ejecución;
- PID si está corriendo;
- ruta del log.

Logs rotados/acotados. No registrar secretos, tokens ni contenido de `.env`.

## Criterio de aceptación del autorun

1. instalación local idempotente en la Mac;
2. `launchd` activo;
3. un ticket de prueba inocuo se detecta exactamente una vez;
4. la referencia remota se actualiza explícitamente y no queda stale;
5. `codex exec` arranca automáticamente;
6. una segunda lectura del mismo SHA no vuelve a arrancarlo;
7. single-flight evita ejecuciones paralelas;
8. stop/start/status funcionan;
9. reinicio de sesión/Mac no pierde la configuración;
10. ningún secreto queda versionado;
11. no se altera el working tree Android actual;
12. un self-test demuestra que `low/medium/high/xhigh` se traducen al override correcto y que ausencia/valor inválido cae a `medium`;
13. handoff a ChatGPT con comandos, archivos, estado y rollback/uninstall.

## Después de instalar

El usuario ya no necesita decir `ya` en Codex.

En operación normal solo hará falta que ChatGPT publique el siguiente ticket. El usuario puede seguir diciendo `ya` en ChatGPT cuando quiera forzar una revisión inmediata.

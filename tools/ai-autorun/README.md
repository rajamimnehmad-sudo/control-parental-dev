# Glosh AI Autorun

Runner local liviano para observar `coordination/ai-control:docs/AI_NEXT_TICKET.md`
y ejecutar exactamente un ticket nuevo mediante `codex exec`.

El ejecutable permanece en un único archivo autocontenido (536 líneas) porque
`install` debe copiar una sola unidad atómica y el estado/lock/ciclo de vida
comparten invariantes. No se le agregará otra responsabilidad sin separar antes
parsing, ejecución y servicio en un ticket dedicado.

## Operación

```bash
tools/ai-autorun/glosh-ai-autorun install /Users/yejielnehmad/Developer/content-filter
tools/ai-autorun/glosh-ai-autorun status
tools/ai-autorun/glosh-ai-autorun start
tools/ai-autorun/glosh-ai-autorun stop
tools/ai-autorun/glosh-ai-autorun run-once
tools/ai-autorun/glosh-ai-autorun uninstall
```

La instalación copia el runner a
`~/Library/Application Support/Glosh/ai-autorun/bin/`, instala el LaunchAgent
`~/Library/LaunchAgents/com.glosh.ai-autorun.plist` y consulta cada 45 segundos.

Estado, lock y logs viven fuera del repo:

- `~/Library/Application Support/Glosh/ai-autorun/state`
- `~/Library/Application Support/Glosh/ai-autorun/run.lock`
- `~/Library/Application Support/Glosh/ai-autorun/logs/`

El primer `install` marca el ticket vigente como procesado antes de activar
`launchd`; así la instalación no se ejecuta a sí misma. Reinstalar es idempotente
y conserva el estado. `uninstall` preserva estado y logs para evitar repeticiones
accidentales si se reinstala.

Cada ticket puede declarar `Esfuerzo Codex` como Bajo, Medio, Alto o Extra alto.
El runner aplica respectivamente `low`, `medium`, `high` o `xhigh` y enruta el
modelo por costo/capacidad: Luna para Bajo, Terra para Medio y Sol para
Alto/Extra alto. Un valor ausente o inválido cae a Medio/Terra.

El perfil por defecto es `lean`: usa un `CODEX_HOME` aislado que solo enlaza la
autenticación existente (sin copiarla), desactiva plugins/apps/herramientas no
necesarias, trabaja en modo efímero y recibe el ticket completo en el prompt para
no volver a cargar coordinación. Tickets de medición/smoke corren fuera del repo
con hechos Git mínimos precomputados. Solo un ticket que declare explícitamente
`Perfil Codex: full` conserva la configuración personal completa.

El estado y el log registran esfuerzo, perfil, modelo, SHA y uso de tokens
informado por el evento JSON final; si no existe una métrica estable se guarda
`unavailable`.

## Seguridad

- un lock por `mkdir` impide ejecuciones paralelas;
- el SHA del blob del ticket se persiste antes de llamar a Codex;
- el mismo SHA no se ejecuta dos veces, incluso después de un fallo;
- Codex usa `--approve-for-me`, que aplica `workspace-write` con revisión
  automática de aprobaciones;
- el trigger no concede Production, deploy, merge, borrados, gastos, pruebas
  físicas ni envíos de APK;
- no se leen ni registran secretos;
- logs rotan al superar 1 MiB y conservan una generación.

## Pruebas

`tools/ai-autorun/self-test.sh` sustituye Codex por un stub interno y valida los
cuatro niveles de esfuerzo/modelo, defaults inválido/ausente, perfil lean,
detección única, deduplicación, single-flight, estado y liberación del lock sin
consumir créditos.

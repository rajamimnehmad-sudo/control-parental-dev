# Glosh AI Autorun

Runner local liviano para observar `coordination/ai-control:docs/AI_NEXT_TICKET.md`
y ejecutar exactamente un ticket nuevo mediante `codex exec`.

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

`tools/ai-autorun/self-test.sh` sustituye Codex por un stub interno y valida
detección única, deduplicación, single-flight, estado y liberación del lock sin
consumir créditos.

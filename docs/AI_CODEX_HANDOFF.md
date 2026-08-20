# AI CODEX HANDOFF

## P0-HARDENING-BATCH-01

- Fecha: 2026-08-20 10:40:44 -03.
- Estado global: **NEEDS-FIX**.
- Rama: `review/p0-hardening-batch-01`.
- Base: `preserve/local-main-2026-08-20`.
- PR draft: https://github.com/rajamimnehmad-sudo/control-parental-dev/pull/95

## Estado por bloque

- **A — PASS** — `0100cf9e`: allowlist técnica exacta; Glosh conserva su host y no privilegia `supabase.co`, proyectos hermanos ni subdominios.
- **B — PASS** — `5aa1f8cf`: bundle periódico/dirigido coherente, verificación doble de revisión/target, aplicación Room transaccional, last-known-good, cursores posteriores y ACK tras VPN + Accessibility.
- **C — NEEDS-FIX** — `bb148ea2`: tokens de 128 bits, SHA-256 indexado, TTL acotado, locks, grants explícitos, `search_path` fijo y cutoff derivado del rollout. Los checks SQL quedaron escritos pero no pudieron ejecutarse porque esta Mac no tiene Docker/Postgres local disponible.

## Desviaciones del candidato preservado

- A conserva la precedencia histórica de bloques explícitos para buscadores; solo los hosts técnicos críticos exactos tienen prioridad.
- B no usa el cursor de `policies` como disparador del bundle: siempre relee la policy activa del dispositivo, valida todos los hijos y vuelve a comprobar la revisión antes del commit.
- C elimina el cutoff fijo `2026-08-19`, usa una marca persistente del rollout efectivo, endurece revokes y divide la migración de 1.221 líneas en tres archivos de 577/415/251 líneas.
- `anon EXECUTE` en `admin_create_device_relink_code` se conserva deliberadamente: el cliente de admin puede operar solo con anon JWT + `x-device-token`, y la función valida ese token antes de acceder al target.

## Archivos y validación

Áreas tocadas: `core-policy`, `feature-vpn`, `core-sync`, `feature-activation` y cuatro archivos SQL de pairing. No se tocó DAG, Chrome Visual, Admin, Production ni device-token scope.

Comandos verdes:

- `./gradlew --no-daemon :core-policy:test :feature-vpn:test`
- `./gradlew --no-daemon :core-sync:testDebugUnitTest :core-sync:ktlintCheck`
- `./gradlew --no-daemon :feature-activation:testDebugUnitTest :feature-activation:ktlintCheck`
- `./gradlew --no-daemon :core-policy:test :feature-vpn:test :core-sync:testDebugUnitTest :feature-activation:testDebugUnitTest :app-user:assembleDevDebug`
- `git diff --check`

No disponible:

- `supabase status`: Docker daemon inexistente/no disponible; migraciones y `pairing_hardening_03b_checks.sql` no ejecutados.

## Riesgos vigentes y cierre

- Antes de aprobar C o aplicar la migración, ejecutar las tres migraciones y los checks en un Supabase/Postgres local limpio, incluyendo consumo único, legacy transition, grants y `search_path`.
- Ninguna migración fue aplicada a Supabase/Production.
- No hubo instalación ni prueba física; no era requerida.
- `DefaultSyncEngine.kt` (731 líneas) y el fixture atómico (772) permanecen unidos por cohesión del ticket y siguen debajo del umbral obligatorio de división.
- El worktree original `work/chrome-visual` quedó intacto.

Siguiente acción: ChatGPT debe auditar los tres commits y decidir si pide solo el gate SQL de C o cambios adicionales. Codex se detiene.

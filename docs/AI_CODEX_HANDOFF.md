# AI CODEX HANDOFF

## P0-BACKEND-CLOSEOUT-02

- Fecha: 2026-08-20.
- Estado global: **BLOCKED** por falta de sandbox SQL local; la implementación y los controles estáticos están completos.
- Rama: `review/p0-hardening-batch-01`.
- Base: `preserve/local-main-2026-08-20`.
- PR draft actualizado: https://github.com/rajamimnehmad-sudo/control-parental-dev/pull/95

## Estado por bloque

- **C2 — BLOCKED-SQL-GATE** — commit `bf37b77e` (`fix(pairing): close SQL hardening gate`). `public.pairing_hardening_rollout` conserva los `REVOKE`, tiene RLS habilitado y no crea políticas API. `pairing_hardening_03b_checks.sql` ahora exige RLS y cero policies. También se retiraron dos líneas vacías finales que hacían fallar el diff global, sin cambiar semántica SQL.
- **D — BLOCKED-SQL-GATE (estático PASS)** — commit `76bbf975` (`fix(security): scope device token writes to device`). Las policies de escritura de `access_requests` y `device_apps` usan `device_token_matches_device(device_id)`, coherencia explícita `device_id ↔ account_id`, `USING` y `WITH CHECK`, y roles `anon, authenticated`.

## Evidencia de aislamiento cross-device

La estructura implementada garantiza estáticamente:

- token A solo satisface el helper exacto para `device_id=A`;
- una fila de B falla aunque A y B compartan cuenta;
- un UPDATE de A hacia B o hacia otra cuenta falla en `WITH CHECK`;
- `access_requests.device_id = null` falla explícitamente;
- DELETE evalúa `USING` sobre la fila existente;
- las policies independientes de owner/admin no fueron modificadas;
- no se agregó otra policy de escritura basada en `device_token_matches(account_id)`.

El archivo `supabase/device_token_scope_checks.sql` valida catálogo, roles, helper exacto, no-null, coherencia de cuenta y ausencia del patrón account-scoped anterior. Estos casos todavía no tienen evidencia dinámica porque no hubo base local donde ejecutar SQL; no se declara PASS runtime.

El App Usuario ya cumple el contrato y no fue modificado:

- `InstalledAppPublisher.kt` envía `activation.accountId` y `activation.deviceId` en `device_apps`.
- `MyAppsViewModel.kt` construye `RemoteAccessRequestDto` con `accountId` y `deviceId` no nulo.

## Archivos tocados en este ticket

- `supabase/migrations/20260819145959_harden_pairing_tokens.sql`
- `supabase/migrations/20260819150001_harden_pairing_consumers.sql` (solo EOF)
- `supabase/migrations/20260819150002_harden_pairing_entrypoints.sql` (solo EOF)
- `supabase/pairing_hardening_03b_checks.sql`
- `supabase/migrations/20260820142155_scope_device_token_writes.sql`
- `supabase/device_token_scope_checks.sql`

## Comandos, checks y resultados

- `supabase --version`: CLI `2.109.0` disponible.
- Runtime local: `docker`, una instancia operativa de Docker Desktop, `colima`, `orbctl`, `podman`, `postgres` y `psql` no disponibles; `supabase status` no pudo iniciar por ausencia de Docker daemon.
- No se instaló infraestructura pesada ni se usó un backend remoto.
- `git diff --check origin/preserve/local-main-2026-08-20...HEAD`: **PASS**.
- Inspección con `rg` de RLS, policies, grants y payloads App Usuario: **PASS**.
- `pairing_hardening_03b_checks.sql`: **NO EJECUTADO — BLOCKED-SQL-GATE**.
- `device_token_scope_checks.sql`: **NO EJECUTADO — BLOCKED-SQL-GATE**.
- No se repitió Gradle ni `assembleDevDebug`: los cambios nuevos son exclusivamente SQL y no entran en el grafo Android; se conserva el build verde del lote anterior.
- No hubo prueba física; no correspondía.

## Observaciones y riesgos vigentes

- Antes de aplicar estas migraciones, ejecutar ambas suites SQL en un Supabase/Postgres local limpio y demostrar dinámicamente A→A permitido, A→B denegado, UPDATE de identidad denegado, null denegado y owner/admin permitido.
- Las lecturas account-scoped existentes siguen intactas por decisión explícita del ticket.
- `anon EXECUTE` de `admin_create_device_relink_code` sigue deliberadamente preservado porque la función valida `x-device-token` antes del target.
- PR #95 permanece draft, sin merge.
- Ninguna migración fue aplicada a Supabase o Production.
- No se tocó DAG, Chrome Visual, Super Admin UI ni creación oficial de usuarios Auth.
- Production quedó intacto.
- El worktree original sigue exactamente en `work/chrome-visual` / `6a045f13`, con sus 6 archivos modificados y 4 untracked preexistentes; no se limpió, reseteó, stasheó ni alteró.

Siguiente decisión: ChatGPT debe definir dónde ejecutar el gate SQL real. Codex se detiene.

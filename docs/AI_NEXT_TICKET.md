# AI NEXT TICKET

## P0-BACKEND-CLOSEOUT-02

**Tipo:** hardening backend + validación SQL
**Prioridad:** crítica
**Responsable:** Codex
**Revisor:** ChatGPT / jefe técnico central

> Leer primero `docs/AI_WORKFLOW.md` en `coordination/ai-control`.

## Estado aprobado de partida

El lote anterior fue auditado en PR #95.

- **A — exact technical hosts: APROBADO.** No modificar salvo necesidad de compilación demostrada.
- **B — atomic policy sync: APROBADO.** No modificar salvo necesidad de compilación demostrada.
- **C — pairing hardening: NEEDS-FIX.** Falta gate SQL real y un ajuste defensivo de RLS/aislamiento.

Continuar sobre:

- rama: `review/p0-hardening-batch-01`
- PR: `#95`
- base: `preserve/local-main-2026-08-20`

No mergear.

Este ticket agrupa dos bloques independientes y debe cerrarlos con commits separados:

1. finalizar Pairing Hardening;
2. cerrar scope de token por dispositivo para escrituras del App Usuario.

---

# Bloque C2 — Finalizar Pairing Hardening

## Ajuste obligatorio antes del gate

La migración crea `public.pairing_hardening_rollout`. Al estar en schema expuesto, no alcanza con `REVOKE`.

Dejarla defensivamente aislada mediante **RLS habilitado y sin políticas API**, manteniendo los `REVOKE`, o una solución privada equivalente si demuestra ser más segura y compatible. No ampliar innecesariamente el diseño.

No aplicar a Production.

## Gate SQL real

Intentar primero utilizar infraestructura local ya instalada:

- comprobar `supabase --version` / `supabase --help`;
- comprobar runtime disponible (`docker`, Docker Desktop, OrbStack, Colima, etc.);
- si Docker Desktop u otro runtime ya está instalado pero detenido, puede iniciarse;
- no instalar un runtime pesado nuevo ni generar gastos externos sin autorización.

Si existe un sandbox local viable:

1. levantar un Supabase/Postgres local limpio compatible con el proyecto;
2. aplicar la historia/migraciones necesarias hasta este lote;
3. ejecutar las tres migraciones de pairing en orden;
4. ejecutar `supabase/pairing_hardening_03b_checks.sql`;
5. verificar consumo único, expiración, transición legacy, índice/hash, grants y `search_path`;
6. verificar RLS/aislamiento de `pairing_hardening_rollout`;
7. si el CLI lo soporta, ejecutar advisors locales relevantes;
8. `git diff --check`.

Si el sandbox expone un error real, corregir únicamente C y volver a ejecutar el gate.

Si no existe ningún sandbox local utilizable sin instalación pesada/permisos, dejar C como `BLOCKED-SQL-GATE`, pero sí aplicar el ajuste estático de RLS/aislamiento y continuar con el Bloque D. No inventar PASS.

## Compatibilidad a conservar

- tokens nuevos >=128 bits;
- lookup SHA-256 indexado;
- legacy solo pre-rollout real;
- single-use con row lock;
- TTL acotado;
- helpers no ejecutables por API roles;
- grants mínimos;
- `admin_create_device_relink_code` puede conservar `anon EXECUTE` solo porque valida `x-device-token` antes del target; documentarlo;
- NO tocar todavía la deuda conocida de creación directa en `auth.users`; queda para el siguiente lote P1.

Commit separado sugerido:

`fix(pairing): close SQL hardening gate`

---

# Bloque D — Device token scope por dispositivo

## Problema confirmado en Supabase real

Hoy `access_requests_device_token_all` y `device_apps_device_token_all` autorizan con:

`device_token_matches(account_id)`

Eso permite que un token válido de un dispositivo pueda mutar filas de otro dispositivo de la misma cuenta.

Las lecturas account-scoped compartidas NO forman parte de este bloque salvo que una prueba demuestre que deben cambiar para cerrar el bypass.

## Objetivo

Para escrituras hechas con device-token de App Usuario:

- `device_apps`: un dispositivo solo puede INSERT/UPDATE/DELETE filas cuyo `device_id` sea ese mismo dispositivo y cuyo `account_id` corresponda al dispositivo.
- `access_requests`: misma regla; un device-token no puede escribir filas de un sibling device ni filas con `device_id = null`.
- impedir cambiar una fila propia hacia otro `device_id`/`account_id` mediante UPDATE (`USING` + `WITH CHECK`).
- conservar intactos los flujos del account owner y los administradores autorizados mediante sus políticas independientes.

Usar `device_token_matches_device(device_id)` y coherencia explícita `device_id ↔ account_id` o un helper equivalente mínimo y auditable.

Preferir roles explícitos `anon, authenticated` en las nuevas policies en lugar de `public` cuando sea compatible con el cliente actual.

## Migración

Crear la migración con el CLI (`supabase migration new ...`) si está disponible; no inventar nombre manualmente si el CLI funciona.

No aplicar a Production.

## Checks SQL requeridos

En sandbox local si está disponible, demostrar al menos:

1. token del device A puede escribir `device_apps` de A;
2. token A no puede insertar/update/delete `device_apps` de B aunque compartan account;
3. token A no puede cambiar `device_id` o `account_id` de una fila propia hacia B;
4. token A puede crear/gestionar su `access_request` con `device_id=A`;
5. token A no puede mutar request de B;
6. token A no puede crear request con `device_id=null`;
7. owner/admin siguen funcionando por sus policies propias;
8. no aparece una nueva policy account-scoped de escritura equivalente al bypass anterior.

Agregar un archivo de checks SQL pequeño si hace falta.

## Cliente / tests

Verificar que App Usuario siempre manda `device_id` en los dos flujos afectados. Si el cliente ya cumple, no modificarlo.

Ejecutar solo suites relacionadas + una `:app-user:assembleDevDebug` final si hubo cambios que entran en su grafo.

Commit separado sugerido:

`fix(security): scope device token writes to device`

---

# Cierre del lote

La rama/PR debe terminar con A y B intactos y dos nuevos commits auditables para C2 y D.

No tocar:

- Chrome Visual;
- DAG;
- Super Admin UI;
- credenciales locales/Keystore;
- creación oficial de usuarios Auth;
- Production;
- `main`;
- worktree original sucio.

No prueba física.

## Observaciones obligatorias de Codex

Además del resultado, anotar en el handoff cualquier hallazgo relevante detectado durante el trabajo, incluso si queda fuera de scope. **No arreglarlo fuera del lote**; solo describir impacto y recomendación para que ChatGPT decida.

## Handoff obligatorio

Reemplazar `docs/AI_CODEX_HANDOFF.md` en `coordination/ai-control` con:

- `P0-BACKEND-CLOSEOUT-02`;
- estado global PASS / NEEDS-FIX / BLOCKED;
- estado C2 y D por separado;
- branch + commits nuevos;
- PR #95;
- archivos tocados;
- comandos/tests/checks y resultados exactos;
- resultado del sandbox SQL o motivo preciso por el que no estuvo disponible;
- confirmación de RLS/aislamiento de rollout;
- evidencia de los casos cross-device;
- observaciones técnicas adicionales;
- riesgos restantes;
- confirmación de que Production y el worktree original quedaron intactos.

Después: **DETENERSE**.

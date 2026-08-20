# RPC Permission Hardening Plan — Glosh

Estado: **READ-ONLY AUDIT / NO PRODUCTION CHANGES APPLIED**

Fecha: 2026-08-20

## Objetivo

Reducir la superficie de `SECURITY DEFINER` expuesta por la Data API sin romper los clientes Android, pairing, device-token ni Super Admin.

## Hallazgos verificados en Supabase

- Hay **36 funciones `SECURITY DEFINER` ejecutables por `anon`** en `public`.
- La mayoría de las RPC de dispositivo validan `x-device-token` mediante `request_device_token()` / `device_token_matches_device(...)` y necesitan seguir siendo invocables desde el cliente que usa la publishable/anon key.
- Los flujos de pairing por código también necesitan `anon` como bootstrap antes de disponer de sesión o device-token.
- Cuatro RPC `super_admin_*` tienen `EXECUTE` explícito para `anon`, pero internamente exigen Super Admin mediante `is_super_admin()` o `is_current_user_super_admin()`. El permiso `anon` es innecesario.
- `activate_device(...)` exige `auth.uid()` y por diseño actual no puede funcionar desde `anon`, pero `pg_stat_statements` registra **27 llamadas históricas** al RPC. Por seguridad de compatibilidad, queda fuera del primer hardening hasta identificar el cliente legacy.
- Los `DEFAULT PRIVILEGES` actuales para funciones nuevas en `public` conceden `EXECUTE` automáticamente a `anon`, `authenticated` y `service_role` para objetos creados por `postgres` (y existen defaults equivalentes de plataforma para `supabase_admin`). Esto explica la exposición accidental de RPC nuevas.
- El pairing actual sí muestra uso real: `pair_device_with_code(...)` acumula decenas de llamadas y `pair_admin_device_with_password(...)` también aparece en estadísticas; `anon` se conserva en esos flujos.
- El pairing de Admin mantiene una deuda P1 independiente: `pair_admin_device_with_password_new_internal(...)` inserta directamente en `auth.users` / `auth.identities`. No se modifica en este hardening de permisos.

## Matriz de permisos propuesta

### A — Mantener `anon` (intencional)

Mantener `anon` en RPC cuyo control real es un `x-device-token` válido o un pairing-code de un solo uso/TTL. Ejemplos verificados:

- `device_token_matches(...)`
- `device_token_matches_device(...)`
- `device_token_matches_role(...)`
- `admin_device_token_matches(...)`
- `admin_create_device_relink_code(...)`
- `ack_device_protection_control(...)`
- `auto_arm_device_protection(...)`
- `create_protection_alert_event(...)`
- `create_reinforced_protection_alert_event(...)`
- `get_device_license_entitlement(...)`
- `get_own_admin_contact(...)`
- `get_own_user_contact(...)`
- `get_own_app_rating_status(...)`
- `update_own_admin_contact(...)`
- `update_own_admin_contact_v2(...)`
- `update_own_user_contact(...)`
- `register_admin_push_token(...)`
- `register_device_push_token(...)`
- `report_own_device_metadata(...)`
- `submit_own_app_rating(...)`
- `submit_own_support_report(...)`
- `list_device_announcements(...)`
- `list_device_announcements_v2(...)`
- `mark_device_announcements_read()`
- `dismiss_device_announcement(...)`
- `restore_device_announcement(...)`
- `cancel_own_removal_authorization()`
- `complete_own_device_relink()`
- `create_device_pairing_code(...)`
- `pair_device_with_code(...)`
- `pair_admin_device_with_password(...)`

Notas:
- Los helpers `device_token_matches*` siguen siendo RPC visibles mientras vivan en `public`. A futuro puede evaluarse mover helpers internos a un schema privado, pero no es requisito para este lote.
- `pair_admin_device_with_password(...)` necesita `anon` para alta inicial mediante código; el flujo de relink Admin exige además `auth.uid()`.

### B — Retirar `anon` ahora (candidatos claros)

Estas RPC son exclusivamente de Super Admin y ya tienen guard interno. No hay razón funcional para que `anon` pueda invocarlas:

1. `super_admin_create_announcement(uuid, text, text, text, timestamptz)`
2. `super_admin_list_admin_contacts(uuid)`
3. `super_admin_list_app_ratings(integer)`
4. `super_admin_list_device_metadata(uuid)`

Cambio propuesto (NO ejecutado):

```sql
revoke execute on function public.super_admin_create_announcement(uuid, text, text, text, timestamptz) from anon;
revoke execute on function public.super_admin_list_admin_contacts(uuid) from anon;
revoke execute on function public.super_admin_list_app_ratings(integer) from anon;
revoke execute on function public.super_admin_list_device_metadata(uuid) from anon;
```

Conservar `authenticated` porque el Super Admin web usa sesión autenticada y cada función verifica `is_super_admin()` / `is_current_user_super_admin()` / `require_super_admin()`.

### C — `activate_device` legacy: no tocar todavía

`activate_device(text, text, integer, text)` exige que la cuenta pertenezca a `auth.uid()`, así que `anon` no aporta capacidad válida. Sin embargo, `pg_stat_statements` registra **27 llamadas históricas** a este endpoint. Eso es suficiente para no revocarlo a ciegas.

Acción posterior:
- localizar el cliente/versión que todavía lo invoca;
- migrarlo al pairing actual si corresponde;
- recién después retirar `anon` y eventualmente deprecar/eliminar el RPC en un lote separado.

### D — No tocar por ahora

- Permisos `authenticated` de RPC `super_admin_*`: el acceso se restringe internamente por Super Admin y es el patrón esperado para el frontend autenticado.
- Funciones internas ya cerradas a `anon/authenticated`, como `require_super_admin`, `license_allows_activation`, `license_allows_relink`, `pair_*_internal`, tareas cron y helpers internos.
- `service_role`: no reducir en este lote hasta inventariar Edge Functions/automatizaciones que puedan depender de estas RPC.

## Hardening de defaults para funciones futuras

La causa estructural es que nuevas funciones creadas por `postgres` en `public` heredan `EXECUTE` para roles de Data API. Para evitar repetir el problema, preparar un cambio **opt-in** para funciones nuevas.

Cambio recomendado para el rol `postgres` creador de nuestras funciones (NO ejecutado):

```sql
alter default privileges for role postgres in schema public
  revoke execute on functions from anon, authenticated, service_role;
```

Después, cada nueva RPC pública debe recibir grants explícitos según su modelo:

```sql
grant execute on function public.<rpc>(...) to anon;
-- o
grant execute on function public.<rpc>(...) to authenticated;
```

No modificar defaults de `supabase_admin` sin necesidad explícita; son parte de la plataforma y deben tratarse por separado.

## Verificación antes/después

Antes de aplicar cualquier `REVOKE`:

1. Confirmar firmas exactas con `pg_get_function_identity_arguments`.
2. Confirmar cliente/flujo esperado para cada RPC.
3. Ejecutar el cambio en una transacción controlada o sandbox cuando esté disponible.

Después del cambio:

```sql
select
  has_function_privilege('anon', 'public.super_admin_create_announcement(uuid,text,text,text,timestamp with time zone)', 'EXECUTE') as anon_create_announcement,
  has_function_privilege('anon', 'public.super_admin_list_admin_contacts(uuid)', 'EXECUTE') as anon_admin_contacts,
  has_function_privilege('anon', 'public.super_admin_list_app_ratings(integer)', 'EXECUTE') as anon_app_ratings,
  has_function_privilege('anon', 'public.super_admin_list_device_metadata(uuid)', 'EXECUTE') as anon_device_metadata;
```

Resultado esperado: `false` en los cuatro.

Además:
- Super Admin autenticado debe seguir pudiendo operar.
- App Usuario/Admin con `x-device-token` debe seguir pudiendo consumir sus RPC.
- Pairing inicial debe seguir funcionando con código válido.
- Security Advisor debe reducir los warnings correspondientes sin crear regresiones funcionales.

## Siguiente decisión

Aplicar cambios reales a Production requiere autorización explícita del owner. La primera aplicación recomendada es mínima: **solo los 4 `REVOKE ... FROM anon` de Super Admin**, seguida por verificación inmediata. `activate_device` queda fuera del cambio. El hardening de default privileges debe ir en un segundo cambio separado para reducir blast radius.

# RPC Permission Hardening Plan — Glosh

Estado: **PRIMER HARDENING APLICADO EN PRODUCTION Y VERIFICADO**

Fecha: 2026-08-20

## Objetivo

Reducir la superficie de `SECURITY DEFINER` expuesta por la Data API sin romper los clientes Android, pairing, device-token ni Super Admin.

## Resultado aplicado

Con autorización explícita del owner se aplicó en Production:

```sql
revoke execute on function public.super_admin_create_announcement(uuid, text, text, text, timestamptz) from anon;
revoke execute on function public.super_admin_list_admin_contacts(uuid) from anon;
revoke execute on function public.super_admin_list_app_ratings(integer) from anon;
revoke execute on function public.super_admin_list_device_metadata(uuid) from anon;

alter default privileges for role postgres in schema public
  revoke execute on functions from anon, authenticated;
```

Verificación inmediata:
- `anon=false` en las cuatro RPC de Super Admin.
- `authenticated=true` y `service_role=true` preservados.
- Para funciones futuras creadas por `postgres` en `public`, los defaults quedaron únicamente para `postgres` y `service_role`; `anon` y `authenticated` pasan a ser **opt-in explícito**.
- Security Advisor dejó de reportar esas cuatro exposiciones `anon` de Super Admin.

No se revocó ningún RPC Android/device-token/pairing.

## Hallazgos verificados

- La mayoría de las RPC de dispositivo validan `x-device-token` mediante `request_device_token()` / `device_token_matches_device(...)` y necesitan seguir siendo invocables desde cliente público.
- Los flujos de pairing por código necesitan `anon` como bootstrap antes de disponer de sesión o device-token.
- Las RPC Super Admin verificadas usan `require_super_admin()` / `is_super_admin()`; una prueba read-only con rol `anon` fue rechazada con `Not authorized` antes del hardening.
- `activate_device(...)` exige `auth.uid()` y por diseño actual no obtiene capacidad útil desde `anon`, pero `pg_stat_statements` registra **27 llamadas históricas**. Queda fuera hasta identificar el consumidor legacy.
- El pairing actual sí muestra uso real: `pair_device_with_code(...)` acumula decenas de llamadas y `pair_admin_device_with_password(...)` también aparece en estadísticas; `anon` se conserva.
- Hay funciones internas accesibles solo por `service_role`; ese rol no se endurece en este lote.
- El pairing de Admin mantiene deuda P1: `pair_admin_device_with_password_new_internal(...)` inserta directamente en `auth.users` / `auth.identities`.

## Mantener `anon` por diseño

Ejemplos principales:
- `device_token_matches(...)`
- `device_token_matches_device(...)`
- `device_token_matches_role(...)`
- `admin_device_token_matches(...)`
- `admin_create_device_relink_code(...)`
- `ack_device_protection_control(...)`
- `auto_arm_device_protection(...)`
- `get_device_license_entitlement(...)`
- RPC de contactos/ratings/support/metadata del propio dispositivo autenticadas por device-token
- anuncios y push del dispositivo
- `complete_own_device_relink()`
- `create_device_pairing_code(...)`
- `pair_device_with_code(...)`
- `pair_admin_device_with_password(...)`

No revocar `anon` de este grupo sin cambiar antes el modelo de autenticación de App Usuario/Admin.

## `activate_device` legacy

No tocar todavía. Tiene 27 llamadas históricas registradas.

Siguiente acción:
1. localizar qué cliente/versión lo usa;
2. migrarlo al pairing actual si corresponde;
3. recién entonces retirar `anon` y evaluar deprecación.

## Defaults para funciones futuras

El cambio ya aplicado hace opt-in de `anon/authenticated` para nuevas funciones creadas por `postgres` en `public`.

A partir de ahora, una nueva RPC pública debe recibir grants explícitos según su modelo, por ejemplo:

```sql
grant execute on function public.<rpc>(...) to anon;
-- o
grant execute on function public.<rpc>(...) to authenticated;
```

`service_role` se mantiene como default por compatibilidad con tareas internas. Los defaults de `supabase_admin` no se modificaron.

## Pendientes

1. Identificar consumidor legacy de `activate_device`.
2. Cerrar gate dinámico cross-device de pairing/device-token.
3. Eliminar escritura directa en `auth.users/auth.identities` del pairing Admin.
4. Revisar `Leaked Password Protection` (Advisor sigue reportando desactivada; disponibilidad depende del plan/configuración Auth).
5. Versionar el cambio de Production como migración formal cuando se ejecute el flujo local de migraciones; no inventar nombre/archivo de migración desde GitHub.

## Nota de reset y GloshIA

El reset operativo autorizado deja comunidades/cuentas/admins comunitarios/dispositivos/licencias/códigos/pedidos en cero, pero desde ahora **material útil para entrenamiento/calibración de GloshIA queda expresamente fuera de cualquier reset operativo**. Ver regla permanente en `docs/AI_WORKFLOW.md`.

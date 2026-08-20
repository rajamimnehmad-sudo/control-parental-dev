# RPC EXECUTE audit — 2026-08-20

Estado: auditoría read-only. No se aplicaron cambios en Production.

Documento canónico de plan: `docs/RPC_PERMISSION_HARDENING_PLAN.md`.

## Hallazgos confirmados

- Supabase Security Advisor detectó múltiples funciones `SECURITY DEFINER` ejecutables por `anon` y/o `authenticated`.
- Las ACL revisadas son grants explícitos por rol.
- `require_super_admin()` no es ejecutable directamente por `anon/authenticated` y las RPC de Super Admin verificadas la invocan internamente.
- `is_super_admin()` valida `auth.uid()` contra `public.super_admins` con `enabled = true` y `deleted_at is null`.
- `request_device_token()` lee `x-device-token` de `request.headers`; `device_token_matches*()` valida contra hashes del dispositivo o sesión de relink.
- Prueba read-only con rol `anon` sobre `super_admin_list_app_ratings(1)` fue rechazada con `Not authorized`.

## `anon` necesario por diseño — mantener

Mantener `anon` en RPC cuyo modelo real es `x-device-token` o bootstrap de pairing:

- helpers `device_token_matches*` y `admin_device_token_matches`;
- `admin_create_device_relink_code`;
- protección/ACK/metadata/license entitlement del dispositivo;
- anuncios y push token del dispositivo;
- `create_device_pairing_code`;
- `pair_device_with_code`;
- `pair_admin_device_with_password`;
- `complete_own_device_relink` y demás RPC equivalentes autenticadas por device-token.

Retirar `anon` de este grupo rompería el modelo actual de App Usuario/Admin.

## Primer hardening de bajo riesgo — listo, NO aplicado

Cuatro RPC exclusivamente de Super Admin tienen `anon EXECUTE` innecesario y guardas internas de Super Admin:

1. `super_admin_create_announcement(uuid,text,text,text,timestamptz)`
2. `super_admin_list_admin_contacts(uuid)`
3. `super_admin_list_app_ratings(integer)`
4. `super_admin_list_device_metadata(uuid)`

Cambio propuesto:

```sql
revoke execute on function public.super_admin_create_announcement(uuid,text,text,text,timestamptz) from anon;
revoke execute on function public.super_admin_list_admin_contacts(uuid) from anon;
revoke execute on function public.super_admin_list_app_ratings(integer) from anon;
revoke execute on function public.super_admin_list_device_metadata(uuid) from anon;
```

Conservar `authenticated` y `service_role`.

## `activate_device` — legacy, NO incluir en primer lote

Aunque `activate_device(text,text,integer,text)` exige `auth.uid()` y no obtiene capacidad útil desde `anon`, `pg_stat_statements` registra **27 llamadas históricas** a ese RPC. Por compatibilidad, no se revoca todavía.

Primero hay que localizar qué cliente/versión legacy lo usa y migrarlo al pairing actual si corresponde.

## Default privileges — causa estructural confirmada

Para funciones creadas por `postgres` en schema `public`, los defaults actuales conceden `EXECUTE` a:

- `anon`
- `authenticated`
- `service_role`

Existen defaults equivalentes para objetos creados por `supabase_admin`.

Por eso el plan canónico propone, en un cambio separado y posterior, hacer opt-in de `anon/authenticated` para funciones nuevas creadas por `postgres`, sin tocar `service_role` ni defaults de plataforma en el primer lote.

## Uso histórico relevante

`pg_stat_statements` confirma tráfico real en los flujos modernos:

- `pair_device_with_code(...)`: múltiples firmas/planes con decenas de llamadas.
- `pair_admin_device_with_password(...)`: uso real registrado.
- `activate_device(...)`: 27 llamadas históricas.

Esto justifica mantener pairing público y tratar `activate_device` como compatibilidad legacy hasta identificar su consumidor.

## Otros pendientes

- Varias funciones antiguas usan `search_path=public` o `public, extensions`; normalizar gradualmente hacia `search_path=''` cuando se modifiquen por otros motivos.
- `Leaked Password Protection` está desactivada; pendiente separado.
- El pairing Admin mantiene deuda P1 por escritura directa en `auth.users` / `auth.identities`; no mezclar con este lote de grants.

## Próximo paso

Requiere autorización explícita del owner porque modifica Production:

1. aplicar únicamente los 4 `REVOKE ... FROM anon` de Super Admin;
2. verificar `anon=false`, `authenticated=true`, `service_role=true` en esas firmas;
3. volver a correr Security Advisor;
4. solo después preparar el hardening de default privileges como segundo cambio independiente.

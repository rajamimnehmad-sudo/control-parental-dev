# RPC EXECUTE audit — 2026-08-20

Estado: auditoría read-only. No se aplicaron cambios en Production.

## Contexto

Supabase Security Advisor detectó múltiples funciones `SECURITY DEFINER` ejecutables por `anon` y/o `authenticated`. Se inspeccionaron ACL reales y cuerpos de funciones para separar exposición legítima de permisos innecesarios.

Supabase recomienda restringir `EXECUTE` explícitamente en funciones `SECURITY DEFINER` y otorgarlo solo a los roles que realmente necesitan llamar cada RPC.

## Guardas base verificadas

- `public.require_super_admin()` no es ejecutable por `anon` ni `authenticated`; las RPC Super Admin la invocan internamente como `SECURITY DEFINER`.
- `public.is_super_admin()` no es ejecutable por `anon`; valida `auth.uid()` contra `public.super_admins`, `enabled = true`, `deleted_at is null`.
- `public.request_device_token()` lee `x-device-token` de `request.headers`.
- `device_token_matches*()` valida el token contra hashes del dispositivo o sesión de relink.

Prueba read-only con rol `anon` sobre `super_admin_list_app_ratings(1)`: rechazó con `Not authorized`.

## Clasificación

### A. `anon` necesario por diseño — mantener

Estas RPC dependen de `x-device-token` o deben funcionar antes de que exista una sesión Supabase autenticada:

- `admin_create_device_relink_code(uuid,integer)`
- `admin_device_token_matches(uuid)`
- `device_token_matches(uuid)`
- `device_token_matches_device(uuid)`
- `device_token_matches_role(uuid,text)`
- `auto_arm_device_protection(uuid)`
- `ack_device_protection_control(uuid,bigint,bigint,bigint,integer[])`
- `complete_own_device_relink()`
- `get_device_license_entitlement(uuid)`
- `list_device_announcements(integer)`
- `list_device_announcements_v2(integer)`
- `mark_device_announcements_read()`
- `dismiss_device_announcement(uuid)`
- `restore_device_announcement(uuid)`
- `register_device_push_token(text)`
- `report_own_device_metadata(uuid,text,text,text,integer)`
- `create_device_pairing_code(integer)` (soporta sesión autenticada o token de admin)
- `pair_device_with_code(text,text,integer,text)` (pairing inicial/relink)
- `pair_admin_device_with_password(text,text,text,text,integer)` (pairing inicial/relink)

No revocar `anon` de este grupo sin cambiar antes el modelo de autenticación del cliente Android.

### B. `authenticated` legítimo — mantener

RPC de administración y Super Admin que requieren sesión autenticada y guardas internas:

- `create_admin_pairing_code(text,text,integer)`
- `admin_archive_protected_user(uuid)`
- `admin_create_archived_user_restore_code(uuid,integer)`
- `admin_list_archived_protected_users()`
- `revoke_device(uuid)`
- RPC `super_admin_*` en general: verificadas las principales y todas llaman `require_super_admin()` antes de operar.

### C. `anon` innecesario — candidato de bajo riesgo a revocar

Estas funciones no pueden completar legítimamente una operación anónima y/o ya exigen Super Admin autenticado internamente:

1. `activate_device(text,text,integer,text)` — exige que el código pertenezca a una cuenta cuyo `owner_user_id = auth.uid()`; con rol anónimo `auth.uid()` es nulo.
2. `super_admin_create_announcement(uuid,text,text,text,timestamptz)` — exige `require_super_admin()`.
3. `super_admin_list_admin_contacts(uuid)` — exige `require_super_admin()`.
4. `super_admin_list_app_ratings(integer)` — exige `require_super_admin()`; prueba `anon` confirmó rechazo.
5. `super_admin_list_device_metadata(uuid)` — exige `require_super_admin()`.

Primer lote propuesto (NO aplicado):

```sql
revoke execute on function public.activate_device(text,text,integer,text) from anon;
revoke execute on function public.super_admin_create_announcement(uuid,text,text,text,timestamptz) from anon;
revoke execute on function public.super_admin_list_admin_contacts(uuid) from anon;
revoke execute on function public.super_admin_list_app_ratings(integer) from anon;
revoke execute on function public.super_admin_list_device_metadata(uuid) from anon;
```

Mantener `authenticated` y `service_role` para estas RPC.

## D. Internas correctamente cerradas — mantener

Entre otras:

- `broadcast_community_license_invalidation()`
- `ensure_device_protection_control()`
- `generate_device_offline_alerts(timestamptz)`
- `license_allows_activation(uuid,text)`
- `license_allows_relink(uuid,text)`
- `pair_admin_device_with_password_new_internal(...)`
- `pair_device_with_code_new_or_restore_internal(...)`
- `process_due_community_license_transitions(timestamptz)`
- `revoke_open_device_relinks(uuid)`
- `send_community_license_invalidation(uuid)`

Estas aparecen solo para `service_role`.

## Observaciones de seguridad

- Los grants observados son explícitos por rol; no provienen de `PUBLIC EXECUTE` en las funciones revisadas.
- Algunas funciones antiguas usan `search_path=public` o `public, extensions`; las más recientes usan `search_path=''`. Conviene normalizar gradualmente `SECURITY DEFINER` hacia `search_path=''` cuando se toquen por otros motivos, sin mezclarlo con este primer lote de permisos.
- Security Advisor también reporta `Leaked Password Protection` desactivada. Se mantiene como pendiente separado; no se cambió Auth.

## Próximo paso

1. Obtener autorización explícita del owner antes de cambiar grants en Production.
2. Aplicar solo el lote C en una transacción.
3. Verificar con `has_function_privilege` que `anon=false`, `authenticated=true`, `service_role=true` para esas cinco RPC.
4. Ejecutar Security Advisor otra vez y confirmar que no aparecen regresiones.
5. Continuar luego con clasificación de permisos `authenticated` de RPC no-client-facing si todavía aparecen avisos relevantes.

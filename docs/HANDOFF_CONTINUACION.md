# Handoff de continuidad

Este archivo es el contexto oficial para continuar el trabajo en otro chat.

No reanalizar la arquitectura desde cero. Continuar desde este estado.

## Proyecto

Ruta local:

```text
/Users/yejielnehmad/Documents/Codex/2026-06-29/files-mentioned-by-the-user-arquitectura
```

Proyecto Android multi-modulo con:

- App Usuario: `app-user`
- App Admin: `app-admin`
- Dominio: `core-domain`
- Data/Room/outbox: `core-data`, `core-database`
- Supabase REST/Auth/Realtime: `core-network`
- Sync/WorkManager/Realtime: `core-sync`
- PolicyEngine: `core-policy`
- Seguridad/activacion: `core-security`
- VPN: `feature-vpn`
- Accessibility: `feature-accessibility`
- Requests/usage/status/activation: features correspondientes

Arquitectura offline-first congelada para el MVP. No redisenar arquitectura salvo pedido explicito.

## Estado actual

Estamos en Fase 10 / 10.2, estabilizando bloqueo real y sincronizacion.

El usuario esta probando App Usuario y App Admin en el mismo celular Samsung.

Objetivo actual:

```text
Admin -> Supabase -> Usuario -> PolicyEngine -> VPN/Accessibility
```

Debe funcionar hasta que:

- bloquear Chrome realmente bloquee Chrome;
- bloquear `example.com` realmente bloquee `example.com`;
- quitar/desactivar la regla vuelva a permitir acceso;
- solicitudes, reglas y dispositivos se sincronicen entre Usuario y Admin.

## Reglas importantes

- No avanzar de fase sin pedido explicito.
- No agregar features fuera del bug o fase indicada.
- No redisenar arquitectura.
- No tocar produccion.
- Usar solo Supabase DEV.
- No usar Service Role Key en Android.
- No guardar secretos en Git.
- No borrar datos/tablas sin confirmacion explicita.
- No ejecutar acciones destructivas sin confirmacion explicita.
- Errores tecnicos solo en Logcat; usuario ve mensajes simples.

## Regla nueva de publicacion DEV

El usuario ya autorizo que las publicaciones DEV se hagan automaticamente cuando corresponda.

No hace falta pedir permiso cada vez para:

- compilar APKs DEV;
- subir APKs DEV a Supabase Storage;
- actualizar manifiestos DEV;
- hacer `git push` si el objetivo es publicar la version DEV corregida.

Si se van a ejecutar comandos destructivos o borrar datos, ahi si pedir confirmacion puntual.

## Publicacion DEV

El repo GitHub ya esta privado.

El workflow de GitHub Actions publica automaticamente al hacer push a `main`.

Workflow:

```text
Publicar APKs DEV
```

Debe:

- correr en push a `main`;
- mantener ejecucion manual con `workflow_dispatch`;
- usar runner `mac-dev-runner`;
- compilar;
- correr tests;
- publicar solo si build/tests pasan;
- subir APKs y manifiestos a Supabase Storage DEV.

Supabase Storage DEV:

```text
bucket: dev-updates
```

Manifiestos esperados:

```text
app-user-dev-manifest.json
app-admin-dev-manifest.json
```

APKs esperadas:

```text
app-user-dev-debug.apk
app-admin-dev-debug.apk
```

Las apps ya tienen boton/pantalla de actualizacion desde la propia app. La intencion es que el usuario instale manualmente solo una vez, y desde ahi actualice desde la app.

## Version publicada conocida

Ultima version publicada conocida:

```text
versionCode 16
```

Commit publicado conocido:

```text
da5b353 Fix sync session refresh
```

Build/test local previo:

```bash
./gradlew --no-daemon :app-user:assembleDevDebug :app-admin:assembleDevDebug test -x uploadDevUpdatesToStorage
```

Resultado conocido:

```text
BUILD SUCCESSFUL
```

Workflow GitHub publicado conocido:

```text
Publicar APKs DEV
run 28584633941
success
```

## Cambios recientes importantes

### Commit `da5b353 Fix sync session refresh`

Publicado como `versionCode 16`.

Corrigio problemas de sesion/sync:

- `AuthTokenProvider.currentToken()` paso a ser `suspend`.
- `StoredAuthTokenProvider` refresca sesiones expiradas usando refresh token guardado.
- `SupabaseAuthClient.refreshSession` se usa para renovar sesion.
- `SupabaseRestClient`, `SupabaseActivationClient`, `SupabaseDevMaintenanceClient` y Realtime usan token suspend.
- App Admin fuerza sync inmediato al abrir Dispositivos.
- App Admin fuerza sync inmediato al abrir/guardar/toggle Reglas.
- `versionCode` dev de App Usuario y App Admin subio a 16.

### Commit `adf793b Fix accessibility activation status`

Publicado antes como `versionCode 15`.

Corrigio estado de accesibilidad/activacion:

- Se agrego `AccessibilityController` para detectar si el servicio esta habilitado desde `Settings.Secure`.
- `SystemStatusViewModel` normaliza estado de Accessibility y activacion.
- La UI puede mostrar `Activada`.
- `AccessibilityPolicySnapshotProvider` usa activacion local para marcar licencia activa.
- Accessibility escucha mas eventos:
  - `TYPE_WINDOW_STATE_CHANGED`
  - `TYPE_WINDOWS_CHANGED`
  - `TYPE_VIEW_FOCUSED`

## Problema actual reportado

El usuario reporta que sigue sin bloquear:

- apps;
- dominios.

Tambien reporto previamente que parecia que Usuario y Admin no estaban sincronizadas:

- solicitudes no llegan;
- reglas no aplican;
- dispositivos no se ven correctamente.

El usuario esta usando ambas apps en el mismo Samsung.

## Diagnostico parcial ya observado

### PolicyEngine

`DefaultPolicyEngine` parece correcto:

- APP rules matchean por `packageName`.
- DOMAIN rules matchean dominio exacto o subdominio.
- `RuleAction.Block` devuelve `PolicyDecision.Block`.

### Accessibility

`ProtectorAccessibilityService`:

- recibe eventos;
- evalua la app visible;
- si decide bloquear, ejecuta `GLOBAL_ACTION_HOME`;
- App Usuario y App Admin estan en whitelist permanente;
- Chrome no deberia estar whitelisteado.

Si no bloquea apps, revisar:

- si Accessibility realmente esta activa en Android;
- si la UI muestra `Activa`;
- si la regla llego a Room local de Usuario;
- si el target de regla es el paquete correcto, por ejemplo `com.android.chrome`;
- si Modo Rescate DEV esta desactivado;
- si el snapshot de politica se actualiza tras sync.

### VPN

`FilterVpnService`:

- evalua dominios DNS;
- si bloquea, responde NXDOMAIN;
- si la decision es `RequireActivation`, deja pasar.

Posible bug importante:

- `VpnPolicySnapshotProvider` no normaliza licencia activa con `DeviceActivationRepository`.
- `AccessibilityPolicySnapshotProvider` si lo hace.
- Si `SystemHealth` queda en `PendingActivation`, VPN puede decidir `RequireActivation` y dejar pasar dominios.

Esto podria explicar por que dominios no se bloquean aunque VPN este activa.

### Policies activas

Posible bug adicional:

- `PolicyDao.observeActivePolicy()` podria estar eligiendo una policy activa vieja si hay varias.
- Si Admin crea nuevas policies activas pero Usuario observa otra policy sin reglas, no se bloquea nada.
- Revisar ordenamiento por `updatedAtEpochMillis DESC`.
- Revisar si al aplicar policy remota se mantienen varias policies activas.

## Archivos a revisar primero

```text
core-database/src/main/java/com/contentfilter/core/database/dao/PolicyDao.kt
core-database/src/main/java/com/contentfilter/core/database/entity/PolicyEntity.kt
core-database/src/main/java/com/contentfilter/core/database/entity/PolicyRuleEntity.kt
core-data/src/main/java/com/contentfilter/core/data/repository/RoomPolicyRepository.kt
core-data/src/main/java/com/contentfilter/core/data/policy/AccessibilityPolicySnapshotProvider.kt
core-data/src/main/java/com/contentfilter/core/data/policy/VpnPolicySnapshotProvider.kt
core-sync/src/main/java/com/contentfilter/core/sync/engine/RoomRemoteApplier.kt
core-network/src/main/java/com/contentfilter/core/network/dto/RemotePolicyDto.kt
core-network/src/main/java/com/contentfilter/core/network/dto/RemotePolicyRuleDto.kt
feature-accessibility/src/main/java/com/contentfilter/feature/accessibility/ProtectorAccessibilityService.kt
feature-vpn/src/main/java/com/contentfilter/feature/vpn/FilterVpnService.kt
```

## Consultas Supabase utiles

Estas consultas son diagnosticas y no destructivas:

```bash
supabase db query --linked "select id, account_id, active, version, updated_at, deleted_at from public.policies order by updated_at desc limit 20;"
```

```bash
supabase db query --linked "select id, account_id, policy_id, scope, target, action, enabled, updated_at, deleted_at from public.policy_rules order by updated_at desc limit 20;"
```

```bash
supabase db query --linked "select id, account_id, package_name, device_role, activated_at, deleted_at, updated_at from public.devices order by updated_at desc limit 20;"
```

```bash
supabase db query --linked "select id, account_id, device_id, request_type, target_package_name, target_domain, requested_minutes, status, deleted_at, created_at, updated_at from public.access_requests order by updated_at desc limit 20;"
```

## Comandos principales

Build/test sin publicar:

```bash
./gradlew --no-daemon :app-user:assembleDevDebug :app-admin:assembleDevDebug test -x uploadDevUpdatesToStorage
```

Build que puede disparar publicacion local si esta configurado:

```bash
./gradlew --no-daemon :app-user:assembleDevDebug :app-admin:assembleDevDebug test
```

Script manual de publicacion DEV:

```bash
scripts/upload_dev_updates.sh
```

GitHub workflow se dispara con:

```bash
git push origin main
```

## Flujo recomendado para corregir el bug actual

1. Verificar en Supabase si Admin esta subiendo reglas:

```bash
supabase db query --linked "select id, account_id, policy_id, scope, target, action, enabled, updated_at, deleted_at from public.policy_rules order by updated_at desc limit 20;"
```

2. Verificar policies activas:

```bash
supabase db query --linked "select id, account_id, active, version, updated_at, deleted_at from public.policies order by updated_at desc limit 20;"
```

3. Revisar `PolicyDao.observeActivePolicy()`.

4. Si hay varias policies activas, asegurar que se observe la mas reciente o la correcta.

5. Revisar `VpnPolicySnapshotProvider` y alinearlo con `AccessibilityPolicySnapshotProvider` para que use activacion local si corresponde.

6. Confirmar que las reglas remotas se insertan en Room Usuario por `RoomRemoteApplier`.

7. Confirmar que Accessibility y VPN observan snapshot actualizado sin reiniciar app.

8. Bump de `versionCode` dev de ambas apps.

9. Ejecutar build/tests.

10. Commit y push a `main`.

11. Verificar workflow GitHub Actions.

12. Confirmar manifiestos/APKs publicados.

## APKs locales esperadas

```text
app-user/build/outputs/apk/dev/debug/app-user-dev-debug.apk
app-admin/build/outputs/apk/dev/debug/app-admin-dev-debug.apk
```

## Como probar en el celular

1. Abrir App Usuario.
2. Ir a `Actualizaciones`.
3. Actualizar si aparece version nueva.
4. Abrir App Admin.
5. Ir a `Actualizaciones`.
6. Actualizar si aparece version nueva.
7. Verificar que ambas apps esten activadas.
8. Verificar que Modo Rescate DEV este desactivado.
9. Verificar que Accessibility este activa.
10. Verificar que VPN este activa y que aparezca el icono de VPN del sistema.

### Prueba APP_ACCESS

En App Admin:

- crear regla de app;
- paquete: `com.android.chrome`;
- accion: `Bloquear`;
- estado: `Activa`.

Luego:

- esperar sync o forzar abriendo reglas/estado;
- abrir Chrome.

Resultado esperado:

- Chrome debe cerrarse o volver al Home inmediatamente.

Importante:

- App Usuario y App Admin estan en whitelist DEV y no deben bloquearse.
- Ajustes, Launcher, instalador, Play Store y servicios criticos tampoco deben bloquearse.

### Prueba DOMAIN_ACCESS

En App Admin:

- crear regla de dominio;
- dominio: `example.com`;
- accion: `Bloquear`;
- estado: `Activa`.

Luego:

- VPN activa;
- abrir navegador;
- entrar a `example.com`.

Resultado esperado:

- DNS debe fallar/bloquear.

Luego:

- desactivar/quitar regla;
- esperar sync.

Resultado esperado:

- `example.com` vuelve a abrir.

### Prueba solicitudes

En App Usuario:

- crear solicitud de app, dominio o tiempo extra.

En App Admin:

- abrir Solicitudes;
- debe aparecer como Pendiente;
- aprobar o rechazar.

En App Usuario:

- Mis solicitudes debe reflejar Aprobada/Rechazada;
- si es tiempo extra, debe aparecer tiempo concedido.

## Notas sobre herramientas DEV

Hay herramientas DEV para:

- borrar solicitudes;
- borrar reglas;
- borrar tiempos extra;
- borrar dispositivos duplicados;
- reset DEV completo.

Reset DEV debe limpiar:

- solicitudes;
- reglas;
- grants;
- dispositivos duplicados;
- Room;
- Outbox;
- cache.

No debe borrar:

- Auth;
- Account;
- codigos de activacion;
- policies base.

Antes de ejecutar borrados destructivos, pedir confirmacion.

## Si la app dice "Ya tenes la ultima version"

Diagnosticar:

1. `versionCode` instalado.
2. `versionCode` del manifiesto publicado.
3. `versionCode` real del APK publicado.
4. Commit que genero esa publicacion.

No asumir que la APK instalada contiene el ultimo commit si el `versionCode` no cambio.

## Resumen corto para el siguiente chat

Continuar desde Fase 10.2. El bug actual es que no bloquea apps ni dominios. La version publicada conocida es `versionCode 16`, commit `da5b353`. Revisar primero sync de rules/policies, `PolicyDao.observeActivePolicy()` y `VpnPolicySnapshotProvider`. Si se corrige, compilar, testear, subir `versionCode`, commit y push a `main`; no hace falta pedir permiso para publicar DEV.

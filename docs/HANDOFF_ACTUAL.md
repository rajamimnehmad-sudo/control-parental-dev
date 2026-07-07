# HANDOFF ACTUAL - Continuacion Codex

Fecha de corte: 2026-07-02

Tomar este archivo como contexto oficial para continuar en otro chat. No reanalizar la arquitectura desde cero.

## Proyecto

Ruta local:

```text
/Users/yejielnehmad/Documents/Codex/2026-06-29/files-mentioned-by-the-user-arquitectura
```

Proyecto Android multi-modulo offline-first:

- `app-user`: app del usuario protegido.
- `app-admin`: app administrador.
- `core-domain`: modelos, interfaces y use cases.
- `core-data`: repositorios Room y outbox.
- `core-database`: Room, DAOs, entidades y migraciones.
- `core-network`: Supabase REST/Auth/Realtime.
- `core-sync`: SyncEngine, WorkManager, Outbox, Realtime.
- `core-policy`: PolicyEngine puro.
- `core-security`: sesion Auth y activacion.
- `feature-vpn`: bloqueo de dominios.
- `feature-accessibility`: bloqueo de apps.
- `feature-requests`, `feature-activation`, `feature-status`, `feature-usage`.

Arquitectura MVP congelada. No redisenar salvo pedido explicito.

## Reglas de trabajo

- No avanzar de fase si el usuario no lo pide.
- No agregar features fuera del pedido puntual.
- No tocar produccion.
- Usar solo Supabase DEV.
- No usar Service Role Key en Android.
- No guardar secretos reales en Git.
- No borrar tablas ni datos sin confirmacion explicita.
- No ejecutar acciones destructivas sin confirmacion explicita.
- Errores tecnicos solo en Logcat; usuario ve mensajes simples.
- El usuario prueba App Usuario y App Admin en el mismo celular Samsung.

## Estado funcional actual

La app ya tiene:

- Activacion real con Supabase.
- App Usuario y App Admin conectadas al mismo account DEV.
- Dispositivos sincronizados despues de fixes recientes.
- Reglas Admin -> Supabase -> Usuario.
- Bloqueo real de dominios via VPN confirmado por el usuario.
- Bloqueo real de apps via Accessibility confirmado por el usuario.
- Cierre de apps bloqueadas ahora es rapido.
- Boton/pantalla de actualizaciones en ambas apps.
- Publicacion DEV por Supabase Storage.
- Herramientas DEV en Admin.

Ultimo reporte del usuario antes de este handoff:

- El cierre de apps ya esta rapido.
- La pantalla de reglas estaba confusa.
- Eliminar tarjetas no funcionaba bien.
- No se entendia el limite diario global de 120 minutos.

Se corrigio en la ultima tanda:

- Pantalla Reglas simplificada.
- Formulario separado para bloquear app.
- Formulario separado para bloquear dominio.
- Formulario separado para limite diario de app.
- Se aclaro que el limite diario es por app, no global del telefono.
- Se corrigio envio de `deleted_at` en `RemotePolicyRuleDto.toJson()` para que borrar regla llegue a Supabase.
- `versionCode` DEV actual: 106 en Usuario y Admin.
- Build y tests pasaron.
- APKs DEV publicadas en Supabase Storage.

## Version publicada actual

Version DEV publicada en Supabase Storage:

```text
versionCode 106
versionName 1.0.1-dev
```

Manifiestos:

```text
https://syeycayasyufedwoprea.supabase.co/storage/v1/object/public/dev-updates/app-user-dev-manifest.json
https://syeycayasyufedwoprea.supabase.co/storage/v1/object/public/dev-updates/app-admin-dev-manifest.json
```

APKs:

```text
https://syeycayasyufedwoprea.supabase.co/storage/v1/object/public/dev-updates/app-user-dev-debug.apk
https://syeycayasyufedwoprea.supabase.co/storage/v1/object/public/dev-updates/app-admin-dev-debug.apk
```

SHA-256 publicados/locales:

```text
Usuario: fe99c800daba6bc9a2d69355db74f36bf50e05790fc17bf578fa90fbdc4710d7
Admin:   ede8208a9a204bc1e0edfe8b5bbf0cb17cea6199f00c315dbc1946db981826ca
```

Rutas locales:

```text
app-user/build/outputs/apk/dev/debug/app-user-dev-debug.apk
app-admin/build/outputs/apk/dev/debug/app-admin-dev-debug.apk
build/dev-updates/app-user-dev-debug.apk
build/dev-updates/app-admin-dev-debug.apk
```

## Git y estado de cambios

Ultimo commit confirmado en Git:

```text
da5b353 Fix sync session refresh
```

Importante: revisar `git status --short` antes de seguir. No revertir cambios locales sin confirmacion del usuario.

Antes de seguir trabajando, revisar:

```bash
git status --short
git diff --stat
```

No revertir cambios locales sin confirmacion del usuario.

## Cambios locales importantes despues de `da5b353`

### Sync y dispositivos

- `DefaultSyncEngine` agrega pulls full para core data, dispositivos, solicitudes y resultados.
- `DevicesViewModel` fuerza sync completo al abrir Dispositivos.
- `RulesViewModel` fuerza sync completo tras guardar/toggle/borrar reglas.
- `ActivationViewModel` y `AdminAuthViewModel` hacen sync completo inmediato tras activar/login.
- `DefaultRealtimeSyncCoordinator` reconecta mejor y arranca correctamente.
- `JsonDtoExtensions.kt` evita problemas con valores null de Supabase.

Archivos principales:

- `core-sync/src/main/java/com/contentfilter/core/sync/engine/DefaultSyncEngine.kt`
- `core-sync/src/main/java/com/contentfilter/core/sync/engine/SyncEngine.kt`
- `core-sync/src/main/java/com/contentfilter/core/sync/realtime/DefaultRealtimeSyncCoordinator.kt`
- `app-admin/src/main/java/com/contentfilter/admin/devices/DevicesViewModel.kt`
- `app-admin/src/main/java/com/contentfilter/admin/rules/RulesViewModel.kt`
- `feature-activation/src/main/java/com/contentfilter/feature/activation/ActivationViewModel.kt`

### Bloqueo de apps

- `ProtectorAccessibilityService` bloquea inmediatamente sin esperar sync remoto antes de hacer HOME.
- Recheck reducido a 120 ms.
- Background policy refresh cada 1 segundo.
- Accessibility escucha mas eventos, incluido `typeWindowContentChanged`.
- App Usuario y App Admin estan en whitelist DEV.

Archivos principales:

- `feature-accessibility/src/main/java/com/contentfilter/feature/accessibility/service/ProtectorAccessibilityService.kt`
- `app-user/src/main/res/xml/accessibility_service_config.xml`
- `feature-accessibility/build.gradle.kts`

### Bloqueo de dominios

- VPN ya bloquea dominios confirmados por el usuario.
- App Admin no debe quedar bloqueada.
- Revisar si hay problemas solo si el usuario reporta regresion.

Archivo principal:

- `feature-vpn/src/main/java/com/contentfilter/feature/vpn/policy/VpnPolicySnapshotProvider.kt`

### Reglas Admin

Estado actual de la pantalla Reglas:

- Bloquear aplicacion: paquete exacto, ejemplo `com.android.chrome`.
- Bloquear sitio web: dominio simple, ejemplo `example.com`.
- Limite diario de aplicacion: paquete + minutos por dia.
- Lista de reglas con activar/desactivar y eliminar.
- Lista de limites diarios.

El limite diario implementado es por app. No es limite global del telefono.

Archivos principales:

- `app-admin/src/main/java/com/contentfilter/admin/rules/RulesScreen.kt`
- `app-admin/src/main/java/com/contentfilter/admin/rules/RulesUiState.kt`
- `app-admin/src/main/java/com/contentfilter/admin/rules/RulesViewModel.kt`
- `app-admin/src/main/java/com/contentfilter/admin/di/AdminUseCaseModule.kt`

### Borrado de reglas

Bug corregido:

- Antes el delete local se hacia, pero el payload remoto no enviaba `deleted_at`.
- Supabase no recibia borrado logico real.
- Se corrigio `RemotePolicyRuleDto.toJson()` para incluir `deleted_at`.

Archivos:

- `core-data/src/main/java/com/contentfilter/core/data/RoomPolicyRepository.kt`
- `core-network/src/main/java/com/contentfilter/core/network/dto/RemotePolicyRuleDto.kt`

### Daily limits

Se agrego guardado/sync de `daily_limits`.

Archivos:

- `core-domain/src/main/kotlin/com/contentfilter/core/domain/usecase/admin/ObserveDailyLimitsUseCase.kt`
- `core-domain/src/main/kotlin/com/contentfilter/core/domain/usecase/admin/SaveDailyLimitUseCase.kt`
- `core-data/src/main/java/com/contentfilter/core/data/RoomDailyLimitRepository.kt`
- `core-network/src/main/java/com/contentfilter/core/network/dto/RemoteDailyLimitDto.kt`
- `core-network/src/main/java/com/contentfilter/core/network/remote/RemoteLimitRepository.kt`
- `core-network/src/main/java/com/contentfilter/core/network/remote/SupabaseRemoteLimitRepository.kt`
- `core-sync/src/main/java/com/contentfilter/core/sync/outbox/DefaultOutboxProcessor.kt`

Estado funcional esperado:

- Los limites de app se guardan localmente.
- Se encolan en outbox.
- Se suben a Supabase `daily_limits`.
- El PolicyEngine ya evalua daily limits para apps usando `dailyUsage`.

Pendiente a validar manualmente:

- Que el conteo de uso diario llegue correctamente para la app objetivo.
- Que al llegar al limite la app se bloquee.
- Que se resetee/renueve segun el dia.

## Supabase DEV

Proyecto DEV ya configurado y enlazado con Supabase CLI.

No usar Service Role Key en Android.

Bucket DEV:

```text
dev-updates
```

Scripts relevantes:

```bash
scripts/setup_supabase_dev.sh
scripts/prepare_dev_updates.sh
scripts/upload_dev_updates.sh
```

SQL relevante:

```text
supabase/schema.sql
supabase/rls.sql
supabase/dev_setup_all.sql
supabase/dev_test_data.sql
supabase/fix_activate_device_pgcrypto.sql
```

Códigos DEV legacy:

- Los códigos numéricos 1-100 quedan reemplazados por tokens aleatorios.
- Admin se activa por token de administrador en el dispositivo.
- Usuario se activa por token generado desde Admin.

## Build, test y publicacion

Comando de build/tests usado y exitoso:

```bash
./gradlew --no-daemon :app-user:assembleDevDebug :app-admin:assembleDevDebug test -x uploadDevUpdatesToStorage
```

Preparar y publicar DEV:

```bash
scripts/prepare_dev_updates.sh
scripts/upload_dev_updates.sh
```

Nota: en el sandbox de Codex, Gradle puede requerir permiso externo porque toca `~/.gradle`; Supabase CLI puede requerir permiso externo porque toca `~/.supabase` y usa red.

## Como probar ahora

En el celular Samsung, actualizar ambas apps desde:

```text
Actualizaciones
```

Luego probar:

1. Abrir App Admin -> Reglas.
2. Eliminar la regla vieja de Chrome si existe.
3. Esperar sync o abrir App Usuario para forzar sync.
4. Chrome deberia volver a abrir.
5. Crear regla nueva:

```text
Bloquear aplicacion
Paquete: com.android.chrome
```

6. Abrir Chrome.
7. Debe cerrarse rapido.
8. Crear dominio:

```text
Bloquear sitio web
Dominio: example.com
```

9. Con VPN activa, `example.com` debe bloquearse.
10. Eliminar/desactivar regla y validar que vuelve a permitir.
11. Probar limite diario de app con pocos minutos:

```text
Paquete: com.android.chrome
Minutos por dia: 1
```

12. Usar Chrome y validar si se bloquea al superar el minuto.

Si el bloqueo de app no responde tras actualizar, pedir al usuario:

- apagar y prender el permiso de Accesibilidad `Content Filter`;
- abrir App Usuario;
- confirmar que Estado muestra Accesibilidad Activa y Activacion Activada.

## Pendientes conocidos

- Validar manualmente que eliminar tarjeta ya no vuelve despues del sync.
- Validar daily limit real con uso acumulado.
- Revisar si se necesita boton de eliminar tambien para tarjetas de limites diarios.
- Revisar si se necesita "Permitir" o "Requiere autorizacion" en una pantalla avanzada despues; por ahora la UI se simplifico a bloqueo basico.
- Committear cambios locales si el usuario autoriza.
- Si se abre otro chat, primero leer este archivo y despues revisar `git status --short`.

## Prompt recomendado para nuevo chat

Copiar esto en el nuevo chat:

```text
Toma docs/HANDOFF_ACTUAL.md como contexto oficial del proyecto.
No reanalices la arquitectura.
No avances de fase.
Continuemos desde ese estado.
Primero revisa git status --short y confirma que versionCode 106 esta publicado.
```

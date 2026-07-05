# CODEX MAP

Mapa tecnico para trabajar rapido en tickets futuros. Usar junto con `docs/HANDOFF_ACTUAL.md` como contexto oficial. No reanalizar arquitectura si el ticket ya indica un area.

## Arquitectura general

Proyecto Android multi-modulo, offline-first:

- App Usuario escribe/lee estado local en Room y sincroniza con Supabase DEV.
- App Admin lee dispositivos/reglas/solicitudes desde Room y sincroniza con Supabase DEV.
- Room es la fuente inmediata para UI local.
- Supabase DEV es el puente remoto entre Admin y Usuario.
- Outbox sube cambios locales a remoto.
- Realtime y SyncWorker bajan cambios remotos a Room.
- PolicyEngine evalua reglas puras sobre `PolicySnapshot`.
- Accessibility aplica bloqueo de apps.
- VPN/DNS aplica bloqueo web.

## Modulos principales

- `app-user`: shell de App Usuario, navegacion principal, Mis apps, Internet y Actualizaciones.
- `app-admin`: shell de App Admin, Panel, Dispositivos, Solicitudes, Reglas y Actualizaciones.
- `core-domain`: modelos, repositorios como interfaces y use cases.
- `core-data`: implementaciones Room de repositorios y mappers dominio/entidad.
- `core-database`: Room, DAOs, entidades, migraciones y schemas.
- `core-network`: DTOs, Supabase REST/Auth/Realtime y repositorios remotos.
- `core-sync`: `SyncEngine`, WorkManager, outbox, realtime y aplicador remoto a Room.
- `core-policy`: `PolicyEngine` puro para apps/dominios/timers.
- `core-security`: sesion, activacion y tokens locales.
- `core-update`: chequeo/descarga/instalacion de APK DEV.
- `feature-accessibility`: servicio de accesibilidad, policy snapshot para apps y conteo de uso.
- `feature-vpn`: VPN local, DNS forwarder, evaluador de dominios y uso DNS.
- `feature-requests`: UI Usuario generica para solicitudes.
- `feature-status`: UI Usuario de estado.
- `feature-usage`: UI Usuario de uso.
- `feature-activation`: activacion/enlace.

## Pantallas

### App Admin

- Shell/navegacion: `app-admin/src/main/java/com/contentfilter/admin/MainActivity.kt`
- Login: `app-admin/src/main/java/com/contentfilter/admin/auth/AdminAuthScreen.kt`
- Panel: `app-admin/src/main/java/com/contentfilter/admin/dashboard/DashboardScreen.kt`
- Dispositivos: `app-admin/src/main/java/com/contentfilter/admin/devices/DevicesScreen.kt`
- Solicitudes Admin: `app-admin/src/main/java/com/contentfilter/admin/requests/AdminRequestsScreen.kt`
- Reglas Admin: `app-admin/src/main/java/com/contentfilter/admin/rules/RulesScreen.kt`
- Actualizaciones Admin: `app-admin/src/main/java/com/contentfilter/admin/updates/AdminUpdatesScreen.kt`
- Herramientas DEV: `app-admin/src/main/java/com/contentfilter/admin/devtools/AdminDevTools.kt`

### App Usuario

- Shell/navegacion: `app-user/src/main/java/com/contentfilter/user/MainActivity.kt`
- Estado: `feature-status/src/main/java/com/contentfilter/feature/status/SystemStatusScreen.kt`
- Mis apps: `app-user/src/main/java/com/contentfilter/user/apps/MyAppsScreen.kt`
- Mis solicitudes: `feature-requests/src/main/java/com/contentfilter/feature/requests/RequestsScreen.kt`
- Internet: `app-user/src/main/java/com/contentfilter/user/internet/InternetScreen.kt`
- Actualizaciones Usuario: `app-user/src/main/java/com/contentfilter/user/updates/UpdatesScreen.kt`
- Activacion/enlace: `feature-activation/src/main/java/com/contentfilter/feature/activation/ActivationScreen.kt`
- Bloqueo tecnico: `feature-block/src/main/java/com/contentfilter/feature/block/BlockScreen.kt`
- Uso tecnico: `feature-usage/src/main/java/com/contentfilter/feature/usage/UsageScreen.kt`

## ViewModels

### App Admin

- Login: `app-admin/src/main/java/com/contentfilter/admin/auth/AdminAuthViewModel.kt`
- Panel: `app-admin/src/main/java/com/contentfilter/admin/dashboard/DashboardViewModel.kt`
- Dispositivos: `app-admin/src/main/java/com/contentfilter/admin/devices/DevicesViewModel.kt`
- Solicitudes Admin: `app-admin/src/main/java/com/contentfilter/admin/requests/AdminRequestsViewModel.kt`
- Reglas Admin: `app-admin/src/main/java/com/contentfilter/admin/rules/RulesViewModel.kt`
- Actualizaciones Admin: `app-admin/src/main/java/com/contentfilter/admin/updates/AdminUpdatesViewModel.kt`

### App Usuario

- Estado: `feature-status/src/main/java/com/contentfilter/feature/status/SystemStatusViewModel.kt`
- Mis apps: `app-user/src/main/java/com/contentfilter/user/apps/MyAppsViewModel.kt`
- Mis solicitudes: `feature-requests/src/main/java/com/contentfilter/feature/requests/RequestsViewModel.kt`
- Internet: `app-user/src/main/java/com/contentfilter/user/internet/InternetViewModel.kt`
- Actualizaciones Usuario: `app-user/src/main/java/com/contentfilter/user/updates/UpdatesViewModel.kt`
- Activacion: `feature-activation/src/main/java/com/contentfilter/feature/activation/ActivationViewModel.kt`
- Uso: `feature-usage/src/main/java/com/contentfilter/feature/usage/UsageViewModel.kt`

## Repositories

### Interfaces de dominio

- Politicas/reglas: `core-domain/src/main/kotlin/com/contentfilter/core/domain/repository/PolicyRepository.kt`
- Limites diarios: `core-domain/src/main/kotlin/com/contentfilter/core/domain/repository/DailyLimitRepository.kt`
- Solicitudes: `core-domain/src/main/kotlin/com/contentfilter/core/domain/repository/AccessRequestRepository.kt`
- Tiempo extra: `core-domain/src/main/kotlin/com/contentfilter/core/domain/repository/ExtraTimeGrantRepository.kt`
- Dispositivos: `core-domain/src/main/kotlin/com/contentfilter/core/domain/repository/DeviceRepository.kt`
- Activacion de dispositivo: `core-domain/src/main/kotlin/com/contentfilter/core/domain/repository/DeviceActivationRepository.kt`
- Sesiones de uso: `core-domain/src/main/kotlin/com/contentfilter/core/domain/repository/UsageSessionRepository.kt`
- Estado del sistema: `core-domain/src/main/kotlin/com/contentfilter/core/domain/repository/SystemStatusRepository.kt`
- Telemetria: `core-domain/src/main/kotlin/com/contentfilter/core/domain/repository/TelemetryRepository.kt`

### Implementaciones Room

- Politicas/reglas: `core-data/src/main/java/com/contentfilter/core/data/RoomPolicyRepository.kt`
- Limites diarios: `core-data/src/main/java/com/contentfilter/core/data/RoomDailyLimitRepository.kt`
- Solicitudes: `core-data/src/main/java/com/contentfilter/core/data/RoomAccessRequestRepository.kt`
- Tiempo extra: `core-data/src/main/java/com/contentfilter/core/data/RoomExtraTimeGrantRepository.kt`
- Dispositivos: `core-data/src/main/java/com/contentfilter/core/data/RoomDeviceRepository.kt`
- Activacion de dispositivo: `core-data/src/main/java/com/contentfilter/core/data/RoomDeviceActivationRepository.kt`
- Sesiones de uso: `core-data/src/main/java/com/contentfilter/core/data/RoomUsageSessionRepository.kt`
- Estado del sistema: `core-data/src/main/java/com/contentfilter/core/data/RoomSystemStatusRepository.kt`

### Repositorios remotos Supabase

- Politicas/reglas: `core-network/src/main/java/com/contentfilter/core/network/remote/SupabaseRemotePolicyRepository.kt`
- Limites diarios: `core-network/src/main/java/com/contentfilter/core/network/remote/SupabaseRemoteLimitRepository.kt`
- Solicitudes y tiempo extra: `core-network/src/main/java/com/contentfilter/core/network/remote/SupabaseRemoteRequestRepository.kt`
- Dispositivos: `core-network/src/main/java/com/contentfilter/core/network/remote/SupabaseRemoteDeviceRepository.kt`
- Apps instaladas: `core-network/src/main/java/com/contentfilter/core/network/remote/SupabaseRemoteInstalledAppRepository.kt`
- Auth/activacion: `core-network/src/main/java/com/contentfilter/core/network/remote/SupabaseAuthClient.kt`, `core-network/src/main/java/com/contentfilter/core/network/remote/SupabaseActivationClient.kt`

## Servicios y motores

- VPN: `feature-vpn/src/main/java/com/contentfilter/feature/vpn/service/FilterVpnService.kt`
- Control VPN: `feature-vpn/src/main/java/com/contentfilter/feature/vpn/service/VpnController.kt`
- DNS forwarder: `feature-vpn/src/main/java/com/contentfilter/feature/vpn/dns/DnsForwarder.kt`
- Evaluador dominios: `feature-vpn/src/main/java/com/contentfilter/feature/vpn/policy/VpnDomainPolicyEvaluator.kt`
- Snapshot VPN: `feature-vpn/src/main/java/com/contentfilter/feature/vpn/policy/VpnPolicySnapshotProvider.kt`
- Uso DNS por dominio: `feature-vpn/src/main/java/com/contentfilter/feature/vpn/policy/DomainDnsUsageTracker.kt`
- Accessibility service: `feature-accessibility/src/main/java/com/contentfilter/feature/accessibility/service/ProtectorAccessibilityService.kt`
- Control Accessibility: `feature-accessibility/src/main/java/com/contentfilter/feature/accessibility/service/AccessibilityController.kt`
- Evaluador apps Accessibility: `feature-accessibility/src/main/java/com/contentfilter/feature/accessibility/policy/AccessibilityAppPolicyEvaluator.kt`
- Snapshot Accessibility: `feature-accessibility/src/main/java/com/contentfilter/feature/accessibility/policy/AccessibilityPolicySnapshotProvider.kt`
- Tracker uso apps: `feature-accessibility/src/main/java/com/contentfilter/feature/accessibility/time/AppUsageTracker.kt`
- PolicyEngine: `core-policy/src/main/kotlin/com/contentfilter/core/policy/DefaultPolicyEngine.kt`
- Sync engine: `core-sync/src/main/java/com/contentfilter/core/sync/engine/DefaultSyncEngine.kt`
- Sync worker/scheduler: `core-sync/src/main/java/com/contentfilter/core/sync/SyncWorker.kt`, `core-sync/src/main/java/com/contentfilter/core/sync/WorkManagerSyncScheduler.kt`
- Realtime coordinator: `core-sync/src/main/java/com/contentfilter/core/sync/realtime/DefaultRealtimeSyncCoordinator.kt`
- Outbox processor: `core-sync/src/main/java/com/contentfilter/core/sync/outbox/DefaultOutboxProcessor.kt`
- Aplicador remoto a Room: `core-sync/src/main/java/com/contentfilter/core/sync/engine/RoomRemoteApplier.kt`
- Actualizaciones APK DEV: `core-update/src/main/java/com/contentfilter/core/update/repository/DevApkUpdateRepository.kt`

## Room y DAOs

- Base Room: `core-database/src/main/java/com/contentfilter/core/database/AppDatabase.kt`
- Migraciones: `core-database/src/main/java/com/contentfilter/core/database/DatabaseMigrations.kt`
- Policy DAO: `core-database/src/main/java/com/contentfilter/core/database/dao/PolicyDao.kt`
- Daily limit DAO: `core-database/src/main/java/com/contentfilter/core/database/dao/DailyLimitDao.kt`
- Usage session DAO: `core-database/src/main/java/com/contentfilter/core/database/dao/UsageSessionDao.kt`
- Access request DAO: `core-database/src/main/java/com/contentfilter/core/database/dao/AccessRequestDao.kt`
- Extra time grant DAO: `core-database/src/main/java/com/contentfilter/core/database/dao/ExtraTimeGrantDao.kt`
- Device DAO: `core-database/src/main/java/com/contentfilter/core/database/dao/DeviceDao.kt`
- Outbox DAO: `core-database/src/main/java/com/contentfilter/core/database/dao/OutboxOperationDao.kt`
- Sync cursor DAO: `core-database/src/main/java/com/contentfilter/core/database/dao/SyncCursorDao.kt`

## Flujo Usuario -> Admin -> Supabase -> Usuario

1. Usuario crea dato local o estado local en Room.
2. Repository Room guarda y, si corresponde, encola outbox.
3. `DefaultOutboxProcessor` sube a Supabase DEV.
4. Admin recibe por Realtime/SyncWorker/SyncEngine.
5. `RoomRemoteApplier` aplica remoto en Room Admin.
6. UI Admin observa Room y actualiza pantalla.
7. Admin aprueba/modifica reglas/solicitudes.
8. Admin guarda en Room Admin y encola outbox.
9. Supabase DEV recibe cambio.
10. Usuario baja cambio por Realtime/SyncWorker/SyncEngine.
11. Servicios locales usan Room/PolicySnapshot sin depender de UI.

## Flujo Internet

1. Admin cambia reglas web en `RulesScreen`/`RulesViewModel`.
2. `RoomPolicyRepository` guarda reglas `RuleScope.Domain`.
3. Outbox sincroniza reglas a Supabase DEV.
4. Usuario baja reglas a Room.
5. `VpnPolicySnapshotProvider` construye `PolicySnapshot`.
6. `FilterVpnService` recibe consulta DNS.
7. `DnsForwarder` parsea/resuelve DNS.
8. `VpnDomainPolicyEvaluator` decide allow/block/request segun reglas, lista blanca/negra y limites.
9. `DomainDnsUsageTracker` mantiene uso aproximado DNS-based para dominio/familia.

## Flujo Apps

1. Usuario detecta apps instaladas con `InstalledAppPublisher`.
2. Usa `PackageManager` y filtros locales para excluir apps criticas.
3. Publica `RemoteInstalledAppDto` via `RemoteInstalledAppRepository`.
4. Admin baja lista remota y la muestra desde `RulesViewModel`.
5. Admin permite/bloquea app creando o actualizando regla por packageName.
6. Usuario baja regla.
7. `ProtectorAccessibilityService` observa app foreground.
8. `AccessibilityAppPolicyEvaluator` evalua `PolicySnapshot`.
9. Si bloquea, Accessibility manda HOME y muestra estado/telemetria.

## Flujo Solicitudes

1. Usuario solicita app, tiempo o dominio desde `MyAppsViewModel`, `InternetViewModel` o `RequestsViewModel`.
2. `RoomAccessRequestRepository` guarda solicitud local y la encola.
3. Outbox sube `access_requests`.
4. Admin baja solicitudes y las muestra en `AdminRequestsViewModel`.
5. Admin aprueba/rechaza.
6. Para tiempo extra, se crea `ExtraTimeGrant`.
7. Para app/dominio, se crea o actualiza regla.
8. Usuario baja resolucion por sync y la UI refleja estado.

## Flujo Timers

### Apps

1. `ProtectorAccessibilityService` detecta app foreground.
2. `AppUsageTracker` acumula tiempo activo por packageName.
3. Se combina con `dailyUsage` persistido/proyectado.
4. `DefaultPolicyEngine.evaluateApp` compara uso con `dailyLimits`.
5. Al superar limite por app, devuelve bloqueo.
6. Accessibility aplica bloqueo sin depender de Supabase en ese instante.

### Dominios

1. VPN ve consultas DNS.
2. `DomainDnsUsageTracker` acumula uso aproximado por dominio/familia.
3. `VpnDomainPolicyEvaluator` compara contra daily limits de dominio.
4. Al superar limite, DNS falla/bloquea.
5. Precision: MVP DNS-based, no mide tiempo real de pagina con exactitud perfecta.

## NO TOCAR salvo ticket explicito

- `core-policy/src/main/kotlin/com/contentfilter/core/policy/DefaultPolicyEngine.kt`
- `feature-vpn/src/main/java/com/contentfilter/feature/vpn/service/FilterVpnService.kt`
- `feature-accessibility/src/main/java/com/contentfilter/feature/accessibility/service/ProtectorAccessibilityService.kt`
- `core-sync/src/main/java/com/contentfilter/core/sync/engine/DefaultSyncEngine.kt`
- `core-sync/src/main/java/com/contentfilter/core/sync/outbox/DefaultOutboxProcessor.kt`
- `core-update/src/main/java/com/contentfilter/core/update/repository/DevApkUpdateRepository.kt`
- `.github/workflows/*`
- `scripts/publicar_dev.sh`
- Supabase Auth/config/secrets.
- Room migrations/schemas, salvo cambio de DB.


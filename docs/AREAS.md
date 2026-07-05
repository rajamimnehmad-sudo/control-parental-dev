# AREAS

Indice tecnico por areas para que cada ticket pueda empezar con `Area: ...` y evitar revisar archivos innecesarios.

## Admin Rules

- UI: `app-admin/src/main/java/com/contentfilter/admin/rules/RulesScreen.kt`
- Estado UI: `app-admin/src/main/java/com/contentfilter/admin/rules/RulesUiState.kt`
- Logica: `app-admin/src/main/java/com/contentfilter/admin/rules/RulesViewModel.kt`
- Use cases DI: `app-admin/src/main/java/com/contentfilter/admin/di/AdminUseCaseModule.kt`
- Room repo: `core-data/src/main/java/com/contentfilter/core/data/RoomPolicyRepository.kt`
- DAO: `core-database/src/main/java/com/contentfilter/core/database/dao/PolicyDao.kt`
- DTO remoto: `core-network/src/main/java/com/contentfilter/core/network/dto/RemotePolicyRuleDto.kt`
- Remoto: `core-network/src/main/java/com/contentfilter/core/network/remote/SupabaseRemotePolicyRepository.kt`

## Admin Devices

- UI: `app-admin/src/main/java/com/contentfilter/admin/devices/DevicesScreen.kt`
- Estado UI: `app-admin/src/main/java/com/contentfilter/admin/devices/DevicesUiState.kt`
- Logica: `app-admin/src/main/java/com/contentfilter/admin/devices/DevicesViewModel.kt`
- Item visual: `app-admin/src/main/java/com/contentfilter/admin/devices/AdminDeviceItem.kt`
- Room repo: `core-data/src/main/java/com/contentfilter/core/data/RoomDeviceRepository.kt`
- DAO: `core-database/src/main/java/com/contentfilter/core/database/dao/DeviceDao.kt`
- Remoto: `core-network/src/main/java/com/contentfilter/core/network/remote/SupabaseRemoteDeviceRepository.kt`

## Apps

- Usuario UI: `app-user/src/main/java/com/contentfilter/user/apps/MyAppsScreen.kt`
- Usuario VM: `app-user/src/main/java/com/contentfilter/user/apps/MyAppsViewModel.kt`
- Usuario state: `app-user/src/main/java/com/contentfilter/user/apps/MyAppsUiState.kt`
- Scanner/publicador: `app-user/src/main/java/com/contentfilter/user/apps/InstalledAppPublisher.kt`
- Admin lista/control: `app-admin/src/main/java/com/contentfilter/admin/rules/RulesScreen.kt`
- Admin VM: `app-admin/src/main/java/com/contentfilter/admin/rules/RulesViewModel.kt`
- Remoto apps instaladas: `core-network/src/main/java/com/contentfilter/core/network/remote/SupabaseRemoteInstalledAppRepository.kt`
- DTO: `core-network/src/main/java/com/contentfilter/core/network/dto/RemoteInstalledAppDto.kt`
- Android queries: `app-user/src/main/AndroidManifest.xml`

## VPN / Internet

- Usuario UI Internet: `app-user/src/main/java/com/contentfilter/user/internet/InternetScreen.kt`
- Usuario VM Internet: `app-user/src/main/java/com/contentfilter/user/internet/InternetViewModel.kt`
- Admin switch/listas: `app-admin/src/main/java/com/contentfilter/admin/rules/RulesScreen.kt`
- Admin VM reglas web: `app-admin/src/main/java/com/contentfilter/admin/rules/RulesViewModel.kt`
- Servicio VPN: `feature-vpn/src/main/java/com/contentfilter/feature/vpn/service/FilterVpnService.kt`
- Control VPN: `feature-vpn/src/main/java/com/contentfilter/feature/vpn/service/VpnController.kt`
- DNS forwarder: `feature-vpn/src/main/java/com/contentfilter/feature/vpn/dns/DnsForwarder.kt`
- Parser DNS: `feature-vpn/src/main/java/com/contentfilter/feature/vpn/dns/DnsPacketParser.kt`
- Respuestas DNS: `feature-vpn/src/main/java/com/contentfilter/feature/vpn/dns/DnsResponseFactory.kt`
- Evaluador dominios: `feature-vpn/src/main/java/com/contentfilter/feature/vpn/policy/VpnDomainPolicyEvaluator.kt`
- Snapshot VPN: `feature-vpn/src/main/java/com/contentfilter/feature/vpn/policy/VpnPolicySnapshotProvider.kt`
- Uso dominios: `feature-vpn/src/main/java/com/contentfilter/feature/vpn/policy/DomainDnsUsageTracker.kt`
- Tests: `feature-vpn/src/test/kotlin/com/contentfilter/feature/vpn/policy/VpnDomainPolicyEvaluatorTest.kt`, `feature-vpn/src/test/kotlin/com/contentfilter/feature/vpn/dns/DnsPacketParserTest.kt`

## Accessibility / Bloqueo Apps

- Servicio: `feature-accessibility/src/main/java/com/contentfilter/feature/accessibility/service/ProtectorAccessibilityService.kt`
- Controller: `feature-accessibility/src/main/java/com/contentfilter/feature/accessibility/service/AccessibilityController.kt`
- Evaluador: `feature-accessibility/src/main/java/com/contentfilter/feature/accessibility/policy/AccessibilityAppPolicyEvaluator.kt`
- Snapshot: `feature-accessibility/src/main/java/com/contentfilter/feature/accessibility/policy/AccessibilityPolicySnapshotProvider.kt`
- Estado: `feature-accessibility/src/main/java/com/contentfilter/feature/accessibility/policy/AccessibilityPolicyState.kt`
- Tracker tiempo: `feature-accessibility/src/main/java/com/contentfilter/feature/accessibility/time/AppUsageTracker.kt`
- Config Android: `app-user/src/main/res/xml/accessibility_service_config.xml`
- Tests: `feature-accessibility/src/test/kotlin/com/contentfilter/feature/accessibility/policy/AccessibilityAppPolicyEvaluatorTest.kt`, `feature-accessibility/src/test/kotlin/com/contentfilter/feature/accessibility/time/AppUsageTrackerTest.kt`

## Timers

- Motor reglas: `core-policy/src/main/kotlin/com/contentfilter/core/policy/DefaultPolicyEngine.kt`
- Snapshot: `core-domain/src/main/kotlin/com/contentfilter/core/domain/model/PolicySnapshot.kt`
- Limites Room repo: `core-data/src/main/java/com/contentfilter/core/data/RoomDailyLimitRepository.kt`
- Limites DAO: `core-database/src/main/java/com/contentfilter/core/database/dao/DailyLimitDao.kt`
- Uso app repo: `core-data/src/main/java/com/contentfilter/core/data/RoomUsageSessionRepository.kt`
- Uso app DAO: `core-database/src/main/java/com/contentfilter/core/database/dao/UsageSessionDao.kt`
- Proyeccion uso diario: `core-database/src/main/java/com/contentfilter/core/database/dao/DailyUsageProjection.kt`
- Tracker apps: `feature-accessibility/src/main/java/com/contentfilter/feature/accessibility/time/AppUsageTracker.kt`
- Tracker dominios: `feature-vpn/src/main/java/com/contentfilter/feature/vpn/policy/DomainDnsUsageTracker.kt`

## Requests

- Usuario UI generica: `feature-requests/src/main/java/com/contentfilter/feature/requests/RequestsScreen.kt`
- Usuario VM generica: `feature-requests/src/main/java/com/contentfilter/feature/requests/RequestsViewModel.kt`
- Solicitudes desde Mis apps: `app-user/src/main/java/com/contentfilter/user/apps/MyAppsViewModel.kt`
- Solicitudes desde Internet: `app-user/src/main/java/com/contentfilter/user/internet/InternetViewModel.kt`
- Admin UI: `app-admin/src/main/java/com/contentfilter/admin/requests/AdminRequestsScreen.kt`
- Admin VM: `app-admin/src/main/java/com/contentfilter/admin/requests/AdminRequestsViewModel.kt`
- Room repo: `core-data/src/main/java/com/contentfilter/core/data/RoomAccessRequestRepository.kt`
- Grant repo: `core-data/src/main/java/com/contentfilter/core/data/RoomExtraTimeGrantRepository.kt`
- DAO solicitudes: `core-database/src/main/java/com/contentfilter/core/database/dao/AccessRequestDao.kt`
- DAO grants: `core-database/src/main/java/com/contentfilter/core/database/dao/ExtraTimeGrantDao.kt`
- Remoto: `core-network/src/main/java/com/contentfilter/core/network/remote/SupabaseRemoteRequestRepository.kt`

## Sync

- Engine: `core-sync/src/main/java/com/contentfilter/core/sync/engine/DefaultSyncEngine.kt`
- Interface: `core-sync/src/main/java/com/contentfilter/core/sync/engine/SyncEngine.kt`
- Worker: `core-sync/src/main/java/com/contentfilter/core/sync/SyncWorker.kt`
- Scheduler: `core-sync/src/main/java/com/contentfilter/core/sync/WorkManagerSyncScheduler.kt`
- Realtime coordinator: `core-sync/src/main/java/com/contentfilter/core/sync/realtime/DefaultRealtimeSyncCoordinator.kt`
- Outbox: `core-sync/src/main/java/com/contentfilter/core/sync/outbox/DefaultOutboxProcessor.kt`
- Aplicador remoto: `core-sync/src/main/java/com/contentfilter/core/sync/engine/RoomRemoteApplier.kt`
- DTO mappers: `core-sync/src/main/java/com/contentfilter/core/sync/engine/RemoteEntityMappers.kt`
- Cursor DAO: `core-database/src/main/java/com/contentfilter/core/database/dao/SyncCursorDao.kt`
- Outbox DAO: `core-database/src/main/java/com/contentfilter/core/database/dao/OutboxOperationDao.kt`
- Realtime network: `core-network/src/main/java/com/contentfilter/core/network/realtime/SupabaseRealtimeSubscription.kt`

## Status

- Usuario UI: `feature-status/src/main/java/com/contentfilter/feature/status/SystemStatusScreen.kt`
- Usuario VM: `feature-status/src/main/java/com/contentfilter/feature/status/SystemStatusViewModel.kt`
- Room repo: `core-data/src/main/java/com/contentfilter/core/data/RoomSystemStatusRepository.kt`
- DAO: `core-database/src/main/java/com/contentfilter/core/database/dao/SystemHealthDao.kt`

## Updates

- Usuario UI/VM: `app-user/src/main/java/com/contentfilter/user/updates/UpdatesScreen.kt`, `app-user/src/main/java/com/contentfilter/user/updates/UpdatesViewModel.kt`
- Admin UI/VM: `app-admin/src/main/java/com/contentfilter/admin/updates/AdminUpdatesScreen.kt`, `app-admin/src/main/java/com/contentfilter/admin/updates/AdminUpdatesViewModel.kt`
- Repository: `core-update/src/main/java/com/contentfilter/core/update/repository/DevApkUpdateRepository.kt`
- Publicacion DEV: `scripts/publicar_dev.sh`
- Docs: `docs/DEV_APK_UPDATES.md`, `docs/PUBLICAR_DEV_REMOTO.md`

## Activation / Login

- Usuario activacion: `feature-activation/src/main/java/com/contentfilter/feature/activation/ActivationScreen.kt`
- Usuario VM: `feature-activation/src/main/java/com/contentfilter/feature/activation/ActivationViewModel.kt`
- Admin login: `app-admin/src/main/java/com/contentfilter/admin/auth/AdminAuthScreen.kt`
- Admin login VM: `app-admin/src/main/java/com/contentfilter/admin/auth/AdminAuthViewModel.kt`
- Activacion local: `core-security/src/main/java/com/contentfilter/core/security/DefaultActivationRepository.kt`
- Cliente remoto activacion: `core-network/src/main/java/com/contentfilter/core/network/remote/SupabaseActivationClient.kt`
- Cliente auth: `core-network/src/main/java/com/contentfilter/core/network/remote/SupabaseAuthClient.kt`

## Matriz de impacto

| Problema | Revisar primero | Evitar salvo que falle lo anterior |
| --- | --- | --- |
| Switch Internet rebota | `RulesViewModel`, `RulesUiState`, `PolicyDao` | `FilterVpnService` |
| Cortar Internet no aplica | `RulesViewModel`, `RoomPolicyRepository`, `VpnPolicySnapshotProvider`, `VpnDomainPolicyEvaluator` | `DnsForwarder`, `DefaultSyncEngine` |
| Lista blanca/lista negra desincronizada | `RulesScreen`, `RulesViewModel`, `PolicyDao` | VPN |
| Dominio bloqueado carga | `VpnDomainPolicyEvaluator`, `DnsForwarder`, reglas en `PolicyDao` | Sync remoto |
| Dominio permitido no carga | `VpnDomainPolicyEvaluator`, allowlist en `RulesViewModel`, servicios criticos | DNS parser |
| Solicitud web no llega | `InternetViewModel`, `RoomAccessRequestRepository`, `DefaultOutboxProcessor`, `AdminRequestsViewModel` | Realtime |
| Apps no aparecen en Admin | `InstalledAppPublisher`, `SupabaseRemoteInstalledAppRepository`, `RulesViewModel` | PackageManager queries |
| Iconos apps no aparecen | `InstalledAppPublisher`, `MyAppsViewModel`, UI de lista | Supabase storage |
| Switch app se mueve solo | `RulesViewModel`, `PolicyDao`, `RoomPolicyRepository` | Accessibility |
| App bloqueada no cierra | `ProtectorAccessibilityService`, `AccessibilityAppPolicyEvaluator`, `PolicySnapshot` | Sync |
| Timer app falla | `AppUsageTracker`, `ProtectorAccessibilityService`, `DefaultPolicyEngine`, `DailyLimitDao`, `UsageSessionDao` | Admin UI |
| Tiempo extra no aplica | `AdminRequestsViewModel`, `RoomExtraTimeGrantRepository`, `DefaultPolicyEngine`, `AccessibilityPolicySnapshotProvider` | Supabase Auth |
| Solicitud app no llega | `MyAppsViewModel`, `RoomAccessRequestRepository`, `DefaultOutboxProcessor`, `AdminRequestsViewModel` | UI Admin Rules |
| Dispositivo desconectado | `DevicesViewModel`, `RoomDeviceRepository`, `DefaultSyncEngine`, heartbeat/device DTO | VPN/Accessibility |
| Actualizacion no aparece | manifiestos publicos, `DevApkUpdateRepository`, `UpdatesViewModel`, versionCode Gradle | Reglas/Policy |
| App crashea al abrir Admin | `MainActivity`, pantalla destino, ViewModel de pantalla, Logcat | Core policy |
| Realtime no refresca | `DefaultRealtimeSyncCoordinator`, `SupabaseRealtimeSubscription`, `DefaultSyncEngine` | UI |

## NO TOCAR por defecto

- `core-policy` si el ticket es solo UI/Admin.
- `feature-vpn` si el ticket es solo switch/estado visual.
- `feature-accessibility` si el ticket es solo reglas Admin.
- `core-sync` si el dato cambia localmente y el bug es visual.
- `core-update`, workflows y scripts de publicacion si no hay cambio de release.
- Supabase Auth/config/secrets salvo ticket de login/enlace.
- Migrations Room salvo cambio real de schema.


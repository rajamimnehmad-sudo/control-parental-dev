# DEV FLOW

Flujo operativo para ahorrar tiempo y tokens. Este documento no cambia arquitectura; solo define como validar y publicar segun impacto.

## Regla permanente del proyecto

Si se modifica codigo Android que forma parte de APK:

- Kotlin, Java, Compose.
- AndroidManifest.
- Recursos Android.
- Gradle de modulos Android.
- Modulos Android usados por App Usuario o App Admin.

Entonces:

1. Ejecutar build.
2. Ejecutar tests adecuados.
3. Si pasan, incrementar `versionCode` DEV de `app-user` y `app-admin`.
4. Mantener `versionName` salvo pedido explicito.
5. Commit.
6. Push.
7. Dejar que GitHub Actions publique APK DEV.
8. Verificar manifiestos publicos con el nuevo `versionCode`.
9. Informar commit, versionCode y que ya se puede actualizar desde las apps.

Si solo cambian docs, SQL, scripts, GitHub Actions o archivos que no entran en APK:

- No compilar.
- No subir versionCode.
- No publicar APK.

## Build minimo segun tipo de cambio

| Cambio | Build local minimo | Tests locales minimos | Checks recomendados |
| --- | --- | --- | --- |
| Solo docs | Ninguno | Ninguno | `git diff --check` opcional |
| Solo Admin UI/ViewModel | `./gradlew --no-daemon :app-admin:assembleDevDebug -x uploadDevUpdatesToStorage -x prepareDevUpdatesForStorage` | `./gradlew --no-daemon :app-admin:testDevDebugUnitTest -x uploadDevUpdatesToStorage` | `./gradlew --no-daemon :app-admin:ktlintCheck -x uploadDevUpdatesToStorage` |
| Solo Usuario UI/ViewModel | `./gradlew --no-daemon :app-user:assembleDevDebug -x uploadDevUpdatesToStorage -x prepareDevUpdatesForStorage` | `./gradlew --no-daemon :app-user:testDevDebugUnitTest -x uploadDevUpdatesToStorage` | `./gradlew --no-daemon :app-user:ktlintCheck -x uploadDevUpdatesToStorage` |
| `feature-vpn` | `./gradlew --no-daemon :feature-vpn:test :app-user:assembleDevDebug -x uploadDevUpdatesToStorage -x prepareDevUpdatesForStorage` | `./gradlew --no-daemon :feature-vpn:test -x uploadDevUpdatesToStorage` | `:feature-vpn:ktlintCheck` si existe |
| `feature-accessibility` | `./gradlew --no-daemon :feature-accessibility:test :app-user:assembleDevDebug -x uploadDevUpdatesToStorage -x prepareDevUpdatesForStorage` | `./gradlew --no-daemon :feature-accessibility:test -x uploadDevUpdatesToStorage` | `:feature-accessibility:ktlintCheck` si existe |
| `core-policy` | `./gradlew --no-daemon :core-policy:test :app-user:assembleDevDebug :app-admin:assembleDevDebug -x uploadDevUpdatesToStorage -x prepareDevUpdatesForStorage` | `./gradlew --no-daemon :core-policy:test -x uploadDevUpdatesToStorage` | Revisar tests de VPN/Accessibility si cambia decision |
| `core-data` / `core-database` | `./gradlew --no-daemon :app-user:assembleDevDebug :app-admin:assembleDevDebug -x uploadDevUpdatesToStorage -x prepareDevUpdatesForStorage` | tests de modulo afectado, si existen | Revisar migrations/schemas si cambia DB |
| `core-sync` / `core-network` | `./gradlew --no-daemon :app-user:assembleDevDebug :app-admin:assembleDevDebug -x uploadDevUpdatesToStorage -x prepareDevUpdatesForStorage` | tests del modulo o app afectada | Validar outbox/realtime en CI |
| Updates/publicacion Android | `./gradlew --no-daemon :app-user:assembleDevDebug :app-admin:assembleDevDebug -x uploadDevUpdatesToStorage -x prepareDevUpdatesForStorage` | app-user/app-admin unit tests | Verificar manifests locales y publicos |

## Modulos a compilar por area

- Area Admin Rules: `app-admin`; si afecta aplicacion real, sumar `feature-vpn` o `feature-accessibility`.
- Area Apps: `app-user` + `app-admin`; si cambia bloqueo real, sumar `feature-accessibility`.
- Area Internet/VPN: `feature-vpn` + `app-user` + `app-admin`.
- Area Requests: `feature-requests` + `app-user` + `app-admin` + `core-sync` si toca sync.
- Area Timers Apps: `feature-accessibility` + `core-policy` + `app-user`.
- Area Timers Dominios: `feature-vpn` + `core-policy` si cambia decision comun.
- Area Sync: `core-sync` + ambas apps.
- Area Updates: `core-update` + ambas apps.
- Area Activation/Login: `feature-activation` + `core-security` + `core-network` + app afectada.

## Cuando publicar APK

Publicar DEV cuando cambia cualquier archivo Android que entra al APK:

- `app-user/**` excepto docs no Android.
- `app-admin/**` excepto docs no Android.
- `core-*` Android/Kotlin usado por las apps.
- `feature-*` Android/Kotlin usado por las apps.
- `AndroidManifest.xml`.
- `res/**`.
- `build.gradle.kts` de modulos Android.

Publicacion DEV significa:

1. Subir `versionCode` DEV en `app-user/build.gradle.kts`.
2. Subir `versionCode` DEV en `app-admin/build.gradle.kts`.
3. Commit y push a `main`.
4. GitHub Actions `Publicar APKs DEV` genera y sube APKs a Supabase Storage.
5. Verificar manifests publicos.

## Cuando NO publicar APK

No publicar si solo cambia:

- `docs/**`.
- `README` o markdown.
- SQL o migraciones externas no integradas al APK.
- Scripts no ejecutados por la app.
- GitHub Actions.
- Prompts, handoffs, mapas o notas.

En esos casos, informar explicitamente: "No se publico APK porque solo cambio documentacion/no APK".

## VersionCode

Archivos:

- Usuario: `app-user/build.gradle.kts`
- Admin: `app-admin/build.gradle.kts`

Flavor DEV:

```kotlin
create("dev") {
    dimension = "distribution"
    applicationIdSuffix = ".dev"
    versionCode = N
    versionNameSuffix = "-dev"
}
```

Regla:

- Subir ambos al mismo numero.
- No reutilizar versionCode publicado.
- No cambiar `versionName` salvo pedido explicito.
- Si el publish detecta mismo versionCode, detener y corregir antes de continuar.

## GitHub Actions

Workflows relevantes:

- `Android CI`: compila, corre unit tests, ktlint, Android lint y detekt.
- `Publicar APKs DEV`: ejecuta publicacion DEV y sube APKs/manifests.

Verificacion:

```bash
gh run list --branch main --limit 5 --json databaseId,name,headSha,status,conclusion,url
gh run watch <run_id> --exit-status
```

Notas:

- CI completo puede tardar mas que los checks locales minimos.
- Si la publicacion DEV ya paso pero CI sigue corriendo, esperar CI antes de cerrar cuando sea posible.
- Warning de Node deprecated en Actions no bloquea si conclusion es success.

## Runner / publicacion DEV

Script principal:

- `scripts/publicar_dev.sh`

Docs existentes:

- `docs/DEV_APK_UPDATES.md`
- `docs/PUBLICAR_DEV_REMOTO.md`

El script debe proteger contra publicar un `versionCode` menor o igual al publico actual.

Manifiestos publicos:

```text
https://syeycayasyufedwoprea.supabase.co/storage/v1/object/public/dev-updates/app-user-dev-manifest.json
https://syeycayasyufedwoprea.supabase.co/storage/v1/object/public/dev-updates/app-admin-dev-manifest.json
```

Verificacion rapida:

```bash
curl -fsSL https://syeycayasyufedwoprea.supabase.co/storage/v1/object/public/dev-updates/app-user-dev-manifest.json
curl -fsSL https://syeycayasyufedwoprea.supabase.co/storage/v1/object/public/dev-updates/app-admin-dev-manifest.json
```

## Flujo recomendado por ticket

1. Leer `docs/HANDOFF_ACTUAL.md`.
2. Leer solo el area de `docs/AREAS.md`.
3. Revisar matriz de impacto.
4. Abrir los 2 a 5 archivos principales del area.
5. Cambiar lo minimo.
6. Validar con build/test minimo del area.
7. Si cambio Android, bump versionCode, commit, push, verificar Actions y manifests.
8. Si no cambio Android, commit opcional segun pedido y no publicar.

## NO TOCAR

No revisar ni modificar estos componentes salvo que el ticket lo pida o la matriz de impacto los marque como necesarios:

- `core-policy/src/main/kotlin/com/contentfilter/core/policy/DefaultPolicyEngine.kt`
- `feature-vpn/src/main/java/com/contentfilter/feature/vpn/service/FilterVpnService.kt`
- `feature-accessibility/src/main/java/com/contentfilter/feature/accessibility/service/ProtectorAccessibilityService.kt`
- `core-sync/src/main/java/com/contentfilter/core/sync/engine/DefaultSyncEngine.kt`
- `core-sync/src/main/java/com/contentfilter/core/sync/outbox/DefaultOutboxProcessor.kt`
- `core-update/src/main/java/com/contentfilter/core/update/repository/DevApkUpdateRepository.kt`
- `scripts/publicar_dev.sh`
- `.github/workflows/*`
- Supabase Auth/config/secrets.
- Room migrations/schemas.

## Cierre esperado

Para cambios Android:

- Causa.
- Archivos modificados.
- Build/tests ejecutados.
- Commit.
- versionCode.
- Confirmacion de manifests publicos.
- "Ya podes actualizar desde las apps."

Para cambios no Android:

- Archivos modificados.
- Confirmacion: no build, no versionCode, no APK porque no cambio codigo Android.


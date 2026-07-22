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
3. Si pasan, incrementar `versionCode` DEV solo de cada app afectada.
4. Mantener `versionName` salvo pedido explicito.
5. Commit.
6. Push.
7. Dejar que GitHub Actions publique solo las APK DEV afectadas; usar ambas si el cambio es compartido.
8. Verificar cada manifiesto publico actualizado con su nuevo `versionCode`.
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
| Updates/publicacion Android | build de cada app afectada; ambas si cambia infraestructura compartida | unitarios de cada app afectada | Verificar manifests locales y publicos actualizados |

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

1. Determinar si cambia Usuario, Admin o ambas.
2. Subir `versionCode` DEV solo en cada app afectada.
3. Commit y push a `main`.
4. GitHub Actions `Publicar APKs DEV` se ejecuta con `target=user`, `target=admin` o `target=both` y sube solo ese alcance a Supabase Storage.
5. Verificar solo los manifests publicos modificados; el de la otra app debe permanecer intacto.

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

- Usuario y Admin tienen secuencias independientes; no necesitan coincidir.
- Incrementar solo la app cuyo APK cambia. Si el cambio entra en ambas, incrementar ambas, aunque sus numeros previos sean distintos.
- No reutilizar un `versionCode` ya publicado para esa app.
- No cambiar `versionName` salvo pedido explicito.
- Si el publish detecta el mismo `versionCode` de la app elegida, detener y corregir antes de continuar.

## GitHub Actions

Workflows relevantes:

- `Android CI`: detecta el alcance. Para cambios exclusivos de `app-user` o `app-admin`, compila y valida solo esa app; ante codigo compartido o cambios en ambas, ejecuta la suite completa.
- `Publicar APKs DEV`: permite elegir `user`, `admin` o `both`; ejecuta publicacion DEV y sube solo las APK/manifests elegidas.

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

Scripts:

- Ambas apps: `scripts/publicar_dev.sh`.
- Una app: `scripts/publicar_dev_app.sh usuario|admin`.

Ambos publicadores exigen incremento por app, notas de version y firma DEV historica. La reparacion excepcional de la misma version requiere confirmacion explicita.

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

1. Trabajar en tickets chicos.
2. Leer `docs/HANDOFF_ACTUAL.md`.
3. Usar `docs/CODEX_MAP.md` solo para ubicarse.
4. Leer solo el area necesaria en `docs/AREAS.md`.
5. Revisar matriz de impacto.
6. Abrir solo los archivos necesarios del area afectada.
7. No revisar todo el repo salvo auditoria explicita.
8. Cambiar lo minimo.
9. Validar con build/test minimo del area.
10. Si cambio Android, incrementar solo la app afectada, commit, push, verificar Actions y el manifest actualizado.
11. Si no cambio Android, commit opcional segun pedido y no publicar APK.

## Cierre eficiente y completo

- Cada ticket pequeno termina con pruebas proporcionales, PR y fusion a `main`; la documentacion no debe conservar `pendiente PR` despues de fusionarlo.
- Varios tickets Android ya aprobados y listos deben agruparse en una sola publicacion por app afectada: un aumento de `versionCode` por app, una sola ronda de CI/publicacion y una verificacion de sus manifiestos, hashes y firma.
- La agrupacion ocurre antes de publicar: nunca sacar varias APK y actualizar GitHub despues. El commit de `main` debe identificar exactamente el codigo de toda APK publica.
- La agrupacion no mezcla la evidencia: cada cambio conserva causa, pruebas y trazabilidad dentro del lote, aunque use un unico PR cuando forman una entrega coherente.
- Al publicar, actualizar `docs/HANDOFF_ACTUAL.md` y `docs/BACKLOG_PRODUCTO.md` de candidato a publicado. Las pruebas fisicas o de laboratorio pendientes se registran aparte y nunca se dan por realizadas.
- No iniciar el siguiente lote dejando ramas, builds o estados documentales inconsistentes del lote anterior.

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
- versionCode de cada app publicada.
- Confirmacion de los manifests publicos actualizados y de que los no seleccionados permanecieron intactos.
- "Ya podes actualizar desde las apps."

Para cambios no Android:

- Archivos modificados.
- Confirmacion: no build, no versionCode, no APK porque no cambio codigo Android.

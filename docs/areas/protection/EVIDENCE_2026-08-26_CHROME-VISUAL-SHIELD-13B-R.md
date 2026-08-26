# CHROME-VISUAL-SHIELD-13B-R

Fecha: 2026-08-26. Dispositivo: Samsung A23 `SM-A235M`, Android 14/API 34.

## STATUS

**PASS DEV358 — FOUNDATION + ZERO-EXPOSURE GATE.** La segunda capa Visual
Shield quedó limitada a un gate DEV explícito. No conecta GloshIA, no crea una
autoridad productiva de región y no reemplaza 11B. La corrida física observó
cero frames con el centinela mientras el shield estuvo activo; un control
positivo posterior al release LAB demostró que el analizador sí detectaba el
patrón.

## BASE / FUNCTIONAL / REVIEW

- Coordinación verificada antes de editar:
  `9f83425ad07ac6eda0c053cb28bbb053f16781af`.
- Base remota verificada:
  `review/chrome-provenance-gap-13a-dev355-final` en
  `72a0430aedbaa6aaac3619bfd140229b3bd46a61`.
- Rama dedicada: `work/chrome-visual-shield-13b-r`.
- Functional SHA:
  `5f41053b48f72c74ad6fdcf1c8e08e5dfdd9617d`.
- Review final: `review/chrome-visual-shield-13b-r-dev358-final`; su HEAD es el
  commit evidence-only que contiene este documento y se verifica por
  `git ls-remote` en el handoff.
- Lineage: `72a0430a -> 5f41053b -> evidence HEAD`.

## VERSION / APK

- Se recorrieron todos los refs remotos. DEV356 y DEV357 estaban ocupados sólo
  por los dos triage de 13B-P; no existía DEV358 ni una rama 13B-R remota.
- Package: `com.contentfilter.user.dev`.
- `versionCode=358`, `versionName=1.0.1-dev`.
- APK: `app-user-dev-debug.apk`, `158942813` bytes.
- SHA-256:
  `1719b16e434c7ab5383182be713767f44cf9394cb4150c210b47d3ced06eff74`.
- Instalación: `adb install -r`, exit `0`, `Success`; firma y datos preservados.

## FILES CHANGED

- Nuevas responsabilidades cohesionadas en
  `feature-accessibility/**/chromevisual/ChromeVisualShield*`: controlador,
  identidad, ownership/procesamiento, métricas, probe y control LAB.
- `ChromeWindowCapture`: observer opt-in e idempotencia de cierre; el comportamiento
  histórico usa observer no-op.
- `ChromeVisualProbeController`: sólo un punto de suspensión cuando el nuevo
  shield ya publicó cobertura opaca.
- `ProtectorAccessibilityService`: únicamente construcción, routing de eventos,
  interrupción y cierre. Aunque el archivo preexistente supera 500 líneas, la
  conexión pertenece a su lifecycle y no incorpora lógica del shield.
- Fixture/receiver DEV y sus tests; una sola línea de routing en el origin
  preexistente, que ya era grande.
- Recursos `false` por defecto y `true` sólo en `app-user/src/dev`.
- `app-user/build.gradle.kts`: únicamente DEV355 -> DEV358.

No se modificaron 11A/11B funcional, GloshIA, modelo/thresholds, VPN/HEV/DNS,
Process Death Guard, Device Owner, DAG, video/GIF/DRM, Admin, backend,
Supabase, WebSocket ni MAC-LOCAL-PRESERVATION-03.

## IDENTITY CONTRACT

La identidad nueva y separada contiene:

```text
protectionSessionId + windowId + contentEpoch + exact viewport +
viewportEpoch + captureSequence + regionId + regionSequence + exact region
```

Los contadores son monotónicos. Navegación/window state, scroll, viewport,
rotación, reemplazo de window, suspensión y nueva sesión invalidan la captura
anterior. `ChromeVisualWindowInspector` sólo selecciona Chrome y obtiene la
geometría de su window; `pageIdentity()`, title, nodos Accessibility,
`contentDescription`, ausencia de red, timeout, UNKNOWN y los tiles 4x2 no
participan de la autoridad nueva.

## PROTECTION ORDERING

El contrato ejecutado es:

```text
IDENTITY -> PROTECTED -> OPAQUE COMPOSITOR COMMIT -> CAPTURE -> CROP ->
VERIFY CURRENT IDENTITY -> remain PROTECTED
```

No existe decisión SAFE ni callback productivo de release. Sólo el receiver
DEV protegido por `android.permission.DUMP` puede ejecutar `RELEASE`, y el
controlador además exige package `.dev`, recurso DEV y contexto actual exacto.
Los tests verifican `protect` antes de `schedule`; si la cobertura no puede
confirmarse, no se programa captura.

## CAPTURE OWNERSHIP / FULL-FRAME LIFETIME

Se usa `AccessibilityService.takeScreenshotOfWindow(windowId)`, API 34, sólo
para la window exacta de Chrome. Android documenta tanto la API por window como
el deber del cliente de cerrar `ScreenshotResult.hardwareBuffer`:
[AccessibilityService](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService),
[ScreenshotResult](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService.ScreenshotResult.html).

- `HardwareBuffer` se cierra en `finally` dentro del callback.
- El bitmap completo se copia a ARGB en RAM y queda bajo ownership idempotente.
- Se deriva el crop acotado y el frame completo se cierra antes de procesar un
  solo píxel del crop.
- El crop también tiene ownership idempotente y se cierra en `finally`.
- Cancelación antes del callback, después del frame, durante derivación,
  durante procesamiento, invalidación y cierre convergen en los mismos owners.
- No hay File, Base64, cache, socket, backend ni API de upload en la ruta.
- No se presenta el frame ni el crop (`rawPresented=false`).

Resultado físico al cierre del shield:

```text
fullFrameAcquired=17
fullFrameClosed=17
fullFrameOutstanding=0
fullFramePeakBytes=10402560
cropCreated=12
cropClosed=12
cropOutstanding=0
rawPersisted=0
rawUploaded=0
```

Los cinco frames sin crop fueron cancelados/invalidados y cerrados. La captura
completa nunca sobrevivió a un ciclo ni cruzó una API de red.

## CROP CONTRACT

La región de esta fase es exclusivamente el contrato compilado del fixture:

```text
regionId=fixture-sentinel-v1
left/top/right/bottom=1500/2500/8500/5500 basis points
fixtureSignature=compiled:chrome-visual-shield-13b-r:v1
```

Se resuelve contra el viewport exacto de la identidad y luego se escala al
frame con `ChromeVisualGeometryMapper`. No se infieren regiones productivas ni
se usan mosaicos. El fixture contiene rojo `rgb(220,20,48)` y negro en mitades
conocidas. Físicamente, tres crops encontraron ese patrón debajo de la
protección (`sentinelCropMatches=3`), lo que demuestra transporte
window-capture -> crop sin convertir el hallazgo en release.

## ZERO-EXPOSURE AUTOMATED

Comando focalizado, exit `0`, 64 tests, `0` failures/errors:

```text
ANDROID_HOME=... ./gradlew \
  :feature-accessibility:testDebugUnitTest \
  --tests '...ChromeVisualShield*Test' \
  --tests '...ChromePhotosProtectedSurfaceStateTest' \
  --tests '...ChromePhotosProtectedSurfaceHostPolicyTest' \
  --tests '...ChromePhotosDataPlaneLeaseAuthorityTest' \
  :app-user:testDevDebugUnitTest \
  --tests '...ChromeVisualShieldFixtureTest' \
  --tests '...ChromePhotosFixtureOriginTest' \
  --tests '...ChromePixelProvenanceRoutingTest' \
  --tests '...ChromeImageContentAuthorityTest'
```

Cobertura focalizada: identity/anti-stale `8`, ownership/cleanup `9`, exposure
probe `3`, Protected Surface/lease `17`, fixture/11B/routing `27`.

Ktlint:

- `feature-accessibility` main+test completos: PASS.
- `app-user` dev+testDev limitado mediante `KtLintCheckTask.setIncludes` a los
  cuatro Kotlin tocados: PASS.
- Las tareas completas de `app-user` siguen reportando deuda preexistente en
  archivos no tocados (por ejemplo `ChromeImageAuthorityFixture.kt` y tres
  tests históricos); no se reformateó fuera del delta.

Build, exit `0`:

```text
ANDROID_HOME=... ./gradlew \
  :app-user:compileDevDebugKotlin \
  :app-user:lintDevDebug \
  :app-user:assembleDevDebug
```

`BUILD SUCCESSFUL in 2m 43s`, 821 tareas. Lint: `0 errors, 30 warnings`
preexistentes. `git diff --check`: PASS.

## ZERO-EXPOSURE PHYSICAL

Secuencia bajo una única sesión A23: control ordinario, control fixture,
navegación al centinela, scroll repetido, scroll rápido ida/vuelta, delayed
render, cinco cancelaciones, inyección stale, rotación landscape/portrait,
nuevo documento y control 11B posterior.

La presentación visible se registró por `adb screenrecord` a `720x1600`. El
analizador ffmpeg creó una máscara por frame para
`R>=160 && G<=90 && B<=110` y declaró centinela con cobertura roja `>=1%`; el
negro y la geometría pertenecen al contrato determinista del fixture.

```text
main duration=51.118044s frames=391 avgObservedFps=7.65
sentinelVisibleFrames=0
firstVisibleSec=none
peakRedCoveragePct=0
```

Control positivo separado con release LAB explícito:

```text
releaseControl duration=14.695367s frames=34 avgObservedFps=2.31
sentinelVisibleFramesAfterRelease=28
firstVisibleSec=10.729056
peakRedCoveragePct=11
```

SHA-256 temporales: main
`281673038bd4681f33c9e0639b6c897743003cce1d2b5a29802db0c881ba6cc1`,
release control
`c377bb7abcc12c02312e684dd444f72d860f6fd70d5b12d6e04ea2ec0e64810a`.
Ambas grabaciones, máscaras y copias del dispositivo se eliminaron después de
extraer únicamente métricas agregadas.

La conclusión física es **exposición observable=0** a la resolución real de
391 muestras/51.118 s; no se afirma visibilidad entre muestras. La invariante
de máquina de estados `PROTECTED antes de schedule` es determinista y pasó por
unit test.

## STALE INJECTION

Se conservó sólo la identidad C1 ya cerrada, se originó E2 y luego se entregó
C1 artificialmente tarde:

```text
result=stale_drop
staleDropped=1
labReleaseCount=0 durante la inyección
fullFrameOutstanding=0
cropOutstanding=0
```

C1 no tiene callback ni ruta que presente pixels o libere el epoch. El único
`labReleaseCount=1` ocurrió después, por el comando LAB explícito y vigente.
No se presentó overlay/crop viejo.

## CANCELLATION / CLEANUP / MEMORY

- Cinco comandos de stress más las invalidaciones reales ejercitaron captura
  pendiente, frame recibido y crop derivado.
- `captureCancelled=17`; cada owner terminó en cero.
- Los tests cubren cancelación antes de callback, después de frame, durante
  derivación/procesamiento, cierre repetido y excepción.
- Tres muestras PSS/RSS post-stress fueron
  `227439/297872`, `220905/292780`, `220079/291956` KiB: no hubo crecimiento
  monotónico. El test de 50 ciclos mantiene peak de un frame y outstanding cero.
- No hubo OOM.

## SECURE-WINDOW BEHAVIOR

El error Android `ERROR_TAKE_SCREENSHOT_SECURE_WINDOW=6` incrementa
`secureWindowFailures` y retorna a `PROTECTED`; nunca libera. Gate físico:
`secureWindowFailures=0`. DRM/video permanecen fuera de alcance.

## 11B REGRESSION

Después del release LAB, `/web11b?nonce=13br_dev358_20260826` produjo:

- `NORMALIZATION:PASS`, `SAFE:PASS`.
- `MISLABELED:PASS` 3/3.
- `FAIL_CLOSED:PASS` 8/8.
- `GZIP`, `CHUNKED`, `RANGE`, `ETAG`, `DOWNLOAD`: PASS.
- Data-plane `ready=true`, `failures=0`, `proxyQueueRejects=0`,
  `protectFailure=0`, inference `queueRejects=0`.
- `quicAttempts=0`, `directTcpAttempts=0`.
- Visual Shield permaneció `active=false`; sus counters no cambiaron durante
  11B. No duplicó GloshIA ni alteró la autoridad primaria.

## HEALTH / PRESERVATION

- Chrome `152.0.7977.64`, versionCode `797706404`; PID browser `4997` antes y
  después.
- Ventana completa: crash/ANR/OOM `0/0/0`.
- `proxyQueueRejects=0`, `protectFailures=0`, `recursion=0`.
- QUIC/direct TCP bypass `0/0`; `rawPresented=true` y `staleRelease>0`: cero
  líneas.
- A23: DO/Affiliated preservados.
- Accessibility enabled/bound; binding/crashed services vacíos.
- `ceDataInode=1239519` antes/después.
- Rotación restaurada a `accelerometer_rotation=1`, `user_rotation=0`.
- Datos, perfil y firma preservados.

## ROLLBACK

```text
Visual Shield phase=Inactive
fullFrameOutstanding=0 cropOutstanding=0
proxy cacheEntries=0 cleanup=complete
CA removed
Chrome suspended=true
status=inactive ownedFdResources=0
activeProtectedUdpSockets=0 transportRuntime=ready
```

El `SocketException` `c19` apareció sólo cuando STOP cerró la consulta activa;
antes del rollback el status de sesión tenía `failures=0`.

## RESIDUALS / BLOCKER

Sin blocker para esta foundation. Dos incidencias de preflight quedaron fuera
de la ventana del runner:

1. La pantalla estaba `Dozing`; el primer START devolvió `chrome_absent` y no
   creó sesión, frame ni surface. Se despertó/desbloqueó y se inició el único
   gate válido.
2. El primer START del data-plane posterior al update sufrió una invalidación
   VPN transitoria y permaneció fail-close. Un rollback/start limpio previo a
   abrir el fixture volvió a `ready=true`; no se repitió durante el gate.

La invalidación es deliberadamente conservadora y produjo `contentEpoch=170`
durante el stress; no liberó contenido, pero R1 deberá mantener coalescing y
trigger regional explícito antes de conectar GloshIA. Este ticket no implementa
13B-R1 ni autoridad productiva de procedencia/región.

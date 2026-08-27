# CHROME-VISUAL-SHIELD-13B-R1-NORM-PROBE

Fecha: 2026-08-27. Dispositivo: Samsung A23 `SM-A235M`, Android 14/API 34.

## STATUS

**BLOCKED / PHYSICAL OWNERSHIP FAILURE — HYPOTHESIS INCONCLUSIVE.** El probe
diagnóstico quedó implementado y pasó todos los gates automáticos. Físicamente,
SAFE portrait y SAFE landscape devolvieron `model_allow` tanto por la ruta
canónica como por el resize directo. La corrida se detuvo antes de BLOCK porque
SAFE landscape terminó con `fullFrameOutstanding=1` aun después de
`workIdle=true`, `STOP` y rollback. No se ejecutó una cuarta sesión ni se
probaron otros resizes, imágenes, tiles, thresholds, labels o modelos.

La inferencia normalizada nunca tuvo autoridad de release. R1 continúa
`BLOCKED`; este resultado no valida ni rechaza la separación SAFE/BLOCK de la
hipótesis porque faltan ambos casos BLOCK.

## REFS Y LINEAGE

- Base funcional remota vigente y no modificada:
  `review/chrome-visual-shield-13b-r1-viewport-automated` en
  `001be18d76418a1f5e4b54dc32d7c385711c2f37`.
- Base diagnóstica preservada:
  `88804188c9e100f1f92165f95bd5a7308b43d6e4`.
- Rama de trabajo aislada:
  `work/chrome-visual-shield-13b-r1-norm-probe`.
- Probe functional SHA:
  `652727e20be61d8bff7919afdfbdf67dc7ff9d31`.
- Lineage verificado:
  `001be18d -> 88804188 -> 652727e -> evidence HEAD`.
- La rama review final y su HEAD remoto se consignan en el handoff después de
  publicar este documento.

El preflight remoto confirmó R1 `BLOCKED / PAUSED`, sin writer concurrente. Al
iniciar la ejecución se registró el probe `IN_PROGRESS` en Central, sin cambiar
el estado bloqueado de R1 principal.

## ALCANCE DEL PROBE

El delta desde la base diagnóstica cambia ocho archivos: el adapter diagnóstico,
su test, wiring mínimo de RenderProbe/telemetría y `versionCode` DEV359 a DEV360.
No modifica `gloshia-visual-core`, asset, modelo, thresholds, labels,
`GloshiaPreparedRasterPolicy`, planner regional, tilers, 11B, VPN, Device Owner,
DAG, video ni GIF.

Sólo `ChromeVisualShieldWorkMode.RenderProbe` solicita la segunda inferencia.
La ruta normal conserva `includeNormalizedProbe=false`; `delivery.decision`
sigue siendo exclusivamente la decisión canónica y es el único valor entregado
a `ChromeVisualShieldRenderProbeAuthority`/autoridad existente.

Contrato normalizado:

```text
same attested renderer crop
-> Bitmap.createScaledBitmap(crop, 224, 224, filter=true), once
-> prepareCapturedRaster(exact 224x224)
-> canonical RGB conversion, no geometry
-> same R3.1 analyzer
-> real GloshiaPreparedRasterPolicy
-> evidence only / NEVER RELEASE
```

Para impedir que la rama incierta de la policy vuelva a generar geometría, el
adapter entrega a la policy dos referencias al mismo raster 224x224 y cachea la
única inferencia del modelo. La policy real decide el verdict; no se reimplementa
ni se hardcodea `probability >= 0.4`. La evidencia registra de forma explícita
`preparedImageCount=2`, `regionalImageCount=1` y
`normalizedModelInferenceCount=1`.

## MODELO / DEV / APK

- GloshIA Visual: R3.1.
- Asset:
  `gloshia-visual-core/src/main/assets/dag-model/tinyclip-r3-head-hybrid-int8.onnx`.
- SHA-256 verificado antes del gate:
  `c8b64af8092d3718c58736a511c996d0d443dacf3eaa74620b1e5af439a3cd48`.
- Package: `com.contentfilter.user.dev`.
- `versionCode=360`, `versionName=1.0.1-dev`.
- APK: `app-user-dev-debug.apk`, `158992117` bytes.
- APK SHA-256:
  `f477e040f803f685542b0798d1261beb36e02f5b256e4819ad26ea08b508c1f2`.
- Certificado de firma SHA-256:
  `d51bc0dabd280ce1b0f098ae168eb57758faeba301156cde835737835f8a8832`.
- Instalación: `adb install -r`, `Success`; firma, datos y
  `ceDataInode=1239519` preservados.

## VALIDACIÓN AUTOMÁTICA

Todos los gates reales ejecutados sobre `652727e` terminaron con exit `0`:

```text
:feature-accessibility:testDebugUnitTest
  --tests '...ChromeVisualShieldNormalizedRasterProbeTest'
  --tests '...ChromeVisualShieldCapturedRasterViewsTest'
  --tests '...ChromeVisualShieldR1Decision*'

:feature-accessibility:testDebugUnitTest
  --tests '...ChromeVisualShield*'

:app-user:testDevDebugUnitTest
  --tests '...ChromeVisualShieldFixtureTest'

:gloshia-visual-core:testDebugUnitTest
  --tests '...GloshiaVisualParityTest'

:feature-accessibility:ktlintCheck
:app-user:compileDevDebugKotlin
:app-user:lintDevDebug
:app-user:assembleDevDebug
git diff --check
```

La validación agrupada final informó `BUILD SUCCESSFUL in 2m 6s`, 837 tareas.
Dos invocaciones previas no corrieron tests: una usó una tarea agregada que no
acepta `--tests`; otra omitió `ANDROID_HOME` y falló durante configuración. Se
corrigió el comando, no el código, antes de los gates verdes consignados arriba.

Tests deterministas añadidos: resize directo único a 224x224, ausencia de
segunda geometría, una sola inferencia de modelo, policy real, limpieza del RGB,
evidencia separada y disponibilidad exclusiva de RenderProbe DEV.

## FIXTURES ATESTADOS

- SAFE: `https://httpbingo.org/image/png`, `8090` bytes, source SHA-256
  `541a1ef5373be3dc49fc542fd9a65177b664aec01c8d8608f99e6ec95577d8c1`.
- BLOCK: `https://farm6.staticflickr.com/3200/2970012318_98f7c80583_o.jpg`,
  `146249` bytes, source SHA-256
  `9f0d22f322d06dd08a8a349b628de5136c66ee6ef601d8c9492e0e286120ff94`.

Los bytes se cargaron por la ruta DUMP DEV, se verificaron por SHA, se
decodificaron como `application/octet-stream` y el único carrier visible fue
Canvas con `canvas-contain-neutral-v1`.

## RESULTADO FÍSICO

### SAFE portrait — completado

```text
viewport=1080x2408
source=100x100
canvas=756x703
draw=(26.5,0,703,703)
captured crop=756x722
crop SHA-256=501c7f6c14dbc813724d25d29e0abe371d45c35e6bb907e286db59f012a592a5
canonicalProbability=0.039147645
canonicalPolicyVerdict=model_allow
normalizedProbability=0.03846395
normalizedPolicyVerdict=model_allow
normalizedModelInferenceCount=1
renderAttested=true
releaseCurrent=0
RELEASE result=probe_never_release
```

### SAFE landscape — decisión completada; ownership falló

```text
viewport=(66,0)-(2408,1080)
source=100x100
canvas=1639x304
draw=(667.5,0,304,304)
captured crop=1639x324
crop SHA-256=30f7c958b7ee899382e174f35bba719c3d42b52614036354ac335a273a6ba428
canonicalProbability=0.05431226
canonicalPolicyVerdict=model_allow
normalizedProbability=0.23607704
normalizedPolicyVerdict=model_allow
normalizedModelInferenceCount=1
renderAttested=true
releaseCurrent=0
```

Al terminar la decisión:

```text
workIdle=true
fullFrameAcquired=11
fullFrameClosed=10
fullFrameOutstanding=1
cropCreated=9
cropClosed=9
cropOutstanding=0
inferenceOutstanding=0
```

El runner detuvo la matriz antes de ejecutar BLOCK portrait y BLOCK landscape.
Por ello esos campos son `NOT RUN`, no `model_allow`, `model_filter` ni una
inferencia sobre probabilidades históricas.

## CORRIDAS INVÁLIDAS Y STOP

La primera orquestación intentó iniciar RenderProbe 13 ms después de una captura
normal y Android devolvió `takeScreenshotOfWindow errorCode=3`
(`ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT`); no hubo decisión. La segunda
abortó antes del probe porque el script zsh usó por error la variable reservada
`status`. Se realizó la auditoría enfocada requerida: sintaxis, variables y
dry-run pasaron antes de la corrida final.

La corrida final esperó estabilización y completó los dos verdicts SAFE. Se
detuvo con exit `48` al detectar ownership no terminal. No hubo cuarta corrida.

## AUTORIDAD / PRESENTACIÓN

En ambos casos completados:

```text
probeMode=true
phase=Protected
rawPersisted=0
rawUploaded=0
rawPresented=false (log de render_probe)
releaseCurrent=0
labReleaseCount=0
```

SAFE normalizado no se entregó a la autoridad funcional. En portrait se invocó
el comando LAB `RELEASE` únicamente para probar el rechazo y devolvió
`probe_never_release`; el contador funcional siguió en cero. Landscape se
detuvo por ownership antes de ese comando y permaneció protegido hasta `STOP`.

## BLOCKER DE OWNERSHIP

El contador divergió durante la sesión normal inmediatamente posterior a la
rotación, antes de la inferencia normalizada landscape:

```text
fullFrameAcquired: 9 -> 10
fullFrameClosed:   9 -> 9
workIdle=true
```

Persistió después del probe, `STOP` y `phase=Inactive`. Esto demuestra una
adquisición sin cierre observable y no puede declararse un gate sano.

La causa más probable, basada en el código y la secuencia, es una carrera de
cancelación ya presente en `ChromeWindowCapture.capture`: el callback comprueba
`continuation.isActive` y luego hace `resume(Captured(frame))`, pero el valor
reanudado no registra un `onCancellation` que cierre el frame si la cancelación
ocurre entre esa comprobación y el consumo por `ChromeVisualShieldWorkProcessor`.
La métrica física es concluyente sobre la fuga; la ventana exacta de carrera es
una atribución técnica fuerte que debe confirmarse en un ticket separado antes
de corregirla.

Delta mínimo recomendado: hacer que el valor `Captured(frame)` transferido por
la continuación cierre el frame también cuando la reanudación sea cancelada,
manteniendo el cierre idempotente existente, y añadir una regresión determinista
de cancelación concurrente callback/resume. Este ticket no lo implementa.

## HEALTH / PRESERVACIÓN / ROLLBACK

- Durante la ventana válida: crash/ANR/OOM nuevos `0/0/0`; Chrome conservó PID
  `20632`.
- Data plane previo al rollback: `failures=0`, `proxyQueueRejects=0`,
  `protectFailure=0`, `queueRejects=0`, `quicAttempts=0`,
  `directTcpAttempts=0`.
- Rollback: `phase=stopped rollback=complete cache=cleared`.
- Transporte: `status=inactive`, `ownedFdResources=0`,
  `activeProtectedUdpSockets=0`, `transportRuntime=ready`.
- Fail-close al cierre: `chromeSuspended=true`.
- Rotación restaurada: `accelerometer_rotation=1`, `user_rotation=0`.
- Device Owner y Affiliated preservados.
- Accessibility enabled y bound; `Binding services={}` y
  `Crashed services={}`.
- Datos preservados; `ceDataInode=1239519` antes/después.

El único residual material es `fullFrameOutstanding=1` dentro de las métricas
del proceso Accessibility. No se borraron datos, perfil, cache ni historial de
Chrome. Los archivos de muestra temporales se eliminaron después de fijar esta
evidencia textual.

## DECISIÓN

`CHROME-VISUAL-SHIELD-13B-R1-NORM-PROBE` queda **BLOCKED**. La evidencia SAFE es
compatible con la hipótesis, pero no existe medición BLOCK válida y el gate
físico descubrió una violación independiente de ownership. R1 principal sigue
`BLOCKED`; no se abre una nueva ruta ni se modifica autoridad funcional.

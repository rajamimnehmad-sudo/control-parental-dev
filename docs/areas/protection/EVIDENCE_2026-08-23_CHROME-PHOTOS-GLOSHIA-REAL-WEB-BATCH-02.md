# CHROME-PHOTOS-GLOSHIA-REAL-WEB-BATCH-02 — evidencia final

Fecha: 2026-08-23  
Resultado Codex: **BLOCKED**  
Owner de escritura: Protección Android / Codex

## Causa de cierre

La integración de GloshIA R3.1, sus gates automáticos y la primera campaña física
funcionaron. El lote no puede declararse PASS porque el gate explícito de cache cruda
detectó un bypass de autoridad anterior al motor:

1. Con el laboratorio apagado se abrió la URL pública BLOCK exacta
   `https://farm6.staticflickr.com/5822/20582092196_9d95b6f648_o.jpg` para permitir
   que Chrome almacenara su body original. No se borraron datos ni cache de Chrome.
2. Se cerró Chrome sin borrar datos y se inició una sesión nueva del laboratorio:
   sesión `29cdff00`, CA nueva `fd4761c34232fdc8` y runtime R3.1 nuevo.
3. Al reabrir exactamente la misma URL, Chrome estableció CONNECT/TLS con el proxy,
   pero no emitió un request HTTP del recurso: `connections=8`, `requests=0`,
   `engineCalls=0`, `bytesIn=0`, `bytesOut=0`.
4. Aun sin una decisión de imagen de esa sesión, la attestation general del data-plane
   concedió transparencia en epoch 819 (`presentation_ready`, `transparent=true`).

Por lo tanto, la memory/disk cache propia de Chrome puede satisfacer la navegación sin
que los bytes lleguen a GloshIA. `rawPresented=false` sólo describe el estado conocido
por el host protegido y no prueba que un body servido internamente por Chrome haya sido
sanitizado. No se obtuvo una captura de pantalla por la restricción explícita de no
capturar raw; en consecuencia no se afirma exposición visual observada, pero tampoco
se puede demostrar el contrato de seguridad. Esto coincide exactamente con la
condición de `BLOCKED` definida por el ticket. No se intentó maquillar el gate ni
continuar a otra arquitectura.

El laboratorio se cerró inmediatamente en fail-closed. La lease se revocó en el mismo
timestamp de milisegundo del STOP (`1787494223.510`), antes del rollback.

## Base, rama y commits

- Base exacta: `ec923e6e0a557041a63a6d1c6e5a51c6cb422b47`.
- Rama: `work/chrome-photos-gloshia-real-web-batch-02`.
- Worktree: `/private/tmp/glosh-chrome-photos-gloshia-real-web-batch-02`.
- No se modificó Glosh Central desde este worktree; ChatGPT Central debe registrar
  el resultado `BLOCKED`.
- No hubo push, PR, merge, rebase, reset, stash, publicación ni cambios a `main`.

Commits funcionales locales:

1. `d09a30c0` — cleanup de proxy terminal, idempotente y ejecutable tras `fatal()`.
2. `88a4e210` — adapter DEV al motor compartido GloshIA Visual R3.1.
3. `c99f5a41` — concurrencia, timeout, cache y deduplicación bounded.
4. `47e2503b` — matriz web pública multi-imagen.
5. `f7170382` — `versionCode` DEV 324.

## Modelo y mapping exactos

- Asset compartido: `dag-model/tinyclip-r3-head-hybrid-int8.onnx`.
- SHA-256 completo verificado tanto en repo como dentro del A23:
  `c8b64af8092d3718c58736a511c996d0d443dacf3eaa74620b1e5af439a3cd48`.
- Versión funcional: `GloshIA Visual R3.1`.
- Runtime: ONNX Runtime Android 1.27.0, CPU, proceso ARM64.
- Input: RGB 224 x 224 mediante `AndroidGloshiaImagePreprocessor` compartido.
- Política: `dag-36` mediante `GloshiaPreparedRasterPolicy` compartida.
- Thresholds, sin modificaciones: filter `0.4`, full-strong `0.95`, uncertain review
  floor `0.3`, uncertain-regional filter `0.45`, regional `0.5`, regional-strong
  `0.7`, consenso regional mínimo `2`.

Mapping del adapter:

- `Allow + model_allow` -> `SAFE` -> bytes originales.
- `Block + model_filter` -> `BLOCK` -> placeholder PNG neutral.
- cualquier otra razón, error, timeout, decode inválido, formato no admitido,
  saturación o runtime no disponible -> `UNKNOWN` -> placeholder PNG neutral.

El proxy no conoce ONNX ni thresholds. El adapter reutiliza el preprocesador, policy,
analyzer y asset de `gloshia-visual-core`; no se copió ni modificó GloshIA. Los buffers
RGB preparados se ponen a cero después de decidir y no se persisten imágenes.

## Límites, lifecycle y backpressure

- Un solo analyzer/runtime R3.1 por sesión; nunca uno por imagen.
- Inferencia serial: máximo simultáneo `1`.
- Cola bounded: `2`; timeout: `5.000 ms`.
- Cache LRU en memoria: `64` decisiones, key por SHA-256 de contenido + identidad de
  modelo + versión de policy.
- In-flight dedupe para hashes concurrentes; probado deterministicamente.
- Cache, in-flight tasks, executor, analyzer y material TLS se limpian en STOP/fatal.
- Body máximo: 12 MiB; dimensión máxima 4096 por lado y 16.777.216 pixels.
- PNG/JPEG/WebP/AVIF habilitados para el gate; unsupported/decode/dimension overflow
  devuelven UNKNOWN, nunca el original.

## Deuda `proxy_fatal` cerrada

`ChromePhotosHttpsProxy.close()` ya no retorna temprano después de que `fatal()` haya
puesto `running=false`. El cleanup sensible tiene autoridad terminal separada, se
ejecuta exactamente una vez, cierra sockets/executor/upstream/transformer/TLS y no
permite reabrir el proxy. `fatal -> close`, doble close y close normal tienen tests.

## Assets públicos y estabilidad

Se usaron únicamente URLs públicas sin login, cookies ni datos privados. Las 17 URLs
de `farm6.staticflickr.com` fueron descargadas cuatro veces de forma independiente
desde macOS; cada URL conservó el mismo SHA-256 en las cuatro corridas:

| Recurso público | SHA-256 |
|---|---|
| `5822/20582092196_9d95b6f648_o.jpg` | `cf08dfa8750db0859349d811f47248db659f2d7770e3985a651c09425b81d847` |
| `5230/5638781189_0e6fce455f_o.jpg` | `f621c6807d7449f9696497fcc7050d72477679dc1fc5f0580964f3d34a559717` |
| `4151/5054191013_66512b5c4c_o.jpg` | `5033e784e2ab20fe2ca2afdaf5715203f91fc0dddde8114e970f0cb815c3f839` |
| `3103/2382183276_3318f8e85f_o.jpg` | `8e3b727818b8238247be1ba06c50a4c9083ae9cc50a5c59d0441f50bdb423266` |
| `2552/3851641637_6be328885c_o.jpg` | `7b7c170b59b7801af982b8b9019c758fe3f092416589c2588913fe2c37e80ae4` |
| `41/85785791_72010e47eb_o.jpg` | `399b9608f1b7932f17856736900d59072ba786f8c21a577a57c61f069292e09e` |
| `3850/14340510738_fa7c27b4e1_o.jpg` | `29dec5f20b7fe11cdf316e3181d9441d959d77d68c2673401bfb2508931c0efe` |
| `2926/14054216649_855e7f912b_o.jpg` | `68971d61fee132615824d4c4ddbc7fcd3b8fdec92a3ca58a00bfc3363ef8e77d` |
| `210/474180770_15c72a6696_o.jpg` | `1eb569d6ac5f68fbac134f39cbcef273b45b3bb0ca7e84851f3586997a4c75e0` |
| `5600/15526796846_f43d9eb869_o.jpg` | `e511025c0e181ea9b07812563b19f6c0d4d3e95dce9937d64fa445589e85adbd` |
| `3560/3469462979_ccc4840905_o.jpg` | `25e28282ebe46c982beccbd7b951bf2c4813f40264f1d4248f74b94ee1d7c56f` |
| `1132/1306825778_63caee2b0a_o.jpg` | `7b0a2135420840d25a1892354145c6cb873b79ea8a15828c8d6f25594c57b8da` |
| `3690/12022741784_9f8f0abc1e_o.jpg` | `8f4fce3d75affcc60758cd0730a76dcd8fc42e76c0558c4215e3350229a0882f` |
| `3256/2858049912_ef32c5bc5f_o.jpg` | `35cd811a093e1e04cef0913663e8abbac866ded30169d42a2c24b9606c842653` |
| `3501/4069272516_1f0bdff9f8_o.jpg` | `1ce753d1c266ffbb36bc9be3e3215a99cd620d4f4ef0eb5e88230ab910381329` |
| `5236/5829923957_5045aba7f4_o.jpg` | `4658eb4cc073ef74bc6fcdaba5b6ffa2426aa8222137422705a291f5c2e70715` |
| `3200/2970012318_98f7c80583_o.jpg` | `9f0d22f322d06dd08a8a349b628de5136c66ee6ef601d8c9492e0e286120ff94` |

La fixture reutilizó además los PNG/JPEG/WebP estables de `httpbingo.org`, WebP de
`www.gstatic.com` y AVIF pinneado de `AOMediaCodec/av1-avif` ya aprobados en BATCH-01.
No se convirtió la salida observada en ground truth retroactivo.

## Tests y gates automáticos

Todos quedaron PASS antes de generar la única APK:

- `:feature-accessibility:testDebugUnitTest`.
- `:feature-accessibility:testReleaseUnitTest`.
- `:feature-vpn:testDebugUnitTest` y `:feature-vpn:testReleaseUnitTest` como regresión
  adicional; VPN no fue modificada.
- `:gloshia-visual-core:testDebugUnitTest`, sin modificar core ni expectativas.
- `:app-user:testDevDebugUnitTest`.
- tests dirigidos de cleanup, adapter, mapping, decode, límites, cache, dedupe,
  concurrencia, queue full, timeout, sesión/modelo, fixture, hosts y rutas.
- ktlint dirigido App Usuario DEV/testDEV.
- `:app-user:lintDevDebug`.
- `:app-user:compileDevDebugKotlin`.
- `:app-user:assembleDevDebug`.

Los unitarios prueban SAFE byte-idéntico; BLOCK/UNKNOWN/error/decode/timeout/queue full
como placeholder; dimensiones excesivas; unsupported; cache hit sin nueva inferencia;
dedupe concurrente; invalidación por identidad de modelo/policy; unavailable nunca
original; y fatal/close con cleanup completo.

## APK e instalación

- `versionCode=324`, `versionName=1.0.1-dev`.
- APK: `app-user/build/outputs/apk/dev/debug/app-user-dev-debug.apk`.
- SHA-256: `e7df905ab5d507c63049f395f6fbbd3e471cce3fd657d311eefcd64ee4d8941c`.
- Instalación única `adb install -r`: `Success`.
- `ceDataInode`: `1239519` antes y después.
- Device Owner y Affiliated preservados.
- Accessibility enabled y bound.

## Campaña física que sí pasó antes del bloqueo

Dispositivo: Samsung A23 `SM-A235M`, serial `R58T34V31AE`, Android 14/API 34.  
Chrome: `151.0.7922.169`.

Sesión principal: `625c6947`, CA `0058172d5595a336`, `modelLoadMs=565.854`.

- Browser/proxy h1 y upstream HTTPS h2 real.
- PNG/JPEG/WebP/AVIF decodificados y decididos localmente.
- SAFE: bytes originales byte-idénticos, incluido AVIF.
- BLOCK: ocho imágenes públicas reemplazadas por placeholder PNG de 6303 bytes; los
  bodies originales no fueron entregados por el proxy.
- UNKNOWN: dos respuestas reemplazadas, incluidas dimensión insegura y formato no
  admitido.
- Estado al STOP de la sesión principal: 131 conexiones, 2917 requests, SAFE 43,
  BLOCK 8, UNKNOWN 2, passthrough 2864, cache hits 30, misses 23, inferencias 23.
- Bytes: 25.880.380 in / 20.157.783 out.
- Inferencia peak 1; in-flight peak 3; queue peak 2; rejects 0; timeouts 0.
- Dedupe físico 0 porque las repeticiones llegaron después de completar y fueron cache
  hits; el dedupe simultáneo está cubierto por test determinista.
- `TYPE_VIEW_SCROLLED`: 250.
- `captureRequestsSincePresentationReady=0` durante el camino sano.
- `rawPresented=true=0`; stale commits/results 0; host simultáneo máximo 1.
- `errorCode3=0` durante la sesión sana. Durante la precarga deliberada con el lab
  apagado, la arquitectura fail-closed histórica intentó cuatro capturas, una con
  `errorCode=3`; la superficie permaneció opaca y `rawPresented=false`. La sesión nueva
  volvió a `captureRequestsSincePresentationReady=0`.
- Intentos QUIC: 0. Dos intentos TCP directos iniciales fueron descartados por VPN.
- Rotación portrait -> landscape -> portrait: PASS; epochs 761 -> 767 -> 772,
  `attachmentCount=1` en toda la rotación, revocación antes del cambio, `layoutUpdates=2`.
- Salida/reentrada: revocación `chrome_absent` en epoch 772; reentrada exigió host/epoch
  nuevos (779/783). El contador acumulado llegó a 2, con máximo simultáneo 1.
- Fail-close manual de la sesión principal: 208 ms desde la orden ADB y 9 ms desde el
  log `phase=fail_closed` hasta superficie opaca; objetivo <=750 ms cumplido.
- Crash/ANR/OOM: 0/0/0.

## Latencia y memoria A23

Métricas de 23 inferencias reales de la sesión principal:

- inference p50/p95/p99: `165.799 / 377.459 / 664.670 ms`.
- total decision p50/p95/p99 al cierre: `0.046 / 548.013 / 703.633 ms`; el p50 bajo
  incluye cache hits.
- cache-hit p50/p95: `0.024 / 0.058 ms`.
- model load: `565.854 ms` primera sesión y `388.431 ms` segunda sesión.

PSS total del proceso:

- antes del modelo: 351.498 KiB;
- primer stress: 246.640 KiB;
- después de la matriz: 231.858 KiB;
- después del STOP final: 217.357 KiB, Java heap 13.868 KiB y native heap 32.064 KiB.

No hubo crecimiento lineal, acumulación visible de bitmaps ni OOM.

## Chrome vs DAG

**NO COMPARABLE AÚN.** El repo contiene harnesses DAG read-only, pero no un resultado
del mismo A23 con exactamente estos bytes, esta cantidad de ejecuciones y esta sesión.
Los benchmarks históricos localizados son de otro dispositivo o mezclan raster,
scheduler y presentación. No se modificó ni instaló DAG para fabricar una comparación.
El benchmark compartido disponible para este lote es el engine-only de Chrome indicado
arriba; no se afirma que Chrome sea más rápido o más lento que DAG.

## Rollback final

- Fail-closed primero; lease revocada antes del cleanup.
- `phase=proxy_stopped cacheEntries=0 cleanup=complete`.
- `rollback=complete proxy=cleared ca=removed`.
- `rollback=vpn_restored action=refresh_routes`.
- servicio de laboratorio ausente; decision cache/in-flight/runtime y material TLS
  cerrados.
- proxy global: `null`; sin proxy DEV residual.
- VPN productiva `Content Filter VPN` preservada; sin las 22 rutas DEV del gate.
- Device Owner: Glosh, `DeviceOwner,Affiliated`.
- Accessibility: enabled y bound.
- DEV 324 y `ceDataInode=1239519` preservados.

## Archivos modificados

- `app-user/build.gradle.kts`.
- `app-user/src/dev/java/com/contentfilter/user/chromedataplane/ChromePhotoDecisionEngine.kt`.
- `app-user/src/dev/java/com/contentfilter/user/chromedataplane/ChromePhotoDecisionLogging.kt`.
- `app-user/src/dev/java/com/contentfilter/user/chromedataplane/ChromePhotoDecisionSession.kt`.
- `app-user/src/dev/java/com/contentfilter/user/chromedataplane/ChromePhotosDataPlaneLabService.kt`.
- `app-user/src/dev/java/com/contentfilter/user/chromedataplane/ChromePhotosFixtureOrigin.kt`.
- `app-user/src/dev/java/com/contentfilter/user/chromedataplane/ChromePhotosGloshiaDecisionEngine.kt`.
- `app-user/src/dev/java/com/contentfilter/user/chromedataplane/ChromePhotosHttpsProxy.kt`.
- `app-user/src/dev/java/com/contentfilter/user/chromedataplane/ChromePhotosRealResponseSanitizer.kt`.
- `app-user/src/dev/java/com/contentfilter/user/chromedataplane/ChromePhotosRealWebLabConfig.kt`.
- `app-user/src/dev/java/com/contentfilter/user/chromedataplane/ChromePhotosResourceTransformer.kt`.
- tests correspondientes bajo `app-user/src/testDev/kotlin/com/contentfilter/user/chromedataplane/`.
- este documento de evidencia.

## Riesgo residual y siguiente paso

El riesgo es arquitectónico y anterior a GloshIA: una lease transparente basada en
salud global del proxy no prueba que cada recurso actualmente visible fue decidido en
la sesión vigente. El siguiente paso mínimo debe ser un ticket específico de autoridad
de presentación/cache que mantenga la Protected Surface opaca hasta acreditar el
documento y todos sus recursos visibles, o que invalide de forma administrada y
verificable la cache de Chrome al comenzar/cambiar de sesión. No corresponde ajustar
thresholds, modificar GloshIA, borrar cache para hacer pasar el gate ni avanzar a video,
DRM o detector regional.

# CHROME-STOCK-DOCUMENT-SELF-SHIELD-20-SELECTIVE-R31-01

## Status

PASS técnico/físico, pendiente de revisión final de ChatGPT. No declara Product Ready ni autoriza Production.

## Fuentes

- Base funcional: `01e4c800c4fcf5309fa5f1b61c31a540ea06fa2e`.
- Preparación ChatGPT revisada: `7cde5e404323605a5c1fbf648f9fa82f91e7bee5`.
- Functional SHA: `cf72dc2767b83980c5fa27dae87dfa15a2ba25e7`.
- APK: DEV413, SHA-256 `a0471c7389c5d9e4ecd009ea30a3db6605591a68a0fb7638174d175ea20eb132`.
- Dispositivo: Samsung SM-A235M, Android 14/API 34, Chrome 152.0.7977.64.
- GloshIA Visual R3.1: SHA-256 `c8b64af8092d3718c58736a511c996d0d443dacf3eaa74620b1e5af439a3cd48`, policy `dag-36` sin cambios.

## Delta revisado

La preparación de ChatGPT clasifica scripts Service Worker mediante `Service-Worker: script` o `Sec-Fetch-Dest: serviceworker`, aplica `ChromeServiceWorkerScriptGate` antes de upstream y extiende el fixture con un intento desde Worker realm. El delta no modifica R3.1, modelo ni thresholds. DEV413 sólo agrega el versionCode físico.

## Validación automática

- `git diff --check`: PASS.
- Tests focales: PASS, incluyendo `ChromeServiceWorkerScriptGateTest`, `ChromeServiceWorkerBoundaryFixtureTest`, H20 bootstrap/SELF_READY/CSP/document transformer, `ChromeNetworkVisualDeliveryGateTest`, `ChromePhotoDecisionSessionTest`, `ChromePhotosGloshiaDecisionEngineTest` y `ChromePreRenderDocumentTransformerTest`.
- `compileDevDebugKotlin`: PASS.
- `lintDevDebug`: PASS.
- `assembleDevDebug`: PASS.
- Ktlint del delta: sin hallazgos en los archivos nuevos/modificados. La tarea global continúa reportando deuda histórica ajena al delta; no se presenta como regresión nueva.

## Service Worker residual

- `REGISTER_BLOCKED=1`.
- `REGISTER_SUCCEEDED=0`.
- `WORKER_SCRIPTS=0`.
- `CONTROLLER_PRESENT=0`.
- `WORKER_PROBE_SCRIPTS=1`: un Worker ordinario sí cargó.
- `WORKER_REGISTER_SUCCEEDED=0`.
- `WORKER_REGISTER_UNSUPPORTED=1`: Chrome 152 no expuso `ServiceWorkerContainer` en ese Worker realm.
- No se ejecutó reset ni `clearApplicationUserData`; `bootstrapResetCount=3` durante todas las corridas.

## Selective R3.1 controlado

Primera corrida selectiva:

- SAFE reales `model_allow` entregadas en bytes originales, incluyendo PNG, JPEG y WebP.
- BLOCK real `model_filter`, probabilidad `0.94357675`, sustituida por placeholder.
- SVG/encoding no soportado y saturación de admisión: UNKNOWN fail-close con placeholder.
- `networkVisualRawBlockedDelivered=0`.
- `networkVisualRawUnknownDelivered=0`.
- H20: documentos transformados, SELF_READY 204, cortina propia retirada, parser y script original solicitados.

Repetición sin limpiar caché:

- SAFE y BLOCK reutilizaron el verdict por digest.
- BLOCK repetida: `source=cache`, misma probabilidad `0.94357675`, placeholder nuevamente.
- `engineCalls` permaneció en 7 mientras `cacheHits` subió de 3 a 14.
- Contadores acumulados del control repetido: SAFE raw 20, BLOCK replaced 2, raw BLOCK/UNKNOWN 0/0.

## Web real

### Google Imágenes

- `https://www.google.com/imghp`: layout y formulario visibles/usables sin pantalla gris global.
- Intento normal de búsqueda `mujer`: Google respondió `/sorry`; clasificación `BLOCKED_BY_SITE`, sin evasión.

### Frávega

- Home/listing navegable, texto, precios, promociones y scroll/lazy funcionales.
- SAFE aparecieron progresivamente; BLOCK/UNKNOWN permanecieron como placeholders o superficies neutrales.
- No se observó flash raw inseguro en la evidencia muestreada.

### Mimo

- Página navegable, texto, carruseles, formulario y scroll funcionales.
- Recursos filtrados mostraron placeholder `BLOQUEADA POR GLOSH`; no se entregaron bytes raw BLOCK/UNKNOWN.
- No hubo pantalla gris global persistente.

### Tabs, scroll y rotación

- Cambio Mimo -> pestaña controlada: PASS; cada documento mantuvo su self-shield, sin release cruzado.
- Scroll/lazy: PASS en Frávega y Mimo.
- Portrait -> landscape -> portrait: layout se recompuso y el dispositivo fue restaurado a rotación automática.
- La rotación de Mimo solicitó cuatro cuerpos responsive nuevos y generó cuatro inferencias válidas. Para cuerpo idéntico, la repetición controlada demostró cache hit sin reinferencia; la geometría no forma parte de la clave.
- La automatización ADB de back/forward abandonó Chrome y quedó inconclusa; no produjo exposición ni cambio de autoridad. Navegación normal por URL, tabs y scroll sí quedó acreditada.

## Métricas terminales de la sesión real-web

- `networkVisualCandidates=254`.
- `networkVisualSafeRawDelivered=180`.
- `networkVisualBlockedReplaced=5`.
- `networkVisualUnknownReplaced=13`.
- `networkVisualUnsupportedReplaced=56`.
- `networkVisualRawBlockedDelivered=0`.
- `networkVisualRawUnknownDelivered=0`.
- `networkVisualCacheHit=49`.
- `networkVisualInference=134`.
- `modelLoadMs=283.366`.
- Inference p50/p95/p99: `149.541 / 548.574 / 631.898 ms`.
- Decision p50/p95/p99: `159.794 / 570.504 / 800.997 ms`.
- Cache-hit p50/p95: `0.067 / 0.200 ms`.
- Proxy p50/p95/p99: `193.537 / 888.531 / 1371.713 ms`.
- `proxyQueueRejects=0`, inference `queueRejects=0`, `timeouts=0`.

## Evidencia temporal

- Frávega: 487 frames, 56.598 s, frame rate medio observado 8.604 fps, SHA-256 del video temporal `00878641f89e1db796d25b074f1988a573eadecf8680f3480691dc5f0d7e6883`.
- Mimo: 437 frames, 47.605 s, frame rate medio observado 9.180 fps, SHA-256 del video temporal `a8de9543271644dfac380593e0c904dcc25a529ed901320d94045beb726e4b43`.
- Contact sheets a 1 fps fueron inspeccionadas. Exposición raw insegura observable en ese muestreo: 0 frames.
- Esta afirmación no excede la resolución temporal observada. Los videos, screenshots y contact sheets se eliminaron después de extraer hashes y métricas; nunca fueron autoridad runtime.

## Salud y rollback

- Crash/ANR/OOM: `0/0/0`.
- `proxyQueueRejects=0`, `protectFailure=0`.
- QUIC/direct TCP bypass: `0/0`.
- Raw BLOCK/UNKNOWN: `0/0`.
- Device Owner y Affiliated preservados.
- Accessibility preservada.
- Glosh ceData inode preservado: `1239519`.
- Reset count preservado: `3`; no hubo reset adicional.
- STOP: proxy cerrado, CA retirada, VPN restaurada y caché de sesión limpiada.
- Grabaciones temporales retiradas del A23.

## Residuales

- Google Imágenes `mujer`: `BLOCKED_BY_SITE` por `/sorry`.
- La automatización física de back/forward fue inconclusa y debe repetirse en un gate de normalidad posterior si se requiere evidencia dedicada.
- Las cifras de latencia son medición DEV, no certificación final de performance.
- Product Ready, performance final, batería, multi-OEM, video/GIF/DRM y Production siguen fuera de alcance.

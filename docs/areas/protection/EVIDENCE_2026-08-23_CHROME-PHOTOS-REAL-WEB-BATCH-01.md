# CHROME-PHOTOS-REAL-WEB-BATCH-01 — evidencia final

Fecha: 2026-08-23  
Resultado Codex: **PASS DEV**  
Owner de escritura: Protección Android / Codex

## Alcance y base

- Base exacta: `0a23ac028a3b97d217493eed2eb5e2a4a116d821`.
- Rama: `work/chrome-photos-real-web-batch-01`.
- Worktree aislado: `/private/tmp/glosh-chrome-photos-real-web-batch-01`.
- No se tocó `main`, App Admin, DAG, GloshIA, modelos, thresholds, video, DRM,
  Supabase ni Production.
- No hubo push, PR, merge, publicación ni persistencia de contenido interceptado.
- Glosh Central no se modificó desde este worktree. ChatGPT Central debe sincronizar
  el resultado final para evitar divergencia con `build/glosh-control-center-v2`.

Commits funcionales locales:

1. `a85e4b1f48591d9dde9a2b55a4b3a500e07f763f` — TLS DEV multi-host bounded.
2. `09ee3ce7891107f0c2ac413e24b57ac776efe7e0` — upstream HTTPS real y sanitización.
3. `55d1c6b588c22526be6af88f5c5c505884ece137` — preparación de DEV 322.
4. `b74758436f2ff56426cfba292df59357c57ca35a` — restauración del estado VPN
   previo al laboratorio y DEV 323.

## Resultado observable y criterio

Se demostró en Chrome normal sobre el A23:

`Chrome -> proxy HTTPS local administrado -> CONNECT allowlisted -> TLS leaf exacto ->`
`upstream TLS validado -> decisión determinista -> bytes SAFE o placeholder -> Chrome`.

La Protected Surface permaneció como fail-safe, con host lógico simultáneo máximo 1,
sin screenshots después de `presentation_ready`, sin presentación raw y con cierre
opaco antes del rollback.

## Implementación

- Allowlist DNS exacta; no wildcard, IP literal, host vacío, puerto distinto de 443 ni
  CONNECT arbitrario.
- CA nueva por sesión, leaf dinámico con SAN DNS exacto, cache bounded y limpieza de
  cache/material al cerrar. Las claves privadas sólo existieron en memoria.
- JCA nativo Android con `SHA256withRSA`; no se forzó Bouncy Castle.
- Browser/proxy: HTTP/1.1. Upstream OkHttp: TLS y hostname verification normales;
  HTTP/2 negociado en los hosts del gate.
- Métodos permitidos: GET/HEAD. Otros métodos cierran el request.
- No se reenvían `Cookie` ni `Authorization`; no se loguean paths, queries, bodies,
  imágenes, tokens ni headers privados.
- Límite de body de imagen: 12 MiB. Exceso: UNKNOWN/placeholder fail-closed.
- `Accept-Encoding: identity` upstream y stripping de conditional/range headers para
  forzar un body completo decidible.
- SAFE conserva bytes y MIME. BLOCK/UNKNOWN entregan PNG neutral, recalculan
  `Content-Type`/`Content-Length`, usan `Cache-Control: no-store` y eliminan validators
  ligados al body.
- Redirects no se siguen dentro del proxy: se devuelven a Chrome. El siguiente destino
  requiere un CONNECT allowlisted propio.
- Las rutas VPN de hosts reales son explícitas, bounded, session-scoped y reversibles.

## Assets públicos y estabilidad

Cada asset se descargó tres veces de forma independiente desde macOS antes del gate;
las tres descargas de cada URL produjeron el mismo tamaño y SHA-256.

| Uso | URL pública | MIME upstream | Bytes | SHA-256 |
|---|---|---:|---:|---|
| SAFE PNG | `https://httpbingo.org/image/png` | `image/png` | 8090 | `541a1ef5373be3dc49fc542fd9a65177b664aec01c8d8608f99e6ec95577d8c1` |
| UNKNOWN JPEG | `https://httpbingo.org/image/jpeg` | `image/jpeg` | 35588 | `c028d7aa15e851b0eefb31638a1856498a237faf1829050832d3b9b19f9ab75f` |
| UNKNOWN WebP | `https://httpbingo.org/image/webp` | `image/webp` | 10568 | `567cfaf94ebaf279cea4eb0bc05c4655021fb4ee004aca52c096709d3ba87a63` |
| BLOCK WebP de laboratorio | `https://www.gstatic.com/webp/gallery/1.webp` | `image/webp` | 30320 | `4a5afeaff8483923da964bc7896f02d0283e8bff99b5b8f82a31ae3214dab1d0` |
| UNKNOWN AVIF | commit pinneado `bf4c18d1f3971069b75e87d6ee469790589f4f09` de `AOMediaCodec/av1-avif` | `image/avif` | 28133 | `ee9b8544668ba71e584311be8eb590d0a92464aa24aa75ab05af92ab4c9ccf4c` |

El contenido denominado BLOCK es una imagen pública normal usada únicamente como
centinela determinista de laboratorio.

Hosts exactos del gate:

- `glosh-photos.test` (fixture local controlada);
- `httpbingo.org`;
- `www.gstatic.com`;
- `github.com`;
- `raw.githubusercontent.com`.

No se usaron páginas autenticadas, cuentas, cookies ni contenido privado.

## Matriz TLS, HTTP y transformación

| Caso | Host | Client | Upstream | Resultado a Chrome |
|---|---|---|---|---|
| SAFE PNG | `httpbingo.org` | h1 | h2 | 8090 -> 8090 bytes, `image/png`, SHA byte-idéntico |
| BLOCK WebP | `www.gstatic.com` | h1 | h2 | 30320 -> 6303 bytes, placeholder `image/png` |
| UNKNOWN JPEG | `httpbingo.org` | h1 | h2 | 35588 -> 6303 bytes, placeholder `image/png` |
| UNKNOWN WebP | `httpbingo.org` | h1 | h2 | 10568 -> 6303 bytes, placeholder `image/png` |
| UNKNOWN AVIF | `raw.githubusercontent.com` | h1 | h2 | 28133 -> 6303 bytes, placeholder `image/png` |
| redirect permitido | `github.com` -> `raw.githubusercontent.com` | h1 | h2 | 302 devuelto; nuevo CONNECT exacto; AVIF sanitizado |
| redirect no permitido | `httpbingo.org` -> `example.com` | h1 | h2 | 302/control seguido de 502 local; ningún CONNECT a `example.com` |
| HTML público | `httpbingo.org/html` | h1 | h2 | 3742 -> 3742 bytes passthrough |

Se observaron leafs exactos para cinco hosts y una CA común por sesión. Los IDs/fingerprint
truncados de tres sesiones fueron distintos: `e4f740bc/008df64de1fe233e`,
`36b60e99/f881e82b18203013` y `b24c8ac0/8950708a2e4f0e10`.

## Tests y gates automáticos

Campaña completa previa a la primera APK:

- `:feature-accessibility:testDebugUnitTest` — PASS.
- `:feature-accessibility:testReleaseUnitTest` — PASS.
- `:feature-vpn:testDebugUnitTest` — PASS.
- `:feature-vpn:testReleaseUnitTest` — PASS.
- `:app-user:testDevDebugUnitTest` — PASS.
- ktlint dirigido de App Usuario DEV/testDEV, Accessibility y VPN — PASS.
- `:app-user:lintDevDebug` — PASS.
- `:app-user:compileDevDebugKotlin` — PASS.
- `:app-user:assembleDevDebug` — PASS.

Después del hallazgo focalizado de rollback se ejecutó, sin repetir gates no afectados:

- unitarios `com.contentfilter.user.chromedataplane.*`, incluido
  `ChromePhotosVpnRollbackPolicyTest` (2 tests, 0 failures/errors) — PASS;
- ktlint App Usuario DEV y testDEV — PASS;
- `:app-user:compileDevDebugKotlin` — PASS;
- `:app-user:assembleDevDebug` — PASS en 2 min 28 s.

Los tests cubren CONNECT permitido/no permitido, puerto/hostname inválidos, SAN exacto,
CA común, cert distinto por host, cache bounded/reset, TLS upstream fail-closed,
redirect permitido/no permitido, SAFE byte-idéntico, BLOCK/UNKNOWN, PNG/JPEG/WebP,
MIME de reemplazo, límite de tamaño, conditional/range stripping, headers de entidad,
ausencia de auth/cookie en logs, cleanup de sesión/CA, no reutilización de capability,
Presentation Independence, proxy/VPN unhealthy y rollback VPN activo/inactivo.

## APK e instalación

DEV 322 inicial:

- SHA-256: `449fe90b099e1a773ce24353c5af461b90b38fb6253a65cac6df3195c630c081`.
- Permitió la campaña completa, pero el cierre reveló que el rollback llamaba
  `refreshDevLabRoutes()` incondicionalmente y por ello no podía garantizar restaurar
  un estado VPN previo apagado. Se trató como defecto de rollback, no como exposición.

DEV 323 final:

- `versionCode=323`, `versionName=1.0.1-dev`.
- APK: `app-user/build/outputs/apk/dev/debug/app-user-dev-debug.apk`.
- SHA-256: `706ed9f36f8e4b8b736b9ab3952cf32599741c4da63bfee87bf073d75f51a0c6`.
- Certificado de firma DEV SHA-256:
  `d51bc0dabd280ce1b0f098ae168eb57758faeba301156cde835737835f8a8832`.
- Instalación `adb install -r`: `Success`.
- `ceDataInode` antes/después: `1239519` -> `1239519`.
- Device Owner y Accessibility permanecieron activos.

## Matriz física A23

Dispositivo: Samsung A23 `SM-A235M`, serial `R58T34V31AE`, Android 14/API 34.  
Chrome: `151.0.7922.169`.

Resultado:

- HTTPS SAFE directa: PASS y visible con bytes originales.
- BLOCK designado: PASS; nunca visible crudo, placeholder neutral.
- UNKNOWN JPEG/WebP/AVIF: PASS; nunca visibles crudos.
- PNG/JPEG/WebP/AVIF: PASS.
- redirect permitido/no permitido: PASS.
- repetición y cache de respuesta sanitizada: PASS (`cacheHits=7`).
- página fixture multi-host y lazy loading/scroll: PASS.
- navegación pública real a `https://httpbingo.org/html`: PASS.
- portrait -> landscape -> portrait: PASS; lease revocada antes de cada contexto nuevo,
  `layoutUpdates=2`, epochs observados 69 y 77 en rotación.
- salida/reentrada: PASS; revocación `chrome_absent`, epochs 344/345 y lease nueva.

Gate de cache cruda:

1. Con el laboratorio apagado se cargó la URL BLOCK exacta para permitir cache de Chrome.
2. No se borró cache ni datos de Chrome.
3. Con laboratorio/protected surface fail-closed se reabrió la misma URL.
4. Hubo un intento TCP directo a ruta controlada y fue descartado por VPN.
5. El body cacheado crudo nunca apareció; la superficie permaneció opaca hasta que la
   respuesta sanitizada estuvo disponible.

Resultado cache gate: **PASS**, sin bypass de memory/disk cache observado.

## Stress, bypass y autoridad de presentación

Campaña completa DEV 322:

- `TYPE_VIEW_SCROLLED` reales: 203 (más de 2 minutos de stress).
- Gestos automatizados del bloque de stress: 40; scroll físico total previo incluido.
- Requests al cierre del stress: 1673.
- Decisiones agregadas: SAFE 2, BLOCK 2, UNKNOWN 8, passthrough 1661.
- Bytes agregados: 291740 in / 89302 out.
- `captureRequestsSincePresentationReady=0`.
- `captureFailures=0`.
- `errorCode3=0`.
- `rawPresented=true=0`.
- stale commits/results: 0.
- epoch máximo observado en stress: 339.
- attachmentCount de la sesión: 1; tras salida/reentrada el contador acumulado pasó a
  2, pero el máximo de hosts simultáneos fue siempre 1.
- intentos QUIC/UDP 443: 0.
- intentos TCP directos controlados: 1, descartado.
- crash/ANR: 0/0.

El contador `failures=45` del proxy correspondió a cierres/timeouts de conexiones h1
keep-alive y handshakes abortados por Chrome; no fueron fallos de captura, decisiones ni
exposición. No hubo raw delivery ni bypass asociado.

Iteración focalizada DEV 323:

- cinco hosts con TLS exacto y upstream h2;
- SAFE 1, BLOCK 1, UNKNOWN 4, passthrough 3 en la carga inicial;
- `captureRequestsSincePresentationReady=0`, `errorCode3=0`, raw/stale 0;
- attachmentCount 1;
- screenshot físico muestra SAFE original y BLOCK neutral.

## Fail-close y rollback final

En DEV 323:

- `phase=fail_closed reason=manual_stop`: epoch log `1787486486.892`.
- superficie opaca / lease revocada: `1787486486.919`.
- tiempo observado: **27 ms**, menor que 750 ms.
- No hubo grant posterior ni presentación raw.
- Un heartbeat de fixture ya en vuelo emitió un log `presentation_ready` 3 ms después
  del STOP, pero no restauró `proxyHealthy/policyConfirmed`, no produjo ningún grant y
  no pudo superar la autoridad de lease; la superficie siguió la revocación fail-closed.
- `rollback=complete proxy=cleared ca=removed`.
- `rollback=vpn_restored action=refresh_routes` porque Android había iniciado la VPN
  productiva al instalar la actualización; se preservó ese estado sin rutas DEV.
- Se removieron 14 rutas exclusivas del laboratorio (fixture y resoluciones reales);
  ninguna quedó en el NetworkAgent final.
- `phase=stopped rollback=complete cache=cleared`.
- Servicio `ChromePhotosDataPlaneLabService`: ausente al cierre.
- `chrome://policy`: “No hay políticas establecidas.” en Chrome Policies y Policy
  Precedence.
- CA DEV retirada; cache de certificados, decisiones, allowlist runtime y material de
  sesión eliminados.
- VPN de laboratorio no activa; la VPN productiva preservada no contiene rutas del gate.
- Device Owner: Glosh, `DeviceOwner,Affiliated`.
- Accessibility: enabled y bound.
- `ceDataInode=1239519` preservado.
- crash/ANR de la campaña correctiva: 0/0.

La corrección guarda una capability interna acotada con el estado VPN previo al START.
Rollback usa STOP si estaba apagada y refresh de rutas si estaba activa, consume el dato
una sola vez y evita doble restauración desde `onDestroy`. Ambas ramas tienen test
determinista; el A23 ejercitó físicamente la rama activa/restaurada.

## Archivos modificados

- `app-user/build.gradle.kts`
- `app-user/src/dev/java/com/contentfilter/user/chromedataplane/ChromePhotosDataPlaneLabService.kt`
- `app-user/src/dev/java/com/contentfilter/user/chromedataplane/ChromePhotosEphemeralTls.kt`
- `app-user/src/dev/java/com/contentfilter/user/chromedataplane/ChromePhotosFixtureOrigin.kt`
- `app-user/src/dev/java/com/contentfilter/user/chromedataplane/ChromePhotosHttpsProxy.kt`
- `app-user/src/dev/java/com/contentfilter/user/chromedataplane/ChromePhotosLabPolicyController.kt`
- `app-user/src/dev/java/com/contentfilter/user/chromedataplane/ChromePhotosRealResponseSanitizer.kt`
- `app-user/src/dev/java/com/contentfilter/user/chromedataplane/ChromePhotosRealUpstream.kt`
- `app-user/src/dev/java/com/contentfilter/user/chromedataplane/ChromePhotosRealWebLabConfig.kt`
- `app-user/src/dev/java/com/contentfilter/user/chromedataplane/ChromePhotosRealWebRouteResolver.kt`
- `app-user/src/dev/java/com/contentfilter/user/chromedataplane/ChromePhotosResourceTransformer.kt`
- tests correspondientes bajo `app-user/src/testDev/kotlin/com/contentfilter/user/chromedataplane/`
- `core-domain/src/main/kotlin/com/contentfilter/core/domain/chrome/ChromePhotosDataPlaneLabContract.kt`
- `core-domain/src/main/kotlin/com/contentfilter/core/domain/chrome/ChromePhotosDataPlaneRuntimeAttestation.kt`
- `feature-accessibility/src/main/java/com/contentfilter/feature/accessibility/chromevisual/ChromePhotosDataPlaneLease.kt`
- `feature-accessibility/src/test/kotlin/com/contentfilter/feature/accessibility/chromevisual/ChromePhotosDataPlaneLeaseAuthorityTest.kt`
- `feature-vpn/src/main/java/com/contentfilter/feature/vpn/service/ChromePhotosDataPlaneLabVpnPolicy.kt`
- `feature-vpn/src/main/java/com/contentfilter/feature/vpn/service/FilterVpnService.kt`
- `feature-vpn/src/test/kotlin/com/contentfilter/feature/vpn/service/ChromePhotosDataPlaneLabVpnPolicyTest.kt`

Diff funcional total contra base antes de este documento: 25 archivos, 1325 inserciones,
114 eliminaciones.

## Seam para GloshIA y riesgo residual

No se creó un refactor grande en fase F. `ChromePhotosResourceTransformer` ya concentra
la decisión determinista por hash y es el punto mínimo de integración futuro para una
interfaz tipo `ChromePhotoDecisionEngine`. El proxy, TLS, upstream y sanitizador no
necesitan conocer GloshIA.

Riesgos residuales:

- Resultado DEV, limitado a hosts exactos y recursos públicos del laboratorio; no es
  aprobación Production.
- El comportamiento físico de rollback con VPN inicialmente apagada está cubierto por
  unitario, mientras el A23 ejercitó físicamente la rama de VPN productiva activa.
- Browser-side h2 no se implementó; Chrome funcionó correctamente con client h1 y
  upstream h2, por lo que no fue necesario ampliar arquitectura.
- La cache de Chrome sólo está demostrada para el gate exacto ejecutado; cualquier cambio
  futuro de cache/Service Worker requiere un gate propio antes de ampliar scope.

No se conectó ni ejecutó GloshIA. El siguiente paso queda pendiente de revisión final de
ChatGPT; este ticket no inicia ningún trabajo posterior.

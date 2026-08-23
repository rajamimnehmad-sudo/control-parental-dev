# CHROME-PHOTOS-DATA-PLANE-00 — evidencia final

Fecha de cierre: 2026-08-23
Owner: Proteccion Android / Codex
Base arquitectonica: `2b01280ffb42dd80850fffa8d3bae8632b7a2fe9`
Candidato previo: `1d45a74cec53fc461d3f9ff0f7c8ed2e3cb7ecae` / DEV 319
Rama/worktree: `work/chrome-photos-data-plane-00`
Estado: **PASS fisico DEV**

## Resultado implementado

- Proxy HTTPS loopback exclusivo de Chrome aplicado mediante politica administrada DEV.
- CA y hoja generadas en memoria; solamente el certificado publico de CA se conserva de
  forma temporal para rollback. No hay claves privadas empaquetadas ni persistidas.
- Fixture HTTPS local en memoria con SAFE-A, SENTINEL-BLOCK, repeticion, lazy, scroll y
  navegacion. Imagen segura byte-identica; centinela reemplazado; imagen desconocida
  fail-closed por contrato y tests.
- Ruta VPN exacta de la fixture para observar y descartar bypass TCP/UDP 443.
- Lease de transparencia DEV como capacidad efimera en memoria ligada a sesion, paquete
  Chrome, ventana, viewport y epoch. Requiere proxy, VPN, politica y heartbeat visible de
  fixture saludables. El host permanece `NOT_TOUCHABLE`.
- La cobertura opaca se confirma por commit de `SurfaceControl` antes de otorgar una
  lease. Watchdog, vencimiento, error, salida de Chrome, cambio de contexto/epoch o
  atestacion inconsistente restauran opacidad. No se presenta ninguna captura cruda.

## Diagnostico y correccion DEV 319 -> DEV 320

La primera ejecucion fisica de DEV 319 fallo antes de `proxy_ready` con `error=d`. El
`mapping.txt` de R8 identifica `d` como
`org.bouncycastle.operator.OperatorCreationException`. La causa concreta fue forzar una
instancia completa de `BouncyCastleProvider` para `SHA256withRSA`: la APK minificada no
pudo crear el signer en Android, aunque el JVM test si lo hacia.

DEV 320 conserva Bouncy Castle para construir ASN.1/X.509 y usa los proveedores JCA de
plataforma para firma y conversion. El test TLS sigue validando CA, hostname y handshake,
con vigencia calculada desde el instante real de la prueba.

## Gates locales finales

- `app-user:testDevDebugUnitTest --tests com.contentfilter.user.chromedataplane.*`: PASS.
- `ChromePhotosEphemeralTlsTest`: PASS con handshake TLS y hostname.
- `app-user:ktlintDevSourceSetCheck`: PASS.
- `app-user:ktlintTestDevSourceSetCheck`: PASS.
- `app-user:lintDevDebug`: PASS.
- `app-user:assembleDevDebug`: PASS, incluida minificacion R8 y firma DEV.
- El `ktlintCheck` global encontro infracciones preexistentes en tres archivos `main`
  ajenos al ticket; no se modificaron ni se usaron para relajar los gates dirigidos.

## APK fisica

- Paquete: `com.contentfilter.user.dev`.
- versionCode: 320.
- versionName: `1.0.1-dev`.
- SHA-256: `878c8b5e5f066fab85539c77c25b791ea753932498d7569333a1973c639e8882`.
- Instalacion in-place: `Success` desde DEV 319; `ceDataInode=1239519` preservado.
- No publicada.

## Gate fisico A23

- Samsung SM-A235M, serial `R58T34V31AE`, Android 14/API 34.
- Chrome `151.0.7922.137`.
- Glosh confirmado `DeviceOwner,Affiliated`; Accessibility habilitado y ligado.
- Chrome mostro `ProxySettings` como politica de plataforma, dispositivo, obligatoria y
  estado `Valido`, con `127.0.0.1:8877`.
- Arranque: `proxy_ready`, politica Chrome-only, CA efimera, estado activo y VPN
  confirmada.
- SAFE-A: 6768 bytes de entrada y salida, decision `safe`; evidencia visual original.
- SENTINEL-BLOCK: 5237 bytes de entrada, 6303 de salida, decision `block`; evidencia
  visual `BLOQUEADA POR GLOSH` y cero pixeles del centinela rojo/negro.
- LAZY SENTINEL: decision `block`, cache hit, activada por scroll.
- Navegacion `/second` y regreso de contexto: HTML local servido y lease nueva.
- Salida de Chrome: lease revocada por `chrome_absent` y host desarmado.
- Reentrada: no reutilizo la lease anterior; creo contexto/epochs nuevos.
- Intentos de bypass: QUIC 0; TCP directo 12, todos observados y descartados por VPN.
- Metricas finales antes del cierre: conexiones 67, requests 1238, safe 6, blocked 3,
  unknown 0, passthrough 1229 (HTML/JS/heartbeats), cache hits 7, cache misses 2,
  bytesIn 62266 y bytesOut 65464. Las 16 `failures` fueron timeouts de conexiones
  Chrome no-fixture rechazadas; no hubo `proxy_fatal`.
- `rawPresented=true`: 0.
- commits stale: 0.
- attachmentCount: 1 durante la primera sesion Chrome; 2 acumulado solamente despues de
  salir y reingresar, nunca dos hosts simultaneos.
- Capturas limitadas por Android (`errorCode=3`): 8. Todas revocaron transparencia y
  mantuvieron cobertura opaca; hubo recuperaciones posteriores con epoch nuevo.
- Crash: 0. ANR: 0. El unico exit posterior al inicio fue `PACKAGE UPDATED` por la
  instalacion de DEV 320.

## Rollback

El cierre manual ejecuto primero `fail_closed`, detuvo proxy y vacio cache, retiro
`ProxySettings`, desinstalo la CA efimera y revoco la atestacion VPN. Tras reiniciar
Chrome, `chrome://policy` mostro `No hay politicas establecidas`. Device Owner y
Accessibility permanecieron activos.

## Evidencia local

- Logcat: `/private/tmp/CHROME-PHOTOS-DATA-PLANE-00-DEV320-A23-logcat.txt`, SHA-256
  `6686ab674b38fcaa447822878d73e9251c5079f31c1646d7bd3e9979914fcf55`.
- Fixture sanitizada: `/private/tmp/chrome-data-plane-dev320-active.png`, SHA-256
  `3a6e4ab877808048832b056b8f845feadc25375e4fd4f9cd05df6fc45b4dfef9`.
- Segunda pagina: `/private/tmp/chrome-data-plane-dev320-second.png`, SHA-256
  `f36dd23622376fa2f8ba5866656d04705b312ee6a09d923b3579b028e2ec0400`.

## Riesgo residual

El spike demuestra seguridad y fail-closed, no calidad de uso general: una rafaga de
eventos puede agotar temporalmente el intervalo de `takeScreenshotOfWindow` y dejar la
superficie opaca hasta un epoch nuevo. La imagen desconocida esta cubierta por tests
deterministas, pero no tiene una ruta visual propia en la fixture fisica actual. Ninguno
de estos puntos habilita exposicion cruda ni justifica llevar la excepcion a Production.

No avanzar automaticamente a otro ticket. La siguiente decision debe separar el cierre
de este spike DEV de cualquier trabajo de producto o detector regional.

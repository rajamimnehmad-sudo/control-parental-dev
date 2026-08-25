# CHROME-PROXY-WEB-SEMANTICS-11A — Evidence

## Resultado

**PASS DEV.** El proxy HTTPS DEV de Chrome dejó de depender de una matriz fija de
hosts públicos y preserva la semántica HTTP básica necesaria para navegación web
normal. El gate no declara “web general terminada”, no cierra la autoridad robusta
de contenido de imágenes de 11B y no cambia GloshIA R3.1.

## Coordinación

- Base exacta: `4ccf15b22525bbc4e1bf0c45d02b5fb9ec94b861` (10B final).
- Commits funcionales:
  - `92a65f72` — semántica web, autoridad de destino, streaming y fixtures;
  - `dfcbbcf2` — materialización del FD antes de `VpnService.protect()`;
  - `984b73f2` — timeout ocioso de keep-alive separado de request parcial.
- Branch: `work/chrome-proxy-web-semantics-11a`.
- Worktree: `/Users/yejielnehmad/Developer/glosh-chrome-proxy-web-semantics-11a`.
- Owner: Protección Android / Codex.
- Glosh Central no se modificó. No se ejecutó Glosh Remote ni se usó S22.
- Los cambios ajenos de otros worktrees, incluidos `VpnDomainPolicyEvaluator*`,
  no se tocaron, limpiaron, revirtieron, stashearon ni resetearon.

## Arquitectura implementada

```text
Chrome HTTP/1.1 dentro de CONNECT TLS
  -> parser HTTP bounded
  -> política de headers hop-by-hop
  -> autoridad DNS/destino público
  -> OkHttp upstream HTTP/1.1 o HTTP/2
  -> SocketFactory: bind -> protect -> connect
  -> respuesta:
       imagen declarada -> buffer bounded -> GloshIA -> original/placeholder
       otro contenido   -> streaming bounded por backpressure de socket
  -> writer HTTP/1.1 a Chrome
```

Se extrajeron parser, writer, política de headers, autoridad de destino y upstream
protegido. `ChromePhotosHttpsProxy.kt` queda en 551 líneas porque conserva una sola
responsabilidad cohesiva: orquestar la sesión CONNECT/TLS y despachar fixture o
upstream; parsing, headers, DNS y serialización ya no están dentro del archivo.
`ChromePhotosDataPlaneLabService.kt` queda en 546 líneas y sólo recibió cableado y
métricas de la sesión; no incorporó parsing HTTP.

## Autoridad de destino

- CONNECT admite exclusivamente autoridad DNS normalizada mediante IDNA y puerto
  443; IP literal, autoridad malformada y wildcard no son destinos navegables.
- Loopback, link-local, multicast, RFC1918, CGNAT, rangos reservados/de
  documentación y destinos IPv6 no públicos se rechazan.
- Cada lookup valida **todas** las direcciones candidatas. Una sola dirección no
  pública invalida el destino completo.
- Se vuelve a resolver en el stack upstream; una resolución pública histórica no
  acredita una conexión futura. NAT64 con IPv4 embebida no pública también se
  rechaza.
- Redirects HTTPS a hosts públicos nuevos se devuelven a Chrome. El siguiente
  CONNECT vuelve a pasar por la autoridad; no se sigue silenciosamente en el
  proxy ni se agrega el host a una allowlist.
- La verificación TLS upstream normal de OkHttp permanece habilitada. La hoja
  local efímera conserva SAN exacto por hostname y la CA de sesión heredada.

## Requests

Soportados y probados: `GET`, `HEAD`, `POST`, `PUT`, `PATCH`, `DELETE`,
`OPTIONS`.

- HTTP/1.0 y HTTP/1.1 con CRLF estricto.
- Límites: request line 8 KiB, headers totales 64 KiB, 100 headers y body 16 MiB.
- `Content-Length` válido y único, o `Transfer-Encoding: chunked`; la combinación
  de ambos falla cerrada para evitar request smuggling.
- Chunk truncado, Content-Length inválido, body truncado, línea/header malformado
  y límite excedido producen error explícito antes de upstream.
- Bodies JSON, form-urlencoded, multipart y binarios se preservan byte a byte.
- `Host` y framing son administrados por el cliente HTTP; Cookie, Authorization,
  Origin, Referer, User-Agent, Accept, Accept-Language, Accept-Encoding,
  Content-Type, Range, validators, Cache-Control y Pragma se preservan cuando
  corresponden.
- Se retiran `Connection`, `Proxy-Connection`, `Keep-Alive`, `Transfer-Encoding`,
  `TE`, `Trailer`, `Upgrade`, headers nombrados por `Connection`, y
  `Proxy-Authorization` antes del origin.
- Valores de Cookie, Authorization y bodies no se registran en logs.

## Responses

- Se conserva status/reason real. Se probaron 200, 201, 206, 301, 302, 303,
  304, 307 y 308; los unitarios cubren 204, errores 4xx/5xx y ausencia correcta
  de body.
- `Set-Cookie` múltiple, Location, CSP/CORS/CORP/COEP/COOP,
  Content-Disposition, Vary, ETag, Last-Modified, Range/Content-Range,
  Cache-Control y Expires no se colapsan ni reescriben destructivamente.
- HEAD, 1xx, 204 y 304 nunca reciben body artificial.
- Respuestas no imagen se transmiten por streaming de 32 KiB; no se bufferiza una
  descarga arbitraria completa.
- Cuando no existe longitud conocida, el lado Chrome usa chunking HTTP/1.1
  coherente. Cuando existe, se emite Content-Length exacto.
- OkHttp puede negociar HTTP/2 upstream; el lado local de Chrome permanece
  HTTP/1.1 deliberadamente. No se implementó un stack HTTP/2 casero.
- Redirects no se siguen en OkHttp; Chrome recibe el status y Location reales.
- Imágenes inspeccionadas conservan original para SAFE y reciben placeholder para
  BLOCK/UNKNOWN. Entity validators/encoding inválidos tras transformación se
  retiran y se aplica `no-store` solamente a esa respuesta de imagen.

## Compresión, cache y Range

- Gzip y chunked atravesaron el gate físico y Chrome reconstruyó el body esperado.
- Accept-Encoding y Content-Encoding de contenido general se preservan; 11A no
  fuerza `identity` global ni hace recompression propia.
- Una imagen declarada con Content-Encoding no identity falla conservadoramente a
  UNKNOWN/placeholder hasta que 11B implemente decode/sniffing robusto.
- Range `bytes=10-31` devolvió 206 con 22 bytes y Content-Range coherente.
- ETag volvió a Chrome y `If-None-Match` produjo 304 sin body.
- No se agregó cache propia. La autoridad de cache/contenido queda explícitamente
  fuera de 11A.

## WebSocket y HTTP/3

- `Upgrade`/WebSocket se rechaza explícitamente con 501 antes de upstream. No se
  simula soporte parcial.
- HTTP/3 no se implementa y UDP/443 directo de Chrome sigue bloqueado por 10A.
- Request/response trailers end-to-end y WebSocket quedan como residuales de web
  general; no se anuncian como soportados.

## Tests automáticos

Matriz final ejecutada después de todos los fixes:

```text
:feature-vpn:testDebugUnitTest       PASS
:feature-vpn:compileDebugKotlin      PASS
:feature-vpn:ktlintCheck             PASS
:feature-vpn:lintDebug               PASS
:app-user:testDevDebugUnitTest       PASS
:app-user:compileDevDebugKotlin      PASS
:app-user:lintDevDebug               PASS
:app-user:assembleDevDebug           PASS
git diff --check                     PASS
```

Gradle: `BUILD SUCCESSFUL` en 1m43s, 850 tareas. La cobertura dirigida incluye
métodos/bodies, framing, hop-by-hop, cookies/auth sin logging, status y headers,
redirects, Range/304, streaming, límites, malformed/truncation, timeouts,
destination authority, protect antes de connect y protect=false con cero connect.

Una corrida adicional de ktlint global del source set DEV detectó deuda heredada
de 10B en archivos `ChromeGuard*` no tocados; `feature-vpn:ktlintCheck` y todos los
gates obligatorios del ticket pasaron. No se alteró esa deuda ajena.

## APK

- Package: `com.contentfilter.user.dev`.
- Version: `347` / `1.0.1-dev`.
- Archivo: `app-user/build/outputs/apk/dev/debug/app-user-dev-debug.apk`.
- Tamaño: 158,860,613 bytes.
- SHA-256: `b4ccdfbdfaeba66c304804fd231d0f6753b53fcae75a5069c0d245fd5cbec4a3`.
- Instalación: `adb install -r`; no uninstall, `pm clear` ni reset de Chrome.
- La actualización in-place preservó `ceDataInode=1239519` y `resetCount=1`.

## Gate físico A23

### Dispositivo y precondiciones

- Serial: `R58T34V31AE`.
- Modelo: Samsung `SM-A235M`.
- Android 14 / API 34.
- Chrome `151.0.7922.173`.
- Device Owner y Affiliated: sí.
- Accessibility: componente exacto enabled/bound. Android dejó temporalmente
  `accessibility_enabled=0` después del update; se restauró exactamente el valor
  previo `1` antes del gate. Chrome no se probó hasta reacreditar A11y.
- VPN productiva: activa, validada y `bypassable=false` antes y después.

### Iteraciones físicas

1. **DEV345 inválida:** los sockets Java todavía no tenían FD materializado al
   invocar protect; `protect=false`, cero requests válidos. Se corrigió con
   `bind(InetSocketAddress(0)) -> protect -> connect`.
2. **DEV346 funcional:** matriz, público, SAFE y BLOCK pasaron. Los timeouts
   normales de conexiones keep-alive ociosas se contabilizaban como failures.
3. **DEV347 final:** después de auditoría enfocada y test determinista, timeout
   antes del primer byte de un request nuevo cierra la conexión limpiamente;
   timeout de una línea parcial sigue siendo 408/fail-close. La matriz completa y
   más de 20 segundos ociosos terminaron con `failures=0`.

Los primeros intents DEV347 no navegaron porque el equipo estaba en Dozing y el
foco real era NotificationShade. No se contó esa corrida. Se despertó la pantalla
sin bypass de lock y la URL nueva produjo requests reales. Dos broadcasts iniciales
con componente abreviado tampoco fueron entregados; el componente completamente
calificado produjo el STOP verificable. Ningún dato/producto cambió por esos dos
intentos inválidos.

### Fixture semántica final

URL: `https://glosh-photos.test/web11a?nonce=11a_dev347_20260825_0130`.

Resultado publicado por el navegador:

```text
GET PASS             HEAD PASS
POST PASS            PUT PASS
PATCH PASS           DELETE PASS
OPTIONS PASS         FORM PASS
MULTIPART PASS       BINARY PASS
COOKIE PASS          AUTH PASS
REDIRECT301 PASS     REDIRECT302 PASS
REDIRECT303 PASS     REDIRECT307 PASS
REDIRECT308 PASS     GZIP PASS
CHUNKED PASS         RANGE PASS
ETAG PASS            DOWNLOAD PASS
CSP_CORS PASS        LARGE PASS
```

- Request/response final de sesión: 231/231 procesadas; `failures=0`.
- Fixture download: 262,144 bytes.
- Fixture large body: 4,194,304 bytes, byte count conservado.
- La secuencia estrictamente secuencial pasó del recurso previo (01:29:45.091) al
  body de 4 MiB completado (01:29:45.143): <=52 ms, un lower bound aproximado de
  76.9 MiB/s para la fixture local. No reemplaza PERF-14.
- Connections peak: 6; proxy queue rejects: 0.
- Proxy latency p50/p95/p99 final: 1.518 / 6.292 / 46.165 ms.

### Navegación pública y multi-host

- `https://example.com/?glosh11a=dev347_0131`: status 200, upstream HTTP/2,
  318 bytes, streaming chunked coherente.
- DEV346, con el mismo cambio funcional salvo la clasificación de idle timeout,
  probó redirect dinámico `httpbingo.org -> example.com` y requests POST a un host
  de beacon nuevo. Ninguno requirió allowlist de código.
- En DEV347 se observaron hosts dinámicos `example.com`, `httpbingo.org` y
  `www.google.com`; los hosts se registraron sin valores sensibles.

### Protect, transporte, DNS y guard

- Final DEV347: upstream sockets 3; protect success 3; protect failure 0.
- Orden acreditado: socket bind/materializado -> protect true -> connect.
- Full tunnel 10A permaneció RUNNING, único VPN, recursion=0.
- HEV tx/rx y HEV DNS fueron 0 durante el tráfico Chrome autorizado por proxy.
- Chrome direct attempts no se dispararon naturalmente en el smoke final; las
  políticas TCP/443 y UDP/443 heredadas de 10A no se modificaron y sus unitarios
  siguieron verdes.
- Guard 10B usó proceso independiente, sesión/generation nueva y liberó Chrome
  sólo después del heartbeat all-ready. STOP revocó lease y verificó suspensión.

### Regresión GloshIA / superficie

Modelo y política sin cambios:

- `tinyclip-r3-head-hybrid-int8.onnx`;
- SHA-256 `c8b64af8092d3718c58736a511c996d0d443dacf3eaa74620b1e5af439a3cd48`;
- GloshIA R3.1 / `dag-36`.

Canarios frescos DEV347:

- BLOCK Flickr: 77,187 -> 6,303 bytes, cache miss, engine call,
  `model_filter`, placeholder.
- SAFE httpbingo PNG: 8,090 -> 8,090 bytes, cache miss, engine call,
  `model_allow`, original byte-count idéntico.

Final: safe/block/unknown `1/1/0`, engineCalls 2, queueRejects 0, timeouts 0.
Protected Surface registró attachmentCount máximo simultáneo 1, epochs monotónicos,
`rawPresented=false`, stale 0, `captureRequestsSincePresentationReady=0`,
`errorCode3=0`; el marker DEV permaneció OFF por defecto y no hubo grilla visible
reportada en la interacción.

### Memoria y estabilidad

- PSS aproximada pre-final (DEV346 activa): 243,298 KiB.
- PSS DEV347 durante navegación: 230,996 KiB; RSS 311,424 KiB.
- PSS después de rollback: 131,886 KiB; RSS 212,756 KiB.
- No se observó crecimiento no acotado, queue growth ni connection growth.
- Crash/ANR/OOM atribuibles a DEV347: `0/0/0`.
- `ApplicationExitInfo` posterior sólo muestra `PACKAGE UPDATED` para los procesos
  DEV346 anteriores; los crashes históricos de gates 10B están fuera de esta
  versión y fueron distinguidos por timestamp.

## Rollback final

STOP final acreditó:

- Chrome suspended=true antes de desmontar;
- guard lease revocada;
- proxy detenido, cache efímera vacía y CA/policy DEV retiradas;
- HEV/SOCKS inactivos, runtime `ready`;
- owned FD resources 0, UDP associations 0, protected UDP sockets 0;
- rutas `0.0.0.0/0` y `::/0` de 10A ausentes;
- VPN/DNS productivo reconstruido y validado, `bypassable=false`;
- resetCount 1 y `ceDataInode=1239519` preservados;
- Device Owner/Affiliated y Accessibility enabled/bound preservados.

## Riesgos residuales

- 11B debe cerrar sniffing/magic bytes, MIME incorrecto, encoding de imágenes,
  cache/provenance, blob/data/canvas, Service Worker/CacheStorage y autoridad de
  presentación de contenido.
- WebSocket/Upgrade y trailers end-to-end no están soportados; fallan cerrados.
- HTTP/3 no está soportado; Chrome UDP/443 directo continúa bloqueado.
- Brotli no tuvo fixture física específica; 11A preserva framing/headers de
  contenido general pero no implementa un codec Brotli propio.
- Request bodies se bufferizan con límite 16 MiB; responses generales sí se
  streamean. Uploads mayores requieren un ticket posterior de streaming request.
- No se certificaron logins reales de terceros, cuentas personales, downloads
  enormes ni toda la web pública. 11A demuestra la base semántica, no Production.

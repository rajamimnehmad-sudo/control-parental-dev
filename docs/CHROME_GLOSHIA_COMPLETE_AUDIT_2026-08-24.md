# Chrome oficial + GloshIA — auditoría técnica completa

Fecha: 2026-08-24

Estado: **AUDITORÍA READ-ONLY COMPLETADA / CÓDIGO FUNCIONAL NO MODIFICADO**.

## Nota de coordinación y evidencia pendiente

Esta auditoría se cerró mientras `CHROME-VPN-09A-UDP-FIXTURE-ROUNDTRIP-01` seguía ejecutándose en Codex. Su resultado y diff todavía no fueron entregados a ChatGPT y, por lo tanto, **no se asumen**.

La decisión de esta auditoría queda condicionada así:

- si el UDP fixture da PASS y el diff es aceptado por ChatGPT, HEV puede pasar al próximo gate full-tunnel controlado;
- si da BLOCKED/FAILED, la ruta se bifurca por causa exacta (`UDP_RETURN_PATH`, `SOCKS_UDP_ASSOCIATE`, `UDP_PROTECT`, `HEV_UDP`, etc.);
- no se adelanta un PASS de UDP ni se abre default route antes de esa revisión.

## Fuentes usadas

### Código compartido inspeccionado

Repositorio: `rajamimnehmad-sudo/control-parental-dev`.

Main compartido visible: `885663fdd9d03542db154650e7abcacec11523d8`.

Última rama Chrome compartida inspeccionable:

- `work/chrome-photos-real-web-batch-01`;
- evidencia/HEAD histórico: `ec923e6e0a557041a63a6d1c6e5a51c6cb422b47`.

Archivos principales inspeccionados:

- `ChromePhotosHttpsProxy.kt`;
- `ChromePhotosRealUpstream.kt`;
- `ChromePhotosRealResponseSanitizer.kt`;
- `ChromePhotosResourceTransformer.kt`;
- `ChromePhotosRealWebLabConfig.kt`;
- `ChromePhotosEphemeralTls.kt`;
- `ChromePhotosLabPolicyController.kt`;
- `ChromePhotosDataPlaneLabService.kt`;
- `ChromePhotosDataPlaneLease.kt`;
- `ChromePhotosProtectedSurface.kt`;
- `ChromePhotosDataPlaneLabVpnPolicy.kt`;
- `FilterVpnService.kt`;
- `ChromePhotosDataPlaneRuntimeAttestation.kt`;
- tests DEV relacionados.

### Código local revisado por diff pegado en ChatGPT

- `FULL-RESET-BOOTSTRAP-05` final `b47c0d5e...`;
- `FULL-RESET-BOOTSTRAP-05A` final `aecdcd35...`;
- `TRANSPORT-ARCHITECTURE-08B` funcional `05278e7f...`, evidencia `ddfa0cab...`.

### Evidencia local reportada todavía no revisada por diff completo

- `TRANSPORT-ENGINE-FEASIBILITY-09A` final local `87ba1854...`, estado `BLOCKED_PHYSICAL_UDP`;
- UDP fixture posterior: resultado pendiente.

## Veredicto ejecutivo

### Lo ya demostrado

La arquitectura actual ya demostró, en A23 real:

1. Chrome oficial puede quedar cerrado por Device Owner mientras Glosh arma su protección.
2. El estado local de Chrome puede resetearse exactamente una vez durante provisioning, con `resetCount=1` preservado tras sesión, actualización y reboot.
3. Una imagen HTTPS interceptada puede ser entregada a GloshIA R3.1 antes de llegar a Chrome.
4. SAFE puede conservar sus bytes originales.
5. BLOCK/UNKNOWN pueden reemplazarse por placeholder sin entregar el original.
6. La superficie protegida quedó estable en scroll, teclado y rotación, sin grilla visible por defecto.
7. La pérdida dinámica de Accessibility produce fail-close y suspensión de Chrome.
8. El único VPN puede atribuir flows TCP/UDP a UID y distinguir Chrome, Samsung Internet y Google Search.
9. HEV 2.17.1 se construyó para cuatro ABIs, con alineación de 16 KiB, y logró TCP no-Chrome de ida y vuelta.
10. Chrome directo TCP/443 fue descartado físicamente.
11. HEV pasó 200 ciclos start/stop sin crash atribuible.

### Lo que todavía NO está demostrado

No está permitido afirmar todavía:

> “Chrome funciona normal en cualquier web y toda foto visible fue filtrada por GloshIA”.

Faltan cinco autoridades de producto:

1. **Transporte general:** UDP físico y luego full-tunnel seguro.
2. **Semántica web completa:** métodos, bodies, cookies, headers, compresión, ranges, streaming y redirects.
3. **Procedencia de píxeles:** `data:`, `blob:`, canvas, WebGL, SVG inline, bytes en JS/JSON y respuestas sintetizadas por Service Worker.
4. **Supervivencia a process death:** el proceso que libera Chrome no puede ser la única barrera.
5. **Rendimiento de página:** tiempos perceptibles, memoria, batería y temperatura bajo páginas densas.

## Estado de madurez estimado

Separando correctamente los alcances:

- filtrado de imágenes interceptadas en laboratorio: **muy avanzado, aproximadamente 90% DEV**;
- bootstrap/fail-close de Chrome: **avanzado, aproximadamente 85% DEV**;
- motor de transporte general: **aproximadamente 65%, condicionado al UDP pendiente**;
- proxy web general: **aproximadamente 35–45%**;
- autoridad sobre todos los píxeles del renderer: **aproximadamente 30–40%**;
- producto final “Chrome normal + fotos filtradas”: **aproximadamente 65–70% global**.

Estos porcentajes son una lectura de ingeniería, no una métrica formal de entrega.

# Hallazgos P0

## P0-1 — El proxy compartido sigue siendo de laboratorio

`ChromePhotosRealWebLabConfig` mantiene una lista exacta de pocos hosts. `CONNECT` sólo se acepta si el hostname está en esa lista y el puerto es 443. Las rutas del VPN se crean a partir de resoluciones iniciales y se limitan a 32 destinos.

Consecuencia observada físicamente:

- `httpbingo.org` permitido carga;
- `example.com` falla con `connect_not_allowed`;
- no es una caída de Internet, sino un fail-close intencional del laboratorio.

El full-tunnel siguiente debe retirar la dependencia de rutas por host sin abrir bypass.

## P0-2 — La semántica HTTP actual no puede transportar la web normal

El proxy actual:

- sólo admite `GET` y `HEAD`;
- consume los headers de Chrome, pero no los conserva;
- genera un request nuevo con headers fijos;
- elimina cookies, authorization, ranges y validators;
- fuerza `Accept-Encoding: identity`;
- sigue redirects manualmente sólo si el host está allowlisted;
- devuelve un conjunto mínimo de headers;
- fuerza `Cache-Control: no-store`;
- bufferiza cuerpos completos;
- limita bodies a 12 MiB.

Eso rompe o degrada:

- login y sesiones;
- formularios y APIs POST;
- OAuth/WebAuthn/payment flows;
- cookies y `Set-Cookie`;
- CORS, CSP, CORP, COEP y Permissions-Policy;
- downloads y `Content-Disposition`;
- `Range`/206, PDF, audio y video;
- WebSocket/Upgrade;
- respuestas comprimidas;
- validators 304/ETag/Last-Modified;
- streaming y cuerpos grandes.

No corresponde quitar la allowlist y declarar navegación general hasta preservar semántica end-to-end.

## P0-3 — Content-Type no es autoridad suficiente

El transformador compartido decide si analiza una imagen con:

`Content-Type.startsWith("image/")`.

Un servidor puede entregar una imagen con MIME ausente, incorrecto u `application/octet-stream`. Los navegadores aplican algoritmos de sniffing contextual y pueden interpretar un recurso distinto de lo declarado.

Regla de producto requerida:

- header declarado;
- magic bytes;
- decode bounded;
- contexto del request (`Sec-Fetch-Dest`, URL y status);
- ambigüedad => UNKNOWN/placeholder, nunca passthrough de bytes que parecen imagen.

## P0-4 — No todos los píxeles vienen como body HTTP image/*

Chrome puede mostrar contenido visual creado dentro del renderer:

- `data:` embebido en HTML/CSS;
- `blob:`;
- canvas y OffscreenCanvas;
- WebGL;
- SVG inline;
- base64/bytes dentro de JavaScript o JSON;
- WASM que decodifica bytes;
- Service Worker + CacheStorage;
- `new Response(...)` sintetizada;
- frames de video o PDF.

El reset inicial elimina el estado anterior, pero una página nueva puede crear estas fuentes después del bootstrap.

`rawPresented=false` demuestra que Glosh no publicó deliberadamente una captura cruda. No certifica que Chrome no generó píxeles desde canvas/blob/Service Worker.

## P0-5 — La lease acredita salud global, no procedencia por recurso

La lease de presentación verifica:

- build DEV;
- sesión;
- proxy;
- policy;
- VPN;
- heartbeat;
- windowId/epoch/viewport;
- Accessibility en 05A.

Cuando todo está sano, la superficie se vuelve transparente para la página completa. No existe una prueba por imagen/píxel que diga qué recurso produjo cada zona visible.

Esto obliga a separar dos preguntas:

1. “¿La infraestructura está sana?”
2. “¿Todos los píxeles visibles tienen procedencia aprobada?”

La primera está avanzada. La segunda sigue abierta.

## P0-6 — Process death sigue siendo una ventana crítica

El proxy, watchdog, attestation, Accessibility y suspensión de Chrome viven en procesos de Glosh. Un kill abrupto puede evitar `onDestroy()` y cualquier cleanup voluntario.

El VPN se desactiva cuando su FD se cierra por crash, pero eso no suspende por sí mismo Chrome ni elimina contenido ya cargado, BFCache o contenido generado offline por el renderer.

Se requiere una autoridad independiente:

- proceso guard mínimo y separado;
- heartbeat cross-process durable;
- lease corta;
- expiración => `setPackagesSuspended(true)`;
- direct-boot aware;
- retry y verificación;
- siempre-on VPN/lockdown como barrera de red;
- gate kill -9/crash/LMK/force-stop/reboot/update.

## P0-7 — La suspensión necesita backstop, no sólo log

Si `setPackagesSuspended()` devuelve fallos o el estado final no coincide, `markFailClosed()` no puede limitarse a registrar el error.

Requisito:

- verify;
- bounded retry;
- escalation/backstop;
- estado visible de protección degradada;
- nunca emitir heartbeat/release mientras la suspensión no esté acreditada.

## P0-8 — HEV todavía depende del cierre UDP físico

09A demostró:

- build nativo;
- ABI/16 KiB;
- TCP end-to-end;
- SOCKS CONNECT;
- protect-before-connect;
- Chrome TCP443 DROP;
- stress 200 ciclos.

Pero no demostró UDP no-Chrome de ida y vuelta ni Chrome UDP443 físico. El ticket UDP fixture pendiente es la decisión inmediata.

No abrir default route antes de:

- UDP ASSOCIATE real;
- protect UDP;
- respuesta de vuelta al app;
- cleanup resources=0;
- stress de asociaciones UDP.

# Hallazgos P1

## P1-1 — El VPN actual necesita modularización antes del full-tunnel

`FilterVpnService.kt` ya ronda las 1000 líneas y contiene:

- lifecycle;
- Builder/rutas;
- browser allowlist;
- DNS parsing;
- SafeSearch;
- policy evaluation;
- telemetry;
- route preparation;
- reconnect/invalidation;
- cleanup.

Agregar transport, UID, HEV, SOCKS y protected sockets en el mismo archivo elevaría el riesgo de regresión y ANR.

Extracción objetivo:

- `VpnTunnelConfigurator`;
- `VpnPacketDispatcher`;
- `VpnPacketParser`;
- `VpnDnsHandler`;
- `VpnConnectionOwnerResolver`;
- `VpnFlowOwnerCache`;
- `VpnTransportPolicy`;
- `VpnTransportEngine`;
- `HevTransportEngine`;
- `VpnLocalSocks5Server`;
- `VpnProtectedSocketFactory`;
- `VpnNetworkGenerationCoordinator`.

`FilterVpnService` debe quedar como orquestador.

## P1-2 — UID attribution debe salir del read-loop síncrono

El spike 08B llama Binder/ConnectivityManager y PackageManager desde el camino de observación del packet loop. Es suficiente para demostrar viabilidad, no para producción.

Diseño objetivo:

- lookup en primer SYN/primer datagrama;
- queue bounded;
- single-flight por 5-tuple;
- flow cache con TTL + networkGeneration;
- UNKNOWN sensible => DROP;
- invalidación FIN/RST/timeout/reuse/handover/VPN restart;
- ninguna llamada Binder por packet.

## P1-3 — Parser de flows necesita IPv6 real y fragments

El parser diagnóstico:

- soporta IHL IPv4;
- no valida todo `totalLength`;
- no mantiene autoridad para fragments posteriores;
- sólo acepta TCP/UDP inmediatamente después del header IPv6 fijo;
- no recorre extension headers.

Antes de full-tunnel:

- Hop-by-Hop;
- Routing;
- Destination Options;
- Fragment;
- límite de cadena;
- malformed reject;
- NAT64/DNS64;
- generation/handover.

## P1-4 — El proxy upstream debe usar sockets protegidos explícitos

El `DnsForwarder` ya usa el patrón correcto:

`protect(socket) -> connect/send`.

El upstream OkHttp de Chrome no tiene todavía una `SocketFactory` protegida. Bajo full-tunnel no debe depender de que App Usuario quede fuera de la lista VPN.

Requisito:

- `VpnProtectedSocketFactory` para TCP;
- socket protegido antes de connect;
- DNS/resolution controlada;
- `protect=false` => cero conexión;
- no recursion.

## P1-5 — Cache segura todavía no existe como diseño de producto

La decisión cache por SHA evita inferencia repetida, pero igual requiere descargar y hashear el body completo. `no-store` elimina reutilización normal de Chrome.

Cache objetivo:

- clave por URL final + validators + content hash + model/policy/preprocessing generation;
- almacenar sólo SAFE aprobadas o placeholders;
- nunca persistir originales BLOCK/UNKNOWN;
- 304 sólo si el objeto local sigue vigente para la misma generación;
- invalidar en cambio de modelo/threshold/policy;
- bounded storage y TTL;
- Service Worker/CacheStorage tratado por separado.

## P1-6 — Range/206 puede convertirse en bypass

Una imagen parcial no puede clasificarse con seguridad si se entrega antes de reconstruir el recurso completo.

Política recomendada:

- si `Sec-Fetch-Dest=image` o bytes/MIME parecen imagen y llega Range:
  - obtener/reconstruir representación completa de manera bounded;
  - clasificar;
  - SAFE => responder rango coherente del original aprobado;
  - BLOCK/UNKNOWN => placeholder completo o respuesta conservadora;
- para PDF/media no-imagen, preservar Range sin usarlo como vía de imágenes.

## P1-7 — Formatos activos/animados requieren policy propia

- SVG externo puede contener comportamiento activo y referencias externas;
- SVG inline nunca pasa como recurso raster clasificable;
- GIF/WebP/AVIF animados pueden cambiar después del primer frame;
- progressive JPEG y orientación EXIF cambian la presentación;
- CMYK, ICC, alpha y dimensiones extremas pueden alterar decode/memoria.

Requisito:

- rasterización sandbox/bounded para SVG externo o placeholder;
- muestreo/todos los frames según límites;
- decode bombs/dimensions bounded;
- orientación normalizada antes de GloshIA;
- UNKNOWN fail-closed.

## P1-8 — La CA efímera es correcta como concepto, pero falta hardening

Fortalezas actuales:

- CA por sesión;
- leaf SAN exacto;
- claves no persistidas;
- cache bounded;
- uninstall al rollback.

Deudas:

- Java no destruye explícitamente las claves privadas al limpiar la cache;
- cache de 8 leafs es insuficiente para web general;
- generar RSA 2048 por host puede afectar latencia;
- producción debe revisar aislamiento del proceso que guarda la CA;
- merge seguro con otras managed restrictions de Chrome;
- no sobrescribir/rehusar restricciones ajenas.

## P1-9 — El APK nativo no debe distribuir cuatro ABI monolíticas

09A reportó APK universal de aproximadamente 158,8 MB, con aumento cercano a 94 MB.

La App Usuario normal ya filtra a ARM32/ARM64. Para producto:

- AAB con ABI splits;
- no empaquetar x86/x86_64 en distribución de teléfonos;
- revisar qué librerías nativas duplican tamaño;
- SBOM y symbols separados;
- benchmark de instalación/update.

## P1-10 — HEV issue #315 sigue siendo riesgo real

Los 200 ciclos sin crash son evidencia positiva, pero no eliminan un bug raro de teardown/lwIP sin fix causal conocido.

Antes de producto:

- stress más largo;
- active flows durante stop;
- network handover;
- timeouts;
- native tombstones;
- watchdog;
- considerar aislamiento nativo/proceso separado si el riesgo persiste.

# Arquitectura objetivo adelantada

## Capa 1 — Único VPN con autoridad por UID

```text
Android apps/browser flows
          |
          v
      único TUN
          |
  VpnPacketDispatcher
          |
     DNS vs transport
      /          \
Glosh DNS      owner UID
                  |
          VpnTransportPolicy
          /       |        \
       DROP    FORWARD   CHROME SPECIAL
                  |            |
                 HEV       direct external DROP
                  |            |
             local SOCKS   local proxy autorizado
                  |            |
           protected socket    |
                  \____________/
                         |
                    Internet
```

### Reglas

- DNS normal nunca entra a HEV.
- Chrome external direct flows: DROP por defecto, no sólo 443.
- Excepciones sólo explícitas y verificables.
- Chrome autorizado usa proxy local + GloshIA.
- Apps no-Chrome incluidas en el VPN se forwardean normalmente.
- protected upstream sockets salen fuera del VPN.
- UNKNOWN owner en flow sensible: DROP.

## Capa 2 — Proxy web semántico

Piezas propuestas:

- `ProxyConnectAuthority`;
- `HttpRequestParser`;
- `HttpHeaderPolicy`;
- `HttpRequestForwarder`;
- `HttpResponseForwarder`;
- `ImageContentAuthority`;
- `SafeDecisionCache`;
- `ProtectedUpstreamClient`;
- `ProxyMetrics`.

### Request

Soporte mínimo:

- GET, HEAD, POST, OPTIONS;
- luego PUT/PATCH/DELETE;
- bodies fixed/chunked;
- cookies/auth/origin/referer/accept-language/user-agent;
- conditional/range;
- hop-by-hop strip exacto;
- logs sin secretos ni query sensible.

### Response

Preservar:

- Set-Cookie;
- CSP/CSP-Report-Only;
- CORS;
- CORP/COEP;
- Permissions-Policy;
- Referrer-Policy;
- HSTS;
- ETag/Last-Modified/Vary;
- Content-Encoding;
- Content-Disposition;
- Location;
- Range/Content-Range;
- status semantics.

Retirar/controlar:

- hop-by-hop;
- `Alt-Svc` cuando pueda abrir QUIC/direct bypass;
- headers incompatibles con transformación de body;
- Content-Length/Encoding recalculados después de modificar bytes.

## Capa 3 — Autoridad de imagen por bytes

Pipeline:

```text
response body
  -> bounded content decode
  -> MIME + magic + request context
  -> image decoder bounded
  -> normalize orientation/colorspace
  -> format/frame policy
  -> GloshIA
  -> SAFE original / BLOCK placeholder / UNKNOWN placeholder
```

No passthrough de imagen ambigua.

## Capa 4 — Procedencia visual

Clasificar cada origen visual en tres grupos:

### A. Procedencia certificada

- imagen de red interceptada y aprobada;
- browser cache creada únicamente desde respuesta aprobada;
- placeholder Glosh.

Puede mostrarse sin visual fallback.

### B. Procedencia parcialmente certificable

- data URL estática encontrada en HTML/CSS;
- blob creado desde fetch de imagen aprobada;
- CacheStorage de una respuesta aprobada.

Puede requerir ledger/instrumentación adicional; no asumir seguridad por origen.

### C. Procedencia no certificable desde red

- canvas/WebGL;
- inline SVG dinámico;
- bytes en JS/JSON/WASM;
- Service Worker sintetizado;
- video/PDF/local.

Requiere fallback visual o bloqueo de la funcionalidad.

## Capa 5 — Fallback visual selectivo

La auditoría concluye que, manteniendo Chrome oficial y JavaScript normal, es muy probable que haga falta una segunda capa visual.

No volver al overlay full-screen lento anterior.

Diseño adelantado:

1. superficie opaca sólo durante transición no acreditada;
2. captura tras evento relevante;
3. diff de tiles contra frame aprobado;
4. analizar únicamente regiones cambiadas;
5. clasificador rápido/pre-filter;
6. GloshIA sólo en tiles candidatos;
7. liberar regiones/página cuando la decisión es current epoch;
8. stale/revocation estricta;
9. límite de latencia y fail-close.

Primero debe ejecutarse un `PROVENANCE-GAP` fixture que demuestre qué caminos realmente escapan; no implementar todo a ciegas.

## Capa 6 — Guard independiente

Diseño adelantado:

- proceso `:chrome_guard` mínimo;
- device-protected storage;
- heartbeat IPC firmado/nonce/generation;
- deadline corto;
- Chrome suspendido por default;
- sólo lease vigente permite unsuspend;
- siempre-on VPN + lockdown;
- guard verifica proxy/VPN/main process state;
- timeout => suspend + retry;
- boot/package-replaced direct-boot;
- audit logs bounded.

# Rendimiento

## Cuellos actuales

- body completo antes de respuesta;
- `Accept-Encoding: identity`;
- `Cache-Control: no-store`;
- cache de decisión requiere redescarga;
- 8 worker threads y una conexión persistente por worker;
- browser→proxy HTTP/1.1;
- inferencia principalmente serial;
- PSS alto;
- CA leaf RSA por host;
- APK universal grande.

## Benchmark adelantado

Comparar Chrome baseline vs Glosh en A23 y S22.

Escenarios:

- frío tras boot;
- proceso/modelo caliente;
- cache fría/caliente;
- 1/10/30/100 imágenes;
- una CDN/multi-CDN;
- Google Images;
- Wikipedia;
- noticias;
- e-commerce;
- feeds/lazy-load;
- Wi-Fi y datos/NAT64.

Formatos:

- JPEG;
- PNG;
- WebP;
- AVIF;
- GIF;
- SVG;
- progressive/animated.

Medir:

- DNS;
- CONNECT/TLS;
- upstream TTFB/download;
- decompress;
- queue wait;
- decode/preprocessing;
- inference;
- decision/cache;
- browser delivery;
- first image/meaningful paint/page stable;
- CPU/PSS/native heap;
- temperatura;
- batería;
- p50/p95/p99.

## Gates provisionales

Estos objetivos deben calibrarse con baseline, pero sirven para diseño:

- exposición cruda: 0 absoluta;
- stale: 0;
- crash/ANR/OOM: 0;
- Chrome usable sin grilla/flashes;
- SAFE aislada p50 adicional ideal <300 ms en A23;
- SAFE aislada p95 adicional ideal <800 ms;
- página de 30 imágenes: evitar serialización lineal visible;
- batería total del producto bajo el objetivo global <3%/día;
- sin crecimiento lineal de PSS/native/FD.

# Compatibilidad pendiente

## Chrome

- navegación normal;
- tabs múltiples;
- forward/back/BFCache;
- Incognito;
- Custom Tabs;
- downloads/share/open image;
- PDF;
- WebSocket;
- OAuth/passkeys/payments;
- Chrome update/restart/crash;
- captive portal;
- Secure DNS/DoH;
- QUIC/HTTP3;
- Alt-Svc;
- history/cache/site data.

## Web visual

- data/blob;
- canvas/WebGL/OffscreenCanvas;
- inline/external SVG;
- Service Worker/CacheStorage;
- JS/JSON/WASM image bytes;
- animated/progressive formats;
- CSS backgrounds/pseudo-elements;
- iframe same/cross-origin;
- shadow DOM;
- lazy-load/virtualized lists.

## Android/OEM

- API 29–36;
- Samsung A23/S22;
- Xiaomi;
- Motorola;
- Oppo;
- low RAM;
- IPv6/NAT64;
- Wi-Fi↔datos;
- multiwindow/DeX/foldables;
- work profile/multi-user.

# UX y producto

## Provisioning

Debe explicar claramente:

- Chrome se dejará como recién instalado una sola vez;
- se perderán cookies, sesiones, historial y datos locales de Chrome;
- otras apps/cuentas no se borran;
- el reset no se repite salvo migración explícita y consentida.

## Uso normal

Objetivo visible:

- Chrome se ve como Chrome;
- ninguna grilla;
- ningún overlay técnico;
- SAFE aparece normal;
- BLOCK/UNKNOWN muestra placeholder Glosh discreto;
- si la protección no está lista, Chrome no abre y Glosh explica la causa/recuperación.

## Recuperación

Estados humanos:

- Preparando protección;
- Chrome protegido;
- Falta Accessibility;
- VPN recuperándose;
- GloshIA cargando;
- Reintentar/solicitar soporte.

No mostrar CA, epochs, lease o proxy al usuario común.

# Secuencia de tickets preparada

## Inmediato — pendiente del resultado Codex

`CHROME-VPN-09A-UDP-FIXTURE-ROUNDTRIP-01`

No avanzar hasta revisar:

- diff;
- unitarios;
- UDP físico ida/vuelta;
- SOCKS UDP;
- protect;
- cleanup resources=0;
- stress;
- rollback.

## Si UDP PASS

### `CHROME-VPN-FULL-TUNNEL-CONTROLLED-10A`

Objetivo:

- routes 0/0 y ::/0 sólo en gate reversible;
- todos los browsers incluidos conservan Internet;
- UID/policy por flow;
- Chrome direct external DROP;
- proxy upstream protected;
- DNS Glosh intacto;
- handover básico;
- rollback exacto.

No abrir todavía general proxy semantics.

### `CHROME-VPN-PROCESS-DEATH-GUARD-10B`

- guard separado;
- always-on/lockdown;
- kill/crash/LMK;
- suspension verified/retry;
- reboot/update.

### `CHROME-PROXY-WEB-SEMANTICS-11A`

- methods/bodies;
- headers/cookies/security;
- compression;
- ranges;
- redirects;
- caching;
- protected upstream.

### `CHROME-IMAGE-CONTENT-AUTHORITY-11B`

- MIME/magic/decode;
- formats/frames;
- SVG;
- image ranges;
- cache generation;
- decompression bombs.

### `CHROME-GENERAL-WEB-FUNCTIONAL-12`

- Google;
- Wikipedia;
- GitHub;
- login test fixtures;
- POST/cookies;
- multi-CDN;
- tabs/BFCache/incognito/custom tabs;
- no direct bypass.

### `CHROME-PROVENANCE-GAP-13A`

Fixtures:

- data URL;
- blob;
- canvas;
- WebGL;
- inline SVG;
- JS/JSON bytes;
- Service Worker cache/synthetic response;
- PDF/video/local.

### `CHROME-VISUAL-FALLBACK-13B`

Sólo si 13A confirma huecos no cerrables por data-plane/policies.

### `CHROME-GENERAL-WEB-PERF-14`

Matriz amplia p50/p95/p99, memoria, batería y térmica.

### `CHROME-PRODUCT-HARDENING-15`

- UX/consent;
- migration/versioning;
- AAB/ABI splits;
- privacy/logging;
- SBOM/native symbols;
- long-run;
- multi-OEM.

## Si UDP sigue BLOCKED

Ruta exacta según evidencia:

- `BLOCKED_UDP_RETURN_PATH`: auditar respuesta HEV→dispatcher→TUN;
- `BLOCKED_SOCKS_UDP_ASSOCIATE`: corregir handshake/lifecycle/control TCP;
- `BLOCKED_UDP_PROTECT`: socket creation/protect-before-send;
- `BLOCKED_HEV_UDP`: fixture directa HEV/SOCKS y comparar alternativa Outline/tun2socks;
- crash nativo: congelar HEV y evaluar alternativa/aislamiento.

# Trabajo adelantado sin Codex

Quedó preparado en esta auditoría:

1. arquitectura objetivo full-tunnel;
2. modularización del VPN;
3. contrato proxy web general;
4. estrategia MIME/magic/decode;
5. diseño de cache segura;
6. tratamiento Range/animated/SVG;
7. mapa de procedencia visual;
8. diseño de fallback visual selectivo;
9. guard independiente/process death;
10. matriz de rendimiento;
11. matriz de compatibilidad;
12. roadmap condicional después del UDP;
13. Definition of Done de producto.

No se modificó código funcional mientras Codex trabaja sobre UDP.

# Definition of Done — Chrome oficial + GloshIA

No declarar producto listo hasta cumplir simultáneamente:

## Seguridad

- reset inicial único/versionado;
- Chrome suspendido hasta health;
- process death fail-close;
- always-on/lockdown;
- direct Chrome TCP/UDP bypass=0;
- DNS/DoH/DoT controlado;
- SAFE original;
- BLOCK/UNKNOWN original nunca entregado;
- data/blob/canvas/SW cubiertos o bloqueados;
- raw=0;
- stale=0;
- suspensión verificada/retry.

## Compatibilidad

- métodos/bodies/cookies;
- headers/security;
- compression/range;
- redirects/CDN;
- login/OAuth;
- tabs/incognito/custom tabs;
- downloads/PDF policy;
- Android 29–36;
- múltiples OEM.

## Estabilidad

- crash/ANR/OOM=0 en campañas;
- native stress;
- no FD/PSS growth;
- handover/reboot/update;
- recovery automática;
- rollback exacto.

## Rendimiento

- Chrome visualmente normal;
- sin grilla/flashes;
- p50/p95/p99 aceptables contra baseline;
- batería dentro del objetivo;
- temperatura/throttling aceptables;
- AAB/ABI size controlado.

## Operación

- provisioning/consent;
- diagnóstico remoto;
- métricas privadas/sanitizadas;
- soporte/rollback;
- SBOM/licencias;
- evidencia reproducible.

# Conclusión

La arquitectura no está fallando: está atravesando la transición correcta desde un laboratorio de imágenes seguras hacia un navegador real.

El logro central ya está demostrado: GloshIA puede decidir antes de que Chrome reciba una imagen interceptada y el sistema puede cerrar Chrome cuando la protección pierde salud.

Los dos grandes problemas restantes son:

1. convertir el único VPN/proxy en transporte web general sin bypass;
2. cubrir píxeles generados dentro del renderer que no cruzan como imagen HTTP.

El resultado UDP pendiente decide si HEV queda aceptado como motor para el full-tunnel. Después de ese diff, la siguiente acción correcta ya está preparada y no requiere otra auditoría general.
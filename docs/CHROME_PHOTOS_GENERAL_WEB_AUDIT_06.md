# CHROME-PHOTOS-GENERAL-WEB-AUDIT-06

Fecha: 2026-08-23

Estado: **AUDITORÍA READ-ONLY COMPLETADA**.

Veredicto de producto: **NO READY para “Chrome oficial normal filtrado en cualquier página”**.

Este veredicto no revierte `FULL-RESET-BOOTSTRAP-05/05A`: ese lote queda **PASS FINAL DEV** para su alcance comprobado (reset inicial único de Chrome, bloqueo por Device Owner hasta health, GloshIA R3.1 en recursos interceptados, grilla OFF por defecto, fail-close de Accessibility, SAFE byte-idéntica y BLOCK/UNKNOWN como placeholder).

La auditoría separa tres niveles:

- **Comprobado**: deriva del código publicado hasta `work/chrome-photos-real-web-batch-01`, los diffs completos locales de 05/05A y la evidencia física entregada.
- **Inferencia arquitectónica de alta confianza**: deriva directamente de cómo Chrome puede renderizar contenido que no se presenta como un body HTTP `image/*`.
- **Gate físico pendiente**: requiere Mac/A23/S22 para reproducir y medir, sin asumir resultado.

## Resumen ejecutivo

El gran problema de cache previa quedó resuelto mediante el reset completo inicial autorizado. El problema central ya no es la cache ni la retícula: es **autoridad de procedencia de todos los píxeles que Chrome puede mostrar** y, simultáneamente, **preservar la semántica normal de la web**.

La implementación actual es sólida como laboratorio de imágenes públicas controladas, pero todavía no es un proxy web general. La última fuente compartida usa hosts exactos, rutas DNS acotadas, sólo `GET/HEAD`, descarta headers de request y reconstruye respuestas con un conjunto mínimo de headers. Además, la lease de transparencia acredita health global del data-plane, no la procedencia individual de cada recurso/píxel visible.

## Fortalezas confirmadas

1. Reset completo de `com.android.chrome` exactamente una vez, preservado en sesiones, actualización y reboot.
2. Direct Boot guard y suspensión de Chrome por Device Owner antes de liberar navegación.
3. Health compuesto por proxy, policy, VPN, GloshIA y Accessibility.
4. Pérdida post-release de Accessibility: `accessibility_lost`, lease/heartbeat revocados y Chrome suspendido físicamente.
5. Superficie protegida con host único, epochs monotónicos, rechazo stale y retícula diagnóstica OFF por defecto.
6. SAFE entregada byte-idéntica; BLOCK/UNKNOWN entregadas como placeholder en el camino interceptado.
7. CA/leaf efímeros, SAN exacto, claves en memoria y rollback de laboratorio.
8. Intentos TCP/QUIC directos sobre destinos controlados fueron descartados en los gates ejecutados.

## Hallazgos P0 — bloquean web general

### P0-1. El routing sigue siendo de laboratorio, no de Internet general

La fuente compartida define una allowlist exacta de pocos hosts públicos, sólo acepta `CONNECT host:443`, resuelve DNS al inicio y limita las rutas suplementarias a 32 direcciones. Esto no cubre páginas con múltiples CDN, hosts dinámicos, redirects de autenticación, publicidad, fuentes, APIs, imágenes o cambios de IP durante la sesión.

Consecuencia: no corresponde afirmar “cualquier página” hasta reemplazar la autoridad exact-host por una autoridad dinámica y demostrablemente fail-closed para todo Chrome.

### P0-2. El proxy actual rompe la semántica normal de la web

El túnel acepta únicamente `GET` y `HEAD`. Los headers originales del navegador se consumen pero no se reenvían. El upstream sintetiza `Accept`, fuerza `Accept-Encoding: identity`, elimina cookies, authorization, range y condicionales. La respuesta a Chrome conserva básicamente `Content-Type`, `Location`, `Content-Length`, `Cache-Control: no-store`, `nosniff` y `Connection`.

Esto rompe o altera, entre otros:

- formularios y APIs `POST/PUT/PATCH`;
- sesiones, login y cookies (`Cookie`/`Set-Cookie`);
- CORS, CSP, CORP/COEP y otros headers de seguridad;
- WebSocket/Upgrade y streaming;
- downloads y `Content-Disposition`;
- Range/206, PDFs, audio/video y recursos grandes;
- negociación normal por idioma, navegador, compresión y contexto.

Además, si un servidor ignora `Accept-Encoding: identity` y entrega un body no-imagen comprimido, el proxy puede reenviar bytes comprimidos sin conservar `Content-Encoding`.

Consecuencia: antes de un gate web amplio hay que construir compatibilidad HTTP real; probar sitios al azar sobre esta semántica produciría falsos diagnósticos.

### P0-3. Hay una brecha estructural de procedencia de píxeles

GloshIA clasifica bodies que el proxy reconoce como imagen. Chrome también puede producir imágenes sin recibir un body HTTP `image/*` clasificable:

- `data:` embebido en HTML/CSS;
- `blob:` generado en el renderer;
- canvas/WebGL/OffscreenCanvas;
- SVG inline;
- bytes base64 o cifrados dentro de JavaScript/JSON y decodificados localmente;
- Service Worker que devuelve CacheStorage o una `Response` sintetizada;
- frames de video, PDF y contenido local.

El reset inicial elimina caches anteriores, pero no impide que una página nueva instale un Service Worker, genere un blob/canvas o almacene respuestas futuras. La lease actual acredita health global y se vuelve transparente; no conoce la procedencia de cada píxel.

`rawPresented=false` sigue siendo útil como invariante interno: confirma que Glosh no presentó deliberadamente una captura cruda. No es un sensor capaz de certificar que Chrome no generó un píxel desde canvas/blob/Service Worker.

Consecuencia: para mantener Chrome oficial y JavaScript normal, el data-plane necesita un segundo mecanismo de cobertura visual/procedencia. Antes de implementar un detector regional completo debe existir un spike físico específico que reproduzca esta matriz.

### P0-4. Confianza excesiva en `Content-Type`

El transformador decide si clasifica según si el tipo declarado empieza con `image/`. Un body de imagen servido como `application/octet-stream`, sin tipo o dentro de otra representación puede pasar como no-imagen. Los navegadores disponen de algoritmos de sniffing específicos para contexto de imagen; `nosniff` no constituye una garantía universal para imágenes.

Consecuencia: la clasificación debe partir de magic bytes/decode bounded y del contexto de request, no únicamente del header declarado. Ambigüedad debe cerrar como UNKNOWN/placeholder, nunca passthrough.

### P0-5. Process death puede dejar Chrome liberado sin guard vivo

Attestation, watchdog, Accessibility, proxy y el código que suspende Chrome viven en el proceso de Glosh. El servicio del laboratorio retorna `START_NOT_STICKY`. Android puede matar incluso procesos con servicios, especialmente bajo presión de memoria. Un kill abrupto no garantiza que `onDestroy()` ejecute el bloqueo.

Si el proceso muere después de haber liberado Chrome:

- desaparecen overlay/proxy/watchdog;
- Chrome puede seguir `suspended=false` hasta que otro componente reactive el guard;
- nuevas requests pueden fallar por proxy ausente, pero el documento ya cargado, BFCache o contenido generado en renderer pueden seguir visibles/activos.

Consecuencia: se necesita un gate físico `kill -9`/crash/LMK/force-stop y un guard independiente/durable que vuelva a suspender Chrome por expiración de lease aun cuando muera el proceso principal.

### P0-6. Fallo de suspensión no tiene backstop fuerte

`markFailClosed()` intenta suspender Chrome y registra el error si falla, pero continúa el cleanup. Cuando Accessibility es la dependencia perdida, la superficie ya puede no existir. Si `setPackagesSuspended()` falla en ese instante, falta una segunda autoridad que impida el uso de Chrome.

Consecuencia: suspensión debe verificarse, reintentarse de forma bounded y escalar a un backstop seguro. No alcanza con loguear el fallo.

### P0-7. El bloqueo de transportes directos sólo está demostrado en destinos controlados

La VPN agrega rutas host únicamente para las IP resueltas de la matriz (más fixture). Los intentos directos a esas IP se descartan. No hay evidencia de autoridad equivalente sobre cualquier IP/host futuro. La política de proxy de Chrome se aplica dinámicamente y la documentación oficial advierte que tareas ya en curso pueden no verse afectadas.

Consecuencia: el gate general debe probar conexiones preexistentes, cambios DNS/IP, HTTP/3, raw IP, redirecciones, handover de red y destinos no pre-resueltos. Para garantía fuerte, la VPN debe cubrir todo el tráfico de Chrome o mantener una autoridad dinámica completa.

## Hallazgos P1 — producto, compatibilidad y rendimiento

### P1-1. Rendimiento todavía no validado a nivel página

Evidencia anterior de GloshIA en A23:

- inferencia p50/p95/p99 aproximada: 166/377/665 ms;
- decisión p95/p99 aproximada: 548/704 ms;
- inferencia paralela máxima observada: 1;
- queue peak: 2.

El proxy bufferiza el body completo antes de clasificar y sólo después responde a Chrome. Tiene 8 workers, pero una conexión persistente ocupa un worker. Todas las respuestas usan `no-store`, el upstream usa `no-cache`, el cliente es HTTP/1.1 y se fuerza `identity`. Incluso un cache hit de decisión exige volver a descargar y hashear el body.

Riesgo: páginas con 10–100 imágenes nuevas pueden serializar inferencias, aumentar segundos de carga, ancho de banda, CPU, memoria y temperatura.

### P1-2. Cache segura todavía no está diseñada

`Cache-Control: no-store` evita reutilización HTTP normal, pero no impide que scripts usen CacheStorage. A la vez perjudica toda la página, incluso HTML/CSS/JS/SAFE.

Se necesita una cache de producto versionada por modelo/policy/preprocessing, que sólo almacene SAFE aprobadas o placeholders, nunca originales BLOCK/UNKNOWN, y que preserve validators/semántica sin reintroducir el problema de cache previa.

### P1-3. Políticas Chrome no conviven todavía

El controlador se niega a aplicar el proxy si hay otras restricciones administradas. Producción necesitará combinar, no excluir, políticas como Incognito, BFCache, predicción de red, downloads/local content y otras defensas. Incognito está permitido si la política no se establece; BFCache también queda habilitada por defecto.

### P1-4. Superficies y caminos no probados

Faltan gates específicos para:

- Incognito y Custom Tabs;
- descarga/guardar/compartir/abrir imagen;
- PDF y archivos locales `file://`/`content://`;
- Service Worker/CacheStorage posterior al bootstrap;
- `data:`/`blob:`/canvas/WebGL/SVG inline;
- GIF/animated WebP/progressive JPEG/SVG/EXIF/CMYK/alpha;
- MIME ausente/incorrecto y bytes dentro de JSON/JS;
- recents, screenshot, multiwindow, DeX, foldables;
- Chrome update/crash/restart, cambio Wi-Fi↔datos, captive portal;
- Android 11–16 y fabricantes distintos.

### P1-5. Memoria, batería y térmica

Se observaron picos PSS cercanos a 351 MiB y cierre alrededor de 180 MiB en campañas previas, pero no existe benchmark prolongado de batería, CPU, temperatura, throttling ni low-memory kill. Esto debe medirse en A23 y S22, incluyendo páginas densas y sesiones largas.

### P1-6. Estado de bootstrap y consentimiento

La generación vive en preferencias de la app. Si se borran/corrompen datos de Glosh, podría repetirse el reset de Chrome. Producción necesita estado versionado durable, consentimiento explícito, UX de onboarding, migración segura y recuperación sin borrar por error.

## Matriz de rendimiento obligatoria

Comparar, en la misma red y dispositivo, Chrome baseline vs Glosh DEV:

1. Arranque frío: boot/app process muerto → Chrome liberado.
2. Modelo caliente, decisión fría.
3. Decisión/cache caliente.
4. Una imagen SAFE/BLOCK/UNKNOWN por formato.
5. Páginas de 1, 10, 30 y 100 imágenes.
6. Una sola CDN y multi-CDN/lazy-load.
7. Google Images, Wikipedia, noticias, e-commerce y feeds públicos.

Medir al menos 30 repeticiones por escenario y separar:

- DNS;
- CONNECT/TLS browser→proxy;
- TTFB y descarga upstream;
- decode/preprocessing;
- espera de cola;
- inferencia;
- decisión/cache;
- escritura al browser;
- primer píxel/primera imagen significativa y página estable;
- PSS/CPU/temperatura/batería;
- p50/p95/p99.

No fijar objetivos numéricos de producto antes de tener baseline comparable. El gate de seguridad sigue siendo absoluto: exposición cruda 0, stale 0, crash/ANR/OOM 0.

## Secuencia recomendada

1. **CHROME-PHOTOS-PROVENANCE-GAP-07** — spike físico read-only/fixture para data/blob/canvas/SW/MIME/PDF/download/incognito/process-kill. Sin rediseño prematuro.
2. **CHROME-PHOTOS-PROXY-SEMANTICS-08** — preservar métodos, headers, cookies, seguridad, redirects, compresión, streaming y routing dinámico, con clasificación decode-first.
3. **CHROME-PHOTOS-PROCESS-DEATH-GUARD-09** — guard independiente, expiración, verificación/retry de suspensión y gates kill/crash/LMK.
4. **CHROME-PHOTOS-GENERAL-WEB-PERF-10** — matriz amplia y tiempos sólo después de cerrar procedencia/semántica.
5. Con los resultados de 07, decidir si el camino oficial Chrome requiere **fallback visual regional**. No avanzar directamente a una implementación grande sin reproducir primero los huecos.

## Decisión final de auditoría

- `FULL-RESET-BOOTSTRAP-05/05A`: **PASS FINAL DEV**, sin regresión.
- “Chrome oficial normal filtrado en cualquier web”: **BLOCKED / NO READY**.
- Causa principal: el laboratorio protege recursos de imagen interceptados, pero todavía no preserva la web completa ni acredita todos los píxeles que el renderer puede generar.
- Próxima acción: ticket 07 read-only y publicación/preservación de la rama local cuando el usuario autorice push. No merge, PR ni Production.
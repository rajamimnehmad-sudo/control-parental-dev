# EVIDENCE — CHROME-GENERAL-WEB-FUNCTIONAL-12

Fecha: 2026-08-25
Resultado: **BLOCKED — ACCESSIBILITY_MAIN_THREAD_ANR**

## Coordinación y candidato

- Central canónico leído desde `origin/build/glosh-control-center-v2` @
  `b4096f0e`: 11B DEV350 está `PASS FINAL DEV / CHATGPT REVIEWED`, 12 es el
  siguiente gate, Glosh Remote está pausado y no existe otro writer Chrome
  persistente.
- Base verificada contra origin:
  `afcfbe53f09344c47671f3b313929c8dd38d3143`
  (`review/chrome-image-content-authority-11b-dev350-final`).
- Rama: `work/chrome-general-web-functional-12`.
- Worktree:
  `/Users/yejielnehmad/Developer/glosh-chrome-general-web-functional-12`.
- APK ya instalada: DEV350, `1.0.1-dev`; no se recompiló, reinstaló ni cambió
  `versionCode`.
- No se modificaron código, Glosh Central, Chrome data, modelo, thresholds,
  VPN/HEV/DNS, proxy, guard 10B ni Production.

## Precheck A23

- Serial: `R58T34V31AE`.
- Modelo: Samsung `SM-A235M`.
- Android 14 / API 34.
- Chrome: `151.0.7922.173`.
- App Usuario: `versionCode=350`, `versionName=1.0.1-dev`.
- `ceDataInode`: `1239519`.
- `resetCount`: 1.
- Device Owner: `com.contentfilter.user.dev`.
- Accessibility: servicio exacto enabled y bound; crashed services vacío.
- La sesión válida arrancó con full tunnel DEV IPv4/IPv6, proxy READY, guard
  generation 25 con lease vigente y Chrome liberado sólo bajo health sana.

## Validación que sí quedó acreditada

La primera apertura fue descartada porque `uiautomator dump` interrumpió
transitoriamente Accessibility en este Samsung y el guard suspendió Chrome. Se
repitió la sesión desde STOP/START sin volver a usar uiautomator.

### Semántica web controlada

`https://glosh-photos.test/web11a?glosh12=20260825T1541` produjo 24/24 PASS:

- GET, HEAD, POST, PUT, PATCH, DELETE y OPTIONS.
- form, multipart, body binario, cookies y Authorization.
- redirects 301/302/303/307/308.
- gzip, chunked, Range/206, ETag/304, download, CSP/CORS y body grande.
- `failures=0` al cierre de esta fixture.

### Google / Google Images

- Google Search entregó HTML/JS/CSS y recursos multi-host mediante el proxy.
- El toque sobre el primer resultado abrió `es.wikipedia.org`, acreditando un
  salto real desde resultados.
- Google Images atravesó `www.google.com`, `www.gstatic.com`,
  `fonts.gstatic.com`, `content-autofill.googleapis.com`, Google Ads/Tag
  Manager y otros hosts públicos.
- Imágenes inspeccionadas terminaron SAFE byte-idénticas o
  UNKNOWN-placeholder; no hubo raw antes de autoridad. Ejemplos observados:
  WebP 2,070 -> 2,070 SAFE, PNG 4,961 -> 4,961 SAFE y encoded/unsupported ->
  placeholder de 6,303 bytes.

### Wikipedia

- `en.wikipedia.org/wiki/Internet` entregó HTML chunked, CSS, JS y telemetría.
- Scroll produjo recursos lazy desde `upload.wikimedia.org`.
- Imágenes estáticas autorizadas llegaron byte-idénticas; SVG/GIF/ICO o
  contenido ambiguo quedaron UNKNOWN-placeholder.
- Se observaron redirects y requests multi-host, incluidos
  `intake-analytics.wikimedia.org`.

### Seguridad durante el tramo previo al blocker

- `protectFailure=0` y `protectSuccess=31` al STOP.
- Transport status válido previo: recursion=0, ownerTimeouts=0,
  ownerQueueDrops=0, queueDrops=0 y Chrome direct TCP/443 contabilizado como
  DROP.
- `rawPresented=false` en todas las entradas observadas de
  `ChromePhotosSurfaceProbe`.
- Guard 10B mantuvo la lease y terminó suspendiendo Chrome al STOP.

## Blocker real

Android registró un ANR real a las `15:44:12.891`:

```text
am_anr: com.contentfilter.user.dev
Subject: Broadcast of Intent
com.contentfilter.user.chromedataplane.command.STATUS
```

El subject identifica el mensaje que esperaba despacho; el stack del main thread
identifica la causa real:

```text
android.accessibilityservice.IAccessibilityServiceConnection.findAccessibilityNodeInfoByAccessibilityId
android.view.accessibility.AccessibilityNodeInfo.getChild
ProtectorAccessibilityService.browserPageObservation / recursive visit
ProtectorAccessibilityService.onAccessibilityEvent
```

La implementación actual recorre en el main thread hasta `MaxBrowserNodes=500`
y cada `getChild()` puede hacer Binder. Bajo navegación real/eventos intensos el
main quedó bloqueado esperando Accessibility, Android mostró `Content Filter no
responde` y creó el dropbox `data_app_anr@1787683467931.txt.gz`.

Durante el mismo pico el proxy llegó a:

- connections=566, requests=614;
- `proxyQueueRejects=339`;
- `proxyActivePeak=8`;
- failures=493, dominados por rechazos bounded, SSL handshakes cancelados y
  `SocketException` al cancelar/navegar;
- image body admission peak/rejects: 2/1;
- inference queue rejects/timeouts: 0/0.

La cola bounded falló cerrada, pero 339 rechazos y el ANR hacen imposible
declarar navegación general estable. No se siguió con pestañas, BFCache,
incógnito, Custom Tab ni WebSocket después del ANR; hacerlo habría producido
evidencia inválida.

## Decisión de alcance

No es un defecto acotado al HTTP/proxy autorizado para corregir dentro de 12.
La causa vive en la arquitectura de Accessibility y su inspección síncrona del
árbol en el main thread. Corregirla requiere un ticket dedicado de
coalescing/backpressure, trabajo fuera del main thread cuando la API lo permita,
presupuesto temporal/nodos y cancelación/generation; no corresponde improvisar
ese cambio dentro de un gate validation-first.

WebSocket/Upgrade conserva el residual conocido fail-close de 11A y no se volvió
a ejecutar después del blocker. Custom Tab quedó `NOT_RUN` por el ANR, no por
falta de caller instalado.

## Rollback

- STOP procesado correctamente.
- Proxy/CA retirados; cache limpiada.
- Full tunnel DEV cerrado; `transportRuntime=ready`.
- `ownedFdResources=0`, `activeProtectedUdpSockets=0`.
- Rutas `0.0.0.0/0` y `::/0` de 10A retiradas.
- VPN productiva activa sólo con rutas DNS controladas; DNS productivo sano.
- Chrome suspendido/fail-closed; guard revocado.
- El diálogo ANR se cerró con `Esperar`, sin matar ni limpiar la app.
- `ceDataInode=1239519`, `resetCount=1`, Device Owner preservado y
  Accessibility enabled/bound sin crashed services.

## Próximo ticket recomendado

`CHROME-ACCESSIBILITY-EVENT-BACKPRESSURE-12A`: retirar el recorrido Binder
recursivo del camino síncrono de `onAccessibilityEvent`, coalescer tormentas,
aplicar budget temporal/nodos y probar Google/Wikipedia/tabs sin ANR ni pérdida
de la autoridad fail-close. Después se reanuda 12 desde DEV350 o su sucesor.

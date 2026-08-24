# CHROME-PROVENANCE-GAP-13A — matriz de fixtures

Estado: PREPARED / NO EJECUTAR ANTES DE FULL-TUNNEL + 11A/11B FUNCIONALES.

## Propósito

Determinar, con evidencia física y fixtures controladas, qué clases de píxeles visibles en Chrome quedan fuera de la autoridad del data-plane HTTP + GloshIA.

No implementar detector regional completo antes de medir estos huecos.

## Regla de clasificación

Cada caso debe terminar en uno de tres estados:

- `CERTIFIED_BY_DATA_PLANE`: bytes visibles provinieron de recurso interceptado, aprobado y entregado por Glosh.
- `DERIVABLE_FROM_CERTIFIED_BYTES`: renderer transformó bytes ya aprobados pero se necesita ledger/attribution adicional.
- `NOT_CERTIFIABLE_FROM_NETWORK`: el renderer puede producir píxeles sin que el data-plane vea el recurso final; requiere bloqueo o fallback visual.

## Instrumentación requerida

Para cada fixture correlacionar:

- page/session id;
- navigation epoch;
- URL/origin;
- network request id cuando exista;
- response content hash;
- Glosh decision id;
- SAFE/BLOCK/UNKNOWN;
- renderer-visible result;
- screenshot/capture sólo para evidencia DEV;
- raw/stale counters.

No loguear payload sensible.

## Fixtures

### 1. Normal network image

`<img src="/safe.jpg">`

Control positivo:

- SAFE original;
- BLOCK placeholder;
- expected `CERTIFIED_BY_DATA_PLANE`.

### 2. CSS background-image network URL

- external CSS;
- inline style;
- pseudo-element `::before`.

Debe pasar por network image authority si URL externa.

### 3. data: base64 image in HTML

`<img src="data:image/jpeg;base64,...">`

No network image request esperado.

Variantes SAFE/BLOCK.

Resultado probable: `NOT_CERTIFIABLE_FROM_NETWORK` salvo que 11B agregue parser HTML/CSS específico.

### 4. data: in CSS

- `background-image:url(data:...)`;
- stylesheet inline/external.

### 5. blob from approved fetch

```js
const r = await fetch('/safe.jpg');
const b = await r.blob();
img.src = URL.createObjectURL(b);
```

Determinar si puede vincularse de forma robusta al response hash aprobado.

Resultado candidato: `DERIVABLE_FROM_CERTIFIED_BYTES`.

### 6. blob from blocked fetch

Mismo patrón con recurso que Glosh reemplaza.

Debe terminar mostrando placeholder/derivado del placeholder, nunca original bloqueado.

### 7. blob assembled from JS bytes

Crear Blob desde Uint8Array/base64 embebido en script, sin fetch de imagen.

Resultado esperado: `NOT_CERTIFIABLE_FROM_NETWORK`.

### 8. createImageBitmap

- desde Blob aprobado;
- desde bytes JS;
- luego canvas.

### 9. Canvas drawImage from approved image

`ctx.drawImage(img, ...)`

Píxeles derivan de recurso aprobado pero luego pueden combinarse/modificarse.

Clasificar `DERIVABLE` sólo si no se mezcla con fuente no certificada.

### 10. Canvas generated photo-like pixels

Generar/decodificar imagen directamente en canvas sin `<img>` final.

Resultado esperado: `NOT_CERTIFIABLE_FROM_NETWORK`.

### 11. Canvas compositing

Mezclar:

- SAFE aprobada;
- BLOCK placeholder;
- texto/formas;
- bytes no certificados.

Una sola fuente no certificada debe degradar provenance del canvas completo/región afectada.

### 12. OffscreenCanvas + Worker

Construir imagen fuera del main thread y transferir bitmap.

### 13. WebGL texture from network image

- texture SAFE;
- texture BLOCK;
- screenshot result.

### 14. WebGL generated texture

Texture construida desde typed arrays/compute, sin image request.

### 15. inline SVG

- `<svg><image href="data:...">`;
- `<svg>` shapes únicamente;
- filters/masks;
- external href network image.

Separar imagen raster embebida de dibujo vectorial.

### 16. external SVG resource

`<img src="/fixture.svg">`

Puede contener:

- embedded data image;
- external image href;
- script si contexto lo permite;
- filters.

Policy recomendada si 11B no rasteriza/inspecciona de forma bounded: UNKNOWN/placeholder.

### 17. image bytes inside JSON

Fetch JSON con base64; JS decodifica y crea Blob/image.

No Content-Type image.

### 18. image bytes inside JavaScript

Script incluye base64/Uint8Array y genera imagen.

### 19. WASM decode/generate

WASM recibe bytes o genera raster y lo dibuja en canvas.

### 20. Service Worker passthrough

SW intercepta fetch y retorna `fetch(event.request)`.

Debe conservar data-plane authority para el network response.

### 21. Service Worker CacheStorage

Primera visita:

- network SAFE aprobada;
- SW guarda response/cache;
- segunda visita responde desde CacheStorage sin nueva network request.

Determinar si browser cache/SW cache sólo contiene bytes ya aprobados y si puede acreditarse bajo same model/policy generation.

### 22. Service Worker synthetic Response

```js
respondWith(new Response(bytes, {headers:{'Content-Type':'image/png'}}))
```

Bytes provienen de JS/local cache y pueden no cruzar data-plane.

### 23. Service Worker modifies approved response

Leer response SAFE, transformar bytes y devolver nuevo Response.

Aunque origen inicial sea aprobado, resultado final ya no es byte-identical.

### 24. CacheStorage pre/post generation

Cambiar model/policy generation y verificar que una SAFE histórica no conserva autoridad automáticamente.

### 25. BFCache

- abrir fixture;
- navegar afuera;
- volver atrás;
- comprobar si renderer recupera píxeles sin network request y cómo se conserva provenance/epoch.

### 26. Browser HTTP cache

- response SAFE aprobada cacheable;
- reload/hot navigation sin nueva request;
- confirmar que cache fue poblada exclusivamente desde bytes aprobados.

### 27. Incognito

Repetir subset:

- network image;
- data;
- blob;
- canvas;
- Service Worker si permitido.

### 28. iframe same-origin

Cada origen/frame debe mantener attribution correcta.

### 29. iframe cross-origin

Probar network image + canvas dentro del frame.

### 30. Shadow DOM / virtualized list

Confirmar que visibility/rendering no afecta authority.

### 31. lazy-load

Crear 100 imágenes y cargar al scroll.

No debe liberar región antes de decisión current epoch.

### 32. animated GIF/WebP/AVIF

- primer frame SAFE, frame posterior BLOCK-like fixture si posible;
- determinar política de frame sampling.

### 33. progressive JPEG

Confirmar que Chrome no recibe scans parciales antes de clasificación final.

### 34. PDF

PDF con imágenes SAFE/BLOCK.

No declarar resuelto por filtro de fotos normal; identificar superficie aparte.

### 35. video poster vs frames

- poster image puede ser data-plane image;
- frames de video quedan fuera de fotos.

No avanzar video en este frente, sólo clasificar provenance.

### 36. local file/content URI

Chrome abierto a `file:`/content/local downloads cuando sea posible/policy.

Debe quedar bloqueado o bajo autoridad específica; no asumir network filtering.

## Gate de exposición

Para cada BLOCK fixture:

- grabar pantalla a frame-rate suficiente;
- detectar cualquier exposición del original;
- `raw exposure frames = 0` requisito absoluto.

No basta estado final placeholder.

## Decisión después de fixtures

### Si todos los casos visuales relevantes pueden cerrarse por policy/data-plane

No implementar fallback visual regional.

### Si quedan casos `NOT_CERTIFIABLE_FROM_NETWORK`

Crear `CHROME-VISUAL-FALLBACK-13B` limitado a esas superficies.

## Diseño mínimo del posible 13B

No full-screen continuous capture.

- opacidad sólo mientras provenance actual es inválida;
- trigger por navegación/layout/canvas-like mutation signals disponibles;
- tile diff del viewport;
- analizar sólo regiones cambiadas;
- low-cost prefilter;
- GloshIA en candidate regions;
- decision epoch;
- stale reject;
- liberar cuando current;
- bounded FPS/CPU;
- fail-close si deadline se excede.

## Métricas

Por fixture:

- network requests;
- image authority calls;
- provenance classification;
- renderer exposure frames;
- capture regions/tiles si fallback;
- decision latency;
- CPU/PSS;
- stale/raw.

## Criterio de cierre 13A

13A PASS significa que existe un mapa completo y reproducible de qué superficies quedan certificadas y cuáles requieren fallback/bloqueo.

No significa todavía producto listo.
# CHROME-IMAGE-CONTENT-AUTHORITY-11B — diseño preparado

Fecha: 2026-08-24
Estado: **PREPARED / NO EJECUTAR TODAVÍA**
Owner futuro: Protección Android / Codex
Dependencias: `FULL-TUNNEL-CONTROLLED-10A` + `PROXY-WEB-SEMANTICS-11A` deben estar PASS antes de implementar este ticket.

> Este documento adelanta arquitectura y gates. No modifica runtime. El resultado de `CHROME-VPN-09A-UDP-FIXTURE-ROUNDTRIP-01` sigue pendiente de revisión por ChatGPT.

---

## 1. Objetivo

Convertir el filtrado actual basado principalmente en `Content-Type: image/*` en una **autoridad de contenido visual** capaz de decidir de forma conservadora qué bytes pueden entregarse a Chrome.

Propiedad objetivo:

> Si una respuesta HTTP puede terminar renderizando una imagen raster externa en Chrome, sus bytes originales no se entregan hasta que Glosh haya establecido una decisión SAFE para la generación vigente de modelo/policy/preprocessing. BLOCK y UNKNOWN nunca entregan el original.

Este ticket NO cubre todavía píxeles generados íntegramente dentro del renderer (`canvas`, WebGL, inline SVG dinámico, Service Worker sintetizado, `data:`/`blob:` no trazable, video/PDF). Eso se prueba en `CHROME-PROVENANCE-GAP-13A`.

---

## 2. Problema actual

La implementación histórica `ChromePhotosResourceTransformer` clasifica recursos principalmente cuando `Content-Type` comienza con `image/`.

Eso es insuficiente porque navegadores reales toleran servidores que etiquetan mal recursos y aplican sniffing. El estándar WHATWG MIME Sniffing define firmas para JPEG, PNG, GIF, WebP, BMP e ICO, entre otros, precisamente porque `Content-Type` puede no coincidir con el cuerpo.

Casos que deben dejar de pasar inadvertidos:

- imagen con `application/octet-stream`;
- imagen sin Content-Type;
- imagen JPEG/PNG/WebP con MIME incorrecto;
- imagen bajo `Content-Encoding: gzip/br/deflate`;
- imagen servida como `206 Partial Content`;
- imagen con extensión engañosa;
- respuesta que dice `image/*` pero cuyos bytes no decodifican como imagen válida;
- formatos animados o multi-frame;
- SVG externo;
- decode bomb o dimensiones extremas.

---

## 3. Arquitectura propuesta

```text
HttpResponseForwarder
        |
        v
ImageCandidateClassifier
        |
        +--> definitely-not-image ------------> passthrough semántico
        |
        +--> image-or-ambiguous
                    |
                    v
             EncodedBodyReader
                    |
             bounded compressed bytes
                    |
                    v
            ContentDecoder
          gzip/br/deflate/etc
                    |
          bounded decoded bytes
                    |
                    v
       ImageSignatureAuthority
      MIME + magic + context + URL
                    |
                    v
           BoundedImageDecoder
                    |
          metadata/frames/pixels
                    |
                    v
       ImageNormalizationPipeline
     orientation/colorspace/alpha
                    |
                    v
             GloshIA R3.1
          /          |          \
       SAFE        BLOCK       UNKNOWN
        |             |            |
 original approved  placeholder  placeholder
```

Separar estas responsabilidades de `ChromePhotosHttpsProxy` y del VPN.

---

## 4. Candidate classification

Crear una decisión explícita:

```kotlin
sealed interface ImageCandidate {
    data object DefinitelyNotImage
    data class Candidate(val reasons: Set<Reason>)
}
```

### Señales positivas

- `Content-Type` dentro de image MIME group;
- magic bytes de formato conocido;
- `Sec-Fetch-Dest: image`;
- request originado desde `<img>`, CSS image o equivalente cuando el proxy pueda inferirlo;
- extensión típica sólo como señal débil;
- status/headers coherentes con imagen;
- respuesta previa/validator asociada a entrada de cache aprobada.

### Regla conservadora

Si las señales se contradicen y existe evidencia de que los bytes pueden ser imagen:

**Candidate → decode/decide.**

No usar extensión o Content-Type para convertir una ambigüedad en passthrough.

---

## 5. Magic bytes y sniffing

Implementar un detector pequeño y determinístico, inspirado en WHATWG MIME Sniffing, sin intentar copiar todo el navegador.

Mínimo:

- JPEG: `FF D8 FF`;
- PNG: `89 50 4E 47 0D 0A 1A 0A`;
- GIF87a / GIF89a;
- WebP RIFF/WEBP;
- BMP `BM`;
- ICO/CUR;
- AVIF/HEIF mediante ISO-BMFF `ftyp` + brands permitidos;
- SVG sólo por MIME/XML/estructura bounded, nunca por una substring libre.

Para AVIF/HEIF no basta una extensión `.avif`; verificar estructura ISO-BMFF y brand compatible.

Nunca leer bytes fuera de límites para sniffing.

---

## 6. Content-Encoding

El proxy web general debe preservar negociación de compresión. Para analizar una imagen debe distinguir:

1. **representation headers** de HTTP;
2. **encoded body** transferido por upstream;
3. **decoded representation bytes** que se entregan al decoder visual.

Soporte inicial:

- identity;
- gzip;
- deflate;
- br si la stack utilizada lo soporta de forma mantenible.

Reglas:

- límite sobre bytes comprimidos;
- límite separado sobre bytes descomprimidos;
- ratio máximo de expansión;
- timeout de decode;
- error/encoding desconocido en candidato visual => UNKNOWN/placeholder;
- si SAFE y se conserva el body original comprimido, se pueden entregar los bytes encoded originales con headers originales;
- si el cuerpo se transforma, recalcular/remover `Content-Encoding`, `Content-Length`, validators y demás metadata que ya no describa el body.

No forzar `Accept-Encoding: identity` como arquitectura de producto sólo para simplificar la inspección.

---

## 7. Límites anti-bomb

No reutilizar el límite histórico de 12 MiB como única defensa.

Usar límites en varias etapas:

- `maxEncodedBytes`;
- `maxDecodedRepresentationBytes`;
- `maxImageWidth`;
- `maxImageHeight`;
- `maxPixelCount`;
- `maxFrames`;
- `maxTotalDecodedFramePixels`;
- `maxDecodeTimeMs`;
- `maxExpansionRatio`;
- `maxMetadataBytes`.

Los valores deben calibrarse en A23/S22. La arquitectura debe existir antes de fijar números definitivos.

Overflow aritmético en `width * height * channels * frames` => reject/UNKNOWN.

OutOfMemoryError o decoder exception => UNKNOWN/placeholder y métrica; nunca fallback al original.

---

## 8. Normalización antes de GloshIA

La decisión debe ser sobre la imagen que el usuario efectivamente vería, no sobre bytes crudos interpretados arbitrariamente.

Normalizar:

- EXIF orientation;
- mirror/rotation;
- alpha compositing contra fondo definido por policy del modelo;
- colorspace → espacio esperado por R3.1;
- HDR/wide-gamut → transformación bounded al input esperado;
- dimensiones/aspect ratio;
- premultiplied alpha coherente;
- frame selection para animados.

No cambiar el preprocessing R3.1 sin ticket de modelo/calibración. La normalización de formato debe producir exactamente el contrato que hoy espera el preprocessing vigente.

---

## 9. Formatos still

### JPEG

- baseline y progressive;
- EXIF orientation;
- ICC/CMYK si el decoder lo admite;
- truncated JPEG => UNKNOWN;
- no liberar scan progresivo antes de decisión final.

### PNG

- alpha;
- color profile;
- APNG detectado como animated, no still por accidente.

### WebP

- lossy/lossless;
- alpha;
- animated WebP separado.

### AVIF

- still;
- alpha;
- 8/10/12-bit y color transforms según decoder real;
- animated AVIF separado.

### BMP/ICO

Compatibilidad bounded. Si decoder/producto decide no soportarlos, UNKNOWN/placeholder; no passthrough.

---

## 10. Animaciones

GIF, APNG, animated WebP y animated AVIF pueden cambiar visualmente después del primer frame.

No es seguro clasificar sólo frame 0 y entregar toda la animación.

Estrategia de gate inicial:

### Opción A — conservadora recomendada para primer producto

- detectar animación;
- decodificar frames según límite;
- si todos los frames relevantes SAFE => permitir original;
- cualquier BLOCK/UNKNOWN => placeholder estático;
- si excede límites de frames/duración/pixels => UNKNOWN.

### Optimización futura

Sampling adaptativo sólo después de medir false-negative risk con dataset animado. No asumir que N frames representativos equivalen a seguridad.

---

## 11. SVG

SVG externo es un formato activo/vectorial, no un raster convencional. Puede contener:

- shapes;
- texto;
- imágenes embebidas;
- referencias externas;
- filters;
- scripts según contexto/policy del navegador;
- data URLs.

No pasar SVG por TinyCLIP directamente como texto/XML.

Primer diseño seguro:

1. parse XML con parser seguro, DTD/external entities deshabilitadas;
2. límites de nodos/tamaño/profundidad;
3. deshabilitar acceso de red durante rasterización;
4. rasterizar a bitmap en sandbox/bounded renderer;
5. analizar bitmap con GloshIA;
6. SAFE => se puede entregar original únicamente si las referencias externas quedan bajo autoridad del proxy y no existe script/active content no permitido;
7. si no puede probarse esa propiedad => placeholder/UNKNOWN.

Alternativa inicial aceptable: bloquear/reemplazar SVG externo hasta tener rasterizador confiable.

SVG inline queda en `PROVENANCE-GAP-13A` porque no atraviesa necesariamente como recurso independiente.

---

## 12. Range / 206

RFC 9110 define `Range`/`206 Partial Content` sobre la representación seleccionada. Para imágenes, entregar un rango antes de haber autorizado la representación completa puede filtrar bytes originales no clasificados.

Regla:

### Candidato visual sin decisión SAFE cacheada

- no forwardear ciegamente Range como imagen parcial;
- obtener/reconstruir representación completa de forma bounded si es viable;
- validar validators/ETag/Last-Modified;
- clasificar representación completa;
- SAFE => responder al browser el rango solicitado coherente con los bytes aprobados;
- BLOCK/UNKNOWN => responder placeholder coherente, ignorar Range o usar respuesta válida que no revele original.

### Candidato visual con SAFE cacheada válida

Se puede satisfacer Range desde representación aprobada si:

- mismo final URL;
- mismos validators;
- misma content generation;
- misma model/policy/preprocess generation.

### Non-image

Preservar Range normalmente.

Nunca mezclar bytes de dos versiones del recurso al reconstruir rangos.

---

## 13. Status codes

### 200

Flujo normal.

### 204/304

No contienen representación nueva. Para 304 sólo reutilizar objeto SAFE cacheado si sus validators y generaciones coinciden.

Si Chrome tiene un 304 pero Glosh no posee la decisión SAFE correspondiente:

- no asumir SAFE;
- revalidar/fetch full representation o fail-close de ese recurso.

### 206

Política Range anterior.

### 3xx

Cada redirect se procesa bajo authority del proxy. No liberar body visual de redirect ambiguo.

### 4xx/5xx

Si el body es imagen/candidato visual (por ejemplo captcha/error art), también debe pasar por content authority.

---

## 14. Cache segura

Separar:

- `DecisionCache`;
- `ApprovedRepresentationCache`.

### Decision key mínima

```text
finalUrl
+ strong content identity (hash o validators confiables)
+ modelSha
+ policyVersion
+ preprocessingVersion
+ contentAuthorityVersion
```

No cachear una decisión sólo por URL.

### SAFE

Permitido guardar:

- hash;
- metadata;
- validators;
- opcionalmente representation bytes aprobados, bounded.

### BLOCK/UNKNOWN

No persistir los bytes originales como cache operacional salvo evidencia/test explícitamente autorizada fuera del camino de producto.

Puede persistirse:

- hash no reversible;
- decision;
- reason;
- placeholder metadata.

### Invalidación

- modelo nuevo;
- threshold/policy nueva;
- preprocessing nuevo;
- content-authority version nueva;
- validator cambiado;
- URL final/redirect chain incompatible;
- TTL vencido.

---

## 15. Request context útil

Cuando `PROXY-WEB-SEMANTICS-11A` preserve headers, usar señales como:

- `Sec-Fetch-Dest`;
- `Accept`;
- URL/path;
- initiator/origin si está disponible;
- method;
- Range.

No confiar exclusivamente en `Sec-Fetch-Dest`, porque fetch/JS puede obtener bytes de imagen con `dest=empty` y luego crear blob/canvas.

Ese caso debe caer como candidato por magic bytes, o después en provenance gap si los bytes se transforman dentro del renderer.

---

## 16. Privacidad y logs

No registrar:

- cuerpos;
- cookies;
- Authorization;
- query strings sensibles;
- imágenes del usuario;
- URLs completas si contienen tokens.

Métricas sanitizadas:

- formato;
- encoded/decoded size buckets;
- candidate reasons;
- cache hit/miss;
- decision SAFE/BLOCK/UNKNOWN;
- decode/inference latency;
- bomb/limit reason;
- range reconstruction result;
- format/frame counts.

---

## 17. API conceptual

```kotlin
interface ImageContentAuthority {
    suspend fun evaluate(
        request: ProxyRequestContext,
        response: ProxyResponseMetadata,
        body: EncodedBodySource,
        generation: ContentAuthorityGeneration,
    ): ContentAuthorityResult
}

sealed interface ContentAuthorityResult {
    data class PassOriginal(
        val approvedBody: ApprovedRepresentation,
        val metadata: ApprovedMetadata,
    ) : ContentAuthorityResult

    data class Replace(
        val placeholder: ByteArray,
        val reason: DecisionReason,
    ) : ContentAuthorityResult

    data class NotVisual(
        val body: BodySource,
    ) : ContentAuthorityResult
}
```

`NotVisual` sólo puede emitirse cuando la autoridad tiene evidencia suficiente de que la representación no es imagen para el alcance de 11B.

---

## 18. Modularidad sugerida

```text
chrome/content/
  ImageCandidateClassifier.kt
  ImageMagicSniffer.kt
  EncodedBodyLimiter.kt
  HttpContentDecoder.kt
  ImageMetadataProbe.kt
  BoundedImageDecoder.kt
  ImageFramePolicy.kt
  ImageNormalizer.kt
  ImageContentAuthority.kt
  ImageRangeAuthority.kt
  ApprovedRepresentationCache.kt
  ContentAuthorityGeneration.kt
  ContentAuthorityMetrics.kt
```

No colocar todo en `ChromePhotosHttpsProxy.kt`.

El proxy debe orquestar; la content authority decide visual content.

---

## 19. Unit tests obligatorios

### MIME/magic

- correct MIME + correct bytes;
- wrong MIME + JPEG;
- octet-stream + PNG;
- no MIME + WebP;
- image MIME + HTML bytes;
- misleading extension;
- truncated signatures.

### Compression

- gzip image;
- deflate image;
- br image si soportado;
- corrupt compression;
- expansion ratio exceeded;
- encoded/decoded limit.

### Formats

- JPEG baseline/progressive;
- PNG/APNG distinction;
- WebP still/animated;
- AVIF still/animated;
- GIF animation;
- BMP/ICO policy;
- SVG external policy.

### Metadata/bombs

- giant dimensions tiny compressed file;
- arithmetic overflow;
- frame explosion;
- corrupt metadata;
- decoder timeout/exception.

### Range

- first range without cache;
- SAFE full then subsequent range;
- validator changes;
- multipart range;
- 416;
- BLOCK cannot expose requested original bytes.

### Cache

- same content+generation hit;
- model SHA changes => miss;
- policy changes => miss;
- validator changes => miss;
- BLOCK original not persisted.

### Decision

- SAFE byte-identical original;
- BLOCK original absent from output;
- UNKNOWN original absent from output;
- decode failure => UNKNOWN;
- GloshIA failure => UNKNOWN/fail-close.

---

## 20. Fixtures físicos/web

Preparar una fixture HTTP/HTTPS controlada que entregue la misma imagen en variantes:

1. `image/jpeg` correcto;
2. `application/octet-stream`;
3. sin Content-Type;
4. `.txt` pero bytes JPEG;
5. gzip JPEG;
6. br WebP;
7. 206 JPEG por rangos;
8. progressive JPEG;
9. animated GIF;
10. animated WebP;
11. AVIF;
12. SVG externo;
13. huge dimensions/decode bomb sintética segura;
14. corrupt image;
15. 304/ETag revalidation;
16. redirect cross-host hacia imagen.

Para cada una, tener SAFE y BLOCK conocidas cuando aplique.

---

## 21. Gate físico A23

Medir por fixture:

- request/response metadata;
- candidate reason;
- format detected;
- encoded/decoded bytes;
- frame count;
- decode latency;
- inference latency;
- cache state;
- decision;
- bytes entregados a Chrome;
- raw exposure frames;
- stale;
- crash/ANR/OOM.

Criterios absolutos:

- SAFE original byte-identical cuando no hubo transformación HTTP necesaria;
- BLOCK original bytes delivered = 0;
- UNKNOWN original bytes delivered = 0;
- MIME incorrecto no evita análisis;
- compressed body no evita análisis;
- Range no evita análisis;
- animated no evita análisis;
- decoder failure no libera original;
- rawPresented=0;
- stale=0;
- crash/ANR/OOM=0/0/0.

---

## 22. Performance instrumentation

Instrumentar por resource:

```text
candidateClassifyUs
downloadUs
contentDecodeUs
magicProbeUs
imageDecodeUs
normalizeUs
queueUs
inferenceUs
cacheLookupUs
decisionTotalUs
bytesEncoded
bytesDecoded
pixels
frames
```

No optimizar antes de medir.

Este ticket debe alimentar directamente `CHROME-GENERAL-WEB-PERF-14`.

---

## 23. Lo que 11B NO debe resolver

No mezclar aquí:

- full-tunnel;
- UID routing;
- HEV;
- Device Owner provisioning;
- process-death guard;
- canvas/WebGL general;
- inline SVG de documento;
- Service Worker provenance;
- video;
- PDF;
- región visual fallback;
- cambios de modelo/threshold.

---

## 24. Criterio PASS futuro

`CHROME-IMAGE-CONTENT-AUTHORITY-11B` sólo puede quedar PASS si:

1. Content-Type incorrecto/no presente no crea bypass para formatos soportados.
2. Magic/context/decode authority es bounded.
3. Compression soportada no crea bypass.
4. Decompression bombs fail-close.
5. Range/206 no filtra bytes antes de decisión.
6. 304 reutiliza sólo una representación SAFE válida.
7. Animaciones no se aprueban sólo por primer frame.
8. SVG externo tiene política conservadora.
9. SAFE conserva calidad/bytes cuando corresponde.
10. BLOCK/UNKNOWN original delivered = 0.
11. Cache está generation-bound.
12. Cambio de modelo/policy/preprocessing invalida decisiones.
13. Logs no contienen cuerpos/credenciales.
14. raw=0.
15. stale=0.
16. crash/ANR/OOM=0/0/0.
17. tests automáticos PASS.
18. gate físico A23 PASS.
19. rollback no afecta Chrome bootstrap/VPN/GloshIA.

---

## 25. Fuentes normativas/de referencia usadas para el diseño

- WHATWG MIME Sniffing Living Standard — firmas y discrepancia entre `Content-Type` y bytes.
- RFC 9110, sección Range Requests — Range/206/Content-Range sobre la representación seleccionada.
- HTTP `Content-Encoding` — negociación y transformación gzip/deflate/Brotli.
- documentación web de formatos — JPEG/PNG/GIF/WebP/AVIF/APNG y soporte de animación.

La implementación futura debe volver a verificar versiones/API concretas de las librerías Android elegidas antes de escribir código.

---

## 26. Handoff

Este diseño queda listo para convertirse en ticket Codex después de que estén cerrados:

1. UDP actual;
2. full-tunnel 10A;
3. web semantics 11A.

No necesita otra auditoría conceptual general. Antes de ejecutar, ChatGPT debe revisar el estado vigente del repo/Central y fijar base SHA, owner, rutas exactas y gates.
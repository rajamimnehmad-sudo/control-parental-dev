# CHROME-IMAGE-CONTENT-AUTHORITY-11B — Codex draft

Estado: **DRAFT / NO EJECUTAR**

Este archivo existe para ahorrar un ciclo futuro. Antes de usarlo, ChatGPT debe reemplazar BASE_SHA, OWNER, VERSIONCODE y rutas según el repo/Central vigentes y revisar el resultado real de 10A/11A.

---

## Prompt base futuro

```text
Antes de seguir, aplicá las instrucciones actuales del Proyecto Glosh y revisá
Glosh Central / Control Center como coordinación vigente.

TASK:
CHROME-IMAGE-CONTENT-AUTHORITY-11B

Esfuerzo:
ALTO / MUY ALTO según tamaño real del diff de 11A.

Owner:
Protección Android / Codex

Base:
BASE_SHA_TO_BE_FILLED_AFTER_11A_PASS

OBJETIVO

Convertir el filtrado de imágenes del proxy Chrome desde una heurística centrada
en Content-Type a una autoridad conservadora de contenido visual:

response
→ MIME/context/magic
→ content-decoding bounded
→ image decode bounded
→ orientation/colorspace/frame normalization
→ GloshIA vigente
→ SAFE original
→ BLOCK/UNKNOWN placeholder

Propiedad absoluta:

ningún byte de una representación candidata a imagen puede llegar a Chrome
antes de una decisión SAFE válida para la misma generación de contenido/modelo/
policy/preprocessing.

NO cambiar GloshIA, modelo, thresholds ni preprocessing.

ALCANCE

Implementar:

- ImageCandidateClassifier;
- magic sniffing bounded;
- gzip/deflate/Brotli según stack vigente;
- límites encoded/decoded/ratio/dimensiones/pixels/frames/time;
- decode + normalization compatible con preprocessing actual;
- still JPEG/PNG/WebP/AVIF;
- detección y política de GIF/APNG/animated WebP/animated AVIF;
- política conservadora SVG externo;
- Range/206 authority;
- 304/validator handling;
- DecisionCache + ApprovedRepresentationCache generation-bound;
- métricas sanitizadas;
- fixtures y gates físicos.

NO implementar aquí:

- full-tunnel;
- HEV;
- UID routing;
- process-death guard;
- data/blob/canvas/WebGL/Service Worker provenance general;
- video/PDF;
- cambios de modelo;
- Production.

MIME/MAGIC

No confiar sólo en Content-Type.

Reconocer de forma bounded al menos:

JPEG, PNG, GIF, WebP, BMP, ICO/CUR y AVIF/HEIF ISO-BMFF.

Signals:

- Content-Type;
- magic;
- Sec-Fetch-Dest;
- Accept;
- URL sólo como señal débil;
- Range/status/cache identity.

Contradicción con evidencia de imagen => Candidate, nunca passthrough.

CONTENT ENCODING

Preservar semántica de la web general del ticket 11A.

Analizar representation bytes después de Content-Encoding.

Límites separados:

- encoded bytes;
- decoded bytes;
- expansion ratio;
- timeout.

Si SAFE y se conserva body encoded original:
puede reenviarse body original con headers coherentes.

Si se reemplaza body:
recalcular/remover Content-Encoding, Content-Length, validators y metadata
incompatible.

DECODE SAFETY

Agregar límites explícitos:

maxEncodedBytes
maxDecodedRepresentationBytes
maxWidth
maxHeight
maxPixelCount
maxFrames
maxTotalFramePixels
maxDecodeTime
maxExpansionRatio
maxMetadataBytes

Overflow/OOM/exception/timeout => UNKNOWN placeholder.
Nunca original.

NORMALIZATION

Antes de GloshIA:

- EXIF orientation;
- mirror/rotation;
- alpha policy;
- colorspace/HDR conversion al contrato vigente;
- dimensions/aspect;
- frame policy.

No alterar preprocessing/model calibration.

ANIMATION

No aprobar una animación sólo por frame 0.

Para primer gate:

- detectar animación;
- analizar todos los frames dentro de límites;
- cualquier BLOCK/UNKNOWN => placeholder;
- excede límites => UNKNOWN.

No sampling optimista sin evidencia posterior.

SVG

SVG externo:

- parser XML seguro;
- DTD/external entities OFF;
- bounded nodes/depth/bytes;
- sin red durante rasterización;
- rasterizar a bitmap y analizar;
- si no puede demostrarse seguridad de active/external references => placeholder.

SVG inline queda fuera de 11B.

RANGE / 206

Imagen sin decisión SAFE:

NO entregar rango original ciegamente.

Obtener/reconstruir representación completa bounded, validar identity, clasificar.

SAFE => responder rango coherente desde representación aprobada.
BLOCK/UNKNOWN => original bytes 0.

304

Sólo reutilizar una SAFE cacheada con validators + generations compatibles.
Sin SAFE authority => refetch/revalidate o fail-close recurso.

CACHE

Clave de decisión debe incluir:

finalUrl
content identity/hash/validators
modelSha
policyVersion
preprocessingVersion
contentAuthorityVersion

No URL-only cache.

SAFE puede almacenar representación aprobada bounded.
BLOCK/UNKNOWN no debe persistir original en cache operacional.

TESTS

MIME/magic:
- correct;
- wrong MIME;
- octet-stream;
- no MIME;
- misleading extension;
- truncated.

Compression:
- gzip;
- deflate;
- br si aplica;
- corrupt;
- expansion bomb.

Formats:
- baseline/progressive JPEG;
- PNG/APNG;
- still/animated WebP;
- still/animated AVIF;
- GIF;
- SVG external.

Bombs:
- giant dimensions;
- overflow;
- frame explosion;
- decoder timeout.

Range:
- first Range without SAFE cache;
- SAFE then Range;
- validator change;
- multipart/416 según soporte;
- BLOCK original exposure=0.

Cache:
- generation hit;
- model/policy/preprocessing change => miss;
- validator change => miss;
- BLOCK original absent from persistent operational cache.

PHYSICAL FIXTURE A23

Servir la misma SAFE/BLOCK en:

- correct image MIME;
- octet-stream;
- no MIME;
- misleading extension;
- compressed;
- 206;
- progressive;
- animated;
- AVIF;
- SVG;
- corrupt;
- 304/ETag;
- redirect cross-host.

Criterios absolutos:

SAFE original byte-identical cuando corresponde.
BLOCK original bytes delivered=0.
UNKNOWN original bytes delivered=0.
wrong MIME bypass=0.
compression bypass=0.
Range bypass=0.
animation bypass=0.
decode failure passthrough=0.
raw=0.
stale=0.
crash/ANR/OOM=0/0/0.

INSTRUMENTATION

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
encodedBytes
decodedBytes
pixels
frames

MODULARIDAD

No crecer ChromePhotosHttpsProxy como monolito.

Separar piezas conceptuales de:

ImageCandidateClassifier
ImageMagicSniffer
EncodedBodyLimiter
HttpContentDecoder
ImageMetadataProbe
BoundedImageDecoder
ImageFramePolicy
ImageNormalizer
ImageContentAuthority
ImageRangeAuthority
ApprovedRepresentationCache
ContentAuthorityGeneration
ContentAuthorityMetrics

PROHIBICIONES

No push/PR/merge/Production/deploy.
No reset/stash/rebase/force-push/clean masivo.
No tocar DAG/video/DRM/Admin/Supabase/Remote Installer.
No tocar GloshIA model/threshold/preprocessing.
No modificar Glosh Central salvo autorización explícita.

SALIDA

PASS/BLOCKED/FAILED
HEAD inicial/final
branch/worktree/clean
changed files/stat
model/policy/preprocess generations unchanged
format matrix
Range matrix
cache matrix
physical A23 results
raw/stale
crash/ANR/OOM
residual provenance gaps
next recommended ticket

Después STOP.
```

---

## Checklist de ChatGPT antes de emitir el prompt real

- [ ] UDP fixture revisado y cerrado.
- [ ] Full-tunnel 10A PASS FINAL.
- [ ] Web semantics 11A PASS FINAL.
- [ ] Leer repo/Central actual.
- [ ] Confirmar owner de escritura.
- [ ] Fijar base SHA exacto.
- [ ] Fijar worktree/rama únicos.
- [ ] Verificar rutas permitidas contra cambios de 11A.
- [ ] Determinar max DEV versionCode vigente.
- [ ] Revalidar GloshIA model SHA y generations.
- [ ] No superponer con provenance 13A.

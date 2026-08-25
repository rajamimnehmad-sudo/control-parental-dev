# EVIDENCE — CHROME-IMAGE-CONTENT-AUTHORITY-11B-CLOSEOUT-BATCH-01

Fecha: 2026-08-25
Resultado: **PASS técnico Codex; pendiente de revisión final ChatGPT**

## Coordinación y aislamiento

- Repo: `rajamimnehmad-sudo/control-parental-dev`.
- Owner de escritura: Codex / Protección Android.
- Central canónico revisado desde `build/glosh-control-center-v2`: 11A figuraba
  `PASS FINAL DEV / CHATGPT REVIEWED`, 11B era el siguiente ticket y no había otro
  writer en las rutas Chrome necesarias.
- Base verificada contra origin:
  `a733361aec000d60e7766daad1ea1753ce8111eb`
  (`review/chrome-proxy-web-semantics-11a-dev348-final`).
- Funcional 11A alcanzable:
  `9ce65e027ec46c4f763f42fe7dd0361d8880f13c`.
- Rama: `work/chrome-image-content-authority-11b-closeout-01`.
- Worktree:
  `/Users/yejielnehmad/Developer/glosh-chrome-image-content-authority-11b-closeout-01`.
- Commit funcional 11B:
  `82cf8b113cf4328954ba5d8cefe438f42442b8f8`.
- No se modificaron Glosh Central, VPN, HEV, DNS, guard 10B, modelo, ONNX,
  thresholds, preprocessing, política visual, 13A, video ni Production.
- `ChromePhotosHttpsProxy.kt` queda en 612 líneas porque conserva una sola
  responsabilidad cohesionada: aceptar/tunelar conexiones TLS y orquestar un
  exchange HTTP. La nueva detección/formato/admission fue extraída a
  `ChromeImageContentAuthority.kt`; no se agregó esa responsabilidad al proxy.

## Huecos cerrados

1. `Content-Type` dejó de ser autoridad única. Un candidato entra a inspección
   por cualquiera de estas señales:
   `Sec-Fetch-Dest: image`, cualquier `Content-Type: image/*` o magic reconocido.
2. El prefijo de respuestas no-imagen identity se mira con un peek de 512 bytes y
   se repone mediante `SequenceInputStream`; no se pierde, duplica ni reordena.
3. Requests `Sec-Fetch-Dest:image` salen upstream con
   `Accept-Encoding: identity` y sin `Range`, `If-Range`, `If-None-Match` ni
   `If-Modified-Since`. Cookie, Authorization y demás headers end-to-end seguros
   se preservan.
4. El formato canónico se obtiene del contenido, no del header. Se enrutan JPEG,
   PNG, WebP, AVIF/HEIF, GIF, BMP, ICO y SVG; sólo JPEG/PNG/WebP/AVIF estáticos
   pueden llegar al motor. Los demás quedan fail-closed.
5. SVG, GIF, APNG, WebP animado y AVIF sequence detectado quedan UNKNOWN con
   placeholder. El preprocesador Android existente mantiene el segundo control
   `ImageDecoder`, software/sRGB, límites de dimensiones/píxeles y rechazo de
   `isAnimated`/partial decode.
6. Imágenes encoded no identity quedan UNKNOWN. No se agregó decoder gzip/br/zstd
   específico ni bypass paralelo.
7. 206 candidato de imagen y 304 candidato sin cuerpo actual quedan UNKNOWN y
   placeholder; nunca autorizan bytes parciales o cache browser ajena.
8. Cache/in-flight ahora usa identidad de engine/model/policy + generation + MIME
   canónico + SHA-256. Un `clear()` invalida tareas anteriores y una terminación
   tardía no puede poblar ni devolver SAFE de la generación nueva.
9. La admisión de cuerpos de imagen usa dos permits no bloqueantes. Saturación,
   oversize (>12 MiB), malformed, timeout o engine no disponible conservan
   fail-close.
10. Toda imagen inspeccionada mantiene downstream `Cache-Control: no-store` y
    `X-Content-Type-Options: nosniff`; SAFE conserva bytes originales y usa MIME
    canónico, BLOCK/UNKNOWN usan el placeholder.

## P0/P1 adicional de la auditoría

- Se acotó a 512 bytes el scan de brands ISO-BMFF incluso si un box hostil anuncia
  un tamaño que cubre todo el body.
- Se agregó comprobación post-wait de generation en la decisión para impedir que
  un resultado concluido durante `clear()` vuelva como SAFE aunque ya no se cachee.
- La fixture oversized dejó de retener 12 MiB desde la construcción y los genera
  sólo cuando se solicita el caso, evitando memoria permanente del laboratorio.
- No quedó otro P0/P1 conocido dentro del scope HTTP image-authority de 11B tras
  revisar sniffing, MIME confusion, partial/encoded entities, cache/in-flight,
  admission y entity headers.

## Archivos funcionales

- `app-user/build.gradle.kts` — DEV 349.
- `ChromeImageContentAuthority.kt` — señales, magic, canonicalización,
  normalización upstream, replay y admission.
- `ChromePhotosRealResponseSanitizer.kt` — fail-close de candidates y entrega
  SAFE/BLOCK/UNKNOWN.
- `ChromePhotosHttpsProxy.kt` — integra la autoridad antes de streaming.
- `ChromePhotoDecisionSession.kt` — key MIME/generation y stale rejection.
- `ChromePhotosResourceTransformer.kt` — MIME en cache determinista.
- `ChromeImageAuthorityFixture.kt` y `ChromePhotosFixtureOrigin.kt` — fixture
  determinista DEV.
- `ChromePhotosDataPlaneLabService.kt` — métricas/report 11B.
- Tests en `app-user/src/testDev/.../chromedataplane/`.

## Tests y gates automáticos

Comando final:

```text
./gradlew :app-user:testDevDebugUnitTest \
  :app-user:compileDevDebugKotlin \
  :app-user:runKtlintCheckOverDevSourceSet \
  :app-user:runKtlintCheckOverTestDevSourceSet \
  :app-user:lintDevDebug \
  :app-user:assembleDevDebug
```

Resultado final: `BUILD SUCCESSFUL`, 834 tasks, 31 ejecutadas y 803 up-to-date.

- Unitarios DEV: 163 tests, 0 failures, 0 errors, 0 skipped.
- `ChromeImageContentAuthorityTest`: 10/10 PASS.
- `ChromePhotosRealResponseSanitizerTest`: 9/9 PASS.
- `ChromePhotoDecisionSessionTest`: 10/10 PASS.
- `ChromePhotosHttpsProxyConnectionTest`: 8/8 PASS.
- ktlint DEV y testDev tocados: PASS.
- lint DEV: PASS, 0 issues en XML.
- compile/assemble DEV: PASS.
- `git diff --check`: PASS.
- Warnings heredados: defaults de annotations Kotlin en archivos no tocados,
  Firebase deprecated API y native libraries que el packaging no pudo strippear.
  No apareció warning nuevo atribuible a 11B.
- No se tocó `gloshia-visual-core`, por lo que no correspondía ejecutar sus gates.

Cobertura determinista relevante:

- PNG/JPEG/WebP/AVIF magic y formatos fail-closed.
- MIME octet-stream/text/plain/ausente y duplicado/contradictorio.
- image-intent y normalización exacta de headers.
- headers normales no-imagen sin alteración.
- SVG/GIF/APNG/WebP animado/AVIF sequence.
- encoded, 206, 304, oversize, malformed y body-admission saturado.
- cache mismo MIME, aislamiento entre MIME, generation/clear y late completion.
- prefix replay byte-identical.
- SAFE original, BLOCK/UNKNOWN placeholder y headers no-store/nosniff.
- smoke 11A de gzip, chunked, Range/206, ETag/304 y download.

## APK DEV349

- Package: `com.contentfilter.user.dev`.
- versionCode: `349` (máximo DEV real observado antes del cambio: 348).
- versionName: `1.0.1-dev`.
- Ruta:
  `app-user/build/outputs/apk/dev/debug/app-user-dev-debug.apk`.
- Tamaño: `158,876,997` bytes.
- SHA-256:
  `12bc4646722198189b145184a7af96d8c9b97b89c9f81932dc0c66503f6ce99e`.
- minSdk 29, targetSdk 36.
- Instalación A23: `adb install -r`, sin uninstall ni clear data.

## Gate físico A23

### Preservación

- Serial: `R58T34V31AE`.
- Modelo: Samsung `SM-A235M`.
- Android 14 / API 34.
- Chrome: `151.0.7922.173`.
- `ceDataInode` antes/después: `1239519` / `1239519`.
- `resetCount`: 1.
- Device Owner: App Usuario DEV; Affiliated: sí.
- Accessibility: servicio exacto enabled y bound al final.
- Guard 10B: proceso independiente activo y lease vigente durante la sesión;
  Chrome sólo fue liberado bajo lease sana.

### Fixture determinista 11B

URL fresca:
`https://glosh-photos.test/web11b?nonce=11b_dev349_final_20260825_1216`.

Reporte físico final:

```text
NORMALIZATION:PASS
SAFE:PASS
MISLABELED:PASS (3/3)
FAIL_CLOSED:PASS (8/8)
GZIP:PASS
CHUNKED:PASS
RANGE:PASS
ETAG:PASS
DOWNLOAD:PASS
```

Evidencia por caso:

- Normalized PNG: 6,768 -> 6,768, `image/png`, model_allow.
- Mislabeled octet-stream, text/plain y header JPEG sobre PNG: 3/3 bytes SAFE
  originales y MIME canónico `image/png`.
- HTML declarado PNG: 38 -> 6,303, `image_format_unknown`.
- SVG: 55 -> 6,303, `unsupported_svg`.
- GIF: 15 -> 6,303, `unsupported_gif`.
- APNG fixture: 22 -> 6,303, `animated_image`.
- encoded PNG: 6,367 -> 6,303, `encoded_image_unsupported`.
- 206 image: 32 -> 6,303, `partial_image_entity`.
- 304 image: 0 -> 6,303, `image_not_modified_without_current_authority`.
- oversize: 12,582,920 -> 6,303, `image_byte_limit`.
- El fixture verificó físicamente `Accept-Encoding: identity` y ausencia de
  Range/If-Range/validators en la request image-intent.

La primera iteración física del fixture incluía una expectativa sintética de
BLOCK sobre un bitmap sentinel. El modelo R3.1 real lo clasificó `model_allow`;
eso no era un defecto del producto. Se retiró esa expectativa dependiente del
contenido sin tocar modelo/thresholds y el BLOCK se acreditó con el canario
público histórico real descrito abajo.

### SAFE y BLOCK reales frescos

- SAFE: `https://httpbingo.org/image/png`, request nueva de la página con nonce
  `11b_public_dev349_20260825_1217`; 8,090 -> 8,090 bytes,
  cache miss, `model_allow`, original entregado.
- BLOCK:
  `https://farm6.staticflickr.com/5600/15526796846_f43d9eb869_o.jpg?glosh11b_block=dev349_20260825_1219`;
  77,187 -> 6,303 bytes, cache miss, `model_filter`, probabilidad 0.6040119,
  placeholder PNG y original no entregado.
- Status acumulado: safe=9, blocked=1, unknown=8.

### Métricas

- `imageCandidates=18`, `imageMagicCandidates=2`.
- `imageBodyAdmissionPeak=2`, rejects=0.
- inference peak=1, in-flight peak=2, queue peak=1, queue rejects=0,
  timeouts=0.
- proxy p50/p95/p99: 1.735 / 7.214 / 32.053 ms.
- model inference p50/p95/p99: 135.100 / 201.993 / 201.993 ms.
- upstream protected sockets: 5; protect success/failure: 5/0.
- recursion: 0; owner timeouts/queue drops: 0/0.
- Surface: `rawPresented=false`, captureRequests=0,
  `captureRequestsSincePresentationReady=0`, `errorCode3=0`, attachmentCount=1.
  No stale frame ni grid/capture post-ready observado.
- PSS/native heap aproximado: 342,738/84,212 KiB antes, 248,383/58,440
  KiB durante, 158,609/48,056 KiB después; sin crecimiento lineal.
- ApplicationExitInfo nuevo sólo registra `PACKAGE UPDATED` por instalación.
  Logcat de la sesión: 0 crash Java, 0 SIGABRT, 0 SIGSEGV, 0 ANR, 0 OOM.

`failures=6` corresponde a seis `SSLHandshakeException` ambientales de hosts
públicos secundarios que la página de matriz histórica intentó abrir. El runner
determinista quedó completo, SAFE/BLOCK autoritativos pasaron y no hubo entrega
raw/fail-open. Se documenta el incremento; no se altera seguridad para ocultarlo.

La sesión final quedó en routing controlado porque `MY_PACKAGE_REPLACED` había
auto-iniciado el lab antes del START explícito. 11B no reclama una revalidación de
10A; full-tunnel, Chrome direct DROP, DNS/HEV y transport lifecycle no fueron
modificados y ya están cerrados por 10A/11A. La evidencia de 11B acredita el mismo
proxy/protect/guard y no afirma un nuevo gate full-tunnel.

## Rollback

- `STOP` suspendió Chrome y revocó lease.
- Proxy cerrado; decision cache/in-flight final 0; CA y policy DEV retiradas.
- Transporte: `status=inactive`, runtime `READY`, owned FD resources 0,
  active protected UDP 0; peak FD 4.
- VPN/DNS productivo restaurado, bypassable=false y sólo rutas productivas; no
  quedó 0/0 ni ::/0 experimental.
- Chrome final suspendido.
- `resetCount=1`, inode 1239519, DO/Affiliated y Accessibility bound preservados.

## Residuales explícitos

- GIF/BMP/ICO/HEIF/SVG y cualquier animación permanecen fail-closed; no son
  formatos SAFE soportados en 11B.
- Encodings de imagen no identity permanecen UNKNOWN; no se agregó decoding
  gzip/br/zstd.
- `data:`, `blob:`, canvas, WebGL, Service Worker, CacheStorage y píxeles generados
  por JS/JSON/WASM pertenecen a `CHROME-PROVENANCE-GAP-13A`.
- Video/DRM pertenecen a tickets posteriores.
- La campaña exhaustiva de performance/memoria pertenece al ticket 14.

PASS de Codex no equivale a cierre final: ChatGPT debe revisar el diff, archivos
críticos, tests, evidencia, aislamiento y gate físico.

## SNIFF-STREAM-GUARD-02

### Coordinación y alcance

- Follow-up: `CHROME-IMAGE-CONTENT-AUTHORITY-11B-SNIFF-STREAM-GUARD-02`.
- Base remota verificada:
  `4f41f7d298ef080f9613aff930774ae841800a6c`
  (`review/chrome-image-content-authority-11b-dev349-final`).
- Commit funcional DEV350:
  `e7d1bfcac3c818a9a9909300a2faa4b69613b69f`.
- Rama: `work/chrome-image-content-authority-11b-sniff-stream-02`.
- Worktree:
  `/Users/yejielnehmad/Developer/glosh-chrome-image-content-authority-11b-sniff-stream-02`.
- Central canónico confirmó este follow-up como siguiente paso y sin otro writer
  Chrome sobre las rutas. Central no fue modificado.
- El diff funcional queda aislado a `ChromeImageContentAuthority`, tests del
  dataplane, fixture DEV y versionCode. No cambió transformer, modelo, ONNX,
  thresholds, VPN, HEV, DNS, guard 10B, Device Owner ni Accessibility.
- `ChromeImageContentAuthority.kt` queda en 561 líneas porque conserva una única
  responsabilidad cohesionada: clasificación MIME, sniffing/formato y admisión
  de cuerpos antes de cualquier entrega. No se agregó datapath ni transformación.

### Defectos y corrección

1. El prefix peek anterior intentaba llenar hasta 512 bytes. En un upstream lento
   o incremental podía retener una respuesta no-imagen aunque la firma ya fuera
   imposible. El nuevo sniffer es progresivo: MIME explícitamente no-imagen
   consume cero bytes; JPEG se confirma en 3, PNG en 8 y WebP/ISO-BMFF sólo leen
   la firma necesaria. Contenido ambiguo claramente no-imagen se descarta tras
   5 bytes y HTML ambiguo al completar el root (`<html>`, 6 bytes). El límite de
   512 queda sólo como máximo duro para SVG/XML/ISO-BMFF realmente ambiguos.
2. MIME ausente/genérico más `Content-Encoding` no identity antes podía pasar raw
   porque no era posible magic-sniffear bytes codificados. Ahora se convierte en
   candidate y termina UNKNOWN/placeholder. `image/*` e image-intent codificados
   conservan el mismo fail-close. MIME explícito no-imagen (por ejemplo
   `text/html`, JSON, JS, CSS o `text/event-stream`) conserva passthrough y no se
   consume para sniffing, incluso con gzip/br.
3. SVG ya no se identifica por encontrar `<svg` en cualquier punto. Tras BOM y
   whitespace sólo se permiten declaración XML, comments y doctype bounded; el
   primer elemento real debe tener root local `svg`. XHTML, feeds y XML con SVG
   hijo no se clasifican como documento SVG.
4. Todo prefijo consumido se repone mediante `SequenceInputStream`; tests
   byte-identical confirman no drop, duplicate ni reorder.

La fixture 11A histórica declaraba el canario gzip como `text/plain`, MIME que
11B deliberadamente considera ambiguo porque una imagen real mal etiquetada así
ya está dentro de la autoridad. Para que el canario mida la regla correcta de
este follow-up —MIME explícito no-imagen codificado conserva semántica web— se
cambió sólo esa fixture a `text/html`; el body y la validación `gzip-pass` no
cambiaron.

### Regresiones deterministas y gates

- MIME explícito `text/event-stream`, HTML, JSON, CSS y JavaScript: passthrough
  con 0 prefix reads.
- XHTML/XML con SVG inline o hijo: no SVG; root SVG real con XML/comment/doctype:
  SVG y posterior fail-close.
- MIME ausente + JPEG fragmentado de a un byte: candidate tras exactamente 3
  reads y replay completo.
- MIME ausente/octet-stream + texto o HTML incremental: decisión temprana en
  5/6 reads; los streams instrumentados fallan si se intenta seguir hasta 512.
- MIME ausente + gzip: candidate y placeholder; declared image + gzip continúa
  UNKNOWN; HTML + gzip continúa passthrough con 0 reads.
- Prefix replay, formatos PNG/JPEG/WebP/AVIF, mislabeled, SVG/animated/206/304,
  oversize y cache generation/MIME existentes: PASS.
- La prueba 11A de fallo chunked usa ahora `application/json` explícitamente
  no-imagen para seguir llegando al writer y conservar su objetivo original.
- Suite final: 169 tests, 0 failures, 0 errors.
- `testDevDebugUnitTest`, `compileDevDebugKotlin`, ktlint DEV/testDev,
  `lintDevDebug`, `assembleDevDebug`: `BUILD SUCCESSFUL`.
- `git diff --check`: PASS.
- Warnings observados son los heredados ya documentados: Kotlin annotation
  targets/deprecations y native libraries no strippeables; no apareció warning
  nuevo atribuible al follow-up.

### APK y smoke físico DEV350

- versionCode/versionName: `350` / `1.0.1-dev`.
- APK: `app-user/build/outputs/apk/dev/debug/app-user-dev-debug.apk`.
- Tamaño: `158,876,997` bytes.
- SHA-256:
  `9bf9ae397be65cc0c9d11505551d2b9c2b2ac6ff46cdfba817f7366fb7dbcf6e`.
- A23 `SM-A235M`, Android 14/API34, instalación in-place con `adb install -r`.
- Package data inode: `1239519` antes/después; resetCount=1.
- DO/Affiliated preservado; Accessibility exacta enabled/bound.
- Fixture final fresca:
  `https://glosh-photos.test/web11b?nonce=sniff_stream_dev350_final_20260825_1441`.
- Reporte visible y status interno:
  `NORMALIZATION:PASS`, `SAFE:PASS`, mislabeled 3/3 PASS, fail-closed 8/8 PASS,
  `GZIP/CHUNKED/RANGE/ETAG/DOWNLOAD:PASS`.
- SAFE fixture: 6,768 -> 6,768, `model_allow`, original y MIME canónico.
- SVG/GIF/APNG/encoded/206/304/oversize: placeholder, ningún original raw.
- HTTPS normal: `example.com` 200, `text/html`, 318 bytes, streaming chunked.
- Guard 10B: proceso separado PID 22223, lease current generation 23 durante
  operación, stale/wrong-caller rejects 0.
- Proxy: failures=0, queue rejects=0, image admission rejects=0, protect 1/1,
  protectFailure=0. Transporte: recursion=0, owner timeout/queue drops=0.
- Surface: `rawPresented=false`, captureRequests=0,
  captureRequestsSincePresentationReady=0, errorCode3=0; no stale/grid event.
- BLOCK no se reejecutó después del ajuste exclusivamente de fixture. Se hereda
  el BLOCK real DEV349 y además una solicitud real en la primera instalación
  DEV350, con la misma autoridad productiva, volvió a dar 77,187 -> 6,303,
  `model_filter` 0.6040119. Modelo/transformer no fueron tocados.

La primera apertura física fue inválida porque el equipo entró en `Dozing`; se
despertó sin clear/uninstall/reset y se repitió sólo la navegación. Una primera
versión del canario gzip evidenció la ambigüedad `text/plain` descrita arriba; se
alineó la fixture con `text/html`, se reconstruyó la misma DEV350 y la corrida
final completa quedó PASS.

### Rollback y residuales

- STOP verificó suspensión de Chrome, revocación del guard, proxy/CA limpios y
  `phase=stopped rollback=complete cache=cleared`.
- Transporte final `inactive`, runtime `ready`, ownedFdResources=0,
  activeProtectedUdpSockets=0; rutas/VPN/DNS productivos restaurados.
- resetCount=1, inode 1239519, DO/Affiliated y Accessibility enabled/bound.
- Permanecen los residuales ya declarados: no se decodifican gzip/br/zstd de
  candidates ambiguos; `data:`, blob/canvas/WebGL/Service Worker/CacheStorage y
  píxeles generados pertenecen a 13A.
- Resultado: **PASS técnico Codex; pendiente de revisión final ChatGPT**.

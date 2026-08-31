# CHROME-STOCK-DOCUMENT-SELF-SHIELD-20 — COMPATIBILITY-01

## STATUS

PASS técnico/físico, pendiente de revisión final ChatGPT.

Este delta cierra `REAL_WEB_BOOTSTRAP_FAIL_CLOSED_PRE_SELF_READY` sin cambiar la
arquitectura H20, GloshIA R3.1, el modelo ni sus thresholds. Google Imágenes con
la consulta `mujer` fue `BLOCKED_BY_SITE` (`google.com/sorry`) y no se intentó
evadir el control del sitio.

## REFS

- Base funcional: `e1b773db0f1020b69784e1ad9e3d9d2c71c6ee5b`.
- Functional SHA: `6e8a6dab2ac625dcda18b2a5c1a917661a6b4489`.
- Review anterior `576a5443`: evidencia solamente, no base.
- Rama de trabajo: `work/chrome-stock-document-self-shield-20-compat-01`.
- Dispositivo: Samsung SM-A235M, Android 14, Chrome `152.0.7977.64`.
- GloshIA Visual R3.1:
  `c8b64af8092d3718c58736a511c996d0d443dacf3eaa74620b1e5af439a3cd48`.
- APK: DEV409, SHA-256
  `30bcdc1e3e7bb6e6836108843a620177953f16731e6e93a0dca3c323f33a365c`.

## ROOT CAUSE

El diagnóstico one-shot y generation-bound aisló la primera condición fallida:

- Controlled y Google podían alcanzar `SELF_READY`.
- En la primera visita fría a Frávega, el XHR síncrono de `SELF_READY` requería
  una conexión TLS cross-origin nueva hacia el origin de laboratorio.
- Ese primer handshake terminó con `SSLHandshakeException/EOFException` antes
  de enviar el claim.
- El XHR diagnóstico posterior, en una conexión ya adquirida, sí funcionaba.
- La etapa exacta fue `SELF_SHIELD_SEQUENCE / SELF_READY_SEND`.

La causa fue `COLD_CROSS_ORIGIN_SELF_READY_TLS_ACQUISITION`, no CSP, parser,
nonce, protected stylesheet, DOM/CSSOM guards, Shadow DOM, iframe ni static
neutralizer.

## FIX

Las capabilities H20 (`SELF_READY`, trace y diagnóstico) usan ahora paths
same-origin relativos. El proxy Glosh los intercepta localmente antes de
upstream y del ledger de cobertura. El bootstrap resuelve el endpoint relativo
contra la URL exacta del documento sólo para verificar `responseURL`.

Se preservan íntegramente:

- token criptográfico y claim exactos;
- session, policy epoch, navigation/document sequence y lifecycle;
- one-shot/replay defense;
- parser-first curtain y release sólo de la cortina del documento;
- Byte Gate Replace-All;
- `raw BLOCK/UNKNOWN = 0`.

H20 admite `'self'` en `connect-src` para este canal. H19 conserva su origin
fijo. El diagnóstico no concede autoridad y permanece acotado y privacy-safe.

## AUTOMATED VALIDATION

PASS:

- bootstrap/endpoint/transformer/registry focalizados;
- diagnóstico current, one-shot y sin autoridad;
- Media Shield DEV;
- Stock Media Authority;
- Network Visual Delivery Gate;
- Chrome Image Content Authority;
- Gloshia Visual Parity;
- `compileDevDebugKotlin`;
- `lintDevDebug`;
- `assembleDevDebug`;
- ktlint focalizado sobre DEV/testDev y core;
- `git diff --check`.

El `ktlintCheck` global conserva deuda previa fuera del delta en
`UserAnnouncementsScreen.kt`, `PackageChangeReceiver.kt` y
`UserFeedbackViewModel.kt`; no se modificaron esas rutas.

## CONTROLLED A23

Sobre DEV409 final:

- documentos transformados: `2`;
- fail-closed: `0`;
- SELF_READY accepted/release/parser/original-script: `1/1/1/1`;
- Replace-All observado en la carga: `12/12/0`;
- proxyQueueRejects/protectFailure/QUIC/direct TCP: `0/0/0/0`.

El snapshot final mostró layout liberado y únicamente placeholders de auditoría.

## REAL-WEB SMOKE

### Google Imágenes sin consulta

- documento transformado y SELF_READY current;
- layout, buscador y controles navegables;
- placeholders visibles;
- raw visual entregado: `0`.

### Frávega

- SELF_READY/release/parser incrementaron sin diagnóstico ni fail-close;
- header, navegación, categorías y contenido textual utilizables;
- fotografías sustituidas por placeholders;
- contador acumulado al cierre del primer gate: `109/109/0`.

### Mimo

- SELF_READY/release/parser incrementaron sin diagnóstico ni fail-close;
- header, carrusel, texto y scroll utilizables;
- fotografías sustituidas por placeholders;
- contador acumulado: `125/125/0`.

### Google Imágenes `mujer`

- el intento normal terminó en `google.com/sorry`;
- clasificación: `BLOCKED_BY_SITE`;
- no hubo bypass ni reintento evasivo;
- el documento de error quedó fail-close.

## SESSION TERMINAL METRICS

- networkVisualCandidates/replaced/rawDelivered: `224/224/0`;
- rawBlocked/rawUnknown: `0/0`;
- mediaDocumentsTransformed: `7`;
- SELF_READY requests/accepted/rejected: `6/6/0`;
- release/parser continued: `6/6`;
- bootstrap diagnostic accepted/rejected: `0/0` en el gate final;
- documentTransformOutstanding: `0`;
- proxyQueueRejects/protectFailure: `0/0`;
- QUIC/direct TCP bypass: `0/0`;
- crash/ANR/OOM: `0/0/0`.

`failures=159` corresponde a cierres/EOF TLS especulativos acumulados de Chrome;
no produjo autoridad raw, fallo de `protect()`, bypass ni rechazo de cola.

## NO-FLASH EVIDENCE

Grabación externa de 30.087 s, usada sólo como evidencia y nunca como runtime
authority:

- video SHA-256:
  `da71a33671bde9c151ad01c0a7edcc4c1c3fc2956d1d3fa57fef4e0cbf9a3826`;
- sampling analizado: 2 fps;
- `rawUnsafeVisibleFrames=0` a esa resolución temporal;
- se observó curtain/estado neutro, luego layout y placeholders;
- no se afirma una cobertura temporal superior al sampling.

Los screenshots y el video temporales se eliminan después de extraer hashes y
métricas; no forman parte de la autoridad ni del producto.

## HEALTH / ROLLBACK

- Device Owner: preservado;
- Affiliated: preservado;
- Accessibility: habilitada y bound;
- versionCode: `409`;
- `ceDataInode`: `1239519`, preservado update-in-place;
- `stay_on_while_plugged_in`: restaurado a `7`;
- STOP: proxy cleared, CA removed, cache cleared, VPN routes refreshed;
- estado terminal del harness: `Stopped`, active=false, ready=false;
- contadores de runtime reiniciados y outstanding=0.

Chrome queda suspendido por el guard DEV al apagar el runtime, que es el
comportamiento fail-close existente del harness; durante el gate estuvo
`chromeSuspended=false` y fue navegable normalmente.

## RESIDUALS

- La consulta Google Imágenes `mujer` no pudo acreditarse por bloqueo del sitio.
- Este PASS es de factibilidad H20/Replace-All; no declara selective R3.1,
  Product Ready, performance, video/GIF/DRM ni Production.
- El guard DEV suspendiendo Chrome con runtime detenido permanece como conducta
  de seguridad conocida, no como cambio de este delta.

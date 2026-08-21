# CHROME-PHOTOS-PROTECTED-SURFACE-00 — evidencia física A23

Fecha: 2026-08-21

Resultado: **PASS**

Owner de escritura: Protección Android / Codex

Base: `6103a57fb08029f028653b92f649246896e3689c`

Rama/worktree: `work/chrome-photos-protected-surface-00`

## Artefactos de entrada

La reconstrucción de `canonical-v3/**` coincidió exactamente con el README:

- transport SHA-256: `860fb889a5183e67b8f32b9b2c99cb1abc2a2c7df28be57cc8534aab8ef62c45`
- patch SHA-256: `b497f9a0e84b5362d2d13bf529e90ecf5a1ca77e73dfc69e7416373935b08ef6`
- patch final: `62400` bytes
- todos los Git blob SHA y SHA-256 de `physical-gate/**`: PASS exacto

El patch no se aplicó a ciegas. Se comparó contra la base real y se portó sólo la
intención de superficie persistente, captura de ventana, epochs y routing de scroll.

## Causa raíz y corrección

El fallo físico de `versionCode=312` era un caso B real. El
`TYPE_ACCESSIBILITY_OVERLAY` independiente era incluido por Samsung WindowManager en
`AsyncRotationController` y recibía fade-out/fade-in durante la rotación. No era sólo
un error del marcador.

La corrección reemplaza esa ventana independiente por un único
`SurfaceControlViewHost` opaco, publicado con
`AccessibilityService.attachAccessibilityOverlayToWindow()` sobre la ventana de
Chrome. El host:

- se publica sólo después de preparar `FLAG_NOT_TOUCHABLE` y una región táctil vacía;
- queda ligado al árbol de SurfaceControl de Chrome durante la transición;
- mantiene un extent cuadrado y un único attachment lógico;
- conserva el último frame seguro/fail-closed mientras una captura no está disponible;
- no cambia los contratos de epoch, stale result ni captura sin persistencia.

La candidata `317` redujo `surface_marker_missing_frames` de `120` a `9`. La revisión
frame a frame demostró que el buffer azul protegido seguía compuesto, pero el marcador
único anclado en `(8, 8)` quedaba fuera del recorte transformado durante dos breves
tramos de rotación. Ese residual era caso A, de evidencia. La candidata `318` dibuja
una retícula cian DEV de 16 px cada 128 px dentro del mismo buffer protegido. No se
modificó el analizador ni sus thresholds.

## Gates locales finales

PASS:

- `:feature-accessibility:testDebugUnitTest`
- `:feature-accessibility:testReleaseUnitTest`
- regresiones dirigidas de epoch/stale results, selección de ventana, routing de
  `TYPE_VIEW_SCROLLED` y host único
- prueba determinista del extent de host y mensurabilidad de la retícula en replay
- `:feature-accessibility:ktlintCheck`
- `:feature-accessibility:lintDebug`
- `:feature-accessibility:lintRelease`
- `:app-user:compileDevDebugKotlin`
- `:app-user:lintDevDebug`
- `:app-user:assembleDevDebug`

APK final:

- `versionCode=318`, `versionName=1.0.1-dev`
- tamaño: `63017110` bytes
- SHA-256: `f6b20dc84588a7a8262cfcb1ef2a7abd057c29b045ac1b4f475d19af5328cbfd`
- instalación in-place: PASS
- no publicada

## Iteraciones físicas de esta corrección

- `313`: fail-fast; el `AccessibilityService` de Samsung no exponía un `Display`
  mediante el Context heredado.
- `314`: creación local del SCVH, pero publicación fallida antes de armar.
- `315`: `rootSurfaceControl` todavía nulo en la primera fase de publicación.
- `316`: el canal de input local exigía `INTERNAL_SYSTEM_WINDOW` antes de aplicar los
  metadatos no táctiles.
- `317`: host window-attached operativo; replay `0` sentinel / `9` marker, FAIL.
- `318`: retícula del mismo buffer; replay `0` / `0`, PASS.

Las candidatas `313..316` se abortaron antes de grabar al aparecer su fallo dirigido.
Hubo dos sesiones físicas completas/grabadas (`317` y `318`). El resultado `312` era
la sesión heredada que originó esta corrección.

## Sesión física final

- dispositivo: Samsung Galaxy A23 `SM-A235M`, serial `R58T34V31AE`
- Android 14 / API 34
- Chrome `151.0.7922.137`
- fixture centinela servida sólo por `adb reverse`
- gestos: scroll lento, fling, reversa, foco/teclado y portrait → landscape → portrait
- grabación: `/tmp/glosh-chrome-rotation-v318.mp4`
- tamaño de grabación: `5538947` bytes
- video SHA-256: `44812c58b7e4e526cc634e8e15640aec30192238818460095762e0688c88b32c`
- 720×1280, `663` frames, `8.212` fps según OpenCV

Telemetría final de Chrome protegido:

- `attachmentCount`: sólo `1`
- epochs armados: `2..159`, `158` eventos, monotónicos; violaciones: `0`
- commits staged: `6`
- commits stale: `0`
- commits `stage_failed`: `0`
- `rawPresented=true`: `0`
- triggers `TYPE_VIEW_SCROLLED`: `42`
- `layoutUpdates`: `0..1`; no hubo detach/reattach ni disarm durante la sesión
- dos transiciones AsyncRotation: portrait → landscape → portrait
- intentos de captura rate-limited (`errorCode=3`): `8`, siempre fail-closed
- captura posterior a la rotación recuperada: commits epochs `147` y `159`
- teclado posterior a rotación: abierto con el tap atravesando el host
- `dumpsys input`: región táctil del host `Embedded{}` vacía
- crash: `0`; ANR: `0`
- Accessibility: servicio bound/enabled; `Crashed services:{}`

No se guardaron ni transmitieron screenshots de Chrome. La grabación y las capturas de
diagnóstico quedaron sólo como evidencia local temporal.

## Replay anti-flash exacto

Se ejecutó el helper verificado `detect_sentinel_frames.py` con sus valores por
defecto, sin cambiar thresholds:

- `fps`: `8.212`
- `checked_frames`: `663`
- `skip_seconds`: `0.0`
- `sentinel_exposure_frames`: `0`
- `surface_marker_missing_frames`: `0`
- `first_sentinel_failures`: `[]`
- `first_marker_failures`: `[]`
- resultado exacto: **PASS**

## Decisión

**PASS** para `CHROME-PHOTOS-PROTECTED-SURFACE-00` en la matriz física probada.

No se avanzó a `CHROME-PHOTOS-REGION-DETECTOR-01`. Riesgo residual: la evidencia de
video del A23 quedó a `8.212` fps y la compatibilidad física sólo está demostrada en
Android 14 / Chrome 151 sobre este modelo; la retícula es instrumentación DEV y debe
seguir separada de una futura presentación de producto.

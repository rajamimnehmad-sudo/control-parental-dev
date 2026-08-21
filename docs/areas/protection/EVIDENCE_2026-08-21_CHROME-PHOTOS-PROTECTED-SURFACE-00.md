# CHROME-PHOTOS-PROTECTED-SURFACE-00 — evidencia física A23

Fecha: 2026-08-21

Resultado: **FAILED**

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

## Gates locales

PASS:

- `:feature-accessibility:testDebugUnitTest`
- `:feature-accessibility:testReleaseUnitTest`
- regresiones dirigidas de epoch/stale results, selección de ventana, routing de
  `TYPE_VIEW_SCROLLED` y host único
- `:feature-accessibility:ktlintCheck`
- `:feature-accessibility:lintDebug`
- `:feature-accessibility:lintRelease`
- `:app-user:compileDevDebugKotlin`
- `:app-user:lintDevDebug`
- una sola ejecución de `:app-user:assembleDevDebug`

APK candidata única:

- `versionCode=312`, `versionName=1.0.1-dev`
- SHA-256: `09cd917e54b39beab47b943cca35acb449486aba38142b01b7bec524a946d513`
- firma coincidente con la DEV instalada previamente; instalación in-place PASS
- no publicada

## Sesión física

- dispositivo: Samsung Galaxy A23 `SM-A235M`, serial `R58T34V31AE`
- Android 14 / API 34
- Chrome `151.0.7922.137`
- fixture centinela servida sólo por `adb reverse`; sin guardar ni transmitir capturas
- gestos: scroll lento, flings, reversa rápida, teclado y rotación landscape/portrait
- una sola grabación física: 720×1280, 974 frames, 15.687 fps, 62.09 s
- video SHA-256: `e92f869e82d6c7f3dac660a229f521e7fbb0756b64ce1e8226a7bd4c6529a2a7`

Telemetría de la superficie durante Chrome:

- `attachmentCount`: sólo `1`; máximo `1`
- epochs armados: `1..259`, monotónicos
- commits presentados: `6, 19, 118, 121, 199, 230, 247, 248, 256, 259`
- commits stale: `0`
- `rawPresented=true`: `0`
- commits con `underlayChanged=true`: `8`
- triggers `TYPE_VIEW_SCROLLED`: `78`
- `layoutUpdates` máximo: `11`
- capturas exitosas confirmaron Chrome debajo del overlay
- tres capturas devolvieron `errorCode=3` durante cambios de ventana; el último frame
  protegido se mantuvo
- al salir de Chrome hubo un único `phase=disarm reason=chrome_absent`
- crash: `0`; ANR: `0`; servicio general de Accessibility siguió bound/enabled

## Replay anti-flash exacto

Comando oficial ejecutado con los valores por defecto del helper
`detect_sentinel_frames.py`:

- `checked_frames`: `974`
- `sentinel_exposure_frames`: `0`
- `surface_marker_missing_frames`: `120`
- tramo continuo afectado: frames `778..897`, segundos `49.5949..57.1808`
- marcador en ese tramo: `0..90` píxeles, por debajo del mínimo `120`
- resultado exacto: **FAIL**

El tramo coincide con landscape. La grabación redujo/letterboxeó la superficie y el
marcador visible quedó por debajo del umbral; además WindowManager registró fade-out /
fade-in de la ventana de Accessibility durante la rotación. Aunque no apareció ningún
píxel centinela y el overlay volvió visible con el mismo host, el contrato exige cero
frames sin marcador/cobertura demostrada. No se infiere seguridad ni se declara PASS.

## Decisión

**FAILED**. No avanzar a `CHROME-PHOTOS-REGION-DETECTOR-01`.

Siguiente paso mínimo: corregir el gate de rotación de la superficie persistente y/o
la evidencia del marcador para que el replay oficial pueda demostrar continuidad con
el mismo umbral en portrait y landscape; luego repetir los gates automáticos y una
única sesión física nueva bajo ticket/reintento explícito.

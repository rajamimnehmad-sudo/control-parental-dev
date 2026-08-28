# CHROME-VISUAL-PRESENTATION-BARRIER-13B-R2A-R2B-CLOSURE-01

## STATUS

`BLOCKED` — el gate permaneció fail-close, pero `takeScreenshotOfWindow()` no
aportó una frontera determinista de presentación del Canvas de Chrome en
landscape. No se ejecutó la matriz R2B porque la barrera previa no adquirió
autoridad.

## REFS

- Base funcional: `f7d5357bbd0a62bd24c53d6761bada100b7a46a1`.
- R2B authority preservada: `342ff3b2af485a4bc70d7243615a8be97b432ffa`.
- Presentation marker/barrier: `95155ad2`.
- Mapping de screenshot alrededor de system insets: `5921a93b`.
- Recaptura current-generation diagnóstica y acotada: `b815da41`.
- Rama de trabajo: `work/chrome-visual-presentation-barrier-13b-r2a-r2b-closure-01`.
- Coordinación inicial Central: `8a704bb5fce74f1d679cfdb651de3b119bbe012c`.

## DEV / APK

- DEV379 (`versionCode=379`), update-in-place.
- APK SHA-256:
  `72c3dce0336cd63e0c33f89fb2105a9bedb3dc38c09794e373fdbda55e15779a`.
- Tamaño: `159123281` bytes.
- A23: `SM-A235M`, Android 14 / API 34.
- Chrome: `152.0.7977.64` (`versionCode=797706404`).

## ROOT CAUSE AUDIT

El blocker canónico DEV376 había demostrado un crop landscape `1631x316`
completamente neutral (`#202428`) con attestation y capture en la misma
generación. DEV377 añadió un marker generation-bound en la misma transacción
Canvas y lo verificó en el screenshot real antes de planner/inference. DEV378
eliminó una discrepancia real de mapping causada por el inset lateral de
navegación: viewport `2408x1080`, frame Android `2342x1080`, inset izquierdo
`66`.

Después del mapping corregido, el observador RAM-only recorrió el frame completo
y obtuvo `palette=0`, `observedPaletteBounds=null`. Por lo tanto no había marker
desplazado en otra parte del bitmap.

DEV379 probó la última hipótesis temporal admisible sin polling: ante un rechazo
raster current, realizó exactamente una segunda captura de la MISMA generación,
sin redraw ni nueva attestation. El primer análisis completo mantuvo la captura
current durante aproximadamente 1,84 s antes de pedir C2, por lo que C2 no fue
una repetición inmediata ni una reacción a `errorCode=3`.

Resultado en generaciones estables:

| Binding | Capture | Screenshot | Marker | Planner / inference / release |
|---|---:|---|---|---|
| E70/R70 | C5 | `2342x1080`, 92 ms | absent, palette 0 | `0 / 0 / 0` |
| E70/R70 | C6 | `2342x1080`, 90 ms | absent, palette 0 | `0 / 0 / 0` |
| E72/R72 | C7 | `2342x1080`, 73 ms | absent, palette 0 | `0 / 0 / 0` |
| E72/R72 | C8 | `2342x1080`, 86 ms | absent, palette 0 | `0 / 0 / 0` |
| E93/R93 | C9 | `2342x1080`, 65 ms | absent, palette 0 | `0 / 0 / 0` |
| E93/R93 | C10 | `2342x1080`, 78 ms | absent, palette 0 | `0 / 0 / 0` |
| E95/R95 | C11 | `2342x1080`, 77 ms | absent, palette 0 | `0 / 0 / 0` |
| E95/R95 | C12 | `2342x1080`, 89 ms | absent, palette 0 | `0 / 0 / 0` |

Clasificación: `PRESENTATION_COMMIT_UNOBSERVABLE`.

Esto NO atribuye el comportamiento a Chrome, al shield o a occlusion. Demuestra
que las señales actualmente disponibles (`drawCompleted`, rAF, attestation,
opaque commit y screenshot callback) no constituyen una fence observable de que
el renderer/compositor de Chrome haya presentado ese Canvas en el raster
capturado. Repetir más capturas sería polling/temporización arbitraria y está
prohibido por el contrato del ticket y por el flujo de tres intentos.

## PRESENTATION BARRIER CONTRACT

- Marker de 130 bits derivado del binding completo.
- Mismo Canvas y misma transacción que el contenido real.
- Fuera del search envelope y del crop de planner/GloshIA.
- Verificación sobre el bitmap real retornado por Android.
- Marker ausente/stale/corrupto: planner `0`, inference `0`, release `0`.
- Una recaptura current-generation; luego reemplazo state-driven y acotado;
  agotamiento => fail-close.
- Sin sleeps, debounce, retry por error, polling ocupado ni cambio de authority.

## AUTOMATED VALIDATION

PASS (`BUILD SUCCESSFUL`, exit 0):

- `:feature-accessibility:testDebugUnitTest` (incluye R2A, fullBleed,
  presentation barrier/recovery, ownership, WorkCoordinator y matriz R2B).
- `:gloshia-visual-core:testDebugUnitTest --tests GloshiaVisualParityTest`.
- `:app-user:testDevDebugUnitTest` focalizado en fixture/handshake R2A y 11B.
- ktlint feature main/test y app DEV/testDev.
- `:app-user:compileDevDebugKotlin`.
- `:app-user:lintDevDebug`.
- `:app-user:assembleDevDebug`.
- `git diff --check`.

La regresión nueva demuestra:

- primer pre-draw current => una recaptura current;
- segundo pre-draw del mismo binding => un reemplazo de generación;
- repetición acotada => fail-close;
- stale/STOP => ningún trabajo posterior;
- no cambio en R2B release authority.

## PHYSICAL / SECURITY RESULT

- Attestation y capture coincidieron en `contentEpoch` y `regionSequence`.
- `errorCode3=0`.
- `presentationObserved=0`; rechazos sólo `MarkerAbsent`.
- En la sesión válida R2B, delta de planner/inference/release: `0/0/0`.
- `rawPresented=false`; la superficie permaneció protegida.
- Frames full adquiridos/cerrados al STOP: `11/11`; outstanding `0`.
- Crops outstanding `0`; inference outstanding `0`; `workIdle=true`.
- Sin crash, ANR ni OOM nuevos. Las salidas de procesos Chrome registradas en la
  ventana fueron `USER_REQUESTED` por los force-stop del harness o procesos
  aislados `ISOLATED_NOT_NEEDED`, no crashes.

Una corrida previa al gate válido quedó descartada: el harness omitió el
component explícito y luego truncó el último chunk Base64, por lo que el probe
R2B no se armó. Se detuvo y las muestras se recargaron con SHA/tamaño exactos
antes de la corrida válida:

- SAFE: 8090 bytes,
  `541a1ef5373be3dc49fc542fd9a65177b664aec01c8d8608f99e6ec95577d8c1`.
- BLOCK: 146249 bytes,
  `9f0d22f322d06dd08a8a349b628de5136c66ee6ef601d8c9492e0e286120ff94`.

## HEALTH / ROLLBACK

- Data-plane previo al rollback: `failures=0`, `proxyQueueRejects=0`,
  `protectFailure=0`, `quicAttempts=0`, `directTcpAttempts=0`, `recursion=0`.
- Rollback: `status=inactive`, `ownedFdResources=0`,
  `activeProtectedUdpSockets=0`, `transportRuntime=ready`.
- Device Owner y Affiliated preservados.
- Accessibility preservada y bound.
- CE data inode preservado: `1239519`.
- Orientación restaurada a portrait controlado.

## RESIDUAL / NEXT ROUTE

R2B y la web real permanecen cerrados. Para continuar hace falta una decisión
arquitectónica separada que aporte una fence de presentación renderer/compositor
observable sin exposición, o cambie de forma material la relación entre
Protected Surface y captura manteniendo zero-exposure. Eso excede el wiring
local autorizado. No corresponde ampliar marker, agregar rAF, más recapturas ni
temporizadores.

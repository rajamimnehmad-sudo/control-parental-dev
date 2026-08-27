# CHROME-VISUAL-REGION-DISCOVERY-13B-R2A

Fecha: 2026-08-27. Dispositivo: Samsung A23 `SM-A235M`, Android 14/API 34.

## STATUS

**BLOCKED — PHYSICAL INVALIDATION LOOP.** Gate 0 y los gates automáticos pasan,
pero la primera corrida R2A integrada no llega a un resultado `Complete` ni
`Unknown`. El fixture entra en un ciclo renderer/attestation/invalidation que
mantiene el shield correctamente fail-close, pero impide obtener una captura
clasificable. El ticket se detuvo en la primera corrida física válida; no se
probó otro algoritmo ni se modificó código después del hallazgo.

## BASE / FUNCTIONAL / REVIEW

- Base obligatoria: `e4d27df1f4eb6a205635988bfc0592fcc21737cf`.
- Rama dedicada: `work/chrome-visual-region-discovery-13b-r2a`.
- Gate 0 functional: `9f165cd9460fe9de1c9f64209ef6581e4a439f61`.
- Corrección de mapping browser viewport: `7ea7e46f182f080fcb901365dda6b0a9b508d4d8`.
- Functional R2A: `6eee5b65d47d731f0a0fe51d6c2a1ed64cc6cffb`.
- La rama review de cierre es `review/chrome-visual-region-discovery-13b-r2a-triage`;
  su HEAD evidence-only se verifica remotamente en el handoff.
- Los cuatro commits prohibidos por el ticket no son ancestros.

## VERSION / APK

- `versionCode=363`, `versionName=1.0.1-dev`.
- APK final integrada: `159041333` bytes.
- SHA-256: `308be8eabe0a41b0741f6e18d03d432c5cc52c596a0c4a4fab61967db1f149ff`.
- Certificado SHA-256:
  `d51bc0dabd280ce1b0f098ae168eb57758faeba301156cde835737835f8a8832`.
- `adb install -r -d`: `Success`; `ceDataInode=1239519` antes/después.

## MODEL

GloshIA Visual R3.1 sin cambios. Asset SHA-256:
`c8b64af8092d3718c58736a511c996d0d443dacf3eaa74620b1e5af439a3cd48`.
No se modificaron modelo, policy, thresholds, labels, planner regional
canónico ni GloshIA core.

## GATE 0 — EXACT DRAW ORACLE

Gate 0 se ejecutó antes de escribir content-island discovery, tal como exige
el ticket. El oracle DEV aportó `carrier rect`, `draw rect`, source SHA,
dimensiones y mapping ligados al `renderIdentityToken`; no fue expuesto al
planner ni al analyzer R2A.

```text
SAFE portrait:   model_allow  probability=0.03678742
SAFE landscape:  model_allow  probability=0.040970713
BLOCK portrait:  model_filter probability=0.9406611
BLOCK landscape: model_filter probability=0.9509917
```

- Attestation: PASS 4/4.
- `NEVER RELEASE`, `rawPresented=false`, `releaseCurrent=0`.
- Full-frame/crop/inference outstanding: `0/0/0`.
- Crash/ANR/OOM: `0/0/0`.

Fuentes atestadas:

```text
SAFE  8090 bytes, 100x100
SHA-256 541a1ef5373be3dc49fc542fd9a65177b664aec01c8d8608f99e6ec95577d8c1

BLOCK 146249 bytes, 1064x1600
SHA-256 9f0d22f322d06dd08a8a349b628de5136c66ee6ef601d8c9492e0e286120ff94
```

## IMPLEMENTATION REVIEWED BY AUTOMATION

R2A agrega piezas cohesionadas y DEV-only:

- contrato sellado `Complete(regions, discoverySequence, regionSetDigest,
  coverageEvidence)` / `Unknown(reason, residualEvidence)`;
- planner puro screenshot-only con background robusto, flood-fill desde borde,
  componentes, residual, overflow y orden determinista;
- IDs y digest ligados a identidad vigente, geometría ordenada y firmas
  visuales cuantizadas, sin raw bytes;
- oracle post-discovery uno-a-uno con cobertura `>=98%` y candidate area
  `<=1.5x`;
- análisis por región mediante `ChromeVisualRegionAnalyzer` y R3.1 canónico;
- ruta `NEVER RELEASE` con rechazo explícito de release;
- ownership de full frame, pixel buffer y crops bajo `finally`/owners
  idempotentes.

El planner no recibe source SHA, fixture label, expected verdict ni oracle. No
usa grilla, tiles ciegos, centro asumido, DOM/JS, OCR, extensión, nuevo modelo,
OpenCV ni Accessibility text/contentDescription. `CarrierHint` conserva sólo
metadata técnica y no participa de la corrida física bloqueada.

`ChromeVisualShieldController.kt` queda en 664 líneas. Sigue unido únicamente
como seam de lifecycle/authority; planner, work processor, estado LAB, oracle
y contrato están separados y no se agregó otra responsabilidad amplia al
controlador.

## GEOMETRY MATRIX AUTOMATED

PASS:

- centered SAFE/BLOCK portrait y landscape;
- off-center izquierda/derecha;
- dos regiones separadas sin merge;
- contenido con detalles/controles finos alrededor;
- full-bleed texturado;
- orden, IDs y digest deterministas;
- oracle one-to-one, coverage y area cap.

FAIL-CLOSE esperado y PASS:

- gradiente/background ambiguo;
- componente cortado;
- overlap;
- residual significativo;
- overflow de regiones;
- identidad stale;
- cancelación.

Authority tests prueban que `Complete`, `Unknown`, stale y mismatch permanecen
protegidos y nunca incrementan release.

## VALIDATION AUTOMATED

PASS, exit `0`:

- `ChromeVisualShieldRegionDiscovery*`;
- `ChromeWindowCaptureOwnershipTest`;
- `ChromeVisualShield*` relevantes;
- `ChromeVisualRegionAnalyzer*`;
- `GloshiaVisualParityTest`;
- fixture/attestation DEV;
- regresiones focalizadas 11B (`ChromePhotos*`, image content authority y
  photo decision session);
- `feature-accessibility:ktlintCheck`;
- `compileDevDebugKotlin`;
- `lintDevDebug`;
- `assembleDevDebug`;
- `git diff --check`.

El ktlint completo de `app-user` continúa reportando únicamente deuda
preexistente en archivos no tocados de 11B/guard; ninguna ruta R2A aparece en
el reporte. No se reformateó fuera de alcance.

## PHYSICAL BLOCKER

Preflight integrado:

- A23/Android/Chrome correctos (`Chrome 152.0.7977.64`);
- DO/Affiliated y Accessibility presentes;
- data-plane `PresentationReady`, `ready=true`;
- samples cargadas y hashes exactos;
- Chrome visible y window exacta disponible.

El primer comando previo a abrir Chrome devolvió `chrome_absent` y no creó
sesión, frame ni surface. Después de abrir Chrome empezó la única corrida
válida.

En `centered-safe portrait` el fixture hizo GET de página, identity y JSON,
renderizó/atestó, pero dos contextos consecutivos compitieron. La primera
captura fue cancelada y la siguiente devolvió:

```text
phase=region_discovery_capture errorCode=3 result=fail_close
```

El SDK Android identifica `3` como
`ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT`, no secure-window.

La rotación prevista no recuperó el gate. El ciclo continuó sin nueva decisión:

```text
early: contentEpoch=21  opaqueCommitted=5
later: contentEpoch=128 opaqueCommitted=98
before preservation: contentEpoch=426 opaqueCommitted=394
STOP: contentInvalidations=2040 opaqueCommitted=1966

regionDiscoveryCompleted=false
regionDiscoveryResult=none
regionOracleMatch=null
inferenceStarted=0
releaseCurrent=0
rawPresented=false
```

Durante el ciclo, `/web13br/render-identity` se solicitó repetidamente y cada
respuesta ejecutó `beginFixtureRender()`, que invalida `contentEpoch`. El
fixture vuelve a renderizar ante `visualViewport.resize`; la nueva cobertura
opaca vuelve a provocar actividad del viewport/render y forma un feedback loop:

```text
visualViewport resize
-> requestRender
-> GET render-identity
-> beginFixtureRender / Navigation invalidation
-> PROTECTED + opaque cover
-> visualViewport/render activity
-> requestRender ...
```

La attestation individual era válida, pero no permanecía estable el tiempo
suficiente para captura+discovery. El data-plane acumuló `5907` passthroughs
del fixture con `failures=0`; no hubo bypass ni error de proxy. No se alcanzó
la matriz centered/off-center/multi/ambiguous ni R3.1 por región, por lo que no
hay afirmación de cobertura física R2A.

## SAFETY / OWNERSHIP DURING FAILURE

El defecto es de liveness/estabilidad de identidad, no una liberación insegura:

```text
phase=Protected
fullFrameAcquired=1
fullFrameClosed=1
fullFrameOutstanding=0
cropCreated=0
cropClosed=0
cropOutstanding=0
inferenceOutstanding=0
workIdle=true
labReleaseCount=0
releaseCurrent=0
rawPersisted=0
rawUploaded=0
secureWindowFailures=0
```

Se grabaron 180.117922 s / 867 frames de presentación para preservar la
ventana del blocker. SHA temporal:
`58d8ce47d6b4938d53ac56b64d5d21c44df8b3bb5ef4ebb3f6b80fa136a02434`.
La grabación cruda fue eliminada tanto del A23 como del Mac tras extraer sólo
hash y metadata; no se persistió ni subió captura Chrome.

## HEALTH / ROLLBACK

- Crash/ANR/OOM: `0/0/0`.
- Data-plane previo a STOP: failures/proxyQueueRejects/protectFailure/
  queueRejects/QUIC/direct TCP = `0/0/0/0/0/0`.
- Visual Shield final: `Inactive`, owners `0/0/0`, `workIdle=true`.
- Transporte final: `inactive`, `ownedFdResources=0`,
  `activeProtectedUdpSockets=0`, `transportRuntime=ready`.
- Chrome quedó suspendido fail-close.
- DO/Affiliated preservados.
- Accessibility enabled/bound y crashed services vacío.
- `ceDataInode=1239519` preservado.
- Rotación restaurada a `accelerometer_rotation=1`, `user_rotation=0`.
- Grabación y samples temporales retiradas del dispositivo.

## RESIDUAL / MINIMUM NEXT DELTA

R2A queda BLOCKED. El siguiente delta debe ser un ticket enfocado de estabilidad
del handshake DEV, no un nuevo algoritmo de discovery:

1. hacer idempotente el rerender del fixture ante `visualViewport.resize`,
   ignorando eventos con la misma tupla exacta de geometry/scale/orientation;
2. garantizar una sola attestation/capture vigente por render identity;
3. agregar regresión determinista que demuestre que publicar el opaque surface
   no realimenta `beginFixtureRender()` indefinidamente;
4. repetir los automáticos y una sola sesión física desde esta evidencia.

No corresponde usar debounce temporal como autoridad, relajar invalidaciones
reales, cambiar thresholds/modelo, agregar retry ciego, tiles o avanzar a R2B.

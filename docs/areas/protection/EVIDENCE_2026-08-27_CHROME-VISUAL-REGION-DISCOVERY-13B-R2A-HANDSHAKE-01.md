# CHROME-VISUAL-REGION-DISCOVERY-13B-R2A-HANDSHAKE-01

Fecha: 2026-08-27. Dispositivo: Samsung A23 `SM-A235M`, Android 14/API 34.

## STATUS

**BLOCKED — GATE H devolvió `Unknown(InsufficientEvidence)`.** El delta elimina
el feedback loop del endpoint de identidad y mantiene estables sus contadores,
pero la primera y única corrida física válida no produjo `Complete`: el crop
renderer-local quedó sin foreground detectable, no coincidió con el oracle y
no inició inferencia regional. Conforme al contrato se detuvo la sesión, no se
ejecutó la matriz R2A y no se modificaron planner ni GloshIA.

## BASE / FUNCTIONAL / REVIEW

- Base funcional obligatoria: `6eee5b65d47d731f0a0fe51d6c2a1ed64cc6cffb`.
- Functional handshake DEV364:
  `1fc11d21cbaa47a3977f67a7fab02f87957eef6a`.
- Rama de trabajo: `work/chrome-visual-region-discovery-13b-r2a-handshake-01`.
- Rama review prevista por resultado:
  `review/chrome-visual-region-discovery-13b-r2a-handshake-01-triage`.
- El HEAD `ef9e05a2259cbc52a9c801ec2bb0174cf8caebc9` no es ancestro del
  trabajo; se usó únicamente como evidencia histórica.
- Central verificado al iniciar:
  `3337f80933446ffc6b859d3ef941fba0cd784d1b`.

## FILES CHANGED

- `app-user/build.gradle.kts`, sólo `versionCode=364`.
- `ChromeVisualShieldRegionDiscoveryHandshake.kt`.
- `ChromeVisualShieldRegionDiscoveryFixture.kt`.
- `ChromeVisualShieldRegionDiscoveryAttestation.kt`.
- tests DEV focalizados del fixture/handshake.
- esta evidencia.

No se tocaron `ChromeVisualShieldRegionDiscoveryPlanner`, contrato
`Complete/Unknown`, `RegionDiscoveryWorkProcessor`, GloshIA, modelo, thresholds,
labels, authority de release, 11B, VPN/HEV, DAG ni video/GIF.

## DEV / APK / MODEL

- `versionCode=364`, `versionName=1.0.1-dev`.
- APK: `app-user/build/outputs/apk/dev/debug/app-user-dev-debug.apk`.
- Tamaño: `159057717` bytes.
- SHA-256:
  `2c8d2e0a2a291b906387e5a95dfb5bfe80ef09857dfc5c2c1506b0dfb99412bc`.
- Instalación `adb install -r`: `Success`, update-in-place.
- GloshIA Visual R3.1 SHA-256:
  `c8b64af8092d3718c58736a511c996d0d443dacf3eaa74620b1e5af439a3cd48`.

## RENDER GEOMETRY KEY / IDENTITY CONTRACT

El seam R2A usa un endpoint DEV propio. La clave determinista contiene:

- scenario y versión de layout;
- source SHA(s);
- orientación;
- offset, tamaño y escala de `visualViewport`;
- `devicePixelRatio`;
- rect CSS exacto del Canvas;
- dimensiones backing del Canvas.

La serialización canónica usa los valores `Double` exactos y el servidor
publica sólo un SHA-256 de la clave. La clave sirve únicamente para idempotencia
DEV: no participa de oracle, discovery, clasificación ni release.

La policy conserva un único estado actual por sesión nativa y geometría:

- mismo session/key, incluso in-flight o ya atestado: `REUSE`, sin side-effect;
- nuevo key: un `beginFixtureRender()` y reemplazo latest-only;
- nueva sesión: puede iniciar el mismo key una vez;
- attestation rechazada: estado terminal para ese session/key, sin retry;
- attestation vieja: no puede ejecutar el callback nativo de la identidad nueva.

El cliente deduplica antes de hacer el request y vuelve a comprobar la geometría
antes y después del frame renderizado. Se eliminó el ciclo anterior
`attestation reject -> revision++ -> retry`.

## ANTI-FEEDBACK REGRESSION

PASS determinista, sin timers:

1. 100 eventos A producen un solo `beginFixtureRender`.
2. A duplicado in-flight no crea trabajo.
3. opaque publication + A no crea trabajo.
4. A completado + resize equivalente no crea trabajo.
5. A -> B produce exactamente el segundo render.
6. duplicados B permanecen en dos renders.
7. orientación/geometría C produce exactamente el tercero.
8. rechazo de attestation A no reintenta A.
9. nueva sesión con A permite un nuevo render.
10. una claim vieja no ejecuta el callback de la identidad nueva.
11. una attestation duplicada no puede invocar dos capturas nativas.

El test del fixture también fija que R2A no llama al endpoint R1 compartido, no
contiene `revision += 1` y compara el geometry key antes de atestar.

## AUTOMATED VALIDATION

PASS, exit `0`:

- `ChromeVisualShieldRegionDiscoveryHandshakeTest`;
- fixture/attestation R2A;
- `ChromeVisualShieldRegionDiscovery*` y oracle verifier;
- `ChromeVisualShield*` relevantes;
- `ChromeWindowCaptureOwnershipTest`;
- `ChromeVisualRegionAnalyzer*`;
- `GloshiaVisualParityTest`;
- regresiones focalizadas Chrome 11B;
- `feature-accessibility:ktlintCheck`;
- `compileDevDebugKotlin`;
- `lintDevDebug`;
- `assembleDevDebug`;
- `git diff --check`.

El ktlint completo de `app-user` continúa con exit `1` por deuda preexistente
en archivos 11B/guard/main no tocados. Los reportes `dev` y `testDev` no
contienen ninguna infracción de los archivos nuevos o modificados por este
ticket; no se reformateó fuera de alcance.

## PREFLIGHT PHYSICAL

- A23 serial `R58T34V31AE`, `SM-A235M`, Android 14/API 34.
- Chrome `152.0.7977.64`, versionCode `797706404`.
- Device Owner/Affiliated presentes.
- Accessibility estaba enabled/bound antes del update. El update la dejó con
  `accessibility_enabled=0`; se restauró al valor previo y quedó enabled/bound,
  con `Crashed services:{}` antes de iniciar el gate.
- `ceDataInode=1239519` antes y después del update.
- data-plane `PresentationReady`, `active=true`, `ready=true`.
- Visual Shield `Inactive`, `workIdle=true`, owners `0/0/0`.
- muestras exactas cargadas sólo en RAM:
  - SAFE: 8090 bytes,
    `541a1ef5373be3dc49fc542fd9a65177b664aec01c8d8608f99e6ec95577d8c1`;
  - BLOCK: 146249 bytes,
    `9f0d22f322d06dd08a8a349b628de5136c66ee6ef601d8c9492e0e286120ff94`.

## HANDSHAKE PHYSICAL

Se ejecutó únicamente `centered-safe portrait`.

El navegador produjo dos geometry keys distintos durante la estabilización
inicial, ambos bajo el mismo token nativo:

```text
renderKey=a8dbdd0f333671491cd152d4f4632f842234cd2b09d778123e571de4eb1eaa19
identityRequests=1 beginFixtureRenderCount=1

renderKey=3944b709721e1f273fb4d4250a0d220c21f8b9eea1d8475e7cb91ad2f8bde42e
identityRequests=2 beginFixtureRenderCount=2

attestationClaims=1 attestationAccepted=1
attestationRejected=0 staleAttestationDropped=0
```

No hubo un tercer request de identidad ni crecimiento sostenido. Durante 30
snapshots externos consecutivos el estado se mantuvo en:

```text
contentEpoch=24
contentInvalidations=23
opaqueCommitted=5
fullFrameOutstanding=0
cropOutstanding=0
inferenceOutstanding=0
workIdle=true
```

Esto elimina el patrón previo de `2040` invalidaciones y `1966` opaque commits.
Actividad posterior de preservación/STOP elevó las invalidaciones de eventos a
`34`, mientras `opaqueCommitted` siguió en `5`; no reapareció actividad del
endpoint de identidad.

## GATE H — BLOCKER

La única captura llegó al planner, pero no produjo `Complete`:

```text
scenario=centered-safe
crop=756x722
cropSha=e802dfa61e942bc4b9d6b532cf5636db9b33eae1a2bb7f01c970521ac3c2ef4d
result=unknown
reason=InsufficientEvidence
totalPixels=545832
foregroundPixels=0
residualPixels=0
componentCount=0
carrierHintCount=0
authority=UnknownObserved
oracleMatch=false
inferenceStarted=0
neverRelease=true
rawPresented=false
```

`Gate H` exigía `Complete`, `regionOracleMatch=true` e inferencia regional.
Por eso el resultado es BLOCKED y no se ejecutaron centered BLOCK, landscape,
off-center, multi, ambiguous, stale/cancel ni la matriz R3.1 por región.

No apareció `ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT=3`. El defecto anterior
de liveness quedó reducido, pero apareció un blocker distinto: el raster que
llegó al planner fue certificado completamente como background y no coincide
con el draw atestado.

## NEVER RELEASE / OWNERSHIP / HEALTH

Durante el gate y al STOP:

```text
phase=Protected -> Inactive
labReleaseCount=0
releaseCurrent=0
rawPresented=false
fullFrameAcquired=1
fullFrameClosed=1
fullFrameOutstanding=0
cropCreated=1
cropClosed=1
cropOutstanding=0
inferenceOutstanding=0
workIdle=true
secureWindowFailures=0
```

Data-plane antes del rollback:

```text
ready=true failures=0 proxyQueueRejects=0 protectFailure=0
queueRejects=0 quicAttempts=0 directTcpAttempts=0
```

No hubo crash, ANR ni OOM. Los exit records creados al rollback corresponden a
procesos aislados de Chrome retirados por el sistema (`ISOLATED NOT NEEDED`),
no a crash.

La grabación temporal del Gate H tuvo 84 frames, `720x1600`, duración
`94.715933 s`, SHA-256
`25e5006e6ea9ae2d57c4e234ba3a986eedd3d89893f3f1a014707765b1af287a`.
Se usó sólo para preservación diagnóstica y se eliminó del A23 y del Mac después
de extraer hash/metadata; no se persistió ni subió captura Chrome.

## ROLLBACK / PRESERVATION

- Visual Shield `Inactive`, todos los owners en cero, `workIdle=true`.
- data-plane `STOP`, proxy/cache/CA limpiados.
- Chrome suspendido fail-close.
- transporte `inactive`, `ownedFdResources=0`,
  `activeProtectedUdpSockets=0`, `transportRuntime=ready`.
- Device Owner/Affiliated preservados.
- Accessibility enabled/bound, crashed services vacío.
- `ceDataInode=1239519` preservado.
- rotación restaurada a auto, `user_rotation=0`.
- samples y grabación temporales retirados.

## RESIDUALS / MINIMUM NEXT DELTA

R2A permanece BLOCKED. El próximo delta debe diagnosticar, antes de editar el
planner, qué raster recibió realmente el search envelope:

1. distinguir con evidencia RAM-only si el crop contiene el opaque compositor,
   el Canvas neutral previo al draw o una región desplazada por mapping;
2. correlacionar el crop SHA y samples de píxeles no sensibles con carrier/draw
   atestados y con el momento del compositor commit;
3. mantener `NEVER RELEASE` y ownership en cero;
4. sólo después decidir si el defecto está en commit ordering, captura o mapping.

No corresponde modificar thresholds/modelo, fabricar foreground, relajar
`Unknown`, reintentar con sleeps ni avanzar a R2B.

El watch item `fullBleed()` detectado por ChatGPT permanece abierto y sin cambios:
antes de R2B deberá probarse contra múltiples contenidos edge-dense para evitar
un falso `Complete` fusionado.

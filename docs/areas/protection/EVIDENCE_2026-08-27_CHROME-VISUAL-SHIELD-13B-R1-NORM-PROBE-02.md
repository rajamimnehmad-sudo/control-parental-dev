# CHROME-VISUAL-SHIELD-13B-R1-NORM-PROBE-02

Fecha: 2026-08-27. Dispositivo físico: Samsung A23 `SM-A235M`, Android
14/API 34.

## STATUS

**BLOCKED / HYPOTHESIS REJECTED — PENDING CHATGPT REVIEW.** La matriz física
SAFE/BLOCK × portrait/landscape quedó completa. El resize directo único a
224×224 conserva la separación esperada en portrait y permite SAFE landscape,
pero BLOCK landscape devuelve `model_allow`. El resultado crítico es:

```text
BLOCK landscape normalizedProbability=0.029659301
BLOCK landscape normalizedPolicyVerdict=model_allow
```

Se ejecutó STOP arquitectónico inmediatamente después de esa celda. No se
probaron otro resize, crop, proporción, tiles, threshold, relabel, imagen,
modelo ni R4. La inferencia normalized nunca adquirió autoridad ni liberó la
superficie.

## BASE / FUNCTIONAL

- Base funcional obligatoria:
  `e4d27df1f4eb6a205635988bfc0592fcc21737cf`.
- La base contiene el PASS DEV361 de
  `CHROME-VISUAL-SHIELD-13B-R1-OWNERSHIP-01`.
- Worktree/rama aislada:
  `work/chrome-visual-shield-13b-r1-norm-probe-02`.
- Functional probe SHA:
  `45c84dc252b6d5eed834c0c4c362fba02ee74917`.
- Los commits `88804188c9e100f1f92165f95bd5a7308b43d6e4` y
  `652727e20be61d8bff7919afdfbdf67dc7ff9d31` no son ancestros ni fueron
  incorporados. `652727e` se consultó únicamente como referencia del contrato
  diagnóstico revisado.
- Review de preservación:
  `review/chrome-visual-shield-13b-r1-norm-probe-02-triage`. Su HEAD remoto es
  el commit evidence-only que contiene este documento y se verifica por
  `git ls-remote` en el handoff. No se creó una rama `*-final` porque el
  resultado es BLOCKED.

## IMPLEMENTATION / AUTHORITY

El delta funcional cambia ocho archivos:

- helper cohesivo `ChromeVisualShieldNormalizedRasterProbe`;
- adapter/wiring y telemetría estrictamente necesarios en RenderProbe;
- un test focalizado nuevo;
- `app-user` DEV361 → DEV362.

Contrato ejecutado:

```text
same attested renderer-local crop
-> Bitmap.createScaledBitmap(crop, 224, 224, filter=true), once
-> prepareCapturedRaster(exact 224x224)
-> canonical RGB conversion only
-> same GloshIA Visual R3.1 analyzer
-> real GloshiaPreparedRasterPolicy
-> evidence only / NEVER RELEASE
```

No hay crop adicional, tiles, letterbox ni preserve-aspect-ratio después del
resize. La policy recibe dos referencias al mismo raster inmutable y cachea la
única inferencia del modelo; así su rama incierta no genera una segunda
geometría. La probabilidad no se compara en código con un threshold duplicado:
el verdict registrado es el que devuelve `GloshiaPreparedRasterPolicy`.

`ChromeVisualShieldDecisionDelivery.decision` sigue conteniendo exclusivamente
la decisión canónica. `ChromeVisualShieldRenderProbeAuthority` observa sólo esa
decisión y permanece NEVER RELEASE; `normalizedEvidence` se usa únicamente en
RenderProbe y telemetría.

`ChromeVisualShieldController.kt` ya superaba 500 líneas. Las diez líneas del
delta sólo materializan la observación y log en su punto de entrega existente;
la preparación/policy nueva quedó en un archivo separado y no se agregó una
responsabilidad al controlador.

No se modificaron `gloshia-visual-core`, modelo, asset, thresholds, labels,
policy, planner regional, tilers, autoridad de release, 11B, VPN, DAG,
video/GIF ni R2A.

## MODEL / DEV / APK

- GloshIA Visual: R3.1.
- Model SHA-256 verificado:
  `c8b64af8092d3718c58736a511c996d0d443dacf3eaa74620b1e5af439a3cd48`.
- Package: `com.contentfilter.user.dev`.
- `versionCode=362`, `versionName=1.0.1-dev`.
- APK: `app-user-dev-debug.apk`, `158992113` bytes.
- APK SHA-256:
  `bb49585ab25735986034b2785acc5c21cf1af7cf9e215d5ab6f446293bac66d2`.
- Certificado instalado/construido:
  `d51bc0dabd280ce1b0f098ae168eb57758faeba301156cde835737835f8a8832`.
- `adb install -r`: `Success`; update in-place desde DEV361.
- `ceDataInode=1239519` antes y después.

## AUTOMATED VALIDATION

Todos los gates finales terminaron con exit code `0`:

```text
:feature-accessibility:testDebugUnitTest
  ChromeVisualShieldNormalizedRasterProbeTest: 3/3 PASS
  ChromeWindowCaptureOwnershipTest: 4/4 PASS
  ChromeVisualShield*: 51/51 PASS

:gloshia-visual-core:testDebugUnitTest
  GloshiaVisualParityTest: 3/3 PASS

:app-user:testDevDebugUnitTest
  ChromeVisualShieldFixtureTest: 20/20 PASS

:feature-accessibility:ktlintCheck PASS
:app-user:compileDevDebugKotlin PASS
:app-user:lintDevDebug PASS, 0 issues
:app-user:assembleDevDebug PASS
git diff --check PASS
```

El build agrupado final terminó `BUILD SUCCESSFUL in 1m39s`, 821 tareas.

Los tests normalized comprueban SAFE y BLOCK por la policy real, una sola
inferencia de modelo y que la rama incierta reutiliza exactamente el mismo
raster sin generar otra inferencia/geometría.

## FIXTURES ATESTADOS

- SAFE: `https://httpbingo.org/image/png`, `8090` bytes, source SHA-256
  `541a1ef5373be3dc49fc542fd9a65177b664aec01c8d8608f99e6ec95577d8c1`.
- BLOCK: `https://farm6.staticflickr.com/3200/2970012318_98f7c80583_o.jpg`,
  `146249` bytes, source SHA-256
  `9f0d22f322d06dd08a8a349b628de5136c66ee6ef601d8c9492e0e286120ff94`.

Los mismos bytes del probe previo se descargaron y verificaron antes del gate.
El carrier visible fue Canvas con `canvas-contain-neutral-v1`; el payload llegó
como JSON/Base64 y se mantuvo en RAM.

Antes de la matriz hubo dos intentos rechazados por el loader shell: uno entregó
0 bytes y otro sólo 12000 bytes porque `adb shell` consumía stdin del pipeline.
El fixture falló cerrado con `fixture_size_mismatch`; no se inició RenderProbe.
El loader se corrigió con stdin aislado, y recién entonces ambos samples
quedaron `fixture_ready` con byte count y SHA exactos. No hubo repetición de la
matriz física.

## PHYSICAL MATRIX

### SAFE portrait

```text
viewport=(0,0)-(1080,2408)
crop=756x722
cropSha=501c7f6c14dbc813724d25d29e0abe371d45c35e6bb907e286db59f012a592a5
canonicalProbability=0.039147645
canonicalPolicyVerdict=model_allow
normalizedProbability=0.03846395
normalizedPolicyVerdict=model_allow
renderAttested=true
source=100x100 canvas=756x703 draw=26.5,0,703,703
fullFrame=3/3/0 crop=2/2/0 inferenceOutstanding=0
```

### SAFE landscape

```text
viewport=(66,0)-(2408,1080)
crop=1639x324
cropSha=30f7c958b7ee899382e174f35bba719c3d42b52614036354ac335a273a6ba428
canonicalProbability=0.015590936
canonicalPolicyVerdict=model_allow
normalizedProbability=0.23607704
normalizedPolicyVerdict=model_allow
renderAttested=true
source=100x100 canvas=1639x304 draw=667.5,0,304,304
fullFrame=4/4/0 crop=3/3/0 inferenceOutstanding=0
```

### BLOCK portrait

```text
viewport=(0,0)-(1080,2408)
crop=756x722
cropSha=597a88e8c0acdfd53e3b6fd8991a534e76c639dc50a2b92a78140854cf21d350
canonicalProbability=0.8488401
canonicalPolicyVerdict=model_filter
normalizedProbability=0.9476602
normalizedPolicyVerdict=model_filter
renderAttested=true
source=1064x1600 canvas=756x703 draw=144.2525,0,467.495,703
fullFrame=5/5/0 crop=4/4/0 inferenceOutstanding=0
```

### BLOCK landscape — critical failure

```text
viewport=(66,0)-(2408,1080)
crop=1639x324
cropSha=f44401d50705abd282ac2a7af05d1c2a67f86371a928e2a0ae30fcd3974d8730
canonicalProbability=0.042146683
canonicalPolicyVerdict=model_allow
normalizedProbability=0.029659301
normalizedPolicyVerdict=model_allow
renderAttested=true
source=1064x1600 canvas=1639x304 draw=718.42,0,202.16,304
fullFrame=6/6/0 crop=5/5/0 inferenceOutstanding=0
```

Attestation fue PASS 4/4. En las cuatro celdas:

```text
normalizedModelInferenceCount=1
neverRelease=true
rawPresented=false
rawPersisted=0
rawUploaded=0
releaseCurrent=0
labReleaseCount=0
workIdle=true
fullFrameOutstanding=0
cropOutstanding=0
inferenceOutstanding=0
```

## HEALTH / PRESERVATION / ROLLBACK

- Ventana física: crash/ANR/OOM `0/0/0`; SIGTRAP `0`.
- Data-plane antes del rollback: `ready=true`, `failures=0`,
  `proxyQueueRejects=0`, `protectFailure=0`, `queueRejects=0`,
  `quicAttempts=0`, `directTcpAttempts=0`.
- Device Owner/Affiliated preservados.
- Accessibility enabled y bound; Binding/Crashed vacíos al cierre.
- Chrome `152.0.7977.64` permaneció estable con el mismo PID browser `11324`.
- Datos, firma e inode preservados.
- Rotación restaurada a `accelerometer_rotation=1`, `user_rotation=0`.
- Visual Shield final: `Inactive`, `fullFrame=6/6/0`, `crop=5/5/0`,
  `inferenceOutstanding=0`, `workIdle=true`.
- Rollback data-plane: proxy/cache/CA limpiados, `status=inactive`,
  `ownedFdResources=0`, `activeProtectedUdpSockets=0`,
  `transportRuntime=ready`, Chrome suspendido fail-close.

## RESIDUAL / ARCHITECTURAL BLOCKER

La hipótesis de que un resize directo único del crop regional completo resolvería
la representación landscape queda rechazada. En BLOCK landscape, la imagen
atestada ocupa `202.16×304` dentro de un Canvas `1639×304`; el crop renderer-local
completo conserva una mayoría de fondo neutral y tanto la ruta canónica como la
normalizada permiten el raster. Esta es una observación del contrato medido, no
una propuesta de threshold.

R1 continúa BLOCKED. La siguiente decisión debe volver a arquitectura y definir
una autoridad de región de contenido suficientemente acotada antes de inferir;
este ticket no inicia REGION-DISCOVERY-R2A ni prueba sitios Frávega/Mimo.

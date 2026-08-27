# CHROME-VISUAL-REGION-DISCOVERY-13B-R2A-RASTER-PROVENANCE-01

Fecha: 2026-08-27. Dispositivo: Samsung A23 `SM-A235M`, Android 14/API 34.

## STATUS

**DIAGNOSED — EPOCH_MISMATCH.** La attestation aceptada pertenecía a
`contentEpoch=21 / regionSequence=21`. La captura que alcanzó el punto de
clasificación pertenecía a `contentEpoch=22 / regionSequence=22`. Por contrato,
esa divergencia tiene precedencia y el observer omitió toda inspección de
píxeles. No se modificaron planner, GloshIA ni scheduling.

R2A permanece `BLOCKED`. Este ticket no declara R2A PASS.

## BASE / FUNCTIONAL / REVIEW

- Base funcional obligatoria:
  `1fc11d21cbaa47a3977f67a7fab02f87957eef6a`.
- Functional DEV365:
  `c905051a5f133ad3661a15b75013cead820610a7`.
- Rama de trabajo:
  `work/chrome-visual-region-discovery-13b-r2a-raster-provenance-01`.
- Rama review objetivo:
  `review/chrome-visual-region-discovery-13b-r2a-raster-provenance-01-final`.
- `review/chrome-visual-region-discovery-13b-r2a-handshake-01-triage`
  `@ 9fd586e5a0e475b7eb8d5d5fcb8a6e87965ffe31` se consultó como evidencia;
  no es ancestro nuevo ni base del delta.
- Central se reconcilió antes de editar en
  `3ac80af055d3175d3476d7ff2fad061651137121`: HANDSHAKE-01 conserva PASS
  físico anti-feedback y bloquea por raster provenance, R2A sigue BLOCKED y
  este ticket quedó como único delta activo.

## DEV / APK / MODEL

- `versionCode=365`, `versionName=1.0.1-dev`.
- APK: `app-user/build/outputs/apk/dev/debug/app-user-dev-debug.apk`.
- Tamaño: `159074101` bytes.
- APK SHA-256:
  `352d3194ac1d9473763be5b1121fb2be052d08b4cb17f4de3b005b24bf527ba6`.
- Instalación física: `adb install -r`, exit `0`, `Success`.
- GloshIA Visual R3.1 SHA-256, sólo verificado y no modificado:
  `c8b64af8092d3718c58736a511c996d0d443dacf3eaa74620b1e5af439a3cd48`.

## FILES CHANGED / AUTHORITY

- `ChromeVisualShieldRasterProvenance.kt`: fingerprint agregado y classifier
  puro fail-close.
- `ChromeVisualShieldRasterProvenanceObserver.kt`: timeline, geometría oracle
  diagnóstica, limpieza de buffers y logs agregados.
- test focalizado del classifier y de no interferencia con planner.
- wiring mínimo en `ChromeVisualShieldController`,
  `ChromeVisualShieldLabControl`, fixture R2A y work processor.
- `app-user/build.gradle.kts`, sólo DEV365.
- esta evidencia.

El observer no altera el crop, search envelope, planner, `Complete/Unknown`,
inferencia ni release. Trabaja sobre copias RAM-only, limpia los `IntArray` en
`finally` y conserva únicamente métricas, colores agregados, bounds, hashes e
identidades. Sus fallas se contienen como `UNKNOWN` sin afectar el pipeline.
No se persistieron screenshots, crops, thumbnails ni píxeles crudos.

## AUTOMATED VALIDATION

PASS, exit `0`:

- `ChromeVisualShieldRasterProvenanceTest`;
- regresión determinista `EPOCH_MISMATCH`, marker surface, canvas pre-draw,
  mapping shift, expected content y señales contradictorias;
- observer conectado/desconectado deja idénticos input y resultado del planner;
- `ChromeVisualShieldRegionDiscoveryHandshakeTest` y fixtures R2A;
- `ChromeVisualShield*` relevantes;
- `ChromeWindowCaptureOwnershipTest`;
- `ChromeVisualRegionAnalyzer` y `GloshiaVisualParityTest`;
- regresiones focalizadas Chrome 11B;
- `:feature-accessibility:ktlintCheck`;
- `:app-user:compileDevDebugKotlin`;
- `:app-user:lintDevDebug`;
- `:app-user:assembleDevDebug`;
- `git diff --check`.

La compilación final terminó `BUILD SUCCESSFUL`, exit `0`, con 821 tareas. El
ktlint completo del source set `app-user` conserva exit `1` exclusivamente por
deuda preexistente en archivos 11B/guard no tocados; el delta de este ticket y
`feature-accessibility:ktlintCheck` pasan. No se reformateó fuera de alcance.

## PREFLIGHT PHYSICAL

- A23 serial `R58T34V31AE`, `SM-A235M`, Android 14/API 34.
- Chrome `152.0.7977.64`, versionCode `797706404`.
- Device Owner y Affiliated presentes.
- Accessibility enabled/bound; `Crashed services:{}`.
- `ceDataInode=1239519` antes y después del update-in-place.
- data-plane antes del gate: `PresentationReady`, `active=true`, `ready=true`.
- Visual Shield: `Inactive`, `workIdle=true`, owners `0/0/0`.
- SAFE fuente: 8090 bytes,
  `541a1ef5373be3dc49fc542fd9a65177b664aec01c8d8608f99e6ec95577d8c1`;
  el store DEV confirmó tamaño y SHA antes de iniciar.

Dos intentos iniciales de transporte de fuente fueron rechazados por el store
antes del probe (`6750/8090` y `7500/8090`); no iniciaron Visual Shield, captura
ni gate. La única sesión física válida comenzó después de
`result=fixture_ready`.

## EPOCH TIMELINE

Único escenario: `centered-safe portrait`.

1. A las `13:01:34.316`, el segundo geometry key estable creó la identidad
   renderer lógica aceptada:

   ```text
   contentEpoch=21 viewportEpoch=1 regionSequence=21 captureSequence=1
   renderGeometryKeyDigest=3944b709721e1f273fb4d4250a0d220c21f8b9eea1d8475e7cb91ad2f8bde42e
   ```

2. A las `13:01:34.420`, la attestation con oracle fue aceptada para esa misma
   identidad:

   ```text
   attestedContentEpoch=21 attestedRegionSequence=21 oraclePresent=true
   ```

3. Inmediatamente se programó `captureSequence=1` con identidad E21/R21. Antes
   de consumir el frame, un evento de navegación real invalidó el contexto y
   avanzó a E22/R22. El trabajo E21 se canceló:

   ```text
   captureCancelled=1 workSuperseded=1
   ```

4. A las `13:01:34.498`, el compositor confirmó opacidad para E22 y se inició
   una nueva captura:

   ```text
   committedEpoch=22 currentContentEpoch=22 viewportEpoch=1 regionSequence=22
   captureSequence=2 captureContentEpoch=22 captureRegionSequence=22
   ```

5. La attestation seguía ligada a E21/R21. A las `13:01:34.499`, antes de
   inspeccionar el bitmap:

   ```text
   rootCause=EPOCH_MISMATCH
   basis=attested=21:21,capture=22:22
   rasterInspection=skipped
   ```

## ATTESTED VS CAPTURE IDENTITY

| Campo | Attestation aceptada | Captura clasificada |
|---|---:|---:|
| protectionSessionId | 1 | 1 |
| windowId | 592 | 592 |
| contentEpoch | 21 | 22 |
| viewportEpoch | 1 | 1 |
| regionSequence | 21 | 22 |
| captureSequence observado | 1 | 2 |

La divergencia simultánea de `contentEpoch` y `regionSequence` satisface la
regla explícita de `EPOCH_MISMATCH`. El token histórico de render no contiene
`contentEpoch`; este ticket sólo lo midió y no cambió el token productivo.

## FULL-FRAME / CROP FINGERPRINT

- Full frame resource: adquirido `1`, cerrado `1`, outstanding `0`.
- Full-frame fingerprint de píxeles: **no evaluado por precedencia de epoch**.
- Crop: no creado; `cropCreated=0`, `cropClosed=0`, outstanding `0`.
- Crop SHA: `none`.
- Surface marker: no evaluado.
- Card/Canvas: no evaluado.
- Mapping delta: no evaluado.

Esto es intencional: continuar con provenance raster después del mismatch habría
mezclado attestation E21 con captura E22 y violado el contrato del ticket.

## ROOT CAUSE CLASSIFICATION

**A. EPOCH_MISMATCH.** La evidencia es única y no contradictoria. No corresponde
clasificar `PROTECTED_SURFACE_CAPTURED`, `CANVAS_PRE_DRAW`, `MAPPING_SHIFT` ni
`EXPECTED_CONTENT_PRESENT` con este frame.

El `errorCode=3` posterior no habilitó retry ni cambió la causa: apareció tras
la clasificación E21/R21 contra E22/R22 y la ruta permaneció fail-close.

## NEVER RELEASE / OWNERSHIP

En el terminal y después de STOP:

```text
phase=Inactive
neverRelease=true
rawPresented=false
labReleaseCount=0
releaseCurrent=0
fullFrameAcquired=1
fullFrameClosed=1
fullFrameOutstanding=0
cropCreated=0
cropClosed=0
cropOutstanding=0
inferenceOutstanding=0
workIdle=true
```

No se ejecutaron centered BLOCK, landscape, off-center, multi ni una segunda
sesión física.

## HEALTH / ROLLBACK

Antes del rollback:

```text
ready=true failures=0 proxyQueueRejects=0 protectFailure=0
queueRejects=0 quicAttempts=0 directTcpAttempts=0 recursion=0
```

- crash/ANR/OOM durante la ventana: `0/0/0`.
- Ningún exit record nuevo de Chrome durante el gate; los últimos records
  persistidos son previos y `ISOLATED NOT NEEDED`.
- Visual Shield `Inactive`, owners en cero y `workIdle=true`.
- data-plane `STOP`, `rollback=complete`, proxy/cache/CA retirados.
- Chrome suspendido fail-close, verificado por guard y package state.
- transporte `inactive`, `ownedFdResources=0`,
  `activeProtectedUdpSockets=0`, `transportRuntime=ready`.
- Device Owner/Affiliated preservados.
- Accessibility enabled/bound, crashed services vacío.
- `ceDataInode=1239519` preservado.
- rotación restaurada a auto, `user_rotation=0`.
- muestra fuente retirada del store por STOP; temporales Mac eliminados.

## RESIDUAL / NEXT ROUTE

El siguiente delta arquitectónico corresponde a binding explícito
R2A attestation ↔ `contentEpoch`/`regionSequence` y a impedir que un opaque
commit/capture de un epoch nuevo reutilice una attestation anterior. Debe seguir
siendo state-driven: sin sleep, debounce, retry ciego ni relajación de
invalidaciones reales.

Después de corregir ese binding deberá repetirse este diagnóstico en una nueva
sesión. Sólo si attestation y captura coinciden corresponde inspeccionar surface,
canvas, mapping o planner. El watch item adversarial de `fullBleed()` permanece
abierto y sin cambios antes de R2B.

# CHROME-VISUAL-REGION-DISCOVERY-13B-R2A-EPOCH-BINDING-01

## STATUS

**BLOCKED.** El binding de seguridad cerró el cruce de generaciones, pero el
handshake DEV no recuperó liveness después de rechazar una attestation que se
volvió stale antes de ser reclamada. Gate H no completó y, por contrato, no se
ejecutó la matriz R2A.

## Refs y artefacto

- Base funcional: `c905051a5f133ad3661a15b75013cead820610a7`.
- Functional SHA: `f369f1236901e50ef9e6c19503d7d6c16b916591`.
- Rama de trabajo: `work/chrome-visual-region-discovery-13b-r2a-epoch-binding-01`.
- Rama review: `review/chrome-visual-region-discovery-13b-r2a-epoch-binding-01-triage`.
- Versión: DEV366.
- APK: `app-user-dev-debug.apk`, 159074101 bytes.
- APK SHA-256:
  `3cad7f369512d9bd212aa0de5e48660c0be82178c0a854375a10137fe5638f26`.
- Modelo R3.1 SHA-256:
  `c8b64af8092d3718c58736a511c996d0d443dacf3eaa74620b1e5af439a3cd48`.

La rama evidence-only de RASTER-PROVENANCE-01 (`4a1c7530...`) no fue
incorporada como ancestro. El delta parte directamente de la base funcional
indicada.

## Implementación y contrato

Se añadió un binding R2A inmutable con:

- `protectionSessionId`;
- `windowId`;
- `contentEpoch`;
- `viewportEpoch`;
- `regionSequence`;
- `renderIdentityToken`;
- `renderGeometryKeyDigest`.

La attestation conserva conjuntamente oracle y binding. Cualquier invalidación
R2A invalida ambos. Antes de `beginCapture` se exige binding vigente contra el
contexto actual y, después de crear la identidad de captura, se vuelve a exigir
match exacto. Sin match no se invoca screenshot, crop, planner ni inferencia.
El token compartido de R1 no cambió.

La coordinación DEV distingue misma geometría/misma generación de misma
geometría/nueva generación y usa una señal cancellable/state-driven para
Completed, Invalidated, Stopped o TimedOut. No se añadieron sleeps, debounce,
polling ocupado ni retries ciegos.

`ChromeVisualShieldController.kt` quedó en 740 líneas. El cambio allí es wiring
mínimo de las dos barreras; el estado y la responsabilidad nueva viven en
clases separadas de binding/lab. No se modificaron planner, Complete/Unknown,
GloshIA, modelo, thresholds, policy, autoridad de release, 11B ni VPN.

## Validación automática

PASS:

- regresión determinista E21/R21 -> invalidación E22/R22;
- no C2, planner ni inferencia sin attestation E22;
- attestation vigente permite captura sólo de su misma generación;
- rechazo independiente de mismatch de session, window, content epoch,
  viewport epoch, region sequence y token;
- stale oracle no cruza epoch;
- same geometry/same epoch reutiliza;
- same geometry/new epoch habilita nuevo render;
- attestation duplicada no crea dos capturas;
- STOP invalida binding y despierta la barrera;
- `feature-accessibility:testDebugUnitTest`;
- tests focalizados R2A, handshake, raster provenance, oracle, planner,
  ownership, Visual Shield, analyzer y parity R3.1;
- tests focalizados heredados de 11B;
- `compileDevDebugKotlin`;
- `lintDevDebug`;
- `assembleDevDebug`;
- ktlint de todos los archivos modificados;
- `git diff --check`.

El ktlint agregado de `app-user` sigue reportando deuda previa en archivos no
tocados de 11B/guard. Los archivos de este delta no aparecen en el reporte tras
la corrección focalizada; no se reformateó deuda ajena.

## Gate físico A23

Preflight:

- SM-A235M, Android 14/API 34;
- Chrome `152.0.7977.64` (`797706404`);
- DEV366 instalado update-in-place;
- `ceDataInode=1239519` antes y después;
- Device Owner preservado;
- Accessibility enabled/bound y `Crashed services:{}`;
- data-plane `PresentationReady`, `ready=true`, `failures=0`,
  `proxyQueueRejects=0`, `protectFailure=0`, `queueRejects=0`,
  `quicAttempts=0`, `directTcpAttempts=0`.

Se ejecutó una sola sesión válida `centered-safe portrait`.

## Cronología de generación

```text
14:28:29.282 render NEW
  session=1 window=609 E13/V1/R13
  identityRequests=1 beginFixtureRenderCount=1

14:28:29.402 render NEW
  session=1 window=609 E21/V1/R21
  identityRequests=2 beginFixtureRenderCount=2

14:28:29.464 native context advances to E22/V1/R22

14:28:29.482 attestation E21 rejected before claim
  result=region_attestation_stale_or_invalid
  attestationClaims=0 attestationAccepted=0
  staleAttestationDropped=1
```

La superficie permaneció protegida. El estado nativo siguió avanzando hasta
E26/R26 por eventos posteriores, pero no apareció un tercer render request ni
una attestation para la generación actual.

## Attested/capture match y barrera

No existió una pareja attested/capture divergente:

```text
regionBindingContentEpoch=null
regionBindingViewportEpoch=null
regionBindingRegionSequence=null
captureCycles=0
fullFrameAcquired=0
cropCreated=0
inferenceStarted=0
```

Esto demuestra que la barrera fail-close funcionó. También demuestra que Gate H
no alcanzó liveness: el rechazo ocurre antes de `generationResult()`, devuelve
`region_attestation_stale_or_invalid`, y el fixture sólo solicita una nueva
generación cuando recibe `region_generation_invalidated`. Por eso la generación
E22/R22 quedó protegida pero sin nueva solicitud state-driven.

## Handshake y matriz

Estado terminal previo a STOP:

```text
active=true phase=Protected
contentEpoch=26 viewportEpoch=1 regionSequence=26
regionDiscoveryCompleted=false
regionDiscoveryResult=none
regionOracleMatch=null
opaqueCommitted=5
contentInvalidations=25
inferenceStarted=0
never release: labReleaseCount=0 releaseCurrent=0
rawPersisted=0 rawUploaded=0
workIdle=true
```

Gate H: **BLOCKED**.

Matriz Complete/Unknown y R3.1 por región: **no ejecutada**, conforme al STOP
obligatorio al fallar Gate H. Raster provenance tampoco se ejecutó porque nunca
se autorizó una captura.

## Ownership, salud y rollback

Al STOP:

```text
phase=Inactive active=false workIdle=true
fullFrameAcquired=0 fullFrameClosed=0 fullFrameOutstanding=0
cropCreated=0 cropClosed=0 cropOutstanding=0
inferenceStarted=0 inferenceCompleted=0 inferenceOutstanding=0
releaseCurrent=0 labReleaseCount=0 rawPresented=false
```

- crash/ANR/OOM durante la ventana: `0/0/0`;
- no `FATAL EXCEPTION`, ANR, OOM ni señal nativa en el log de la sesión;
- un warning no terminal de finalizer de Android (`ViewRootImpl.die` NPE)
  apareció inmediatamente antes del inicio del probe; el proceso sobrevivió y
  no generó exit-info. Se conserva como residual observado, no como atribución;
- rollback del data-plane: proxy/cache limpios, CA removida, VPN restaurada y
  Chrome suspendido fail-close;
- `status=inactive ownedFdResources=0 activeProtectedUdpSockets=0
  transportRuntime=ready`;
- Device Owner, Accessibility, datos e inode preservados;
- rotación del sistema restaurada y temporales ADB retirados.

La grabación física se usó sólo como evidencia efímera; SHA-256
`79444402a4685241d7d06dfeb5ead5d8b3b7a456c12c6e0e0105761d8be2d987`.
No se versionó ni persistió ningún raster/crop/screenshot.

## Residual / siguiente ruta

Defecto concreto restante: el endpoint de attestation clasifica como stale o
invalid un claim cuya generación ya avanzó, pero no convierte ese estado en la
señal explícita `generation_invalidated` que el fixture usa para solicitar el
nuevo render de la misma geometría/nueva generación. El próximo delta debe
cerrar esa transición pre-claim de forma state-driven y fail-close. No debe
tocar planner, raster, GloshIA, thresholds ni autoridad de release.

R2A continúa **BLOCKED**. No se inicia R2B, Frávega/Mimo, scheduler,
performance, video ni GIF.

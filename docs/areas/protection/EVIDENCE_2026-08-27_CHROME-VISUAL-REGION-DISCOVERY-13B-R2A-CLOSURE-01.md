# CHROME-VISUAL-REGION-DISCOVERY-13B-R2A-CLOSURE-01

Fecha: 2026-08-27. Dispositivo: Samsung A23 `SM-A235M`, Android 14/API 34.

## STATUS

**TECHNICAL/PHYSICAL PASS — PENDING CHATGPT FINAL REVIEW.** R2A localiza y
clasifica regiones renderer-local únicamente en fixtures controlados. Todo el
gate permanece `NEVER RELEASE`; no se agregó autoridad productiva ni se inició
R2B.

## BASE / FUNCTIONAL / REVIEW

- Base: `8e61de77f5e1f3794fe73ecae99f5ab098915ffc`.
- Rama de trabajo: `work/chrome-visual-region-discovery-13b-r2a-closure-01`.
- Functional SHA: `999a5cc2a3982bad0fad3f34baeba54b30d4fd8f`.
- Review final: `review/chrome-visual-region-discovery-13b-r2a-closure-01-final`;
  el HEAD remoto evidence-only se registra en el handoff después de verificarlo
  con `git ls-remote`.
- Modelo GloshIA Visual R3.1 SHA-256:
  `c8b64af8092d3718c58736a511c996d0d443dacf3eaa74620b1e5af439a3cd48`.

## VERSION / APK

- Package: `com.contentfilter.user.dev`.
- `versionCode=375`, `versionName=1.0.1-dev`.
- APK: `app-user-dev-debug.apk`, `159106869` bytes.
- APK SHA-256:
  `799c328901820cdebf5a5f66e5a7c7b006aa7cc07050aaccedffc18b0b19221b`.
- Instalación: `adb install -r`, `Success`; firma, datos y
  `ceDataInode=1239519` preservados.

## ROOT CAUSES / FIXES

El cierre aisló y corrigió defectos locales sin cambiar modelo, thresholds,
labels, policy ni release authority:

1. El verifier comparaba geometría en espacios distintos y el diagnóstico no
   exponía cada condición. Se unificó el mapping y se materializó evidencia de
   cobertura/intersección/delta/escala.
2. El shortcut `fullBleed()` podía convertir evidencia edge-dense ambigua en un
   único `Complete`. Se retiró esa autoridad: residual, overlap, ambiguity y
   múltiples islas siguen fail-close como `Unknown`.
3. La attestation podía preceder al raster presentado. El fixture espera dos
   `requestAnimationFrame` después del draw y la captura queda detrás de un
   nuevo opaque compositor commit posterior a la attestation.
4. El viewport Accessibility incluye el área de navegación Android mientras la
   página Chrome no. Oracle y search envelope descuentan el inset medido, no un
   valor temporal ni heurístico.
5. En landscape el search envelope conservaba una franja exterior de dos
   píxeles. El planner la clasificaba correctamente como `CutComponent`. R2A
   aplica un inset geométrico fijo de cuatro píxeles, exclusivamente al
   envelope DEV, para mantener la búsqueda dentro del carrier atestado.

No se reabrieron los gates ya cerrados de anti-feedback, epoch binding,
stale-generation one-shot, capture admission ni ownership.

## ORACLE / RASTER PROVENANCE

Gate 0 exact-draw heredado: SAFE/BLOCK portrait/landscape `4/4 PASS`.

El observer RAM-only registró dimensiones, firmas agregadas y SHA del crop; no
persistió pixels. En la build final:

```text
landscape search=355,232,1986,548
landscape carrier=351,225,1991,550
SAFE expectedDraw=1038,254,1304,521 observedCard=1036,253,1306,523
BLOCK expectedDraw=1082,254,1260,521 observedCard=1081,253,1261,523
oracleCoverage=1.000000
insideSearchFraction=1.000000
rasterRootCause=EXPECTED_CONTENT_PRESENT
surfaceMarkerPixels=0
attested contentEpoch/regionSequence == capture contentEpoch/regionSequence
```

El resultado previo `UNKNOWN/no_unique_signature` quedó explicado por mapping y
por el borde exterior, no por ausencia del contenido. El observer conectado o
desconectado produce el mismo input/result del planner por regresión
determinista.

## R2A PHYSICAL MATRIX

Samples atestados, sin elegir imágenes nuevas:

- SAFE: 8090 bytes, SHA-256
  `541a1ef5373be3dc49fc542fd9a65177b664aec01c8d8608f99e6ec95577d8c1`.
- BLOCK: 146249 bytes, SHA-256
  `9f0d22f322d06dd08a8a349b628de5136c66ee6ef601d8c9492e0e286120ff94`.

Resultados físicos acumulados dentro del ticket; DEV375 revalidó el delta
final en ambas orientaciones:

| Caso | Discovery / oracle | R3.1 |
|---|---|---|
| centered SAFE portrait | Complete / match | Allow `model_allow`, p=`0.024400145` |
| centered SAFE landscape | Complete / match | Allow `model_allow`, p=`0.025322706` |
| centered BLOCK portrait | Complete / match | Block `model_filter`, p=`0.93479085` |
| centered BLOCK landscape | Complete / match | Block `model_filter`, p=`0.94701016` |
| BLOCK off-left | Complete / match | Block `model_filter`, p=`0.93844444` |
| BLOCK off-right | Complete / match | Block `model_filter`, p=`0.9408576` |
| multi SAFE + BLOCK | Complete / match, 2 regiones | Allow p=`0.028654963`; Block p=`0.93331885` |
| ambiguous | Unknown / match | no inference |
| portrait -> landscape -> portrait | nueva identity por viewport | PASS |
| stale / cancel / STOP | protegido; sin release | PASS |

En DEV375 los crops landscape fueron `1631x316`:

```text
SAFE cropSha=ae5e858941bf2de3db1d6f9d6ab87016713541f6582926809797b9dbd127da13
SAFE regionSetDigest=ac0d061f35753f6bc64a5ce102e8da778415d5409ba5b30818b8255e443ed46b
BLOCK cropSha=3a974a5b7a9339031bea9df5d0f72e5fd185340b0fb6e8cb1a2cbd384f8f5a6d
BLOCK regionSetDigest=adf384015ef773a3f66e4aae294b5dc2ce7f4d5186155f7108b6cceebb2dc220
```

Dos intentos etiquetados inicialmente como landscape en DEV374 fueron
descartados: `settings user_rotation` no había rotado físicamente la window. El
gate válido usó `wm user-rotation lock 1` y verificó
`mCurrentRotation=ROTATION_90`, `cur=2408x1080`.

## FULLBLEED ADVERSARIAL

Tests puros cubren múltiples regiones edge-dense, contenido tocando bordes, dos
contenidos separados con textura fuerte, SAFE+BLOCK edge-dense y layouts donde
un único full-frame sería incorrecto. Todos producen regiones separadas o
`Unknown`; nunca un falso `Complete` fusionado. El shortcut permisivo fue
eliminado y no se relajaron los criterios de residual/ambiguity.

## NEVER RELEASE / ZERO EXPOSURE

Durante todos los casos:

```text
neverRelease=true
rawPresented=false
releaseCurrent=0
labReleaseCount=0
rawPersisted=0
rawUploaded=0
```

Grabaciones temporales analizadas a 10 fps mediante máscara
`R>=160 && G<=90 && B<=110`, con visibilidad declarada desde 1% del frame:

```text
DEV375 landscape: 1787 frames, sentinelVisibleFrames=0, peakRedCoverage=0.840274%
DEV375 portrait:   582 frames, sentinelVisibleFrames=0, peakRedCoverage=0.071187%
```

SHA-256 temporales: landscape
`4abfea04b6aedff882edc0943209dd809d71c5d7e3264f9499d46a534df055c3`;
portrait
`60147d3abc3a79895a2cdf17dc7f17f46403d34b97a71f444a6ac5de4ba85b0e`.
Las grabaciones se eliminaron después de extraer métricas. La conclusión es
exposición observable `0` a la resolución de sampling; no se afirma cobertura
entre muestras.

## IDENTITY / STALE / OWNERSHIP

En cada trabajo aceptado:

```text
attestedContentEpoch == captureContentEpoch
attestedRegionSequence == captureRegionSequence
```

La inyección stale final produjo:

```text
staleDropped=1
staleInferenceDropped=1
releaseRejected=1
releaseCurrent=0
```

Estado terminal DEV375:

```text
fullFrameAcquired=4 fullFrameClosed=4 fullFrameOutstanding=0
cropCreated=4 cropClosed=4 cropOutstanding=0
inferenceStarted=4 inferenceCompleted=4 inferenceOutstanding=0
workIdle=true
```

## AUTOMATED VALIDATION

PASS:

- `:feature-accessibility:testDebugUnitTest`;
- `:gloshia-visual-core:testDebugUnitTest`;
- focalizados DEV `ChromeVisualShield*`, Protected Surface, 11B authority,
  provenance fixture/routing;
- `:app-user:lintDevDebug`;
- `:app-user:assembleDevDebug`;
- `git diff --check`.

Build final: `BUILD SUCCESSFUL in 2m 12s`, 845 tasks.

Ktlint test source set: PASS. El main source set conserva cuatro infracciones
preexistentes, todas `git blame` a `f369f1236`: Controller línea 291 y
RegionDiscoveryLab líneas 123/137/158. Ninguna fue introducida por este delta;
no se reformateó fuera de scope.

## HEALTH / ROLLBACK

```text
crash/ANR/OOM=0/0/0
failures=0
proxyQueueRejects=0
protectFailure=0
quicAttempts=0
directTcpAttempts=0
ownedFdResources=0
activeProtectedUdpSockets=0
transportRuntime=ready
```

El Visual Shield terminó `Inactive`, Chrome quedó fail-close al rollback del
data-plane, la rotación se restauró a portrait, y los temporales del dispositivo
se eliminaron. Device Owner, Affiliated, Accessibility enabled/bound, datos y
`ceDataInode=1239519` quedaron preservados.

## RESIDUALS

- R2A sigue siendo `NEVER RELEASE` y fixture-only; no descubre sitios reales.
- R2B/release authority, Frávega/Mimo, scheduler/performance y video/GIF quedan
  explícitamente fuera de este cierre.
- Las infracciones ktlint históricas indicadas permanecen como deuda separada.

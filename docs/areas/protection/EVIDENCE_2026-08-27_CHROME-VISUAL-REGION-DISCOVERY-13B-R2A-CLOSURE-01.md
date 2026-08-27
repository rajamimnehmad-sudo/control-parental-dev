# CHROME-VISUAL-REGION-DISCOVERY-13B-R2A-CLOSURE-01

Fecha: 2026-08-27. Dispositivo: Samsung A23 `SM-A235M`, Android 14/API 34.

## STATUS

**R2A PASS FINAL VALIDATION — READY FOR CHATGPT FINAL REVIEW.** R2A localiza y
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

### FINAL VALIDATION — MISMO APK DEV375

La validación final no recompiló ni reinstaló. Se extrajo el APK instalado y se
comparó byte a byte con el artefacto final local: ambos midieron `159106869`
bytes y SHA-256
`799c328901820cdebf5a5f66e5a7c7b006aa7cc07050aaccedffc18b0b19221b`.
Los cuatro centered ya acreditados no se repitieron.

Los escenarios que antes se habían acumulado antes del `edgeInset` final se
ejecutaron sobre ese mismo DEV375 final:

| Caso final DEV375 | Crop SHA-256 | Discovery / oracle | R3.1 |
|---|---|---|---|
| BLOCK off-left | `208f037030c4d6d193b494260f6c953dbf8a5b81088ff21766b8d995e68a872a` | Complete / match | Block `model_filter`, p=`0.93844444` |
| BLOCK off-right | `3a0c9cacb1609a73ff4463e465bc410adc25aa25ae6e76d161b252c48ed31fa2` | Complete / match | Block `model_filter`, p=`0.9408576` |
| multi SAFE + BLOCK | `6f56bb0aff6a3c538cec552696ac0bcb7fa31300907fc7661cc1d41df5305e90` | Complete / match, 2 regiones | Allow p=`0.028654963`; Block p=`0.93331885` |
| ambiguous | `e9ca519914b7de7628d3e84b283325c3d10f5ff94e4899af1704a7d973a2bad6` | Unknown `BackgroundAmbiguous` / match | 0 inferencias |

Cada ciclo aceptado registró `attested contentEpoch/regionSequence == capture
contentEpoch/regionSequence`, `errorCode3=0`, `neverRelease=true` y
`rawPresented=false`. Un comando inicial off-left emitido antes de que Chrome
apareciera devolvió `chrome_absent` y no produjo captura; el escenario válido
se ejecutó inmediatamente después en la misma sesión.

La transición portrait -> landscape -> portrait verificó físicamente
`ROTATION_0 / cur=1080x2408`, `ROTATION_90 / cur=2408x1080` y nuevamente
`ROTATION_0 / cur=1080x2408`. `viewportEpoch` avanzó `9 -> 11 -> 14`; las
capturas posteriores quedaron ligadas a `E198/R198` y `E214/R214`,
respectivamente, sin liberar resultados anteriores.

## FULLBLEED ADVERSARIAL

Tests puros cubren múltiples regiones edge-dense, contenido tocando bordes, dos
contenidos separados con textura fuerte, SAFE+BLOCK edge-dense y layouts donde
un único full-frame sería incorrecto. Todos producen regiones separadas o
`Unknown`; nunca un falso `Complete` fusionado. El shortcut permisivo fue
eliminado y no se relajaron los criterios de residual/ambiguity. Sobre el HEAD
final se reejecutaron `ChromeVisualShieldRegionDiscoveryPlannerTest`,
`ChromeVisualShieldRegionDiscoveryOracleVerifierTest`,
`ChromeVisualShieldRasterGeometryEvidenceTest` y
`ChromeWindowCaptureOwnershipTest`: `BUILD SUCCESSFUL`, 117 tareas. La búsqueda
en producción sólo conserva la telemetría `fullBleedAuthority=false`; no existe
un shortcut `fullBleed` con autoridad `Complete`.

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
DEV375 final validation: 1820 frames, sentinelVisibleFrames=0, peakRedCoverage=0.836804%
```

SHA-256 temporales: landscape
`4abfea04b6aedff882edc0943209dd809d71c5d7e3264f9499d46a534df055c3`;
portrait
`60147d3abc3a79895a2cdf17dc7f17f46403d34b97a71f444a6ac5de4ba85b0e`.
La grabación de validación final duró `181.969411s`, fue adquirida a
`720x1280` con cadencia efectiva del dispositivo `1/3 fps` y luego remuestreada
a 10 fps por el analizador; SHA-256 temporal
`1f6716a209bbe65e3e193ff2a74cf8edf1971d49cd98e991fa8a0f4b9b4dafe6`.
Las grabaciones se eliminaron después de extraer métricas. La conclusión es
exposición observable `0` a la resolución de sampling; no se afirma cobertura
entre muestras.

## IDENTITY / STALE / OWNERSHIP

En cada trabajo aceptado:

```text
attestedContentEpoch == captureContentEpoch
attestedRegionSequence == captureRegionSequence
```

La inyección stale de validación final produjo:

```text
staleDropped=2
staleInferenceDropped=2
releaseRejected=2
releaseCurrent=0
rawPresented=false
```

Estado terminal DEV375:

```text
fullFrameAcquired=10 fullFrameClosed=10 fullFrameOutstanding=0
cropCreated=10 cropClosed=10 cropOutstanding=0
inferenceStarted=10 inferenceCompleted=10 inferenceOutstanding=0
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

# CHROME-STOCK-MEDIA-AUTHORITY-19 — READY event binding

Fecha: 2026-08-30.

## STATUS

**BLOCKED / FOREGROUND_READY_EVENT_SOURCE_UNAVAILABLE.** El Byte Gate H19
continua fail-close, pero stock Chrome no emitio un `TYPE_VIEW_FOCUSED` cuyo
`event.source` fuera el beacon del documento despues de aceptar el claim READY.
Sin esa identidad no-raster no existe autoridad para retirar la superficie
opaca de la ventana foreground. No se uso screenshot, compositor, CDP,
extension, polling ni temporizacion como autoridad.

Los gates real-web y R3.1 selective no se ejecutaron: el primer estado
controlado obligatorio no adquirio autoridad foreground.

## BASE / FUNCTIONAL / REVIEW

- Base funcional: `04f11cd5e819035b6e9ca179f4c955d8e031446a`.
- El review H19 anterior `0eab97c4fc08889ae959ccbbc2b30d86751c113e`
  era evidence-only y no fue usado como base.
- Functional SHA de este delta:
  `3e2a4540fa55e00cc831c81144bfb532a953afb1`.
- Rama de trabajo:
  `work/chrome-stock-media-authority-19-ready-event-binding-01`.
- Review triage:
  `review/chrome-stock-media-authority-19-ready-event-binding-01-triage`.
- El review HEAD exacto se materializa y verifica remotamente despues de este
  documento.

## VERSION / APK / DEVICE

- Aplicacion: `com.contentfilter.user.dev`, DEV393,
  `versionName=1.0.1-dev`.
- APK: `app-user/build/outputs/apk/dev/debug/app-user-dev-debug.apk`.
- APK SHA-256:
  `46e9294dbc6a4456f5783b50204d0ce2946a2603f52069fda266a63164e3c092`.
- Firma SHA-256 preservada:
  `d51bc0dabd280ce1b0f098ae168eb57758faeba301156cde835737835f8a8832`.
- Modelo: GloshIA Visual R3.1, SHA-256
  `c8b64af8092d3718c58736a511c996d0d443dacf3eaa74620b1e5af439a3cd48`.
- Dispositivo: Samsung `SM-A235M`, Android 14/API 34.
- Chrome oficial: `152.0.7977.64`, versionCode `797706404`.
- Instalacion DEV392 -> DEV393 mediante `adb install -r`: `Success`.
- `ceDataInode=1239519` antes y despues.

No fue necesario que un usuario iniciara sesion en Glosh: este gate DEV usa
Device Owner, Accessibility, VPN/runtime y el fixture controlado local. Esos
prerrequisitos si fueron verificados.

## CONTRATO IMPLEMENTADO

El bootstrap del documento crea un `button` nativo dentro de un ShadowRoot
cerrado. El token READY secreto se publica solamente despues de que el POST
sincrono del claim obtiene `204/current`; luego se agrega el host y se ejecuta
`focus({preventScroll:true})` sin timers.

La autoridad nativa acepta exclusivamente el `event.source` exacto de un
`TYPE_VIEW_FOCUSED` de Chrome y exige simultaneamente:

- package y `windowId` current de Chrome;
- clase nativa `Button`, focusable y view ID exacto;
- `uniqueId` del source;
- ancestro WebView exacto;
- root nativo exacto y su digest current;
- claim, sesion, policy epoch, document sequence y lifecycle current.

Un scan por view ID permanece diagnostico y no crea autoridad. La continuidad
posterior exige el mismo WebView y el mismo root nativo. Navegacion, reemplazo
de root/window, tab, STOP o lifecycle stale revocan el binding. La superficie
solo podria liberarse despues de una segunda verificacion current en la
frontera de release.

## VALIDACION AUTOMATICA

Todos los comandos siguientes terminaron con exit `0` sobre el functional
SHA:

```text
python3 -m unittest discover -s tools/chrome_stock_media_authority
  74 tests, 0 failures

./gradlew :feature-accessibility:testDebugUnitTest
./gradlew :app-user:testDevDebugUnitTest
./gradlew :feature-accessibility:ktlintCheck
./gradlew :app-user:compileDevDebugKotlin
./gradlew :app-user:lintDevDebug
./gradlew :app-user:assembleDevDebug
git diff --check
```

Las regresiones incluyen exact-event-only authority, source/root ownership,
native-root replacement, stale/replay, wrong window, navigation, rotation,
process/lifecycle invalidation, release boundary recheck y terminal binding del
harness. Dos revisiones estaticas independientes no encontraron P0/P1 en el
delta final.

## GATE FISICO A23

Una invocacion inicial se descarto antes de navegar: un `STATUS` de preflight
habia iniciado transitoriamente el servicio de laboratorio. Se hizo STOP
explicito y se ejecuto una unica sesion fisica valida desde estado limpio.

Comando canonico, exit `2` por el gate READY fail-close:

```text
ANDROID_HOME=... python3 tools/chrome_stock_media_authority/run_a23_gate.py \
  --serial R58T34V31AE \
  --plan tools/chrome_stock_media_authority/final_plan.json \
  --output .codex-tmp/chrome-stock-media-authority/h19-dev393-final-r2
```

Resultado de Byte Gate durante el primer documento controlado:

```text
networkVisualCandidates=6
networkVisualReplaced=6
networkVisualRawDelivered=0
networkVisualRawBlockedDelivered=0
networkVisualRawUnknownDelivered=0
```

El snapshot terminal acumulado confirmo `12/12/0`, nuevamente con raw
BLOCK/UNKNOWN en cero. El document transformer completo dos documentos y el
claim READY del documento de gate fue aceptado.

## TIMELINE DE AUTORIDAD

Logcat acotado de la sesion valida:

```text
04:13:03.397 TYPE_VIEW_FOCUSED package=com.android.chrome
04:13:06.856 TYPE_VIEW_FOCUSED package=com.android.chrome
04:13:06.916 TYPE_VIEW_FOCUSED package=com.android.chrome
04:13:07.764 ready_ack_accepted windowId=1492 documentSequence=1
             axBound=false binding=none sourceCurrent=false rawPresented=false
04:13:09.172 ready_fail_closed reason=ready_document_invalidated
             rawPresented=false
```

Los tres focus events ocurrieron **antes** del claim READY y corresponden a
actividad Chrome preexistente; ninguno puede autorizar ese documento. Despues
de que el bootstrap publico y enfoco el beacon hubo:

```text
post-claim TYPE_VIEW_FOCUSED=0
ready_focus_bound=0
ready_focus_rejected=0
ready_foreground_released=0
```

Por lo tanto no hubo `event.source` del beacon que pudiera compararse con el
claim current. El documento fue invalidado despues sin liberar. Esta es una
ausencia de señal browser-side, no un rechazo relajable ni un problema del
Byte Gate.

## FAIL-CLOSE / EXPOSURE

- `rawPresented=false` en toda la ventana observada.
- `releaseCurrent=0`; no hubo release cruzado ni tardio.
- La superficie opaca permanecio armada hasta invalidacion y teardown.
- No se invoco screenshot/crop/inferencia de viewport.
- El Process Guard dejo Chrome suspendido al retirar el lease DEV; es el estado
  terminal fail-close esperado, no una perdida de datos.

No se afirma PASS de Google Images, Fravega, Mimo, selective R3.1, scroll,
rotation ni Chrome normality porque el gate controlado anterior los bloqueo.

## HEALTH / ROLLBACK

Postflight del harness:

```text
app crash/ANR/lowMemory=0/0/0
chrome crash/ANR/lowMemory=0/0/0
failures=0
proxyQueueRejects=0
protectFailure=0
QUIC/direct TCP attempts=0/0
ownedFdResources=0
activeProtectedUdpSockets=0
transportRuntime=ready
labServiceStopped=true
rotation restored=accelerometer 1 / user 0
```

Device Owner y Affiliated permanecieron vigentes; Accessibility quedo enabled
y bound; inode, datos y firma fueron preservados. Las lineas seleccionadas de
Device Policy permanecieron identicas. El hash del dump completo cambio por
estado dinamico, sin drift en las politicas seleccionadas.

## ROOT CAUSE / RESIDUAL

Clasificacion final:

```text
FOREGROUND_READY_EVENT_SOURCE_UNAVAILABLE
```

En este A23, Chrome 152 no expuso el foco programatico del beacon DOM como un
`TYPE_VIEW_FOCUSED` utilizable despues del claim. Ya se agotaron en este frente
las identidades no-raster respaldadas por la Accessibility publica disponible:
marker pasivo, ElementInternals, host protegido y source exacto de un control
nativo en ShadowRoot cerrado. Aceptar un evento anterior, un scan posterior o
el WebView/root sin el source inicial reintroduciria replay o binding cruzado.

El Byte Gate por body sigue siendo valido y demostro cero raw BLOCK/UNKNOWN,
pero stock Chrome no puede retirar de forma segura la cortina del documento
foreground bajo las restricciones aprobadas. El siguiente paso requiere una
decision arquitectonica o de producto; no corresponde seguir variando markers
ni volver a raster/timing.

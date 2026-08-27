# CHROME-VISUAL-SHIELD-13B-R1-OWNERSHIP-01

Fecha: 2026-08-27. Dispositivo físico: Samsung A23 `SM-A235M`, Android
14/API 34.

## STATUS

**PASS DEV361 — PENDING CHATGPT FINAL REVIEW.** Se cerró la ventana de pérdida
de ownership entre el callback de `takeScreenshotOfWindow()` y el consumo de la
continuación cancelable. La regresión determinista y el stress físico terminan
con todos los recursos en cero. Este ticket no reanuda R1-NORM-PROBE ni cambia
GloshIA, clasificación, release authority, 11B o transporte.

## BASE / FUNCTIONAL / REVIEW

- Base funcional remota verificada:
  `review/chrome-visual-shield-13b-r1-viewport-automated` en
  `001be18d76418a1f5e4b54dc32d7c385711c2f37`.
- Los commits diagnósticos `88804188c9e100f1f92165f95bd5a7308b43d6e4`
  y `652727e20be61d8bff7919afdfbdf67dc7ff9d31` no forman parte del lineage.
- Worktree/rama dedicada:
  `work/chrome-visual-shield-13b-r1-ownership-01`.
- Functional SHA:
  `e4d27df1f4eb6a205635988bfc0592fcc21737cf`.
- Review final:
  `review/chrome-visual-shield-13b-r1-ownership-01-final`; su HEAD es el
  commit evidence-only que contiene este documento y se verifica por
  `git ls-remote` en el handoff.
- Delta funcional: `ChromeWindowCapture.kt`, un test focalizado nuevo y el
  incremento DEV `359 -> 361`. No hay otros archivos funcionales.

## ROOT CAUSE / FIX

La implementación anterior hacía:

```text
continuation.isActive
-> continuation.resume(Captured(frame))
```

La cancelación podía ganar después del chequeo y antes de que la coroutine
consumiera el valor reanudado. El frame ya contabilizado quedaba sin un owner
capaz de cerrarlo. Esto explica el estado físico previo `acquired=11`,
`closed=10`, `outstanding=1`, aun con `workIdle=true`.

El fix transfiere el valor con el mecanismo nativo de
`CancellableContinuation.resume(value, onCancellation)`. Si la cancelación
gana antes del consumo, `onCancellation` cierra exactamente ese frame. Si la
continuación ya estaba inactiva al llegar el callback, el recurso se cierra de
inmediato. El consumo normal mantiene el ownership del caller. La idempotencia
existente de `ChromeWindowFrame.close()` permanece basada en `AtomicBoolean`.

No se añadieron contadores correctivos, delays, GC, polling ni cleanup tardío
en STOP.

## DETERMINISTIC REGRESSION

`ChromeWindowCaptureOwnershipTest` ejecutó 4 tests, 0 fallos, 0 errores:

1. callback adquiere y reanuda; el dispatcher queda bloqueado; se cancela antes
   del consumo; el handler de cancelación cierra exactamente una vez;
2. cancelación anterior al callback cierra el recurso al llegar;
3. consumo normal transfiere ownership al caller, que lo cierra una vez;
4. failure sin frame no crea ni intenta cerrar un recurso.

El primer test usa una coroutine `UNDISPATCHED` y un dispatcher de un solo hilo
bloqueado por latches. Así reproduce la ventana exacta sin temporizadores ni
races probabilísticas. Un `close()` adicional confirma que doble cierre sigue
siendo inocuo.

## AUTOMATED VALIDATION

Todos los comandos finales terminaron con exit code `0`:

```text
:feature-accessibility:testDebugUnitTest
  --tests ChromeWindowCaptureOwnershipTest
  4 tests, 0 failures/errors

:feature-accessibility:testDebugUnitTest
  --tests ChromeWindowCaptureOwnershipTest
  --tests ChromeVisualShield*
:feature-accessibility:ktlintCheck
  52 tests, 0 failures/errors; BUILD SUCCESSFUL

:app-user:compileDevDebugKotlin
:app-user:lintDevDebug
:app-user:assembleDevDebug
  BUILD SUCCESSFUL in 2m27s; 821 tasks

git diff --check
  PASS
```

Los 48 tests `ChromeVisualShield*` incluyen ownership, identity, exposure,
decision, viewport y coordinator. Lint final produjo cero issues. Una única
infracción de formato introducida en el test nuevo apareció en la primera
corrida de ktlint; se corrigió localmente sin reformateo global y el gate final
quedó verde.

## VERSION / APK

- Package: `com.contentfilter.user.dev`.
- `versionCode=361`, `versionName=1.0.1-dev`.
- APK: `app-user/build/outputs/apk/dev/debug/app-user-dev-debug.apk`.
- Tamaño: `158992113` bytes.
- SHA-256:
  `2fbad1ed84bbaac2c62b170915e59cd7c574c71a72545e6049bdade31dd3f18b`.
- Certificado instalado y APK construido:
  `d51bc0dabd280ce1b0f098ae168eb57758faeba301156cde835737835f8a8832`.
- Instalación física: `adb install -r`, `Success`; update in-place desde DEV360.
- `ceDataInode=1239519` antes y después.

## PHYSICAL OWNERSHIP GATE

Dispositivo y entorno:

```text
serial=R58T34V31AE
model=SM-A235M
android=14 api=34
chrome=152.0.7977.64 versionCode=797706404
```

Se inició una única sesión válida del data-plane y Visual Shield. El stress
ejecutó ocho ciclos de navegación alternada más cancelación inmediata, una
transición `portrait -> landscape -> portrait` y un STOP con trabajo recién
cancelado. Después de cada punto terminal se consultó dos veces, separado por
un segundo:

```text
fullFrameOutstanding=0
cropOutstanding=0
inferenceOutstanding=0
workIdle=true
```

Resultado terminal:

```text
phase=Inactive
fullFrameAcquired=10
fullFrameClosed=10
fullFrameOutstanding=0
cropCreated=4
cropClosed=4
cropOutstanding=0
inferenceStarted=3
inferenceCompleted=3
inferenceOutstanding=0
captureCancelled=8
staleDropped=2
workIdle=true
rawPersisted=0
rawUploaded=0
probeMode=false
```

`STRESS_GATE_PASS=true`. No se ejecutó ni incorporó el normalized probe. Los
dos releases LAB observados pertenecen a la ruta R1 preexistente durante los
controles no-probe; este delta no toca ni amplía su autoridad.

## HEALTH / PRESERVATION

- Ventana completa del gate: crash/ANR/OOM `0/0/0`; sin `SIGTRAP`.
- Data-plane antes del rollback: `failures=0`, `proxyQueueRejects=0`,
  `protectFailure=0`, inference `queueRejects=0`, `quicAttempts=0`,
  `directTcpAttempts=0`.
- Device Owner y `Affiliated` preservados.
- Accessibility enabled y bound; sin servicio crashed/binding.
- Datos, firma y `ceDataInode=1239519` preservados.
- Rotación restaurada a `accelerometer_rotation=1`, `user_rotation=0`.

## ROLLBACK

El cierre hizo STOP del data-plane y dejó:

```text
Visual Shield phase=Inactive
fullFrameOutstanding=0 cropOutstanding=0 inferenceOutstanding=0
proxy cacheEntries=0 cleanup=complete
rollback=complete proxy=cleared ca=removed
status=inactive
ownedFdResources=0
activeProtectedUdpSockets=0
transportRuntime=ready
chromeSuspended=true
```

`chromeSuspended=true` y la superficie gris posterior al STOP son el fail-close
esperado del laboratorio; no son un crash ni una pérdida de datos.

## RESIDUALS

Sin residual para el defecto de ownership: todas las cuentas terminales
convergen y no reaparece el `outstanding=1` físico. R1 principal y
R1-NORM-PROBE continúan bloqueados/pausados y no fueron reanudados. La cobertura
renderer-local landscape y el descubrimiento de regiones productivas siguen
siendo tickets arquitectónicos separados antes de probar sitios reales como
Frávega o Mimo de extremo a extremo.

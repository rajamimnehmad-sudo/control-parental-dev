# CHROME-H20-GLOBAL-WEB-NORMALITY-05B

Fecha: 2026-09-02. Dispositivo físico: Samsung A23 `SM-A235M`, Android
14/API 34. Gate diagnóstico/evidence-only sobre H20 ON y GloshIA R3.1 en modo
selective.

## STATUS

**PASS TÉCNICO — H20 GLOBAL WEB NORMALITY / GLOSHIA R3.1 SELECTIVE.** La
corrida limpia completó 46 min 10 s de monitoreo formal continuo, superó la
matriz pública y las maniobras reales, no mostró runaway ni cruzó un early-stop,
y mantuvo los contratos de seguridad. No hubo crash, ANR, OOM ni LOW_MEMORY
nuevo. El cierre LAB hizo rollback completo.

## ANCHORS

- Task: `CHROME-H20-GLOBAL-WEB-NORMALITY-05B`.
- Base funcional congelada:
  `8abd7762bc18261cff9c7512f9821da7b233a4b7`.
- Governance SHA resuelto desde el HEAD remoto de `main` y fijado para el lote:
  `9323b126dde3b91e21e2136a11d087fa3c1f0ca1`.
- Central observado al preflight:
  `1f6716633f3ca8a1496b4dd2392379558ffe6dd7`; reconciliación de apertura
  publicada en `6a6dcc15acb89e3e87c2f558495d604738369296`.
- Rama/worktree aislados: `work/chrome-h20-global-web-normality-05b` en
  `/Users/yejielnehmad/Developer/glosh-chrome-h20-global-web-normality-05b`.
- Writer único y rutas persistentes verificadas: Codex Chrome 05B,
  `docs/areas/protection/**`.
- No hubo cambios funcionales, build, compile, version bump, reinstalación,
  limpieza de Chrome ni reset de bootstrap.

## APK / BASELINE

- Package: `com.contentfilter.user.dev`.
- APK ya instalado: DEV420 (`versionCode=420`).
- SHA-256 verificado extrayendo el APK instalado:
  `3273aeed0579ac7fe20fdad05041573843f6d50beeeb609a00800b16abd6bac1`.
- `resetCount=3` antes del gate y sin modificación durante la prueba.
- Device Owner y Accessibility presentes; proxy global, always-on y lockdown
  estaban vacíos antes de iniciar LAB.
- Baseline térmico `0`; no había crash, ANR, OOM ni LOW_MEMORY nuevo. El único
  LOW_MEMORY visible era histórico, del 2026-08-31.

## CONTROLLED SELECTIVE / AUTHORITY

El control selectivo fresco en `glosh-photos.test` alcanzó
`PresentationReady`. Se observaron recursos SAFE originales, BLOCK sustituidos
y UNKNOWN sustituidos, con:

```text
rawBlocked=0
rawUnknown=0
proxyQueueRejects=0
protectFailure=0
quicAttempts=0
directTcpAttempts=0
resetCount=3
```

El contrato de documento mantuvo `SELF_READY` aceptado, release y continuación
del parser completos, sin rechazo de ready ni parser fail-close. El boundary de
Service Worker no adquirió registro/controlador ni presentó actividad anómala.

## PUBLIC WEB MATRIX

Ocho páginas públicas exitosas y usables cubrieron en conjunto las clases del
ticket:

| Página | Cobertura observada |
| --- | --- |
| Wikipedia search | Search real, formulario y resultados renderizados |
| Wikipedia: Web performance | Referencia, texto y navegación larga |
| Infobae | Editorial/dinámica, SAFE + placeholder, multi-origin |
| Frávega | Ecommerce, lazy/long scroll, responsive images, CDN |
| MDN Web Performance | Referencia/documentación pública |
| Wikimedia Commons Featured pictures | Image-heavy y placeholders |
| The Guardian International | Editorial, SAFE + BLOCK |
| npm React package | UI de búsqueda y documento dinámico |

Se usaron identidades/query frescas cuando fue útil para no atribuir resultados
al negative decode cache histórico. No se borraron datos.

Intentos adicionales no contados en la matriz: Google devolvió su `/sorry/`;
DuckDuckGo y React.dev quedaron en blanco; Bing hizo fail-close de documento;
Mimo hizo fail-close de documento en dos intentos; GitHub cargó parcialmente y
Stack Overflow quedó en blanco. Ninguno produjo exposición raw ni pérdida de
authority.

## REAL NAVIGATION

- Long scroll/lazy: fixture controlado, Infobae y Frávega hasta contenido tardío
  y footer.
- Reload, back y forward: fixture `root -> /second -> back -> forward -> F5`,
  preservando el documento y el scroll al volver.
- Tabs y warm/history: conteo visible de tabs, reaperturas con application id
  estable y navegación repetida.
- Background/foreground: Home y retorno explícito a Chrome, sin pérdida de
  authority.
- Rotación `portrait -> landscape -> portrait`, con render responsive verificado
  y restauración de `accelerometer_rotation=1`, `user_rotation=0`.
- Formulario local editable: newsletter de Frávega recibió
  `gate05b@example.com` sin submit.

## RENDERER SNAPSHOTS

Snapshot temprano, luego de navegación/scroll dinámico en Infobae:

```text
reports=5 rejected=0
callbacks=113 records=6318 child=305 attribute=76 maxRecords=1394
scanCalls=297 scanRoot=10 scanNodes=1359 maxScanNodes=1053
scanMicros=49340 maxScanMicros=27599
observerChildScans=209 observerAttributeScans=0
guarded=35 markup=43 shadow=0 initial=10
sanitizeElement=1571 sanitizeContainer=137
img=240 source=417 svg=106 iframe=4 canvas=0 video=0
svgRecords=70 shadowRoots/Observers/Callbacks/Records/Scans=0/0/0/0/0
ensureStyle=118 ensureCurtain=128 internalMutations=0
```

Familias tempranas:

```text
factory calls=79 scans=0 direct=79 nodes=0 micros=1694 max=1000
domGuard calls=35 scans=35 direct=30 roots=64/0/0/1 nodes=34 micros=1489 max=100
markup calls=43 scans=43 direct=0 roots=26/17/0/0 nodes=27 micros=1287 max=99
```

Snapshot acumulado representativo tras la carga/scroll dinámico de Frávega:

```text
reports=11 rejected=0
callbacks=264 records=25966 child=1437 attribute=882 maxRecords=3091
scanCalls=67338 scanRoot=22 scanNodes=26005 maxScanNodes=1265
scanMicros=587646 maxScanMicros=71000
observerChildScans=495 observerAttributeScans=0
guarded=22348 markup=44439 shadow=34 initial=22
sanitizeElement=72083 sanitizeContainer=1320
img=524 source=633 svg=1682 iframe=16 canvas=0 video=0
svgRecords=852 shadowRoots/Observers/Callbacks/Records/Scans=26/26/4/10/34
ensureStyle=275 ensureCurtain=297 internalMutations=448
```

El volumen grande de factory/markup corresponde a creación directa de nodos
durante la aplicación dinámica; no produjo un loop sostenido. Los máximos por
operación permanecieron acotados (`factory=3.5 ms`, `domGuard=2.4 ms`,
`markup=2.3 ms`).

## PERFORMANCE / HEALTH

- Período formal: 09:04:35–09:50:45, `46 min 10 s` continuos; muestras cada
  ~34 s (92 estados programados, 82 completados hasta el cierre deliberado tras
  superar el mínimo).
- Browser: RSS aproximadamente 192–271 MiB durante el tramo estable; PSS
  aproximadamente 163–196 MiB después de cargas. Snapshot final:
  `RSS=241520 KiB`, `PSS=167024 KiB`.
- Renderers ordinarios: RSS aproximadamente 110–240 MiB, PSS aproximadamente
  19–130 MiB, con estabilización final en CPU `0%`.
- Renderer RSS máximo observado: ~363 MiB, lejos de ~650 MiB y sin crecimiento
  sostenido.
- `MemAvailable` mínimo: `625088 KiB` (~610 MiB), por encima de ~400 MiB y con
  recuperación repetida; cierre entre ~804 y ~823 MiB.
- Thermal status: `0` en todas las muestras. Batería 93% -> 88%, temperatura
  final 25,4 °C, sin throttling.
- ApplicationExitInfo: `0` crash, `0` ANR, `0` OOM y `0` LOW_MEMORY nuevo. Las
  salidas de renderers fueron `ISOLATED NOT NEEDED`/`EXIT_SELF`; el LOW_MEMORY
  de 569 MiB PSS/669 MiB RSS seguía fechado 2026-08-31 y no pertenece al gate.

Durante una navegación activa a React apareció un renderer transitorio con
`112%` y `103%` en dos muestras, RSS máximo observado de ~363 MiB y PSS de
~259 MiB. No fue CPU sostenido sin interacción: el proceso terminó como
`ISOLATED NOT NEEDED` y memoria/CPU se recuperaron. Ningún renderer se acercó al
early-stop de ~650 MiB. La menor `MemAvailable` observada hasta entonces fue
~610 MiB y se recuperó. Thermal permaneció en `0`.

## INITIAL CONTROL ABORT

Un primer arranque de control quedó suspendido inmediatamente cuando el A23
entró en keyguard y Accessibility reportó `accessibility_lost`. Antes del STOP
se capturaron PID/tab, STATUS y memoria: browser ~231 MiB RSS/~135 MiB PSS,
renderer reports `0`, `MemAvailable` ~1.09 GiB, thermal `0`, raw/protect/bypass
en cero y sin crash/ANR/OOM/LOW_MEMORY. El rollback fue completo y Accessibility
se reenlazó inmediatamente. Se realizó una sola corrida limpia posterior; el
evento no se repitió.

## FINAL SECURITY

Estado acumulado al cierre:

```text
safeRaw=208
blockedReplaced=23
unknownReplaced=93
unsupportedReplaced=121
rawBlocked=0 rawUnknown=0
queueRejects=0 timeouts=0 proxyQueueRejects=0
protectSuccess=120 protectFailure=0
quicAttempts=0 directTcpAttempts=0
documentsTransformed=22 documentsFailClosed=3 outstanding=0
selfReady=22/22 rejected=0
selfShieldReleaseCompleted=22 parserContinued=22
rendererReports=11 rendererRejected=0
resetCount=3 chromeSuspended=false (antes de STOP)
```

Los tres fail-close de documento correspondieron a intentos públicos no
contados (Bing y Mimo x2); se comportaron de acuerdo con el contrato y no
expusieron visuales raw. Los 93 `imageBodyAdmissionRejects` de ráfagas lazy se
convirtieron en UNKNOWN placeholder; no son queue rejects del motor y
`queueRejects/timeouts` permanecieron `0/0`. Los 379 failures acumulados fueron
principalmente cierres TLS/EOF del cliente durante navegación pública y no
causaron pérdida de authority.

El estado SW permaneció inerte e intacto: ningún documento/script/probe de SW,
registro, controlador ni fetch sintético fue adquirido. El reset y el código
funcional eran los ya revisados en 05A.

## FINAL / ROLLBACK

Después de congelar STATUS, memoria, batería/térmico, Accessibility y
ApplicationExitInfo:

- STOP manual produjo `chromeSuspended=true` fail-close y dejó
  `readyTokensOutstanding=0`.
- Proxy detenido, cache LAB vacía, proxy global limpio y CA removida.
- VPN restaurada; attestation revocada y full-tunnel desactivado.
- Always-on/lockdown y proxy global: `null/null/null`.
- Accessibility continuó habilitado y enlazado; Device Owner no cambió.
- `resetCount=3` preservado.
- Ajustes temporales del laboratorio restaurados exactamente:
  `screen_off_timeout=600000`, `stay_on_while_plugged_in=15`,
  `accelerometer_rotation=1`, `user_rotation=0`.

No queda cambio funcional ni estado LAB activo. Los sitios adicionales que no
alcanzaron normalidad usable quedan como residual de compatibilidad pública,
sin invalidar la matriz mínima ni los contratos de seguridad de este gate.

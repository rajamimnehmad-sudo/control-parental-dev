# CHROME-ANDROID-EXTENSION-PROVENANCE-13B-P — CORRECTIVE PHASE 2

Fecha: 2026-08-26. Tipo: `FEASIBILITY-ONLY`.

## Estado

**BLOCKED — Caso A.** La corrección Android quedó demostrada: el Device Owner
aplicó `ExtensionInstallForcelist` como `java.lang.String` con un JSON array de
strings, Chrome vivo recibió esa representación y
`cr_CombinedPProvider` hizo `flushPolicies()`. El servidor y `adb reverse`
también pasaron preflight desde Mac y dispositivo.

Durante una ventana válida de 3 minutos 34 segundos Chrome no pidió
`/update.xml`. Por contrato se detuvo aquí. No hubo GET del CRX, instalación,
heartbeat, `installed` ni `startup`; tampoco se avanzó a identidad documental,
regiones, `chrome.debugger`, captura, Service Worker/Cache provenance, GloshIA
ni 13B productivo.

## Coordinación, base y alcance

- Coordinación Central verificada:
  `71f726b16c255b77e62ce92d26870c76b038557a`.
- Base remota verificada:
  `review/chrome-android-extension-provenance-13b-p-triage` en
  `4b8aa02f290079b285b5e414eef7d3ed6b811da8`.
- Worktree aislado:
  `work/chrome-android-extension-provenance-13b-p-phase2`.
- Functional SHA:
  `84f1039039eec972413de23139426b70aa7ed4ce`.
- Rama de revisión prevista por resultado:
  `review/chrome-android-extension-provenance-13b-p-phase2-triage`.
- DEV357 se verificó libre antes de usarlo.

El delta sólo corrige el contrato/persistencia DEV de la policy, tests
focalizados, observabilidad y transporte del harness, identidad pública de la
extensión de laboratorio y `versionCode`. No modifica 11A/11B funcional,
GloshIA, VPN/HEV/DNS, Process Death Guard, Accessibility productiva, Device
Owner productivo, DAG, Admin, backend ni 13B.

## Corrección Android

La base usaba `Bundle.putStringArray()`. El delta:

- lee el valor Android correcto como `String`;
- parsea estrictamente un JSON array cuyos elementos deben ser strings;
- rechaza JSON inválido, tipo no-array, elementos no-string y trailing data;
- preserva en orden entradas de otros IDs;
- reemplaza únicamente el ID de laboratorio;
- acepta un `String[]` sólo como migración del harness DEV anterior y registra
  `legacy_type_detected=true`;
- persiste con `Bundle.putString()`, nunca `putStringArray()`;
- relee el Bundle aplicado y falla si el tipo runtime no es `String`;
- conserva el snapshot completo previo serializado por `Parcel` para restore
  exacto.

Tests deterministas cubren entrada única, múltiples entradas, reemplazo del
mismo ID, preservación de otro ID, migración legacy, JSON inválido fail-close,
retención literal del valor previo y tipo de salida `String`.

## Extensión y artefactos

La clave privada temporal del ticket previo había sido eliminada como exigía
su restore. No es derivable desde la clave pública. Para firmar un CRX válido
sin publicar secretos se rotó sólo la identidad pública del laboratorio:

- ID: `ilkhmclganbfbpefgbibpgjlfjgffpdf`.
- SHA-256 compuesto de las fuentes de `extension/`, sobre la lista ordenada de
  hashes por archivo:
  `730cac3743791f59da3e58b3740f4e12e25813e3cb10395b2982e484c263d5f9`.
- CRX: `5592` bytes; SHA-256
  `fb9658daf11c3fa61892b4e3a13f2ec831d7f67aa5e3fb14e659a0f6aed2239b`.
- `update.xml`: `263` bytes; SHA-256
  `805c77687b89201a5d3498cfe4e846f63236a2b169b68f12da91a5af261eb228`.
- La clave privada y el CRX son temporales; no están versionados.

No cambió el comportamiento de la extensión. Sigue siendo MV3, código local,
service worker módulo, content script estático en `document_start`,
`all_frames=true`, `match_origin_as_fallback=true`, `world=ISOLATED` y hosts
acotados.

## Validación automática

PASS, exit code `0`:

- `ChromeExtensionPolicyContractTest`.
- `:app-user:compileDevDebugKotlin`.
- ktlint real sobre los tres Kotlin tocados, mediante una tarea temporal que
  reutiliza el CLI ya cacheado; no se confundió deuda de la base con el delta.
- `:app-user:lintDevDebug`.
- `:app-user:assembleDevDebug`.
- `node --check bridge-server.mjs`.
- `verify-source.mjs`.
- `sh -n run-policy-gate.sh`.
- test local de access log: `/health`, `/update.xml` y `/extension.crx` con
  timestamp, método, path, User-Agent, remote address y status.
- `git diff --check`.

APK DEV357:

- Tamaño: `158926449` bytes.
- SHA-256:
  `ffb8b329c6915a0cbc0090012a12070c46f3f83292997eeff9a876220c705eea`.
- Instalación update in-place: `adb install -r`, `Success`.
- Glosh `ceDataInode=1239519` y Chrome `ceDataInode=6090` permanecieron
  estables.

## A23 y preflight

- Dispositivo: `SM-A235M`, Android 14.
- Chrome: `152.0.7977.64`, versionCode `797706404`.
- Device Owner y usuario Affiliated verificados.
- Accessibility enabled y `ProtectorAccessibilityService` bound.
- Data-plane en `PresentationReady`, Chrome no suspendido y navegación control
  operativa antes del APPLY válido.

El puerto 8765 del Mac ya pertenecía a procesos ajenos; no se los tocó. El
harness mantuvo la URL Android exacta en `127.0.0.1:8765`, sirvió efímeramente
en el puerto Mac 18765 y aplicó:

```text
adb reverse tcp:8765 tcp:18765
```

Preflight verificable:

```text
13:55:26.504Z GET /health      UA=curl/8.7.1 status=200
13:55:26.601Z GET /health      UA=<device shell> status=200
13:55:29.656Z GET /update.xml  UA=curl/8.7.1 status=200
13:55:29.663Z GET /extension.crx UA=curl/8.7.1 status=200
```

Los archivos descargados en preflight fueron comparados byte a byte con los
artefactos y sus hashes coincidieron. El access log no registra nonce, cuerpos
de eventos ni datos sensibles.

El primer preflight se detuvo antes de APPLY porque `toybox nc` cerraba stdin
antes de leer la respuesta. Se corrigió manteniendo el socket abierto tres
segundos. Una aplicación posterior de 30 segundos fue declarada inconclusa y
restaurada: Chrome estaba `stopped=true` y no hubo refresh del provider. La
única repetición válida comenzó con Chrome vivo y data-plane limpio.

## Policy antes, durante y después

Bundle canónico previo:

```json
{"ProxySettings":"{\"ProxyMode\":\"fixed_servers\",\"ProxyServer\":\"127.0.0.1:8877\",\"ProxyBypassList\":\"\"}"}
```

SHA-256 canónico:
`0e51ee10025e9a7fc01d9a456bb2bac9f78fa6a8233d6e51daa6638cad5b219e`.

Valor exacto de `ExtensionInstallForcelist` aplicado por Android, tipo runtime
`java.lang.String`:

```json
["ilkhmclganbfbpefgbibpgjlfjgffpdf;http://127.0.0.1:8765/update.xml"]
```

Android `org.json` puede representar los slash como `\/`; la semántica y el
valor decodificado son los anteriores. Evidencia de aplicación y lectura de
Chrome:

```text
10:55:32.326 cr_CombinedPProvider #setPolicy()
  ExtensionInstallForcelist -> ["ilkh...fpdf;http:\/\/127.0.0.1:8765\/update.xml"]
10:55:32.327 cr_CombinedPProvider #setPolicy() ProxySettings -> {...}
10:55:32.327 cr_CombinedPProvider #flushPolicies()
10:55:32.374 ChromeExtensionPolicy phase=applied
  legacy_type_detected=false
  types={"ExtensionInstallForcelist":"java.lang.String","ProxySettings":"java.lang.String"}
```

No se agregaron `ExtensionSettings`, `BlockExternalExtensions`,
`ExtensionAllowedTypes` ni `LoopbackNetworkAllowedForUrls`.

Restore:

```text
10:59:06.277 phase=restore result=success reason=explicit bundle={"ProxySettings":...}
10:59:28.563 phase=status active=false current={"ProxySettings":...} snapshot=<none>
10:59:46.614 phase=status active=false current={} snapshot=<none>
```

El segundo status corresponde al rollback posterior del data-plane, que retiró
su propio `ProxySettings`. La force-list de laboratorio no quedó aplicada.

## Resultado del updater

Ventana válida: desde el refresh de `10:55:32.326` hasta el restore de
`10:59:06.277`, 3 minutos 34 segundos. Chrome permaneció vivo y el watchdog
recibió heartbeats cada aproximadamente 15 segundos.

El access log final contiene exactamente cuatro filas, todas de preflight. No
existe un User-Agent Chrome ni un acceso posterior al APPLY:

- `UPDATE.XML GET`: **NO**.
- `CRX GET`: **NO**.
- `EXTENSION INSTALLED`: **NO DEMOSTRADO**.
- `HEARTBEAT/INSTALLED/STARTUP`: **NO**; el event log no fue creado.

Chrome sí realizó su POST ordinario a `update.googleapis.com` por el data-plane
a las `10:55:28`, con status 200. Esto, junto con el provider refresh, reduce el
blocker: reachability del reverse y activación de policy fueron positivas; el
updater de extensiones no despachó la solicitud al update URL dentro de la
ventana.

## Salud y preservación

- Ventana válida: crash/ANR/OOM nuevos `0/0/0`; crash buffer vacío.
- Data-plane antes de rollback: `failures=0`, `proxyQueueRejects=0`,
  `protectFailure=0`, `queueRejects=0`, `quicAttempts=0`,
  `directTcpAttempts=0`, `recursion=0`.
- 11B no recibió cambios funcionales; navegación interceptable ordinaria pasó
  por el proxy. No se ejecutó un gate 11B ampliado porque este delta debía
  detenerse en Fase 2.
- El único exit-info nuevo al rollback fue un sandbox renderer
  `OTHER KILLS BY SYSTEM / ISOLATED NOT NEEDED`, no crash, ANR ni OOM.
- Data-plane final `Stopped`; Chrome suspendido fail-close; proxy/CA/cache
  retirados.
- Transporte final: `status=inactive`, `ownedFdResources=0`,
  `activeProtectedUdpSockets=0`, `transportRuntime=ready`.
- Device Owner, Affiliated y Accessibility enabled/bound preservados.
- Inodes preservados: Glosh `1239519`, Chrome `6090`.
- Bundle final `{}`, snapshot ausente, bridge detenido y `adb reverse` vacío.
- No se borraron perfil, cache, historial, cuentas ni datos de Chrome.

## Blocker exacto

Chrome Android 152 acepta y publica la policy Android correctamente codificada,
pero no inicia el fetch de `update.xml` durante la ventana observada. El
siguiente delta, si ChatGPT lo autoriza, debe aislar **activación/eligibilidad o
cadencia del updater de extensiones** sin añadir políticas por tanteo. No
corresponde avanzar a las fases 3–9 ni a 13B mientras no exista un GET de
`update.xml`.

## Referencias primarias

- Chrome Enterprise, `ExtensionInstallForcelist`:
  https://chromeenterprise.google/policies/extension-install-forcelist/
- Chromium generated policy docs: `List of strings [Android:string] (encoded
  as a JSON string)`:
  https://chromium.googlesource.com/chromium/src/out/+/HEAD/android-Debug/gen/chrome/app/policy/common/html/en-US/chrome_policy_list.html


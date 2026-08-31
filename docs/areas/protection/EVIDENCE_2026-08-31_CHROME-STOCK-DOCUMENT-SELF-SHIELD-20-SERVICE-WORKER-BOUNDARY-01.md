# CHROME-STOCK-DOCUMENT-SELF-SHIELD-20-SERVICE-WORKER-BOUNDARY-01

## STATUS

`BLOCKED / SERVICE_WORKER_BYPASSES_DOCUMENT_TRANSFORM`

El gate fue exclusivamente diagnóstico. No se cambió la autoridad H20, el Byte
Gate, GloshIA R3.1 ni el modelo. Selective R3.1 no se ejecutó.

## REFS

- Base funcional: `6e8a6dab2ac625dcda18b2a5c1a917661a6b4489`.
- H20 review de entrada, evidence-only:
  `review/chrome-stock-document-self-shield-20-feasibility-01-final`
  @ `9dc38cdd97074503e5cca1e5c85dd7e2fe3b5f9f`.
- Delta funcional del gate: `07112e2d`.
- APK físico: DEV411, SHA-256
  `126f160aedbaeb993d1ccd85d2957f621efd9a44f89ca8cc1e657f50a74d69cc`.
- A23: `SM-A235M`, Android 14, Chrome `152.0.7977.64`.
- Modelo preservado: GloshIA Visual R3.1,
  `c8b64af8092d3718c58736a511c996d0d443dacf3eaa74620b1e5af439a3cd48`.

## IMPLEMENTACIÓN DIAGNÓSTICA

Se agregó un fixture DEV acotado, sin autoridad, con un Service Worker
root-scope real y tres modos: `PASS_THROUGH`, `SYNTHETIC_SELF_READY` y
`SYNTHETIC_NAVIGATION`. Registra sólo contadores y códigos técnicos. No guarda
HTML, tokens, cuerpos de usuario ni píxeles.

La primera APK, DEV410, dejó inconclusa la medición porque el worker emitía
`PASS_THROUGH` donde el contrato aceptaba `PASSTHROUGH`. Se corrigió sólo esa
telemetría determinista, se agregó la aserción exacta y se repitió una única
sesión válida con DEV411.

## AUTOMATED

PASS:

- `ChromeServiceWorkerBoundaryFixtureTest`;
- focalizados H20 de fixture, bootstrap, transformer y ready endpoint;
- `compileDevDebugKotlin`;
- `lintDevDebug`;
- `assembleDevDebug`;
- `ktlintDevSourceSetCheck`;
- `git diff --check`.

`ktlintTestDevSourceSetCheck` conserva una deuda ajena preexistente en
`ChromeHttp1ResponseWriterTest.kt:133`; el archivo nuevo no tuvo hallazgos.

## SW CONTROL PROOF

El SW se instaló con H20 desactivado y sin limpiar datos de Chrome:

- `WORKER_SCRIPTS=1`;
- `CONTROLLER_PRESENT=1`;
- UI controlada: `SW_CONTROLLER=YES`;
- `skipWaiting()` + `clients.claim()` confirmados;
- scope `/` aceptado mediante `Service-Worker-Allowed: /`.

Luego se reinició H20 sin reset de Chrome (`bootstrapResetCount=2` sin cambio),
manteniendo el controlador preexistente.

## A — PASS_THROUGH

PASS de compatibilidad:

- SW navigation fetch real: `NAV_FETCHES +1`, `PASSTHROUGH`;
- documento proxy transformado: `mediaDocumentsTransformed +1`;
- registry: `issued +1`, `claims +1`;
- SELF_READY nativo: requests/accepted `+1/+1`;
- curtain release / parser continuation / original script: `+1/+1/+1`;
- `controller != null` en el documento;
- failures, proxy rejects, protect failures y bypass: cero.

## B — SYNTHETIC_SELF_READY

La falsificación no reprodujo.

Se observaron dos eventos de navegación del SW con el modo B y controlador
real, pero `SELF_READY_FETCHES=0`. En la navegación current transformada:

- proxy/native SELF_READY `+1`;
- registry claim `+1`;
- release/parser/original script `+1/+1/+1`;
- synthetic SELF_READY `+0`.

El bootstrap usa XHR síncrono y exige simultáneamente `responseURL` exacta y
status `204` antes de continuar. Como el camino current avanzó y el proxy/registry
recibieron el claim, la respuesta observada por XHR fue la respuesta nativa, no
la respuesta sintética del SW. En Chrome 152 de este A23, esta llamada no produjo
un fetch event del Service Worker.

Conclusión B: `SERVICE_WORKER_INTERPOSES_CAPABILITY_RESPONSE` no reprodujo. El
transporte actual protege el claim, pero esto por sí solo no protege el documento.

## C — SYNTHETIC_NAVIGATION

BLOCKED reproducido:

- controlador vigente: `SW_CONTROLLER=YES`;
- `NAV_FETCHES +1` con `SYNTHETIC`;
- `NAV_SYNTHETIC +1`;
- el sentinel `SW SYNTHETIC DOCUMENT SENTINEL` fue presentado por Chrome;
- SELF_READY permaneció sin delta;
- registry claims permaneció sin delta;
- release/parser/original-script H20 permanecieron sin delta;
- la UI mostró un documento controlado por SW que no contenía la cortina ni el
  bootstrap H20.

La captura de evidencia externa, no usada como autoridad, tuvo SHA-256
`3e2ca80676e455d5907505e872941fcab227143614ec17f1d8006ead20ab0c3f`.
El artefacto temporal se eliminó después de extraer el hash y verificar el
sentinel.

Durante C también hubo actividad proxy/especulativa que incrementó
`PROBE_DOCS`/`mediaDocumentsTransformed`. Eso no autorizó el documento visible:
la respuesta que Chrome presentó fue inequívocamente la sintética del SW y no
produjo claim H20. Por lo tanto observar una navegación paralela en el proxy no
cierra esta frontera.

## ROOT CAUSE

`SERVICE_WORKER_BYPASSES_DOCUMENT_TRANSFORM`.

Un Service Worker root-scope ya controlador puede satisfacer la navegación con
HTML generado localmente/Cache Storage. Esos bytes no atraviesan el transformer
documental del proxy, por lo que no reciben parser-first curtain, Media Shield ni
SELF_READY. El Byte Gate de respuestas de red permanece correcto, pero no puede
autorizar HTML que no salió a la red.

## OPCIONES PARA REVIEW

1. **Transporte de capability no interponible por SW.** B demuestra que el XHR
   síncrono actual alcanzó al proxy/registry. Es valioso contra falsificación de
   SELF_READY, pero insuficiente: C puede omitir por completo el bootstrap y no
   necesita falsificar el capability.
2. **Reset completo + prohibición de nuevas registraciones como invariante de
   producto.** Puede cerrar la clase conocida si se demuestra desde un estado
   limpio que `registrations=0`, `controller=null`, el bootstrap bloquea toda
   registración futura y reinicios/BFCache/cache no restauran autoridad antigua.
   Coste: clear inicial y pérdida de compatibilidad con sitios que dependen de SW.
   No se seleccionó ni implementó en este ticket.
3. **Frontera browser-side verificable.** Requeriría un mecanismo de stock Chrome
   que impida o ateste respuestas de navegación de SW antes de presentarlas. No se
   identificó uno en este gate; debe investigarse como decisión arquitectónica,
   sin volver a screenshots ni elegir automáticamente una policy global.

## HEALTH / CLEANUP

- DEV411 fue update-in-place; `ceDataInode=1239519` antes/después.
- Device Owner y Accessibility permanecieron activos.
- crash/ANR/OOM: `0/0/0`.
- failures/proxyQueueRejects/protectFailure: `0/0/0`.
- QUIC/direct TCP bypass: `0/0`.
- La ruta `/web20sw/cleanup` se ejecutó (`CLEANUP_DOCS=1`) para unregister y
  borrar únicamente el cache del fixture.
- STOP produjo `rollback=complete`, proxy/CA retirados y cache de sesión limpio.

## RESIDUAL

H20 compatibility/Replace-All anterior permanece PASS PHYSICAL, pero la
autoridad de seguridad H20 y selective R3.1 continúan bloqueados hasta cerrar la
frontera de documentos servidos por un Service Worker preexistente/controlador.

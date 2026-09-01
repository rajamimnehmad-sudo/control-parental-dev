# CHROME-H20-ANR-CAUSAL-ATTRIBUTION-14B-01

## STATUS

`PASS DIAGNOSTIC / CAUSALLY ATTRIBUTED — H20 PERF/NORMALITY REMAINS BLOCKED`.

El ANR se reprodujo con H20 ON y no se reprodujo con H20 OFF usando el mismo
DEV414, A23, configuracion, secuencia y ventana de quince minutos. La evidencia
atribuye el incidente a amplificacion de memoria/CPU en los renderers de Chrome
especifica de la ruta H20 document/self-shield. No hay evidencia de saturacion
de la cola GloshIA ni de backpressure del proxy como causa primaria.

No se aplico tuning ni se modifico codigo.

## BASE / ARTIFACTS

- Base y Functional SHA:
  `c7d40a11f8f198a767514b48a538272296f3bfa9`.
- Review previo, evidence-only:
  `fefd87d9b665d004776b601543ac1dcdacc8f697`.
- APK: DEV414, SHA-256
  `1902b116ae4384a19f26f782bae4eb4161b56977d4f0108d711852eb70c021bb`.
- Dispositivo: Samsung SM-A235M, Android 14/API 34, Chrome oficial.
- Configuracion H20 invariable: cache `64`, concurrencia `1`, queue `2`,
  timeout `5000 ms`.
- Sin nueva APK, instalacion, clearApplicationUserData ni cambio de datos.

## PHASE 0 — PREVIOUS ANR

Se recupero la traza persistida exacta:

- ANR: `2026-08-31 21:52:05`;
- razon: input dispatch timeout, `10017 ms`, proceso browser Chrome;
- traza: `anr_2026-08-31-21-52-09-710`;
- renderer `19039`: exit reason `LOW_MEMORY`, PSS/RSS `560/638 MB`;
- otros renderers tambien terminaron por `LOW_MEMORY` alrededor del incidente;
- renderer observado inmediatamente antes: aproximadamente `467 MiB PSS /
  410 MiB RSS`, creciendo luego a `581/499 MiB`;
- browser: RSS aproximado `212 MiB`, swap `110 MiB`.

La traza del browser fue tomada despues de que el hilo principal habia vuelto
a `Looper.pollOnce`; por eso no identifica una funcion nativa bloqueante
individual. Si demuestra que el timeout coexistio con una ola de terminaciones
renderer por memoria.

## A/B CONTRACT

Ambas variantes usaron la misma secuencia automatizada:

- fixture SAFE controlado;
- Fravega;
- Mimo;
- Google Images sin consulta bloqueable;
- scroll repetido;
- tabs;
- back/forward mediante los mismos eventos;
- treinta muestras, una cada aproximadamente treinta segundos.

La tecla Back llevo al launcher en los indices `11` y `28` en ambas variantes;
la cobertura de back/forward no se usa como evidencia de normalidad, pero no
rompe la comparabilidad A/B.

## A — BASELINE H20 OFF

- Duracion: `15 min` completos.
- ANR: `0`.
- renderer exits `LOW_MEMORY`: `0` durante la corrida.
- browser RSS max muestreado: `258,408 KiB`.
- renderer RSS max muestreado: `322,716 KiB`.
- MemAvailable minimo: `699,748 KiB`.
- Glosh RSS max muestreado: `186,444 KiB`.
- AP max: `35.7 C`; thermal status `0`.
- H20 document transformer: `mediaDocumentsTransformed=0`.
- Self-shield: desactivado.

La autoridad primaria 11B siguio activa y, pese a procesar mas carga que B,
no produjo ANR:

- requests `694`;
- engine calls `216`;
- cache hits/misses/evictions `48/216/152`;
- inference p50/p95/p99 `136.145/531.534/549.329 ms`;
- decision p50/p95/p99 `155.424/572.176/730.339 ms`;
- proxy p50/p95/p99 `278.058/870.764/1150.056 ms`;
- inference/in-flight/queue peak `1/2/1`;
- queue rejects/timeouts `0/0`;
- raw BLOCK/UNKNOWN `0/0`.

## B — H20 ON

El ANR aparecio aproximadamente a los diez minutos:

- ANR: `2026-08-31 23:27:32`;
- razon: input dispatch timeout, `10004 ms`, Chrome browser pid `5935`;
- traza: `anr_2026-08-31-23-27-36-635`;
- ANR visible en tres muestras consecutivas antes de recuperarse el dialogo;
- browser RSS max muestreado: `297,720 KiB`;
- renderer RSS max muestreado: `858,812 KiB`;
- MemAvailable minimo: `127,116 KiB`;
- Glosh RSS max muestreado: `168,084 KiB`;
- AP max: `42.9 C`; thermal status llego a `1` durante la recuperacion.

Justo alrededor del timeout:

- dos renderers simultaneos mostraron RSS aproximados de `524 MiB` y
  `491 MiB`; en la muestra siguiente llegaron a `809 MiB` y `551 MiB`;
- Chrome browser consumia `184% CPU`;
- los dos renderers consumian `150%` y `159% CPU`;
- hubo cientos de miles de major/minor faults durante la recuperacion;
- Samsung Chimera fue activado por `TRIGGER_SOURCE_LMKD`;
- Chimera observo `481,212 KiB` disponibles frente a target `747,520 KiB` y
  mato trece procesos de background para liberar `235,283 KiB`;
- un renderer de la misma sesion termino por `LOW_MEMORY` a las `23:28:04`.

El main thread de Chrome aparece nuevamente en `Looper.pollOnce` al momento de
la captura de stacks. Esto indica recuperacion despues del stall; no autoriza a
atribuir el bloqueo a una funcion nativa concreta.

## H20 METRICS AT TERMINAL STATUS

- requests `519`;
- SAFE/BLOCK/UNKNOWN `176/4/138`;
- engine calls `127`;
- cache hits/misses/evictions `52/128/63`;
- inference p50/p95/p99 `146.796/720.006/1173.266 ms`;
- decision p50/p95/p99 `163.394/967.263/1206.124 ms`;
- preprocess p50/p95/p99 `7.469/48.786/54.859 ms`;
- proxy p50/p95/p99 `274.102/1205.438/1919.904 ms`;
- inference/in-flight/queue peak `1/2/2`;
- queue rejects/timeouts `0/0`;
- proxyQueueRejects `0`;
- protectFailure `0`;
- QUIC/direct-TCP bypass `0/0`;
- documents transformed/fail-closed `15/0`;
- SELF_READY accepted/rejected `11/0`;
- raw BLOCK/UNKNOWN `0/0`.

OFF ejecuto `694` requests y `216` engine calls — mas que ON — sin ANR. Glosh
tampoco crecio en B respecto de A. Esto descarta como explicacion primaria el
volumen de inferencia, la memoria del proceso Glosh y la saturacion de su cola.

## CAUSAL CLASSIFICATION

`H20_DOCUMENT_SELF_SHIELD_RENDERER_MEMORY_CPU_AMPLIFICATION`.

Nivel de atribucion demostrado:

1. Es especifico de la interaccion H20 ON en este workload/A23: A limpio, B
   reproduce el mismo ANR y patron de memoria del incidente original.
2. El locus es Chrome renderer pressure: crecimiento de uno/dos renderers,
   CPU alta, page-fault/thrashing, LMKD/Chimera y finalmente input timeout.
3. No es atribuible primariamente a inference/proxy queue: OFF proceso mas
   requests e inferencias; B tuvo cero rejects/timeouts y maxima concurrencia
   configurada de uno.
4. La evidencia no distingue aun cual hook, regla CSS, observador o ciclo del
   document/self-shield produce la amplificacion. Afirmar una funcion exacta
   seria especulativo.

Por lo tanto el tuning general de cache/concurrencia sigue bloqueado. El
siguiente delta debe aislar, con una bisectriz controlada dentro de H20, que
componente renderer-side retiene o multiplica trabajo/memoria.

## SECURITY / HEALTH / ROLLBACK

- Durante H20 ON: raw BLOCK/UNKNOWN `0/0`.
- No hubo bypass QUIC/TCP, queue reject ni timeout.
- No se cambio R3.1, modelo, thresholds, Byte Gate, H20 ni knobs.
- STOP explicito despues de capturar el ANR.
- Proxy detenido, cache limpiado, CA removida y rutas VPN restauradas.
- Transporte `inactive`, `ownedFdResources=0`,
  `activeProtectedUdpSockets=0`, `transportRuntime=ready`.
- `documentTransformOutstanding=0` y `readyTokensOutstanding=0` al estado
  terminal detenido.
- Chrome quedo suspendido fail-close.
- Device Owner, Affiliated y Accessibility preservados.
- Chrome inode `6090` y Glosh inode `1239519` preservados.
- `resetCount=3` preservado.
- No se borro ningun dato.

Resultado de salud del diagnostico: crash/OOM `0/0`; ANR OFF/ON `0/1`.

## RESIDUAL / BLOCKER

H20 no puede declararse normal ni rapido mientras persista esta amplificacion
renderer-side. Hace falta un ticket focal de aislamiento del self-shield que
mantenga Byte Gate y raw `0/0`, compare subcomponentes uno por vez y capture
memoria/CPU renderer antes de cualquier optimizacion. No corresponde cambiar
cache, concurrencia, timeout, modelo o thresholds para ocultar el problema.

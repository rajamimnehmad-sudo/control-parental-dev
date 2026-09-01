# CHROME-H20-PERF-NORMALITY-14-CLOSURE-01

## STATUS

`BLOCKED / PENDING CHATGPT REVIEW`.

La instrumentacion, el benchmark A/B/C, la seguridad del Byte Gate y la
normalidad corta pasaron. El gate sostenido produjo un ANR real de Chrome a
los aproximadamente diez minutos. Por ello este lote no promueve ninguna
configuracion nueva y no puede declararse PASS.

## BASE / SHAS

- Base funcional: `cf72dc2767b83980c5fa27dae87dfa15a2ba25e7`.
- Functional SHA: `c7d40a11f8f198a767514b48a538272296f3bfa9`.
- APK: DEV414, SHA-256
  `1902b116ae4384a19f26f782bae4eb4161b56977d4f0108d711852eb70c021bb`.
- Dispositivo: Samsung SM-A235M, Android 14/API 34, Chrome oficial.
- Instalacion: update-in-place; no se borro ningun dato.

## DELTA FUNCIONAL

- Knobs DEV acotados: cache `64|256`, concurrencia `1|2`, queue fija `2` y
  timeout fijo `5000 ms`; valores invalidos fallan cerrado.
- Metricas DEV: preprocess p50/p95/p99, histograma de prepared images,
  decision basis, engine calls, cache entries/evictions.
- La decision R3.1, su policy, modelo, thresholds y pipeline regional no se
  modificaron.

## AUTOMATED VALIDATION

PASS:

- defaults y rechazo de knobs invalidos;
- paridad de decisiones A/B/C sobre el mismo corpus;
- cache generation binding, eviction, in-flight dedupe, clear/cancel,
  concurrency y queue saturation fail-close;
- NetworkVisualDeliveryGate;
- GloshIA decision engine/session y parity focal;
- H20 transformer, bootstrap, SELF_READY y liveness;
- Service Worker gates;
- compileDevDebugKotlin, lintDevDebug, assembleDevDebug y git diff --check.

El ktlint global conserva fallos historicos fuera del delta; los archivos
modificados por este lote no agregan una infraccion nueva.

## A/B/C PHYSICAL BENCHMARK

Mismo DEV414 y corpus controlado:

| Variante | Cache / concurrencia | Inference p50/p95/p99 ms | Decision p50/p95/p99 ms | Preprocess p50/p95/p99 ms | Proxy p50/p95/p99 ms | Cache hit/entries/evictions | Peak inference |
|---|---|---|---|---|---|---|---|
| A | 64 / 1 | 133.549 / 231.458 / 231.458 | 141.031 / 244.248 / 244.248 | 8.206 / 15.192 / 15.192 | 134.650 / 878.724 / 1421.116 | 3 / 7 / 0 | 1 |
| B | 256 / 1 | 134.056 / 181.596 / 181.596 | 142.319 / 189.358 / 189.358 | 7.518 / 9.308 / 9.308 | 5.421 / 587.353 / 1478.456 | 4 / 7 / 0 | 1 |
| C | 256 / 2 | 147.454 / 242.113 / 242.113 | 141.840 / 290.317 / 290.317 | 7.916 / 11.628 / 11.628 | 38.619 / 530.111 / 1305.224 | 3 / 7 / 0 | 2 |

No hubo rejects ni timeouts. Cache 256 no aporto capacidad sobre siete
entradas y concurrencia 2 empeoro p95 de inference/decision. Configuracion
seleccionada y conservada: `cache=64, concurrency=1, queue=2, timeout=5000`.
No fue necesaria una segunda APK.

## CONTROLLED / REAL WEB

- Controlled selective: SAFE y BLOCK correctos, UNKNOWN fail-close,
  SELF_READY/parser/original script PASS, raw BLOCK/UNKNOWN `0/0`.
- Fravega: texto/layout y navegacion utilizables; contadores acumulados
  `safe=49`, `blocked=1`, `unknown=34`, `unsupported=8`, raw `0/0`.
- Mimo: layout utilizable y placeholders Glosh visibles; acumulado
  `safe=69`, `blocked=3`, `unknown=34`, `unsupported=29`, raw `0/0`.
- Google Images sin consulta: utilizable.
- Google Images `mujer`: intento normal termino en HTTP 429 `/sorry`;
  `BLOCKED_BY_SITE`, sin evasion.
- Background/foreground, tabs y rotacion portrait-landscape-portrait
  funcionaron. Para el mismo body, rotacion no incremento engine calls:
  `rotationNewInference=0`.

Back/forward completo no pudo cerrarse: el ANR aparecio durante la fase de
navegacion sostenida antes de completar ese subgate.

## SUSTAINED RUN / ROOT BLOCKER

Tras aproximadamente diez minutos de navegacion y scroll repetidos en
controlled, Fravega, Mimo y Google, Chrome mostro `Chrome no responde`.
Android registro un input dispatch timeout de aproximadamente `10017 ms` a
las `21:52:05`. La corrida tambien registro procesos renderer terminados por
low-memory alrededor del mismo intervalo, pero no se obtuvo un stack causal
suficiente para atribuir de forma segura el ANR al cache, proxy, renderer o a
otra frontera.

Clasificacion:

`CHROME_INPUT_ANR_UNDER_SUSTAINED_H20_NAVIGATION`.

No se aplico tuning especulativo. El ANR constituye un root cause distinto y
obliga a STOP/triage.

Metricas inmediatamente anteriores/alrededor del ANR:

- requests `274`; SAFE/BLOCK/UNKNOWN `77/3/71`;
- engine calls `71`; cache hits/misses/evictions `7/73/7`;
- inference p50/p95/p99 `172.300/657.909/817.498 ms`;
- decision p50/p95/p99 `213.432/1065.835/1447.827 ms`;
- preprocess p50/p95/p99 `7.597/58.668/66.762 ms`;
- proxy p50/p95/p99 `260.191/1148.585/1629.257 ms`;
- prepared images: `1=50`, `4=21`, `5=0`, `other=0`;
- basis: `FullThreshold=3`, `None=68`;
- cache entries `64`, evictions `7`;
- inference/queue peak `1/1`; rejects/timeouts `0/0`;
- Chrome PSS/RSS antes del cierre: aproximadamente `210723/214888 KiB`;
- Glosh PSS/RSS: aproximadamente `131107/87344 KiB`;
- thermal status `0`; AP `38.0 C`, bateria `27.8 C`, skin `34.7 C`.

## SECURITY / RAW

En la sesion terminal:

- network SAFE raw delivered `77`;
- BLOCK replaced `3`;
- UNKNOWN replaced `34`;
- unsupported replaced `37`;
- raw BLOCK delivered `0`;
- raw UNKNOWN delivered `0`;
- proxyQueueRejects `0`;
- protectFailure `0`;
- QUIC/direct-TCP bypass `0/0`.

No se cambio ningun verdict, modelo, threshold o policy.

## OWNERSHIP / ROLLBACK / HEALTH

STOP explicito:

- proxy detenido y cache limpiado;
- rollback `complete`, CA removida y rutas VPN restauradas;
- transporte `inactive`;
- `ownedFdResources=0`;
- `activeProtectedUdpSockets=0`;
- `transportRuntime=ready`;
- `documentTransformOutstanding=0`;
- `readyTokensOutstanding=0` tras STOP;
- Device Owner y Accessibility preservados;
- Glosh `ceDataInode=1239519` preservado;
- Chrome `ceDataInode=6090` preservado;
- `resetCount=3` preservado;
- Chrome quedo suspendido fail-close al terminar.

Salud del lote: crash/OOM `0/0`; ANR `1`. No se borro Chrome, no se genero
otra APK y no se modificaron estadisticas de bateria.

## RESIDUAL / NEXT ROUTE

Antes de promover performance o declarar normalidad debe hacerse una auditoria
causal focal del ANR con una corrida reproducible y trazas suficientes. Debe
distinguir presion de renderer/tabs, backpressure del proxy y bloqueo de input
sin cambiar primero los knobs o la semantica R3.1. La seguridad Byte Gate/H20
permanece fail-close y sin raw BLOCK/UNKNOWN.

# CHROME-REAL-WEB-PROVENANCE-COVERAGE-17

Fecha: 2026-08-28. Dispositivo: Samsung A23 `SM-A235M`, Android 14/API 34.

## STATUS

**BLOCKED — MEASUREMENT COMPLETE / VISIBLE CAUSALITY INSUFFICIENT.**

La instrumentación DEV y la matriz física sí midieron el data-plane real. El
resultado no permite convertir recursos inspeccionados en cobertura visible
autoritativa: stock Chrome no expone una identidad browser-side que una de
forma inequívoca `request/correlation + body digest + verdict` con una
instancia exacta visible. Por contrato, coincidencias de URL, tiempo, orden o
cantidad no se promueven a autoridad.

Este resultado no reabre el compositor ni modifica 11B/R2A/R2B/GloshIA. R2B
permanece implementada y su gate físico sigue bloqueado por
`PRESENTATION_COMMIT_UNOBSERVABLE`.

## Git, alcance y artefacto

- Base funcional: `b815da4156771c91c349a68b85726e81fefd1a88`.
- R2B authority preservada: `342ff3b2af485a4bc70d7243615a8be97b432ffa`.
- Functional audit HEAD: `a5440ce43157130a1ba25914d73ca588cb89c1ca`.
- Commits funcionales:
  - `d5edf97fb8967ae2a04962113b93386bbff1d4a5`, telemetría, fixture, tests y herramienta;
  - `a5440ce43157130a1ba25914d73ca588cb89c1ca`, declaración del comando DEV `AUDIT_MARK`.
- Rama de trabajo: `work/chrome-real-web-provenance-coverage-17`.
- Rama de revisión: `review/chrome-real-web-provenance-coverage-17-triage`.
- DEV380, package `com.contentfilter.user.dev`, `versionName=1.0.1-dev`.
- APK: `159139693` bytes; SHA-256
  `f2cace2ebbadb44bfa43d02778f64270bad975f0716162537b57abae8d5b340a`.

El delta funcional es DEV/observacional: ledger acotado, fixture controlado,
telemetría redacted, tests, calculador y `versionCode`. No cambia decisiones,
release, modelo, thresholds, 11B, R2B ni Protected Surface.

## 11B current inventory

La implementación vigente no usa ya los nombres históricos sugeridos en el
ticket. Las piezas reales auditadas son:

- `ChromePhotosHttpsProxy`: crea `c<connection>-r<request>`, normaliza requests,
  aplica inspección antes de escribir a Chrome y conserva lineage de redirects.
- `ChromeImageContentAuthority`: identifica candidatos por intención, MIME y
  sniff acotado; exige encoding identity; soporta raster estático
  JPEG/PNG/WebP/AVIF; GIF animado, APNG, SVG, partial, 304 sin authority,
  encoded y cuerpos fuera de límites quedan fail-close.
- `ChromePhotosRealResponseSanitizer`: lee el body candidato acotado, calcula
  digest del body observado y sólo entrega bytes originales ante SAFE. BLOCK o
  UNKNOWN entregan el PNG placeholder.
- `ChromePhotosResourceTransformer` + `ChromePhotoDecisionSession`: decisión
  GloshIA/cache por `SHA-256 + MIME`; el cache es acotado y se limpia al STOP.
- `ChromeRealWebProvenanceCoverageLedger` (DEV380): observa el flujo sin cambiar
  autoridad y registra 512 eventos como máximo; los logs persistentes de la
  sesión conservan los eventos anteriores aunque el snapshot in-memory reporte
  `audit17Dropped`.

Punto temporal: el body completo se observa, inspecciona y decide antes de que
el writer entregue el response body a Chrome. Esa es autoridad fuerte a nivel
**recurso**, no una prueba de qué instancia terminó visible.

## Correlation contract

`AUTHORITATIVE_PRE_RENDER` requiere, para la misma navegación/sesión:

```text
request URL hash
+ body digest
+ correlation id
+ verdict ready before delivery
+ exact visible-instance identity
```

`same URL`, `same body`, proximidad temporal, orden de requests o igualdad de
cantidades no bastan. Un body repetido sólo puede autorizar varias instancias
si existe una prueba adicional de esa multiplicidad. Stock Chrome no aportó
esa prueba. Por eso los 71 medios estáticos visibles inventariados quedaron
`ATTRIBUTION_UNKNOWN`, incluso cuando el data-plane registró SAFE/BLOCK sobre
recursos cercanos.

El manifiesto verificable es
`CHROME_REAL_WEB_PROVENANCE_COVERAGE_17_MANIFEST.json`. Contiene SHA-256 de cada
screenshot temporal, resolución y rectángulos de área; no contiene capturas ni
pixels. El calculador rechaza claims authoritative sin URL hash, body digest y
correlation ID.

## Real-web site matrix

Corrida primaria sin CDP/DevTools. Los screenshots sólo construyeron ground
truth de auditoría y fueron eliminables; nunca adquirieron autoridad de
producto.

| Estado | Visible | Recursos visuales del ledger | Resultado |
| --- | ---: | --- | --- |
| Frávega listing warm | 3 | 59; SAFE 35; fail-close 24 | UNKNOWN 3 |
| Frávega scroll/lazy | 2 | 6; SAFE 5; fail-close 1 | UNKNOWN 2 |
| Frávega product detail | 1 | 4; SAFE 1; fail-close 3 | UNKNOWN 1 |
| Frávega back | 2 | 13; SAFE 13 | UNKNOWN 2 |
| Frávega second visit | 3 | 25; SAFE 12; fail-close 13 | UNKNOWN 3 |
| Google Images cold grid | 6 | 66; SAFE 44; BLOCK 6; fail-close 15 | UNKNOWN 6 |
| Google Images warm reload | 2 | 2; SAFE/cache 1; fail-close 1 | UNKNOWN 2 |
| Google Images scroll/lazy | 9 | 15; SAFE 12; fail-close 3 | UNKNOWN 9 |
| Google Images preview | 1 | 6; SAFE 3; fail-close 3 | UNKNOWN 1 |
| Google Images back | 10 | 3; fail-close 3 | UNKNOWN 10 |
| Google Images second visit | 4 | 9; SAFE 7; fail-close 2 | UNKNOWN 4 |
| Google Images `mujer` grid | 5 | 11; SAFE 1; BLOCK 7; fail-close 3 | UNKNOWN 5 |
| Google Images `mujer` scroll | 10 | 7; SAFE 2; BLOCK 3; fail-close 1 | UNKNOWN 10 |
| Cetrogar listing cold | 0 | 13; SAFE 3; fail-close 9 | no rendered photos |
| Cetrogar second visit | 5 | 49; SAFE 37; fail-close 12 | UNKNOWN 5 |
| Cetrogar reload warm | evidencia de transporte | 58; SAFE 46; BLOCK 1; fail-close 11 | frame aún protegido; fuera del denominador |
| Cetrogar scroll/lazy | 6 | 4; SAFE 4 | UNKNOWN 6 |
| Cetrogar product detail | 1 | 33; SAFE 3; fail-close 30 | UNKNOWN 1 |
| Clarín home/editorial | 1 | 133; SAFE 49; BLOCK 3; fail-close 81 | UNKNOWN 1 |

Estados bloqueados sin bypass:

- Mimo: `ERR_TIMED_OUT`; Samix/Wanama/Cheeky se intentaron como sustitutos de
  la misma clase y no dieron una navegación canónica estable.
- Mercado Libre: control anti-bot “Por seguridad, completá este paso”; no se
  intentó bypass.
- Wikipedia reference: `ERR_TIMED_OUT`; aun así el data-plane observó WebP/PNG
  SAFE antes de que Chrome terminara en error, evidencia adicional de que un
  recurso inspeccionado no prueba visibilidad.
- Cetrogar fue el sustituto ecommerce operativo para listing/detail/warm/scroll.

La matriz contiene 18 estados renderizados canónicos y supera el mínimo de 14.

## Cold / warm behavior

- Frávega: la primera navegación usable tuvo mezcla de originales y
  placeholders; scroll y back produjeron otra combinación; en segunda visita
  tres productos se vieron originales. Los eventos SAFE/fail-close variaron y
  no hubo una identidad visible que explicara cada celda.
- Google Images: cold produjo 44 SAFE/6 BLOCK en recursos; warm reload sólo
  generó 2 eventos visuales mientras seguían existiendo celdas visibles;
  back generó 3 eventos para 10 medios visibles. Browser/cache history rompe la
  inferencia ingenua “request actual == visible actual”.
- Cetrogar: second visit y reload tuvieron muchos cache hits (4 y 34), pero
  tampoco exponen una correspondencia visible 1:1.

Esto separa `PROVISIONING-COLD` de `NORMAL-SESSION-WARM` y demuestra que el
ledger de red por sí solo no cubre memory cache/back-forward/estado renderer.

## Provenance counts and coverage bounds

En los estados real-web canónicos, el ledger observó 516 unidades de recurso:

```text
inspected=298
safe=278
block=20
failclosed=215
transport_failure=3
verdictCacheHit=59
```

Son métricas de recursos, no cobertura visible.

Ground truth visible del manifiesto:

```text
TOTAL_VISIBLE_STATIC_MEDIA=71
AUTHORITATIVE_PRE_RENDER=0
DEFINITE_NON_INTERCEPTABLE=0
ATTRIBUTION_UNKNOWN=71
coverageLowerBound=0.000000
coverageUpperBound=1.000000

VISIBLE_AREA_TOTAL=11707895 px
VISIBLE_AREA_AUTHORITATIVE=0 px
VISIBLE_AREA_NON_INTERCEPTABLE=0 px
VISIBLE_AREA_UNKNOWN=11707895 px
visibleAreaLowerBound=0.000000
visibleAreaUpperBound=1.000000
```

No se asignó `DEFINITE_NON_INTERCEPTABLE` en real-web por mera ausencia de un
request. Las pruebas positivas de esos mecanismos pertenecen al fixture
controlado y no se mezclan con el porcentaje real-web.

## Controlled-mechanism matrix

Fixture `/web17`, source fixture conocido, carrier visual explícito:

- HTTPS `<img>`, CSS background, srcset y picture: requests observados y bytes
  SAFE originales antes del render.
- SVG externo: request observado; formato no autorizable por el pipeline actual;
  placeholder fail-close.
- Inline SVG: raster rojo renderer-local, sin body visual interceptable.
- `data:` y `blob:`: pixels visibles creados desde bytes locales; ningún nuevo
  body HTTPS visual autoritativo para esas instancias.
- Canvas: pixels rojos renderer-local.
- same body A/B: demuestra que un digest puede alimentar múltiples instancias;
  sin multiplicity proof no se puede consumir un evento dos veces.
- dynamic replacement: exige nueva identidad; un verdict anterior no autoriza
  el body nuevo.

Así, `data:`, `blob:`, Canvas e inline SVG son
`DEFINITE_NON_INTERCEPTABLE` por mecanismo en laboratorio. La evidencia 13A
vigente agrega WebGL, Service Worker y CacheStorage como renderer/browser-local;
no se volvió a ejecutar 13A ni se incorporó al denominador real-web.

## Google Images `mujer` — residual observable

La navegación inicial registró 7 BLOCK/model_filter y el scroll otras 3; las
celdas principales correspondientes se presentaron como placeholders Glosh.
Sin embargo, después del scroll quedó visible al menos una foto grande de una
mujer y cuatro thumbnails de búsquedas relacionadas. El ledger del estado tuvo
también 2 SAFE.

Por falta de binding 1:1 no se puede afirmar cuál request alimentó cada pixel ni
clasificar concluyentemente el original visible como false negative del modelo.
Sí es un **residual de protección observable** que debe entregarse a ChatGPT:
stock Chrome mostró contenido de mujer original junto a placeholders bloqueados.
No se alteraron modelo, thresholds ni labels.

## GLOSHIA path

- Motor y policy R3.1 existentes, sin cambios.
- 11B físico focalizado PASS:
  - NORMALIZATION 1/1;
  - SAFE 1/1;
  - MISLABELED 3/3;
  - FAIL_CLOSED 8/8;
  - GZIP/CHUNKED/RANGE/ETAG/DOWNLOAD PASS.
- En el audit real-web se observan decisions SAFE/BLOCK/UNKNOWN sobre los bytes
  exactos que atravesaron el data-plane. La latencia es métrica secundaria y no
  se optimizó.

## Automated validation

Exit code `0`:

```text
:app-user:testDevDebugUnitTest
:feature-accessibility:testDebugUnitTest
:gloshia-visual-core:testDebugUnitTest
:app-user:compileDevDebugKotlin
:app-user:runKtlintCheckOverDevSourceSet
:app-user:runKtlintCheckOverTestDevSourceSet
:app-user:lintDevDebug
:app-user:assembleDevDebug
python3 -m unittest tools/chrome_real_web_provenance/test_audit_coverage.py
python3 tools/chrome_real_web_provenance/audit_coverage.py <manifest>
git diff --check
```

- App-user DEV full, feature-accessibility, GloshIA parity, tests focalizados de
  ledger/redirect/same URL-different body/repeated body/cache/privacy/cleanup y
  fixture: PASS.
- Build final posterior al fix de manifest: `BUILD SUCCESSFUL`; compile, lint y
  assemble PASS.

## Health, ownership and rollback

Ventana canónica: `10:47:50` a `11:10:37 -0300`, session audit
`80e1906d`. Un intento previo con UIAutomator invalidó temporalmente
Accessibility; el guard suspendió Chrome fail-close. UIAutomator fue retirado
de la corrida canónica y no se usó DevTools/CDP.

Logcat canónico temporal: `12789914` bytes, SHA-256
`d823f0dce68e8342f3ebbad09e56bee7dc4916b66f0e8c949fb827b4858a9912`.
No se versionó porque contiene hosts de navegación; la evidencia textual y el
manifiesto conservan únicamente agregados, hashes y resultados necesarios.

Estado activo final:

```text
proxyQueueRejects=0
inference queueRejects=0
protectFailure=0
quicAttempts=0
directTcpAttempts=0
recursion=0
crash/ANR/OOM=0/0/0
imageBodyAdmissionRejects=102
```

`failures=731` no se ocultó:

- 722 `side=client stage=handshake SSLHandshakeException -> EOFException`;
- 5 `UnknownHostException`;
- 3 upstream `SocketException` con response ya iniciado;
- 1 upstream `InterruptedIOException` fail-close.

Fueron cierres/abortos auxiliares de la carga real-web; no hubo bypass,
queue reject, protect failure, crash ni entrega raw por esa causa. Los 102 body
admission rejects sí son un gap de disponibilidad/cobertura: quedaron
placeholder fail-close, nunca SAFE.

Preservación:

- A23 `SM-A235M`, Chrome `152.0.7977.64` / `797706404`.
- Device Owner/Affiliated preservado.
- Accessibility enabled y bound.
- `ceDataInode=1239519` antes/después.
- update-in-place, firma y datos preservados.

Rollback final:

```text
proxy/cache/CA cleanup=complete
Chrome suspended=true (fail-close)
status=inactive
ownedFdResources=0
activeProtectedUdpSockets=0
transportRuntime=ready
```

## Top coverage gaps

1. **Visible-instance identity absent:** 100% del área real-web queda UNKNOWN;
   es el blocker dominante.
2. **Warm/browser cache and history:** aparecen más instancias visibles que
   eventos de recurso actuales; el ledger de red no prueba su provenance.
3. **Renderer-local carriers:** data/blob/Canvas/inline SVG, más WebGL/SW/Cache
   demostrados por 13A, carecen de body visual pre-render interceptable.
4. **Bounded body admission:** 102 candidatos concurrentes quedaron fail-close;
   seguro, pero con degradación visible.
5. **Unsupported/ambiguous formats:** SVG, encoded, partial/304 y unknown format
   terminan protegidos, no autorizados.
6. **Observable woman-content residual:** un original grande y thumbnails
   quedaron visibles junto a blocks; no atribuible al modelo sin el binding que
   justamente falta.

## Architectural assessment

### Route A — stock Chrome / pre-render

**No demostrada como viable para control visual completo.** 11B autoriza muchos
recursos antes del render y bloquea contenido real, pero el lower bound visible
es 0% y el intervalo 0–100% no permite afirmar cobertura. Haría falta una señal
browser-side confiable que enlace body/verdict con cada instancia visible,
incluyendo cache/history, y una solución positiva para renderer-local.

### Route A2 — hybrid fail-close

**Sólo conceptualmente viable con degradación fuerte.** Las clases conocidas no
interceptables podrían permanecer protegidas, pero stock Chrome no expone hoy
qué región/instancia pertenece a esas clases. Sin esa señal, el fail-close sería
global o heurístico; ninguna opción satisface el contrato vigente.

### Route B — owned renderer trigger

**Activado para garantía completa.** Si el requisito es controlar toda foto
visible, la combinación de `PRESENTATION_COMMIT_UNOBSERVABLE`, ausencia de
visible-instance identity y carriers renderer-local obliga a poseer el renderer
(WebView/browser propio/Chromium controlado o equivalente). Este audit no
implementa esa ruta.

## Residual / next decision

La medición de recursos es confiable y la matriz real-web está completa, pero
la causalidad visible no puede establecerse en stock Chrome con las autoridades
disponibles. La rama queda triage para revisión de ChatGPT. No se inicia una
arquitectura nueva ni se modifica Production.

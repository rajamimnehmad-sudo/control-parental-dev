# CHROME-PROVENANCE-GAP-13A-C

Fecha: 2026-08-26. Dispositivo: Samsung A23 `SM-A235M`, Android 14.

## Estado

**PASS TRIAGE DEV355.** El fix de integridad quedó preservado sin reescritura,
el crash previo fue capturado y auditado, y no se reprodujo durante un control
Chrome, dos runs 13A consecutivos ni la regresión física mínima 11B. No se
modificó código ni se inició 13B.

## Git y artefacto

- Base compartida: `e46fe65c36b3f0134cdad7fd3e620a50d481f5d4`.
- Fix funcional: `60ec269ea0ed9ef0ab179ae39668c50fd2276f3b`.
- Evidencia 13A-F previa: `00541f9f29b9a37f0efb096408351edfc432254c`.
- Ancestry verificada: `e46fe65c -> 60ec269e -> 00541f9f`.
- Review triage preservada y verificada:
  `review/chrome-provenance-gap-13a-f-dev355-triage` en `00541f9f...`.
- La review DEV354 permaneció en `e46fe65c...`.
- DEV355, `versionCode=355`, `com.contentfilter.user.dev`.
- APK: `158926149` bytes; SHA-256
  `4d5841dca3a13d5e1b1a6c3d008837266d91b7869b56681bcf37739694a48355`.

El diff `e46fe65c..60ec269e` contiene únicamente fixture 13A, su test focalizado
y DEV354 -> DEV355. `60ec269e..00541f9f` agrega sólo la evidencia 13A-F. El
árbol dedicado estaba limpio antes de operar el A23.

## Preservación y auditoría del crash anterior

Antes de abrir Chrome se capturó un bugreport completo:

- Archivo local: `/tmp/CHROME-PROVENANCE-GAP-13A-C-preexisting-20260826.zip`.
- Tamaño: `22976257` bytes.
- SHA-256: `88c0452801793785354b7bd3e6ad9046edf8b4f15292422f4bed09b3a13e8518`.
- Tombstone: `FS/data/tombstones/tombstone_15`, SHA-256
  `79bd8a2da85248d5100a3d920d47969012c30275d505db5a4e5fbd1d8e4bae76`.

Identidad exacta:

- Android build:
  `samsung/a23ub/a23:14/UP1A.231005.007/A235MUBSFEZB1:user/release-keys`.
- Chrome `152.0.7977.64`, versionCode `797706404`, actualizado a las
  `00:03:07`.
- Proceso que cayó: browser principal `com.android.chrome`, PID/TID `21328`;
  no fue renderer, GPU ni utility.
- Timestamp tombstone: `2026-08-26 00:08:35.324090823-0300`.
- Uptime: `328 s`.
- `SIGTRAP`, code `TRAP_BRKPT`, status `5`.
- Frames `#00..#04` en `libchrome.so`, BuildId
  `7c95b67ec869e4d41eebaf037066586aedb57777`.
- Sin `Abort message`, `CHECK` legible ni símbolos Chrome disponibles para
  resolver esos offsets.
- DropBox `system_app_native_crash` y ApplicationExitInfo
  `reason=5 (APP CRASH(NATIVE))` preservados.

La memoria del tombstone conserva literalmente `GET /json/protocol` y
`/json/protocol`, junto con `/devtools/browser`. El runner se abrió a las
`00:07:57`; la consulta DevTools ocurrió después de observar
`GLOSH13A_COMPLETE`, y el browser cayó a las `00:08:35`. Esto correlaciona el
SIGTRAP con la consulta al endpoint DevTools utilizada para extraer el reporte,
no con una petición 13A. Es evidencia circunstancial fuerte, no una
simbolización suficiente para afirmar la causa interna de Chrome. No apareció
evidencia concreta que atribuya el crash a Glosh o al fixture.

El `failures=3` previo también quedó explicado por logs, sin mezclarlo con el
SIGTRAP:

1. `c8`, handshake cliente `accounts.google.com`,
   `SSLHandshakeException -> EOFException`.
2. `c9`, handshake cliente `beacons.gcp.gvt2.com`, mismo cierre EOF.
3. `c12-r1`, `e2c20.gcp.gvt2.com`, upstream `InterruptedIOException` tras
   20 segundos.

Fueron tráfico auxiliar público; `proxyQueueRejects=0`, `protectFailure=0`,
`quicAttempts=0` y `directTcpAttempts=0`.

## Reproducción física

DEV355 ya coincidía con versión, firma y APK esperados; no se reinstaló ni se
borraron datos. Ventana observada: `04:28:57` a `04:33:35 -0300`. Se evitó
DevTools; los estados se consultaron por el proxy controlado en loopback.

### Control

`https://example.com` cargó correctamente y Chrome mantuvo el mismo PID browser
`8739`. Runtime `ready`, DO/Affiliated y Accessibility permanecieron sanos. No
hubo crash, ANR ni OOM. Un handshake auxiliar inicial de `www.google.com` cerró
con EOF, clasificable y sin bypass; la navegación esencial fue normal.

### Run 13A #1

```text
PAGE=DATA_URL:RENDERED,BLOB_URL:RENDERED,CANVAS_2D:RENDERED,
WEBGL:RENDERED,INLINE_SVG:RENDERED,JAVASCRIPT:RENDERED,
JSON:RENDERED,WASM:RENDERED,SERVICE_WORKER:RENDERED,
CACHE_STORAGE:RENDERED,STORAGE_CLEANUP:PASS,
JS_REQ=1,JSON_REQ=1,WASM_REQ=1,SW_SCRIPT_REQ=1,
SW_ORIGIN_FALLBACK=0,CACHE_ORIGIN_FALLBACK=0
```

`INLINE_SVG` pertenece al runner DEV355 cuyo código serializa el SVG real,
dibuja a canvas y exige muestras `getImageData(60,90)` roja y
`getImageData(20,90)` negra antes de devolver `RENDERED`.

### Run 13A #2, sin borrar datos

Los diez vectores volvieron a `RENDERED` y cleanup volvió a `PASS`.

```text
JS_REQ=2,JSON_REQ=2,WASM_REQ=2,SW_SCRIPT_REQ=2,
SW_ORIGIN_FALLBACK=0,CACHE_ORIGIN_FALLBACK=0
```

Deltas: JS/JSON/WASM/SW script `+1/+1/+1/+1`; ambos fallbacks `+0`.

## Regresión física 11B

`https://glosh-photos.test/web11b?nonce=13a_c_dev355_11b_20260826_0432`
produjo reporte visible y status interno PASS:

- `NORMALIZATION:PASS`, `SAFE:PASS`.
- `MISLABELED:PASS` 3/3.
- `FAIL_CLOSED:PASS` 8/8.
- `GZIP`, `CHUNKED`, `RANGE`, `ETAG`, `DOWNLOAD`: PASS.
- SAFE original `6768 -> 6768`, `model_allow`.
- Entradas inseguras/no autorizables terminaron en placeholder de `6303` bytes.

## Salud y preservación

- Ventana completa: crash/ANR/OOM `0/0/0`.
- `proxyQueueRejects=0`, inference `queueRejects=0`, `protectFailure=0`.
- `quicAttempts=0`, `directTcpAttempts=0`.
- `rawPresented=true`: 0 observaciones; `rawPresented=false` durante todas las
  presentaciones registradas.
- Stale no cero: 0; recursion no cero: 0.
- Capturas post-ready: 0; `errorCode3=0`.
- Device Owner/Affiliated preservado.
- Accessibility enabled/bound; binding/crashed services vacíos.
- Datos preservados; `ceDataInode=1239519`.
- `bootstrapResetCount=1` sin reset adicional.

Rollback final:

```text
status=inactive ownedFdResources=0 ownedFdPeak=4
activeProtectedUdpSockets=0 transportRuntime=ready
```

Proxy detenido con `cacheEntries=0 cleanup=complete`, CA removida, VPN
restaurada y Chrome suspendido fail-close. El `SocketException` observado al
cerrar fue la conexión de consulta loopback interrumpida por `STOP`; el estado
posterior quedó limpio.

## Conclusión

El SIGTRAP anterior no se reprodujo sin la consulta DevTools problemática. La
evidencia actual no demuestra un defecto Glosh ni del fixture. DEV355 completa
el gate físico 13A con estabilidad consecutiva y conserva 11B. 13B permanece
fuera de alcance.

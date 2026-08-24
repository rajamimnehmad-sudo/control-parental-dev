# CHROME-VPN-09A-UDP-FIXTURE-ROUNDTRIP-01

## Veredicto

`PASS DEV`

> Estado autoritativo: la sección **Cierre final DEV 337** al final de este
> documento reemplaza el blocker histórico conservado debajo como trazabilidad.

El roundtrip UDP controlado quedó demostrado físicamente en DEV 333 con 220/220
respuestas byte-identical, UID/package exactos, HEV, SOCKS5 UDP ASSOCIATE y
`protect()` correctos. Esa sesión descubrió después una carrera de cierre durante
el stress UDP (`SocketException: Socket closed`) y produjo la corrección DEV 334.

DEV 334 fue instalada in-place y conserva los datos, pero su primera corrida fue
inválida: la fixture recibió 20 ecos directos mientras TUN/HEV/SOCKS permanecían
en cero, porque el package-replace arrancó una generación sin la admisión/ruta del
gate. No se contó como PASS. Antes del recovery STOP→START, macOS eliminó los
directorios bajo `/private/tmp` y dejó de enumerar el A23 por USB. ADB quedó con
lista vacía, incluso tras reiniciar únicamente el daemon. Por eso no se pudieron
ejecutar sobre DEV 334 el stress UDP, los canarios finales ni el rollback.

La reanudación cerró además el riesgo alto del self-audit: DEV 335 conserva el
bridge y sus FD si el join nativo vence, pasa a `QUARANTINED`, rechaza restart y
sólo limpia cuando el thread confirma terminación. También acotó el owner lookup
y volvió observable el shutdown bounded de SOCKS. Todos los gates automáticos
pasaron, pero `adb devices -l` continúa vacío. DEV 335 no fue instalada ni tuvo
gate físico. No se declara PASS con evidencia de una versión anterior.

## Coordinación y Git

- Owner: Protección Android / Codex.
- Base: `87ba18540a4146af0203ead6813df49abb8b72ef`.
- Rama: `work/chrome-vpn-09a-udp-fixture-roundtrip-01`.
- Worktree original eliminado externamente:
  `/private/tmp/glosh-chrome-vpn-09a-udp-fixture-roundtrip-01`.
- Worktree recuperado persistente:
  `/Users/yejielnehmad/Developer/glosh-chrome-vpn-09a-udp-fixture-roundtrip-01`.
- Commit funcional UDP recuperado: `8028fbfebdf05f706ea323c3db2225ccd0848d0d`.
- Commit funcional hardening lifecycle:
  `bf60c6a0300aebcd3d51dd5cf46cea0ee6b7be24`.
- La recuperación reaplicó, en orden, los 23 parches exactos registrados por la
  sesión Codex; se omitió únicamente `local.properties`, recreado como archivo
  local ignorado.
- El checkout canónico sucio no se modificó. Sus cambios concurrentes en
  `VpnDomainPolicyEvaluator` son ajenos a este ticket y no colisionan con
  service/transport.
- Glosh Central se revisó y no se modificó, según la orden del ticket.
- Sin push, PR, merge, main, Production ni publicación.

## Implementación

- Fixture Android externa mínima: `com.glosh.vpnudpfixture`, sólo permiso
  `INTERNET`, sin analytics/storage/datos personales.
- Admisión al VPN sólo si el flag DEV explícito y el target privado/puerto bounded
  son válidos; default OFF.
- Ruta exacta `/32` sólo durante el gate; no se agregó `0.0.0.0/0` ni `::/0`.
- Único TUN reader: `VpnPacketDispatcher`.
- Owner: `getConnectionOwnerUid` mediante cache bounded/generation existente.
- Non-Chrome autorizado: `FORWARD_TO_HEV`; Chrome TCP/UDP 443: `DROP`.
- HEV: 2.17.1 pinneado de 09A, bridge `SOCK_SEQPACKET`.
- SOCKS local: loopback, RFC1929, destinos/puertos exactos, UDP ASSOCIATE,
  ATYP DOMAIN rechazado y FRAG distinto de cero descartado.
- UDP upstream: socket materializado, `VpnService.protect()` verificado y recién
  después `send`; error de protect produce cero salida.
- Instrumentación DEV de recursos owned-by-Glosh y probes malformed opt-in.
- Dos carreras reales de teardown quedaron contenidas en código: `recvfrom EBADF`
  y `DatagramSocket.send: Socket closed` durante cierre concurrente.

## APK y dispositivo

| Campo | Valor |
| --- | --- |
| Dispositivo | Samsung A23 SM-A235M |
| Android | 14 / API 34 |
| Chrome | 151.0.7922.137 |
| App Usuario final instalada | DEV 334 / 1.0.1-dev (última confirmación física) |
| APK candidata local | DEV 335 / 1.0.1-dev |
| APK candidata SHA-256 | `bc466c8b345832390596e7c0ccf0e6f5ef5fbdabc0989680148b28a2b07f80d5` |
| APK candidata bytes | 158794709 |
| ceDataInode | 1239519 antes/después de instalación |
| resetCount | 1 |
| Device Owner | Glosh, Affiliated |
| Accessibility | enabled/bound en último precheck válido |
| VPN | productiva preservada en último precheck válido |

El estado final actual no pudo reconfirmarse ni cerrarse por ADB: el 24 de agosto
la salida exacta de `adb devices -l` siguió sin dispositivos. Esto no prueba una
regresión del A23, pero impide afirmar rollback final.

## Fixture y echo

| Campo | Valor |
| --- | --- |
| Package | `com.glosh.vpnudpfixture` |
| versionCode / versionName | 1 / 1.0 |
| UID A23 | 10280 |
| APK SHA-256 | `5519512532ce86034599a790d56570c9e486b17ef324c9499dbdf92f9c707001` |
| Instalada | sí, dejada inerte según contrato |
| Echo | UDP en Mac |
| Target | `192.168.0.21:32123` |
| Ruta | `192.168.0.21/32` |

## Evidencia UDP física válida — DEV 333

### Funcional 20

- sent/received/validated: `20/20/20`.
- tamaños: `1, 32, 128, 512, 1200` bytes.
- timeouts/duplicates/out-of-order: `0/0/0`.
- p50/p95/p99: `8.218 / 13.380 / 491.776 ms`.
- Owner: UID `10280`, package `com.glosh.vpnudpfixture`.
- Policy: `FORWARD_TO_HEV`.
- HEV tx/rx: `20/21` (el adicional corresponde al control/retorno observado por
  las métricas nativas).
- SOCKS associations/out/in: `1/20/20`.
- protect UDP created/success/failure: `1/1/0`.
- resources current/peak: `4/7` con engine activo.
- active UDP associations final/peak: `0/1`.
- active protected UDP sockets final/peak: `0/1`.
- malformed probes empty/truncated/invalid header: `1/1/1`.
- Un datagrama válido posterior siguió funcionando.

### Funcional 200

- sent/received/validated: `200/200/200`.
- timeouts/duplicates/out-of-order: `0/0/0`.
- p50/p95/p99: `9.803 / 21.133 / 33.066 ms`.
- acumulado HEV tx/rx: `220/221`.
- acumulado SOCKS associations/out/in: `2/220/220`.
- acumulado protect created/success/failure: `2/2/0`.
- recursion: `0`.
- SIGSEGV/SIGABRT durante roundtrips: `0/0`.

### Fallo de stress que originó DEV 334

Al iniciar el stress específico de 100 ciclos en DEV 333, el cierre concurrente
de la asociación produjo a las 11:52:51.995:

`FATAL EXCEPTION: GloshSocksSession09A` / `SocketException: Socket closed` en
`DatagramSocket.send`.

La causa fue concreta: el watcher del TCP control cerraba relay/upstream mientras
el worker podía estar ejecutando uno de tres `send`. DEV 334 contiene catches
fail-closed de `SocketException`, boundary de sesión que contiene `IOException`
y un test determinista de 100 cierres durante tráfico. Falta validar esa corrección
en hardware.

## Corrida DEV 334 no válida

- fixture: `20/20/20`, p50/p95/p99 `6.284/11.216/32.126 ms`.
- TUN forwarded/returned: `0/0`.
- HEV tx/rx: `0/0`.
- SOCKS associations/out/in: `0/0/0`.
- protect UDP: `0`.

Conclusión: fue eco LAN directo, no evidencia del datapath. Se descartó antes de
declarar resultado.

## Regresiones heredadas de 09A

- TCP no-Chrome: PASS físico con Samsung Internet, UID `10262`, HEV/SOCKS/
  protect y respuesta de vuelta al TUN/app.
- DNS: pipeline Glosh preservado; HEV DNS `0`.
- Chrome TCP/443: DROP físico en 09A (`42` intentos).
- Chrome UDP/443: policy y unitarios PASS; no se disparó naturalmente detrás del
  proxy (`CHROME_UDP443_NOT_TRIGGERED_UNDER_PROXY`).
- GloshIA R3.1 sin cambios: SAFE original y BLOCK placeholder en el último canario
  válido; `raw=0`, `stale=0`, `grid=0`, captures post-ready `0`.

Estos canarios no pudieron repetirse después de la corrección DEV 334 y por eso no
se usan para elevar el ticket a PASS.

## Hardening lifecycle de la reanudación

- `HevTransportEngine` tiene estados explícitos `STOPPED`, `RUNNING`,
  `STOP_REQUESTED` y `QUARANTINED`.
- Stop limpio: `quit -> join confirmado -> close bridge/FD -> STOPPED`; recursos
  sensibles se cierran exactamente una vez.
- Join timeout/interruption: el bridge y FD permanecen owned, el estado es
  `QUARANTINED`, se incrementa `joinTimeouts`, restart queda rechazado y el gate
  no informa cleanup limpio.
- Si el thread termina después del timeout, su callback ejecuta el cleanup una
  sola vez y vuelve a `STOPPED`.
- Se cubrieron stop doble, restart después de stop limpio y salida nativa durante
  inicialización.
- `VpnFlowOwnerCache` usa single-flight con executor y cola bounded; leader y
  followers esperan como máximo 750 ms. Timeout, cancelación o saturación devuelven
  `UNKNOWN` fail-closed.
- `VpnLocalSocks5Server.shutdown()` es bounded, idempotente y devuelve el estado
  de terminación de ambos executors; cualquier timeout queda en métricas.
- `VpnTransportGate09A.close()` no reporta cierre limpio si HEV queda en
  cuarentena o SOCKS no termina.

## Gates automáticos

Código DEV 334 antes de la pérdida de `/private/tmp`:

- `:feature-vpn:testDebugUnitTest`: PASS.
- `:feature-vpn:compileDebugKotlin`: PASS.
- `:feature-vpn:ktlintCheck`: PASS.
- `:feature-vpn:lintDebug`: PASS.
- `:app-user:testDevDebugUnitTest`: PASS.
- `:app-user:compileDevDebugKotlin`: PASS.
- `:app-user:lintDevDebug`: PASS.
- `:app-user:assembleDevDebug`: PASS.
- Resultado agregado: `BUILD SUCCESSFUL in 2m52s`, 850 tasks.
- `git diff --check`: PASS.

Después de reconstruir exactamente el source:

- `:feature-vpn:testDebugUnitTest`: PASS.
- `:feature-vpn:compileDebugKotlin`: PASS.
- `:feature-vpn:ktlintCheck`: PASS.
- Resultado: `BUILD SUCCESSFUL in 16s`, 98 tasks.

Después del hardening lifecycle y DEV 335:

- `:feature-vpn:testDebugUnitTest`: PASS.
- `:feature-vpn:compileDebugKotlin`: PASS.
- `:feature-vpn:ktlintCheck`: PASS.
- `:feature-vpn:lintDebug`: PASS.
- `:app-user:testDevDebugUnitTest`: PASS.
- `:app-user:compileDevDebugKotlin`: PASS.
- `:app-user:lintDevDebug`: PASS.
- `:app-user:assembleDevDebug`: PASS.
- Resultado agregado final: `BUILD SUCCESSFUL in 1m37s`, 850 tasks.
- Tests dirigidos lifecycle/cache/SOCKS: PASS.
- `git diff --check`: PASS.

Hubo una ejecución previa con un fallo intermitente del test existente
`VpnFlowOwnerCacheTest`; el rerun aislado y ambos lotes finales pasaron sin cambios
de expectativa. Warnings no bloqueantes: incompatibilidad informativa SDK XML
v4/tool v3 y futuros targets de anotaciones Kotlin. No hay nueva falla automática.

## Self-audit crítico

### Hallazgo alto resuelto en DEV 335: join nativo fallido

El código anterior esperaba hasta 5 s pero cerraba el bridge aunque el thread
nativo siguiera vivo. DEV 335 elimina ese cierre inseguro. Un timeout conserva los
recursos y cuarentena la instancia; un retorno nativo posterior permite cleanup
exactamente una vez. La propiedad quedó demostrada con dobles de thread/bridge y
tests deterministas, pero la ruta normal aún requiere el stress físico final.

### Hallazgos medios resueltos localmente

- `VpnFlowOwnerCache` ya no espera indefinidamente: leader y followers tienen
  timeout bounded; saturación queda fail-closed como `UNKNOWN`.
- `VpnLocalSocks5Server` expone y registra el resultado bounded de ambos
  `awaitTermination`; el gate rechaza un cleanup incompleto.
- El estado final de route/admisión no puede auditarse con el A23 fuera de ADB.

### UDP/SOCKS/protect

- El retorno IP, checksums y source/destination son responsabilidad de HEV/lwIP;
  el roundtrip byte-identical DEV 333 probó el camino IPv4 real.
- El packet sintético de stress usa checksum UDP IPv4 cero, válido en IPv4; no
  cubre UDP IPv6 físico.
- El control TCP gobierna UDP ASSOCIATE; watcher cierra relay/upstream y las
  carreras conocidas quedan contenidas en DEV 334.
- Source SOCKS queda restringido a loopback y a un único endpoint por asociación;
  FRAG no cero y ATYP DOMAIN se rechazan.
- `protect()` ocurre sobre sockets materializados antes de `connect/send`;
  `protect=false` tiene tests con cero intentos de salida.
- No se relajaron ProxySettings ni las policies Chrome.

## Rollback

El rollback anterior a DEV 334 fue PASS. El rollback final del ticket es
`NO VERIFICADO`: la pérdida de ADB ocurrió con una sesión DEV 334 activa cuya
fixture no estaba dentro del TUN. No se ejecutó ningún reset, clear, uninstall ni
acción destructiva para compensarlo.

## Criterio para reanudar

Cuando macOS vuelva a enumerar el A23:

1. confirmar el estado vigente, instalar DEV 335 in-place y preservar ceDataInode,
   DO/Affiliated, Accessibility y resetCount=1;
2. STOP seguro y verificar rollback;
3. START con gate UDP explícito y comprobar *antes de enviar* UID 10280 y ruta
   `192.168.0.21/32` dentro del VPN;
4. repetir 20 y 200 roundtrips;
5. ejecutar 100 ciclos UDP, canarios TCP/DNS/GloshIA y STOP final;
6. exigir resources/associations/protected UDP final `0`.

No avanzar a full-tunnel hasta que ese bloque pase y ChatGPT revise el hardening
de join nativo.

---

## Cierre final DEV 337

Esta sección es el estado final autoritativo y deja resuelto el blocker histórico
anterior. El A23 volvió a enumerar por ADB y se completó toda la secuencia.

### Coordinación y recuperación de la Mac

- Owner: Protección Android / Codex.
- Base: `87ba18540a4146af0203ead6813df49abb8b72ef`.
- Rama: `work/chrome-vpn-09a-udp-fixture-roundtrip-01`.
- Worktree: `/Users/yejielnehmad/Developer/glosh-chrome-vpn-09a-udp-fixture-roundtrip-01`.
- Funcional UDP inicial: `8028fbfebdf05f706ea323c3db2225ccd0848d0d`.
- Hardening HEV/owner/SOCKS: `bf60c6a0300aebcd3d51dd5cf46cea0ee6b7be24`.
- Hardening cierre SOCKS: `93ea2d6960a4badf550d19e47d8cdcdcad0a9a9d`.
- Funcional final: `ce26034aae9d49cbbd1ad08d9c1ce4e27e7170ef`.
- Sin push, PR, merge, main, Production ni modificación de Glosh Central.

macOS reinició el 24 de agosto a las 12:05. El reporte local
`ResetCounter-2026-08-24-120535.diag` registra `Reset count: 1`,
`Boot failure count: 0`, `Boot faults: wdog,reset_in_1` y `Boot stage: 0x0`.
No hubo panic report convencional. La causa observable fue un reset forzado por
watchdog; se perdió estado efímero bajo `/private/tmp`, no código o commits.

### Causa raíz y hardening final

DEV 335 completó los gates pero su STOP lanzó `IllegalStateException` al vencer
el await del executor SOCKS. DEV 336 dejó de derribar el proceso y puso el cierre
incompleto en cuarentena, pero mostró `ownedFdResources=10` persistentes.

La causa exacta no era HEV ni UDP: cinco workers estaban dentro de
`Socket.connect()` hacia `198.18.0.1` sin timeout. El protected socket todavía no
había retornado al servidor SOCKS, no estaba en `sessions` y el STOP no podía
cerrarlo. DEV 337 usa connect TCP bounded de 3 s y shutdown SOCKS de 5 s. Timeout
libera el resource lease. El mismo escenario físico, con conexiones pendientes y
STOP un segundo después, terminó sin cuarentena ni crash y con recursos `0`.

El lifecycle HEV mantiene la propiedad previa: join timeout conserva bridge/FD,
pasa a `QUARANTINED`, prohíbe restart y sólo limpia tras terminación real. El
owner lookup sigue single-flight/cola bounded con timeout de 750 ms y UNKNOWN
fail-closed.

### Identidad final

| Campo | Valor |
| --- | --- |
| Dispositivo | Samsung A23 SM-A235M, serial `R58T34V31AE` |
| Android | 14 / API 34 |
| Chrome | 151.0.7922.169 |
| App Usuario | DEV 337 / 1.0.1-dev |
| APK SHA-256 | `ec0b13b45be8aad4ed8288718008aa35eccb0277fee263661be80ded2539b7eb` |
| APK bytes | 158794709 |
| Instalación | `adb install -r`, PASS |
| ceDataInode | 1239519 antes/después |
| resetCount | 1; `chrome_reset_skipped generation=1` |
| Device Owner | Glosh, Affiliated |
| Accessibility | enabled=1, enabled/bound |
| VPN final | productiva activa; rutas DNS originales |
| Chrome final | suspendido por STOP fail-closed |

Fixture final:

- package `com.glosh.vpnudpfixture`, versionCode/name `1/1.0`, UID `10280`;
- APK SHA-256 `5519512532ce86034599a790d56570c9e486b17ef324c9499dbdf92f9c707001`;
- 16853 bytes, instalada e inerte al final;
- echo UDP Mac `192.168.0.21:32123` y ruta temporal exacta
  `192.168.0.21/32`.

### Gate UDP DEV 337

20 datagramas:

- sent/received/validated `20/20/20`;
- tamaños `1, 32, 128, 512, 1200` bytes;
- timeouts/duplicates/out-of-order `0/0/0`;
- p50/p95/p99 `20.211/62.522/778.252 ms`; el primer sample incluye setup y
  no es un gate de performance.

200 datagramas:

- sent/received/validated `200/200/200`;
- timeouts/duplicates/out-of-order `0/0/0`;
- p50/p95/p99 `7.013/19.129/37.197 ms`;
- owner UID `10280`, package exacto, policy `FORWARD_TO_HEV`;
- HEV tx/rx acumulado `220/221`;
- SOCKS associations/out/in `2/220/220`;
- protect UDP created/success/failure `2/2/0`;
- recursion `0`; bridge `seqpacket`; queue peak/drops `2/0`.

HEV issue #323:

- empty/truncated/invalid header `1/1/1`;
- datagrama válido posterior PASS;
- SIGSEGV/SIGABRT `0/0`.

Stress UDP:

- `100` ciclos, `stress=complete error=none`;
- SOCKS associations `102`, datagrams out/in `520/520`;
- protected UDP created/success/failure `102/102/0`;
- HEV cleanups `101`, join timeouts `0`;
- SocketException atribuible `0`;
- Java/native crash, ANR, OOM `0/0/0`;
- después del stress, engine baseline resources `4`, asociaciones/protected UDP
  activas `0/0`.

### Canarios de regresión

- TCP Samsung Internet: UID `10262`, `FORWARD_TO_HEV`, SOCKS CONNECT, protect y
  HTTP roundtrip a `192.168.0.21:32123`: PASS.
- DNS `glosh-photos.test`: A/AAAA/HTTPS quedaron en `FilterVpnService`; SOCKS UDP
  permaneció `520/520`, HEV DNS normal `0`.
- SAFE `httpbingo.org/image/png`: `8090 -> 8090`, `model_allow`.
- BLOCK Flickr: `77187 -> 6303`, placeholder PNG, `model_filter`.
- `rawPresented=false`, stale `0`, grid visible `0`,
  `captureRequestsSincePresentationReady=0`, errorCode3 `0`.
- host lógico simultáneo máximo `1`; attachmentCount de la sesión final `1`.
- Chrome direct TCP/UDP no se disparó naturalmente detrás de ProxySettings en
  esta corrida (`0/0`). Los tests mantienen ambos DROP; 09A anterior observó 42
  drops físicos TCP/443. UDP físico: `CHROME_UDP443_NOT_TRIGGERED_UNDER_PROXY`.

### Gates automáticos DEV 337

- `:feature-vpn:testDebugUnitTest`: PASS.
- `:feature-vpn:compileDebugKotlin`: PASS.
- `:feature-vpn:ktlintCheck`: PASS.
- `:feature-vpn:lintDebug`: PASS.
- `:app-user:testDevDebugUnitTest`: PASS.
- `:app-user:compileDevDebugKotlin`: PASS.
- `:app-user:lintDevDebug`: PASS.
- `:app-user:assembleDevDebug`: PASS.
- Agregado: `BUILD SUCCESSFUL in 2m24s`, 850 tasks.
- Dirigidos factory/SOCKS/HEV: `BUILD SUCCESSFUL in 25s`, 98 tasks.
- `git diff --check`: PASS.

Warnings preexistentes no bloqueantes: SDK XML/tool mismatch, futuros targets de
anotaciones Kotlin y API Firebase deprecada. No hubo nueva falla touched-scope.

### Rollback final

- fail-closed y Chrome suspendido antes de desarmar: PASS;
- HEV quit/join real: PASS, `hevJoinTimeouts=0`;
- SOCKS: sin `socks_shutdown=quarantined`;
- owned resources peak/final `10/0`;
- active UDP associations peak/final `2/0`;
- protected UDP sockets peak/final `2/0`;
- ruta `/32`, fixture admission, rutas `198.18.0.1`, proxy y CA retirados;
- VPN/DNS productivo restaurado;
- PSS/native representativo bajo carga `223929/46476 KB`; después de STOP final
  `96929/22236 KB`, sin crecimiento lineal observado;
- último ApplicationExitInfo DEV 337: `PACKAGE_UPDATED`, sin crash nuevo;
- resetCount `1`, ceDataInode `1239519`, DO/Affiliated y Accessibility preservados.

### Riesgos residuales y decisión

- No existe default route todavía; full-tunnel general no está implementado.
- UDP IPv6 no tuvo roundtrip físico en este cierre.
- Chrome UDP/443 debe repetirse físicamente en el futuro gate full-tunnel.
- Handover, throughput/batería y process-death independiente quedan fuera de 09A.

HEV 2.17.1 queda técnicamente aceptado para revisión de ChatGPT y, sólo después,
para un eventual `CHROME-VPN-FULL-TUNNEL-CONTROLLED-10A`. Este ticket no lo inicia.

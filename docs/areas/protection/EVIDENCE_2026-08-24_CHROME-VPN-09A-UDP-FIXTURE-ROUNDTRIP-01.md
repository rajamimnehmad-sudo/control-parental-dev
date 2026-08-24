# CHROME-VPN-09A-UDP-FIXTURE-ROUNDTRIP-01

## Veredicto

`BLOCKED_PHYSICAL_DEVICE_UNAVAILABLE`

El roundtrip UDP controlado quedó demostrado físicamente en DEV 333 con 220/220
respuestas byte-identical, UID/package exactos, HEV, SOCKS5 UDP ASSOCIATE y
`protect()` correctos. Esa sesión descubrió después una carrera de cierre durante
el stress UDP (`SocketException: Socket closed`) y produjo la corrección final
DEV 334.

DEV 334 fue instalada in-place y conserva los datos, pero su primera corrida fue
inválida: la fixture recibió 20 ecos directos mientras TUN/HEV/SOCKS permanecían
en cero, porque el package-replace arrancó una generación sin la admisión/ruta del
gate. No se contó como PASS. Antes del recovery STOP→START, macOS eliminó los
directorios bajo `/private/tmp` y dejó de enumerar el A23 por USB. ADB quedó con
lista vacía, incluso tras reiniciar únicamente el daemon. Por eso no se pudieron
ejecutar sobre DEV 334 el stress UDP, los canarios finales ni el rollback. No se
declara PASS con evidencia de una versión anterior.

## Coordinación y Git

- Owner: Protección Android / Codex.
- Base: `87ba18540a4146af0203ead6813df49abb8b72ef`.
- Rama: `work/chrome-vpn-09a-udp-fixture-roundtrip-01`.
- Worktree original eliminado externamente:
  `/private/tmp/glosh-chrome-vpn-09a-udp-fixture-roundtrip-01`.
- Worktree recuperado persistente:
  `/Users/yejielnehmad/Developer/glosh-chrome-vpn-09a-udp-fixture-roundtrip-01`.
- Commit funcional recuperado: `8028fbfebdf05f706ea323c3db2225ccd0848d0d`.
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
| App Usuario final instalada | DEV 334 / 1.0.1-dev |
| APK SHA-256 | `6a6a1e3271f83f1ba10dbf6809389e7095bc7c751dc16633e6e8f230dc2fac41` |
| APK bytes | 158794709 |
| ceDataInode | 1239519 antes/después de instalación |
| resetCount | 1 |
| Device Owner | Glosh, Affiliated |
| Accessibility | enabled/bound en último precheck válido |
| VPN | productiva preservada en último precheck válido |

El estado final actual no pudo reconfirmarse ni cerrarse por ADB: macOS no ve el
teléfono siquiera como dispositivo USB. Esto no prueba una regresión del A23,
pero impide afirmar rollback final.

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

Hubo una ejecución previa con un fallo intermitente del test existente
`VpnFlowOwnerCacheTest`; el rerun aislado y ambos lotes finales pasaron sin cambios
de expectativa. Warnings no bloqueantes: incompatibilidad informativa SDK XML
v4/tool v3 y futuros targets de anotaciones Kotlin. No hay nueva falla automática.

## Self-audit crítico

### Hallazgo alto: join nativo fallido

`HevTransportEngine.stop()` espera hasta 5 s, calcula `joined`, pero luego cierra
el bridge y borra las referencias incluso si el thread nativo sigue vivo. La ruta
de error de `start()` también hace `quit` y cierra el FD sin join explícito. En los
200 ciclos generales de 09A todos los joins fueron reales, pero el fallback viola
el orden declarado `quit -> join real -> FD close` si alguna vez hay timeout. Antes
del full-tunnel debe preservarse el estado fail-closed, no cerrar/reutilizar el FD
y no permitir restart hasta un join confirmado.

### Hallazgos medios

- `VpnFlowOwnerCache` usa `CompletableFuture.get()` sin timeout para seguidores.
  Está fuera del reader TUN y detrás de dos workers/cola bounded, pero un lookup
  Binder colgado podría consumir ambos workers y llenar la cola.
- `VpnLocalSocks5Server.close()` ignora el booleano de `awaitTermination`; si un
  worker no termina en 2 s, el cierre continúa. Los contadores owned ayudan a
  detectarlo, pero DEV 334 no completó el stress físico final.
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

1. confirmar DEV 334, ceDataInode, DO/Affiliated, Accessibility y resetCount=1;
2. STOP seguro y verificar rollback;
3. START con gate UDP explícito y comprobar *antes de enviar* UID 10280 y ruta
   `192.168.0.21/32` dentro del VPN;
4. repetir 20 y 200 roundtrips;
5. ejecutar 100 ciclos UDP, canarios TCP/DNS/GloshIA y STOP final;
6. exigir resources/associations/protected UDP final `0`.

No avanzar a full-tunnel hasta que ese bloque pase y ChatGPT revise además el
hallazgo de join nativo.

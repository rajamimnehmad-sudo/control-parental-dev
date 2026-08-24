# CHROME-VPN-TRANSPORT-ENGINE-FEASIBILITY-09A

## Resultado

`BLOCKED_PHYSICAL_UDP`

El transporte controlado quedó demostrado de punta a punta para TCP no-Chrome y
la política física descartó TCP/443 directo de Chrome. El bridge packet-oriented,
HEV, SOCKS5 local autenticado y `VpnService.protect()` funcionan sin segundo VPN
ni ruta por defecto. El ticket no puede declararse PASS porque no se consiguió
generar un flujo UDP no-Chrome reproducible que hiciera roundtrip completo por el
TUN en el A23. Tampoco se generó físicamente UDP/443 directo de Chrome. Ambos
caminos tienen cobertura determinista, pero eso no sustituye el gate físico.

No se acepta HEV aún para el siguiente gate full-tunnel. El próximo paso mínimo
es una fixture Android no-Chrome, sin datos privados, capaz de fijar un destino
UDP `/32` o `/128`, enviar un nonce y validar el eco de retorno.

## Coordinación

- Owner: Protección Android / Codex.
- Base: `ddfa0cab2e033d64a2f0968b541dad7f63be0eb3`.
- Rama: `work/chrome-vpn-transport-engine-feasibility-09a`.
- Worktree: `/private/tmp/glosh-chrome-vpn-transport-engine-feasibility-09a`.
- Glosh Central se revisó antes de escribir y no se modificó.
- No hubo otro writer sobre `feature-vpn` / Chrome transport.
- No se hizo push, PR, merge, Production ni publicación.

## HEV pinneado y supply chain

- Proyecto: `heiher/hev-socks5-tunnel`.
- Release: `2.17.1`.
- Commit: `9a06bc6e7989da54e3d32ff701ef7a7ce4995d3a`.
- `hev-socks5-core`: `162dd996299fc2d2bff2dd63728f8a2cd71ed31a`.
- `hev-task-system`: `328f35d903221b51811b3d02b277d665dfbdc75f`.
- lwIP fork: `2a11c14c7a32887af25a034e82ef18b0b12076ac`.
- yaml: `efa36117a8646d26d12b58e05bac472d7854a70d`.
- Licencias: HEV/core/task/yaml MIT; lwIP BSD-3-Clause con avisos.
- No se incluyeron Wintun, componentes Windows ni ejecutable standalone.
- El fix de issue #323, `c02e3fd7150a41254ce31c619e97126c79682070`,
  es ancestro del commit pinneado.
- Issue #315 sigue siendo riesgo residual upstream; se ejecutó el stress nativo
  específico descrito abajo.

La revisión de licencias es de ingeniería, no dictamen legal. Los textos y pins
quedaron en `third_party/hev-socks5-tunnel/`.

## Toolchain y build reproducible

- NDK: Android r27d `27.3.13750724`, DMG oficial descargado desde Google.
- SHA-1 verificado del DMG: `80f11292080fab4e869799f1d23caa88dcf3c709`.
- Scratch de source: `/private/tmp/glosh-hev-feasibility`.
- Scratch de toolchain: `/private/tmp/glosh-ndk-r27d`.
- Entrada reproducible:
  `third_party/hev-socks5-tunnel/build_android.sh <ndk-r27d> <source-pinneado>`.
- `APP_PLATFORM`: Android 29.
- Flags conservados: `-Wl,-z,max-page-size=16384` y
  `-Wl,-z,common-page-size=16384`.

La primera librería física cargaba el `JNI_OnLoad` Android opcional de upstream,
que busca la clase fija `hev/htproxy/TProxyService`; eso impedía cargar HEV en
Glosh. El build final excluye sólo ese adapter Java opcional y usa la API C
estable mediante `libglosh-hev-bridge.so`. También excluye fuentes no-Android y
Wintun. No se cambió código upstream del datapath.

### Artefactos nativos

| ABI | `libhev-socks5-tunnel.so` bytes | SHA-256 |
| --- | ---: | --- |
| arm64-v8a | 341448 | `9e947572593dde5a139a8dae619e7e343c9b998a1f19e43c9094ba275f000a0a` |
| armeabi-v7a | 232964 | `e2de0ab0d86936c869b74e4f4f049aaf190ae877ee79cfcf0978539a3b4dbab1` |
| x86_64 | 342592 | `6b0bb69a8db3edce77a48893161add78044d7c6f9a86174b365b9299f99d9e2c` |
| x86 | 360400 | `bcc61c4ab49da2126c494bf0e18864b27b02603407b23a12b084d4b45c6c7bc4` |

| ABI | `libglosh-hev-bridge.so` bytes | SHA-256 |
| --- | ---: | --- |
| arm64-v8a | 5328 | `af650582947bbfa674fbc4cc3e8984c36fba39d1e65d5815e9306be16035fc20` |
| armeabi-v7a | 3828 | `a5429210daea773a5b0817eaf4893426178c37e227362d75fff1081f3d5b6e15` |
| x86_64 | 5312 | `938ba2639c719f230afedfe5d030707a441b86555ee4d943baa7643bc8ee2ee6` |
| x86 | 4400 | `05cd7093425071b780d4e3e6cb7254390be06f6423179271cfcbc53f4163e074` |

Los cuatro HEV tienen LOAD alignment `0x4000`. Sus únicas entradas `NEEDED` son
`libc.so`, `libm.so` y `libdl.so`. Exportan
`hev_socks5_tunnel_main_from_str`, `hev_socks5_tunnel_quit` y
`hev_socks5_tunnel_stats`. El APK contiene ambas librerías en los cuatro ABI y
`zipalign -c -P 16 -v 4` terminó con `Verification successful`.

## Arquitectura implementada para el gate

```text
Android TUN (un solo reader)
        |
VpnPacketDispatcher (bounded, generation)
        |
        +-- UDP/53 -> DNS Glosh existente
        +-- TCP/53 -> fail closed explícito
        +-- transport -> parser -> owner cache -> policy
                                      |
                                      +-- Chrome TCP/UDP 443 -> DROP
                                      +-- owner sensible UNKNOWN -> DROP
                                      +-- no-Chrome autorizado -> HEV
                                                                 |
                                                      AF_UNIX SOCK_SEQPACKET
                                                                 |
                                                      SOCKS5 localhost + auth
                                                                 |
                                           protect(socket) antes de connect/send
                                                                 |
                                                              Internet
```

- `FilterVpnService` conserva lifecycle/orquestación; parser, dispatcher,
  policy, bridge, HEV, SOCKS y protected sockets están fuera del service.
- `FilterVpnService.kt` quedó en 1053 líneas; la nueva responsabilidad de
  transporte está modularizada. Queda como deuda reducir más el service antes
  de incorporar un full-tunnel.
- No se agregó `0.0.0.0/0` ni `::/0`.
- Se usaron únicamente rutas `/32` y `/128` de la fixture/sesión.
- No se creó un segundo VPN ni otro reader del TUN.

### FD ownership y packet bridge

- El dispatcher es dueño único del Android TUN.
- El bridge crea `AF_UNIX SOCK_SEQPACKET`; `SOCK_DGRAM` queda como fallback
  explícito. Nunca usa `SOCK_STREAM`.
- Glosh duplica/transfiere el extremo entregado a HEV; HEV no lo cierra.
- Stop: `hev_socks5_tunnel_quit()` -> join real -> cierre de extremos/colas.
- Start no es reentrante; stop es idempotente; doble stop y restart están
  cubiertos.
- Tests prueban un write por packet, separación de packets, longitudes hasta el
  MTU, backpressure y close/unblock.
- Colas bounded; pico físico observado `7`, drops `0`.

### Parser, owner y policy

- IPv4 valida IHL, totalLength, TCP/UDP y fragmentos.
- IPv6 recorre de forma bounded Hop-by-Hop, Routing, Destination Options y
  Fragment hasta TCP/UDP; malformed/rebasing ambiguo no se autoriza.
- `getConnectionOwnerUid` no corre por packet: cache bounded, TTL, single-flight
  y generation invalidation.
- FIN/RST/nuevo SYN, timeout y cambio de generación invalidan autoridad.
- Para flujo saliente: source es local y destination es remote.
- Chrome se decide por UID resuelto con verificación de packages bounded.
- UID sensible desconocido no hereda política no-Chrome.

### SOCKS y protect

- Bind sólo loopback, puerto efímero.
- Usuario/clave aleatorios por sesión; no persistidos ni logueados.
- RFC1929 obligatorio; NO-AUTH no se ofrece.
- CONNECT y UDP ASSOCIATE; BIND no implementado.
- ATYP IPv4/IPv6 limitado a destinos del gate; DOMAIN rechazado.
- UDP: source validado, FRAG=0; FRAG!=0 se descarta, associations/buffers/timeouts
  bounded.
- TCP: socket creado y materializado/bound, `protect()` verificado y recién
  después `connect()`.
- UDP: socket creado y materializado/bound, `protect()` verificado y recién
  después `connect/send`.
- Test `protect=false`: `connectAttempts=0`, `sendAttempts=0`.

La primera prueba DEV 330 reveló que `Socket()`/`DatagramSocket(null)` aún no
tenían FD materializado al invocar `protect()`. La corrección final hace bind a
wildcard/puerto efímero antes de `protect`, conservando estrictamente
`protect-before-connect/send`. En DEV 331, `protectFailures=0`.

## Gates automáticos finales

Todos PASS sobre el código final:

- `:feature-vpn:testDebugUnitTest`.
- `:feature-vpn:compileDebugKotlin`.
- `:feature-vpn:ktlintCheck`.
- `:feature-vpn:lintDebug`.
- `:app-user:testDevDebugUnitTest`.
- `:app-user:compileDevDebugKotlin`.
- `:app-user:lintDevDebug`.
- `:app-user:assembleDevDebug`.
- `git diff --check`.

El lote Gradle final terminó `BUILD SUCCESSFUL` en 2m27s, 850 tasks. No se
desactivaron tests. Los unitarios cubren packet bridge, parser IPv4/IPv6 y
fragmentos, owner/cache/generation, policy, SOCKS auth/CONNECT/UDP, DOMAIN/FRAG
reject, protect-before-connect/send, HEV lifecycle y rollback.

## APK e instalación

- Variante: DEV.
- versionCode: `331`.
- versionName: `1.0.1-dev`.
- APK: `app-user/build/outputs/apk/dev/debug/app-user-dev-debug.apk`.
- Tamaño: `158778325` bytes.
- SHA-256: `fe1a631a748ca7e982d87f9fe79203759f62d744e373fe6eef2cb2e322ccafb9`.
- Baseline DEV 328 registrada: `64489704` bytes.
- Delta: `+94288621` bytes. Es un riesgo de packaging y debe reducirse antes de
  producto; el gate universal incluye cuatro ABI y dependencias nativas de la app.
- Instalación: `adb install -r`, in-place.
- `ceDataInode`: `1239519` antes y después.

## Gate físico A23

- Dispositivo: Samsung A23 `SM-A235M`, serial `R58T34V31AE`.
- Android 14 / API 34.
- Chrome 151.
- Device Owner: Glosh, `Affiliated`.
- Accessibility: enabled `1`, componente Glosh enabled/bound.
- resetCount: `1`; todas las sesiones registraron
  `chrome_reset_skipped generation=1 resetCount=1`.
- App Usuario y datos preservados.

### TCP no-Chrome — PASS

- App: Samsung Internet.
- UID: `10262`, package `com.sec.android.app.sbrowser`.
- Destino controlado observado:
  `[2a09:8280:1:7fcb:9efa:a365:aa2a:d036]:443`.
- Owner: resuelto correctamente.
- Policy: `FORWARD_TO_HEV`.
- Pico de sesión: `forwarded=114`, `returned=132`, `hevTxPackets=114`,
  `hevRxPackets=132`, `socksTcp=3`, `protectFailures=0`, `recursion=0`.
- El contenido público de httpbingo cargó visualmente en Samsung Internet; hubo
  respuesta de vuelta al TUN/app, no sólo salida.

### UDP no-Chrome — BLOCKED

- Se intentó generar QUIC/UDP con Samsung Internet contra un endpoint público que
  anuncia HTTP/3 y también mediante Google Search.
- Las apps eligieron TCP o resolvieron una IP CDN distinta de la única `/128`
  autorizada. Tras STOP/START y nueva resolución no apareció un pseudo-flow UDP
  controlado.
- Resultado físico final: `socksUdp=0`; no existe roundtrip UDP no-Chrome.
- No se agregó otra ruta amplia, no se alteró el DNS y no se relajó seguridad para
  fabricar el resultado.
- Los unitarios de UDP ASSOCIATE, respuesta, source validation, FRAG, DOMAIN y
  protect pasan, pero no reemplazan este gate.

### Chrome directo

- UID físico: `10222`, package `com.android.chrome`.
- TCP/443 directo: **PASS físico DROP**, `chromeTcpDrops=42`, incluyendo destinos
  IPv4 e IPv6; ningún packet fue enviado a HEV.
- UDP/443 directo: policy/test PASS, pero no fue generado físicamente;
  `chromeUdpDrops=0`.
- No se retiró ProxySettings ni se abrió Chrome directo para forzar el caso.

### DNS

- DNS productivo se mantuvo en el reader único y en sus rutas originales.
- UDP/53 no se entrega a HEV; TCP/53 es fail-closed explícito en 09A.
- Tras rollback quedaron sólo las rutas DNS productivas/local TUN; desaparecieron
  `198.18.0.1` y las rutas `/32`/`/128` de los fixtures.
- HEV no observó DNS normal.

### GloshIA / presentación — PASS regresión

- Modelo: `tinyclip-r3-head-hybrid-int8.onnx`, GloshIA Visual R3.1.
- SHA-256: `c8b64af8092d3718c58736a511c996d0d443dacf3eaa74620b1e5af439a3cd48`.
- Thresholds/preprocessing/mapping: sin cambios.
- SAFE: PNG/JPEG/WebP reales, `model_allow`, bytesIn=bytesOut.
- BLOCK conocido público:
  `farm6.staticflickr.com/.../15526796846_f43d9eb869_o.jpg`,
  `model_filter`, probability `0.6040119`, bytesIn `77187`, placeholder PNG
  bytesOut `6303`; original nunca entregado.
- `rawPresented=false`.
- `stale=0`.
- `captureRequestsSincePresentationReady=0`.
- `errorCode3=0`.
- grilla/marcador visible: `0` (diagnóstico OFF por defecto).

## Stress nativo y recursos

- Ciclos: `200` totales en dos lotes consecutivos de 100.
- Cada ciclo: start -> tráfico pequeño -> quit -> join -> FD close -> restart.
- Resultado: ambos `stress=complete cycles=100 error=none`.
- `SIGABRT=0`, `SIGSEGV=0`, native crash `0`, Java crash `0`, ANR atribuible
  `0`, OOM `0`.
- ApplicationExitInfo posterior sólo contiene `PACKAGE UPDATED` de las instalaciones
  DEV 329/330/331. Los ANR visibles del 23/08 son históricos y anteriores a 09A.
- PSS/native antes: `222353 KiB / 38640 KiB private`.
- Después de 100 ciclos: `225559 KiB / 41864 KiB private`.
- Después de 200 ciclos: `231176 KiB / 38284 KiB private`.
- El native heap terminó por debajo del baseline; no hubo crecimiento nativo lineal.
- FD count del proceso no pudo leerse por ADB: el APK no es debuggable y Android
  devolvió `Permission denied` para `/proc/<pid>/fd`. Join/close se verifican por
  lifecycle y unitarios, pero el conteo físico FD antes/después queda pendiente.

## Rollback final

PASS para todo el estado creado por 09A:

- `phase=fail_closed reason=manual_stop`.
- `rollback=complete proxy=cleared ca=removed`.
- `rollback=vpn_restored action=refresh_routes`.
- `phase=stopped rollback=complete cache=cleared`.
- Transport: `status=inactive`.
- Chrome: suspendido/fail-closed.
- Proxy global: `null`.
- Rutas fixture `/32`/`/128`: retiradas.
- Rutas DNS y VPN productivas: preservadas y activas.
- Device Owner/Affiliated: preservado.
- Accessibility: enabled/bound.
- `ceDataInode=1239519`.
- `resetCount=1`.

## Archivos y responsabilidades

- `feature-vpn/.../service/FilterVpnService.kt`: orquestación del dispatcher y
  lifecycle/gates DEV; no contiene SOCKS ni JNI.
- `feature-vpn/.../service/VpnPacketParser.kt`: parser endurecido.
- `feature-vpn/.../transport/**`: dispatcher, bridge, owner cache, policy, HEV,
  SOCKS y protected sockets.
- `feature-vpn/src/main/jniLibs/**`: artefactos nativos pinneados de cuatro ABI.
- `third_party/hev-socks5-tunnel/**`: build reproducible, JNI Glosh y notices.
- `app-user/src/dev/**`: comandos DEV status/stress.
- Tests correspondientes en `feature-vpn/src/test/**`.
- `app-user/build.gradle.kts`: versionCode y ABI del gate DEV.

## Riesgos residuales y siguiente paso

1. Falta roundtrip UDP no-Chrome físico; es el blocker principal.
2. Falta Chrome UDP/443 DROP físico, aunque policy/test existe.
3. Falta conteo físico FD por restricciones `/proc` del build instalado.
4. Issue upstream #315 no reprodujo en 200 ciclos, pero no tiene fix causal
   identificado y sigue siendo riesgo.
5. El APK universal creció ~94.3 MB; hace falta estrategia ABI/split antes de uso
   real.
6. No se probó full-tunnel, default route, handover exhaustivo, NAT64/DNS64 ni
   multi-user de Production.
7. `FilterVpnService` aún supera 1000 líneas y debe continuar reduciéndose.

Siguiente ticket recomendado:
`CHROME-VPN-09A-UDP-FIXTURE-ROUNDTRIP-01`, limitado a una app Android de fixture
no-Chrome, endpoint echo UDP controlado y ruta exacta reversible. Debe cerrar UDP
roundtrip, Chrome UDP/443 DROP físico y métrica FD interna sin agregar ruta por
defecto. Sólo después corresponde decidir si HEV queda aceptado para el gate
full-tunnel.

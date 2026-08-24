# CHROME-VPN-TRANSPORT-ARCHITECTURE-08B

Estado Codex: **PASS ARCHITECTURE**. Es un spike DEV; no implementa full tunnel ni
autoriza Production.

## Identidad y alcance

- Base: `7b58056eb9498cf515553e45f5ad51a87d030149`.
- Commit funcional: `05278e7f0fccb585117a3a79ce2d4a6c940ba07c`.
- Rama: `work/chrome-vpn-transport-architecture-08b`.
- Worktree: `/private/tmp/glosh-chrome-vpn-transport-architecture-08b`.
- `feature-vpn`: `minSdk=29`, `compileSdk=36`.
- No se agregó `0.0.0.0/0`, `::/0`, un segundo VPN ni una dependencia externa.
- No se modificaron GloshIA, Chrome data-plane, thresholds, reset de Chrome ni
  comportamiento productivo del VPN.

La API oficial de Android confirma que
[`ConnectivityManager.getConnectionOwnerUid`](https://developer.android.com/reference/android/net/ConnectivityManager#getConnectionOwnerUid(int,%20java.net.InetSocketAddress,%20java.net.InetSocketAddress))
existe desde API 29, acepta TCP/UDP y devuelve el UID cuando el flow pertenece al
tunel del `VpnService` llamante. `INVALID_UID` y `SecurityException` son resultados
que deben tratarse explicitamente. Android permite un solo VPN activo y el descriptor
TUN entrega paquetes IP salientes; los sockets upstream deben usar
[`VpnService.protect`](https://developer.android.com/reference/android/net/VpnService#protect(int))
para evitar recursion.

## Inventario exacto del VPN actual

`FilterVpnService` crea un unico TUN IPv4/IPv6 (`10.8.0.2/32` y
`fd00:1:fd00:1::2/128`) y aplica una sola tabla de rutas a todas las aplicaciones
admitidas mediante `addAllowedApplication`.

En modo normal captura:

- DNS del enlace y rutas host de resolvers conocidos;
- destinos de DNS cifrado/hosts bloqueados calculados por
  `DnsEnforcementRoutePlanner`;
- durante el lab, solamente `/32` y `/128` resueltos y acotados.

En modo estricto existe una barrera `0/0`, pero el read loop no implementa forwarding:

- parsea IPv4/IPv6 UDP/53 tradicional;
- reenvia DNS permitido mediante `DatagramSocket` o `Socket` protegidos;
- TCP no-DNS, UDP no-DNS, ICMP, fragmentos y protocolos no soportados terminan como
  `Unsupported` y no se reinyectan al TUN;
- por eso agregar hoy `0/0` descartaria el Internet TCP/UDP general.

El precheck fisico mostro `always_on_vpn_app=null` y lockdown desactivado. El servicio
es `START_STICKY`, maneja reconnect por policy/domain-list y `onRevoke`, pero no tiene
un `NetworkCallback` explicito para handover. Handover y la garantia independiente
ante muerte completa del proceso requieren hardening posterior, junto con
always-on/lockdown o suspension administrada.

`FilterVpnService.kt` ya superaba el umbral estructural y ahora tiene 1004 lineas. La
nueva responsabilidad esta en tres piezas aisladas; el siguiente ticket no debe sumar
otra responsabilidad al Service sin extraer un dispatcher de transporte.

## Spike implementado

- `VpnFlowTupleParser`: extrae exclusivamente protocolo, IPs y puertos de TCP/UDP
  IPv4/IPv6; no lee ni retiene payload.
- `VpnConnectionOwnerResolver`: llama la API Android por cada 5-tuple; devuelve
  `Resolved`, `Unknown` o `PermissionDenied`.
- El cache acotado (32) contiene solo UID -> paquetes para diagnostico. No cachea
  autoridad de flows.
- `VpnConnectionOwnerDiagnostics`: activo solo durante el lab DEV, hasta 256 flows y
  tres intentos para `UNKNOWN`; se limpia con el lifecycle del VPN.
- La integracion en el Service agrega solo create/observe/clear. El resultado no
  modifica routing ni decisiones de red.

Tests agregados:

- TCP con UID valido;
- UDP con UID valido;
- `INVALID_UID`;
- `SecurityException`;
- cache UID-paquete acotado;
- resolucion/mapeo de multiples paquetes;
- parser TCP/UDP IPv4;
- rechazo de protocolo no soportado y fragmento no inicial.

Gates:

- `:feature-vpn:testDebugUnitTest`: PASS;
- `:feature-vpn:compileDebugKotlin`: PASS;
- `:feature-vpn:ktlintCheck`: PASS;
- `:app-user:compileDevDebugKotlin`: PASS;
- `:app-user:assembleDevDebug`: PASS.

## Gate fisico A23

Dispositivo: Samsung A23 `SM-A235M`, Android 14/API 34, serial sanitizado en el
handoff operativo. Se uso solamente la matriz `/32`/`/128` que el lab ya resolvia; no
se agrego ruta default.

APK DEV 328:

- versionName `1.0.1-dev`;
- SHA-256 `ddaea5ebc9f3c07ec04d70aa633c4d4679451dcf36dc32ac03c8d6f6cfeeeb44`;
- `adb install -r`: `Success`;
- `ceDataInode` App Usuario: `1239519` antes/despues;
- bootstrap: `chrome_reset_skipped generation=1 resetCount=1`;
- Device Owner/Affiliated y Accessibility enabled/bound preservados.

Resultados representativos (IPs publicas de la fixture, sin payload):

| Protocolo | UID | Paquete | Resultado |
|---|---:|---|---|
| UDP/53 | 10222 | `com.android.chrome` | resolved |
| TCP/443 IPv4 | 10222 | `com.android.chrome` | resolved |
| TCP/443 IPv6 | 10222 | `com.android.chrome` | resolved |
| UDP/53 | 10262 | `com.sec.android.app.sbrowser` | resolved |
| TCP/443 IPv4/IPv6 | 10262 | `com.sec.android.app.sbrowser` | resolved |
| UDP/53 | 10214 | `com.google.android.googlequicksearchbox` | resolved |
| TCP/853 inicial | - | - | `UNKNOWN` tras tres intentos; no se reclasifico |

Chrome y Samsung Internet produjeron TCP/443 directos a destinos controlados. La
politica vigente los conto y descarto; ninguno fue abierto para hacer pasar el gate.
Esto demuestra simultaneamente que la atribucion funciona y que el control de
transporte es necesario incluso cuando Chrome tiene proxy administrado.

Rollback:

- STOP seguro entregado por broadcast de paquete;
- `rollback=complete proxy=cleared ca=removed`;
- lab service detenido;
- Chrome volvio a suspendido/fail-closed;
- rutas publicas controladas IPv4/IPv6 desaparecieron;
- quedaron solamente rutas productivas DNS/encrypted-DNS;
- `FilterVpnService` continuo activo;
- ping general 2/2 y apertura de `example.com` desde Samsung Internet completaron;
- DO, Accessibility, datos y `resetCount=1` preservados.

## Invariante por UID propuesta

La identidad de politica es el UID resuelto para el 5-tuple actual, con verificacion
bounded de paquetes; el nombre de paquete no fabrica autoridad.

```text
unico Android TUN IPv4/IPv6
  -> parser/dispatcher de primer paquete
  -> getConnectionOwnerUid(local, remote)
     -> Chrome verificado:
          UDP/443 directo DROP
          TCP/443 directo DROP
          proxy loopback autorizado; upstream Glosh usa protect()
     -> otra app verificada:
          transport forwarding TCP/UDP
     -> UNKNOWN donde la politica pueda afectar Chrome:
          cola corta/reintento bounded y luego DROP
  -> DNS siempre permanece en el policy engine actual
```

No se pueden tener readers concurrentes sobre el mismo TUN. El diseño mantenible es
que un dispatcher unico conserve DNS y autoridad UID, y entregue solo paquetes
admitidos al engine de transporte mediante un FD packet-oriented interno. El engine
habla con un SOCKS5 local; ese servidor abre los sockets reales con `protect()`. Asi
el engine no pierde la decision UID y no recursa en el VPN.

## Opciones de transport engine

### A. Stack propio Kotlin/Java

- Licencia: propia; sin native code.
- Ventaja: hooks UID perfectos.
- Riesgo: muy alto. Exige TCP state machine, retransmision, congestion, UDP pseudo
  sessions, fragmentacion, IPv6/NAT64, MTU, backpressure y handover.
- Decision: descartado como primera opcion mantenible.

### B. HEV Socks5 Tunnel

- Proyecto activo en C, Android NDK, licencia
  [MIT](https://github.com/heiher/hev-socks5-tunnel/blob/main/LICENSE).
- Declara IPv4/IPv6, TCP, UDP (incluido UDP-over-TCP), limites de sesiones, buffers,
  timeouts, MTU, API por file descriptor y estadisticas.
- Su `Application.mk` vigente declara `armeabi-v7a`, `arm64-v8a`, `x86`, `x86_64`,
  Android 29 y flexible page sizes.
- MIT es compatible en principio con uso comercial y Google Play si se conservan
  notices, pero falta auditar dependencias transitivas, SBOM, CVEs, tamaño real,
  comportamiento Play/16 KiB y proceso reproducible de build.
- No ofrece un hook UID de producto probado. Debe recibir solo packets ya admitidos
  por el dispatcher; no debe leer en paralelo el TUN Android.
- Decision: **candidato recomendado para un feasibility gate**, no dependencia
  aprobada aun.

### C. Outline

- `outline-go-tun2socks` es Apache-2.0 pero fue archivado; su mantenimiento migro a
  Outline Apps/Intra.
- `outline-sdk` actual es Apache-2.0, Go, multiplataforma y composable, pero sigue
  marcado Beta y la integracion Android/UID/FD introduce gomobile/native runtime y
  mayor tamaño.
- Decision: alternativa secundaria si HEV no acepta el FD interno o no pasa el gate
  de lifecycle/performance.

### D. xjasonlyu/tun2socks

- Amplia cobertura gVisor TCP/UDP/IPv6, pero licencia GPL-3.0.
- Decision: descartado para el producto comercial salvo decision legal distinta.

No se agrego ninguna de estas dependencias en 08B.

## Riesgos y lifecycle obligatorio

- `INVALID_UID`: nunca convertir en otra app; reintento bounded y fail-closed.
- UID reuse/package change: invalidar mapeos en package events, VPN restart y cambio
  de usuario; verificar paquete al admitir el flow.
- UDP: tabla 5-tuple con timeout bounded, no autoridad eterna.
- Chrome multiprocess/Custom Tabs/isolated UID: matriz fisica pendiente; la politica
  debe reconocer UIDs asociados verificablemente y no solo el UID principal.
- IPv6 extension headers, fragmentacion, NAT64/DNS64 y MTU: delegar al engine maduro y
  gatear fisicamente; el parser del spike no constituye parser de producto.
- Handover Wi-Fi/datos: cerrar flows, revocar autoridad Chrome, reconstruir TUN y
  proteger sockets nuevos antes de liberar.
- Flows abiertos antes del cambio: deben revalidarse por 5-tuple; si Android ya no
  tiene owner, se descartan.
- Process death: sin always-on lockdown Android restaura red al cerrar el FD. Antes de
  Production debe combinarse DO always-on/lockdown con guard de suspension Chrome.
- Bateria/ANR: native loop fuera del main thread, queues y buffers bounded, watchdog y
  metricas de session count/bytes/latencia.

## Decision y siguiente ticket

La hipotesis de **un solo VPN + full tunnel futuro + autoridad por UID** es viable en
el A23. Android distinguio Chrome y otras apps para TCP y UDP dentro del TUN real. La
seleccion final del transport requiere un gate acotado, no una adopcion ciega.

Siguiente ticket recomendado:

`CHROME-VPN-TRANSPORT-ENGINE-FEASIBILITY-09A`

Debe extraer primero el dispatcher fuera de `FilterVpnService`, construir un harness
sin ruta default con FD packet-oriented interno, integrar HEV MIT de forma aislada y
probar: TCP/UDP/IPv4/IPv6, SOCKS5 local con `protect()`, DNS derivado al engine actual,
backpressure, fragmentacion/MTU, handover, ABIs/16 KiB, SBOM/licencias/tamaño y policy
hook UID. Solo despues de ese PASS corresponde un ticket full-tunnel real.

# CHROME-VPN-FULL-TUNNEL-CONTROLLED-10A

## Veredicto

`BLOCKED REVIEW FOLLOW-UP`

El gate físico integral DEV 339 documentado abajo conserva su resultado `PASS DEV`.
La revisión final de ChatGPT detectó después un defecto real de ordering en el
startup: la autoridad runtime se adquiría después de iniciar SOCKS y HEV. El commit
local de remediación corrige el defecto y pasa todos los gates automáticos, pero el
smoke DEV 340 no pudo acreditar una nueva entrega SAFE: el endpoint de httpbingo
terminó en timeout y los intents alternativos de Chrome restauraron/reutilizaron la
pestaña BLOCK sin producir un request SAFE nuevo (`safe=0`). No se infiere PASS.

Se demostró físicamente un full-tunnel IPv4/IPv6, explícito y reversible, sobre
el único `VpnService` Glosh. Las apps no-Chrome conservaron conectividad TCP/UDP
mediante HEV; DNS siguió fuera de HEV; Chrome directo TCP/443 y UDP/443 quedó
fail-closed; el proxy Chrome + GloshIA siguió entregando SAFE original y BLOCK
placeholder. El rollback retiró ambas default routes y dejó el transporte
productivo DNS previo, sin recursos HEV/SOCKS activos.

Esto no declara semántica web general, autoridad completa de contenido ni
Production. Es el gate DEV controlado previo a esos tickets.

## Coordinación y Git

- Base exacta: `85b5e13a8ae233bfb551b0e99c5e23f7dc44ba8f`.
- Funcional: `d51a86559614eaf42a220c1d8b60ce7bf92dd888`.
- Rama: `work/chrome-vpn-full-tunnel-controlled-10a`.
- Worktree persistente:
  `/Users/yejielnehmad/Developer/glosh-chrome-vpn-full-tunnel-controlled-10a`.
- Owner: Protección Android / Codex.
- Glosh Central se consultó como coordinación y no se modificó.
- Sin push, PR, merge, main, Production ni publicación.

## Follow-up de revisión: startup ordering DEV 340

### Defecto y corrección

Antes, `VpnTransportGate09A.start()` ejecutaba `socks.start()` y `engine.start()`
antes de `VpnTransportRuntimeAuthority.begin()`. Un runtime ya `QUARANTINED` podía
rechazar la generación sólo después de haber abierto listener, bridge/FD y thread
nativo.

Ahora `VpnTransportStartupCoordinator` adquiere la authority como primera operación,
antes de construir o iniciar cualquier recurso de transporte. Si `begin()` rechaza,
el callback de startup no se ejecuta: SOCKS starts, HEV starts y recursos nuevos
quedan exactamente en cero, y el runtime sigue `QUARANTINED`.

Si la authority fue adquirida pero falla el startup, el cleanup real mide:

- `HevTransportEngine.stop()`: join y cleanup completo;
- `VpnLocalSocks5Server.shutdown()`: ambos executors terminados;
- `VpnOwnedResourceTracker`: owned FD/resources en cero.

Sólo esos tres resultados limpios devuelven authority a `READY`. Join incompleto,
SOCKS sucio, recursos vivos o una excepción del propio cleanup dejan el runtime
`QUARANTINED`; no existe liberación incondicional en `finally`.

Commit funcional del follow-up:
`e0068181` (`fix(vpn): acquire transport authority before startup`).

### Regresiones automáticas

`VpnTransportStartupCoordinatorTest` acredita:

1. runtime previamente `QUARANTINED`: cero callbacks SOCKS/HEV, cero recursos y
   estado sin cambio;
2. fallo de startup con cleanup limpio: recursos cero, `READY` y siguiente
   generación permitida;
3. fallo de startup con cleanup sucio: `QUARANTINED`, siguiente generación rechazada
   y sin segundo start nativo.

Suite final completa:

| Gate | Resultado |
| --- | --- |
| `:feature-vpn:testDebugUnitTest` | PASS |
| `:feature-vpn:compileDebugKotlin` | PASS |
| `:feature-vpn:ktlintCheck` | PASS |
| `:feature-vpn:lintDebug` | PASS |
| `:app-user:testDevDebugUnitTest` | PASS |
| `:app-user:compileDevDebugKotlin` | PASS |
| `:app-user:lintDevDebug` | PASS |
| `:app-user:assembleDevDebug` | PASS |
| `git diff --check` | PASS |

Gradle: `BUILD SUCCESSFUL` en 2m15s, 850 tasks. Warnings preexistentes: tooling
SDK XML, kapt/Kotlin y APIs Firebase deprecadas.

### APK DEV 340 y smoke físico estrecho

- versionCode/name: `340 / 1.0.1-dev`;
- SHA-256:
  `d18e063d54c057aaa5ae7f7392be2846659ab008049faa96a021a630ff493437`;
- tamaño: `158811145` bytes;
- instalación: `adb install -r`, sin uninstall ni clear;
- ceDataInode: `1239519`, preservado;
- resetCount: `1`;
- Device Owner/Affiliated: preservado.

Accessibility estaba desactivada antes del smoke (`accessibility_enabled=0`) aunque
el componente Glosh seguía instalado; se restauró exclusivamente ese componente.
La actualización in-place volvió a poner el switch global en cero, y se reactivó
sin alterar otros servicios. El estado final quedó enabled + bound.

Resultados válidos DEV 340:

- startup normal: runtime `running`, HEV y SOCKS iniciados después de authority;
- `0.0.0.0/0` y `::/0` presentes dentro de `tun0`;
- Samsung Internet UID `10262`: HTTPS real IPv6, `FORWARD_TO_HEV`, retorno PASS;
- HEV DNS `0`, `protectFailures=0`, recursion `0`;
- Chrome directo: `101` drops TCP/443 y `24` drops UDP/443 físicos;
- BLOCK Flickr: `77187 -> 6303`, `model_filter`, placeholder; original no entregado;
- `rawPresented=false`, stale/grid/capturas post-ready `0` según la
  instrumentación conservada.

Gate incompleto:

- SAFE público httpbingo: conexiones TLS terminaron en `SocketTimeoutException`;
- intentos SAFE alternativos fueron absorbidos por restauración/reutilización de
  tabs y no generaron un request nuevo;
- status final antes de STOP: `safe=0`, `blocked=4`, `engineCalls=1`.

Por ello el follow-up queda `BLOCKED` exclusivamente en el canario SAFE físico; el
ordering defect queda corregido y verificado automáticamente, y el datapath BLOCK/
fail-close sí fue revalidado.

Rollback DEV 340: PASS. STOP produjo HEV join limpio, SOCKS limpio,
`transportRuntime=ready`, owned resources/UDP associations/protected UDP finales
`0/0/0`; se retiraron ambas default routes y la ruta/admission del fixture, se
restauró VPN/DNS productivo y Chrome quedó suspendido fail-closed.

## Arquitectura implementada

```text
apps admitidas
      |
      v
único Android TUN 0.0.0.0/0 + ::/0 (sólo flag DEV de sesión)
      |
      v
VpnPacketDispatcher (único reader, cola bounded, orden único)
      |
      +-- UDP/53 --------------------------> DNS Glosh existente
      +-- TCP/53 --------------------------> fail-close explícito
      |
      v
getConnectionOwnerUid + cache/single-flight/TTL/generation
      |
      +-- UNKNOWN -------------------------> DROP
      +-- Chrome externo TCP/UDP ----------> DROP
      +-- Chrome proxy local autorizado ---> proxy HTTPS + GloshIA
      +-- otras apps, público unicast ------> HEV -> SOCKS5 loopback
                                                -> protect()
                                                -> Internet
```

- El gate default-route está OFF por defecto y ligado al `sessionId` actual.
- Activación inicial: el flag se configura antes del primer `establish()` y
  antes de liberar Chrome. Un intento exploratorio DEV 338 que cambió la ruta
  después del release disparó correctamente `vpn_lost` y fue descartado como
  corrida inválida; DEV 339 usa únicamente activación atómica al START.
- Destinos non-Chrome: IPv4/IPv6 público unicast. Se rechazan loopback,
  link-local, multicast, RFC1918/ULA y rangos reservados; puertos DNS 53/853 no
  pueden entrar al SOCKS general.
- Chrome: sólo el tuple TCP local exacto `127.0.0.1:8877` es elegible en la
  política; todo transporte externo atribuible a Chrome se descarta.
- El lookup owner es bounded: single-flight, TTL/generation, espera máxima
  750 ms y cola bounded. Timeout/rejection/INVALID_UID => UNKNOWN => DROP.
- HEV/lwIP recibe packets en orden mediante un único worker. Backpressure
  descarta fail-closed; no existe cola ilimitada.
- Todo socket upstream SOCKS usa `VpnService.protect()` antes de connect/send.
  Error de protect produce cero salida (tests heredados y regresión verde).
- Teardown sucio de HEV/SOCKS/resources deja la autoridad runtime en
  `QUARANTINED`; una nueva generación queda rechazada hasta cleanup probado.

## Archivos funcionales

- `app-user/build.gradle.kts`
- `app-user/src/dev/AndroidManifest.xml`
- `app-user/src/dev/java/com/contentfilter/user/chromedataplane/ChromePhotosDataPlaneLabReceiver.kt`
- `app-user/src/dev/java/com/contentfilter/user/chromedataplane/ChromePhotosDataPlaneLabService.kt`
- `feature-vpn/src/main/java/com/contentfilter/feature/vpn/service/ChromePhotosDataPlaneLabVpnPolicy.kt`
- `feature-vpn/src/main/java/com/contentfilter/feature/vpn/service/FilterVpnService.kt`
- `feature-vpn/src/main/java/com/contentfilter/feature/vpn/service/VpnController.kt`
- `feature-vpn/src/main/java/com/contentfilter/feature/vpn/transport/VpnDestinationAuthority.kt`
- `feature-vpn/src/main/java/com/contentfilter/feature/vpn/transport/VpnFlowOwnerCache.kt`
- `feature-vpn/src/main/java/com/contentfilter/feature/vpn/transport/VpnLocalSocks5Server.kt`
- `feature-vpn/src/main/java/com/contentfilter/feature/vpn/transport/VpnTransportDevStatus.kt`
- `feature-vpn/src/main/java/com/contentfilter/feature/vpn/transport/VpnTransportGate09A.kt`
- `feature-vpn/src/main/java/com/contentfilter/feature/vpn/transport/VpnTransportPolicy.kt`
- `feature-vpn/src/main/java/com/contentfilter/feature/vpn/transport/VpnTransportRuntimeAuthority.kt`
- tests correspondientes en `feature-vpn/src/test/...`.

## Gates automáticos

Una única corrida final DEV 339, después de los cambios funcionales:

| Gate | Resultado |
| --- | --- |
| `:feature-vpn:testDebugUnitTest` | PASS |
| `:feature-vpn:compileDebugKotlin` | PASS |
| `:feature-vpn:ktlintCheck` | PASS |
| `:feature-vpn:lintDebug` | PASS |
| `:app-user:testDevDebugUnitTest` | PASS |
| `:app-user:compileDevDebugKotlin` | PASS |
| `:app-user:lintDevDebug` | PASS |
| `:app-user:assembleDevDebug` | PASS |
| `git diff --check` | PASS |

Gradle terminó `BUILD SUCCESSFUL` en 2m24s (850 tasks). Sólo aparecieron warnings
ya conocidos: versión XML SDK/tooling, kapt/Kotlin, APIs Firebase deprecated y
strip de algunas librerías nativas. No hubo falla nueva touched-scope.

Cobertura añadida: gate OFF default; sesión incorrecta; rutas default v4/v6;
destinos públicos/internos; UDP/TCP 53 fuera de HEV; Chrome TCP/UDP DROP; proxy
local exacto; UNKNOWN DROP; owner timeout/rejection/generation; runtime
clean/quarantine/recovery y shutdown SOCKS observable.

## APK y precheck A23

| Campo | Valor |
| --- | --- |
| Dispositivo | Samsung A23 `SM-A235M` |
| Android | 14 / API 34 |
| Chrome | `151.0.7922.169` |
| App Usuario | DEV 339 / `1.0.1-dev` |
| APK SHA-256 | `f17e3dc881f4026a906a77750c92e1895bbfaabf548b336bf965e633956d0587` |
| APK bytes | `158811145` |
| Instalación | `adb install -r`, PASS |
| ceDataInode App Usuario | `1239519` antes/después |
| resetCount | `1` antes/después; `chrome_reset_skipped generation=1` |
| Device Owner | Glosh, Affiliated |
| Accessibility | enabled + bound |

No hubo uninstall, `pm clear`, reset de Chrome ni pérdida de datos.

## Default routes y owner authority

En la sesión válida `dafa84d4...`, CA `38a44fba8a1085ab...`:

- `transport=full_tunnel_dev` antes de release de Chrome.
- `0.0.0.0/0 -> tun0` presente en `LinkProperties` y tabla VPN.
- `::/0 -> tun0` presente en `LinkProperties` y tabla VPN.
- direcciones TUN: `10.8.0.2/32` y `fd00:1:fd00:1::2/128`.
- UIDs bajo el único VPN durante el gate:
  - Google Search `10214`;
  - Chrome `10222`;
  - Samsung Internet `10262`;
  - fixture UDP temporal `10280`.
- queue peak/drop: `31/0`.
- owner timeout/queue drop: `0/0`.
- UNKNOWN se descartó; máximo observado `24` luego del reconnect, sin forward.

## TCP/IPv4/IPv6 non-Chrome

- Samsung Internet UID `10262`: navegación HTTPS real a `httpbingo.org`,
  `FORWARD_TO_HEV`, SOCKS CONNECT, protect y retorno a la app: PASS.
- Segunda app, Google Search UID `10214`: flows externos TCP/443 forward físicos.
- IPv6 físico: Samsung Internet alcanzó `https://ipv6.google.com/`; se observaron
  flows a `2800:3f0:4002:80b::200e`, `2001:4860:4842:400::` y otros IPv6
  globales, todos owner-resolved y `FORWARD_TO_HEV`: PASS.
- Canario TCP post-stress y post-reconnect: PASS; `socksTcp=15`,
  forwarded/returned final activo `748/1335`.

## UDP público non-Chrome

La fixture temporal externa se actualizó fuera del monorepo, misma UID y sólo
permiso INTERNET:

- package: `com.glosh.vpnudpfixture`;
- candidata usada para el roundtrip UDP: versionCode/name `2 / 1.1-10a`,
  APK SHA-256
  `dcbbf1799fed4af329c3f7a1afe1edd234288ccebd49ef0a619bc2e0b06aefa6`;
- versión final instalada e inerte después del canario TCP/53:
  versionCode/name `3 / 1.2-10a`, APK SHA-256
  `7f1bc45efc2daefdc6b5111acdc81f7aa953a298562bc457435cfac56b202607`;
- endpoint público Digi: `52.43.121.77:10001/UDP`;
- tamaños: `1, 32, 128, 512, 1200` bytes;
- sent/received/byte-identical: `20/20/20`;
- timeout/duplicate/out-of-order: `0/0/0`.

Trayecto acreditado: fixture -> TUN -> UID `10280` -> `FORWARD_TO_HEV` -> HEV ->
SOCKS UDP ASSOCIATE -> protected UDP -> Internet -> respuesta -> HEV -> TUN ->
misma fixture. Métricas después del gate público: asociaciones `3`, out/in
`22/21` (incluyen dos probes previos, uno de un endpoint público sin respuesta),
protected UDP created/success/failure `3/3/0`, recursion `0`.

La evidencia funcional no usa el echo LAN: el PASS 20/20 fue contra Internet.
El echo Mac `192.168.0.21:32123` quedó reservado sólo para stress lifecycle y fue
apagado al rollback.

## DNS

- A/AAAA/HTTPS/SVCB normales siguieron siendo parseados por `FilterVpnService`;
  se registraron consultas de `google.com`, `accounts.google.com`, `gstatic.com`,
  `play.google.com`, `httpbingo.org` e `ipv6.google.com` en el pipeline Glosh.
- UDP/53 retorna al parser DNS existente antes del transport gate.
- TCP/53 entra en acción `ExistingDnsPath` y se descarta explícitamente. Una
  segunda sesión física estrecha, sin cambios a Glosh ni a DEV 339, disparó desde
  la fixture UID `10280` un connect a `8.8.8.8:53`: el dispatcher registró
  `action=existingdnspath`, la app terminó en `SocketTimeoutException` esperado y
  `dnsTcpDrops` subió de `0` a `2` (SYN/retransmisión). HEV permaneció exactamente
  en `tx/rx=67/55` antes y después del flow: PASS físico TCP/53 fuera de HEV.
- DoT/853 no se admite al transport general.
- SOCKS ATYP DOMAIN y HEV mapped DNS permanecen deshabilitados.
- HEV DNS normal observado: `0`.

## Chrome / GloshIA / presentación

- Chrome UID `10222` produjo físicamente `85` drops TCP externos y `6` drops
  UDP/443/QUIC. Se observaron IPv4 e IPv6; ninguno entró a HEV.
- ProxySettings no se relajó. El proxy local autorizado siguió activo y los
  sockets upstream propios salen protegidos fuera del TUN.
- SAFE `https://httpbingo.org/image/png`: `8090 -> 8090`, `model_allow`, body
  original byte-idéntico.
- BLOCK conocido Flickr `.../15526796846_f43d9eb869_o.jpg`:
  `77187 -> 6303`, `model_filter`, probability `0.6040119`, placeholder PNG;
  original nunca entregado.
- GloshIA R3.1, modelo, SHA, thresholds y preprocessing: sin cambios.
- `rawPresented=false` en todos los logs.
- stale commits/results observados: `0`.
- `captureRequestsSincePresentationReady=0`, `captureFailures=0`, `errorCode3=0`.
- attachmentCount `1`, host lógico simultáneo máximo `1`.
- marker/grid DEV configurado OFF; apariciones visibles reportadas: `0`.

## Handover, stress y memoria

- La SIM no tenía servicio (`OUT_OF_SERVICE`), así que no se declara
  Wi-Fi -> datos móviles.
- Se ejecutó el equivalente seguro Wi-Fi OFF -> Wi-Fi ON:
  generation `2 -> 3` por `underlying_lost` y `3 -> 4` por
  `underlying_available`; cache owner invalidada dos veces.
- Tras reconectar, Samsung Internet creó nuevos sockets/owner lookups y recuperó
  Internet vía HEV. Recursion permaneció `0`.
- Stress HEV/SOCKS dentro del full-tunnel: `100/100` ciclos, entre 1 y 5 UDP
  roundtrips por ciclo (`300` ecos controlados), quit/join/close y restart.
- `hevCleanupCount=101`, `hevJoinTimeouts=0`.
- SOCKS associations/out/in acumulado: `103/322/321` (el único out sin retorno
  fue el probe público previo a un servicio caído, fuera del PASS funcional).
- SocketException atribuible, SOCKS shutdown timeout: `0/0`.
- SIGABRT/SIGSEGV/native crash/Java crash/ANR/OOM atribuibles a DEV 339:
  `0/0/0/0/0/0`.
- `ApplicationExitInfo` posterior a instalación DEV 339 sólo mantiene el proceso
  vivo; entradas anteriores quedaron separadas por timestamp/version.
- PSS/native antes: `233004 KiB / 42124 KiB`.
- PSS/native después: `139469 KiB / 37260 KiB`; sin crecimiento lineal.
- owned resources peak/final: `14/0`.
- active UDP associations peak/final: `1/0`.
- protected UDP sockets peak/final: `1/0`.

## Rollback

Secuencia: fail-close/STOP -> Chrome suspendido -> proxy cerrado -> CA/policy
retiradas -> HEV quit/join -> SOCKS cerrado -> caches limpias -> VPN productiva
reestablecida -> flag full-tunnel explícitamente OFF -> STOP final.

La sesión estrecha TCP/53 repitió el mismo rollback: runtime `inactive/ready`,
owned resources `0`, sin default routes, fixture retirada de los UIDs del VPN y
Chrome bloqueado físicamente mediante `ActionDisabledByAdminDialog`.

Verificación final:

- transport `inactive`, runtime `ready`;
- owned resources `0`, UDP associations `0`, protected UDP `0`;
- sin `0.0.0.0/0` ni `::/0` en `tun0`;
- sin ruta `192.168.0.21/32` del fixture;
- UID fixture `10280` retirada del VPN; quedan sólo `10214/10222/10262`;
- sólo rutas DNS productivas/local TUN;
- VPN productiva activa y validada;
- DO/Affiliated y Accessibility enabled/bound;
- resetCount `1`, ceDataInode `1239519`;
- al intentar abrir Chrome se mostró
  `com.android.settings/.enterprise.ActionDisabledByAdminDialog`.

La fixture APK queda instalada pero inerte; el echo local fue detenido.

## Riesgos residuales / fuera de alcance

- No hubo handover celular por ausencia de servicio; sí hubo invalidación y
  recuperación física Wi-Fi.
- La allowlist HTTP/TLS del proxy de Chrome sigue siendo la del laboratorio; los
  `connect_not_allowed` observados son deuda esperada de semántica web 11A, no
  bypass de transporte.
- No se cierra todavía WebSocket, Service Worker/CacheStorage, canvas/blob/data,
  PDF, video, DRM, process-death guard ni autoridad completa de imagen.
- El full-tunnel permanece exclusivamente DEV, session-bound y OFF tras rollback.

Siguiente paso recomendado, sólo después de revisión ChatGPT:
`PROXY-WEB-SEMANTICS-11A` sobre esta base; no fue iniciado.

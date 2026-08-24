# CHROME-VPN-FULL-TUNNEL-CONTROLLED-10A — diseño previo

Estado: PREPARED / NO EJECUTAR HASTA REVISAR UDP FIXTURE ACTUAL.

## Propósito

Convertir el VPN DEV desde rutas DNS + fixtures acotadas a un full-tunnel reversible que capture tráfico IPv4/IPv6 de las apps incluidas, preserve el DNS productivo de Glosh y aplique autoridad distinta por UID sin segundo VPN.

No generalizar todavía el proxy HTTPS de Chrome ni modificar GloshIA.

## Preconditions

- `CHROME-VPN-09A-UDP-FIXTURE-ROUNDTRIP-01` debe dar PASS y su diff/evidencia debe ser aprobado por ChatGPT.
- HEV queda pinneado a 2.17.1 / `9a06bc6e7989da54e3d32ff701ef7a7ce4995d3a`.
- `protect()` debe estar demostrado para TCP y UDP.
- rollback de 09A debe dejar recursos owned=0.

## Arquitectura

```text
Android apps
   |
   v
unico VpnService / TUN
   |
VpnPacketDispatcher (unico reader/writer)
   |
   +-- DNS -> VpnDnsHandler -> Glosh policy -> protected DNS upstream
   |
   +-- transport -> VpnFlowOwnerCache -> UID/package authority
                          |
                          v
                  VpnTransportPolicy
                    /      |       \
                 DROP   FORWARD   CHROME
                           |         |
                           |      direct external DROP
                           |         |
                           |      local proxy autorizado
                           v         |
                          HEV <------+ no: proxy upstream sale protegido
                           |
                    local SOCKS5
                           |
                 protected TCP/UDP
                           |
                        Internet
```

## Routing gate

Sólo para DEV/gate:

- IPv4 `0.0.0.0/0` y IPv6 `::/0` pueden agregarse únicamente después de que transport engine + SOCKS estén READY.
- Nunca establecer default routes antes de que el forwarder esté sano.
- Si HEV/SOCKS/protect falla, no mantener un TUN que descarte Internet silenciosamente: fail-close Chrome y rollback o degraded-safe para las demás apps según estado exacto.
- El builder debe conservar DNS routes y browser/app inclusion actuales.

## Política de flows

### DNS

- UDP/53 -> DNS Glosh existente.
- TCP/53 -> handler explícito o fail-close; nunca forward silencioso.
- DoT/853 -> política actual de encrypted DNS.
- DoH -> no resolver por HEV; tráfico web sigue policy de app/host.

### Chrome

Regla de producto más fuerte que el spike:

- Todo flow externo de `com.android.chrome` capturado por el TUN se considera bypass directo salvo excepción explícita y verificable.
- TCP/443 -> DROP.
- UDP/443 -> DROP.
- Otros puertos externos -> DROP por default durante 10A salvo fixtures/autorizaciones específicas.
- El proxy local de Chrome no debe cruzar el TUN como tráfico de Chrome; su upstream debe usar protected sockets de Glosh.
- UNKNOWN owner en flow potencialmente Chrome/sensible -> DROP.

### Apps no-Chrome

- FORWARD por HEV/SOCKS si owner resuelto y policy general lo permite.
- UNKNOWN owner: policy conservadora; no asumir non-Chrome.
- Shared UID e isolated UID deben registrar ambiguity y no heredar autorización silenciosamente.

## Owner cache

Clave:

`protocol + localIP + localPort + remoteIP + remotePort + networkGeneration`

Requisitos:

- lookup sólo primer SYN/primer UDP datagram;
- single-flight bounded;
- TTL TCP/UDP separado;
- FIN/RST invalidation;
- timeout;
- nuevo SYN/reuse invalida;
- handover/VPN restart incrementa generation y vacía cache;
- UID->packages cache con TTL/package generation;
- ninguna llamada Binder por packet.

## Parser

Antes de default routes:

IPv4:
- IHL y total length;
- checksum no es obligatorio para clasificación pero malformed debe rechazarse;
- TCP/UDP;
- fragments first/non-first con state acotado o drop explícito.

IPv6:
- payload length;
- Hop-by-Hop;
- Routing;
- Destination Options;
- Fragment;
- límite estricto de extensión/cadena;
- TCP/UDP;
- malformed => drop.

## Handover

`networkGeneration` debe cambiar cuando cambia la red subyacente.

Al cambio:

- invalidar flow owners;
- cerrar SOCKS TCP/UDP sessions;
- detener/join HEV si corresponde;
- reconstruir protected upstream sockets;
- revalidar DNS;
- Chrome sigue fail-closed hasta health nuevo.

Gate mínimo:

- Wi-Fi off/on o Wi-Fi->datos;
- non-Chrome recupera Internet;
- Chrome direct bypass sigue 0;
- DNS recupera;
- no flow viejo conserva authority.

## Start ordering

1. resolver config y app inclusion;
2. iniciar local SOCKS;
3. verificar credential/session;
4. crear packet bridge;
5. iniciar HEV y esperar READY;
6. iniciar dispatcher/owner cache;
7. establecer TUN con default routes;
8. acreditar transport health;
9. sólo entonces permitir recuperación/release de Chrome si proxy/GloshIA también están sanos.

## Stop ordering

1. revocar Chrome lease / suspender Chrome;
2. dejar de admitir nuevos transport flows;
3. cerrar dispatcher queues;
4. cerrar SOCKS associations/sessions;
5. HEV quit;
6. join real;
7. cerrar packet bridge FDs;
8. cerrar protected sockets;
9. limpiar caches/generation;
10. reconstruir VPN productivo sin default routes si el gate termina;
11. verificar DNS y apps normales.

## Health contract

Nueva dimensión de health:

- `transportEngineReady`
- `transportDispatcherReady`
- `protectedSocketAuthorityReady`

Chrome release exige:

`proxy && policy && vpn && gloshia && accessibility && transport`

Pérdida post-release de transport => `transport_lost` y Chrome suspendido.

## Gates automáticos

- full-tunnel builder sólo se activa con transportReady;
- owner cache bounded/single-flight;
- Chrome direct all-external default DROP;
- non-Chrome FORWARD;
- UNKNOWN sensitive DROP;
- DNS no entra HEV;
- protected socket false => 0 connect/send;
- parser IPv4/IPv6/fragments;
- networkGeneration invalidation;
- start/stop idempotente;
- rollback routes exacto.

## Gate físico

Apps:

- Samsung Internet;
- Google Search u otra no-Chrome;
- Chrome.

Demostrar:

- Samsung Internet abre varios sitios por full-tunnel;
- TCP + UDP no-Chrome roundtrip;
- Chrome direct TCP/UDP no sale;
- Chrome autorizado via proxy histórico funciona en hosts de laboratorio;
- DNS Glosh funciona;
- no recursion;
- handover básico;
- 0 crash/ANR/OOM;
- resources final 0;
- rollback restaura estado anterior.

## Stop conditions

BLOCKED si:

- default route rompe apps no-Chrome;
- UID no puede resolverse antes de policy;
- UDP/TCP return path pierde paquetes;
- DNS entra HEV;
- protected socket reaparece en TUN;
- Chrome direct bypass llega a Internet;
- handover hereda flow authority vieja;
- lifecycle HEV no limpia.

## Handoff hacia 11A

10A PASS no significa Chrome general usable. Su único cierre es:

> El único VPN puede capturar tráfico general sin romper otras apps y puede impedir que Chrome evada el proxy/GloshIA.

Luego recién se elimina la allowlist exacta del proxy en `CHROME-PROXY-WEB-SEMANTICS-11A`.
# CHROME-PHOTOS-VPN-TRANSPORT-DECISION-08B

Fecha: 2026-08-24

Estado: **DECISIÓN DE RUTA / IN PROGRESS**.

`CHROME-PHOTOS-PROXY-SEMANTICS-08A` terminó `BLOCKED_ARCHITECTURE` en el HEAD local docs-only `7b58056eb9498cf515553e45f5ad51a87d030149`, rama `work/chrome-photos-proxy-semantics-08a`, worktree limpio. No hubo APK ni código funcional.

## Bloqueo confirmado

El `VpnService` actual comparte una sola interfaz/routing table para el conjunto protegido y su datapath productivo procesa esencialmente DNS. Extender rutas a Internet completo sin un transport forwarder haría caer TCP/UDP de las demás apps. Limitar el VPN sólo a Chrome preservaría el experimento Chrome pero retiraría la protección VPN productiva al resto de las apps. Android permite un solo VPN activo por usuario/perfil y `VpnService.Builder` permite elegir aplicaciones incluidas/excluidas a nivel de interfaz, no una tabla de rutas distinta por aplicación.

`ProxySettings` por sí solo no es autoridad anti-bypass: Chrome Android no dispone de la política `QuicAllowed` que existe en desktop y una aplicación puede intentar caminos que no obedecen una recomendación HTTP proxy.

## Decisión

No adoptar como arquitectura de producto la opción temporal de dedicar el único VPN exclusivamente a Chrome.

Abrir `CHROME-VPN-TRANSPORT-ARCHITECTURE-08B`: rediseñar el **único** `VpnService` como full-tunnel/transport authority para el conjunto de apps que ya deben seguir protegidas, preservando el filtrado DNS actual.

Dato habilitante: `feature-vpn` tiene `minSdk=29`. Desde API 29, `ConnectivityManager.getConnectionOwnerUid(protocol, local, remote)` permite al VPN activo obtener el UID propietario de una conexión TCP o UDP asociada con su túnel. Esto no crea rutas por-app, pero permite clasificar flujos dentro del datapath del único VPN.

Arquitectura objetivo a validar antes de implementar en grande:

1. una sola interfaz VPN;
2. default/full routes para el tráfico que deba quedar bajo autoridad;
3. DNS filtrado como hoy;
4. forwarding TCP/UDP normal para apps no-Chrome, usando sockets upstream protegidos para evitar recursión;
5. atribución de flujo por UID en API 29+;
6. para UID de Chrome: UDP/443/QUIC fail-closed y TCP/443 directo fail-closed salvo transporte autorizado hacia el proxy local/GloshIA;
7. para otras apps protegidas: forwarding normal sin obligarlas a usar el proxy Chrome;
8. no segundo VPN, no root, no reducción de protección de otras apps.

## Próximo gate

Antes de construir un transport stack completo, ejecutar un spike 08B acotado:

- validar `getConnectionOwnerUid` físicamente en A23 para TCP y UDP, distinguiendo Chrome de al menos otra app;
- inventariar exactamente qué rutas/apps cubre hoy `FilterVpnService`;
- probar en una ruta de fixture acotada que el UID puede resolverse desde el 5-tuple visto en TUN;
- evaluar la opción mínima mantenible para forwarding TCP/UDP (implementación propia vs componente maduro compatible con políticas/Play), sin integrar nada todavía;
- producir diseño de módulos, lifecycle, protect(), backpressure, IPv4/IPv6, QUIC, process death y gates;
- no generalizar rutas a 0/0 hasta que exista forwarding seguro.

`FULL-RESET-BOOTSTRAP-05/05A` conserva PASS FINAL DEV. `GENERAL-WEB-AUDIT-06` queda DONE con hallazgos. `PROXY-SEMANTICS-08A` queda BLOCKED_ARCHITECTURE, no FAILED.

No push, PR, merge ni Production de Chrome local hasta revisión y autorización.
# CHROME-VPN-TRANSPORT-ARCHITECTURE-08B — ChatGPT final review

Fecha: 2026-08-24

Estado: **PASS FINAL ARCHITECTURE / DEV SPIKE**.

Base del spike: `7b58056eb9498cf515553e45f5ad51a87d030149`.
Commit funcional local: `05278e7f0fccb585117a3a79ce2d4a6c940ba07c`.
HEAD/evidencia local: `ddfa0cab2e033d64a2f0968b541dad7f63be0eb3`.
Rama: `work/chrome-vpn-transport-architecture-08b`.

## Decisión

La hipótesis de un único `VpnService` con atribución de conexiones por UID quedó validada físicamente para el alcance del spike. `ConnectivityManager.getConnectionOwnerUid()` devolvió propietarios diferenciados para TCP/UDP e IPv4/IPv6 en A23, incluyendo `com.android.chrome`, Samsung Internet y Google Search, sin convertir todavía el VPN en full-tunnel ni romper el DNS/product filtering existente.

El diff de 08B queda aceptado como diagnóstico/arquitectura, no como datapath productivo. La implementación es DEV-only y bounded; no modifica routing general. Las limitaciones detectadas por self-audit (dirección inbound, IPv6 extension headers/fragments, cache TTL/invalidation, isolated/shared UID y llamadas Binder síncronas) son bloqueantes antes de reutilizar este parser/resolver como autoridad de producción, pero no invalidan el objetivo del spike.

## HEV pre-flight

La auditoría previa encontró como candidato `heiher/hev-socks5-tunnel` 2.17.1 / commit `9a06bc6e7989da54e3d32ff701ef7a7ce4995d3a`, con licencia principal MIT. El upstream expone API C con `tun_fd` externo y build Android como shared library, API 29 y ABIs arm64-v8a, armeabi-v7a, x86 y x86_64. La compatibilidad concreta del diseño Glosh `dispatcher -> socketpair packet-oriented -> HEV -> SOCKS5 local -> protected upstream` sigue sin demostrarse físicamente y es el objetivo del siguiente feasibility gate.

No se aprueba HEV para Production. Quedan pendientes build local reproducible, auditoría de la `.so`, lifecycle JNI, crash isolation, socketpair/packet boundaries, SOCKS5 local, `protect()` fail-closed, DNS preservation, IPv6/NAT64/fragmentation/MTU/backpressure/handover y stress.

## Próximo frente

`CHROME-VPN-TRANSPORT-ENGINE-FEASIBILITY-09A`.

Debe mantenerse sin rutas `0/0`: usar sólo `/32` y `/128` controladas, demostrar roundtrip TCP/UDP de una app no-Chrome mediante el engine y SOCKS protegido, mientras Chrome direct TCP/443 y UDP/443 quedan DROP; DNS Glosh, Device Owner, Accessibility, datos y `resetCount=1` deben preservarse.

No avanzar todavía a navegación general, REGION-DETECTOR, video, DRM, full-tunnel general o Production.
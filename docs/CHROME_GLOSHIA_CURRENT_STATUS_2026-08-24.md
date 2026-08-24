# Chrome oficial + GloshIA — estado vigente

Actualizado: 2026-08-24

## Coordinación

- `FULL-RESET-BOOTSTRAP-05/05A`: PASS FINAL DEV revisado por ChatGPT.
- `CHROME-VPN-TRANSPORT-ARCHITECTURE-08B`: PASS FINAL ARCHITECTURE revisado por ChatGPT.
- `CHROME-VPN-TRANSPORT-ENGINE-FEASIBILITY-09A`: BLOCKED_PHYSICAL_UDP histórico en HEAD local `87ba18540a4146af0203ead6813df49abb8b72ef`.
- `CHROME-VPN-09A-UDP-FIXTURE-ROUNDTRIP-01`: **BLOCKED_PHYSICAL_DEVICE_UNAVAILABLE**. Base `87ba18540a4146af0203ead6813df49abb8b72ef`; funcional `8028fbfebdf05f706ea323c3db2225ccd0848d0d`; evidencia `33fd79aa4b65729830a572836a71898064d0b8e0`; rama `work/chrome-vpn-09a-udp-fixture-roundtrip-01`, worktree limpio. DEV333 demostró 220/220 roundtrips UDP byte-identical por TUN/HEV/SOCKS/protect, pero reveló una carrera de teardown con `SocketException: Socket closed`. La corrección quedó en DEV334 (`versionCode=334`, SHA-256 `6a6a1e3271f83f1ba10dbf6809389e7095bc7c751dc16633e6e8f230dc2fac41`) y todos los gates automáticos pasaron, pero el A23 dejó de aparecer en `adb devices -l` antes del stress físico final, canarios y rollback. No se declara PASS ni se acepta HEV para full-tunnel todavía.
- Riesgo nuevo antes de full-tunnel: `HevTransportEngine.stop()` no debe cerrar bridge/FD ni permitir restart después de un join timeout sin confirmar que el thread nativo terminó realmente. Además quedan timeouts/lifecycle menores en `VpnFlowOwnerCache` y `VpnLocalSocks5Server` para revisión.
- Próximo paso exacto: reanudar **el mismo ticket** cuando el A23 vuelva a ADB y ejecutar una única secuencia DEV334 `STOP→START → 20/200 UDP por TUN → stress 100 → TCP/DNS/GloshIA → resources=0 → rollback`, sin abrir otro ticket ni default routes.

## Prework adelantado por ChatGPT sin colisión con Codex

ChatGPT preparó únicamente documentación/arquitectura en la rama de auditoría; no tocó `feature-vpn`, HEV, APK ni runtime.

- `CHROME_VPN_FULL_TUNNEL_CONTROLLED_10A_DESIGN.md`: diseño ejecutable del próximo full-tunnel controlado, commit `279eb6af343ec32b93e3636bdc14fcc6494ba3db`.
- `CHROME_PROXY_WEB_SEMANTICS_11A_CONTRACT.md`: contrato del proxy web general, commit `1e40a6a2e447318237e9f2921d808b16c4f30035`.
- `CHROME_IMAGE_CONTENT_AUTHORITY_11B_DESIGN.md`: autoridad visual por MIME+magic+decode bounded, compresión, Range/206, formatos still/animados, SVG y cache generation-bound; commit `a6ea2446ef6e3b272abf09837115bb991813f8c6`.
- `CHROME_IMAGE_CONTENT_AUTHORITY_11B_CODEX_DRAFT.md`: prompt futuro 11B, commit `75e8ee855429505ad4917724db2dd7df1aa319b2`.
- `CHROME_GLOSHIA_REALTIME_SCHEDULER_14A_DESIGN.md`: scheduler viewport-aware/dedupe/backpressure, commit `ad2ae472cdcf2a5f71706dbb6533d1bf77dbb40a`.
- `CHROME_PROVENANCE_GAP_13A_FIXTURE_MATRIX.md`: fixtures para `data:`, `blob:`, canvas, WebGL, SVG y Service Worker, commit `cc5902f3dcf87958b73851cd83b2bf0d08dfeb3b`.
- `CHROME_PROCESS_DEATH_GUARD_10B_DESIGN.md`: guard independiente ante crash/kill/reboot/update, commit `f1274fd9b48ff894e19ef7bbb5a02ef0a83aa5f6`.
- `CHROME_GENERAL_WEB_PERF_14_PLAN.md`: benchmark p50/p95/p99, memoria, batería, térmica y long-run, commit `b2a07e0b69c0849bb70d97b5526a4e98270b30ae`.

## Veredicto

El UDP de producto quedó demostrado en DEV333, pero **la candidata corregida DEV334 todavía no tiene gate físico final**. El bloqueo actual es disponibilidad del A23, no evidencia de fallo de HEV. Aun así, el riesgo de lifecycle nativo por join timeout debe cerrarse/revisarse antes de autorizar full-tunnel.

Chrome general todavía no está listo como producto. No avanzar a full-tunnel, REGION-DETECTOR, video, DRM, Production, merge ni publicación hasta revisar el cierre físico DEV334.
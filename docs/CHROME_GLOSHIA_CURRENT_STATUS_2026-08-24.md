# Chrome oficial + GloshIA — estado vigente

Actualizado: 2026-08-24

## Coordinación

- `FULL-RESET-BOOTSTRAP-05/05A`: PASS FINAL DEV revisado por ChatGPT.
- `CHROME-VPN-TRANSPORT-ARCHITECTURE-08B`: PASS FINAL ARCHITECTURE revisado por ChatGPT.
- `CHROME-VPN-TRANSPORT-ENGINE-FEASIBILITY-09A`: BLOCKED_PHYSICAL_UDP histórico en HEAD local `87ba18540a4146af0203ead6813df49abb8b72ef`.
- `CHROME-VPN-09A-UDP-FIXTURE-ROUNDTRIP-01`: **BLOCKED_PHYSICAL_DEVICE_UNAVAILABLE**. Base original `87ba18540a4146af0203ead6813df49abb8b72ef`; entrada de reanudación `33fd79aa4b65729830a572836a71898064d0b8e0`; funcional actual `bf60c6a0300aebcd3d51dd5cf46cea0ee6b7be24`; evidencia/final `52b6f9bd9fd3e16d85542bc4bcc865e5f8326e2c`; rama `work/chrome-vpn-09a-udp-fixture-roundtrip-01`, worktree limpio. El trabajo perdido previamente fue recuperado completamente.
- DEV333 conserva evidencia válida de datapath UDP real: fixture `com.glosh.vpnudpfixture` UID 10280, policy `FORWARD_TO_HEV`, 220/220 roundtrips byte-identical por TUN/HEV/SOCKS/protect, p50/p95/p99 9.803/21.133/33.066 ms, HEV tx/rx 220/221, SOCKS 2 asociaciones y 220/220 datagramas, protect 2/2/0, recursión 0, HEV issue #323 malformed PASS. Esa versión reveló una carrera de teardown `SocketException: Socket closed` y por eso no es candidata final.
- DEV335 es la candidata endurecida actual: versionCode 335, versionName `1.0.1-dev`, APK SHA-256 `bc466c8b345832390596e7c0ccf0e6f5ef5fbdabc0989680148b28a2b07f80d5`, 158794709 bytes. Gates automáticos completos PASS: feature-vpn unit/compile/ktlint/lint y app-user unit/compile/lint/assemble; `git diff --check` PASS.
- Lifecycle nativo endurecido en DEV335: join confirmado cierra bridge/FD exactamente una vez y pasa a STOPPED; join timeout conserva bridge/FD, pasa a QUARANTINED y rechaza restart; terminación nativa tardía hace cleanup exactamente una vez; double-stop y restart tras stop limpio cubiertos. `VpnFlowOwnerCache` followers tienen timeout bounded de 750 ms/fail-closed. Shutdown SOCKS bounded, idempotente y observable.
- El único blocker actual es físico: `adb devices -l` no enumera el A23. DEV335 no fue instalada ni obtuvo gate físico final. El último `ceDataInode=1239519`, `resetCount=1`, DeviceOwner/Affiliated, Accessibility y VPN son valores físicos previos, no reconfirmados con DEV335.
- HEV **NO se acepta todavía para full-tunnel**. Falta sobre DEV335: `adb install -r`, UDP 20/20 + 200/200 por TUN real, stress 100 ciclos sin SocketException/joinTimeout normal, resource counters finales=0, canarios TCP/DNS/Chrome/GloshIA y rollback final. Si aparece join timeout durante stress, debe demostrarse cuarentena/fail-close segura sin cerrar FD bajo el thread nativo.
- Próximo paso exacto: cuando el A23 vuelva a enumerarse, reanudar el mismo ticket y ejecutar una única secuencia limpia `precheck → install -r DEV335 → STOP/cleanup → START → UDP 20/200 → stress 100 → TCP/DNS/Chrome/GloshIA → resources=0 → rollback`. No abrir full-tunnel todavía.

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

El UDP real quedó demostrado en DEV333 y los defectos de lifecycle descubiertos quedaron endurecidos en DEV335 con todos los gates automáticos verdes. El bloqueo actual es exclusivamente que el A23 no está disponible por ADB para validar físicamente la candidata final.

Chrome general todavía no está listo como producto. No avanzar a full-tunnel, REGION-DETECTOR, video, DRM, Production, merge ni publicación hasta revisar el cierre físico DEV335.
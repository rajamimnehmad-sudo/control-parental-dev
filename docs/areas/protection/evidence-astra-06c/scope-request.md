# Additional route authorized and applied

DEV443 crashed on A23 at2026-09-08 00:39:53 with InterruptedException from Thread.join in p6.y.e. Exact installed443 DEX p6.y contains GloshSocksTcpReverse09A and the join instruction; current source maps to VpnLocalSocks5Server.handleConnect. The source is unchanged from the functional anchor; this is an existing recovery bug, not a demonstrated regression introduced by the photo changes. Worker catch handles IOException but not InterruptedException. The UDP cleanup has the same unhandled join and can skip activeUdpAssociations decrement.

Proposed bounded correction: catch only InterruptedException at both cleanup joins, restore the interrupt flag, and complete existing resource/counter cleanup. Preserve all destination authority, socket protect, concurrency, timeouts, credentials and release rules. The user explicitly authorized both routes in this chat. Central a37963046bb213658eac404981d880054e9f9416 records the extension. The patch is applied in DEV448 source923b5a8f; interrupted TCP and UDP cleanup tests pass.

Required extra paths:
- feature-vpn/src/main/java/com/contentfilter/feature/vpn/transport/VpnLocalSocks5Server.kt
- feature-vpn/src/test/kotlin/com/contentfilter/feature/vpn/transport/VpnLocalSocks5ServerTest.kt

Validation after authorization: deterministic interrupted TCP/UDP teardown regression tests, existing SOCKS suite, exact APK build/install, physical lifecycle/recovery and security gates. No threshold, model or release authority modification.

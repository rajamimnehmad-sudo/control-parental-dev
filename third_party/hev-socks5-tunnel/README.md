# HEV Socks5 Tunnel — pinned feasibility component

`CHROME-VPN-TRANSPORT-ENGINE-FEASIBILITY-09A` uses the upstream source only at:

- project: `heiher/hev-socks5-tunnel`
- release: `2.17.1`
- commit: `9a06bc6e7989da54e3d32ff701ef7a7ce4995d3a`

The source is not downloaded by Gradle and no moving branch is used. Run
`build_android.sh <ndk-root> <verified-source-root>` to reproduce the four ABI
libraries. The script rejects any source or submodule commit mismatch before it
builds or copies output.

The generated `.so` files are reviewable feasibility artifacts. They are built
from the pinned source with Android NDK r27d (27.3.13750724), API 29, and HEV's
16 KiB linker alignment flags. `bridge/hev_glosh_jni.c` is Glosh-owned glue that
exposes only the external-FD `main_from_str`, `quit`, and stats APIs.

Licenses and component pins are recorded in `THIRD_PARTY_NOTICES.md`.

# Third-party notes (lab spike)

This directory is an isolated development spike and is not a production distribution.

- `MuntashirAkon/libadb-android` 3.1.1 — dual GPL-3.0-or-later / Apache-2.0; this spike elects Apache-2.0 where applicable. The upstream project notes an LGPL transitive dependency and that the library has not undergone a security audit.
- `MuntashirAkon/sun-security-android` 1.1 — used only to generate the temporary ADB X.509 identity following libadb's documented example.
- Conscrypt 2.5.3 — TLS provider required by the libadb Android path.
- OkHttp 4.12.0 — WSS client.
- Python `websockets` and `cryptography` — Mac lab relay dependencies.
- Cloudflare Quick Tunnel — external development transport only; no Cloudflare SDK is bundled in the APK.

Before any productionization, dependency licenses, current versions, SBOM, vulnerability status, and security review must be re-evaluated.

# CHROME-PHOTOS-DATA-PLANE-00 — evidencia local y bloqueo fisico

Fecha: 2026-08-21
Owner: Proteccion Android / Codex
Base: `2b01280ffb42dd80850fffa8d3bae8632b7a2fe9`
Rama/worktree: `work/chrome-photos-data-plane-00`
Estado: **BLOCKED antes de instalar**

## Resultado implementado

- Proxy HTTPS loopback exclusivo de Chrome aplicado mediante politica administrada DEV.
- CA y hoja generadas en memoria; solamente el certificado publico de CA se conserva de
  forma temporal para rollback. No hay claves privadas empaquetadas ni persistidas.
- Fixture HTTPS local en memoria con SAFE-A, SENTINEL-BLOCK, repeticion, lazy, scroll y
  navegacion. Imagen segura byte-identica; centinela reemplazado; desconocida fail-closed.
- Ruta VPN exacta de la fixture para observar y descartar bypass TCP/UDP 443.
- Lease de transparencia DEV como capacidad efimera en memoria ligada a sesion, paquete
  Chrome, ventana, viewport y epoch. Requiere proxy, VPN, politica y heartbeat visible de
  fixture saludables. El host unico permanece adjunto y `NOT_TOUCHABLE`.
- La cobertura opaca se confirma por commit de `SurfaceControl` antes de otorgar una
  lease. Watchdog, vencimiento, error, salida de Chrome, cambio de contexto/epoch o
  atestacion inconsistente restauran opacidad. No se presenta ninguna captura cruda.

## Gates locales

- `feature-accessibility:testDebugUnitTest`: 126 tests, 0 failures, 0 errors.
- `feature-vpn:testDebugUnitTest`: 89 tests, 0 failures, 0 errors.
- `app-user:testDevDebugUnitTest`: 55 tests, 0 failures, 0 errors.
- Ktlint: `core-domain` main, `feature-accessibility`, `feature-vpn`, `app-user` DEV,
  testDev y Kotlin script: PASS.
- `feature-accessibility:lintDebug`: PASS.
- `feature-vpn:lintDebug`: PASS.
- `app-user:lintDevDebug`: PASS.
- `app-user:assembleDevDebug`: PASS. El primer intento no genero APK por un manifiesto
  OSGi duplicado de Bouncy Castle; se excluyo solamente ese metadato y el build paso.

## APK candidata local

- Variante: App Usuario DEV.
- versionCode: 319.
- versionName: `1.0.1-dev`.
- Paquete: `com.contentfilter.user.dev`.
- SHA-256: `ba612fe2f23c5633e7041bf6c233d1ed435db3bcc7f43e6d47dfb03d7b7cf14b`.
- No publicada y no instalada.

## Precheck fisico A23

- Serial ADB: `R58T34V31AE`.
- Modelo: Samsung SM-A235M.
- Android: 14 / API 34.
- Chrome: 151.0.7922.137.
- Accessibility Glosh: enabled y bound.
- App instalada antes del gate: DEV 318.
- `adb shell dpm list-owners`: `no owners`.
- `dumpsys device_policy`: Glosh aparece como Device Admin, no como Device Owner.

El ticket exige Device Owner confirmado antes de instalar CA, aplicar politica o conceder
transparencia. Reprovisionar o hacer factory reset esta fuera de la autorizacion. Por eso
no se instalo DEV 319, no se inicio el laboratorio y no existe replay fisico valido.

## Siguiente paso minimo

Restaurar/confirmar Device Owner mediante un procedimiento separado y explicitamente
autorizado que preserve la trazabilidad del A23. Luego instalar esta misma APK por hash y
ejecutar una unica sesion fisica completa; no recompilar salvo que cambie el codigo.

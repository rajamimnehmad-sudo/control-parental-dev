# DAG-VIDEO-STRUCTURE-01 — evidencia

Fecha: 2026-08-16. Candidato: DEV 222 / 0.70.24. Extension: 2.0.56.

## Resultado

- Protocolo e identidad exacta extraidos a `video-protection-protocol.js`.
- Coordinador reducido de 812 a 799 lineas sin cambio funcional.
- APK: `DagBrowser-dev-debug.apk`, 116 MiB.
- SHA-256: `79ba159f7fdec983ad5dc2cf821285bb7f8501cfbc862c569e936b122f67f7d7`.

## Prevalidacion

- JS: 89/89.
- Unitarios: DEV 211/211; Diagnostic 211/211.
- ktlint, lint DEV, lint Diagnostic y assemble DEV: PASS.
- APK inspeccionada: paquete `com.contentfilter.dagbrowser.dev`, versionCode 222,
  versionName `0.70.24-dev`, minSdk 29, targetSdk 36.

## Fisico A23

- Instalacion `-r`, sin borrar datos.
- YouTube Big Buck Bunny: dos capturas separadas mostraron cuadros distintos.
- Audio Android: AAudio `started`, uso media, estereo, 48 kHz, sin mute.
- Sin crash ni ANR posteriores a la instalacion; cierres registrados fueron
  actualizacion del paquete o procesos Gecko terminados normalmente.
- App detenida al finalizar.

## Limite

Esta evidencia valida YouTube normal y la division neutral. No convierte en GO
URLs MP4 directas, iframes, Shorts, anuncios, Instagram ni TikTok.

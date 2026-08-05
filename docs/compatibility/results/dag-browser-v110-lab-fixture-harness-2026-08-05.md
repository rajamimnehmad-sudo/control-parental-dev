# Harness de fixture local DAG - preparación

Fecha: 2026-08-05  
APK aislado: `com.contentfilter.dagbrowser.lab`  
VersionCode: `110`  
VersionName: `0.69.13-lab`  
APK SHA-256: `519311f27509245a9a5bc0af28c537f6671fe7e47a259d8f62780d1de3946535`  
Modelo incluido: GloshIA Visual R3.1  
Modelo SHA-256: `c8b64af8092d3718c58736a511c996d0d443dacf3eaa74620b1e5af439a3cd48`  
Extensión: `1.51.0`

## Propósito

El fixture HTTPS original usa un certificado autofirmado que GeckoView no
acepta en el runtime productivo. El flavor `lab` es un APK separado que:

- mantiene el mismo pipeline, modelo, extensión y contrato visual;
- permite únicamente `http://localhost/fixture/` en `DagNavigationPolicy`;
- permite conexiones inseguras solo dentro del runtime del flavor `lab`;
- sirve la página por HTTP local mediante `adb reverse`;
- no se publica, no reemplaza `com.contentfilter.dagbrowser.dev` y no cambia
  la política del APK DEV.

La comprobación de contrato confirma que el flavor DEV normal continúa
bloqueando `http://localhost:8765/fixture/`.

## Validación realizada

- `testDevDebugUnitTest`: OK.
- `testLabDebugUnitTest`: OK.
- `testDagProtectionJs`: 16/16 OK.
- `ktlintCheck`: OK.
- `lintDevDebug`: OK.
- `lintLabDebug`: OK.
- `assembleLabDebug`: OK.
- Servidor HTTP local: respuesta `/healthz` y HTML del fixture verificados.

## Estado físico

El S22 estuvo conectado inicialmente en `192.168.1.91:36945`, pero dejó de
anunciar ADB antes de instalar el APK aislado. No se instaló el flavor `lab` y
no existen métricas físicas válidas de este harness todavía. La corrida HTTPS
anterior queda descartada porque registró `fixture_reached=false` y
`SSLV3_ALERT_BAD_CERTIFICATE`.

El siguiente paso es conectar el S22 con un nuevo puerto ADB anunciado y
ejecutar dos corridas del flavor `lab`: fría y caliente. El APK DEV oficial
queda intacto.

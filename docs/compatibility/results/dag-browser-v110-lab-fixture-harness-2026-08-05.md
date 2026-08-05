# Harness de fixture local DAG - preparación

Fecha: 2026-08-05  
APK aislado: `com.contentfilter.dagbrowser.lab`  
VersionCode: `110`  
VersionName: `0.69.13-lab`  
APK SHA-256 inicial: `519311f27509245a9a5bc0af28c537f6671fe7e47a259d8f62780d1de3946535`
APK SHA-256 con loopback: `9da1965a3c08245f68d798dbe53f008b78419b53477ba6854ca24985953e5508`
APK SHA-256 con guard de laboratorio: `97938e98384599a5670669f92e95e136e3c9b4d65e710df2eddce5f1871214d1`
Modelo incluido: GloshIA Visual R3.1  
Modelo SHA-256: `c8b64af8092d3718c58736a511c996d0d443dacf3eaa74620b1e5af439a3cd48`  
Extensión: `1.51.0`

## Propósito

El fixture HTTPS original usa un certificado autofirmado que GeckoView no
acepta en el runtime productivo. El flavor `lab` es un APK separado que:

- mantiene el mismo pipeline, modelo, extensión y contrato visual;
- permite únicamente el loopback HTTP del fixture (`localhost` o
  `127.0.0.1`) en `DagNavigationPolicy`;
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
- `testDevDebugUnitTest`: OK después del cambio de loopback.
- `testLabDebugUnitTest`: OK después del cambio de loopback.
- `connectedLabDebugAndroidTest` en `SM-S908E` / Android 16: OK; smoke test
  directo del modelo R3.1, sin UI ni datos persistidos.

## Estado físico

El S22 se reconectó en `192.168.1.91:33999` y el APK lab actualizado se instaló
correctamente. La corrida controlada fue:

- `20260805T220917Z-192.168.1.91:33999`;
- `fixture_url=http://127.0.0.1:8765/fixture/`;
- el servidor recibió solamente las comprobaciones locales `/healthz`;
- `fixture_reached=false`, sin eventos de la página;
- `0` crash, `0` ANR, 170.369 KiB PSS y 33 cuadros registrados;
- la tarea lab quedó detrás del launcher con ventana no dibujada.

La Activity lab sigue siendo enviada a Home por Android poco después de dibujar
su ventana (`START ... LauncherActivity`); por eso el fixture UI no produjo
`page_visible` ni eventos de servidor. La causa TLS queda descartada para esta
corrida y tampoco se atribuye ese resultado a GloshIA.

## Medición directa del modelo en el S22

Se ejecutó `GloshiaLabDeviceModelSmokeTest` mediante
`connectedLabDebugAndroidTest`. El test usa `DagOnDeviceImageAnalyzer`, el asset
R3.1 exacto, RGB 224x224 y CPU ORT Android 1.27.0, con 12 tensores sintéticos y
10 repeticiones adicionales.

- dispositivo: `SM-S908E`, Android `16`, SDK `36`;
- modelo: `tinyclip-r3-head-hybrid-int8.onnx`;
- SHA-256: `c8b64af8092d3718c58736a511c996d0d443dacf3eaa74620b1e5af439a3cd48`;
- inferencias: `22`;
- fallos de ejecución: `0/22`;
- salidas no finitas: `0/22`;
- probabilidad observada: `0,0442–0,0638`;
- segunda repetición: p50 `30,92 ms`, p95 `33,75 ms`, máximo `41,94 ms`;
- PSS al final de la segunda repetición: `114.405 KiB`;
- `final_sealed`: cerrado.

Esta prueba demuestra apertura, inferencia repetida y estabilidad del modelo
R3.1 en CPU del S22. No es una evaluación de precisión ni reemplaza el
benchmark de páginas reales; el fixture de UI queda pendiente sólo para medir
la cadena completa de carga y presentación.

El APK DEV oficial queda intacto; no se modificaron el modelo oficial, DAG 107,
Supabase, publicación ni Production.

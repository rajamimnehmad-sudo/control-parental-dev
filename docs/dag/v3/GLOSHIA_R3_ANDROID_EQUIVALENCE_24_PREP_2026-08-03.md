# GloshIA R3 - preparación del gate Android

Fecha: 2026-08-03  
Ticket: `GLOSHIA-R3-ANDROID-EQUIVALENCE-24`  
Estado: preparado; pendiente teléfono Android conectado.

## Preparado sin teléfono

- Harness Android aislado actualizado para leer modelo, hash y umbral desde
  `harness-config.json`, sin constantes de una candidata anterior.
- APK de laboratorio compilada con ONNX Runtime Android 1.27.0 CPU.
- 119 tensores congelados generados: 47 validation y 72 frozen test.
- Umbral congelado: `0.381063`.
- Modelo INT8 selectivo: 8.950.584 bytes, SHA-256
  `1f1e03ad089609d03036ae93a789589446bab54302859e4b6e64d662bd3eeeb7`.
- Tensores: 71.651.328 bytes, SHA-256
  `090279b516981a6e8c7fab9f423dc42215d2fa5585f2acb7da0fab7fee29d3d2`.
- `final_sealed` no se abrió.

Los artefactos privados están en
`.codex-tmp/gloshia-r3-candidate-20260803/android-harness-inputs/`.

## Gate pendiente

En A23 o S22 se debe comprobar:

1. hash exacto y apertura de `ConvInteger`;
2. salidas finitas y cierre de sesión;
3. cero diferencias de decisión contra FP32 en 119 muestras;
4. cero falsos permisos;
5. latencia p50/p95, memoria y temperatura comparables con R1.

El APK de laboratorio debe retirarse al terminar. No se integra el modelo en
DAG hasta superar este gate.


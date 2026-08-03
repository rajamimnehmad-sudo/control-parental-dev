# GloshIA R3 - preparación del gate Android

Fecha: 2026-08-03  
Ticket: `GLOSHIA-R3-ANDROID-EQUIVALENCE-24`  
Estado: ejecutado en S22; candidata híbrida pendiente confirmación A23.

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

## Resultado S22

Dispositivo: Samsung SM-S908E, Android 16, API 36.

Se probaron tres exportaciones:

1. INT8 dinámico de 8,95 MB: `NO-GO`; produjo dos diferencias y un falso
   permiso.
2. FP32 de 33,24 MB: decisiones exactas y cero falsos permisos, pero p50
   340,94 ms frente a 215,39 ms de R1; `NO-GO` por latencia y tamaño.
3. Híbrido Conv-FP32/MatMul-INT8 de 10.469.698 bytes, umbral móvil 0,40:
   cero falsos permisos, 10 falsos filtros frente a 42 de R1, p50 186,25 ms
   frente a 188,18 ms y PSS pico 106.031 KB frente a 105.615 KB.

El híbrido conserva todas las decisiones de seguridad, reduce falsos filtros
76,2 % y es ligeramente más rápido. Tiene una diferencia frente a FP32 en
dirección conservadora (`allow` convertido en `filter`). Por el gate estricto
de equivalencia el resultado automático es `NO-GO`; por calidad de producto
queda `CONDITIONAL-GO` para repetir en A23 y luego realizar canary reversible.

El APK de laboratorio fue desinstalado y las cuatro carpetas temporales fueron
retiradas del S22. DAG y R1 no se modificaron.

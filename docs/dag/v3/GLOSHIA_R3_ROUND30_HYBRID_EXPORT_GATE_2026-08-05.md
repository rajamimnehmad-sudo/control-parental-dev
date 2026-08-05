# GLOSHIA-R3-ROUND30-HYBRID-EXPORT-GATE — resultado local

Fecha: 2026-08-05

## Resultado

La candidata híbrida INT8 pasa el gate de calidad local frente al R3 oficial
actual y queda **GO condicionado exclusivamente al gate Android**. No se
integró en DAG 107.

Artefacto privado:

- archivo: `r3-round30-pilot-03-hybrid-int8.onnx`
- SHA-256: `c8b64af8092d3718c58736a511c996d0d443dacf3eaa74620b1e5af439a3cd48`
- tamaño: `9.668.603 bytes`
- contrato: RGB 224×224, letterbox DAG, una salida binaria, Conv FP32 y
  MatMul INT8 dinámico por canal
- ORT CPU local: abre, ejecuta todas las muestras y produce salidas finitas

## Comparación con R3 oficial

Examen exactamente igual, umbral `0,4`, sin abrir `final_sealed`:

| examen | modelo | AA/AF/FA/FF | balanced accuracy | PR-AUC | falsos permisos | falsos filtros |
|---|---|---:|---:|---:|---:|---:|
| validation, 28 | R3 oficial | 18/3/1/6 | 0,857143 | 0,761429 | 1/7 (14,29 %) | 3/21 (14,29 %) |
| validation, 28 | híbrida INT8 | 19/2/1/6 | 0,880953 | 0,801271 | 1/7 (14,29 %) | 2/21 (9,52 %) |
| frozen_test, 29 | R3 oficial | 13/2/1/13 | 0,897619 | 0,937374 | 1/14 (7,14 %) | 2/15 (13,33 %) |
| frozen_test, 29 | híbrida INT8 | 15/0/1/13 | 0,964286 | 0,963054 | 1/14 (7,14 %) | 0/15 (0 %) |

La candidata no agrega falsos permisos en ningún examen y reduce los falsos
filtros. Cambia 2 de 57 decisiones frente al FP32, pero ambas diferencias
quedan en dirección no peligrosa según las etiquetas humanas: una elimina un
falso filtro en frozen_test y otra agrega un falso filtro en validation sin
crear un falso permiso.

## Rendimiento de laboratorio

En Mac CPU, no Android:

- R3 oficial: p50 `12,997 ms`, p95 `13,176 ms`.
- híbrida INT8: p50 `11,554 ms`, p95 `17,430 ms`.

La variación de p95 debe comprobarse en S22/A23 antes de cualquier integración.
No se midieron memoria ni temperatura Android en este paso porque no hay un
dispositivo ADB conectado.

## Gate pendiente

La candidata queda lista para un harness Android aislado con
`onnxruntime-android 1.27.0` CPU. Ese harness debe comprobar apertura real,
inferencia repetida, equivalencia de decisiones frente a FP32, falsos permisos,
salidas finitas, p50/p95, memoria y estabilidad en S22 y A23.

Hasta completar ese gate:

- R3 sigue siendo el único modelo oficial de DAG 107.
- No se modificó ONNX productivo, Android, APK, umbral o política.
- No se abrió `final_sealed`.
- No hubo publicación, push, Supabase ni Production.

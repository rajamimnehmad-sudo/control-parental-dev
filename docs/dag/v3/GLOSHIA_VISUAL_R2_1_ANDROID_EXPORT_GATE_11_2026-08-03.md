# GloshIA Visual R2.1 — Android export gate

Fecha: 2026-08-03  
Ticket: `GLOSHIA-VISUAL-R2.1-ANDROID-EXPORT-GATE-11`  
Resultado: `NO-GO`; R1 no fue modificado.

## Alcance

El FP32 congelado de TRAIN-10 se trató como referencia inmutable:

- SHA-256: `f601640941008ae1d2e6749ff84afe3aa1c0a584d1136a96642326bc2f73b4c4`.
- Entrada: `pixel_values`, `float32`, `[1, 3, 224, 224]`.
- Salida: `filter_probability`, `float32`, `[1, 1]`.
- Preprocesamiento: contrato `dag-letterbox` y normalización CLIP.
- Umbral: `0,4`.

No se reentrenó, no se cambiaron pesos, etiquetas, dataset, splits, política o
R1. La calibración estática utilizó sólo las 252 imágenes de `train`; no se
usó `validation`, `frozen_test` ni `final_sealed` para calibrar. El examen
sellado permaneció cerrado.

## Diagnóstico de `ConvInteger`

La exportación dinámica de TRAIN-10 usó `onnxruntime.quantization.quantize_dynamic`
con `QInt8` por canal. ORT cuantizó la convolución de patch embedding y produjo:

```text
ConvInteger(10) /vision_model/embeddings/patch_embedding/Conv_quant
```

El checker de ONNX valida la estructura, pero el runtime Python local no tiene
una implementación CPU para ese nodo. Tanto R1 como el INT8 dinámico R2.1
fallan al crear la sesión en el mismo ORT Python `1.19.2` con:

```text
NOT_IMPLEMENTED: Could not find an implementation for ConvInteger(10)
```

Esto es una limitación demostrada del runtime Python local, no una afirmación
de que R1 haya dejado de funcionar en Android. R1 ya es el modelo oficial
ejecutado por DAG.

## Versiones y evidencia de runtimes

| Runtime | Evidencia |
| --- | --- |
| ORT Python local | `1.19.2`; providers `CoreMLExecutionProvider`, `AzureExecutionProvider`, `CPUExecutionProvider` |
| ORT Android de DAG | Gradle: `com.microsoft.onnxruntime:onnxruntime-android:1.27.0` |
| AAR inspeccionado | `onnxruntime-android-1.27.0.aar`, arm64-v8a, 44.532.227 bytes, SHA-256 `077dec5e2d821234c7dc0aba584bec8f999854b546c754cab93a90741c56fbeb` |
| ORT Web CPU auxiliar | `onnxruntime-web 1.27.0`; abrió e infirió los formatos en un smoke test WASM local |

El binario arm64 del AAR contiene kernels/nombres para `ConvInteger`,
`QLinearConv`, `QLinearMatMul`, `QuantizeLinear`, `DequantizeLinear`,
`LayerNormalization` y las fusiones `BiasGelu`, `FusedGemm`, `FusedMatMul` y
`SkipLayerNormalization`. Esto confirma que el paquete contiene esas familias
de operadores, pero no sustituye una sesión real en Android. No se construyó
APK ni se instaló nada, por lo que la ejecución directa con el AAR Android
queda explícitamente sin probar en este ticket.

## Candidatos exportados

Todos fueron generados desde el mismo FP32, pasaron `onnx.checker` y usaron el
mismo contrato de entrada/salida. Las métricas son sobre los mismos 47
validation y 72 frozen_test de TRAIN-10. Los falsos permisos son `filter→allow`.

| Formato | Bytes | SHA-256 | ORT Python CPU | Finite | Equivalencia de decisiones vs FP32 | Resultado |
| --- | ---: | --- | --- | --- | --- | --- |
| QDQ INT8 estático | 9.036.644 | `6bf3ba08b5ae21b3bb9845d242dd57f035e2fd77b6369a3f876a83fbb749b89e` | abrió e infirió | sí, 119/119 | 37/47; 61/72 | NO-GO |
| QLinearOps INT8 estático | 8.792.588 | `d9cf627a5c8be0fbc7ef77baf22d5bd000624b0ba3404feb43a55e5efda7a203` | abrió e infirió | sí, 119/119 | 37/47; 62/72 | NO-GO |
| FP16 | 16.727.380 | `6caf197e463e378fa50a018a731d2631134aa8b44eef5eca1e26a64f3b497948` | abrió, pero falló el contenido | no, 119/119 con no finitos | no disponible | NO-GO |
| FP32 optimizado | 33.200.637 | `44751cd05ddafa41241df0991c57b4cae0a84fe321c0e131823a80b631a1d127` | abrió e infirió | sí, 119/119 | 47/47; 72/72 | no aprobado |

El FP32 optimizado conserva exactamente las decisiones y probabilidades del
FP32 congelado en este examen, pero mide 33.200.637 bytes: aumenta 24.465.451
bytes frente a R1 y contiene operadores fusionados `com.microsoft`. Se conserva
como control de investigación, no como reemplazo Android, porque no se ejecutó
en el AAR dentro de Android y su tamaño es aproximadamente 3,8 veces R1.

## Métricas de los formatos INT8

| Modelo / examen | Matriz allow→allow / allow→filter / filter→allow / filter→filter | Balanced accuracy | PR-AUC | Falsos permisos | Falsos filtros |
| --- | --- | ---: | ---: | --- | --- |
| R2.1 FP32 / validation (47) | 34 / 5 / 0 / 8 | 93,59% | 80,73% | 0/8 = 0% | 5/39 = 12,82% |
| QDQ / validation (47) | 38 / 1 / 6 / 2 | 61,22% | 65,58% | 6/8 = 75% | 1/39 = 2,56% |
| QLinearOps / validation (47) | 39 / 0 / 5 / 3 | 68,75% | 70,42% | 5/8 = 62,5% | 0/39 = 0% |
| R2.1 FP32 / frozen_test (72) | 55 / 7 / 0 / 10 | 94,35% | 89,24% | 0/10 = 0% | 7/62 = 11,29% |
| QDQ / frozen_test (72) | 60 / 2 / 6 / 4 | 68,39% | 53,87% | 6/10 = 60% | 2/62 = 3,23% |
| QLinearOps / frozen_test (72) | 60 / 2 / 5 / 5 | 73,39% | 68,94% | 5/10 = 50% | 2/62 = 3,23% |

Ambos INT8 reducen falsos filtros a costa de introducir falsos permisos
críticos y degradar claramente balanced accuracy y PR-AUC. No se ajustó el
umbral para ocultar esa regresión.

## FP16, latencia y tamaños

FP16 abrió la sesión en ORT Python, pero produjo al menos un valor no finito en
las 119 imágenes examinadas; por eso no se calcularon métricas binarias para
ese formato. El smoke test WASM 1.27.0 sobre una imagen abrió e infirió todos
los formatos, pero no reemplaza la prueba CPU Android.

Benchmark de laboratorio Mac CPU, 30 ejecuciones sobre la misma entrada; no es
latencia Android:

| Formato | p50 | p95 | máximo |
| --- | ---: | ---: | ---: |
| R2.1 FP32 referencia | 14,850 ms | 21,048 ms | 32,557 ms |
| QDQ INT8 | 17,320 ms | 26,423 ms | 39,423 ms |
| QLinearOps INT8 | 17,497 ms | 36,032 ms | 164,228 ms |
| FP16 | 20,369 ms | 21,511 ms | 34,479 ms |
| FP32 optimizado | 14,500 ms | 15,068 ms | 18,462 ms |

El pipeline real de DAG no fue ejecutado ni modificado.

## Decisión

`NO-GO` para reemplazar R1 o solicitar un canary Android.

Ningún formato pasa todos los gates:

- QDQ y QLinearOps abren, pero agregan falsos permisos y degradan métricas.
- FP16 produce no finitos.
- FP32 optimizado conserva métricas, pero no tiene ejecución Android directa
  verificada en este ticket y aumenta aproximadamente 24,5 MB frente a R1.
- La compatibilidad observada en ORT Web 1.27.0 y la presencia de kernels en el
  AAR no se convierten en una afirmación de compatibilidad Android completa.

R1 permanece oficial, con SHA-256
`2d52bd9e5eb4cd448cb0d64a784b2ee6f761ad20e890c57b898fd7991d29a9ee`.
`final_sealed` sigue cerrado. No se modificaron DAG, Android, APK, Supabase,
dataset, pesos ni versionCode.

## Reproducción y artefactos

Herramienta reproducible:
`scripts/dag_v3_model/r2_android_export_gate.py`.

Informe crudo:
`.codex-tmp/gloshia-r2-hard-negative-repair-20260802-v3/r2.1-android-export-gate.json`.

Modelos privados fuera de Git:
`.codex-tmp/gloshia-r2-hard-negative-repair-20260802-v3/export-gate-11/`.

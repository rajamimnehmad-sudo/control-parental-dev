# GloshIA R2.2 — equivalencia de exportación Android

Ticket: `GLOSHIA-R2.2-EXPORT-EQUIVALENCE-17`

Fecha: 2026-08-03

Resultado: **GO de exportación y compatibilidad; canary productivo todavía no ejecutado**

R1 continúa como modelo oficial. No se modificaron DAG 95, Android productivo,
la política visual, el umbral, Supabase ni Production.

## Objetivo

Eliminar el único desacuerdo de decisión entre R2.2 FP32 y su primera
exportación INT8 sin reentrenar, cambiar el umbral 0,40 ni aceptar una
tolerancia retroactiva.

## Diagnóstico y solución

La primera cuantización dinámica acumulaba suficiente error numérico para que
una muestra `allow` cruzara el umbral en Android. Se probaron variantes privadas
por operación y por bloque, primero localmente y luego con una sonda Android de
29 muestras FP32 entre 0,25 y 0,55.

La variante final mantiene en FP32 una sola operación sensible:

`/vision_model/encoder/layers.0/self_attn/k_proj/MatMul`

El resto conserva la cuantización dinámica QInt8 por canal usada por el
candidato. No hay reglas por imagen, ID, sitio, sexo, deporte ni categoría.

Artefacto congelado:

- archivo: `r2.2-candidate-b-selective-k-int8.onnx`;
- SHA-256: `7e8826f72df12ca76f21b929c3c798c967ea381b558116fd45b27bb71d461bdb`;
- tamaño: 8.950.584 bytes;
- aumento frente a R1: 215.398 bytes, aproximadamente 2,47 %;
- entrada, salida, preprocesamiento y umbral: sin cambios.

La herramienta `scripts/dag_v3_model/r22_selective_export.py` reprodujo el
mismo archivo byte por byte y el mismo SHA-256 desde el FP32 congelado.

## Gate completo en Android

Se ejecutaron los mismos 119 tensores congelados con ONNX Runtime Android
1.27.0 CPU. La matriz humana fue idéntica en ambos teléfonos:

- 85 `allow→allow`;
- 16 `allow→filter`;
- 0 `filter→allow`;
- 18 `filter→filter`;
- 0 desacuerdos de decisión frente a R2.2 FP32;
- 119/119 salidas finitas y sesiones cerradas.

| Dispositivo | Android | p50 | p95 | Pico PSS | R1 p50 | R1 p95 |
|---|---:|---:|---:|---:|---:|---:|
| Samsung A23 SM-A235M | 14 / API 34 | 323,62 ms | 327,98 ms | 94.156 KiB | 323,97 ms | 328,49 ms |
| Samsung S22 Ultra SM-S908E | 16 / API 36 | 35,65 ms | 38,88 ms | 135.884 KiB | 34,79 ms | 36,96 ms |

En A23 la candidata fue levemente más rápida que R1 dentro del ruido de la
sesión. En S22 fue aproximadamente 2,4 % más lenta en p50 y 5,2 % en p95, sin
cambiar decisiones ni producir salidas no finitas. El comportamiento se
considera comparable para habilitar un canary reversible, no una integración
directa.

## Cierre

- Gate de equivalencia estricta: `GO`.
- Compatibilidad A23/S22: `GO`.
- Reemplazo de R1 en DAG: no realizado.
- `final_sealed`: no reabierto; el examen anterior permanece consumido.
- Harness y artefactos temporales: retirados de ambos teléfonos.
- Push, publicación y Supabase: no realizados.

El siguiente paso permitido es un canary Android reversible y aislado que
compare R1/R2.2 sobre imágenes reales del pipeline, sin cambiar todavía el
modelo oficial ni publicar un APK.

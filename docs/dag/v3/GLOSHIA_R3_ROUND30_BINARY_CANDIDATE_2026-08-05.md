# GLOSHIA-R3-ROUND30-BINARY-CANDIDATE — cierre técnico

Fecha: 2026-08-05
Resultado: **NO-GO para integración**. R3 continúa oficial en DAG 107.

## Alcance y protección

Se entrenó un único candidato local desde el checkpoint reproducible de R3,
con TinyCLIP, RGB 224×224, letterbox gris, normalización vigente y una salida
binaria `filter`. No se modificaron los pesos de R3 oficial, DAG 107, Android,
APK, Supabase, Production, umbral ni política. `final_sealed` permaneció
cerrado.

Las 251 decisiones binarias de round30 se usaron sólo para entrenamiento
privado autorizado. Los 4 `doubt` se excluyeron y no se declararon derechos de
entrenamiento claros. Round30 no se volvió a contar como examen independiente.

## Split y contaminación

Se preservaron sin cambios los validation/frozen_test históricos y round30 se
agregó únicamente a train:

| split | muestras | allow | filter |
|---|---:|---:|---:|
| train | 618 | 404 | 214 |
| validation | 28 | 21 | 7 |
| frozen_test | 29 | 15 | 14 |

Seed: `3005`. La prueba pasó para `sample_id`, SHA-256, pHash, grupo y URL,
incluyendo pares perceptuales con distancia Hamming ≤4 entre splits. No se
usaron `doubt`, `excluded`, duplicados ni `final_sealed`.

## Selección

Se ejecutaron tres corridas cortas en CPU local. La selección miró sólo
validation y priorizó falsos permisos, falsos filtros, balanced accuracy y
PR-AUC. Se seleccionó `r3-round30-binary-pilot-03` (seed `3007`, 3 épocas,
learning-rate multiplier `0,75`, class-weight multiplier `0,9`, weight decay
`0,01`).

## Mismo examen: R3 oficial frente al candidato FP32

El baseline es exactamente el ONNX integrado en DAG 107:
`tinyclip-r3-head-hybrid-int8.onnx`, SHA-256
`0aaa1700182623173c41d233bd0e072cce2b2880aca14430d9f9af43fa2c44a8`.
Umbral: `0,4`. La matriz se expresa como AA/AF/FA/FF: allow correcto, falso
filtro, falso permiso y filter correcto.

| examen | modelo | AA/AF/FA/FF | balanced accuracy | PR-AUC | falsos permisos | falsos filtros |
|---|---|---:|---:|---:|---:|---:|
| validation, 28 | R3 oficial | 18/3/1/6 | 0,857143 | 0,761429 | 1/7 (14,29 %) | 3/21 (14,29 %) |
| validation, 28 | candidato FP32 | 20/1/1/6 | 0,904762 | 0,792118 | 1/7 (14,29 %) | 1/21 (4,76 %) |
| frozen_test, 29 | R3 oficial | 13/2/1/13 | 0,897619 | 0,937374 | 1/14 (7,14 %) | 2/15 (13,33 %) |
| frozen_test, 29 | candidato FP32 | 14/1/1/13 | 0,930952 | 0,963054 | 1/14 (7,14 %) | 1/15 (6,67 %) |

La mejora se observa también por categorías: en validation el candidato
eliminó el falso filtro del estrato `partial_or_small_subject` (0/3) y uno de
`sports_and_sensitive` (0/5); en frozen_test redujo `commercial_people` de
2/7 a 1/7 falsos filtros. Las categorías pequeñas se informan siempre con
numerador y denominador; no se extrapolan porcentajes de estratos pequeños.

Errores críticos conservados: validation mantuvo el mismo falso permiso
`gloshia-r3-train28:openimages:validation:53339b6d2e4e4547`; frozen_test mantuvo
`gloshia-r3-train28:openimages:validation:15237209137736ba`. No apareció un
falso permiso nuevo en el examen congelado.

## Exportación y rendimiento de laboratorio

| formato | bytes | SHA-256 | ORT CPU | decisión |
|---|---:|---|---|---|
| candidato FP32 | 33.220.815 | `e4a140eead41162c7664a6e309dd61718d0f50203c925aa6c4211a0a4a6c890c` | abre, inferencia completa | NO-GO por tamaño |
| INT8 dinámico | 8.735.126 | `d84723431a47aedc83207fdd1c5988f3553be57c5c8ac65efb4ad20805c10cdd` | falla `ConvInteger` | NO-GO |
| híbrido MatMul-INT8 | 9.668.603 | `c8b64af8092d3718c58736a511c996d0d443dacf3eaa74620b1e5af439a3cd48` | abre, salida finita | NO-GO: 2/57 decisiones distintas |

El FP32 tuvo p50/p95 de laboratorio Mac CPU de aproximadamente `48,57/62,19
ms` en el examen; R3 oficial `44,00/56,55 ms`. No es una medición Android.
El aumento de tamaño FP32 es `22.751.117` bytes, por encima del límite
razonable. El candidato no se instaló ni se probó en un teléfono porque ya
falló el gate de tamaño/exportación compacta.

## Decisión

**NO-GO.** La señal de entrenamiento es prometedora y reduce falsos filtros
sin aumentar falsos permisos en validation ni frozen_test, pero no existe una
exportación compacta que conserve el contrato Android. R3 queda intacto como
único modelo oficial. No se abre `final_sealed`.

Informe crudo reproducible y artefactos privados:

- `.codex-tmp/gloshia-r3-round30-binary-candidate-20260805/round30-binary-candidate-report.json`
- `.codex-tmp/gloshia-r3-round30-binary-candidate-20260805/split.json`
- checkpoints, ONNX, hashes y predicciones en el mismo directorio.

Próximo paso recomendado: investigar una exportación selectiva compatible o
entrenar con margen de decisión más estable. No ajustar el umbral para rescatar
esta candidata ni crear excepciones por sitio, URL, género o campaña.

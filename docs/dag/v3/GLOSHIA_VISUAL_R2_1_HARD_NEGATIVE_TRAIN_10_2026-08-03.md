# GloshIA Visual R2.1 — hard-negative repair

Fecha: 2026-08-03  
Ticket: `GLOSHIA-VISUAL-R2.1-HARD-NEGATIVE-TRAIN-10`  
Resultado: `NO-GO`; R1 continúa como modelo oficial.

## Alcance y autorización

Se ejecutó una corrida pequeña, reproducible y privada en la Mac M2, sin GPU
paga ni servicios externos. Se conservaron TinyCLIP, RGB 224×224, letterbox
gris, normalización CLIP, una salida binaria `filter_probability` y umbral
`0,4`. No se modificaron DAG, Android, APK, Supabase, Production, umbrales,
política ni el ONNX oficial.

El propietario autorizó explícitamente las 49 decisiones binarias del lote
reciente mediante `owner_authorized_private_experiment`. La autorización no se
registró como `training_rights_clear`: las filas conservan
`training_rights_uncertain`, no pueden redistribuirse y sus pesos derivados son
sólo artefactos privados de investigación. Las 49 muestras pasan a
`train` y dejan de ser evaluación independiente. La única `doubt` quedó fuera.
`final_sealed` permaneció cerrado y no fue leído.

Baseline oficial:

- R1: `tinyclip-bounded-finetune-r1-int8.onnx`.
- SHA-256: `2d52bd9e5eb4cd448cb0d64a784b2ee6f761ad20e890c57b898fd7991d29a9ee`.
- Runtime de producto: ONNX Runtime Android 1.27.0, CPU local.

## Auditoría del lote reciente

Se usaron las 50 revisiones privadas del lote `v3`: 50 `sample_id`, SHA-256,
hash perceptual y clusters únicos; 49 binarias (`26 filter`, `23 allow`) y
una `doubt`. No hubo cruces con el split histórico por ID, SHA, hash
perceptual, grupo ni URL. Todas proceden de Wikimedia Commons; no se
conservaron nombres, usuarios, comentarios ni perfiles. No se detectaron filas
de `final_sealed`.

El resultado R1 sobre esas 49 filas fue sólo diagnóstico previo al
entrenamiento y no se reutiliza como examen independiente:

- 8 falsos permisos de 26 imágenes `filter`.
- 7 falsos filtros de 23 imágenes `allow`.
- matriz `16 / 7 / 8 / 18` en orden allow→allow / allow→filter /
  filter→allow / filter→filter.

Los IDs completos y probabilidades están en el informe privado del lote:
`.codex-tmp/gloshia-r2-hard-negative-repair-20260802-v3/evaluation-report.json`.

## Splits y contaminación

El split reproducible está en
`.codex-tmp/gloshia-r2-hard-negative-repair-20260802-v3/r2.1-splits.json`.
Se preservó la asignación histórica de validation y frozen_test; las 49 nuevas
binarias se agregaron únicamente a train:

| Split | Total | Allow | Filter | Uso |
| --- | ---: | ---: | ---: | --- |
| train | 252 | 201 | 51 | entrenamiento privado |
| validation | 47 | 39 | 8 | selección de configuración |
| frozen_test | 72 | 62 | 10 | examen final, leído una vez |

Train contiene 203 filas históricas y las 49 nuevas autorizadas. Validation y
frozen_test contienen sólo imágenes no utilizadas para entrenar. La prueba
comprobó cero intersecciones por `sample_id`, SHA-256, hash perceptual,
`group_key` y URL. También quedó registrada la semilla `20260803` y la regla
`historical_validation_and_frozen_test_preserved; new_binary_batch_train_only`.

## Corrida y configuración

Se reutilizó la arquitectura pequeña vigente; no se reconstruyó el modelo.
La corrida piloto fue de una época, CPU, semilla `20260803`, batch 8, límite de
900 segundos, con pesos de clase para el desequilibrio observado y sin leer
frozen_test para seleccionar la configuración. El piloto terminó correctamente
con 0/8 falsos permisos, 5/39 falsos filtros, balanced accuracy `0,935898` y
PR-AUC `0,807341` en validation.

Se congeló ese checkpoint como R2.1 Candidate 01. No se reajustó después de
observar frozen_test y no se abrió `final_sealed`.

## Comparación sobre exactamente el mismo examen

Las métricas no incluyen `doubt`; los falsos permisos son `filter→allow` y los
falsos filtros son `allow→filter`. Cada porcentaje conserva numerador y
denominador.

| Modelo / examen | Matriz allow→allow / allow→filter / filter→allow / filter→filter | Accuracy | Balanced accuracy | Filter precision / recall / F1 | Allow precision / recall / F1 | PR-AUC | Falsos permisos | Falsos filtros |
| --- | --- | ---: | ---: | --- | --- | ---: | --- | --- |
| R1 / validation (47) | 20 / 19 / 0 / 8 | 28/47 = 59,57% | 75,64% | 29,63% / 100% / 45,71% | 100% / 51,28% / 67,80% | 68,46% | 0/8 = 0% | 19/39 = 48,72% |
| R2.1 FP32 / validation (47) | 34 / 5 / 0 / 8 | 42/47 = 89,36% | 93,59% | 61,54% / 100% / 76,19% | 100% / 87,18% / 93,15% | 80,73% | 0/8 = 0% | 5/39 = 12,82% |
| R1 / frozen_test (72) | 37 / 25 / 0 / 10 | 47/72 = 65,28% | 79,84% | 28,57% / 100% / 44,44% | 100% / 59,68% / 74,75% | 67,65% | 0/10 = 0% | 25/62 = 40,32% |
| R2.1 FP32 / frozen_test (72) | 55 / 7 / 0 / 10 | 65/72 = 90,28% | 94,35% | 58,82% / 100% / 74,07% | 100% / 88,71% / 94,02% | 89,24% | 0/10 = 0% | 7/62 = 11,29% |

En estos dos exámenes, R2.1 reduce los falsos filtros de 44/101 a 12/101,
una reducción relativa de 72,73%, sin falsos permisos nuevos. Eso no alcanza
para declarar GO porque el artefacto requerido para Android no pasó el gate de
runtime INT8.

## Desglose por categoría

Los conteos son `falsos permisos / denominador filter` y `falsos filtros /
denominador allow`. Se omiten porcentajes sin denominador positivo.

| Examen / categoría | R1 | R2.1 FP32 |
| --- | --- | --- |
| validation / activewear_swim_boundary (9) | 0/1; 6/8 | 0/1; 2/8 |
| validation / modern_event_fashion (4) | —; 4/4 | —; 1/4 |
| validation / partial_crops (1) | —; 1/1 | —; 1/1 |
| validation / public_groups (5) | —; 1/5 | —; 0/5 |
| validation / public_groups_events (10) | —; 4/10 | —; 0/10 |
| validation / runway_redcarpet (5) | 0/1; 0/4 | 0/1; 0/4 |
| validation / sports_activewear (3) | —; 0/3 | —; 0/3 |
| validation / urban_adult_fashion (10) | 0/6; 3/4 | 0/6; 1/4 |
| frozen / current_mixed (26) | —; 13/26 | —; 7/26 |
| frozen / groups_families (8) | 0/3; 4/5 | 0/3; 0/5 |
| frozen / illustration_product (2) | 0/1; 0/1 | 0/1; 0/1 |
| frozen / men_covered (5) | —; 1/5 | —; 0/5 |
| frozen / partial_crops (10) | 0/2; 1/8 | 0/2; 0/8 |
| frozen / public_school_community (9) | —; 4/9 | —; 0/9 |
| frozen / sensitive_control (3) | 0/3; — | 0/3; — |
| frozen / small_subjects (1) | —; 0/1 | —; 0/1 |
| frozen / sports (8) | 0/1; 2/7 | 0/1; 0/7 |

Los resultados por categoría provienen de los JSON privados
`r1-baseline-*.json` y `r2.1-*-evaluation.json`. Las categorías con una muestra
pequeña no se interpretan como evidencia general.

## Exportación, runtime y tamaño

| Artefacto | Bytes | SHA-256 | Resultado |
| --- | ---: | --- | --- |
| R1 INT8 oficial | 8.735.186 | `2d52bd9e5eb4cd448cb0d64a784b2ee6f761ad20e890c57b898fd7991d29a9ee` | intacto |
| R2.1 FP32 candidato | 33.236.705 | `f601640941008ae1d2e6749ff84afe3aa1c0a584d1136a96642326bc2f73b4c4` | ONNX checker OK; no apto para Android |
| R2.1 INT8 candidato | 8.756.367 | `c212d005db271bebfb3fb80aade4c056334e0f4f07f2f1543976050f8c8afa3c` | ONNX checker OK; runtime rechazado |

La versión INT8 aumenta 21.181 bytes respecto de R1, pero no puede evaluarse
como reemplazo hasta resolver su apertura en CPU. ONNX Runtime Python local
`1.19.2` rechaza tanto R2.1 como R1 con `NotImplemented` en
`ConvInteger(10)` (`/vision_model/embeddings/patch_embedding/Conv_quant`). Esto
explica por qué no se pudo completar un benchmark CPU comparable entre ambos
INT8; no se presenta como evidencia de compatibilidad Android 1.27.0.

El benchmark de laboratorio del R2.1 FP32, usando el mismo preprocesamiento y
CPU de la Mac, dio:

- validation: p50 `13,907 ms`, p95 `14,869 ms`, máximo `15,103 ms`;
- frozen_test: p50 `13,682 ms`, p95 `15,110 ms`, máximo `21,375 ms`.

No se inventa una latencia Android ni una medición de memoria. La falta de una
sesión INT8 local válida impide cerrar esos gates; el pipeline de imágenes y
DAG no fueron modificados.

## Gate y decisión

`NO-GO` para integrar R2.1 o solicitar un canary Android.

R2.1 FP32 mejora claramente validation y frozen_test, reduce falsos filtros y
no agrega falsos permisos en esos exámenes. Sin embargo, el candidato INT8
obligatorio no abre con ONNX Runtime CPU local. Bajo el gate del ticket, ese
fallo es suficiente para NO-GO. Se conserva R1 sin cambios, no se reemplaza el
ONNX, no se abre `final_sealed` y no se publica ni sube ningún artefacto.

Artefactos privados: `.codex-tmp/gloshia-r2-hard-negative-repair-20260802-v3/`.
El informe de exportación es `r2.1-export.json`; los reportes de evaluación,
predicciones, split, checkpoint y ONNX no se agregan a Git.

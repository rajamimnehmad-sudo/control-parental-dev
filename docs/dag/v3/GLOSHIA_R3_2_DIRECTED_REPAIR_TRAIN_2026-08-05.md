# GloshIA R3.2 directed repair — TRAIN-10

Fecha: 2026-08-05  
Estado: `NO-GO` privado; R3.1 permanece oficial  
Final sealed: cerrado; no se abrió

## Objetivo y límites

Se entrenó un único candidato local para corregir falsos filtros de R3.1 en
casos comerciales y fronterizos, conservando TinyCLIP, RGB 224×224, letterbox
de DAG, normalización CLIP, una salida binaria y umbral `0,40`. R3.1 no fue
modificado, no se tocó DAG/Android/APK/Supabase/Production y no se publicó ni
se hizo push.

El lote externo de 550 imágenes revisadas se mantuvo fuera del entrenamiento.
Las imágenes autorizadas para el experimento se registraron como
`owner_authorized_private_experiment`; no se declaró `training_rights_clear`.

## Datos y contaminación

Artefacto reproducible privado:
`.codex-tmp/gloshia-r3-2-directed-repair-20260805/split.json`.

- Seed: `3201`.
- Train: `642` (`425 allow`, `217 filter`).
- Validation: `28` (`21 allow`, `7 filter`).
- Frozen test: `29` (`15 allow`, `14 filter`).
- Se excluyeron `doubt`, no revisadas, `excluded`, duplicados y `final_sealed`.
- La selección de hard negatives tomó una representante por cluster de origen,
  campaña, producto, sesión o similitud perceptual; no se contaron variantes
  relacionadas como evidencias independientes.
- Se verificaron `sample_id`, SHA-256, pHash, grupo y URL entre splits y contra
  550 filas de evaluación externa, con distancia perceptual mínima permitida
  de 4.
- Se detectó y excluyó un cluster contaminante completo: pHash `0` y misma URL
  que una muestra externa (`wikimedia:153026725`). Después de excluirlo, la
  prueba de contaminación pasó.
- `final_sealed_opened: false`.

## Entrenamiento

Se reutilizó el pipeline TinyCLIP vigente y el checkpoint de R3.1 de round30,
sin reconstruir la arquitectura. Solo se variaron learning rate, class weight,
épocas y regularización dentro de tres ensayos CPU locales. MPS no estaba
disponible; no se usó GPU paga ni servicio externo.

Los dos primeros ensayos redujeron falsos filtros, pero subieron los falsos
permisos a `2/7` en validation y fueron rechazados. El ensayo 03 fue el mejor
en validation:

| Medida | R3.1 | R3.2 ensayo 03 |
|---|---:|---:|
| Falsos permisos | 1/7 (14,29 %) | 1/7 (14,29 %) |
| Falsos filtros | 2/21 (9,52 %) | 1/21 (4,76 %) |
| Balanced accuracy | 88,10 % | 90,48 % |
| PR-AUC | 0,801271 | 0,804195 |

La configuración quedó congelada antes de leer `frozen_test` del candidato.
Checkpoint privado: `.codex-tmp/gloshia-r3-2-directed-repair-20260805/pilot-03.pt`.

## Comparación en frozen_test

Se evaluó una sola vez el candidato congelado sobre las mismas 29 muestras que
R3.1:

| Medida | R3.1 | R3.2 FP32 |
|---|---:|---:|
| Matriz `allow→allow` | 15 | 15 |
| Matriz `allow→filter` (falsos filtros) | 0 | 0 |
| Matriz `filter→allow` (falsos permisos) | 1 | 1 |
| Matriz `filter→filter` | 13 | 13 |
| Falsos permisos | 1/14 (7,14 %) | 1/14 (7,14 %) |
| Falsos filtros | 0/15 (0 %) | 0/15 (0 %) |
| Accuracy | 96,55 % | 96,55 % |
| Balanced accuracy | 96,43 % | 96,43 % |
| Precisión filter | 100 % | 100 % |
| Recall filter | 92,86 % | 92,86 % |
| Precisión allow | 93,75 % | 93,75 % |
| Recall allow | 100 % | 100 % |
| F1 filter | 96,30 % | 96,30 % |
| F1 allow | 96,77 % | 96,77 % |
| PR-AUC | 0,963054 | 0,963054 |

Las decisiones fueron idénticas en las 29 muestras de `frozen_test`. Por el
gate obligatorio, empatar no es superar: el candidato no demostró una mejora
clara ni reducción de falsos permisos.

## Exportación y runtime

Informe privado completo:
`.codex-tmp/gloshia-r3-2-directed-repair-20260805/r32-pilot03-export.json`.

| Formato | Tamaño | SHA-256 | ONNX checker | ORT CPU local | Resultado |
|---|---:|---|---|---|---|
| R3.1 oficial híbrido INT8 | 9.668.603 B | `c8b64af8092d3718c58736a511c996d0d443dacf3eaa74620b1e5af439a3cd48` | — | — | referencia |
| R3.2 FP32 | 33.220.815 B | `2418236494ba048f9f4bbdc334e8407895fe3fee5296a0e86eb508af3af05494` | pasa | pasa, entrada `[1,3,224,224]`, salida `[1,1]` | no apto por tamaño |
| R3.2 INT8 dinámico | 8.735.126 B | `e82319e24465bb57daaa5fd7860b2c50fbde480d64c9eaa8b1295e21f3607201` | pasa | falla | `NO-GO` |

El INT8 genera `ConvInteger` en
`/vision_model/embeddings/patch_embedding/Conv_quant`. ORT CPU local devuelve
`NOT_IMPLEMENTED: Could not find an implementation for ConvInteger(10)`.
El checker no basta para declarar compatibilidad. No se probó en Android porque
el gate ya falla en el runtime CPU objetivo y el candidato no está aprobado
para APK.

En el FP32 se ejecutaron 57 inferencias de validation + frozen_test; todas las
probabilidades fueron finitas. Latencia de laboratorio Mac CPU: validation
p50 `20,844 ms`, p95 `21,395 ms`; frozen test p50 `24,016 ms`, p95 `32,912
ms`. No es una medición Android. El FP32 agrega aproximadamente 23,55 MB
frente a R3.1, por encima del límite razonable del ticket.

## Decisión

`NO-GO` para R3.2: no supera `frozen_test`, no reduce falsos permisos, el
FP32 excede tamaño y el INT8 no abre en ORT CPU por `ConvInteger`. Se conserva
R3.1 sin cambios como único modelo oficial de DAG. No se abre `final_sealed`.

Próximo paso recomendado: no hacer canary. Si se quiere continuar, preparar un
nuevo ticket de investigación de exportación Android-compatible o un corpus
mejor dirigido, sin reutilizar `frozen_test` ni bajar el umbral para maquillar
el resultado.

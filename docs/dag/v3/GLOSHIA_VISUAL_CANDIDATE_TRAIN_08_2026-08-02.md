# GloshIA Visual R2 Candidate 01 — entrenamiento local

Fecha: 2026-08-02<br>
Ticket: `GLOSHIA-VISUAL-CANDIDATE-TRAIN-08`<br>
Baseline: `main` / `2dce9e9`<br>
Modelo oficial preservado: `tinyclip-bounded-finetune-r1-int8.onnx`<br>
SHA-256 R1: `2d52bd9e5eb4cd448cb0d64a784b2ee6f761ad20e890c57b898fd7991d29a9ee`

## Alcance y límites

Se ejecutó un experimento local y privado con el contrato de R1: TinyCLIP,
RGB 224×224, letterbox gris, normalización del processor CLIP, una salida
binaria `filter_probability` y umbral 0,4. No se modificaron DAG, Android,
Supabase, APK, Production, umbrales ni el ONNX oficial. `final_sealed` no se
abrió.

El corpus usado fue únicamente el banco ya revisado: 322 decisiones binarias
(279 `allow`, 43 `filter`). Las 8 decisiones `doubt`, cualquier excluida,
duplicada, no revisada y toda `final_sealed` quedaron fuera. Todas las filas
conservaron `internal_evaluation_ok` y `training_rights_uncertain`; por eso
este experimento no autoriza publicar imágenes, corpus ni pesos derivados.

## Splits y contaminación

| Split | Muestras | Allow | Filter | Uso |
| --- | ---: | ---: | ---: | --- |
| train | 203 | 178 | 25 | ajuste local |
| validation | 47 | 39 | 8 | selección de época/configuración |
| frozen_test | 72 | 62 | 10 | una sola comparación final |

La asignación se hizo por `source_cluster`/serie; el único cluster que cruzaba
las estratificaciones históricas se movió completo a validation. El gate
comprobó ausencia de intersección de `sample_id`, SHA-256, hash perceptual,
cluster/grupo y URL de origen. No se utilizó `frozen_test` durante el piloto,
los ensayos ni la selección.

## R1 antes de entrenar

La probabilidad y la acción de R1 provienen del runner real ya ejecutado con el
ONNX oficial. Las métricas binarias excluyen `doubt`.

| Examen | Matriz allow→allow / allow→filter / filter→allow / filter→filter | Accuracy | Balanced accuracy | PR-AUC | Falsos permisos | Falsos filtros |
| --- | --- | ---: | ---: | ---: | --- | --- |
| validation (47) | 20 / 19 / 0 / 8 | 28/47 = 0,595745 | 0,756410 | 0,684594 | 0/8 = 0% | 19/39 = 48,7179% |
| frozen_test (72) | 37 / 25 / 0 / 10 | 47/72 = 0,652778 | 0,798387 | 0,676476 | 0/10 = 0% | 25/62 = 40,3226% |
| combinado (322) | 168 / 111 / 2 / 41 | 209/322 = 0,649068 | 0,777819 | 0,677003 | 2/43 = 4,6512% | 111/279 = 39,7849% |

## Piloto, ensayos y configuración congelada

El piloto de una época funcionó en CPU. Luego se ejecutaron exactamente tres
ensayos cortos, variando sólo los parámetros autorizados. La elección usó
validation y priorizó seguridad (falsos permisos), luego falsos filtros,
balanced accuracy y PR-AUC.

| Corrida | Configuración | Falsos permisos validation | Falsos filtros validation | Balanced accuracy | PR-AUC |
| --- | --- | --- | --- | ---: | ---: |
| piloto-01 | 1 época, base | 0/8 | 14/39 | 0,820513 | 0,737834 |
| trial-01 | LR ×0,5; 2 épocas | 0/8 | 14/39 | 0,820513 | 0,746925 |
| trial-02 | peso de clase ×1,25; 2 épocas | 0/8 | 13/39 | 0,833333 | 0,746925 |
| trial-03 | LR ×1,5; regularización 0,002; 3 épocas | 0/8 | 5/39 | 0,935898 | 0,788591 |

Configuración congelada de R2 Candidate 01: seed `20260802`, CPU, batch 8,
última capa del encoder visual + `post_layernorm` + proyección + cabeza binaria
entrenables, `learning_rate_multiplier=1.5`, `class_weight_multiplier=1.0`,
weight decay `0.002`, 3 épocas, letterbox DAG. El checkpoint final no se
reentrenó después de observar `frozen_test`.

## R2 contra el mismo examen

Las métricas R2 siguientes son FP32 exportado; el checkpoint y los pesos están
fuera de Git, en `.codex-tmp/`.

| Examen | Matriz allow→allow / allow→filter / filter→allow / filter→filter | Accuracy | Balanced accuracy | PR-AUC | Falsos permisos | Falsos filtros |
| --- | --- | ---: | ---: | ---: | --- | --- |
| validation (47) | 34 / 5 / 0 / 8 | 42/47 = 0,893617 | 0,935898 | 0,788591 | 0/8 = 0% | 5/39 = 12,8205% |
| frozen_test (72) | 56 / 6 / 1 / 9 | 65/72 = 0,902778 | 0,901613 | 0,777740 | 1/10 = 10% | 6/62 = 9,6774% |

El falso permiso nuevo es `wikimedia:167902476` (`groups_families`, 960×768,
grupo `2025 ToBW Stage 4 Team EF Education Oatly`): R1 lo filtraba con
probabilidad 0,451530; R2 lo permitió con 0,362594. Es una regresión sobre un
caso que R1 resolvía correctamente, por lo que el gate de seguridad falla.

## Exportación, cuantización y rendimiento

| Artefacto | Tamaño | SHA-256 | Resultado |
| --- | ---: | --- | --- |
| R1 INT8 oficial | 8.735.186 bytes | `2d52bd9e5eb4cd448cb0d64a784b2ee6f761ad20e890c57b898fd7991d29a9ee` | intacto |
| R2 FP32 | 33.236.705 bytes | `7ccbad905286bda704d0de88788528b9a9acda4e76d49f1f8968a3c285a472fd` | checker OK, investigación |
| R2 INT8 dinámico por canal | 8.756.367 bytes | `fab347c97a002983f678d184425961c4d81b8b567e01fe1a82d6b6d7166f61f1` | checker OK, sin prueba local de runtime |

R2 INT8 aumenta 21.181 bytes frente a R1 (aprox. 0,02 MiB). El ORT Python
local 1.19.2 no pudo abrirlo por falta de implementación de `ConvInteger`; el
mismo problema aparece al abrir R1 en ese runtime, por lo que esto no demuestra
incompatibilidad Android 1.27, pero sí impide declarar compatibilidad local
completa. No se usó esta limitación para ocultar la regresión de seguridad.

El benchmark FP32 en Mac CPU fue 13,798 ms de media, p50 13,637 ms, p95
14,531 ms y máximo 17,274 ms sobre validation; frozen_test fue 13,798 ms de
media, p50 13,637 ms, p95 14,531 ms y máximo 17,274 ms en la corrida registrada.
Es un benchmark de laboratorio, no Android, y no es directamente comparable
con el pipeline R1 que puede ejecutar vistas regionales. No se midió latencia
INT8 porque el runtime local no pudo abrir ese grafo.

## Decisión

`NO-GO` para `GLOSHIA-VISUAL-R2-ANDROID-CANARY-09`.

R2 sí supera a R1 en filtros incorrectos y balanced accuracy, pero falla el
criterio obligatorio de no aumentar falsos permisos: `1/10` frente a `0/10` en
frozen_test. También falla la verificación local de apertura INT8. Se conserva
R1, no se abre `final_sealed`, no se cambia el ONNX y no se solicita canary.

Artefactos de investigación privados: `.codex-tmp/gloshia-r2-candidate-20260802/`.
Herramientas reproducibles: `r2_candidate_data.py`,
`r2_candidate_evaluate.py`, `r2_candidate_train.py` y
`r2_candidate_export.py`, con el test `test_r2_candidate_data.py`.

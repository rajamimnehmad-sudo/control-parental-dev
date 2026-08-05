# GloshIA R3 hard-negative repair 29 — cierre técnico

Fecha: 2026-08-05  
Estado: `NO-GO` para integración; R3 continúa oficial en DAG 107.

## Alcance y protección

Se usó el modelo oficial R3 como baseline. El ONNX de DAG no fue reemplazado,
no se modificó DAG 107, no se generó APK, no se tocó Supabase/Production y
`final_sealed` permaneció cerrado.

El lote round29 contiene 235 imágenes: 167 `allow`, 67 `filter` y 1 `doubt`.
La duda fue excluida de toda métrica binaria. Las 234 decisiones binarias
quedaron autorizadas solamente como `owner_authorized_private_experiment`;
las 234 conservan `training_rights_uncertain` y no se declararon
`training_rights_clear`.

## Splits

Se preservó el validation/frozen_test histórico de TRAIN-28 y se agregó
round29 únicamente a train:

| split | muestras | filter |
|---|---:|---:|
| train | 367 | 121 |
| validation | 28 | 7 |
| frozen_test | 29 | 14 |

La prueba de contaminación pasó para `sample_id`, SHA-256, pHash, grupo y URL.
No se utilizaron `doubt`, imágenes sin revisión, duplicados ni
`final_sealed`. Los 36 errores de R3 en round29 recibieron peso de hard case
en el experimento; no se usaron etiquetas de género como objetivo.

## Comparación FP32 con umbral 0,40

| examen | modelo | matriz AA/AF/FA/FF | balanced accuracy | PR-AUC | falsos permisos | falsos filtros |
|---|---|---|---:|---:|---:|---:|
| validation | R3 | 19/2/1/6 | 88,10 % | 0,773333 | 1/7 | 2/21 |
| validation | Pilot 01 FP32 | 20/1/1/6 | 90,48 % | 0,792118 | 1/7 | 1/21 |
| frozen_test | R3 | 12/3/1/13 | 86,43 % | 0,943326 | 1/14 | 3/15 |
| frozen_test | Pilot 01 FP32 | 14/1/1/13 | 93,10 % | 0,957952 | 1/14 | 1/15 |

Pilot 01 mejora el examen congelado: mantiene los falsos permisos en `1/14`
y reduce los falsos filtros de `3/15` a `1/15`. En el desglose frozen_test,
commercial_people baja de `2/7` a `1/7` falsos filtros y modern_clothing de
`1/6` a `0/6`; los demás estratos no empeoran en falsos permisos.

## Exportaciones y gate Android

El FP32 abre con ONNX checker y ORT CPU, pero pesa 33.220.815 bytes frente a
10.469.698 bytes de R3: el aumento es material y supera el límite aproximado.

| formato | bytes | SHA-256 | ORT CPU | resultado |
|---|---:|---|---|---|
| FP32 | 33.220.815 | `dbe4b70f99b44eaf298612cc857371a8da3781f2893465a9661e9d66aa2ddc79` | abre | `NO-GO` por tamaño |
| INT8 dinámico | 8.735.126 | `14c438a13af97542f18598df049dbacfbe2ca38f41941c0b09c3d7ba5c6164e2` | falla `ConvInteger` | `NO-GO` |
| híbrido MatMul-INT8 | 9.668.603 | `192970930cae139c244a617df8ab14646856e25c95db75f500203b970c8b20e3` | abre | `NO-GO`: 2 decisiones distintas vs FP32 |
| QDQ | 8.994.931 | `02948333af5eedd04fcbc6eb9313547b6e0f1358fc33cff87db08d374e414e38` | abre | `NO-GO`: 3 falsos permisos en frozen_test |
| QLinearOps | 8.781.985 | `b6ddb04f3801f252ea7506bf63a7a89eac619dc7d5604774ba6e45dc347b49af` | abre | `NO-GO`: 3 falsos permisos en frozen_test |

La exportación híbrida conservadora, dejando además proyección y cabeza en
FP32, todavía tuvo 2 decisiones distintas frente a FP32. Una variante que
deja toda la última capa visual en FP32 tuvo 1 diferencia, pero no alcanza
equivalencia exacta.

No se probó FP16 para Android porque no existe aquí una confirmación del
runtime CPU Android objetivo que permita considerarlo compatible; no se usa
como atajo.

## Diagnóstico

El entrenamiento sí produjo una señal prometedora: la adaptación visual
reduce falsos filtros en los casos de personas comerciales y ropa moderna sin
aumentar falsos permisos en el examen congelado. El bloqueo actual es de
exportación/huella: FP32 conserva la calidad pero es demasiado grande; las
cuantizaciones compactas alteran casos fronterizos o introducen falsos
permisos.

Decisión final: `NO-GO` para canary Android y reemplazo de R3. Se conserva R3
como único modelo oficial. El siguiente trabajo recomendado es una reparación
de exportación cuantizada selectiva o un candidato visual entrenado con
márgenes más estables, seguido de una nueva verificación ORT Android; no se
debe cambiar el umbral ni crear excepciones por sitio, género, URL o campaña.

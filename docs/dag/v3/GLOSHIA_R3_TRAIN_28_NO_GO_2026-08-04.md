# GLOSHIA-R3-TRAIN-28 — Cierre NO-GO

Fecha: 2026-08-04  
Baseline oficial: GloshIA Visual R3  
Archivo: `tinyclip-r3-head-hybrid-int8.onnx`  
SHA-256: `0aaa1700182623173c41d233bd0e072cce2b2880aca14430d9f9af43fa2c44a8`

## Datos y contaminación

- Se seleccionaron 400 candidatos nuevos de Open Images V7.
- La auditoría exacta y perceptual excluyó 207 variantes o duplicados frente a
  material histórico o entre candidatos.
- Quedaron 193 muestras evaluables para revisión humana: 64 comerciales, 67 de
  ropa moderna, 35 de deporte/sensible y 27 de sujetos parciales o pequeños.
- Se revisaron 190 decisiones binarias y 3 `doubt`; las dudas quedaron fuera
  del entrenamiento y de las métricas binarias.
- Split reproducible con seed `2804`: 133 train, 28 validation y 29
  frozen_test.
- La prueba de contaminación pasó para SHA-256, cluster, serie, campaña,
  producto y pHash. No hubo cruce con el examen externo gate 27.
- `final_sealed` permaneció cerrado.

Openverse no produjo imágenes utilizables por respuestas HTTP 502. Wikimedia
Commons fue limitado por HTTP 429 y no se usó para este pool. No se forzaron ni
evadieron esos límites. El pool usado quedó compuesto sólo por Open Images V7,
con licencia de fuente CC BY 2.0 y autorización del propietario para el
experimento privado local; no se declaró `training_rights_clear`.

## Baseline R3 antes del entrenamiento

Se midió R3 con el mismo umbral `0,4` sobre los mismos splits. Esto no modifica
R3 ni el modelo oficial.

| Split | Matriz AA/AF/FA/FF | Accuracy | Balanced accuracy | Falsos permisos | Falsos filtros | PR-AUC |
| --- | --- | ---: | ---: | ---: | ---: | ---: |
| Validation, 28 | 19/2/1/6 | 25/28 = 89,29 % | 88,10 % | 1/7 | 2/21 | 0,773333 |
| Frozen test, 29 | 12/3/1/13 | 25/29 = 86,21 % | 86,43 % | 1/14 | 3/15 | 0,943326 |

`AA` es allow como allow, `AF` allow como filter, `FA` filter como allow y
`FF` filter como filter.

## Ensayos R3.1

Se reutilizó la arquitectura TinyCLIP vigente, el letterbox DAG, RGB 224×224,
la normalización existente y una única salida binaria. Los ensayos fueron
locales en CPU, con dos épocas y sin cargar frozen_test durante la selección.

| Ensayo | Configuración | Matriz validation | Balanced accuracy | Falsos permisos | Falsos filtros | PR-AUC |
| --- | --- | --- | ---: | ---: | ---: | ---: |
| Pilot 01 | LR 1,0; weight 1,0; decay 0,001 | 18/3/1/6 | 85,71 % | 1/7 | 3/21 | 0,776812 |
| Trial 02 | LR 0,5; weight 0,75; decay 0,001 | 19/2/1/6 | 88,10 % | 1/7 | 2/21 | 0,775000 |
| Trial 03 | LR 0,75; weight 0,9; decay 0,01 | 18/3/1/6 | 85,71 % | 1/7 | 3/21 | 0,775000 |

El mejor ensayo empata a R3 en falsos permisos y falsos filtros, pero no
mejora ningún gate y tiene menor PR-AUC. Los otros dos aumentan los falsos
filtros o reducen balanced accuracy. Por lo tanto no se congeló candidato
final y no se evaluó ningún candidato sobre frozen_test.

## Decisión

**NO-GO para TRAIN-28.**

R3 permanece oficial, intacto y sin cambio de umbral. No se exportó ONNX, no se
modificó DAG/Android, no se compiló APK, no se publicó, no se hizo push y no se
abrió `final_sealed`.

La revisión sí sirvió: confirmó que el problema es real y medible, pero este
pool y estos tres ensayos no contienen una mejora demostrada. El siguiente
intento debería ampliar sólo los grupos débiles identificados y obtener más
variación independiente antes de volver a entrenar; no conviene ajustar el
umbral para rescatar estos ensayos.

## Artefactos privados

- Corpus y revisiones: `.codex-tmp/gloshia-r3-train-28-20260804/candidate-400/`.
- Split y contaminación: `candidate-400/split.json`.
- Reportes R3: `candidate-400/r3-validation-report.json` y
  `candidate-400/r3-frozen_test-report.json`.
- Reportes de ensayos: `r3-1-pilot-report.json`,
  `r3-1-trial-02-report.json` y `r3-1-trial-03-report.json`.
- Checkpoints experimentales permanecen fuera de Git y no son modelos
  oficiales.

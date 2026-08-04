# GLOSHIA-R3-BALANCED-CORPUS-REVIEW-GATE-27

Fecha: 2026-08-04  
Baseline: GloshIA Visual R3, `tinyclip-r3-head-hybrid-int8.onnx`  
SHA-256: `0aaa1700182623173c41d233bd0e072cce2b2880aca14430d9f9af43fa2c44a8`  
Scope: evaluación privada local; `final_sealed` excluido y cerrado.

## Resultado

La revisión humana del lote quedó registrada en
`.codex-tmp/gloshia-r3-balanced-commercial-gate-20260804/review-295/`.
Hay 295 imágenes únicas por SHA-256: 241 de Open Images V7 y 54 del piloto
previo conservado por separado. Se encontraron y excluyeron 9 duplicados o
contaminaciones frente a manifiestos históricos antes de formar este lote.
No se publicó ni redistribuyó el corpus.

El lote tiene 194 `allow`, 91 `filter` y 10 `doubt`. Las dudas no entran en la
matriz binaria. El reporte indica 285 referencias binarias, 10 dudas y cero
filas pendientes de decisión humana; `pending_review=10` representa solamente
las dudas no binarias.

## Métricas de R3

Examen común: 285 decisiones binarias, sin abrir `final_sealed`.

| Métrica | Resultado |
| --- | ---: |
| Matriz `allow_as_allow / allow_as_filter / filter_as_allow / filter_as_filter` | `162 / 32 / 13 / 78` |
| Accuracy | 240/285 = 84,21 % |
| Balanced accuracy | 0,8461 = 84,61 % |
| Precisión `filter` | 78/110 = 70,91 % |
| Recall `filter` | 78/91 = 85,71 % |
| Precisión `allow` | 162/175 = 92,57 % |
| Recall `allow` | 162/194 = 83,51 % |
| Falsos permisos (`filter_as_allow`) | 13/91 = 14,29 % |
| Falsos filtros (`allow_as_filter`) | 32/194 = 16,49 % |
| F1 `filter` | 0,7761 |
| F1 `allow` | 0,8780 |
| Macro F1 | 0,8271 |
| PR-AUC para `filter` | 0,835803 |

Latencia de laboratorio Mac sobre 295 imágenes: p50 93,039 ms, p90 123,750
ms, p95 435,973 ms, media 120,929 ms y máximo 467,004 ms. Es una medición de
laboratorio, no una medición Android.

## Desglose relevante

- `commercial_people`: 3 falsos permisos de 8 filtros y 10 falsos filtros de
  73 allow; 84 binarias.
- `modern_clothing`: 5 falsos permisos de 40 filtros y 4 falsos filtros de 24
  allow; 64 binarias.
- `partial_or_small_subject`: 3 falsos permisos de 16 filtros y 11 falsos
  filtros de 31 allow; 47 binarias.
- `catalog_mannequin_safe`: 0 falsos permisos y 5 falsos filtros de 22 allow;
  24 binarias y 1 duda.
- `commercial_banner_safe`: 0 falsos permisos y 0 falsos filtros en 9
  binarias.
- `groups_public_normal`: 2 falsos permisos de 3 filtros y 0 falsos filtros;
  15 binarias.
- `sports_and_sensitive`: 0 falsos permisos y 2 falsos filtros; 37 binarias y
  1 duda.

Las categorías son estratos de búsqueda, no etiquetas automáticas. Las
categorías pequeñas no deben interpretarse como evidencia concluyente.

## Calidad y derechos

- 295/295 hashes SHA-256 únicos.
- Los duplicados visuales, variantes de resolución, crops y grupos históricos
  fueron auditados en el proceso de selección; no se afirma ausencia total de
  contaminación de sesión cuando la fuente no aporta ese dato.
- Todos los elementos quedaron `internal_evaluation_ok` y
  `training_rights_uncertain`; ninguno fue marcado `training_rights_clear`.
- El lote completo quedó en `directed_review`; todavía no existen splits
  independientes de entrenamiento, validation y frozen_test.

## Decisión

**GO condicionado para proponer TRAIN-28; NO-GO para entrenar con este lote
sin una nueva separación y autorización.**

La revisión aporta errores reales para R3, incluidos 13 falsos permisos y 32
falsos filtros, por lo que justifica preparar un ticket de entrenamiento. No
autoriza a reutilizar estas imágenes como entrenamiento ni como examen
independiente, no autoriza abrir `final_sealed` y no autoriza integrar otro
modelo. TRAIN-28 debe crear un pool de entrenamiento explícitamente autorizado
separado por serie/campaña/producto/origen/cluster, y reservar validation y
frozen_test antes de mirar resultados de candidato.

## Artefactos

- Manifest y revisiones: `.codex-tmp/gloshia-r3-balanced-commercial-gate-20260804/review-295/manifest.jsonl` y `reviews.json`.
- Predicciones R3: `predictions.jsonl` en el mismo directorio.
- Reporte reproducible: `evaluation-report.json` en el mismo directorio.
- Servidor móvil local: `http://192.168.0.186:8774/?token=wIvsQEBOISLNvUe9zDsDhfsI-JuI9ilW`.

No se modificaron pesos, umbrales, política, DAG, Android ni APK.

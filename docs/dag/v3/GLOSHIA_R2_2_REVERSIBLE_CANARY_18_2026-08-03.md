# GloshIA R2.2 - canary reversible de pipeline real

Fecha: 2026-08-03

Ticket: `GLOSHIA-R2.2-REVERSIBLE-CANARY-18`

Resultado: `NO-GO`

## Objetivo

Comparar R1 y el candidato R2.2 selectivo dentro del recorrido real de DAG:
decodificacion de bytes, preprocesamiento de 224 x 224, revision regional y
politica final. El canary fue aislado, opt-in y no reemplazo el modelo oficial.

## Artefactos congelados

- R1 oficial: `tinyclip-bounded-finetune-r1-int8.onnx`.
- SHA-256 R1:
  `2d52bd9e5eb4cd448cb0d64a784b2ee6f761ad20e890c57b898fd7991d29a9ee`.
- Candidato R2.2 selectivo: `r2.2-candidate-b-selective-k-int8.onnx`.
- SHA-256 R2.2:
  `7e8826f72df12ca76f21b929c3c798c967ea381b558116fd45b27bb71d461bdb`.
- Examen: 40 imagenes reales revisadas y enlazadas por hash; 28 `allow` y 12
  `filter`.
- `final_sealed` no se abrio.

El manifiesto se genero con
`scripts/dag_v3_model/r22_canary_manifest.py`. El harness Android reutilizo
`DagMediaBytesPolicy`, el preprocesador y el analizador R1 productivos. El
candidato vivio solo en los assets temporales del APK de instrumentacion.

## Dispositivo

- Samsung Galaxy S22 Ultra `SM-S908E`.
- Android 16, API 36.
- ONNX Runtime Android CPU 1.27.0.

## Resultado

| Metrica | R1 | R2.2 |
| --- | ---: | ---: |
| Aciertos | 34/40 (85,0 %) | 34/40 (85,0 %) |
| Falsos permisos | 2/12 | 3/12 |
| Falsos filtros | 4/28 | 3/28 |
| Recall `filter` | 83,33 % | 75,00 % |
| Balanced accuracy | 84,52 % | 82,14 % |
| Inferencias totales | 72 | 63 |
| Politica p50 | 42,21 ms | 41,84 ms |
| Politica p95 | 184,96 ms | 181,42 ms |
| Inferencia p50 | 35,13 ms | 35,55 ms |
| Inferencia p95 | 174,13 ms | 175,50 ms |

PSS pico observado: 88.748 KiB. No hubo errores de pipeline ni salidas no
finitas. El rendimiento fue comparable y R2.2 redujo un falso filtro, pero el
gate de seguridad prohibia agregar falsos permisos.

## Caso que decide el NO-GO

`wikimedia:172790759` estaba etiquetada `filter`. R1 termino en `block` y R2.2
en `allow`. La probabilidad maxima observada por el candidato fue alta, pero el
voto regional del pipeline no produjo el bloqueo final. Esto demuestra por que la
equivalencia sobre tensores congelados no sustituye la prueba de bytes y
politica completa.

Tambien hubo dos cambios favorables para contenido permitido y un falso
filtro nuevo, pero ninguna de esas mejoras compensa un falso permiso adicional.
El examen de este canary queda consumido y no debe usarse para ajustar el mismo
candidato.

## Cierre reversible

- R1 continua oficial e intacto.
- DAG 95, extension, umbrales y politica productiva no cambiaron.
- El APK de laboratorio fue desinstalado del S22.
- Las 40 imagenes y el ONNX temporales fueron retirados de `androidTest`.
- El informe completo permanece solo en
  `.codex-tmp/gloshia-r22-sports-repair-20260803/reversible-canary-18/android-s22-result.json`.
- No hubo push, publicacion, Supabase ni Production.

## Siguiente paso recomendado

No integrar ni recalibrar R2.2 con este examen. Si se continua, preparar un
R2.3 con datos nuevos independientes que representen el patron regional del
fallo y evaluarlo con otro holdout desconocido. No usar dos modelos en DAG ni
agregar una excepcion por imagen o sitio.

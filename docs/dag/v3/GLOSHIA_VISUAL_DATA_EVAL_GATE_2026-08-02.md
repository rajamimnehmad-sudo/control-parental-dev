# GloshIA Visual — DATA-EVAL-GATE-07

Fecha: 2026-08-02
Baseline: `main` / `141b519`, DAG 87 (`0.67.0-dev`)
Modelo: `tinyclip-bounded-finetune-r1-int8.onnx`
SHA-256: `2d52bd9e5eb4cd448cb0d64a784b2ee6f761ad20e890c57b898fd7991d29a9ee`
Runtime de laboratorio: `onnxruntime-web 1.27.0`; el resultado es benchmark de Mac, no Android.

## Resultado del gate

El laboratorio y el corpus piloto quedaron creados sin entrenar, cambiar pesos,
cambiar umbrales, abrir `final_sealed`, modificar el pipeline Android o publicar
imágenes. El corpus tiene 500 muestras; 108 pertenecen al split `final_sealed`
y permanecen fuera de todas las mediciones. Se conservaron 109 muestras en
cuarentena porque coincidían exactamente por SHA-256 con manifiestos históricos.

El conjunto no sellado, no contaminado y medido tiene 283 muestras. Esto es una
evaluación útil del modelo vigente, pero todavía no es evidencia suficiente para
autorizar entrenamiento: la revisión humana binaria es parcial y el criterio
final corresponde al propietario.

## Procedencia, derechos y contaminación

El piloto se descargó sólo de Wikimedia Commons, mediante páginas públicas y
miniaturas HTTPS. El manifiesto conserva URL pública, catálogo, licencia,
hashes, pHash/dHash, MIME, dimensiones, tamaño, fecha de adquisición, categoría,
serie y estado de uso. No se guardaron perfiles, comentarios ni metadatos
privados. No se publicó ni redistribuyó el corpus.

Las 500 muestras tienen `training_rights_uncertain`: una licencia pública del
archivo no resuelve por sí sola derechos de imagen, atribución o uso comercial.
Por tanto, el lote es apto para evaluación interna, no está autorizado para
entrenamiento ni publicación.

Comparación contra cuatro manifiestos históricos disponibles:

- 1.000 + 100 + 1.000 + 20 filas históricas inspeccionadas por hashes;
- 109 coincidencias exactas de SHA-256 y sample_id;
- 0 coincidencias adicionales con pHash de distancia Hamming <= 8;
- no se afirma ausencia total de contaminación fuera de esos manifiestos.

Las muestras contaminadas no se borraron: están en `quarantine.json`, con
`usage_state=excluded`, y no entran en el runner ni en los reportes.

## Composición efectiva

La distribución inicial se ajustó sólo cuando cuatro búsquedas resultaron
limitadas después de licencias, tamaño y deduplicación. Se completó la cuota con
`current_mixed`, manteniendo 500 imágenes y documentando el cambio en el plan.

| Categoría | Corpus | Evaluables no selladas/no cuarentena |
| --- | ---: | ---: |
| modern_boundary | 31 | 2 |
| men_covered | 75 | 15 |
| groups_families | 75 | 34 |
| small_subjects | 6 | 4 |
| sports | 50 | 32 |
| public_school_community | 50 | 40 |
| partial_crops | 50 | 38 |
| illustration_product | 10 | 4 |
| sensitive_control | 20 | 10 |
| current_mixed | 133 | 104 |
| **Total** | **500** | **283** |

Los casos explícitos obvios no dominan el lote; el valor está concentrado en
ropa contemporánea, hombres, grupos, escenas públicas, deporte, sujetos
pequeños, recortes y fondos complejos. La categoría `modern_boundary` quedó
limitada a 31 por disponibilidad real y `small_subjects` a 6; no se rellenaron
con imágenes antiguas ni con duplicados.

## Métricas del modelo vigente

Se usó el runner real: RGB, letterbox gris, normalización CLIP, entrada 224×224,
misma política `dag-36` y vistas regionales cuando las activa el pipeline. Se
procesaron 283/283 muestras, con 0 errores, siempre con el SHA esperado.

| Métrica | Resultado |
| --- | ---: |
| decisiones GloshIA | allow 151/283; filter 132/283 |
| latencia media Mac | 102.543 ms |
| p50 | 58.852 ms |
| p90 | 271.950 ms |
| p95 | 274.602 ms |
| máximo | 291.048 ms |
| inferencias por imagen | media 1.813; distribución 1:223, 3:2, 4:6, 5:52 |

Esto no es una medición de CPU Android. No se modificó DAG para obtenerla.

## Revisión humana provisional

Se revisaron manualmente 58 muestras no contaminadas: 52 binarias y 6 `doubt`.
Las dudas quedaron excluidas de la matriz y no se transformaron en `allow`.
La revisión fue una ronda de auditoría acotada (`codex-audit`), no una
aprobación definitiva del criterio del propietario; quedan 231 muestras
pendientes.

Matriz binaria revisada, 52 muestras:

| Verdad humana / modelo | allow | filter |
| --- | ---: | ---: |
| allow | 29 | 23 |
| filter | 0 | 0 |

Resultados derivados de esa matriz, siempre con numerador/denominador:

- accuracy: 29/52 = 0.557692;
- balanced accuracy: no calculable: no hubo filtros humanos binarios en esta ronda;
- precisión de allow: 29/(29+0) = 1.000000;
- recall de allow: 29/(29+23) = 0.557692;
- precisión/recall/F1 de filter: no calculables por 0 positivos humanos;
- falsos filtros: 23/52 = 0.442308;
- falsos permisos observados: 0/0; esto no demuestra que no existan en las 231 pendientes;
- PR-AUC: no calculable con una clase positiva humana ausente.

El patrón provisional más claro es el exceso de filtrado en hombres, grupos,
deporte, escenas escolares/comunitarias, sujetos pequeños y algunas imágenes de
producto/ilustración. No se debe convertir este resultado en ajuste de umbral:
la muestra aún no contiene suficientes filtros humanos confirmados.

## Errores y causa probable

La causa probable no es sólo el umbral. El modelo tiende a responder a señales
visuales de ropa ajustada, piel, recortes y composición regional sin suficiente
contexto de persona, grupo, edad o escena pública. Las vistas regionales también
pueden elevar la decisión en fotografías panorámicas o de grupos. La ronda no
permite todavía cuantificar falsos permisos en forma confiable.

Las listas auditables están en `false-allows.json`, `false-filters.json`,
`doubts.json` y `quarantine.json` dentro del directorio privado del piloto.

## Rendimiento, espacio y riesgo

- modelo sin cambios: 8.735.186 bytes;
- corpus piloto completo: aproximadamente 107 MB de imágenes normalizadas;
- laboratorio completo con manifiestos, predicciones, hojas de contacto y
  respaldos: aproximadamente 115 MB al cierre;
- entrenamiento: no ejecutado, por lo que no hubo costo de GPU ni riesgo de
  degradar precisión, APK o latencia;
- para un futuro lote de 1.800–2.400 muestras, reservar aproximadamente
  0.5–2 GB incluyendo originales temporales, manifiestos y artefactos;
- GPU no es necesaria para evaluar el modelo actual; para fine-tuning local
  posterior sería recomendable una GPU CUDA con al menos 8 GB, o aceptar varias
  horas/una noche en CPU. Estos son rangos de planificación, no mediciones.

## Recomendación

`NO-GO` para `GLOSHIA-VISUAL-CANDIDATE-TRAIN-08` en este momento. Primero el
propietario debe completar o confirmar la revisión de los pendientes, reforzar
todos los posibles filtros y falsos permisos, y cerrar la procedencia/licencia
del subconjunto que eventualmente podría entrenarse. El próximo ticket de
entrenamiento sólo debería comenzar con un examen congelado, holdout sellado
intacto y comparación contra este mismo examen.

Artefactos privados del piloto: `.codex-tmp/gloshia-pilot-20260802/`.

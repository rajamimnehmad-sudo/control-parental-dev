# GloshIA Visual — BALANCED-REVIEW-09

Fecha: 2026-08-02  
Baseline: `main` / `e48742c`; DAG 87 (`0.67.0-dev`)  
Modelo: `tinyclip-bounded-finetune-r1-int8.onnx` (GloshIA Visual R1)  
SHA-256: `2d52bd9e5eb4cd448cb0d64a784b2ee6f761ad20e890c57b898fd7991d29a9ee`  
Runtime: ONNX Runtime 1.27.0; inferencia local CPU.  

## Alcance y seguridad

Se completó la revisión equilibrada sin entrenar, calibrar, modificar pesos,
umbrales, política visual, Android, DAG, APK, Supabase, GitHub ni
`final_sealed`. El lote nuevo fue utilizado sólo para evaluación interna.

El corpus combinado de trabajo tiene 330 muestras: 283 limpias originales y
47 muestras nuevas independientes después de deduplicación. El examen sellado
de 108 muestras no se abrió. Las 109 muestras históricas en cuarentena no
entran en el examen.

## Lote nuevo y deduplicación

Se recopilaron 100 imágenes públicas adicionales desde Wikimedia Commons, con
licencias CC BY, CC BY-SA, CC0 o dominio público. Se conservaron URL pública,
catálogo, licencia, hashes SHA-256, dHash/pHash, dimensiones, MIME, bytes,
fecha, categoría, serie y estado de uso. No se conservaron nombres de usuario,
comentarios, perfiles ni metadatos privados.

De esas 100 muestras:

- 47 quedaron `internal_evaluation_ok` como evidencia independiente;
- 35 fueron excluidas por pertenecer a la misma serie/cluster visual que otra
  muestra del lote;
- 18 coincidieron por SHA-256 o sample ID con manifiestos históricos;
- no aparecieron coincidencias perceptuales históricas adicionales con pHash
  de distancia Hamming <= 8;
- las 47 nuevas mantienen `training_rights_uncertain`, por lo que no están
  autorizadas para entrenamiento ni publicación.

La limitación permanece explícita: los manifiestos históricos disponibles no
demuestran ausencia total de contaminación fuera de esas fuentes.

## Revisión y colas

La web local existente se amplió con colas de trabajo: posibles filter,
desacuerdos, casos fronterizos, dudas, posibles falsos filtros, muestra
aleatoria y resto. La predicción permanece oculta antes de decidir y se revela
después con probabilidad y coincidencia/desacuerdo. La web sigue funcionando
sin API, sin Supabase y con modo loopback o LAN privada con token temporal.

En el corpus combinado quedaron 330 decisiones registradas:

- allow: 279/330 (84.55%);
- filter: 43/330 (13.03%);
- doubt: 8/330 (2.42%), separadas de la matriz binaria;
- pendientes binarias: 8 dudas, no convertidas silenciosamente a allow.

La ronda superó el objetivo de 100 decisiones allow, pero quedó por debajo del
objetivo orientativo de 50 decisiones filter: hubo 43. No se inventaron filtros
para completar el número; conviene ampliar el lote dirigido antes de entrenar.

## Métricas del examen actual

Las métricas siguientes excluyen `doubt` de la matriz. Siempre se muestran
numerador y denominador cuando representan tasas.

| Métrica | Original limpio (283) | Lote dirigido (47) | Combinado (330) |
| --- | ---: | ---: | ---: |
| revisadas binarias | 276/283 | 46/47 | 322/330 |
| dudas | 7/283 | 1/47 | 8/330 |
| matriz allow→allow | 148 | 20 | 168 |
| matriz allow→filter (falso filtro) | 93 | 18 | 111 |
| matriz filter→allow (falso permiso) | 2 | 0 | 2 |
| matriz filter→filter | 33 | 8 | 41 |
| accuracy | 181/276 = 0.655797 | 28/46 = 0.608696 | 209/322 = 0.649068 |
| balanced accuracy | 0.778482 | 0.763158 | 0.777819 |
| precisión filter | 33/126 = 0.261905 | 8/26 = 0.307692 | 41/152 = 0.269737 |
| recall filter | 33/35 = 0.942857 | 8/8 = 1.000000 | 41/43 = 0.953488 |
| precisión allow | 148/150 = 0.986667 | 20/20 = 1.000000 | 168/170 = 0.988235 |
| recall allow | 148/241 = 0.614108 | 20/38 = 0.526316 | 168/279 = 0.602151 |
| F1 filter | 0.420513 | 0.470588 | 0.420513 |
| falsos permisos | 2/35 = 5.714% | 0/8 = 0% | 2/43 = 4.651% |
| falsos filtros | 93/241 = 38.589% | 18/38 = 47.368% | 111/279 = 39.785% |
| PR-AUC filter | 0.677693 | 0.797619 | 0.677003 |

El subconjunto dirigido es pequeño (47 muestras) y no debe generalizarse sin
una nueva ronda. El comportamiento principal sigue siendo exceso de filtrado,
no una ausencia demostrada de filtros: el conjunto contiene sólo 43 filtros
humanos combinados.

## Patrones observados

Los falsos filtros se concentran visualmente en hombres cubiertos, grupos y
familias, escenas escolares/comunitarias, deporte normal, sujetos pequeños,
recortes parciales y escenas públicas. En el lote nuevo también aparecen en
eventos modernos y deportes contextualizados. Los dos falsos permisos
observados corresponden a escenas performáticas/deportivas que requieren
revisión reforzada.

La revisión no justifica modificar el umbral ni la política. La causa probable
combina señales de piel/ropa ajustada y decisiones regionales con poca
comprensión del contexto, cantidad de personas y escena pública. Las
categorías de edad, cantidad de personas, escala del sujeto y cobertura
corporal no fueron anotadas como atributos humanos estructurados en esta ronda;
los reportes los dejan explícitamente como `unannotated` y no se presentan como
desgloses confiables.

## Rendimiento y espacio

El modelo vigente no cambió y conserva 8.735.186 bytes. El benchmark de Mac
para el examen combinado fue:

- media: 99.715 ms;
- p50: 59.010 ms;
- p90: 271.911 ms;
- p95: 274.602 ms;
- máximo: 291.048 ms;
- inferencias por imagen: media 1.752; distribución 1:265, 3:2, 4:8, 5:55.

Es benchmark de laboratorio, no medición Android. No se agregó trabajo al
pipeline DAG. El laboratorio BALANCED-REVIEW-09 ocupa aproximadamente 76 MB;
el lote dirigido bruto y sus checkpoints ocupan aproximadamente 35 MB
adicionales. No hubo GPU, costo de inferencia remoto ni publicación.

## Estado y recomendación

`NO-GO` para `GLOSHIA-VISUAL-CANDIDATE-TRAIN-08` todavía. El modelo vigente
debe conservarse. Antes de entrenar faltan al menos una nueva tanda de filtros
humanos confirmados hasta equilibrar la matriz, anotaciones estructuradas para
los estratos débiles y una decisión legal explícita sobre qué imágenes tienen
derechos de entrenamiento. `final_sealed` debe seguir cerrado hasta que el
candidato de entrenamiento esté completamente congelado.

Artefactos privados: `.codex-tmp/gloshia-balanced-review-20260802/`. No se
publican ni se incluyen imágenes en Git.

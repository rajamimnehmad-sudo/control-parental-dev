# DAG V3 model tools

Herramientas locales, reproducibles y sin red para preparar el futuro dataset visual. No forman
parte del APK y no activan decisiones del navegador.

## Mapa vigente

La única referencia oficial para el runtime es el modelo R3.1 ya incluido en
`app-dag-browser/src/main/assets/dag-model/`. Los scripts de este directorio
son herramientas de laboratorio, no rutas de ejecución de DAG.

Para continuar una evaluación nueva se deben reutilizar únicamente estos
componentes y revisar primero su informe correspondiente:

- `onnx_split_score.py`: examen binario reproducible sobre validation/frozen_test.
- `r2_candidate_train.py` y `r2_candidate_export.py`: entrenamiento/exportación
  experimental; no sustituyen el modelo Android.
- `r3_2_directed_repair_split.py`: splits agrupados y control de contaminación
  para el experimento R3.2.
- `manifest_validator.py`, `evaluation_harness.py` y
  `pilot_training_provenance.py`: validación de contrato, procedencia y
  resultados.

Los scripts `pilot_*`, `r22_*`, `r23_*`, `r24_*` y los informes fechados de
R1/R2/R3 anteriores se conservan como evidencia histórica reproducible. No se
deben usar para seleccionar un modelo nuevo sin un ticket que los vuelva a
declarar vigentes. Los modelos, imágenes, checkpoints y resultados privados
permanecen fuera de Git en `.codex-tmp`.

## Auditar procedencia del piloto binario

Antes de reutilizar rondas historicas o pagar GPU, `pilot_training_provenance.py` comprueba que los
archivos humanos sigan completos, calcula hashes reales y separa entrenamiento, validacion y
holdout. Tambien cruza esos hashes con los manifiestos de descarga y exige autorizacion explicita
de licencia, uso ML y derechos de imagen:

```bash
python3 scripts/dag_v3_model/pilot_training_provenance.py \
  --train revision-ronda-1.json items-ronda-1.json public/ \
  --validation revision-ronda-3.json items-ronda-3.json public/ \
  --holdout revision-ronda-9.json items-ronda-9.json public/ \
  --download-manifest descargas/downloads.jsonl \
  --output .codex-tmp/dag-v3-pilot/provenance-report.json --pretty
```

El comando no copia fotos, no usa red y no entrena. Termina con `0` solamente cuando el conjunto
esta listo para reentrenar, con `3` cuando la auditoria se completo pero hay bloqueos, y con `1`
ante entradas invalidas. Una licencia abierta o `needs_license_and_visual_review` no se convierte
automaticamente en permiso de entrenamiento: deben constar `training_authorized`, revision ML y
derechos aprobados de manera explicita.

## Validar un manifiesto

```bash
python3 scripts/dag_v3_model/manifest_validator.py ruta/al/manifiesto.jsonl
```

El proceso imprime un resumen JSON, escribe errores con numero de linea en stderr y termina con:

- `0`: manifiesto valido;
- `1`: muestras invalidas;
- `2`: archivo o contrato ilegible.

Comprueba:

- esquema `dag-v3-dataset-manifest-v1`;
- contrato de 21 senales y estados de anotacion;
- fuente y referencias HTTPS/URN aprobadas;
- licencia comercial, derivados, revision ML y derechos;
- hashes, dimensiones, MIME, IDs y timestamps;
- prelabels separados de etiquetas humanas;
- decisiones completas e independientes de cada revisor;
- doble revision para validacion/test y adjudicador tercero ante desacuerdos;
- coincidencia exacta entre revisiones, adjudicacion y etiqueta final;
- dependencias semanticas versionadas entre persona, presentacion, edad y senales derivadas;
- ausencia de contenido sin revisar en splits asignados;
- hashes duplicados y grupos/clusters cruzados entre splits.

No descarga imagenes ni verifica remotamente que una licencia siga publicada. Esa evidencia debe
capturarse durante la importacion autorizada y queda registrada en el manifiesto.

## Medir acuerdo entre revisores

```bash
python3 scripts/dag_v3_model/annotation_agreement.py \
  manifiesto.jsonl --pretty > acuerdo.json
```

Primero valida el manifiesto completo. Despues compara solamente las muestras que conservan dos
revisiones independientes y reporta, por senal:

- acuerdo exacto entre los cuatro estados revisados;
- kappa de Cohen, sin ocultar el acuerdo bruto cuando kappa no esta definido;
- acuerdo binario donde ambos eligieron `positive` o `negative`;
- desacuerdos positivo/negativo, desacuerdos con incertidumbre y arbitrajes;
- hasta 50 IDs seudonimos para revisar los casos concretos.

El mismo calculo queda separado por split y version de la guia. La herramienta no abre imagenes, no
usa red y no fija por si sola un objetivo de acuerdo: ese gate necesita el piloto y revision Ultra.

## Inventario acotado de Openverse

```bash
python3 scripts/dag_v3_model/openverse_inventory.py \
  --query 'modest fashion woman' \
  --query 'sleeveless dress woman' \
  > candidatos.jsonl
```

El inventario consulta solamente metadatos, limita cada pagina a 20 resultados y cada consulta a
tres paginas como maximo. Solicita solo `by`, `by-sa`, `cc0` y `pdm`, excluye resultados marcados
como maduros, deduplica IDs/paginas y nunca solicita la URL del archivo, thumbnail ni pixeles.

Cada resultado queda como `needs_review`. Openverse no garantiza que sus datos de licencia sean
correctos: antes de convertir un candidato en muestra elegible hay que verificar pagina original,
atribucion, permiso comercial/derivados, derechos de imagen y relevancia visual.
El inventario puede descubrir CC BY-SA para una eventual revision legal, pero el descargador piloto
lo rechaza: 05A solo permite descargar CC BY, CC0 o dominio publico verificados.

## Descarga piloto acotada

Solo despues de autorizar explicitamente una prueba, un inventario puede convertirse en una carpeta
local de candidatos:

```bash
python3 scripts/dag_v3_model/openverse_pilot_downloader.py \
  candidatos.jsonl .codex-tmp/dag-v3-pilot/downloaded --limit 20 \
  --known-downloads lote-anterior/downloads.jsonl \
  --delay-seconds 1
```

El descargador acepta como maximo 100 candidatos, 8 MiB por archivo y 100 MiB totales. Rechaza
HTTP, destinos de red no publicos, licencias fuera del sondeo, respuestas vacias, formatos que no
sean JPEG/PNG/GIF/WebP y duplicados exactos. Guarda los pixeles fuera de Git junto con
`downloads.jsonl`, hashes y procedencia. La carpeta de salida debe estar vacia para no mezclar dos
pilotos accidentalmente. `--known-downloads` puede repetirse para evitar duplicados exactos contra
lotes anteriores.
Para Wikimedia se usa una demora de un segundo, solicitudes en serie y un `User-Agent` identificable
con el repositorio como contacto; un `429` se respeta como fallo y nunca se evade.

Una descarga exitosa sigue marcada como `needs_license_and_visual_review`: no integra la imagen al
dataset, no la etiqueta y no autoriza entrenamiento. Openverse agrega metadatos de terceros y exige
verificar por separado los derechos y terminos de cada obra.

## Inventario acotado de Wikimedia Commons

```bash
python3 scripts/dag_v3_model/wikimedia_inventory.py \
  --query 'crop top' \
  --query 'sleeveless dress' \
  > candidatos-wikimedia.jsonl
```

Consulta la API oficial de Commons con un maximo de 20 resultados por pagina y tres paginas por
consulta. Recupera una miniatura de hasta 1024 px junto con pagina de descripcion, autor, licencia,
SHA-1 de origen y restricciones publicadas. Solo conserva raster y licencias potencialmente
comerciales/modificables (`CC BY`, `CC BY-SA`, `CC0` o dominio publico).

El inventario sigue siendo descubrimiento: Commons exige comprobar la pagina de cada archivo,
atribucion, licencia y restricciones no relacionadas con copyright, incluidos derechos de imagen.
Los candidatos compatibles se descargan con el mismo `openverse_pilot_downloader.py`, que acepta el
contrato generico `candidate_id` aunque conserva su nombre historico.

## Evaluar predicciones y cuantizacion

```bash
python3 scripts/dag_v3_model/evaluation_harness.py \
  predicciones.jsonl politica-piloto-no-aprobada.json \
  --reference-variant float \
  --candidate-variant int8 \
  --slice-key source \
  --slice-key media_type \
  --pretty > reporte.json
```

Cada fila de predicciones usa `dag-v3-prediction-v1`, conserva `sample_id`, las 21 etiquetas humanas
y un vector ordenado de 21 probabilidades por variante. Los nombres y el orden salen de
`glosh-visual-signals-v1.json`; una probabilidad faltante, fuera de rango o una variante desigual
entre filas invalida la corrida completa.

La politica de evaluacion usa `dag-v3-evaluation-policy-v1`. Debe declarar las 21 senales, dos
umbrales por senal y si esa senal bloquea o solo se observa:

```json
{
  "schema_version": "dag-v3-evaluation-policy-v1",
  "signal_contract_version": "glosh-visual-signals-v1",
  "policy_version": "pilot-unapproved-1",
  "labels": {
    "adult_or_explicit": {
      "uncertain_threshold": 0.2,
      "positive_threshold": 0.8,
      "policy_action": "block"
    }
  }
}
```

El fragmento muestra la forma, pero el archivo real debe incluir exactamente las 21 senales. Los
umbrales del ejemplo no estan aprobados ni habilitan el navegador.

El reporte calcula por senal precision, recall, falsos negativos, PR-AUC, Brier, calibracion,
intervalos Wilson, curva precision/recall y cuantos positivos quedan debajo del umbral de
incertidumbre. Tambien calcula la politica completa, separa `block`, `uncertain` y `allow`, lista
hasta 50 IDs de falsos permisos y compara cruces de umbral y regresiones entre float e INT8.

Solo `positive` y `negative` entran en las metricas de una senal. `unknown`, `not_applicable` y
`unreviewed` quedan visibles como enmascarados. Para la verdad de politica, un positivo basta para
`block`; sin positivos, `unknown` o `unreviewed` dejan el caso sin resolver y `not_applicable` no
crea por si solo una senal de riesgo.

La herramienta no abre imagenes, no usa red, no entrena y no decide si un modelo esta aprobado.
Limita archivo, filas, variantes, campos de corte y cantidad de valores por corte para que un
reporte accidentalmente enorme falle de manera controlada.

## Linea base binaria del piloto humano

La primera revision privada puede medirse como `allow` frente a `filter`, combinando `blur` y
`block` segun la aclaracion de politica del piloto. Esta prueba no cambia Android, no produce un
modelo aprobable ni reemplaza el contrato multietiqueta:

```bash
python scripts/dag_v3_model/pilot_binary_baseline.py \
  revision-humana.json \
  review-items.json \
  public/ \
  .codex-tmp/dag-v3-pilot/baseline-report.json \
  --weights-cache .codex-tmp/dag-v3-baseline-cache
```

La herramienta valida que la revision este completa, que IDs e imagenes coincidan y que no haya
acciones dudosas. Extrae rasgos congelados de MobileNetV3-Small preentrenado, respetando el
letterbox gris de 224 px del contrato, y mide una regresion logistica mediante validacion cruzada
estratificada repetida. El reporte conserva falsos permisos y falsos filtros por ID.
`--pooling average-max` permite medir, en la misma pasada del modelo, si conservar tambien la
activacion espacial maxima ayuda con sujetos pequenos; es una ablacion y no una decision aprobada.
Una segunda revision independiente puede pasarse con `--external-review`, `--external-items` y
`--external-public-dir`. En ese modo el clasificador aprende solo de la primera ronda y la segunda
se conserva como prueba dirigida que nunca participa del ajuste.

Es solamente una comprobacion de viabilidad con 100 imagenes. Sin un split aprobado por origen y
cluster perceptual, ni un conjunto externo, sus resultados no autorizan enforcement ni APK.

## Afinado acotado del piloto

`pilot_finetune.py` combina rondas humanas ya resueltas, conserva cinco pliegues de prueba y afina
primero la cabeza binaria y despues solamente las ultimas tres capas de MobileNetV3-Small. Mantiene
BatchNorm congelado, no recorta la imagen, limita epocas y no produce un modelo para Android:

```bash
python scripts/dag_v3_model/pilot_finetune.py \
  --review revision-ronda-1.json --items items-ronda-1.json --public-dir public \
  --review revision-ronda-2.json --items items-ronda-2.json --public-dir public \
  --weights-cache .codex-tmp/dag-v3-baseline-cache \
  --output .codex-tmp/dag-v3-pilot/finetune-report.json
```

El reporte es investigacion local. Aun con buenos resultados, requiere una tercera prueba
independiente y exportacion/benchmark LiteRT antes de entrar al APK.

## Profesor experimental por regiones

Si la linea de imagen completa no alcanza el gate, `pilot_region_teacher.py` compara rasgos de la
imagen con regiones de persona, torso superior y zona inferior detectadas localmente. El detector
SSDLite se usa exclusivamente como profesor de investigacion: no se exporta, no se integra a
Android y no cambia la meta de un unico modelo final. La ronda de validacion no participa del ajuste
ni de la seleccion del candidato:

```bash
python scripts/dag_v3_model/pilot_region_teacher.py \
  --review revision-ronda-1.json --items items-ronda-1.json --public-dir public \
  --review revision-ronda-2.json --items items-ronda-2.json --public-dir public \
  --validation-review revision-ronda-3.json \
  --validation-items items-ronda-3.json \
  --validation-public-dir public \
  --weights-cache .codex-tmp/dag-v3-baseline-cache \
  --output .codex-tmp/dag-v3-pilot/region-teacher-report.json
```

Los pesos de TorchVision/COCO no quedan aprobados para redistribucion por esta prueba. Antes de usar
un detector o pose landmarker en un pipeline comercial se verifica por separado licencia, modelo,
privacidad, mantenimiento y compatibilidad. Un resultado favorable solo autoriza estudiar
supervision regional o destilacion hacia el modelo unico.

## Estudiante unico con supervision regional

`pilot_single_student.py` usa el detector solamente durante entrenamiento para crear una mascara de
atencion sobre el torso superior. Entrena un MobileNetV3-Small con atencion espacial incorporada y
lo evalua sin detector, recortes ni segundo modelo. El checkpoint sigue siendo investigacion local:
no se exporta ni entra a Android.

```bash
python scripts/dag_v3_model/pilot_single_student.py \
  --review revision-ronda-1.json --items items-ronda-1.json --public-dir public \
  --review revision-ronda-2.json --items items-ronda-2.json --public-dir public \
  --review revision-ronda-4.json --items items-ronda-4.json --public-dir public \
  --validation-review revision-ronda-3.json \
  --validation-items items-ronda-3.json \
  --validation-public-dir public \
  --weights-cache .codex-tmp/dag-v3-baseline-cache \
  --checkpoint .codex-tmp/dag-v3-pilot/single-student.pt \
  --output .codex-tmp/dag-v3-pilot/single-student-report.json
```

La validacion no ajusta pesos ni detiene epocas, pero la ronda 3 ya influyo en la eleccion de la
arquitectura. Por eso un resultado favorable exige otra prueba independiente antes de exportar.

## A/B agrupado de representación R4

`r4_reviewed_group_folds.py` congela grupos humanos sin contaminación y exige
una reducción mínima del 20 % en falsos permisos y falsos filtros. Cada fold se
entrena con `r4_consistency_train.py`; después,
`r4_reviewed_representation_cv.py` puntúa exclusivamente los grupos no vistos,
reconstruye la validación fija desde sus predicciones y aplica el gate común.
No lee `frozen_test` ni `final_sealed` y no exporta modelos.

## Corpus independiente R4

`r4_independent_corpus_plan.py` convierte lotes con revisión completa del
propietario en train y holdout agrupados. Verifica los bytes reales, conserva
separado el SHA histórico cuando no coincide, excluye dudas, duplicados y
contaminación por ID, hash, URL, grupo o pHash contra train, validation y el
gate dirigido. Una serie completa permanece en un solo split aunque contenga
tomas `allow` y `filter`.

El resultado es sólo para experimento privado: no declara derechos comerciales,
no abre `frozen_test`/`final_sealed`, no entrena y no autoriza ONNX ni APK.

## Tests

```bash
python3 -m unittest discover -s scripts/dag_v3_model/tests -p 'test_*.py'
```

## Artefacto DEV integrado

El piloto convergió en un único clasificador binario `allow/filter` para el
navegador actual. No son varios motores de generaciones anteriores.

```text
modelo: tinyclip-bounded-finetune-r1-int8.onnx
sha256: 2d52bd9e5eb4cd448cb0d64a784b2ee6f761ad20e890c57b898fd7991d29a9ee
entrada: pixel_values float32 [1, 3, 224, 224]
salida: probabilidad de filter
umbral Android: 0.4
entrenamiento: 197 ejemplos
validacion congelada: 21 casos
holdout independiente: 4 casos allow
```

Resultado congelado: recall de filtro `1.0`, cero falsos permisos, un falso
filtro y exactitud `0.952381`; el holdout obtuvo `4/4`. El archivo está
cuantizado para distribución, aunque conserva entrada float normalizada para
ONNX Runtime. El nombre del asset y su test de SHA-256 son canónicos desde v18.

El artefacto queda habilitado solamente como candidato DEV. No debe presentarse
como certificación universal, cobertura de todas las edades o autorización de
Production.

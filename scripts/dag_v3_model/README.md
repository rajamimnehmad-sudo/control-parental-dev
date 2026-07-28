# DAG V3 model tools

Herramientas locales, reproducibles y sin red para preparar el futuro dataset visual. No forman
parte del APK y no activan decisiones del navegador.

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

## Tests

```bash
python3 -m unittest discover -s scripts/dag_v3_model/tests -p 'test_*.py'
```

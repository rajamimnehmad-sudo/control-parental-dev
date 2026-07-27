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

- contrato de 21 senales y estados de anotacion;
- fuente y referencias HTTPS/URN aprobadas;
- licencia comercial, derivados, revision ML y derechos;
- hashes, dimensiones, MIME, IDs y timestamps;
- prelabels separados de etiquetas humanas;
- doble revision para validacion y test;
- ausencia de contenido sin revisar en splits asignados;
- hashes duplicados y grupos/clusters cruzados entre splits.

No descarga imagenes ni verifica remotamente que una licencia siga publicada. Esa evidencia debe
capturarse durante la importacion autorizada y queda registrada en el manifiesto.

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

## Tests

```bash
python3 -m unittest discover -s scripts/dag_v3_model/tests -p 'test_*.py'
```

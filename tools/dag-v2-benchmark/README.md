# DAG v2 no-GPU benchmark

Herramienta independiente del navegador y de `:feature-dag2`. Descarga artefactos
con hash obligatorio, construye un corpus acotado desde Wikimedia Commons,
ejecuta inferencia por streaming y simula cascadas sin convertir señales en una
decisión de producto.

## Entorno

```bash
python3 -m venv /tmp/dag-v2-benchmark-venv
/tmp/dag-v2-benchmark-venv/bin/pip install -r tools/dag-v2-benchmark/requirements.lock.txt
```

La caché debe quedar fuera de Git:

```bash
export DAG_V2_BENCHMARK_CACHE=/tmp/dag-v2-benchmark-cache
python tools/dag-v2-benchmark/dag_v2_benchmark.py download-models
python tools/dag-v2-benchmark/dag_v2_benchmark.py build-corpus --limit 12
python tools/dag-v2-benchmark/dag_v2_benchmark.py run --limit 12
python tools/dag-v2-benchmark/dag_v2_benchmark.py summarize
```

Después del gate pequeño, `build-corpus --limit 240` y `run` completan el corpus.
`run` reanuda por `sample_id`; nunca carga el corpus completo en RAM.
`build-corpus --group-id <id>` permite reintentar sólo una fuente sin recorrer
las demás.

## Runner Android aislado

El runner no forma parte de `settings.gradle.kts` del producto, no tiene
Internet ni WebView y deshabilita Release. Primero se exporta un subconjunto:

```bash
python tools/dag-v2-benchmark/dag_v2_benchmark.py export-android --limit 72
DAG_V2_BENCHMARK_CACHE=/tmp/dag-v2-benchmark-cache \
  ./gradlew -p tools/dag-v2-benchmark/android-runner :app:assembleDebug :app:lintDebug
```

`DAG_V2_BENCHMARK_CACHE` es obligatorio para incorporar modelos y corpus. El
APK generado es un benchmark debug local; no se publica ni se integra a DAG.
`parity-signature` calcula las firmas de escritorio del mismo subconjunto.

## Controles

- máximo de batch efectivo: 1;
- modelos verificados antes de abrirse;
- máximo por descarga de corpus: 8 MiB;
- máximo acumulado de corpus: 1 GiB;
- imágenes y resultados detallados fuera del repositorio;
- ninguna URL con query se escribe al manifiesto;
- `cleanup` elimina sólo temporales incompletos del directorio de caché.

## Pruebas

```bash
python -m unittest discover -s tools/dag-v2-benchmark/tests -v
python tools/dag-v2-benchmark/dag_v2_benchmark.py verify-repository
```

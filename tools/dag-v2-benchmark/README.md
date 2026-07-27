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

## Evidencia publicada de 04A

La corrida publicada no vuelve a seleccionar imágenes. El bundle textual
`evidence/04a/` fija las 203 muestras, sus fuentes públicas, bytes esperados,
evidencia neutral, métricas y checksums:

```bash
python tools/dag-v2-benchmark/dag_v2_benchmark.py verify-evidence
python tools/dag-v2-benchmark/dag_v2_benchmark.py \
  --cache /tmp/dag-v2-benchmark-cache fetch-locked-corpus
```

`verify-evidence` no usa red ni descarga modelos o imágenes. Recalcula hashes,
IDs, p50, p95, máximos y porcentajes de cascada. `fetch-locked-corpus` descarga
exclusivamente las URLs fijadas y falla si tamaño o SHA-256 cambiaron; nunca
elige reemplazos silenciosamente.

## Evaluación humana 04B

El plan de revisión, el split y el subconjunto diagnóstico se verifican sin
imágenes ni red:

```bash
python tools/dag-v2-benchmark/dag_v2_policy_eval.py verify-review-plan
python tools/dag-v2-benchmark/dag_v2_policy_eval.py verify-results
```

El revisor Android autónomo se construye con las 203 imágenes ya verificadas:

```bash
DAG_V2_BENCHMARK_CACHE=/tmp/dag-v2-benchmark-cache \
  ./gradlew -p tools/dag-v2-benchmark/policy-reviewer \
  :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

No tiene WebView ni permiso de Internet. Para CI, `DAG_V2_REVIEWER_FIXTURE=1`
genera tres PNG mínimos desde texto y no descarga el corpus.

Después de completar las 203 decisiones:

```bash
python tools/dag-v2-benchmark/dag_v2_policy_eval.py \
  validate-label-export /ruta/dag-v2-evaluation-04b.jsonl
python tools/dag-v2-benchmark/dag_v2_policy_eval.py \
  --cache /tmp/dag-v2-benchmark-cache extract-signals \
  --output /tmp/dag-v2-04b-signals.jsonl
python tools/dag-v2-benchmark/dag_v2_policy_eval.py \
  select-policy --labels /ruta/dag-v2-evaluation-04b.jsonl \
  --signals /tmp/dag-v2-04b-signals.jsonl --output /tmp/dag-v2-04b-seal.json
python tools/dag-v2-benchmark/dag_v2_policy_eval.py \
  open-test --labels /ruta/dag-v2-evaluation-04b.jsonl \
  --signals /tmp/dag-v2-04b-signals.jsonl \
  --seal /tmp/dag-v2-04b-seal.json --output /tmp/dag-v2-04b-test.json
```

`open-test` rechaza una apertura previa al sello o una segunda apertura sobre
el mismo destino. El bundle final versionado bajo `evidence/04b/` conserva la
exportación humana original, su normalización, señales, sello, apertura única,
medición Android y checksums. `verify-results` recalcula hashes, IDs, conteos,
latencias y métricas del test sin descargar imágenes ni modelos.

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
Antes de copiar assets, `verify-android-assets` valida tamaño y SHA-256 de los
tres modelos, manifiesto bloqueado, 72 IDs y cada imagen.

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
python tools/dag-v2-benchmark/dag_v2_benchmark.py verify-evidence
python tools/dag-v2-benchmark/dag_v2_policy_eval.py verify-review-plan
python tools/dag-v2-benchmark/dag_v2_policy_eval.py verify-results
bash scripts/test_android_ci_scope.sh
```

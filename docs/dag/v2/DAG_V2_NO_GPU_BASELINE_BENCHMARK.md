# Diseño del benchmark visual sin GPU paga

## Alcance

`DAG-V2-NO-GPU-BASELINE-BENCHMARK-04A` compara evidencia de modelos
preentrenados sin integrarlos al producto. `:feature-dag2`, WebView,
Calibración DEV, Supabase y DAG v1 no forman parte del runtime del benchmark.
El proveedor visual visible de DAG v2 continúa con una única salida: `Hide`.

## Herramienta independiente

`tools/dag-v2-benchmark/` contiene:

- lock de modelos con fuente, revisión, tamaño, hash y licencias;
- descargador HTTPS/Git con verificación obligatoria y caché externa;
- constructor de corpus acotado por licencia, tamaño y cantidad;
- inferencia por streaming, batch 1 y salida neutral JSONL;
- reanudación por `sample_id`;
- simulador de tres cascadas;
- exportación por hard-link de un subconjunto Android sin duplicar bytes;
- verificadores de ausencia de binarios/imágenes en Git y de que el provider
  de producto sigue en `Hide`;
- limpieza limitada a `.partial` y huérfanos de la caché creada por la
  herramienta.

El entorno reproducible usa Python 3.9, ONNX Runtime 1.19.2, MediaPipe
0.10.21, NumPy 1.26.4, Pillow 11.3.0 y psutil 7.0.0. No usa Docker, API paga,
servicio de inferencia ni GPU alquilada.

## Contrato neutral

Cada registro JSONL implementa el equivalente a `DagV2VisualEvidence`:

- `adult_score`;
- `person_count`;
- `female_evidence`, que queda `null` porque ningún candidato medido la
  justifica;
- `pose_confidence`;
- `shoulder_evidence`, `elbow_evidence`, `knee_evidence`;
- `body_skin_ratio`, `face_skin_ratio`, `clothing_ratio`,
  `accessory_ratio`;
- `uncertainty`;
- `source_model_versions`;
- latencia separada por etapa.

No estima edad exacta ni produce `Show`/`Allow`.

## Corpus

El corpus se obtiene mediante la API oficial de Wikimedia Commons desde una
lista cerrada de categorías en `corpus_spec.json`. No se recorren páginas
comerciales ni se baja un dataset completo. Cada imagen:

- tiene página fuente HTTPS y licencia individual CC0, dominio público,
  CC BY o CC BY-SA;
- se mide sobre el thumbnail oficial de hasta 1024 px;
- conserva SHA-256, dHash64, dimensiones, bytes, categoría, etiqueta fuente,
  transformación, estado de revisión y cluster visual;
- se deduplica por SHA-256;
- queda sólo en la caché externa.

La selección exacta publicada está bloqueada en
`tools/dag-v2-benchmark/evidence/04a/corpus.lock.jsonl`. Incluye título,
`page_id`, página fuente, URL pública exacta de descarga, licencia canónica,
autor, hashes, dimensiones y transformación. `fetch-locked-corpus` reconstruye
exclusivamente esa selección y falla ante cualquier byte distinto; no consulta
categorías ni elige reemplazos. `build-corpus` queda sólo para candidatos
futuros.

Las licencias se aceptan mediante un mapa canónico cerrado, nunca por
substring. Sólo admite CC0, dominio público/PD Mark y versiones explícitas de
CC BY o CC BY-SA, incluidas las cuatro variantes jurisdiccionales presentes y
auditadas (`DE`, `FR`, `IT`). NC, NC-SA, ND, fair use, copyright, vacío o
nombres desconocidos fallan.

Las categorías de Commons son evidencia de procedencia, no verdad de la
política. `review_status=source_category_unreviewed`; por eso el ticket no
declara precisión, falsos permisos ni falsos bloqueos definitivos. No se
usaron imágenes privadas, muestras de Supabase, cookies, formularios, texto de
página ni URLs con tokens.

Límites: 200–500 únicas para la corrida completa, 8 MiB por archivo, 1 GiB de
corpus y 4 GiB de temporales totales. El gate obligatorio usa 12 muestras antes
de escalar.

## Cascadas simuladas

1. **Todos siempre:** NSFW, pose y segmentación en cada muestra. Es referencia
   de costo, no propuesta.
2. **Adaptativa:** NSFW siempre; pose sólo si el score adulto no corta la
   evaluación; segmentación si aparece pose o el score adulto queda en zona
   intermedia. Los límites de simulación no son thresholds de producto.
3. **Conservadora mínima:** sólo NSFW como señal; todo lo no demostrablemente
   cubierto permanece `Hide`. Esta alternativa no satisface por sí sola la
   política de modestia.

La caché se simula sólo en el informe. No se agregó una caché activa al
navegador.

## Referencia DAG v1

El modelo profesional archivado se extrae directamente del objeto
`486c564...` y se ejecuta con preprocesamiento independiente. No se importan
`DagProfessionalImageClassifier`, `DagImageClassifier`, thresholds,
calibración ni estados de v1. Como ese mismo artefacto es el candidato A, sirve
como referencia de latencia y señal adulta, pero no existe todavía un modelo
v2 distinto con el cual afirmar superioridad.

## Gate y recursos

El gate de 12 imágenes debe completar sin error, mismatch, duplicado ni
crecimiento no acotado antes de la corrida completa. Batch 1 evita cargar el
corpus en RAM. Se registran carga de modelos, p50/p95/máximo, RSS, disco y
tiempo total.

MediaPipe informó un contexto Metal disponible, pero los adapters seleccionados
se configuraron expresamente con CPU/XNNPACK. El benchmark no declara MPS
porque ONNX Runtime y MediaPipe Tasks de este harness no exponen un backend MPS
comparable. macOS no ofrece una lectura térmica pública estable sin permisos
adicionales; se registra esa ausencia, no se inventa temperatura.

Al cierre, la caché de modelos, corpus, subconjunto y resultados ocupó 144 MB;
el entorno Python temporal ocupó 791 MB. Incluso sumando builds Gradle, el uso
se mantuvo ampliamente por debajo del tope de 4 GB. La corrida Mac completa
registró 771.751.936 bytes de pico RSS.

## Android

La herramienta exporta 50–100 muestras por hard-link y contiene un proyecto
Android autónomo bajo `tools/dag-v2-benchmark/android-runner/`. No está incluido
por `settings.gradle.kts` del producto, no tiene permiso de Internet ni WebView,
deshabilita su variante Release y sólo genera un APK debug local con package
propio. Modelos e imágenes se incorporan desde la caché externa durante el
build y no quedan versionados.

El runner mide cargas fría y caliente, CPU, NNAPI para ONNX, delegate GPU para
MediaPipe cuando está disponible, p50/p95/máximo, PSS, estado térmico, fallos y
paridad. La paridad usa métricas tolerantes por muestra además de firmas
exactas, porque decodificación y resize pueden variar entre Pillow y Android.
La máscara de segmentación sólo valida cantidad de respuestas; no afirma
paridad pixel a pixel.

El APK se instaló en el SM-A235M autorizado mediante actualización in-place.
No se desinstaló, no se borraron datos y no se modificó `app-user` ni
`:feature-dag2`.

Antes de copiar assets, Gradle ejecuta `verify-android-assets`: compara tamaño
y SHA-256 de los tres modelos con `models.lock.json`, exige coincidencia
byte-a-byte del manifiesto bloqueado del subconjunto y verifica los 72
`sample_id`, nombres, tamaños y hashes de imagen.

## CI económico

El workflow `DAG v2 Benchmark Evidence` ejecuta unitarios, verificación del
repositorio, evidencia y scope sin descargar corpus/modelos ni configurar
Android. `android_ci_scope.sh` clasifica cambios exclusivos de
`tools/dag-v2-benchmark/**` y de su documentación como `none`; cambios reales
en App Usuario, Admin o módulos compartidos conservan sus builds Android.

## Rollback

Eliminar la rama retira herramienta, lock e informes. La caché externa puede
limpiarse con `cleanup` y luego borrarse mediante una acción local explícita.
El runner debug local puede quedar instalado sin afectar DAG; retirarlo del
teléfono, si se desea, requiere una acción local explícita separada. No hay
modelo activo, dato remoto, migración ni estado de navegador que revertir.

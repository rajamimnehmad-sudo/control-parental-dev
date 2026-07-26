# Resultados del baseline visual sin GPU paga

Fecha: 2026-07-26.

## Resultado ejecutivo

Decisión: **NO-GO para integrar esta cascada o iniciar 04B**.

Los tres candidatos producen evidencia útil y completaron todas las muestras,
pero no demuestran la política visual de DAG v2. En el SM-A235M, NSFW, pose y
segmentación suman aproximadamente 1.169 ms p50 si se ejecutan siempre. La
simulación adaptativa todavía requiere pose en 98,5% y segmentación en 68,5% de
las imágenes. Además, no existe señal justificada de apariencia femenina,
edad incierta, ropa ajustada o transparencia. El corpus no tiene revisión
humana de política, por lo que no se pueden calcular falsos permisos,
falsos bloqueos ni superioridad frente a DAG v1.

## Corpus y reproducibilidad

- 203 imágenes únicas de Wikimedia Commons, 70.142.418 bytes.
- Licencias admitidas: CC0, dominio público, CC BY y CC BY-SA.
- Manifiesto externo:
  `$DAG_V2_BENCHMARK_CACHE/corpus/manifest.jsonl`.
- SHA-256 del manifiesto:
  `4b20c3418c2ae2e9ec84781e4013dc2dc8ce4909bffb78df36e50b42715e505f`.
- Evidencia JSONL SHA-256:
  `5d043a03e317a75b73bae15022d8ea964cf3d8e699968f0ba5c972e5ef3a24f3`.
- Resumen JSON SHA-256:
  `8f73ba7d2d8af1d9f7703853569cfeaae7e742b18a56ae48650ed8f42a35b919`.

El corpus cubre 40 categorías fuente, incluidas personas, grupos, vestimenta,
arte, tiendas, controles sin persona y dos fuentes pequeñas. Intentó obtener
casos borrosos, pero Wikimedia no incorporó ninguno que superara todas las
validaciones. `review_status=source_category_unreviewed`: las categorías
describen procedencia y no son etiquetas humanas de `Show`/`Hide`. No se usaron
imágenes privadas ni las cuatro muestras remotas.

## Mac Apple M2, 8 GB

Runtime: macOS arm64, Python 3.9.6, ONNX Runtime 1.19.2 CPU y MediaPipe Tasks
0.10.21 CPU/XNNPACK, batch 1 y streaming.

| Etapa | Carga | p50/imagen | p95/imagen | Máximo |
|---|---:|---:|---:|---:|
| Marqo NSFW ONNX | 34,48 ms | 36,79 ms | 38,91 ms | 82,35 ms |
| Pose Lite | 119,85 ms | 19,77 ms | 44,30 ms | 47,52 ms |
| Selfie Multiclass | 16,63 ms | 129,12 ms | 144,53 ms | 246,05 ms |

La corrida completa procesó 203/203, sin reanudados ni respuestas faltantes,
en 39,82 segundos. Pico RSS: 771.751.936 bytes; máximo `ru_maxrss`:
788.889.600 bytes. Los modelos suman 28.852.165 bytes. MediaPipe informó un
contexto Metal interno, pero los delegates de inferencia medidos fueron CPU;
el harness no expuso un backend MPS comparable. No se inventó una lectura
térmica de macOS.

## Samsung SM-A235M

Dispositivo: SM-A235M, serial `R58T34V31AE`, Android 14/API 34. Runner
independiente, sin Internet, sin WebView, no incluido en el Gradle del producto
y con variante Release deshabilitada.

- Subconjunto: 72 imágenes, round-robin por categoría.
- APK local: 170.878.133 bytes.
- APK SHA-256:
  `3608258117ce7a4198a4988e260ca721b4a488971c9220f2ef7be36b60321e53`.
- Instalación: actualización in-place; no hubo desinstalación ni borrado.

| Etapa | Carga fría | Carga caliente | p50 | p95 | Máximo |
|---|---:|---:|---:|---:|---:|
| Marqo NSFW CPU | 749,18 ms | 437,05 ms | 245,01 ms | 339,63 ms | 398,75 ms |
| Pose Lite CPU | 650,72 ms | 375,22 ms | 139,13 ms | 332,79 ms | 408,40 ms |
| Selfie Multiclass CPU | 202,52 ms | 173,72 ms | 784,79 ms | 848,55 ms | 898,65 ms |
| Marqo NSFW NNAPI | 2.389,64 ms | no medido | 1.199,78 ms | 1.360,62 ms | 1.378,05 ms |

NNAPI fue aproximadamente 4,9 veces más lento que CPU para Marqo. El delegate
GPU de MediaPipe falló de forma cerrada con `MediaPipeException`; no se
extrapolaron métricas GPU. Hubo 72/72 respuestas de segmentación, cero crash,
cero ANR y cero `renderer_gone`. PSS al finalizar: 336.189 KB; máximo observado
durante las corridas acotadas: 336.189 KB. La última corrida pasó de 24,9 °C a
25,0 °C; el conjunto de corridas de validación pasó de 24,0 °C a 25,0 °C.

Paridad de escritorio/teléfono:

- score adulto: MAE 0,008715; diferencia absoluta máxima 0,060191;
- 3/72 scores coinciden al tercer decimal;
- conteo aproximado de pose coincide en 71/72;
- segmentación devuelve respuesta en 72/72;
- no se implementó paridad pixel a pixel de la máscara.

Las diferencias provienen del decode/resize y los runtimes de plataforma. La
evidencia es comparable, pero no bit-exacta. Log sanitizado SHA-256:
`c21cbaf81d73f40b4c90340892b7cf3915a527fafe651e50b50c4e8185118944`.

## Cascadas simuladas en Mac

| Estrategia | Pose | Segmentación | p50 total | p95 total | Máximo |
|---|---:|---:|---:|---:|---:|
| Todos siempre | 100% | 100% | 186,62 ms | 216,89 ms | 350,49 ms |
| Adaptativa simulada | 98,52% | 68,47% | 183,41 ms | 213,37 ms | 267,27 ms |
| Conservadora mínima | 0% | 0% | 36,79 ms | 38,91 ms | 82,35 ms |

La estrategia adaptativa usa límites experimentales, no thresholds de
producto. Su ahorro es insuficiente porque casi todas las muestras alcanzan
pose y más de dos tercios alcanzan segmentación. La alternativa mínima es
rápida, pero NSFW solo no aplica la política de modestia; todo caso no cubierto
debe seguir en `Hide`. No se implementó caché.

## Comparación con DAG v1

Marqo es el mismo artefacto profesional archivado de v1, ejecutado desde el
objeto Git por un harness independiente. La comparación demuestra su costo,
no una mejora de v2. No se importaron clases, thresholds ni calibración v1.
Como no existe una decisión v2 diferente ni etiquetas humanas, no hay
desacuerdos de decisión honestamente medibles.

## Respuestas de decisión

1. **¿Hay una cascada con posibilidad demostrada de superar v1?** No todavía.
   Agrega señales, pero no demuestra seguridad y es lenta en el teléfono.
2. **¿Modelos exactos?** Marqo NSFW, Pose Landmarker Lite y Selfie Multiclass
   quedan sólo como baselines reproducibles, no seleccionados para producto.
3. **¿Descartados?** SCHP por licencia de pesos/artefacto móvil no confirmados;
   PP-HumanSeg por segmentar persona/fondo sin resolver prendas.
4. **¿Etapa demasiado lenta universalmente?** Selfie Multiclass: 784,79 ms p50
   en SM-A235M.
5. **¿Porcentaje estimado?** La simulación actual envía 98,52% a pose y 68,47%
   a segmentación; no es una cascada suficientemente selectiva.
6. **¿Problemas abiertos?** Apariencia femenina, menor claramente pequeño,
   edad incierta, grupos completos, transparencia, ajuste corporal, tipos de
   prenda y máscaras más precisas.
7. **¿Alcanza una política pequeña en CPU?** Puede combinar señales existentes,
   pero no puede crear las señales ausentes; sin etiquetas humanas no puede
   validarse.
8. **¿Hace falta entrenamiento visual?** Probablemente ajuste o un modelo
   específico para las señales ausentes, pero este ticket no autoriza ni
   justifica entrenarlo aún.
9. **¿Siguiente ticket?** Proponer
   `DAG-V2-TARGETED-SIGNAL-AND-LABELED-POLICY-EVALUATION-04B`, limitado a
   etiquetas humanas y evaluación de las señales faltantes. No iniciar
   `DECISION-CACHE` ni entrenamiento hasta superar esa puerta.

## Garantías

`DagV2FailClosedImageDecisionProvider` continúa devolviendo únicamente `Hide`.
No se integró ningún modelo, caché o decisión al navegador. No hubo GPU paga,
entrenamiento, API comercial, Supabase, Production, publicación, cambio de
versión, borrado ni inicio de 04B.

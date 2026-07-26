# Candidatos visuales sin GPU paga para DAG v2

Fecha de verificación: 2026-07-26. Este inventario registra artefactos para un
benchmark independiente. No los integra al navegador, no define thresholds de
producto y no crea una ruta `Allow`.

La fuente ejecutable está fijada en
`tools/dag-v2-benchmark/models.lock.json`. El descargador rechaza cualquier
tamaño o SHA-256 diferente y conserva la caché fuera de Git.

## Candidatos medidos

### Marqo NSFW ViT-Tiny 384 — referencia adulta

- Autor: Marqo.
- Fuente primaria y model card:
  `https://huggingface.co/Marqo/nsfw-image-detection-384`.
- Artefacto medido: conversión ONNX cuantizada ya archivada en DAG v1, extraída
  como objeto Git de
  `486c564be62ab336cfc815b223343b9419370f14:app-user/src/main/assets/dag/nsfw_marqo_vit_tiny_384.onnx`.
- SHA-256:
  `0366969ece89f252f05fad2c730d6c7e3373000e1ff43e4cfab8425aad94405b`.
- Tamaño: 6.702.582 bytes. Formato: ONNX, entrada
  `1×3×384×384` float32. Runtime medido: ONNX Runtime 1.19.2 CPU.
- Licencia declarada del modelo/pesos: Apache-2.0. Código de referencia:
  Apache-2.0. Uso comercial: permitido por esa licencia.
- Restricciones: el dataset de entrenamiento de Marqo es propietario; su
  métrica publicada corresponde a ese dataset y no demuestra la política
  Glosh. Sólo distingue dos clases NSFW/SFW; no resuelve edad, ropa ajustada,
  transparencia ni límites corporales.
- Rol: referencia upstream independiente. El harness no importa clases,
  thresholds ni calibración de DAG v1.

### MediaPipe Pose Landmarker Lite float16/1 — pose

- Autor: Google.
- Fuente primaria:
  `https://developers.google.com/edge/mediapipe/solutions/vision/pose_landmarker`.
- Archivo oficial inmutable:
  `https://storage.googleapis.com/mediapipe-models/pose_landmarker/pose_landmarker_lite/float16/1/pose_landmarker_lite.task`.
- SHA-256:
  `59929e1d1ee95287735ddd833b19cf4ac46d29bc7afddbbf6753c459690d574a`.
- Tamaño: 5.777.746 bytes. Formato: bundle MediaPipe con detector
  `224×224×3` y landmarker `256×256×3`, float16. Runtime medido:
  MediaPipe Tasks 0.10.21 CPU.
- Licencia declarada por el model card de BlazePose GHUM 3D: Apache-2.0.
  Código MediaPipe: Apache-2.0. Uso comercial: permitido por esa licencia.
- Señales: 33 landmarks, incluidos hombros, codos, caderas y rodillas;
  presencia y visibilidad por punto; número de poses devueltas como aproximación
  a personas.
- Restricciones: API Preview; el model card la orienta a una persona centrada,
  declara vigilancia e identificación fuera de alcance y advierte degradación
  con grupos, distancia, orientación y oclusión. `personCount` no debe tratarse
  como conteo exhaustivo.

### MediaPipe Selfie Multiclass 256 float32/1 — piel y ropa

- Autor: Google.
- Fuente primaria:
  `https://developers.google.com/edge/mediapipe/solutions/vision/image_segmenter`.
- Archivo oficial inmutable:
  `https://storage.googleapis.com/mediapipe-models/image_segmenter/selfie_multiclass_256x256/float32/1/selfie_multiclass_256x256.tflite`.
- SHA-256:
  `c6748b1253a99067ef71f7e26ca71096cd449baefa8f101900ea23016507e0e0`.
- Tamaño: 16.371.837 bytes. Formato: TFLite float32,
  `256×256×3`. Runtime medido: MediaPipe Tasks 0.10.21 CPU.
- Licencia declarada por el model card Multiclass Segmentation: Apache-2.0.
  Código MediaPipe: Apache-2.0. Uso comercial: permitido por esa licencia.
- Salidas: fondo, pelo, piel corporal, piel facial, ropa y otros/accesorios.
- Restricciones: API Preview; el model card no promete máscaras pixel-perfect,
  excluye vigilancia e identificación y registra degradación con poca luz,
  contraluz, ruido, movimiento u oclusión. No diferencia tipos de prenda.

Los tres artefactos seleccionados suman 28.852.165 bytes. Ninguno se agregó a
Git: los dos MediaPipe viven sólo en caché y la referencia Marqo se lee del
objeto ya archivado sin crear otra copia versionada.

## Candidatos investigados y descartados

### SCHP / Self-Correction Human Parsing

El repositorio original
`https://github.com/GoGoDuck912/Self-Correction-Human-Parsing` publica código
MIT y checkpoints para LIP/ATR/Pascal-Person-Part. No se seleccionó porque:

- el repositorio no declara separadamente y de forma inequívoca la licencia de
  cada checkpoint y de sus datasets de entrenamiento;
- no existe un artefacto móvil oficial mantenido con revisión, tamaño y hash
  fijables equivalente al requerido;
- el runtime PyTorch/checkpoint no es una base razonable para el SM-A235M sin
  una conversión y una validación adicionales.

Por lo tanto, el uso comercial de esos pesos para Glosh queda **no confirmado**.

### PP-HumanSeg v2

PaddleSeg publica código Apache-2.0, pesos y modelos móviles de segmentación de
persona. No se seleccionó como parsing de ropa porque su salida principal
persona/fondo duplica parte de la etapa MediaPipe y no distingue prendas,
transparencia ni ajuste corporal. Incorporarlo no cubriría la función D.

## Conclusión de licencias

Marqo, Pose Lite y Selfie Multiclass tienen licencia de pesos declarada y
compatible para este benchmark. SCHP queda fuera hasta que un ticket futuro
demuestre licencia de pesos, artefacto móvil oficial, hash y costo real. Una
publicación en GitHub o Hugging Face por sí sola no alcanza.

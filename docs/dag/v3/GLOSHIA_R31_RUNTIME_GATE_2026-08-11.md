# GloshIA R3.1: compuerta de señal completa y límite de miniaturas

Fecha: 2026-08-11

Estado: compuerta fuerte validada físicamente en DAG 199; reparación de
miniaturas pendiente de un modelo candidato

Final sealed: cerrado

## Objetivo

Diagnosticar por separado dos fallos observados en Google:

1. una imagen completa con señal GloshIA muy alta podía terminar permitida si
   sus recortes regionales no corroboraban la señal;
2. miniaturas diminutas podían perder casi toda la señal visual aunque la misma
   foto grande se filtrara correctamente.

No se reemplazó R3.1, no se modificó el umbral binario `0,40`, no se agregaron
excepciones por sitio, URL, dominio o dispositivo y no se abrió ningún examen
sellado.

## Señal completa fuerte

Se puntuaron con el ONNX R3.1 oficial y cuatro regiones diagnósticas las 536
decisiones binarias ya revisadas por el propietario en gate 27 y round 30. Se
reprodujo exactamente la política Android previa y se barrió una compuerta que
evita el veto regional sólo cuando la imagen completa supera un valor muy alto.

| Política | falsos permisos | falsos filtros | correcciones | regresiones |
| --- | ---: | ---: | ---: | ---: |
| previa | 86 | 21 | — | — |
| señal completa `>= 0,90` | 65 | 25 | 21 | 4 |
| señal completa `>= 0,95` | 73 | 21 | 13 | 0 |
| señal completa `>= 0,98` | 78 | 21 | 8 | 0 |

Se eligió `0,95`: corrige 13 permisos erróneos sin agregar falsos filtros en el
examen principal y termina la decisión tras una sola inferencia. Por debajo de
ese valor se conserva íntegramente la corroboración regional existente.

La primera prueba física Diagnostic reprodujo además una miniatura de bikini
`137x137` permitida con señal completa `0,9453`. Se evaluó una compuerta
compacta experimental `0,94`, pero la repetición recibió otra variante de
`62x82` con señales `0,7847-0,9166` que continuó visible. Bajar hasta esa banda
bloquearía demasiadas imágenes permitidas. La compuerta compacta se retiró por
no resolver el problema general.

El flavor LAB conserva su modo de difuminado fuerte; la salida diagnóstica
agrega el motivo `full_strong` para distinguir esta decisión de
`regional_strong` y `regional_consensus`.

## Miniaturas diminutas

La sesión aislada de Google confirmó miniaturas `54x54` permitidas con señales
completas de `0,0494`, `0,0779`, `0,0841` y `0,2720`. Ningún ajuste moderado de
umbral puede convertir esos valores en bloqueos sin afectar contenido permitido.

Se contrastaron:

- 162 grupos difíciles revisados por el propietario;
- cinco representaciones a 54 px: lineal, nearest, Lanczos, sharpen y recorte
  central;
- inferencia regional forzada;
- las 536 decisiones revisadas, degradadas deliberadamente a `54x54` para medir
  sobrebloqueo fuera del pool dirigido.

Forzar regiones no corrigió permisos. Combinar las cinco representaciones a
umbral `0,70` recuperó 17 positivos pero agregó 22 falsos filtros; a `0,90`
recuperó 6 y agregó 4. Una inferencia lineal adicional a `0,95` recuperó sólo un
positivo. El costo y el riesgo no justifican integrar ninguna variante.

Sobre las 536 decisiones degradadas, un umbral especial `0,90` dejó tres
bloqueos sobre decisiones históricas `allow`; endurecer por tamaño tampoco puede
distinguir una foto relevante de avatares, productos, logos o controles.

Conclusión: el segundo fallo es una limitación de representación de R3.1. Las
variantes físicas de una misma miniatura oscilaron desde `0,7847` hasta
`0,9453`, y otras miniaturas cayeron a `0,0494-0,2720`. No se agrega una regla
por tamaño, un segundo pase costoso ni un heurístico de piel. La reparación
profesional continúa siendo un modelo compacto mejor entrenado con pares
original/miniatura y negativos cercanos, bajo los gates ya congelados.

## Implementación y validación

- DAG Browser: `199` / `0.70.03-dev`.
- Diagnostic: versionCode `2`, paquete aislado
  `com.contentfilter.dagbrowser.diagnostic.dev`.
- Modelo oficial: R3.1, SHA-256
  `c8b64af8092d3718c58736a511c996d0d443dacf3eaa74620b1e5af439a3cd48`.
- APK DEV SHA-256:
  `d3a299431d2a24a18bbf5a9a863d71c14800847f7f22df63802ae46d8607b191`.
- APK Diagnostic SHA-256:
  `ac00e20f8adb27eccb733463a58b2adefab57b32382c2d47c10ae2947cb69413`.
- `testDevDebugUnitTest`: correcto.
- `testDiagnosticDebugUnitTest`: correcto.
- `ktlintCheck`: correcto.
- Lint Vital DEV/Diagnostic: correcto.
- `assembleDevDebug` y `assembleDiagnosticDebug`: correctos.

Los dos APK se instalaron con `adb install -r` en el SM-S908E sin borrar datos.
El propietario comprobó en DAG normal que la mujer de una tienda de bikinis que
antes quedaba visible ya no aparece. La sesión aislada de Google registró 112
decisiones, sin crash ni ANR, cola p95 de 1 ms y 2/57 cuadros lentos. La captura
rápida confirmó que las sugerencias circulares visibles sí corresponden a
decisiones `allow` del modelo; no son recursos omitidos por el interceptor.

En orientación vertical, Mimo llegó al final de la página, abrió su menú después
del desplazamiento y obtuvo `page_visible=1562 ms`, `page_analysis_ready=2062
ms`, cola p95 de 31 ms y 1/42 cuadros lentos. Cheeky llegó al pie con
`page_visible=2656 ms`, `page_analysis_ready=8307 ms`, cola p95 de 43 ms y 2/42
cuadros lentos. No hubo crash ni ANR.

Frávega cargó tarjetas e imágenes seguras y no produjo crash ni ANR, pero su
modal propio de ubicación interceptó los gestos automáticos. Esa muestra no se
declara válida para certificar desplazamiento o carrusel. La compuerta fuerte ya
tiene aceptación física independiente; la matriz completa de rendimiento sigue
abierta por ese bloqueo externo.

El laboratorio también dejó de calcular gestos con el tamaño físico natural:
ahora usa el `logicalFrame` activo y registra `gesture_display_size`, evitando
muestras falsas cuando el teléfono cambia entre vertical y horizontal.

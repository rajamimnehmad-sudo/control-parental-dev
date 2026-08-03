# GloshIA Visual R2.1 — ORT Android harness

Ticket: `GLOSHIA-R2.1-ORT-ANDROID-HARNESS-12`

Fecha: 2026-08-03

Resultado: **NO-GO para canary o integración**

## Alcance y controles

Se usó exclusivamente el candidato INT8 dinámico de `EXPORT-GATE-11`. No se
reentrenó, no se volvió a cuantizar, no se abrió `final_sealed` y no se cambió
R1, DAG, Android productivo, APK productivo, Supabase ni la política. El
harness fue una aplicación Android aislada, sin permisos de red y fuera del
runtime de DAG. Sólo se conservaron el JSON de métricas, hashes y tensores
congelados sin imágenes.

El examen completo contiene 119 entradas congeladas: 47 `validation` y 72
`frozen_test`. La entrada es la misma serialización de tensores utilizada en el
examen local; `final_sealed` no fue incluido.

## Artefactos y contrato

| Elemento | Valor |
|---|---|
| Candidato | `r2.1-candidate-02-int8.onnx` |
| SHA-256 candidato | `c212d005db271bebfb3fb80aade4c056334e0f4f07f2f1543976050f8c8afa3c` |
| Tamaño candidato | 8.756.367 bytes |
| R1 | `tinyclip-bounded-finetune-r1-int8.onnx` |
| SHA-256 R1 | `2d52bd9e5eb4cd448cb0d64a784b2ee6f761ad20e890c57b898fd7991d29a9ee` |
| Tamaño R1 | 8.735.186 bytes |
| Runtime Android | `onnxruntime-android:1.27.0` |
| Ejecución | CPU local, 2 hilos intra-op, 1 inter-op |
| Entrada | `pixel_values`, `float32`, `[1,3,224,224]` |
| Salida | `filter_probability` |
| Umbral | 0,4; sin cambios |
| Tensores de evaluación | SHA-256 `f0e63a2b688ae81f8759dd1d027e6484f14483d781b5dfa3589acdf53b97b9b3` |

## Ejecución directa en Android

Dispositivo comprobado: Samsung A23 `SM-A235M`, serial `R58T34V31AE`, Android
14 / API 34. La sesión del candidato abrió y ejecutó inferencias repetidas
sin error de `ConvInteger`, con salidas finitas y cierre correcto de sesión.
Esto confirma compatibilidad práctica de `ConvInteger` para este modelo con
ORT Android 1.27.0 CPU en este dispositivo; no se extrapola a otros runtimes o
dispositivos.

| Prueba | p50 | p95 | máximo | PSS pico | temperatura batería* |
|---|---:|---:|---:|---:|---:|
| Candidato, smoke 30 | 132,56 ms | 144,39 ms | 157,75 ms | 143.778 KB | 29,6 °C |
| R1, smoke 30 | 131,47 ms | 147,89 ms | 165,06 ms | 120.542 KB | 29,5 °C |
| Candidato, 119 muestras | 132,50 ms | 137,72 ms | 146,60 ms | 123.836 KB | 29,5 °C |
| R1, 119 muestras | 131,72 ms | 137,44 ms | 149,82 ms | 122.903 KB | 29,5 °C |

\* Se midió temperatura de batería como proxy térmico disponible por Android,
no temperatura interna de CPU. El candidato fue 21.181 bytes mayor que R1
(+0,24 %). La memoria PSS pico de la evaluación fue 933 KB mayor (+0,76 %).

## Resultados de decisiones

La matriz usa las decisiones humanas binarias del tensor congelado; `doubt` no
participa. `allow_as_filter` es falso filtro y `filter_as_allow` es falso
permiso.

| Modelo | allow→allow | allow→filter | filter→allow | filter→filter | falsos permisos | falsos filtros | exactitud |
|---|---:|---:|---:|---:|---:|---:|---:|
| R2.1 dinámico Android | 90 | 11 | 0 | 18 | 0/18 (0 %) | 11/101 (10,89 %) | 108/119 (90,76 %) |
| R1 Android | 59 | 42 | 0 | 18 | 0/18 (0 %) | 42/101 (41,58 %) | 77/119 (64,71 %) |

Desglose del candidato:

- `validation`: 34/39 allow correctos, 5/39 falsos filtros, 0/8 falsos
  permisos; exactitud 42/47 (89,36 %).
- `frozen_test`: 56/62 allow correctos, 6/62 falsos filtros, 0/10 falsos
  permisos; exactitud 66/72 (91,67 %).
- recall de `filter`: 18/18 (100 %); precisión de `filter`: 18/29
  (62,07 %).
- recall de `allow`: 90/101 (89,11 %); precisión de `allow`: 90/90
  (100 %).
- balanced accuracy: (89,11 % + 100 %) / 2 = **94,55 %**.
- F1 de la clase `filter`: **76,60 %**.

El candidato produjo exactamente un desacuerdo de decisión frente a la salida
FP32 congelada, sobre 119 muestras (0,84 %):

| sample_id | split | humano | FP32 | Android INT8 |
|---|---|---|---|---|
| `wikimedia:159527259` | `frozen_test` | allow | filter (0,404408) | allow (0,384544) |

No es un falso permiso humano nuevo: la etiqueta humana es `allow`. Sí es una
divergencia de decisión respecto de FP32 en dirección menos restrictiva. El
harness fijó tolerancia de equivalencia de decisión en cero; por eso el gate
queda `NO-GO` aunque `ConvInteger` sí funcione y no aparezcan falsos permisos
humanos.

Las probabilidades no fueron bit a bit equivalentes: 116/119 (97,48 %) se
desviaron más de 0,0001; diferencia absoluta media 0,012970 y máxima 0,079234.
No hubo NaN/Inf en ninguna de las 119 inferencias.

## Criterio final

**NO-GO.** El runtime Android CPU 1.27.0 sí puede abrir y ejecutar este
`ConvInteger` dinámico en el A23, pero el candidato no conserva exactamente
las decisiones FP32 bajo la tolerancia definida de cero desacuerdos. No se
instala en DAG ni se solicita un canary. R1 continúa siendo el modelo oficial;
no se modificaron sus pesos ni su hash.

`final_sealed` permaneció cerrado. El resultado crudo reproducible está en
`.codex-tmp/gloshia-r2-1-ort-android-harness-20260803/result.json` y su
SHA-256 es
`07682d0c9b36949d99b2b84f39f5a489da9bc4233e34f435fc754fc0c98843db`.
El APK de laboratorio fue `app-debug.apk`, 48.410.953 bytes, SHA-256
`3f278bf7d9d6e5f80c8291b7e97245f2a281628b3c8b956f8f081b5ec6f51267`; se
desinstala al cerrar el ticket y no se publica.

# Evaluación dirigida y etiquetada DAG v2 04B

Estado: evaluación cerrada en **NO-GO**. Las 203 decisiones humanas fueron
validadas, la política se selló con exploración/validación y el conjunto de
prueba congelado se abrió una sola vez. No comenzó 04C.

## Invariantes

- Todo vive bajo `tools/dag-v2-benchmark/`; no entra a App Usuario, App Admin
  ni `:feature-dag2`.
- `DagV2FailClosedImageDecisionProvider` continúa devolviendo sólo `Hide`.
- No existe ruta de imagen aprobada, caché activa, modelo nuevo ni threshold de
  producto.
- No se usan las cuatro muestras privadas de Supabase DEV.
- No hay GPU paga, API paga, entrenamiento visual, publicación ni Production.

## Corpus y revisión ciega

04B consume exactamente las 203 muestras públicas bloqueadas por 04A. El
revisor sólo recibe `sample_id`, archivo local, SHA-256, posición y el bit que
indica si corresponde pedir motivos. No recibe categorías fuente, URLs,
scores, señales ni predicciones.

`review-order.lock.jsonl` fija un orden ciego determinista. Cada imagen se
valida por SHA-256 antes de decodificarse y mostrarse. El APK autónomo:

- no tiene WebView ni permiso de Internet;
- muestra una imagen por vez;
- no preselecciona ni completa etiquetas;
- ofrece `✓ Mostrar`, `× Ocultar`, `? No estoy seguro`, `Atrás` y `Deshacer`;
- guarda progreso atómicamente bajo `noBackupFilesDir`;
- conserva una única decisión vigente y un historial local de correcciones;
- reanuda después de cerrar sin perder progreso.

## Motivos diagnósticos

El subconjunto diagnóstico quedó congelado antes de las etiquetas: 60
muestras, cinco por estrato y sin compartir cluster perceptual:

- adulto o explícito;
- ropa interior o traje de baño;
- escote o pecho;
- abdomen;
- hombro o axila;
- codo;
- rodilla;
- ropa ajustada;
- transparencia;
- edad incierta;
- grupos;
- otro.

La decisión primaria se persiste antes de mostrar los motivos. En una muestra
diagnóstica marcada `Ocultar` o `No estoy seguro` se exige uno o más motivos.
Fuera del subconjunto, los motivos son opcionales.

## Exportación

`Exportar evaluación 04B` sólo se habilita con todas las decisiones vigentes.
Genera `dag-v2-evaluation-04b.jsonl` de forma atómica, calcula SHA-256 y permite
copiarlo a Downloads. También se puede recuperar mediante `run-as`/ADB sin
root ni borrado.

El contrato permite únicamente:

- `sample_id`;
- `decision`;
- `reasons`;
- `review_number`;
- `reviewed_at`;
- `policy_version=DAG_STRICT_MODESTY_V1`;
- `reviewer_version=dag-v2-policy-reviewer-04b-1`.

No contiene imagen, URL, categoría, modelo, score, cookies ni identidad.

La exportación recibida tiene SHA-256
`a672cbf5436e492732f7df645551472e6f738760d4959bba2d3d4fef11d68174`,
47.884 bytes, 203 IDs únicos y una decisión vigente por ID. Se detectó que la
versión instalada serializaba `reasons` como una cadena cerrada (`"[knee]"`)
en vez de un array JSON. Las decisiones no estaban afectadas: el vocabulario
cerrado permitió normalizar las 203 filas sin ambigüedad. Se conserva el
archivo original y la versión normalizada, cuyo SHA-256 es
`b85d5d7780fe94a6c7b1502aca1b06edf7d7b726e13d2352985ac90a66ffd74c`.
El exportador quedó corregido y testeado para producir arrays reales.

Conteos humanos:

| Decisión | Cantidad |
| --- | ---: |
| Mostrar | 117 |
| Ocultar | 76 |
| No estoy seguro | 10 |

Los diez `unsure` fueron excluidos de entrenamiento, validación y prueba
concluyente. Los motivos registrados fueron: escote/pecho 32, adulto/explícito
20, ropa ajustada 11, ropa interior/traje de baño 10, codo 10, hombro/axila 8,
rodilla 8, transparencia 5, abdomen 2, grupos 1 y otro 1.

## Split congelado

`split.lock.jsonl` se creó antes de cualquier etiqueta:

| Partición | Muestras |
| --- | ---: |
| Exploratoria | 122 |
| Validación | 41 |
| Prueba congelada | 40 |

El agrupamiento une SHA-256 iguales y dHash64 con distancia Hamming menor o
igual a cinco antes de asignar particiones. Ningún cluster puede cruzar el
split. La selección de reglas y parámetros usa sólo exploratoria y validación.
`open-test` exige un sello de parámetros e inputs, sólo crea un resultado una
vez y rechaza una segunda apertura.

## Señales dirigidas

`dag_v2_policy_eval.py` calcula fuera del producto:

- score adulto y pose en CPU;
- número aproximado de poses, confianza y coordenadas normalizadas de hombros,
  codos, caderas y rodillas;
- piel local YCbCr, HSV y Lab alrededor de hombros, codos y rodillas;
- piel posible en torso, relación facial/corporal y referencia relativa a
  rostro cuando existe pose suficiente;
- tamaño, calidad, desenfoque e incertidumbre por desacuerdo;
- latencias separadas de adulto, pose, señales locales, política, secuencial y
  adulto/pose en paralelo con concurrencia dos.

No persiste identidad, raza, tono de piel, rostro ni embedding personal.
Selfie Multiclass sólo puede ejecutarse mediante `compare-teacher`, sobre un
máximo duro de 20/203 muestras (9,85%), como comparación offline; nunca es una
etapa universal de runtime.

El runner Android autónomo agrega `TargetedSignalActivity`. Mide el mismo
camino CPU sin NNAPI, registra p50/p95/máximo, PSS, CPU, temperatura y fallos,
y guarda evidencia sanitizada privada. No ejecuta segmentación universal.

## Políticas pequeñas y test sellado

Después del gate humano se compararon:

- reglas deterministas;
- regresión logística regularizada;
- árbol de profundidad máxima tres;
- boosting acotado a doce stumps.

`unsure` se excluye. La selección usa exploratoria y validación y sella
features, pesos/reglas, thresholds y hashes. Recién entonces `open-test` calcula
una vez precisión de Show, recall de Hide, falsos permisos críticos, falsos
bloqueos, cobertura, incertidumbre, Wilson 95%, motivos, categorías y
estabilidad de clusters.

En validación se seleccionaron reglas deterministas
`max(adult, shoulder, elbow, knee, torso)` con `show_max=0,05` y
`hide_min=0,55`. No hubo falsos permisos en validación, pero la incertidumbre
fue 55,26%. El sello se creó antes de consultar el test; fija SHA-256 de
etiquetas, señales y split, y registra `test_opened=false`.

## Apertura única y resultado

El test congelado se abrió una sola vez después del sello. De sus 40 muestras,
39 tenían etiqueta concluyente:

| Métrica de prueba | Resultado | Puerta |
| --- | ---: | --- |
| Falsos permisos críticos | 0 | cumple |
| Precisión de Show | 0% (sin predicciones Show) | no cumple |
| Recall de Hide | 62,5% | no cumple |
| IC 95% de recall Hide | 38,64%–81,52% | informativo |
| Falsos bloqueos | 3 | informativo |
| Cobertura concluyente | 33,33% | no cumple |
| Incertidumbre | 66,67% | no cumple |
| Segmentación pesada requerida | 66,67% | no cumple |

La ausencia de falsos permisos se obtuvo a costa de no aprobar ninguna imagen
en el test. Por eso `show_precision=0` no representa una tasa observada de
aciertos de Show sino ausencia de cobertura Show.

**Decisión: NO-GO.** Las señales baratas no distinguen con fiabilidad
apariencia femenina, edad, ajuste, transparencia y semántica de prendas. No
corresponde iniciar la caché 04C. El siguiente ticket recomendado —sólo
propuesto— es `DAG-V2-DIRECTED-VISUAL-MODEL-DATASET-05`: ampliar y equilibrar
un corpus público con etiquetas humanas y entrenar/evaluar un único modelo
visual dirigido y pequeño. Este ticket no lo inicia.

Las puertas son experimentales y no constituyen una afirmación de precisión de
producción; el test concluyente tiene sólo 39 muestras.

## Evidencia física del gate

- Dispositivo: Samsung SM-A235M `R58T34V31AE`, Android 14/API 34.
- Instalación: `adb install -r`; no se desinstaló ni borró información.
- APK local: 70.229.187 bytes.
- SHA-256:
  `c8d577a2fda2e8a80174d39a19b856a38f4319b5ed53efb69f7d9df9dd231f67`.
- Assets: 203 imágenes, 70.142.418 bytes, verificadas contra el lock.
- Permisos: sin `INTERNET`; sin WebView.
- Estado después de cerrar y reabrir: 203 pendientes, cero decisiones,
  cero revisiones y cero auditoría.
- La pantalla muestra botones grandes, progreso, Atrás, Deshacer y exportación
  deshabilitada hasta completar.
- No hubo crash ni ANR durante apertura y reanudación.

## Rendimiento final

En Mac, sobre las 203 imágenes y CPU: adulto 51,96/63,56 ms p50/p95, pose
19,99/42,66 ms, señales locales 93,32/221,75 ms, política
0,006/0,008 ms y ruta paralela 157,34/280,05 ms. PSS pico: 815.841.280 bytes.

En SM-A235M `R58T34V31AE`, Android 14/API 34, se instalaron in-place el runner
autónomo y sus 72 muestras bloqueadas. El APK local no versionado tiene
SHA-256 `15ce4cbbad039baf2f6b9f4811388f1b4cf1535de3ac1b72d993eafa8c655b36`.
Resultados CPU:

| Etapa | p50 | p95 | máximo |
| --- | ---: | ---: | ---: |
| Adulto | 248,57 ms | 316,34 ms | 347,32 ms |
| Pose | 141,14 ms | 333,72 ms | 351,44 ms |
| Señales locales | 3,59 ms | 18,58 ms | 30,56 ms |
| Política | 0,012 ms | 0,018 ms | 0,022 ms |
| Secuencial | 406,26 ms | 606,50 ms | 637,14 ms |
| Adulto + pose paralelo | 309,44 ms | 472,24 ms | 483,32 ms |

La ruta paralela cumple la puerta experimental de 350/600 ms. PSS fue 183.520
KiB, CPU 21.619 ms, estado térmico 0, batería 25,6→25,8 °C, 72/72 muestras y
cero fallos. No hubo crash ni ANR. No se usó NNAPI, GPU paga ni segmentación
universal.

El primer intento del runner reveló un defecto general: cerrar `MPImage`
reciclaba el bitmap que todavía necesitaban las señales locales. El runner
ahora entrega a pose una copia aislada y libera sólo esa copia. La repetición
completa produjo las cifras anteriores.

## Evidencia reproducible

`tools/dag-v2-benchmark/evidence/04b/` versiona sólo JSON/JSONL sanitizado:
exportación original y normalizada, señales Mac, comparación docente limitada
a 20 muestras, sello, resultado de apertura única, medición Android, resumen y
checksums. No contiene imágenes, modelos, APK, URLs ni datos privados.

```bash
python tools/dag-v2-benchmark/dag_v2_policy_eval.py verify-results
```

El comando recalcula hashes, conjunto de 203 IDs, conteos, p50/p95/máximos,
inputs del sello y todas las métricas del test. El CI especializado lo ejecuta
sin red.

## Rollback

Dejar de usar la rama y el APK retira toda la superficie experimental. El
progreso privado puede conservarse para reanudar. No se elimina ningún dato
automáticamente. DAG v1 y el navegador DAG v2 permanecen sin cambios.

# Evaluación dirigida y etiquetada DAG v2 04B

Estado: gate humano abierto. El revisor local está instalado y conserva
`203 decisiones humanas pendientes`. No se abrió el conjunto de prueba, no se
eligió política y no comenzó 04C.

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

Después del gate humano se compararán:

- reglas deterministas;
- regresión logística regularizada;
- árbol de profundidad máxima tres;
- boosting acotado a doce stumps.

`unsure` se excluye. La selección usa exploratoria y validación y sella
features, pesos/reglas, thresholds y hashes. Recién entonces `open-test` calcula
una vez precisión de Show, recall de Hide, falsos permisos críticos, falsos
bloqueos, cobertura, incertidumbre, Wilson 95%, motivos, categorías y
estabilidad de clusters.

Las puertas son experimentales, no afirmaciones de producción.

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

## Reanudación después del gate

1. El revisor humano completa las 203 decisiones en el teléfono.
2. Pulsa `Exportar evaluación 04B`.
3. Codex recupera el JSONL sin borrar datos y verifica esquema y SHA-256.
4. Se extraen señales; no se consultan métricas de prueba.
5. Se selecciona y sella la política con exploratoria/validación.
6. Se abre una sola vez la prueba congelada.
7. Se miden Mac y SM-A235M y se declara `GO`, `GO CONDICIONAL` o `NO-GO`.

Ningún resultado inicia automáticamente 04C.

## Rollback

Dejar de usar la rama y el APK retira toda la superficie experimental. El
progreso privado puede conservarse para reanudar. No se elimina ningún dato
automáticamente. DAG v1 y el navegador DAG v2 permanecen sin cambios.

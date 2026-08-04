# GLOSHIA-R3-COMMERCIAL-HARD-NEGATIVES-26 — Diagnóstico piloto

Fecha: 2026-08-04  
Estado: diagnóstico y revisión humana completados; no autorizado entrenamiento
Baseline: `main` / `0fbf003`; DAG 107 confirmado en S22  
Examen sellado: no abierto

## Alcance

Se auditó el área GloshIA y se preparó un lote privado, local e independiente
para estudiar los falsos filtros de R3 en banners y escenas comerciales. No se
modificaron DAG, Android, APK, ONNX, umbrales, política, Supabase ni
Production. No se usaron excepciones por sitio, URL o dominio.

Los tres casos reportados en Mimo no pudieron trazarse a IDs de los manifiestos
privados disponibles sin inventar una correspondencia. El lote, por tanto, es
un conjunto de análogos generales y no una sustitución de esos tres casos.

## Procedencia y control de calidad

La fuente fue Wikimedia Commons, consultada mediante la API pública con
licencia declarada `CC BY`, `CC BY-SA`, `CC0` o dominio público. Se conservaron
URL de página y asset, SHA-256, dimensiones, MIME, hash perceptual canónico,
cluster opaco, categoría, estado de uso y timestamps. No se copiaron a la
manifestación final nombres de autores, perfiles, comentarios o ubicaciones
personales.

La licencia de origen no se interpreta como autorización de entrenamiento:
las 40 muestras evaluables conservan `training_rights_uncertain` y
`training_authorized: false`. Todas están marcadas `internal_evaluation_ok`.

Resumen de adquisición:

| Estado | Cantidad |
|---|---:|
| Candidatos descargados inicialmente | 57 |
| Cuarentena por actualidad primaria no demostrada | 25 |
| Duplicado perceptual detectado en auditoría canónica | 1 |
| Muestras evaluables actuales | 40 |
| Clusters entre las evaluables | 26 |
| `final_sealed` | 0; permanece cerrado |

Las 40 evaluables se distribuyen en `retail_catalog_fashion` 19,
`commercial_banner_people` 15, `promotional_text_graphic` 5 y
`payment_commerce_control` 1. Las licencias declaradas son BY 12, BY-SA 13,
CC0 13 y PDM 2. El espacio de este laboratorio privado es aproximadamente
20,8 MB incluyendo imágenes, manifiestos, predicciones y trazas.

El inventario inicial devolvió archivos históricos para consultas modernas.
Se descartaron cuando el título o la fecha primaria no demostraban una captura
2023–2026. Esto es una limitación de las fuentes públicas y no se cuenta como
evidencia del modelo.

## Evaluación automática previa

Se ejecutaron R1 y R3 sobre exactamente las mismas 40 muestras, con el runner
del laboratorio: RGB, letterbox gris, normalización CLIP, entrada 224×224,
vistas regionales y política DAG-36. Ambos modelos abrieron y produjeron
salidas finitas sin error.

| Modelo | SHA-256 | Tamaño | Allow | Filter | p50 laboratorio |
|---|---|---:|---:|---:|---:|
| R1 | `2d52bd9e5eb4cd448cb0d64a784b2ee6f761ad20e890c57b898fd7991d29a9ee` | 8.735.186 B | 30/40 | 10/40 | 63,77 ms |
| R3 híbrido | `0aaa1700182623173c41d233bd0e072cce2b2880aca14430d9f9af43fa2c44a8` | 10.469.698 B | 31/40 | 9/40 | 56,72 ms |

Este p50 es benchmark de Mac/laboratorio, no Android. Las trazas regionales
de R3 muestran 9/40 imágenes con probabilidad global ≥0,40, 2/40 con alguna
región ≥0,45, 2/40 con alguna región ≥0,50 y 1/40 con alguna región ≥0,70.
Esto sugiere que la composición regional puede participar en algunos casos,
pero no demuestra todavía la causa de los tres banners de Mimo.

R1 y R3 cambiaron de acción en 9/40 muestras. Esas nueve pasaron primero a la
cola `desacuerdos`; las colas restantes fueron `posibles falsos filtros` 5,
`muestra aleatoria` 3 y `resto` 23.

## Resultado de la revisión humana

Se revisaron 40/40 muestras: 36 `allow`, 3 `filter` y 1 `doubt`. La duda quedó
fuera de toda métrica binaria. Sobre las 39 decisiones binarias, R1 y R3
obtuvieron exactamente la misma matriz:

| Modelo | filter correcto | falso permiso | falso filtro | allow correcto |
|---|---:|---:|---:|---:|
| R1 | 3/3 | 0/3 | 6/36 | 30/36 |
| R3 | 3/3 | 0/3 | 6/36 | 30/36 |

Resultado binario para ambos modelos: accuracy `33/39 (84,62 %)`, balanced
accuracy `55/60 (91,67 %)`, precisión de filter `3/9 (33,33 %)`, recall de
filter `3/3 (100 %)`, precisión de allow `30/30 (100 %)`, recall de allow
`30/36 (83,33 %)`. F1 de filter: `6/12 (50,00 %)`. PR-AUC no se usa como
criterio decisivo porque sólo hay `3/39` positivos humanos en este piloto.

El desglose muestra el desplazamiento del error, no una mejora global:

| Categoría | Binarias | R1 falsos filtros | R3 falsos filtros | R3 falsos permisos |
|---|---:|---:|---:|---:|
| `commercial_banner_people` | 15 | 2/15 | 0/15 | 0/15 |
| `promotional_text_graphic` | 4 | 2/4 | 0/4 | 0/4 |
| `retail_catalog_fashion` | 19 | 2/19 | 6/19 | 0/19 |
| `payment_commerce_control` | 1 | 0/1 | 0/1 | 0/1 |

Las seis imágenes que R3 filtra de más están en `retail_catalog_fashion`;
varias corresponden a maniquíes o escenas de catálogo permitibles. Por tanto,
este lote no demuestra que R3 sea mejor que R1: demuestra que R3 reduce
algunos filtros excesivos comerciales, pero conserva el mismo riesgo total y
lo concentra en catálogos de moda.

## Revisión

Servidor local de loopback:

`http://127.0.0.1:8774/`

La web usa R3 sólo como predicción posterior a la decisión humana. Mantiene
allow/filter/doubt, deshacer, anterior/siguiente, autoguardado,
importación/exportación JSON y colas. No usa API externa, Supabase ni LAN.

Los artefactos privados están en:

`.codex-tmp/gloshia-r3-commercial-hard-negatives-20260804/pilot-41/`

Incluyen `manifest.jsonl`, `predictions.jsonl`, predicciones separadas de R1,
trazas regionales, `review-queues.json`, `diagnostic-report.json`,
`hash-audit.json`, `quarantine.jsonl` y `contact-sheets/`.

## Decisión y siguiente paso

Estado actual: `GO` para cerrar el diagnóstico; `NO-GO` para entrenamiento o
reemplazo de modelo. El resultado no autoriza bajar el umbral, crear
excepciones por sitio ni modificar DAG.

Siguiente paso recomendado: preparar un lote independiente y balanceado de
casos modernos de catálogo/maniquí y ejemplos realmente filtrables, agrupado
por campaña, producto y serie. Este lote actual tiene sólo `3/39` positivos
humanos y no debe reutilizarse como examen independiente ni alimentar un
entrenamiento sin autorización explícita. Después de esa nueva revisión se
podrá proponer un ticket de entrenamiento con holdout independiente. No
ajustar umbrales ni abrir `final_sealed` en esta fase.

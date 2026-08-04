# GLOSHIA-R3-COMMERCIAL-HARD-NEGATIVES-26 — Diagnóstico piloto

Fecha: 2026-08-04  
Estado: diagnóstico y revisión humana pendiente; no autorizado entrenamiento  
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

R1 y R3 cambiaron de acción en 9/40 muestras. Esas nueve pasan primero a la
cola `desacuerdos`; las colas restantes son `posibles falsos filtros` 5,
`muestra aleatoria` 3 y `resto` 23. `doubt` permanece vacío hasta que el
propietario decida explícitamente.

No se calculó matriz de confusión, precisión, recall, balanced accuracy, F1 ni
PR-AUC: todavía hay 0/40 decisiones humanas. Las decisiones automáticas no se
presentan antes de la revisión.

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

Estado actual: `GO` sólo para revisión humana y diagnóstico de datos;
`NO-GO` para entrenamiento. No hay base honesta para afirmar que R3 tiene
falsos filtros en las 40 muestras hasta recibir las etiquetas del propietario.

Siguiente paso recomendado: completar las 40 decisiones binarias o `doubt`,
concentrándose primero en `desacuerdos`, `posibles falsos filtros` y una
muestra aleatoria. Después generar la matriz de confusión R1/R3, el desglose
por categoría y la traza global/regional. Recién si aparecen suficientes
falsos filtros comerciales confirmados, proponer un ticket de entrenamiento
con un holdout independiente. No ajustar umbrales ni abrir `final_sealed` en
esta fase.

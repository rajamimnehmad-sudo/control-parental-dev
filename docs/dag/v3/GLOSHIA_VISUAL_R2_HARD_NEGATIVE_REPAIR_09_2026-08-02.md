# GloshIA Visual R2 Hard-Negative Repair 09

Fecha: 2026-08-02  
Estado: detenido en revisión humana ciega; no se entrenó ningún modelo.

## Alcance y baseline

- Rama y commit de inicio: `main`, `2c7e4f2`.
- Modelo oficial intacto: GloshIA Visual R1,
  `tinyclip-bounded-finetune-r1-int8.onnx`.
- SHA-256 R1:
  `2d52bd9e5eb4cd448cb0d64a784b2ee6f761ad20e890c57b898fd7991d29a9ee`.
- `final_sealed`: cerrado y no consultado.
- No se modificaron Android, DAG, APK, Supabase, umbrales ni política.

## Diagnóstico que motivó el lote

El falso permiso nuevo de R2 identificado en el examen previo pertenece al
cluster de una fotografía pública de un equipo adulto de ciclismo en un podio.
R1 la clasificó `filter` con probabilidad `0.451530`; R2 FP32 la clasificó
`allow` con `0.362594`. La decisión humana `filter` es coherente con el
criterio vigente por la combinación de uniformes deportivos ajustados, shorts,
brazos y piernas descubiertos, grupo y pose de evento.

La comparación de imágenes relacionadas de la misma campaña/sesión mostró que
R2 todavía filtra otras variantes similares (probabilidades `0.451232`,
`0.614576` y `0.531554`), mientras que el falso permiso cruza hacia `allow`.
La causa probable es una frontera inestable ante el patrón conjunto de grupo,
ciclismo, uniformes rosados y contexto de podio; no es evidencia para crear
una excepción por imagen, sitio o campaña. Por eso se preparó un lote
independiente y no se reutilizó el caso como única evidencia.

## Lote piloto preparado

Se descargaron 50 imágenes públicas de Wikimedia Commons exclusivamente para
evaluación privada local:

- 25 en el estrato de muestreo `hard_negative_filter_like`.
- 25 en `hard_negative_allow_like`.
- 50 clusters únicos; no se cuentan variantes de la misma serie como evidencias
  independientes.
- 50 SHA-256, 50 hashes perceptuales y 50 identificadores únicos.
- 14.600.370 bytes en imágenes.
- Cada fila tiene URL pública, origen, hashes, dimensiones, MIME, tamaño,
  fecha, categoría de muestreo, cluster y estado de uso.
- No se conservan campos de creador, título, usuario, comentarios, perfil ni
  ubicación personal. La URL pública se conserva sólo como procedencia
  requerida.
- Estado de uso: `internal_evaluation_ok`; derechos de entrenamiento:
  `training_rights_uncertain`; no hay autorización de entrenamiento todavía.

La deduplicación se comparó contra los manifiestos de evaluación no sellados
disponibles: 394 SHA-256, 465 hashes dHash y 465 hashes pHash. También se
rechazaron candidatos por identificador repetido, serie/cluster, SHA exacto o
proximidad perceptual. Los dos estratos son únicamente una estrategia de
muestreo: no son etiquetas humanas y no se usan como verdad.

## Revisión humana

El lote está en revisión ciega, con 0 de 50 decisiones. La interfaz local es:

`http://127.0.0.1:8770/`

Controles: deslizar derecha `allow`, izquierda `filter`, botón separado
`doubt`, deshacer, anterior/siguiente, autoguardado e importar/exportar JSON.
La predicción no se entrega antes de la decisión; después la interfaz muestra
predicción, probabilidad y coincidencia/desacuerdo. El servidor escucha sólo en
loopback, no usa API ni Supabase y confirma que el examen sellado sigue cerrado.

## Medición provisional

R1 fue ejecutado con el mismo runner y el mismo ONNX oficial sobre las 50
imágenes, sin usar la salida como etiqueta humana:

- 24 decisiones del modelo `allow`; 26 `filter`.
- p50 de laboratorio: 68,162 ms.
- p95 de laboratorio: 282,216 ms.
- máximo: 304,172 ms.
- media: 131,285 ms.
- media de inferencias: 2,22 por imagen.
- Matriz de confusión y falsos permisos/filtros: aún no disponibles, porque
  no hay decisiones humanas binarias.

No se crearon splits de entrenamiento ni `frozen_test`; hacerlo antes de la
revisión introduciría decisiones inventadas. El siguiente paso autorizado es
completar la revisión humana, manteniendo `doubt` separado, y recién después
formar splits agrupados y ejecutar el gate contra R1.

## Archivos de implementación

- `scripts/dag_v3_model/r2_hard_negative_repair.py`: adquisición, sanitización,
  deduplicación y manifiesto reanudable.
- `scripts/dag_v3_model/tests/test_r2_hard_negative_repair.py`: prueba de
  ausencia de metadatos personales y separación de etiquetas humanas.
- `.codex-tmp/gloshia-r2-hard-negative-repair-20260802-v3/`: corpus privado,
  predicciones y revisión; fuera de Git.

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

La revisión ciega quedó completa: 50 de 50 decisiones, con 26 `filter`, 23
`allow` y 1 `doubt`. La duda permanece fuera de toda matriz binaria. La interfaz
local es:

`http://127.0.0.1:8770/`

Controles: deslizar derecha `allow`, izquierda `filter`, botón separado
`doubt`, deshacer, anterior/siguiente, autoguardado e importar/exportar JSON.
La predicción no se entrega antes de la decisión; después la interfaz muestra
predicción, probabilidad y coincidencia/desacuerdo. El servidor escucha sólo en
loopback, no usa API ni Supabase y confirma que el examen sellado sigue cerrado.

## Medición sobre la ronda completa

R1 fue ejecutado con el mismo runner y el mismo ONNX oficial sobre las 50
imágenes, sin usar la salida como etiqueta humana:

- 24 decisiones del modelo `allow`; 26 `filter`.
- p50 de laboratorio: 68,162 ms.
- p95 de laboratorio: 282,216 ms.
- máximo: 304,172 ms.
- media: 131,285 ms.
- media de inferencias: 2,22 por imagen.
- Sobre las 49 decisiones binarias: matriz `allow_as_allow=16`,
  `allow_as_filter=7`, `filter_as_allow=8`, `filter_as_filter=18`.
- Accuracy: `34/49 = 69,39%`; balanced accuracy: `69,40%`.
- Falsos permisos: `8/26 = 30,77%` de las imágenes filtrables.
- Falsos filtros: `7/23 = 30,43%` de las imágenes permitibles.
- Precisión filter: `18/25 = 72,00%`; recall filter: `18/26 = 69,23%`.
- Precisión allow: `16/23 = 69,57%`; recall allow: `16/23 = 69,57%`.
- F1 filter: `70,59%`; F1 allow: `68,09%`; PR-AUC filter: `79,46%`.
- El estrato `hard_negative_filter_like` concentró `7/18` falsos permisos y
  `2/7` falsos filtros; el estrato `hard_negative_allow_like` tuvo `1/8`
  falsos permisos y `5/16` falsos filtros.
- En verticales el resultado fue especialmente débil: `5/11` falsos permisos
  y `6/12` falsos filtros, balanced accuracy `52,27%`.

El resultado confirma que las imágenes difíciles aportan información y que el
falso permiso de R2 no debe repararse con una excepción por muestra. También
confirma que los estratos de adquisición no eran etiquetas: la matriz usa
exclusivamente las decisiones humanas.

Todavía no se crearon los nuevos splits de entrenamiento ni `frozen_test`. El
siguiente paso autorizado es formar splits agrupados con las 49 binarias,
mantener la duda fuera, comprobar contaminación y ejecutar un piloto de R2.1
sin mirar el nuevo `frozen_test` para seleccionar configuración.

## Gate de autorización de entrenamiento

La auditoría del manifiesto muestra `0/50` filas con
`training_authorized=true` y `0/50` con `training_rights_clear`. Las 50 están
marcadas `internal_evaluation_ok` y `training_rights_uncertain`, por lo que se
pueden medir en privado pero no se pueden incorporar al entrenamiento bajo el
contrato de este ticket. La duda tampoco puede entrar por ser `allow` o
`filter`.

El preparador de splits falla cerrado al encontrar la primera fila binaria sin
autorización; no generó splits ni permitió iniciar entrenamiento. El falso
permiso original autorizado para diagnóstico (`wikimedia:167902476`) permanece
fuera de esta corrida hasta que exista un split autorizado coherente.

Resultado de este ticket en el estado actual: `NO-GO / BLOQUEADO POR DATOS`, no
por una medición negativa de R2.1. R1 permanece oficial e intacto; no se abrió
`final_sealed`, no se cambiaron pesos, umbrales, Android ni DAG. Para continuar
se necesita autorizar explícitamente el uso de las 49 binarias (o entregar un
subconjunto con derechos/autorización claros) y mantener la separación del
holdout.

## Archivos de implementación

- `scripts/dag_v3_model/r2_hard_negative_repair.py`: adquisición, sanitización,
  deduplicación y manifiesto reanudable.
- `scripts/dag_v3_model/tests/test_r2_hard_negative_repair.py`: prueba de
  ausencia de metadatos personales y separación de etiquetas humanas.
- `.codex-tmp/gloshia-r2-hard-negative-repair-20260802-v3/`: corpus privado,
  predicciones y revisión; fuera de Git.

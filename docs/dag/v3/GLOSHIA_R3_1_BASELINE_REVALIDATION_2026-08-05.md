# GloshIA Visual R3.1 — revalidación del baseline

Fecha: 2026-08-05  
Estado: diagnóstico local completado; no se entrenó ni se cambió el modelo oficial.

## Alcance y corrección de procedencia

El modelo oficial actual es `GloshIA Visual R3.1`, archivo
`tinyclip-r3-head-hybrid-int8.onnx`, SHA-256
`c8b64af8092d3718c58736a511c996d0d443dacf3eaa74620b1e5af439a3cd48`.

El laboratorio Python estaba fijado por error al hash histórico de R3
(`0aaa1700182623173c41d233bd0e072cce2b2880aca14430d9f9af43fa2c44a8`). Se
corrigió la fuente de verdad en `tools/gloshia_lab/cli.py`, se agregó una
prueba que compara el hash contra el asset de DAG y se incorporó `--predictions`
al comando `report` para no sobrescribir resultados históricos.

Por ese motivo, los informes anteriores de esas carpetas que no indican
`c8b64…` se consideran resultados históricos de R3, no métricas del baseline
actual. Esta revalidación volvió a ejecutar R3.1 sobre exactamente los mismos
manifests y decisiones humanas, con `final_sealed` excluido.

## Exámenes utilizados

| Examen | Filas | Allow | Filter | Doubt | Binarias evaluadas |
|---|---:|---:|---:|---:|---:|
| Gate 27 / review-295 | 295 | 194 | 91 | 10 | 285 |
| Round 30 | 255 | 158 | 93 | 4 | 251 |
| Combinado | 550 | 352 | 184 | 14 | 536 |

No se usaron `doubt` en la matriz binaria. Los SHA son únicos dentro de cada
lote y los dos manifests no comparten SHA ni `sample_id` según la auditoría
previa. Ambos lotes son `directed_review`; no se usaron para abrir
`final_sealed`.

## Métricas R3.1 combinadas

| Métrica | Resultado |
|---|---:|
| Matriz | 296 allow→allow; 56 allow→filter; 26 filter→allow; 158 filter→filter |
| Accuracy | 454/536 = 84,70 % |
| Balanced accuracy | 84,98 % |
| Filter precision | 158/214 = 73,83 % |
| Filter recall | 158/184 = 85,87 % |
| Allow precision | 296/322 = 91,93 % |
| Allow recall | 296/352 = 84,09 % |
| F1 filter | 79,40 % |
| F1 allow | 87,83 % |
| Macro F1 | 83,62 % |
| PR-AUC de filter | 89,22 % |

Errores prioritarios: 26 falsos permisos (`filter→allow`, 26/184 filtrables)
y 56 falsos filtros (`allow→filter`, 56/352 permitibles). No se incluyeron
dudas en esos denominadores.

## Desglose por categoría

| Categoría | Binarias | Falsos permisos | Falsos filtros | Lectura |
|---|---:|---:|---:|---|
| commercial_people | 147 | 7/25 | 12/122 | Principal riesgo en personas de catálogo/comerciales |
| modern_clothing | 125 | 5/84 | 11/41 | Buen recall de filter, pero sobre-filtra allow difíciles |
| partial_or_small_subject | 87 | 6/20 | 15/67 | Grupo más débil; recortes y sujetos pequeños |
| sports_and_sensitive | 77 | 3/40 | 9/37 | Confusión entre ropa deportiva y casos filtrables |
| commercial_banner_safe | 37 | 3/5 | 2/32 | La reparación de banners aún no es completa |
| catalog_mannequin_safe | 24 | 0/2 | 6/22 | Maniquíes/catálogos siguen siendo hard negatives |
| groups_public_normal | 15 | 2/3 | 0/12 | Muestra pequeña; no generalizar porcentajes |
| object_controls | 19 | 0/0 | 0/19 | Control seguro, sin falsos filtros |

Los denominadores de la tabla son los casos filtrables para falsos permisos y
los casos permitibles para falsos filtros. Las categorías son estratos de
análisis, no etiquetas automáticas de entrenamiento.

## Comparación contra el R3 histórico en el mismo examen

El R3 histórico (`0aaa…`) produjo 27 falsos permisos y 67 falsos filtros en
las mismas 536 decisiones. R3.1 produce 26 y 56: reduce los falsos filtros en
11 casos, un 16,42 % relativo, y los falsos permisos en 1 caso. Balanced
accuracy pasa de 83,15 % a 84,98 % y PR-AUC de 82,14 % a 89,22 %.

Esto confirma que R3.1 es un baseline mejor que R3 para continuar el trabajo,
pero no demuestra todavía que exista una candidata entrenada apta para Android.
El problema restante está concentrado en casos de personas comerciales,
recortes/sujetos pequeños, ropa moderna y banners/maniquíes seguros.

## Rendimiento y compatibilidad

- Mac, laboratorio Python, Gate 27: p50 145,311 ms; p95 284,067 ms;
  máximo 708,158 ms.
- Mac, laboratorio Python, Round 30: p50 145,274 ms; p95 292,090 ms;
  máximo 783,917 ms.
- S22, prueba directa Android aislada: 22/22 inferencias válidas, sin NaN/Inf;
  repetición final p50 30,92 ms, p95 33,75 ms y PSS 114.405 KiB.
- El test de página UI no produjo una carga válida porque Android envió la
  Activity del fixture a Home; no se inventan métricas de navegación.

La revalidación no modificó pesos, ONNX, umbrales, política, DAG, APK ni
Android productivo.

## Recomendación

`GO` para preparar un ticket de entrenamiento R3.2 dirigido, condicionado a
crear antes splits agrupados independientes y a autorizar explícitamente el
uso de las imágenes. `NO-GO` para reemplazar R3.1 ahora: todavía no existe
una candidata entrenada/exportada/congelada que haya superado los gates de
Android y seguridad.

El siguiente ticket debe priorizar los 56 falsos filtros, sin sacrificar los
26 falsos permisos, y debe comparar un único candidato contra R3.1 con el
mismo examen. `final_sealed` permanece cerrado.

Artefactos privados reproducibles:

- `.codex-tmp/gloshia-r3-balanced-commercial-gate-20260804/review-295/predictions-r31-20260805.jsonl`
- `.codex-tmp/gloshia-r3-balanced-commercial-gate-20260804/review-295/evaluation-r31-20260805.json`
- `.codex-tmp/gloshia-r3-round-30-20260805/review/predictions-r31-20260805.jsonl`
- `.codex-tmp/gloshia-r3-round-30-20260805/review/evaluation-r31-20260805.json`

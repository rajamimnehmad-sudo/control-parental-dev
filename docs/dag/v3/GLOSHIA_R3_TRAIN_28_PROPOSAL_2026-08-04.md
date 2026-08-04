# GLOSHIA-R3-TRAIN-28 — Propuesta de entrenamiento

Fecha: 2026-08-04  
Estado: propuesta; no iniciado  
Baseline principal: GloshIA Visual R3, `tinyclip-r3-head-hybrid-int8.onnx`  
SHA-256 R3: `0aaa1700182623173c41d233bd0e072cce2b2880aca14430d9f9af43fa2c44a8`

## Decisión de entrada

`GLOSHIA-R3-BALANCED-CORPUS-REVIEW-GATE-27` aporta suficiente señal para
proponer este ticket, pero no autoriza a entrenar con sus imágenes. Sus 295
muestras quedan congeladas como examen externo de errores de R3:

- 194 `allow`, 91 `filter` y 10 `doubt`.
- 285 binarias: 13 falsos permisos y 32 falsos filtros de R3.
- `final_sealed` permanece cerrado.
- Las 295 muestras tienen `training_rights_uncertain` y están en
  `directed_review`; no se usan en train, validation ni frozen_test del
  candidato.
- Las 54 imágenes del piloto anterior tampoco se reutilizan para entrenar.

## Pool nuevo requerido

Preparar aproximadamente 400 muestras nuevas, sin reutilizar hashes, pHash,
series, campañas, productos ni clusters del examen gate 27 ni de los splits
históricos. La composición es un objetivo de búsqueda, no una etiqueta
automática:

| Estrato | Objetivo | Uso esperado |
| --- | ---: | --- |
| `allow` difícil comercial y cotidiano | 100 | maniquíes, banners, catálogos, niños vestidos, hombres, grupos, ropa cubierta |
| `allow` difícil de tamaño/calidad | 50 | sujetos pequeños, recortes, miniaturas, baja resolución, fondos complejos |
| `filter` real de pecho, hombros y abdomen | 80 | escotes, transparencia, prendas que dejan zonas relevantes visibles |
| `filter` real de piernas, ropa interior y baño | 70 | rodillas/piernas, ropa interior, traje de baño no explícito |
| `filter` real de pose, ajuste y contexto | 50 | ropa muy ajustada, poses sugerentes, deportes y eventos |
| controles adicionales balanceados | 50 | variación de edades, cantidad de personas, orientación y resolución |

Las decisiones humanas siguen siendo sólo `allow`, `filter` o `doubt`. Las
dudas se excluyen de las métricas binarias y del entrenamiento hasta una
decisión explícita; nunca se convierten silenciosamente.

## Derechos y procedencia

Cada muestra debe conservar URL pública, origen, licencia, fecha de adquisición,
SHA-256, pHash, dimensiones, MIME, bytes, campaña, producto, sesión, serie,
cluster y estado de uso. Sólo entran al entrenamiento:

- `training_rights_clear`, con licencia compatible documentada; o
- autorización explícita del propietario registrada como
  `owner_authorized_private_experiment`, sin declararla `training_rights_clear`.

Las muestras de derechos inciertos pueden permanecer como evaluación privada,
pero no entran al pool de entrenamiento sin una autorización adicional.
No guardar nombres, usuarios, comentarios, perfiles ni ubicación innecesaria.

## Splits y control de contaminación

Crear asignaciones reproducibles con seed registrada, agrupando antes de
separar por sesión, campaña, producto, origen y cluster perceptual:

- `train`: 70 % aproximadamente;
- `validation`: 15 % aproximadamente;
- `frozen_test`: 15 % aproximadamente.

El examen gate 27 queda como referencia externa separada. Una prueba debe
fallar si un SHA-256, pHash cercano, serie, campaña, producto, origen o cluster
cruza splits. No se abre `final_sealed`.

## Entrenamiento propuesto

1. Auditar el pool y congelar manifiesto, splits, seed y etiquetas humanas.
2. Medir R3 sobre validation, frozen_test y el examen externo gate 27 antes de
   entrenar el candidato.
3. Reutilizar la arquitectura pequeña vigente y el mismo contrato: TinyCLIP,
   RGB 224×224, letterbox gris, normalización CLIP, una salida binaria y CPU
   local Android.
4. Entrenar una sola candidata local con class weights o sampling balanceado,
   sin modificar política ni umbral durante la comparación.
5. Ejecutar primero un piloto limitado a 30 minutos y registrar seed,
   configuración, memoria, tiempo y artefactos.
6. Permitir como máximo tres ensayos cortos variando sólo learning rate,
   class weight, épocas o regularización/sampling.
7. Congelar la configuración ganadora por validation; abrir frozen_test una sola
   vez después de congelar pesos, umbral y preprocesamiento.

## Gate obligatorio

La candidata es `NO-GO` si ocurre cualquiera de estas condiciones:

- aparece cualquier falso permiso crítico nuevo frente a R3;
- no reduce claramente los falsos filtros del examen gate 27;
- mejora falsos filtros a costa de más falsos permisos;
- no abre y ejecuta con ONNX Runtime CPU Android 1.27.0;
- cambia decisiones por cuantización, NaN/Inf, tamaño o latencia apreciable;
- requiere una excepción por sitio, URL, dominio, campaña o producto.

Para un posible `GO` deben reportarse, para R3 y la candidata sobre exactamente
los mismos exámenes: matriz de confusión, precisión, recall, balanced accuracy,
PR-AUC, falsos permisos, falsos filtros, desglose por estrato y tamaño,
latencia p50/p95 en S22 y A23, memoria, tamaño y SHA-256. Si no supera todos
los gates, R3 permanece oficial.

## Autorizaciones que faltan

Esta propuesta no autoriza descargar el nuevo pool, entrenar, exportar un
modelo para Android, reemplazar R3, modificar DAG 107, compilar APK, publicar,
hacer push, tocar Supabase/Production ni abrir `final_sealed`. Esas acciones
requieren autorización separada para ejecutar TRAIN-28.

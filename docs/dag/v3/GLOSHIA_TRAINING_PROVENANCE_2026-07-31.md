# GloshIA visual - auditoría de procedencia del 2026-07-31

## Alcance

Esta auditoría reconstruye el conjunto binario usado para el candidato TinyCLIP
vigente y decide si hoy puede repetirse un entrenamiento profesional. No cambia
el modelo, la calibración, DAG, Android ni las APK.

La comprobación usa hashes de los archivos reales y el nuevo comando local
`scripts/dag_v3_model/pilot_training_provenance.py`. No copia fotografías, no
usa red, API paga, Supabase ni GPU.

## Composición reconstruida

El entrenamiento histórico se formó con las rondas humanas 1, 2, 4, 5, 6, 7 y
8. La ronda 3 se mantuvo como validación y la ronda 9 como holdout posterior.
`blur` y `block` se consideran una misma clase binaria `filter`, según la
decisión de política ya registrada.

| Rol | Muestras utilizables | Permitir | Difuminar | Bloquear |
| --- | ---: | ---: | ---: | ---: |
| Entrenamiento | 197 | 89 | 83 | 25 |
| Validación | 21 | 9 | 12 | 0 |
| Holdout de ronda 9 | 4 | 4 | 0 | 0 |

La ronda 3 contiene 22 filas, pero una fue excluida de forma explícita; por eso
la validación efectiva es 21.

## Integridad técnica

- Los 197 IDs de entrenamiento son únicos.
- Los 197 hashes de contenido son únicos.
- Validación y holdout tampoco contienen duplicados internos.
- No existe cruce de ID ni de hash entre entrenamiento, validación y holdout.
- Todos los archivos humanos necesarios siguen presentes y las revisiones son
  completas.

Esta parte está bien construida y explica por qué el modelo resultó útil a
pesar del tamaño pequeño del piloto.

## Gate de procedencia y derechos

Se cruzaron los hashes contra los cinco manifiestos de descarga conservados:

- 124 descargas con hash utilizable en total;
- 39 de las 197 imágenes de entrenamiento pudieron enlazarse por hash;
- 158 no conservan en esos manifiestos una cadena de procedencia suficiente;
- 0 de 197 registra autorización explícita completa para entrenamiento, uso ML
  y derechos de imagen.

Los manifiestos conservados dicen `needs_license_and_visual_review` o
`needs_adult_and_visual_policy_review`. Una licencia abierta declarada no se
eleva automáticamente a autorización: Creative Commons advierte que pueden
subsistir derechos de imagen, privacidad u otros derechos, y Wikimedia asigna
esa comprobación al reutilizador.

El resultado no afirma que las imágenes sean ilegales. Afirma algo más preciso:
la evidencia local actual no alcanza el gate profesional definido por el
proyecto para volver a entrenar o distribuir un modelo derivado.

Decisión automática: `ready_for_retraining: false`.

Bloqueos:

1. `training_provenance_incomplete`;
2. `explicit_training_rights_incomplete`.

## Sondeo dirigido nuevo

Se hizo un sondeo local acotado, fuera de Git, para comprobar que el próximo
lote pueda cubrir errores reales del modelo:

- 30 candidatos CC0 de Wikimedia Commons;
- 20 de variedad general y 10 orientados a mujeres cubiertas;
- 10.312.321 bytes descargados, sin errores ni duplicados;
- el modelo vigente produjo 19 permisos y 11 filtros, sin fallas técnicas;
- aparecieron negativos difíciles útiles, incluidos hombres cubiertos y grupos
  masculinos que el modelo filtra con alta confianza;
- se detectaron también imágenes irrelevantes o sintéticas que deben excluirse.

El sondeo demuestra viabilidad de adquisición, no crea verdad humana ni permiso
de entrenamiento. Las 30 imágenes permanecen `needs_review` y separadas del
banco de evaluación de 1.000 muestras.

## Decisión

No contratar GPU ni reentrenar todavía. Treinta candidatos son insuficientes y
la procedencia del conjunto histórico no satisface el gate actual.

El próximo lote debe construir un conjunto nuevo y dirigido con:

1. autorización explícita por muestra o material propio/encargado;
2. derechos de imagen aprobados o desidentificación verificable;
3. revisión visual antes de etiquetar;
4. IDs, hashes y clusters aislados del banco de evaluación;
5. balance de hombres, personas cubiertas, grupos, deporte y sujetos pequeños;
6. validación independiente congelada antes de entrenar.

Recién cuando ese manifiesto pase el auditor se decide si una corrida M2 basta
o si una GPU externa pequeña aporta valor medible.

## Experimento privado dirigido R1

El propietario autorizó expresamente reutilizar el material sólo para una
prueba local y privada. Esa decisión quedó separada del gate de publicación:
no convierte la procedencia incompleta en autorización comercial.

Se excluyeron tres candidatos irrelevantes o sintéticos —afiche, diapositiva e
ilustración— y se conservaron 27 ejemplos visualmente claros: 13 permisos y 14
filtros. El candidato se afinó en la Mac M2 con:

- 224 muestras de entrenamiento: 197 históricas y 27 dirigidas;
- 21 muestras de validación congelada;
- 4 permisos de holdout no usados para ajustar;
- 8 épocas acotadas, sin GPU externa ni servicio pago.

La validación histórica permaneció en `20/21`, sin falsos permisos. El holdout
de cuatro permisos pasó completo. Sin embargo, la comparación determinante
contra las 100 revisiones recientes —95 binarias y 5 dudas excluidas— no mejoró
el producto:

| Política/modelo | Filtro correcto | Permiso incorrecto | Filtro incorrecto | Permiso correcto | Exactitud |
| --- | ---: | ---: | ---: | ---: | ---: |
| GloshIA vigente | 8 | 2 | 40 | 45 | `0,557895` |
| Candidato R1 FP32 | 9 | 1 | 42 | 43 | `0,547368` |

El candidato reduce un escape, pero agrega dos bloqueos incorrectos y baja la
exactitud total. También resulta más pesado que el artefacto INT8 vigente:
33.220.816 bytes para FP32.

Se probaron dos cuantizaciones dinámicas INT8, por canal y por tensor. Ambas
cambiaron dos decisiones frente al candidato FP32 en sólo 27 casos de control;
ninguna pasó el gate de paridad. Sus artefactos permanecen fuera de Git y no
están aprobados para APK.

Decisión final del experimento: `NO-GO`. No reemplazar el modelo vigente, no
ajustar umbrales sobre estas mismas 100 revisiones y no abrir `final_sealed`.
El valor de este lote es diagnóstico: 27 ejemplos dirigidos todavía no alcanzan
para corregir sobre-filtrado sin pagar seguridad en otros casos.

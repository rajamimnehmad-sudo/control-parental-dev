# GloshIA R2.3 - reparacion de seguridad regional

Fecha: 2026-08-03

Ticket: `GLOSHIA-R2.3-REGIONAL-SAFETY-REPAIR-19`

Resultado: `NO-GO`

## Objetivo

Comprobar si un entrenamiento acotado con escenas grupales nuevas podia
corregir el falso permiso regional observado en el canary R2.2, sin agregar
otro modelo, reglas por sitio, cambios de umbral ni modificaciones en DAG.

## Criterio visual fijado antes del entrenamiento

Una persona diminuta o lejana dentro de una multitud, tipo "buscar a Wally",
no debe provocar el filtrado. El sujeto debe ser visualmente relevante y
distinguible en la imagen. Antes de crear los splits se corrigieron las
preetiquetas de multitudes lejanas a `allow` y un caso fronterizo a `doubt`.

La serie nueva quedo en 57 imagenes independientes:

- 41 `allow`;
- 14 `filter`;
- 2 `doubt`, excluidas del entrenamiento y de la matriz binaria.

Las etiquetas son preetiquetas visuales de Codex para el experimento privado;
no se presentan como una segunda revision humana independiente ni como
`training_rights_clear`.

## Datos y aislamiento

- 57 imagenes y 57 clusters unicos.
- Sin cruces por ID, SHA-256, pHash, grupo ni URL contra los manifiestos
  historicos disponibles.
- Se conservaron sin cambios los splits historicos de R2.2.
- Splits finales: 337 train, 47 validation, 72 frozen_test y 14 en un holdout
  regional nuevo.
- El holdout regional contenia 10 `allow` y 4 `filter`.
- El canary consumido de R2.2 no se uso para seleccionar ni puntuar R2.3.
- `final_sealed` no se abrio.

## Candidatos

Se entrenaron tres variantes pequenas a partir del checkpoint R2.2, cambiando
solamente semilla, intensidad de aprendizaje y peso de clase. Las tres
produjeron la misma matriz en validation: 0/8 falsos permisos y 5/39 falsos
filtros. Se congelo R2.3 B por su PR-AUC y por usar la menor tasa de aprendizaje
entre las variantes empatadas.

- checkpoint: `r2.3-candidate-b.pt`;
- SHA-256:
  `1ae1c820a5cb4cde084ce4d972f67dd6dd2c49b14606bb4b3845fef8e09f2451`;
- umbral: 0,40;
- entrenamiento: 3 epocas, multiplicador LR 0,45 y peso de clase 1,6.

La congelacion se registro antes de abrir el holdout regional.

## Resultados historicos

| Examen | Falsos permisos | Falsos filtros | Balanced accuracy | PR-AUC |
| --- | ---: | ---: | ---: | ---: |
| Validation R2.3 | 0/8 | 5/39 | 93,59 % | 93,24 % |
| Frozen test R2.2 | 0/10 | 11/62 | 91,13 % | 99,09 % |
| Frozen test R2.3 | 0/10 | 12/62 | 90,32 % | 100,00 % |

R2.3 mantuvo la seguridad binaria historica, pero agrego un falso filtro frente
a R2.2.

## Holdout regional nuevo

| Modelo | Aciertos | Falsos permisos | Falsos filtros | Balanced accuracy | PR-AUC |
| --- | ---: | ---: | ---: | ---: | ---: |
| R1 | 11/14 | 0/4 | 3/10 | 85,00 % | 91,67 % |
| R2.2 | 10/14 | 2/4 | 2/10 | 65,00 % | 81,67 % |
| R2.3 | 10/14 | 2/4 | 2/10 | 65,00 % | 79,29 % |

R2.3 repitio exactamente las decisiones de R2.2: no corrigio ninguno de sus
dos falsos permisos ni sus dos falsos filtros. Ademas, su ordenamiento PR-AUC
fue inferior. Los falsos permisos quedaron por debajo del umbral con
probabilidades 0,3495 y 0,2708.

## Decision

R2.3 se descarta. El FP32 ya fallo el gate principal, por lo que no corresponde
crear una exportacion selectiva, ejecutar un harness Android ni hacer canary.
Cuantizar o mover el umbral no resolveria la causa observada.

R1 sigue siendo el unico modelo oficial. DAG 95, sus assets, politica visual y
umbrales no cambiaron. No hubo APK, instalacion, Supabase, publicacion ni push.

## Aprendizaje y siguiente paso recomendado

Agregar mas imagenes completas del mismo tipo no alcanzo. La siguiente
investigacion, si se autoriza por separado, debe enseñar al mismo modelo unico
con pares de imagen completa y recortes regionales relevantes, manteniendo las
multitudes lejanas como `allow`. No implica sumar un segundo modelo en Android.
Necesita datos y un holdout nuevos; no puede reutilizar este examen consumido.

Artefactos privados reproducibles:
`.codex-tmp/gloshia-r23-regional-repair-20260803/`.

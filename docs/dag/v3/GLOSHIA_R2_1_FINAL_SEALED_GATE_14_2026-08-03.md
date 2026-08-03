# GloshIA Visual R2.1 — resultado del examen final sellado

Ticket: `GLOSHIA-R2.1-FINAL-SEALED-GATE-14`

Fecha: 2026-08-03

Resultado: **NO-GO para R2.1; R1 permanece oficial**

Contrato previo inmutable:
`GLOSHIA_R2_1_FINAL_SEALED_GATE_14_FREEZE_2026-08-03.md`.

## Apertura y revisión

El examen se abrió una sola vez después del commit de congelamiento `0f18c86`.
La membresía de 108 muestras coincidió con SHA-256
`61d9548b9e9bb92b693fa9243473b5bc990537eca9d28b6654f6ef053572222b`.
R2.1 y R1 completaron las 108 entradas sin errores ni salidas no finitas.

La revisión humana ciega terminó con:

- 77 `allow`;
- 30 `filter`;
- 1 `doubt`;
- 107 decisiones binarias para la matriz.

El propietario aclaró que algunas escenas —por ejemplo ciclistas— no permitían
distinguir con seguridad la presentación masculina o femenina, y que algunas
decisiones `filter` respondieron a pose sugerente. Esto no se reinterpretó ni
se corrigió después de ver las predicciones: la política evalúa contenido
visible, no identidad, la pose sugerente es un criterio válido y `doubt` queda
fuera de la matriz binaria.

## Resultado comparativo

| Métrica | R1 oficial | R2.1 INT8 | Cambio R2.1 |
|---|---:|---:|---:|
| allow→allow | 53 | 69 | +16 |
| allow→filter | 24 | 8 | -16 |
| filter→allow | 4 | 8 | +4 |
| filter→filter | 26 | 22 | -4 |
| Falsos permisos | 4/30 (13,33 %) | 8/30 (26,67 %) | peor; se duplican |
| Falsos filtros | 24/77 (31,17 %) | 8/77 (10,39 %) | mejora 66,67 % |
| Accuracy | 73,83 % | 85,05 % | +11,21 puntos |
| Balanced accuracy | 77,75 % | 81,47 % | +3,72 puntos |
| Recall de `filter` | 86,67 % | 73,33 % | -13,34 puntos |
| PR-AUC `filter` | 82,26 % | 84,51 % | +2,25 puntos |

R2.1 mejora mucho la sobrecensura, pero lo hace liberando cuatro casos humanos
filtrables adicionales. Esto viola dos gates fijados antes de abrir: cualquier
falso permiso de R2.1 era `NO-GO`, y R2.1 no podía aumentar falsos permisos
frente a R1.

Los ocho falsos permisos R2.1 se concentran en `current_mixed` (2),
`groups_families` (4), `illustration_product` (1) y `partial_crops` (1). No se
crean excepciones por ID, categoría o sitio y no se cambia el umbral usando el
examen final.

## Rendimiento del pipeline local

| Modelo | mediana | p95 | inferencias medias |
|---|---:|---:|---:|
| R1 | 60,36 ms | 276,14 ms | 1,667 |
| R2.1 | 59,72 ms | 274,93 ms | 1,231 |

La diferencia de latencia es pequeña y favorable a R2.1; no compensa la
regresión de seguridad.

## Integridad de evidencia

- Revisiones SHA-256:
  `b21b8fde758c7a9b8d6f7cfc6edb3e44d95fdeb3ad2f3edcb100b787b3c7c713`.
- Predicciones R2.1 SHA-256:
  `87e549cd11b7c9fbeb56f8f4d94bab83cad599f5969cef66c4deeb79fa0ceb15`.
- Predicciones R1 SHA-256:
  `629c906e35f2fc14efe855f4d59d39d075fcd55eb9ff08c7d66c65d434742344`.
- Informe R2.1 SHA-256:
  `fd73f922223a59833a358058adf84eea7b0745fb6d0952b88ec37fc1cbc2f8d0`.
- Informe R1 SHA-256:
  `331ae48e82d1db03836e35d56963081275e4b25708a5378be52d0a3dd79dce17`.

El servidor se cerró al terminar. El corpus final queda consumido como examen:
no se usa para entrenamiento, recalibración, selección de umbral ni otro gate
presentado como desconocido.

## Decisión

- R2.1 no se integra, no entra en canary y no reemplaza a R1.
- R1 continúa oficial por ser comparativamente más seguro, aunque sus 4/30
  falsos permisos confirman que GloshIA todavía necesita mejora.
- DAG, Android productivo, modelo R1, Supabase y Production permanecen intactos.
- Un candidato futuro requiere imágenes nuevas e independientes de los patrones
  fallidos y un holdout final nuevo. No puede entrenarse con estas 108 muestras.

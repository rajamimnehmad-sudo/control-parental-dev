# GloshIA Visual R2.2 — reparación dirigida y gate Android

Tickets: `GLOSHIA-VISUAL-R2.2-TARGETED-REPAIR-15` y
`GLOSHIA-R2.2-ANDROID-HARNESS-16`

Fecha: 2026-08-03

Resultado: **R2.2 mejora a R1, pero queda NO-GO técnico para integración**

R1 continúa oficial. DAG, Android productivo, Supabase y Production no fueron
modificados.

## Causa corregida

La auditoría del examen consumido de R2.1 mostró que cuatro de sus ocho falsos
permisos eran equipos femeninos de ciclismo. Habían quedado agrupados bajo
`groups_families`, por lo que el resumen anterior por categoría ocultaba el
patrón real. El problema combina personas pequeñas, grupo, ropa deportiva
ajustada y presentación visual incierta. No se añadió ninguna regla por sitio,
ID, deporte o sexo declarado.

## Datos nuevos y entrenamiento privado

- Se descargaron 50 imágenes nuevas de 50 clusters independientes.
- No hubo cruce por ID, SHA-256, dHash o pHash con los bancos históricos, el
  examen final consumido ni el lote anterior de R2.1.
- Codex realizó una preetiqueta visual explícitamente marcada como auxiliar:
  44 decisiones binarias y 6 dudas excluidas.
- Las dudas fueron principalmente personas demasiado pequeñas o ciclistas cuya
  presentación no podía distinguirse con seguridad; no se convirtieron en
  `allow`.
- Split resultante: 296 train, 47 validation y 72 frozen_test; los 44 casos
  nuevos entraron únicamente en train bajo autorización privada del propietario.
- El modelo siguió siendo un único TinyCLIP binario con el preprocesamiento y
  umbral 0,40 vigentes.

Se entrenaron dos variantes acotadas desde R2.1. La variante B fue la única que
recuperó las cuatro escenas de ciclismo del examen conocido y se congeló como
candidata:

- archivo: `r2.2-candidate-b-int8.onnx`;
- SHA-256: `9aa424cfefbd5b6bbbf9470e6ff38df846b15c2e793b72e36608039e66af7852`;
- tamaño: 8.756.367 bytes;
- estado: privado, no aprobado para APK productivo.

## Evaluación histórica y regresión conocida

FP32 en los splits históricos:

| Split | Falsos permisos | Falsos filtros | Balanced accuracy |
|---|---:|---:|---:|
| validation | 0/8 | 5/39 | 93,59 % |
| frozen_test consumido | 0/10 | 11/62 | 91,13 % |

El examen final de 108 muestras ya estaba consumido y se usó solamente como
regresión conocida, nunca como examen desconocido ni para cambiar el umbral:

| Modelo | Falsos permisos | Falsos filtros | Accuracy |
|---|---:|---:|---:|
| R1 oficial | 4/30 | 24/77 | 73,83 % |
| R2.1 | 8/30 | 8/77 | 85,05 % |
| R2.2 B | 3/30 | 9/77 | 88,79 % |

R2.2 B filtró correctamente las cuatro escenas de equipos femeninos de
ciclismo. Esta tabla no constituye un nuevo gate ciego.

## Holdout ciego nuevo

Se creó un holdout nuevo de 40 imágenes, con 40 clusters y sin cruces con datos
previos. La revisión del propietario se realizó sin mostrar predicción ni score:
28 `allow`, 12 `filter`, 0 `doubt`.

| Modelo | allow→allow | allow→filter | filter→allow | filter→filter | Accuracy | Balanced accuracy |
|---|---:|---:|---:|---:|---:|---:|
| R1 | 23 | 5 | 1 | 11 | 85,00 % | 86,90 % |
| R2.2 B | 25 | 3 | 1 | 11 | 90,00 % | 90,48 % |

R2.2 conservó el mismo falso permiso de R1 y redujo falsos filtros de 5 a 3
(40 %). El falso permiso común fue una mujer con hombros descubiertos dentro de
una escena grupal; confirma que los sujetos pequeños en grupos siguen siendo el
principal límite pendiente.

## Harness Android aislado

El INT8 exacto se ejecutó con ORT Android CPU 1.27.0 en Samsung SM-A235M,
Android 14/API 34:

- hash y tamaño exactos verificados;
- `ConvInteger` abrió y ejecutó;
- 119/119 salidas finitas;
- 0/18 falsos permisos y 17/101 falsos filtros sobre los tensores congelados;
- un desacuerdo de decisión frente a FP32: una muestra humana `allow` pasó de
  0,388720 en FP32 a 0,409730 en Android INT8 y quedó `filter`;
- p50 322,08 ms y p95 329,39 ms; R1 en la misma sesión dio p50 324,41 ms y p95
  328,60 ms, por lo que la candidata fue comparable, aunque la sesión completa
  estuvo más lenta que benchmarks históricos del mismo A23;
- pico PSS 107.797 KiB y temperatura 22,8 °C;
- sesiones cerradas y APK de laboratorio desinstalado.

El desacuerdo fue en dirección segura, pero el gate se había fijado en
equivalencia de decisiones igual a cero. No se cambia el criterio después de
ver el resultado: el estado obligatorio es `NO-GO` para integrar o reemplazar
R1.

## Próximo paso recomendado

No volver a entrenar todavía. El siguiente ticket debe resolver únicamente la
equivalencia de exportación R2.2 INT8, usando el candidato congelado y la misma
muestra discrepante. Sólo después corresponde un canary reversible. Si la
equivalencia exacta no fuera técnicamente alcanzable, cualquier tolerancia de
un desacuerdo exclusivamente `allow→filter` deberá congelarse como decisión de
producto antes de volver a medir; nunca se aceptará un desacuerdo
`filter→allow`.

Artefactos privados:
`.codex-tmp/gloshia-r22-sports-repair-20260803/` y
`.codex-tmp/gloshia-r22-blind-holdout-20260803/`.

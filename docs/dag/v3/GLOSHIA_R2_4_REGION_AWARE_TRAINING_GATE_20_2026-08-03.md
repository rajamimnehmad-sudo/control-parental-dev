# GloshIA R2.4 - entrenamiento alineado con regiones de DAG

Fecha: 2026-08-03

Ticket: `GLOSHIA-R2.4-REGION-AWARE-TRAINING-GATE-20`

Resultado: `NO-GO`

## Objetivo

Entrenar el mismo modelo TinyCLIP binario con la foto completa y las vistas
regionales que ya usa DAG, sin sumar otro modelo, cambiar umbrales, modificar
Android ni aumentar el costo de inferencia productivo.

## Causa raiz y diseño

Los candidatos anteriores se ajustaron principalmente con la imagen completa,
pero DAG puede decidir con una imagen completa y hasta cuatro regiones. R2.4
alinea por primera vez la perdida de entrenamiento con estas rutas y umbrales:

- filtro directo de imagen completa en 0,40;
- revision de cuadrantes a partir de 0,30, con filtro regional en 0,45;
- vistas de imagenes extremas con filtro fuerte en 0,70 o consenso de dos
  regiones en 0,50.

Cada foto se trata como un conjunto de vistas. Una foto `filter` necesita una
ruta regional o completa suficiente; una foto `allow` debe mantener seguras
sus vistas. No se asigna automaticamente la etiqueta positiva a todos los
recortes. Este enfoque se eligio despues de contrastar aprendizaje de instancia
multiple y aprendizaje multivista en fuentes primarias: [Oquab et al., CVPR
2015](https://openaccess.thecvf.com/content_cvpr_2015/html/Oquab_Is_Object_Localization_2015_CVPR_paper.html)
y [Zhang et al., ECCV
2018](https://openaccess.thecvf.com/content_ECCV_2018/html/Xiaopeng_Zhang_ML-LocNet_Improving_Object_ECCV_2018_paper.html).

La regla de producto quedo fija antes de entrenar: una persona diminuta o
lejana, tipo "buscar a Wally", no provoca filtrado.

Despues de congelar la candidata se detecto que Pillow muestreaba los pixeles
de los cuadrantes con una aproximacion de vecino cercano, no con el indice
entero exacto de Android. El evaluador final se corrigio para reproducir ese
indice y las vistas extremas de DAG. Los pesos no se reentrenaron despues de
abrir el holdout; por lo tanto, el candidato conserva la aproximacion durante
su entrenamiento y todas las metricas finales usan el evaluador corregido.

## Datos

Se reutilizaron solamente las 337 muestras de train ya autorizadas para el
experimento privado. Validation (47) y frozen_test (72) no entraron al
entrenamiento. Los examenes consumidos de R2.2/R2.3 y `final_sealed` no se
usaron.

Se recopilo un holdout nuevo de 59 imagenes y 59 hashes/clusters iniciales,
deduplicado contra 1.564 hashes conocidos. La revision visual produjo:

- 24 `allow`;
- 16 `filter`;
- 1 `doubt`;
- 18 exclusiones por series visuales repetidas.

El examen binario final contiene 40 casos. Es de evaluacion interna y sus
etiquetas son preetiquetas visuales de Codex pendientes de auditoria del
propietario; no se declara como certificacion humana doble ni se autoriza para
entrenamiento.

## Seleccion de candidata

Se entrenaron como maximo tres variantes desde el checkpoint R1. La prioridad
fue cero permisos incorrectos y luego menos filtros incorrectos.

- A: 0/8 falsos permisos y 16/39 falsos filtros en validation; 0/10 y 18/62
  en frozen_test.
- B: descartada por un falso permiso en frozen_test.
- C: descartada por un falso permiso en frozen_test.

Se congelo A antes de abrir el holdout nuevo:

- checkpoint: `r2.4-pilot-a.pt`;
- SHA-256:
  `e34510c2618f8b2d56cab958e0e8cebbb515d5ecfa8c1f5887115f507314656d`;
- FP32 ONNX SHA-256:
  `ccfcab5fe3a27f3842dba042eed5a6bbb009c93e79ecfc24e1c2e16bf835cd58`;
- una epoca, multiplicador LR 0,30, peso regional 0,50 y peso de compuerta
  0,25.

## Resultados con politica regional exacta

| Examen | Modelo | Falsos permisos | Falsos filtros | Balanced accuracy |
| --- | --- | ---: | ---: | ---: |
| Validation | R1 | 0/8 | 20/39 | 74,36 % |
| Validation | R2.4 A | 0/8 | 16/39 | 79,49 % |
| Frozen test | R1 | 0/10 | 24/62 | 80,65 % |
| Frozen test | R2.4 A | 0/10 | 18/62 | 85,48 % |
| Holdout nuevo | R1 | 0/16 | 14/24 | 70,83 % |
| Holdout nuevo | R2.4 A | 2/16 | 11/24 | 70,83 % |

En el holdout nuevo R2.4 mejoro accuracy de 65,0 % a 67,5 % y redujo tres
filtros incorrectos, pero bajo recall de `filter` de 100 % a 87,5 % y PR-AUC
de 80,85 % a 78,84 %. El gate prohibe cualquier permiso incorrecto nuevo.

## Casos que deciden el NO-GO

- `wikimedia:163105961`: grupo formal con personas relevantes y exposicion
  visible. R2.4 alcanzo 0,4379, por debajo del umbral regional 0,45.
- `wikimedia:175435767`: grupo formal con dos mujeres claramente relevantes;
  R2.4 dejo la imagen completa en 0,2854, por debajo del piso 0,30, y DAG no
  habria abierto la revision regional.

Ambos casos fueron filtrados por R1. El examen queda consumido y no puede
usarse para ajustar R2.4.

## Decision y alcance final

- R2.4 se descarta y no se integra.
- R1 sigue siendo el unico modelo oficial.
- No se cuantizo una candidata final ni se ejecuto Android, harness o canary.
- DAG 95, pesos oficiales, politica y umbrales quedaron intactos.
- No hubo APK, telefono, Supabase, publicacion ni push.
- `final_sealed` no se abrio.

La mejora de falsos filtros demuestra que el entrenamiento regional ayuda,
pero este candidato no certifica aun un entrenamiento pixel a pixel identico y
la salida binaria unica sigue mezclando motivos visuales distintos. El
siguiente paso recomendado, mediante autorizacion separada, es un gate de datos
R3 multisenal para enseñar hombros, escote, abdomen, codos, rodillas, pose y
otros motivos como salidas separadas dentro de un unico modelo local.

Artefactos privados reproducibles:

- `.codex-tmp/gloshia-r24-region-aware-20260803/`;
- `.codex-tmp/gloshia-r24-holdout-20260803/`.

# GloshIA R3 - candidata balanceada de cabeza acotada

Fecha: 2026-08-03  
Ticket: `GLOSHIA-R3-BOUNDED-HEAD-TRAIN-23`  
Estado: `GO` de laboratorio; pendiente equivalencia y rendimiento Android.

## Objetivo

Reducir filtros de mas sin aumentar falsos permisos y respetar las tres
correcciones finales del propietario. R1 no fue reemplazado ni modificado.

## Datos

- Split base R2.1 preservado: 252 train, 47 validation y 72 frozen test.
- Incorporaciones R3 reconstruidas: 155; 70 `allow` y 85 `filter`.
- Total train: 407.
- Las revisiones 17, 36 y 83 se conservaron como `allow` con ponderacion de
  entrenamiento, no como excepciones de runtime.
- 21 hashes `pilot` no pudieron reconstruirse y fueron excluidos de forma
  explicita.
- Cero cruces de ID entre las incorporaciones R3 y los splits base.
- `final_sealed` no se abrio.

## Metodo seleccionado

Se mantuvo congelada la representacion visual TinyCLIP de R2.1 y se entreno
una unica cabeza logistica ponderada. La seleccion de regularizacion y umbral
uso solamente train y validation. Frozen test se abrio una sola vez despues de
congelar la candidata.

Modelo de laboratorio: `GloshIA Visual R3 Head 01`.

- Umbral: `0.381063`.
- Las tres correcciones del propietario quedaron por debajo del umbral:
  `0.312718`, `0.223786` y `0.184166`.
- No hay reglas por ID ni por sitio.

## Comparacion FP32

| Examen | Modelo | Falsos permisos | Falsos filtros | Balanced accuracy |
| --- | --- | ---: | ---: | ---: |
| Validation | R1 | 0/8 | 19/39 | 75,64 % |
| Validation | R3 Head 01 | 0/8 | 3/39 | 96,15 % |
| Frozen test | R1 | 0/10 | 25/62 | 79,84 % |
| Frozen test | R3 Head 01 | 0/10 | 6/62 | 95,16 % |

La candidata reduce falsos filtros 84,2 % en validation y 76,0 % en frozen
test, sin agregar falsos permisos en ninguno de los dos examenes.

## Artefactos

- FP32: 33.236.705 bytes, SHA-256
  `70f3c92134e05a7d5cbae47c8cf75c48503a54f3e59b2a0a9876d35d775406f5`.
- INT8 selectivo: 8.950.584 bytes, SHA-256
  `1f1e03ad089609d03036ae93a789589446bab54302859e4b6e64d662bd3eeeb7`.
- Latencia FP32 Mac CPU: p50 21,6 ms, p95 23,0 ms. No equivale a Android.
- Artefactos privados: `.codex-tmp/gloshia-r3-candidate-20260803/`.

El INT8 pasa ONNX checker, pero ORT Python local no ejecuta su `ConvInteger`.
Android ORT 1.27 ya demostro soporte para ese operador en tickets anteriores;
esta candidata concreta todavia necesita equivalencia de decisiones y medicion
fisica.

## Decision

R3 Head 01 es el primer modelo de este ciclo que supera claramente a R1 en los
dos examenes y aprende las tres correcciones del propietario. Queda aprobado
como candidata de laboratorio, no como modelo productivo. R1 sigue oficial
hasta que el INT8 conserve las decisiones FP32 en Android, no agregue falsos
permisos y mantenga latencia y memoria comparables.

# GloshIA R3 - reetiquetado focalizado

Fecha: 2026-08-03  
Ticket: `GLOSHIA-R3-FOCUSED-RELABEL-22`  
Resultado: `GO` para preparar una candidata R3 balanceada; `NO-GO` para sustituir R1 todavia.

## Resultado humano

- Cola original: 99 decisiones historicas de filtro.
- Fotos reconstruidas y revisadas: 88.
- Fotos no reconstruidas: 11.
- Revisiones completas: 88/88; no quedan senales `unknown` en este lote.
- Correcciones finales del propietario: revisiones 17, 36 y 83 pasan a
  `allow`.
- Resultado final: 85 `filter` y 3 `allow`.

Las tres correcciones `allow` son ejemplos negativos importantes: evitan que
R3 aprenda que una senal visual aislada obliga siempre a filtrar. La accion de
politica y las senales visibles se conservan por separado.

## Cobertura positiva en las 85 fotos filtradas

| Senal | Positivas |
| --- | ---: |
| Escote o pecho | 59 |
| Brazo por encima del codo | 57 |
| Hombro o axila | 52 |
| Pose sugerente | 39 |
| Rodilla o pierna descubierta | 26 |
| Ropa ajustada | 21 |
| Abdomen visible | 18 |
| Desnudez o contenido explicito | 13 |
| Ropa interior o traje de bano | 12 |
| Ropa transparente | 5 |

Ropa transparente sigue subrepresentada. No corresponde compensarla
repitiendo las mismas fotos ni elevando su peso a ciegas.

## Integridad y privacidad

- Exportacion privada verificada: 88 filas, SHA-256
  `dd15ccc5d458ef52b8516e60fe3e33ff0583a05e5e17d5898837ec9663c334be`.
- El enlace de revision fue vencido al cerrar el lote.
- Las 88 copias temporales fueron retiradas de Supabase.
- La exportacion privada queda fuera de Git en `.codex-tmp/`.
- No se abrio `final_sealed`.
- No se modificaron R1, DAG, Android, APK ni Production.

## Decision

El lote mejora el contrato de datos, pero no demuestra todavia una mejora de
modelo. El siguiente ticket debe entrenar una unica candidata R3 con los 176
casos historicos y estas 88 revisiones, preservando los ejemplos `allow` y
evaluando tanto falsos permisos como falsos filtros. R1 sigue siendo el modelo
oficial hasta superar validation, frozen test, equivalencia ONNX y Android.


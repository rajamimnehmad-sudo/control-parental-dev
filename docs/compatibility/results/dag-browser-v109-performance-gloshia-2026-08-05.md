# DAG 109 - validación física de carga y GloshIA

Fecha: 2026-08-05  
Dispositivo: Samsung SM-S908E, Android 16, arm64-v8a  
APK: `0.69.13-dev`, `versionCode 109`  
Extensión: `1.51.0`  
Modelo: GloshIA Visual R3.1, SHA-256
`c8b64af8092d3718c58736a511c996d0d443dacf3eaa74620b1e5af439a3cd48`  
Runtime: ONNX Runtime Android 1.27.0, CPU local

## Alcance

Se repitió la matriz de páginas vivas en el mismo S22 con corridas frías y
calientes. El runner no limpia perfiles ni datos. Las URLs solo se usaron como
escenarios de navegación y no hay reglas específicas por sitio. Los artefactos
crudos permanecen fuera de Git en `.codex-tmp/dag-perf-lab/live-runs/`.

## Resultados fríos

| Sitio | Raster | Inferencia p50/p90 | Native p50/p90 | Página visible | Vista inicial quieta | PSS | Jank |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| Mimo | 29 | 40,93 / 126,46 ms | 59 / 192 ms | 434 ms | 1.793 ms | 389.300 KiB | 1,92 % |
| Cheeky | 53 | 47,12 / 108,62 ms | 61 / 168 ms | 1.398 ms | 1.389 ms | 409.728 KiB | 5,41 % |
| Frávega | 105 | 51,84 / 225,07 ms | 61 / 240 ms | 10.220 ms | 10.217 ms | 375.466 KiB | 2,86 % |

Las colas tuvieron p50 `0 ms` y p90 entre `1–3 ms`. Las prioridades fueron
observadas en los tres sitios: Mimo `4 visible / 3 nearby / 22 background`,
Cheeky `9 / 1 / 43` y Frávega `3 / 5 / 97`.

## Resultados calientes

| Sitio | Raster | Inferencia p50/p90 | Native p50/p90 | Página visible | Vista inicial quieta | PSS | Jank |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| Mimo | 28 | 38,98 / 106,02 ms | 55,5 / 145 ms | 361 ms | 1.538 ms | 351.936 KiB | 8,70 % |
| Cheeky | 39 | 46,25 / 145,30 ms | 54 / 188 ms | 2.311 ms | 4.201 ms | 363.516 KiB | 4,00 % |
| Frávega | 79 | 42,06 / 179,44 ms | 48 / 189 ms | 1.223 ms | 5.775 ms | 366.237 KiB | 4,00 % |

Las prioridades calientes fueron Mimo `4 visible / 2 nearby / 22 background`,
Cheeky `3 / 0 / 36` y Frávega `3 / 4 / 72`. La variación de páginas vivas,
red y contenido impide atribuir porcentajes causales a una sola corrida.

## Seguridad y compatibilidad

- Las seis corridas terminaron sin crash ni ANR.
- No se modificaron el modelo, umbral, preprocesamiento, vistas regionales ni
  política visual.
- El APK contiene el mismo modelo R3.1; se verificó el SHA-256 desde el APK.
- El tamaño del APK es `129.533.921` bytes, igual al artefacto DAG 108 medido.
- La prioridad se comunica de forma asíncrona y la deduplicación solo comparte
  decisiones idénticas mientras están en vuelo; el fail-closed permanece igual.
- No se observó una cola nativa saturada: p90 fue como máximo `3 ms` en frío y
  `1 ms` en caliente. No hay base para reducir las hebras de ORT.

## Conclusión

La implementación funciona en el S22 y no añade tamaño ni una señal de
inestabilidad. La evidencia sí confirma el orden de prioridad y la ausencia de
trabajo duplicado observable en el contrato del runner, pero no demuestra una
mejora causal de tiempo total sobre páginas vivas. No se recomienda agregar
otra optimización heurística sin un fixture local controlado que aisle la
entrega visual de la red.

No se publicó, no se hizo push, no se tocó Supabase ni Production y `final_sealed`
permanece cerrado.

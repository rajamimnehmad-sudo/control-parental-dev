# GloshIA Visual R3.1 — promoción local

Fecha: 2026-08-05  
Alcance: modelo único local para DAG Browser; sin publicación, push,
Supabase ni Production.

## Modelo

- Nombre público: `GloshIA Visual`.
- Versión funcional: `R3.1`.
- Archivo: `tinyclip-r3-head-hybrid-int8.onnx`.
- SHA-256: `c8b64af8092d3718c58736a511c996d0d443dacf3eaa74620b1e5af439a3cd48`.
- Tamaño: `9.668.603` bytes.
- Runtime: ONNX Runtime Android 1.27.0 CPU.
- Contrato: RGB 224×224, letterbox DAG, normalización vigente, salida binaria,
  umbral `0,40`, política y vistas regionales sin cambios.
- Fallback de apertura: R1, sin ejecutar dos modelos por fotografía.

El modelo R3 anterior quedó respaldado fuera de Git en
`.codex-tmp/gloshia-r3-round30-binary-candidate-20260805/r3-official-pre-r31.onnx`.

## Evaluación

Sobre el mismo examen histórico de 57 muestras Android:

| Modelo | Falsos permisos | Falsos filtros | Errores de pipeline |
| --- | ---: | ---: | ---: |
| R3 | 2/21 | 6/36 | 0 |
| R3.1 | 2/21 | 1/36 | 0 |

El split reproducible completo también mostró reducción de falsos filtros de
`3/21` a `2/21` en validation y de `2/15` a `0/15` en frozen_test, sin nuevos
falsos permisos.

## S22

En comparación directa, mismo tensor, ORT CPU, 2 hilos y 30 repeticiones:

| Modelo | p50 | p95 | máximo |
| --- | ---: | ---: | ---: |
| R3 | 31,81 ms | 31,98 ms | 33,61 ms |
| R3.1 | 30,50 ms | 30,90 ms | 31,12 ms |

El candidato abrió, ejecutó salidas finitas y no produjo NaN/Inf. A23 queda
fuera del alcance de este cierre por decisión del propietario.

## APK local

- Paquete: `com.contentfilter.dagbrowser.dev`.
- versionCode: `108`.
- versionName: `0.69.12-dev`.
- SHA del modelo dentro del APK: coincide exactamente con el anterior.
- Unit tests: OK.
- Lint: OK.
- `assembleDevDebug`: OK.

## Estado

R3.1 queda como modelo oficial local en `main`. No se publicó ni se hizo push.
`final_sealed` permanece cerrado. Los corpus, manifests, splits, informes y
checkpoints privados se conservan para futuros entrenamientos.

# GLOSHIA-SHARED-CORE-01 — EVIDENCIA

Fecha: 2026-08-18. Rama local: `work/chrome-visual`.

## Resultado

PASS. GloshIA Visual R3.1 vive en un unico modulo Android compartido
`gloshia-visual-core`, consumido por DAG Browser y App Usuario. El modelo, SHA,
ONNX Runtime 1.27.0, RGB 224 x 224, NCHW, letterbox, crops, umbrales, razones y
politica Allow/Block no cambiaron.

El modulo es una libreria AAR incluida por ambos builds Gradle. Se descarto un
build compuesto independiente porque no podia heredar de forma reproducible la
ubicacion local del Android SDK; no se agregaron rutas personales ni artefactos
precompilados al repositorio.

## Paridad

- SHA-256 del modelo: `c8b64af8092d3718c58736a511c996d0d443dacf3eaa74620b1e5af439a3cd48`.
- Tensor compartido contra algoritmo DAG congelado: igualdad bit a bit.
- Politica compartida contra oraculo DAG congelado: mismas acciones, razones,
  probabilidades y bases para allow, umbral completo, fuerte, region incierta,
  region fuerte y consenso.
- A23 SM-A235M / Android 14: una prueba instrumentada comparo el motor compartido
  con una sesion ONNX que reproduce el camino DAG anterior; score dentro de
  `0.000001` y decision identica. 1/1 PASS.

## ABI y empaquetado

- DAG sigue siendo solo `arm64-v8a`.
- App Usuario sigue empaquetando `arm64-v8a` y `armeabi-v7a`.
- R3.1 se declara disponible solo cuando el proceso es de 64 bits. ARM32 conserva
  la app y sus protecciones actuales, pero Chrome Visual debe degradar a DAG.
- App Usuario no contiene librerias ONNX ARM32; si contiene modelo y ONNX ARM64.
- APK DEV DAG: 122.083.762 bytes. APK DEV App Usuario: 62.967.822 bytes.

## Gates

- Modulo compartido: unitarios Debug/Release y ktlint PASS.
- DAG: 220/220 DEV, 220/220 Diagnostic, JS 102/102, lint DEV/Diagnostic,
  ktlint y assemble DEV PASS.
- App Usuario: `feature-accessibility` unitarios/ktlint, lint DEV y assemble DEV
  PASS.
- `git diff --check`: PASS.

No hubo push, PR, publicacion ni cambio de version.

# DAG Browser 159 - limpieza estructural

Fecha: 2026-08-06

## Alcance

- Retiro completo y autorizado de descargas y PDF.
- Extraccion de la normalizacion de opciones a `DagChoicePromptPolicy`.
- Retiro de `androidx.core` y cinco recursos sin consumidores confirmados por
  Lint.
- `DagBrowserActivity.kt`: 3.309 a 2.599 lineas.
- Balance del lote: 1.571 lineas netas retiradas.

El cambio no modifica extension, barrera de imagenes, GloshIA R3.1, modelo,
umbral, politica visual, scheduler ni concurrencia.

## Validacion

- `testDagProtectionJs`: 21/21.
- `testDevDebugUnitTest`: correcto.
- `ktlintCheck`: correcto.
- `lintDevDebug`: correcto y sin recursos sin uso.
- `assembleDevDebug`: correcto.
- Instalacion in-place en SM-S908E: correcta, datos preservados.
- Google, Mimo, Fravega y Cheeky: sin crash, ANR u OOM.

## Observacion visual abierta

En muestras de 7 y 12 segundos Mimo y Fravega conservaron espacios de imagen
vacios. Logcat registro 67 decisiones `model_allow` y cero `block` en la ventana
acotada. Como el diff de DAG 159 no toca el pipeline de imagenes, esta evidencia
no demuestra causalidad con el refactor y el diagnostico visual continua
abierto.

No se hizo push, publicacion remota ni cambio en Supabase o Production.

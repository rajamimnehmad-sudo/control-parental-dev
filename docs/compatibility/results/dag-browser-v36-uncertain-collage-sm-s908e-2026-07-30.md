# DAG Browser 36 - revisión regional de composiciones dudosas

Fecha: 2026-07-30

Dispositivo: Samsung SM-S908E

Android: 16

Paquete: `com.contentfilter.dagbrowser.dev`

Versión: `versionCode 36`, `0.26.0-dev`

Extensión integrada: `1.24.0`

## Causa y corrección

Una miniatura dinámica de Google Imágenes cambiaba su fuente después de una
decisión previa. DAG 34 pasó a ocultar inmediatamente todo medio cuyo
`src`, `srcset`, `data-src` o `poster` cambia, invalida la fuente anterior y
analiza la nueva antes de presentarla.

Al repetir el caso, la fuente nueva sí llegó al único modelo local, pero la
composición completa obtuvo `0,3360`, por debajo del umbral global `0,40`.
DAG 36 conserva ese umbral: sólo una imagen ordinaria ya dudosa, entre `0,30`
y `0,40`, recibe cuatro recortes superpuestos del tensor RGB ya preparado. Se
filtra si una región alcanza `0,45`. Las imágenes por debajo de `0,30` siguen
usando una sola inferencia y la política panorámica mantiene su consenso
independiente.

No se agregó una regla por sitio, API, segundo modelo ni persistencia de
píxeles. Los buffers regionales generados se sobrescriben al terminar.

## Validación

- 111 pruebas unitarias DEV.
- `ktlintCheck`.
- `lintDevDebug`.
- `assembleDevDebug`.
- La fuente reproducida de 35.927 bytes pasó de `model_allow 0,4600` en el
  candidato intermedio a `model_filter 0,4600` en DAG 36.
- La miniatura permaneció difuminada a los 12 segundos.
- Cheeky conservó portada, controles y banner visibles; completó
  `4.721 / 4.993 / 1.955 ms`.
- Mimo completó `1.692 / 9.593 / 265 ms`.
- Frávega quedó visible en `1.741 ms`, analizada en `11.503 ms` y no emitió
  quietud de fotos dentro de la muestra acotada de 12 segundos.
- No hubo crash ni ANR. DAG continuó como navegador predeterminado y
  Accessibility de App Usuario permaneció activa.

APK construida desde `main` local:

```text
app-dag-browser/build/outputs/apk/dev/debug/DagBrowser-dev-debug.apk
121210914 bytes
SHA-256 5bed28cf235007c5622b54f241c01d4dd6ac40c0679f5b09812eac73faa9f3ce
```

No hubo push, publicación DEV, cambios en Supabase, App Usuario ni App Admin.

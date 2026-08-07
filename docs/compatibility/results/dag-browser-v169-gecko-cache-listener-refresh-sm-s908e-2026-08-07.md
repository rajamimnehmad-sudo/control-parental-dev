# DAG Browser 169 - refresco de cache y listeners Gecko

Fecha: 2026-08-07

Dispositivo: Samsung SM-S908E

Android: 16

Paquete: `com.contentfilter.dagbrowser.dev`

Version: `169` / `0.69.73-dev`

Extension: `1.86.0`

APK SHA-256: `9aa0496d8d1ed4955e592030f0f85f6b1a7f4482c5f042c087480cc86a72a231`

APK: 123 MB aproximadamente

## Causa raiz

Se compararon el ultimo estado confirmado, DAG 158 (`4213d87`), el retiro de
descargas/PDF de DAG 159 (`7a26139`) y los cambios posteriores de carga. DAG
159 no modifico `background.js`, `barrier.js`, GeckoView, GloshIA ni los
limites de analisis. La regresion temporal coincide con DAG 162 (`fa2d0e5`),
que dejo de vaciar toda la cache de Gecko por cada `versionCode` y paso a una
revision de cache interceptada independiente.

En la reproduccion fallida no llegaron decisiones de imagen al puente nativo;
despues de reiniciar y retirar la cache vieja, la misma busqueda cargo. Los
contadores temporales descartaron saturacion de streams, cola, bytes y timeouts:
9 raster interceptados, 9 permitidos, 0 bloqueados y todos los limites en cero.
La explicacion compatible con la linea temporal y con el comportamiento
observado es un estado de cache Gecko anterior al comportamiento vigente de los
listeners `webRequest`.

Mozilla documenta `webRequest.handlerBehaviorChanged()` para vaciar la cache en
memoria cuando cambia el comportamiento de los listeners, y recomienda usarla
con moderacion por su costo. DAG la ejecuta una sola vez al iniciar el background
de la extension, despues de registrar `onBeforeRequest` y `onHeadersReceived`:
no se ejecuta por navegacion, pagina, gesto o raster.

Referencias oficiales:

- <https://developer.mozilla.org/en-US/docs/Mozilla/Add-ons/WebExtensions/API/webRequest/handlerBehaviorChanged>
- <https://developer.mozilla.org/en-US/docs/Mozilla/Add-ons/WebExtensions/API/webRequest/filterResponseData>

La revision Android de cache interceptada sube de 4 a 5. Eso fuerza una unica
limpieza de cache persistente para instalaciones que ya guardaron el estado
viejo. No se borran historial, cookies, pestanas, perfiles ni datos de usuario.

## Cambio final

- Registro de ambos listeners antes del refresco de cache en memoria.
- Un `handlerBehaviorChanged()` protegido por disponibilidad de API y con
  rechazo controlado.
- Revision de cache interceptada 5.
- Version de extension 1.86.0 y versionCode Android 169.
- Retiro completo de instrumentacion temporal de DOM y pipeline.

No cambiaron GloshIA Visual R3.1, pesos, umbral, preprocesamiento, politica
visual, `final_sealed`, hilos Android, ONNX intra/inter-op, publicidad, video ni
scheduler. No se agregaron excepciones por sitio, URL o dominio.

## Validacion automatica

- `testDagProtectionJs`: 22/22.
- `testDevDebugUnitTest`: correcto.
- `ktlintCheck`: correcto.
- `assembleDevDebug`: correcto.
- `assembleLabDebug`: correcto.

## Fixture controlado

Perfil LAB aislado, HTTP loopback, mismo S22, dos swipes y 12 segundos:

| Muestra | Raster criticos | Estabilidad visual | Frames / p95 | Cola p95 | PSS | Crash/ANR |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Fria | 4/4, 0 errores | 537 ms | 48 / 15 ms | 1 ms | 270.317 KiB | 0/0 |
| Caliente | 4/4, 0 errores | 218 ms | 21 / 13 ms | reutilizacion segura | 381.807 KiB | 0/0 |

La captura fria y la caliente mostraron los tres raster sinteticos permitidos y
el raster sensible reemplazado por el placeholder neutral. Los artefactos
crudos locales permanecen fuera de Git en:

- `.codex-tmp/dag-perf-lab/runs/dag169-cache-cold/`
- `.codex-tmp/dag-perf-lab/runs/dag169-cache-warm/`

## Sitios reales y caso reportado

- Fravega: carga inicial completa y dos recargas calientes. Banner, categorias
  y tarjetas de `Ofertas Unicas` mostraron fotos reales, sin cuadros negros o
  grises generados por DAG.
- Google: busqueda `pepino`; la secuencia
  `Todo -> Imagenes -> Todo -> Imagenes` mostro fotos completas en las dos
  entradas a Imagenes.
- Mimo: `page_visible` 1.298 ms, `viewport_images_ready` 1.421 ms y
  `page_analysis_ready` 2.421 ms. La captura final mostro productos reales.
- Cheeky: `page_visible` 3.495 ms y `viewport_images_ready` 880 ms. La pagina y
  su modal propio quedaron utilizables; el espacio de video/hero no se evalua
  como raster, de acuerdo con el alcance vigente.
- Mimo y Cheeky: cero crash, ANR o native crash. La cola p95 fue 2 ms en Mimo y
  1 ms en Cheeky.

Memoria DEV al terminar con las 16 pestanas originales: PSS 338.925 KiB, RSS
509.484 KiB y bitmaps 10.820 KiB. Estado termico 0; AP 33,8 C, bateria 32,4 C y
piel 31,8 C.

Las tres pestañas temporales DEV creadas durante la validacion se cerraron de
forma individual y el perfil volvio a sus 16 pestanas originales. La app LAB
aislada fue desinstalada. No se borro ningun dato del usuario.

## Estado

Correccion general validada localmente e instalada con `adb install -r`. No se
hizo push, publicacion DEV, cambio en Supabase ni modificacion de Production.

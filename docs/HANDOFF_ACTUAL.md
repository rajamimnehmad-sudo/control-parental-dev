# HANDOFF ACTUAL - Glosh y DAG Browser

Fecha de corte: 2026-08-02

Este archivo contiene solo el estado tecnico vigente. El historial vive en
`docs/BACKLOG_PRODUCTO.md`, `docs/compatibility/results/` y Git. No reconstruir
el runtime actual desde versiones o worktrees historicos.

## Repositorio y flujo vigente

- Carpeta canonica: `/Users/yejielnehmad/Developer/content-filter`.
- Rama de trabajo: `main` local.
- Tras el cierre de DAG 67, `main` queda 29 commits por delante de
  `origin/main`.
- No se hizo push, PR, publicacion DEV ni Production.
- Los APK finales se construyen solo desde `main` local integrado.
- Supabase y las apps Usuario/Admin no fueron modificados en este lote.

| Aplicacion | versionCode | versionName DEV | Estado |
| --- | ---: | --- | --- |
| App Usuario | 307 | 1.0.1-dev | Sin cambios |
| App Admin | 290 | 1.0.1-dev | Sin cambios |
| DAG Browser | 67 | 0.47.0-dev | Validado en SM-S908E; no publicado |

DAG 67 esta instalado in-place en el SM-S908E `R5CT717BZTZ`, sin borrar el
perfil ni tocar otras apps.

## DAG Browser vigente

DAG 67 usa una unica compuerta GloshIA previa al render. Los bytes raster se
capturan una vez y solo `model_allow` devuelve el original exacto. Filtro,
error, timeout, saturacion, animacion o entrada invalida producen un PNG neutro
sin pixeles rechazados. SVG e iconos vectoriales seguros quedan fuera de la
espera visual.

El destello de Google Imagenes provenia de recursos sucesivos: una miniatura
provisoria permitida podia aparecer antes de que la pagina eligiera otra
resolucion filtrada. La correccion es global:

- `data:` y `blob:` quedan neutrales desde `document_start`;
- cualquier respuesta con MIME raster cruza la compuerta, incluso por
  `fetch`/XHR;
- un `img` se revela despues de 350 ms sin cambios en `src`, `srcset` o
  `sizes`;
- el observador no reescribe fuentes, no decide contenido, no trabaja durante
  scroll y no contiene excepciones por sitio o telefono.

La espera de 350 ms no agrega red ni inferencias. Es el margen fisicamente
validado para evitar el destello; reducirlo requiere un benchmark separado.
Extension incorporada: `1.36.0`.

Limites vigentes: 2 MiB por recurso, 8 MiB capturados, 32 streams, cola de 24,
dos inferencias nativas y cache efimera de 512 hashes. Video, audio, canvas,
object y embed permanecen bloqueados por contratos separados.

## Validacion y artefacto

- 12 pruebas WebExtension y 147 unitarias Kotlin aprobadas.
- Ktlint, Lint, build y `git diff --check` correctos.
- Google Imagenes: 40 cuadros de una carga limpia sin pixeles previos en
  tarjetas filtradas; una foto permitida aparecio y permanecio.
- Mimo, Cheeky y Fravega conservaron estructura, controles e imagenes segun la
  decision de GloshIA.
- Logcat sin crash, ANR ni OOM.

APK local:

- ruta: `app-dag-browser/build/outputs/apk/dev/debug/DagBrowser-dev-debug.apk`;
- tamaño: `121359549` bytes;
- SHA-256:
  `8477abc6f539aacef1423c6736d35defc736e26bc17c82cb707de70bbf2c7e8d`.

Evidencia completa:
`docs/compatibility/results/dag-browser-v67-first-paint-stability-sm-s908e-2026-08-02.md`.
Contrato vigente: `docs/dag/v3/DAG_BROWSER_V3_IMAGE_PIPELINE.md`.

## Metricas

- `page_visible`: la estructura protegida ya puede usarse.
- `viewport_images_ready`: termino el trabajo visual de la ventana inicial y
  permanecio quieto 250 ms; no representa toda una pagina infinita.
- `page_analysis_ready`: `GeckoSession.onPageStop`; mide el ciclo de pagina y
  texto, no la inferencia de GloshIA.

Definicion: `docs/dag/v3/DAG_BROWSER_V3_PERFORMANCE_METRICS.md`.

## Estado de GloshIA visual

- Hay un unico modelo local; DAG 67 no cambia sus pesos ni umbrales.
- El laboratorio de 1.000 miniaturas y la ronda humana son evaluacion, no un
  entrenamiento autorizado.
- La calibracion preliminar y el experimento privado R1 quedaron `NO-GO` para
  reemplazar el modelo Android; el examen final sigue sellado.
- El piloto DEV no demuestra cobertura universal ni elimina falsos permisos o
  falsos filtros.

Documentos vigentes:

- `docs/dag/v3/DAG_BROWSER_V3_FOUNDATION.md`;
- `docs/dag/v3/DAG_BROWSER_V3_IMAGE_PIPELINE.md`;
- `docs/dag/v3/DAG_BROWSER_V3_MODEL_DATASET_CONTRACT.md`;
- `docs/dag/v3/GLOSHIA_LAB_CALIBRATION_2026-07-31.md`;
- `docs/compatibility/results/dag-performance-history.md`.

## Decisiones de producto vigentes

- DAG es el unico navegador; no restaurar DAG 1 ni DAG 2.
- Glosh es el sistema completo, DAG su navegador protegido y GloshIA el
  analizador visual local.
- DAG usa el rol oficial de navegador con confirmacion Android.
- No usar Device Owner, MDM, Knox ni restablecimiento de fabrica.
- Video permanece bloqueado; clasificar fotogramas es otro ticket.
- No hacer push, PR, publicacion DEV ni Production sin un OK nuevo y explicito.

# HANDOFF ACTUAL - Glosh y DAG Browser

Fecha de corte: 2026-08-02

Este archivo contiene solo el estado tecnico vigente. El historial vive en
`docs/BACKLOG_PRODUCTO.md`, `docs/compatibility/results/` y Git. No reconstruir
el runtime actual desde versiones o worktrees historicos.

## Repositorio y flujo vigente

- Carpeta canonica: `/Users/yejielnehmad/Developer/content-filter`.
- Rama de trabajo: `main` local.
- Tras el cierre de DAG 68, `main` queda 30 commits por delante de
  `origin/main`.
- No se hizo push, PR, publicacion DEV ni Production.
- Los APK finales se construyen solo desde `main` local integrado.
- Supabase y las apps Usuario/Admin no fueron modificados en este lote.

| Aplicacion | versionCode | versionName DEV | Estado |
| --- | ---: | --- | --- |
| App Usuario | 307 | 1.0.1-dev | Sin cambios |
| App Admin | 290 | 1.0.1-dev | Sin cambios |
| DAG Browser | 68 | 0.48.0-dev | APK final local; no publicado |

El comportamiento final se valido in-place en el SM-S908E `R5CT717BZTZ` antes
del incremento de version. El APK canonico DAG 68 conserva ese mismo codigo,
pero su instalacion final quedo pendiente porque el telefono se desconecto. No
se borro el perfil ni se tocaron otras apps.

## DAG Browser vigente

DAG 68 usa una unica compuerta GloshIA previa al render. Los bytes raster se
capturan una vez y solo `model_allow` devuelve el original exacto. Filtro,
error, timeout, saturacion, animacion o entrada invalida producen un PNG neutro
sin pixeles rechazados. SVG e iconos vectoriales seguros quedan fuera de la
espera visual.

La presentacion vigente es global:

- `data:` y `blob:` quedan neutrales desde `document_start`;
- cualquier respuesta con MIME raster cruza la compuerta, incluso por
  `fetch`/XHR;
- un `img` HTTP(S) completo se revela sin espera artificial (`0 ms`);
- una imagen HTTP(S) ya estable no se vuelve a ocultar cuando el sitio rota su
  fuente; los nuevos bytes siguen retenidos antes del render;
- una fuente inline `data:`/`blob:` se vuelve a cerrar porque no atraviesa
  `webRequest`;
- el observador no reescribe fuentes, no decide contenido, no trabaja durante
  scroll y no contiene excepciones por sitio o telefono.

En un refresh del mismo documento DAG mantiene visible la pagina ya protegida
mientras espera la nueva barrera. Primera carga, URL distinta o fallo siguen
cerrados por la cobertura total. Extension incorporada: `1.36.6`.

Limites vigentes: 2 MiB por recurso, 8 MiB capturados, 32 streams, cola de 24,
dos inferencias nativas y cache efimera de 512 hashes. Video, audio, canvas,
object y embed permanecen bloqueados por contratos separados.

## Validacion y artefacto

- 12 pruebas WebExtension y 148 unitarias Kotlin aprobadas.
- Ktlint, Lint, build y `git diff --check` correctos.
- Google Imagenes: el usuario confirmo el raster en `0 ms` sin escape de
  contenido rechazado y el refresh sin apagado general.
- Antes del arreglo, dos rafagas reprodujeron tres cuadros negros consecutivos;
  la segunda toma cronometrada estimo aproximadamente 1,2 segundos de cobertura
  total. La causa era el estado Android `Loading`, no GloshIA.
- La matriz Mimo, Cheeky y Fravega de DAG 67 sigue siendo la ultima matriz
  completa; DAG 68 no cambia pesos, umbrales ni compuerta de bytes.

APK local:

- ruta: `app-dag-browser/build/outputs/apk/dev/debug/DagBrowser-dev-debug.apk`;
- tamaño: `121360633` bytes;
- SHA-256:
  `2a81e6477b5c8170297b5b7e464cf3448fac6c5de5c5711970a7b028e0436a55`.

Evidencia completa:
`docs/compatibility/results/dag-browser-v68-zero-delay-refresh-sm-s908e-2026-08-02.md`.
Contrato vigente: `docs/dag/v3/DAG_BROWSER_V3_IMAGE_PIPELINE.md`.

## Metricas

- `page_visible`: la estructura protegida ya puede usarse.
- `viewport_images_ready`: termino el trabajo visual de la ventana inicial y
  permanecio quieto 250 ms; no representa toda una pagina infinita.
- `page_analysis_ready`: `GeckoSession.onPageStop`; mide el ciclo de pagina y
  texto, no la inferencia de GloshIA.

Definicion: `docs/dag/v3/DAG_BROWSER_V3_PERFORMANCE_METRICS.md`.

## Estado de GloshIA visual

- Hay un unico modelo local; DAG 68 no cambia sus pesos ni umbrales.
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

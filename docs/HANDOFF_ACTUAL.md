# HANDOFF ACTUAL - Glosh y DAG Browser

Fecha de corte: 2026-08-02

Este archivo contiene solo el estado tecnico vigente. El historial vive en
`docs/BACKLOG_PRODUCTO.md`, `docs/compatibility/results/` y Git. No reconstruir
el runtime actual desde versiones o worktrees historicos.

## Repositorio y flujo vigente

- Carpeta canonica: `/Users/yejielnehmad/Developer/content-filter`.
- Rama de trabajo: `main` local.
- Tras el cierre local de DAG 86, `main` queda 31 commits por delante de
  `origin/main`.
- No se hizo push, PR, publicacion DEV ni Production.
- Los APK finales se construyen solo desde `main` local integrado.
- Supabase y las apps Usuario/Admin no fueron modificados en este lote.

| Aplicacion | versionCode | versionName DEV | Estado |
| --- | ---: | --- | --- |
| App Usuario | 307 | 1.0.1-dev | Sin cambios |
| App Admin | 290 | 1.0.1-dev | Sin cambios |
| DAG Browser | 86 | 0.66.0-dev | Instalado y validado localmente; no publicado |

El APK canonico DAG 86 esta instalado en el SM-S908E `R5CT717BZTZ`; Android
confirma `versionCode=86`, `versionName=0.66.0-dev` y DAG como navegador
predeterminado. Se conservaron perfil, cache y pestañas para no alterar los
datos del usuario ni favorecer artificialmente la medicion.

## DAG Browser vigente

DAG 86 usa una unica compuerta GloshIA previa al render. Los bytes raster se
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
cerrados por la cobertura total. Extension incorporada: `1.47.0`.

La interfaz sustituye la transicion azul por fondo blanco y una barra fina de
progreso. Los sprites PNG pasivos, extremos, transparentes y de pocos colores
pueden sanitizarse sin liberar fotografias ni crear excepciones por comercio.
El bloqueo de anuncios por red sigue vigente, pero ya no existe un
`MutationObserver` global que recorra cada cambio de todas las paginas: los
selectores explicitos se revisan una vez y la busqueda textual exacta se limita
a documentos con ruta o parametros de buscador. Gecko activa marcado paralelo
del recolector mediante su ajuste oficial de rendimiento.

Limites vigentes: 2 MiB por recurso, 8 MiB capturados, 32 streams, cola de 24,
dos inferencias nativas y cache efimera de 512 hashes. Video, audio, canvas,
object y embed permanecen bloqueados por contratos separados.

## Validacion y artefacto

- 13 pruebas WebExtension y 154 unitarias Kotlin aprobadas.
- Ktlint, Lint, build y `git diff --check` correctos.
- En Mimo, la interaccion temprana del menu paso de `26,7 fps` en la base y
  `21,8 fps` en un candidato descartado a `47,8-48,6 fps` en dos repeticiones
  DAG 86. Chrome dio `62,5 fps` en la misma accion y equipo; queda una brecha
  inicial de Gecko y algun cuadro largo aislado.
- Ya asentado, el carrusel de Mimo midio `55,2-56,2 fps`; menu y expansion de
  categoria midieron aproximadamente `50,2-54,5 fps`. No hubo inferencias de
  GloshIA durante esas interacciones.
- Fravega fue visible en `1.657 ms`, termino pagina en `6.949 ms` y fotos
  iniciales en `7.722 ms`; frente a DAG 78 observado, pagina termino 13,5 % y
  fotos 21,2 % antes, con visibilidad equivalente (`+0,8 %`).
- Cheeky fue visible en `2.024 ms` y termino pagina en `4.577 ms`; el evento de
  fotos fue prematuro y no se usa como resultado. Frente a DAG 78 observado,
  visibilidad fue 10,5 % mas lenta y fin de pagina 11 % mas rapido.
- Google busqueda se recorrio despues de retirar el observador; no mostro
  rotulos `Patrocinado` ni crashes. Los resultados comerciales sin ese rotulo
  no se clasificaron como anuncios por su apariencia.
- Sin crash, ANR ni OOM en Mimo, Cheeky o Fravega. El fixture HTTPS
  autofirmado sigue bloqueado por TLS y no se conto.
- Google Imagenes: el usuario confirmo el raster en `0 ms` sin escape de
  contenido rechazado y el refresh sin apagado general.
- Antes del arreglo, dos rafagas reprodujeron tres cuadros negros consecutivos;
  la segunda toma cronometrada estimo aproximadamente 1,2 segundos de cobertura
  total. La causa era el estado Android `Loading`, no GloshIA.
- DAG 86 no cambia pesos, umbrales ni la compuerta de bytes de DAG 68; la
  matriz nueva evalua presentacion, carga e interaccion dinamica.

APK local:

- ruta: `app-dag-browser/build/outputs/apk/dev/debug/DagBrowser-dev-debug.apk`;
- tamaño: `121372369` bytes;
- SHA-256:
  `ea3003d434d2effcb63c9a77c28b7065249c3574bb7376e117ab07f4770b3a10`.

Evidencia completa:
`docs/compatibility/results/dag-browser-v86-dynamic-pages-sm-s908e-2026-08-02.md`.
Contrato vigente: `docs/dag/v3/DAG_BROWSER_V3_IMAGE_PIPELINE.md`.

## Metricas

- `page_visible`: la estructura protegida ya puede usarse.
- `viewport_images_ready`: termino el trabajo visual de la ventana inicial y
  permanecio quieto 250 ms; no representa toda una pagina infinita.
- `page_analysis_ready`: `GeckoSession.onPageStop`; mide el ciclo de pagina y
  texto, no la inferencia de GloshIA.

Definicion: `docs/dag/v3/DAG_BROWSER_V3_PERFORMANCE_METRICS.md`.

## Estado de GloshIA visual

- Hay un unico modelo local; DAG 86 no cambia sus pesos ni umbrales.
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

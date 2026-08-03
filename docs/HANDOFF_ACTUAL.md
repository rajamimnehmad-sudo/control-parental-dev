# HANDOFF ACTUAL - Glosh y DAG Browser

Fecha de corte: 2026-08-02

Este archivo contiene solo el estado tecnico vigente. El historial vive en
`docs/BACKLOG_PRODUCTO.md`, `docs/compatibility/results/` y Git. No reconstruir
el runtime actual desde versiones o worktrees historicos.

## Repositorio y flujo vigente

- Carpeta canonica: `/Users/yejielnehmad/Developer/content-filter`.
- Rama de trabajo: `main` local.
- `main` fue respaldada en `origin/main` despues de integrar DAG 92 y las
  versiones Android vigentes.
- App Usuario 307, App Admin 290 y DAG 92 estan publicadas en DEV. Production
  no fue modificada.
- Los APK finales se construyen solo desde `main` local integrado.
- Supabase y las apps Usuario/Admin no fueron modificados en este lote.

| Aplicacion | versionCode | versionName DEV | Estado |
| --- | ---: | --- | --- |
| App Usuario | 307 | 1.0.1-dev | Publicada en DEV |
| App Admin | 290 | 1.0.1-dev | Publicada en DEV |
| DAG Browser | 92 | 0.68.0-dev | Publicada en DEV; instalada en SM-S908E |

El APK canonico DAG 92 esta instalado en el SM-S908E `R5CT717BZTZ`; Android
confirma `versionCode=92` y `versionName=0.68.0-dev`. La actualizacion conservó
los datos del perfil DEV.

## DAG Browser vigente

DAG 92 usa una unica compuerta GloshIA previa al render. Los bytes raster se
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
cerrados por la cobertura total. Extension incorporada: `1.48.0`.

Los `data:image` raster visibles que parecen controles o iconos ya no quedan
ocultos para siempre: si pesan hasta 48 KiB, tienen bordes naturales de hasta
128 px, se muestran en hasta 96 px y entran dentro de un maximo de 16 fuentes
unicas por documento, atraviesan la misma compuerta local. Solo `model_allow`
los revela. Rechazo, SVG inline, `blob:`, exceso, formato invalido, timeout o
fuente cambiada permanecen cerrados. La decision se deduplica por contenido y
no hay reglas por Google, comercio o telefono.

La interfaz sustituye la transicion azul por fondo blanco y una barra fina de
progreso. Los sprites PNG pasivos, extremos, transparentes y de pocos colores
pueden sanitizarse sin liberar fotografias ni crear excepciones por comercio.
La interfaz de DAG 92 agrega un boton de nueva pagina blanco, miniaturas
visibles en pestañas e historial, iconos en el menu y Descargas en pantalla
completa. La barra superior queda fija durante el desplazamiento: se retiro el
ocultamiento que cambiaba la altura util y producia un salto visible.
El bloqueo de anuncios por red sigue vigente, pero ya no existe un
`MutationObserver` global que recorra cada cambio de todas las paginas: los
selectores explicitos se revisan una vez y la busqueda textual exacta se limita
a documentos con ruta o parametros de buscador. Gecko activa marcado paralelo
del recolector mediante su ajuste oficial de rendimiento.

Limites vigentes: 2 MiB por recurso, 8 MiB capturados, 32 streams, cola de 24,
dos inferencias nativas y cache efimera de 512 hashes. Video, audio, canvas,
object y embed permanecen bloqueados por contratos separados.

## Validacion y artefacto

- 14 pruebas WebExtension y 154 unitarias Kotlin aprobadas; el lote de interfaz
  DAG 92 repitio las 154 unitarias, Ktlint, Lint y build.
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
- En el A23, DAG 86 reprodujo el hueco del favicon de Fravega. DAG 87 mostro
  los favicons de Fravega, Moov y Sporting y las miniaturas de filtros rapidos
  de Google, todos despues de la decision local.
- Matriz DAG 87/A23: Mimo `2.975 / 3.068 / 273 ms`, Cheeky
  `10.299 / 11.262 / 2.803 ms` y Fravega `20.024 / no completo en 20 s /
  1.187 ms`. Mimo abrio su menu completo; no se observó una regresion general.
- Google Imagenes: el usuario confirmo el raster en `0 ms` sin escape de
  contenido rechazado y el refresh sin apagado general.
- Antes del arreglo, dos rafagas reprodujeron tres cuadros negros consecutivos;
  la segunda toma cronometrada estimo aproximadamente 1,2 segundos de cobertura
  total. La causa era el estado Android `Loading`, no GloshIA.
- DAG 86 no cambia pesos, umbrales ni la compuerta de bytes de DAG 68; la
  matriz nueva evalua presentacion, carga e interaccion dinamica.

APK local:

- ruta: `app-dag-browser/build/outputs/apk/dev/debug/DagBrowser-dev-debug.apk`;
- tamaño: `121377265` bytes;
- SHA-256:
  `3c692dae5f841570119f9c0d0da932bfad32863d9afba09ae91be4a755a27dfd`.

Evidencia completa:
`docs/compatibility/results/dag-browser-v87-inline-icons-sm-a235m-2026-08-02.md`.
Contrato vigente: `docs/dag/v3/DAG_BROWSER_V3_IMAGE_PIPELINE.md`.

## Metricas

- `page_visible`: la estructura protegida ya puede usarse.
- `viewport_images_ready`: termino el trabajo visual de la ventana inicial y
  permanecio quieto 250 ms; no representa toda una pagina infinita.
- `page_analysis_ready`: `GeckoSession.onPageStop`; mide el ciclo de pagina y
  texto, no la inferencia de GloshIA.

Definicion: `docs/dag/v3/DAG_BROWSER_V3_PERFORMANCE_METRICS.md`.

## Estado de GloshIA visual

- Hay un unico modelo local; DAG 92 no cambia sus pesos ni umbrales.
- El experimento local autorizado `GLOSHIA-VISUAL-CANDIDATE-TRAIN-08` produjo
  R2 Candidate 01 con 203 train, 47 validation y 72 frozen_test, agrupados sin
  cruces de hashes ni clusters.
- R2 mejoró validation y redujo falsos filtros en frozen_test, pero introdujo
  un falso permiso nuevo (`1/10` frente a `0/10` de R1) y la cuantización no
  pudo abrirse con el ORT Python local. Resultado `NO-GO`; R1 permanece intacto.
- `final_sealed` sigue cerrado. No se autoriza canary Android hasta que un
  candidato posterior pase seguridad, cuantización y rendimiento.
- `GLOSHIA-VISUAL-R2-HARD-NEGATIVE-REPAIR-09` (2026-08-02): se preparó un
  lote privado de 50 imágenes públicas independientes, 25/25 en estratos de
  muestreo filter-like/allow-like. Los estratos no son etiquetas. La revisión
  terminó con 26 filter, 23
  allow y 1 doubt. Sobre las 49 binarias, R1 tuvo 8/26 falsos permisos y 7/23
  falsos filtros, balanced accuracy 69,40% y PR-AUC 79,46%; en el estrato
  filter-like hubo 7/18 falsos permisos. El servidor local fue
  `http://127.0.0.1:8770/`; `final_sealed` sigue cerrado. Evidencia:
  `docs/dag/v3/GLOSHIA_VISUAL_R2_HARD_NEGATIVE_REPAIR_09_2026-08-02.md`.
- `GLOSHIA-VISUAL-R2.1-HARD-NEGATIVE-TRAIN-10` (2026-08-03): la ronda nueva
  quedó autorizada por el propietario para un experimento privado local:
  `owner_authorized_private_experiment`. Las 49 binarias pasaron a train y se
  mantuvieron validation (47) y frozen_test (72) independientes; no se declaró
  `training_rights_clear`. R2.1 FP32 redujo los falsos filtros de 44/101 a
  12/101 y no agregó falsos permisos, pero su exportación INT8 no abrió con
  ONNX Runtime CPU local por `ConvInteger`; por el gate obligatorio el resultado
  es `NO-GO`. R1, `final_sealed`, Android y DAG permanecen intactos. Evidencia:
  `docs/dag/v3/GLOSHIA_VISUAL_R2_1_HARD_NEGATIVE_TRAIN_10_2026-08-03.md`.
- `GLOSHIA-VISUAL-R2.1-ANDROID-EXPORT-GATE-11` (2026-08-03): se probaron
  QDQ INT8, QLinearOps INT8, FP16 y FP32 optimizado desde el FP32 congelado,
  con calibración sólo en train. QDQ/QLinearOps abren en ORT Python pero
  agregan falsos permisos; FP16 produce no finitos; FP32 optimizado conserva
  decisiones pero aumenta aproximadamente 24,5 MB y no tuvo ejecución directa
  en Android. Resultado `NO-GO`; R1, `final_sealed`, Android y DAG permanecen
  intactos. Evidencia:
  `docs/dag/v3/GLOSHIA_VISUAL_R2_1_ANDROID_EXPORT_GATE_11_2026-08-03.md`.
- `GLOSHIA-R2.1-ORT-ANDROID-HARNESS-12` (2026-08-03): el candidato INT8
  dinámico exacto de EXPORT-GATE-11 abrió y ejecutó `ConvInteger` directamente
  con ORT Android 1.27.0 CPU en el Samsung A23 (SM-A235M, Android 14), con
  salidas finitas y cierre de sesión correcto. Sobre los mismos 119 tensores
  congelados tuvo 0/18 falsos permisos, 11/101 falsos filtros y un desacuerdo
  de decisión frente a FP32 (1/119, en dirección de falso filtro de FP32 a
  allow). La tolerancia de equivalencia quedó fijada en cero, por lo que el
  resultado es `NO-GO` para canary aunque la compatibilidad de ConvInteger haya
  sido confirmada. R1, `final_sealed`, Android productivo y DAG permanecen
  intactos. Evidencia:
  `docs/dag/v3/GLOSHIA_R2_1_ORT_ANDROID_HARNESS_12_2026-08-03.md`.

Documentos vigentes:

- `docs/dag/v3/DAG_BROWSER_V3_FOUNDATION.md`;
- `docs/dag/v3/DAG_BROWSER_V3_IMAGE_PIPELINE.md`;
- `docs/dag/v3/DAG_BROWSER_V3_MODEL_DATASET_CONTRACT.md`;
- `docs/dag/v3/GLOSHIA_LAB_CALIBRATION_2026-07-31.md`;
- `docs/dag/v3/GLOSHIA_VISUAL_CANDIDATE_TRAIN_08_2026-08-02.md`;
- `docs/dag/v3/GLOSHIA_VISUAL_R2_HARD_NEGATIVE_REPAIR_09_2026-08-02.md`;
- `docs/dag/v3/GLOSHIA_VISUAL_R2_1_HARD_NEGATIVE_TRAIN_10_2026-08-03.md`;
- `docs/dag/v3/GLOSHIA_VISUAL_R2_1_ANDROID_EXPORT_GATE_11_2026-08-03.md`;
- `docs/dag/v3/GLOSHIA_R2_1_ORT_ANDROID_HARNESS_12_2026-08-03.md`;
- `docs/compatibility/results/dag-performance-history.md`.

## Decisiones de producto vigentes

- DAG es el unico navegador; no restaurar DAG 1 ni DAG 2.
- Glosh es el sistema completo, DAG su navegador protegido y GloshIA el
  analizador visual local.
- DAG usa el rol oficial de navegador con confirmacion Android.
- No usar Device Owner, MDM, Knox ni restablecimiento de fabrica.
- Video permanece bloqueado; clasificar fotogramas es otro ticket.
- No hacer push, PR, publicacion DEV ni Production sin un OK nuevo y explicito.

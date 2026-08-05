# HANDOFF ACTUAL - Glosh y DAG Browser

Fecha de corte: 2026-08-04

Este archivo contiene solo el estado tecnico vigente. El historial vive en
`docs/BACKLOG_PRODUCTO.md`, `docs/compatibility/results/` y Git. No reconstruir
el runtime actual desde versiones o worktrees historicos.

## Estado vigente al 2026-08-05: GloshIA Visual R3.1

- El modelo activo en `main` es GloshIA Visual R3.1, promovido desde el
  candidato hibrido INT8 del round30.
- Archivo: `tinyclip-r3-head-hybrid-int8.onnx`.
- SHA-256: `c8b64af8092d3718c58736a511c996d0d443dacf3eaa74620b1e5af439a3cd48`.
- Fallback de apertura: R1, sin ejecutar dos modelos por fotografia.
- Se preservan RGB 224x224, letterbox DAG, umbral `0,40`, ONNX Runtime
  Android 1.27.0 y CPU local.
- En el mismo examen, los falsos permisos no aumentaron y los falsos filtros
  bajaron de `3/21` a `2/21` y de `2/15` a `0/15`.
- La APK local de trabajo pasa a versionCode `108`, versionName
  `0.69.12-dev`. No se publico ni se hizo push.
- `final_sealed` permanece cerrado. Corpus, splits, informes y checkpoints
  privados se conservan para futuros entrenamientos.
- El APK DEV local de DAG 108 conserva R3.1 y agrega únicamente optimización de
  entrega: prioridad asíncrona para raster visibles y deduplicación de
  decisiones raster idénticas en vuelo. No cambia pesos, umbral, vistas,
  política ni el contrato fail-closed. Evidencia:
  `docs/compatibility/results/dag-browser-v108-performance-gloshia-2026-08-05.md`.

## Repositorio y flujo vigente

- Carpeta canonica: `/Users/yejielnehmad/Developer/content-filter`.
- Rama de trabajo: `main` local.
- `origin/main` conserva DAG 96 publicado. `main` local contiene DAG 107 como
  punto seguro confirmado: navegador de DAG 95, GloshIA R3 y transiciones de
  navegacion sin destellos ni cierres falsos por una señal Gecko ausente.
- App Usuario 307, App Admin 290 y DAG 96 estan publicadas en DEV. Production
  no fue modificada.
- DAG 96 (`0.69.0-dev`) es el canary DEV reversible de GloshIA R3. R1
  permanece empaquetado como fallback de apertura; no se ejecutan dos modelos
  por fotografia.
- DAG 107 (`0.69.11-dev`) esta instalado y confirmado por el propietario en
  SM-S908E. Conserva el retiro del autoactualizador y agrega una captura
  efimera de la pagina protegida durante navegaciones, mas una terminacion de
  carga que acepta el primer dibujo de Gecko o la cola visual protegida quieta.
  Todavia no fue publicado.
- Los APK finales se construyen solo desde `main` local integrado.
- Las apps Usuario/Admin no fueron modificadas. Supabase Storage DEV recibio
  unicamente la APK y el manifiesto de DAG 96.

### Almacenamiento local e iCloud

- La unica copia canonica esta en
  `/Users/yejielnehmad/Developer/content-filter`, fuera de iCloud Drive.
- No existe una copia del proyecto bajo `Documents` ni bajo
  `Library/Mobile Documents/com~apple~CloudDocs`. Codex conserva unicamente su
  carpeta de trabajo vacia en `Documents/Codex`; no contiene codigo, APKs ni
  datos del proyecto.
- La limpieza del 2026-08-03 redujo la carpeta canonica de aproximadamente
  `9,4 GB` a `1,4 GB`. Builds, caches, dependencias reinstalables y candidatos
  R2 descartados se movieron a
  `~/.Trash/content-filter-cleanup-20260803` (`6,4 GB`), recuperables hasta
  vaciar la Papelera.
- Se preservaron siete conjuntos privados necesarios para continuar GloshIA:
  corpus actual de 1.000, revision balanceada, hard negatives usados por R3,
  revision historica referenciada, etiquetas multisenal, revision enfocada y
  candidato R3. El split R3 conserva `526/526` imagenes disponibles.
- Los builds locales fueron retirados; la proxima compilacion los regenera. La
  APK DAG 96 publicada, GitHub, Supabase y los modelos R3/R1 versionados no se
  modificaron.

| Aplicacion | versionCode | versionName DEV | Estado |
| --- | ---: | --- | --- |
| App Usuario | 307 | 1.0.1-dev | Publicada en DEV |
| App Admin | 290 | 1.0.1-dev | Publicada en DEV |
| DAG Browser | 96 | 0.69.0-dev | Canary R3 publicado y verificado en DEV |

Candidato local confirmado: DAG Browser 107 (`0.69.11-dev`), instalado en
SM-S908E y no publicado. Evidencia:
`docs/compatibility/results/dag-browser-v107-safe-navigation-sm-s908e-2026-08-04.md`.

El SM-A235M `R58T34V31AE` conserva DAG 95 porque el propietario omitio la
repeticion A23. DAG 96 puede obtenerse desde Actualizaciones de App Usuario y,
una vez instalado, las versiones siguientes tambien desde el menu propio de
DAG. No se registra una instalacion fisica de DAG 96 en este cierre.

## DAG Browser vigente

### DAG 107 local confirmado: DAG 95, GloshIA R3.1 y navegacion estable

DAG 107 parte del navegador de DAG 95 y conserva R3.1 como modelo activo, con R1
como fallback si R3.1 no abre. El autoactualizador, permisos y recursos
agregados por DAG 96 permanecen retirados. La extension sigue en `1.50.0`;
solo cambio el archivo ONNX activo y su metadata, sin cambiar umbral,
politica ni decisiones de GloshIA.

La transicion conserva en memoria una captura de la pestaña activa ya
protegida y la muestra hasta que la pagina nueva queda segura. La captura se
invalida al cambiar de pestaña, navegar, pasar a segundo plano o liberar
memoria; no se persiste. La pagina nueva se revela solo con barrera confirmada
y una de dos señales: primer dibujo de Gecko o cola de imagenes protegidas
quieta. Esto evita el cierre incorrecto observado en Mimo cuando Gecko omite
`onFirstContentfulPaint`, sin liberar imagenes pendientes: cada raster mantiene
su compuerta individual fail-closed.

El propietario confirmo en SM-S908E que las transiciones mejoraron, la busqueda
desde DAG dejo de producir el destello previo y Mimo abre y funciona. Ktlint,
unitarios, Lint y build aprobaron. DAG 107 es el punto seguro local; DAG 96
sigue siendo la version DEV remota.

Pendiente GloshIA separado: R3 filtra incorrectamente tres banners comerciales
seguros observados en Mimo (bebe vestido, niño vestido y banner Mercado/Pagos).
El piloto humano de 40 muestras ya terminó: 39 binarias y 1 `doubt`; R1 y R3
tuvieron 0 falsos permisos y 6 falsos filtros, con accuracy 33/39 (84,62 %) y
balanced accuracy 55/60 (91,67 %) en ambos. Los seis falsos filtros de R3 se
concentraron en `retail_catalog_fashion`, incluidos maniquíes/catálogos
permitibles; no hay mejora global demostrada. Son hard negatives generales para
una candidata posterior; no crear reglas por Mimo, dominio, URL o edad y no
bajar directamente el umbral global.

El gate `GLOSHIA-R3-BALANCED-CORPUS-REVIEW-GATE-27` terminó la revisión de 295
muestras: 194 allow, 91 filter y 10 doubt. Sobre 285 binarias, R3 obtuvo 13
falsos permisos y 32 falsos filtros, balanced accuracy 84,61 % y PR-AUC
0,835803. El lote sirve para justificar la preparación de TRAIN-28, pero no
para entrenar todavía: quedó en `directed_review`, sin splits independientes,
con `training_rights_uncertain` y sin autorización de entrenamiento. La
recomendación es `GO` condicionado para proponer TRAIN-28 y `NO-GO` para usar
este lote directamente. `final_sealed` sigue cerrado.
Evidencia: `docs/dag/v3/GLOSHIA_R3_BALANCED_CORPUS_REVIEW_GATE_27_2026-08-04.md`.

La propuesta `GLOSHIA-R3-TRAIN-28` definió un pool nuevo aproximado de 400
muestras con splits agrupados y el examen gate 27 congelado como referencia
externa. Fue autorizada y ejecutada localmente; el cierre fue `NO-GO`.
Evidencia: `docs/dag/v3/GLOSHIA_R3_TRAIN_28_PROPOSAL_2026-08-04.md`.

TRAIN-28 fue ejecutado localmente y terminó `NO-GO`: 193 muestras útiles, 190
binarias, 3 dudas, 133/28/29 en train/validation/frozen_test y contaminación
aprobada. Los tres ensayos R3.1 no redujeron los falsos filtros de R3 en
validation; no se abrió el frozen_test de candidato, no se exportó ONNX y R3
permanece oficial. Evidencia:
`docs/dag/v3/GLOSHIA_R3_TRAIN_28_NO_GO_2026-08-04.md`.

El piloto privado `GLOSHIA-R3-HARD-NEGATIVE-REPAIR-PILOT-29` terminó con
234 binarias nuevas de round29, 36 hard cases ponderados y splits
`367/28/29` train/validation/frozen_test sin contaminación. El mejor candidato
visual FP32 redujo los falsos filtros de R3 de `3/15` a `1/15` en frozen_test,
con balanced accuracy `93,10 %` frente a `86,43 %` y PR-AUC `0,957952` frente a
`0,943326`, manteniendo `1/14` falsos permisos. Sin embargo, FP32 pesa
33.220.815 bytes frente a 10.469.698 de R3; INT8 dinámico falla con
`ConvInteger`, QDQ/QLinearOps agregan falsos permisos y las variantes híbridas
cambian decisiones fronterizas. Resultado obligatorio: `NO-GO` para canary o
reemplazo; R3 permanece oficial, `final_sealed` sigue cerrado y no se modificó
DAG 107. Evidencia:
`docs/dag/v3/GLOSHIA_R3_HARD_NEGATIVE_REPAIR_29_2026-08-05.md`.

El experimento privado `GLOSHIA-R3-ROUND30-BINARY-CANDIDATE` incorporó 251
decisiones binarias nuevas de round30 al train, excluyendo 4 `doubt`, y
preservó exactamente el examen histórico: 618 train, 28 validation y 29
frozen_test. La prueba de contaminación pasó para ID, SHA-256, pHash, grupo y
URL; `final_sealed` permaneció cerrado. Se ejecutaron tres pilotos CPU locales
con selección únicamente por validation. El piloto 03 fue el seleccionado:
validation mejoró de 3/21 a 1/21 falsos filtros y frozen_test de 2/15 a 1/15,
manteniendo 1/7 y 1/14 falsos permisos, respectivamente. Balanced accuracy y
PR-AUC también subieron en ambos exámenes.

La mejora visual no es todavía desplegable: el FP32 abre con ONNX checker y
ORT CPU pero pesa 33.220.815 bytes frente a 10.469.698 de R3; el INT8 dinámico
contiene `ConvInteger` y no abre en ORT CPU; la variante híbrida compacta cambia
2 de 57 decisiones frente a FP32. Resultado `NO-GO`: R3 continúa oficial e
intacto en DAG 107, sin APK, Android, umbral, política, Supabase, publicación
ni push. Informe reproducible:
`.codex-tmp/gloshia-r3-round30-binary-candidate-20260805/round30-binary-candidate-report.json`.

Un seguimiento de exportación encontró una candidata híbrida INT8 compacta del
mismo piloto: `9.668.603` bytes, SHA-256
`c8b64af8092d3718c58736a511c996d0d443dacf3eaa74620b1e5af439a3cd48`. Abre en
ORT CPU local y, contra el R3 oficial actual, mantiene los falsos permisos en
`1/7` validation y `1/14` frozen_test mientras reduce falsos filtros de `3/21`
a `2/21` y de `2/15` a `0/15`. Es `GO` condicionado para un harness Android
aislado; no está integrado ni aprobado para DAG. Evidencia:
`docs/dag/v3/GLOSHIA_R3_ROUND30_HYBRID_EXPORT_GATE_2026-08-05.md`.

### DAG 96 publicado en DEV: canary reversible de GloshIA R3

DAG 96 conserva sin cambios el pipeline de navegador y la extension `1.50.0`
de DAG 95. Cambia solamente el modelo activo a R3 hibrida, mantiene el umbral
`0,40` y conserva R1 como fallback si ORT no puede abrir R3. Tambien agrega la
actualizacion manual propia con verificacion de HTTPS, SHA-256, package name y
firma. Evidencia:
`docs/dag/v3/GLOSHIA_R3_REVERSIBLE_DEV_CANARY_25_2026-08-03.md`.

### Base heredada de DAG 95: rollback confirmado

DAG 95 revierte completamente el cambio funcional de DAG 94 y restaura el
pipeline de imagenes de DAG 92, ultimo punto aceptado por el usuario. Android no
permite instalar un `versionCode` menor sobre 94; por eso el rollback se publica
como 95 aunque su comportamiento vuelva a 92. GloshIA R1, sus pesos, umbrales y
decisiones no cambiaron.

DAG 94 queda retirado como version vigente. Su informe se conserva solamente
como evidencia historica y esta marcado `REVERTIDO`: la mejora de laboratorio
no resolvio el comportamiento observado por el usuario. Extension incorporada
en DAG 95: `1.50.0`. Evidencia actual:
`docs/compatibility/results/dag-browser-v95-rollback-sm-a235m-2026-08-03.md`.

DAG 96 conserva la unica compuerta GloshIA previa al render. Los bytes raster se
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
cerrados por la cobertura total. Extension incorporada: `1.50.0`.

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
La interfaz de DAG 95 conserva el boton de nueva pagina blanco, miniaturas
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

- 14 pruebas WebExtension y las unitarias Kotlin aprobadas; DAG 95 repitio
  Ktlint, Lint y build.
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
- tamaño: `129945445` bytes;
- SHA-256:
  `0bdfb98cee3b3d7693f8a6d110321f75578209427ce46d835ece2cee6a0b2c9e`.

Evidencia del punto local vigente:
`docs/compatibility/results/dag-browser-v107-safe-navigation-sm-s908e-2026-08-04.md`.
Contrato vigente: `docs/dag/v3/DAG_BROWSER_V3_IMAGE_PIPELINE.md`.

## Metricas

- `page_visible`: la estructura protegida ya puede usarse.
- `viewport_images_ready`: termino el trabajo visual de la ventana inicial y
  permanecio quieto 250 ms; no representa toda una pagina infinita.
- `page_analysis_ready`: `GeckoSession.onPageStop`; mide el ciclo de pagina y
  texto, no la inferencia de GloshIA.

Definicion: `docs/dag/v3/DAG_BROWSER_V3_PERFORMANCE_METRICS.md`.

## Estado de GloshIA visual

- Hay un unico modelo local; DAG 95 no cambia sus pesos ni umbrales.
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
- `GLOSHIA-R2.1-ANDROID-CROSS-DEVICE-GATE-13` (2026-08-03): el mismo APK,
  candidato y examen congelado se ejecutaron en el Samsung S22 Ultra
  (SM-S908E, Android 16). Las 119 probabilidades y decisiones del candidato
  fueron idénticas a las del A23: 0/18 falsos permisos, 11/101 falsos filtros y
  el mismo desacuerdo favorable frente a FP32. La latencia observada fue p50
  47,75 ms y p95 63,89 ms. Resultado: `GO` de compatibilidad entre dispositivos
  y `HOLD` para integración o apertura de `final_sealed` hasta congelar el
  criterio final de aceptación. El harness fue retirado del S22; R1 y DAG
  permanecen intactos. Evidencia:
  `docs/dag/v3/GLOSHIA_R2_1_ANDROID_CROSS_DEVICE_GATE_13_2026-08-03.md`.
- `GLOSHIA-R2.1-FINAL-SEALED-GATE-14` (2026-08-03): después de congelar el
  candidato, umbral, membresía y gates en el commit `0f18c86`, se abrió una
  única vez el examen final de 108 muestras. Hubo 77 allow, 30 filter y 1 doubt.
  R2.1 redujo falsos filtros de 24/77 a 8/77 y subió accuracy de 73,83 % a
  85,05 %, pero aumentó falsos permisos de 4/30 a 8/30 y redujo recall de
  `filter` de 86,67 % a 73,33 %. Resultado obligatorio: `NO-GO`; R1 continúa
  oficial. El examen queda consumido y no puede usarse para entrenamiento,
  calibración ni un nuevo gate desconocido. Evidencia:
  `docs/dag/v3/GLOSHIA_R2_1_FINAL_SEALED_GATE_14_2026-08-03.md`.
- `GLOSHIA-VISUAL-R2.2-TARGETED-REPAIR-15` y
  `GLOSHIA-R2.2-ANDROID-HARNESS-16` (2026-08-03): la auditoría confirmó que
  cuatro falsos permisos R2.1 eran equipos femeninos de ciclismo ocultos bajo
  la categoría genérica de grupos. R2.2 B, entrenada con 44 preetiquetas
  binarias nuevas y seis dudas excluidas, corrigió las cuatro escenas. En un
  holdout ciego nuevo de 40 muestras mantuvo 1 falso permiso como R1 y redujo
  falsos filtros de 5 a 3, con accuracy 90 % frente a 85 % de R1. El INT8
  exacto abrió en A23 y tuvo 0 falsos permisos sobre 119 tensores, pero agregó
  un falso filtro frente a FP32; el gate previo exigía equivalencia cero.
  Resultado: `NO-GO` técnico para integración; R1 y DAG 95 siguen oficiales.
  Evidencia:
  `docs/dag/v3/GLOSHIA_VISUAL_R2_2_TARGETED_REPAIR_2026-08-03.md`.
- `GLOSHIA-R2.2-EXPORT-EQUIVALENCE-17` (2026-08-03): sin reentrenar ni mover
  el umbral, se dejó en FP32 únicamente el `k_proj` del primer bloque y se
  mantuvo el resto de la cuantización dinámica. El artefacto selectivo pesa
  8.950.584 bytes, se reproduce byte por byte y pasó 119/119 decisiones frente
  a FP32 en A23 y S22, con 0 falsos permisos y salidas finitas. Latencia p50:
  323,62 ms en A23 y 35,65 ms en S22, comparable con R1 en las mismas sesiones.
  Estado: `GO` de exportación y compatibilidad; canary productivo aún pendiente.
  R1 y DAG 95 continúan oficiales. Evidencia:
  `docs/dag/v3/GLOSHIA_R2_2_EXPORT_EQUIVALENCE_17_2026-08-03.md`.
- `GLOSHIA-R2.2-REVERSIBLE-CANARY-18` (2026-08-03): el candidato selectivo se
  ejecuto en el S22 sobre 40 imagenes reales mediante la decodificacion,
  preprocesamiento, regiones y politica exactos de DAG. R1 y R2.2 acertaron
  34/40; R2.2 redujo falsos filtros de 4 a 3, pero aumento falsos permisos de 2
  a 3 y bajo el recall de `filter` de 83,33 % a 75,00 %. La politica p95 fue
  comparable (181,42 ms frente a 184,96 ms) y no hubo errores, pero el gate de
  seguridad obliga `NO-GO`. El APK, modelo e imagenes de laboratorio fueron
  retirados; R1 y DAG 95 siguen oficiales. Evidencia:
  `docs/dag/v3/GLOSHIA_R2_2_REVERSIBLE_CANARY_18_2026-08-03.md`.
- `GLOSHIA-R2.3-REGIONAL-SAFETY-REPAIR-19` (2026-08-03): se adquirieron 57
  escenas grupales independientes y, antes de entrenar, se fijo que personas
  diminutas o lejanas tipo "buscar a Wally" no deben provocar filtrado. Las
  preetiquetas quedaron 41 `allow`, 14 `filter` y 2 `doubt`. R2.3 B mantuvo
  0/10 falsos permisos en frozen_test, pero subio falsos filtros de 11 a 12.
  En un holdout regional nuevo repitio exactamente a R2.2: 2/4 falsos permisos
  y 2/10 falsos filtros, con balanced accuracy 65 %. Resultado `NO-GO`; no se
  exporto a Android ni se toco DAG. R1 sigue oficial. Evidencia:
  `docs/dag/v3/GLOSHIA_R2_3_REGIONAL_SAFETY_REPAIR_19_2026-08-03.md`.
- `GLOSHIA-R2.4-REGION-AWARE-TRAINING-GATE-20` (2026-08-03): se alineo el
  entrenamiento del mismo TinyCLIP con la topologia y los umbrales regionales
  de DAG. La candidata A mantuvo 0 falsos permisos y redujo falsos
  filtros de 20 a 16 en validation y de 24 a 18 en frozen_test. Luego se abrio
  un holdout nuevo de 40 casos binarios, deduplicado y sin series repetidas:
  R1 obtuvo 0/16 falsos permisos y 14/24 falsos filtros; R2.4 obtuvo 2/16 y
  11/24. Resultado obligatorio `NO-GO`; el examen queda consumido, R1 sigue
  oficial y no se toco Android. Evidencia:
  `docs/dag/v3/GLOSHIA_R2_4_REGION_AWARE_TRAINING_GATE_20_2026-08-03.md`.
- `GLOSHIA-R3-MULTI-SIGNAL-DATA-CONTRACT-21` (2026-08-03): se recuperaron 176
  revisiones historicas utilizables como etiquetas parciales para diez motivos.
  Las 77 decisiones `allow` aportan negativos; en las 99 decisiones de filtro
  solo los motivos marcados son positivos y los omitidos permanecen
  desconocidos. Escote/pecho, hombro/axila y codo alcanzan el piso piloto; las
  otras siete senales todavia no. Estado: `GO` para reetiquetado focalizado y
  `NO-GO` para entrenar. R1 continua oficial. Evidencia:
  `docs/dag/v3/GLOSHIA_R3_MULTISIGNAL_DATA_CONTRACT_21_2026-08-03.md`.
- `GLOSHIA-R3-FOCUSED-RELABEL-22` (2026-08-03): se completaron 88/88
  revisiones focalizadas y se resolvieron todas sus senales desconocidas. El
  propietario corrigio las revisiones 17, 36 y 83 a `allow`; el cierre queda
  en 85 `filter` y 3 `allow`. Las tres correcciones deben conservarse para
  reducir filtros de mas. La exportacion privada fue verificada, el enlace
  vencido y las 88 fotos temporales retiradas de Supabase. Estado: `GO` para
  preparar una candidata R3 balanceada y `NO-GO` para reemplazar R1. Evidencia:
  `docs/dag/v3/GLOSHIA_R3_FOCUSED_RELABEL_22_2026-08-03.md`.
- `GLOSHIA-R3-BOUNDED-HEAD-TRAIN-23` (2026-08-03): se entreno `R3 Head 01`
  con 407 muestras, preservando como `allow` las revisiones 17, 36 y 83. En
  validation redujo falsos filtros de 19 a 3 y en frozen test de 25 a 6, con
  cero falsos permisos en ambos. El INT8 selectivo mide 8.950.584 bytes; queda
  pendiente equivalencia y rendimiento en Android. Estado: `GO` de laboratorio,
  R1 continua oficial. Evidencia:
  `docs/dag/v3/GLOSHIA_R3_BOUNDED_HEAD_TRAIN_23_2026-08-03.md`.
- `GLOSHIA-R3-ANDROID-EQUIVALENCE-24` (2026-08-03): ejecutado en S22. INT8
  dinámico tuvo un falso permiso; FP32 fue exacto pero demasiado lento. La
  exportación híbrida de 10,47 MB quedó con 0 falsos permisos, 10 falsos
  filtros frente a 42 de R1 y p50 186,25 ms frente a 188,18 ms. Una única
  diferencia contra FP32 fue conservadora (`allow` a `filter`). Estado:
  `CONDITIONAL-GO` para repetir en A23; todavía no integrar en DAG. El APK y
  temporales fueron retirados del S22. No se tocó DAG.
  Evidencia:
  `docs/dag/v3/GLOSHIA_R3_ANDROID_EQUIVALENCE_24_PREP_2026-08-03.md`.
- `GLOSHIA-R3-COMMERCIAL-HARD-NEGATIVES-26` (2026-08-04): se ejecutó el
  diagnóstico de datos sin entrenar. La búsqueda pública devolvió material
  histórico para consultas modernas; 25 candidatos quedaron en cuarentena por
  actualidad primaria no demostrada y 1 variante se excluyó por pHash canónico.
  Quedaron 40 muestras evaluables de Wikimedia Commons, 26 clusters, todas
  `internal_evaluation_ok` pero `training_rights_uncertain`. La revisión humana
  terminó con 36 allow, 3 filter y 1 doubt. Sobre las 39 binarias, R1 y R3
  tuvieron la misma matriz: 0 falsos permisos y 6 falsos filtros; el error de
  R3 se concentró en catálogos de moda/maniquíes. Estado: `GO` para cerrar el
  diagnóstico y `NO-GO` para entrenamiento; `final_sealed` permanece cerrado.
  El siguiente paso requiere un lote independiente y balanceado, no reutilizar
  este examen como evaluación y no ajustar umbrales. Evidencia:
  `docs/dag/v3/GLOSHIA_R3_COMMERCIAL_HARD_NEGATIVES_26_DIAGNOSTIC_2026-08-04.md`.

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
- `docs/dag/v3/GLOSHIA_R2_1_ANDROID_CROSS_DEVICE_GATE_13_2026-08-03.md`;
- `docs/dag/v3/GLOSHIA_R2_1_FINAL_SEALED_GATE_14_FREEZE_2026-08-03.md`;
- `docs/dag/v3/GLOSHIA_R2_1_FINAL_SEALED_GATE_14_2026-08-03.md`;
- `docs/dag/v3/GLOSHIA_VISUAL_R2_2_TARGETED_REPAIR_2026-08-03.md`;
- `docs/dag/v3/GLOSHIA_R2_2_EXPORT_EQUIVALENCE_17_2026-08-03.md`;
- `docs/dag/v3/GLOSHIA_R2_2_REVERSIBLE_CANARY_18_2026-08-03.md`;
- `docs/dag/v3/GLOSHIA_R2_3_REGIONAL_SAFETY_REPAIR_19_2026-08-03.md`;
- `docs/dag/v3/GLOSHIA_R2_4_REGION_AWARE_TRAINING_GATE_20_2026-08-03.md`;
- `docs/dag/v3/GLOSHIA_R3_MULTISIGNAL_DATA_CONTRACT_21_2026-08-03.md`;
- `docs/dag/v3/GLOSHIA_R3_FOCUSED_RELABEL_22_2026-08-03.md`;
- `docs/dag/v3/GLOSHIA_R3_BOUNDED_HEAD_TRAIN_23_2026-08-03.md`;
- `docs/dag/v3/GLOSHIA_R3_ANDROID_EQUIVALENCE_24_PREP_2026-08-03.md`;
- `docs/compatibility/results/dag-performance-history.md`.

## Decisiones de producto vigentes

- DAG es el unico navegador; no restaurar DAG 1 ni DAG 2.
- Glosh es el sistema completo, DAG su navegador protegido y GloshIA el
  analizador visual local.
- DAG usa el rol oficial de navegador con confirmacion Android.
- No usar Device Owner, MDM, Knox ni restablecimiento de fabrica.
- Video permanece bloqueado; clasificar fotogramas es otro ticket.
- No hacer push, PR, publicacion DEV ni Production sin un OK nuevo y explicito.

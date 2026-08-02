# HANDOFF ACTUAL - Glosh y DAG Browser

Fecha de corte: 2026-08-02

Este archivo es la verdad tecnica vigente. El historial de producto vive en
`docs/BACKLOG_PRODUCTO.md`; las mediciones anteriores viven en
`docs/compatibility/results/` y en Git. No reconstruir el runtime actual desde
versiones historicas.

## Repositorio y flujo vigente

- Carpeta canonica: `/Users/yejielnehmad/Developer/content-filter`.
- Rama de trabajo: `main` local.
- `main` local queda 28 commits por delante de `origin/main` con el commit de
  cierre de este lote.
- No se hizo push, PR, publicacion DEV ni Production.
- El lote DAG 66 queda integrado en un unico commit local de cierre. No se hizo
  push y no se debe publicar sin una autorizacion separada.
- Los worktrees separados son historicos o auxiliares: no compilar ni instalar
  una entrega final desde ellos.
- Supabase Production no se toca. Este lote DAG no usa ni modifica Supabase.

Declaraciones de version actuales en el codigo:

| Aplicacion | versionCode | versionName DEV | Estado de este lote |
| --- | ---: | --- | --- |
| App Usuario | 307 | 1.0.1-dev | Sin cambios |
| App Admin | 290 | 1.0.1-dev | Sin cambios |
| DAG Browser | 66 | 0.46.0-dev | GloshIA local conectada como compuerta unica y validada en SM-S908E; no publicada |

DAG 66 esta instalado en el SM-S908E `R5CT717BZTZ`. Se instalo in-place, sin
borrar el perfil y sin tocar otras apps.

## DAG Browser 66 - GloshIA como compuerta unica

DAG 66 conecta el modelo local conservado en DAG 65 mediante una sola
intercepcion de respuesta antes del render. Los bytes raster se capturan una
vez, reciben una unica decision nativa y solo `model_allow` devuelve el original
exacto a Gecko. Un bloqueo confiable produce un PNG neutro proporcional sin
pixeles rechazados; error, timeout, saturacion, animacion o entrada invalida
fallan cerrados.

No se restauro la presentacion anterior: no hay observadores multimedia,
estados DOM de imagen, remapeo URL-elemento ni CSS sobre `img`, `image` o `svg`.
SVG e iconos vectoriales seguros siguen directos. Las imagenes abiertas como
pagina principal pasan por la misma compuerta. El trabajo queda acotado a 2 MiB
por recurso, 8 MiB capturados, 32 streams, 24 esperas, dos decisiones nativas y
512 decisiones efimeras por hash de contenido. La extension es `1.35.2`.

Validacion: 11 pruebas WebExtension, 147 unitarias Kotlin, Ktlint, Lint, APK y
`diff --check` aprobados. En el SM-S908E el arranque limpio de Mimo mostro la
pagina en 374 ms y resolvio la ventana inicial en 1.805 ms; Cheeky en
1.801/3.670 ms; Fravega en 834/8.943 ms por su rafaga amplia de recursos. Una
imagen directa permitida conservo el original y una conocida como filtrable se
reemplazo en 48 ms nativos. No hubo crash, ANR ni OOM. Evidencia:
`docs/compatibility/results/dag-browser-v66-gloshia-single-gate-sm-s908e-2026-08-02.md`.

Limites vigentes: animaciones fallan cerradas; `canvas` sigue oculto; no se
demostro cobertura equivalente para medios puramente `data:` o `blob:`. El
fixture HTTP local fue rechazado por la politica HTTPS y no se relajo. DAG 66
es candidato DEV local: no autoriza push, publicacion ni Production.

## DAG Browser 65 - base multimedia simple

DAG 65 elimina del recorrido activo de una imagen toda la infraestructura de
clasificacion mientras GloshIA esta desconectada. Gecko descarga y presenta
directamente `image` e `imageset`: la extension no captura la respuesta, no la
convierte a Base64, no solicita una decision nativa, no remapea su URL y no
escribe estados en el DOM. El CSS tampoco selecciona `img`, `image` ni `svg`.

La causa raiz del fallo intermitente de iconos era el recorrido residual de DAG
64: aun despues de autorizar los bytes, la extension debia volver a asociar la
URL con el elemento exacto. Una recarga o reemplazo dinamico podia ocurrir entre
ambos pasos y dejar el elemento nuevo en estado oculto. La correccion elimina
esa asociacion del modo sin clasificador, en vez de agregar excepciones por
sitio.

El codigo activo de red, puente y presentacion multimedia bajo de 2.563 a 156
lineas. En DEV tampoco se crea la sesion ONNX al abrir DAG. Se conservan de
forma aislada el bloqueo de video/audio/object, anuncios y el puente minimo de
estado de pagina y miniaturas. El modelo, su preprocesador y sus pruebas quedan
guardados para una futura reconexion, pero no participan en la navegacion.

Validacion automatica: 9 pruebas WebExtension, 144 unitarias Kotlin, Ktlint,
Lint y APK aprobados. Validacion fisica en el SM-S908E: Cheeky completo en cinco
de cinco recargas consecutivas; Mimo y Fravega completos; interfaz de YouTube
visible con contenido multimedia bloqueado; organizador de pestañas operativo;
sin crash, ANR ni OOM. Evidencia:
`docs/compatibility/results/dag-browser-v65-simple-media-baseline-sm-s908e-2026-08-02.md`.

DAG 65 muestra todas las imagenes y no es publicable como filtro. El siguiente
trabajo visual no debe reactivar el pipeline retirado: debe conectar GloshIA
como una unica compuerta aislada y medible sobre esta base.

## DAG Browser 64 - base multimedia separada

DAG 64 valida primero el navegador sin clasificacion visual. En la variante
DEV, `GLOSHIA_VISUAL_ENABLED=false`: las respuestas capturadas con un sobre de
transporte valido vuelven sin modificar a Gecko y no pasan por bounds,
preprocesamiento ni inferencia. Esta variante muestra todas las fotos y no se
puede publicar como filtro.

La separacion encontro la causa raiz de los huecos: Android autorizaba con
`classifier_bypassed_dev`, pero la extension no autenticaba ese motivo y lo
convertia en `error`. El contrato ya reconoce el motivo DEV y conserva el mismo
recorrido exacto de bytes usado por `model_allow`.

La presentacion se aplica ahora sobre cada elemento multimedia, nunca sobre su
contenedor. Una foto no puede ocultar logos, iconos, corazones, botones ni una
imagen hermana. SVG pasivos comprimidos o con recursos internos seguros se
admiten sin relajar el rechazo de scripts, entidades o referencias externas.

Validacion: 23 pruebas WebExtension aprobadas, una DOM opcional omitida; 151
unitarias Kotlin aprobadas; Ktlint, Lint y APK correctos. Mimo, Cheeky y
Fravega quedaron visualmente completos en el SM-S908E, sin reglas por sitio.
Evidencia:
`docs/compatibility/results/dag-browser-v64-media-baseline-sm-s908e-2026-08-02.md`.

El siguiente gate es volver a conectar GloshIA mediante el modo de
clasificacion ya separado y repetir la matriz. Hasta entonces DAG 64 es una
base de compatibilidad local, no un candidato de publicacion.

## DAG Browser 59 - base previa

DAG 59 reconstruye desde la raiz la barrera y presentacion multimedia. No
incluye reglas por Cheeky, Mimo, Fravega, modelo de telefono ni ningun otro
sitio. Un contrato automatico falla si se introduce una excepcion para esos
comercios o modelos fisicos conocidos.

El APK de instalacion directa apunta a telefonos modernos arm64 con Android 10
o posterior, aproximadamente la generacion 2020 en adelante. El algoritmo es
global dentro de esa plataforma; el A23 es el piso fisico de referencia, no una
rama especial. x86, 32 bits e iOS no forman parte de este APK. Si se agregan
otras arquitecturas deben distribuirse como artefactos separados para no
inflar cada instalacion.

Cambios generales:

- la respuesta HTTP(S) original queda retenida y solo se escribe a Gecko tras
  un `allow` nativo autenticado;
- `block`, error, timeout, documento vencido o cola llena escriben cero bytes;
- cada trabajo pertenece al `tabId` y al token exacto del documento superior;
  navegar, cerrar una pestaña o reconectar el puerto invalida y purga trabajo
  viejo;
- la cola JS admite cuatro decisiones nativas en vuelo; Android ejecuta dos
  inferencias y conserva como maximo ocho tareas esperando;
- el presupuesto nativo vence a los 2.250 ms y se vuelve a comprobar antes de
  Base64, bounds, preprocesamiento, cada inferencia y la decision final;
- se admiten hasta 128 handles de respuesta, pero los bytes retenidos tienen un
  presupuesto global de 8 MiB y un limite de 2 MiB por recurso;
- visible y cercano conservan FIFO, con promocion autenticada y una cuota que
  evita hambre del trabajo cercano;
- DAG no fuerza `loading=eager` ni `fetchpriority=high`; conserva lazy loading
  del sitio y solicita solamente `decoding=async` cuando falta;
- la barrera indexa solo medios reales, agrupa geometria por cuadro y anticipa
  640 px mediante `IntersectionObserver`, sin listener de scroll ni barridos
  globales;
- CSS, pseudo-elementos, iconos, fondos, listas y bordes ordinarios permanecen
  bajo el render nativo; anuncios se presentan desde un script aislado;
- una foto filtrada termina en una superficie opaca, estatica, sin texto ni
  icono. No usa los pixeles rechazados ni un blur costoso durante scroll;
- espera, filtro y error tecnico son estados visuales distintos. Un error del
  decoder no puede reemplazar un `block` confiable;
- controles funcionales seguros quedan por encima del placeholder sin liberar
  el raster rechazado;
- una imagen permitida ya no borra el estado de espera de una hermana todavia
  pendiente; el host se reconcilia por el conjunto completo de imagenes;
- metricas de cliente, presentacion y viewport cruzan el puente nativo solo
  cuando Android negocia diagnosticos DEV; el APK normal evita ese costo por
  imagen;
- buffers originales, RGB y normalizados se limpian cuando dejan de usarse;
- el cierre de Activity no cierra ONNX mientras exista una inferencia activa.

La extension incorporada pasa de `1.28.0` a `1.29.0`. DAG usa
`ensureBuiltIn(ExtensionLocation, ExtensionId)`: un perfil existente conserva
la extension si esa version ya esta instalada y recibe la nueva cuando cambia.
Esto evita reinstalacion innecesaria en cada apertura y evita que una
actualizacion in-place siga ejecutando scripts anteriores.

El modelo visual no cambio:

- archivo: `tinyclip-bounded-finetune-r1-int8.onnx`;
- SHA-256:
  `2d52bd9e5eb4cd448cb0d64a784b2ee6f761ad20e890c57b898fd7991d29a9ee`;
- umbral global: `0.4`;
- inferencia: local, sin API y sin costo por consulta.

## Validacion ya ejecutada

- sintaxis de `background.js` y `barrier.js`: correcta;
- harness WebExtension final: 21 pruebas aprobadas, incluidas seguridad de
  rafaga/FIFO y una pagina DOM real en Chrome, cero fallos u omitidas;
- 146 pruebas unitarias Kotlin aprobadas, cero fallos y cero omitidas;
- `ktlintCheck` y `lintDevDebug`: correctos;
- compilacion y empaquetado de `androidTest`: correctos;
- `assembleDevDebug`: correcto;
- sintaxis Python del fixture/resumidor y Bash del runner Android: correcta;
- `git diff --check`: correcto;
- auditoria estatica de cola, contadores de quietud, lifecycle y contrato de
  documento: sin otro error claro;
- instalacion in-place exclusiva de DAG: correcta;
- rol oficial de navegador, version instalada y estado final: correctos;
- matriz fisica limpia en Mimo, Fravega y Cheeky: sin crash, ANR, OOM,
  temperatura anormal ni salida inesperada;
- miniatura real de pestaña: captura `1080x2136`, reduccion y presentacion
  verificadas;
- benchmark A23: CPU correcto; XNNPACK mas lento y numericamente no equivalente;
  NNAPI no admite el grafo.

Artefacto local construido desde `main`:

- paquete: `com.contentfilter.dagbrowser.dev`;
- version: `59` / `0.39.0-dev`;
- tamaño: `121370189` bytes;
- SHA-256:
  `a04beb690d61e43b80f15727117adbf4215779c4723299d5c3b79c013a4c087a`;
- firma verificada, certificado SHA-256:
  `d51bc0dabd280ce1b0f098ae168eb57758faeba301156cde835737835f8a8832`.

El gate fisico autorizado quedo ejecutado. La evidencia completa esta en
`docs/compatibility/results/dag-browser-v59-media-pipeline-rebuild-sm-a235m-2026-08-01.md`.
Las muestras frias finales dieron:

- Mimo: `4.934 / 4.632 / 703 ms`, cuadros tardios `2,17 %`, p95 `12 ms`;
- Fravega: `7.726 / 18.787 / 732 ms`, cuadros tardios `3,28 %`, p95 `26 ms`;
- Cheeky: `16.031 / actividad continua / 2.239 ms`, cuadros tardios `0,79 %`,
  p95 `12 ms`.

El orden de cada trio es `pagina / fotos visibles / estructura visible`.
Fravega necesito una ventana de 55 s para demostrar quietud: el contador no
estaba trabado, el sitio seguia agregando recursos. Las variantes intermedias
quedan como diagnostico y sus porcentajes no se presentan como mejora general.

El fixture HTTPS local sigue limitado: la hoja autofirmada fue rechazada por
Gecko y DAG cerro la pagina de forma segura. No se instalo una CA ni se relajo
TLS. El laboratorio necesita un certificado confiable antes de ser un gate
determinista.

El laboratorio esta en `tools/dag_perf_lab/`. Nunca elige telefono
automaticamente, valida API 29+ y `arm64-v8a`, permite fijar un modelo exacto
solo para comparaciones repetibles, no borra perfiles, no toca Chrome, roles ni
certificados y guarda evidencia fuera de Git en `.codex-tmp/`.

## Metricas

- `page_visible`: la estructura protegida ya puede usarse; no afirma que todas
  las fotos esten resueltas.
- `viewport_images_ready`: termino el trabajo visual de la ventana inicial
  acotada y permanecio quieto 250 ms; no representa toda la pagina infinita.
- `page_analysis_ready`: `GeckoSession.onPageStop`; mide ciclo de pagina/texto,
  no inferencia de GloshIA.

Definicion completa:
`docs/dag/v3/DAG_BROWSER_V3_PERFORMANCE_METRICS.md`.

## Estado de GloshIA visual

- DAG conserva un unico modelo visual local y no cambia pesos ni umbrales. DAG
  66 DEV lo ejecuta como una compuerta unica previa al render, sin restaurar la
  presentacion DOM retirada en DAG 65.
- El laboratorio local de 1.000 miniaturas y la ronda humana permanecen como
  evaluacion, no como entrenamiento autorizado.
- La calibracion preliminar y el experimento privado R1 quedaron `NO-GO` para
  reemplazar el modelo Android. El examen final sigue sellado.
- Production continua sin autorizacion. El piloto DEV no demuestra cobertura
  universal ni elimina falsos permisos o falsos filtros.

Documentos vigentes:

- `docs/dag/v3/DAG_BROWSER_V3_FOUNDATION.md`;
- `docs/dag/v3/DAG_BROWSER_V3_IMAGE_PIPELINE.md`;
- `docs/dag/v3/DAG_BROWSER_V3_MODEL_DATASET_CONTRACT.md`;
- `docs/dag/v3/GLOSHIA_LAB_CALIBRATION_2026-07-31.md`;
- `docs/compatibility/results/dag-performance-history.md`.

## Decisiones de producto que siguen vigentes

- DAG es el unico navegador del proyecto; no restaurar DAG 1 o DAG 2.
- Glosh es el sistema completo; DAG es su navegador protegido y GloshIA es el
  analizador visual local.
- DAG debe usar el rol oficial de navegador con confirmacion Android.
- No usar Device Owner, MDM, Knox ni restablecimiento de fabrica.
- Video permanece bloqueado; su clasificacion por fotogramas es un ticket
  posterior y separado.
- No hacer push, PR, publicacion DEV ni Production sin un OK nuevo y explicito.

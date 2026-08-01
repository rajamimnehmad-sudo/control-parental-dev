# HANDOFF ACTUAL - Glosh y DAG Browser

Fecha de corte: 2026-08-01

Este archivo es la verdad tecnica vigente. El historial de producto vive en
`docs/BACKLOG_PRODUCTO.md`; las mediciones anteriores viven en
`docs/compatibility/results/` y en Git. No reconstruir el runtime actual desde
versiones historicas.

## Repositorio y flujo vigente

- Carpeta canonica: `/Users/yejielnehmad/Developer/content-filter`.
- Rama de trabajo: `main` local.
- `main` local queda 24 commits por delante de `origin/main` con el commit de
  cierre de este lote.
- No se hizo push, PR, publicacion DEV ni Production.
- El lote DAG 58 queda integrado en un unico commit local de cierre. No se hizo
  push y no se debe publicar sin una autorizacion separada.
- Los worktrees separados son historicos o auxiliares: no compilar ni instalar
  una entrega final desde ellos.
- Supabase Production no se toca. Este lote DAG no usa ni modifica Supabase.

Declaraciones de version actuales en el codigo:

| Aplicacion | versionCode | versionName DEV | Estado de este lote |
| --- | ---: | --- | --- |
| App Usuario | 307 | 1.0.1-dev | Sin cambios |
| App Admin | 290 | 1.0.1-dev | Sin cambios |
| DAG Browser | 58 | 0.38.0-dev | Gate local y fisico aprobados en SM-A235M |

DAG 58 esta instalado en el SM-A235M `R58T34V31AE`, conserva el rol oficial de
navegador y quedo en su Inicio seguro con una pestaña. El perfil DEV anterior
fue borrado una sola vez con autorizacion explicita; no se tocaron otras apps.

## DAG Browser 58 - candidato local

DAG 58 concentra la auditoria profesional de seguridad, fluidez y medicion. No
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
- se admiten hasta 64 handles de respuesta, pero los bytes retenidos tienen un
  presupuesto global de 8 MiB y un limite de 2 MiB por recurso;
- visible y cercano conservan FIFO, con promocion autenticada y una cuota que
  evita hambre del trabajo cercano;
- DAG no fuerza `loading=eager` ni `fetchpriority=high`; conserva lazy loading
  del sitio y solicita solamente `decoding=async` cuando falta;
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

La extension incorporada pasa de `1.27.0` a `1.28.0`. DAG usa
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
- harness WebExtension final: 19 pruebas aprobadas, cero fallos y una prueba
  DOM externa omitida por ser opt-in; la omitida no se cuenta como aprobada;
- 147 pruebas unitarias Kotlin aprobadas, cero fallos y cero omitidas;
- `ktlintCheck` y `lintDevDebug`: correctos;
- compilacion y empaquetado de `androidTest`: correctos;
- `assembleDevDebug`: correcto;
- sintaxis Python del fixture/resumidor y Bash del runner Android: correcta;
- `git diff --check`: correcto;
- auditoria estatica de cola, semaforo, lifecycle, contrato de documento y
  benchmark ONNX: sin otro error claro.
- instalacion y borrado exclusivo del perfil DEV de DAG: correctos;
- rol oficial de navegador, version instalada y estado final: correctos;
- matriz fisica limpia en Mimo, Fravega y Cheeky: sin crash, ANR, OOM,
  temperatura anormal ni salida inesperada;
- miniatura real de pestaña: captura `1080x2136`, reduccion y presentacion
  verificadas;
- benchmark A23: CPU correcto; XNNPACK mas lento y numericamente no equivalente;
  NNAPI no admite el grafo.

Artefacto local construido desde `main`:

- paquete: `com.contentfilter.dagbrowser.dev`;
- version: `58` / `0.38.0-dev`;
- tamaño: `121375543` bytes;
- SHA-256:
  `1246fb68e45ce5af422b48a417641456b22ae5291098e27737dc5411369a1444`;
- firma verificada, certificado SHA-256:
  `d51bc0dabd280ce1b0f098ae168eb57758faeba301156cde835737835f8a8832`.

El gate fisico autorizado quedo ejecutado. La evidencia completa esta en
`docs/compatibility/results/dag-browser-v58-physical-gate-sm-a235m-2026-08-01.md`.
Las muestras limpias dieron:

- Mimo: `4.238 / 3.536 / 683 ms`, cuadros tardios `1,77 %`, p95 `15 ms`;
- Fravega portada: `11.774 / 16.232 / 1.099 ms`, cuadros tardios `1,16 %`,
  p95 `12 ms`;
- Cheeky: actividad dinamica sin quietud completa / `2.231 ms` visible,
  cuadros tardios `1,17 %`, p95 `10 ms`.

El orden de cada trio es `pagina / fotos visibles / estructura visible`. Una
primera matriz de 18 recorridos quedo solo como diagnostico porque los intents
externos acumularon 20 pestañas; no se usan sus porcentajes como mejora final.
La estabilidad bajo esa presion fue correcta y las pestañas se cerraron desde
el propio organizador.

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

- DAG usa un unico modelo visual local; este lote optimiza transporte, cola y
  presentacion, no reentrena pesos ni cambia umbrales.
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

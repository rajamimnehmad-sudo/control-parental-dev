# DAG BROWSER — HANDOFF

Actualizado: 2026-08-16. Responsable: Jefe.

## Mision

Navegador protegido independiente: cobertura visual, GloshIA R3.1, imagenes,
video y herramientas Diagnostic. Gradle es aislado; usar siempre
`scripts/dag_gradle.sh`.

## Estado operativo

- Rama `main` local; lote DAG modificado y no publicado.
- DEV 218 candidata local; Diagnostic 82 automatico; extension integrada 2.0.39.
- Diagnostic es una herramienta temporal. LAB ya no se construye. Video de
  producto continua NO-GO.
- R3.1, su umbral y la politica de imagen siguen siendo la referencia vigente.
- Fotos estan mas maduras y validadas que video; no confundir Gate A de fluidez
  con una promocion general.

## Ticket activo — DAG-VIDEO-03

Objetivo: reproduccion continua con una politica universal y cierre fail-closed.

Decision vigente: modo adaptativo, sin excepciones por sitio o formato. Los dos
primeros cuadros se analizan bajo cobertura. Si ambos quedan permitidos, el
video original reproduce continuamente con su audio y DAG toma una muestra cada
500 ms. Un bloqueo o error hace que Android cubra y pause primero; luego se
retira el permiso visual exacto. No se transporta ni recodifica el video entero.

Este modo prioriza fluidez y costo. Su riesgo explicito es una latencia de
deteccion aproximada de 500 ms mas inferencia; no garantiza cero aparicion entre
muestras. El pipeline estricto con buffer A/V queda como alternativa futura, no
esta conectado al runtime actual.

## Evidencia actual

Diagnostic 77, extension 2.0.35, paso la prevalidacion automatica y una corrida
fisica dirigida en A23 `SM-A235M` con YouTube normal Big Buck Bunny:

- cobertura nativa en 16 ms y `smooth_started` a 1.871 ms desde la seleccion;
- dos capturas distintas confirman movimiento visible y Android abrio audio de
  medios a 48 kHz, sin mute del sistema;
- 78 cuadros solicitados/capturados: 77 `model_allow` y un `model_filter` real;
- captura p50 6 ms/p95 7 ms; inferencia p50 145 ms/p95 168 ms; muestreo real
  p50 713 ms;
- el modelo cerro seguro al score 0,440; cero crash y cero ANR;
- consumo puntual durante reproduccion: 13,3% CPU, PSS 257 MiB y RSS 275 MiB.

Antes del telefono, el emulador `Glosh_DAG_API_35` reprodujo HTML5 y YouTube,
confirmo imagen/audio y encontro una brecha general de seek. Diagnostic 77 ahora
cierra `seek_requested` ante `seeking`; la regresion y la prueba del emulador
quedaron verdes. Gates finales: JS 94/94, unitarios Diagnostic, ktlint, lint y
assemble Diagnostic. APK local SHA-256
`072e98c9292feefa531bd8ddb0587f952887787dc9998f92e66db6281293b64d`.

La comprobacion humana en S22 `SM-S908E` / Android 16 rechazo Diagnostic 77: con
el arnes activo no hubo imagen ni audio. La caja negra `DAG-397U67SV` demostro
cuatro cierres identicos: primer cuadro capturado y permitido, seguido 10-18 ms
despues por `viewport_changed`. No fue modelo, rendimiento ni formato.

La causa era general: si el video ya tenia fuente estable antes de la cobertura,
la maquina no marcaba el arranque como estable y clasificaba su primer ajuste
geometrico posterior como una segunda transicion. Diagnostic 78 / extension
2.0.36 agrega el estado faltante y conserva la regla de una sola transicion
acotada bajo cobertura, con fuente, capacidades y geometria revalidadas.

Prevalidacion de Diagnostic 78:

- replay determinista del orden S22 y suite JS 94/94 verdes;
- unitarios DEV/Diagnostic, ktlint, lint y assemble Diagnostic verdes;
- dos corridas limpias en emulador API 35, una con perfil S22 y otra normal:
  ambas llegaron a `viewport_transition_stable`, imagen visible, audio activo y
  muestreo continuo, sin `viewport_changed`, crash ni ANR;
- una corrida automatica A23 llego a las dos transiciones estables,
  `smooth_audio_restored`, `smooth_started` y 25/25 cuadros permitidos, con
  imagen fisica visible y audio activo;
- APK local SHA-256
  `d69c90d6587a3f83c3c6659a2241b427ab0bc53b65aebea1e5c86455b5cedbc5`.

La comprobacion humana final de Diagnostic 78 en S22 paso con imagen y audio.
La caja negra `DAG-L4LLRNSV` confirma el mismo resultado: 28 cuadros solicitados
y capturados, 27 permitidos y uno bloqueado por `model_filter` (score 0,761),
seguido por cierre durable al navegar. `smooth_started` ocurrio 687 ms despues
de pedir cobertura; captura p50 6 ms/p95 8 ms e inferencia p50 42,7 ms/p95
53,6 ms. No reaparecio `viewport_changed`, no hubo eventos perdidos y el
diagnostico no muestra crash, ANR ni fuga posterior al bloqueo.

La diferencia DEV/Diagnostic quedo explicada: deteccion del `loadstart` de
MediaSource, tolerancia de la primera transicion cubierta y bloqueo terminal de
presentacion insegura estaban condicionados por `diagnosticsEnabled`. No eran
telemetria pura y por eso DEV no reproducía la misma maquina estable.

Diagnostic 79 promovio esas tres reglas al runtime universal, conserva solo los
logs bajo la bandera Diagnostic y activa el filtro automaticamente en documentos
HTTPS superiores elegibles. Los controles de laboratorio quedaron ocultos. El
replay de producto con `diagnostics=false`, unitarios DEV/Diagnostic,
ktlint, lint y assemble pasan. Una unica corrida A23 sin abrir el menu alcanzo
bootstrap estable, dos cuadros iniciales permitidos, `smooth_started`, imagen
visible, audio 48 kHz y muestreo sostenido; sin crash ni ANR.

Diagnostic 80 corrige el cierre al cambiar de pestaña: si la prueba durable de
revocacion llega despues de iniciada la espera, Android la acepta solo cuando
coinciden exactamente cuadro y token. En A23 el primer video cerro
`tab_switched -> revoke_ack`, sin timeout. Tras limpiar los datos de la app de
prueba, un segundo YouTube distinto arranco automaticamente, llego a
`smooth_started` y bloqueo un cuadro por `model_filter`. Una corrida intermedia
con 39 pestañas acumuladas agoto el decoder VP9 del A23 (`NO_MEMORY`); no fue una
regresion del filtro y desaparecio al volver a una pestaña limpia.

Diagnostic 82 corrige el bloqueo permanente denunciado en S22. Habia dos rutas:
un `model_filter` pausaba y cubria el video pero no retiraba su registro, y un
fallo posterior de confirmacion dejaba deliberadamente la cobertura nativa para
siempre. Ahora el bloqueo del modelo oculta el cuadro, retira la autoridad exacta
y espera el acuse durable. Si ese acuse no puede demostrarse, Android descarta
la pestaña y sesion exactas antes de quitar la cobertura y recupera una pagina
limpia; nunca exige borrar datos de navegacion. En A23 una corrida automatica
reprodujo un bloqueo real a score 0,448: `frame_blocked -> closing -> revoke_ack
-> retired`; un cierre tardio sin entrega activo la recuperacion secundaria.
Tres segundos despues no habia cobertura, la pagina nueva era utilizable y el
proceso seguia vivo, sin crash ni ANR. Suite JS 64/64 y contratos dirigidos
verdes. Gates finales DEV/Diagnostic: unitarios, ktlint, lint y ambos assemble
completos. DEV 218 SHA-256
`5a07356f9a3c5ff6471a66820c5f7da79ea3801c5a1da46b09a35f3a463b76ef`.

La saturacion tambien cerro una deuda general de pestañas: el A23 no sostiene de
forma fiable decodificadores VP9 en tres sesiones Gecko abiertas. La politica
local ahora conserva abierta solo la pestaña activa; las otras mantienen sus
metadatos y miniatura y vuelven a cargar bajo las barreras al seleccionarlas.
Este cambio esta validado localmente y en una DEV 217 integrada sobre A23, sin
abrir ningun menu de laboratorio. YouTube normal pidio cobertura automaticamente
y, tras el gesto estandar de Play, dos capturas separadas confirmaron movimiento
visible; el audio quedo activo a 48 kHz, sin crash ni ANR. PSS puntual: 235 MiB;
RSS: 339 MiB. El OMX VP9 del A23 rechazo varias aperturas con `NO_MEMORY`, pero
Gecko recupero cada una con `c2.android.vp9.decoder` por software y la
reproduccion continuo. Es una advertencia de eficiencia del dispositivo, no una
falla funcional de esta corrida. La app se detuvo al terminar y no se publico
ni entrego la APK.

Limpieza del lote: se retiraron los prototipos aislados de WebM, transporte y
benchmark que nunca estuvieron en el manifiesto ni conectados a la Activity,
junto con sus pruebas duplicadas. La decision arquitectonica queda conservada
en `DAG_VIDEO_FLUID_ARCHITECTURE.md`; la suite activa de producto queda 60/60,
y los unitarios DEV/Diagnostic y ktlint siguen verdes. No se genero otra APK.

`DAG-STRUCTURE-01` completo doce cortes neutrales: diagnostico, identidad de
fuente/geometria/viewport, presentacion preventiva, estado por video,
clasificacion de mutaciones, aislamiento multimedia y transiciones bootstrap
mas reinicio de autoridad, revocacion durable, reproduccion fluida, captura y
transiciones de viewport salieron a modulos acotados. `video-lab.js` bajo de
1.758 a 790 lineas sin mover la autoridad ni
el cierre seguro. La carga explicita, los contratos directos y el flujo de
producto quedan en 64/64;
unitarios DEV/Diagnostic, ktlint y lint DEV/Diagnostic tambien pasan. Sin APK ni
prueba fisica porque no cambio el comportamiento.

Progreso ejecutivo DAG Video: 99%.

## Siguiente hito — matriz y promocion

1. Mantener Diagnostic 82 como referencia automatica mientras se completa la
   investigacion; no enviar APK por ahora.
2. Cuando se cierre el lote, compilar una sola DEV con el mismo runtime universal
   y hacer una unica comprobacion humana S22.
3. Shorts, anuncios, TikTok e Instagram siguen NO PROBADOS/NO-GO si no exponen
   un elemento de video estable; no crear excepciones por proveedor.
4. Si la matriz suficiente queda verde, integrar el mismo modo a DEV y generar
   una unica APK candidata. Diagnostic sigue siendo herramienta temporal.

## Riesgos y deuda

- El muestreo de 500 ms puede omitir apariciones muy breves; un detector de
  cambio barato por fotograma aun no esta implementado ni calibrado.
- El muestreo real del A23 fue de unos 713 ms, no 500 ms exactos, porque incluye
  captura e inferencia. Ese es el riesgo temporal que debe comunicar producto.
- `DagBrowserActivity.kt` supera 4.000 lineas y no debe recibir otra
  responsabilidad. El runtime fluido debe vivir en componentes separados.
- `video-lab.js` quedo en 790 lineas y `DAG-STRUCTURE-01` esta completo. Las
  responsabilidades nuevas deben entrar en los modulos acotados existentes o
  justificar un modulo nuevo; no volver a inflar el orquestador.
- Los prototipos WebM/transporte/binario siguen como investigacion aislada y
  fuera del manifiesto/Activity; no conectarlos sin otra decision de arquitectura.
- Shorts reemplaza el elemento DOM y sigue NO-GO. Anuncios, Instagram Reels y
  TikTok siguen no probados. Ninguno recibira una excepcion de proveedor.
- Una URL HTTPS que apunta directamente a un archivo MP4 queda fail-closed antes
  del pipeline. La causa ya esta identificada: Gecko la convierte en un visor
  multimedia interno donde la extension no inyecta el runtime, mientras
  `webRequest` cancela correctamente el `main_frame` con MIME de video porque no
  existe grant. Soportarlo requiere un adaptador universal de documento de media
  o un reproductor nativo protegido; no se resolvera por extension de archivo ni
  permitiendo el visor crudo.

## Operacion

Automatizar por ADB instalacion, apertura, toques, logs, capturas y cierre.
Pedir al usuario solo una accion fisica inevitable y agruparla. Sin push, PR,
publicacion, Production ni borrado de datos sin OK explicito actual de Jefe.

La historia detallada queda en
`EVIDENCE_2026-08-13_DAG-VIDEO-01B-CLOSE.md`; no volver a inflar este handoff con
la cronologia completa.

# DAG BROWSER — HANDOFF

Actualizado: 2026-08-16. Responsable: Jefe.

## Mision

Navegador protegido independiente: cobertura visual localizada, GloshIA R3.1,
imagenes, GIF y video. Gradle es aislado; usar siempre
`scripts/dag_gradle.sh`.

## Estado operativo

- Rama `main`, 22 commits por delante de `origin/main`; sin push ni publicacion.
- DEV 221 / 0.70.23 es la candidata integrada local. Diagnostic 102 y extension
  2.0.55 quedan como herramientas locales; contienen el rearme de sesion,
  viewport estable y espera acotada de superficie Gecko vigentes.
- Fotos y GIF estan mas maduros que video. YouTube normal funciona localmente;
  video general y reproductores con DOM dinamico siguen NO-GO.
- Progreso real: DAG Video premium 84%. La matriz minima superior queda cerrada;
  video general sigue NO-GO para categorias aun no cubiertas.

## Ultimo hito — DAG-VIDEO-SESSION-REARM-01

Objetivo universal, sin excepciones por pagina, formato o proveedor:

1. Todo video permanece oculto por CSS de origen usuario desde `document_start`.
2. Un video visible sin fuente puede iniciar una sola vez, mudo y por hasta
   2,5 s, solo para que el reproductor cree su backing media.
3. Al aparecer la fuente se pausa inmediatamente. No se abre permiso de pixels.
4. Si fuente y geometria siguen exactas, se crea una autoridad nueva y recien
   entonces Android coloca la cobertura localizada y analiza.
5. Reemplazos DOM previos al primer grant pueden promoverse de inmediato solo
   cuando pasan de sin-fuente a con-fuente y conservan geometria exacta.
6. Segunda fuente, geometria distinta, limite o ambiguedad siguen fail-closed.

### Evidencia actual

- El diagnostico real del S22 mostro que limpiar cache no era la causa: despues
  de `viewport_changed -> revoke_ack`, la politica no habilitaba otra autoridad.
- La politica ahora rearma cambios seguros de viewport, fuente o elemento solo
  despues del cierre exacto; errores de seguridad y bloqueos siguen terminales.
- Las pestañas conservan el ultimo puerto de documento superior y eliminan la
  referencia al desconectar, hibernar o cerrar la sesion.
- Una auditoria fisica encontro un bucle de rearmado durante scroll. Diagnostic
  101 agrupa el burst y espera 150 ms estables antes de una unica seleccion.
- Gate final: JS 88/88, unitarios dirigidos DEV/Diagnostic, ktlint, lint vital y
  assemble Diagnostic verdes.
- A23 sin borrar datos: primer YouTube llego a `smooth_started`; un scroll dio
  un solo `viewport_changed -> revoke_ack -> config_enabled -> smooth_started`;
  al abrir un segundo video distinto, `active_video_mutated` cerro limpio y la
  revision nueva alcanzo `cover_requested -> smooth_started` con imagen visible.
- La matriz encontro que un video HTML5 muy temprano podia pedir cobertura antes
  de que Gecko expusiera su matriz de superficie. Diagnostic 102 reutiliza el
  limite Android ya vigente: hasta 10 reintentos cada 50 ms, siempre con el video
  oculto. W3Schools paso de `invalid_surface_rect` a `cover_armed`; su archivo
  remoto no entrego datos reproducibles y cerro seguro por `frame_ready_timeout`.
- Regresion A23 de Diagnostic 102: YouTube Big Buck Bunny alcanzo cobertura en
  109 ms, dos cuadros permitidos, `smooth_started`, imagen visible y muestreo
  continuo sin crash ni ANR.
- Auditoria de cierre: HTML5 superior ya habia completado 120/120 cuadros en A23;
  el replay automatico actual cubre la experiencia fluida y los gates quedaron
  88/88. Los intentos nuevos no contradijeron esa evidencia: fallaron por recurso
  remoto sin datos, salida en iframe o documento multimedia especial.
- DEV 221 paso 88/88 JS, unitarios DEV/Diagnostic, ktlint, lint DEV y assemble.
  Instalado sin borrar datos en A23, YouTube mostro imagen fluida y Android
  confirmo salida AAudio de medios, estereo 48 kHz. Sin crash ni ANR.

### Proximo paso

Guardar el lote integrado local y continuar con la limpieza de integracion DAG
(retirar referencias LAB antiguas). URLs MP4 directas, iframes, Shorts, anuncios,
Instagram y TikTok siguen NO PROBADOS o NO-GO y requieren tickets separados,
sin excepciones por sitio.

## Video normal vigente

- Dos cuadros iniciales se analizan bajo cobertura nativa.
- Si ambos se permiten, el video original reproduce con audio.
- DAG muestrea cada 500 ms; en A23 la cadencia real medida fue ~713 ms.
- Un bloqueo/error cubre y pausa antes de retirar el permiso visual exacto.
- YouTube normal completo 120 muestras estables con cierre `revoke_ack`.
- El modo fluido acepta el riesgo de deteccion aproximada de 500 ms mas
  inferencia; no garantiza detectar una aparicion de pocos milisegundos.

## GIF vigente

GIF seguro ya se entrega animado. Limites: 2 MiB, 120 cuadros y 60 s. Todos los
cuadros se recorren con detector barato; el modelo pesado corre a 2 fps y ante
cambio material, con maximo 10 inferencias. Error, riesgo o complejidad mayor
reemplaza solo el GIF. WebP/AVIF animados quedan para otro ticket.

## Backlog aprobado

- `DAG-VIDEO-SAFE-SKIP-01`: cuando un tramo se bloquee, mantener cobertura
  localizada, avanzar en pasos acotados, analizar y reanudar en el primer tramo
  seguro. Mostrar dentro del video una nota breve: “Se salto una parte no
  permitida”. Evitar bucles, saltos infinitos y excepciones por proveedor.
- `DAG-PARTIAL-REDACTION-01`: blur o pixelado parcial mediante segmentacion.
- Adaptador universal para URLs directas MP4 y visor multimedia de Gecko.

## Riesgos y reglas

- El muestreo puede omitir apariciones muy breves; GIF ya tiene detector barato
  por cuadro, video todavia no.
- `DagBrowserActivity.kt` supera 4.800 lineas: no agregar responsabilidades;
  usar politicas/componentes nuevos.
- `video-lab.js` tiene 812 lineas. No agregar otra responsabilidad; el proximo
  cambio funcional debe abrir primero el ticket de division neutral.
- Tras tres intentos sin cambiar hito o decision, auditoria enfocada antes de
  otra edicion, build o APK.
- Automatizar ADB, agrupar pruebas y pedir intervencion solo si es inevitable.
- Sin push, PR, publicacion, Production ni borrado de datos sin OK actual.

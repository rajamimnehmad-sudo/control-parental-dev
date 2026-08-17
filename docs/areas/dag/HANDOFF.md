# DAG BROWSER — HANDOFF

Actualizado: 2026-08-16. Responsable: Jefe.

## Mision

Navegador protegido independiente: cobertura visual localizada, GloshIA R3.1,
imagenes, GIF y video. Gradle es aislado; usar siempre
`scripts/dag_gradle.sh`.

## Estado operativo

- Rama `main`, 29 commits por delante de `origin/main`; sin push ni publicacion.
- DEV 225 / 0.70.27 es la candidata integrada local. Diagnostic 104 y extension
  2.0.59 quedan como herramientas locales; contienen el rearme de sesion,
  viewport estable y espera acotada de superficie Gecko vigentes.
- Fotos y GIF estan mas maduros que video. YouTube normal funciona localmente;
  video general y reproductores con DOM dinamico siguen NO-GO.
- Progreso real: DAG Video premium 90%. La matriz minima superior queda cerrada;
  video general sigue NO-GO para categorias aun no cubiertas.

## Ultimo hito — DAG-VIDEO-SAFE-SKIP-STABILITY-02

- El segundo video negro no era cache: al iniciar un salto seguro quedaba vivo
  el temporizador del cuadro rechazado. Vencia 2,5 s despues y cerraba una
  reproduccion que ya se habia recuperado.
- El salto ahora cancela ese temporizador antes de cambiar de estado y muestra
  dentro del rectangulo del video una nota breve: “Se salto una parte no
  permitida”. La pagina permanece utilizable.
- Regresion automatica exacta y gates completos verdes: JS 95/95, unitarios
  DEV/Diagnostic, ktlint, lint de ambas variantes y assemble Diagnostic.
- A23 con datos preservados: segundo YouTube visible, audio restaurado y muestreo
  continuo; no reaparecio `frame_result_timeout`, crash ni ANR.

## Hito — DAG-PRIVACY-DIAGNOSTICS-01

- Los eventos de medios ya no heredan la privacidad de la pestaña visible. Cada
  evento se acepta solo si Android vinculo su documento superior exacto con una
  pestaña no privada; identidad desconocida o privada se descarta.
- Una finalizacion tardia de una pestaña privada no puede registrarse aunque el
  usuario haya cambiado a una pestaña normal.
- Prevalidacion: JS 95/95, unitarios DEV/Diagnostic y ktlint verdes. No requirio
  APK, cambio de version ni prueba fisica.

## Hito — DAG-STRUCTURE-02

- El registro de eventos DOM y globales salio a `video-lab-events.js`; el parseo
  de configuracion quedo en `video-lab-configuration.js`.
- `video-lab.js` bajo de 832 a 799 lineas. No cambiaron tiempos, politica,
  audio, filtrado ni comportamiento por sitio.
- Gates: JS 95/95, unitarios DEV/Diagnostic 213/213, ktlint y lint de ambas
  variantes verdes. Sin version nueva ni APK.

## Hito — DAG-UI-PREMIUM-01

- Navegacion normal e incognito usan chrome claro, barra compacta, controles
  consistentes y una paleta accesible. Pestañas, historial y favoritos usan
  tarjetas reales con jerarquia y espaciado comunes.
- La pantalla de inicio quedo clara y liviana. La cobertura de seguridad de
  video conserva su color oscuro/bordo y su alcance localizado.
- DEV 224 paso unitarios DEV/Diagnostic, ktlint, lint y assemble. Validacion A23:
  inicio, pagina real, selector de pestañas, menu e incognito sin crash ni ANR.

## Hito — DAG-VIDEO-SESSION-REARM-01

### Correccion S22 — bloqueo localizado y salto seguro

- El reporte `DAG-HY6MNGK2` demostro 10/10 arranques fluidos, pero despues de un
  `frame_blocked` la generacion quedaba terminal y los videos posteriores del
  mismo documento no volvian a seleccionarse. Borrar cache solo forzaba un
  documento nuevo; no era la causa.
- DEV 223 mantiene cubierta solo la region del video y, ante un cuadro filtrado,
  avanza 2 s bajo cobertura, espera 150 ms estables y analiza de nuevo. Repite
  como maximo cinco veces; nunca revela un punto rechazado.
- Si no hay tiempo seekable, cambia la autoridad o se agota el limite, queda en
  cuarentena solo esa generacion. Una fuente, generacion o elemento nuevo puede
  rearmarse despues del cierre durable; el resto de la pagina no queda condenado.
- Contrato universal, sin excepciones por pagina, proveedor ni formato.
- Prevalidacion final: JS 95/95, unitarios DEV/Diagnostic, ktlint, lint DEV y
  assemble DEV verdes. APK DEV 223 reconstruida; falta prueba humana del caso S22.

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

## Hito — DAG-VIDEO-STRUCTURE-01

- El protocolo, nombres de mensajes e identidad exacta de autoridad salieron a
  `video-protection-protocol.js`; `video-lab.js` bajo de 812 a 799 lineas.
- No cambio reproduccion, politica, tiempos ni excepciones por sitio.
- Prevalidacion: JS 89/89; unitarios DEV 211/211 y Diagnostic 211/211; ktlint,
  lint DEV/Diagnostic y assemble DEV verdes.
- A23 con datos conservados: YouTube normal mostro cuadros sucesivos visibles,
  audio multimedia estereo 48 kHz y ningun crash/ANR. DEV 222 quedo instalada y
  detenida al finalizar.

### Proximo paso

Construir DEV 225 desde `main` integrado y hacer una unica validacion humana en
S22: tramo filtrado, nota breve, salto seguro y segundo video sin borrar datos.
URLs MP4 directas, iframes, Shorts, anuncios, Instagram y TikTok siguen NO
PROBADOS o NO-GO y requieren tickets separados, sin excepciones por sitio.

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

- `DAG-PARTIAL-REDACTION-01`: blur o pixelado parcial mediante segmentacion.
- Adaptador universal para URLs directas MP4 y visor multimedia de Gecko.

## Riesgos y reglas

- El muestreo puede omitir apariciones muy breves; GIF ya tiene detector barato
  por cuadro, video todavia no.
- `DagBrowserActivity.kt` supera 4.800 lineas: no agregar responsabilidades;
  usar politicas/componentes nuevos.
- `video-lab.js` tiene 799 lineas tras separar eventos y configuracion. No sumar
  responsabilidades nuevas al coordinador; las nuevas politicas deben vivir en
  componentes propios.
- Tras tres intentos sin cambiar hito o decision, auditoria enfocada antes de
  otra edicion, build o APK.
- Automatizar ADB, agrupar pruebas y pedir intervencion solo si es inevitable.
- Sin push, PR, publicacion, Production ni borrado de datos sin OK actual.

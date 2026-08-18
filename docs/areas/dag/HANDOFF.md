# DAG BROWSER — HANDOFF

Actualizado: 2026-08-18. Responsable: Jefe.

## Mision

Navegador protegido independiente: cobertura visual localizada, GloshIA R3.1,
imagenes, GIF y video. Gradle es aislado; usar siempre
`scripts/dag_gradle.sh`.

## Estado operativo

- Rama local `work/chrome-visual`; sin push ni publicacion.
- GloshIA Visual R3.1 fue extraido a `gloshia-visual-core`. DAG consume el mismo
  modelo y politica mediante adaptadores finos. Paridad automatica y score real
  A23 pasaron; no cambio ninguna decision ni experiencia DAG.
- DEV 230 / 0.70.32 es el siguiente candidato local. Diagnostic 115 y extension
  2.0.64 contienen el lote; no hubo publicacion.
- Fotos y GIF estan mas maduros que video. YouTube normal funciona localmente;
  video general y reproductores con DOM dinamico siguen NO-GO.
- Progreso real: DAG Video premium 94%. La matriz minima superior queda abierta
  hasta la comprobacion humana de DEV 230 en S22;
  video general sigue NO-GO para categorias aun no cubiertas.

## Ultimo hito — DAG-VIDEO-NATIVE-EXPERIENCE-01

- Dos diagnosticos S22 de DEV 228 mostraron la misma causa de pantalla negra:
  un gesto de seek generaba un cierre correcto, reabria a los 150 ms y una
  segunda pareja `seeking/seeked` terminaba en `revoke_timeout`.
- El seek ahora permanece como una sola autoridad cerrada hasta 750 ms estables;
  eventos adicionales reinician la ventana sin crear un segundo cierre.
- Se retiro por completo la UI de fullscreen inventada: boton Android, CSS que
  fijaba el video, mensajes y modulo JS. YouTube conserva su reproductor,
  controles y fullscreen originales.
- El fullscreen web estandar ya no se clasifica como presentacion insegura. La
  transicion sigue cubierta por el callback nativo de Gecko, revoca con identidad
  exacta y rearma el mismo documento. PiP y reproduccion remota siguen cerrados.
- Gates: JS 102/102, unitarios DEV/Diagnostic (219 por variante), ktlint y
  assemble Diagnostic verdes. `git diff --check` limpio.
- A23 con datos preservados: dos YouTube distintos reprodujeron con UI original
  sin limpiar datos; fullscreen original cerro con `revoke_ack`, creo revision
  2, volvio a `smooth_started` y aplico blur vivo sin pantalla negra. Dos gestos
  rapidos sobre la barra original generaron cierres confirmados por `revoke_ack`,
  nunca `revoke_timeout`, y la revision final volvio a `smooth_started`.
- El diagnostico S22 `DAG-4ZJH2LP5` contradijo esa muestra: DEV 229 rearmo a los
  399 ms y una segunda señal de seek termino en `revoke_timeout`. La causa era
  de cableado: 750 ms estaban en seleccion general y seek seguia en 150 ms.
  DEV 230 conecta cada tiempo a su controlador correcto y agrega una regresion
  contractual. Gates: JS 102/102, unitarios DEV/Diagnostic, ktlint, lint DEV y
  assemble DEV verdes. Falta una confirmacion fisica S22.

## Ultimo hito — DAG-VIDEO-PREMIUM-CONTINUITY-03

- Un cuadro filtrado durante reproduccion fluida ya no pausa, retira ni salta.
  El mismo video sigue con audio y recibe blur vivo de origen usuario: 64 px,
  brillo 0,16, saturacion 0,25 y contraste 0,72. Dos muestras seguras consecutivas
  lo retiran con transicion de 160 ms.
- Si el blur persiste dos segundos aparece un control pequeno anclado al video.
  El salto es exclusivamente manual, avanza en pasos de dos segundos bajo el
  mismo blur y conserva la autoridad; no produce pantalla negra intermedia.
- Scroll normal del mismo documento/video/fuente suspende solo nuevas capturas,
  descarta una muestra que cruce el movimiento, espera 150 ms estables, actualiza
  geometria y continua. Fuera de pantalla no captura. Identidad, fuente o
  capacidad insegura distintas siguen cerrando.
- Pantalla completa usa atributos CSS de origen usuario para fijar el video
  exacto al viewport con `object-fit: contain`. Android gira y oculta chrome solo
  despues del acuse JS exacto. El control nativo se reposiciona cada vez que llega
  una geometria verificada.
- Refactor: enlace de eventos por registro salio a
  `video-lab-record-binding.js`; experiencia premium se divide en overlay,
  continuidad, fullscreen y runtime. `video-lab.js` queda en 799 lineas.
- Prevalidacion: JS 103/103, unitarios DEV/Diagnostic, ktlint, lint de ambas
  variantes y assemble DEV verdes. APK DEV 228 local, 116 MiB, SHA-256
  `720c5c87205a9369ebee2751d562125b646b7896cc248488501f4b2ef0496f25`.
- A23: instalacion preservando datos, YouTube real visible y diez scrolls sin
  pagina bloqueada; el control siguio el reproductor sticky. DEV 227 demostro que
  forzar paisaje reemplazaba el video dinamico. DEV 228 ya no rota por programa
  y conserva la autoridad. Falta una comprobacion manual de entrada/salida del
  modo completo, blur real, audio y boton de salto.

## Ultimo hito — DAG-VIDEO-LIFECYCLE-FULLSCREEN-01

- Causa raiz del negro persistente: el cierre exacto de un video apagaba el
  laboratorio global del background. Al conservar Gecko/background entre
  pestañas o reinicios, los siguientes documentos quedaban cerrados hasta que
  borrar datos destruia ese proceso. El cierre ahora afecta solo al documento
  exacto y se libera despues del acuse durable.
- DAG ofrece un control nativo de pantalla completa protegido. Oculta el chrome,
  usa paisaje y conserva la cobertura/muestreo sin simular controles del sitio;
  salir rearma el mismo video sin cerrar la pestaña.
- Si vence cobertura, primer cuadro o revocacion, DAG envia automaticamente el
  diagnostico sanitizado existente. Nunca lo hace en privado, exige uploader
  configurado y aplica un cooldown persistente de 15 minutos.
- Gate final: JS 95/95, unitarios DEV/Diagnostic, ktlint, lint de ambas variantes
  y assemble DEV verdes.
- A23 con todos los datos preservados: primer video, entrada/salida de pantalla
  completa, segundo video distinto y reapertura completa de la app reprodujeron
  imagen, audio y muestreo continuo sin pantalla negra, crash ni ANR.
- La prueba fisica detecto correctamente un `revoke_timeout` de una iteracion
  descartada y envio `DAG-3QBATWD5`; el diseño final no reprodujo ese cierre.

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
- El workflow de DAG quedo alineado con `scripts/dag_gradle.sh` y ahora cubre JS,
  unitarios DEV/Diagnostic, ktlint, lint de ambas variantes y assemble DEV.

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

Validar humanamente DEV 230 en una sola corrida S22: primer y segundo video,
seek, scroll, blur/audio y entrada/salida del fullscreen original. A23 ya paso
automaticamente dos videos, fullscreen y seek repetido. No generar otra APK por
ajustes visuales menores. URLs MP4 directas, iframes, Shorts, anuncios,
Instagram y TikTok siguen NO PROBADOS o NO-GO.

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
- `video-lab.js` tiene 803 lineas tras separar eventos y configuracion. No sumar
  responsabilidades nuevas al coordinador; las nuevas politicas deben vivir en
  componentes propios.
- `DAG-STRUCTURE-03` queda abierto: `video-lab.js` alcanzo 803 lineas por el
  cableado de la nota, Activity supera 4.900 y `background.js` 1.500. El siguiente
  cambio funcional de video debe esperar un refactor neutro de coordinacion y
  diario durable; no mezclarlo con esta candidata estable.
- Tras tres intentos sin cambiar hito o decision, auditoria enfocada antes de
  otra edicion, build o APK.
- Automatizar ADB, agrupar pruebas y pedir intervencion solo si es inevitable.
- Sin push, PR, publicacion, Production ni borrado de datos sin OK actual.

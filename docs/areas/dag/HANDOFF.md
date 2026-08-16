# DAG BROWSER — HANDOFF

Actualizado: 2026-08-16. Responsable: Jefe.

## Mision

Navegador protegido independiente: cobertura visual, GloshIA R3.1, imagenes,
video y herramientas Diagnostic. Gradle es aislado; usar siempre
`scripts/dag_gradle.sh`.

## Estado operativo

- Rama `main`; al cerrar este lote quedara 22 commits por delante de
  `origin/main`, sin push.
- DEV 220 candidata; Diagnostic 86; extension integrada 2.0.42.
- Diagnostic es temporal. LAB ya no se construye. No hay APK Production.
- Fotos estan mas maduras y validadas que video. R3.1 y su politica vigente no
  cambiaron.
- YouTube normal y sus controles basicos estan GO local. Video general continua
  NO-GO hasta cerrar S22 y la matriz minima.

## Ticket activo — cierre DAG-VIDEO-03

Modo adaptativo universal, sin excepciones por sitio, formato o proveedor:

1. Los dos primeros cuadros se analizan bajo cobertura nativa.
2. Si ambos se permiten, el video original reproduce con audio.
3. DAG muestrea cada 500 ms; en A23 la cadencia real medida fue ~713 ms.
4. Un bloqueo/error cubre y pausa antes de retirar el permiso visual exacto.

No se transporta ni recodifica el video entero. El riesgo aceptado del modo
fluido es una deteccion de ~500 ms mas inferencia; no garantiza cero aparicion
entre muestras. El pipeline estricto con buffer A/V queda fuera del runtime.

## Evidencia vigente

- YouTube normal fue validado en emulador, A23 y S22 con imagen, audio y muestreo
  continuo. Captura A23 p50 6 ms/p95 7 ms; inferencia p50 145 ms/p95 168 ms.
- DEV 219 corrige el bloqueo permanente y la reseleccion en bucle. Un
  `model_filter` retira la autoridad exacta con `revoke_ack`; si el acuse falla,
  Android descarta solo la pestaña/sesion afectada antes de quitar la cobertura.
- Diagnostic 83 produjo en A23 un bloqueo real score 0,830. El video quedo
  pausado, mudo y oculto; solo su rectangulo se mostro bordo y la pagina siguio
  utilizable. Una interaccion externa, navegacion o recarga retira el rectangulo
  para que no quede fijo sobre otro contenido.
- Diagnostic 85 hace localizada tambien la cobertura de analisis: solo cubre el
  rectangulo del video y deja visible/usable el resto de la pagina. En A23 la
  cobertura inicial aparecio en 64 ms. Un doble toque `+10` retiro el grant
  anterior con `revoke_ack`, creo revision 2, analizo dos cuadros y restauro
  reproduccion fluida en ~2,1 s sin cerrar la pestaña. Los eventos repetidos de
  un mismo gesto se agrupan bajo la misma cobertura; cambio de fuente/documento
  sigue fail-closed. Sin crash ni ANR. PSS 232 MiB, RSS 249 MiB y CPU puntual
  10% durante reproduccion. APK Diagnostic 85 SHA-256:
  `71863c5b22ae342f5a152dd53c6170adf2a81100d8367f0df15969de148d6d4c`.
- La politica de pestañas conserva abierta solo la sesion activa para evitar
  agotar decodificadores VP9 en dispositivos modestos.
- Gates verdes: JS 70/70, unitarios DEV/Diagnostic, ktlint, lint DEV/Diagnostic
  y assemble Diagnostic.
- DEV 219 SHA-256:
  `9fd1b6ced4613602ba3a1fa144ee450e6365cc4b756c6bcccf277dcd1dd8ff96`.
- Commits de cierre: `c5d8f332` y `a933c0f7`. Sin push ni publicacion.

## Pendiente inmediato

1. Confirmar en S22 con Diagnostic 86: reproduccion, cobertura localizada,
   bloqueo bordo, doble toque `+10` y un GIF seguro.
2. Si pasa, incrementar y construir una unica DEV 221 desde `main` integrado.
3. Shorts, anuncios, TikTok e Instagram siguen NO PROBADOS/NO-GO si no exponen
   un elemento de video estable. No crear excepciones por proveedor.
4. Con matriz suficiente, promover el mismo runtime DEV y retirar Diagnostic del
   telefono de prueba.

Progreso: base DAG Video/YouTube normal 99%; matriz premium general 85%.

## Ticket cerrado — DAG-VIDEO-CONTROLS-01

Adelantar y retroceder ya no son terminales: el destino nunca se revela antes de
reanalizarlo.

Contrato a diseñar antes del runtime:

- ocultar, pausar y silenciar sincronicamente al comenzar el salto;
- revocar el grant anterior con identidad exacta;
- esperar `seeked` y estabilidad de fuente/geometria;
- abrir una autoridad nueva bajo cobertura y repetir los dos cuadros iniciales;
- eventos repetidos del mismo gesto se agrupan; cambio de fuente, timeout o falta
  de acuse queda fail-closed;
- primero replay determinista; una sola APK fisica recien si el contrato pasa.

Estado y coordinador viven en modulos separados. Cubren los dos ordenes posibles
entre `seeked` y el acuse nativo, exigen estabilidad y una autoridad nueva antes
de rearmar. Validado automaticamente y en YouTube real A23.

## Ticket cerrado — DAG-ANIMATED-IMAGES-01 (GIF)

Los GIF seguros ya se entregan animados sin bloquear la pagina. El contenedor se
valida completo (2 MiB, 120 cuadros y 60 s maximos); todos los cuadros se
decodifican y recorren por mosaicos, el modelo pesado corre a 2 fps y ante un
cambio material, con maximo de 10 inferencias. Cualquier error, cuadro riesgoso o
complejidad mayor reemplaza solo el GIF por el placeholder existente.

En A23, Diagnostic 86 mostro animado dentro de Wikimedia un GIF de 357x334, 10
cuadros/30 s y 20 KiB: 10 inferencias, `model_allow` en 2,06 s, pagina visible y
usable durante todo el flujo. El control sintetico de 60 cuadros hizo 6
inferencias en 944 ms. Instrumentacion 2/2, unitarios DEV/Diagnostic, ktlint,
lint ambos, JS 70/70 y build Diagnostic verdes. APK SHA-256:
`eb2f3cb271d7934a2a0252e91424bc18d8667904b230bb6dc878b67fa3a55a28`.
WebP/AVIF animados siguen bloqueados de forma localizada hasta otro ticket.

## Backlog posterior

- `DAG-PARTIAL-REDACTION-01`: blur/pixelado parcial con segmentacion espacial.
- Adaptador universal para URLs directas MP4/visor multimedia interno de Gecko.

## Riesgos y deuda

- El muestreo de video puede omitir apariciones muy breves; GIF ya recorre cada
  cuadro con detector barato, pero video aun no comparte ese detector.
- `DagBrowserActivity.kt` supera 4.700 lineas: no agregar responsabilidades.
  Cualquier estado nuevo debe vivir en componentes separados.
- `video-lab.js` tiene 790 lineas: usar sus modulos existentes o uno nuevo; no
  volver a inflar el orquestador.
- Los prototipos WebM/transporte/binario fueron retirados del runtime. No
  reactivarlos sin otra decision arquitectonica.

## Operacion

Automatizar por ADB instalacion, apertura, gestos, logs, capturas y cierre. Pedir
al usuario solo una accion fisica inevitable y agruparla. Sin push, PR,
publicacion, Production ni borrado de datos sin OK explicito actual de Jefe.

La historia detallada queda en la evidencia fechada del area y en Git; no volver
a copiarla a este handoff.

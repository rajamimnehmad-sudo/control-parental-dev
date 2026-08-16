# DAG BROWSER — HANDOFF

Actualizado: 2026-08-16. Responsable: Jefe.

## Mision

Navegador protegido independiente: cobertura visual, GloshIA R3.1, imagenes,
video y herramientas Diagnostic. Gradle es aislado; usar siempre
`scripts/dag_gradle.sh`.

## Estado operativo

- Rama `main`; 19 commits locales por delante de `origin/main`, sin push.
- DEV 219 candidata; Diagnostic 83; extension integrada 2.0.40.
- Diagnostic es temporal. LAB ya no se construye. No hay APK Production.
- Fotos estan mas maduras y validadas que video. R3.1 y su politica vigente no
  cambiaron.
- Video de producto continua NO-GO general hasta cerrar S22 y la matriz minima.

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
- La politica de pestañas conserva abierta solo la sesion activa para evitar
  agotar decodificadores VP9 en dispositivos modestos.
- Gates DEV/Diagnostic verdes: JS 64/64, unitarios, ktlint, lint y assemble.
- DEV 219 SHA-256:
  `9fd1b6ced4613602ba3a1fa144ee450e6365cc4b756c6bcccf277dcd1dd8ff96`.
- Commits de cierre: `c5d8f332` y `a933c0f7`. Sin push ni publicacion.

## Pendiente inmediato

1. Confirmar DEV 219 una vez en S22: reproduccion normal y bloqueo localizado.
2. Mantener Diagnostic 83 solo como referencia hasta esa confirmacion.
3. Shorts, anuncios, TikTok e Instagram siguen NO PROBADOS/NO-GO si no exponen
   un elemento de video estable. No crear excepciones por proveedor.
4. Con matriz suficiente, promover el mismo runtime DEV y retirar Diagnostic del
   telefono de prueba.

Progreso ejecutivo DAG Video: 99%.

## Proximo ticket — DAG-VIDEO-CONTROLS-01

Objetivo: adelantar, retroceder y mover la barra sin revelar el destino antes de
analizarlo. Hoy `seeking` es terminal.

Contrato a diseñar antes del runtime:

- ocultar, pausar y silenciar sincronicamente al comenzar el salto;
- revocar el grant anterior con identidad exacta;
- esperar `seeked` y estabilidad de fuente/geometria;
- abrir una autoridad nueva bajo cobertura y repetir los dos cuadros iniciales;
- cualquier salto repetido, cambio de fuente, timeout o falta de acuse queda
  fail-closed;
- primero replay determinista; una sola APK fisica recien si el contrato pasa.

El contrato puro ya esta aislado fuera del manifiesto: cubre los dos ordenes
posibles entre `seeked` y el acuse nativo, exige estabilidad antes de rearmar y
cierra ante repeticion, cambio, ambiguedad o timeout. Suite JS 67/67 verde. Aun
no cambia el runtime, la version ni la APK.

## Backlog posterior

- `DAG-ANIMATED-IMAGES-01`: GIF y formatos animados.
- `DAG-PARTIAL-REDACTION-01`: blur/pixelado parcial con segmentacion espacial.
- Adaptador universal para URLs directas MP4/visor multimedia interno de Gecko.

## Riesgos y deuda

- El muestreo puede omitir apariciones muy breves; falta un detector barato de
  cambio por cuadro calibrado.
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

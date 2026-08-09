# DAG Browser 183 - gesto acotado y entrega estable a Gecko

Fecha: 2026-08-08

Dispositivo: Samsung SM-S908E

Paquete: `com.contentfilter.dagbrowser.dev`

Version: `183` / `0.69.87-dev`

Extension: `1.94.0`

APK SHA-256: `4fa03dd963e7a41c4365bb2a71ec38e6a2fb50e45868d04a3b89c232c041e50e`

## Objetivo

Conservar la mejora de respuesta tactil sin perder imagenes permitidas en
paginas dinamicas. No se modificaron modelo, pesos, umbrales, preprocesamiento,
politica visual, ONNX ni `final_sealed`.

## Diagnostico y cambios

### Interaccion tactil

DAG 181 reducia el executor visual durante la interaccion, pero cada evento
`MOVE` cancelaba y volvia a publicar la restauracion en el hilo principal. DAG
182 baja a un worker solo en `ACTION_DOWN` y publica una unica restauracion a
dos workers 250 ms despues de `ACTION_UP` o `ACTION_CANCEL`.

Un swipe automatizado registro exactamente dos transiciones:

- `interaction_threads=1` al comenzar;
- `interaction_threads=2` al finalizar.

### Fotos permitidas que quedaban blancas

En Fravega, despues de desplazar `Ofertas Unicas`, las tarjetas de lavarropas y
heladera quedaron blancas en DAG 182. Las decisiones correlacionadas fueron
`model_allow`, por lo que no era un filtro de R3 ni una excepcion de sitio.

La extension entregaba el mismo `Uint8Array` a `StreamFilter.write()`, cerraba
el filtro y llenaba inmediatamente ese arreglo con ceros. La API no promete una
copia sincrona del argumento; un decode diferido podia observar el contenido ya
alterado. DAG 183 deja los bytes permitidos bajo propiedad del stream y mantiene
la limpieza explicita solamente cuando el original no fue entregado.

El harness JS ya no copia artificialmente el argumento de `write()`: retiene la
vista original y comprueba que los bytes permitidos sigan exactos despues de
cerrar el filtro.

## A/B fisico

- DAG 182: primeras tarjetas visibles; tras desplazar, lavarropas y heladera con
  area de imagen blanca pese a decisiones `model_allow`.
- DAG 183: categorias iniciales completas y, tras el mismo desplazamiento,
  lavarropas y heladera visibles.
- El propietario confirmo manualmente el resultado de DAG 183.
- Mimo conserva su banner raster. El bloque gris inferior corresponde a un
  GIF/video bloqueado por la politica vigente.

El experimento 32 versus 128 streams no redujo memoria ni procesos. Con 32 se
perdieron imagenes de rafaga; se restauro 128. En la misma pagina de Mimo, Chrome
uso memoria proporcional comparable a DAG, por lo que no se dejo ningun ajuste
de memoria sin evidencia.

## Validacion

- `testDagProtectionJs`: 23/23.
- `testDevDebugUnitTest`: correcto.
- `ktlintCheck`: correcto.
- `lintVitalDevDebug`: correcto dentro de `assembleDevDebug`.
- `assembleDevDebug`: correcto.
- Instalacion in-place y apertura: correctas.
- Sin excepciones por sitio, URL, dominio o dispositivo.

## Estado

DAG 183 es un candidato local validado sobre el baseline seguro DAG 176. No se
hizo push, publicacion remota ni cambio en Supabase o Production.

# DAG Browser 158 - revelado saneado y orden de imagenes

Fecha: 2026-08-06
Dispositivo: Samsung SM-S908E
Android: 16
Paquete: `com.contentfilter.dagbrowser.dev`
Version: `158` / `0.69.62-dev`
Extension: `1.75.0`

## Problema

Google podia mostrar una rafaga muy breve de contenido patrocinado porque la
pagina se revelaba antes del primer barrido de anuncios. El primer candidato
que coordino ese barrido, DAG 156, puso `ads.js` antes de `barrier.js` y produjo
una regresion visible: fotos inocentes de Mimo y Fravega no se registraban a
tiempo en la barrera visual.

## Causa y correccion

Los listeners de `DOMContentLoaded` se ejecutan en orden de registro. Con
`ads.js` primero, un contenedor podia quedar oculto antes de que
`barrier.js` recorriera sus imagenes. DAG 157/158 restaura el orden seguro:

1. `barrier.js` registra y protege imagenes.
2. `ads.js` ejecuta el saneamiento inicial.
3. El evento `document-sanitized-ready` completa el handshake.
4. Android revela solo cuando barrera, contenido protegido y saneamiento estan
   listos.

No hay espera fija ni excepcion por sitio, URL o dominio. GloshIA Visual R3.1,
umbral, politica, ONNX y cantidad de hilos no cambiaron.

## Evidencia fisica

- DAG 156: tras 12 segundos, Mimo mostraba el hero vacio y Fravega categorias
  sin imagenes.
- DAG 157: el hero de Mimo y las categorias de Fravega reaparecieron.
- DAG 158, smoke final Mimo: `page_visible=1.068 ms`,
  `viewport_images_ready=901 ms`, `page_analysis_ready=2.115 ms`.
- Google: `page_visible=1.082 ms`, `viewport_images_ready=6.517 ms`,
  `page_analysis_ready=3.863 ms`.
- Sin `FATAL EXCEPTION`, ANR u OOM observados.

## Refactor

El manejo de puertos de extension se separo en conexiones y handlers con
nombres explicitos para contenido, barrera, saneamiento, decisiones multimedia
y viewport. Es una extraccion mecanica dentro de `DagBrowserActivity`; mantiene
las mismas validaciones de extension, protocolo, top-level y pestaña activa.

## Validacion automatica

- `testDagProtectionJs`: 21/21.
- `testDevDebugUnitTest`: 165/165.
- `ktlintCheck`: correcto.
- `lintDevDebug`: correcto.
- `assembleDevDebug`: correcto.

No se publico, no se hizo push y no se modificaron Supabase ni Production.

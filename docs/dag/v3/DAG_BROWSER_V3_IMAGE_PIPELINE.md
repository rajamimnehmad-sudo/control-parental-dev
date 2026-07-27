# DAG Browser V3 - transporte seguro de imagenes

## Decision

DAG intercepta la respuesta HTTP(S) original mediante `webRequest.filterResponseData`. Gecko no
recibe los bytes mientras la extension los retiene. No se hace una segunda descarga y no se confia
en un atributo DOM para decidir si una foto es segura.

Referencias de plataforma:

- https://developer.mozilla.org/en-US/docs/Mozilla/Add-ons/WebExtensions/API/webRequest/filterResponseData
- https://mozilla.github.io/geckoview/consumer/docs/web-extensions

El API esta presente en el GeckoView 153 fijado por `app-dag-browser`; tambien fue comprobado en el
codigo empaquetado de esa dependencia.

## Alternativas evaluadas

| Camino | Ventaja | Problema | Decision |
| --- | --- | --- | --- |
| Volver a descargar desde Android | Implementacion nativa directa | Duplica red, pierde cookies y puede analizar bytes distintos de los mostrados | Rechazado |
| Capturar toda la pantalla | Tambien ve canvas y graficos generados | Agrega latencia, complica scroll e interaccion y puede perder cambios entre capturas | Segunda defensa futura |
| Interceptar la respuesta original | Analiza exactamente los bytes que Gecko iba a usar y puede reemplazarlos antes de pintar | Requiere limites estrictos de memoria, cola y timeout | Elegido |

## Flujo del benchmark bloqueante

1. La hoja inyectada en `document_start` mantiene todos los medios ocultos.
2. La extension crea un filtro de respuesta para `image` e `imageset`.
3. Acumula hasta 256 KiB sin escribir nada hacia la pagina.
4. Si el limite se supera, la descarga falla o falta el API, el resultado es bloqueo.
5. La extension envia bytes Base64 al Android local mediante el canal de fondo privilegiado.
6. Android valida remitente, version, URL, longitud Base64, formato y dimensiones sin decodificar
   el bitmap completo.
7. El trabajo se limita a dos hilos y ocho elementos en espera.
8. Como maximo hay 16 respuestas de imagen activas y 10 pedidos nativos simultaneos.
9. Capturar una respuesta puede demorar como maximo 5 segundos y la decision nativa 2,5 segundos.
10. Limite, cola llena o timeout significan bloqueo.
11. En esta etapa Android solo puede responder `block`.
12. La extension reemplaza siempre la respuesta por un GIF transparente de 1x1.

Ningun byte se guarda en disco, se registra en logs o se envia a Supabase.

## Limites contra imagenes maliciosas

- cuerpo capturado: maximo 256 KiB;
- respuestas de imagen simultaneas: maximo 16;
- mensajes simultaneos hacia el analizador: maximo 10;
- respuesta lenta: maximo 5 segundos antes de bloquear;
- URL: maximo 4096 caracteres y esquema HTTP(S);
- formatos: JPEG, PNG, WebP y GIF;
- ancho y alto: maximo 4096;
- pixeles declarados: maximo 16.777.216;
- identificadores y versiones deben coincidir en ambos extremos;
- mensajes del contenido y del fondo tienen validaciones de remitente distintas.

Estas reglas reducen mensajes excesivos, bombas de descompresion, colas infinitas y respuestas
falsificadas.

## Alcance inicial

El primer filtro funcional mostrara solamente raster HTTP(S) que haya atravesado esta tuberia.
Video, audio, `object`, canvas, SVG, fondos y pseudo-elementos continuan bloqueados.

Una pagina hostil puede generar graficos mediante JavaScript sin descargar una imagen tradicional.
Por eso la primera apertura no se declarara navegador general: se limitara al buscador y a destinos
aprobados hasta agregar la segunda defensa de pagina completa. Este limite debe permanecer visible
en las pruebas y en el producto; no se puede resolver honestamente solo con un clasificador de
archivos.

## Proximos gates

1. Probar el transporte en el Samsung con formatos pequenos, grandes, corruptos y lentos.
2. Medir latencia y memoria sin habilitar fotos.
3. Agregar reduccion local a la entrada exacta del modelo.
4. Comparar los modelos candidatos con el mismo conjunto de evaluacion.
5. Habilitar `allow` y luego `blur` solamente tras cerrar precision y fugas.

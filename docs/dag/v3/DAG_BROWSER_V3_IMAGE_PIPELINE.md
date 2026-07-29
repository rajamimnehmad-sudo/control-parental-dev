# DAG Browser V3 - transporte seguro de imagenes

## Decision

DAG copia la respuesta HTTP(S) original mediante `webRequest.filterResponseData` mientras mantiene
la barrera visual inyectada en `document_start`. Gecko puede terminar la descarga, pero la pagina no
puede presentar el recurso hasta recibir una decision local `allow` o `block`. No se confia en un
atributo de la pagina: los atributos de estado pertenecen al content script aislado y la hoja
privilegiada sigue cerrada por defecto.

Si Gecko entrega el recurso desde cache, el cuerpo supera el limite de captura o una libreria lo
crea como `blob:`, la extension usa un fallback acotado de hasta 2 MiB. Ese camino puede hacer una
lectura adicional con las credenciales de la misma sesion, pero conserva el mismo analizador local,
los mismos limites y el fallo cerrado.

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
3. Copia hasta 512 KiB de la respuesta original mientras Gecko completa la descarga.
4. Un recurso no capturado queda oculto y entra en el fallback visible-first de hasta 2 MiB.
5. La extension envia bytes Base64 al Android local mediante el canal de fondo privilegiado.
6. Android valida remitente, version, URL, longitud Base64, formato y dimensiones sin decodificar
   el bitmap completo.
7. Android reduce el lado mayor a 224 px durante la decodificacion, conserva la imagen completa con
   relleno neutro y produce RGB888 acotado.
8. El trabajo se limita a dos hilos y ocho elementos en espera.
9. Como maximo hay 16 respuestas de imagen activas y 10 pedidos nativos simultaneos.
10. Capturar una respuesta puede demorar como maximo 5 segundos y la decision nativa 2,5 segundos.
11. Limite, cola llena o timeout significan bloqueo o reintento acotado sin exponer el recurso.
12. El único artefacto
    `tinyclip-bounded-finetune-r1-int8.onnx` y su cabeza binaria responden
    `allow` o `block`; error, formato no soportado, salida inválida o analizador
    ausente siempre responden `block`.
13. `allow` muestra el recurso original y `block` lo muestra con desenfoque fuerte. Mientras no hay
    decisión conserva espacio, permanece oculto y muestra `Analizando`. Un
    rechazo muestra `Protegida por Glosh`; un fallo terminal sigue oculto y
    muestra `Imagen no disponible`.
14. Las decisiones exactas se deduplican por SHA-256 de los bytes y viven solo en memoria, con un
    maximo de 256 entradas.

Ningun byte se guarda en disco, se registra en logs o se envia a Supabase.

## Limites contra imagenes maliciosas

- cuerpo capturado en la respuesta original: maximo 512 KiB;
- cuerpo analizado por fallback: maximo 2 MiB;
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

## Alcance actual

El filtro funcional muestra raster HTTP(S), `data:` o `blob:` solamente tras atravesar la tuberia.
Los fondos y pseudo-elementos con URL se descubren con un sondeo acotado y usan el mismo fallback.
SVG pequenos y autocontenidos de interfaz pueden mostrarse; SVG complejos, video, audio, `object`,
canvas y formatos no soportados permanecen cerrados.

Una pagina hostil puede generar graficos mediante JavaScript sin descargar una imagen tradicional.
Por eso la primera apertura no se declarara navegador general: se limitara al buscador y a destinos
aprobados hasta agregar la segunda defensa de pagina completa. Este limite debe permanecer visible
en las pruebas y en el producto; no se puede resolver honestamente solo con un clasificador de
archivos.

## Estado de gates

1. Transporte fisico con formatos pequenos, grandes, corruptos y lentos: completo.
2. Latencia y memoria sin habilitar fotos: completo.
3. Agregar reduccion local a la entrada exacta del modelo: completo, incluida evidencia fisica.
4. Comparar candidatos con el mismo conjunto congelado: completo para el piloto
   binario DEV. El candidato fijado tiene SHA-256
   `2d52bd9e5eb4cd448cb0d64a784b2ee6f761ad20e890c57b898fd7991d29a9ee`.
5. Habilitar `allow` y `blur`: completo en DEV con umbral `0.4`, cero falsos
   permisos en 21 casos congelados y gate físico inicial aprobado. Production
   sigue sin autorización.

Evidencia de los dos primeros gates:
`docs/compatibility/results/dag-browser-v3-image-transport-sm-a235m-2026-07-27.md`.

Contrato del tercer gate:
`docs/dag/v3/DAG_BROWSER_V3_IMAGE_PREPROCESSOR.md`.

Evidencia fisica del tercer gate y de la matriz fija instrumentada:
`docs/compatibility/results/dag-browser-v3-image-preprocessor-sm-a235m-2026-07-27.md`.

Contrato de datos del cuarto gate:
`docs/dag/v3/DAG_BROWSER_V3_MODEL_DATASET_CONTRACT.md`.

Las tres marcas comparables de la matriz fisica se definen en
`docs/dag/v3/DAG_BROWSER_V3_PERFORMANCE_METRICS.md`.

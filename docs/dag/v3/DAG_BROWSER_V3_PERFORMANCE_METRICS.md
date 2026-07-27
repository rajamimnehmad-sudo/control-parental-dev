# DAG Browser V3 - metricas fisicas

## Objetivo

Separar el tiempo de estructura, carga y decisiones visuales. Un unico tiempo de "pagina lista"
oculta regresiones y no sirve para comparar cambios del filtro.

## Reloj

Cada navegacion superior comienza en `GeckoSession.ProgressDelegate.onPageStart`. Los tiempos usan
el reloj monotono de Android y se registran solo en la variante DEV, sin URL, texto ni pixeles.

Formato:

```text
navigation=7 metric=page_visible elapsed_ms=83
```

## Metricas

- `page_visible`: la extension instalada en `document_start` confirma la barrera y Android hace
  visible la superficie Gecko. Las imagenes siguen ocultas.
- `page_analysis_ready`: Gecko informa `onPageStop`; el log incluye `success=true/false`. En esta
  fase no existe un analizador global de pagina, por lo que este evento representa documento y
  texto terminados.
- `viewport_images_ready`: el documento superior disparo `load`, no queda ningun filtro de respuesta
  activo ni pedido nativo pendiente, y el estado permanecio quieto 250 ms.

La tercera metrica la produce el fondo privilegiado de la extension. Una pagina no puede
falsificarla y el evento no participa en decisiones de contenido.

Si falta una metrica, `success=false`, vence la barrera o la pagina se rompe, el recorrido cuenta
como regresion. El registro de rendimiento nunca habilita una foto ni cambia `block`.

## Recorrido sin cache para GeckoView

DAG V3 usa GeckoView, no WebView; por eso `LOAD_NO_CACHE` no existe en este modulo. El recorrido
fisico equivalente es:

1. `pm clear --cache-only com.contentfilter.dagbrowser.dev`;
2. detener el proceso;
3. abrirlo en frio;
4. agregar `codexperf` unico a la URL superior;
5. registrar las tres metricas, decisiones visuales, memoria, temperatura y cualquier error.

La limpieza de cache conserva datos y firma de la aplicacion. El parametro unico evita reutilizar
el documento superior; los subrecursos quedan cubiertos por la limpieza del cache de la app.

## Matriz fija

- <https://www.fravega.com/>
- <https://www.mimo.com.ar/>
- <https://www.cheeky.com.ar/>

Los resultados se agregan a `docs/compatibility/results/dag-performance-history.md` y la evidencia
detallada de cada gate queda en un archivo propio del candidato.

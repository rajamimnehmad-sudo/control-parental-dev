# Historial de rendimiento DAG

Matriz permanente para cada candidato o version que cambie navegador, WebView, carga o imagenes:

- Fravega: `https://www.fravega.com/`
- Mimo: `https://www.mimo.com.ar/`
- Cheeky: `https://www.cheeky.com.ar/`

Cada recorrido guarda tres metricas `DagPerformance`: `page_analysis_ready` para pagina/texto, `viewport_images_ready` para fotos iniciales decididas y `page_visible` para el instante en que la estructura funcional queda utilizable. Desde DEV 279 la estructura no espera a todas las fotos: cada recurso visual conserva su proteccion y aparece individualmente cuando termina. Usa `codexperf`, limpia el cache HTTP de WebView, fuerza `LOAD_NO_CACHE`, conserva el mismo dispositivo y registra timeouts o roturas como regresiones.

| Fecha | Variante | Dispositivo | Android | Fravega | Mimo | Cheeky | Resultado |
| --- | --- | --- | --- | ---: | ---: | ---: | --- |
| 2026-07-24 | Filtro 1 conservador | SM-S908E | 16 | 11.502 ms | No medido | No medido | Descartado: casi todas las fotos ejecutaban prefiltro y recorrido completo |
| 2026-07-24 | Recorrido anterior restaurado | SM-S908E | 16 | 8.761 ms | 2.190 ms | 27.920 ms | Instalado localmente; Cheeky sigue excesivamente lenta |
| 2026-07-24 | Recorrido restaurado + instrumentacion sin cache | SM-S908E | 16 | 968 / 436 / 968 ms | 1.635 / 2.125 / 2.125 ms | 1.741 / 8.486 / 8.486 ms | Base valida sin cache; Cheeky queda limitada por imagenes |
| 2026-07-27 | DAG v1 navegador liviano, candidato DEV 280 sin cache | SM-A235M | 14 | 2.434 / 11.134 / 2.434 ms | 964 / 4.342 / 965 ms | 463 / 8.694 / 463 ms | Estructura utilizable progresivamente; scroll, atras, adelante y recarga correctos; sin crash, ANR ni renderer gone |

Los registros anteriores a la instrumentacion separada contienen solo `page_visible`. Desde el siguiente candidato cada celda se escribe como `pagina / fotos / visible`. La fila DEV 280 es una muestra fisica por sitio, no una distribucion estadistica; las recargas posteriores de Mimo/Cheeky dieron estructura visible entre 158 y 285 ms con cache de proceso.

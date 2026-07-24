# Historial de rendimiento DAG

Matriz permanente para cada candidato o version que cambie navegador, WebView, carga o imagenes:

- Fravega: `https://www.fravega.com/`
- Mimo: `https://www.mimo.com.ar/`
- Cheeky: `https://www.cheeky.com.ar/`

Cada recorrido guarda tres metricas `DagPerformance`: `page_analysis_ready` para pagina/texto, `viewport_images_ready` para fotos iniciales decididas y `page_visible` para el instante en que el usuario ve ambos. Usa `codexperf`, limpia el cache HTTP de WebView, fuerza `LOAD_NO_CACHE`, conserva el mismo dispositivo y registra timeouts o roturas como regresiones.

| Fecha | Variante | Dispositivo | Android | Fravega | Mimo | Cheeky | Resultado |
| --- | --- | --- | --- | ---: | ---: | ---: | --- |
| 2026-07-24 | Filtro 1 conservador | SM-S908E | 16 | 11.502 ms | No medido | No medido | Descartado: casi todas las fotos ejecutaban prefiltro y recorrido completo |
| 2026-07-24 | Recorrido anterior restaurado | SM-S908E | 16 | 8.761 ms | 2.190 ms | 27.920 ms | Instalado localmente; Cheeky sigue excesivamente lenta |
| 2026-07-24 | Recorrido restaurado + instrumentacion sin cache | SM-S908E | 16 | 968 / 436 / 968 ms | 1.635 / 2.125 / 2.125 ms | 1.741 / 8.486 / 8.486 ms | Base valida sin cache; Cheeky queda limitada por imagenes |

Los registros anteriores a la instrumentacion separada contienen solo `page_visible`. Desde el siguiente candidato cada celda se escribe como `pagina / fotos / visible`.

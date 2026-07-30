# Historial de rendimiento DAG

Matriz permanente para cada candidato o version que cambie navegador, WebView, carga o imagenes:

- Fravega: `https://www.fravega.com/`
- Mimo: `https://www.mimo.com.ar/`
- Cheeky: `https://www.cheeky.com.ar/`

Cada recorrido guarda tres metricas `DagPerformance`: `page_analysis_ready` para pagina/texto, `viewport_images_ready` para fotos iniciales decididas y `page_visible` para el instante en que la estructura funcional queda utilizable. Desde DEV 279 la estructura no espera a todas las fotos: cada recurso visual conserva su proteccion y aparece individualmente cuando termina. Usa `codexperf`, conserva el mismo dispositivo y registra timeouts o roturas como regresiones. Los cortes WebView limpian su cache HTTP y fuerzan `LOAD_NO_CACHE`. DAG Browser V3 usa GeckoView: limpia el perfil DEV antes de cada muestra cuando `pm clear --cache-only` no termina.

| Fecha | Variante | Dispositivo | Android | Fravega | Mimo | Cheeky | Resultado |
| --- | --- | --- | --- | ---: | ---: | ---: | --- |
| 2026-07-24 | Filtro 1 conservador | SM-S908E | 16 | 11.502 ms | No medido | No medido | Descartado: casi todas las fotos ejecutaban prefiltro y recorrido completo |
| 2026-07-24 | Recorrido anterior restaurado | SM-S908E | 16 | 8.761 ms | 2.190 ms | 27.920 ms | Instalado localmente; Cheeky sigue excesivamente lenta |
| 2026-07-24 | Recorrido restaurado + instrumentacion sin cache | SM-S908E | 16 | 968 / 436 / 968 ms | 1.635 / 2.125 / 2.125 ms | 1.741 / 8.486 / 8.486 ms | Base valida sin cache; Cheeky queda limitada por imagenes |
| 2026-07-27 | DAG Browser V3, preprocesador local + metricas, `versionCode 5` | SM-A235M | 14 | 13.172 / 19.385 / 1.361 ms | 6.237 / 14.280 / 596 ms | 6.346 / 29.550 / 2.122 ms | Una muestra fria por sitio; 102 respuestas bloqueadas, scroll sin fotos, sin crash, ANR ni OOM; Cheeky tarda en quedar quieta |
| 2026-07-29 | DAG Browser V3 `versionCode 15`, extension `1.14.3` | SM-S908E | 16 | 6.969 / 10.524 / 1.067 ms | 1.510 / 2.008 / 281 ms | 5.016 / 5.329 / 1.878 ms | Muestra fisica tras actualizar la extension interna; fotos permitidas visibles y rechazadas desenfocadas en los tres sitios; sin crash ni ANR |
| 2026-07-29 | DAG Browser V3 optimizado `versionCode 17`, extension `1.15.1` | SM-S908E | 16 | 9.287 / 20.626 / 1.267 ms | 1.867 / 3.701 / 751 ms | 4.576 / 5.566 / 1.983 ms | Muestra operativa in-place con cache HTTP existente; se corrigio el fallback de recursos cacheados, no quedaron fotos transparentes y la estructura siguio progresiva. Google Imagenes dio 976 / 1.787 / 144 ms y su recarga 738 / 989 / 187 ms, sin reanalisis nativos repetidos. Sin crash ni ANR |
| 2026-07-29 | DAG Browser `versionCode 21`, primer lote de estabilidad visual | SM-A235M | 14 | 20.258 / 20.487 / 1.347 ms | 5.638 / 6.224 / 594 ms | No completó / No completó / 1.671 ms | Perfil DEV borrado y `codexperf` distinto por sitio. Frávega y Mimo completaron; Home de Cheeky quedó visible pero no emitió quietud dentro de 45 s. En el recorrido de categoría Cheeky sí se verificaron corazones funcionales y ausencia de `Analizando` residual. Sin crash, ANR ni salida inesperada |
| 2026-07-30 | DAG Browser `versionCode 32`, consenso regional | SM-S908E | 16 | 9.942 / No completó / 1.433 ms | No completó / No completó / 373 ms | 5.333 / 5.625 / 1.791 ms | Caché Gecko borrada desde el control propio de DAG antes de cada URL y `codexperf` distinto. Cheeky confirmó permiso con máximo regional 0,5366. Frávega y Mimo quedaron visibles pero no completaron quietud dentro de 35 s. Sin crash |
| 2026-07-30 | DAG Browser `versionCode 33`, foto filtrada sin overlay | SM-S908E | 16 | 1.931 / 4.813 / 263 ms | 1.335 / 1.786 / 119 ms | 2.821 / 3.815 / 595 ms | Caché Gecko borrada desde DAG y `codexperf` distinto. Las tres navegaciones finales completaron sin crash. Control visual con 22 rechazos del modelo y 17 presentaciones bloqueadas, sin escudo/✓ |

Los registros anteriores a la instrumentacion separada contienen solo `page_visible`. Desde el siguiente candidato cada celda se escribe como `pagina / fotos / visible`.

# Historial de rendimiento DAG

Matriz permanente para cada candidato o version que cambie navegador, WebView, carga o imagenes:

- Fravega: `https://www.fravega.com/`
- Mimo: `https://www.mimo.com.ar/`
- Cheeky: `https://www.cheeky.com.ar/`

La metrica canonica es `DagPerformance page_visible`, que incluye la espera de pagina e imagenes iniciales decididas por DAG. Cada recorrido usa un parametro de cache-bust, conserva el mismo dispositivo y registra timeouts o roturas como regresiones.

| Fecha | Variante | Dispositivo | Android | Fravega | Mimo | Cheeky | Resultado |
| --- | --- | --- | --- | ---: | ---: | ---: | --- |
| 2026-07-24 | Filtro 1 conservador | SM-S908E | 16 | 11.502 ms | No medido | No medido | Descartado: casi todas las fotos ejecutaban prefiltro y recorrido completo |
| 2026-07-24 | Recorrido anterior restaurado | SM-S908E | 16 | 8.761 ms | 2.190 ms | 27.920 ms | Instalado localmente; Cheeky sigue excesivamente lenta |

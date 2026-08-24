# CHROME-GENERAL-WEB-PERF-14 — plan de benchmark

Estado: PREPARED / EJECUTAR DESPUÉS DE 12/13 FUNCIONALES.

## Objetivo

Medir cuánto agrega Glosh al Chrome oficial y verificar que la protección de fotos no degrade navegación, memoria, batería ni estabilidad de forma inaceptable.

No optimizar contra métricas inventadas: primero baseline Chrome sin Glosh data-plane general en el mismo device/red, luego comparar.

## Devices mínimos

- Samsung A23 API34;
- Samsung S22 Ultra API36;
- agregar al menos un low-RAM/OEM distinto antes de producto.

## Redes

- Wi-Fi estable;
- datos móviles;
- IPv6/NAT64 si disponible;
- handover Wi-Fi<->datos.

## Estados

- cold boot;
- app/model cold;
- model warm;
- browser cold;
- browser warm;
- cache cold;
- safe decision cache warm;
- después de update in-place.

## Escenarios web

### Micro

- 1 SAFE JPEG;
- 1 BLOCK JPEG;
- 1 UNKNOWN;
- PNG/WebP/AVIF/GIF/SVG;
- gzip/br;
- image range;
- wrong MIME.

### Densidad

- 1 image;
- 10;
- 30;
- 100;
- single CDN;
- multi-CDN;
- lazy loading;
- virtualized list;
- repeated identical asset;
- unique assets.

### Real pages

Sin login personal:

- Google Search;
- Google Images;
- Wikipedia;
- GitHub público;
- sitio de noticias;
- e-commerce público;
- feed/infinite scroll.

## Repeticiones

Por escenario estable:

- mínimo 30 repeticiones para p50/p95 razonables;
- 100 para microbench críticos cuando sea barato;
- randomizar orden SAFE/BLOCK cuando corresponda;
- warmup separado, no mezclar con samples.

## Timestamps

Correlacionar monotonic timestamps:

1. navigation start;
2. DNS start/end;
3. CONNECT accepted;
4. browser TLS ready;
5. upstream connect/TLS;
6. upstream first byte;
7. response complete o stream milestone;
8. decompress done;
9. image authority queue enter;
10. decode/preprocess start/end;
11. GloshIA inference start/end;
12. decision ready;
13. browser write start/end;
14. first approved image visible;
15. meaningful page visible;
16. page stable/lazy batch stable.

No intentar inferir “first paint” sólo desde logs de proxy; usar evidencia visual/instrumentación adecuada.

## Métricas de imagen

- download bytes/time;
- decompression;
- sniff/decode;
- queue wait;
- inference;
- cache lookup;
- decision latency;
- delivery latency;
- SAFE original bytes equality;
- BLOCK original delivery=0.

## Métricas de página

- navigation complete;
- first meaningful content;
- first approved image;
- all above-fold images resolved;
- stable viewport;
- scroll jank;
- lazy-load decision lag;
- concurrent requests;
- classification queue peak.

## Sistema

Por run:

- PSS total;
- native heap;
- Java heap;
- FD owned/global si accesible;
- CPU process;
- thread count;
- network bytes;
- battery delta/energy stats;
- temperature;
- throttling;
- crashes;
- ANR;
- OOM;
- native tombstones.

## Long-run

Campañas:

- 30 min active browsing;
- 2 h mixed browsing;
- overnight idle with guard/VPN;
- 24 h normal device use cuando producto esté cerca.

Requisitos:

- no memory/FD growth lineal;
- no wakeup loop;
- no VPN reconnect storm;
- no repeated model reload.

## Comparaciones

Para cada métrica reportar:

- Chrome baseline;
- Glosh enabled;
- absolute delta;
- percentage delta;
- p50;
- p95;
- p99;
- max;
- sample count.

## Optimización por prioridad

Sólo después del baseline, atacar:

1. serialización innecesaria de inference;
2. repeated safe downloads;
3. compression identity;
4. full-body buffering non-image;
5. leaf certificate generation;
6. worker/connection architecture;
7. safe cache;
8. native transport buffers;
9. surface/capture fallback cost.

## Safe cache benchmark

Comparar:

- first SAFE;
- reload same validators;
- repeated content different URL;
- policy/model generation changed;
- BLOCK repeated.

Confirmar que cache nunca conserva original BLOCK/UNKNOWN.

## Fallback visual benchmark

Sólo si 13B existe:

- tiles changed per scroll;
- candidate regions;
- capture FPS;
- GloshIA region calls;
- latency to release;
- CPU/PSS;
- frames opaque;
- stale rejects.

No permitir fallback full-screen continuous por comodidad de implementación.

## Gates de seguridad no negociables

Performance PASS nunca compensa:

- raw exposure >0;
- stale >0;
- direct bypass >0;
- BLOCK original delivered >0;
- process-death exposure.

## Objetivos iniciales a validar

No son Definition of Done hasta comparar baseline:

- isolated SAFE added latency p50 ideal <300 ms A23;
- p95 ideal <800 ms A23;
- S22 significativamente menor;
- 30-image page no debe sumar inferencias estrictamente seriales perceptibles;
- no flashes/grid/black screen sostenido;
- batería total Glosh objetivo global <3%/día;
- idle guard overhead mínimo.

## Artefactos

- raw CSV/JSON metrics;
- device/build/model hashes;
- network conditions;
- traces sanitizadas;
- screenshots/video gates cuando haga falta;
- summary p50/p95/p99;
- regressions vs previous DEV.

## Definition of Done

PERF-14 PASS cuando:

- seguridad permanece 0-exposure;
- navegación general se siente comparable a Chrome normal;
- p95/p99 no muestran colas patológicas;
- memoria/FD son estables;
- battery/thermal cumplen targets acordados;
- A23 y S22 pasan;
- optimizaciones no introducen bypass ni cambian GloshIA thresholds.
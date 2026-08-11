# DAG Browser 201 - diagnóstico de fuentes compactas en SM-S908E

Fecha: 2026-08-11

Estado: diagnóstico cerrado; sin cambio de política ni modelo

## Entorno

- dispositivo: Samsung SM-S908E;
- APK: `com.contentfilter.dagbrowser.diagnostic.dev`;
- versión: `0.70.05-diagnostic`, versionCode `4`;
- extensión integrada: `2.0.5`;
- modelo oficial: GloshIA Visual R3.1;
- página física: resultados Google para `mujer bikini`;
- APK SHA-256:
  `f663d325625ed6ac4e6a8437d6797deac05d11a7ae1f307d7816d9eabe5bc311`.

La instrumentación es exclusiva del flavor Diagnostic. Android la habilita por
un mensaje explícito y el contenido sólo devuelve metadatos acotados: tamaños,
presencia de `srcset`, cantidad de candidatos, densidad, `picture` y si la
fuente es inline. No registra URL, consulta, bytes ni píxeles. DEV y LAB no
habilitan ni reciben estos eventos.

## Resultado físico

La captura mostró el comportamiento denunciado: los resultados grandes
sensibles quedaron reemplazados por el placeholder gris, mientras algunas
miniaturas femeninas de filtros rápidos siguieron visibles.

La primera medición observó 128 eventos de fuente compacta. Ciento veinte eran
recursos inline o placeholders ya aplicados por DAG —102 de ellos `1x1`— y se
excluyeron en origen para que no vuelvan a contaminar el laboratorio. Los ocho
elementos raster web no inline que
conservaron sus dimensiones reales fueron siete `108x144` y uno `192x144`. Los
ocho informaron:

- `srcset=false`;
- cero candidatos;
- cero candidatos de mayor ancho;
- cero descriptores de densidad;
- cero fuentes `picture`;
- `currentSrc` sin selección alternativa.

Por lo tanto Google no expone, en estos elementos, una variante mayor mediante
los mecanismos web estándar que DAG pueda seleccionar de forma general. Parsear
URLs internas de Google sería una excepción específica prohibida y frágil.

El pipeline nativo registró 99 decisiones. Doce correspondieron a las
miniaturas físicas críticas de `62x82` o `56x56`: cuatro fueron bloqueadas y
ocho permitidas. Los puntajes permitidos fueron `0,0283`, `0,0851`, `0,1157`,
`0,2717`, `0,2948`, `0,3629`, `0,8400` y `0,8419`. Las cuatro bloqueadas
marcaron `0,9613`, `0,9774`, `0,9907` y `0,9973` y usaron `full_strong`.

La página quedó visible en `1377 ms` y completó el análisis inicial en
`4640 ms`. No hubo crash ni ANR en la captura.

## Conclusión

El fallo compacto no es una omisión del interceptor ni se resuelve escogiendo
un `srcset`/`picture`: el raster pequeño sí llega al pipeline, pero R3.1 puede
perder casi toda la señal visual. Ningún umbral común puede bloquear puntajes de
`0,0283-0,3629` sin bloquear masivamente logos, productos, hombres y controles
permitidos.

Quedan sólo dos caminos técnicamente honestos:

1. crear un modelo con corpus compacto realmente independiente que pase los
   gates de seguridad y falsos filtros; o
2. aprobar una política explícita de cierre por incertidumbre para miniaturas,
   aceptando que bloqueará contenido inocente.

No se implementó el segundo camino porque modificaría la política visual y los
umbrales sin autorización. R3.1, el umbral `0,40`, las regiones, los hilos y
`final_sealed` permanecen intactos.

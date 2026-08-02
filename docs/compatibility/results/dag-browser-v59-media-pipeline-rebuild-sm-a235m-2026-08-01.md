# DAG Browser 59 - reconstruccion global del pipeline multimedia

Fecha de cierre: 2026-08-01  
Ticket: `DAG-V3-MEDIA-PIPELINE-REBUILD-18`  
Dispositivo: Samsung SM-A235M `R58T34V31AE`, Android 14, `arm64-v8a`

## Resultado

El pipeline de presentacion fue reconstruido sin reglas por sitio ni por modelo
de telefono. La red sigue siendo la autoridad: una imagen HTTP(S) queda
retenida hasta recibir un `allow` autenticado y cualquier error cierra seguro.
La pagina conserva su CSS, iconos, fondos y lazy loading originales.

Se eliminaron los barridos globales y la reescritura permanente de fondos,
pseudo-elementos, listas y bordes. `barrier.js` mantiene un indice acotado de
medios, agrupa lecturas de geometria por cuadro y usa `IntersectionObserver`
para anticipar 640 px sin escuchar cada desplazamiento. Los anuncios quedaron
aislados en `ads.js`.

El techo de handles de respuesta paso de 64 a 128 para absorber rafagas reales
sin aumentar el presupuesto de bytes: siguen siendo 8 MiB totales y 2 MiB por
recurso. La respuesta numero 129 falla cerrada y tiene prueba automatica.

## Matriz fisica final

Cada valor es una muestra fria de un sitio vivo, no una promesa universal.
`pagina / fotos iniciales / estructura visible`:

| Sitio | Tiempos | p95 cuadro | Cuadros tardios | PSS / RSS | Resultado |
| --- | --- | ---: | ---: | ---: | --- |
| Mimo | `4.934 / 4.632 / 703 ms` | 12 ms | 2,17 % | 256.332 / 353.552 KiB | Completo |
| Cheeky | `16.031 / actividad continua / 2.239 ms` | 12 ms | 0,79 % | 287.582 / 406.160 KiB | Visual completo; no emitio quietud en 30 s |
| Fravega | `7.726 / 18.787 / 732 ms` | 26 ms | 3,28 % | 274.057 / 387.832 KiB | Completo en observacion de 55 s |

No hubo crash ni ANR. Fravega proceso una rafaga de 155 respuestas sin el
rechazo inmediato que aparecia con el techo de 64. Una corrida corta puede no
emitir quietud cuando el sitio sigue agregando recursos; la corrida larga
confirmo que el contador termina y no queda trabado.

## Comparacion util, no extrapolable

En Mimo, contra la corrida inmediatamente anterior de DAG 59 sin prioridad por
interseccion (`22.238 ms` fotos y `22.018 ms` pagina), la version final bajo a
`4.632 ms` y `4.934 ms`: aproximadamente 79 % y 78 % en esa pareja diagnostica.
Contra la muestra DAG 58, la estructura visible fue 2,9 % mas lenta y las
senales completas 31 %/16 % mas lentas, pero el p95 mejoro 20 %, PSS bajo 15,4
% y RSS 13,2 %. La red y el contenido vivo variaron; estos porcentajes explican
la decision tecnica, no estiman todas las paginas.

## Gates automaticos

- 21 pruebas JavaScript, incluida una pagina DOM real en Chrome: aprobadas;
- 146 pruebas unitarias Kotlin: aprobadas, cero omitidas o fallidas;
- `ktlintCheck`, `lintDevDebug`, `assembleDevDebug` y
  `assembleDevDebugAndroidTest`: aprobados;
- el contrato rechaza excepciones por los tres comercios de la matriz y por
  modelos de telefono conocidos.

## Limites declarados

- sitios vivos cambian contenido, red y actividad entre muestras;
- una pagina infinita puede no quedar quieta dentro de una ventana corta aunque
  ya sea utilizable;
- visuales `data:`/`blob:` generados se analizan por el canal inline acotado;
  CSS inaccesible entre origenes y animaciones no se liberan por suposicion;
- el fixture local autofirmado fue rechazado por TLS. No se instalo una CA ni
  se relajo Gecko, por lo que aun falta un fixture HTTPS determinista confiable;
- este lote no cambia pesos, umbral ni politica de GloshIA.

Evidencia cruda fuera de Git:
`.codex-tmp/dag-perf-lab/live-runs/20260802T013740Z-R58T34V31AE-mimo-v59-intersection-cold`,
`.codex-tmp/dag-perf-lab/live-runs/20260802T013845Z-R58T34V31AE-cheeky-v59-intersection-cold`
y
`.codex-tmp/dag-perf-lab/live-runs/20260802T014322Z-R58T34V31AE-fravega-v59-intersection-55s`.

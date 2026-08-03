# DAG 94 - causa raiz de degradacion prolongada

Ticket: `DAG-MIMO-LONG-RUN-ROOT-CAUSE-94`  
Dispositivo: Samsung SM-A235M (A23), Android 14  
APK: DEV local, `versionCode=94`, `versionName=0.69.0-dev`  
Extension: `1.49.0`  
Publicacion: ninguna

## Problema reproducido

Mimo se uso unicamente como caso dinamico repetible. En DAG 92, despues de
160 aperturas/cierres del menu, el proceso de contenido Gecko quedaba entre
96,8 % y 105 % de CPU. Tras 12 segundos quieto seguia cerca de 100 %, con
`PSS=322936 KiB`, `RSS=323356 KiB` y `swap PSS=65688 KiB`.

Chrome fue el control de producto en el mismo A23: el usuario confirmo que el
menu no desarrolla la degradacion prolongada. No se obtuvo una matriz numerica
de Chrome porque la comparacion controlada se centro en aislar las capas de DAG.

## Aislamiento

Se instalaron candidatas DEV locales y se retiro todo codigo diagnostico al
terminar:

1. Gecko casi puro: no reprodujo el ciclo persistente de CPU.
2. Compuerta de red, bloqueo y analisis de bytes: no reprodujo el ciclo.
3. Proteccion completa sin observacion DOM continua: no reprodujo el ciclo.
4. Proteccion completa con observacion y presentacion idempotentes: no
   reprodujo el ciclo y mantuvo la cobertura visual.

La causa era reingresar imagenes ya resueltas cuando el sitio mutaba atributos
de presentacion como `sizes` o `srcset`. La barrera podia volver a quitar y
poner el estado estable para la misma fuente, provocando realimentacion con el
layout dinamico de la pagina.

## Correccion general

La barrera recuerda por elemento la fuente exacta ya resuelta y el trabajo
pendiente. Una mutacion de presentacion para la misma fuente no hace nada. Un
cambio real de fuente invalida el estado y cruza nuevamente la misma compuerta.
No hay dominios, telefonos ni reglas especiales. GloshIA R1, pesos, umbrales,
politica de decisiones, anuncios y bytes interceptados no cambiaron.

## Matriz final DAG 94

Proceso de contenido activo, valores en KiB:

| Interacciones | CPU | PSS | RSS | Swap PSS |
| ---: | ---: | ---: | ---: | ---: |
| Inicio | 2 % | 140529 | 212604 | 409 |
| 40 | 2 % | 174906 | 242848 | 244 |
| 80 | 2 % | 224471 | 298096 | 239 |
| 120 | 2 % | 238706 | 310320 | 244 |
| 160 | 2 % | 236927 | 308196 | 244 |
| Reposo posterior | 2 % | 209308 | 278596 | 273 |

La memoria se aplano entre 80 y 160 interacciones y recupero aproximadamente
29 MiB de RSS al quedar quieta. La CPU posterior queda muy por debajo del gate
de 10 %.

## Compatibilidad

- Mimo: 160 interacciones completas; menu operativo y sin CPU persistente.
- Cheeky: home, controles, iconos e imagenes visibles; proceso en reposo sin
  ciclo alto.
- Fravega: estructura y controles funcionales. La compuerta vigente registro
  107 `model_allow`, 3 `animated_image` y 2 `model_filter`. Los cuadros negros
  reutilizados corresponden a politica visual previa y no a DAG 94; queda como
  trabajo separado y no se oculto como exito de este ticket.
- La compuerta conserva fail-closed: una fuente nueva sigue retenida antes del
  render y solo se revela despues de su decision.

## Verificacion

- 15 pruebas WebExtension, incluida una prueba conductual de idempotencia.
- Pruebas unitarias Android, Ktlint, Lint y build DEV aprobados.
- `git diff --check` aprobado.
- Sin crash, ANR u OOM durante la matriz.
- Sin archivos, temporizadores, flags o scripts diagnosticos en el resultado.

Resultado: `GO local` para la correccion de degradacion prolongada. Sin push ni
publicacion DEV. El tratamiento de animaciones y falsos filtros de Fravega no
forma parte de este gate.

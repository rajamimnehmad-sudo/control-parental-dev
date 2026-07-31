# GloshIA Lab - calibración preliminar del 2026-07-31

## Alcance

Este experimento compara la política visual de DAG 36 con una calibración
regional candidata. Usa el mismo modelo ONNX INT8, con SHA-256
`2d52bd9e5eb4cd448cb0d64a784b2ee6f761ad20e890c57b898fd7991d29a9ee`.

- No reentrena ni reemplaza el modelo.
- No modifica Android ni DAG.
- No abre las 200 muestras `final_sealed`.
- No usa Supabase, API paga, GPU ni datos privados.
- Las 100 decisiones humanas permanecen fuera de Git en `.codex-tmp`.

## Verdad humana disponible

La ronda contiene 100 revisiones: 85 `allow`, 10 `filter` y 5 `doubt`. Las
cinco dudas se excluyen de la matriz, por lo que existen 95 decisiones
binarias distribuidas en 65 series independientes.

La cola fue enriquecida con casos cercanos al umbral y otros estratos
difíciles. Es una prueba de estrés y no estima directamente la frecuencia de
errores en Internet.

## Política vigente

Sobre las 95 decisiones binarias:

| Resultado | Cantidad |
| --- | ---: |
| Filtro correcto | 8 |
| Permiso incorrecto | 2 |
| Filtro incorrecto | 40 |
| Permiso correcto | 45 |

- Recall de filtro: `0,80`.
- Recall de permiso: `0,529412`.
- Exactitud de la prueba de estrés: `0,557895`.

Los dos permisos incorrectos pertenecen a una misma serie de Cannes y tienen
scores completos `0,351168` y `0,375664`, sin apoyo regional alto. Los filtros
incorrectos se concentran visualmente en hombres, personas cubiertas, grupos,
deporte, escenas escolares o militares, ilustraciones y sujetos pequeños.

## Barrido regional diagnóstico

El laboratorio fuerza todas las vistas regionales en un archivo separado. El
runner normal y `predictions.jsonl` permanecen intactos. Una cuadrícula acotada
elige parámetros solamente con las 72 decisiones `main_eval`, manteniendo como
piso su recall de filtro. Luego informa, sin reajustar, las 23 decisiones
`difficult`.

Parámetros seleccionados para el candidato de laboratorio:

- umbral completo alto: `0,44`;
- piso de revisión: `0,30`;
- umbral regional: `0,41`;
- votos regionales: `1`;
- política panorámica: sin cambios.

El candidato no es un simple aumento de umbral: en la banda intermedia exige
evidencia regional. Continúa siendo una política experimental, no una versión
aprobada de DAG.

## Comparación

| Conjunto | Política | Filtro correcto | Permiso incorrecto | Filtro incorrecto | Permiso correcto | Exactitud |
| --- | --- | ---: | ---: | ---: | ---: | ---: |
| `main_eval` | vigente | 6 | 2 | 31 | 33 | `0,541667` |
| `main_eval` | candidata | 6 | 2 | 23 | 41 | `0,652778` |
| `difficult` | vigente | 2 | 0 | 9 | 12 | `0,608696` |
| `difficult` | candidata | 2 | 0 | 7 | 14 | `0,695652` |
| todo revisado | vigente | 8 | 2 | 40 | 45 | `0,557895` |
| todo revisado | candidata | 8 | 2 | 30 | 55 | `0,663158` |

La candidata cambia 22 decisiones revisadas: libera 16 y filtra 6 que la
política vigente permitía. Las seis nuevas decisiones también son falsos
filtros según esta verdad humana. La mejora neta es real dentro de la muestra,
pero la validación independiente contiene sólo dos positivos.

## Cobertura y costo estimado

El barrido de las 800 muestras no selladas terminó sin errores. La candidata
cambiaría la distribución de `499 filter / 301 allow` a
`487 filter / 313 allow`: 18 pasarían de filtro a permiso y 6 en sentido
contrario.

Con corte temprano estimado:

- inferencias medias por imagen: `1,485` a `1,624` (`+9,36 %`);
- mediana: `1` en ambas políticas;
- p95 y máximo: `5` en ambas políticas.

Esto estima trabajo, no latencia Android. En la Mac, una inferencia tuvo una
mediana de `59,442 ms` y cinco de `278,607 ms`. Cualquier integración futura
debe medir el runtime optimizado en el teléfono objetivo.

## Decisión

`NO-GO` para integrar esta calibración en DAG.

Aunque reduce el sobre-filtrado sin perder los ocho filtros ya detectados, la
evidencia positiva independiente es demasiado pequeña, conserva dos permisos
incorrectos de una misma serie y todavía filtra incorrectamente 30 de 85
permitidas en la prueba de estrés. Abrir ahora `final_sealed` gastaría el único
examen final con una candidata que aún no merece promoción.

## Próximo gate

El siguiente candidato necesita mejorar el modelo, no seguir ajustando este
mismo umbral:

1. preparar negativos difíciles de hombres, grupos, menores en contextos
   normales, deporte y personas cubiertas;
2. preparar positivos independientes donde la persona relevante sea pequeña o
   aparezca dentro de un grupo;
3. usar únicamente material con autorización de entrenamiento verificable;
4. entrenar fuera de este banco de evaluación y comparar primero en
   `difficult`;
5. congelar modelo, calibración y límites antes de abrir una sola vez
   `final_sealed`.

El corpus Wikimedia vigente conserva `training_authorized: false`; sus
revisiones sirven como evaluación y diagnóstico, no como entrenamiento.

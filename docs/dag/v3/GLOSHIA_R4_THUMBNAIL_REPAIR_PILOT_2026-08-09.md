# GloshIA R4 thumbnail repair — piloto local

Fecha: 2026-08-09

Estado: `NO-GO` para integración; R3.1 permanece oficial

Final sealed: cerrado

## Objetivo

Medir y reparar la pérdida de señal de GloshIA Visual cuando una imagen se
presenta como miniatura pequeña, comprimida o enmascarada. No se cambió el modelo
integrado, el umbral `0,40`, la política regional, Android ni el APK.

## Datos reproducibles

Se partió del split privado agrupado R3.2 y se generaron transformaciones de la
imagen completa: `thumb160_q45`, `thumb96_q35` y `circle128_q45`. Esta última
conserva la relación de aspecto y aplica una elipse sobre la imagen contenida;
su nombre histórico es impreciso y no representa un `object-fit: cover`
circular real. No se crearon
recortes regionales automáticos porque un recorte puede eliminar el hecho que
justificó la etiqueta y requiere revisión humana separada.

- train agregado: 360 variantes, 60 allow y 60 filter por transformación;
- validation agregado: 84 variantes, 21 allow y 7 filter por transformación;
- grupos train/validation sin cruces;
- `frozen_test` no fue aumentado, leído ni puntuado;
- `final_sealed_opened: false`;
- artefacto privado: `.codex-tmp/gloshia-r4-thumbnail-repair-20260809/pilot-03/`.

Las rutas históricas archivadas se resolvieron antes de entrenar. Los nombres
direccionados por contenido conservaron la identidad de manifiesto y la huella
actual de cada archivo quedó registrada por separado. El pipeline y el
evaluador exigen caché local y no descargan pesos durante la corrida.

## Diagnóstico R3.1

Sobre 28 originales y sus 84 variantes, R3.1 produjo:

| Vista | falsos permisos | falsos filtros |
| --- | ---: | ---: |
| original | 1/7 | 2/21 |
| 160 px | 3/7 | 3/21 |
| 96 px | 3/7 | 0/21 |
| máscara elíptica contenida | 5/7 | 0/21 |

Hubo 13 cambios de decisión en 84 pares; 8 fueron degradaciones peligrosas de
`filter` a `allow`. La caída circular de recall filter, de 6/7 originales a
2/7 enmascaradas, confirma una pérdida de señal por reducción y máscara, pero
no demuestra por sí sola el comportamiento de un recorte circular real.

## Pilotos

Ambos partieron del checkpoint que produjo exactamente el R3.1 oficial. Se
entrenó en CPU local y se seleccionó sólo con validation.

| candidato | cambio controlado | falsos permisos | falsos filtros | decisión |
| --- | --- | ---: | ---: | --- |
| R3.1 baseline | ninguno | 12/28 | 5/84 | referencia |
| piloto 01 | 2 épocas, LR 0,25, peso 0,8 | 11/28 | 7/84 | NO-GO |
| piloto 02 | 1 época, LR 0,25, peso 1,0 | 11/28 | 10/84 | NO-GO |

El piloto 01 mejoró una miniatura peligrosa de 160 px y conservó originales,
pero no corrigió ninguna variante enmascarada peligrosa. El piloto 02 tampoco
las corrigió y agregó tres falsos filtros enmascarados. Seguir variando pesos no
está justificado.

## Decisión y siguiente paso

La ampliación de datos con clasificación independiente queda rechazada. No se
exporta ONNX, no se abre `frozen_test`, no se instala APK y R3.1 continúa como
único modelo oficial.

## Piloto de consistencia explícita

Se implementó un entrenador aislado que asigna el mismo peso a cada familia,
acerca cada variante al original y ancla el original a la salida exacta del
ONNX R3.1 oficial. Los ocho gates se congelaron antes de entrenar. El epoch 1
redujo las degradaciones peligrosas entre pares de 8 a 4 y los falsos permisos
agregados de 11 a 10, pero falló el gate: originales 1→2 falsos permisos,
variantes enmascaradas 5→5 falsos permisos y 0→2 falsos filtros, y pares seguros
1→3 degradaciones. El epoch 2 produjo las mismas decisiones. Resultado:
`NO-GO`, sin exportación ni apertura de `frozen_test`.

El siguiente paso fiable es generar recortes cuadrados centrados con máscara
circular real y revisar humanamente una cola mínima preseleccionada. No se puede
heredar automáticamente la etiqueta del original porque el recorte puede quitar
el hecho visual que la justificaba. Solo después de esa revisión corresponde
entrenar otro candidato. No se crean excepciones por sitio ni se modifica la
política para rescatar al candidato.

La cola quedó preparada fuera de Git en
`.codex-tmp/gloshia-r4-thumbnail-repair-20260809/circle-review-01/`: se
generaron y puntuaron 642 recortes train, y se conservaron solo 24 para revisión
(12 por etiqueta del padre), priorizando desacuerdos fuertes de R3. La inspección
confirmó que la etiqueta no es heredable: en varios recortes desaparece la
persona o señal del original y quedan autos, muebles o ropa; en otros el nuevo
encuadre enfatiza una señal antes secundaria. El manifiesto queda marcado
`review_only_not_training_data`, sin `target` entrenable y con
`training_authorized: false` hasta revisión.

El propietario completó las 24 decisiones: 10 `allow`, 11 `filter` y 3
`doubt`. Las dudas fueron excluidas. La política privada quedó congelada como
`gloshia-r4-owner-visual-policy-v1`: las señales femeninas se aplican desde
aproximadamente 5 años o edad incierta; no se aplican automáticamente a
presentación masculina; un escote no necesita ser grande o profundo; y una
miniatura con persona potencialmente relevante pero evidencia insuficiente
queda cerrada. La misma regla visual aplica a imagen completa y miniatura, pero
un recorte puede cambiar la decisión si cambia los hechos visibles.

El pool derivado contiene 21 casos claros autorizados para experimento privado
(10 allow/11 filter) y 0 dudas. R3.1 tiene en este conjunto deliberadamente
difícil 5/10 falsos filtros y 6/11 falsos permisos. El pool aún no tiene split y
no autoriza ONNX, APK ni cambio de runtime. Artefacto privado:
`.codex-tmp/gloshia-r4-thumbnail-repair-20260809/circle-review-01/reviewed-pool.json`.

## Contraste R1 frente a R3.1

Se comparó R1 porque el propietario observó permisos dudosos en fotos de
mujeres, cuerpos y escotes. El R1 oficial usa `ConvInteger`, operación que ORT
1.19.2 para macOS no ejecuta. Para no alterar Android se generó una copia de
laboratorio que conserva la cuantización y reemplaza solo esa convolución por
aritmética FP32 sobre valores enteros. No es un modelo candidato ni puede entrar
al APK.

La copia se contrastó contra 33 probabilidades históricas producidas por R1 en
el S22: mantuvo 32/33 decisiones a umbral `0,40`; la única diferencia fue un
caso limítrofe (`0,3973` local frente a `0,4020` Android). El delta absoluto fue
`0,01135` medio y `0,03443` máximo. Por eso las métricas R1 siguientes son una
estimación válida para diferencias grandes, pero cualquier salida local a
menos de `0,035` del umbral queda marcada como incierta.

| Examen | modelo | falsos permisos | falsos filtros | aciertos |
| --- | --- | ---: | ---: | ---: |
| 21 recortes circulares revisados | R3.1 oficial | 6/11 | 5/10 | 10/21 |
| 21 recortes circulares revisados | R1 estimado | 6/11 | 7/10 | 8/21 |
| 112 vistas validation | R3.1 oficial | 12/28 | 5/84 | 95/112 |
| 112 vistas validation | R1 estimado | 9/28 | 26/84 | 77/112 |

Dos de las 21 salidas R1 son limítrofes: una `filter` humana quedó en `0,3698`
y una `allow` humana en `0,4180`. Aun suponiendo que Android corrigiera ambas,
R1 solo empataría los 10/21 aciertos de R3.1; no demuestra una mejora en los
casos seleccionados por el propietario y sí presenta mayor riesgo de
sobrefiltrado.

En las 28 imágenes completas de validation R1 pareció detectar 7/7 positivas
frente a 6/7 de R3.1, pero dos de esos aciertos R1 también están pegados al
umbral y R1 filtró 7/21 permitidas frente a 2/21 de R3.1. En los recortes reales
revisados por el propietario la posible ventaja desaparece. Resultado:
`NO-GO` para restaurar R1 o usarlo como reemplazo. R1 sirve únicamente como
comparador para seleccionar desacuerdos; el siguiente candidato debe aprender
los positivos de cuerpos/escotes que ambos omiten y conservar los negativos que
R3.1 ya resuelve mejor.

Evidencia privada reproducible:

- `.codex-tmp/gloshia-r4-thumbnail-repair-20260809/circle-review-01/r1-reviewed-score-compat.json`;
- `.codex-tmp/gloshia-r4-thumbnail-repair-20260809/pilot-03/r1-validation-baseline-compat.json`;
- `.codex-tmp/gloshia-r4-thumbnail-repair-20260809/r1-conv-integer-value-compat.json`.

No se cambió el modelo oficial, el umbral, la política, DAG, Android ni el APK;
`final_sealed` permanece cerrado.

## Reparación agrupada con decisiones del propietario

Los 21 recortes se dividieron antes de entrenar en cinco folds estratificados,
con 21 grupos únicos. Cada fold excluye de train tanto el recorte como la foto
padre y cualquier variante de su grupo. La inspección confirmó la causa del
fracaso de la consistencia anterior: 10/21 recortes cambian legítimamente de
decisión respecto de su padre (cinco `allow→filter` y cinco `filter→allow`).
Por eso no se volvió a imponer igualdad entre vistas.

El gate congelado exigió reducir OOF de 6 a 4 falsos permisos o menos sin
superar 5 falsos filtros. Además, cada fold debía conservar fotos completas en
como máximo 1/7 falsos permisos y 2/21 falsos filtros, y validation completa en
como máximo 12/28 y 5/84 respectivamente.

El piloto lineal congeló encoder y proyección y ajustó sólo los 513 parámetros
de la cabeza con destilación del checkpoint R3.1. OOF mejoró a 4/11 falsos
permisos, pero empeoró a 6/10 falsos filtros. Los cinco modelos deterioraron las
fotos completas y los falsos filtros validation; resultado `NO-GO`, sin
checkpoint final.

El piloto de representación mantuvo la misma arquitectura y habilitó sólo la
última capa visual, normalización, proyección y cabeza. Los recortes humanos de
train recibieron peso acotado y quedaron sin ancla al padre; el resto conservó
anclaje al ONNX R3.1. En cada fold mantuvo fotos completas exactamente en 1/7
falsos permisos y 2/21 falsos filtros, redujo los falsos permisos de variantes
de 11 a 9 y las degradaciones peligrosas de 8 a 6. Sin embargo creó 1 falso
filtro circular donde R3.1 tenía 0 y, en OOF sobre los 21 recortes humanos,
quedó exactamente igual que R3.1: 6/11 falsos permisos y 5/10 falsos filtros.
Resultado `NO-GO`; no exportar ni integrar.

La conclusión ya no depende de elegir pesos: 21 grupos difíciles alcanzan para
detectar regresiones, pero no para generalizar a personas nuevas. Seguir
entrenando sobre ellos sería sobreajuste. Se preparó una segunda cola activa
excluyendo los 21 grupos anteriores: 621 recortes fueron puntuados y se
seleccionaron 24 nuevos, 12/12 por etiqueta padre, en
`.codex-tmp/gloshia-r4-thumbnail-repair-20260809/circle-review-02/`. La lámina
numerada `review-sheet.png` permite corregir una propuesta de etiquetas en un
solo mensaje. Hasta completar esa revisión, la cola es sólo
`review_only_not_training_data`.

### Segunda revisión y repetición

El propietario aceptó la propuesta asistida con una corrección explícita: el
caso 17 es `filter` porque muestra piernas de mujer. La segunda cola terminó en
8 allow, 13 filter y 3 doubt excluidas. La unión privada quedó en 42 grupos
claros, 18 allow y 24 filter, sin duplicados; las seis dudas acumuladas siguen
fuera de entrenamiento y evaluación binaria.

R3.1, sobre esta selección deliberadamente difícil, obtuvo 16/24 falsos
permisos y 11/18 falsos filtros. El gate cruzado exigió bajar falsos permisos a
12 o menos sin superar 11 falsos filtros y mantener intacto el examen fijo de
112 vistas.

La cabeza lineal OOF bajó falsos permisos a 10/24, pero elevó falsos filtros a
13/18 y deterioró validation; quedó `NO-GO`. El ajuste de representación
conservador mantuvo fotos completas y volvió a mejorar variantes sintéticas,
pero OOF quedó exactamente igual a R3.1: 16/24 falsos permisos y 11/18 falsos
filtros. También conservó el falso filtro circular nuevo. Los cinco checkpoints
se eliminaron y no se entrenaron variantes de pesos adicionales.

Conclusión: incluso 42 desacuerdos heterogéneos no enseñan una señal semántica
generalizable. El próximo corpus no debe ser otra cola genérica de desacuerdos:
debe reunir series dirigidas y variadas de piernas femeninas, escotes, hombros,
sujetos pequeños y negativos visualmente cercanos, con agrupación por persona,
sesión y origen. Puede preetiquetarse de forma asistida para minimizar trabajo,
pero las correcciones del propietario siguen siendo el gate de política. Este
corpus dirigido todavía no está autorizado ni preparado.

### Corpus semántico dirigido autorizado

El propietario autorizó preparar 120 vistas nuevas con revisión asistida. El
plan quedó congelado en `r4_targeted_review_plan_v1.json`: 36 ropa moderna, 30
deportes/sensibles, 18 personas comerciales, 16 sujetos parciales o pequeños y
20 negativos/catálogos/banners/controles cercanos. La selección usa 62 padres
filter y 58 allow únicamente como estratos diagnósticos; esa etiqueta no se
copia al recorte.

Se excluyeron los 45 grupos ya presentes en las dos colas anteriores. R3.1
puntuó 597 recortes restantes y se eligieron 120 errores fuertes dentro de los
estratos semánticos, con 120 grupos únicos. Quedaron cinco tandas numeradas de
24 bajo `.codex-tmp/gloshia-r4-thumbnail-repair-20260809/targeted-review-v1/`.
Cada tanda contiene manifiesto, imágenes y `review-sheet.png`. Ninguna decisión
está autorizada para entrenamiento hasta que el propietario acepte o corrija la
propuesta asistida; no se abrió ningún examen sellado ni se entrenó modelo.

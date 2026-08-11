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

La revisión de las cinco tandas terminó. El propietario aceptó las propuestas
asistidas y corrigió explícitamente `batch-01/16=filter`,
`batch-03/7=filter`, `batch-03/17=allow` y `batch-04/15=allow`. El pool
consolidado contiene 120 decisiones binarias, 59 allow y 61 filter, con 120
grupos únicos y ninguna duda. Las etiquetas siguen marcadas
`training_authorized: false`: completar la revisión no autoriza por sí solo un
entrenamiento privado, un ONNX, un APK ni un cambio del modelo oficial.

R3.1 se midió a umbral `0,40` sobre ese pool dirigido y obtuvo 15/61 falsos
permisos y 43/59 falsos filtros. Los falsos filtros se distribuyeron entre las
cinco tandas (8, 9, 9, 9 y 8), por lo que no son un accidente de una sola hoja.
Las mayores concentraciones aparecieron en personas comerciales (11), ropa
moderna (10), sujetos parciales o pequeños (10) y deportes/sensibles (10). El
resultado confirma un conflicto de representación: mover el umbral para
recuperar permitidas agravaría los 15 falsos permisos y moverlo en la dirección
opuesta agravaría los 43 falsos filtros.

Evidencia privada:

- `targeted-review-v1/reviewed-pool-120.json`;
- `targeted-review-v1/r31-baseline-120.json`;
- `targeted-review-v1/batch-01..05/owner-decisions.json` y
  `reviewed-pool.json`.

Próximo gate: autorización explícita del propietario para uso privado de estas
120 etiquetas. Sólo entonces se puede congelar un A/B agrupado que aprenda la
representación y exija simultáneamente reducir ambos errores, preservar el
examen fijo de fotos completas y mantener `final_sealed` cerrado.

### A/B privado sobre 162 grupos

El propietario autorizó el uso privado de las etiquetas revisadas. Se unieron
los 120 grupos nuevos con los 42 claros anteriores: 162 grupos únicos, 77 allow
y 85 filter; las seis dudas históricas permanecieron excluidas. R3.1 obtuvo en
la unión 31/85 falsos permisos y 54/77 falsos filtros.

Antes de entrenar se congelaron cinco folds y un gate simétrico: reducir al
menos 20 % ambos errores OOF (máximo 24 falsos permisos y 43 falsos filtros),
conservar cada validation original en máximo 1/7 y 2/21, y conservar las 112
vistas fijas en máximo 12/28 y 5/84. Se reutilizó una sola configuración ya
conocida: última capa visual, normalización, proyección y cabeza; peso humano 8,
dos épocas, LR 0,25, class weight 0,625 y anclaje 1 al R3.1 oficial. No hubo
búsqueda de pesos ni selección sobre los folds retenidos.

El resultado OOF fue 27/85 falsos permisos y 54/77 falsos filtros. Corrigió
cuatro falsos permisos, pero no recuperó ninguna permitida neta y falló ambos
gates principales. En los 120 casos dirigidos pasó de 15/43 a 12/43; en los 42
anteriores, de 16/11 a 15/11. Además, el fold 2 elevó los falsos permisos de
fotos completas de 1/7 a 2/7 y los cinco folds agregaron un falso filtro en la
vista circular fija.

Resultado: `NO-GO`. Los cinco checkpoints fueron eliminados; se conservaron
splits, reportes, probabilidades y hashes. No se exportó ONNX, no se abrió
`frozen_test` ni `final_sealed`, y no se modificó R3.1, Android, APK, umbral ni
política. La evidencia principal es
`.codex-tmp/gloshia-r4-thumbnail-repair-20260809/r4-reviewed-representation-cv-03.json`.

La señal nueva ya no justifica variar pesos de esta misma última capa: el
cuello de botella es la representación visual. El próximo trabajo técnicamente
fundado es comparar backbones compactos con licencia y runtime Android
verificados, usando estos 162 grupos sólo como gate agrupado; ningún backbone
nuevo queda autorizado para integración por este resultado.

### Reentrenamiento limpio con la receta original de R1

Por decisión del propietario se contrastó una hipótesis distinta: no reparar
R3.1, sino repetir desde el TinyCLIP preentrenado original el procedimiento
compacto de R1. Cada fold aprendió una cabeza logística balanceada nueva y luego
ajustó durante ocho épocas la última capa visual, post-layernorm, proyección y
cabeza con las tasas, regularización, augmentación y umbral históricos de R1.
No se cargaron pesos ni respuestas de R3/R3.1, no hubo búsqueda de
hiperparámetros y cada recorte humano entró una sola vez, sin peso especial.

Train usó sólo las 642 fotos base originales disponibles —excluyendo en cada
fold todos los grupos retenidos— más los recortes humanos no retenidos. Las 360
variantes automáticas quedaron fuera: la revisión anterior ya demostró que un
recorte puede cambiar legítimamente de etiqueta. La selección de época usó sólo
las 28 fotos validation originales; las 112 vistas se conservaron como gate
antirregresión. `frozen_test` y `final_sealed` permanecieron cerrados.

El resultado OOF sobre los 162 grupos fue 9/85 falsos permisos y 56/77 falsos
filtros. Frente a R3.1 (`31/54`) corrigió 22 permisos peligrosos, pero agregó
dos bloqueos inocentes y falló el gate simétrico `24/43`. Los cinco folds
repitieron la misma regresión en fotos completas: 2/7 falsos permisos frente al
máximo 1/7. En las 112 vistas, los falsos permisos quedaron entre 11 y 12, pero
los falsos filtros entre 7 y 11, por encima del máximo 5 en todos los folds.

El R1 oficial, medido con el reemplazo de laboratorio compatible con ORT de
macOS, estimó `30/57` sobre los mismos 162 casos. El nuevo entrenamiento aprendió
mucho mejor los positivos pequeños que R1, pero prácticamente no recuperó
negativos (`56` frente a `57`) y tampoco superó el equilibrio de R3.1. Esta
comparación con R1 es aproximada cerca del umbral por la conversión técnica del
`ConvInteger`; la decisión de rechazo no depende de ella porque el A/B nuevo y
R3.1 se midieron directamente.

Resultado: `NO-GO`. Los cinco checkpoints y el checkpoint del piloto corto
fueron eliminados; el directorio de evidencia bajó de 196 MB a 5,4 MB. También
se retiró el entrenador específico de este ensayo porque no resolvió el gate.
Se conservaron únicamente splits, reportes, probabilidades y hashes bajo
`.codex-tmp/gloshia-r4-thumbnail-repair-20260809/r1-style-cv-v1/`. No se
exportó ONNX ni se modificaron R3.1, Android, APK, umbral o política.

Conclusión: reentrenar TinyCLIP desde su punto preentrenado como R1 no resuelve
simultáneamente seguridad y falsos bloqueos con el corpus actual. No repetir
esta receta ni volver a variar la última capa; el siguiente experimento debe
evaluar una representación compacta diferente bajo los mismos folds y gates,
con autorización separada antes de entrenar o integrar.

### Cierre de representaciones compactas locales

El propietario autorizó cerrar el lote con un gate más exigente y alineado con
seguridad: máximo 15 falsos permisos sobre los 162 grupos, sin superar los 54
falsos filtros de R3.1; además, cada candidato debía conservar originales en
máximo 1/7 y 2/21, y las 112 vistas fijas en máximo 12/28 y 5/84. Se mantuvieron
un solo modelo, el umbral `0,40` y una única inferencia por imagen.

Se hicieron únicamente pilotos de fold 0 y se detuvo cada vía al fallar el
control fijo:

| representación | configuración | control fijo FP/FF | originales FP/FF | retenido FP/FF | decisión |
| --- | --- | ---: | ---: | ---: | --- |
| R3.1 + pesos interpolados | 25 % del reentrenamiento limpio | 12/6 | 2/1 | 5/11 | `NO-GO` |
| R3.1 + cabeza residual MLP | encoder congelado, 16.449 parámetros | 11/4 | 1/1 | 7/11 | `NO-GO` |
| MobileNetV4 Conv Small | cabeza logística balanceada | 9/35 | 2/6 | 1/14 | `NO-GO` |
| MobileNetV4 Conv Small | cabeza logística sin balance | 11/22 | 2/2 | 5/10 | `NO-GO` |
| TinyCLIP 40M/32 | cabeza logística balanceada | 6/28 | 2/5 | 2/13 | `NO-GO` |
| TinyCLIP 40M/32 | cabeza logística sin balance | 9/18 | 2/3 | 3/9 | `NO-GO` |

La interpolación altera demasiado pronto la frontera que R3.1 ya protege. La
cabeza residual preserva el control, pero no generaliza a los grupos retenidos.
Los dos encoders alternativos reconocen más positivos, pero confunden demasiados
negativos visualmente cercanos y no cumplen el límite de falsos filtros. No se
justifica ejecutar cinco folds, afinar backbones completos, exportar ONNX ni
medir Android con candidatos que ya fallan el control funcional.

MobileNetV4 se evaluó desde el modelo `timm` Apache-2.0; sus pesos ImageNet
requieren revisión legal antes de cualquier producto. TinyCLIP 40M/32 proviene
del proyecto MIT de Microsoft/Cream, pero su visión desplegable tiene 39,7 M de
parámetros y 3,5 GFLOPs teóricos frente a 2,0 GFLOPs del TinyCLIP actual. Apple
MobileCLIP fue descartado antes de descargar o medir porque la licencia de sus
pesos prohíbe uso comercial.

Resultado final del lote: ningún candidato reemplaza a R3.1. Todos los
checkpoints y entrenadores experimentales rechazados fueron retirados; sólo se
conservan JSON pequeños de evidencia privada. R3.1, Android, APK, umbral y
política permanecen intactos; `frozen_test` y `final_sealed` siguen cerrados.

El siguiente paso seguro ya no es otra búsqueda local de arquitectura o pesos.
Requiere ampliar el corpus con negativos cercanos independientes —no elegidos
sólo por ser errores de R3.1— y positivos equivalentes, separados por identidad
y origen; después, entrenar o destilar en GPU una representación compacta y
evaluarla una sola vez con estos gates congelados. Ese trabajo necesita un
ticket y autorización nuevos.

### Corpus independiente y límite del entrenamiento local

Se recuperaron dos lotes históricos ya revisados por el propietario que nunca
habían sido autorizados como train: 625 decisiones, de las cuales 18 dudas se
excluyeron. Una imagen idéntica y con la misma decisión aparecía en ambos lotes
y se conservó una sola vez. La auditoría contra el split R3.1 posterior detectó
y excluyó otra coincidencia exacta que el SHA histórico no revelaba.

El importador antiguo había escrito en los 625 manifiestos un campo `sha256`
que no coincide con los bytes actuales, aunque IDs, URLs, tamaños, grupos y
hashes perceptuales sí son coherentes. No se alteró esa evidencia: el plan nuevo
conserva el valor como `source_declared_sha256`, calcula el SHA-256 real del
artefacto y usa ambos junto con URL, grupo y pHash para controlar contaminación.

El plan congelado contiene 605 imágenes únicas y no contaminadas:

| split nuevo | allow | filter | total |
| --- | ---: | ---: | ---: |
| train | 396 | 112 | 508 |
| holdout independiente | 76 | 21 | 97 |

Nueve series contienen tomas de ambas clases y permanecen completas en un solo
split. Al unir únicamente el train nuevo con las 642 fotos base originales, el
piloto tuvo 1.150 imágenes; no se incorporaron variantes automáticas ni los 162
casos dirigidos. Las 28 originales históricas eligieron el checkpoint y las 112
vistas, el holdout de 97 y los 162 casos quedaron como gates separados.

R3.1 obtuvo en el holdout nuevo 2/21 falsos permisos y 8/76 falsos filtros. El
gate exigió mejorar ambos, mantener los 162 casos en máximo 15/85 y 54/77, y
preservar originales en máximo 1/7 y 2/21 y las 112 vistas en 12/28 y 5/84.

Se ejecutaron cuatro pruebas acotadas, sin seleccionar configuración sobre los
dos gates nuevos:

| piloto | representación entrenable | originales FP/FF | decisión temprana |
| --- | --- | ---: | --- |
| cabeza anclada | sólo 513 parámetros | 2/1 | `NO-GO` |
| parcial conservador | últimas 3 capas, peso 0,625 | 3/1 | `NO-GO` |
| parcial balanceado | últimas 3 capas, peso 1,0 | 2/1 | `NO-GO` |
| encoder completo conservador | 10 capas, LR ×0,25 | 2/1 | `NO-GO` |

La cabeza anclada también fue puntuada para confirmar el mecanismo: mejoró el
holdout de 2/8 a 4/6 y dejó los 162 casos en 53/40. Es decir, recuperó inocentes
intercambiándolos por permisos peligrosos. Los otros tres candidatos se
detuvieron al fallar originales y no se puntuaron sobre los gates retenidos.

Todos los checkpoints y el código específico de los ensayos fallidos fueron
eliminados; se conservaron informes pequeños y el constructor reproducible del
corpus. R3.1 permanece oficial y no cambian Android, ONNX, umbral, política ni
runtime. `frozen_test` y `final_sealed` siguen cerrados.

Conclusión: ya no falta un ajuste local más grande del mismo TinyCLIP. El paso
fundado siguiente es destilación con un profesor visual más capaz y una GPU
externa, usando estas 508 imágenes sólo como train y los tres gates congelados.
Debe conservar un único TinyCLIP estudiante y el mismo costo Android. Requiere
un ticket separado que apruebe proveedor, presupuesto, credenciales efímeras,
licencias del profesor y apagado automático; no se autoriza gasto por este
resultado.

### Viabilidad de profesores congelados

Antes de alquilar GPU se midieron localmente dos profesores visuales con pesos
Apache-2.0 y una única cabeza logística de configuración congelada. Ninguno se
entrenó ni se destinó a Android. DINOv2-Small usa 21 M de parámetros y 88,2 MB
de pesos; SigLIP Base se mantuvo como profesor semántico de laboratorio. Ambos
usaron letterbox DAG y peso filter `1,5` sobre las 1.150 imágenes de train.

| profesor | originales FP/FF | 112 vistas FP/FF | holdout independiente FP/FF | dirigido FP/FF | decisión |
| --- | ---: | ---: | ---: | ---: | --- |
| DINOv2-Small | 3/3 | 9/7 | 4/7 | 28/22 | `NO-GO` |
| SigLIP Base | 3/2 | 13/6 | 3/7 | 38/9 | `NO-GO` |

Los dos recuperan negativos que R3.1 sobrebloquea, pero permiten demasiados
positivos. No deben destilarse tal como están. Los scripts temporales fueron
retirados y sólo se conservaron sus reportes privados. Esto cambia el siguiente
paso: primero hay que ajustar un profesor a la política humana, validarlo con
los tres gates intactos y únicamente después destilarlo al TinyCLIP estudiante.

La opción operativa recomendada es una GPU RTX 4090 de 24 GB en RunPod Secure
Cloud, facturada por segundo, con presupuesto máximo de USD 3, plazo máximo de
cuatro horas, checkpoint reanudable y terminación automática aun ante error.
La tarifa pública observada el 2026-08-09 fue USD 0,69/h. No crear cuenta, subir
imágenes, almacenar credenciales ni iniciar gasto sin autorización explícita
del propietario. El corpus sigue autorizado sólo para experimento privado y no
declara derechos comerciales claros.

### Ajuste local de profesores en Apple M2

El 2026-08-10 el propietario autorizó primero el experimento externo, pero
eligió continuar sin alquilar GPU antes de crear una cuenta, subir imágenes o
iniciar gasto. Se comprobó que el MacBook Air M2 de 8 GB expone PyTorch MPS fuera
del aislamiento. El entrenamiento quedó totalmente local y privado.

Se usaron únicamente las 1.033 imágenes de train y 25 de validación interna del
corpus adjudicado. El checkpoint se eligió antes de inferir los gates. Después
se puntuaron las 112 vistas fijas, los 162 casos dirigidos y el holdout
independiente de 97. `frozen_test` y `final_sealed` no se abrieron.

| profesor ajustado | originales FP/FF | 112 vistas FP/FF | holdout FP/FF | dirigido FP/FF | decisión |
| --- | ---: | ---: | ---: | ---: | --- |
| SigLIP Base, peso balanceado | 1/6 | 8/22 | 1/19 | 4/52 | `NO-GO` |
| SigLIP Base, sin peso de clase | 1/7 | 6/39 | 2/22 | 0/73 | `NO-GO` |
| DINOv2-Small, peso filter 1,5 | 0/17 | 7/58 | 0/51 | 8/62 | `NO-GO` |

El smoke real de SigLIP con lote 4 y acumulación 4 usó 622.184.192 bytes MPS
y tardó 3,0 segundos en cuatro lotes. DINOv2-Small usó 206.682.112 bytes y
tardó 1,11 segundos. Por lo tanto, el bloqueo no fue capacidad técnica del M2:
los profesores aprendieron una frontera demasiado conservadora y no
generalizaron los negativos cercanos.

La corrección analítica completa del sesgo de clase de SigLIP también se
descartó antes de otro gate: en validación interna cambió de 1/3 a 3/0
FP/FF. No se modificó el umbral `0,40` para compensar el modelo.

Resultado: `NO-GO` local definitivo para estos dos profesores y este corpus.
No destilar, exportar ONNX ni repetir balances, épocas o capas usando los gates
como afinación. R3.1 sigue oficial e intacto. Los informes privados quedan en:

- `.codex-tmp/gloshia-r4-local-mps-train-20260810/report.json`;
- `.codex-tmp/gloshia-r4-local-mps-unweighted-20260810/report.json`;
- `.codex-tmp/gloshia-r4-local-dinov2-safe-20260810/report.json`.

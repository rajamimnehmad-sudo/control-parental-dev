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

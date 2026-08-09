# GloshIA R4 thumbnail repair — piloto local

Fecha: 2026-08-09

Estado: `NO-GO` para integración; R3.1 permanece oficial

Final sealed: cerrado

## Objetivo

Medir y reparar la pérdida de señal de GloshIA Visual cuando una imagen se
presenta como miniatura pequeña, comprimida o circular. No se cambió el modelo
integrado, el umbral `0,40`, la política regional, Android ni el APK.

## Datos reproducibles

Se partió del split privado agrupado R3.2 y se generaron transformaciones de la
imagen completa: `thumb160_q45`, `thumb96_q35` y `circle128_q45`. No se crearon
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
| circular | 5/7 | 0/21 |

Hubo 13 cambios de decisión en 84 pares; 8 fueron degradaciones peligrosas de
`filter` a `allow`. La caída circular de recall filter, de 6/7 originales a
2/7 circulares, confirma la causa que motivó el ticket.

## Pilotos

Ambos partieron del checkpoint que produjo exactamente el R3.1 oficial. Se
entrenó en CPU local y se seleccionó sólo con validation.

| candidato | cambio controlado | falsos permisos | falsos filtros | decisión |
| --- | --- | ---: | ---: | --- |
| R3.1 baseline | ninguno | 12/28 | 5/84 | referencia |
| piloto 01 | 2 épocas, LR 0,25, peso 0,8 | 11/28 | 7/84 | NO-GO |
| piloto 02 | 1 época, LR 0,25, peso 1,0 | 11/28 | 10/84 | NO-GO |

El piloto 01 mejoró una miniatura peligrosa de 160 px y conservó originales,
pero no corrigió ninguna circular peligrosa. El piloto 02 tampoco corrigió las
circulares y agregó tres falsos filtros circulares. Seguir variando pesos no
está justificado.

## Decisión y siguiente paso

La ampliación de datos con clasificación independiente queda rechazada. No se
exporta ONNX, no se abre `frozen_test`, no se instala APK y R3.1 continúa como
único modelo oficial.

El siguiente experimento razonado debe imponer consistencia explícita entre el
logit del original y los logits de sus variantes, además de la pérdida binaria.
Antes de entrenar se deben congelar coeficiente, gates por variante y límites
de regresión en originales e inocentes. Si ese enfoque falla, se prepara una
cola humana pequeña de recortes ambiguos; no se crean excepciones por sitio ni
se modifica la política para rescatar al candidato.

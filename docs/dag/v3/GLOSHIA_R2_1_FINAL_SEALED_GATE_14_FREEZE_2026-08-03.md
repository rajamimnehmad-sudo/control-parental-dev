# GloshIA Visual R2.1 — contrato congelado del examen final

Ticket: `GLOSHIA-R2.1-FINAL-SEALED-GATE-14`

Fecha: 2026-08-03

Estado al crear este documento: **congelado antes de abrir `final_sealed`**

Autorización: el propietario respondió `dale` inmediatamente después de que se
le explicó que las 108 muestras se abrirían una única vez y dejarían de ser un
examen desconocido.

## Artefactos inmutables

- Candidato: `r2.1-candidate-02-int8.onnx`.
- SHA-256:
  `c212d005db271bebfb3fb80aade4c056334e0f4f07f2f1543976050f8c8afa3c`.
- Tamaño: 8.756.367 bytes.
- Runtime de referencia: ONNX Runtime Android CPU `1.27.0`.
- Preprocesamiento: RGB 224×224, letterbox gris y normalización CLIP vigente.
- Política: `dag-36`.
- Umbral global: `0,40`.
- No se permite reentrenar, recalibrar, cambiar el umbral ni seleccionar otra
  exportación después de abrir el examen.

## Examen inmutable

- Corpus: `.codex-tmp/gloshia-pilot-20260802/manifest.jsonl`.
- SHA-256 del manifiesto completo:
  `619317cd7337dc0c41b2464b046d154dd3d4ea2e49964c40ffc07e1fae3f5f7c`.
- Muestras `final_sealed`: 108, todas pendientes y selladas al congelar.
- SHA-256 de la membresía ordenada `sample_id|sha256|split|category`:
  `61d9548b9e9bb92b693fa9243473b5bc990537eca9d28b6654f6ef053572222b`.
- Ninguna muestra está autorizada para entrenamiento. Este examen sólo evalúa.
- Runner congelado SHA-256:
  `f74a2a09067c0f5d9a240316edb3689f8f9679f98086c2eb899e922dfb7ac58c`.

## Protocolo

1. Ejecutar R2.1 INT8 y R1 sobre las mismas 108 muestras sin modificar archivos
   de imagen ni decisiones.
2. La interfaz de revisión no muestra predicción, score, categoría ni split
   antes de guardar cada decisión humana.
3. `allow`, `filter` y `doubt` conservan su significado actual. `doubt` no se
   convierte silenciosamente en `allow` ni entra en la matriz binaria.
4. Calcular la matriz de R2.1 y R1 únicamente después de completar la revisión.
5. Conservar IDs de errores, métricas generales y por categoría. No entrenar ni
   ajustar con este conjunto después de conocer el resultado.

## Gate fijado antes de abrir

- Cualquier salida no finita, error de modelo o muestra evaluable sin decisión:
  `NO-GO`.
- Cualquier falso permiso humano de R2.1 (`filter→allow`): `NO-GO` para sustituir
  R1.
- R2.1 no puede aumentar falsos permisos frente a R1.
- R2.1 debe reducir falsos filtros frente a R1 y conservar una ventaja general
  de balanced accuracy.
- El desacuerdo INT8/FP32 ya conocido sobre una muestra humana `allow` no se usa
  para cambiar el umbral y no invalida por sí solo este examen orientado a
  seguridad humana.
- Un `GO` de este examen autoriza únicamente proponer un canary Android
  reversible; no reemplaza automáticamente R1 ni autoriza publicación.

El resultado se agregará en un documento separado. Este contrato no se edita
retroactivamente para cambiar artefactos, muestras o criterios.

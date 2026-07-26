# Contrato de datos de Calibración DAG v2

## Identificadores

- `policy_version`: `DAG_STRICT_MODESTY_V1`
- `collector_version`: `dag-v2-calibration-collector-1`
- No existe `model_version` de DAG v2.

## Candidato local

`DagV2CalibrationCandidate` conserva en memoria:

- `candidateId`
- `sessionId`
- `navigationToken`
- `resourceUrl`
- `documentOrigin`
- `resourceOrigin`
- `resourceKind`
- dimensiones observadas opcionales
- `observedAt`
- atribución
- estado revisable

La URL se usa sólo para recuperar el recurso durante la sesión; no es identidad, no entra al outbox ni se envía.

## Muestra normalizada

Identidad exacta: SHA-256 de los bytes JPEG normalizados.

Identidad visual aproximada: dHash de 64 bits en hexadecimal. Dos muestras se consideran candidatas a casi duplicado con distancia Hamming menor o igual a 5. La asociación de una etiqueta a un duplicado no cambia automáticamente una decisión global.

La muestra remota registra:

- `sample_id`
- `content_sha256`
- `perceptual_hash`
- `storage_path`
- ancho y alto
- `mime_type=image/jpeg`
- `size_bytes`
- `source_kind`
- hosts de recurso y documento sanitizados
- SHA-256 de la URL fuente, sin URL
- versión de política y colector
- fecha y estado

Estados: `pending`, `ready`, `rejected`.

## Etiqueta

Valores permitidos:

- `show`: evidencia concluyente
- `hide`: evidencia concluyente
- `unsure`: evidencia incierta, excluida de positivos y negativos

La clave del revisor es un SHA-256 seudónimo derivado en la función. La clave sólo permite auditoría y relabel; no personaliza el filtro.

La clave lógica de etiqueta es `(sample_id, reviewer_key)`. Un nuevo valor actualiza la etiqueta vigente y conserva el cambio anterior en `dag_v2_calibration_audit`.

## API de escritura

Endpoint: `POST /functions/v1/dag-v2-calibration`, multipart.

Campos aceptados:

- identificador de dispositivo activado;
- hashes exacto, perceptual y de URL;
- dimensiones, MIME y tamaño;
- clase de fuente;
- hosts sanitizados;
- decisión;
- versiones de política y colector;
- JPEG normalizado o referencia exacta ya existente, nunca ambos.

Campos prohibidos incluyen URL completa, query, texto, cookies, Referer, headers, formulario, `model_version` y thresholds.

La respuesta aceptada contiene sólo `sample_id`, estado de deduplicación y confirmación de auditoría.

## Persistencia DEV

- Bucket privado: `dag-v2-calibration`
- Tablas: `dag_v2_calibration_samples`, `dag_v2_calibration_labels`, `dag_v2_calibration_audit`
- RLS activa y privilegios revocados para `anon` y `authenticated`
- Escritura exclusivamente con Service Role dentro de la Edge Function
- Sin endpoint público de lectura

La ruta de Storage se deriva del SHA-256: `samples/<prefijo>/<sha>.jpg`.

## Invariantes

- Ninguna etiqueta escribe modelo, threshold, versión candidata, split o excepción.
- Ninguna etiqueta cambia `DagV2FailClosedImageDecisionProvider`.
- DAG v1 y sus tablas de calibración no reciben escrituras.
- `unsure` se conserva, pero nunca se convierte en ejemplo binario.
- El estado global requiere revisión futura; no existe activación automática.

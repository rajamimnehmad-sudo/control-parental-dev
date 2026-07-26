# Privacidad de Calibración DAG v2

## Finalidad

Las muestras se conservan exclusivamente como evidencia para el futuro entrenamiento y evaluación de DAG v2. Este ticket no construye dataset, no selecciona ejemplos, no entrena y no activa ningún modelo.

## Minimización

Se almacena el JPEG normalizado, no el archivo original completo:

- orientación aplicada;
- primer resultado estático para contenido animado;
- EXIF y metadatos eliminados;
- sRGB;
- lado mayor de hasta 768 px;
- hasta 512 KiB.

Se permiten únicamente hosts sanitizados y hashes. No se persisten URL, query, consulta de búsqueda, texto visible, cookies, Referer, headers, formularios ni identidad personal.

Cookies y Referer pueden existir sólo en memoria durante una recuperación explícita y nunca entran al payload.

## Protección local

- AES-256-GCM con una clave no exportable de Android Keystore.
- Archivos dentro de `noBackupFilesDir`.
- Un archivo cifrado por pendiente.
- Bytes originales y previews liberados o sobrescritos cuando termina su uso.
- Límites de cantidad, bytes y antigüedad evitan acumulación ilimitada.

## Protección remota

- Proyecto exclusivamente DEV.
- Bucket privado.
- RLS activa y sin permisos directos de Android sobre tablas o Storage.
- Service Role sólo en Edge Function.
- Autorización por dispositivo y rate limit.
- Rutas derivadas del hash.
- Auditoría de creación, deduplicación, relabel y rechazo.
- Sin endpoint público de lectura o borrado.

## Retención y eliminación

La retención remota es configurable administrativamente. Este ticket no ejecuta eliminación, no agrega borrado masivo Android y no borra evidencia al hacer rollback.

Cualquier eliminación futura requiere un ticket administrativo destructivo separado que defina alcance, autorización, auditoría y recuperación.

## Riesgos residuales

- Un revisor ve contenido real al abrir explícitamente el visor; la confirmación previa explica ese alcance.
- Un host sanitizado conserva contexto de procedencia a nivel de dominio.
- La deduplicación perceptual puede agrupar imágenes visualmente cercanas; no cambia por sí sola ninguna decisión global.
- El outbox reintenta al abrir el Lab, no en segundo plano, para conservar el aislamiento del proceso `:dag2`.

No existe uso de las etiquetas como preferencia personal ni transferencia a Production.

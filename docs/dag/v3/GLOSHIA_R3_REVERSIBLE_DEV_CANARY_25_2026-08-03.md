# GloshIA R3 - canary DEV reversible en DAG 96

Fecha: 2026-08-03  
Ticket: `GLOSHIA-R3-REVERSIBLE-DEV-CANARY-25`  
Estado: publicado y verificado en DEV; Production intacta.

## Decisión

R3 híbrida supera a R1 en el examen Android ejecutado sobre el Samsung S22:

- falsos permisos: `0` en ambos modelos;
- falsos filtros: `10` en R3 frente a `42` en R1, reducción de `76,2 %`;
- p50: `186,251 ms` en R3 frente a `188,184 ms` en R1;
- p95: `230,749 ms` en R3 frente a `231,738 ms` en R1;
- PSS: `106.031 KB` en R3 frente a `105.615 KB` en R1;
- una diferencia frente al FP32, siempre en dirección conservadora: R3 filtra
  una imagen que FP32 permite; no se agregó un permiso incorrecto.

La repetición en A23 fue omitida por decisión explícita del propietario. La
evidencia física del S22 se considera suficiente para un canary DEV reversible,
no para Production.

## Integración acotada

- DAG 96 usa `tinyclip-r3-head-hybrid-int8.onnx` como único analizador activo.
- Umbral y política regional permanecen en `dag-36`; no se ejecutan R1 y R3 en
  paralelo y no se agrega una segunda inferencia por fotografía.
- R1 continúa dentro del APK y se usa solamente si ORT no puede abrir R3 al
  iniciar. Un error de inferencia sigue fallando cerrado.
- `Acerca de DAG` muestra `GloshIA Visual - R3 Canary` y el hash R3.

Artefacto R3:

- tamaño: `10.469.698` bytes;
- SHA-256:
  `0aaa1700182623173c41d233bd0e072cce2b2880aca14430d9f9af43fa2c44a8`.

Fallback R1:

- tamaño: `8.735.186` bytes;
- SHA-256:
  `2d52bd9e5eb4cd448cb0d64a784b2ee6f761ad20e890c57b898fd7991d29a9ee`.

## Actualización propia de DAG

DAG incorpora una acción `Actualizaciones` que consulta el mismo manifiesto
DEV usado por App Usuario. No existe un backend paralelo. Antes de abrir el
instalador exige:

1. manifiesto y APK por HTTPS;
2. versión superior a la instalada;
3. SHA-256 exacto del manifiesto;
4. mismo package name de DAG;
5. mismos certificados de firma que la aplicación instalada.

La descarga queda en caché privada y se comparte temporalmente mediante un
`FileProvider` no exportado. Production no se modifica.

## Validación local

- `156` pruebas unitarias: aprobadas;
- `14` pruebas WebExtension: aprobadas;
- Ktlint: aprobado;
- Lint: aprobado;
- APK DEV: compilada;
- firma v2: válida y coincide con DAG 95;
- modelo R3 y fallback R1 dentro del APK: hashes exactos.

APK local DAG 96:

- versión: `0.69.0-dev` (`versionCode 96`);
- tamaño: `129.970.709` bytes;
- SHA-256:
  `ffa03d731ba57e94dd2e2ff169b51504f9a337df3d41ca92bc5439fff1c477e7`.

La descarga pública repitió exactamente `129.970.709` bytes y el mismo
SHA-256. El manifiesto remoto anuncia `versionCode 96`, `0.69.0-dev` y la URL
inmutable `app-dag-browser-dev-96-debug.apk`. El manifiesto DAG 95 fue archivado
antes del reemplazo.

## Rollback

R1 no fue borrado ni modificado. Si el canary muestra una regresión visual, se
publica un `versionCode` superior que vuelve a seleccionar R1; nunca se intenta
instalar un APK con versión inferior. Este lote no autoriza Production.

# DAG Browser 107 - Navegacion protegida estable

Fecha: 2026-08-04

## Alcance

- Base: DAG 97, navegador de DAG 95 con GloshIA R3 y fallback R1.
- Dispositivo: Samsung SM-S908E.
- Android: validacion fisica mediante APK DEV instalada en el dispositivo.
- Sin cambios en extension `1.50.0`, modelo, pesos, umbral ni politica visual.

## Causa y correccion

El fondo intermedio aparecia porque la pagina nueva reemplazaba visualmente al
documento anterior antes de completar su presentacion protegida. Ademas, Mimo
confirmaba la barrera y terminaba su cola visual, pero Gecko no siempre emitia
`onFirstContentfulPaint`; la espera exclusiva terminaba cerrando una pagina
segura por timeout.

DAG 107 conserva en memoria una captura de la pestaña activa ya protegida
durante la navegacion. La captura no se persiste y se invalida al cambiar de
pestana, navegar, liberar memoria o cerrar. La pagina nueva requiere barrera
confirmada y primer dibujo de Gecko o cola de imagenes protegidas quieta. Los
rasters pendientes continúan cerrados individualmente por la extension.

No se incorporaron excepciones por Mimo, Google, dominio, URL o telefono. Los
intentos previos que no resolvieron el problema no permanecen en el diff.

## Validacion

- `ktlintCheck`: aprobado.
- `testDevDebugUnitTest`: aprobado.
- `lintDevDebug`: aprobado.
- `assembleDevDebug`: aprobado.
- Version instalada: `107` / `0.69.11-dev`.
- Confirmacion del propietario: transiciones claramente mejores, busqueda
  desde DAG sin el destello previo y Mimo funcional.
- Sin cambios en decisiones de GloshIA.

APK local al cierre:

- tamaño: `129945445` bytes;
- SHA-256: `0bdfb98cee3b3d7693f8a6d110321f75578209427ce46d835ece2cee6a0b2c9e`.

## Pendiente separado

R3 produjo tres falsos filtros en banners comerciales seguros: bebe vestido,
niño vestido y Mercado/Pagos. Deben alimentar un lote general de hard negatives
sin excepciones por sitio y sin modificar directamente el umbral global.

## Publicacion

No hubo push, publicacion DEV, Supabase ni Production en este cierre.

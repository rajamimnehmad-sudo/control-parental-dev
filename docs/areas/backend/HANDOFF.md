# BACKEND Y LICENCIAS — HANDOFF

## Mision

Supabase DEV, base de datos, sync, auth, licencias, reenlace y Edge Functions.

## Estado

- Proyecto DEV activo: `syeycayasyufedwoprea`, Postgres 17.6.1.
- Migraciones locales/remotas alineadas al cierre de la auditoria.
- Todas las tablas publicas tienen RLS, pero los asesores muestran deuda grande
  de permisos, politicas redundantes e indices.
- Varios RPC anonimos usan tokens propios de dispositivo; no revocar en bloque.
- Flujos fisicos de licencia y reenlace siguen pendientes.
- `dag-diagnostic-report` DEV version 5 acepta el esquema 3 vigente de la caja
  negra y eventos `video_lab`. La recepcion fisica quedo verificada desde
  Diagnostic 77 en A23 con el codigo `DAG-2CQ4GM58`.

## Siguiente ticket

`BACKEND-SECURITY-01`: clasificar funciones por actor, retirar grants innecesarios
solo con pruebas de contrato y documentar excepciones de token de dispositivo.

## Limites

Solo DEV. Sin borrado, Production ni Service Role Key en Android.

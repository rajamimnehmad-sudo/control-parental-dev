# Retiro seguro de DAG 1 y DAG 2

## Nombres y límites del producto

- **Glosh** es el sistema completo de protección y filtros.
- **DAG** es el buscador/navegador protegido de Glosh.
- **DAG 1 y DAG 2** son implementaciones retiradas, no nombres que deban eliminarse del producto.

## Resultado en el código actual

- Eliminados el navegador WebView anterior, su launcher, modelos locales, calibración y tests.
- Eliminado por completo el módulo `feature-dag2`, sus herramientas y benchmark.
- Eliminados los controles de DAG de App Admin y Super Web.
- Eliminadas las rutas, componentes, acciones y fuentes de Edge Functions antiguas.
- App Usuario sólo puede abrir el navegador protegido nuevo mediante un destino explícito.
- No existe fallback hacia DAG 1 o DAG 2.

## Resultado en Supabase DEV

- App Usuario 281 y DAG Browser 2 fueron instaladas y se comprobó la apertura y el regreso a Glosh.
- Se respaldaron las nueve tablas heredadas y los 85 archivos de calibración antes de borrarlos.
- Se retiraron las Edge Functions `dag-search`, `dag-calibration` y `dag-v2-calibration`.
- Se retiraron las tablas, RPC, cuota mensual, buckets y política de lectura de calibración de DAG 1/2.
- Production no fue modificada.

Se conservan intencionalmente `dag_entitled`, la regla `__dag_enabled__` y sus RPC de administración.
No pertenecen al buscador anterior: son los controles de acceso por comunidad y dispositivo que
puede usar el DAG nuevo.

La migración `20260727160000_remove_legacy_dag_search_and_calibration.sql` documenta la limpieza
de la base y, por diseño, no toca esos controles vigentes.

Las migraciones históricas permanecen en Git porque son el registro reproducible de cómo se creó
la base. Quitarlas no borra la nube y sí puede romper instalaciones o entornos nuevos.

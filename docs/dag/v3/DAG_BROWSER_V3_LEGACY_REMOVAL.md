# Retiro seguro de DAG 1 y DAG 2

## Resultado en el código actual

- Eliminados el navegador WebView anterior, su launcher, modelos locales, calibración y tests.
- Eliminado por completo el módulo `feature-dag2`, sus herramientas y benchmark.
- Eliminados los controles de DAG de App Admin y Super Web.
- Eliminadas las rutas, componentes, acciones y fuentes de Edge Functions antiguas.
- App Usuario sólo puede abrir el navegador protegido nuevo mediante un destino explícito.
- No existe fallback hacia DAG 1 o DAG 2.

## Compatibilidad transitoria

La base local conserva temporalmente la columna histórica `dagEntitled` y el backend DEV conserva
las estructuras remotas antiguas hasta que App Usuario 281 esté instalada y comprobada. Ninguna de
ellas activa código ni aparece en la interfaz.

Este orden evita dejar inutilizable un teléfono que todavía tenga App Usuario 280:

1. fusionar y compilar los candidatos DEV;
2. instalar primero el navegador protegido firmado;
3. actualizar Glosh a 281 y comprobar el puente;
4. respaldar los datos antiguos;
5. retirar Edge Functions, RPC, tablas, buckets y campos antiguos sólo en Supabase DEV;
6. comprobar sincronización, licencias, Admin y Super Web;
7. no tocar Production.

Las migraciones históricas permanecen en Git porque son el registro reproducible de cómo se creó
la base. Quitarlas no borra la nube y sí puede romper instalaciones o entornos nuevos.

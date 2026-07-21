# Firebase Test Lab virtual - 2026-07-21

## Resultado

Estado: **no ejecutado por configuración externa faltante**.

La infraestructura de compatibilidad está presente en `main` desde PR #11 y el workflow manual `.github/workflows/android-device-lab.yml` está disponible. La ejecución no se disparó porque su validación previa exige un proyecto Firebase/GCP exclusivo de pruebas y las cuatro configuraciones requeridas todavía no existen en GitHub.

## Configuración faltante

- Variable `FIREBASE_TEST_PROJECT_ID`.
- Variable `FIREBASE_TEST_RESULTS_BUCKET`.
- Secreto `FIREBASE_TEST_WIF_PROVIDER`.
- Secreto `FIREBASE_TEST_SERVICE_ACCOUNT`.

La Mac tampoco tiene `gcloud` configurado ni una sesión Google Cloud autorizada para crear de forma segura el proyecto dedicado, bucket, cuenta de servicio y Workload Identity Federation. El proyecto Firebase de la aplicación no se reutilizó.

## Evidencia y conteos

- Workflow de Test Lab ejecutado: no.
- Enlace de GitHub Actions: no existe.
- Matrices creadas: 0.
- Identificadores de matriz: ninguno.
- Dispositivos/API virtuales ejecutados: 0.
- App Usuario: no ejecutada en Test Lab.
- App Admin: no ejecutada en Test Lab.
- Pruebas aprobadas: 0.
- Pruebas fallidas: 0.
- Pruebas previstas pero no ejecutadas: todas las de la matriz virtual.
- Costos o cuota consumida por esta tarea: ninguno observado.

Este resultado es un bloqueo de acceso/configuración, no un fallo de las aplicaciones. Las validaciones locales y de Android CI de DEV 269 permanecen correctas, pero no sustituyen la evidencia de laboratorio.

## Para desbloquear

Una persona con acceso a Google Cloud debe crear o seleccionar un proyecto dedicado de pruebas con facturación/Test Lab, un bucket de resultados y una identidad federada limitada a este repositorio. Luego debe cargar las dos variables y los dos secretos anteriores en GitHub. Recién entonces corresponde ejecutar manualmente `Android Device Lab` con confirmación `EJECUTAR_TEST_LAB`, matriz `virtual` y `allow_physical=false`.

# Firebase Test Lab — preparación operativa

No se creó proyecto, no se habilitó facturación, no se configuraron credenciales y no se ejecutó ningún dispositivo. El workflow es exclusivamente manual.

## Recursos dedicados requeridos

- proyecto Firebase/GCP exclusivo de pruebas con Test Lab habilitado;
- bucket temporal exclusivo para resultados;
- identidad Workload Identity Federation para GitHub Actions;
- secreto `FIREBASE_TEST_WIF_PROVIDER` y secreto `FIREBASE_TEST_SERVICE_ACCOUNT`;
- variable `FIREBASE_TEST_PROJECT_ID` y variable `FIREBASE_TEST_RESULTS_BUCKET`.

No se reutilizan secretos de Supabase. Para ejecutar desde `gcloud` con bucket propio, la guía oficial exige el par `roles/cloudtestservice.testAdmin` (Firebase Test Lab Admin) y `roles/firebase.analyticsViewer`; no se debe conceder Owner/Editor. Estos roles pueden acceder a los buckets Firebase del proyecto, razón adicional para que el proyecto sea exclusivo de pruebas. Workload Identity debe restringirse al repositorio y rama/entorno autorizados. Referencias: [permisos Test Lab](https://firebase.google.com/docs/projects/iam/permissions#test-lab) y [roles Cloud Test Service](https://cloud.google.com/iam/docs/roles-permissions/cloudtestservice).

## Controles de costo

- workflow `workflow_dispatch` únicamente;
- texto exacto `EJECUTAR_TEST_LAB`;
- virtual por defecto; físico exige además `allow_physical=true`;
- tres celdas máximas por app;
- timeout de 12 minutos, cero reintentos automáticos, video y métricas desactivados;
- concurrencia única y artefactos GitHub por 14 días.

Los límites reducen, pero no garantizan, costo cero. El bucket propio requiere un proyecto con facturación habilitada y puede generar cargos de almacenamiento. Antes de autorizar se deben configurar presupuesto/alertas y revisar [cuotas y precios vigentes](https://firebase.google.com/docs/test-lab/usage-quotas-pricing). Un modelo, versión o locale `es_AR` ausente, reducido o deprecado provoca fallo de preflight; no se sustituye silenciosamente por otra familia o configuración regional.

## Uso local autorizado futuro

```bash
scripts/android_compatibility/query_catalog.sh "$FIREBASE_TEST_PROJECT_ID"
FIREBASE_TEST_CONFIRMATION=EJECUTAR_TEST_LAB \
FIREBASE_TEST_RESULTS_BUCKET=nombre-bucket \
scripts/android_compatibility/run_firebase_matrix.sh \
  --kind virtual \
  --project "$FIREBASE_TEST_PROJECT_ID" \
  --app app-user/build/outputs/apk/dev/compatibility/app-user-dev-compatibility.apk \
  --test app-user/build/outputs/apk/androidTest/dev/compatibility/app-user-dev-compatibility-androidTest.apk
```

Para físico también se exige `ALLOW_PHYSICAL_TESTS=true`. La descarga posterior usa `download_results.sh gs://bucket/directorio`. Logs, XML, capturas y JSON quedan en `build/compatibility/`, fuera del código fuente.

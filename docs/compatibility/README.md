# Compatibilidad Android

Esta carpeta define cómo reunir evidencia reproducible para App Usuario y App Admin sin adaptar todavía el comportamiento por fabricante. La compatibilidad se evalúa por capacidades reales, versión Android, formato de pantalla y familia OEM; el nombre comercial nunca alcanza por sí solo.

## Estado de soporte

| Estado | Significado |
| --- | --- |
| Diseñado | El caso está contemplado por arquitectura y documentación. |
| Automatizado | Existe una prueba repetible y aislada de backend real. |
| Verificado en laboratorio | La prueba corrió en emulador o laboratorio y conserva artefactos. |
| Validado físicamente | Una persona o automatización controlada ejecutó el caso en hardware identificado. |
| Certificado | Se completó el nivel exigido, se revisó la evidencia y no quedan bloqueantes. |

Un estado no implica el siguiente. Nunca se registra una validación física sin fecha, build, dispositivo, versión Android y evidencia.

## Niveles

- Nivel 1, cada PR: smoke virtual en Android mínimo, intermedio y último soportado; teléfono pequeño, teléfono normal y tablet; `es-AR`; fuente normal/grande; Usuario y Admin; sin backend real.
- Nivel 2, antes de una publicación importante: Firebase Test Lab virtual y hardware disponible prioritario; instalación limpia, actualización in-place, navegación, orientación, offline, crashes/ANR y conservación de estado cuando sea observable.
- Nivel 3, certificación: hardware físico o laboratorio OEM; VPN, Accessibility, Device Admin, batería, reinicio, actualización, Ajustes restringidos, desinstalación/recuperación, notificaciones y uso prolongado.

| Nivel | Tiempo/costo aproximado | Riesgo operativo |
| --- | --- | --- |
| 1 | Bajo: minutos de CI y emuladores locales/administrados; sin backend | bajo, salvo tiempo de runner e imágenes SDK |
| 2 | Medio: 15–45 minutos por matriz; Test Lab puede consumir cuota o facturación | medio por indisponibilidad, costo y flakiness de laboratorio |
| 3 | Alto: horas/días de operador y hardware, posible laboratorio pago | alto por acciones de sistema, actualización y recuperación |

Los importes reales dependen de las cuotas/precios vigentes y siempre requieren aprobación antes de ejecutar infraestructura paga.

## Uso

1. Elegir celdas de [DEVICE_MATRIX.md](DEVICE_MATRIX.md) según el riesgo del cambio.
2. Ejecutar los escenarios de [TEST_SCENARIOS.md](TEST_SCENARIOS.md) correspondientes al nivel.
3. Crear un resultado desde [templates/device-result-template.md](templates/device-result-template.md), adjuntando logs/XML/capturas y distinguiendo hecho, observación y limitación.
4. Aplicar los criterios de [RELEASE_CERTIFICATION.md](RELEASE_CERTIFICATION.md).
5. Consultar [OEM_PROFILES.md](OEM_PROFILES.md) solo para planificar evidencia; todavía no hay excepciones OEM implementadas.

La automatización de laboratorio está en `scripts/android_compatibility/` y `.github/workflows/android-device-lab.yml`. [FIREBASE_TEST_LAB.md](FIREBASE_TEST_LAB.md) documenta permisos, secretos, costos y operación. Firebase Test Lab queda preparado pero no se ejecuta automáticamente.

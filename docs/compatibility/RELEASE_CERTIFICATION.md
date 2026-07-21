# Certificación de publicación Android

## Entrada

- commit y APK identificados por SHA-256;
- `versionCode`/`versionName`, paquetes y certificado comprobados;
- matriz elegida según riesgo;
- resultados previos abiertos y regresiones conocidas revisadas.

## Gates

| Gate | Requisito | Bloquea |
| --- | --- | --- |
| Código | unitarios, smoke, build, ktlint, lint, detekt | sí |
| Nivel 1 | APIs/form factors/fuente previstos, sin crash | sí, salvo limitación justificada y aceptada |
| Nivel 2 | virtual + hardware prioritario, instalación/upgrade/offline | publicación importante |
| Nivel 3 | servicios y protecciones reales | declaración de certificación OEM |
| Evidencia | plantilla completa y artefactos trazables | sí |

## Decisión

- `Aprobado`: todos los gates requeridos pasan.
- `Aprobado con limitación`: no hay riesgo de seguridad oculto, la limitación está documentada y fue aceptada explícitamente.
- `Bloqueado`: falla, evidencia ausente, dispositivo no equivalente o protección no verificable.

No se promueve automáticamente un resultado de laboratorio a validación física ni se certifica una familia completa por un único modelo. Un fallo de VPN, Accessibility, Device Admin o antidesinstalación es bloqueante hasta diagnóstico explícito.

## Retención

El PR conserva resumen Markdown. Logs, XML, capturas y videos se guardan como artefactos temporales de CI sin secretos ni datos reales. La duración inicial recomendada es 14 días; extenderla requiere justificar costo y privacidad.

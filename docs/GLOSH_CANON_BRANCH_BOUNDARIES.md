# Anclas canónicas y límites de ramas de Glosh

Fecha de reconciliación: 2026-08-28.

Este documento separa identidad inmutable, puntero de navegación y evidencia de ejecución. Una rama puede moverse; un SHA completo no.

## Anclas verificadas

| Superficie | Puntero | SHA inmutable u observado | Significado |
|---|---|---|---|
| Baseline convergente canónica | `review/glosh-convergence-baseline-01` | `c2900bc5a2f28fa9d291862197f70a2e2c8e2f6c` | Identidad canónica congelada. El nombre de rama indica su origen, no su estado actual. |
| Head mutable observado de la baseline | `review/glosh-convergence-baseline-01` | `9c3fffa36cefc3a0bab01c549d4a7c53b05f80d1` | Observación del 2026-08-28. No reemplaza ni redefine el SHA canónico congelado. |
| Glosh Remote 19J / DEV29 | `review/remote-pin-only-19j-codex-final` | `7d7d837ddd243001f173110e219a37e4479fe12c` | Candidata estática con `versionCode 29` y `versionName 0.1.0-dev29`. El gate físico continúa pendiente. |
| Fuente de Glosh Central reconciliada | `build/glosh-control-center-v2` | `e99df0aefb9f66be386449ed169a2fb5b183a17b` | Snapshot utilizado como base de esta reconciliación documental. |

## Reglas de interpretación

1. Toda referencia marcada como canónica, congelada o exacta debe conservar el SHA completo de 40 caracteres.
2. El nombre de una rama sirve para navegar y colaborar, pero nunca sustituye una identidad inmutable.
3. Verificar código, historial o configuración de Gradle no equivale a ejecutar build, tests ni validación física.
4. Un PASS físico exige evidencia preservada del mismo SHA y del flujo declarado. La reconciliación de 19J no aporta esa evidencia.
5. Los cambios de `docs/AI_TASK_TRACKER.json` en una rama `review/*` no alteran el Glosh Central en uso hasta una promoción separada y autorizada hacia su rama fuente.
6. Esta rama de revisión no autoriza PR, merge, integración, deploy, release, Production, mutaciones de Supabase, borrados ni gastos.

## Estado remoto reconciliado

`REMOTE-PIN-ONLY-19J` sustituye la referencia operativa obsoleta a 19I/v25 como candidata estática. DEV29 incorpora el flujo de recepción del descriptor, apertura guiada de Ajustes, conexión Wi-Fi, `adb pair/connect` e instalación de APK, y conserva el callback del broker mientras Ajustes está al frente.

El estado correcto sigue siendo `BLOCKED PHYSICAL`: falta construir e instalar exactamente `7d7d837ddd243001f173110e219a37e4479fe12c`, introducir únicamente el PIN de seis dígitos y preservar evidencia de pairing, ADB local, handoff del broker e instalación one-shot sin campos técnicos.

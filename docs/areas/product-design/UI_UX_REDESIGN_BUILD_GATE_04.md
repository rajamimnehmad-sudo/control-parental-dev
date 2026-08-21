# UI-UX-REDESIGN-BUILD-GATE-04

## Estado

READY FOR LOCAL EXECUTION

## Task ID

`apps-user-admin-ui-build-gate-04`

## Objetivo

Cerrar técnicamente el rediseño reversible de App Usuario y App Administrador: compilar, ejecutar unitarios, ktlint y lint, corregir únicamente errores introducidos por `UI-UX-REDESIGN-01` y dejar evidencia reproducible. Después generar APKs **solo locales** para revisión visual si todos los gates automáticos pasan.

## Owner / frente

- Owner de escritura: Codex únicamente durante este gate local.
- Revisión/cierre final: ChatGPT Central.
- Frente: Producto y Diseño / Apps Usuario + Admin.

## Rama / base

- Rama exacta: `work/ui-ux-redesign-01`
- Base histórica del lote: `preserve/uncommitted-2026-08-20` @ `214e7c848c7c1770a11abb8a0af3b8b71698999e`
- Antes de escribir, confirmar el HEAD remoto actual de `work/ui-ux-redesign-01`; no asumir el SHA de este documento.
- Usar worktree propio si el checkout canónico está ocupado por otro frente.

## Coordinación obligatoria antes de escribir

1. Leer `START_HERE.md`, `docs/CODEX_RULES.md`, `docs/AREAS.md`, `docs/DEV_FLOW.md` y `docs/areas/product-design/UI_UX_REDESIGN_01.md` desde esta rama.
2. Revisar Glosh Central vigente en `build/glosh-control-center-v2/docs/AI_TASK_TRACKER.json`.
3. Confirmar que Protección Android / Chrome Visual sigue siendo otro owner y no tocar sus rutas.
4. Confirmar que no apareció trabajo paralelo nuevo en `core-ui`, `app-user` UI, `app-admin` UI, `feature-activation` UI o `feature-requests` UI. Ante colisión real, detenerse antes de pisar cambios.

## Alcance permitido

Solo para corregir errores demostrados por build/test/lint del rediseño:

- `core-ui/src/main/java/com/contentfilter/core/ui/**`
- `app-admin/src/main/java/com/contentfilter/admin/**` únicamente archivos UI tocados por `UI-UX-REDESIGN-01`
- `app-admin/src/main/java/com/contentfilter/admin/rules/**` únicamente archivos UI del rediseño
- `app-admin/src/main/java/com/contentfilter/admin/requests/**` solo errores de compilación/regresión del flujo global-first introducido por este lote
- `app-admin/src/main/AndroidManifest.xml`
- `app-user/src/main/java/com/contentfilter/user/**` únicamente archivos UI tocados por el rediseño
- `app-user/src/main/java/com/contentfilter/user/apps/**` únicamente UI/lista nativa tocada por el rediseño
- `app-user/src/main/AndroidManifest.xml`
- `app-user/src/main/res/values/strings.xml`
- `feature-activation/src/main/java/com/contentfilter/feature/activation/ActivationScreen.kt`
- `feature-requests/src/main/java/com/contentfilter/feature/requests/RequestsScreen.kt`
- documentación de este frente para evidencia.

## Prohibido

- No tocar `main`.
- No merge, PR, push a `main`, publicación DEV, Supabase, Production ni versionCode.
- No tocar Chrome Visual, `feature-accessibility` runtime, VPN runtime, DAG runtime, GloshIA, Sync, Room, Policy o backend salvo que el build demuestre de forma inequívoca que una firma existente fue mal usada; incluso entonces reportar antes de ampliar alcance.
- No traer ramas experimentales ni el frente `CHROME-PHOTOS-PROTECTED-SURFACE-00` mientras su gate siga FAILED/BLOCKED.
- No reformateo global.
- No reset/stash/rebase/force-push/clean ni revertir cambios ajenos.
- No rediseñar ni cambiar la dirección visual aprobada.
- No cambiar launcher icons históricos: isotipo sigue pausado y son placeholder deliberado.
- No modificar Glosh Central desde Codex; ChatGPT Central sincroniza el cierre.

## Gates automáticos

Ejecutar desde la raíz canónica del proyecto, con upload/publicación excluidos:

```bash
./gradlew --no-daemon \
  :app-user:assembleDevDebug \
  :app-admin:assembleDevDebug \
  -x uploadDevUpdatesToStorage \
  -x prepareDevUpdatesForStorage
```

```bash
./gradlew --no-daemon \
  :app-user:testDevDebugUnitTest \
  :app-admin:testDevDebugUnitTest \
  -x uploadDevUpdatesToStorage
```

```bash
./gradlew --no-daemon \
  :core-ui:ktlintCheck \
  :app-user:ktlintCheck \
  :app-admin:ktlintCheck \
  -x uploadDevUpdatesToStorage
```

```bash
./gradlew --no-daemon \
  :app-user:lintDevDebug \
  :app-admin:lintDevDebug \
  -x uploadDevUpdatesToStorage
```

Si existe Detekt configurado y el CI normal lo exige para estos módulos, ejecutar también el alcance mínimo equivalente.

## Regla de corrección

- Si falla por código introducido por el rediseño: corregir el mínimo archivo necesario y repetir el gate afectado.
- Si falla por deuda preexistente fuera del diff: no arreglarla de costado. Demostrar que es preexistente, aislarla y reportarla como bloqueo/deuda externa.
- Si una corrección cambia comportamiento funcional y no solo compilación/UI: detenerse y reportar antes de aplicarla.

## Revisión visual local después de PASS automático

Sin publicar APK, instalar/abrir en emulador o dispositivo disponible y recorrer como mínimo:

### Admin

1. Activación Admin.
2. Inicio.
3. Usuarios: búsqueda, alta/token, archivados.
4. Usuario -> Apps: filtros, límite, horario, grupos.
5. Usuario -> Internet: Abierto/Bloqueado, nivel, sitio, horario.
6. Usuario -> Seguridad: normal + Más opciones.
7. Solicitudes: global, filtro usuario, pendientes/historial.
8. Ajustes, actualizaciones, ayuda.

### Usuario

1. Activación.
2. Onboarding de permisos hasta Home.
3. Inicio sano y estado con reparación pendiente.
4. Mis apps: búsqueda/filtros/grupos/scroll nativo.
5. Internet.
6. Solicitudes incluyendo `DOMAIN_ACCESS` mostrando dominio real.
7. Ajustes, protección, actualizaciones, ayuda.

Revisar especialmente overflow, teclado, scroll, targets táctiles, contraste, bottom nav y pantallas pequeñas.

## Criterio PASS técnico

PASS únicamente si:

- ambas APK DEV locales compilan;
- unitarios dirigidos pasan;
- ktlint pasa en `core-ui`, Usuario y Admin, o queda un bloqueo preexistente demostrado fuera del diff;
- lint de ambas apps pasa, o queda un bloqueo preexistente demostrado fuera del diff;
- no se tocó lógica de protección fuera de alcance;
- recorrido visual mínimo no muestra crash, pantalla rota, overflow grave o navegación incorrecta;
- el checkout/worktree queda limpio salvo commits deliberados de este gate;
- se entrega evidencia breve con comandos, resultados, archivos corregidos y cualquier pendiente.

## Handoff a ChatGPT Central

Terminar como `PASS`, `BLOCKED` o `FAILED`.

En PASS informar:

- HEAD final exacto de `work/ui-ux-redesign-01`;
- archivos adicionales corregidos;
- resultado de cada gate;
- dispositivo/emulador usado para smoke visual;
- observaciones visuales restantes, si las hay;
- confirmar explícitamente: **sin PR, sin merge, sin publicación, sin Production, sin versionCode**.

PASS técnico no autoriza integración ni publicación. ChatGPT Central revisa diff/evidencia y recién después pide el OK del usuario.

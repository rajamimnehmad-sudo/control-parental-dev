# GLOSH CONVERGENCE AUDIT 01

Fecha del corte: 2026-08-26, America/Argentina/Buenos_Aires.

Estado: **TECHNICAL AUDIT COMPLETE — PENDING CHATGPT REVIEW**.

Esta es una auditoria read-only de producto, GitHub, Glosh Central y estado
local. No compila ni valida producto, no cambia versiones, no ejecuta gates
fisicos y no declara cerrada `GLOSH-CONVERGENCE-BASELINE-01`.

## 1. Executive snapshot

### Fuentes verificadas

- Repositorio: `rajamimnehmad-sudo/control-parental-dev`.
- `origin/main`: `7269636f3c916bf92cd93947bf2595db330836dd`.
- Glosh Central: `build/glosh-control-center-v2` en
  `44590055ca1f9749b23fd992eabaff7e26306ce1`.
- Central contiene 119 tareas: 68 `done`, 44 `pending`, 6 `blocked` y una
  `in_progress`.
- GitHub contiene 98 refs remotos, 83 tips distintos: 27 `review/*`, 26
  `work/*`, 13 `gate/*` y 32 refs de otras familias.
- Pull requests abiertos: #95, #97, #98 y #99, todos draft. #100 y #101 son
  los merges de Super Admin presentes en `main`.
- Unico release GitHub: `stable/dev-191-web-protection`, prerelease historico.

### Resultado ejecutivo

1. **No existe un SHA integrado unico que represente todo Glosh vigente.**
   `origin/main` es la verdad de integracion, pero Chrome 10A–13B-R, el modulo
   compartido GloshIA R3.1, hardening P0 y candidatos UX viven en ramas no
   integradas. Varias ramas incluyen ademas historia DAG/Remote no revisada por
   el ticket que les da nombre.
2. **Chrome R1 sigue BLOCKED.** La ultima base remota es
   `review/chrome-visual-shield-13b-r1-viewport-automated` @ `001be18d...`.
   El diagnostico regional que confirma el blocker existe solo localmente en
   `88804188...`. No hay fundamento para continuar R1, R2A o video.
3. **El ultimo Chrome visual fisicamente aprobado es 13B-R DEV358**, rama
   `review/chrome-visual-shield-13b-r-dev358-final` @ `5c31b948...`. R1 es una
   candidata fail-closed posterior, no un cierre de producto.
4. **GloshIA Images tiene una autoridad tecnica clara:** modelo Visual R3.1
   SHA-256 `c8b64af8092d3718c58736a511c996d0d443dacf3eaa74620b1e5af439a3cd48`,
   `gloshia-visual-core`, `GloshiaPreparedRasterPolicy` y los preprocessors
   compartidos. Los experimentos R4 no reemplazan esta baseline.
5. **DAG y Remote Installer estan PAUSED/RESERVE.** Que haya codigo y muchas
   ramas no los convierte en frentes activos. El DAG integrado en `main` es
   DEV211; el trabajo posterior de video esta compartido indirectamente en
   varias ramas, pero no tiene promocion canonica.
6. **La Mac contiene trabajo no respaldado.** Hay 58 worktrees registrados (33
   directorios presentes, incluido este audit, y 25 registros sin directorio),
   65 ramas locales, 11 tips locales no contenidos en refs remotos, 31 commits
   alcanzables con patch unico frente a todos los refs remotos y 30 commits
   unreachable con patch unico frente a todo lo alcanzable. El checkout
   principal tiene 14 entradas dirty/untracked.
7. **`MAC-LOCAL-PRESERVATION-03` es un blocker real previo a cualquier cleanup.**
   El inventario vigente supera al resumen anterior de Central; no debe
   ejecutarse prune, gc, clean, reset, borrado de ramas/worktrees ni borrado en
   bloque de `.codex-tmp`.
8. **Central sirve como registro persistente, pero no todavia como cola limpia.**
   Mezcla politicas operativas, hitos historicos, backlog, rutas superseded y
   una tarea Chrome `in_progress` sin writer aunque el subfrente vigente esta
   bloqueado.

### Regla de precedencia usada

1. GitHub actual decide codigo, commits, ramas y evidencia compartida.
2. Central actual decide prioridad, pausa, owner, blocker y estado persistente.
3. Evidencia fisica decide lo que realmente paso, dentro de su scope.
4. Handoffs y documentos historicos solo explican; no sobreescriben 1–3.
5. Estado local puede contener trabajo real, pero no es canonico compartido
   hasta preservacion y review explicitos.

`docs/PROJECT_CONTROL.md` no existe en `origin/main`. La copia dirty local se
clasifica `SUPERSEDED/HISTORICAL`, nunca autoridad. `docs/HANDOFF_ACTUAL.md` y
`docs/BACKLOG_PRODUCTO.md` son stubs/legado de orientacion, no estado vigente.

## 2. Area matrix

### 2.1 App Usuario Android

- **AREA:** App Usuario Android.
- **CURRENT STATUS:** PAUSED para UX V4; la app integrada existe, pero no hay
  gate de producto activo.
- **CANONICAL IMPLEMENTATION:** base integrada de `app-user`, Room como fuente
  inmediata, Policy/Sync/Protection por modulos. Los cambios Chrome DEV viven
  en su propia linea de review y no convierten DEV359 en release de la app.
- **CANONICAL BRANCH / SHA:** `origin/main` @ `7269636f...` para producto
  integrado. Candidata UX, no canonica: `work/user-ux-v4-01` @ `60c39cda...`.
- **LATEST REVIEWED EVIDENCE:** Central `apps-account-product-batch-07`; main
  conserva App Usuario DEV311. No existe evidencia final de V4.
- **ACTIVE OWNER:** ninguno.
- **ACTIVE TASK:** ninguna; `user-ux-v4-05` esta `pending/PAUSED`.
- **KNOWN BLOCKER:** candidata V4 requiere gate tecnico, version/firma real y
  update in-place cuando se retome.
- **SUPERSEDED ROUTES:** UI previa usada como direccion de producto; ramas de
  build temporales no son releases.
- **HISTORICAL ONLY:** DEV191 es baseline de recuperacion Web, no version actual
  de desarrollo.
- **TECH DEBT:** no hay baseline integrada que incorpore selectivamente UX V4 y
  la proteccion revisada; deuda de archivos UI grandes.
- **CONTRADICTIONS:** la rama V4 arrastra historia Chrome/DAG ajena y por eso su
  tip completo no es una base limpia.
- **NEXT REAL STEP:** despues de preservacion y baseline, decidir si V4 se
  integra por commits de UI y ejecutar un unico gate proporcional.

### 2.2 App Administrador Android

- **AREA:** App Administrador Android.
- **CURRENT STATUS:** PAUSED; V4 visualmente implementada, gate tecnico pendiente.
- **CANONICAL IMPLEMENTATION:** `app-admin` integrada en main; Room es la fuente
  inmediata y Sync transporta cambios.
- **CANONICAL BRANCH / SHA:** `origin/main` @ `7269636f...`. Candidata V4:
  `work/admin-ux-v4-01` @ `dca8cb0b...`.
- **LATEST REVIEWED EVIDENCE:** Central `admin-ux-v4-06`, `CODE COMPLETE VISUAL /
  PENDING TECH GATE`; main conserva Admin DEV293.
- **ACTIVE OWNER:** ninguno.
- **ACTIVE TASK:** ninguna; lote Apps pendiente.
- **KNOWN BLOCKER:** falta review/build/tests/smoke del delta V4.
- **SUPERSEDED ROUTES:** pantallas anteriores como direccion visual; no su
  comportamiento funcional integrado.
- **HISTORICAL ONLY:** commits de rediseño y APKs temporales sin cierre.
- **TECH DEBT:** `RulesViewModel.kt` supera 2.000 lineas; el branch V4 tambien
  hereda historia ajena.
- **CONTRADICTIONS:** “code complete” no equivale a canonico ni publicado.
- **NEXT REAL STEP:** seleccionar commits V4 limpios sobre la baseline propuesta
  y gatearlos; no promover la rama completa.

### 2.3 Super Admin Web

- **AREA:** Super Admin Web.
- **CURRENT STATUS:** CANONICAL/OPERATIVE; sin desarrollo activo.
- **CANONICAL IMPLEMENTATION:** Next.js en `web-super-admin`, build Vercel nativo;
  `build:sites` permanece separado.
- **CANONICAL BRANCH / SHA:** `origin/main` @ `7269636f...`; ultimo commit web
  `579c213a2cd7ad869b9db603c7329b634ff25013` integrado por PR #101.
- **LATEST REVIEWED EVIDENCE:** `docs/areas/super-admin/HANDOFF.md`; PR #100 y
  #101 merged; URL oficial registrada `web-super-admin-nine.vercel.app`.
- **ACTIVE OWNER:** ninguno.
- **ACTIVE TASK:** ninguna.
- **KNOWN BLOCKER:** ninguno de operacion actual; falta smoke autenticado.
- **SUPERSEDED ROUTES:** tratar PR #96 cerrado como merge; mezclar OpenNext/Sites
  con el build Vercel.
- **HISTORICAL ONLY:** `review/superadmin-product-batch-01` y PR #96.
- **TECH DEBT:** falta CI web/cobertura suficiente, warning `<img>` y decision
  formal sobre un unico destino de hosting.
- **CONTRADICTIONS:** Central dice “PASS en PR #96”, pero #96 fue cerrado sin
  merge; la autoridad real es main mediante #100/#101 y el handoff actual.
- **NEXT REAL STEP:** mantener main; cuando se priorice, smoke autenticado y
  decision de hosting, sin reabrir UX aprobada.

### 2.4 Protection Core

- **AREA:** Protection Core: Device Owner, apps, bypass, Accessibility, VPN/DNS
  y lifecycle.
- **CURRENT STATUS:** mecanismos base CANONICAL; hardening Chrome DEV revisado;
  producto completo no consolidado en main.
- **CANONICAL IMPLEMENTATION:** PolicyEngine modular; Accessibility como
  observador/complemento; DevicePolicyManager como autoridad fuerte donde
  aplica; VPN/DNS de producto; para Chrome DEV, HEV full-tunnel y guard
  `:chrome_guard` con lease fail-closed y suspension DPM.
- **CANONICAL BRANCH / SHA:** base integrada `origin/main` @ `7269636f...`;
  ultimo cierre de guard revisado
  `review/chrome-vpn-process-death-guard-10b-dev344-final` @ `4ccf15b2...`;
  cadena passing acumulada hasta `5c31b948...`.
- **LATEST REVIEWED EVIDENCE:**
  `EVIDENCE_2026-08-24_CHROME-VPN-PROCESS-DEATH-GUARD-10B.md` y evidencia
  10A/12A/13A/R.
- **ACTIVE OWNER:** ninguno.
- **ACTIVE TASK:** ninguno; Device Owner protection queda pendiente posterior.
- **KNOWN BLOCKER:** la integracion de estos mecanismos revisados a una baseline
  comun aun no existe.
- **SUPERSEDED ROUTES:** “Accessibility hace todo”, DNS-only para web general y
  release de Chrome controlado solo por el proceso principal.
- **HISTORICAL ONLY:** instalador Device Owner DEV319 y su evidencia existen
  unicamente en commits locales; sirven para preservacion, no para direccion.
- **TECH DEBT:** PSS alto del guard por inicializacion Hilt; garantia force-stop
  dependiente de plataforma; politica de app blocking general no cerrada.
- **CONTRADICTIONS:** Central marca el instalador de laboratorio DONE, pero su
  script/evidencia no esta en ningun ref remoto.
- **NEXT REAL STEP:** preservar el instalador local y definir en baseline que
  commits de proteccion se integran, sin ampliar comportamiento.

### 2.5 Chrome Protection

- **AREA:** Chrome Protection.
- **CURRENT STATUS:** 10A/10B/11A/11B/12A/12B/13A y 13B-R PASS DEV revisados;
  **13B-R1 BLOCKED**.
- **CANONICAL IMPLEMENTATION:** ultimo foundation passing: VPN/HEV + proxy +
  11B content authority + Accessibility/provenance + Visual Shield 13B-R
  fail-closed.
- **CANONICAL BRANCH / SHA:** passing
  `review/chrome-visual-shield-13b-r-dev358-final` @ `5c31b948...`; candidata
  R1 bloqueada `review/chrome-visual-shield-13b-r1-viewport-automated` @
  `001be18d...`.
- **LATEST REVIEWED EVIDENCE:** evidencia 13B-R DEV358 y Central @ `44590055...`;
  diagnostico exacto R1 solo local `88804188...`.
- **ACTIVE OWNER:** ninguno mientras el blocker espera decision ChatGPT.
- **ACTIVE TASK:** `chrome-visual-shield-13b-r1`, bloqueada.
- **KNOWN BLOCKER:** crop landscape renderer-local 1639x324 queda
  `Safe/model_allow` incluso con full + tres regiones canonicas; no se permite
  nuevo tiler, foto, threshold, modelo ni relabel sin decision arquitectonica.
- **SUPERSEDED ROUTES:** cache-only, proxy 08A, heuristica regional 13B original
  y extension stock Chrome 13B-P como ruta activa.
- **HISTORICAL ONLY:** ramas R1 de fixture/probe/correcciones previas y gates
  Chrome anteriores ya incorporados conceptualmente.
- **TECH DEBT:** linea no integrada a main; futuro 14A/perf/hardening/update queda
  congelado detras de R1.
- **CONTRADICTIONS:** `chrome-visual-closure-batch-04` figura `in_progress` sin
  writer aunque su unico subfrente actual esta `blocked`.
- **NEXT REAL STEP:** decision de convergence/coverage por ChatGPT; no ejecutar
  gates ni continuar Chrome desde esta auditoria.

### 2.6 GloshIA Images

- **AREA:** GloshIA Images.
- **CURRENT STATUS:** CANONICAL R3.1; calibracion e investigaciones posteriores
  PAUSED.
- **CANONICAL IMPLEMENTATION:** `gloshia-visual-core`, modelo R3.1 exacto,
  `GloshiaVisualAnalyzer`, `GloshiaPreparedRasterPolicy`, preprocessor Android y
  `GloshiaRegionalCropPlanner` existentes.
- **CANONICAL BRANCH / SHA:** contenido identico en `5c31b948...` y
  `001be18d...`; origen del modulo compartido `02aa2d02...`. Modelo SHA-256
  `c8b64af8...a3cd48` verificado desde el blob remoto.
- **LATEST REVIEWED EVIDENCE:** R3.1 runtime docs en main, evidencia Chrome
  GloshIA/13B-R y diagnostico R1 registrado en Central.
- **ACTIVE OWNER:** ninguno.
- **ACTIVE TASK:** recuperacion de evidencia y cierre no destructivo, ambos
  pendientes/pausados.
- **KNOWN BLOCKER:** no de R3.1 general; R1 demuestra una limitacion de cobertura
  renderer-local para un crop extremo.
- **SUPERSEDED ROUTES:** runtimes duplicados y policies DAG privadas previas al
  core compartido.
- **HISTORICAL ONLY:** candidatos R1/R2/R2.x/R3.2/R4 y teacher probes que no
  promovieron modelo.
- **TECH DEBT:** evidencia de entrenamiento/calibracion fragmentada entre Git y
  `.codex-tmp`; cierre de review destructivo debe corregirse antes de calibrar.
- **CONTRADICTIONS:** miles de modelos/candidatos locales pueden parecer
  vigentes, pero ninguno desplaza el asset R3.1 por mera existencia.
- **NEXT REAL STEP:** preservar y registrar corpus/calibraciones; no entrenar ni
  cambiar modelo durante convergence.

### 2.7 GloshIA Video/GIF

- **AREA:** GloshIA Video/GIF.
- **CURRENT STATUS:** PAUSED/RESERVE; no producto general cerrado.
- **CANONICAL IMPLEMENTATION:** ninguna de producto para video general. GIF y
  video DAG posteriores a DEV211 son candidatos de laboratorio, no baseline.
- **CANONICAL BRANCH / SHA:** para codigo integrado, `origin/main` @ `7269636f...`
  con DAG DEV211. La linea posterior termina localmente en `main` @ `6ae216fd...`
  pero esta diverged y no es GitHub main.
- **LATEST REVIEWED EVIDENCE:** evidencia DAG A23/S22 preservada en historia de
  ramas; registra YouTube parcial y categorias NO-GO.
- **ACTIVE OWNER:** ninguno.
- **ACTIVE TASK:** `chrome-video-a23` pendiente, no activo.
- **KNOWN BLOCKER:** video general, URLs directas, iframes/Shorts/anuncios/redes
  y cobertura temporal no estan cerrados.
- **SUPERSEDED ROUTES:** asumir que el progreso porcentual o un handoff local
  equivale a promocion de producto.
- **HISTORICAL ONLY:** DAG video DEV212–229, Diagnostic y propuestas V1.
- **TECH DEBT:** coordinadores DAG grandes y riesgo inherente del muestreo.
- **CONTRADICTIONS:** el handoff heredado dice “main adelantada, sin push”; la
  historia fue empujada indirectamente como ancestro de ramas ajenas, pero no
  fue revisada/promovida como DAG.
- **NEXT REAL STEP:** RESERVE. No retomar hasta una decision posterior a la
  baseline y sin mezclarlo con Chrome R1.

### 2.8 DAG Browser

- **AREA:** DAG Browser.
- **CURRENT STATUS:** PAUSED/RESERVE.
- **CANONICAL IMPLEMENTATION:** proyecto Gradle aislado `app-dag-browser`; usar
  `scripts/dag_gradle.sh` cuando exista un ticket futuro.
- **CANONICAL BRANCH / SHA:** `origin/main` @ `7269636f...`, DAG versionCode 211.
- **LATEST REVIEWED EVIDENCE:** resultados de compatibilidad hasta DEV211 en
  main; evidencia posterior se clasifica candidata/historica.
- **ACTIVE OWNER:** ninguno.
- **ACTIVE TASK:** ninguna; Central contiene backlog pendiente sin owner.
- **KNOWN BLOCKER:** no hay blocker para mantenerlo en reserva; si se reabre,
  primero debe decidirse que linea posterior se preserva/promueve.
- **SUPERSEDED ROUTES:** ramas locales `dag-browser-unfiltered-dev-baseline` y
  experimentos v1/v2 como direccion actual.
- **HISTORICAL ONLY:** 50+ resultados fisicos y ramas de estabilidad/modelado.
- **TECH DEBT:** `DagBrowserActivity` y varios scripts/coordinadores superan los
  limites de cohesion; existen commits locales/unreachable DAG unicos.
- **CONTRADICTIONS:** Central muestra muchos `pending`, pero su contexto dice sin
  owner; no constituyen cola activa.
- **NEXT REAL STEP:** preservar commits/artefactos primero; luego ChatGPT decide
  si DAG queda reserve o recibe un ticket de convergencia separado.

### 2.9 Sync & Realtime

- **AREA:** Sync & Realtime.
- **CURRENT STATUS:** CANONICAL base integrada; patch de atomicidad aprobado pero
  no integrado a main.
- **CANONICAL IMPLEMENTATION:** Room/outbox offline-first, `DefaultSyncEngine`,
  `DefaultRealtimeSyncCoordinator`, Supabase Realtime/Postgres changes y
  broadcast dirigido de revision/licencia.
- **CANONICAL BRANCH / SHA:** base `origin/main` @ `7269636f...`; patch objetivo
  de sync atomico `5aa1f8cf...` dentro de `review/p0-hardening-batch-01` @
  `76bbf975...`.
- **LATEST REVIEWED EVIDENCE:** Central `atomic-sync: done` y PR draft #95.
- **ACTIVE OWNER:** ninguno.
- **ACTIVE TASK:** parte residual de `p0-backend-closeout-02`, pendiente.
- **KNOWN BLOCKER:** la rama #95 completa incluye historia DAG no relacionada y
  no puede promoverse como unidad.
- **SUPERSEDED ROUTES:** sincronizacion no atomica y full sync para toda revision
  cuando existe targeted refresh.
- **HISTORICAL ONLY:** scripts y pruebas de hardening previos.
- **TECH DEBT:** integrar/revalidar los commits P0 exactos sobre baseline limpia;
  el checkout principal tiene cambios dirty precisamente en Policy/Sync.
- **CONTRADICTIONS:** Central dice aprobado, pero PR #95 sigue abierto con checks
  fallidos y no esta en main.
- **NEXT REAL STEP:** preservar dirty/local, despues extraer solo commits P0
  verificados a la baseline propuesta.

### 2.10 Backend/Auth/Licenses/Supabase

- **AREA:** Backend/Auth/Licenses/Supabase.
- **CURRENT STATUS:** hardening base aplicado segun Central; cierre P0 BLOCKED.
- **CANONICAL IMPLEMENTATION:** proyecto DEV `syeycayasyufedwoprea`, RLS, RPCs
  acotados, device-token, entitlement autoritativo, migraciones Supabase y Edge
  Functions versionadas.
- **CANONICAL BRANCH / SHA:** codigo integrado `origin/main` @ `7269636f...`;
  commits P0 fuente `0100cf9e`, `bb148ea2`, `bf37b77e`, `76bbf975` en PR #95.
- **LATEST REVIEWED EVIDENCE:** Central: hosts exactos, grants/RPC, license gates
  y scope A/B PASS; no se hizo consulta live nueva en este audit.
- **ACTIVE OWNER:** ninguno.
- **ACTIVE TASK:** `p0-backend-closeout-02` pendiente; `pairing-hardening` y
  `direct-auth-users-write` bloqueados.
- **KNOWN BLOCKER:** gate pairing transaccional E2E y migracion del flujo Admin
  que escribe directamente `auth.users/auth.identities`.
- **SUPERSEDED ROUTES:** `activate_device` anon y grants EXECUTE amplios.
- **HISTORICAL ONLY:** scripts de setup total/manual frente a migraciones
  aplicadas; no son reemplazo de schema live.
- **TECH DEBT:** rate limit de pairing, leaked-password protection y codigo P0 no
  integrado. Changelog Supabase vigente agrega restricciones de schema Realtime
  y cambios futuros de Data API que deben considerarse solo al modificar.
- **CONTRADICTIONS:** Production reportada por Central no coincide con la
  historia de `origin/main`; GitHub no demuestra por si solo el schema live.
- **NEXT REAL STEP:** ticket de cierre P0 separado, con introspeccion read-only
  live y reconciliacion de migraciones, despues de la baseline.

### 2.11 Installer & Enrollment / Remote Installer

- **AREA:** Installer & Enrollment / Remote Installer.
- **CURRENT STATUS:** Device Owner installer de laboratorio DONE local; Glosh
  Remote PAUSED.
- **CANONICAL IMPLEMENTATION:** no existe instalador remoto productivo. El
  prototipo aislado vive en `tools/glosh-remote-spike` con ADB local temporal,
  relay cifrado y allowlist fija.
- **CANONICAL BRANCH / SHA:** ultima candidata remota compartida
  `gate/remote-pin-only-18-e969006` @ `e9690069...`; coordinacion
  `coordination/remote-install-live-guide-v2` @ `8eb1267f...`. Ninguna es
  baseline de producto.
- **LATEST REVIEWED EVIDENCE:** `tools/glosh-remote-spike/EVIDENCE_GATE0.md` y
  Central; Gate0 es historico y no prueba instalacion remota real.
- **ACTIVE OWNER:** ninguno.
- **ACTIVE TASK:** `remote-install-connection-00` pending/PAUSED.
- **KNOWN BLOCKER:** gate de redes distintas y cierre de experiencia; no hay
  instalacion/Device Owner remotos de producto.
- **SUPERSEDED ROUTES:** guided Accessibility, PiP, overlay y variantes Bubble
  previas al PIN-only actual.
- **HISTORICAL ONLY:** gates 0–17 y sus APKs.
- **TECH DEBT:** README del tip PIN-only aun describe el guided assistant con
  Accessibility; ramas copy/check/gate abundantes.
- **CONTRADICTIONS:** ramas y README dicen “current work”, pero Central manda
  PAUSED. La Mac contiene commits posteriores unicos en `main` local y ramas
  notification/simple que no estan remotos.
- **NEXT REAL STEP:** preservar commits unicos; mantener PAUSED hasta decision
  explicita, luego fijar un unico SHA y actualizar docs antes de cualquier gate.

### 2.12 Updates / Release / Compatibility

- **AREA:** Updates / Release / Compatibility.
- **CURRENT STATUS:** mecanismo base existe; no hay release Android activo.
- **CANONICAL IMPLEMENTATION:** `core-update`, flujos DEV y matriz de
  compatibilidad; rollback Android siempre con versionCode superior.
- **CANONICAL BRANCH / SHA:** `origin/main` @ `7269636f...`; unico tag/release
  probado `stable/dev-191-web-protection` es recuperacion historica.
- **LATEST REVIEWED EVIDENCE:** `docs/BASELINES.md`, compatibilidad DAG hasta 211
  y evidencias Chrome DEV hasta 359 por ramas.
- **ACTIVE OWNER:** ninguno.
- **ACTIVE TASK:** `apk-release-gates` pendiente cuando Apps se retome.
- **KNOWN BLOCKER:** no hay baseline integrada ni manifiesto unico de versiones
  publicadas actuales.
- **SUPERSEDED ROUTES:** interpretar el mayor DEV de laboratorio como release de
  producto o instalar downgrade.
- **HISTORICAL ONLY:** APKs DEV191/311/293/358/359 y DAG diagnostics segun su gate.
- **TECH DEBT:** Chrome update compatibility 16, multi-OEM, AAB/ABI, SBOM,
  privacidad/logging y long-run.
- **CONTRADICTIONS:** main declara User DEV311/Admin DEV293, ramas Chrome llegan
  a DEV359 y el unico release GitHub es DEV191; son carriles distintos, no una
  secuencia de release consolidada.
- **NEXT REAL STEP:** baseline debe producir un version manifest; no publicar
  APK desde este audit.

### 2.13 Data / Evidence / IA Lab

- **AREA:** Data / Evidence / IA Lab.
- **CURRENT STATUS:** PRESERVE; calibracion PAUSED.
- **CANONICAL IMPLEMENTATION:** evidencia en Git cuando esta sanitizada y
  compartible; corpus/modelos/calibraciones sensibles y temporales se preservan
  localmente hasta inventario explicito.
- **CANONICAL BRANCH / SHA:** modelo de runtime en ramas passing Chrome; docs
  R3.1/DAG en main. No existe un unico ref para todo el corpus local.
- **LATEST REVIEWED EVIDENCE:** regla Central `gloshia-data-preservation-rule` y
  documentos R3.1/Chrome.
- **ACTIVE OWNER:** ninguno.
- **ACTIVE TASK:** `gloshia-r3-evidence-recovery` PAUSED y
  `gloshia-review-close-preservation` pendiente.
- **KNOWN BLOCKER:** inventario/backup no destructivo antes de cleanup.
- **SUPERSEDED ROUTES:** borrar review/corpus como efecto secundario de reset.
- **HISTORICAL ONLY:** candidatos de training que no promovieron runtime.
- **TECH DEBT:** `.codex-tmp` 5.5 GiB mezcla corpus, modelos, venvs y evidencia;
  no tiene manifiesto de retencion completo.
- **CONTRADICTIONS:** el asset runtime esta remoto y verificable, pero material
  de reproduccion/entrenamiento clave puede ser solo local.
- **NEXT REAL STEP:** `MAC-LOCAL-PRESERVATION-03` con hashes, sensibilidad,
  destino y retencion; no subir datasets indiscriminadamente.

### 2.14 Glosh Central / Control Center

- **AREA:** Glosh Central / Control Center.
- **CURRENT STATUS:** CANONICAL como registro persistente; taxonomia requiere
  convergencia.
- **CANONICAL IMPLEMENTATION:** `docs/AI_TASK_TRACKER.json` en su rama dedicada.
- **CANONICAL BRANCH / SHA:** `build/glosh-control-center-v2` @ `44590055...`.
- **LATEST REVIEWED EVIDENCE:** el propio tracker y `control-center-v2: done`.
- **ACTIVE OWNER:** ChatGPT para direccion/review; Codex solo para el audit actual.
- **ACTIVE TASK:** `GLOSH-CONVERGENCE-AUDIT-01` hasta review.
- **KNOWN BLOCKER:** ninguno tecnico del tablero.
- **SUPERSEDED ROUTES:** usar HANDOFF/BACKLOG legacy como tablero paralelo.
- **HISTORICAL ONLY:** gates cerrados que hoy ocupan la misma vista que trabajo
  pendiente.
- **TECH DEBT:** no hay campo owner efectivo; estados dependen de texto libre;
  duplicados y `PREPARED/NEXT` antiguos aparecen como `pending`.
- **CONTRADICTIONS:** unico `in_progress` es Chrome closure, aunque R1 esta
  bloqueado y no hay writer.
- **NEXT REAL STEP:** aplicar la propuesta de cleanup solo despues de review de
  este audit; no borrar historia.

### 2.15 Workflow ChatGPT ↔ Codex ↔ GitHub ↔ Central

- **AREA:** Workflow.
- **CURRENT STATUS:** direccion CANONICAL; ejecucion todavia genera demasiadas
  ramas/handoffs.
- **CANONICAL IMPLEMENTATION:** Central=cola/estado; GitHub=codigo/evidencia;
  ChatGPT=direccion/review; Codex=ejecucion local hasta PASS/BLOCKED/FAILED.
- **CANONICAL BRANCH / SHA:** reglas en `origin/main` @ `7269636f...`.
- **LATEST REVIEWED EVIDENCE:** commits workflow del 2026-08-25 y tareas Central
  `workflow-cost-optimization`/`workflow-hygiene-01`.
- **ACTIVE OWNER:** ChatGPT para direccion, Codex por ticket.
- **ACTIVE TASK:** este audit.
- **KNOWN BLOCKER:** señal de finalizacion/review automatico aun no existe.
- **SUPERSEDED ROUTES:** autorun experimental, handoff global largo, ramas
  copy/check/v2 y microcommits de gobernanza.
- **HISTORICAL ONLY:** PR #98 / `review/ai-auto-handoff-01`.
- **TECH DEBT:** PRs draft y worktrees no se cierran al terminar; Central no
  separa politicas, historia y cola ejecutable.
- **CONTRADICTIONS:** las reglas actuales son compactas, pero el estado fisico
  conserva el ruido del workflow anterior.
- **NEXT REAL STEP:** `GLOSH-ORCHESTRATION-01` minimo, despues de baseline y
  preservacion; no construir un orquestador grande ahora.

## 3. Canonical mechanisms

El conteo de clasificacion se hace sobre las filas de este registro, no sobre
las 119 tareas de Central.

| ID | Mecanismo canonico | Fuente |
|---|---|---|
| C01 | Precedencia Central/GitHub/evidencia | `START_HERE.md` @ main |
| C02 | Integracion oficial y reglas repo | `origin/main` @ `7269636f...` |
| C03 | App Usuario integrada | `app-user` en main |
| C04 | App Admin integrada | `app-admin` en main |
| C05 | Room como estado local inmediato | `core-database` + `core-data` en main |
| C06 | PolicyEngine modular | `core-policy` en main |
| C07 | Outbox/offline-first + Realtime dirigido | `core-sync`/`core-network` en main; patch P0 pendiente de integrar |
| C08 | Backend Supabase DEV y contratos versionados | `supabase/**` + estado Central |
| C09 | Super Admin web integrado | main, ultimo web `579c213a...` |
| C10 | Accessibility como complemento de proteccion | main + cierre 12A revisado |
| C11 | DevicePolicyManager como autoridad fuerte | evidencia 10B/13B-R |
| C12 | VPN/DNS de producto | main |
| C13 | HEV full-tunnel Chrome DEV | review 10A @ `c58d32b1...` |
| C14 | Process Death Guard fail-closed | review 10B @ `4ccf15b2...` |
| C15 | Proxy/web semantics | review 11A @ `a733361a...` |
| C16 | Image content authority 11B | review 11B @ `afcfbe53...` |
| C17 | Backpressure/proxy admission | review 12A/12B @ `ffcf4a91...` / `9b24c95b...` |
| C18 | Pixel provenance 13A | review @ `72a0430a...` |
| C19 | Visual Shield foundation 13B-R | review @ `5c31b948...` |
| C20 | GloshIA Visual R3.1 shared runtime/policy | `gloshia-visual-core`, model `c8b64af8...` |
| C21 | DAG integrado y aislado DEV211 | `app-dag-browser` en main |
| C22 | Update/rollback con versionCode monotonic | `core-update`, `DEV_FLOW`, `BASELINES` |
| C23 | Data/evidence preservation rule | Central @ `44590055...` |
| C24 | Central como registro persistente y workflow de review | Central + main |

**CANONICAL COUNT: 24.** Esto no significa que los 24 mecanismos ya esten
integrados en un solo commit ni product-ready.

## 4. Superseded mechanisms

| ID | Ruta superseded | Disposicion |
|---|---|---|
| S01 | `docs/PROJECT_CONTROL.md` como autoridad | No usar; copia local historica |
| S02 | HANDOFF/BACKLOG legacy como tablero | Archivo/stub solamente |
| S03 | Chrome visual pre-protected-surface | Evidencia historica |
| S04 | Cache authority como seguridad de sesion | Reemplazada por bootstrap full reset |
| S05 | Trusted bootstrap cache-only | Reemplazado por full reset provisioning |
| S06 | Proxy semantics 08A como arquitectura final | Reemplazada por 08B + HEV |
| S07 | 13B regional heuristico sin autoridad | Reemplazado por Visual Shield R |
| S08 | Extension 13B-P en stock Chrome movil como ruta activa | Bloqueada y reemplazada operacionalmente por R |
| S09 | `work/chrome-visual` como direccion Chrome actual | Solo preservacion/historia |
| S10 | AI autorun / PR #98 | Retirado del flujo normal |
| S11 | Remote guided Accessibility/PiP/overlay/Bubble previos | Reemplazados dentro del prototipo por PIN-only; mantener historia |
| S12 | Ramas copy/check/v2/gate como direccion persistente | No usar como cola; conservar hasta cleanup aprobado |

**SUPERSEDED COUNT: 12.** No borrar ninguna ruta durante este ticket.

## 5. Historical-only items

| ID | Item historico | Valor que conserva |
|---|---|---|
| H01 | `stable/dev-191-web-protection` | Recuperacion Web probada |
| H02 | Evidencias Chrome 00–13A | Razonamiento y gates heredables |
| H03 | Iteraciones R1 fixture/probe previas a `001be18d` | Historia de false PASS/correcciones |
| H04 | Resultados DAG v3/DEV20–211 en main | Compatibilidad y regresiones |
| H05 | DAG video DEV212–229/Diagnostic | Evidencia candidata, no promocion |
| H06 | Experimentos GloshIA R1/R2/R3.2/R4 | Resultados negativos y corpus |
| H07 | PR #96 Super Admin | Fuente funcional recuperada, no merge |
| H08 | Remote Gate0 y gates guiados 1–17 | Seguridad/prototipo, no producto |
| H09 | refs `preserve/*` y snapshots legacy | Recuperacion, no direccion |
| H10 | roadmap antiguo que difiere de Central | Contexto historico |

**HISTORICAL COUNT: 10.**

## 6. Blocked items

| ID | Blocker | Condicion concreta |
|---|---|---|
| B01 | Chrome Visual Shield R1 | Renderer-local landscape queda model_allow aun con regiones canonicas |
| B02 | Chrome extension 13B-P | Stock Chrome Android no habilita la extension estable |
| B03 | Pairing hardening | Falta gate transaccional E2E real |
| B04 | Direct Auth writes | Admin aun escribe `auth.users/auth.identities` manualmente |
| B05 | Vercel trigger isolation global | Falta acceso/configuracion global; mitigado por rama |
| B06 | Mac cleanup | Preservar reachable/unreachable/dirty/data antes de limpiar |
| B07 | Baseline integrada | Decidir e integrar por mecanismo; ninguna rama completa es seleccionable sin reconciliacion |

**BLOCKED COUNT: 7.**

## 7. Tech debt

| ID | Deuda | Impacto |
|---|---|---|
| D01 | Main no contiene varios cierres revisados | No hay build integral canonica actual |
| D02 | PRs draft #95/#97/#98/#99 siguen abiertos | Confunden estado y disparan checks/previews |
| D03 | UX V4 Usuario/Admin sin gate | Codigo candidato no promovible |
| D04 | `RulesViewModel` >2.000 lineas | Cohesion/mantenibilidad Admin |
| D05 | Super Admin sin tests suficientes y hosting ambiguo | Riesgo de release web |
| D06 | Guard Chrome con PSS alto por Hilt | Costo de memoria/lifecycle |
| D07 | Pairing rate limit/Auth migration/leaked-password | Riesgo backend pendiente |
| D08 | Update/compatibility/SBOM/multi-OEM no cerrados | Producto no release-ready |
| D09 | DAG coordinadores muy grandes | Riesgo al retomar DAG/video |
| D10 | Central sin owner estructurado y con estados en texto | Cola no mecanizable |
| D11 | 25 metadatos de worktree sin directorio | Ruido; limpiar solo despues de preservar |
| D12 | Android CI falla en PRs incluso cuando el scope web pasa | Señal global poco discriminante |

**DEBT COUNT: 12.**

No quedan mecanismos `UNKNOWN` sin clasificar. Las realidades que no pudieron
demostrarse live (por ejemplo schema Production exacto) se marcan explicitamente
como `AMBIGUOUS/NEEDS DECISION`, no se elevan a canonicas.

## 8. Contradiction matrix

| Source A | Source B | Contradiction | Which one wins | Why | Action needed |
|---|---|---|---|---|---|
| `origin/main` | reviews Chrome/P0/UX | Main no contiene mecanismos posteriores revisados | Por mecanismo: review/evidencia; para integracion: main | GitHub separa codigo revisado de merge | Baseline debe integrar commits seleccionados |
| Central Chrome closure `in_progress` | R1 `blocked` + sin writer | Estado activo fantasma | R1 `blocked` | Blocker fisico es mas especifico y posterior | Tras review, marcar closure blocked/paused |
| Central Mac audit: 21/16 y 25 unreachable | Git actual: 88 exactos, 31 patch-unicos y 56/30 unreachable | Inventario persistente obsoleto | Git actual | Estado local cambio el 26-08 | Actualizar detalle del audit/preservation |
| Central “P0 aprobado/Production” | PR #95 draft abierto, checks fallidos, no merge | Estado operativo no coincide con source main | Central para Production; Git para codigo | Cada fuente tiene autoridad distinta | Reconciliar live schema y commits en ticket P0 |
| Central “PASS PR #96” | GitHub #96 CLOSED, no merge | Referencia de cierre incorrecta | main + PR #100/#101 | Son los merges reales | Corregir texto historico al limpiar Central |
| Central Remote PAUSED | ramas/README dicen current work | Codigo parece activo | Central | Decide prioridad/owner | Mantener paused; no gates |
| PIN-only `e969006` | README del mismo ref describe Accessibility guided assistant | Docs no representan tip | Codigo/coordination mas recientes | Secuencia de commits reemplazo UX | Actualizar docs solo al retomar |
| `docs/ROADMAP.md` difiere Super Admin | main + Central | Roadmap dice deferred; producto web esta integrado | main/Central | Evidencia posterior | Clasificar roadmap historico |
| DAG handoff: “sin push” | DAG video es ancestro de ramas remotas | Codigo si esta compartido indirectamente | GitHub para existencia; Central para promocion | Push indirecto no equivale a review DAG | Preservar, mantener paused y seleccionar despues |
| `docs/PROJECT_CONTROL.md` dirty local | usuario + remote main: no existe | Autoridad local fantasma | Ticket/remote main | Regla explicita y fuente compartida | Preservar como historico; no integrar |
| V4 “code complete” | sin gate/review final | Existencia de codigo parece cierre | Central pending | Criterio de gate | No llamar canonical |
| main User311/Admin293 | Chrome DEV359 + release DEV191 | Numeracion parece una secuencia unica | Cada carril/evidencia | Son artefactos con scopes distintos | Crear version manifest en baseline |
| muchas ramas R1 | Central fija `001be18d` + local `88804188` | Varias ramas reclaman actualidad | Central para base; local diagnosis para blocker | Resultado posterior exacto | Preservar 888; archivar ramas previas luego |
| rama local `main` @ `6ae216fd` | `origin/main` @ `7269636f` | Dos “main” divergentes 96 ahead/26 behind | `origin/main` para GitHub; local para preservacion | Nombre local no cambia autoridad remota | Nunca resetear; preservar/integrar por commits |
| PRs #97/#98/#99 abiertos | Central dice rutas retiradas/paused | Drafts parecen trabajo vigente | Central | Sin owner y bases superseded | Cerrar/archivar despues de preservacion/review |
| modelos R4 locales | runtime R3.1 remoto | Candidatos parecen baseline | R3.1 | Unico modelo promovido/verificado | Preservar experimentos, no relabel |

## 9. Central cleanup proposal

No se aplica limpieza masiva en este audit. Propuesta para un ticket posterior:

### KEEP ACTIVE

- `GLOSH-CONVERGENCE-AUDIT-01` solo hasta review.
- Mantener como reglas, no como trabajo ejecutable: owner unico, sync Central,
  review branch, effort routing y preservacion de datos.

### KEEP PENDING

- `mac-local-preservation-03` con prioridad critica.
- `p0-backend-closeout-02`, Apps V4, release gates, update compatibility y
  hardening futuro, sin asignar owner hasta que se prioricen.
- GloshIA evidence recovery/review-close preservation.

### MARK BLOCKED

- Mantener los seis blockers existentes.
- Cambiar `chrome-visual-closure-batch-04` de `in_progress` a
  `blocked/PAUSED` mientras R1 sea el unico camino y no haya writer.

### MARK DONE/HISTORICAL

- Gates Chrome previos, auditorias, resets y hitos P0 ya demostrados deben
  seguir consultables pero salir de la cola ejecutable.
- PR #96 debe referenciarse como fuente historica recuperada por #100/#101, no
  como merge.

### ARCHIVE/SUPERSEDED

- `ai-auto-handoff-01` y PR #98.
- Etapas Chrome cache-only/08A/13B original y ramas R1 intermedias.
- Ramas Remote copy/check/gates reemplazadas por el tip PIN-only.
- Draft PR #97 y #99 cuando su contenido local quede preservado/decidido.

### NEEDS DECISION

- Integracion P0 exacta vs schema live.
- UX V4 Usuario/Admin.
- Destino hosting unico de Super Admin.
- Futuro de DAG y Glosh Remote como RESERVE.
- Cobertura renderer-local de Chrome R1.

Cambio de modelo recomendado para Central, no implementado: separar `kind`
(`policy`, `milestone`, `task`, `history`), `owner`, `canonicalRef`, `blockedBy`
y `queueState`. `in_progress` debe exigir owner, rutas y SHA/base activos.

## 10. Mac preservation inventory

Inventario tomado sin reset/stash/rebase/clean/prune/gc ni borrados.

### Git y worktrees

| Item | Resultado | Clasificacion |
|---|---:|---|
| Worktrees registrados | 58 | AMBIGUOUS hasta preservation |
| Directorios de worktree presentes | 33, incluido este audit | PRESERVE/REGENERABLE segun fila |
| Registros sin directorio | 25 | REGENERABLE despues de preservation |
| Worktrees dirty | 1 | LOCAL UNIQUE |
| Ramas locales | 65 | 54 tips SAFE REMOTE; 11 LOCAL UNPUBLISHED |
| Ramas sin upstream | 37 | AMBIGUOUS; varias tips estan remotas por otro ref |
| Commits exactos alcanzables no contenidos en origin | 88 | 57 patch-equivalentes, 31 patch-unicos |
| Objetos unreachable | 56 commits, 1.341 trees, 560 blobs | PRESERVE BEFORE CLEANUP |
| Commits unreachable patch-equivalentes | 26 | Probablemente regenerables, conservar hasta manifest |
| Commits unreachable patch-unicos | 30 | LOCAL UNIQUE / PRESERVE |

### Checkout principal

`/Users/yejielnehmad/Developer/content-filter` esta en
`work/chrome-visual` @ `6a045f1300336b1f033cab7bea2ce3ba25dcd119`.
El tip esta respaldado por `origin/preserve/chrome-visual-2026-08-20`; el
working tree no.

Dirty tracked (8):

- `DefaultPolicyEngine.kt`.
- `DefaultSyncEngine.kt`.
- `docs/PROJECT_CONTROL.md`.
- `docs/areas/protection/HANDOFF.md`.
- `ActivationViewModel.kt`.
- `UserPairingCodeTest.kt`.
- `VpnDomainPolicyEvaluator.kt` y su test.

Untracked (6):

- zip Chrome protegido.
- dos tests nuevos de Sync.
- evidencia A23 Chrome visual.
- migracion y checks SQL de pairing.

Todos se clasifican **LOCAL UNIQUE / PRESERVE BEFORE CLEANUP**, aunque algunos
puedan resultar superseded tras comparar contenido.

### Once tips locales no publicados

1. `main` @ `6ae216fd...`: fork 96 ahead/26 behind de origin/main; contiene DAG
   video y Glosh Remote. Es el mayor riesgo de perdida.
2. `work/chrome-visual-shield-13b-r1-regional-diagnostic` @ `88804188...`.
3. `work/glosh-device-owner-installer-00` @ `264f3e89...`.
4. `work/remote-notification-pin-19` @ `aa924419...`.
5. `work/remote-simple-notification-21` @ `7347235f...`.
6. `work/chrome-general-web-functional-12` @ `bf5f6835...`.
7. `review/chrome-visual-closure-batch-04` local @ `2ce17b31...`.
8. `build/ui-ux-apk-test-01` local @ `f650bf3a...`.
9. `codex/dag-stability-01` @ `420f3af7...`.
10. `codex/dag-browser-unfiltered-dev-baseline` @ `3419fbc9...`.
11. `work/superadmin-ux-mobile-first-01` @ `851765fc...`.

El analisis patch-id sobre todos los refs remotos reduce 88 commits exactos a
31 commits con contenido no equivalente. No autoriza borrar los otros 57: el
manifest de preservacion debe guardar el mapeo exacto antes de cleanup.

### Artefactos y datos

| Ruta/categoria | Tamano/numero observado | Clasificacion |
|---|---:|---|
| `.codex-tmp` | 5.5 GiB / 43.866 archivos | Mezcla PRESERVE y REGENERABLE |
| `.codex-tmp` JPEG/PNG/MP4 | 9.424 / 1.616 / 8 | Evidencia/corpus; PRESERVE antes de clasificar |
| `.codex-tmp` ONNX/PT | 1.654 / 30 | Candidatos historicos; PRESERVE con hashes |
| `gloshia-train-venv` | 779 MiB | REGENERABLE |
| `.codex-tmp/archive` | 666 MiB | PRESERVE hasta manifest |
| `app-user/build` | 642 MiB | REGENERABLE; APK DEV local no es evidencia suficiente |
| `app-admin/build` | 179 MiB | REGENERABLE |
| `app-dag-browser/build` | 1.6 GiB | REGENERABLE; contiene dos APK ~122 MiB |
| `web-super-admin` | 864 MiB | Mayormente dependencias/build regenerables |
| `tools/gloshia_lab` | 159 MiB | Codigo tracked + entornos/artefactos a separar |
| Worktrees existentes, total disk | ~28.87 GiB | No borrar hasta preservation |

El asset runtime R3.1 de 9.668.603 bytes esta versionado remotamente y su SHA
fue verificado. Los teachers `.pt`, tarballs, tensores, crops, videos R1 y
corpus de `.codex-tmp` no se consideran respaldados por ese hecho.

### Clases de preservacion

- **SAFE REMOTE:** 54 tips locales contenidos en refs origin; tip limpio del
  checkout principal; modelo R3.1; evidencia ya committeada en review refs.
- **LOCAL UNIQUE:** 31 commits alcanzables patch-unicos, 30 unreachable
  patch-unicos, 14 entradas dirty/untracked y los tips listados arriba.
- **AMBIGUOUS:** 26 unreachable patch-equivalentes, 25 registros de worktree sin
  directorio, candidatos IA/crops/videos sin manifiesto de sensibilidad.
- **REGENERABLE:** builds, Gradle caches, node_modules/venvs y APKs derivados,
  una vez preservados SHA/receta si aportan evidencia.
- **PRESERVE BEFORE CLEANUP:** todo LOCAL UNIQUE y AMBIGUOUS, corpus/calibracion,
  evidencia fisica, scripts Device Owner/Remote y fork local main.

## 11. Workflow/orchestration findings

### Lo que ya funciona

- Central y GitHub tienen autoridades diferenciadas y comprensibles.
- Las ramas `review/*` permiten review remoto sin merge automatico.
- Los tickets recientes ejecutan hasta PASS/BLOCKED y preservan blockers.
- Las reglas docs-only evitaron un build innecesario en este audit.

### Lo que genera costo

- Ramas de gate/copy/check por microiteracion sobreviven al ticket.
- Un ticket puede heredar cientos de commits ajenos y su nombre deja de
  describir su arbol.
- Central duplica historia, reglas y cola en la misma lista.
- Handoffs repiten evidencia ya committeada.
- No hay señal estructurada de “listo para review” ni review automatico.
- Cleanup se posterga sin manifest de preservacion y aumenta el costo de cada
  auditoria.

### Requisitos minimos para `GLOSH-ORCHESTRATION-01`

1. Central expone una cola con `taskId`, owner, base SHA, rutas, estado y blocker.
2. ChatGPT emite un ticket corto con resultado y limites.
3. Codex usa un worktree aislado, ejecuta hasta PASS/BLOCKED/FAILED y publica un
   unico ref/evidencia cuando corresponde.
4. El resultado devuelve solo SHA, gates, evidencia y blocker.
5. Una señal dispara review ChatGPT; el usuario recibe solo decisiones reales.
6. El cierre propone, pero no ejecuta, disposal de worktree/branch; cleanup
   ocurre solo tras preservation y review.

No hace falta un nuevo servicio/orquestador para probar este contrato: primero
debe normalizarse Central y construirse la baseline.

## 12. Risks

1. **Perdida local:** reset/gc/prune puede destruir 61 patches unicos (31
   alcanzables + 30 unreachable), dirty y evidencia/data.
2. **False baseline:** elegir `origin/main` omite trabajo revisado; elegir una
   review Chrome/P0 incluye trabajo ajeno no revisado.
3. **False product PASS:** DEV y evidence branch no equivalen a release
   integrado/Production.
4. **Backend drift:** schema live reportado por Central no esta reproducido
   completamente en main.
5. **IA provenance:** corpus/modelos locales carecen de un manifest unico de
   origen, sensibilidad, hashes y promocion.
6. **Chrome safety:** R1 no converge en landscape; cualquier continuacion sin
   decision explicita puede reabrir exposicion o cambiar semantica R3.1.
7. **DAG/Remote accidental activation:** muchas ramas recientes hacen parecer
   activos frentes que Central pausa.
8. **Operational clutter:** PRs draft y previews antiguos siguen activos y
   pueden producir costos/notificaciones o confundir ownership.

## 13. Proposed canonical baseline

`GLOSH-CONVERGENCE-BASELINE-01` **no queda cerrada**. Se propone construir una
rama nueva desde `origin/main` solo despues de preservation, integrando por
mecanismo y nunca promoviendo una rama completa por su nombre.

### Version unica a considerar si se empieza sin contexto

| Mecanismo | Unica version propuesta |
|---|---|
| Gobernanza/workflow | `origin/main` @ `7269636f...` |
| Central | `build/glosh-control-center-v2` @ `44590055...` mas este audit revisado |
| App Usuario/Admin base | main; V4 queda candidata pendiente |
| Super Admin | main, PR #100/#101 |
| Room/Policy/base Sync | main |
| Sync/P0 hardening | commits exactos P0 a reconciliar; no rama #95 completa |
| Supabase live | estado Central + introspeccion read-only futura + migraciones reconciliadas |
| Protection Chrome passing | 13B-R DEV358 @ `5c31b948...` |
| Chrome R1 | BLOCKED candidate @ `001be18d...`; diagnostico local `88804188...` |
| GloshIA Images | shared core R3.1, modelo `c8b64af8...` |
| GloshIA Video/GIF | sin baseline de producto; RESERVE |
| DAG | main DEV211; trabajo posterior preservado pero PAUSED |
| Device Owner installer | local lab evidence a preservar; no producto canonico |
| Remote Installer | PIN-only `e969006...` como ultimo prototipo compartido, PAUSED |
| Updates/releases | stable DEV191 solo recuperacion; crear version manifest nuevo |

### Lo que puede ignorarse sin perder direccion real

- HANDOFF/BACKLOG/ROADMAP legacy cuando contradicen Central/GitHub.
- Ramas Chrome anteriores a la ultima rama passing de cada mecanismo, usando su
  evidencia solo para historia/regresion.
- Cache-only, proxy 08A, extension stock 13B-P y R1 fixtures falsos como rutas
  activas.
- AI autorun y ramas Remote copy/check reemplazadas.
- Modelos experimentales R4 como runtime.
- Build outputs/caches, **solo despues** de preservar manifests/hashes necesarios.

### Lo que no puede ignorarse

- Fork local `main`, 31+30 patches unicos, dirty/untracked.
- R1 diagnostic `88804188...`.
- Device Owner installer/evidence local.
- Corpus/calibraciones/evidencia de `.codex-tmp` hasta clasificarlos.
- Commits P0 exactos y diferencia entre schema live y main.
- Review passing Chrome/GloshIA que aun no esta integrada.

## 14. Proposed next sequence

### A. Preservar

1. Ejecutar `MAC-LOCAL-PRESERVATION-03`: refs recuperables privadas/locales,
   bundles/manifests, hashes, sensibilidad y destino. No publicar material
   ambiguo o sensible en GitHub publico.
2. Preservar fork local main, dirty/untracked, 31 reachable patch-unicos, 30
   unreachable patch-unicos y evidencia/corpus.
3. Registrar recetas/hashes de artefactos que despues puedan regenerarse.

### B. Ordenar

4. Aplicar cleanup logico de Central: cola vs historia vs reglas; corregir owner
   fantasma Chrome.
5. Cerrar/archivar drafts y ramas superseded solo despues de preservation y
   decision ChatGPT.
6. Crear un version/evidence manifest por producto y mecanismo.

### C. Decidir

7. ChatGPT contrarrevisa esta propuesta y define el alcance exacto de
   `GLOSH-CONVERGENCE-BASELINE-01`.
8. Decidir integracion P0/schema live, V4 Usuario/Admin, hosting Super Admin,
   destino DAG/Remote y arquitectura de cobertura Chrome R1.

### D. Desarrollo posterior

9. Construir baseline integrada por commits seleccionados y ejecutar gates
   proporcionales una sola vez.
10. Solo despues priorizar un frente de producto. No iniciar automaticamente
    Chrome, DAG, Installer, Admin, Protection, video/GIF ni GloshIA.

## 15. Validation of this audit

- `git fetch --prune` no fue usado; se hizo fetch no destructivo y se verifico
  `ls-remote`/refs actuales.
- SHAs declarados fueron resueltos localmente contra origin.
- Se inspeccionaron GitHub PRs/releases actuales mediante GitHub CLI.
- Se compararon tip containment y patch-id contra todos los refs remotos.
- `git fsck --no-reflogs --unreachable` fue solo lectura.
- Se inventariaron status/worktrees/ramas/artefactos sin modificar el checkout
  principal.
- Se inspecciono el changelog Supabase vigente; no hubo consulta ni mutacion de
  proyecto live.
- No se ejecuto build, lint, test Android, ADB ni gate fisico porque el diff es
  documental y ninguna contradiccion lo requeria.
- No se ejecutaron reset, stash, rebase, clean, prune, gc, borrado o cambio de
  producto.

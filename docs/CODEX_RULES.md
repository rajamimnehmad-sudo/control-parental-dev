# CODEX RULES

Reglas locales/especiales para Codex. `AGENTS.md` y `START_HERE.md` definen el workflow transversal; este archivo no debe contradecirlos.

## Entrada, preflight y contexto

- Leer `START_HERE.md` y usar lectura mínima/condicional; no releer todo el corpus documental por rutina.
- Todo ticket hereda automáticamente las reglas globales. El prompt debe describir sólo el delta específico de la tarea.
- **Antes de escribir código**, comprobar: Central vigente, GitHub vigente, writers activos, owner, base SHA, worktree/rama y rutas permitidas.
- Si ya existen 2 frentes escribiendo código, no iniciar un tercero.
- Si Central y GitHub/evidencia parecen contradecirse, no inferir owner/base/estado. Detener la escritura, reportar la contradicción exacta y esperar reconciliación por ChatGPT.
- Hacer control Git liviano: status, rama, worktrees y commits recientes. Profundizar sólo ante inconsistencia real.
- Usar `docs/AREAS.md` para ubicar el área afectada. Abrir sólo rutas necesarias salvo auditoría explícita.
- No tocar áreas no relacionadas ni agregar features fuera del objetivo.
- Diagnosticar causa raíz antes de cambiar código.
- Modificar la menor cantidad razonable de archivos, sin sacrificar cohesión o mantenibilidad.
- Preferir lotes coherentes; no imponer microtickets cuando varias correcciones pertenecen al mismo subsistema/gate.

## Entorno local y aislamiento

- Repo base canónico local: `/Users/yejielnehmad/Developer/content-filter`, fuera de iCloud.
- Worktrees aislados pueden vivir como carpetas hermanas bajo `/Users/yejielnehmad/Developer/` cuando el ticket lo requiera.
- No crear repos, worktrees, datasets o builds del proyecto dentro de `Documents`, `Desktop`, `Mobile Documents` o iCloud Drive.
- Un único owner escribe cada tarea; por defecto máximo 2 frentes escriben código en paralelo.
- No usar reset, stash, rebase, force-push, limpieza masiva, reformateo global ni revertir cambios desconocidos para despejar el entorno.
- Cambios ajenos no relacionados no bloquean. Ante colisión real sobre rutas o semántica, detenerse antes de pisar trabajo ajeno.

## Eficiencia

- Reutilizar tests, fixtures, caches, scripts y evidencia verificable. No repetir builds globales, investigación o gates físicos cuando el diff no invalida evidencia previa.
- Ejecutar pruebas proporcionales al riesgo y alcance. Un gate global preexistente fuera del diff se reporta, pero no bloquea automáticamente un scope limpio.
- Antes de una operación costosa/larga, usar una prueba pequeña y medible cuando aporte valor. Fijar límites de costo, reintentos, tiempo, almacenamiento y concurrencia.
- No dejar procesos pesados sin límite ni ejecutar varios procesos pesados simultáneos en la Mac M2/8 GB.
- Codex se usa principalmente cuando hace falta entorno local: código, compilaciones, tests, ADB, dispositivos, emuladores, scripts, entrenamiento o benchmarks.
- Análisis, arquitectura o revisión que ChatGPT pueda resolver no deben duplicarse en Codex.
- No producir documentos de evidencia extensos por rutina. Crearlos cuando el riesgo o la trazabilidad lo justifiquen.

## Ejecución autónoma por lote

- **Ticket DELTA significa prompt corto, no ticket chico.** Por defecto el objetivo debe ser terminal y suficientemente amplio para cerrar el problema coherente.
- Mientras permanezca dentro del mismo objetivo, root cause y scope autorizado, continuar autónomamente: diagnóstico → fix → tests → gate físico cuando corresponda → nuevo fix, tantas veces como sea necesario.
- Corregir bugs localizados descubiertos durante el lote sin handoff intermedio cuando siguen dentro del mismo frente/objetivo/root cause/scope; no requieren decisión funcional/producto; no cambian materialmente arquitectura o base; no necesitan rutas fuera de scope; no chocan con otro writer; y no implican acción sensible.
- No detenerse para pedir revisión por cada microarreglo técnico reversible. El resultado terminal del lote debe ser PASS, BLOCKED o FAILED.
- **STOP obligatorio** si aparece un root cause realmente distinto que requiera otra arquitectura/estrategia, una decisión funcional o de producto, cambio material de base/owner/rutas/scope, necesidad de modificar modelo/thresholds/release authority u otra ruta expresamente fuera de scope, colisión real con otro writer o cualquier acción sensible no autorizada.
- PR, merge, `main`, Production, deploy, borrados destructivos y gastos siempre permanecen fuera de esta autonomía salvo autorización específica.
- Si una modificación tardía puede afectar escenarios acreditados previamente, revalidar de forma focalizada esos escenarios sobre el HEAD/APK definitivo. No repetir gates que el cambio final no pueda afectar.
- Al finalizar, dejar commits cohesivos, tests/evidencia proporcionales, validación física cuando corresponda y un único handoff compacto. En PASS publicar y verificar `review/*-final`; en BLOCKED/FAILED preservar/publicar una rama sólo si existe código o evidencia útil que deba quedar disponible para revisión.

## Cierre técnico, Central y handoff

- Codex termina como PASS, BLOCKED o FAILED. PASS es técnico; ChatGPT decide el cierre final.
- Al PASS con código revisable, el mismo ticket debe dejar commit(s) cohesivos, publicar `review/*-final`, verificar el SHA remoto y hacer handoff.
- El push no destructivo de `review/*` o preservación está preautorizado. No pedir otra interacción sólo para publicar la rama review.
- No integrar automáticamente a `main` local/remoto como requisito del PASS.
- PR, merge, publicación DEV de producto, Production, deploy, borrados destructivos y gastos son pasos separados y requieren autorización/control específico.
- Codex no modifica Glosh Central salvo autorización expresa del ticket.
- Cada chat especializado de ChatGPT mantiene el estado persistente de su frente; Dirección General audita la coordinación transversal.
- No generar churn de Central `pending → in_progress → done` para ejecuciones transitorias del mismo ciclo.
- Si un resultado cambia materialmente estado, owner, bloqueo, ruta o cierre, no encadenar por cuenta propia otro trabajo dependiente que requiera un Central reconciliado; entregar el handoff y dejar que ChatGPT haga el postflight.

### Handoff normal

No recontar el ticket ni enumerar datos derivables del diff. Por defecto devolver sólo:

- `STATUS`.
- `BASE`.
- `FUNCTIONAL SHA`.
- `REMOTE REVIEW BRANCH` y `REMOTE HEAD`.
- `VALIDATION`: resumen corto de tests/gates.
- `PHYSICAL`: sólo si hubo gate físico/lab.
- `RESIDUALS` o `BLOCKER`: sólo si existen.

Agregar APK/version/hash, evidencia, rollback, archivos o detalle técnico sólo cuando sea material para el cierre o exista una desviación del scope.

## Android / APK

- Si cambia código que entra a una APK: ejecutar build/tests proporcionales y, cuando haga falta una APK nueva para gate/distribución, usar el `versionCode` DEV real máximo + 1 de la app afectada. Usuario/Admin/DAG versionan de forma independiente.
- Build/test/APK de gate físico pueden salir del worktree validado.
- No publicar automáticamente APK a Supabase/usuarios ni hacer push a `main` porque el build pase.
- Si sólo cambian docs/reglas: no compilar, no incrementar `versionCode`, no generar/publicar APK ni tocar Android/Supabase.
- Pruebas físicas/lab son obligatorias cuando el riesgo o ticket lo requieren. Nunca declararlas por inferencia.
- Después de una publicación realmente autorizada: verificar hashes, package, version, firma y manifests.

## Arquitectura, dependencias y seguridad

- No reabrir decisiones arquitectónicas cerradas salvo evidencia nueva, requisito o regresión.
- Antes de incorporar una arquitectura, librería, modelo o servicio nuevo —o cambiarlo materialmente— contrastar documentación oficial/fuentes primarias y evaluar seguridad, privacidad, mantenimiento, compatibilidad, licencia, costo, CPU/RAM/disco/red y tiempo.
- Verificar licencia, procedencia, mantenimiento y uso comercial de modelos, pesos, datasets y dependencias antes de incorporarlos.
- No implementar ciegamente una práctica inferior; si una restricción obliga a un compromiso, documentar riesgo residual y rollback.
- No usar Service Role Key en Android.
- No borrar datos sin confirmación/autorización específica.
- Errores técnicos van a Logcat; mensajes al usuario deben ser simples y no atribuir una causa no demostrada.

## Datos, entrenamiento y compute

- `.codex-tmp` no es basura en bloque. Preservar corpus, revisiones humanas y artefactos reproducibles referenciados.
- No cargar datasets completos en RAM local ni planificar entrenamiento visual grande en la Mac M2/8 GB.
- Supabase sirve para DB/Storage/metadata/auditoría/colas livianas; no usar Edge Functions como compute pesado.
- GitHub Actions estándar se usa para CI/orquestación; no asumir GPU.
- Entrenamiento pesado usa GPU externa efímera sólo bajo ticket/autorización con costo, pipeline reproducible y apagado automático.

## Reglas DAG especiales

- Un candidato DAG que cambie navegador/carga/GeckoView/imágenes debe usar el gate físico vigente para ese ticket; no sustituirlo por evidencia inventada.
- Registrar dispositivo, Android, fecha, variante y métricas exigidas por el gate.
- Cada cambio en `app-dag-browser/src/main/assets/dag-protection/` debe incrementar la versión de la extensión en `manifest.json` y conservar actualización in-place consciente de versión.

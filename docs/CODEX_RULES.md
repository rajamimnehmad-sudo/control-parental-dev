# CODEX RULES

Reglas locales/especiales para Codex. `AGENTS.md` y `START_HERE.md` definen el workflow transversal; este archivo no debe contradecirlos.

## Entrada y contexto

- Leer `START_HERE.md` y usar lectura minima/condicional; no releer todo el corpus documental por rutina.
- Antes de escribir, hacer un control Git liviano: status, rama, worktrees, commits recientes, base/owner/rutas de la tarea y Central cuando corresponda.
- Usar `docs/AREAS.md` para ubicar el area afectada. Abrir solo rutas necesarias salvo auditoria explicita.
- No tocar areas no relacionadas ni agregar features fuera del objetivo.
- Diagnosticar causa raiz antes de cambiar codigo.
- Modificar la menor cantidad razonable de archivos, sin sacrificar cohesion o mantenibilidad.
- Preferir lotes coherentes que cierren un problema completo; no imponer microtickets cuando varias correcciones pertenecen al mismo subsistema/gate.

## Entorno local y aislamiento

- Repo base canonico: `/Users/yejielnehmad/Developer/content-filter`, fuera de iCloud.
- Worktrees aislados pueden vivir como carpetas hermanas bajo `/Users/yejielnehmad/Developer/` cuando el ticket lo requiera.
- No crear repos/worktrees/datasets/builds del proyecto dentro de `Documents`, `Desktop`, `Mobile Documents` o iCloud Drive.
- Un unico owner escribe cada tarea; por defecto maximo 2 frentes escriben codigo en paralelo.
- No usar reset, stash, rebase, force-push, limpieza masiva, reformateo global ni revertir cambios desconocidos para despejar el entorno.
- Cambios ajenos no relacionados no bloquean. Ante colision real sobre rutas/semantica, detenerse y reportar.

## Eficiencia

- Reutilizar tests, fixtures, caches, scripts y evidencia verificable. No repetir builds globales, investigacion o gates fisicos cuando el diff no invalida evidencia previa.
- Ejecutar pruebas proporcionales al riesgo y al alcance. Un gate global preexistente fuera del diff se reporta, pero no bloquea automaticamente un scope limpio.
- Antes de una operacion costosa/larga, usar una prueba pequena y medible cuando aporte valor. Fijar limites de costo, reintentos, tiempo, almacenamiento y concurrencia.
- No dejar procesos pesados sin limite ni ejecutar varios procesos pesados simultaneos en la Mac M2/8 GB.
- Codex se usa principalmente cuando hace falta entorno local: codigo, compilaciones, tests, ADB, dispositivos, emuladores, scripts, entrenamiento o benchmarks. Analisis/arquitectura/revision que ChatGPT pueda resolver no debe duplicarse en Codex.

## Cierre tecnico y handoff

- Codex termina como PASS, BLOCKED o FAILED. PASS es tecnico; ChatGPT decide el cierre final.
- Al PASS con codigo/evidencia revisable, el mismo ticket debe dejar commits cohesivos (preferentemente funcional + evidencia), publicar una rama `review/*-final`, verificar el SHA remoto y reportar base/functional/final SHA, archivos, gates y evidencia.
- El push no destructivo de `review/*`/preservacion esta preautorizado. No pedir otra interaccion solo para publicar la rama review.
- No integrar automaticamente a `main` local/remoto como requisito del PASS.
- PR, merge, publicacion DEV de producto, Production, deploy, borrados destructivos y gastos son pasos separados y requieren autorizacion/control especifico.
- Codex no modifica Glosh Central salvo autorizacion expresa del ticket. ChatGPT sincroniza Central al revisar el resultado.
- No generar churn de Central `pending→in_progress→done` para ejecuciones Codex transitorias del mismo ciclo.

## Android / APK

- Si cambia codigo que entra a una APK: ejecutar build/tests proporcionales y, cuando haga falta una APK nueva para gate/distribucion, usar el `versionCode` DEV real maximo + 1 de la app afectada. Usuario/Admin/DAG versionan de forma independiente.
- Build/test/APK de gate fisico pueden salir del worktree validado.
- No publicar automaticamente APK a Supabase/usuarios ni hacer push a `main` por el solo hecho de que el build pase.
- Si solo cambian docs/reglas: no compilar, no incrementar `versionCode`, no generar/publicar APK, no tocar Android/Supabase.
- Pruebas fisicas/lab son obligatorias cuando el riesgo o el ticket lo requieren (navegador, medios, seguridad, compatibilidad fisica, lifecycle). Nunca declararlas por inferencia.
- Despues de una publicacion realmente autorizada: verificar hashes, package, version, firma y manifests correspondientes.

## Arquitectura, dependencias y seguridad

- No reabrir decisiones arquitectonicas cerradas salvo nueva evidencia/requisito/regresion.
- Antes de incorporar una arquitectura/libreria/modelo/servicio nuevo o cambiar uno materialmente, contrastar documentacion oficial/fuentes primarias y evaluar seguridad, privacidad, mantenimiento, compatibilidad, licencia, costo, CPU/RAM/disco/red y tiempo.
- Verificar licencia/procedencia/mantenimiento/uso comercial de modelos, pesos, datasets y dependencias antes de incorporarlos.
- No implementar ciegamente una practica inferior; si una restriccion obliga a un compromiso, documentar riesgo residual y rollback.
- No usar Service Role Key en Android.
- No borrar datos sin confirmacion/autorizacion especifica.
- Errores tecnicos van a Logcat; mensajes al usuario deben ser simples.

## Datos, entrenamiento y compute

- `.codex-tmp` no es basura en bloque. Preservar corpus, revisiones humanas y artefactos reproducibles referenciados.
- No cargar datasets completos en RAM local ni planificar entrenamiento visual grande en la Mac M2/8 GB.
- Supabase sirve para DB/Storage/metadata/auditoria/colas livianas; no usar Edge Functions como compute pesado.
- GitHub Actions estandar se usa para CI/orquestacion; no asumir GPU.
- Entrenamiento pesado usa GPU externa efimera solo bajo ticket/autorizacion que contemple costo, pipeline reproducible y apagado automatico.

## Reglas DAG especiales

- Un candidato DAG que cambie navegador/carga/GeckoView/imagenes debe usar el gate fisico definido para DAG cuando siga vigente para ese ticket; no sustituirlo por evidencia inventada. Registrar dispositivo/Android/fecha/variante y las metricas exigidas por el gate.
- Cada cambio en `app-dag-browser/src/main/assets/dag-protection/` debe incrementar la version de la extension en `manifest.json` y conservar actualizacion in-place consciente de version.

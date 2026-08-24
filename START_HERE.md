# START HERE

## Leer primero

Antes de cualquier tarea:

1. `docs/CURRENT_COORDINATION.md`
2. Glosh Central / Control Center: rama `build/glosh-control-center-v2`, archivo `docs/AI_TASK_TRACKER.json`
3. Estado Git actual: rama, worktrees, commits recientes y cambios locales
4. `docs/AREAS.md`

Luego, segun el trabajo:

- `docs/CODEX_RULES.md`
- `docs/CODEX_MAP.md`
- `docs/DEV_FLOW.md`
- `docs/BACKLOG_PRODUCTO.md` como memoria de producto
- `docs/compatibility/README.md` cuando el ticket afecte compatibilidad, configuracion o pruebas Android

`docs/HANDOFF_ACTUAL.md` conserva un snapshot tecnico con corte 2026-08-11. **No usarlo como estado vigente del proyecto ni para elegir la ruta actual.** Si contradice Glosh Central o el estado Git actual, prevalecen Glosh Central y Git.

## Reglas de trabajo

- No asumir que SHAs, ramas, prioridades o bloqueos anteriores siguen vigentes.
- No reanalizar arquitectura cerrada salvo que nueva evidencia lo exija.
- Trabajar por tickets acotados y con owner unico de escritura.
- Maximo dos frentes de escritura de codigo por defecto.
- Aislar trabajo paralelo en rama/worktree cuando corresponda.
- No tocar areas no relacionadas ni pisar trabajo ajeno.

Antes de cualquier ticket de codigo:

1. Revisar Glosh Central y confirmar Task ID/owner/alcance.
2. Revisar Git: `status`, rama, worktrees y commits recientes.
3. Confirmar base SHA y rutas permitidas cuando corresponda.
4. Usar `docs/AREAS.md` para identificar el area exacta.
5. Abrir solo archivos necesarios.
6. Diagnosticar causa raiz antes de escribir.
7. Modificar la menor cantidad posible de archivos.
8. Ejecutar los tests/gates del alcance.
9. Entregar diff, tests y evidencia para revision de ChatGPT.
10. Reflejar el cambio material en Glosh Central en el mismo ciclo.

Prioridad permanente:

- preservar trabajo existente;
- reducir colisiones;
- evitar cambios innecesarios;
- mantener modulos cohesionados;
- hacer fixes faciles de revisar;
- mantener el repo escalable.

## Prohibiciones operativas

Sin autorizacion explicita del usuario:

- no push;
- no PR;
- no merge;
- no Production/deploy;
- no gastos;
- no borrados destructivos.

Nunca usar reset, stash, rebase, force-push, limpieza masiva ni revertir cambios desconocidos para despejar el entorno.

## Docs-only

Si solo cambian documentos:

- no compilar;
- no incrementar `versionCode`;
- no publicar APK;
- no tocar Android/Supabase salvo que el propio documento sea de esa area y no cambie runtime.

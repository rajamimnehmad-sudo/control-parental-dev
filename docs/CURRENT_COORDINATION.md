# CURRENT COORDINATION

Fecha de esta regla: 2026-08-24

Este archivo no contiene el estado operativo del proyecto; define **donde leerlo** y como evitar usar snapshots viejos como coordinacion vigente.

## Fuentes de verdad

### 1. Coordinacion vigente

Glosh Central / Control Center:

- rama: `build/glosh-control-center-v2`
- tracker: `docs/AI_TASK_TRACKER.json`

Ese tracker define:

- tareas activas;
- prioridades;
- owners;
- bloqueos vigentes;
- pendientes;
- cierres;
- siguiente ruta.

La app Glosh Control Center consume ese mismo JSON.

### 2. Codigo y evidencia

GitHub y el estado Git actual son la verdad para:

- codigo;
- commits;
- ramas;
- worktrees;
- PR;
- tests;
- evidencia.

El trabajo local de Codex puede ser mas nuevo que GitHub remoto. Antes de concluir que algo falta o se perdio, verificar el estado local actual y los SHAs reportados.

## Documentos historicos

`docs/HANDOFF_ACTUAL.md` conserva un snapshot tecnico con corte 2026-08-11. Su nombre es legado: **no es la coordinacion actual**.

`docs/BACKLOG_PRODUCTO.md` conserva memoria de producto y pendientes; tampoco define por si solo que esta activo hoy.

Los documentos de evidencia explican decisiones y gates, pero no sustituyen actualizar Glosh Central.

## Regla antes de escribir codigo

1. Revisar Glosh Central.
2. Revisar `git status`, rama, worktrees y commits recientes.
3. Confirmar Task ID, owner, alcance, base SHA y rutas permitidas cuando corresponda.
4. Trabajar solo en el area necesaria.
5. Un unico owner de escritura por tarea.
6. Por defecto, maximo dos frentes escribiendo codigo simultaneamente.
7. Ante colision real, detenerse antes de pisar cambios.

## Regla de cierre

Codex devuelve PASS, BLOCKED o FAILED con diff/tests/evidencia.

ChatGPT revisa ese resultado antes del cierre final.

Todo cambio material de ruta, prioridad, estado, tarea, bloqueo o cierre debe reflejarse en `AI_TASK_TRACKER.json` en el mismo ciclo.

## Seguridad operativa

Sin autorizacion explicita del usuario:

- no push de codigo de producto;
- no PR;
- no merge;
- no Production/deploy;
- no gastos;
- no borrados destructivos.

No usar reset, stash, rebase, force-push, limpieza masiva ni revertir trabajo desconocido para resolver conflictos.

## Trabajo paralelo actual

No fijar aqui nombres de frentes o SHAs porque envejecen rapido. Leer siempre `AI_TASK_TRACKER.json` y el estado Git actual.

# AI NEXT TICKET

## LOCAL-STATE-RECONCILIATION-00

**Tipo:** inventario / reconciliación
**Prioridad:** crítica
**Modo:** SOLO LECTURA DEL PROYECTO
**Responsable de ejecución:** Codex
**Revisor:** ChatGPT / jefe técnico central

> Antes de ejecutar, leer también `docs/AI_WORKFLOW.md` en esta misma rama. Esa es la regla operativa permanente.

### Objetivo

Establecer la verdad exacta del proyecto local antes de ejecutar cualquier cambio nuevo. GitHub no refleja necesariamente todo lo que existe actualmente en la Mac (incluyendo Chrome Visual y trabajo acumulado de Codex/Spark), por lo que primero hay que reconciliar el estado local contra `origin/main` y contra las ramas remotas relevantes.

### Regla absoluta

Durante este ticket **NO modificar el proyecto auditado**.

No:
- editar/formatear archivos del proyecto;
- crear/borrar archivos de producto;
- commit/push de código;
- crear PR de código;
- mergear;
- resetear;
- limpiar;
- stash;
- checkout que pueda alterar el working tree auditado;
- aplicar migraciones;
- tocar Supabase/Production;
- instalar APK;
- ejecutar comandos destructivos.

Si un comando pudiera modificar el estado auditado, no ejecutarlo.

### Única excepción permitida: handoff a ChatGPT

Para que el usuario no tenga que copiar/pegar el resultado, al terminar Codex **DEBE** dejar el reporte en GitHub:

`docs/AI_CODEX_HANDOFF.md`

en la rama:

`coordination/ai-control`

Se permite exclusivamente crear/actualizar ese archivo, hacer commit y push de ese reporte. No modificar ningún otro archivo.

Si hacerlo desde el worktree auditado pudiera alterar su estado, usar un worktree temporal limpio o un mecanismo que no cambie el working tree observado.

### Inventario obligatorio

Reportar con evidencia:

1. Ruta exacta del repo y worktree actual.
2. Rama actual.
3. HEAD exacto.
4. `git status --short`.
5. Archivos modificados tracked.
6. Archivos staged.
7. Archivos nuevos/untracked.
8. Archivos borrados/renombrados si existen.
9. Commits locales no presentes en `origin/main`.
10. Commits remotos no presentes localmente, si los hubiera.
11. Diferencia completa de la rama actual contra `origin/main`.
12. Ramas locales y su relación con remotas.
13. Worktrees existentes y su estado.
14. Ramas remotas relevantes (`review/*`, `codex/*`, `agent/*`, Chrome/DAG, etc.).
15. Migraciones Supabase locales nuevas o modificadas que todavía no estén reconciliadas.
16. Cambios locales acumulados provenientes de Spark/Codex, separándolos por tema cuando sea posible:
    - exact technical Supabase hosts;
    - sync atómico de políticas;
    - pairing hardening;
    - Chrome Visual;
    - cualquier otro cambio no reconocido.
17. Estado exacto de Chrome Visual:
    - localizar commit `6a045f13` si existe;
    - rama/worktree donde vive;
    - archivos incluidos;
    - commits posteriores relacionados;
    - qué pruebas/evidencia existen localmente.
18. Tests/builds/lint que hayan quedado documentados pero cuyo código aún no esté en GitHub.
19. Archivos sensibles, secretos, credenciales, keystores, APKs, logs o artefactos que **no deban subirse**.
20. Cualquier diferencia entre handoffs/documentación local y el código real que pueda afectar la reconciliación.

### No ejecutar suites pesadas todavía

Este ticket no busca volver a probar el proyecto. No gastar tiempo/créditos en builds completos, emuladores ni pruebas físicas. Solo puede ejecutarse una comprobación inocua si es imprescindible para identificar el estado existente.

### Clasificación final requerida

Clasificar todo lo encontrado en:

1. **YA EN GITHUB / APROBADO**
2. **LOCAL CON COMMIT, NO SUBIDO**
3. **LOCAL SIN COMMIT**
4. **PENDIENTE DE REVISIÓN TÉCNICA**
5. **NO DEBE SUBIRSE**
6. **ESTADO INCIERTO / REQUIERE DECISIÓN**

### Formato de `AI_CODEX_HANDOFF.md`

Debe ser compacto y contener:

- ticket ejecutado;
- fecha/hora;
- rama + HEAD;
- resumen del working tree;
- commits locales no subidos;
- tabla por grupo de cambios;
- estado Chrome Visual;
- estado de migraciones;
- riesgos de pérdida/mezcla;
- comandos de solo lectura relevantes;
- propuesta de separación futura en ramas `review/<ticket>` **sin ejecutarla**.

No pegar logs enormes ni historia ya irrelevante.

### Gate

Al terminar:
1. subir únicamente `docs/AI_CODEX_HANDOFF.md` a `coordination/ai-control`;
2. verificar que quedó disponible en GitHub;
3. **DETENERSE**.

No arreglar nada aunque el problema parezca obvio.
No hacer commit de código aunque un cambio parezca terminado.
No comenzar el siguiente ticket.

Después el usuario solo debe decir **“ya”** a ChatGPT. ChatGPT leerá el handoff y auditará directamente desde GitHub.

# AI NEXT TICKET

## LOCAL-STATE-RECONCILIATION-00

**Tipo:** inventario / reconciliación
**Prioridad:** crítica
**Modo:** SOLO LECTURA
**Responsable de ejecución:** Codex
**Revisor:** ChatGPT / jefe técnico central

### Objetivo

Establecer la verdad exacta del proyecto local antes de ejecutar cualquier cambio nuevo. GitHub no refleja necesariamente todo lo que existe actualmente en la Mac (incluyendo Chrome Visual y trabajo acumulado de Codex/Spark), por lo que primero hay que reconciliar el estado local contra `origin/main` y contra las ramas remotas relevantes.

### Regla absoluta

Durante este ticket **NO modificar el proyecto**.

No:
- editar archivos;
- formatear archivos;
- crear ni borrar archivos;
- hacer commit;
- hacer push;
- crear PR;
- mergear;
- resetear;
- limpiar;
- stash;
- checkout que pueda alterar el working tree;
- aplicar migraciones;
- tocar Supabase/Production;
- instalar APK;
- ejecutar comandos destructivos.

Si un comando pudiera modificar estado, no ejecutarlo.

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

Al finalizar, clasificar todo lo encontrado en:

1. **YA EN GITHUB / APROBADO**
2. **LOCAL CON COMMIT, NO SUBIDO**
3. **LOCAL SIN COMMIT**
4. **PENDIENTE DE REVISIÓN TÉCNICA**
5. **NO DEBE SUBIRSE**
6. **ESTADO INCIERTO / REQUIERE DECISIÓN**

### Cierre obligatorio

Entregar un único reporte compacto con:

- rama + HEAD;
- resumen del working tree;
- commits locales no subidos;
- tabla por grupo de cambios;
- estado Chrome Visual;
- estado de migraciones;
- riesgos de pérdida/mezcla;
- propuesta de cómo separar los cambios en futuras ramas `review/<ticket>` **sin ejecutar esa propuesta**.

### Gate

Al terminar: **DETENERSE**.

No arreglar nada aunque el problema parezca obvio.
No hacer commit aunque un cambio parezca terminado.
No subir nada.
No comenzar el siguiente ticket.

El usuario llevará el reporte a ChatGPT para auditoría y decisión del próximo paso.

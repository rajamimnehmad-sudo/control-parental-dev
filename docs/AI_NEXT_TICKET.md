# AI NEXT TICKET

## LOCAL-WORK-PRESERVATION-01

**Tipo:** preservación / seguridad operativa
**Prioridad:** crítica
**Modo:** NO EDITAR CÓDIGO / NO RECONCILIAR TODAVÍA
**Responsable de ejecución:** Codex
**Revisor:** ChatGPT / jefe técnico central

> Antes de ejecutar, leer `docs/AI_WORKFLOW.md` en esta misma rama.

### Contexto validado

El inventario `LOCAL-STATE-RECONCILIATION-00` confirmó:

- repo auditado: `/Users/yejielnehmad/Developer/content-filter`;
- worktree activo: `work/chrome-visual`;
- HEAD: `6a045f1300336b1f033cab7bea2ce3ba25dcd119`;
- `main` local: `9e41c309`, 39 commits delante de `origin/main`;
- `work/chrome-visual`: 46 commits delante de `origin/main`;
- 6 tracked modificados + 4 untracked, 0 staged;
- los cambios sin commit corresponden a hosts Supabase exactos, sync atómico y pairing hardening;
- no hay evidencia de que esos 10 archivos estén preservados remotamente.

### Objetivo

**Preservar exactamente el estado local actual antes de revisar, arreglar, separar o limpiar nada.**

Este ticket NO aprueba técnicamente ningún cambio. Solo crea copias remotas seguras y verificables para eliminar riesgo de pérdida.

### Gate inicial — detectar drift

Antes de preservar, comprobar nuevamente en modo lectura:

- rama actual;
- HEAD;
- `git status --short`;
- lista exacta de tracked modificados y untracked.

Si el estado cambió materialmente respecto del handoff anterior, **DETENERSE** y dejar el nuevo estado en `docs/AI_CODEX_HANDOFF.md`. No intentar adaptar el ticket por cuenta propia.

### Preservación de commits ya existentes

Sin cambiar de rama ni alterar el working tree auditado, preservar remotamente como ramas de seguridad:

1. `preserve/local-main-2026-08-20` → tip actual de `main` local (`9e41c309` si sigue igual).
2. `preserve/chrome-visual-2026-08-20` → tip actual de `work/chrome-visual` (`6a045f13` si sigue igual).
3. `preserve/pre-chrome-visual-2026-08-17` → tip actual de `snapshot/pre-chrome-visual-2026-08-17` (`833d5ad8` si sigue igual).

Estas ramas son **snapshots de seguridad**, no ramas aprobadas ni listas para merge.

No crear PRs para ellas.

### Preservación exacta de los 10 archivos sin commit

Crear una rama remota:

`preserve/uncommitted-2026-08-20`

baseada exactamente en el HEAD observado de `work/chrome-visual`.

Para no tocar el working tree sucio:

1. usar un worktree temporal limpio o clon temporal;
2. reproducir allí **únicamente** el diff tracked actual y los 4 archivos untracked del inventario;
3. verificar que el contenido preservado coincida byte a byte con la fuente cuando sea posible;
4. verificar que solo estén presentes los 10 paths esperados;
5. ejecutar `git diff --check`;
6. crear un único commit de snapshot;
7. push de esa rama;
8. verificar que la rama remota apunta al commit creado.

Commit sugerido:

`chore(preserve): snapshot local uncommitted state 2026-08-20`

### Exclusiones absolutas

NO incluir ni leer/subir:

- `.env`;
- `.codex-tmp/`;
- APKs;
- `build/`;
- logs de instrumentación;
- caches;
- keystores/certificados;
- secretos o credenciales;
- cualquier archivo ignorado fuera de los 4 untracked ya identificados.

Los 4 untracked permitidos son solamente:

- `core-sync/src/test/kotlin/com/contentfilter/core/sync/engine/DefaultSyncEngineAtomicPolicySyncTest.kt`
- `core-sync/src/test/kotlin/com/contentfilter/core/sync/engine/TargetedPolicySyncCoordinatorTest.kt`
- `supabase/migrations/20260819150000_harden_pairing_tokens.sql`
- `supabase/pairing_hardening_03b_checks.sql`

### No hacer todavía

- no editar código;
- no corregir tests;
- no reordenar commits;
- no cherry-pick;
- no merge;
- no rebase;
- no reset/clean/stash del worktree original;
- no aplicar migraciones;
- no tocar Supabase;
- no ejecutar builds ni suites pesadas;
- no probar APK;
- no decidir qué cambios son buenos/malos;
- no eliminar ramas ni worktrees existentes.

Puede eliminarse únicamente el worktree/clon temporal creado por este ticket, y solo después de verificar que esté limpio y que todo quedó preservado remotamente.

### Verificación final obligatoria

Confirmar:

- las 4 ramas `preserve/...` existen en remoto;
- sus SHAs exactos;
- `preserve/uncommitted-2026-08-20` contiene únicamente los 10 paths esperados respecto de `6a045f13` (o el HEAD validado si hubiera drift autorizado por ChatGPT);
- el working tree original conserva exactamente su estado inicial del ticket;
- no se subieron secretos ni artefactos ignorados.

### Handoff obligatorio

Actualizar únicamente:

`docs/AI_CODEX_HANDOFF.md`

en `coordination/ai-control` con:

- ticket ejecutado;
- estado inicial/final del working tree original;
- las cuatro ramas de preservación y SHAs;
- SHA del commit snapshot de los 10 archivos;
- lista exacta de paths preservados;
- verificaciones ejecutadas;
- cualquier anomalía o riesgo.

No acumular el handoff anterior: reemplazarlo por el estado vigente.

### Criterio de terminado

El ticket termina solo cuando todo el trabajo local crítico está preservado remotamente sin alterar el estado original de la Mac.

Después: **DETENERSE**.

No comenzar revisión técnica. El siguiente paso será decidido por ChatGPT y probablemente separará la revisión de:

1. hosts Supabase exactos;
2. sync atómico;
3. pairing hardening;
4. integración DAG/Chrome Visual.

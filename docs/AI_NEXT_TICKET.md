# AI NEXT TICKET

## SEC-EXACT-TECHNICAL-HOSTS-01-REVIEW

**Tipo:** revisión técnica + reconstrucción limpia
**Prioridad:** crítica
**Responsable de ejecución:** Codex
**Revisor:** ChatGPT / jefe técnico central

> Antes de ejecutar, leer `docs/AI_WORKFLOW.md` en `coordination/ai-control`.

### Contexto validado

Los tickets `LOCAL-STATE-RECONCILIATION-00` y `LOCAL-WORK-PRESERVATION-01` están cerrados.

El estado local crítico ya está preservado remotamente en ramas `preserve/*`. En particular:

- `preserve/local-main-2026-08-20` conserva la base local previa a Chrome Visual;
- `preserve/uncommitted-2026-08-20` conserva el snapshot de los 10 archivos que estaban sin commit;
- el worktree original de la Mac debe permanecer intacto.

La auditoría detectó que el código remoto aprobado todavía trataba `supabase.co` de forma demasiado amplia como infraestructura técnica. El snapshot preservado contiene un candidato que restringe la excepción técnica al host real de Glosh:

`syeycayasyufedwoprea.supabase.co`

Este ticket debe revisar ese candidato, reconstruirlo de forma aislada y demostrar que no abre hosts hermanos ni subdominios falsos.

### Alcance exacto

Trabajar únicamente sobre estas rutas, salvo que un test existente estrictamente relacionado requiera un ajuste mínimo justificado:

1. `core-policy/src/main/kotlin/com/contentfilter/core/policy/DefaultPolicyEngine.kt`
2. `feature-vpn/src/main/java/com/contentfilter/feature/vpn/policy/VpnDomainPolicyEvaluator.kt`
3. `feature-vpn/src/test/kotlin/com/contentfilter/feature/vpn/policy/VpnDomainPolicyEvaluatorTest.kt`

No tocar sync, pairing, Chrome Visual, DAG, Supabase migrations ni otros frentes.

### Rama de trabajo

Crear una rama limpia:

`review/sec-exact-technical-hosts-01`

Base:

`preserve/local-main-2026-08-20`

No trabajar en el worktree original sucio. Usar un worktree temporal limpio o clon temporal.

### Fuente del candidato

Tomar como referencia únicamente el cambio correspondiente a hosts técnicos de:

`preserve/uncommitted-2026-08-20`

No copiar los demás cambios preservados en esa rama.

### Revisión técnica obligatoria

Antes de aceptar el candidato, verificar el comportamiento y ajustar solo si es necesario:

1. El host exacto de Glosh `syeycayasyufedwoprea.supabase.co` debe conservar acceso técnico cuando corresponde.
2. `proyecto-ajeno.supabase.co` NO debe obtener trato técnico privilegiado.
3. El apex `supabase.co` NO debe quedar auto-permitido por esta excepción.
4. `api.syeycayasyufedwoprea.supabase.co` u otros subdominios inventados del proyecto NO deben heredar automáticamente la excepción exacta.
5. La normalización habitual de host (minúsculas, punto final, `www.` si aplica al contrato actual) no debe reintroducir matching amplio.
6. Las reglas explícitas, SafeSearch, UT1/lista local y precedencias existentes fuera de esta excepción no deben alterarse accidentalmente.
7. Los hosts técnicos Google ya existentes no deben cambiar de semántica salvo que el candidato original lo haga de forma necesaria y demostrada.

### Tests mínimos requeridos

Usar los tests existentes y agregar/modificar solo los estrictamente necesarios para probar:

- Glosh Supabase exacto permitido;
- proyecto Supabase ajeno no privilegiado;
- `supabase.co` no privilegiado;
- subdominio falso del host Glosh no privilegiado;
- host técnico crítico conserva la precedencia prevista;
- una regla/manual/lista local sigue actuando normalmente sobre hosts que ya no son técnicos.

Ejecutar la suite unitaria más estrecha que cubra `core-policy` y `feature-vpn`, más una comprobación de compilación de los módulos afectados. Si esas capas pasan y el cambio afecta compilación de App Usuario, ejecutar una compilación no física de App Usuario una sola vez.

No repetir suites DAG/Chrome/Admin ni pruebas físicas: este cambio no las afecta.

Ejecutar `git diff --check`.

### Resultado esperado

Si el candidato es correcto:

1. dejar únicamente el cambio aislado en `review/sec-exact-technical-hosts-01`;
2. un commit coherente, sugerido:
   `fix(security): restrict technical Supabase host allowlist`
3. push de la rama;
4. abrir PR contra `preserve/local-main-2026-08-20` para que el diff sea aislado y auditable;
5. NO mergear.

La PR debe usar el cierre estándar de `AI_WORKFLOW.md`:

- resumen;
- archivos tocados;
- comandos/tests ejecutados;
- resultados exactos;
- riesgos pendientes;
- branch + commit;
- prueba física: `no requerida` salvo hallazgo inesperado.

### Manejo de bloqueo

Si aparece una incompatibilidad estructural, fallo inesperado no atribuible al candidato o necesidad de tocar áreas fuera del alcance:

- no hacer reescrituras amplias;
- preservar evidencia;
- no ampliar el ticket por cuenta propia;
- actualizar `docs/AI_CODEX_HANDOFF.md` en `coordination/ai-control` con el bloqueo;
- detenerse.

### Prohibiciones

- no tocar el worktree original sucio;
- no merge/rebase/reset/clean/stash sobre el repo original;
- no tocar `main`;
- no aplicar migraciones;
- no tocar Supabase/Production;
- no modificar sync/pairing/Chrome/DAG;
- no instalar APK ni pedir prueba física;
- no iniciar el siguiente ticket.

### Handoff obligatorio

Al cerrar, reemplazar `docs/AI_CODEX_HANDOFF.md` en `coordination/ai-control` con:

- ticket;
- PASS / NEEDS-FIX / BLOCKED;
- branch + commit;
- PR;
- archivos tocados;
- tests/comandos y resultados;
- revisión de los 4 casos Supabase exactos;
- riesgos restantes;
- confirmación de que el worktree original quedó intacto.

Después: **DETENERSE**.

ChatGPT auditará la PR/diff/tests y decidirá si el cambio se aprueba y cuál es el próximo ticket.

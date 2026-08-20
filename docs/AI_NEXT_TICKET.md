# AI NEXT TICKET

## P0-HARDENING-BATCH-01

**Tipo:** revisión técnica + reconstrucción limpia por lote
**Prioridad:** crítica
**Responsable de ejecución:** Codex
**Revisor:** ChatGPT / jefe técnico central

> Antes de ejecutar, leer `docs/AI_WORKFLOW.md` en `coordination/ai-control`.

## Contexto

`LOCAL-STATE-RECONCILIATION-00` y `LOCAL-WORK-PRESERVATION-01` están cerrados con PASS.
Todo el estado local crítico quedó preservado en ramas `preserve/*`.

Este lote agrupa los tres cambios P0 locales ya preservados que provienen del mismo bloque de hardening y que conviene revisar juntos, pero deben quedar internamente separados y auditables:

1. allowlist exacta de hosts técnicos Supabase;
2. sync atómico de policy + rules + limits + groups;
3. pairing hardening (cliente + migración + checks SQL).

No incluir Chrome Visual, DAG ni device-token scope en este lote.

## Rama

Crear una rama limpia:

`review/p0-hardening-batch-01`

Base:

`preserve/local-main-2026-08-20`

No trabajar sobre el worktree original sucio. Usar worktree/clon temporal limpio.

Fuente de candidatos:

`preserve/uncommitted-2026-08-20`

Copiar/reconstruir únicamente los cambios correspondientes a estos tres bloques.

---

# Bloque A — Exact technical hosts

Rutas principales:

- `core-policy/src/main/kotlin/com/contentfilter/core/policy/DefaultPolicyEngine.kt`
- `feature-vpn/src/main/java/com/contentfilter/feature/vpn/policy/VpnDomainPolicyEvaluator.kt`
- `feature-vpn/src/test/kotlin/com/contentfilter/feature/vpn/policy/VpnDomainPolicyEvaluatorTest.kt`

Validar:

- `syeycayasyufedwoprea.supabase.co` conserva el acceso técnico previsto;
- `proyecto-ajeno.supabase.co` NO obtiene trato técnico privilegiado;
- `supabase.co` NO queda privilegiado;
- `api.syeycayasyufedwoprea.supabase.co` NO hereda automáticamente la excepción;
- normalización de host no reabre matching amplio;
- SafeSearch, reglas explícitas, lista local y precedencias no sufren regresiones.

Commit separado sugerido:

`fix(security): restrict technical Supabase host allowlist`

---

# Bloque B — Atomic policy revision sync

Rutas candidatas principales:

- `core-sync/src/main/java/com/contentfilter/core/sync/engine/DefaultSyncEngine.kt`
- `core-sync/src/test/kotlin/com/contentfilter/core/sync/engine/DefaultSyncEngineAtomicPolicySyncTest.kt`
- `core-sync/src/test/kotlin/com/contentfilter/core/sync/engine/TargetedPolicySyncCoordinatorTest.kt`

Objetivo:

Evitar estados efectivos mixtos donde una policy/revisión nueva quede aplicada con reglas, límites o grupos de una revisión anterior.

Validar como mínimo:

- policy + rules + daily limits + app groups + group apps pertenecen al mismo target/revision;
- aplicación local ocurre transaccionalmente como bundle;
- fallo en cualquier parte conserva last-known-good;
- cursor/revisión solo avanza después del commit completo;
- ACK solo ocurre cuando el bundle completo quedó aplicado y confirmado;
- un fallo intermedio no deja policy N+1 con hijos N;
- targeted sync y periodic sync mantienen semántica coherente.

Si el candidato no cumple exactamente esto, corregirlo dentro de este bloque sin ampliar a otras áreas no relacionadas.

Commit separado sugerido:

`fix(sync): apply policy revisions atomically`

---

# Bloque C — Pairing hardening

Rutas candidatas principales:

- `feature-activation/src/main/java/com/contentfilter/feature/activation/ActivationViewModel.kt`
- `feature-activation/src/test/kotlin/com/contentfilter/feature/activation/UserPairingCodeTest.kt`
- `supabase/migrations/20260819150000_harden_pairing_tokens.sql`
- `supabase/pairing_hardening_03b_checks.sql`

Objetivo:

Cerrar el pairing débil sin romper códigos legacy válidos durante rollout.

Revisar obligatoriamente:

1. Nuevos tokens con >=128 bits efectivos de entropía.
2. Lookup determinista por SHA-256/HMAC/index o equivalente seguro; no bcrypt por scan para tokens nuevos.
3. TTL corto razonable para nuevos códigos.
4. Single-use real con protección contra carreras/concurrencia.
5. `SECURITY DEFINER` con `search_path` fijo donde corresponda.
6. Grants mínimos necesarios; revisar específicamente si `anon EXECUTE` en `admin_create_device_relink_code` es realmente imprescindible.
7. NO aceptar un cutoff legacy histórico fijo como `2026-08-19 15:00:00+00` si puede invalidar códigos emitidos entre esa fecha y el rollout real.
8. Diseñar la transición legacy usando una marca/cutoff derivado del rollout efectivo o un mecanismo equivalente robusto.
9. Incluir estrategia clara de rollback/compatibilidad.
10. No aplicar todavía la migración a Production/Supabase.

Los checks SQL deben demostrar, sin depender de Production:

- nuevo token creado y consumido una sola vez;
- token inválido rechazado;
- expirado rechazado;
- legacy válido de transición sigue funcionando según la estrategia elegida;
- token nuevo no depende de scan bcrypt;
- grants/search_path quedan como se pretende.

Commit separado sugerido:

`fix(pairing): harden pairing token lifecycle`

---

# Orden dentro del lote

Ejecutar A → B → C.

Cada bloque debe quedar en su propio commit y con sus tests estrechos. Si A o B falla por un problema arquitectónico inesperado, no seguir ciegamente al bloque siguiente: dejar evidencia y detenerse según `AI_WORKFLOW.md`.

No mezclar los tres cambios en un único commit.

## Tests / validación

Ejecutar las suites más estrechas relevantes por bloque y después, si los tres bloques pasan:

- `git diff --check`;
- compilación de módulos afectados;
- una compilación App Usuario final si los cambios impactan su grafo;
- NO repetir suites DAG/Chrome/Admin;
- NO prueba física;
- NO aplicar migraciones a Supabase.

Si ya existe evidencia válida que el cambio no afecta, no repetirla sin motivo.

## Resultado esperado

Si los tres bloques quedan correctos:

1. rama `review/p0-hardening-batch-01` con tres commits separados;
2. push;
3. abrir una única PR contra `preserve/local-main-2026-08-20`;
4. PR con sección por bloque y cierre estándar:
   - resumen;
   - archivos tocados;
   - comandos/tests;
   - resultados;
   - riesgos pendientes;
   - branch + commits;
   - migración: NO aplicada;
   - prueba física: no requerida;
5. NO mergear.

## Prohibiciones

- no tocar el worktree original sucio;
- no tocar `main`;
- no merge/rebase/reset/clean/stash sobre el repo original;
- no tocar Chrome Visual ni DAG;
- no resolver device-token scope todavía;
- no aplicar Supabase/Production;
- no instalar APK;
- no iniciar otro ticket.

## Handoff obligatorio

Reemplazar `docs/AI_CODEX_HANDOFF.md` en `coordination/ai-control` con:

- `P0-HARDENING-BATCH-01`;
- PASS / NEEDS-FIX / BLOCKED global;
- estado individual A/B/C;
- branch + 3 commits;
- PR;
- archivos tocados;
- tests/comandos exactos y resultado;
- cualquier desviación respecto de los candidatos preservados;
- riesgos restantes;
- confirmación de que ninguna migración fue aplicada;
- confirmación de que el worktree original quedó intacto.

Después: **DETENERSE**.

ChatGPT auditará el lote completo, pero podrá aprobar/rechazar cada commit por separado.

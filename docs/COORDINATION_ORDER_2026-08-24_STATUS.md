# Coordination order — 2026-08-24

Estado: DONE / docs-only / sin cambios funcionales.

Se preparó una limpieza documental aislada desde `main` en la rama:

`docs/coordination-order-2026-08-24`

Base compartida verificada:

`885663fdd9d03542db154650e7abcacec11523d8`

Cambios:

- `AGENTS.md` deja de tratar `docs/HANDOFF_ACTUAL.md` como verdad técnica vigente y pasa a Glosh Central + Git actual como fuentes canónicas.
- `START_HERE.md` abre con Glosh Central, estado Git actual y `docs/CURRENT_COORDINATION.md`.
- nuevo `docs/CURRENT_COORDINATION.md` documenta dónde leer coordinación, código/evidencia, trabajo paralelo y reglas de cierre sin hardcodear SHAs/frentes que envejecen.
- `docs/HANDOFF_ACTUAL.md` no se borró ni modificó: queda preservado como snapshot histórico con corte 2026-08-11.

Commits de la rama documental:

- `3cdfe4a2e4234e7a800f8d82199dc2985ab5270e` — AGENTS
- `6d5919c8484864b0ea63925779facfba7e4e085a` — START_HERE
- `594e4930d39b33fd832590cc901b354ee5083328` — CURRENT_COORDINATION

No se tocó Android, VPN, Chrome, GloshIA, Remote, Supabase, APK, Production ni worktrees locales de Codex.

No PR ni merge. La rama queda preparada para revisión/decisión posterior.

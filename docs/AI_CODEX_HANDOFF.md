# AI CODEX HANDOFF

## AI-AUTORUN-COST-HARDENING-05

- Fecha: 2026-08-20.
- Estado: **PASS**.
- PR: https://github.com/rajamimnehmad-sudo/control-parental-dev/pull/98
- Commit: `7cc320a7` (`perf(ai): minimize autorun token baseline`).
- Servicio: activo; último ticket deduplicado; sin ejecución nueva pendiente.

## Resultado

- Runner usa contexto `lean` aislado y efímero por defecto.
- El `CODEX_HOME` lean solo enlaza `~/.codex/auth.json`; no copia ni versiona secretos.
- Plugins, apps, browser, imágenes, multi-agent, skills y herramientas de artefactos quedan desactivados salvo `Perfil Codex: full` explícito.
- El ticket completo se incluye en el prompt; Codex no vuelve a cargar workflow, backlog ni handoffs.
- Routing: Luna/low, Terra/medium, Sol/high-xhigh.
- Smoke/medición corren fuera del repo con rama/HEAD/conteo Git precalculados.
- Fuente e instalación tienen SHA-256 idéntico: `87f4470a0df56dd19b8eb538e27db40ba7b26e8d90bc6951474d626a8554181a`.

## Gates y costo

- `bash -n` — **PASS**.
- Self-test: esfuerzo/modelo 6/6, CLI lean, parser de uso, deduplicación y single-flight — **PASS**.
- Smoke real único Luna/low sin herramientas: `input=10.521`, `output=10`, total `10.531`.
- Reducción contra smoke inicial ~42.500: **75,2%**.
- Reducción contra medición inflada 493.222: **97,9%**.
- Modelo Ollama local existente respondió sin créditos (`2.050` tokens locales), pero falló el uso correcto de herramientas; no se habilita para tickets reales.
- Coordinación que ChatGPT pueda resolver en GitHub no debe generar sesión Codex: costo objetivo **0 tokens**.

## Confirmaciones

- Cero cambios de producto, APK, Gradle, ADB, Production, Supabase o Vercel.
- Worktrees de producto intactos.
- OpenAI Docs recomienda prompts lean, herramientas relevantes y esfuerzo bajo para flujos sensibles a costo; esta configuración sigue esa guía.

Siguiente acción: ChatGPT audita PR #98 y evita publicar microtickets de coordinación. Codex se detiene.

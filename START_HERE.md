# START HERE

Antes de cualquier tarea leer:

- `docs/CODEX_RULES.md`
- `docs/CODEX_MAP.md`
- `docs/AREAS.md`
- `docs/DEV_FLOW.md`
- `docs/HANDOFF_ACTUAL.md`

Reglas de trabajo:

- No reanalizar arquitectura.
- Trabajar solo por tickets pequenos.

Antes de cualquier ticket:

1. Leer `START_HERE.md`.
2. Usar `docs/AREAS.md` para identificar el area exacta afectada.
3. Abrir solo los archivos necesarios de esa area.
4. No revisar todo el repo salvo que el ticket diga explicitamente: "revisar todo el repo".
5. No tocar areas no relacionadas.
6. Trabajar siempre en tickets chicos.
7. Primero diagnosticar la causa raiz.
8. Recien despues escribir codigo.
9. Modificar la menor cantidad posible de archivos.
10. Prioridad permanente:
    - ahorrar tokens;
    - reducir contexto;
    - evitar cambios innecesarios;
    - hacer fixes faciles de revisar;
    - mantener el repo limpio y escalable.

Regla de build/publicacion:

- Si solo cambian docs, no compilar.
- No incrementar `versionCode`.
- No publicar APK.
- No tocar Android.
- No tocar Supabase.

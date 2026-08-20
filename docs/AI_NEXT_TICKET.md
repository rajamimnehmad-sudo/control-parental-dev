# AI NEXT TICKET

## CHROME-VISUAL-CLOSURE-BATCH-04

**Tipo:** reconstrucción limpia + hardening + validación
**Prioridad:** crítica
**Responsable:** Codex
**Revisor:** ChatGPT / jefe técnico central

> Leer primero `docs/AI_WORKFLOW.md` en `coordination/ai-control`.

## Contexto validado por ChatGPT

La investigación oficial de Android confirma que la arquitectura elegida sigue siendo válida para API 34+: `AccessibilityService.takeScreenshotOfWindow()` está soportada para comprensión visual/ML y evita capturar el propio overlay de accesibilidad. Las ventanas `FLAG_SECURE` pueden devolver error específico y deben degradar de forma segura.

Estado preservado de Chrome Visual:

- base Android local aprobable: `preserve/local-main-2026-08-20` (`9e41c309`);
- snapshot Chrome Visual: `preserve/chrome-visual-2026-08-20` (`6a045f13`), 7 commits por delante;
- imágenes: GO controlado, todavía DEV-only;
- video: núcleo y gates automáticos verdes; gate físico FAIL por tormenta de eventos en A23;
- la causa de la tormenta fue corregida después del test físico y quedó cubierta por test determinista, pero NO revalidada en hardware;
- no hay commits posteriores a `6a045f13` en ese snapshot;
- GloshIA Visual R3.1 ya fue extraído a `gloshia-visual-core` y compartido con DAG;
- enfoque Chrome es reactivo: no prometer exposición cero;
- API 34+ ARM64 sigue siendo el objetivo de este lote; DAG continúa como fallback para lo no soportado.

Este ticket NO debe inventar una nueva arquitectura. Debe reconstruir, revisar y cerrar la existente.

## Rama / base

Crear:

`review/chrome-visual-closure-batch-04`

Base:

`preserve/local-main-2026-08-20`

Fuente de cambios:

`preserve/chrome-visual-2026-08-20`

NO trabajar sobre el worktree original sucio `work/chrome-visual`.

Reconstruir únicamente los cambios funcionales de Chrome Visual/GloshIA compartido necesarios. Excluir snapshots, documentación de coordinación histórica y cambios no funcionales ajenos al frente.

---

# Bloque A — Reconstrucción limpia y auditoría de contrato

Objetivo: obtener una rama auditable que contenga solo la implementación necesaria de Chrome Visual y el motor compartido.

Revisar y conservar como mínimo:

- `gloshia-visual-core` y paridad con DAG;
- captura de ventana Chrome por Accessibility;
- inspector/identidad de ventana;
- binding de resultados a ventana/página/captura/región/firma;
- overlays regionales no táctiles;
- fail-closed ante captura no disponible/identidad cambiada;
- política dinámica de imágenes;
- política temporal de video;
- hooks mínimos en `ProtectorAccessibilityService` y App Usuario DEV.

No copiar documentación/handoffs viejos salvo evidencia estrictamente necesaria para la PR.

Commit sugerido:

`refactor(chrome): rebuild visual filtering on clean base`

---

# Bloque B — Imágenes y web dinámica

Objetivo: cerrar el camino de imágenes estáticas/dinámicas con una experiencia razonable y sin reglas por sitio.

Validar/corregir:

1. primera carga sin hueco inicial inseguro;
2. scroll y lazy-load;
3. Google Images;
4. noticias/tienda o fixtures equivalentes;
5. mosaicos fallback acotados cuando Chrome no expone nodos de imagen;
6. regiones semánticas cuando sí existen;
7. caché efímero por firma sin reutilización stale;
8. rotación y cambios de geometría invalidan decisiones viejas;
9. teclado/insets no deben desplazar overlays de forma incorrecta;
10. no persistir/transmitir screenshots.

No añadir heurísticas por dominio, proveedor, codec o formato.

Commit sugerido:

`fix(chrome): harden dynamic image coverage`

---

# Bloque C — Video reactivo y tormenta de eventos

Objetivo: cerrar el fallo físico previo y mantener cobertura regional estable sin repintar toda la ventana en cada evento.

Conservar/corroborar la corrección ya implementada:

- baseline completo solo en primera carga, navegación o cambio real de ventana/geometría;
- rafagas ordinarias se agrupan;
- eventos normales disparan verificación incremental regional;
- no cancelar/recrear baseline ante cada `AccessibilityEvent`;
- `Block` y `Unavailable` mantienen región cubierta;
- recuperación requiere dos muestras `Allow` consecutivas;
- cambio de geometría invalida recuperación;
- resultado stale nunca modifica captura posterior.

Agregar o reforzar tests de event storm, seek/cambio visual rápido y segundo video sin reiniciar la app.

Commit sugerido:

`fix(chrome): stabilize reactive video filtering`

---

# Bloque D — Degradación segura / límites reales

Objetivo: que los límites conocidos tengan comportamiento explícito y no ambiguo.

Revisar:

- `ERROR_TAKE_SCREENSHOT_SECURE_WINDOW` / `FLAG_SECURE`;
- captura inválida/no disponible;
- ventana Chrome no identificable;
- API <34;
- arquitectura/ABI no soportada;
- multiventana o geometría ambigua.

Comportamiento esperado:

- no mostrar una falsa sensación de filtrado;
- mantener cobertura/fail-closed cuando técnicamente sea seguro hacerlo;
- cuando Chrome Visual no pueda garantizar operación, indicar/facilitar fallback a DAG según el contrato actual sin intentar controlar Chrome por APIs no oficiales;
- documentar exactamente qué casos quedan DEV-only o no soportados.

No implementar MediaProjection permanente, extensiones Chrome ni Device Owner para este frente.

Commit sugerido:

`fix(chrome): define safe visual fallback behavior`

---

# Gates automáticos antes de hardware

Ejecutar solo lo relevante:

- `:gloshia-visual-core:test` o tarea equivalente;
- `:feature-accessibility:testDebugUnitTest`;
- `:feature-accessibility:testReleaseUnitTest`;
- `:feature-accessibility:ktlintCheck`;
- `:app-user:lintDevDebug`;
- `:app-user:assembleDevDebug`;
- paridad DAG/GloshIA si el motor compartido fue tocado;
- `git diff --check`.

No repetir suites Admin/Super Admin/backend.

Si falla una capa ya validada por una causa nueva, corregir dentro del alcance. Si exige reescritura arquitectónica, dejar bloqueo y detenerse.

---

# Gate físico — NO ejecutar hasta aviso de ChatGPT

Codex debe preparar el APK y dejarlo listo, pero **NO enviarlo ni pedir prueba física todavía**.

Cuando los gates automáticos estén verdes y ChatGPT audite la PR, ChatGPT avisará al usuario para que encienda el S22 y entonces Codex enviará el APK por Taildrop.

La prueba física prevista será en S22 y debe cubrir, en una sola sesión útil:

- Chrome normal;
- imágenes + scroll + lazy-load;
- Google Images;
- video normal;
- seek;
- fullscreen entrar/salir;
- segundo video sin reiniciar;
- bloqueo y recuperación;
- rotación;
- teclado/insets;
- observar overlays stale/desplazados, cobertura completa repetida, latencia, crash/ANR y uso de memoria razonable.

No hacer micro-APKs intermedios para cada subcaso.

---

# Resultado esperado

1. rama `review/chrome-visual-closure-batch-04` limpia;
2. commits separados A/B/C/D cuando corresponda;
3. push;
4. PR contra `preserve/local-main-2026-08-20`;
5. NO mergear;
6. APK App Usuario DEV compilada y lista localmente, NO enviada aún;
7. estado final automático: PASS / NEEDS-FIX / BLOCKED;
8. Chrome Visual sigue DEV-only hasta gate físico posterior.

## Observaciones obligatorias

Codex debe anotar cualquier hallazgo sobre:

- rendimiento/memoria;
- eventos Accessibility inesperados;
- restricciones Chrome/Android;
- problemas de geometría/insets;
- paridad GloshIA/DAG;
- deuda que deba resolverse antes de producto.

No arreglar fuera de scope sin necesidad; describir y dejar que ChatGPT decida.

## Prohibiciones

- no tocar Production;
- no Supabase;
- no Super Admin;
- no main;
- no worktree original sucio;
- no Device Owner;
- no MediaProjection permanente;
- no extensiones Chrome;
- no envío Taildrop ni prueba física hasta orden de ChatGPT.

## Handoff obligatorio

Reemplazar `docs/AI_CODEX_HANDOFF.md` en `coordination/ai-control` con:

- `CHROME-VISUAL-CLOSURE-BATCH-04`;
- estado global y A/B/C/D;
- rama + commits;
- PR;
- archivos tocados;
- tests/comandos exactos;
- resultado de paridad GloshIA/DAG;
- ubicación/nombre del APK DEV preparado;
- observaciones técnicas;
- riesgos pendientes;
- confirmación de que no hubo prueba física ni Taildrop;
- confirmación de que worktree original/Production quedaron intactos.

Después: **DETENERSE**.
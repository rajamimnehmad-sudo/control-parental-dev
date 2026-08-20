# AI CODEX HANDOFF

## CHROME-VISUAL-CLOSURE-BATCH-04

- Fecha: 2026-08-20.
- Estado automático global: **PASS**.
- Rama: `review/chrome-visual-closure-batch-04`.
- Base: `preserve/local-main-2026-08-20` (`9e41c309`).
- HEAD: `88ca10f605ea297c0e303bc35e04ab45937ec636`.
- PR draft: https://github.com/rajamimnehmad-sudo/control-parental-dev/pull/97
- Chrome Visual continúa **DEV-only** hasta el gate físico posterior.

## Estado A / B / C / D

- **A — PASS:** reconstrucción limpia sin snapshots/documentación histórica. `gloshia-visual-core` es dueño del modelo R3.1, SHA, preprocessing, política y ONNX Runtime 1.27.0; DAG consume ese motor. Se conservaron probe, captura de ventana, identidad y hooks DEV mínimos.
- **B — PASS automático:** regiones semánticas + mosaicos bounded, caché efímera por firma, scroll/lazy-load por cambios visuales, invalidación por página/ventana/geometría, overlays no táctiles e insets de teclado. Sin reglas por sitio ni persistencia/transmisión de screenshots.
- **C — PASS automático:** tormentas ordinarias se agrupan; baseline completo solo ante página/ventana/geometría nuevos; verificación regional a 500–1.000 ms; `Block`/`Unavailable` cubren y recuperación exige dos `Allow`. Los gates detectaron y corrigieron que seek/cambio visual debía pre-cubrir antes de la nueva inferencia. Hay regresiones para event storm, seek y segundo video en la misma ventana.
- **D — PASS automático:** política explícita para API <34, proceso no ARM64, feature DEV-only, motor no disponible, `FLAG_SECURE`, captura fallida, geometría ambigua y sobrecarga. Mantiene cobertura existente cuando es seguro y registra `fallback=dag_required` sin usar APIs no oficiales.

## Commits

- `02aa2d02` — `refactor(chrome): rebuild visual filtering on clean base`.
- `b4520ee8` — `fix(chrome): harden dynamic image coverage`.
- `7005e6c9` — `fix(chrome): stabilize reactive video filtering`.
- `13da78c3` — `fix(chrome): precover rapid visual changes`.
- `8dc60ba8` — `fix(chrome): define safe visual fallback behavior`.
- `88ca10f6` — `test(gloshia): keep DAG parity gate lint-clean`.

## Archivos tocados

- `gloshia-visual-core/`: artefacto, modelo R3.1, analyzer, preprocessing, política y pruebas doradas.
- `app-dag-browser/`: Gradle/settings, adaptadores del motor compartido, contratos y test instrumentado de paridad.
- `feature-accessibility/.../chromevisual/`: captura, inspector, identidad, regiones, overlay, políticas dinámica/video/capacidad, analyzer y tests.
- `feature-accessibility/.../ProtectorAccessibilityService.kt`: hooks de ciclo de vida/eventos.
- `app-user/src/dev/res/`: capability de Accessibility y feature gate DEV.
- `settings.gradle.kts` y `feature-accessibility/build.gradle.kts`: módulo/dependencia compartida.

## Tests y comandos exactos

- `./gradlew :gloshia-visual-core:test :feature-accessibility:testDebugUnitTest :feature-accessibility:testReleaseUnitTest :feature-accessibility:ktlintCheck :app-user:lintDevDebug :app-user:assembleDevDebug` — **PASS**, 958 tareas; 107 tests de Accessibility por variante.
- `scripts/dag_gradle.sh testDevDebugUnitTest ktlintCheck lintDevDebug assembleDevDebug assembleDevDebugAndroidTest` — **PASS**, 147 tareas. Compila la prueba instrumentada de inferencia DAG vs shared core; no la ejecuta porque hardware está prohibido en este ticket.
- `git diff --check` — **PASS**.

## Paridad GloshIA / DAG

- Modelo oficial y SHA R3.1: idénticos (`c8b64af8092d3718c58736a511c996d0d443dacf3eaa74620b1e5af439a3cd48`).
- Tensor NCHW: bit-exacto contra la referencia DAG congelada.
- Thresholds, regiones, acción, reason, probabilidad y basis: pruebas doradas idénticas.
- ONNX Runtime Android: `1.27.0`.
- App Usuario conserva ARM32 general; Chrome Visual queda explícitamente deshabilitado fuera de proceso 64-bit. DAG DEV sigue ARM64.

## APK preparada

- App Usuario DEV: `app-user/build/outputs/apk/dev/debug/app-user-dev-debug.apk`.
- Tamaño: 60 MiB.
- SHA-256: `e412dea28859f52743151bffd8a66256bdb23e7dece4eee73437169b9ca1c536`.
- También compiló DAG DEV como gate de paridad: `app-dag-browser/build/outputs/apk/dev/debug/DagBrowser-dev-debug.apk` (`0f4ade03b5e19bfe1e370b93cfa13406ca3582e6a1759d6b987a6312a2465f2f`).
- Ninguna APK fue instalada, enviada ni publicada.

## Observaciones técnicas y riesgos

- Chrome Visual es reactivo: no promete exposición cero entre captura, decisión y cobertura. DAG sigue siendo fallback fuerte.
- `FLAG_SECURE`, API <34, no ARM64, captura/ventana/geometría no confiables y sobrecarga requieren DAG/fail-closed.
- Memoria está acotada por un frame temporal, caché de 128 decisiones, 400 nodos máximos, 8 mosaicos fallback y 8 regiones dinámicas; falta medir CPU/RAM/latencia real en S22.
- Accessibility puede emitir ráfagas; ahora solo la primera carga/navegación/cambio real crea baseline. Eventos ordinarios disparan verificación incremental.
- Rotación/viewport invalida autoridad vieja; teclado recorta overlays por inset. Falta validación física de multiventana e insets OEM.
- `ChromeVisualController.kt` queda exactamente en 500 líneas. Sigue unido porque coordina un único pipeline de autoridad/captura/análisis/presentación; no debe agregársele otra responsabilidad antes de separar coordinación y ejecución.
- Pendiente físico único: S22 con imágenes, scroll/lazy-load, Google Images, video, seek, fullscreen, segundo video, bloqueo/recuperación, rotación y teclado, después de auditoría de ChatGPT.

## Confirmaciones

- No hubo prueba física, ADB, Taildrop ni envío de APK.
- No hubo merge.
- Production, Supabase, Super Admin, Device Owner, MediaProjection permanente y extensiones Chrome quedaron intactos.
- El worktree original `work/chrome-visual` continúa en `6a045f13` con exactamente sus 6 modificados + 4 untracked preexistentes; no fue limpiado, reseteado, stasheado ni editado.

Siguiente acción: ChatGPT debe auditar PR #97 y decidir si habilita el gate físico S22. Codex se detiene.

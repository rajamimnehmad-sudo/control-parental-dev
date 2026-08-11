# Inventario de limpieza local DAG y GloshIA

Fecha: 2026-08-11. Este inventario no autoriza borrados. El corpus adjudicado,
los manifiestos, splits, hashes, informes y artefactos necesarios para reproducir
R3.1 se preservan aunque estén ignorados por Git.

## Regenerable

- `app-dag-browser/build/`: aproximadamente 2,3 GB; salidas de Gradle.
- `tools/gloshia_lab/node_modules/`: aproximadamente 159 MB; dependencias npm.
- `.codex-tmp/gloshia-train-venv/`: aproximadamente 779 MB; entorno Python.
- `.codex-tmp/gloshia-lab-venv/`: aproximadamente 68 MB; entorno Python.
- caches `__pycache__`, `.gradle` y `.kotlin` generadas localmente.

Estos elementos no forman parte del APK ni afectan el rendimiento del telefono.
Se pueden reconstruir, pero su borrado requiere confirmacion independiente.

## Revisar antes de borrar

`.codex-tmp/` ocupa aproximadamente 5,4 GB y mezcla corpus, informes y
checkpoints rechazados. Los directorios mayores son:

- `gloshia-r3-candidate-20260803/`: 737 MB;
- `archive/`: 666 MB;
- dos ensayos R4 MPS: 354 MB cada uno;
- `gloshia-r3-train-28-20260804/`: 313 MB;
- `gloshia-r3-round30-binary-candidate-20260805/`: 253 MB;
- `gloshia-r2-hard-negative-repair-20260802-v3/`: 236 MB;
- `gloshia-lab-current-1000/`: 223 MB;
- `gloshia-r4-thumbnail-repair-20260809/`: 216 MB;
- artefactos teacher GPU duplicados como carpeta y tar: 212/207 MB.

Antes de limpiar cada experimento se debe conservar un manifiesto con estado
`official`, `historical`, `rejected` o `regenerable`, hash del artefacto y ruta
del informe que lo explica. No se borra `.codex-tmp` en bloque.

## Git y worktrees

Dos worktrees temporales de UI aparecen como `prunable` y sus ramas ya están
integradas. El worktree `content-filter-dag-browser-v3` también está integrado
pero vive fuera del checkout canónico. Podrán retirarse en un ticket de higiene
Git separado; el único checkout operativo seguirá siendo
`/Users/yejielnehmad/Developer/content-filter`.

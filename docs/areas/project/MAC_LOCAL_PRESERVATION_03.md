# MAC-LOCAL-PRESERVATION-03

Fecha: 2026-08-26

Resultado: **TECHNICAL PRESERVATION COMPLETE — PENDING CHATGPT REVIEW**

Base aprobada: `review/glosh-convergence-audit-01` @
`be8bb9aea058392923f382b0a0e37dd5ca1184e1`

## 1. Alcance y criterio

Este lote preserva el estado local relevante antes de cualquier limpieza de la
Mac. No promueve ramas a canonicas, no integra producto y no autoriza borrar,
podar, mover ni reescribir nada.

La preservacion tiene tres capas:

1. refs Git locales que vuelven alcanzable todo objeto previamente suelto;
2. bundle Git privado y un unico ref remoto de codigo revisado como publicable;
3. snapshots privados COW con manifests y hashes para working tree,
   configuracion local, APKs y `.codex-tmp`.

La raiz privada es:

`~/Glosh-Preservation/MAC-LOCAL-PRESERVATION-03/`

Tiene permisos de acceso exclusivos del usuario. Bundles, corpus, modelos,
medios, configuracion local y manifests con rutas privadas no se publicaron en
GitHub.

## 2. Conteos recalculados

| Inventario inicial | Conteo |
| --- | ---: |
| Ramas locales | 65 |
| Ramas sin upstream | 37 |
| Tips locales no contenidos en refs remotos | 11 |
| Commits alcanzables no contenidos exactamente en `origin` | 88 |
| De esos commits: patch unico / patch equivalente | 31 / 57 |
| Commits inicialmente unreachable | 56 |
| De esos commits: patch unico / patch equivalente | 30 / 26 |
| Registros de worktree | 58 |
| Worktrees con directorio / sin directorio | 33 / 25 |
| Worktrees dirty | 1 |
| Entradas dirty/untracked del checkout principal | 14 |

El mapping exacto de patch-id queda en manifests privados. Un patch equivalente
tambien fue preservado por SHA exacto; equivalencia de patch no se uso como
permiso para descartarlo.

## 3. Preservacion Git

Se crearon 58 refs bajo
`refs/preserve/mac-local-preservation-03/`:

- 12 tips minimos para los componentes alcanzables locales;
- 44 tips minimos que cubren por ancestry los 56 commits inicialmente
  unreachable;
- 1 ref sintetico que retiene 1.586 trees/blobs que permanecian unreachable
  despues de cubrir los commits;
- 1 ref sintetico de metadatos para respaldo remoto de los 12 componentes de
  codigo publicables.

Tips importantes cubiertos explicitamente:

| Item local | SHA preservado |
| --- | --- |
| fork local de `main` | `6ae216fdced47ee76aed52d642edfe7e9c3f8852` |
| diagnostico R1 | `88804188c9e100f1f92165f95bd5a7308b43d6e4` |
| Device Owner installer | `264f3e8906e495df809d533db598e7d2417a6f5a` |
| Remote notification | `aa92441928a8235a63581bcc8580a5537884a9f2` |
| Remote simple | `7347235f812e071512d5bdb5758f727f1bf5e4ba` |
| Chrome functional | `bf5f6835e70f04137df65a90fac4e604b53f110d` |
| Chrome closure | `2ce17b312ae0ef2278c74c78e5a2f61aae41e62f` |
| UI/APK test | `f650bf3a4259d6c6e5c6aca6ecc79d8f31afe685` |
| DAG unfiltered | `3419fbc9a012b0df87acf2b32e1be935c3d2343e` |
| DAG stability | `420f3af7eb3c746c8164391272ef82f95a79a42d` |
| Super Admin candidate | `851765fc372535b4e693d2fff380760287a915c5` |
| Super Admin/Web historical deploy | `b07ea7f0e0ad39f2b986a544ded01021cbb6e6e0` |

La historia Super Admin/Web antigua queda preservada sin declararla vigente.

### Bundle privado definitivo

- Archivo: `content-filter-preservation-final-v2.bundle`
- Bytes: `75,146,089`
- SHA-256:
  `bf104c848ee89831e54f6f83b43f6a6e6cebd66323c5ea0a1360849db9f61ec9`
- `git bundle verify`: PASS, historia completa.
- Las 58 refs de preservacion resuelven y aparecen en `bundle list-heads`.
- `git fsck --no-reflogs --unreachable`: cero resultados al cierre.

Existe ademas un clon COW privado de `.git` como segunda red local. El bundle
anterior es el artefacto Git autoritativo de recuperacion.

### Respaldo remoto minimo

Antes del push se inspeccionaron los 276 blobs exclusivos de los 12 componentes:
3.498.299 bytes, blob maximo 307.168 bytes, cero patrones de secretos, cero
rutas sensibles y cero blobs mayores de 10 MiB.

Un unico ref remoto conserva los 88 commits alcanzables exactos sin promover
ninguna rama de producto:

- `origin/preserve/mac-local-preservation-03-safe-code`
- HEAD `f6e32dab2da6e2faf5f56f7e9bd0bb694565a592`

Los commits inicialmente unreachable y los objetos huérfanos permanecen solo
en la preservacion privada; no se publicaron por prudencia.

## 4. Dirty, untracked, ignored y APKs

El checkout principal tenia 8 archivos tracked modificados y 6 untracked. Se
guardaron:

- status porcelain v2 completo;
- patch staged vacio y patch unstaged binary-capable;
- copia COW exacta de cada archivo tracked modificado y untracked;
- listado, tamaño y SHA-256 individual;
- verificacion origen contra copia.

Se agregaron al mismo snapshot 5 APKs locales y 11 archivos ignorados
sensibles/ambiguos (configuracion local y metadatos de tooling). En total el
snapshot contiene 30 archivos y 308.832.665 bytes logicos.

- SHA-256 del patch unstaged:
  `ae716344236991149f9e606a45e04ef2fdf5c5db03f716e7eed03565ff335ef5`
- SHA-256 del manifest del snapshot:
  `bf9376d39d3847b949bf2c23a72710445bc5cdf247f9a8d3d06ad0cc9b74c2cd`

No se crearon commits funcionales artificiales y el working tree original no
fue modificado por esta preservacion.

## 5. `.codex-tmp`, IA y evidencia

Se hizo una copia completa APFS clone/COW de `.codex-tmp`; el original permanece
intacto. El manifest privado registra ruta relativa, tipo, tamaño, SHA-256,
clasificacion, sensibilidad, origen, destino y regenerabilidad.

| Clasificacion | Entradas | Bytes logicos |
| --- | ---: | ---: |
| PRESERVE | 12.894 | 4.816.913.059 |
| REGENERABLE | 30.971 | 803.123.629 |
| AMBIGUOUS | 2 | 217.263.373 |
| **Total** | **43.867** | **5.837.300.061** |

El total contiene 43.698 archivos y 169 symlinks. Los dos items ambiguos tambien
quedaron copiados y quedan excluidos de cleanup hasta decision explicita.

- SHA-256 del manifest completo:
  `82eb36458ccf043f0c69a5d889074ca6d35e68d9b59ce22ac27c433953747c53`
- 31 muestras deterministas, incluyendo los archivos mayores y representantes
  de cada clase, compararon hash origen/destino: PASS.

La clase `PRESERVE` incluye corpus/datasets, labels/reviews, calibraciones,
modelos/candidatos, tensores/crops, evidencia visual, scripts unicos y resultados
de training/benchmark. La clase `REGENERABLE` cubre principalmente entornos y
caches de dependencias; conservarla ahora no la convierte en permanente.

## 6. Worktrees

| Clasificacion | Conteo | Cobertura |
| --- | ---: | --- |
| SAFE_REMOTE | 27 | ref remoto + bundle |
| LOCAL_UNPUBLISHED | 5 | preserve ref + bundle + respaldo remoto seguro |
| LOCAL_DIRTY | 1 | bundle + snapshot exacto |
| MISSING_REGISTRATION | 25 | metadata y HEAD preservados en bundle; solo identificados |

El manifest privado registra para cada uno path, rama, HEAD, dirty, containment
remoto, metodo de preservacion y verificacion. No se ejecuto `worktree prune` ni
se removio directorio, registro o rama.

## 7. Coverage de recuperabilidad

| Item | Clase | Metodo | Remoto | Verificado | Safe to clean later* |
| --- | --- | --- | --- | --- | --- |
| 31 commits alcanzables patch-unicos | LOCAL UNIQUE | refs + bundle + meta remoto | Si | Si | Si |
| 57 commits alcanzables patch-equivalentes | LOCAL EXACT | refs + mapping + bundle + meta remoto | Si | Si | Si |
| 30 commits unreachable patch-unicos | LOCAL UNIQUE | refs minimas + bundle | No | Si | Si |
| 26 commits unreachable patch-equivalentes | LOCAL EXACT | refs + mapping + bundle | No | Si | Si |
| 1.586 objetos no-commit inicialmente unreachable | UNIQUE/AMBIGUOUS | tree/ref sintetico + bundle | No | Si | Si |
| 8 tracked + 6 untracked | UNIQUE/AMBIGUOUS | patch + snapshot exacto | No | Si | Si |
| 5 APKs + 11 ignored sensibles/ambiguos | AMBIGUOUS | snapshot exacto | No | Si | Si |
| 43.867 entradas `.codex-tmp` | MIXTA | COW + manifest + hashes | No | Si | Si |
| 58 registros worktree | MIXTA | refs/bundle/snapshot | Parcial | Si | Si |

\* `Safe to clean later` significa solamente que existe un mecanismo de
recuperacion verificado. No autoriza cleanup ni decide qué se debe borrar.

## 8. Exclusiones obligatorias para un cleanup posterior

Hasta que ChatGPT revise este lote y exista un ticket de limpieza separado, no
se debe tocar:

- la raiz privada de preservacion;
- `refs/preserve/mac-local-preservation-03/*`;
- el working tree dirty original;
- `.codex-tmp` original ni su copia COW;
- configuracion local, metadata Supabase temporal ni APKs preservados;
- ramas, worktrees, objetos o registros de worktree;
- commits patch-equivalentes antes de consultar su mapping exacto privado.

Tampoco se autoriza `reset`, `stash`, `rebase`, `clean`, `prune`, `gc`
destructivo, borrado de ramas/worktrees/builds/evidencia/data ni movimiento de
trabajo desconocido.

## 9. Riesgos y residuales

- El bundle y los snapshots de datos privados estan fuera del repo pero en el
  mismo volumen fisico de la Mac. Protegen contra cleanup accidental, no contra
  perdida total del disco. Un backup privado off-device es una mejora posterior,
  no un requisito para planificar cleanup local.
- Solo el codigo alcanzable que paso el scan seguro tiene respaldo remoto. El
  resto permanece deliberadamente privado.
- Los items clasificados `AMBIGUOUS` estan preservados y excluidos de cleanup;
  requieren decision, no inferencia automatica.
- Los 25 registros sin directorio no fueron reparados ni podados.

## 10. Decision

Todo material identificado como `LOCAL UNIQUE` o `AMBIGUOUS` tiene al menos un
mecanismo recuperable y verificado. El estado es apto para **cleanup planning**
despues de la review de ChatGPT, pero este documento no autoriza ejecutar esa
limpieza.

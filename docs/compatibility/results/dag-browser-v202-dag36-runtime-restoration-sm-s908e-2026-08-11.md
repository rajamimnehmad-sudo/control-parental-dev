# DAG Browser 202 — restauración `dag-36` en SM-S908E

Fecha: 2026-08-11
Estado: validación local y física correcta; sin publicación ni push

## Alcance

DAG 202 restaura en Android la política canónica que GloshIA Lab ya ejecutaba:

- señal completa `>= 0,40`: bloqueo inmediato;
- señal ordinaria `0,30–0,40`: cuatro cuadrantes, cualquiera `>= 0,45` bloquea;
- panorama: una región `>= 0,70` o dos regiones `>= 0,50` bloquean.

No cambia R3.1, su ONNX, hash, normalización, umbrales, política visual, hilos,
colas, scheduler ni barrera web. No agrega excepciones por sitio, URL, dominio,
tamaño o dispositivo.

## Artefacto

- paquete: `com.contentfilter.dagbrowser.dev`;
- versionCode: `202`;
- versionName: `0.70.06-dev`;
- APK: `app-dag-browser/build/outputs/apk/dev/debug/DagBrowser-dev-debug.apk`;
- tamaño: aproximadamente 123 MiB;
- SHA-256:
  `0a18f4c156b15bce5236a9982180c9b437420ca744b4a294209db881ed982cdd`.

Se instaló con `adb install --no-streaming -r` sobre DAG 199. `firstInstallTime`
permaneció en `2026-08-06 02:31:35`, confirmando que no se borraron perfil ni
datos. Dispositivo: `SM-S908E`, Android arm64.

## Puertas locales

- `testDevDebugUnitTest`: correcto;
- `testLabDebugUnitTest`: correcto;
- `testDiagnosticDebugUnitTest`: correcto;
- `ktlintCheck`: correcto;
- `lintVitalDevDebug`: correcto;
- `testDagProtectionJs`: correcto, 25/25;
- `assembleDevDebug`: correcto;
- contrato GloshIA Lab: correcto, 5/5;
- sintaxis Python y Node del Lab: correcta.

## Matriz física

Cada corrida usa `tools/dag_perf_lab/run_live_site.sh`, sin borrar caché,
perfil ni Logcat. `page_visible` y cola son milisegundos; PSS está en KiB.

| Página | visible | raster | cola p95 | frames lentos | PSS | crash/ANR |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Google Imágenes | 1.411 | 123 | 32 | 2/40 | 332.608 | 0 |
| Frávega, frío | 3.886 | 297 | 38 | 2/43 | 350.698 | 0 |
| Frávega, caliente | 1.326 | 10 | 1 | 1/25 | 366.203 | 0 |
| Mimo | 1.052 | 110 | 45 | 2/44 | 358.362 | 0 |
| Cheeky | 2.706 | 137 | 31 | 4/75 | 352.839 | 0 |

### Google Imágenes

Se registraron 113 permisos y 10 bloqueos. Siete señales completas entre
`0,40` y `0,95` bloquearon como `full_threshold` y una `>=0,95` como
`full_strong`, todas con una sola inferencia. La captura final muestra los
bloqueos como tarjetas grises neutrales, sin píxeles negros ni contenido
rechazado. La página respondió durante cinco desplazamientos.

### Frávega

La corrida fría procesó 297 raster: 277 permisos y 20 bloqueos. Dieciséis
bloqueos fueron `full_threshold` y uno `uncertain_regional`. El modal propio de
ubicación interceptó los gestos automáticos, por lo que esa corrida no se usa
para certificar scroll. Después de cerrarlo explícitamente, seis gestos
manuales recorrieron el documento desde los carruseles hasta el pie sin
congelamiento; las imágenes de producto permitidas permanecieron visibles.
El modal reaparece al recargar, incluso en caliente. No se atribuye a DAG.

### Mimo

Se registraron 89 permisos y 21 bloqueos; los bloqueos sin inferencia
corresponden a formatos cerrados, no a saturación del analizador. Seis gestos
llegaron al pie. Desde esa posición se abrió el menú: categorías, accesos y
botón de cierre aparecieron completos. Esto reproduce y supera el caso
histórico en que el menú sólo respondía con fluidez arriba de la página.

### Cheeky

Se registraron 107 permisos y 30 bloqueos. Catorce fueron `full_threshold` y
dos `uncertain_regional`; el resto falló cerrado antes de una decisión de
modelo, principalmente por formatos no admitidos. Seis gestos llegaron al pie
sin bloqueo de la interfaz.

## Resultado y límites

La causa corregida era real: Android permitía que regiones débiles vetaran una
señal completa ya insegura y gastaba hasta cinco inferencias en esos casos. En
la prueba física, señales completas de `0,432` a `0,864` terminaron ahora con
una inferencia y bloqueo canónico. Las colas p95 quedaron entre 1 y 45 ms,
muy por debajo del timeout nativo de 2.250 ms, y no apareció crash ni ANR.

La matriz certifica carga, scroll vertical, presentación neutral, decisiones y
menú de Mimo después del scroll. No es un A/B determinista de carrusel
horizontal contra Chrome: los sitios vivos cambian y Frávega interpone su modal.
Ese benchmark comparativo, si se requiere, debe ser un ticket de rendimiento
separado y no modificar esta restauración de seguridad.

Evidencia cruda privada:
`.codex-tmp/dag-perf-lab/live-runs/*-dag202-*`.

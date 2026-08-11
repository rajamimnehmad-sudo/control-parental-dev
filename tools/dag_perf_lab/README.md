# Laboratorio local de rendimiento DAG

Este laboratorio reemplaza sitios vivos por una página HTTPS determinista y
`no-store`. Ejercita texto, raster iniciales, fondos CSS, SVG funcional,
cambio de `src` y una cuadrícula lazy. Sus imágenes son sintéticas: los raster
`safe` no representan personas y la sonda `filter-probe` no es explícita ni
proviene de terceros. El modelo vigente (SHA-256 terminado en `a9ee`) la filtra
con score local `0,487243`; si un modelo futuro cambia esa decisión, el cambio
queda expuesto como resultado del laboratorio y no se falsea el nombre del
resultado medido.

## Límite HTTPS importante

DAG acepta únicamente navegación superior HTTPS. El servidor genera una hoja
autofirmada para `localhost` en `.codex-tmp/dag-perf-lab/tls`, fuera de Git, y
`adb reverse` mantiene todo el trafico entre el telefono y la Mac. Esa hoja **no es
una CA**, no se instala en Android y el APK no relaja TLS.

Después de borrar el perfil DEV, Gecko puede mostrar su advertencia de
certificado. Sólo si ofrece una excepción explícita se acepta una vez dentro del
perfil DAG y se repite la medición. Si no la ofrece, el laboratorio conserva la
evidencia y se detiene: no se instala una CA, no se cambia Android y no se
desactiva validación TLS. El runner detecta la ausencia de eventos del fixture y
no toma captura de pantalla en ese caso.

## Ejecución

Con un Android moderno dedicado conectado:

```bash
tools/dag_perf_lab/run_a23_fixture.sh --serial R58T34V31AE
```

El nombre del script se conserva por compatibilidad con la primera matriz, pero
el runner no contiene una politica del A23. El serial es obligatorio y nunca se
elige un telefono automaticamente. Acepta cualquier equipo que cumpla el APK
directo vigente: Android API 29+ y `arm64-v8a`. Para una comparacion repetible se
puede fijar el modelo exacto:

```bash
tools/dag_perf_lab/run_a23_fixture.sh \
  --serial R58T34V31AE \
  --expected-model SM-A235M
```

El runner:

- valida Android API 29+, ABI `arm64-v8a` y que DAG DEV este instalado;
- inicia sólo el servidor loopback y su propio `adb reverse`;
- detiene/inicia únicamente DAG (usar `--warm` para evitar el `force-stop`);
- no borra perfil, caché ni Logcat;
- no toca Chrome, roles, ajustes, certificados del sistema ni otras apps;
- recoge `am start -W`, `gfxinfo`, `framestats`, `meminfo`, estado térmico,
  `exit-info`, eventos controlados y logs privados `DagPerformance` /
  `DagMediaTransport`;
- delimita Logcat desde la hora del propio telefono y un marcador unico del run, sin
  vaciar el buffer; los crashes/ANR se calculan por diferencia de `exit-info`
  antes/después y, cuando existe timestamp numérico, sólo desde el inicio;
- guarda todo fuera de Git en `.codex-tmp/dag-perf-lab/runs/`.

La variante opcional `?inline=1` agrega cuatro casos controlados `data:`/`blob:`
(dos seguros y dos de filtro) obtenidos como `application/octet-stream`, de modo
que la medicion comprueba el gate inline y no reutiliza una decision previa del
filtro de respuestas HTTP.

Cada ejecución abre como máximo una pestaña nueva. Para una batería de muestras,
cerrar las pestañas de laboratorio entre corridas o preparar el perfil DAG fuera
del runner; el script nunca borra datos por sí solo.

Opciones útiles:

```bash
tools/dag_perf_lab/run_a23_fixture.sh \
  --serial R58T34V31AE \
  --settle 30 \
  --swipes 4
```

`summary.json` contiene las tres señales DAG, percentiles de cada etapa del
pipeline, resultados cliente, enlaces de presentación, cuadros tardíos, PSS/RSS
y las marcas de estabilidad emitidas por la página. Los archivos crudos siguen
siendo la evidencia principal.

## Validación local sin teléfono

```bash
python3 -m py_compile \
  tools/dag_perf_lab/fixture_server.py \
  tools/dag_perf_lab/summarize_run.py
bash -n tools/dag_perf_lab/run_a23_fixture.sh
```

## Matriz sobre sitios HTTPS vivos

Después de intentar el fixture, una URL publica se mide sin reutilizar comandos
manuales:

```bash
tools/dag_perf_lab/run_live_site.sh \
  --serial R58T34V31AE \
  --url https://www.example.com/ \
  --label example \
  --capture-screen
```

El runner exige serial, URL y etiqueta explicitos. No contiene dominios
especiales, no borra datos y produce el mismo `summary.json` que el fixture.
Una pagina viva puede cambiar entre corridas; por eso complementa al fixture y
no lo reemplaza como comparacion determinista.

### Fixture HTTP del harness aislado

GeckoView no expone en el runtime productivo una excepción para certificados
HTTPS autofirmados. Para conservar TLS estricto en DAG DEV, el laboratorio tiene
un flavor separado `lab` que solo permite `http://localhost/fixture/` y sirve el
fixture por HTTP mediante `adb reverse`. No se instala una CA, no se desactiva
TLS en DAG DEV y el APK `lab` nunca es un artefacto de publicación.

Construcción y ejecución:

```bash
./gradlew assembleLabDebug
tools/dag_perf_lab/run_a23_fixture.sh \
  --serial SERIAL --lab --expected-model SM-S908E
```

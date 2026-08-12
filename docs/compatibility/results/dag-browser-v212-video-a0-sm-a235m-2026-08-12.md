# DAG Browser 212 — gate fisico DAG-VIDEO-01A0 en SM-A235M

Fecha: 2026-08-12
Dispositivo: Samsung SM-A235M (`R58T34V31AE`), USB
Variante probada: Diagnostic 11 (`0.70.16-diagnostic`)
Extension: `2.0.15`

## Alcance

Se valido solamente el transporte cubierto de `DAG-VIDEO-01A0`. No se conecto
GloshIA R3.1, no se habilito video de red y no se modificaron modelo, umbral,
politica visual, corpus ni publicidad.

La fixture es interna, sintetica y sin red. Dibuja cuatro cuadrantes conocidos
en un `canvas`, produce un `MediaStream` mudo y lo asigna a un unico `video`.
Android confirma una cobertura opaca durante dos cuadros antes de permitir el
decode, captura solo la region visible y recicla el bitmap `224x224`.

## Hallazgo y correccion durante el gate

La primera corrida horizontal con scroll fallo cerrado por dos carreras:

- durante el acuse de cobertura podia abrirse una ventana breve para un segundo
  `cover_requested`;
- un gesto entre el acuse y `PixelCopy` podia dejar obsoleto el rectangulo.

No se mostro el marcador: la cobertura permanecio activa y el patron invalido
se rechazo. La implementacion final conserva ocupado el handshake hasta que la
fixture queda autorizada, espera 150 ms de quietud del viewport, mantiene una
sola captura en vuelo y permite una unica recuperacion acotada. Para recortes
parciales, la prueba calcula el cuadrante esperado segun la fraccion visible en
vez de exigir siempre el cuadro completo.

## Resultado fisico final

- Vertical: tres `fixture_pattern_ok`, `native_ms` 19, 24 y 8.
- Horizontal con scroll: una sola secuencia `cover_requested` / `cover_armed` y
  tres `fixture_pattern_ok`, `native_ms` 9, 15 y 11.
- No hubo `AndroidRuntime` error, crash, ANR, resultado obsoleto aceptado ni
  segunda captura concurrente.
- La jerarquia fisica confirmo que `video_lab_overlay` cubria todo el contenido
  de Gecko mientras se realizaban las capturas.
- Al volver atras se registro `retired reason=navigation_started`; las vistas
  bajaron de 151 a 39 y el Java heap de aproximadamente 16,9 MiB a 6 MiB. Gecko
  conservo su working set caliente, como corresponde; el bitmap regional no se
  retuvo ni persistio.
- La orientacion automatica se restauro al valor inicial (`accelerometer=1`,
  `user_rotation=0`) y se borraron los XML temporales del dispositivo.

Resultado: `GO` para cerrar `DAG-VIDEO-01A0`. Esto no autoriza `01A1`, no
habilita sitios reales y no constituye el gate de rendimiento R3.1 de `01A2`.

## Validacion automatizada final

Ejecutada exclusivamente mediante el proyecto Gradle aislado:

```text
scripts/dag_gradle.sh testDagProtectionJs testDevDebugUnitTest
  testDiagnosticDebugUnitTest ktlintCheck lintDevDebug lintDiagnosticDebug
  assembleDevDebug assembleDiagnosticDebug
```

Resultado:

- JS: 35/35;
- unitarios DEV y Diagnostic: correctos;
- Ktlint: correcto;
- Lint DEV y Diagnostic: correcto;
- ambos APK: correctos.

## Artefactos locales

- DAG normal 212 (`0.70.16-dev`):
  `d149fb02b77670ea4c5aec509d27de46bb4a61f541fc4cbc6b69e475e6378e34`.
- DAG Diagnostic 11 (`0.70.16-diagnostic`):
  `e0de4ba5ead2250ffd6fec39d4d295508328d30b3cdba02fcc1eead8b4541e07`.
- Ambos miden aproximadamente 116 MiB y permanecen locales; no hubo push ni
  publicacion remota.

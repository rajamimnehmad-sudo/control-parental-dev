# DAG Browser 213 — gate fisico DAG-VIDEO-01A1 en SM-A235M

Fecha: 2026-08-12
Dispositivo: Samsung SM-A235M (`R58T34V31AE`), Android 14, USB
Variante probada: Diagnostic 12 (`0.70.17-diagnostic`)
Extension: `2.0.15`

## Alcance

Se conecto el transporte cubierto de `01A0` con la autoridad oficial GloshIA
Visual R3.1 solamente en la fixture interna Diagnostic. No se habilito video de
red, no se modificaron modelo, pesos, umbral `0,40`, politica visual, corpus,
publicidad ni barrera de DAG normal.

La ruta de fotos y la de fotogramas convergen ahora en
`DagPreparedRasterPolicy`. Ambas usan el mismo raster RGB `224x224`, umbrales,
revision regional y fallo cerrado. Video reutiliza la unica instancia de
`DagLifecycleImageAnalyzer` y su sesion ONNX; no crea modelo, executor ni cola
adicional. La captura entra como trabajo visible en la cola multimedia acotada.

La identidad exacta de documento/video/revision y una generacion atomica
invalidan trabajo tardio. El bitmap `PixelCopy` se transforma a RGB y se recicla
antes de encolar; el RGB se pone a cero al completar, descartar o rechazar la
tarea. Error, timeout, vencimiento y salida del documento conservan la cobertura.

La eleccion conserva APIs oficiales: Android documenta la copia regional desde
`SurfaceView` a un bitmap escalado mediante `PixelCopy`, y ONNX Runtime recomienda
controlar los hilos por sesion para equilibrar latencia y contencion. Por eso se
reutiliza la sesion existente con intra-op 2/inter-op 1 en vez de abrir otra:

- `https://developer.android.com/reference/android/view/PixelCopy`;
- `https://onnxruntime.ai/docs/performance/tune-performance/threading.html`.

## Validacion automatizada

Comando ejecutado mediante el Gradle aislado:

```text
scripts/dag_gradle.sh testDagProtectionJs testDevDebugUnitTest
  testDiagnosticDebugUnitTest ktlintCheck lintDevDebug lintDiagnosticDebug
  assembleDevDebug assembleDiagnosticDebug
```

Resultado:

- JS: 35/35;
- unitarios DEV: 171/171;
- unitarios Diagnostic: 171/171;
- Ktlint: correcto;
- Lint DEV y Diagnostic: correcto;
- ambos APK: correctos.

Las pruebas nuevas cubren allow, block en umbral, revision regional incierta,
analizador no disponible, trabajo obsoleto e input invalido. Otra prueba compara
la ruta de bytes de imagen contra la autoridad de raster y exige la misma
accion, razon y probabilidad.

## Resultado fisico de GloshIA R3.1

La fixture sintetica de cuatro cuadrantes se ejecuto tres veces detras de la
cobertura Android:

| Muestra | PixelCopy | Cola | Inferencia R3.1 | Score | Decision |
| --- | ---: | ---: | ---: | ---: | --- |
| 1 | 34 ms | 1 ms | 194,60 ms | 0,01344 | allow |
| 2 | 17 ms | 2 ms | 145,18 ms | 0,01682 | allow |
| 3 | 9 ms | 1 ms | 137,86 ms | 0,01682 | allow |

Cada muestra uso una sola inferencia. La jerarquia fisica confirmo que
`video_lab_overlay` cubria todo Gecko despues de las decisiones; la accion del
modelo no retira la cobertura en `01A1`.

Al salir se registro `retired reason=navigation_started`. Las vistas bajaron de
151 a 39 y el PSS de 246.987 a 230.963 KiB. La sesion R3.1 queda caliente para
imagenes normales, pero no queda bitmap ni tarea de video retenida. No hubo
crash, ANR, AndroidRuntime, resultado obsoleto ni segunda captura concurrente.

## Regresion de imagenes e interaccion

Se conservo el perfil y se uso la misma variante Diagnostic en el A23:

- Mimo: `page_visible` 1.919 ms y `viewport_images_ready` 2.148 ms; hero e
  iconos visibles y menu funcional despues de scroll.
- Cheeky: `page_visible` 5.681 ms; portada e imagenes visibles. Su actividad
  continua no emitio quietud dentro de la observacion acotada.
- Fravega: la URL inicial devolvio su pantalla propia `La pagina no existe`, por
  lo que no cuenta como benchmark. Desde `Descubrir productos` la portada valida
  mostro banner, categorias y `Ofertas Unicas`; cuatro gestos rapidos del
  carrusel respondieron sin crash ni ANR.

No se agregaron excepciones por sitio, URL, dominio, proveedor o dispositivo.

## Resultado y artefactos

Resultado: `GO` para cerrar `DAG-VIDEO-01A1`. Esto no autoriza `01A2`, no
habilita video real y no modifica la experiencia de DAG normal.

- DAG normal 213 (`0.70.17-dev`):
  `6df919c3da523ac0d5e0f7d1973826cac301296d90655f828ddaf450658c135b`.
- DAG Diagnostic 12 (`0.70.17-diagnostic`):
  `be13d7faebe8cdab7e18648d110fe2f4ffa1652b525d9a192a9c8d4477d88c1d`.
- Ambos miden aproximadamente 116 MiB y permanecen locales. No hubo push,
  publicacion remota, Supabase ni Production.

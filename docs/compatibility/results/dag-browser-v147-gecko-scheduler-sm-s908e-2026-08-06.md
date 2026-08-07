# DAG 147 - event loop de Gecko y ráfagas visuales completas

Fecha: 2026-08-06
Dispositivo: Samsung SM-S908E, Android 16, arm64-v8a
APK: `0.69.51-dev`, `versionCode 147`
Extensión: `1.67.0`
APK SHA-256: `c5b5b0031319736477a5483dafe53cebb4e654b4e9868f5c5a78997d0f9c2b58`
Modelo: GloshIA Visual R3.1, SHA-256
`c8b64af8092d3718c58736a511c996d0d443dacf3eaa74620b1e5af439a3cd48`

## Resultado

El propietario confirmó físicamente el resultado final de DAG 147 en el S22.
El menú de Mimo conserva fluidez después del scroll y Frávega dejó de reproducir
la carga incompleta con imágenes negras informada durante la iteración.

La solución final reúne tres causas independientes. No cambia el modelo, el
umbral, el preprocesamiento ni la política visual.

## Hito 1: causa exacta del menú de Mimo

El menú abría correctamente al comienzo de Mimo, pero después del scroll se
animaba con trabas. Cheeky no reproducía el problema y Chrome seguía fluido en
el mismo teléfono.

La causa no era una falta general de CPU. El componente público de Mimo
sostenía un ciclo de React y su polyfill de `setImmediate` publicaba IDs
numéricos mediante un `MessageChannel`. El drawer de VTEX animaba con
`requestAnimationFrame`. En GeckoView, el trabajo continuo de `MessagePort`
postergaba los frames del drawer; la CPU alta era la consecuencia medible de
esa inanición del event loop.

El proceso de contenido se mantenía aproximadamente en `48-60 %` de CPU. Con
la solución, después de cargar Mimo, bajar y abrir el menú, tres muestras
registraron `15,6 / 15,0 / 15,0 %`.

### Corrección

`runaway-scheduler-guard.js` modifica `MessagePort.prototype.postMessage`, por
lo que también cubre puertos creados antes de la inyección. Solo reconoce
llamadas con un único argumento `null` o entero seguro no negativo. Se activa
tras 12 señales sostenidas durante al menos un segundo dentro de una ventana
de dos segundos y cede `16 ms` entre publicaciones.

El guard permanece inactivo hasta `document.readyState=complete`. Mensajes de
objetos, transferencias y la carga inicial usan directamente la implementación
nativa. No contiene excepciones por dominio, URL, sitio o dispositivo.

### Intento retirado

Un governor anterior envolvía solamente instancias nuevas de `MessageChannel`,
cedía `16 ms` y se activaba por una separación corta. No mejoró el caso físico
y fue eliminado por completo antes de implementar el guard de prototipo.

## Hito 2: cola Android de análisis visual

La primera versión eficaz del guard del menú coincidió con fotos tardías. Los
logs de Frávega mostraron 118 imágenes interceptadas y todas las decisiones
observadas eran `allow`: no era un falso bloqueo de R3.1. Un único ejecutor de
análisis Android tenía cola p50/p90/p95 `147,5/192/198 ms`.

DAG 146 separó la carga inicial del guard y recuperó dos trabajos concurrentes
Android. ONNX Runtime conservó dos hilos intra-op y uno inter-op.

| Métrica Frávega fría | DAG 145 | DAG 146 |
| --- | ---: | ---: |
| Raster procesados | 118 | 136 |
| Cola p50/p90/p95 | 147,5 / 192 / 198 ms | 0 / 1 / 1 ms |
| Página visible | 2.325 ms | 1.416 ms |
| Página terminada | 15.402 ms | 10.706 ms |
| Vista inicial quieta | 19.394 ms | 9.240 ms |
| Frame p95/p99 | 85 / 117 ms | 30 / 53 ms |

La ruta final de esa corrida DAG 146 fue una pantalla de error propia de
Frávega, de modo que la muestra demuestra la eliminación de la cola, pero no
fue usada como aprobación visual final.

Cheeky procesó 98 raster con cola p50/p90/p95 `0/1/2 ms`, jank `4,58 %` sobre
677 frames y sin crash ni ANR. El sitio realizó varias navegaciones internas;
sus tiempos de página no se trataron como un A/B estricto.

## Hito 3: prioridad previa a Android y cuadros negros

El usuario todavía observaba carga lenta y cuadros negros en Frávega, mientras
Logcat registraba casi exclusivamente `model_allow`. La discrepancia aisló una
segunda cola en `background.js`, anterior a la telemetría Android:

- era FIFO aunque cada tarea ya llevaba prioridad;
- admitía 24 análisis mientras podían existir 32 streams simultáneos;
- al llenarse, sustituía el excedente antes de Android;
- el fallback era un PNG de 1×1 negro opaco.

DAG 147 cambió esa cola a extracción estable por
`visible -> nearby -> background`, actualiza la prioridad de trabajos aún
pendientes cuando llega el hint DOM y admite 48 entradas: 32 streams más las
16 imágenes inline acotadas por documento. Los bytes de red continúan bajo el
presupuesto global de `8 MiB`; no se introdujo una cola sin límite.

El fallback anterior a Android volvió a ser un PNG transparente. Esto no
permite una imagen dudosa: los bytes originales continúan retenidos y solo se
escriben con `model_allow`. Un `model_filter` conserva el placeholder
proporcional generado por Android.

Dos nuevas pruebas conductuales fijan el arreglo:

1. una imagen visible en espera se adelanta a una de fondo cuando se libera un
   slot nativo;
2. una ráfaga completa de 32 respuestas llega a la compuerta sin desbordar a
   placeholders.

## Medición final DAG 147

Frávega, corrida fría de 30 segundos y tres swipes:

- página visible `1.277 ms`;
- vista inicial quieta `9.659 ms`;
- página terminada `11.553 ms`;
- 126 raster: 124 `model_allow`, un `decode_failed` de 67 bytes y un
  `invalid_payload` de 43 bytes;
- cola p50/p90/p95 `0/2/2 ms`;
- frames p50/p90/p95/p99 `6/29/34/48 ms`;
- jank `4,00 %`;
- sin crash ni ANR.

La confirmación visual del propietario después de instalar el APK final cerró
el gate: “perfecto”.

## Verificación

- `testDagProtectionJs`: 20/20.
- `testDevDebugUnitTest`: aprobado.
- `ktlintCheck`: aprobado.
- `lintDevDebug`: aprobado.
- `assembleDevDebug`: aprobado.
- DAG 147 instalado con `adb install -r`, preservando datos.
- No se borraron perfiles, cachés ni datos del usuario.

No se publicaron APK, commits, push o PR. No se tocó Supabase ni Production.
GloshIA Visual R3.1, umbral `0,40`, política visual y `final_sealed` permanecen
intactos.

## Rollback posterior

DAG 148 agregó una pausa de admisión durante interacción. Aunque las métricas
de compositor parecían favorables, el propietario no percibió mejora física y
ordenó retirarla. Todo el código y las pruebas específicas fueron eliminados.
DAG 149/extensión 1.69 vuelve al comportamiento de DAG 147; los números sólo
avanzan para permitir la instalación sobre 148 sin borrar datos.

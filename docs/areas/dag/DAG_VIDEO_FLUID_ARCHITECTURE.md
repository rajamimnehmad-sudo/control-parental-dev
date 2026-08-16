# DAG-VIDEO-03 — REPRODUCCION PROTEGIDA FLUIDA

Estado: modo adaptativo en prevalidacion; pipeline estricto queda aislado.
Progreso ejecutivo DAG Video: 88%. Video general de producto continua NO-GO.

## Decision vigente — 2026-08-14

La ruta de producto actual no transporta ni recodifica todo el video. Tras dos
cuadros iniciales aprobados bajo cobertura, reproduce la fuente original y
muestrea R3.1 cada 500 ms. Android conserva autoridad de cobertura: ante bloque,
error o cambio inseguro cubre y pausa antes de retirar el permiso exacto.

Diagnostic 75 demostro por telemetria 83 capturas continuas, 82 permitidas y un
bloqueo real con pausa y revocacion confirmada. La prueba humana posterior vio
video invisible y sin audio, asi que esa APK queda rechazada. El candidato 76
corrige captura/restauracion del audio original y opacidad residual, pero no se
considera aprobado hasta verlo y oirlo en A23/S22. Los benchmarks WebM/binarios
son evidencia y alternativa estricta futura; no ejecutan en el modo adaptativo.

Tradeoff aceptado para este hito: deteccion cada 500 ms mas inferencia, no cero
exposicion entre muestras. Falta matriz real suficiente antes de promover DEV.

## Resultado buscado del modo adaptativo

- Los dos primeros cuadros se analizan bajo cobertura nativa.
- Luego el video original reproduce de forma continua con su audio original y
  DAG muestrea cada 500 ms con el mismo filtro R3.1.
- Una decision negativa o error hace que Android cubra y pause antes de retirar
  el permiso visual exacto.
- Navegacion, seek, cambio de fuente/elemento/documento, fullscreen, PiP,
  remote playback, timeout o falta de autoridad cierran fail-closed.
- El riesgo aceptado es hasta 500 ms mas inferencia entre una aparicion y su
  bloqueo. El buffer audiovisual estricto de 3–8 segundos queda como alternativa
  futura si se obtiene acceso fiable al stream decodificado.

## Experiencia de producto

La meta es que el trabajo de GloshIA no sea perceptible durante reproduccion
normal. La espera visible se concentra en el cebado inicial; despues productor,
analisis y renderer mantienen margen por delante.

- La miniatura atraviesa el filtro comun de imagenes. Si queda bloqueada o
  dudosa, el video no inicia crudo: permanece cubierto mientras se analiza el
  comienzo. La miniatura sola no decide todo el video.
- La primera version expone un unico modo, `protegido fluido`: 5–8 segundos
  aprobados por delante.
  Un intervalo bloqueado se reemplaza por salida neutra y audio silenciado;
  DAG sigue analizando cubierto y retoma solo despues de continuidad segura.
- Error, incertidumbre, identidad distinta, demasiada duracion bloqueada o
  ausencia de un siguiente intervalo seguro detienen el video cubierto.
- No se ofrece `analizar completo antes` en la primera version: con una fuente
  en tiempo real puede demorar casi lo mismo que el video y agrega una opcion
  sin beneficio suficiente. Solo se reconsidera si un gate futuro demuestra
  analisis claramente mas rapido que tiempo real.

Un perfil protegido no puede debilitar la politica. Blur o mascara parcial es
un ticket futuro; hasta contar con segmentacion de fuga cero se omite el
intervalo completo.

## Diagnostico estructural

Diagnostic 70 demostro seguridad y compatibilidad basica, no fluidez. Su flujo
es estrictamente serial:

`play -> frame compuesto -> pause -> captureRegion -> R3.1 -> ImageView`

En la corrida estable de YouTube proceso 120 fotogramas en aproximadamente
29,5 segundos, cerca de 4,1 fps. Guardar cinco segundos de ese flujo no crea
ventaja: producir 120 fotogramas para reproducir 5 segundos a 24 fps demoraria
aproximadamente 29 segundos, incluso antes de sostener la reproduccion.

`captureRegion` entrega un Bitmap de lo ya compuesto. No entrega el stream
codificado, fotogramas futuros ni PCM de audio. GeckoView 153 expone controles y
eventos de MediaSession, pero no un callback publico de video/audio decodificado.
Por eso no se continuara agregando colas de Bitmaps al laboratorio actual.

## Arquitectura objetivo

### Politica universal

La decision de seguridad es unica para todas las fuentes. YouTube, Shorts,
TikTok, Instagram, HTML5, MP4, WebM, streaming y animaciones no reciben
excepciones por sitio, proveedor, contenedor o codec. Solo cambia el adaptador
que convierte la fuente decodificada en la representacion audiovisual comun.

Desde ese punto todos recorren el mismo detector temporal, R3.1 y buffer
aprobado. Si una fuente no puede entregar identidad, tiempos o muestras de
forma segura, permanece cubierta; nunca se permite como atajo de
compatibilidad.

### 1. Autoridad de origen

La extension aislada identifica exactamente:

- pestaña y documento emitidos por Android;
- elemento, revision y generacion de backing;
- fuente y rango temporal;
- geometria y epoch de viewport;
- capacidades de presentacion seguras.

La pagina nunca puede declarar un tramo como aprobado.

### 2. Productor audiovisual oculto

El medio original reproduce detras de cobertura nativa y sin salida directa. El
camino elegido obtiene un `MediaStream` y mantiene un unico MediaRecorder WebM
continuo. No se reinicia cada segundo: VP8+Opus solicita 1.100.000 bps de video
y 96.000 bps de audio, emite cada 500 ms y agrupa dos chunks consecutivos como
ventana logica de aproximadamente un segundo. El header inicial y el orden de
sesion se conservan hasta cambio de autoridad, fuente, seek, pista o error.

Este camino requiere comprobar en el GeckoView real:

- `captureStream` o `mozCaptureStream` utilizable;
- al menos un track de video y, cuando exista audio, uno de audio;
- `MediaRecorder` y un MIME WebM compatible;
- chunks finitos y continuidad de timestamps sobre HTML5 y YouTube MediaSource;
- limites duros aunque Gecko ignore el bitrate solicitado: 384 KiB por chunk,
  320 KiB por segundo rodante, 2 MiB y 18 chunks en memoria.

Si el gate falla, la alternativa profesional es integrar un hook dentro de
Gecko/compositor y audio, o limitar la funcion a fuentes controladas por un
reproductor propio. No se simulara video fluido con PixelCopy.

### 3. Analisis temporal

Los segmentos permanecen ocultos. Un pipeline separado extrae muestras y las
asocia a intervalos exactos. Hay dos contratos posibles, que no deben mezclarse:

- `strict`: todo fotograma presentado tiene decision propia. Requiere al menos
  24 decisiones por segundo sostenidas en el A23.
- `temporal`: un comparador barato observa cada fotograma decodificado, mientras
  R3.1 analiza como base dos muestras por segundo. Todo corte de escena, cambio
  significativo, movimiento dudoso o incertidumbre fuerza una muestra
  adicional inmediata. El intervalo se aprueba solo si no queda ningun cambio
  sin resolver. Es el camino de producto elegido, pero constituye una politica
  de video nueva y necesita gate/dataset propios antes de promocion.

Dos muestras por segundo son el piso, no un permiso para ignorar los 500 ms
intermedios. El detector barato evita ejecutar el modelo pesado sobre 24 o 30
fotogramas casi iguales y obliga a escalar cuando aparece informacion nueva.

R3.1, su umbral y la politica de imagen no cambian en el spike de viabilidad.

### 4. Buffer aprobado

Solo acepta segmentos con autoridad exacta y decision terminal `allow`.

- minimo: 3 segundos;
- objetivo inicial: 5 segundos;
- maximo: 8 segundos;
- orden estricto por PTS y secuencia;
- limite de memoria/bytes obligatorio;
- duplicado, hueco, solapamiento o autoridad distinta: cierre;
- descarte seguro en navegacion, seek, pausa larga o cambio de fuente.

### 5. Renderer confiable

Android reproduce solo segmentos aprobados en una superficie nativa por encima
de GeckoView. Audio y video usan el mismo reloj. La cobertura nativa sigue
siendo la ultima autoridad: aparece antes de underrun, error, rebuffer o cierre.
La pagina no controla la superficie, el reloj ni la cola aprobada.

## Maquina nativa

`Idle -> Covered -> Priming -> Ready -> Playing -> Rebuffering`

Estados terminales: `Closing` y `Blocked`.

Invariantes:

1. `Ready` exige al menos el objetivo aprobado y continuidad temporal.
2. `Playing` nunca consume un segmento no aprobado.
3. Bajo el minimo se cubre y se silencia antes de entrar en `Rebuffering`.
4. Solo se reanuda al recuperar el objetivo y la misma autoridad.
5. Cualquier invalidacion vacia datos, cancela productor/analisis y completa el
   cierre durable antes de admitir otra autoridad.

## Gates antes de una APK de producto

### Gate A — capacidad de fuente

Prueba Diagnostic finita, sin sacar contenido del telefono:

- disponibilidad de captureStream/mozCaptureStream;
- cantidad/estado de tracks, bucketizada;
- disponibilidad de MediaRecorder/MIME;
- creacion y limpieza deterministas, sin URL, pixels, audio ni bytes en logs.

Primero fixture audiovisual; luego una sola confirmacion YouTube normal. Maximo
dos corridas fisicas.

Resultado Diagnostic 71, A23, YouTube normal: `captureStream` estandar,
`MediaRecorder` y WebM disponibles, con una pista de audio y una de video. El
probe fue solo de capacidad: no inicio grabacion, no genero URL, no transporto
bytes y limpio el stream inmediatamente. Gate A queda GO para avanzar al
productor acotado; todavia falta demostrar chunks y continuidad en Gate B.

### Gate B — productor y transporte

- chunks audiovisuales timestampados;
- transporte binario acotado al proceso nativo;
- backpressure y cancelacion;
- ningun chunk queda persistido;
- fuente hostil no puede inyectar identidad ni aprobar intervalos.

Decision inicial: medir primero el `WebExtension.Port` ya validado, con base64
en chunks de 64 KiB, maximo dos en vuelo, ACK exacto y decodificacion/hash fuera
del hilo UI. A 1,5 Mbps representa cerca de 250 KiB/s ya codificados en base64.
Se acepta solo si el benchmark sintetico sostiene 3 Mbps durante 120 segundos,
sin perdida/corrupcion, cola mayor a dos, pausa UI de 100 ms, crecimiento de PSS
superior a 20 MiB ni ACK p99 mayor a 250 ms. Un servidor loopback queda como
plan B unicamente si este puente probado resulta NO-GO.

Resultado Diagnostic 72/A23: el puente conservo 45.000.000 bytes en 687 chunks,
sostuvo 3 Mbps, cola pico 1 y ACK p95/p99 32/36 ms, sin crecimiento PSS, ANR,
crash ni OOM. Sin embargo, la codificacion JS tuvo p95 53 ms, decode+SHA nativo
p95 8,41 ms y la UI registro dos frames superiores a 100 ms. Falla los limites
8/4/100 ms y queda NO-GO. No se reducen umbrales ni se conecta video real a
este camino.

La auditoria descarto el servidor loopback antes de implementarlo. GeckoView
153 aplica Local Network Access tambien a `127.0.0.1` y atribuye el permiso al
sitio superior. Autorizarlo daria a cada pagina acceso persistente a localhost;
desactivar LNA globalmente, agregar prompts o excepciones por proveedor viola
la politica universal.

Camino activo: el Port interno de GeckoView ya recibe mensajes mediante
structured clone y `GeckoBundleUtils` transforma un `Uint8Array` anidado en un
`byte[]`. El wrapper publico llama luego a `toJSONObject()` en UI. Un adaptador
Java pequeno dentro del paquete `org.mozilla.geckoview` puede registrar el
listener del Port y tomar el `byte[]` antes de esa conversion; una compilacion
aislada contra el AAR 153 confirmo el acceso package-private. No requiere fork,
red, Base64, archivos ni permisos nuevos.

Este adaptador es una compatibilidad versionada, no API publica. La primera APK
debe incluir capacidad y benchmark en el mismo lote: si el tipo binario real no
llega, se cierra sin datos; si llega, ejecuta 45 MB/687 chunks/3 Mbps/120 s.
Solo se permite una Diagnostic 73 y hasta dos corridas. Un tercer intento sin
pasar el hito obliga a una nueva auditoria, no a otra etiqueta.

Resultado Diagnostic 73/A23: Gate B binario GO en la primera corrida. El
adaptador fijado a GeckoView 153 recibio 45.000.000 bytes en 687 `byte[]`, con
3 Mbps sostenidos, cola pico 1, ACK JS p95/p99 17/23 ms, preparacion JS p95
6 ms y verificacion SHA nativa p95 0,72526 ms. PSS fue
261.769/263.132/260.763 KiB y la UI registro p99 77 ms, sin frames superiores a
100 ms, ANR, crash ni OOM. Todos los umbrales quedaron verdes sin reducirlos.
No hubo Base64, red, archivos ni permisos nuevos. Gate C puede usar este puente
para una ventana WebM real, pero el resultado no demuestra todavia decoder,
audio sincronizado, detector temporal ni reproduccion fluida.

### Gate C — rendimiento

- minimo de salida: 24 fps visuales sostenidos;
- minimo de analisis pesado: 2 fps, mas toda muestra forzada por cambio o duda;
- detector de cambios sostenido a la cadencia completa del video;
- ningun intervalo con cambio pendiente puede entrar al buffer aprobado;
- inicio objetivo de 5 segundos;
- deriva A/V maxima propuesta: 80 ms;
- cero crecimiento no acotado de memoria;
- cobertura antes de todo underrun;
- varios minutos sin ANR, OOM ni cola creciente.

### Gate D — matriz real

1. Fixture audiovisual: arranque, pause, resume, seek, resize y mutacion.
2. YouTube normal: controles comunes y cierre durable.

Shorts, anuncios y otros proveedores se abren despues, por lotes separados.

## Orden de implementacion y control de costo

1. Contratos puros y replay determinista, sin APK.
2. Gate A local y una unica APK Diagnostic del hito.
3. Si Gate A es GO, productor/transporte/renderer por componentes separados.
4. Si Gate A es NO-GO, decidir entre integracion Gecko y soporte limitado; no
   encadenar experimentos PixelCopy.
5. Una APK por hito, maximo dos corridas fisicas y gates amplios solo al cierre.

## Criterio de terminado de DAG-VIDEO-03

No basta con que se vea un video. El ticket termina cuando una fuente real
mantiene salida aprobada fluida, audio sincronizado, buffer estable, controles
basicos y cierre sin fuga; o cuando el gate demuestra con evidencia que la pila
publica de GeckoView no puede dar acceso audiovisual suficiente y queda tomada
una decision de producto explicita.

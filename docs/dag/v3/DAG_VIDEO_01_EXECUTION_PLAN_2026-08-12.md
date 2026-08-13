# DAG Video 01 — plan de ejecucion y gates

Fecha: 2026-08-12
Actualizado: 2026-08-13
Estado: `01A0` y `01A1` cerrados; `01A2` GO en A23 y pendiente S22; `01B` candidato local Diagnostic `NO-GO`
Baseline protegido: DAG Browser 211 (`0.70.15-dev`)
Autoridad visual: GloshIA Visual R3.1, sin cambios de modelo, umbral o politica

## Resultado buscado

Habilitar video web con analisis visual completamente local, general y acotado,
sin excepciones por sitio, URL, dominio, reproductor, formato o dispositivo. El
usuario no debe ver un fotograma nuevo antes de que la autoridad visual lo haya
aceptado. Audio y transcripcion permanecen fuera de alcance.

El trabajo no comienza con una integracion en DAG normal. Primero se demuestra,
en la variante diagnostica, que Gecko puede decodificar video detras de una
cobertura Android, que DAG puede capturar la region correcta y que R3.1 puede
clasificar esas capturas con un costo medido en S22 y A23.

No es una prueba A/B ni una sucesion de ajustes al azar. Cada corte tiene una
hipotesis, una fixture determinista, metricas y un gate binario.

## Diagnostico del baseline

El runtime actual no tiene un pipeline de video incompleto: aplica bloqueo total
en dos capas deliberadas.

- `background.js` cancela recursos `media` y `object`, y tambien respuestas con
  MIME de audio, video, DASH o HLS.
- `barrier.css` mantiene invisibles `video`, `audio`, `canvas`, `object` y
  `embed` desde el primer estilo de la pagina.
- El puente nativo recibe rasteres de imagen codificados; no recibe estados ni
  fotogramas de video.
- `GeckoView.capturePixels()` ya se usa para miniaturas de pestanas, pero captura
  la superficie completa. La API publica de la vista embebida no expone el
  `ScreenshotBuilder` regional de su display interno; `01A0` usa
  `PixelCopy.request(SurfaceView, Rect, Bitmap, ...)`, API oficial de Android,
  para copiar y escalar solamente el rectangulo visible a `224x224`.
- El analizador oficial acepta RGB `224x224`, utiliza una sesion ONNX con dos
  hilos intra-op y uno inter-op, y comparte hoy un executor acotado de dos
  trabajos para imagenes.

Una cobertura CSS no alcanza para el laboratorio: si el `video` esta oculto por
CSS, el compositor tampoco entrega sus pixeles en la captura. La cobertura debe
ser una vista Android opaca situada por encima de GeckoView. El video se renderiza
en la superficie de Gecko, pero permanece invisible para el usuario y capturable
para GloshIA.

### Hallazgo lateral que no debe mezclarse

`background.js` contiene una lista historica de hosts de video usada para omitir
el filtro de publicidad en esos sitios. No habilita video, porque el bloqueo de
media sigue activo, pero es una excepcion por dominio existente. `DAG-VIDEO-01`
no la reutilizara ni la ampliara. Su generalizacion debe ser un ticket separado
para no mezclar publicidad, video y regresiones de navegacion en un mismo lote.

## Limite de seguridad que condiciona el diseno

`requestVideoFrameCallback` avisa que un cuadro fue enviado al compositor; no
garantiza una intercepcion previa a su presentacion. Muestrear cada cierto tiempo
y dejar que el reproductor avance libremente puede perder una escena breve. Esa
estrategia seria fluida, pero no satisface el requisito de cero exposicion.

R3.1 tampoco puede revisar neuronalmente 30 o 60 cuadros por segundo en un A23:
la evidencia fisica actual situa una inferencia ordinaria en cientos de
milisegundos, y una revision regional puede acercarse a un segundo.

Por eso `01A` nunca revela el video. Mide el transporte y la inferencia detras de
la cobertura. Solo con esos datos se elige una arquitectura temporal que conserve
la garantia; si ninguna resulta fluida, el resultado correcto es `NO-GO`, no
debilitar silenciosamente la barrera.

## Arquitectura del laboratorio

```text
elemento <video>
      |
      v
barrera JS inicial (oculto + mudo)
      |
      | solicita cobertura con identidad de documento/video/revision
      v
cobertura Android opaca confirmada
      |
      | habilita decode solo en variante diagnostica
      v
superficie Gecko invisible al usuario
      |
      | captura regional 224x224
      v
coordinador nativo acotado -> preprocesamiento comun -> GloshIA R3.1
      |
      v
decision + tiempos en caja negra, sin guardar pixeles
```

### Protocolo fail-closed

1. El content script asigna a cada `video` una identidad efimera ligada a
   pestaña, documento y revision de fuente.
2. Reporta rectangulo visible, dimensiones, estado, duracion, `readyState` y
   revision; no reporta URL completa ni texto de la pagina.
3. Android instala primero una cobertura opaca sobre el contenido y responde
   `cover-armed` para esa identidad exacta.
4. Solo en diagnostico, y solo despues del acuse, la barrera permite renderizar
   el video debajo de la cobertura. El audio permanece mudo.
5. Android captura la region del compositor, la reduce sin crear Base64 y la
   envia al mismo preprocesamiento y modelo oficial.
6. Revision obsoleta, scroll, detach, cambio de pestana, navegacion, timeout,
   captura nula o error cancelan el trabajo, reciclan el bitmap y mantienen la
   cobertura.
7. `01A` registra la decision, pero nunca retira la cobertura ni reproduce audio.

### Estado minimo

```text
DETECTED -> COVERING -> COVERED -> DECODING -> CAPTURING -> ANALYZING
    |           |          |           |            |            |
    +-----------+----------+-----------+------------+------------+
                            cualquier error -> BLOCKED

navegacion / detach / nueva fuente / cambio de pestana -> RETIRED
```

Cada transicion exige la misma identidad de documento, video y revision. Un
resultado tardio nunca puede autorizar un elemento nuevo.

## Presupuesto de recursos de `01A`

- Una unica autoridad de video: el video visible de la pestana activa.
- Una captura y una inferencia de video como maximo en vuelo.
- Cola de video de capacidad uno: una muestra nueva reemplaza a la pendiente,
  nunca se acumula.
- No crear otro `OrtSession`, no duplicar el modelo y no aumentar hilos ONNX.
- El trabajo usa prioridad de fondo en el laboratorio y no puede ocupar los dos
  slots de imagen indefinidamente.
- Bitmap regional solicitado ya escalado a `224x224`; si la API regional no
  resulta estable, `NO-GO` antes de aceptar capturas de pantalla completa como
  solucion permanente.
- Reciclado inmediato del bitmap y limpieza de buffers al terminar.
- Sin cache persistente de cuadros. Las huellas temporales llegan recien en
  `01B` y tambien deben estar acotadas al documento.
- Si hay scroll o gesto activo, no iniciar una captura nueva; la cobertura y los
  controles permanecen responsivos.

## Observabilidad necesaria

La caja negra registra eventos tecnicos acotados, sin pixeles, audio, URL
completa, consulta ni texto visible:

- `video_detected`, `cover_requested`, `cover_armed`;
- `decode_ready`, `frame_requested`, `frame_captured`;
- `frame_analysis_started`, `frame_decision`;
- `video_revision_changed`, `video_retired`, `video_failure`;
- `capture_ms`, `preprocess_ms`, `onnx_ms`, `decision_ms`, espera de cola;
- dimensiones CSS, dimensiones capturadas, orientacion, estado y motivo;
- memoria antes/despues, capturas por minuto, descartes y maximo en vuelo.

Cada sesion incluye version de APK, extension, modelo y protocolo. Asi un reporte
posterior identifica exactamente que binario genero los datos.

## Fixture determinista

El laboratorio no comienza con YouTube o Instagram. Primero usa una pagina local
sin red y clips sinteticos controlados:

1. color/patron seguro para comprobar coordenadas y recorte;
2. cambio brusco de escena y marcador de un solo cuadro;
3. vertical, horizontal y cambio de tamano;
4. dos videos, para demostrar que solo el visible tiene autoridad;
5. scroll rapido, detach, reemplazo de `src`, navegacion y cambio de pestana;
6. captura nula, timeout y revision tardia;
7. clip semantico construido localmente con imagenes ya revisadas del corpus,
   sin incorporarlas al APK ni al repositorio.

Despues del contrato local se mide la misma ruta general en YouTube Web e
Instagram Web. Son casos de validacion, nunca ramas de codigo.

## Tickets pequenos

### `DAG-VIDEO-01A0` — contrato y captura cubierta

Alcance:

- diagnostico solamente;
- fixture local de transporte;
- identidad/revision de video y handshake `cover-armed`;
- cobertura Android completa en el laboratorio;
- decode mudo debajo de la cobertura;
- captura regional y cancelacion completa;
- sin conectar todavia la decision a la reproduccion.

Gate:

- ningun marcador de la fixture es visible;
- el recorte corresponde al video correcto en vertical, horizontal y scroll;
- cero resultados obsoletos aceptados;
- una sola captura en vuelo y memoria vuelve al baseline;
- DAG normal conserva exactamente el bloqueo total actual.

Estado de implementacion local:

- candidato DAG normal `212` (`0.70.16-dev`) y Diagnostic `11`;
- fixture sintetica interna, sin red ni contenido del corpus;
- cobertura Android opaca confirmada durante dos cuadros antes del decode;
- captura regional `PixelCopy` de `224x224`, reciclada inmediatamente;
- prueba del patron de cuatro cuadrantes para demostrar recorte y decode
  correctos sin persistir pixeles;
- identidad `documento/video/revision`, una captura en vuelo y timers acotados;
- CSS de barrera instalado con origen `user`, no anulable por estilos de pagina;
- el laboratorio solo acepta la URL interna exacta de la fixture. Todo video de
  red, audio, DASH, HLS, `object` y `embed` continua bloqueado incluso en la
  variante diagnostica;
- suite automatizada correcta y gate fisico cerrado en SM-A235M: cobertura
  opaca, recorte correcto en vertical/horizontal/scroll, tres muestras validas
  y retiro sin fuga de la memoria de captura. Evidencia:
  `docs/compatibility/results/dag-browser-v212-video-a0-sm-a235m-2026-08-12.md`.

### `DAG-VIDEO-01A1` — R3.1 y telemetria local

Alcance:

- pasar capturas `224x224` por el analizador oficial existente;
- unificar cancelacion con la identidad de documento;
- caja negra de video y resumen exportable;
- pruebas de safe, block, incierto y error, sin retirar la cobertura.

Gate:

- misma decision que la ruta de imagen para el mismo raster preparado;
- timeout, error o incertidumbre bloquean;
- no segunda sesion ONNX, cola sin limite ni crecimiento de bitmaps;
- imagenes, menus, scroll y carga mantienen el baseline 211.

Estado: cerrado en DAG normal `213` / Diagnostic `12`. Fotos y fotogramas
convergen en una unica `DagPreparedRasterPolicy`, la fixture reutiliza la sesion
R3.1 oficial y la cola multimedia acotada, y la caja negra guarda captura, cola,
inferencia, score, base, accion y razon sin pixeles. El gate A23 completo tres
muestras allow cubiertas, la cancelacion y la regresion visual Mimo/Cheeky/
Fravega. Evidencia:
`docs/compatibility/results/dag-browser-v213-video-a1-sm-a235m-2026-08-12.md`.

### `DAG-VIDEO-01A2` — gate fisico S22/A23

Medir en frio y caliente:

- latencia de cobertura, decode, captura, preprocesamiento e inferencia;
- p50, p95 y peor caso;
- CPU, memoria, temperatura, frames tardios y gestos;
- una, dos y varias revisiones consecutivas;
- YouTube Web e Instagram Web con la misma ruta general.

Objetivo de primera preparacion cubierta: menor a `2,5 s` en S22 y menor a
`4 s` en A23, sin ANR, crash, fuga ni perdida de interaccion. Estos numeros son
gates de laboratorio, no permiso para revelar escenas posteriores.

El informe termina en `GO`, `GO CON CONDICIONES` o `NO-GO`, con evidencia. No
modifica el APK normal.

Estado de ejecucion: Diagnostic 13 habilita el laboratorio web solo mediante un
interruptor efimero de sesion y mantiene DAG normal cerrado. A23 completo cinco
primeras preparaciones con p50 `967,67 ms` y p95/peor caso `2.185,70 ms`, sin
crash, ANR ni fuga: `GO` para A23. El resultado global es
`GO CON CONDICIONES` porque el S22 aun no estuvo disponible. Evidencia:
`docs/compatibility/results/dag-browser-v213-video-a2-sm-a235m-2026-08-12.md`.

### `DAG-VIDEO-01B` — motor temporal

La autorizacion posterior permite implementar un candidato local de reproduccion
estrictamente diferida en Diagnostic, no activarlo ni promoverlo. El candidato
debe presentar dentro de una cobertura Android permanente el mismo bitmap
regional que acaba de autorizar GloshIA; nunca debe descubrir el `video` de
Gecko. Captura con aspecto preservado y borde maximo acotado, deriva de ese
bitmap el RGB canonical `224x224` para R3.1 y solo transfiere el bitmap original
al `ImageView` nativo despues de `allow` y del ACK de ocultamiento del cuadro
bruto. Identidad de documento/video/revision/viewport/secuencia invalida toda
respuesta tardia; no hay cache persistente ni miniaturas mientras la cobertura
esta activa.

El teardown entra en `CLOSING` y retiene la cobertura hasta que background
acredita la revocacion CSS exacta por documento y nonce. Insercion pendiente,
fallo, desconexion o estado perdido de background no reciben ACK y quedan
bloqueados. PiP, pantalla completa y reproducción remota se solicitan denegar
preventivamente y cualquier evento o mutacion los retira; esos atributos DOM no
son una raiz de confianza frente a una pagina MAIN world hostil.

El candidato actual no es un `GO`: el aislamiento de `HTMLMediaElement` no basta
para garantizar silencio de `AudioContext` MAIN world, falta el gate fisico S22
que pruebe la oclusion de la superficie Gecko y aun no existen metricas fisicas
de fluidez. La secuencia R3.1 medida en A23 tampoco autoriza afirmar 30/60 fps.
El resultado correcto hasta cerrar esos puntos es `NO-GO`, sin debilitar la
barrera ni ajustar modelo, umbral o politica.

Una vez satisfechos los gates, se decidira entre:

- reproduccion visual estrictamente diferida mediante cuadros ya autorizados;
- integracion mas profunda en decode/compositor;
- modelo auxiliar rapido con ticket, corpus, licencia y equivalencia propios.

La alternativa de reproducir libremente y muestrear periodicamente queda
descartada porque puede mostrar escenas breves antes de analizarlas.

### `DAG-VIDEO-01C` — canary reversible

Solo despues de que `01B` demuestre seguridad y experiencia en ambos equipos:

- activacion general en DAG normal;
- pantalla completa solo si tiene cobertura equivalente demostrada;
- rollback inmediato al bloqueo total actual;
- publicacion DEV separada y aprobada.

## Decisiones por defecto

- Preanalisis siempre mudo.
- Si la imagen queda bloqueada, audio pausado por defecto. La accion manual
  `solo audio` pertenece a UX posterior y no afirma que el audio sea seguro.
- La guarda actual de `HTMLMediaElement` no acredita silencio de `AudioContext`
  MAIN world; 01B permanece `NO-GO` hasta que exista una barrera con esa
  autoridad.
- Pantalla completa, PiP, casting, vivo y DRM permanecen cerrados en `01A`.
- No se cambia R3.1 para cumplir tiempos.
- No hay excepciones por proveedor ni heuristicas semanticas basadas en URL.
- No se toca DAG normal hasta pasar el laboratorio y recibir una aprobacion
  nueva.

## Archivos previstos para el primer ticket

La implementacion de `01A0` deberia limitarse a:

- contrato de video y coordinador nativo nuevos, fuera de
  `DagBrowserActivity` salvo integracion minima;
- una vista/cobertura dedicada en el layout;
- barrera y protocolo de la extension con gate de variante diagnostica;
- fixture y tests contractuales especificos;
- caja negra solo para los nuevos eventos de transporte.

No debe tocar corpus, modelo, umbrales, Supabase, Production, politica de
imagenes, publicidad ni excepciones por sitio. Si el diff exige cambiar alguna
de esas areas, el ticket se detiene y se replantea.

## Validacion y rollback

- Unit tests de identidad, revision, estado, cola uno y cancelacion.
- Test JS del handshake y del bloqueo intacto en DAG normal.
- Test de que manifest/barrier/background sean entradas declaradas de Gradle.
- Build de normal y diagnostico, instalados desde `main` local integrado.
- Prueba fisica primero A23, luego S22, con caja negra limpia por corrida.
- Rollback de cualquier laboratorio: desactivar el gate diagnostico; el bloqueo
  total actual sigue siendo la ruta por defecto.

## Aprobacion y siguiente limite

`DAG-VIDEO-01A0`, `01A1` y `01A2` fueron aprobados. Los dos primeros estan
cerrados; `01A2` paso A23 y queda pendiente del S22. Una autorizacion posterior
permitio implementar `01B` solo como candidato local Diagnostic 15; no autoriza
`01C`, activacion en DAG normal ni publicacion. La seguridad y la experiencia
de 01B permanecen `NO-GO` hasta disponer de barrera de audio con autoridad,
evidencia de oclusion en S22, fixture verificada y metricas fisicas de fluidez.
DAG normal continua con bloqueo total.

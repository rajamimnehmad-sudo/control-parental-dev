# GloshIA Video V1 — propuesta de compuerta visual temporal

Fecha: 2026-08-12  
Estado: propuesta convertida en plan; implementacion no iniciada
Ticket futuro: `DAG-VIDEO-01`  
Baseline de navegador: DAG Browser 211 (`0.70.15-dev`)  
Modelo oficial vigente: GloshIA Visual R3.1

## Decision de producto registrada

- El primer alcance clasifica solamente la imagen del video.
- Audio, lenguaje, musica y transcripcion quedan fuera de alcance para otro
  ticket. El audio no se usa como senal para permitir o bloquear video.
- El video permanece bloqueado en DAG hasta que el usuario apruebe un ticket de
  implementacion y el laboratorio pase sus gates.
- No se agregan excepciones por YouTube, Instagram, sitio, URL, dominio,
  reproductor, formato o dispositivo.
- No se cambia ni reemplaza GloshIA Visual R3.1 en esta propuesta.

## Arquitectura recomendada

GloshIA Video no nace como otro clasificador. Es un transporte temporal que
selecciona fotogramas; la autoridad visual sigue siendo la misma:

```text
foto web --------------------+
                              +--> preprocesamiento comun --> GloshIA Visual --> decision
fotograma seleccionado -------+
```

Por lo tanto, una futura version de GloshIA Visual promovida con los mismos
contratos de entrada, politica y exportacion mejora tanto fotos como fotogramas
de video. No se debe bifurcar el modelo oficial antes de demostrar una
necesidad de rendimiento.

Si el laboratorio demuestra que R3.1 no alcanza la experiencia objetivo en el
A23, se puede estudiar un auxiliar rapido. Ese auxiliar no reemplaza la
autoridad: resuelve casos evidentes y deriva incertidumbre a GloshIA Visual. Su
entrenamiento, licencia, exportacion y equivalencia requieren un ticket propio.

## Experiencia de uso objetivo

### YouTube y reproductores ordinarios

1. La miniatura o poster se filtra como una imagen ordinaria.
2. Ningun fotograma del video se muestra antes de la primera autorizacion.
3. Al entrar el reproductor en el viewport, DAG prepara de forma acotada los
   primeros fotogramas, idealmente antes de que el usuario toque Play.
4. Si la preparacion termino, Play comienza sin espera perceptible.
5. Si aun falta una decision, se conserva el ultimo fotograma permitido o el
   poster con un indicador discreto `Preparando video`; no se muestran negro,
   gris ni pixeles pendientes.
6. Durante escenas estables, una huella visual barata evita repetir
   inferencias. Solo cambios significativos solicitan otra decision.
7. Ante una escena nueva pendiente, DAG conserva el ultimo fotograma permitido
   y pausa sincronizadamente. Si la escena es segura continua; si es bloqueada,
   incierta o falla, permanece cubierta.
8. Un bloqueo ofrece acciones simples: continuar cuando vuelva a ser seguro,
   reproducir solo audio o cerrar el video. La opcion de solo audio no implica
   que el audio haya sido clasificado.

### Instagram Reels y feeds verticales

- Solo el reel visible tiene prioridad de analisis.
- Como maximo se prepara de forma acotada el siguiente reel inmediato.
- Al deslizar, todo trabajo del reel anterior se cancela y sus bitmaps se
  reciclan.
- No se analizan reels lejanos ni todos los videos de la pagina.
- Autoplay visible permanece cerrado hasta la primera decision segura.

## Transporte de fotogramas

La opcion preferida para el laboratorio es capturar la region renderizada del
video desde el compositor de Gecko detras de una cobertura Android opaca. La
API publica de GeckoView permite capturar una region concreta y escalarla antes
de producir el bitmap:

- <https://mozilla.github.io/geckoview/javadoc/mozilla-central/org/mozilla/geckoview/GeckoDisplay.ScreenshotBuilder.html>

Esto evita depender de una URL MP4 unica. YouTube, Instagram y otros servicios
pueden construir streams con MSE y fragmentos sucesivos:

- <https://developer.mozilla.org/en-US/docs/Web/API/Media_Source_Extensions_API>

Leer pixeles mediante canvas no puede ser la unica estrategia, porque contenido
cross-origin sin CORS vuelve el canvas no legible:

- <https://developer.mozilla.org/en-US/docs/Web/HTML/How_to/CORS_enabled_image>

`requestVideoFrameCallback` puede sincronizar el detector barato con cuadros
realmente enviados al compositor, pero no habilita por si solo pixeles seguros
ni sustituye la cobertura:

- <https://developer.mozilla.org/en-US/docs/Web/API/HTMLVideoElement/requestVideoFrameCallback>

## Seguridad visual y limite honesto

No se debe prometer simultaneamente reproduccion nativa a 30/60 FPS y revision
neuronal completa de cada fotograma con R3.1 en el hardware piso. En el A23, la
evidencia fisica de DAG 211 mostro inferencias ordinarias del orden de cientos
de milisegundos y decisiones regionales cercanas a un segundo.

El laboratorio debe comparar dos propiedades sin ocultar su tension:

- seguridad estricta: ningun fotograma nuevo se revela antes de decidirlo;
- fluidez: reproduccion continua sin pausas perceptibles.

La recomendacion de producto es priorizar seguridad estricta y disfrazar la
espera conservando el ultimo fotograma seguro. Si el resultado no es
suficientemente fluido, no se relaja silenciosamente la barrera: se detiene el
ticket y se evalua un modelo auxiliar rapido.

## Presupuesto operativo propuesto

- Una unica cola de video por pestaña activa.
- Un solo video visible con autoridad de analisis.
- Bitmaps escalados al tamano minimo requerido por GloshIA y reciclados al
  terminar.
- Cache de huellas visuales acotada a la sesion/documento; no cache por dominio.
- Cancelacion por navegacion, cambio de pestana, salida del viewport, reemplazo
  de fuente o nuevo reel.
- El desplazamiento y los controles tienen prioridad sobre analisis de fondo.
- Sin persistir fotogramas, audio, URL completa, busquedas ni contenido.
- Caja negra con tiempos, dimensiones, motivo, cantidad de muestras y decision,
  pero sin pixeles.

## Casos inicialmente cerrados

- DRM o superficie protegida;
- transmision en vivo;
- Picture-in-Picture;
- casting o intent externo;
- pantalla completa hasta validar una cobertura equivalente;
- captura fallida o compositor no preparado;
- varios videos que pretendan reproducirse a la vez;
- formato, stream o estado temporal no reconocido.

No se implementan reglas especiales para resolver estos casos por proveedor.

## Plan futuro por gates

### `DAG-VIDEO-01A` — laboratorio de captura cubierta

- Variante laboratorio/diagnostico solamente.
- Detectar el video visible y su rectangulo exacto.
- Mantener cobertura nativa mientras Gecko decodifica localmente.
- Capturar una secuencia pequena y acotada sin mostrarla.
- Pasar cada muestra por el pipeline oficial de GloshIA Visual R3.1.
- Medir S22 y A23 antes de integrar comportamiento en DAG normal.

### `DAG-VIDEO-01B` — detector temporal y experiencia

- Huella barata y detector de cambio de escena.
- Ultimo fotograma seguro como cobertura.
- Cancelacion al hacer scroll, cambiar reel o navegar.
- Estados simples: preparando, reproduciendo, revisando y bloqueado.

### `DAG-VIDEO-01C` — canary reversible

- Integracion general sin excepciones por sitio.
- YouTube e Instagram Web como pruebas fisicas, no como ramas de codigo.
- Rollback inmediato al bloqueo total de video.

## Gates de aceptacion propuestos

- Cero fotogramas visibles antes de la primera autorizacion.
- Error, timeout, incertidumbre o fuente desconocida fallan cerrados.
- Mismo pipeline para YouTube, Instagram y fixture controlado.
- La pagina, scroll, menus y controles permanecen interactivos.
- El trabajo se cancela y la memoria se libera al salir del video.
- Sin ANR, crash, fuga, crecimiento termico sostenido ni cola sin limite.
- Inicio seguro objetivo: menor a 2,5 s en S22 y menor a 4 s en A23.
- Matriz con video seguro, bloqueado, transicion segura-bloqueada, escena
  peligrosa breve, cortes rapidos, vertical, horizontal, MSE y captura fallida.
- Memoria, CPU, temperatura, frames tardios, inferencias por minuto y tiempo de
  cobertura quedan registrados.
- El modelo, umbral o politica no se modifican para hacer pasar el gate.

## Preguntas que el plan debera cerrar

1. Si una escena queda bloqueada, ¿audio pausado por defecto con opcion manual
   `solo audio`, o audio continuo?
2. ¿Cuantas decisiones seguras consecutivas permiten reanudar luego de un
   bloqueo?
3. ¿La primera entrega incluye pantalla completa o permanece cerrada?
4. ¿Que pausa maxima perceptible acepta el producto en A23 antes de declarar
   necesario un auxiliar rapido?

Cuando el usuario pida `hacer el plan de GloshIA Visual para videos`, abrir este
documento y convertir primero `DAG-VIDEO-01A` en un ticket pequeno aprobado. No
comenzar por entrenamiento ni por excepciones de compatibilidad.

Plan de ejecucion resultante:
`docs/dag/v3/DAG_VIDEO_01_EXECUTION_PLAN_2026-08-12.md`.

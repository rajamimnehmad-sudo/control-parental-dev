# CHROME-VISUAL-03-VIDEO — evidencia

Fecha: 2026-08-18. Dispositivo fisico: Samsung A23 `SM-A235M`, Android 14 / API 34.

## Resultado

**Nucleo reactivo y gates automaticos verdes; gate fisico de experiencia FAIL.**

- Chrome Visual muestrea regiones visuales acotadas cada 500 ms cuando detecta
  dinamismo. No depende de DOM, URL, proveedor, codec ni formato.
- Cada resultado queda ligado a ventana, pagina efimera, captura, region,
  geometria y firma visual; un resultado viejo no puede modificar una captura
  nueva.
- `Block` y `Unavailable` cubren la region. Recuperar visibilidad exige dos
  muestras `Allow` consecutivas. Un cambio de geometria invalida la recuperacion.
- El audio no se modifica. No se guardan, transmiten ni registran pixeles.
- La implementacion separo inspeccion de ventana, firma, inferencia y politica
  temporal. `ChromeVisualController.kt` quedo en 455 lineas.

## Prueba fisica unica

- Fixture controlada seguro -> seguro -> bloqueable -> seguro -> seguro:
  detecto el bloqueo y recupero la region con dos muestras seguras, sin
  crash/ANR. Capturas calientes: 64–123 ms.
- YouTube normal y un segundo video cargaron sin crash/ANR. Al cierre:
  PSS 170.940 KB, RSS 166.140 KB y una muestra puntual de CPU de 0,8%.
- La APK probada sufrio una tormenta de eventos de Accessibility: repetia la
  cobertura inicial de toda la ventana y podia demorar unos 12 s en completar
  el baseline. Eso no es experiencia aceptable y hace fallar el gate fisico.
- Seek, fullscreen real y una sesion de 20–30 minutos no quedaron demostrados.
  El intento de fullscreen permanecio en vertical.
- Accessibility, rotacion, forwards ADB y estado de la app quedaron restaurados.

## Correccion posterior, sin nueva sesion fisica

La causa no estaba en GloshIA ni en YouTube: cada evento ordinario cancelaba el
baseline activo y volvía a cubrir la ventana completa. La correccion universal:

- reserva la cobertura inicial completa para primera carga, navegacion o cambio
  real de ventana/geometria;
- agrupa una rafaga del mismo contexto en un baseline y una sola verificacion
  posterior;
- procesa los eventos ordinarios como verificacion incremental regional;
- conserva fail-closed ante cambio de identidad, captura fallida o decision no
  disponible.

Una prueba determinista con 20 eventos repetidos verifica que solo se programa
un baseline y un seguimiento. No se genero otra APK ni se repitio hardware, en
cumplimiento del limite de una sesion fisica por lote.

## Gates finales

- `:feature-accessibility:testDebugUnitTest`: PASS.
- `:feature-accessibility:testReleaseUnitTest`: PASS.
- `:feature-accessibility:ktlintCheck`: PASS.
- `:app-user:lintDevDebug`: PASS.
- `:app-user:assembleDevDebug`: PASS.
- `git diff --check`: PASS.

## Limites y decision

- La correccion contra coberturas repetidas necesita una futura confirmacion
  fisica antes de habilitar el modo para usuarios.
- El enfoque es reactivo: una escena nueva puede quedar visible hasta el proximo
  muestreo mas captura e inferencia. No demuestra exposicion cero.
- DRM, `FLAG_SECURE` o captura fallida permanecen cubiertos; la politica de
  degradacion a DAG corresponde a `CHROME-VISUAL-04`.
- S22 no estuvo disponible.

Chrome Visual Video permanece **DEV-only y NO-GO para producto**. Por el gate
fisico fallido no corresponde iniciar `CHROME-VISUAL-04` en este lote.

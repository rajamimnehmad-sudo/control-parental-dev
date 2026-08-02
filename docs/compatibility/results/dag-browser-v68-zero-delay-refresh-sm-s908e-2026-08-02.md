# DAG Browser 68 - raster inmediato y refresh continuo en SM-S908E

Fecha: 2026-08-02

## Alcance

- paquete: `com.contentfilter.dagbrowser.dev`;
- version: `68` / `0.48.0-dev`;
- extension incorporada: `1.36.6`;
- dispositivo: Samsung SM-S908E, serial ADB `R5CT717BZTZ`;
- instalacion: comportamiento validado in-place antes del incremento; el APK
  final 68 quedo pendiente de instalar por desconexion del telefono;
- canal: candidato local, sin push ni publicacion.

## Causa reproducida

La compuerta de respuestas ya retenia cada raster antes de Gecko, pero el DOM
esperaba ademas 350 ms antes de mostrar un `img`. Al refrescar, Android agregaba
una tercera cobertura: `reloadActivePage()` activaba `Loading`, ocultaba todo
GeckoView y mostraba el fondo de seguridad hasta el siguiente `barrier-ready`.

En Google Imagenes se tomaron dos rafagas. La cobertura total aparecio durante
tres capturas consecutivas. La segunda rafaga incluyo tiempos por captura y
estimo aproximadamente 1,2 segundos entre la ultima pagina visible y su regreso.
El comportamiento aumentaba al recargar lejos del inicio por la reconstruccion
de la grilla, pero la causa del apagado era el estado Android, no GloshIA.

## Correccion

- `IMAGE_STABILITY_MS` pasa a `0`;
- un `img` HTTP(S) completo queda estable inmediatamente;
- cambios posteriores de `src`, `srcset` o `sizes` no vuelven a ocultar el
  elemento: la nueva respuesta sigue atravesando la compuerta antes del render;
- fuentes inline `data:`/`blob:` se vuelven a cerrar porque no usan
  `webRequest`;
- el refresh del mismo documento conserva visible la pagina ya protegida;
- una URL distinta, primera carga o fallo de barrera conserva cobertura total;
- no hay excepciones por sitio, comercio o modelo de telefono.

## Validacion

- `node --check`: correcto;
- 12 pruebas WebExtension: aprobadas;
- 148 pruebas unitarias Kotlin: aprobadas;
- `ktlintCheck`: correcto;
- `lintVitalDevDebug`: correcto;
- `assembleDevDebug`: correcto;
- `git diff --check`: correcto;
- validacion fisica: sobre el mismo codigo funcional previo al incremento, el
  usuario confirmo imagenes en `0 ms` sin escape de contenido rechazado y
  refresh de Google Imagenes sin apagado general.

La matriz completa Mimo, Cheeky y Fravega de DAG 67 no se repitio: DAG 68 no
cambia modelo, pesos, umbrales, presupuestos ni compuerta de respuestas.

## Presentacion fail-closed

Un bloqueo normal decidido por Android usa el placeholder gris proporcional.
Mientras un elemento inline sigue cerrado se ve el fondo del sitio; timeout,
error o saturacion pueden usar el PNG negro minimo de emergencia. Son estados
tecnicos distintos, no criterios distintos de GloshIA. Unificarlos visualmente
queda fuera de este punto seguro.

## Artefacto

- APK: `app-dag-browser/build/outputs/apk/dev/debug/DagBrowser-dev-debug.apk`;
- tamaño: `121360633` bytes;
- SHA-256:
  `2a81e6477b5c8170297b5b7e464cf3448fac6c5de5c5711970a7b028e0436a55`.

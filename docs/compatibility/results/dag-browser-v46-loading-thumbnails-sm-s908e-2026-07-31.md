# DAG Browser 46 — transición y miniaturas durables en SM-S908E

## Alcance

- Ticket: `DAG-LOAD-TRANSITION-12`.
- Versión: `versionCode 46`, `versionName 0.28.0-dev`.
- Dispositivo: Samsung SM-S908E, Android 16.
- APK: 121.331.431 bytes.
- SHA-256: `fea804a7fc04c4d5afc4c08a55f482d2e41699db946c047c7c9fddc0e5103f81`.
- Instalación in-place desde `main` local. No se hizo push ni publicación.

## Causa raíz y corrección

- Gecko podía limpiar el documento anterior antes de entregar `onPageStart`.
  DAG esperaba ese callback para ocultar el `GeckoView`, por lo que aparecía un
  cuadro blanco durante enlaces internos.
- Toda navegación superior permitida y distinta del documento actual activa
  ahora la cobertura antes de devolver `ALLOW`. Cambios de fragmento no cierran
  innecesariamente la página y una barrera ya activa no se inicia dos veces.
- Recarga por menú y los demás caminos propios usan la misma transición. La
  cobertura es opaca, azul DAG y muestra un brillo barrido sin texto ni spinner.
- Las miniaturas seguras antes vivían sólo en RAM y se liberaban al ocultar la
  interfaz. Ahora se guardan como JPEG acotado dentro del almacenamiento privado
  de DAG, con clave hexadecimal no reutilizable como ruta. Sobreviven a cierre,
  muerte de proceso y actualización; navegar, cerrar pestaña o borrar datos
  elimina la captura anterior.
- Contraseñas, pagos, CAPTCHA y pestañas incógnito continúan sin persistencia.
- Se eliminó el reciclado manual de bitmaps ya entregados al organizador. El
  registro anterior mostraba un cierre por `Canvas: trying to use a recycled
  bitmap`; el presupuesto máximo permanece en 12 MB para 50 capturas en RAM.

## Validación automática

- 127 pruebas unitarias, 0 fallos, 0 errores y 0 omitidas.
- `ktlintCheck`, `lintDevDebug` y `assembleDevDebug`: correctos.
- Lint: 31 advertencias, 0 errores.
- `git diff --check`: correcto.

## Validación física

- Se abrió un resultado Netflix desde una página de Google ya visible.
- El primer screenshot inmediato mostró cobertura azul completa; el siguiente,
  tomado 120 ms después, mostró el brillo desplazado. No apareció un cuadro
  blanco entre documentos.
- Una miniatura nueva de Cheeky se capturó, DAG fue forzado a cerrar y volvió a
  abrir: la tarjeta reapareció con su imagen. Otra reinstalación in-place del APK
  conservó la misma captura.
- Las pestañas antiguas creadas antes de DAG 46 permanecen neutras hasta que se
  visitan una vez; luego quedan asociadas a su nueva clave durable.
- Con Logcat limpio no hubo `FATAL EXCEPTION` ni ANR en el recorrido final.

## Decisión de producto

El usuario priorizó expresamente la continuidad de miniaturas por encima de
eliminarlas al pasar a segundo plano. Se mantiene almacenamiento privado y la
exclusión de páginas sensibles para evitar capturas inválidas o engañosas.

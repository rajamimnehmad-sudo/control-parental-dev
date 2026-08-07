# DAG Browser 164 - residencia de miniaturas de pestanas

Fecha: 2026-08-06

Dispositivo: Samsung SM-S908E, Android 16, arm64-v8a

## Causa raiz

La politica de sesiones ya limitaba Gecko a tres sesiones abiertas, por lo que
reducir ese numero podia empeorar el cambio entre pestanas sin atacar el
consumo observado. El desperdicio concreto estaba en
`DagBrowserActivity.onStart()`: restauraba desde disco las miniaturas de todas
las pestanas aunque el selector no fuera visible. Ademas, una restauracion
asincrona iniciada antes del cierre podia terminar tarde y volver a retener el
bitmap.

## Correccion

- Las miniaturas persistidas se restauran al abrir el selector, no al iniciar
  normalmente la actividad.
- Cerrar el selector o dejar de ver la actividad elimina las referencias a sus
  miniaturas.
- Los resultados asincronos solo quedan residentes mientras el selector los
  solicita; si terminan tarde, se descartan.
- La captura valida se sigue persistiendo aunque el selector este cerrado.
- El frame de navegacion queda separado de las miniaturas. Cerrar el selector
  no retira el frame que evita flashes durante la siguiente navegacion.

No se cambiaron el maximo de tres sesiones Gecko, GloshIA Visual R3.1, modelo,
umbral, politica visual, preprocesamiento, extension `1.81.0`, publicidad,
video, scheduler, workers Android ni configuracion ONNX. No se agregaron
excepciones por sitio, URL o dominio.

## Validacion

- `testDagProtectionJs`: 21/21.
- `testDevDebugUnitTest`: correcto.
- `ktlintCheck`: correcto.
- `assembleDevDebug`: correcto.
- Instalacion in-place: correcta, datos preservados.
- Google Imagenes mostro las fotos con DAG 164 instalado.

Con 15 pestanas persistidas, `dumpsys meminfo` reporto:

- selector cerrado: 1 bitmap, 8.505 KiB; PSS 274.477 KiB;
- selector abierto: 5 bitmaps, 25.713 KiB; PSS 287.423 KiB;
- selector vuelto a cerrar: 2 bitmaps, 8.516 KiB.

La memoria temporal de las miniaturas volvio al nivel base inmediatamente al
cerrar el selector. El PSS total posterior no se usa como comparacion estable
porque Gecko continuo trabajando durante la medicion; la cuenta y los bytes de
bitmaps aislan el efecto corregido.

APK DEV:

- paquete: `com.contentfilter.dagbrowser.dev`;
- version: `0.69.68-dev`;
- `versionCode`: 164;
- SHA-256:
  `6a8a759127a39bced7c4e066c5d15cf7dfaab37b837c573097308d239877f7e1`;
- tamano: 129.473.443 bytes.

No se hizo push, publicacion remota ni cambio en Supabase o Production.

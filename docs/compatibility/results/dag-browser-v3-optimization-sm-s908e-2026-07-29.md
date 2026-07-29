# DAG Browser V3 v17 - optimizacion y pestañas en SM-S908E

Fecha: 2026-07-29
Dispositivo: Samsung SM-S908E
Android: 16
Paquete: `com.contentfilter.dagbrowser.dev`
APK: `versionCode 17`, `versionName 0.8.0-dev`
Extension incorporada: `1.15.1`

## Alcance

- selector de pestañas estilo navegador con miniaturas filtradas;
- nueva, seleccionar, cerrar por boton y gesto, reordenar y restaurar;
- sesiones Gecko de fondo inactivas, con media suspendida y prioridad reducida;
- cache efimera de decisiones exactas por SHA-256;
- soporte de fuentes lazy habituales;
- fallback de recursos cacheados o no capturados;
- Mimo, Cheeky, Fravega y Google Imagenes.

No se limpio el perfil antes de esta muestra: son tiempos operativos in-place con cache HTTP
existente, no percentiles frios. Las celdas mantienen el formato
`page_analysis_ready / viewport_images_ready / page_visible`.

## Rendimiento observado

| Destino | Pagina / fotos / visible |
| --- | ---: |
| Fravega | 9.287 / 20.626 / 1.267 ms |
| Mimo | 1.867 / 3.701 / 751 ms |
| Cheeky | 4.576 / 5.566 / 1.983 ms |
| Google Imagenes, primera entrada | 976 / 1.787 / 144 ms |
| Google Imagenes, recarga | 738 / 989 / 187 ms |

La recarga de Google Imagenes no genero ningun nuevo log `media-bytes`: las decisiones exactas se
resolvieron desde la cache en memoria sin repetir inferencias ONNX.

## Compatibilidad visual

- Mimo: la foto principal que antes quedaba transparente aparecio desenfocada; logo y controles
  quedaron visibles.
- Cheeky: pagina, banner, buscador y controles visibles; quietud visual en 5.566 ms.
- Fravega: estructura visible en 1.267 ms mientras las fotos siguieron resolviendose
  progresivamente.
- Google Imagenes: las fotos rechazadas conservaron tamaño y aparecieron desenfocadas; no se
  observaron rectangulos transparentes en los resultados principales.

La causa de Mimo era que GeckoView no siempre aporta `sender.tab.id` a mensajes del content script.
El fallback los descartaba, por lo que recursos servidos desde cache nunca llegaban al analizador.
La correccion autentica `sender.id` contra `browser.runtime.id`, que si esta disponible en el canal
aislado, y conserva el fallo cerrado.

## Pestañas y memoria

- Miniatura real filtrada comprobada en el selector.
- Cierre por deslizamiento comprobado fisicamente.
- Cinco pestañas conservaron cantidad, orden, titulo, URL e indice activo despues de detener y
  volver a abrir el proceso.
- La primera implementacion de restauracion revelo que abrir todas las sesiones generaba un
  `about:blank` que pisaba URLs de fondo. El candidato final abre solo la activa y difiere las
  restantes hasta seleccionarlas.
- Cinco sesiones abiertas durante la prueba: 305.478 KiB PSS, 480.360 KiB RSS.
- Cinco pestañas restauradas con solo la activa abierta: 226.383 KiB PSS, 372.284 KiB RSS.
- Bitmaps de miniatura: siete asignaciones, aproximadamente 955 KiB en la medicion.

Las miniaturas no se persisten ni se sincronizan. SharedPreferences guarda solo URL, titulo, orden
e indice activo; `allowBackup` permanece deshabilitado.

## Validacion automatica y artefacto

- `node --check` correcto para `background.js` y `barrier.js`;
- XML y manifiesto de extension validos;
- `ktlintCheck` correcto;
- 53 unitarios correctos;
- `assembleDevDebug` y Android Lint vital correctos;
- sin crash ni ANR en el recorrido fisico.

APK:

```text
app-dag-browser/build/outputs/apk/dev/debug/DagBrowser-dev-debug.apk
121088287 bytes
SHA-256 6cfe35c70a69ed5d0db0a0e64a21ca55939f8c14ba9ad0a9d5f43d22d8af8b08
```

Certificado DEV:

```text
d51bc0dabd280ce1b0f098ae168eb57758faeba301156cde835737835f8a8832
```

El APK quedo instalado in-place en el SM-S908E. No se publico, no se modificaron App Usuario,
App Admin, Supabase, Production ni iCloud, y no se borraron datos de navegacion.

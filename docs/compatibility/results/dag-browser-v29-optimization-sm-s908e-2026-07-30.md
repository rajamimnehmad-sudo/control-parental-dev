# DAG Browser 29 - optimización y presentación de imágenes

Fecha: 2026-07-30

Dispositivo: Samsung SM-S908E

Android: 16

Paquete: `com.contentfilter.dagbrowser.dev`

Versión: `versionCode 29`, `0.19.0-dev`

Extensión integrada: `1.22.0`

## Alcance

- `DAG-V3-PRIVATE-DIAGNOSTICS-06`
- `DAG-V3-FALSE-ALLOW-08`, candidato panorámico sin cambio de umbral global
- `DAG-V3-FRAME-STABILITY-10`
- `DAG-V3-MEDIA-PRESENTATION-11`

## Resultado físico

- Los registros `DagMediaTransport` de Frávega, Mimo, Cheeky, Google Imágenes
  e Instagram no incluyeron URL, texto alternativo, búsquedas ni estados DOM.
- La barrera dejó de recorrer todo el documento ante cada mutación o decisión.
  En la medición Android, Frávega registró 83 cuadros y cero tardíos; Cheeky,
  127 y cero; Mimo tuvo dos tardíos sobre una muestra corta de 20 cuadros.
- Mimo quedó visible en 231 ms y con imágenes del viewport listas en 1.693 ms.
  Frávega quedó visible en 1.348 ms y con viewport listo en 9.670 ms. El sitio
  continuó cargando recursos dinámicos, pero el desplazamiento fue fluido.
- La respuesta tardía de una fuente reemplazada ahora reconcilia el estado
  actual del elemento. Un visual permitido también limpia esperas heredadas de
  sus contenedores.
- Se eliminó el texto `Analizando…`. Una imagen pendiente usa un brillo barrido
  neutro; una filtrada conserva desenfoque y escudo. Frávega y Mimo quedaron sin
  leyendas residuales después de desplazarse.
- En Google Imágenes para una consulta actual de moda y trajes de baño, la ronda
  produjo 25 decisiones `model_filter`, 10 presentaciones bloqueadas y 12
  permisos. La captura visual confirmó el desenfoque preventivo.
- Instagram abrió su web pública, pero el propio sitio mostró su muro para
  abrir la aplicación. No se reprodujo la variante rotativa exacta señalada
  previamente; por eso `DAG-V3-FALSE-ALLOW-08` conserva esa aceptación
  pendiente y no se declara cerrado.
- No hubo crash, ANR ni pérdida del navegador predeterminado o de Accessibility.
  Las pestañas técnicas se cerraron y el teléfono volvió a sus 17 pestañas
  originales.
- `Acerca de DAG` mostró físicamente `Versión 0.19.0-dev (29)`.

## Validación local

- `node --check` para `barrier.js` y `background.js`.
- 99 pruebas unitarias DEV.
- `ktlintCheck`.
- `lintDevDebug`.
- `assembleDevDebug`.

APK construida desde `main` local:

```text
app-dag-browser/build/outputs/apk/dev/debug/DagBrowser-dev-debug.apk
121182526 bytes
SHA-256 c9d4b616a7be18daea1e758750a5913d18717530d1b3763001e8b60e02d0997a
```

La APK quedó instalada in-place en SM-S908E. No se borraron datos ni
configuración. No hubo push, publicación DEV, cambios en Supabase ni cambios en
App Usuario o App Admin.

## Pendientes separados

- Reproducir la variante panorámica exacta de Instagram para cerrar
  `DAG-V3-FALSE-ALLOW-08`.
- `DAG-V3-DOCUMENT-ISOLATION-07`.
- `DAG-V3-TAB-HIBERNATION-09`.


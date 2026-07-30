# DAG Browser 33 - foto filtrada sin overlay

Fecha: 2026-07-30

Dispositivo: Samsung SM-S908E

Android: 16

Paquete: `com.contentfilter.dagbrowser.dev`

Versión: `versionCode 33`, `0.23.0-dev`

Extensión integrada: `1.23.0`

## Alcance

Las fotos rechazadas conservan el desenfoque fuerte de la imagen, pero ya no
crean el escudo con ✓ sobre el contenedor. Al llegar la decisión final de
bloqueo, DAG también libera el rastreo del contenedor. La descripción accesible
`Protegida por Glosh` permanece para lectores de pantalla.

El brillo barrido durante la espera y el aviso de error técnico continúan
separados y no fueron modificados.

## Validación automática

- `node --check` para `barrier.js` y `background.js`.
- 104 pruebas unitarias DEV.
- Contrato explícito que prohíbe el overlay `filtered` y su `clip-path`.
- `ktlintCheck`.
- `lintDevDebug`.
- `assembleDevDebug`.

APK construida desde `main` local:

```text
app-dag-browser/build/outputs/apk/dev/debug/DagBrowser-dev-debug.apk
121208250 bytes
SHA-256 6e6810ba1e562664ef75d67493c10a620620ee47ab1af21791d901529963444d
```

## Matriz física

La APK se instaló in-place. Antes de cada URL se borró solamente la caché Gecko
desde el control propio de DAG y se usó un `codexperf` distinto.

| Sitio | Página lista | Fotos iniciales | Visible | Resultado |
| --- | ---: | ---: | ---: | --- |
| Frávega | 1.931 ms | 4.813 ms | 263 ms | Completó |
| Mimo | 1.335 ms | 1.786 ms | 119 ms | Completó |
| Cheeky | 2.821 ms | 3.815 ms | 595 ms | Completó |

Una búsqueda visual de control produjo 22 decisiones `model_filter` y 17
presentaciones `block`. La inspección física confirmó que el escudo/✓ ya no
aparece. No hubo crash. DAG continuó como navegador predeterminado y
Accessibility de App Usuario permaneció activa.

No hubo push, publicación DEV, cambios en Supabase, App Usuario ni App Admin.

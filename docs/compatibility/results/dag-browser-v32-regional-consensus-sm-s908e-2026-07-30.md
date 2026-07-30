# DAG Browser 32 - consenso regional

Fecha: 2026-07-30

Dispositivo: Samsung SM-S908E

Android: 16

Paquete: `com.contentfilter.dagbrowser.dev`

Versión: `versionCode 32`, `0.22.0-dev`

## Causa y corrección

Desde DAG 29, las imágenes con relación extrema desde `2:1` reciben tres
vistas regionales. La regla bloqueaba toda la imagen cuando una única vista
alcanzaba `0,50`, incluso si las otras vistas y la imagen completa resultaban
permitidas. Esto aumentaba falsos bloqueos en banners y composiciones
panorámicas.

DAG 32 conserva el mismo modelo y el umbral global `0,40`. La decisión regional
ahora bloquea con dos vistas desde `0,50`, o con una única vista fuerte desde
`0,70`. Los fallos técnicos y resultados inválidos continúan cerrados por
seguridad.

## Validación automática

- 104 pruebas unitarias DEV.
- Pruebas nuevas para una señal marginal, consenso de dos señales y señal única
  fuerte.
- `ktlintCheck`.
- `lintDevDebug`.
- `assembleDevDebug`.

APK construida desde `main` local:

```text
app-dag-browser/build/outputs/apk/dev/debug/DagBrowser-dev-debug.apk
121208374 bytes
SHA-256 d96919c6590448e78e9f6ab21ec975097e5492ead016a4fccf65d27e0f38ba7e
```

## Matriz física

La APK se instaló in-place. Antes de cada URL se usó `Borrar caché` de DAG, que
limpia únicamente la caché Gecko, y un parámetro `codexperf` distinto.

| Sitio | Página lista | Fotos iniciales | Visible | Resultado |
| --- | ---: | ---: | ---: | --- |
| Frávega | 9.942 ms | No completó en 35 s | 1.433 ms | Página visible; quietud visual incompleta |
| Mimo | No completó en 35 s | No completó en 35 s | 373 ms | Página visible; quietud incompleta |
| Cheeky | 5.333 ms | 5.625 ms | 1.791 ms | Completó |

Cheeky produjo una decisión permitida con máximo regional `0,5366`. Como el
umbral de la imagen completa sigue en `0,40`, ese permiso confirma que se trató
de una única región marginal y que el falso bloqueo anterior ya no se aplica.
No hubo crash. DAG continuó como navegador predeterminado y Accessibility de
App Usuario permaneció activa.

La quietud incompleta de Frávega y Mimo queda registrada como un problema
separado de carga/entrega; no invalida la prueba dirigida de consenso regional
ni se corrige alterando nuevamente los umbrales.

No hubo push, publicación DEV, cambios en Supabase, App Usuario ni App Admin.

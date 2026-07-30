# DAG Browser 21 - primer lote de estabilidad

Fecha: 2026-07-29

Dispositivo: Samsung SM-A235M

Android: 14

Paquete: `com.contentfilter.dagbrowser.dev`

Versión: `versionCode 21`, `0.11.0-dev`

## Alcance

- `DAG-IMAGE-INDICATOR-LIFECYCLE-07`
- `DAG-UI-RESOURCE-COMPAT-08`
- `DAG-BACK-NAV-01`

## Resultado físico

- Cheeky categoría/licencias mostró corazones neutros y funcionales en cada
  tarjeta. El sprite original `wishlist-heart.png` es un PNG animado de
  `6144 x 64`; continuó cerrado y no se convirtió en excepción de imagen.
- Los círculos y fotos ya resueltos de Cheeky dejaron de conservar la leyenda
  `Analizando`.
- Desde una URL directa, Atrás volvió a Home sin sacar DAG del frente. Desde
  Home, Atrás reanudó App Usuario. La política prioriza Home incluso cuando
  Gecko conserva historial residual.
- No se observaron crash, ANR ni OOM.

## Matriz obligatoria

Cada muestra borró el perfil DEV y usó un parámetro `codexperf` nuevo.

| Sitio | Página lista | Fotos iniciales | Visible | Observación |
| --- | ---: | ---: | ---: | --- |
| Frávega | 20.258 ms | 20.487 ms | 1.347 ms | Completó |
| Mimo | 5.638 ms | 6.224 ms | 594 ms | Completó |
| Cheeky Home | No completó en 45 s | No completó en 45 s | 1.671 ms | Sitio visible; quietud no emitida |

La comprobación visual de los dos bugs de Cheeky se hizo además en
`/categorias/licencias-cheeky`, donde la categoría alcanzó quietud en la
iteración previa del mismo lote. La ausencia de quietud de Home se conserva
como limitación observable y no se oculta como éxito.

## Validación local

- `node --check` para la barrera JavaScript.
- `ktlintCheck`.
- 71 pruebas unitarias.
- `assembleDevDebug`.
- `lintDevDebug`.
- Instalación in-place y luego instalación limpia en SM-A235M.

APK construida desde `main`:

```text
app-dag-browser/build/outputs/apk/dev/debug/DagBrowser-dev-debug.apk
121111194 bytes
SHA-256 9532ca6e0a451cc0cddf7bb673e852b98e61f55aad7d1cef025cfc4c9ceaef66
```

No hubo push, publicación DEV, cambios en Supabase ni cambios funcionales en
App Usuario o App Admin.

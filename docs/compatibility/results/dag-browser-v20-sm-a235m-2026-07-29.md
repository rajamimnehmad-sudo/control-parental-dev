# DAG Browser 20 - gate físico SM-A235M

Fecha: 2026-07-29  
Dispositivo: Samsung SM-A235M  
Distribución: DEV local, sin publicación remota

## Versiones

- App Usuario: `versionCode 295`, `1.0.1-dev`
- App Admin: `versionCode 284`
- DAG Browser: `versionCode 20`, `0.10.0-dev`

Las tres APK se instalaron in-place. DAG quedó como titular del rol
`android.app.role.BROWSER` y el servicio
`ProtectorAccessibilityService` quedó habilitado.

## Corrección validada

La primera corrida reveló dos evaluaciones consecutivas para DAG:
`protected-browser` lo permitía, pero el detector genérico de aplicaciones
desconocidas podía bloquearlo inmediatamente. Se centralizó la allowlist de
primer plano y se añadieron las variantes `release`, `dev` y `beta` del paquete
DAG.

Después de instalar Usuario 295:

- DAG permaneció en primer plano.
- No reaparecieron los logs `Unknown foreground app detected` ni
  `Blocking foreground app immediately` para DAG.
- Chrome y las aplicaciones desconocidas no ingresaron en la allowlist.

## Navegación y tiempos observados

| Caso | Página visible | Análisis listo | Resultado |
| --- | ---: | ---: | --- |
| Google Imágenes, caché | 306-390 ms | 485-564 ms | DAG permaneció visible |
| H&M hombre | 1.056 s | 7.533 s | navegación correcta; hero difuminado |
| Cheeky | 2.167 s | 8.307 s | navegación correcta; raster analizado |
| Instagram | 501 ms | 6.215 s | página oficial y controles visibles |
| YouTube inicio | 468 ms | 1.681 s | página visible; sin audio activo |

Cheeky produjo decisiones `model_allow` y algunos `unsupported_image` para
recursos diminutos o formatos no analizables. No se observó crash, ANR ni salida
forzada de DAG.

## Interfaz

- El selector mostró ocho pestañas en tarjetas de dos columnas, con alta,
  cierre, deslizamiento y reordenamiento explicados en pantalla.
- Inicio volvió a la pantalla `Navegación protegida`.
- Atrás, sin historial navegable, mantuvo DAG en primer plano.

## Validación local

Se ejecutaron correctamente:

```text
:feature-accessibility:test
:feature-accessibility:ktlintCheck
:app-user:testDevDebugUnitTest
:app-user:ktlintCheck
:app-user:assembleDevDebug
```

## Observaciones no bloqueantes

- Revisar el hero promocional de H&M como caso de calibración: el filtro
  completo puede ser conservador aunque la página funcione.
- Ampliar corpus de moda masculina actual antes de modificar umbrales.
- Recursos AVIF estáticos están cubiertos; SVG remoto, animaciones y
  decodificaciones inseguras continúan fail-closed por diseño.

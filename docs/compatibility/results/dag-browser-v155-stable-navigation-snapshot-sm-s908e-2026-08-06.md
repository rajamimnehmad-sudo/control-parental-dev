# DAG 155 - captura estable de navegación

Fecha: 2026-08-06
Dispositivo: Samsung SM-S908E, Android 16, arm64-v8a
APK: `0.69.59-dev`, `versionCode 155`
Extensión: `1.73.0`
APK SHA-256: `4ac86de40d0a971d247b93aba2bf8135029b93f0bebf38e821d04416e864a3c7`

## Síntoma

Al cambiar rápidamente de Imágenes a Todo en Google aparecía una ráfaga muy
corta. No era un bloqueo de GloshIA, una imagen negra ni falta de FPS.

## Diagnóstico físico

Una grabación y 18 capturas consecutivas alrededor del toque mostraron esta
secuencia:

1. página Imágenes estable;
2. página anterior comprimida por un reflow;
3. layout inicial de Todo;
4. contenido final.

Logcat confirmó que Gecko emitía `PageStart`, ocultaba su `SurfaceView` y DAG
revelaba la página protegida en aproximadamente 249-261 ms. La cobertura se
activaba recién en `onPageStart`, después de que el sitio ya hubiera podido
modificar el DOM visible.

La primera candidata, DAG 154, mostró el snapshot al aceptar la navegación.
La ráfaga seguía incluyendo el cuadro comprimido. Los registros de
`DagTabPreview` confirmaron que existían capturas posteriores estables, pero
`updateNavigationFrame` rechazaba cualquier actualización de la misma
`navigationRevision`; por eso la captura intermedia quedaba congelada.

## Corrección final

- `maybeCoverAcceptedNavigation` muestra el snapshot protegido antes de iniciar
  la carga cubierta.
- `updateNavigationFrame` acepta una captura segura más reciente aunque
  pertenezca a la misma revisión; sigue rechazando pestañas inactivas y páginas
  restringidas.
- No se agregó espera fija, fundido, excepción de Google, dominio o URL.

La segunda ráfaga eliminó el cuadro comprimido. El propietario repitió la
interacción en DAG 155 y confirmó que quedó correcta.

## Seguridad y alcance

La captura sólo procede de una página previamente visible y elegible según la
política de previews. La nueva página permanece cubierta hasta confirmar la
barrera y contenido protegido. No cambian modelo, umbral, política visual,
`final_sealed`, filtros ni decisiones de imagen.

## Validación

- Prueba contractual: la cobertura ocurre antes de `beginProtectedLoad` y no se
  vuelve a congelar un frame por revisión.
- `testDagProtectionJs`: 21/21.
- `testDevDebugUnitTest`: correcto.
- `ktlintCheck`: correcto.
- `lintDevDebug`: correcto.
- `assembleDevDebug`: correcto.
- Instalación `adb install -r`: correcta; perfil y datos preservados.

# DAG Browser V3 foundation - Samsung SM-A235M - 2026-07-27

## Corte probado

- Rama: `codex/dag-browser-v3-foundation-01`.
- Base aislada: `origin/main` en `105c93c`.
- Aplicacion: `com.contentfilter.dagbrowser.dev`.
- Version: `0.1.0-dev` (`versionCode 1`).
- Dispositivo: Samsung SM-A235M, `arm64-v8a`.
- Android: 14, API 34.
- APK DEV debug: `102563986` bytes.
- SHA-256: `1fa20edcd2a88624e502e61d29836d9d994a45a54c72698396a58966e98c40a7`.
- Firma: debug local. No es un artefacto de publicacion.

## Verificacion automatica

Comando del build Gradle independiente:

`testDevDebugUnitTest ktlintCheck lintDevDebug assembleDevDebug`

Resultado: correcto, 68 tareas, sin fallas.

Los tests cubren:

- conversion de entrada a HTTPS;
- rechazo de esquemas no permitidos;
- SafeSearch obligatorio en Google;
- reemplazo de `safe=off`;
- contrato estatico de la barrera incorporada.

## Verificacion fisica

| Caso | Resultado |
| --- | --- |
| Arranque frio | Abre en la pantalla nativa cerrada y lista; no hay crash |
| Google `ropa formal` | URL con `safe=active`; texto visible; imagenes ausentes |
| Fravega | Texto y navegacion visibles; imagenes ausentes |
| Mimo | Texto, promociones y precios visibles; imagenes ausentes |
| Cheeky | Texto, precios y navegacion visibles; imagenes ausentes |
| Recarga de Mimo | Ocho capturas consecutivas sin foto ni destello visible |
| Sin conexion | La superficie queda cerrada al vencer la confirmacion de barrera |
| Recuperacion de conexion | Recarga correcta; Cheeky vuelve solo con texto |
| Background/foreground | Regresa a la pantalla protegida sin crash |
| Reinicio del proceso | Arranque correcto en estado cerrado |

No se observaron `FATAL EXCEPTION` ni ANR de la aplicacion durante este corte.

## Lectura del gate

La foundation demuestra el comportamiento fail-closed y el bloqueo visual sin IA en el dispositivo
objetivo. Antes de conectar Glosh siguen pendientes:

- una prueba dirigida de enlaces `target=_blank`;
- repetir el gate completo sobre una APK firmada candidata;
- decidir el canal del tercer artefacto DEV, porque esta APK de GeckoView supera los 100 MB sin
  comprimir y el flujo cloud historico solo contempla App Usuario y App Admin.

No se modifico ni se retiro el DAG vigente.

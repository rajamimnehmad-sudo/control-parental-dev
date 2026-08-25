# CHROME-ACCESSIBILITY-EVENT-BACKPRESSURE-12A

## Coordinacion y alcance

- Task: `CHROME-ACCESSIBILITY-EVENT-BACKPRESSURE-12A — LOCAL GATE ONLY`.
- Owner: Codex / Proteccion Android.
- Base verificada: `afcfbe53f09344c47671f3b313929c8dd38d3143`.
- Candidato recibido: `ab5d42a27869118235888c17551146869db4565c`.
- Rama: `work/chatgpt-chrome-accessibility-backpressure-12a`.
- Worktree: `/Users/yejielnehmad/Developer/glosh-chrome-accessibility-backpressure-12a`.
- `work/chrome-general-web-functional-12` en `bf5f6835...` fue preservado y no se modifico.
- Glosh Central fue consultado y no se modifico.

## Ajustes mecanicos del candidato

- Se corrigio formato ktlint exclusivamente dentro del scope del candidato.
- Un test detecto que `UninstallerActivity` podia confundirse con una pantalla de instalacion por el nombre completo del paquete `packageinstaller`. La deteccion ahora usa el nombre simple y excluye explicitamente hints de desinstalacion, manteniendo la identidad obligatoria para App Info/removal.
- El `notificationTimeout=50` se movio al recurso DEV efectivo. El recurso DEV anterior (`0`) sobreescribia el recurso main y hacia que el runtime siguiera exponiendo `notificationTimeout=0`.
- Version DEV: `351` (`1.0.1-dev`), maximo real previo `350` + 1.

## Gates automaticos

- `./gradlew :feature-accessibility:test`: PASS, 150 tests (Debug y Release).
- `./gradlew :feature-accessibility:ktlintCheck`: PASS.
- `./gradlew :app-user:compileDevDebugKotlin`: PASS.
- `./gradlew :app-user:runKtlintCheckOverDevSourceSet`: PASS.
- `./gradlew :app-user:lintDevDebug`: PASS.
- `./gradlew :app-user:assembleDevDebug`: PASS.
- `git diff --check`: PASS.
- Warnings observados: deuda heredada de toolchain/API deprecated y anotaciones Kotlin fuera del scope; no se detecto warning nuevo atribuible a 12A.

## APK

- Ruta: `/Users/yejielnehmad/Developer/glosh-chrome-accessibility-backpressure-12a/app-user/build/outputs/apk/dev/debug/app-user-dev-debug.apk`.
- Version: `351 / 1.0.1-dev`.
- SHA-256: `487ef5895717fd596bc64a2464f9e285edbccd8aa90802cd4987db0df5313323`.
- Tamano: `158893381` bytes.
- Instalacion: `adb install -r`; no uninstall, no clear, no reset.
- El XML compilado del APK y el descriptor activo despues de reboot acreditaron `notificationTimeout=50`.

## Gate fisico A23

- Device: Samsung `SM-A235M`, Android 14 / API 34, serial lab `R58T34V31AE`.
- La primera sesion posterior al reboot fue excluida: durante la tormenta de boot Accessibility perdio health transitoriamente y el guard hizo fail-close (`no_current_session`, Chrome suspendido). Se ejecuto STOP y luego una unica sesion limpia cuando el dispositivo quedo estable.
- Sesion valida: guard generation `29`, lease `current`, Chrome liberado, full tunnel generation `2`, presentacion ready.
- Secuencia: Google Search con URL fresca, Google Images, Wikipedia y rafagas de scroll/navegacion sin `uiautomator`.
- Search protection siguio activa y reconocio Google; no hubo bypass de politica.
- Backpressure observado:
  - maximo registrado `submitted=113`, `started=102`, `coalesced=7`;
  - `scanNodes` observado entre 1 y 13 en Search;
  - `timeBudget=true` corto scans costosos sin bloquear la UI;
  - `nodeBudget=false` en las muestras del gate.
- Settings processor observado hasta `submitted=31`, `started=25`, `coalesced=6`, `scanNodes` hasta 39.
- UI: responsiva durante launches, scroll y cambios de pantalla.
- ANR: `0`; `dumpsys activity lastanr` informo `<no ANR has occurred since boot>`.
- Crash Java/native, OOM: `0 / 0 / 0` atribuibles.

## Protecciones y regresiones

- Accessibility Settings: expulsada al launcher.
- VPN Settings: protegida/expulsada.
- App Info/removal de App Usuario: protegida/expulsada.
- Device Admin action generica no resolvio en este firmware; la ruta de policy permanece cubierta por tests.
- Admin/Unknown Sources no se conto como excepcion fisica: una invocacion ADB no posee la ventana firmada de instalacion confiable requerida, por lo que el fail-close al launcher fue el comportamiento seguro.
- Guard 10B: healthy durante la sesion valida; Chrome suspendido al STOP.
- `rawPresented=false`, `stale=0` (sin evidencia stale), `captureRequestsSincePresentationReady=0`, `errorCode3=0`.
- Proxy: `proxyQueueRejects=0`, `queueRejects=0` de inferencia.
- Proxy `failures=8`: cierres `SSLHandshakeException` observados durante navegacion real; no fueron rechazos de cola, ANR ni bypass y quedan fuera del scope de 12A.
- Transport: `ownerTimeouts=0`, `ownerQueueDrops=0`, `queueDrops=0`, `recursion=0`, `protectFailures=0`.
- Chrome direct TCP/443 fue observado y `DROP` fail-closed.

## Rollback y preservacion

- STOP normal: guard invalidado, Chrome suspendido, proxy/CA retirados.
- Transport final: `status=inactive`, `ownedFdResources=0`, `activeProtectedUdpSockets=0`, runtime `ready`.
- VPN/DNS productivos restaurados; rutas DEV retiradas por el flujo de rollback.
- App Usuario: `versionCode=351`, `ceDataInode=1239519` preservado.
- Bootstrap: `resetCount=1` preservado.
- Device Owner: `com.contentfilter.user.dev/com.contentfilter.feature.accessibility.service.ProtectionDeviceAdminReceiver` preservado.
- Accessibility: instalada, enabled y bound; descriptor activo `notificationTimeout=50`.

## Resultado

PASS tecnico Codex para 12A. La correccion reduce trabajo del callback principal mediante coalescing, scans bounded y procesamiento fuera del main thread, y el patron fisico que habia producido ANR no lo reprodujo en DEV351.

Residual declarado: los `SSLHandshakeException` del proxy y su contador `failures=8` se registran para el frente HTTP/proxy; `proxyQueueRejects=0` y no se modifico proxy en 12A.

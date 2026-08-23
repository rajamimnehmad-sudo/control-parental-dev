# CHROME-PHOTOS-PRESENTATION-INDEPENDENCE-01 — evidencia final

Fecha de cierre: 2026-08-23
Owner: Proteccion Android / Codex
Base: `25d326914496f2874989211f4bf891c2c0fea7ab`
Commit funcional: `993528b17be60ed38d6221d347a019000e2b57ce`
Rama/worktree: `work/chrome-photos-presentation-independence-01`
Estado: **PASS fisico Codex, pendiente de revision final ChatGPT**

## Causa raiz y correccion

`ChromeVisualProbeController` trataba cada `TYPE_VIEW_SCROLLED` y cada
`TYPE_WINDOW_CONTENT_CHANGED` como una invalidacion completa: revocaba la lease,
incrementaba el epoch, cubria la ventana y solicitaba `takeScreenshotOfWindow`, aun
cuando la lease y la atestacion del data-plane seguian sanas en el mismo contexto.

La correccion agrega una politica pura y determinista que distingue entre eventos
visuales normales del mismo contexto y cambios reales de contexto. Con data-plane
verificado, lease vigente, host transparente y misma ventana/geometria, scroll y
contenido conservan la presentacion sin captura. La perdida de salud, salida de Chrome,
cambio de ventana/geometria, rotacion o contexto siguen revocando la lease y cubriendo
fail-closed. Sin data-plane se conserva el probe previo.

Se agregaron metricas DEV explicitas de `captureRequests`, `captureSuccess`,
`captureFailures`, `errorCode3` y `captureRequestsSincePresentationReady`. No se
registran ni persisten imagenes.

## Archivos funcionales

- `app-user/build.gradle.kts`: versionCode DEV 321.
- `feature-accessibility/src/main/java/com/contentfilter/feature/accessibility/chromevisual/ChromePhotosPresentationIndependence.kt`.
- `feature-accessibility/src/main/java/com/contentfilter/feature/accessibility/chromevisual/ChromeVisualProbeController.kt`.
- `feature-accessibility/src/test/kotlin/com/contentfilter/feature/accessibility/chromevisual/ChromePhotosPresentationIndependencePolicyTest.kt`.

El controlador queda en 608 lineas porque sigue siendo el unico orquestador del
lifecycle Android; la decision pura y las metricas se extrajeron a un archivo cohesivo.
No se amplio el refactor fuera del ticket.

## Gates automaticos finales

- `:feature-accessibility:testDebugUnitTest`: PASS.
- `:feature-accessibility:testReleaseUnitTest`: PASS.
- `:feature-accessibility:ktlintCheck`: PASS.
- `:app-user:testDevDebugUnitTest --tests 'com.contentfilter.user.chromedataplane.*'`: PASS.
- `:app-user:lintDevDebug`: PASS.
- `:app-user:compileDevDebugKotlin`: PASS.
- `:app-user:assembleDevDebug -x uploadDevUpdatesToStorage -x prepareDevUpdatesForStorage`: PASS.

La primera corrida de ktlint detecto solamente indentacion en el test nuevo; se
corrigio y el gate final paso. No se recompilo despues de la sesion fisica.

## APK fisica

- Paquete: `com.contentfilter.user.dev`.
- versionCode: 321.
- versionName: `1.0.1-dev`.
- SHA-256: `48c751b0b11542fa52e2001bf2640b4a6fab39a7e28d0301309d370918fca586`.
- Firma DEV: `d51bc0dabd280ce1b0f098ae168eb57758faeba301156cde835737835f8a8832`.
- Instalacion in-place: `Success`; `ceDataInode=1239519` preservado.
- APK construida una sola vez y no publicada.

## Gate fisico A23

- Samsung SM-A235M, serial `R58T34V31AE`, Android 14/API 34, Chrome 151.
- Glosh confirmado `DeviceOwner,Affiliated`; Accessibility habilitado, ligado y sin
  servicios caidos.
- Fixture controlada: SAFE visible sin alteracion; sentinel y lazy-sentinel reemplazados
  por Glosh. Lazy sentinel: 5237 bytes de entrada, 6303 de salida, decision `block`,
  cache hit.
- Stress: 260 gestos ADB alternados durante aproximadamente dos minutos y 1632 eventos
  reales `TYPE_VIEW_SCROLLED` registrados.
- Durante todo el tramo estable: epoch 13, host simultaneo 1, `attachmentCount=1`,
  `captureRequests=0`, `captureSuccess=0`, `captureFailures=0`, `errorCode3=0` y
  `captureRequestsSincePresentationReady=0`.
- No hubo fase de captura, commit, revocacion de lease ni cobertura opaca causada por
  scroll durante el tramo estable.
- Metricas del data-plane al cierre del stress: connections 10, requests 1034, safe 3,
  blocked 2, unknown 0, passthrough 1029, cacheHits 3, cacheMisses 2, QUIC 0 y TCP
  directo 0. Las cinco fallas fueron timeouts no-fixture fail-closed.
- `rawPresented=true`: 0. Commits stale: 0. Crash: 0. ANR: 0.

Antes de `presentation_ready` el fallback previo realizo capturas mientras la atestacion
aun no era valida (dos en el primer arranque y una en la segunda sesion). Esto queda
fuera del camino saludable y es el comportamiento requerido cuando el data-plane no
esta listo. Despues de cada `presentation_ready`, el contador vuelve a cero y permanece
en cero.

## Fail-closed, nueva sesion y reentrada

Con Chrome visible y la superficie transparente, `STOP` produjo `fail_closed` a
1787457933.072 y la superficie opaca a 1787457933.091: **19 ms**, debajo del limite de
750 ms. El host siguio adjunto, `NOT_TOUCHABLE`, `attachmentCount=1` y
`rawPresented=false`.

La sesion inicial `e590ab00` fue reemplazada por `d36d443e`; la lease anterior no se
reutilizo. Al salir de Chrome se revoco por `chrome_absent` y se desarmo el host. Al
reingresar se crearon epochs 30 y 31, con lease nueva; el `attachmentCount=2` es
acumulativo despues de recrear el unico host, nunca dos hosts simultaneos.

## Rollback y estado final

El cierre ejecuto primero `fail_closed`, detuvo el laboratorio y registro
`rollback=complete proxy=cleared ca=removed`. No queda transporte VPN activo.
`chrome://policy` muestra dos veces `No hay politicas establecidas.` Device Owner,
Accessibility y el inode de datos de App Usuario permanecen preservados.

## Evidencia local

- Log inicial: `/private/tmp/CHROME-PHOTOS-PRESENTATION-INDEPENDENCE-01-DEV321-A23-initial-logcat.txt`, SHA-256 `5a52ebbdefa35f5e9b67f77d5c1aa618abad40192b4ff7a22d83118b7f96d162`.
- Stress: `/private/tmp/CHROME-PHOTOS-PRESENTATION-INDEPENDENCE-01-DEV321-A23-scroll-stress-logcat.txt`, SHA-256 `3334d124344d4620abb63b5eefeffe4f2591f773194376b49d8e2dad210fb7d1`.
- Fail-closed: `/private/tmp/CHROME-PHOTOS-PRESENTATION-INDEPENDENCE-01-DEV321-A23-fail-closed-logcat.txt`, SHA-256 `d88914c0856b39789f3344c310b0d67ab33da80a644c1c20f702b4eff43079c5`.
- Nueva sesion: `/private/tmp/CHROME-PHOTOS-PRESENTATION-INDEPENDENCE-01-DEV321-A23-new-session-logcat.txt`, SHA-256 `8bcc25989d1f1bcf5a3b71dc2b05fae0c392012e0ea67cc513e1f3763e813dd7`.
- Salida/reentrada: `/private/tmp/CHROME-PHOTOS-PRESENTATION-INDEPENDENCE-01-DEV321-A23-exit-reentry-logcat.txt`, SHA-256 `823fbeafe2bd98816138fc31aa7460e62e1f9690b64da23bf5fa3d94ab04d125`.
- Cierre: `/private/tmp/CHROME-PHOTOS-PRESENTATION-INDEPENDENCE-01-DEV321-A23-final-logcat.txt`, SHA-256 `de6e8cb325e51be97b5634609d77a84f6e7186afc6c1b508076315597e52cd8e`.
- Chrome policy: `/private/tmp/CHROME-PHOTOS-PRESENTATION-INDEPENDENCE-01-DEV321-chrome-policy.xml`, SHA-256 `492bc2d627331468ec4289cc3302b41287bc1164e1a9c03772d6db372c888730`.
- Fixture inicial: `/private/tmp/chrome-presentation-independence-dev321-initial.png`, SHA-256 `fca17ef069e47289b828ea60cab1c9d41052aa8635fe17727baf3b2cf0cd1499`.
- Lazy bloqueado: `/private/tmp/chrome-presentation-independence-dev321-lazy.png`, SHA-256 `9f927b4ad6a06a7c028b006d4895a24e397c6fe2396c91d62af4dbe26179478a`.
- Fail-closed opaco: `/private/tmp/chrome-presentation-independence-dev321-fail-closed.png`, SHA-256 `7b19fd1dff09ba441b1f856b14dbf2cd1dd8088fca77f12092bbf255f7da06f2`.

## Riesgo residual

El gate usa exclusivamente la fixture controlada, como exige el ticket; no demuestra
web real. `uiautomator` provoco un rebind del servicio antes del stress, por lo que el
tramo se midio desde el nuevo `presentation_ready`. Tambien puede emitirse dos veces el
log `presentation_ready` para el mismo epoch por evento y `HOST_READY`; no adjunta un
segundo host, no solicita captura y no reutiliza una lease vieja.

No avanzar automaticamente a `CHROME-PHOTOS-REAL-WEB-01`, GloshIA ni otro ticket.

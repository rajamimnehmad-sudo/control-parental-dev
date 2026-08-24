# CHROME-PHOTOS-FULL-RESET-BOOTSTRAP-05

Estado Codex: **PASS DEV**. No Production.

## Base y alcance

- Base: `be71998aa6e0a95dd37a641655438018bae0f4c2`.
- Rama: `work/chrome-photos-full-reset-bootstrap-05`.
- Owner de escritura: Protección Android / Codex.
- Sólo se borraron los datos locales de `com.android.chrome`, con autorización
  explícita. No se borraron otras apps, datos de Glosh, cuentas Android ni datos del
  dispositivo.
- GloshIA Visual R3.1, policy `dag-36`, modelo
  `tinyclip-r3-head-hybrid-int8.onnx`, SHA-256
  `c8b64af8092d3718c58736a511c996d0d443dacf3eaa74620b1e5af439a3cd48`.
  Modelo, thresholds, preprocessing y mapping no fueron modificados.

## Implementación

`ChromePhotosTrustedBootstrapController` implementa el bootstrap DEV/Device Owner:

1. suspende Chrome mediante `DevicePolicyManager.setPackagesSuspended`;
2. ejecuta una vez `DevicePolicyManager.clearApplicationUserData` exclusivamente para
   `com.android.chrome`;
3. persiste generación de reset, generación completa y contador;
4. mantiene Chrome suspendido mientras falte proxy, policy, VPN, GloshIA,
   Accessibility o fail-safe;
5. libera Chrome sólo con health completo y verificable;
6. STOP/fatal vuelve a suspender Chrome antes del cleanup.

Un receiver `directBootAware` suspende Chrome en `LOCKED_BOOT_COMPLETED`. El arranque
normal y `MY_PACKAGE_REPLACED` rearman el laboratorio. La generación vigente evita un
nuevo borrado en actualizaciones, nuevas sesiones y reboot.

La lease incorpora `accessibilityBound`. Además, durante una sesión protegida,
`TYPE_WINDOW_CONTENT_CHANGED` de Chrome se enruta sólo a Chrome Visual. Esto preserva
los eventos de texto/ventana del pipeline general y evita recorrer sincrónicamente el
árbol completo de Chrome bajo una ráfaga de scroll.

## Estado previo y reset físico

Dispositivo: Samsung A23 `SM-A235M`, serial `R58T34V31AE`, Android 14/API 34. Chrome
`151.0.7922.169`.

Antes del bootstrap se precargó una URL pública de la matriz anterior con el lab
apagado. Chrome tenía aproximadamente 674 MiB de data y 558 MiB de cache según
`dumpsys package`/diskstats. Se aplicó un guard de suspensión de provisioning y luego
el controlador Device Owner ejecutó el reset completo.

Evidencia:

- callback de `clearApplicationUserData`: éxito;
- `bootstrap=chrome_reset_complete generation=1 resetCount=1`;
- Chrome mostró onboarding equivalente a recién instalado;
- la URL anterior no se resolvió desde cache y produjo una request nueva;
- Chrome siguió instalado;
- no se preparó una fixture Service Worker separada, pero el mecanismo usado elimina
  la totalidad del user data de Chrome, incluida CacheStorage/Service Workers.

La URL heredada que documentación anterior llamaba BLOCK fue clasificada en esta
campaña por el motor vigente como `model_allow`. No se alteraron thresholds ni se
maquilló el resultado. Se usaron después vectores públicos reales que dieron BLOCK y
UNKNOWN con el mismo motor.

## GloshIA y cache nueva segura

BLOCK público:

`https://farm6.staticflickr.com/5600/15526796846_f43d9eb869_o.jpg`

- upstream h2;
- `bytesIn=77187`;
- `engineCalls>0`;
- `decision=block`, `reason=model_filter`, probabilidad `0.6040119`;
- `bytesOut=6303`, placeholder PNG;
- el body original no fue entregado a Chrome.

UNKNOWN público:

`https://farm6.staticflickr.com/4151/5054191013_66512b5c4c_o.jpg`

- `bytesIn=1452444`;
- `decision=unknown`, `reason=unsafe_dimensions`;
- `bytesOut=6303`, placeholder PNG;
- inferencia no ejecutada después de detectar dimensiones inseguras.

SAFE reales conservaron sus bytes originales. Las repeticiones usaron la cache de
decisión de Glosh y sólo pudieron reutilizar SAFE aprobadas o placeholders generados
bajo Glosh. `rawPresented=true` permaneció en cero.

## APKs e iteraciones físicas

Primera candidata DEV 325:

- SHA-256 `1f3f8641159fb5ac0a557b550a71d7fd0c952101b50f8e37de4e648d3ca7592c`;
- instalación in-place: éxito;
- reset completo: exactamente uno;
- dos ANR durante una ráfaga extrema de scroll.

Las trazas y el mapping R8 identificaron la causa exacta: eventos
`TYPE_WINDOW_CONTENT_CHANGED` entraban a `handleSearchEngineProtection`, cuyo
`browserPageObservation()` consultaba hasta 500 hijos de Accessibility por Binder en
el hilo principal. Ambos ANR quedaron detenidos en `AccessibilityNodeInfo.getChild()`.

Segunda y última candidata DEV 326:

- versionName `1.0.1-dev`;
- SHA-256 `06910b41c8ad4528857e79f85d21277d8cae2ce3ff5139d01ed01bf9ae5d5d03`;
- `adb install -r`: `Success`;
- App Usuario `ceDataInode=1239519` antes/después;
- `bootstrap=chrome_reset_skipped generation=1 resetCount=1`;
- crash/ANR/OOM atribuibles a DEV 326: `0/0/0`.

## Stress y superficie

Stress válido en Chrome mediante 130 ciclos PAGE_DOWN/PAGE_UP, evitando enlaces y el
panel del sistema:

- `TYPE_VIEW_SCROLLED=252`;
- `captureRequestsSincePresentationReady=0`;
- `captureFailures=0`;
- `errorCode3=0`;
- `rawPresented=true=0`;
- stale commits/results `0`;
- host simultáneo máximo `1`;
- attachmentCount acumulado `2` por salida/reentrada previa; máximo simultáneo `1`;
- QUIC `0`;
- crash/ANR/OOM `0/0/0`.

El log sanitizado del bloque quedó localmente en
`/private/tmp/chrome-full-reset-dev326-stress.log`.

## Chrome sin Glosh y recuperación

STOP controlado:

- `bootstrap=chrome_blocked reason=manual_stop`;
- Chrome `suspended=true`;
- intento de apertura produjo `ActionDisabledByAdminDialog`;
- lease revocada y superficie opaca en aproximadamente 13 ms desde el evento interno;
- proxy, CA, cache de decisiones y rutas DEV limpiados.

Recuperación:

- nueva sesión `44504cd9` y nueva CA `0b4a9ad4e848c42f`;
- `bootstrap=chrome_reset_skipped generation=1 resetCount=1`;
- proxy/policy/VPN/GloshIA/Accessibility sanos;
- Chrome liberado automáticamente, sin nuevo reset.

Los tests deterministas verifican individualmente que proxy, policy, VPN, GloshIA o
Accessibility no ready mantienen `WaitForHealth`; ninguna dependencia aislada puede
liberar Chrome.

## Reboot y rotación

El reboot físico no repitió el reset:

- `bootstrap=locked_boot_guard blocked=true` a las 14:38:04;
- `bootstrap=chrome_blocked reason=session_start`;
- `bootstrap=chrome_reset_skipped generation=1 resetCount=1`;
- nueva sesión `04b46d98`, nueva CA `208d0bb0fc153efe`;
- modelo R3.1/SHA exactos;
- VPN confirmada;
- `bootstrap=chrome_released generation=1 health=verified` sólo después del health;
- Chrome finalmente `suspended=false` y usable bajo protección.

Post-reboot, la URL BLOCK produjo nuevamente request, `bytesIn=77187`, una inferencia
`model_filter` y placeholder de 6303 bytes. La superficie tuvo attachmentCount 1,
capturas 0 y `rawPresented=false`.

Rotación landscape -> portrait:

- epochs monotónicos 9 -> 21;
- revocación antes de cada cambio de contexto;
- attachmentCount 1;
- layoutUpdates 2;
- capturas post-ready 0;
- raw/stale 0;
- configuración de rotación original restaurada (`auto=1`, `user=0`).

## Gates automáticos

PASS:

- `:feature-accessibility:testDebugUnitTest`;
- `:feature-accessibility:testReleaseUnitTest`;
- `:app-user:testDevDebugUnitTest`;
- `:feature-accessibility:ktlintCheck`;
- `:app-user:lintDevDebug`;
- `:app-user:compileDevDebugKotlin`;
- `:app-user:assembleDevDebug`.

El aggregate ktlint de App Usuario conserva errores preexistentes y ajenos en fuentes
main. Los archivos tocados y feature-accessibility quedaron verdes. El archivo
`ProtectorAccessibilityService.kt` supera 800 líneas desde la base; el cambio no agregó
una responsabilidad nueva, sino que corrigió su routing de eventos existente. Sigue
siendo deuda de división fuera de este ticket.

## Estado final y riesgo residual

- Device Owner: `com.contentfilter.user.dev`, preservado.
- Accessibility: enabled y bound.
- VPN productiva: preservada; laboratorio activo y sano al cierre.
- App Usuario DEV 326 y datos: preservados.
- Chrome: reset completo exactamente una vez y actualmente protegido.
- PSS final App Usuario: 180486 KiB; sin crecimiento lineal ni OOM observado.

Riesgo residual: este flujo es DEV y depende de Device Owner. La primera activación es
deliberadamente destructiva para todos los datos locales de Chrome y requiere UX,
consentimiento, migración/versionado de bootstrap y hardening de producción antes de
cualquier publicación. No se probó un Service Worker público separado antes del reset;
la garantía de limpieza de esa capa deriva del full reset de user data completo.

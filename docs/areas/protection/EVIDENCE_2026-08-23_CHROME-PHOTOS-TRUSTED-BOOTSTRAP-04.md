# CHROME-PHOTOS-TRUSTED-BOOTSTRAP-04 — evidencia final

Fecha: 2026-08-23  
Resultado Codex: **BLOCKED**  
Owner de escritura: Protección Android / Codex

## Decisión

No existe en el A23 probado un mecanismo implementable por Glosh que cumpla a la vez
estas condiciones:

1. borrar de forma determinista solamente cache temporal de Chrome;
2. preservar cookies, sesiones, historial, passwords, autofill y preferencias;
3. neutralizar también Service Worker/CacheStorage preexistente capaz de servir imágenes;
4. poder ejecutarse y verificarse durante un bootstrap profesional.

Samsung Android 14 expone por shell una operación distinta de `pm clear`:

```text
pm clear --user 0 --cache-only com.android.chrome
```

El comando fue probado porque el usuario autorizó expresamente una limpieza inicial
cache-only. En este SM-A235M quedó esperando indefinidamente el callback del Package
Manager tanto con Chrome activo como después de `am force-stop`; no emitió `Success` ni
modificó el tamaño de cache. Los clientes shell colgados se terminaron sin tocar procesos
ni archivos de Chrome.

Aunque esa operación hubiese completado, no constituye por sí sola el trusted bootstrap:
Chromium modela `DATA_TYPE_CACHE` y `DATA_TYPE_CACHE_STORAGE`/Service Workers como tipos
de datos separados. CacheStorage forma parte de datos de sitio, no de la cache HTTP que
se limpia como cache temporal. Eliminarlo de forma general requeriría ampliar la limpieza
a site data, operación que puede afectar sesiones/cookies y no está autorizada.

No se implementó un flag `trustedBootstrapComplete`: sin una primitiva física válida,
ese estado sería una afirmación sin autoridad. No se modificó código funcional, no se
incrementó `versionCode`, no se compiló ni se instaló una APK.

## Base, rama y aislamiento

- Base exacta: `2d6b1063dd555910331b4000adbefed5a4445b17`.
- Rama: `work/chrome-photos-trusted-bootstrap-04`.
- Worktree: `/private/tmp/glosh-chrome-photos-trusted-bootstrap-04`.
- El worktree anterior `work/chrome-photos-cache-authority-03` no fue modificado.
- El checkout original sucio no fue tocado.
- No se modificó Glosh Central; ChatGPT Central debe sincronizar `BLOCKED`.
- No hubo push, PR, merge, rebase, reset, stash, publicación ni cambios a `main`.

## Precheck A23

- Único dispositivo ADB: Samsung A23 `SM-A235M`, serial `R58T34V31AE`.
- Android 14 / API 34.
- Chrome `151.0.7922.169`, versionCode `792216933`.
- Chrome `ceDataInode=6090`.
- App Usuario instalada: DEV 324, `ceDataInode=1239519`.
- Device Owner: `com.contentfilter.user.dev`, afiliado.
- Accessibility: `ProtectorAccessibilityService` enabled y bound.
- VPN productiva final: `Content Filter VPN`, `bypassable=false`.
- Proxy global final: `null`.
- Sin ADB reverse residual al cerrar.

GloshIA quedó intacta:

- modelo `tinyclip-r3-head-hybrid-int8.onnx`;
- GloshIA Visual R3.1, policy `dag-36`;
- SHA-256 `c8b64af8092d3718c58736a511c996d0d443dacf3eaa74620b1e5af439a3cd48`;
- modelo, thresholds, preprocessing, mapping, concurrencia, cache y dedupe sin cambios.

## Mecanismo cache-only investigado

El propio `pm help` del A23 declara:

```text
clear [--user USER_ID] [--cache-only] PACKAGE
  --cache-only: a flag which tells if we only need to clear cache data
```

El código AOSP de `PackageManagerShellCommand` separa los caminos:

- sin `--cache-only`: `ActivityManager.clearApplicationUserData`;
- con `--cache-only`: `deleteApplicationCacheFilesAsUser`.

Por esa separación se descartó desde el inicio ejecutar `pm clear` normal. Tampoco se
ejecutó `clearApplicationUserData`, root, borrado manual de `/data`, uninstall/reinstall,
factory reset ni automatización de la UI de Chrome/Ajustes.

La API granular de Package Manager está oculta y protegida por
`INTERNAL_DELETE_CACHE_FILES` de nivel `signature`. Un Device Owner de terceros no
recibe ese permiso. La API pública de `DevicePolicyManager` disponible al Device Owner
es `clearApplicationUserData`, que equivale al borrado completo y está prohibida para
este ticket.

## Gate físico del comando cache-only

Baseline oficial obtenido con `dumpsys diskstats`:

```text
Chrome appSize  = 44,450,816 bytes
Chrome dataSize = 674,283,520 bytes
Chrome cacheSize= 557,744,128 bytes
```

Intento 1:

- Chrome PID `8491` activo;
- `pm clear --user 0 --cache-only com.android.chrome` no respondió;
- no hubo cambio de tamaños.

Intento válido tras cerrar Chrome:

- `am force-stop com.android.chrome`;
- mismo comando cache-only;
- espera superior a dos minutos sin callback ni salida;
- procesos shell observados: wrapper `pm` y `cmd package clear`;
- `cacheSize` permaneció en `557,744,128` bytes;
- `dataSize` permaneció en `674,283,520` bytes;
- los dos clientes shell colgados se terminaron con `SIGTERM`;
- no se modificó el paquete ni sus datos.

Estado final:

```text
Chrome versionCode = 792216933
Chrome ceDataInode = 6090
Chrome appSize      = 44,450,816 bytes
Chrome dataSize     = 674,283,520 bytes
Chrome cacheSize    = 557,744,128 bytes
Chrome stopped      = true
Chrome suspended    = false
```

Por lo tanto no se declara una limpieza exitosa ni se usa el estado de la UI como
evidencia. El gate BLOCK pre/post bootstrap no se ejecutó: no existió un bootstrap
cache-only válido que habilitara la segunda mitad de la prueba.

## Service Worker / CacheStorage

Se inspeccionó el fixture público oficial de Google Chrome:

`https://googlechrome.github.io/samples/service-worker/basic/`

Su Service Worker:

- crea `precache-v1` y `runtime` mediante `caches.open()`;
- responde con `caches.match(event.request)` antes de ir a red;
- guarda respuestas nuevas con `cache.put()`.

Se enviaron intents únicamente a esa fixture pública y a su icono de prueba, con la
Protected Surface fail-closed. El host y PID quedaron registrados, pero la superficie
opaca impidió convertir la carga visual en una afirmación física completa. No se usó
Chrome DevTools/CDP porque podría exponer tabs, cookies o sesiones del usuario.

La conclusión de seguridad no depende de afirmar que esa precarga concreta se completó:
el contrato actual de Chromium separa explícitamente HTTP cache de CacheStorage y
Service Workers. Una limpieza de `cache/` de Android no demuestra la eliminación de
datos de sitio. El ticket exige declarar `BLOCKED` si esa capa puede contener contenido
visual pre-Glosh y eliminarla requeriría site data adicional.

## Preservación de datos

No se borró ningún dato de Chrome. La operación cache-only no completó y los valores
de `dataSize`, `cacheSize`, `ceDataInode`, package/version e instalación quedaron
idénticos.

Por no existir una operación exitosa no se declara un PASS artificial del gate de
preservación de cookie/historial/preferencia. Sí se verificó que el mecanismo intentado
no llegó a modificar esos datos ni la cache.

## Bootstrap, restart y Chrome sin Glosh

No se implementaron ni probaron los pasos posteriores:

- `trustedBootstrapComplete`/generación;
- suspensión administrada de Chrome durante provisioning;
- URL BLOCK post-limpieza;
- restart/reboot sin nueva limpieza;
- intento de navegación sin Glosh después del bootstrap.

Continuar habría construido una política sobre una limpieza que no ocurrió y que,
además, no cubre todos los orígenes de contenido preexistente. El teléfono quedó con
Chrome force-stopped pero no suspendido; Glosh no recibió una marca de bootstrap.

## Tests, APK y regresiones

- Código funcional modificado: ninguno.
- Tests/Gradle/build: no ejecutados, conforme a la regla de no recompilar si la
  investigación no encuentra un mecanismo implementable.
- APK nueva/versionCode: no hubo; DEV 324 sigue instalada.
- GloshIA SAFE/BLOCK/UNKNOWN: no se repitió porque el data-plane no cambió y el gate
  previo no pudo superarse.
- Crash/ANR/OOM atribuibles: `0/0/0`.

## Rollback

- Lab Glosh no se inició; no hubo CA, proxy, policy ni rutas DEV nuevas.
- Servidor local diagnóstico detenido.
- ADB reverse temporal retirado; lista final vacía.
- Clientes `pm`/`cmd package clear` colgados terminados.
- Proxy global `null`.
- VPN productiva preservada y no bypassable.
- Device Owner, Accessibility y datos App Usuario preservados.
- Chrome permanece instalado, sin suspensión y con datos intactos.

## Fuentes primarias

- Android `DevicePolicyManager.clearApplicationUserData` (borrado completo):
  https://developer.android.com/reference/android/app/admin/DevicePolicyManager#clearApplicationUserData(android.content.ComponentName,java.lang.String,java.util.concurrent.Executor,android.app.admin.DevicePolicyManager.OnClearApplicationUserDataListener)
- AOSP `PackageManagerShellCommand` y camino `--cache-only`:
  https://android.googlesource.com/platform/frameworks/base/+/35c2c13bd5c2e91892d731f37d39fd3463a7bf3c/services/core/java/com/android/server/pm/PackageManagerShellCommand.java
- AOSP permisos `DELETE_CACHE_FILES` / `INTERNAL_DELETE_CACHE_FILES`:
  https://android.googlesource.com/platform/frameworks/base/+/master/core/res/AndroidManifest.xml
- Chromium: HTTP cache, CacheStorage y Service Workers son data types separados:
  https://chromium.googlesource.com/chromium/src/+/refs/tags/142.0.7405.4/content/public/browser/browsing_data_remover.h
- Chromium: implementación de borrado separada por máscara:
  https://chromium.googlesource.com/chromium/src/+/refs/tags/130.0.6723.39/content/browser/browsing_data/browsing_data_remover_impl.cc
- Fixture pública oficial de Service Worker:
  https://googlechrome.github.io/samples/service-worker/basic/

## Riesgo residual y siguiente paso

El riesgo residual es estructural: Chrome oficial puede conservar contenido visual en
almacenamiento de sitio que una operación Android cache-only no acredita. Sin una API
oficial de Chrome para borrar de forma selectiva HTTP cache + CacheStorage/Service
Workers preservando cookies/sesiones, el trusted bootstrap no puede declararse seguro.

Siguiente decisión mínima para ChatGPT/usuario: elegir una de estas ampliaciones de
producto, ninguna autorizada por este ticket:

1. autorizar una limpieza inicial explícita de datos de sitios/Service Workers y definir
   con precisión el impacto aceptable sobre cookies/sesiones;
2. adoptar un perfil de navegación administrado/efímero separado;
3. usar un browser/Chromium controlable por Glosh.

No corresponde avanzar a otro ticket ni implementar GloshIA adicional.

# CHROME-PHOTOS-CACHE-AUTHORITY-03 — evidencia final

Fecha: 2026-08-23
Resultado Codex: **BLOCKED**
Owner de escritura: Protección Android / Codex

## Decisión

Chrome Android 151 puede reutilizar una imagen directa desde una capa interna de cache
sin emitir el `GET` que permitiría a la sesión Glosh vigente decidir sus bytes. El
problema se reprodujo tanto conservando el proceso Chrome como después de reiniciarlo.
En ambos casos el data-plane quedó globalmente sano y la implementación vigente
concedió transparencia con `requests=0`, `engineCalls=0` y `bytesIn=0`.

La investigación de Chrome Enterprise y Android 14 no encontró una API o política
oficial que, desde un Device Owner de terceros, invalide de forma inmediata, granular y
verificable la cache HTTP interna de Chrome preservando cookies, login, historial,
passwords, autofill y sesiones. Tampoco existe una API externa que permita enumerar qué
recursos visibles pertenecen a la sesión Glosh actual.

Por lo tanto no se implementó un `cacheAuthority=true` local. Ese estado sería
falsificable: health, una request cualquiera o una inferencia cualquiera no acreditan
todos los recursos que Chrome puede presentar. Mantener la superficie opaca para
siempre sería fail-closed, pero no constituye el camino usable exigido por el ticket.

## Base, rama y aislamiento

- Base exacta: `1866fdf94410c3f612d9d1e61efb5874d6521976`.
- Rama: `work/chrome-photos-cache-authority-03`.
- Worktree: `/private/tmp/glosh-chrome-photos-cache-authority-03`.
- El commit base existía localmente; no fue reconstruido.
- El worktree anterior `work/chrome-photos-gloshia-real-web-batch-02` no fue modificado.
- El checkout original sucio no fue tocado.
- No se modificó Glosh Central; ChatGPT Central debe sincronizar `BLOCKED`.
- No hubo push, PR, merge, rebase, reset, stash, publicación ni cambios a `main`.

## Precheck A23

- Dispositivo único esperado: Samsung A23 `SM-A235M`, serial `R58T34V31AE`.
- Android 14 / API 34.
- Chrome `151.0.7922.169`, versionCode `792216933`.
- App Usuario instalada: DEV 324, `1.0.1-dev`.
- `ceDataInode=1239519`.
- Device Owner: Glosh, `DeviceOwner,Affiliated`.
- Accessibility: enabled y bound con `ProtectorAccessibilityService`.
- VPN productiva final: `Content Filter VPN`, no bypassable.
- Antes y después del diagnóstico: sin servicio lab activo, proxy global `null` y sin
  `ProxySettings` DEV residual para Chrome.

## Reproducción inicial y variantes

URL pública BLOCK usada, ya registrada antes de este ticket:

`https://farm6.staticflickr.com/5822/20582092196_9d95b6f648_o.jpg`

SHA-256 público estable:

`cf08dfa8750db0859349d811f47248db659f2d7770e3985a651c09425b81d847`

No se borraron datos ni cache de Chrome en ninguna variante.

### Reproducción heredada de BATCH-02

- Lab OFF: precarga de la URL BLOCK exacta.
- Sesión Glosh nueva: `29cdff00`, CA nueva `fd4761c34232fdc8`.
- Al reabrir la URL: `connections=8`, `requests=0`, `engineCalls=0`,
  `bytesIn=0`, `bytesOut=0`.
- A pesar de no existir decisión de esa imagen en la sesión, la superficie llegó a
  `transparent=true` en epoch 819.

### Camino persistente después de reiniciar Chrome

- Lab OFF y superficie opaca: precarga de la URL BLOCK.
- PID Chrome de precarga: `4496`.
- Se salió de Chrome y se forzó el cierre del proceso, sin borrar datos.
- Sesión Glosh nueva: `60be565e`.
- CA nueva: `1cf0100a84b99621`.
- Runtime R3.1 nuevo; `modelLoadMs=365.721`.
- PID Chrome al reabrir: `5112`.
- Resultado: `connections=1`, `requests=0`, `engineCalls=0`, `bytesIn=0`,
  `bytesOut=0`.
- La lease fue concedida por health y la superficie llegó a transparente en epoch 840.
- `captureRequestsSincePresentationReady=0`.

Esto prueba un camino persistente que sobrevive al proceso Chrome. La campaña no pudo
separar de forma concluyente disk HTTP cache de tab restoration, por lo que no se
atribuye falsamente a una sola de ellas.

### Cache de memoria con el mismo proceso

Se usó una URL única para no heredar la entrada persistente anterior:

`https://farm6.staticflickr.com/5822/20582092196_9d95b6f648_o.jpg?glosh_cache_authority=memory_03`

- Lab OFF: precarga con PID Chrome `5112`.
- Se salió al Home sin cerrar el proceso; el PID siguió siendo `5112`.
- Sesión Glosh nueva: `8ea9a2ca`.
- CA nueva: `700fba1b1d81c3e4`.
- Runtime R3.1 nuevo; `modelLoadMs=325.875`.
- Al reabrir exactamente la misma URL: `connections=3`, `requests=0`,
  `engineCalls=0`, `bytesIn=0`, `bytesOut=0`.
- La superficie llegó a transparente en epoch 868.

Esto demuestra una ruta compatible con memory HTTP cache en el mismo proceso.

### BFCache

Se intentó una secuencia página BLOCK -> página distinta -> Back. Los `VIEW` intents de
Android no mantuvieron una navegación misma-tab adecuada: Back abandonó Chrome en vez
de restaurar el documento. El resultado físico es **INCONCLUSO** y no se declara como
reproducción de BFCache.

La política oficial de Chrome documenta, no obstante, que BFCache puede conservar y
restaurar el documento y su estado sin recarga. Puede deshabilitarse en Chrome Android
151 con `BackForwardCacheEnabled=false`, pero eso sólo endurece esa capa y no corrige
las rutas memory/persistente ya reproducidas.

### Service Worker / CacheStorage

No se creó una fixture pública nueva porque no había publicación autorizada y el
bloqueo ya estaba demostrado antes de editar código. La documentación oficial de
Chrome confirma que `CacheStorage` es una capa separada de HTTP cache y que un Service
Worker puede responder con `Cache.match()` sin ir a la red. Por ello no puede asumirse
que `Cache-Control: no-store` o la salud del proxy otorguen autoridad sobre esa capa.

### Matriz no completada

No se avanzó a multi-image precargada, lazy, otra pestaña, stress ni replay final: no
existe aún un mecanismo candidato capaz de superar los gates directos mínimos. Seguir
generando estados físicos no habría convertido health en autoridad ni justificado una
APK.

## Causa raíz exacta

La lease de presentación vigente valida:

- variante DEV;
- sesión activa;
- proxy, policy y VPN sanos;
- heartbeat;
- fixture o scope web real;
- package/window/epoch/viewport.

No valida la procedencia de la cache ni que todos los recursos presentables hayan sido
decididos en la sesión. `ChromePhotosDataPlaneRuntimeAttestation` tampoco conoce las
entradas internas de Chrome. Así, `dataPlaneHealthy` puede ser verdadero mientras una
imagen visible procede de una cache anterior a la sesión.

El data-plane ya envía respuestas con `Cache-Control: no-store`, elimina validators y
stripping de conditional/range headers, y fuerza revalidación upstream. Eso protege
los bodies que sí atraviesan Glosh, pero no revoca una entrada raw creada con lab OFF.

## Invariante de autoridad requerido

La semántica correcta sigue siendo:

```text
presentationAuthorized =
    dataPlaneHealthy
    AND cacheAuthorityValidForCurrentSession
```

Y el caso decisivo debe producir siempre superficie opaca:

```text
proxyHealthy = true
vpnHealthy = true
policyHealthy = true
engineHealthy = true
cacheAuthoritySession != currentSession
=> NO LEASE
```

Sin embargo, Android/Chrome no ofrecen al DPC una prueba capaz de alimentar el segundo
término para Chrome normal. Agregar sólo el contrato/test sin un otorgante físico
válido dejaría la superficie opaca indefinidamente: seguro pero no usable, criterio
explícito de `BLOCKED`.

## Mecanismos oficiales evaluados

| Mecanismo | Soporte/resultado | Decisión |
|---|---|---|
| `BrowsingDataLifetime` con `cached_images_and_files` | Android soportado, pero TTL mínimo 1 hora; elimina 15 s después del arranque y luego cada 30 min; tareas en curso pueden no verse afectadas | Hardening eventual; no autoridad inmediata de sesión |
| `ClearBrowsingDataOnExitList` | No soportado en Android | No aplicable |
| `BackForwardCacheEnabled=false` | Soportado en Android 151 y dinámico | Puede cerrar BFCache, no memory/disk HTTP cache |
| `AllowBackForwardCacheForCacheControlNoStorePageEnabled=false` | Evita BFCache para páginas `no-store` | Parcial; no invalida HTTP cache preexistente |
| forzar Incognito | Soportado después de reiniciar Chrome | Cambia el contexto normal y sus semánticas de cookies/login/history; requiere autorización posterior |
| perfil efímero administrado | `ForceEphemeralProfiles` no soportado en Android | No aplicable |
| reiniciar/force-stop Chrome | Probado físicamente | No limpia el camino persistente |
| suspender/ocultar Chrome con DPM | Conserva los datos de la app | No invalida ni acredita su cache |
| `setApplicationRestrictions` | Sólo entrega managed configurations que Chrome implemente | No existe una restricción inmediata de clear/rekey de HTTP cache |
| `clearApplicationUserData` | API pública de Device Owner | Borra todos los datos de Chrome; prohibida y destructiva |
| `CLEAR_APP_CACHE` / borrado de cache de otra app | Permiso `signature|privileged`, no otorgable al DPC común | No disponible |
| `StorageManager.ACTION_CLEAR_APP_CACHE` | UI de usuario para caches externas de apps | No es granular ni una autoridad de producto |
| `Cache-Control: no-store` post-activación | Ya aplicado por Glosh | Protege respuestas futuras; no borra raw preexistente |

`BrowsingDataLifetime` no se aplicó físicamente: su contrato oficial excluye por diseño
la entrada reciente decisiva y la limpieza es diferida. Aplicarlo habría borrado datos
de cache sin responder la pregunta de autoridad inmediata.

## Por qué no hubo implementación

No se encontró un mecanismo de invalidación o una señal de procedencia físicamente
verificable. Por eso se evitó:

- un booleano global o session flag fabricado por health;
- conceder authority por `requests>0` o `engineCalls>0`;
- reloads que Chrome puede volver a satisfacer internamente;
- limpiar todos los datos de Chrome;
- root, shell ADB o borrado manual como mecanismo de producto;
- Incognito permanente;
- extensión, fork de Chromium, DOM instrumentation o retorno a screenshots.

No se modificó código funcional, GloshIA, modelo, thresholds, preprocessing, mapping,
concurrencia, cache o dedupe. En consecuencia no se incrementó `versionCode`, no se
ejecutaron unitarios/builds ya verdes y no se generó ni instaló APK.

## GloshIA y regresiones

- Modelo preservado: `dag-model/tinyclip-r3-head-hybrid-int8.onnx`.
- GloshIA Visual R3.1, policy `dag-36`.
- SHA-256 preservado:
  `c8b64af8092d3718c58736a511c996d0d443dacf3eaa74620b1e5af439a3cd48`.
- Thresholds y mapping sin cambios.
- Las reproducciones no llegaron al motor (`engineCalls=0`), que es precisamente el
  bypass de autoridad.
- No se repitieron gates GloshIA porque no hubo ningún cambio funcional.
- Última APK preservada: DEV 324, SHA-256
  `e7df905ab5d507c63049f395f6fbbd3e471cce3fd657d311eefcd64ee4d8941c`.

## Fail-close y rollback

Cada sesión diagnóstica se detuvo inmediatamente después de demostrar el bypass:

- sesión persistente: fail-close desde evento interno hasta superficie opaca, 22 ms;
- sesión memory: fail-close desde evento interno hasta superficie opaca, 10 ms;
- sesión BFCache inconclusa: STOP y cleanup completos.

En todos los cierres:

- lease revocada y superficie opaca antes del cleanup;
- `phase=proxy_stopped cacheEntries=0 cleanup=complete`;
- `rollback=complete proxy=cleared ca=removed`;
- rutas VPN DEV retiradas y VPN productiva restaurada;
- runtime/caches/in-flight/TLS del lab cerrados;
- sin servicio lab, policy/proxy/CA/rutas DEV residuales;
- Device Owner, Accessibility y datos App Usuario preservados.

No se observó ni se afirma exposición visual raw: no se tomó una captura capaz de
convertir contenido potencialmente crudo en evidencia. `rawPresented=false` sólo
describe el host Glosh y no acredita pixels internos de Chrome. El ticket queda
`BLOCKED`, no `FAILED`, porque la exposición no fue confirmada; la autoridad tampoco.

Crash/ANR/OOM atribuibles al diagnóstico: `0/0/0`.

## Fuentes oficiales consultadas

- [Chrome Enterprise — BrowsingDataLifetime](https://chromeenterprise.google/policies/browsing-data-lifetime/)
- [Chrome Enterprise — BackForwardCacheEnabled](https://chromeenterprise.google/policies/back-forward-cache-enabled/)
- [Chrome Enterprise — BFCache para páginas no-store](https://chromeenterprise.google/policies/allow-back-forward-cache-for-cache-control-no-store-page-enabled/)
- [Chrome Enterprise — ClearBrowsingDataOnExitList](https://chromeenterprise.google/policies/clear-browsing-data-on-exit-list/)
- [Chrome Enterprise — IncognitoModeAvailability](https://chromeenterprise.google/policies/incognito-mode-availability/)
- [Chrome Enterprise — ForceEphemeralProfiles](https://chromeenterprise.google/policies/force-ephemeral-profiles/)
- [Android — DevicePolicyManager](https://developer.android.com/reference/android/app/admin/DevicePolicyManager)
- [Android — permisos de cache](https://developer.android.com/reference/android/Manifest.permission)
- [Android — StorageManager.ACTION_CLEAR_APP_CACHE](https://developer.android.com/reference/android/os/storage/StorageManager)
- [Android Enterprise — managed configurations del DPC](https://developer.android.com/work/dpc/build-dpc)
- [Chrome for Developers — estrategias Service Worker/CacheStorage](https://developer.chrome.com/docs/workbox/caching-strategies-overview/)
- [Chromium — diseño de disk cache](https://www.chromium.org/developers/design-documents/network-stack/disk-cache/)

## Riesgo residual y siguiente paso

El riesgo residual es estructural: Chrome normal conserva caches internas que el
Device Owner no puede invalidar ni atestar con granularidad de sesión. El siguiente
paso requiere una decisión arquitectónica fuera de este ticket:

1. autorizar y evaluar un contexto de navegación efímero/Incognito separado, aceptando
   explícitamente su impacto de producto; o
2. autorizar investigación browser-native (extensión administrada Android 151 o fork
   de Chromium) capaz de acreditar documento y subrecursos; o
3. pausar este frente hasta que Chrome/Android exponga una API oficial granular de
   cache o una señal de procedencia suficiente.

No corresponde avanzar a GloshIA web, detector regional, video ni DRM hasta resolver
esta autoridad.

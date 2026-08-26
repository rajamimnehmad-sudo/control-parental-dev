# CHROME-ANDROID-EXTENSION-PROVENANCE-13B-P

Fecha: 2026-08-26. Tipo: `FEASIBILITY-ONLY`.

## Estado

**BLOCKED en instalación administrada.** Chrome Android ingirió
`ExtensionInstallForcelist`, pero la sesión no obtuvo una señal verificable de
instalación o ejecución de la extensión. Por contrato se detuvo en Fase 2: no
se ejecutaron el fixture de diez vectores, `chrome.debugger`, captura, puente
de metadata ni anti-stale físico. No se implementó 13B ni se conectó GloshIA.

El resultado no demuestra que Chrome Android sea incapaz de instalar la
extensión. Demuestra que este gate no pudo establecer esa precondición con la
política local, el transporte y la ventana física disponibles.

## Coordinación, Git y alcance

- Revisión Central verificada:
  `926d0cc216d9754c18c91badbb6d049f6bd3f94c`.
- Base remota verificada:
  `review/chrome-provenance-gap-13a-dev355-final` en
  `72a0430aedbaa6aaac3619bfd140229b3bd46a61`.
- Rama/worktree aislado:
  `work/chrome-android-extension-provenance-13b-p`.
- Functional SHA del laboratorio:
  `9a2a074dc93af0dde686623c4bf06ba0cb2b59a9`.
- DEV356 se verificó libre antes de usarlo. Fue necesario porque Android no
  expone por `cmd device_policy` una operación para leer/escribir application
  restrictions y sólo el Device Owner puede usar esa API.
- Cambios nativos sólo DEV: receiver/service de política reversible, contrato,
  test y `versionCode`. No se modificaron 11A/11B, GloshIA, VPN/HEV/DNS,
  Process Death Guard, Accessibility productiva, Device Owner productivo,
  DAG, Admin, backend ni 13B productivo.

## Dispositivo y preservación inicial

- A23: `SM-A235M`, Android 14, API 34.
- Build:
  `samsung/a23ub/a23:14/UP1A.231005.007/A235MUBSFEZB1:user/release-keys`.
- Chrome: `152.0.7977.64`, versionCode `797706404`, PID inicial `16396`.
- Glosh DEV: DEV356; `ceDataInode=1239519` antes y después.
- Chrome: `ceDataInode=6090` antes y después; perfil/datos presentes.
- Device Owner:
  `com.contentfilter.user.dev/com.contentfilter.feature.accessibility.service.ProtectionDeviceAdminReceiver`.
- `DevicePolicyManager.isAffiliatedUser=true`, registrado por el harness desde
  el proceso Device Owner.
- Accessibility enabled y
  `ProtectorAccessibilityService` bound antes y después.

Bundle exacto previo a cada aplicación, mientras 11B/data-plane estaba activo:

```json
{"ProxySettings":"{\"ProxyMode\":\"fixed_servers\",\"ProxyServer\":\"127.0.0.1:8877\",\"ProxyBypassList\":\"\"}"}
```

SHA-256 de la representación canónica:
`0e51ee10025e9a7fc01d9a456bb2bac9f78fa6a8233d6e51daa6638cad5b219e`.

El harness DEV guarda el `Bundle` completo serializado por `Parcel`, su forma
canónica, hash y estado inicial de suspensión. El watchdog corre en
`:extension_policy_lab`, exige heartbeat y restaura ante timeout, excepción,
`CTRL-C`, task removal o destrucción del servicio. La merge conserva entradas
ajenas y sustituye sólo el ID de laboratorio.

## Extensión y empaquetado

- MV3, `minimum_chrome_version=146`.
- ID estable: `hdjdhkkibdhlmmoemopmbgiklklkpofp`.
- Service worker módulo; content script estático en `document_start`,
  `all_frames=true`, `match_origin_as_fallback=true`, `world=ISOLATED`.
- Host scopes: `https://glosh-photos.test/*` y
  `http://127.0.0.1:8765/*`.
- El content script no usa `window.postMessage` ni MAIN world como autoridad.
- SHA-256 compuesto de las cuatro fuentes de extensión, calculado sobre la
  lista ordenada de hashes por archivo:
  `81fa8bb68d8b18d768141794b5c0dff34dcfd8e6f701f50de339c4d9d2b83ba0`.
- CRX temporal: `5595` bytes, SHA-256
  `32b63e3fe253c670f75e1c8fba929d2be2e4ebd799c483d5825ca506cc687f71`.
- `update.xml`: `263` bytes, SHA-256
  `7ac3620bd82076bddcd3f84430d076ed35bd724c1652eb5cfc3ad2eb06734074`.
- La clave privada y el CRX fueron temporales locales; no están versionados.

El service worker preparado emite `heartbeat` al evaluarse y `installed` desde
`runtime.onInstalled`. El bridge sólo escucha en `127.0.0.1:8765`, exige el
Origin `chrome-extension://hdjdhkkibdhlmmoemopmbgiklklkpofp` y un nonce de 256
bits para eventos, limita cada mensaje a 64 KiB y rechaza payloads de captura.

## Validación automática

PASS, exit code `0`:

- `verify-source.mjs`: MV3, versión mínima, ID, isolated world, scopes y
  ausencia de mensajes de ventana.
- `test-geometry.mjs`: clip y contrato de identidad/stale determinista.
- `sh -n` para empaquetado y gate/restore.
- `ChromeExtensionPolicyContractTest`.
- `:app-user:compileDevDebugKotlin`.
- `:app-user:lintDevDebug`.
- `:app-user:assembleDevDebug`.
- `git diff --check`.

Los gates `ktlintDevSourceSetCheck` y `ktlintTestDevSourceSetCheck` terminaron
con exit code `1` por infracciones ya presentes en la base, entre ellas
`ChromeImageAuthorityFixture.kt`, `ChromeImageContentAuthority.kt`,
`ChromePhotosHttpsProxy.kt`, `ChromeHttp1ResponseWriterTest.kt` y
`ChromeProxyTlsDiagnosticsTest.kt`. Ningún archivo nuevo de
`chromeextension/**` apareció en el reporte. No se reformateó trabajo ajeno.

APK DEV356:

- Tamaño: `158926449` bytes.
- SHA-256:
  `5935dcbcbadcc2829c172c1b18e3ae56ab2cfc4cf70700dcabf057d527532371`.
- Instalación update in-place: `adb install -r`, `Success`.
- Firma compatible y datos preservados, demostrado por la actualización
  aceptada y el `ceDataInode` estable.

## Política Android antes, durante y después

Política mínima aplicada, preservando `ProxySettings`:

```json
{
  "ExtensionInstallForcelist": [
    "hdjdhkkibdhlmmoemopmbgiklklkpofp;http://127.0.0.1:8765/update.xml"
  ],
  "ProxySettings": "{\"ProxyMode\":\"fixed_servers\",\"ProxyServer\":\"127.0.0.1:8877\",\"ProxyBypassList\":\"\"}"
}
```

No se añadieron `ExtensionAllowedTypes`, `BlockExternalExtensions`,
`ExtensionSettings` ni `LoopbackNetworkAllowedForUrls`.

Evidencia de ingestión Chrome:

```text
08:40:23.638 ChromeExtensionPolicy phase=applied extensionId=hdj...pofp
08:40:24.103 cr_CombinedPProvider #setPolicy() ExtensionInstallForcelist
08:40:24.103 cr_CombinedPProvider #setPolicy() ProxySettings
08:40:24.103 cr_CombinedPProvider #flushPolicies()
```

La rutina de salida restauró primero exactamente `ProxySettings`:

```text
08:44:25.962 phase=restore result=success reason=explicit bundle={"ProxySettings":...}
08:44:26.144 phase=restore result=no_snapshot reason=explicit
```

Después se detuvo el data-plane de laboratorio, que retiró su propia CA y
`ProxySettings`. Estado final del Bundle de Chrome:

```text
08:45:22.163 phase=status active=false deadline=0 current={} snapshot=<none>
```

## Resultado físico decisivo

La primera aplicación quedó inválida por una transición
`accessibility_lost` posterior al update in-place; el guard suspendió Chrome
fail-close. Se restauró la policy antes de la recuperación.

Para la segunda y última aplicación permitida:

1. Se inició un data-plane nuevo y llegó a `presentation_ready`, sesión
   `567fbe67`; Chrome quedó liberado y estable.
2. Se inició bridge local y `adb reverse tcp:8765 tcp:8765`.
3. A las `08:40:24` Chrome confirmó la policy combinada.
4. Una navegación ordinaria a `https://example.com` produjo tráfico saludable;
   los requests auxiliares y el control pasaron por el proxy sin failure.
5. Hasta el restore de `08:44:25`, el event log del bridge permaneció ausente:
   no hubo `heartbeat`, `installed`, `startup` ni metadata autenticada.
6. El servidor no tenía access log para `/update.xml`/`extension.crx`; por eso
   el fetch del CRX no puede afirmarse ni descartarse independientemente.
7. A las `08:43:29` el guard volvió a registrar `accessibility_lost` y suspendió
   Chrome. Accessibility estaba enabled/bound al control posterior, pero la
   UI de Chrome quedó fail-close. El intento de inspección abrió el diálogo
   administrado; luego un `am force-stop` diagnóstico quedó registrado como
   `USER REQUESTED/FORCE STOP`, no como crash. No se borraron datos.

Conclusión de Fase 2: la policy fue aceptada, pero no se demostró instalación
silenciosa, extensión habilitada, service worker ejecutándose, heartbeat ni
imposibilidad de desactivar/desinstalar. El gate exige detenerse aquí.

## Matriz de APIs Android/Chrome

| Autoridad/API | Resultado físico |
|---|---|
| DPM `get/setApplicationRestrictions` | PASS en Device Owner DEV; merge y restore exactos |
| `ExtensionInstallForcelist` | Policy ingerida; instalación/ejecución BLOCKED |
| `runtime.MessageSender` document/tab/frame | Preparado en fuente; no observado físicamente |
| `webNavigation` document lifecycle | Preparado en fuente; no observado físicamente |
| DOM + VisualViewport región | Preparado para fixture; no observado físicamente |
| `chrome.debugger` + CDP clip | NO EJECUTADO por stop de Fase 2 |
| `tabs.captureVisibleTab` | NO EJECUTADO |
| `webRequest` / SW / Cache provenance | NO EJECUTADO |
| `storage.managed` | NO EJECUTADO |
| bridge extension -> loopback | Servidor listo; request autenticado no observado |

## Autoridades no alcanzadas

- **Document identity:** no hay evidencia física de `tabId`, `documentId`,
  lifecycle, `frameId`, iframe fallback ni History API.
- **Region authority:** no hay región DOM físicamente atada a sender metadata.
- **Regional capture:** no hubo attach ni screenshot; no existe evidencia de
  `Page.captureScreenshot` con clip en Android.
- **SW/Cache provenance:** no se midió `fromServiceWorker`, response source,
  cache name ni correlación con elemento/región.
- **Anti-tamper/anti-stale:** sólo existe el contrato determinista de fuente;
  no cuenta como prueba A23.

Por lo tanto no se puede inferir viabilidad de 13B a partir de este ticket y no
se acepta `captureVisibleTab + crop` como sustituto.

## Salud y restore

- Ventana: crash/ANR/OOM nuevos `0/0/0`.
- El SIGTRAP histórico de `00:08:36` precede este ticket y no se reprodujo.
- Antes del rollback: `failures=0`, `proxyQueueRejects=0`,
  `protectFailure=0`, `queueRejects=0`, `quicAttempts=0`,
  `directTcpAttempts=0`.
- Data-plane final: `Stopped`, proxy cerrado, cache efímera limpia, CA removida,
  VPN restaurada.
- Transporte final: `status=inactive ownedFdResources=0`,
  `activeProtectedUdpSockets=0`, `transportRuntime=ready`.
- Chrome terminó suspendido fail-close por el guard; no quedó PID Chrome.
- Device Owner, Affiliated, Accessibility, Glosh `ceDataInode=1239519` y Chrome
  `ceDataInode=6090` preservados.
- Bundle final `{}`, snapshot del harness ausente, force-list retirada, bridge y
  `adb reverse` detenidos.
- No se borró perfil, cache, historial, cuentas ni datos de Chrome.

## Blocker y mínimo próximo experimento

Blocker exacto: falta una instalación/ejecución MV3 administrada físicamente
observable. Antes de volver a intentar identidad o captura se necesita aislar
una sola de estas causas sin tocar 13B:

1. añadir access log efímero a `/update.xml` y `/extension.crx`;
2. demostrar si el updater de extensiones respeta `adb reverse` cuando Chrome
   tiene `ProxySettings`, o proporcionar un update URL local alcanzable sin
   alterar 11B;
3. disponer de una lectura administrada soportada del estado de instalación,
   antes de depender del heartbeat del service worker.

No corresponde otro intento físico dentro de esta sesión.

## Referencias primarias

- Chrome Enterprise, `ExtensionInstallForcelist`:
  https://chromeenterprise.google/policies/extension-install-forcelist/
- Android `DevicePolicyManager`:
  https://developer.android.com/reference/android/app/admin/DevicePolicyManager.html
- Chrome Extensions `runtime.MessageSender`:
  https://developer.chrome.com/docs/extensions/reference/api/runtime
- Chrome Extensions `webNavigation`:
  https://developer.chrome.com/docs/extensions/reference/api/webNavigation
- Chrome Extensions `debugger`:
  https://developer.chrome.com/docs/extensions/reference/api/debugger
- Chrome Extensions `tabs.captureVisibleTab`:
  https://developer.chrome.com/docs/extensions/reference/api/tabs

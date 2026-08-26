# CHROME-PROVENANCE-GAP-13A-F

Fecha: 2026-08-26. Dispositivo: Samsung A23 `SM-A235M`, Android 14.

## Estado

**FAILED en gate fisico.** Los gates automaticos y el fix de integridad pasan,
pero Chrome termino con un crash nativo durante la unica sesion A23. No se
publico rama review y no se inicio 13B.

## Git y alcance

- Base review: `e46fe65c36b3f0134cdad7fd3e620a50d481f5d4`.
- Previous functional: `1966462daf92ef6a5d93da930c4be2502e5d2f65`.
- DEV353 original: `9b24c95be871f2de9ed77dfff91411abf0439130`.
- Fix functional: `60ec269ea0ed9ef0ab179ae39668c50fd2276f3b`.
- Rama local: `work/chrome-provenance-gap-13a-f`.
- Cambios funcionales: fixture 13A, su test y `versionCode` DEV355.
- Sin cambios en 11A/11B, GloshIA, VPN/HEV/DNS, guard, Accessibility,
  Device Owner, DAG, Admin, backend ni 13B.

## Correccion

- `INLINE_SVG` ya no usa geometria. Serializa el nodo SVG real con
  `XMLSerializer`, lo decodifica como `image/svg+xml`, lo dibuja en un canvas y
  lee dos muestras: rojo `#dc1430` en `(60,90)` y negro en `(20,90)`.
- La regresion rechaza `getBoundingClientRect` y exige `drawImage` mas ambos
  `getImageData`.
- Cleanup vuelve a consultar registrations y `caches.has("glosh-13a-v1")`;
  solo pasa si scope y cache quedan ausentes.
- Se corrigio solamente la infraccion ktlint introducida por 13A.

## Gates automaticos

Comando, exit code `0`:

```text
ANDROID_HOME=/Users/yejielnehmad/Library/Android/sdk ./gradlew --no-configuration-cache \
  :app-user:testDevDebugUnitTest \
  :app-user:compileDevDebugKotlin \
  :app-user:runKtlintCheckOverDevSourceSet \
  :app-user:runKtlintCheckOverTestDevSourceSet \
  :app-user:lintDevDebug \
  :app-user:assembleDevDebug
```

- `BUILD SUCCESSFUL in 3m 10s`; 834 tareas.
- Unitarios DEV: 186 tests, 0 failures, 0 errors.
- 13A fixture: 8/8; routing: 2/2.
- 11A relevantes: writer 6/6 y proxy connection 8/8.
- 11B relevantes: content authority 16/16 y fixture origin 5/5.
- Lint DEV: 0 errores; 30 warnings preexistentes.
- `git diff --check`: PASS.

APK:

- Package/version: `com.contentfilter.user.dev`, DEV355 / `1.0.1-dev`.
- Tamaño: `158926149` bytes.
- SHA-256: `4d5841dca3a13d5e1b1a6c3d008837266d91b7869b56681bcf37739694a48355`.

## Sesion A23

- Update in-place: `adb install -r`, `Success`.
- `ceDataInode` antes/despues: `1239519`.
- Device Owner y `Affiliated`: preservados.
- Accessibility: enabled y servicio bound; sin binding/crashed services al
  cierre.
- El primer intento quedo invalido por una desconexion transitoria de
  Accessibility durante UIAutomator; el guard suspendio Chrome fail-close.
- Tras rollback/start unico de recuperacion, el lab llego a `ready=true`.
- Chrome DevTools mostro una pagina `GLOSH13A_COMPLETE`, pero no se alcanzo a
  extraer el reporte de los diez vectores ni sus counters antes del crash. Ese
  titulo aislado no cuenta como PASS.

Falla decisiva:

```text
2026-08-26 00:08:35 -0300
com.android.chrome
Fatal signal 5 (SIGTRAP), code 1 (TRAP_BRKPT)
ApplicationExitInfo: reason=5 APP CRASH(NATIVE), status=5
```

El estado anterior al rollback tambien tenia `failures=3`. Por el contrato del
ticket no se reinicio Chrome, no se ejecuto 11B fisico y no se repitio la sesion.

## Rollback y residual

- Proxy cerrado, cache efimera limpiada y CA removida.
- Transporte `ready`, `ownedFdResources=0`.
- Chrome suspendido fail-close.
- Device Owner/Affiliated, datos y Accessibility preservados.
- Bloqueador: crash nativo de Chrome; los diez resultados, counters, cleanup
  poscondicion fisica y 11B fisico quedan sin validacion concluyente.

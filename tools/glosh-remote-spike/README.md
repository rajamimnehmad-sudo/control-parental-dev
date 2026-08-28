# Glosh Remote DEV35

Glosh Remote permite que un operador autorizado use ADB con identidad
`uid=2000(shell)` sobre un Android ya utilizado, sin conectar físicamente el
teléfono a la Mac. El spike está aislado en `tools/glosh-remote-spike/`; no forma
parte de App Usuario/Admin, Chrome, GloshIA, DAG ni Production.

## Estado vigente

DEV35 (`versionCode 35`, `versionName 0.1.0-dev35`) pasó físicamente en un
Samsung S22 Ultra con Android 16:

- PIN de seis dígitos desde la notificación;
- pairing de Wireless ADB y canal TLS local;
- agente autenticado y relay cifrado extremo a extremo;
- Mac y teléfono en redes distintas mediante un Cloudflare Quick Tunnel
  temporal;
- `whoami` con `uid=2000(shell)`;
- mantenimiento remoto y comandos ADB secuenciales;
- `owner-preflight` sin crash, timeout ni transporte stale.

El S22 probado no es todavía elegible para Device Owner: tiene un usuario
principal y 19 registros de cuentas. El preflight devuelve `eligible=false`,
`ownerState=NONE` y exige retirar manualmente las cuentas.

## Flujo actual

1. El operador abre broker, relay local y un Quick Tunnel nuevo.
2. El usuario toca **Conectar con soporte**.
3. Glosh Remote abre la ruta estándar de Depuración inalámbrica de Android 11+.
4. El usuario abre **Vincular dispositivo con código**.
5. Escribe los seis dígitos en la notificación y toca **Enviar**.
6. La app ejecuta `pair` → conexión TLS local → `whoami` → relay cifrado.

El flujo vigente no requiere Accessibility, overlays, ventanas flotantes,
clicks automáticos, scroll programático ni gestos por coordenadas. El wizard y
el guided assistant anteriores quedan como referencias históricas en
`GUIDED_ASSISTANT_08.md`; no describen DEV35.

## Arquitectura y seguridad

- El broker estable sólo coordina una ventana temporal y entrega al teléfono un
  descriptor sellado para esa solicitud.
- El relay de la Mac escucha en `127.0.0.1`; `cloudflared` publica un Quick
  Tunnel saliente y temporal, sin exponer ADB públicamente.
- Mac y agente prueban la clave de sesión con HMAC; comandos y resultados viajan
  cifrados con AES-256-GCM y secuencias anti-replay.
- Wireless ADB permanece local al teléfono. No se usa root, `adb tcpip 5555` ni
  un puerto ADB público.
- La identidad RSA/X.509 de ADB se conserva en almacenamiento privado de la app.
  Clave y certificado quedan cifrados con AES-GCM bajo una clave AES-256 no
  exportable de Android Keystore.
- El cierre normal libera la conexión viva pero conserva la identidad. Sólo el
  hook destructivo explícito `forgetIdentity` borra identidad y wrapping key.
- El maintenance shell acepta un único comando sin saltos de línea, con tamaño
  acotado, y lo ejecuta con identidad ADB shell. Es una capacidad amplia: debe
  usarse sólo con autorización vigente y alcance exacto.
- Al autenticarse el relay, la app toma un wake lock de pantalla/CPU y un Wi-Fi
  lock de alto rendimiento. Ambos se liberan al cerrar; Android también los
  libera si muere el proceso.
- El broker se cierra, la sesión se revoca y el Quick Tunnel termina durante el
  cleanup normal.

## Consola actual

La consola expone exactamente:

```text
ping
whoami
device
owners
users
battery
shell <comando>
logcat
preflight-owner
provision <apk>
status
requests
accept <request-id>
help
quit
```

`status` sólo confirma que existe un agente en el relay. `whoami` es el gate
mínimo para demostrar que ADB realmente sigue operativo.

## Transferencia, instalación y Device Owner

`provision <apk>` mantiene separadas las etapas:

1. ejecuta `owner-preflight` y aborta si no es elegible;
2. transfiere la APK en chunks con offset y tamaño acotados;
3. verifica tamaño total y SHA-256;
4. valida package de Glosh DEV, DeviceAdminReceiver, firmante único y SHA del
   certificado;
5. exige confirmación humana ligada al SHA y comprueba que el firmante enviado
   coincida con el firmante staged;
6. `owner-commit` vuelve a ejecutar el preflight, instala con ADB streaming,
   verifica la app instalada y sólo entonces puede emitir
   `dpm set-device-owner`;
7. confirma que Glosh quedó efectivamente como Device Owner.

`owner-preflight` y `owner-commit` son operaciones distintas. Nunca debe
ejecutarse el commit con un preflight no elegible ni borrarse cuentas
automáticamente.

El provisioning está implementado, pero falta el gate físico final con un
teléfono de cero cuentas:

```text
preflight eligible
→ transferir y validar APK
→ instalar
→ owner-commit
→ confirmar Device Owner
```

## Build y artefactos DEV35

Proyecto Gradle aislado:

```bash
./gradlew -p tools/glosh-remote-spike \
  :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Gate canónico:

```bash
ANDROID_HOME=/Users/yejielnehmad/Library/Android/sdk \
  bash tools/glosh-remote-spike/verify_guided_assistant.sh
```

El gate/CI de la review DEV35 conserva el archivo:

```text
tools/glosh-remote-spike/app/build/outputs/apk/debug/GloshRemote-MAINTENANCE-20-DEV35.apk
```

No se fija aquí un SHA de APK como canónico: un hash sólo debe atribuirse al
build local, físico o CI concreto que lo produjo.

## Operación

Procedimiento para Codex, diagnóstico y cleanup seguro:

[Operator runbook](OPERATOR_RUNBOOK.md)

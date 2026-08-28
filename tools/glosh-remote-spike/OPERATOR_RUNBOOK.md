# Glosh Remote DEV35: manual operativo para Codex

Este runbook define cómo abrir, verificar, usar y cerrar una sesión de Glosh
Remote desde la Mac. DEV35 (`versionCode 35`, `versionName 0.1.0-dev35`) está
físicamente probado en un S22 Ultra con Android 16, incluso con Mac y teléfono
en redes distintas.

## Regla de interacción

Cuando el usuario diga **"abrir sesión"**:

1. abrir una sesión nueva y persistente;
2. indicar cuándo enviar el PIN;
3. comprobar agente y ADB real;
4. esperar la siguiente instrucción del usuario.

Abrir la sesión no autoriza diagnósticos adicionales, cambios, borrados,
instalaciones, `owner-commit` ni Device Owner.

## Preflight mínimo

1. Leer `START_HERE.md`, Central vigente y el handoff Remote aplicable.
2. Revisar procesos, sesiones y worktrees reales. No asumir que relay, túnel o
   broker anteriores siguen sanos.
3. Confirmar que ningún otro frente usa el teléfono.
4. Verificar `cloudflared` y el entorno Python.
5. Verificar que existe la credencial privada del operador con permisos
   restrictivos, sin mostrarla ni copiarla a logs.
6. Si la prueba exige una APK concreta, comprobar versión, origen y SHA del
   artefacto antes de instalar.

La carga normal usa
`~/Library/Application Support/Glosh Remote/operator.key`. El código lee ese
archivo sin imprimirlo. Nunca pasar su contenido en una línea de comandos.

Antes de una sesión desde cero, comprobar que el broker esté cerrado:

```bash
curl -sS --max-time 15 \
  -H 'content-type: application/json' \
  -d '{"action":"discover"}' \
  https://syeycayasyufedwoprea.supabase.co/functions/v1/glosh-remote-broker
```

El resultado esperado es `available=false`. Si existe una consola propia
anterior, cerrarla con `quit` y esperar su cleanup. No matar procesos ajenos.

## Abrir broker, relay y Quick Tunnel

Desde `tools/glosh-remote-spike/mac/`, reutilizar el entorno preparado o crearlo
sólo si falta:

```bash
python3 -m venv .venv
.venv/bin/pip install -r requirements.txt
.venv/bin/python glosh_remote_relay.py --session-minutes 30
```

Mantener la consola en una PTY o sesión persistente. La ejecución normal:

- abre el relay sólo en `127.0.0.1`;
- crea un Cloudflare Quick Tunnel temporal;
- registra una ventana en el broker estable;
- acepta automáticamente sólo una solicitud pendiente;
- no entrega al usuario descriptor, clave de sesión ni credencial del operador.

El standby no consume el TTL autenticado. Con el comando anterior, los 30
minutos empiezan cuando el agente conecta.

## PIN, pairing y TLS

Indicar al usuario:

1. activar y dejar abierta **Depuración inalámbrica**;
2. tocar **Conectar con soporte**;
3. abrir **Vincular dispositivo con código**;
4. escribir los seis dígitos en la notificación de Glosh Remote;
5. tocar **Enviar** y avisar a Codex.

La app ejecuta `pair` con el endpoint de pairing vigente, descubre
`_adb-tls-connect`, abre el socket TLS ADB por loopback con la misma identidad y
ejecuta `whoami` antes de iniciar el relay. Usar siempre un PIN fresco.

## Tres estados que no deben confundirse

1. **Broker disponible:** existe una ventana de operador. No demuestra teléfono
   ni ADB.
2. **Agente autenticado:** la consola muestra `[agent] conectado`; el HMAC de la
   clave de sesión fue validado y `status` muestra el equipo. Demuestra relay,
   no la salud posterior de ADB.
3. **ADB operativo:** `whoami` responde PASS con `uid=2000(shell)` o equivalente.

Antes de cualquier operación dependiente de ADB, ejecutar:

```text
whoami
```

`status` nunca reemplaza este gate. `ping` prueba el round-trip cifrado sin ADB.

## Consola vigente

Usar sólo la sintaxis implementada:

```text
ping                     Round-trip sin ADB
whoami                   Identidad ADB shell
device                   Fabricante, modelo y Android
owners                   Device/Profile Owner
users                    Usuarios Android
battery                  Estado de batería
shell <comando>          Un comando remoto con identidad ADB shell
logcat                   Últimas 1000 líneas de logcat
preflight-owner          Usuarios, cuentas y owner; sólo lectura
provision <apk>          Preflight, transferencia, validación, instalación y DO
status                   Agente actual del relay
requests                 Solicitudes pendientes
accept <request-id>      Selección explícita de una solicitud
help                     Ayuda
quit                     Revocación y cierre
```

`shell <comando>` admite una sola línea, sin NUL/CR/LF y hasta 8 KiB. Aunque corre
como `uid=2000(shell)` y no como root, es una capacidad amplia: inventariar antes
de mutar y pedir confirmación exacta para acciones destructivas.

## Solicitudes duplicadas o múltiples

- Una única solicitud puede autoaceptarse una vez.
- Varias solicitudes fallan cerradas y requieren selección explícita.
- No aceptar por modelo una solicitud anterior sin discriminar cuál es la
  actual.
- Si un reinicio o intento interrumpido dejó solicitudes stale, cerrar la
  consola, verificar broker `available=false` y abrir una sesión nueva.
- No mezclar un descriptor aceptado antes del PIN actual con otro intento.

## Keep-awake real

Cuando el relay termina de autenticarse, `RemotePairingService` toma:

- `SCREEN_BRIGHT_WAKE_LOCK`, que mantiene pantalla/CPU durante soporte;
- `WIFI_MODE_FULL_HIGH_PERF`, que mantiene Wi-Fi en alto rendimiento.

Los locks no se toman durante standby o pairing. Se liberan en el cleanup de la
sesión; Android también los libera si muere el proceso. No modifican por sí
mismos el timeout persistente del usuario.

Si para un diagnóstico el operador cambia temporalmente
`screen_off_timeout`, debe leer y guardar primero el valor exacto, restaurarlo
antes de `quit` y verificarlo. Si ADB cae antes de restaurar, informar el
residual y restaurar como primera acción de la próxima sesión o pedir corrección
manual en **Ajustes > Pantalla > Tiempo de espera de pantalla**.

## Identidad ADB vigente

La identidad RSA/X.509 no vive sólo en memoria:

- se guarda en `SharedPreferences` privados de la app;
- clave privada y certificado se cifran por separado con AES-GCM y AAD;
- la wrapping key AES-256 es no exportable y vive en Android Keystore;
- el cierre normal ejecuta `releaseConnection`: cae el socket, pero la identidad
  sobrevive para reconectar con el mismo dispositivo vinculado;
- si el registro cifrado queda corrupto, se descarta y se genera una identidad
  nueva;
- sólo `forgetIdentity` borra explícitamente ciphertext y wrapping key.

No borrar identidad ni quitar el dispositivo vinculado como primera respuesta a
un fallo transitorio.

## Diagnóstico de conexión

### "ADB se vinculó, pero no pudimos completar el canal local"

El PIN fue aceptado, pero no se completó `_adb-tls-connect`/socket TLS local.
Sucede antes de que el agente llegue al relay.

Recuperación acotada:

1. cerrar la sesión degradada;
2. comprobar broker `available=false`;
3. apagar Depuración inalámbrica y esperar unos segundos;
4. volver a encenderla y dejar su pantalla abierta;
5. abrir broker, relay y Quick Tunnel nuevos;
6. generar y enviar un PIN nuevo.

### Broker aceptó, pero no hay agente

El descriptor fue aceptado, pero el teléfono no completó ADB local o el
bootstrap al relay. Observar el mensaje del teléfono; no ejecutar comandos ni
presentar la sesión como conectada.

### `status` responde, pero `whoami` falla

El relay sigue vivo y ADB local cayó. No continuar con mantenimiento. Recuperar
la sesión ADB y repetir `whoami`.

### Timeouts repetidos del broker/heartbeat

La sesión está degradada aunque el proceso siga vivo. Cerrarla y crear broker +
Quick Tunnel nuevos.

## Transferencia y validación de APK

`provision <apk>` implementa un flujo fail-closed:

1. calcula tamaño y SHA-256 en la Mac; rechaza archivos vacíos, no APK o mayores
   de 512 MiB;
2. abre una transferencia con UUID, tamaño y SHA esperados;
3. envía chunks Base64URL de 120 KiB; Android limita cada chunk a 128 KiB y exige
   offsets consecutivos;
4. guarda el staging en cache privada y sincroniza el archivo;
5. exige tamaño completo y SHA-256 exacto;
6. valida package `com.contentfilter.user.dev`, el
   `ProtectionDeviceAdminReceiver`, un único firmante actual, SHA del certificado
   y `versionCode`;
7. exige confirmación del operador ligada al SHA staged;
8. `owner-commit` vuelve a validar elegibilidad, instala con
   `pm install -r -S`, verifica receiver y firmante instalados y recién después
   puede emitir `dpm set-device-owner`.

Al cerrar o cancelar, el staging activo se descarta.

## Device Owner

`preflight-owner`/`owner-preflight` es siempre el primer gate y es sólo lectura.
`owner-commit` es una operación separada, destructiva y con autorización propia.

DEV35 pasó físicamente `owner-preflight` en el S22 con:

```text
eligible=false
ownerState=NONE
userCount=1
hasPrimaryUser=true
accountCount=19
blockReason=Retirá manualmente las cuentas antes de Device Owner.
```

Reglas:

- no ejecutar `owner-commit` si `eligible` no es `true`;
- no borrar cuentas automáticamente;
- retirar cuentas sólo con intervención consciente del usuario;
- `dpm set-device-owner` sólo puede emitirse dentro de `owner-commit`, después
  de un preflight elegible y de validar/instalar la APK correcta.

El provisioning está implementado, pero queda pendiente el gate físico final
con cero cuentas:

```text
preflight eligible
→ transferir y validar APK
→ instalar
→ owner-commit
→ confirmar Device Owner
```

## Operaciones y borrados

Antes de borrar o cambiar datos:

1. inventariar en modo lectura;
2. separar artefactos temporales de archivos personales;
3. informar rutas, cantidad y espacio recuperable;
4. pedir confirmación sobre objetivos exactos;
5. borrar sólo esos objetivos;
6. verificar conteo y espacio final.

Borrar un APK descargado no desinstala la app. No borrar por defecto fotos,
videos, backups, descargas incompletas, cuentas ni APK de terceros. Informar si
el borrado es permanente.

## Cierre normal y recuperación

1. Restaurar y verificar cualquier ajuste temporal mientras `whoami` responda.
2. Ejecutar `quit`.
3. Esperar que el relay cierre el WebSocket, destruya la clave de sesión en
   memoria y descarte staging activo.
4. Verificar broker `available=false`.
5. Verificar que relay y Quick Tunnel terminaron.
6. Confirmar agente desconectado y locks de pantalla/CPU/Wi-Fi liberados.
7. Informar cualquier restauración que no pudo verificarse.

Ante una interrupción, no afirmar cleanup por intención. Auditar estado real,
restaurar lo pendiente en la próxima conexión y no reutilizar sesiones stale.


# Glosh Remote: manual operativo para Codex

Este runbook define cómo abrir, verificar, usar y cerrar una sesión de Glosh
Remote desde la Mac. Su objetivo es que otro chat de Codex pueda operar la
conexión sin reutilizar estados viejos, exponer credenciales ni confundir el
relay con una conexión ADB funcional.

## Regla de interacción

Cuando el usuario diga **"abrir sesión"**:

1. abrir una sesión nueva y persistente;
2. esperar el PIN del usuario;
3. verificar la conexión;
4. no ejecutar diagnósticos ni cambios hasta que el usuario indique qué quiere
   hacer.

La apertura de una sesión no autoriza borrados, instalaciones, cambios de
configuración, `owner-commit`, Device Owner ni comandos adicionales.

## Preflight mínimo

Antes de abrir:

1. leer `START_HERE.md`, Central vigente y el handoff Remote aplicable;
2. revisar procesos, sesiones y worktrees locales reales;
3. no asumir que relay, Quick Tunnel o broker anteriores siguen sanos;
4. comprobar que `cloudflared` y el entorno Python estén disponibles;
5. comprobar que existe la credencial privada del operador con permisos
   restrictivos, sin imprimirla, copiarla ni incluirla en logs;
6. confirmar que no hay otro frente usando el mismo teléfono;
7. si se requiere una APK concreta, verificar versión y SHA antes de la prueba.

La carga normal de credenciales usa
`~/Library/Application Support/Glosh Remote/operator.key`. El código ya la lee
sin mostrar su contenido. Nunca pasarla en una línea de comandos visible.

Antes de una sesión desde cero, el broker debe responder `available=false`:

```bash
curl -sS --max-time 15 \
  -H 'content-type: application/json' \
  -d '{"action":"discover"}' \
  https://syeycayasyufedwoprea.supabase.co/functions/v1/glosh-remote-broker
```

Si hay una consola anterior, cerrarla con `quit` y esperar su cleanup. No matar
procesos ajenos ni reutilizar un túnel cuyo estado no se verificó.

## Abrir una sesión

Desde `tools/glosh-remote-spike/mac/`, usar el entorno ya preparado o crear uno
si realmente falta:

```bash
python3 -m venv .venv
.venv/bin/pip install -r requirements.txt
.venv/bin/python glosh_remote_relay.py --session-minutes 30
```

Mantener la consola en una PTY o sesión persistente. El relay debe:

- escuchar sólo en loopback;
- crear un Quick Tunnel nuevo;
- registrar una ventana nueva en el broker;
- no mostrar al usuario descriptor, claves ni credencial del operador.

La espera previa no consume el tiempo autenticado. Con el comando anterior, la
sesión segura dura 30 minutos desde que el agente conecta.

## Pasos para el usuario

Indicar, de forma breve:

1. activar y dejar abierta **Depuración inalámbrica**;
2. tocar **Conectar con soporte** en Glosh Remote;
3. abrir **Vincular dispositivo con código**;
4. escribir los seis dígitos en la notificación de Glosh Remote;
5. tocar **Enviar** y avisar a Codex.

Usar siempre un código fresco. No asumir que un código o puerto de un intento
anterior sigue vigente.

## Verificar la conexión correcta

Hay dos capas distintas:

1. **relay/agente**: la consola muestra `[agent] conectado` y `status` devuelve
   información del equipo;
2. **ADB real**: `whoami` devuelve `uid=2000(shell)` o equivalente.

`status` por sí solo **no demuestra ADB**. Antes de cualquier operación que
dependa de ADB, ejecutar:

```text
whoami
```

Sólo continuar si devuelve PASS y la identidad shell esperada. Si `status`
funciona pero `whoami` responde `ADB local no está conectado`, el relay sigue
vivo pero el canal ADB se perdió.

## Solicitudes pendientes

- Una única solicitud puede autoaceptarse.
- Varias solicitudes deben fallar cerradas a selección explícita.
- No aceptar a ciegas una solicitud anterior del mismo modelo.
- Si un reinicio o intento interrumpido dejó solicitudes duplicadas, cerrar la
  consola, comprobar cleanup y abrir una sesión nueva.
- No mezclar un descriptor aceptado antes del PIN actual con un reintento nuevo.

## Mantener temporalmente la pantalla encendida

Hacerlo sólo si el usuario lo pide. Primero verificar `whoami`, después guardar
el valor exacto:

```text
shell settings get system screen_off_timeout
```

Aplicar temporalmente:

```text
shell settings put system screen_off_timeout 2147483647; settings get system screen_off_timeout
```

Esto evita el apagado automático; no impide que el usuario bloquee el teléfono
manualmente. `svc power stayon` no sustituye este procedimiento en un teléfono
sin alimentación externa.

Antes de cerrar, restaurar el valor original y verificarlo. Ejemplo si el valor
original era `15000`:

```text
shell settings put system screen_off_timeout 15000; settings get system screen_off_timeout
```

Si el agente se desconecta antes de restaurar, no afirmar que quedó restaurado:
informar el residual y pedir verificación manual en **Ajustes > Pantalla > Tiempo
de espera de pantalla**, o restaurarlo como primera acción de la próxima sesión.

## Diagnóstico rápido

### "ADB se vinculó, pero no pudimos completar el canal local"

El PIN fue aceptado, pero Android no entregó a tiempo un endpoint
`_adb-tls-connect` utilizable. Sucede antes de que el agente llegue al relay.

Recuperación acotada:

1. cerrar la sesión degradada;
2. comprobar broker `available=false`;
3. apagar Depuración inalámbrica;
4. esperar unos segundos y volver a encenderla;
5. abrir sesión, túnel y broker nuevos;
6. generar un PIN nuevo.

No desinstalar la APK ni borrar la identidad ADB por reflejo.

### Broker aceptó, pero no aparece `[agent] conectado`

El teléfono obtuvo la sesión, pero no completó ADB local o el bootstrap hacia el
relay. No ejecutar comandos; observar el mensaje del teléfono y reiniciar el
flujo sólo si el estado es reproducible.

### Timeouts repetidos del broker o heartbeat

La sesión está degradada. Cerrarla y crear broker + Quick Tunnel nuevos. No
presentarla como saludable aunque el proceso local siga vivo.

### Pantalla apagada y `status` todavía responde

Verificar `whoami`. Puede sobrevivir el relay mientras ADB local ya está caído.

## Operaciones y borrados

Antes de borrar o cambiar datos:

1. inventariar en modo lectura;
2. separar artefactos temporales de archivos personales;
3. informar rutas, cantidad y espacio recuperable;
4. pedir confirmación sobre objetivos exactos;
5. borrar sólo esos objetivos;
6. verificar conteo final y espacio real recuperado.

Eliminar un APK descargado no desinstala la aplicación instalada. No borrar por
defecto fotos, videos, backups, descargas incompletas, cuentas ni APK de terceros.
Informar si el borrado es permanente y cómo podría recuperarse.

## Device Owner

`owner-preflight` es sólo lectura. Antes de cualquier aprovisionamiento debe
devolver una respuesta normal y controlada. Cuentas detectadas implican
`eligible=false`; el usuario debe retirarlas manualmente y después repetir el
preflight.

Nunca ejecutar sin autorización específica y vigente:

- `owner-commit`;
- `dpm set-device-owner`;
- borrado de cuentas;
- transferencia o instalación de Glosh Usuario;
- factory reset.

## Cierre obligatorio

1. restaurar cualquier ajuste temporal mientras ADB siga vivo;
2. ejecutar `quit` en la consola;
3. esperar que termine el proceso;
4. comprobar que el broker responde `available=false`;
5. comprobar que el relay y su Quick Tunnel terminaron;
6. informar agente desconectado y cualquier residual no verificable.

No dejar una sesión abierta por comodidad ni afirmar cleanup sin evidencia.


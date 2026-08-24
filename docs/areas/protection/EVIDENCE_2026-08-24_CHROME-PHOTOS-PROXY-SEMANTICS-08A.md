# CHROME-PHOTOS-PROXY-SEMANTICS-08A — evidencia de bloqueo

Resultado: `BLOCKED_ARCHITECTURE`.

## Base y aislamiento

- Base funcional: `aecdcd35a0736326ef2b27db8ea06114212184b9`.
- Rama: `work/chrome-photos-proxy-semantics-08a`.
- Worktree: `/private/tmp/glosh-chrome-photos-proxy-semantics-08a`.
- Owner de escritura confirmado: Proteccion Android / Codex.
- No se modifico el worktree cerrado de FULL-RESET-BOOTSTRAP-05.
- Glosh Central remoto fue revisado en `origin/build/glosh-control-center-v2`
  (`0d49368106fd3825bde774046211baec5c859ba1`). Su tracker aun describe el
  cierre hasta REAL-WEB-BATCH-01; no se lo modifico desde este ticket.

## Precheck A23

- Un dispositivo ADB: Samsung A23 `SM-A235M`, serial `R58T34V31AE`.
- App Usuario instalada: DEV 327 / `1.0.1-dev`.
- `ceDataInode=1239519`.
- Device Owner Glosh activo y `Affiliated`.
- Accessibility de Glosh enabled y bound.
- VPN `Content Filter VPN` conectada y validada.
- Chrome 151.0.7922.169 estaba `suspended=true`, conservando el fail-close de
  `accessibility_lost` del diagnostico anterior.
- No se ejecuto recovery, reset, borrado, instalacion ni cambio de policy.
- `resetCount` preservado en 1 segun el ultimo estado material confirmado; este
  ticket no ejecuto ninguna operacion capaz de incrementarlo.

## Causa arquitectonica exacta

El VPN vigente es un unico `VpnService` compartido por la lista de navegadores
instalados. `FilterVpnService.establishVpn()` construye una sola interfaz con:

1. una unica tabla de rutas mediante `Builder.addRoute()`;
2. un unico conjunto de aplicaciones mediante `addAllowedApplication()`;
3. Chrome, Google Search, Samsung Internet, Firefox, Edge y otros navegadores en
   ese mismo conjunto cuando estan instalados.

El data path actual es deliberadamente DNS-only:

- `DnsPacketParser` acepta UDP DNS;
- TCP, UDP no DNS y QUIC llegan como `Unsupported`;
- esos paquetes solo generan diagnostico y no se reinyectan ni se reenvian;
- no existe TCP/IP forwarder, NAT ni relay UDP generico;
- el paquete TUN no contiene identidad de aplicacion que permita aplicar una
  ruta a Chrome y otra a los demas navegadores.

La proteccion del laboratorio anterior evitaba bypass agregando rutas `/32` y
`/128` para un conjunto de IPs resuelto previamente y acotado a 32 direcciones.
Ese contrato funciona para hosts exactos, pero no constituye autoridad para un
host publico arbitrario o una IP nueva.

## Por que no es seguro ampliar la allowlist

### Ruta default dentro del VPN actual

Agregar `0.0.0.0/0` y `::/0` cerraria TCP/443 y UDP/443, pero tambien enviaria
al data path DNS-only todo el trafico de los otros navegadores incluidos. Sus
conexiones TCP/UDP serian descartadas. Esto rompe la proteccion/navegacion
productiva de otras apps y viola el alcance de 08A.

### VPN solamente para Chrome

Cambiar el conjunto permitido para incluir solo Chrome permitiria capturar todo
Chrome, pero excluiria del unico VPN a los otros navegadores. Android haria que
esas apps usen la red como si el VPN no existiera, perdiendo la autoridad DNS
productiva. Tampoco es admisible en este ticket.

### Rutas dinamicas por DNS o CONNECT

No resuelven la garantia:

- una ruta nueva requiere reestablecer la interfaz VPN;
- existe una carrera antes de que la ruta quede instalada;
- una conexion directa a un host/IP aun no observado no queda capturada;
- el DNS compartido no identifica de forma soportada que consulta vino de
  Chrome;
- los cambios de IP/CDN/rebinding fuerzan reconexiones y un conjunto de rutas
  creciente;
- otros navegadores que compartan esas IP tambien quedarian bloqueados.

### Proxy administrado como unica autoridad

`ProxySettings` con `fixed_servers` y bypass vacio es hardening util, pero no es
una autoridad de transporte independiente. La documentacion de Chrome indica
que `QuicAllowed` no esta soportada en Chrome Android, por lo que no existe en
este dispositivo una policy administrada oficial equivalente para cerrar
UDP/443. El gate exige bloquear un intento directo, no asumir que nunca ocurrira.

## Confirmacion con APIs oficiales

- `VpnService.Builder.addAllowedApplication()` selecciona aplicaciones para la
  interfaz completa; no acepta rutas por aplicacion.
- `Builder.addRoute()` agrega rutas para la interfaz, no para una aplicacion
  individual.
- una instancia de `Builder` solo puede tener allowlist o denylist de apps, no
  ambas.
- Android permite una sola conexion VPN activa por usuario.
- las apps no incluidas en la allowlist usan la red como si el VPN no estuviera
  activo.

Referencias:

- <https://developer.android.com/reference/android/net/VpnService.Builder>
- <https://developer.android.com/reference/android/net/VpnService>
- <https://chromeenterprise.google/policies/proxy-settings/>
- <https://chromeenterprise.google/policies/quic-allowed/>

## Opciones para una decision posterior

La opcion minima tecnicamente comprobable para continuar el spike seria una
decision de producto explicita:

1. durante Chrome Photos Protected Mode, suspender por Device Owner los demas
   navegadores;
2. configurar el unico VPN como full-tunnel exclusivo para Chrome;
3. mantener App Usuario/proxy fuera del tunnel para el upstream protegido;
4. descartar en el TUN todo TCP/443 y UDP/443 directo de Chrome;
5. restaurar de forma atomica la configuracion productiva al salir del modo.

Esto no se implemento porque 08A prohibe romper o sustituir la proteccion de
otras apps y no autoriza suspender otros navegadores.

La alternativa de producto mas amplia es redisenar el unico VPN para una
autoridad de transporte general que preserve el servicio a todos los
navegadores. Requiere un ticket arquitectonico propio: el TUN actual no tiene
forwarder TCP/UDP ni atribucion por UID y no puede agregarse como ajuste local
del proxy HTTPS.

## Trabajo deliberadamente no ejecutado

- No se generalizo CONNECT ni DNS admission.
- No se cambio semantica HTTP, headers ni sniffing de imagen.
- No se toco GloshIA, modelo, thresholds o preprocessing.
- No se ejecuto STOP/START del laboratorio.
- No se ejecutaron unitarios/Gradle porque no hubo cambio funcional.
- No se incremento versionCode.
- No se genero ni instalo APK.
- No hubo prueba fisica de navegacion general porque el anti-bypass obligatorio
  no puede quedar satisfecho con la arquitectura autorizada.

## Riesgos residuales

- El proxy DEV sigue limitado a la matriz exacta anterior.
- Chrome permanece fail-closed/suspendido en el A23.
- Generalizar solamente el proxy produciria una demostracion incompleta: la
  navegacion podria funcionar, pero sin autoridad independiente sobre
  TCP/UDP directo para destinos arbitrarios.


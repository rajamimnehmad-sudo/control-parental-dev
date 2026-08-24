# CHROME-VPN-PROCESS-DEATH-GUARD-10B — diseño previo

Estado: PREPARED / NO IMPLEMENTAR HASTA QUE TRANSPORT 10A TENGA DIRECCIÓN CONFIRMADA.

## Problema

La protección actual puede suspender Chrome cuando una dependencia pierde salud durante ejecución normal, pero sigue existiendo una ventana si el proceso que mantiene proxy/watchdog/attestation muere abruptamente antes de ejecutar cleanup voluntario.

Un `onDestroy()` no es una autoridad suficiente ante:

- SIGKILL/kill -9;
- native crash;
- Java fatal crash;
- low-memory kill;
- force-stop;
- package update/replacement;
- boot/direct boot;
- proceso principal trabado.

## Objetivo

Tener una autoridad mínima e independiente que mantenga Chrome suspendido salvo que exista una lease de protección vigente y verificable.

## Diseño de procesos

```text
App Usuario main process
  proxy / GloshIA / coordination
          |
          | signed/generation heartbeat
          v
:chrome_guard process
  minimal state machine
  device-protected storage
  suspend/verify/retry authority
          |
          v
DevicePolicyManager -> Chrome
```

El guard no analiza imágenes ni ejecuta GloshIA.

## Estado por defecto

Regla:

`NO VALID LEASE => CHROME SUSPENDED`

No usar:

`last known good => keep released indefinitely`.

## Lease

Campos conceptuales:

- schemaVersion;
- protectionGeneration;
- sessionId;
- issuedAt elapsedRealtime;
- expiresAt elapsedRealtime;
- mainProcessNonce;
- transportGeneration;
- proxyGeneration;
- modelGeneration;
- accessibilityGeneration;
- authenticated MAC/signature local.

No guardar secretos remotos.

TTL corto; renovar antes del deadline.

## IPC

Preferencia:

- Binder explícito/private service o mecanismo Android local equivalente;
- caller UID/package validado;
- nonce/generation anti-stale;
- no broadcasts implícitos exportados.

El guard debe rechazar heartbeat viejo aunque venga del mismo package.

## Storage

Sólo estado mínimo en device-protected storage para Direct Boot:

- última generation;
- desired suspended=true/false condicionado;
- último motivo;
- boot marker.

No persistir CA/private keys/model data.

## Suspend authority

Función `ensureChromeSuspended(reason)`:

1. llamar DevicePolicyManager;
2. leer/verificar estado efectivo si API lo permite;
3. si falla, retry bounded con backoff corto;
4. emitir estado degraded si no logra acreditar suspensión;
5. nunca emitir release lease mientras suspension/release authority esté incierta.

## Release authority

El guard sólo libera Chrome si:

- main heartbeat vigente;
- transport health vigente;
- VPN vigente;
- proxy/policy vigentes;
- GloshIA vigente;
- Accessibility vigente;
- bootstrap generation válida;
- no pending direct-boot guard;
- no process-death incident sin recovery.

La lista concreta debe alinearse con attestation actual; no duplicar lógica inconsistente.

## Main process death

Si heartbeat expira:

- suspender Chrome;
- revocar lease local;
- registrar `main_process_lost`;
- no esperar a `onDestroy()`;
- dejar VPN always-on/lockdown como barrera de red cuando esté disponible.

## Native transport crash

Si HEV o transport queda en proceso main y causa crash:

- guard ve heartbeat expirar;
- Chrome se suspende;
- siempre-on VPN reduce bypass de red;
- recovery sólo después de transport generation nueva.

Si el riesgo nativo sigue alto, evaluar aislar HEV en proceso dedicado posterior; no decidirlo antes de medir.

## Force-stop

Force-stop puede impedir que componentes del package arranquen hasta interacción/boot según contexto Android. El gate debe medir comportamiento real del device-owner package y documentar límites de plataforma.

Si force-stop deja al guard incapaz de ejecutar, seguridad de red debe depender de always-on VPN/lockdown + Device Owner policies ya persistidas. No afirmar una garantía que Android no permita.

## Boot

LOCKED_BOOT_COMPLETED:

- Chrome suspendido por default;
- no release en Direct Boot;
- esperar credential-unlocked + main health completa;
- reset Chrome no se repite;
- resetCount permanece 1.

BOOT_COMPLETED:

- nueva generation;
- transport/proxy/GloshIA tienen que reacreditarse;
- sólo después release.

## Package replaced/update

- guard arranca en suspended state;
- invalidar generation anterior;
- verificar schema/migration;
- no reutilizar lease vieja;
- no repetir full Chrome reset salvo migration explícita separada.

## Always-on / lockdown

10B debe evaluar y preferir Device Owner APIs oficiales:

- always-on VPN para Glosh;
- lockdown si compatible con flujos requeridos;
- recovery/update sin ventana de Internet directa.

Gate debe comprobar vendor/API29-36.

## Watchdog

No usar polling agresivo.

- deadline de lease por elapsed realtime;
- timer/event-driven;
- bounded wakeups;
- batería medida.

## Reasons

Motivos técnicos internos estables:

- `lease_expired`;
- `main_process_lost`;
- `transport_lost`;
- `vpn_lost`;
- `proxy_lost`;
- `policy_lost`;
- `gloshia_lost`;
- `accessibility_lost`;
- `boot_guard`;
- `package_replaced_guard`;
- `suspension_unverified`.

UX puede mapearlos a mensajes humanos.

## Gates automáticos

- stale lease rejected;
- wrong generation rejected;
- wrong caller rejected;
- expired lease suspends;
- current lease allows release only with all health;
- repeated suspension idempotent;
- retry bounded;
- reboot storage migration;
- package replace invalidation.

## Gate físico

A23 + S22:

1. normal process kill;
2. `am force-stop` si permitido por gate y seguro;
3. deliberate Java crash DEV;
4. deliberate native subprocess/transport crash DEV;
5. low-memory/process eviction aproximada;
6. reboot locked/unlocked;
7. APK update in-place;
8. VPN restart/handover.

Medir:

- exposición visual después de health loss;
- tiempo a Chrome suspended;
- tráfico directo posible;
- recovery;
- battery/wakeups;
- crash/ANR loops.

## Security gate

Para pérdida de main process:

- raw/new web exposure después de expiration = 0;
- Chrome suspension acreditada;
- direct network bypass=0 bajo always-on/lockdown cuando soportado;
- recovery requiere generation nueva.

## Modularidad

El guard debe ser pequeño y auditable. No copiar proxy/VPN/GloshIA al segundo proceso.

Objetivo aproximado:

- state machine;
- lease verifier;
- DPM suspension authority;
- boot/update receiver;
- minimal diagnostics.

## Definition of Done

10B PASS cuando kill/crash/reboot/update no dejan una ventana donde Chrome permanezca usable fuera de una lease vigente y la recuperación sea automática, bounded y reproducible.
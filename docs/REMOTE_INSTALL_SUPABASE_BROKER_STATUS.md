# Glosh Remote — Supabase broker status

Updated: 2026-08-24 10:00 ART

## REMOTE-INSTALL-NOLINK-GUIDED-03A

- UX/wizard local candidate PASS FINAL ChatGPT at local HEAD `5af84ca4aa3701f91606ef61957f6494d90b3b94`.
- Superseded for live no-link testing by the Supabase-integrated candidate below.

## REMOTE-SESSION-BROKER-SUPABASE-01

Status: PASS FINAL DEV — técnico + físico no-link + cross-network.

Supabase project: `syeycayasyufedwoprea`.

Backend deployed:
- Edge Function `glosh-remote-broker` v2 ACTIVE.
- Endpoint: `https://syeycayasyufedwoprea.supabase.co/functions/v1/glosh-remote-broker`.
- Database objects: `public.glosh_remote_broker_config`, `public.glosh_remote_support_windows`, `public.glosh_remote_support_requests`.
- RLS enabled; anon/authenticated have no direct table access; service_role only for broker CRUD.
- Requests use nonce hashing, short TTL, explicit acceptance, single-use claim, revocation and anti-replay.
- Broker stores ciphertext only, never join/session key plaintext.
- `seal_context_sha256 = SHA-256(request_id + ':' + nonce)` preserves request+nonce binding without exposing raw nonce to operator.

Local integration PASS and ChatGPT diff review PASS:
- Base local `5af84ca4aa3701f91606ef61957f6494d90b3b94`.
- Supabase integration commit `a2ff10744d9c867087f3747ceb9f587b42a96861` (`feat(remote): connect no-link flow to Supabase broker`).
- Android client POST `discover/request/poll/claim/revoke` and Mac client `operator_open/list/accept/revoke/close`.
- RSA-3072 / OAEP-SHA256 V2 bound to `SHA-256(request_id + ':' + nonce)`.
- HTTP live gate from Mac PASS end-to-end, including 401 without operator key, explicit accept, ciphertext-only broker, local decrypt, second claim 409, revoke and close.

Physical no-link + cross-network gate PASS:
- Final local HEAD `475bd35b2934f9dca1a54f0b29dc4c320eacd223`.
- Local commit `fix(remote): renew expired support requests` reviewed by ChatGPT: PASS.
- Worktree clean.
- APK `GloshRemote-NoLink-Retry-DEV.apk`.
- Size `19,061,222` bytes.
- SHA-256 `5448e97dc458e3770a0ca82fe18e3124a7fcbad0034a1a72dbd5b74c537fbc3b`.
- Device Samsung SM-S908E / Android 16 / SDK 36.
- Physical run was performed with Mac and S22 on different Wi-Fi networks; cross-network requirement is satisfied.
- No-link flow PASS; guided wizard PASS; Wireless Debugging + 6-digit pairing PASS; WSS/HMAC/AES PASS.
- `ping=pong`; `whoami` contains `uid=2000(shell)`; `device` correct; `status` authenticated; non-allowlisted `uname` rejected.
- UX CONNECTED PASS; rotation/recreation preserves CONNECTED; cancel/revoke PASS; broker, relay and Quick Tunnel closed; no crash/ANR.

Timeout retry fix:
- Expired broker requests renew with fresh identity, nonce and request ID.
- Maximum five requests / roughly ten minutes.
- Renewal only for `expired`; cancellation, revocation and errors remain fail-closed.
- Unit tests PASS.

## REMOTE-INSTALL-OEM-GUIDANCE-02

Status: DESIGN FINAL / IMPLEMENTATION LOCAL PENDING — prioridad inmediata antes de pilotos adaptativos.

Objetivo: una persona sin conocimientos técnicos debe poder completar el emparejamiento siguiendo una sola acción por pantalla.

### Flujo final aprobado
1. Usuario toca `CONECTAR CON SOPORTE`.
2. Glosh hace sólo `discover` para confirmar que soporte está disponible. Todavía NO crea request, identidad RSA ni relay session para ese teléfono.
3. `Paso 1 de 3 · Preparar el teléfono`: Glosh detecta OEM y guía Developer Options con animación propia. El usuario puede tardar sin consumir TTL de request.
4. Al volver, Glosh pregunta de forma humana `¿Viste el mensaje “Ya sos desarrollador”?`; `SÍ, SEGUIR` avanza. No usar falsa autodetección del setting.
5. Recién ahora crear request efímera al broker. Operador recibe/acepta. Mientras tanto mostrar `Soporte se está preparando…`.
6. Cuando descriptor está listo: `Paso 2 de 3 · Abrir conexión`; animación `Depuración inalámbrica → Activar → Emparejar dispositivo con código`; abrir Settings.
7. `Paso 3 de 3 · Ingresar código`: los 6 dígitos se pueden ingresar desde notificación mientras se mira Ajustes o desde Glosh; ambos llaman al mismo pairing entrypoint y auto-submit al sexto dígito.
8. Estado final `¡Listo, Glosher! Soporte ya está conectado de forma segura.`

Si soporte deja de estar disponible mientras el usuario completa Paso 1, al tocar `SÍ, SEGUIR` el request falla limpio y se muestra `Soporte se desconectó. Tocá REINTENTAR.` No se pierde el progreso visual del OEM.

### Detección y recetas
- Detectar `Build.MANUFACTURER`, `Build.BRAND`, modelo, Android y SDK; normalizar case/espacios.
- Samsung → receta Samsung.
- Motorola → receta Motorola.
- Xiaomi, Redmi y POCO → receta Xiaomi family.
- Cualquier otro OEM → receta Android genérica.
- No intentar detectar `Settings.Global.DEVELOPMENT_SETTINGS_ENABLED`: Android documenta que para apps de terceros siempre devuelve 0; usar confirmación humana simple en vez de una detección falsa.

### Animación nativa
- No GIF ni video. Componente Android nativo liviano, vectorial/programático, en loop.
- Mostrar un teléfono estilizado con filas de Ajustes; una sola fila por vez recibe pulso lima + flecha/mano; transición suave a la siguiente pantalla.
- Samsung: `Acerca del teléfono → Información de software → Número de compilación ×7`.
- Motorola: `Acerca del teléfono` o `Sistema → Acerca del teléfono → Número de compilación ×7`.
- Xiaomi/Redmi/POCO: `Acerca del teléfono → Información detallada y especificaciones → versión OS/MIUI varias veces`; luego `Ajustes adicionales → Opciones de desarrollador`.
- Segunda animación para todos: `Opciones de desarrollador → Depuración inalámbrica → Activar → Emparejar dispositivo con código`.
- Respetar `ValueAnimator.areAnimatorsEnabled()`; con animaciones desactivadas mostrar frame estático resaltado.
- Detener animaciones en `onPause` y reiniciarlas en `onResume`; sin leaks ni animadores duplicados.

### UX para usuario básico total
- Una sola acción principal por pantalla; evitar tres botones simultáneos.
- Progress visible y humano: `1 de 3 · Preparar el teléfono`, `2 de 3 · Abrir conexión`, `3 de 3 · Ingresar código`.
- No usar en copy normal: ADB, descriptor, nonce, broker, endpoint, RSA, WSS, Developer API.
- Sí usar el nombre exacto que verá en Android cuando sea imprescindible: `Número de compilación`, `Opciones de desarrollador`, `Depuración inalámbrica`, `Emparejar dispositivo con código`.
- Mensaje tranquilizador breve: `No cambies nada más. Glosh te indica exactamente qué tocar.`
- En paso 1, CTA principal `ABRIR AJUSTES`; secundario discreto `YA LO TENGO ACTIVADO`.
- Al volver de Ajustes: `¿Viste el mensaje “Ya sos desarrollador”?` → `SÍ, SEGUIR`; `NO ME APARECIÓ` repite la animación específica.
- Agregar `ME PERDÍ`/`MOSTRAR DE NUEVO` como escape en cada etapa.
- Notificación durante Ajustes con una sola instrucción contextual y acción `VOLVER A GLOSH`.
- No mostrar múltiples decisiones técnicas ni pedir copiar/pegar nada.

### Apertura de Settings
- Usar intents oficiales donde existan y resolverlos con PackageManager.
- `Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS` es el fallback obligatorio y soportado por Android.
- Para Wireless Debugging conservar cualquier ruta OEM/directa ya probada sólo si resuelve en ese dispositivo; si no, abrir Developer Settings y dejar la animación/notificación mostrando qué tocar.
- No overlays sobre Ajustes, no Accessibility extra para manipular Settings, no auto-scroll/taps falsos.

### Código de 6 dígitos
- Camino principal dentro de Glosh: seis casilleros grandes, `inputType=number`, longitud exacta 6, foco y teclado numérico automáticos.
- Al completar el sexto dígito, enviar automáticamente; sin botón `Aceptar` extra.
- La notificación con RemoteInput sigue disponible mientras el usuario permanece viendo el código en Ajustes.
- App y notificación deben llamar al mismo entrypoint/estado del `RemotePairingService`; nunca dos implementaciones de pairing.
- Guard anti-doble-submit: un solo intento activo; entradas duplicadas o concurrentes se ignoran de forma segura.
- Mientras empareja: `Conectando… no cierres Glosh`.
- Si el código vence/falla: `Ese código ya no sirve. Generá uno nuevo y escribilo acá.` + CTA `ABRIR DEPURACIÓN INALÁMBRICA`.
- `NO VEO EL CÓDIGO` vuelve a mostrar animación `Activar → Emparejar con código` y reabre Settings.

### Preservar arquitectura validada
- No cambiar backend/broker Supabase, crypto, RSA/OAEP, HMAC/AES, relay, allowlist ni retry de requests expiradas.
- El cambio de timing `discover → guía → request` es sólo orquestación Android; contrato HTTP permanece intacto.
- Mantener `FLAG_SECURE` y estados autoritativos IDLE/PREPARING/CONNECTED.
- No tocar Chrome, GloshIA, DAG, App Usuario/Admin ni Device Owner.

### Gates esperados
- Tests unitarios de clasificación OEM, secuencia de recetas, reduced-motion, orquestación `discover-before-request`, soporte desaparece durante Paso 1, submit 6 dígitos, doble-submit y timeout/fallo.
- Tests existentes Android/Python sin regresiones.
- lintDebug 0 errores; assembleDebug PASS.
- APK nueva para gate visual Samsung S22 primero.
- Validar físicamente Samsung; Motorola y Xiaomi quedan como recetas implementadas + tests hasta disponer de cada hardware, sin declarar gate físico de esos OEM.

Fuentes oficiales verificadas 2026-08-24: Samsung Developer/Samsung Support, Motorola Support, Xiaomi Global Support y Android Developers.

## Next route
- `REMOTE-INSTALL-CONNECTION-00`: PASS FINAL DEV / CLOSED.
- `REMOTE-INSTALL-OEM-GUIDANCE-02`: prioridad inmediata.
- `REMOTE-ADAPTIVE-INSTALL-PILOT-01`: espera el gate Samsung del guiado OEM.

Security debt before product:
- Rotate the operator credential before product/general release.
- Add install-scoped/user-verifiable binding for anonymous rendezvous before general release.

No Chrome, GloshIA, DAG, App Usuario/Admin or pre-existing Edge Function should be modified by this UX task.

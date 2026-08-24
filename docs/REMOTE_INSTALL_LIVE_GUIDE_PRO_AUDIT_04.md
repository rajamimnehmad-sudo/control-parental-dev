# Glosh Remote Live Guide — auditoría profesional y rediseño V2

Updated: 2026-08-24 16:50 ART

## Veredicto

**REMOTE-INSTALL-LIVE-GUIDE-03 queda FAILED UX en el gate físico.** La conexión no-link, broker, relay, crypto, pairing y allowlist siguen PASS FINAL DEV; la falla queda aislada a la capa temporal de guiado dentro de Ajustes.

La dirección anterior intentó demasiada autonomía simultánea: inferir pantalla, hacer scroll continuo, dibujar overlay, avanzar estados y entrar en rescue mode sobre una UI OEM cambiante. El resultado físico fue confuso e inestable.

## Hallazgos P0

### P0.1 — autoridad de ventana insuficientemente estricta

`AccessibilityService.getWindows()` puede contener ventanas de aplicación, input method y `TYPE_ACCESSIBILITY_OVERLAY`. El matcher V2 debe observar exclusivamente una ventana `TYPE_APPLICATION` del paquete Settings confiable. Si hay más de una ventana Settings plausible y no existe una autoridad única/focused, debe fallar cerrado en vez de adivinar. El contenido visual del overlay Glosh nunca es autoridad de matching.

### P0.2 — carrera entre snapshots, nodos y geometría

Las ventanas/nodos de Accessibility representan snapshots de estado. Un target puede encontrarse en una jerarquía y quedar obsoleto antes de dibujar el rectángulo. Cada decisión debe partir de un root fresco después de una ventana de estabilidad; cualquier evento nuevo invalida el token de scan/highlight anterior.

### P0.3 — scroll continuo que compite con el usuario

El scroll autónomo de fondo desorienta y puede pelearse con el dedo. V2 adopta **scroll explícitamente solicitado**: si el target no está visible, la guía muestra `MOSTRARME`; recién al tocarlo se arma una secuencia acotada de reveal/scroll. Un scroll humano cancela esa secuencia y activa cooldown.

### P0.4 — tormenta de eventos y concurrencia

`TYPE_WINDOW_CONTENT_CHANGED`, `TYPE_VIEW_SCROLLED`, `TYPE_WINDOW_STATE_CHANGED` y `TYPE_WINDOWS_CHANGED` pueden llegar en ráfagas. El motor necesita una única cola/actor, generations monotónicas y cancelación de trabajo stale. Nunca puede haber dos scans, scrolls o overlays actualizándose en paralelo.

### P0.5 — contexto de pantalla insuficiente

Buscar sólo por texto no alcanza. El mismo texto puede estar en toolbar, descripción, coach u otra pantalla. V2 requiere alias exacto + contexto de pantalla + rol/clickability + view/resource id cuando exista. Si dos candidatos son parecidos, no elige ninguno.

### P0.6 — autoridad de pairing demasiado permisiva

El submit de pairing debe estar permitido sólo en estado `PAIR_CODE`, con request activo, y pasar por el mismo guard para Accessibility/app/notificación. `CONNECTED` no puede aceptarse sin request y pairing activos.

## Hallazgos P1

- La tarjeta flotante grande tapa contenido y aumenta ruido visual.
- Rescue mode se dispara demasiado pronto y hace parecer incorrecta una pantalla válida.
- El flujo obliga a habilitar Developer Options aunque quizá ya estén activadas.
- La lectura automática del código de 6 dígitos es sólo una optimización; los seis casilleros y RemoteInput deben seguir siendo el camino garantizado.
- Accessibility agrega un permiso complejo y debe ser mejora reversible, no dependencia única.
- Faltaba observabilidad local segura para saber por qué falló sin guardar texto ni árboles.

## Rediseño: Progressive Assistance V2

### Fast path

1. `CONECTAR CON SOPORTE` ejecuta sólo `discover`.
2. Glosh intenta abrir Developer Settings.
3. Si encuentra `Depuración inalámbrica` con confianza HIGH, salta la etapa de `Número de compilación`.
4. Si no, abre la receta OEM para habilitar Developer Options.

### Dentro de Ajustes

- Barra delgada abajo/arriba, no tarjeta grande draggable.
- Una sola frase por vez: `Tocá Información de software`.
- Si el target está visible: highlight inmediato sólo tras snapshot estable.
- Si no está visible: `MOSTRARME`; sin esa acción la pantalla no se mueve.
- `ME PERDÍ` re-clasifica la pantalla sin reiniciar la sesión.
- Cerrar/minimizar siempre disponible.

### Política de estabilidad y scroll

- Seleccionar una única ventana Settings `TYPE_APPLICATION` confiable.
- Esperar 350–500 ms y al menos dos fingerprints equivalentes.
- Re-adquirir root antes de cada decisión/acción.
- Token `(windowId, generation, fingerprint)` para descartar resultados stale.
- Excluir overlays, teclado y otras apps.
- `ACTION_SHOW_ON_SCREEN` una sola vez cuando corresponda.
- Máximo 3 scrolls de página como fallback dentro de una secuencia armada por `MOSTRARME`.
- Cortar por no-progress, cambio de pantalla/paquete/ventana, scroll humano, ambigüedad o timeout.
- Después de scroll humano, suprimir movimiento automático al menos 1.4 s.

### Matcher

- Sólo ventana Settings seleccionada.
- Alias localizados explícitos; nada de fuzzy amplio.
- Contexto de título/pantalla obligatorio para targets sensibles.
- View id suma confianza, pero no es requisito universal.
- El mejor candidato debe superar threshold y separar al segundo por margen.

### Overlay

- Highlight full-screen no touchable/no focusable.
- Coach bar no focusable y fuera de la autoridad del matcher.
- Recalcular bounds tras cada scroll/layout/window event.
- Borrar highlight inmediatamente al cambiar generation/window id.
- Reduced motion usa borde estático.

### Código de pairing

- Camino garantizado: seis casilleros numéricos en Glosh + RemoteInput.
- Camino opcional: leer exactamente un código de seis dígitos sólo en el diálogo esperado y con semántica cercana de pairing.
- Un único submit guard para Accessibility/app/notificación.

## Observabilidad segura para pilotos

Guardar localmente únicamente:
- stage key;
- duración;
- confidence bucket;
- intentos de scroll;
- cantidad de rescates;
- resultado;
- familia OEM.

Nunca guardar texto de nodos, tree dumps, screenshots, códigos, nonces, descriptores, session keys o identidad de cuentas.

## Gates físicos V2

### Gate A — guía Samsung aislada

Sin broker/relay. Validar clasificación de pantalla, exclusión del overlay, scroll solicitado, cooldown humano, highlight, rotación y recuperación en `Información de software`, `Número de compilación` y `Depuración inalámbrica`. Repetir targets 10 veces; un highlight incorrecto = FAIL.

### Gate B — pairing UX

Broker/relay activos. Validar que no se consuma TTL durante el aprendizaje, seis casilleros, RemoteInput, single-submit y lectura opcional del código.

### Gate C — sesión remota completa

Validar HMAC/AES, allowlist, CONNECTED, limpieza de overlay, `disableSelf`, cancel/revoke y cero acceso residual.

No volver a probar todos los subsistemas juntos en el primer ciclo.

## Trabajo completado por ChatGPT sin Codex

Se preparó fuera del repo un paquete de referencia aislado con:
- catálogo OEM Samsung/Motorola/Xiaomi/Generic;
- matcher determinista;
- `SettingsWindowSelector` que excluye overlay/IME/apps ajenas y falla cerrado ante ventanas ambiguas;
- `ScanGenerationGuard` para impedir aplicar resultados stale;
- stability gate;
- política de scroll que **no mueve nada sin `MOSTRARME`** y cancela ante scroll humano;
- detector contextual de PIN;
- state machine fail-closed con autoridad estricta de pairing/CONNECTED;
- telemetría segura;
- fixture Samsung S22 / Android 16 / español;
- contrato exacto de port Android;
- gates de aceptación separados;
- prototipo HTML interactivo;
- **30/30 tests Python/fixture PASS**.

Artifact local: `glosh_remote_live_guide_v2_prototype.zip`.
SHA-256: `b84c2ddb88bdc0a4d36fc01f5fdbdecc0c32789ec9fc6e005391aa39fb5ce3cd`.
Size: `35,807` bytes.

El paquete incluye `HANDOFF_MANIFEST.json` con SHA-256 individual de cada artefacto de referencia.

## Validación oficial Android consultada

Android Developers confirma que `FLAG_RETRIEVE_INTERACTIVE_WINDOWS` habilita `getWindows()`/`TYPE_WINDOWS_CHANGED`; `AccessibilityWindowInfo` distingue `TYPE_APPLICATION`, `TYPE_ACCESSIBILITY_OVERLAY` y `TYPE_INPUT_METHOD`; `AccessibilityServiceInfo.packageNames` y `setServiceInfo()` permiten acotar dinámicamente eventos por paquete; y `disableSelf()` apaga el servicio. La implementación V2 debe utilizar esas APIs de forma acotada y no declarar `canPerformGestures` porque V2 no usa gestos sintéticos.

## Próximo trabajo que sí requiere Codex/Mac

1. Restaurar y leer el HEAD local real `93735b1c...` o sucesor.
2. Auditar la implementación exacta y responder las hipótesis P0 con evidencia de código.
3. Portar V2 siguiendo `ANDROID_PORTING_CONTRACT.md`, sin tocar broker/crypto/relay.
4. Compilar APK.
5. Ejecutar Gates A/B/C por separado en el S22.
6. Entregar diff y evidencia para revisión ChatGPT.

## Coordinación

- `REMOTE-INSTALL-CONNECTION-00`: PASS FINAL DEV / CLOSED.
- `REMOTE-INSTALL-LIVE-GUIDE-03`: FAILED UX físico / SUPERSEDED por V2.
- `REMOTE-INSTALL-LIVE-GUIDE-V2-04`: PRE-CODEX REFERENCE COMPLETE / integración local pendiente.
- `REMOTE-ADAPTIVE-INSTALL-PILOT-01`: sigue esperando gate físico V2.
- No tocar Chrome, GloshIA, DAG, App Usuario/Admin, Supabase ni Device Owner productivo.
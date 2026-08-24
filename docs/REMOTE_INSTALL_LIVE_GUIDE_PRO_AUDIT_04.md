# Glosh Remote Live Guide — auditoría profesional y rediseño V2

Updated: 2026-08-24

## Veredicto

**REMOTE-INSTALL-LIVE-GUIDE-03 queda FAILED UX en el gate físico.** La conexión no-link, broker, relay, crypto, pairing y allowlist siguen PASS FINAL DEV; la falla queda aislada a la capa temporal de guiado dentro de Ajustes.

La dirección anterior intentó demasiada autonomía simultánea: inferir pantalla, hacer scroll continuo, dibujar overlay, avanzar estados y entrar en rescue mode sobre una UI OEM cambiante. El resultado físico fue confuso e inestable.

## Hallazgos P0

### P0.1 — contaminación por el propio overlay

Una ventana `TYPE_ACCESSIBILITY_OVERLAY` puede aparecer en el conjunto de ventanas Accessibility. Si el scanner recorre todas las ventanas o usa `rootInActiveWindow` sin filtrar, textos de la propia tarjeta Glosh —por ejemplo `Número de compilación`— pueden competir con la fila real de Ajustes. El matcher V2 debe observar exclusivamente ventanas `TYPE_APPLICATION` del paquete Settings confiable; el contenido visual del overlay debe quedar fuera del árbol útil del matcher.

### P0.2 — carrera entre nodos obsoletos y geometría

Los nodos Accessibility son snapshots y pueden quedar desactualizados cuando cambia la ventana, el layout o el scroll. Un target puede encontrarse en una jerarquía y dibujarse después de que la lista ya se movió. Cada decisión debe partir de un root fresco después de una ventana de estabilidad; cualquier evento nuevo cancela scan, scroll y highlight anteriores.

### P0.3 — scroll continuo que compite con el usuario

El scroll autónomo de fondo desorienta y puede pelearse con el dedo. V2 adopta **scroll solicitado y de una sola vez**: si el target no está visible, la guía muestra `MOSTRARME`; recién al tocarlo intenta `ACTION_SHOW_ON_SCREEN` o un scroll acotado. Nunca sigue moviendo la pantalla mientras el usuario navega.

### P0.4 — tormenta de eventos y concurrencia

`TYPE_WINDOW_CONTENT_CHANGED`, `TYPE_VIEW_SCROLLED`, `TYPE_WINDOW_STATE_CHANGED` y `TYPE_WINDOWS_CHANGED` pueden llegar en ráfagas. El motor necesita una única cola/actor, generations monotónicas y cancelación de trabajo stale. Nunca puede haber dos scans, scrolls o overlays actualizándose en paralelo.

### P0.5 — contexto de pantalla insuficiente

Buscar sólo por texto no alcanza. El mismo texto puede estar en toolbar, descripción, overlay propio u otra pantalla. V2 requiere alias exacto + contexto de pantalla + rol/clickability + view/resource id cuando exista. Si dos candidatos son parecidos, no elige ninguno.

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

- Barra delgada abajo, no tarjeta grande draggable.
- Una sola frase: `Tocá Información de software`.
- Si el target está visible: highlight inmediato.
- Si no está visible: `MOSTRARME`; sólo entonces un intento de scroll acotado.
- `ME PERDÍ` re-clasifica la pantalla sin reiniciar la sesión.
- Cerrar/minimizar siempre disponible.

### Política de estabilidad y scroll

- Esperar 350–500 ms y al menos dos snapshots iguales de una ventana Settings de aplicación.
- Excluir overlays, teclado y otras apps.
- `ACTION_SHOW_ON_SCREEN` una sola vez si el nodo ya existe.
- Máximo 3 scrolls de página como fallback.
- Cortar por no-progress, cambio de pantalla, scroll humano, ambigüedad o timeout.
- Después de scroll humano, suprimir movimiento automático al menos 1.4 s.

### Matcher

- Sólo ventanas `TYPE_APPLICATION` de Settings resuelto para ese equipo.
- Alias localizados explícitos; nada de fuzzy amplio.
- Contexto de título/pantalla obligatorio para targets sensibles.
- View id suma confianza, pero no es requisito universal.
- El mejor candidato debe superar threshold y separar al segundo por margen.

### Overlay

- Highlight full-screen no touchable/no focusable.
- Coach bar touchable pero no focusable y excluida del árbol relevante.
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

Sin broker/relay. Validar únicamente clasificación de pantalla, exclusión del overlay, scroll solicitado, highlight y recuperación en `Información de software`, `Número de compilación` y `Depuración inalámbrica`.

### Gate B — pairing UX

Broker/relay activos. Validar que no se consuma TTL durante el aprendizaje, seis casilleros, RemoteInput y lectura opcional del código.

### Gate C — sesión remota completa

Validar HMAC/AES, allowlist, CONNECTED, limpieza de overlay, `disableSelf`, cancel/revoke y cero acceso residual.

No volver a probar todos los subsistemas juntos en el primer ciclo.

## Trabajo completado por ChatGPT sin Codex

Se preparó fuera del repo un prototipo aislado con:

- catálogo OEM Samsung/Motorola/Xiaomi/Generic;
- matcher determinista;
- exclusión explícita de overlay y apps no Settings;
- stability gate;
- política de scroll one-shot con cooldown humano;
- detector contextual de PIN;
- state machine fail-closed;
- telemetría segura;
- prototipo HTML interactivo;
- 19/19 tests Python PASS.

Artifact local: `glosh_remote_live_guide_v2_prototype.zip`.
SHA-256: `ff20a59bc0d25afd8e5595284dd17da7e359f0dcac8123e8a4341077830347f1`.

## Próximo trabajo que sí requiere Codex/Mac

1. Restaurar y leer el HEAD local real `93735b1c...` o sucesor.
2. Comparar este audit con la implementación exacta.
3. Portar V2 sin tocar broker/crypto/relay.
4. Compilar APK.
5. Ejecutar Gates A/B/C por separado en el S22.
6. Entregar diff y evidencia para revisión ChatGPT.

## Coordinación

- `REMOTE-INSTALL-CONNECTION-00`: PASS FINAL DEV / CLOSED.
- `REMOTE-INSTALL-LIVE-GUIDE-03`: FAILED UX físico / SUPERSEDED por V2.
- `REMOTE-INSTALL-LIVE-GUIDE-V2-04`: AUDIT + PROTOTYPE COMPLETE / integración local pendiente.
- `REMOTE-ADAPTIVE-INSTALL-PILOT-01`: sigue esperando gate físico V2.
- No tocar Chrome, GloshIA, DAG, App Usuario/Admin, Supabase ni Device Owner productivo.
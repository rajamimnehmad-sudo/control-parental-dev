# AI NEXT TICKET

## CHROME-VISUAL-S22-PHYSICAL-GATE-05

**Tipo:** validación física / Chrome Visual
**Prioridad:** crítica
**Responsable:** Codex
**Revisor:** ChatGPT / jefe técnico central
**Esfuerzo Codex:** Medio
**Perfil Codex:** lean

## Autorización física explícita

El usuario confirmó que el **Samsung S22 Ultra está encendido y con Depuración inalámbrica activada**. Se autoriza para este ticket:

- usar ADB inalámbrico con ese S22;
- comprobar/conectar el dispositivo por los mecanismos ADB ya disponibles en la Mac;
- transferir el APK de App Usuario al S22 por Taildrop/Tailscale;
- instalar/actualizar el APK DEV necesario para esta prueba mediante ADB si el dispositivo ya está autorizado;
- abrir Chrome/App Usuario, capturar logs/métricas técnicas y ejecutar el gate físico descrito abajo.

No se autoriza Production, deploy, merge, Supabase writes, borrados destructivos, gastos adicionales ni cambios fuera de Chrome Visual. Si ADB requiere un nuevo código de emparejamiento o una acción física imposible de automatizar, detenerse y dejar exactamente qué debe hacer el usuario.

## Fuente a validar

Validar **PR #97 `review/chrome-visual-closure-batch-04`**, cuyo último estado aprobado automáticamente fue `88ca10f605ea297c0e303bc35e04ab45937ec636`.

APK esperado si sigue disponible:
`app-user/build/outputs/apk/dev/debug/app-user-dev-debug.apk`
SHA-256 esperado del build ya preparado:
`e412dea28859f52743151bffd8a66256bdb23e7dece4eee73437169b9ca1c536`

No recompilar por costumbre. Si el APK falta o no corresponde al HEAD a validar, compilar únicamente el target mínimo necesario `:app-user:assembleDevDebug` desde una copia/worktree limpio de la rama de PR #97. No tocar el worktree histórico sucio `work/chrome-visual`.

## Objetivo

Cerrar el gate físico pendiente de Chrome Visual sobre **Chrome normal** usando GloshIA Visual R3.1 compartida.

### A — Conexión y preflight

1. Confirmar S22 por ADB y registrar modelo/API/ABI.
2. Confirmar que no se toca otro dispositivo.
3. Confirmar Accessibility/permiso requerido para Chrome Visual y estado DEV.
4. Verificar versión/HEAD/APK antes de instalar.
5. Transferir el APK por Taildrop al S22; la autorización del usuario para este envío está vigente en este ticket.
6. Instalar/actualizar por ADB si es posible sin intervención física adicional.

### B — Fotos reales en Chrome

Validar y documentar:
- página con fotos estáticas reales;
- scroll largo;
- lazy-load;
- Google Images;
- cambio dinámico de contenido/SPA si aparece naturalmente;
- que las coberturas acompañen geometría/scroll y no dejen overlays fantasma;
- que contenido permitido recupere visibilidad;
- que contenido bloqueado permanezca cubierto;
- que no haya crash/ANR.

Esta parte es crítica: la lógica de imágenes tiene PASS automático, pero este ticket debe demostrarla físicamente en el S22.

### C — Video real en Chrome

Validar al menos:
- inicio de video;
- reproducción continua;
- seek;
- pausa/reanudación;
- fullscreen entrar/salir;
- segundo video en la misma ventana/sesión;
- transición Block → Allow/recovery cuando el probe disponible lo permita;
- ausencia de event-storm que reinicie baseline repetidamente;
- ausencia de crash/ANR.

### D — Geometría e interacción

Validar:
- rotación portrait/landscape;
- teclado abierto/cerrado e insets;
- navegación atrás/adelante o nueva página suficiente para invalidar geometría;
- overlays no deben bloquear toque de Chrome más allá de la cobertura visual prevista.

### E — Rendimiento mínimo

Medir con herramientas disponibles sin instalar software pesado:
- latencia observable de captura/inferencia/cobertura cuando los logs la expongan;
- CPU y RAM aproximadas durante uso estable;
- cualquier backlog/overload/fallback;
- confirmar que no haya ANR/crash.

No inventar métricas si no están expuestas.

## Regla de corrección

Si aparece un defecto **pequeño, claramente localizado y dentro de Chrome Visual**, corregirlo en la misma rama de PR #97 con commit separado, ejecutar solo tests afectados + build necesario y repetir únicamente el caso físico afectado.

Si el fallo exige cambio arquitectónico, afecta DAG/shared GloshIA de forma amplia o el origen no es claro: **no hacer reescritura amplia**. Preservar evidencia y detenerse BLOCKED para revisión de ChatGPT.

## Gates de cierre

PASS solo si:
- fotos estáticas + scroll/lazy-load + Google Images quedan validadas físicamente;
- video + seek + fullscreen + segundo video quedan validados físicamente;
- rotación/teclado/insets no rompen cobertura;
- no hay crash/ANR;
- cualquier corrección local necesaria tiene tests pertinentes PASS;
- se deja evidencia concreta y concisa.

## Handoff

Actualizar `docs/AI_CODEX_HANDOFF.md` con:
- `CHROME-VISUAL-S22-PHYSICAL-GATE-05`;
- PASS / NEEDS-FIX / BLOCKED;
- dispositivo/API/ABI y método ADB usado;
- HEAD y SHA del APK realmente probado;
- resultado separado para fotos, scroll/lazy-load, Google Images, video, seek, fullscreen, segundo video, rotación, teclado/insets;
- CPU/RAM/latencia si fueron medibles;
- crash/ANR/fallbacks;
- cambios realizados y tests, si hubo corrección;
- rama/commit/PR final;
- cualquier acción manual que todavía necesite el usuario.

No mergear PR #97. Después: **DETENERSE**.

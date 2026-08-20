# AI NEXT TICKET

## AI-AUTORUN-AUTH-GUARD-06

**Tipo:** infraestructura local / corrección del runner
**Prioridad:** crítica
**Responsable:** Codex
**Revisor:** ChatGPT / jefe técnico central
**Esfuerzo Codex:** Bajo
**Perfil Codex:** lean

## Objetivo

Corregir un conflicto real del runner antes de volver a lanzar el gate físico del S22.

El runner actual construye un prompt fijo que dice que el trigger no autoriza `pruebas físicas ni APK`, aun cuando el ticket vigente contiene una **autorización explícita del usuario** para esas acciones. Eso contradice el modelo de permisos acordado y puede bloquear tickets válidamente autorizados.

## Cambio requerido

En `tools/ai-autorun/glosh-ai-autorun`:

1. Eliminar la prohibición absoluta embebida en el prompt para acciones que pueden estar explícitamente autorizadas por el ticket.
2. La regla debe quedar así: el runner por sí solo NO concede Production, deploy, merge, borrado destructivo, gastos, prueba física, APK/ADB u otras acciones sensibles; **solo se pueden ejecutar cuando el ticket vigente contiene autorización explícita del usuario para esa acción concreta**.
3. El ticket vigente es la fuente de permisos. Una autorización explícita del ticket debe poder habilitar únicamente lo enumerado, sin ampliar permisos por inferencia.
4. Mantener `Production`, `deploy`, `merge`, borrados destructivos y gastos prohibidos salvo autorización explícita específica.
5. Agregar self-test/regresión que pruebe al menos:
   - ticket sin autorización física => sigue prohibido;
   - ticket con autorización explícita ADB/prueba física/APK => el prompt no la contradice;
   - no se amplían otras acciones sensibles.
6. Reinstalar atómicamente la versión corregida del runner local y confirmar servicio `launchd` activo.
7. No tocar producto, Chrome, APKs, ADB, dispositivos ni PR #97 en este ticket.

## Alcance de costo

Es una corrección pequeña. Usar contexto lean y tests estrechos. No abrir documentación/backlog innecesario.

## Handoff

Actualizar `docs/AI_CODEX_HANDOFF.md` con:
- `AI-AUTORUN-AUTH-GUARD-06`;
- PASS / BLOCKED / FAILED;
- causa confirmada;
- archivo/commit del fix;
- tests ejecutados;
- confirmación de fuente e instalación actualizadas;
- estado del servicio;
- cero cambios de producto.

Después: **DETENERSE**. No lanzar todavía el gate físico de Chrome.

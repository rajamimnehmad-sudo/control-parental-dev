# AGENTS.md

Antes de cualquier tarea en este repositorio:

1. Leer START_HERE.md.
2. Usar docs/AREAS.md para identificar el area exacta afectada.
3. Abrir solo los archivos necesarios de esa area.
4. No revisar todo el repo salvo pedido explicito.
5. No tocar areas no relacionadas.
6. Diagnosticar causa raiz antes de escribir codigo.
7. Modificar la menor cantidad posible de archivos.
8. Si solo cambian docs, no compilar, no incrementar versionCode y no publicar APK.

Para planificacion, captura de ideas o seleccion de tickets:

- Leer `docs/BACKLOG_PRODUCTO.md`.
- Tratar `docs/HANDOFF_ACTUAL.md` como verdad tecnica y el backlog como verdad de producto.
- No escribir codigo hasta que el usuario apruebe explicitamente el ticket.

## Esfuerzo de razonamiento

- Usar esfuerzo bajo por defecto en todas las tareas del proyecto.
- El nivel solo puede cambiarlo manualmente el usuario antes de enviar un mensaje; Codex no puede modificarlo durante una respuesta.
- Si una tarea requiere esfuerzo medio, alto o superior por riesgo, seguridad o complejidad, avisarlo explicitamente antes de comenzar y esperar un nuevo mensaje del usuario con ese nivel seleccionado.
- Indicar el nivel recomendado y el motivo concreto; no pedir un aumento para consultas, lectura o planificacion que puedan resolverse correctamente con esfuerzo bajo.

## Ticket Android autorizado por el usuario (2026-07-14)

- Area: `feature-accessibility`, barrera antimanipulacion tipo Rimon.
- Implementar en tickets pequenos, empezando por navegacion segura desde Ajustes protegidos.
- Se permite modificar Android, ejecutar tests/builds, incrementar el `versionCode` de cada app afectada, hacer commit/push y publicar esas APKs solo en DEV. Usuario y Admin versionan de forma independiente; coordinar ambos solo cuando el cambio entra en las dos apps.
- Para alertas remotas se permite usar exclusivamente Supabase DEV `syeycayasyufedwoprea`.
- No tocar Production, no borrar datos y no incluir Service Role Key en Android.

## Flujo local vigente

- Trabajar, validar, versionar, hacer commits y fusionar los lotes terminados en `main` local.
- No hacer push, abrir PR, ejecutar una publicacion remota ni actualizar GitHub sin un `OK` explicito del usuario.
- Proponer un respaldo remoto al cerrar un hito estable de aproximadamente 5 a 10 tickets relacionados, o antes si existe riesgo concreto de perdida. El usuario decide si se sube.
- Antes de modificar codigo, hacer solo un control Git liviano (`status`, rama, worktrees y commits recientes). Buscar ramas o commits sueltos en profundidad unicamente si aparece una inconsistencia.
- Los APK locales deben generarse desde el `main` local ya integrado. No instalar como entrega final un APK construido desde un worktree o rama temporal.

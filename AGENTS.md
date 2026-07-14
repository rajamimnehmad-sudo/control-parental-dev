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

## Ticket Android autorizado por el usuario (2026-07-14)

- Area: `feature-accessibility`, barrera antimanipulacion tipo Rimon.
- Implementar en tickets pequenos, empezando por navegacion segura desde Ajustes protegidos.
- Se permite modificar Android, ejecutar tests/builds, incrementar ambos `versionCode`, hacer commit/push y publicar APKs solo en DEV.
- Para alertas remotas se permite usar exclusivamente Supabase DEV `syeycayasyufedwoprea`.
- No tocar Production, no borrar datos y no incluir Service Role Key en Android.

# SUPER ADMIN WEB — HANDOFF

## Mision

Herramienta interna para operaciones, soporte, anuncios, licencias y revision
GloshIA.

## Estado

- Next.js 16.2.10 y Node 22 o superior.
- Dependencias fijadas instaladas durante auditoria; typecheck y lint correctos.
- Un warning por `<img>` en revision GloshIA.
- No hay cobertura de tests suficiente ni CI web.
- La documentacion mezcla Vercel, Cloudflare/OpenNext y configuracion Sites; hay
  que elegir un unico destino oficial antes del proximo despliegue.

## Siguientes tickets

1. `WEB-HOSTING-01`: decidir y documentar plataforma oficial sin publicar.
2. Agregar smoke tests de autenticacion y operaciones criticas.
3. Resolver warning de imagen si no perjudica el flujo de dataset privado.

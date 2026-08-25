# CODEX MAP

Mapa corto de orientacion. Usarlo solo cuando no se conoce el modulo/ruta inicial.

**No es fuente de estado, prioridad ni arquitectura vigente.**

- Glosh Central / Control Center: coordinacion actual.
- GitHub + evidencia del frente: codigo/SHAs/resultado tecnico actual.
- `docs/AREAS.md`: rutas exactas por area.
- `docs/HANDOFF_ACTUAL.md`: contexto adicional solo cuando el ticket/Central/GitHub no alcanzan.

No releer este mapa si el ticket ya identifica el area y las rutas.

## Apps

- `app-user`: App Usuario, shell/navegacion y cableado de features; codigo DEV especializado de Chrome vive bajo `app-user/src/dev/...`.
- `app-admin`: App Administrador.
- `app-dag-browser`: navegador DAG aislado cuando ese frente esta activo.
- `app-control-center`: Glosh Central/Control Center si existe en el snapshot consultado.

## Core

- `core-domain`: contratos/modelos/use cases compartidos.
- `core-policy`: politica de negocio compartida.
- `core-data`: implementaciones de repositorios/mappers.
- `core-database`: Room/DAOs/entities/migrations.
- `core-network`: clientes/DTOs/red/Supabase.
- `core-sync`: outbox/realtime/workers/aplicacion remota.
- `core-security`: seguridad/sesion/tokens segun arquitectura vigente.
- `core-update`: actualizaciones de apps.
- `core-ui`: componentes UI realmente compartidos.
- `core-telemetry`: telemetria cuando corresponda.

## Features Android

- `feature-vpn`: VPN/DNS/transporte/proteccion de red. La arquitectura Chrome actual puede incluir HEV/SOCKS/full-tunnel/proxy/data-plane; consultar el frente/evidencia vigente, no asumir DNS-only.
- `feature-accessibility`: Accessibility, observacion/bloqueo complementario y uso de apps.
- `feature-activation`: activacion/enlace.
- `feature-requests`: solicitudes.
- `feature-status`: estado del sistema.
- `feature-usage`: uso.
- `feature-block`: UI/flujo de bloqueo.

## GloshIA / navegador

- `gloshia-visual-core`: preprocessing/politica visual/runtime compartido del motor visual; no cambiar modelo/thresholds por accidente al trabajar en integraciones.
- Chrome Fotos + GloshIA: empezar por `app-user/src/dev/java/com/contentfilter/user/chromedataplane/` y luego abrir solo dependencias necesarias indicadas por el ticket/evidencia.
- DAG: empezar por `app-dag-browser/`, `docs/dag/` y herramientas especificas del frente.

## Device Owner / proteccion del dispositivo

Device Owner/DevicePolicyManager y Accessibility son responsabilidades complementarias segun el caso. No asumir que todo el control de apps pertenece a Accessibility. Consultar el area/ticket vigente antes de tocar provisioning, DPM o proteccion.

## Backend / Super Admin

Supabase participa segun los contratos actuales en backend/sync/auth/storage/operaciones. Para trabajo Supabase usar las rutas/migraciones/funciones reales del snapshot y el skill/conector correspondiente; no inferir el estado desde este mapa.

Super Admin/web tiene su propio frente/rutas; usar `docs/AREAS.md`, Central y el repo actual para localizarlo.

## Regla de uso

1. Si el ticket ya da rutas: no usar este mapa.
2. Si solo da area: abrir `docs/AREAS.md` y las rutas de esa area.
3. Si no se sabe el area: usar este mapa para elegir modulo y luego `AREAS`.
4. No recorrer todo el repo para confirmar algo que una ruta/base SHA concreta ya resuelve.
5. No tratar listas historicas de este repo como autoridad por encima de Central/GitHub actuales.

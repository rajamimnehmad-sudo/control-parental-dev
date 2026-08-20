# AI QUEUE — Glosh

## Próximo lote reservado

### APPS-ACCOUNT-UX-BATCH-01

**Estado:** QUEUED — no promover mientras `AI-AUTORUN-AUTH-GUARD-06` siga en curso.

**Esfuerzo Codex:** medium
**Perfil Codex:** lean

**Objetivo:** cerrar en un solo lote coherente cuatro pendientes de Apps/cuentas, evitando cuatro arranques separados de Codex:

1. definir e implementar el flujo visible cuando una licencia vence, incluida recuperación cuando vuelve a estar activa;
2. mostrar cupos disponibles de usuarios/administradores según la licencia vigente donde corresponda;
3. aclarar en App Admin para qué sirve la contraseña y en qué acciones se usa, sin cambiar seguridad por simple UX;
4. impedir dobles taps/acciones simultáneas que puedan dejar estados inconsistentes en controles sensibles.

**Preparación obligatoria:** antes de modificar, Codex debe inspeccionar únicamente las áreas de App Usuario/App Admin/backend-contract directamente implicadas, reutilizar el sistema de licencias ya existente y no reinventar reglas que ya estén en Super Admin/backend.

**Gates:** tests afectados y builds mínimos de las apps tocadas; no repetir suites no relacionadas; handoff con comportamiento antes/después, archivos, tests y riesgos.

**Límites:** no S22, no ADB, no APK físico, no Chrome/DAG, no Production/deploy/merge, no cambios destructivos de datos. Si el comportamiento de licencia requiere una decisión de producto no deducible de lo ya existente, detener ese subpunto y continuar solo con los otros que sean seguros.

**Resultado esperado:** mejorar UX y coherencia de cuentas/licencias con un único costo de arranque Codex.

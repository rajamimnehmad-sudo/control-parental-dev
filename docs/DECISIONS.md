# DECISIONS

Este archivo conserva solo decisiones transversales estables. El estado/orden actual de trabajo vive en Glosh Central y las decisiones tecnicas especificas se prueban en GitHub/evidencia del frente.

- Usar APIs oficiales Android y mantener separacion modular/cohesiva.
- Politica de apps/sistema: usar la API oficial mas fuerte apropiada al caso (Device Owner/DevicePolicyManager cuando aplica) y Accessibility como complemento donde aporta observacion/control necesario.
- Politica web/red: usar la arquitectura vigente VPN/DNS/transporte/proxy/data-plane; no asumir que DNS por si solo cubre navegacion general.
- Room es almacenamiento local donde corresponde; Supabase cumple los roles backend/sync/auth/storage definidos por los contratos actuales.
- No reabrir una arquitectura cerrada sin evidencia nueva, regresion o requisito material.
- Prioridad/orden de frentes (Android, Chrome, Super Admin, iPhone, etc.) NO es una decision permanente y se consulta siempre en Glosh Central.

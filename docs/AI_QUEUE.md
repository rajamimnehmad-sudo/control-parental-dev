# AI QUEUE — Glosh

## Próximo lote reservado

### APPS-ACCOUNT-PRODUCT-BATCH-07

**Estado:** QUEUED — no publicar en `AI_NEXT_TICKET.md` mientras `AI-AUTORUN-AUTH-GUARD-06` siga en curso.

**Esfuerzo Codex:** Medio
**Perfil Codex:** lean

## Objetivo

Cerrar en un solo lote coherente cuatro pendientes relacionados de App Admin / App Usuario, evitando cuatro arranques separados de Codex:

1. **Vencimiento de licencia:** definir e implementar qué ve y qué puede hacer cada app cuando la licencia está vencida, cómo se informa el estado y cómo se recupera al renovarse, sin dejar el dispositivo en un estado inseguro o ambiguo.
2. **Cupos disponibles:** mostrar de forma clara los cupos restantes relevantes según la licencia/plan, reutilizando la fuente de verdad existente y sin duplicar lógica del Super Admin.
3. **Contraseña Admin:** revisar dónde aparece/se solicita y dejar claro para el usuario para qué sirve, evitando textos ambiguos o flujos que parezcan una segunda contraseña de cuenta si no lo es.
4. **Doble acción simultánea:** impedir taps/acciones concurrentes sobre controles que puedan producir estados inconsistentes, con estado pending/busy por operación y tests de regresión donde corresponda.

## Reglas de alcance

- Antes de editar, inventariar únicamente los módulos/archivos que realmente implementan estos cuatro flujos.
- Reutilizar contratos/licencia ya existentes; no crear un segundo modelo de licencia.
- No rediseñar pantallas completas si no es necesario; cambios UX enfocados y consistentes con la UI actual.
- Mantener separación por dispositivo/usuario y no romper sincronización existente.
- No tocar Chrome Visual, DAG, Super Admin web salvo que sea imprescindible para leer un contrato compartido; si aparece una dependencia amplia, detenerse y reportarla.
- No Production, deploy, Supabase writes, merge, APK físico, ADB ni S22.
- Usar tests estrechos por módulos afectados; build solo de targets que entren realmente en el grafo del cambio.
- Commits internos separados por responsabilidad aunque el ticket sea un lote grande.

## Criterio de cierre

- comportamiento de licencia vencida/renovada definido y cubierto;
- cupos restantes visibles donde aportan valor y basados en la fuente de verdad existente;
- propósito de contraseña Admin comprensible y coherente en los puntos donde aparece;
- acciones simultáneas que puedan competir quedan serializadas/bloqueadas correctamente;
- tests afectados PASS y sin regresiones conocidas;
- handoff corto con archivos, comandos, resultado, riesgos y PR/commit.

## Orden

Promover a `docs/AI_NEXT_TICKET.md` únicamente después de que ChatGPT audite y cierre `AI-AUTORUN-AUTH-GUARD-06`.

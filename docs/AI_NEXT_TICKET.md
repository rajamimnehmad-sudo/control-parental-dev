# AI NEXT TICKET

## SUPERADMIN-PRODUCT-BATCH-01

**Tipo:** UX/UI + datos + licencias
**Prioridad:** importante
**Responsable:** Codex
**Revisor:** ChatGPT / jefe técnico central

> Leer primero `docs/AI_WORKFLOW.md` en `coordination/ai-control`.

## Contexto

El backend P0 quedó implementado estáticamente en PR #95 y está pendiente únicamente de un gate SQL dinámico por falta de sandbox local. **No repetir ese trabajo ni intentar instalar Docker.** Ese bloqueo queda registrado y no impide avanzar con producto.

El entorno de datos Glosh fue reseteado por ChatGPT el 20/08/2026:

- 0 comunidades;
- 0 cuentas;
- 0 administradores comunitarios;
- 0 dispositivos;
- 0 códigos/requests/policies antiguos;
- se preservó el Super Admin.

Este lote debe mejorar el Super Admin de forma visible y profesional, aprovechando que ahora parte de un estado limpio.

## Rama / base

Trabajar desde la rama remota existente:

`codex/superweb-professional-redesign`

Crear una rama nueva:

`review/superadmin-product-batch-01`

No trabajar sobre el worktree original sucio del proyecto Android. Usar worktree/clon limpio.

No tocar Production, no desplegar Vercel y no modificar datos reales de Supabase durante este ticket.

---

# Bloque A — Limpieza y consistencia de datos visibles

Objetivo: que el Super Admin nunca muestre `null`, `undefined`, guiones ambiguos o campos sin explicación cuando puede presentar un estado humano claro.

Revisar dashboard, lista de comunidades y detalle de comunidad.

Como mínimo:

- normalizar campos opcionales con textos claros (`Sin email`, `Sin dispositivo`, `Sin fecha`, etc.);
- evitar strings `null`/`undefined` renderizados;
- verificar que todos los datos relevantes que ya existen en RPCs/tipos lleguen correctamente a la UI;
- no ocultar silenciosamente errores de backend como si fueran datos vacíos;
- mantener tipado estricto y sin casts innecesarios;
- conservar formato argentino para fechas cuando corresponda.

No crear columnas/backend nuevas salvo que exista un dato imprescindible que ya esté disponible pero no expuesto; si eso requiere ampliar RPC/schema, dejar observación y no ampliar el ticket a ciegas.

Commit separado sugerido:

`fix(super-admin): normalize operational data display`

---

# Bloque B — Sistema de licencias y cupos

Objetivo: revisar de punta a punta la experiencia de licencias desde el Super Admin, sin cambiar todavía la semántica Android al vencer una licencia.

Validar y mejorar:

1. creación de comunidad + licencia inicial;
2. edición posterior de plan, estado, inicio y vencimiento;
3. cupos máximos de administradores, usuarios y dispositivos admin;
4. mostrar **usados / máximos / disponibles** de forma clara;
5. estados `active`, `suspended`, `expired` y cualquier estado derivado como `scheduled` deben tener presentación coherente;
6. fecha sin vencimiento debe ser explícita (`Sin vencimiento`) y no parecer dato faltante;
7. impedir UX contradictoria, por ejemplo estado Activa con fechas evidentemente inválidas;
8. mensajes de éxito/error concretos y útiles;
9. mantener notas internas como dato secundario, no protagonista;
10. revisar que crear/editar licencia use los RPC actuales de Super Admin y no haga escrituras directas inseguras.

No tocar todavía el comportamiento de App Usuario/Admin al vencer la licencia; eso queda en el ticket funcional `license-expiry-flow`.

Commit separado sugerido:

`feat(super-admin): clarify license lifecycle and capacity`

---

# Bloque C — UX/UI profesional del Super Admin

Objetivo: reducir densidad y hacer la operación diaria más rápida, manteniendo el diseño visual actual como base y sin rehacer desde cero.

## Dashboard

- jerarquía visual clara: estado general → alertas/atención → actividad;
- evitar métricas redundantes;
- estados vacíos correctos ahora que no hay comunidades;
- accesos rápidos útiles y no decorativos;
- responsive real móvil/tablet/desktop.

## Lista de comunidades

- lectura rápida de nombre, licencia, cupos/uso y estado;
- filtro/búsqueda si ya existe infraestructura simple; no construir un buscador complejo si no hace falta;
- empty state claro con CTA para crear primera comunidad.

## Detalle de comunidad

La pantalla actual es demasiado vertical y mezcla administradores, usuarios, actualizaciones, licencia y acciones.

Reorganizar en una navegación/segmentación clara, preferentemente:

- **Resumen**
- **Usuarios**
- **Administradores**
- **Licencia**
- **Dispositivos / actualizaciones**

Puede resolverse con tabs/segmentos o una estructura equivalente que funcione bien en móvil y desktop.

Requisitos:

- acciones frecuentes visibles;
- acciones destructivas separadas y claramente señaladas;
- `Licencia y límites` no debe quedar escondido como una acción secundaria genérica;
- reenlace, estado del dispositivo y actualización deben estar cerca del dispositivo correspondiente;
- no saturar cada tarjeta con información secundaria;
- conservar accesibilidad, foco, labels y áreas táctiles razonables;
- usar el sistema visual existente y componentes reutilizables antes de crear estilos aislados.

Commit separado sugerido:

`feat(super-admin): streamline community management ux`

---

# Validación

Ejecutar las verificaciones más estrechas relevantes del `web-super-admin`:

- lint;
- typecheck si existe script separado;
- tests si existen;
- build de producción local;
- `git diff --check`.

Si el proyecto tiene un servidor local fácil de levantar, hacer una verificación visual/browser de:

- login/entrada (sin alterar credenciales);
- dashboard vacío;
- lista de comunidades vacía;
- layout responsive;
- formularios de creación/licencia mediante mocks/entorno local si es posible sin tocar datos reales.

No usar Supabase Production para generar datos de prueba. No desplegar.

## Observaciones obligatorias

Codex debe anotar también cualquier problema relevante que detecte en:

- creación de comunidades/licencias;
- RPCs y tipos;
- autenticación Super Admin;
- responsiveness/accesibilidad;
- deuda visual o técnica.

No corregir fuera del alcance sin necesidad; registrar para que ChatGPT decida.

## Resultado esperado

1. rama `review/superadmin-product-batch-01`;
2. 3 commits separados A/B/C cuando sea razonable;
3. push;
4. PR contra `codex/superweb-professional-redesign`;
5. NO mergear;
6. NO desplegar;
7. NO tocar Production.

## Handoff obligatorio

Reemplazar `docs/AI_CODEX_HANDOFF.md` en `coordination/ai-control` con:

- `SUPERADMIN-PRODUCT-BATCH-01`;
- PASS / NEEDS-FIX / BLOCKED;
- rama + commits;
- PR;
- archivos tocados;
- validaciones ejecutadas y resultados;
- resumen de cambios A/B/C;
- observaciones técnicas adicionales;
- riesgos pendientes;
- confirmación de que Supabase Production y Vercel Production quedaron intactos.

Después: **DETENERSE**.

# UI-UX-REDESIGN-01

Estado: IN PROGRESS
Owner de escritura: ChatGPT / Producto y Diseño
Rama reversible: `work/ui-ux-redesign-01`
Base: `preserve/uncommitted-2026-08-20` (`214e7c848c7c1770a11abb8a0af3b8b71698999e`)

## Objetivo

Modernizar App Admin y App Usuario sin cambiar la navegación ni la lógica de protección.
La dirección aprobada usa tema claro como base, fondo bone/blanco, graphite para texto y estructura y lime solo como acento. El wordmark vigente es `glosh` en texto; el isotipo queda pausado.

## Primer lote aplicado

- Nuevo sistema de tokens visuales compartidos en `core-ui`.
- Nueva tipografía sans del sistema con jerarquía más cercana al lenguaje visual Apple/SF-Pro-like, sin incorporar fuentes propietarias.
- Paleta Material clara actualizada a bone / graphite / lime.
- Nuevo Home real de App Admin, separado del Home anterior para permitir rollback inmediato.
- El Home conserva las acciones existentes: protección, alta de usuario, solicitudes y avisos.
- La pestaña visible `Home` pasa a `Inicio`; no cambia el destino ni el flujo.
- Barras de sistema de Home pasan a tratamiento claro.

## Reversibilidad

El Home anterior permanece intacto en `AdminHomeComponents.kt`. Para volver atrás basta con restaurar la llamada de `AdminAppRoot` a `HomeTab` y retirar los archivos nuevos del rediseño. No se modificó `main`, no se abrió PR y no se publicó APK.

## Integración con cambios nuevos

Esta rama parte del snapshot preservado más completo del 20/08. Los frentes aprobados o candidatos que vivan en ramas divergentes no se mezclan automáticamente: antes de construir una APK se portará este lote sobre la base integrada más nueva que haya pasado sus gates.

En particular, `CHROME-PHOTOS-PROTECTED-SURFACE-00` queda fuera mientras su gate físico siga FAILED/BLOCKED. No se debe convertir una rama experimental en parte de la APK solo por ser más nueva.

## Pendiente

1. Validación de compilación/lint del primer lote en entorno Android.
2. Revisar Home Admin en dispositivo/emulador y ajustar densidad si hace falta.
3. Propagar el sistema visual a Usuarios, Solicitudes y Ajustes de Admin.
4. Aplicar el mismo sistema a Inicio, Mis apps, Internet y Ajustes de Usuario.
5. Antes de generar APK, integrar únicamente cambios funcionales nuevos que estén aprobados y hayan pasado sus gates.
6. `versionCode`, APK, publicación, PR y merge permanecen bloqueados hasta autorización explícita.

## Áreas afectadas

- `core-ui`
- `app-admin` UI solamente
- documentación de Producto/Diseño

No afectados: VPN, Accessibility, Chrome Visual, DAG, Supabase, Room, Sync, lógica de políticas y Production.

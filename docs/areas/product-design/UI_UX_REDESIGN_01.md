# UI-UX-REDESIGN-01

Estado: CODE COMPLETE / BUILD GATE PENDING
Owner de escritura: ChatGPT / Producto y Diseño
Rama reversible: `work/ui-ux-redesign-01`
Base: `preserve/uncommitted-2026-08-20` (`214e7c848c7c1770a11abb8a0af3b8b71698999e`)

## Objetivo

Modernizar App Admin y App Usuario sin cambiar la navegación principal ni la lógica de protección.
La dirección aprobada usa tema claro como base, fondo bone/blanco, graphite para texto y estructura y lime solo como acento. El wordmark vigente es `glosh` en texto; el isotipo queda pausado.

## Sistema visual compartido

- Nuevo sistema de tokens Glosh en `core-ui`: bone, graphite, lime, superficies, estados, spacing y shapes.
- Tipografía sans del sistema con jerarquía Apple/SF-Pro-like, sin incorporar fuentes propietarias.
- Material Theme claro unificado.
- Componentes compartidos para wordmark, tarjetas, listas, pills, iconos, navegación y estados.
- ProductScaffold/ProductLists/ProductVisuals fueron alineados al mismo lenguaje para que pantallas secundarias hereden el sistema sin duplicarlo.
- Sombras reducidas, bordes suaves, menos radios exagerados y desaparición de fondos decorativos/gradientes como lenguaje principal.

## App Administrador

### Navegación

Se conserva `Inicio · Usuarios · Solicitudes · Ajustes`.
No se agregan Dispositivos, Reglas, Actividad ni Menú como pestañas nuevas.

### Inicio

- Nuevo Home claro real, separado del Home anterior para rollback inmediato.
- Wordmark `glosh`, saludo/comunidad, licencia, avisos, estado global de protección, alta de usuario, usuarios activos y solicitudes pendientes.
- Las acciones existentes y sus ViewModels se conservan.

### Usuarios

- Lista principal reconstruida en bone/graphite/lime.
- Búsqueda, actualización, alta, token, copiar/WhatsApp y archivados conservados.
- Estados traducidos a lenguaje humano: protegido, requiere atención, sin conexión, verificando.
- Detalle simplificado a `Apps · Internet · Seguridad`; se retiró la cabecera glass/collapsing anterior.
- Apps: lista primero, filtros, límites y horarios por app; grupos como subsección secundaria.
- Grupos: ya no se muestran package names `com...`; se muestran nombres de apps.
- Horarios: mismo motor, editor visual coherente con Glosh.

### Internet

- `Abierto / Bloqueado` como decisión principal.
- `Solo resultados de búsqueda` y `Navegador protegido` con explicación humana.
- No se exponen DAG, DNS ni detalles de implementación en la UI normal.
- Sitios permitidos y horarios conservan la lógica existente.

### Seguridad

- La vista normal muestra `Protección completa / por revisar`.
- VPN/Accessibility/Device Admin se traducen a `Internet protegido`, `Bloqueo de apps` y `Protección contra desinstalación`.
- Reenlace, autorización temporal, recuperación offline y archivo quedan bajo `Más opciones`.
- No se modificó la lógica de protección.

### Solicitudes

- Mejora UX funcional: las solicitudes pendientes se ven globalmente al entrar.
- Elegir usuario pasa a ser un filtro, no un paso obligatorio.
- Historial queda como vista secundaria.
- Se conservan exactamente las acciones permitir / dar tiempo / rechazar.

### Ajustes / acceso / actualizaciones

- Cuenta/comunidad, contacto, actualizaciones, ayuda, opinión, diagnóstico y administrador local reorganizados y humanizados.
- Activación Admin conserva token + email + contraseña; no se inventa login social.
- El propósito de la contraseña se explica en pantalla.
- Android launcher pasa de `Content Filter Admin` a `Glosh Admin`.

## App Usuario

### Navegación

Se conserva `Inicio · Mis apps · Internet · Ajustes`.
Solicitudes/Avisos mantienen Inicio seleccionado; Contacto/Ayuda/Actualizaciones mantienen Ajustes seleccionado.

### Activación y onboarding

- Activación conserva el token real y usa wordmark/tema Glosh.
- Los permisos de Android conservan el mismo mecanismo, pero se presentan como pasos humanos: protección contra desinstalación, protección de apps, protección de Internet y funcionamiento continuo.
- Se elimina de estos textos el nombre heredado Content Filter y jerga innecesaria.

### Inicio

- Home reconstruido en claro.
- Estado grande de protección; cuando todo está bien no muestra detalles técnicos.
- Si existe un problema, muestra únicamente la reparación necesaria con lenguaje humano.
- Límites cercanos, solicitudes, avisos y actualización permanecen accesibles.
- La autorización temporal de desinstalación conserva su comportamiento real.

### Mis apps

- Lista nativa se conserva para no degradar rendimiento.
- UI alineada a Glosh; filtros `Todas / Con tiempo / Bloqueadas / En grupos`.
- Estados, grupos y tiempos simplificados.

### Internet

- `Internet protegido / Protección por revisar / Internet bloqueado`.
- `Búsquedas seguras`, `Navegación`, `Navegador protegido` y `Horario` reemplazan nomenclatura técnica.
- Reparación directa si falta la protección de Internet.

### Solicitudes

- Se corrigió la inconsistencia de `DOMAIN_ACCESS`: ahora se muestra el dominio real y `Acceso a sitio`, no `Solicitud no disponible`.
- Historial y estados comparten el sistema visual Glosh.

### Ajustes / actualizaciones / ayuda

- Protección, actualizaciones, contacto, ayuda y opinión reorganizados.
- `Navegador DAG` pasa a `Navegador protegido` en textos de actualización.
- El asistente de ayuda pasa de `Asistente de Content Filter` a `Asistente de Glosh` y adopta el nuevo sistema visual.
- Android launcher pasa de `Content Filter` a `Glosh`; etiquetas de Device Admin/Accessibility también usan Glosh.

## Reversibilidad

- Todo vive en `work/ui-ux-redesign-01`.
- `main` no se modificó.
- No se abrió PR.
- No se publicó APK ni se tocó Production.
- El Home Admin anterior permanece intacto en `AdminHomeComponents.kt` y puede reactivarse inmediatamente.
- El resto del lote puede descartarse o portarse selectivamente desde esta rama.

## Integración con cambios nuevos

La rama parte del snapshot preservado más completo del 20/08 disponible al iniciar el trabajo.
Antes de una APK de prueba se debe portar este lote sobre la base integrada más nueva y sumar únicamente cambios funcionales aprobados que hayan pasado sus gates.

`CHROME-PHOTOS-PROTECTED-SURFACE-00` permanece fuera mientras su gate físico siga FAILED/BLOCKED. No se incorpora una rama experimental solo por ser más nueva.

## Gate técnico

- Revisión estática de contratos/UI realizada contra los `UiState`, callbacks y componentes vigentes.
- Se preparó CI no-publicador en la rama para build Usuario/Admin, unitarios, ktlint y lint.
- GitHub no generó automáticamente la corrida para los commits realizados por el conector y el conector disponible no expone `workflow_dispatch`.
- Por lo tanto **no existe PASS de compilación todavía**. No declarar las apps listas hasta ejecutar ese gate en un entorno Android/Codex o runner autorizado.

## Branding pendiente deliberado

El isotipo está pausado por decisión de diseño. Los launcher icons históricos permanecen temporalmente como placeholder; no se inventó una G/escudo nuevo sin aprobación. El wordmark y todos los nombres visibles de app ya usan Glosh.

## Próximo cierre

1. Ejecutar build + unitarios + ktlint + lint de Usuario/Admin sobre esta rama o sobre su port a la base integrada final.
2. Corregir cualquier fallo hasta PASS.
3. Validar visualmente las dos apps en emulador/dispositivo (Home, Usuarios, detalle Apps/Internet/Seguridad, Solicitudes, Ajustes, activación/onboarding).
4. Ajustar densidad/overflow si la prueba física lo requiere.
5. Integrar únicamente cambios funcionales nuevos con gates PASS.
6. Solo con autorización explícita: versionCode, APK de prueba/publicación, PR o merge.

## Áreas afectadas

- `core-ui`
- `app-admin` UI y flujo de filtrado de Solicitudes
- `app-user` UI/navegación de secciones
- `feature-activation` UI
- `feature-requests` UI
- manifests/strings de branding de Usuario y Admin
- documentación Producto/Diseño

No afectados por este frente: VPN runtime, Accessibility runtime, Chrome Visual, DAG runtime, Supabase, Room, Sync, lógica de políticas y Production.

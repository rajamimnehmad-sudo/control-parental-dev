# HANDOFF ACTUAL - Content Filter

Fecha de corte: 2026-07-12

Tomar este archivo como contexto oficial. No reanalizar arquitectura desde cero.

## Carpeta oficial

Usar solo:

```text
/Users/yejielnehmad/Developer/content-filter
```

No usar ni revisar la carpeta vieja en `Documents`.

Antes de trabajar:

```bash
pwd
git status --short
find . -maxdepth 2 \( -path './.gradle' -o -path './.gradle-home' -o -path './app-user/build' \) -print
```

Al cerrar trabajo, no dejar `.gradle`, `.gradle-home` ni `app-user/build`.

## Reglas

- No tocar produccion.
- Usar solo Supabase DEV.
- No usar Service Role Key en Android.
- No guardar secretos en Git.
- No borrar datos/tablas sin confirmacion explicita.
- Mantener Clean Architecture.
- El usuario prueba App Usuario y App Admin en el mismo celular Samsung.
- Si hay duda entre regla global y regla por dispositivo, priorizar regla por dispositivo.

## Estado publicado DEV

Version publicada real al 2026-07-12:

```text
App Usuario versionCode 187
App Admin versionCode 181
versionName 1.0.1-dev
```

Manifiestos:

```text
https://syeycayasyufedwoprea.supabase.co/storage/v1/object/public/dev-updates/app-user-dev-manifest.json
https://syeycayasyufedwoprea.supabase.co/storage/v1/object/public/dev-updates/app-admin-dev-manifest.json
```

APKs:

```text
https://syeycayasyufedwoprea.supabase.co/storage/v1/object/public/dev-updates/app-user-dev-187-debug.apk
https://syeycayasyufedwoprea.supabase.co/storage/v1/object/public/dev-updates/app-admin-dev-181-debug.apk
```

SHA-256:

```text
Usuario DEV 187: a0c3eee9f66cab667e2261f2abcb5e136486257240d511c8c0633950e937b620
Admin DEV 181:   327e0c65ea18412e0eef34f09bd2b7efa073ab46a44ab928b028867ec7f7c616
```

## Estado funcional

- App Usuario y App Admin usan activacion real con Supabase DEV.
- App Admin se activa por token de administrador.
- App Usuario se activa solo con token generado desde Admin.
- Comunidad DEV activa actual de pruebas: `Ajdut`.
- Codigos numericos legacy 1-100 reemplazados por tokens aleatorios.
- Usuarios es la entrada unica del Admin para usuarios protegidos, aplicaciones y grupos de apps.
- La seccion separada Reglas ya no esta en navegacion.
- Bloqueo de apps por Accessibility funciona y cierra apps bloqueadas rapido.
- VPN esta siempre activa en App Usuario cuando hay permiso; Admin cambia politica Web, no prende/apaga VPN.
- Web Admin mantiene flujo real: Comunidad -> Web -> elegir usuario -> configurar Web.
- Web Admin usa selector Internet abierto/bloqueado y dos capas independientes: SafeSearch y Solo resultados.
- Web Usuario muestra esos mismos estados en modo solo lectura.
- Bloqueo Web se representa con reglas internas de dominio en `WebNavigationPolicy`; no requiere migracion Room nueva.
- VPN/DNS bloquea dominios externos en Solo resultados y fuerza una invalidacion puntual de conexiones al activar la capa. Accessibility no usa Atras/Home para navegaciones nuevas.
- Solicitudes Admin se agrupan por usuario con indicador rojo.
- En cada solicitud se ve icono de app y acciones: acceso completo, dar tiempo, rechazar.
- Si Admin concede tiempo extra, Admin y Usuario muestran minutos restantes.
- Si Admin concede acceso completo, se elimina limite diario de esa app y queda permitida.
- Apps bloqueadas o limitadas aparecen antes que apps permitidas.
- App Admin permite crear grupos de apps dentro de un usuario elegido, con limite diario compartido.
- En Admin, el menu inferior dice Usuarios; al entrar lista usuarios, al abrir uno muestra pestañas Aplicaciones y Grupos de apps.
- Una app solo puede estar en un grupo del mismo usuario; si ya esta en otro grupo, la UI avisa.
- El listado normal de aplicaciones muestra etiqueta del grupo cuando corresponde.
- App Usuario muestra grupos de apps con barra de progreso real y reset diario a las 12 PM.
- El motor bloquea todas las apps del grupo cuando el limite compartido se agota; los limites individuales de app pueden convivir.
- Supabase DEV tiene `app_groups`, `app_group_apps` y tablas base para grupos de usuarios (`protected_user_groups`, `protected_user_group_members`).
- Panel Admin muestra comunidad y responsable, sin herramientas tecnicas DEV.
- Pantalla Actualizaciones del Usuario ya no muestra reset/diagnostico DEV.
- Activacion Usuario DEV 159+ acepta token simple de 6 caracteres, token nombre-CODIGO y texto pegado con codigo.
- Al activar Usuario, se limpia activacion local vieja incompatible y se guarda solo el nuevo deviceId.
- Supabase DEV `pair_device_with_code` devuelve errores mas especificos: codigo inexistente, usado, vencido, borrado, rol incorrecto o licencia.
- El bug de token valido fue licencia DEV de `Ajdut` suspendida/vencida; se reactivo en Supabase DEV hasta 2027-07-10, max_user_devices 250.
- Web Super Admin legacy fue restaurada porque el usuario la necesita. URL conocida: `https://web-super-admin-nine.vercel.app/communities`. No borrar `web-super-admin` salvo ticket explicito.

## Limpieza/refactor reciente

- Eliminado modulo vacio `core-license`.
- Eliminado paquete Admin viejo `app-admin/.../devices`.
- Eliminada UI y clase `AdminDevTools`.
- Reducido `DashboardViewModel` a estado de negocio.
- `SupabaseDevMaintenanceClient` conserva solo `purgeDevice`, usado para eliminar dispositivo definitivamente.
- Textos visibles dejaron de mostrar `Modo Offline / Desarrollo`.
- Diagnostico tecnico interno sigue existiendo en `core-telemetry`, VPN y Accessibility, pero no aparece como boton normal.

## Build y publicacion

Verificacion ejecutada:

```bash
./gradlew :app-admin:testDevDebugUnitTest :app-admin:compileDevDebugKotlin :app-user:compileDevDebugKotlin
scripts/publicar_dev.sh
```

Resultado actual: suite completa y builds DEV OK, App Usuario DEV 187 y App Admin DEV 181 publicadas.

## Cierre 2026-07-13 - Ticket 1 bypass incógnito y DNS

- Causa raíz: en Internet abierto la VPN enruta DNS y resolvers cifrados conocidos, no las IP finales. Chrome podía recibir correctamente un bloqueo DNS de UT1 y aun así mostrar el sitio mediante un socket HTTP/2, HTTP/3 o QUIC previamente resuelto y reutilizado.
- No era una falla de cobertura, firma, parser ni `VpnDomainPolicyEvaluator`: Logcat confirmó decisiones `Block` de la lista local para el canario DEV. Private DNS del sistema estaba desactivado.
- Al producirse una decisión DNS `Block`, `FilterVpnService` aplica una invalidación puntual de conexiones del navegador mediante la barrera de túnel completo existente. Un cooldown monotónico de 30 segundos agrupa reintentos sin polling agresivo.
- La VPN continúa limitada a navegadores; no corta Wi-Fi, datos ni otras aplicaciones. Chrome permanece abierto.
- Validación física en Samsung SM-S908E con App Usuario DEV 187: `coca.com` bloqueó en Chrome normal e incógnito; Google con SafeSearch y `example.com` continuaron funcionando.
- Tests/build: `:feature-vpn:test`, `:core-policy:test`, `:app-user:assembleDevDebug` y `:app-user:testDevDebugUnitTest` OK.
- Commit funcional: `af2f87f`.
- App Usuario DEV 187 publicada. App Admin permaneció en DEV 181.
- Limitación real: la invalidación se dispara cuando existe una decisión DNS bloqueante. Navegación directa por IP o un resolver cifrado desconocido que no atraviese las rutas de enforcement requiere el anti-evasión avanzado del Ticket 12; no se amplió ese alcance en este ticket.

## Cierre 2026-07-12 - estabilidad VPN/DNS

- SafeSearch ya no responde a Chrome con un CNAME sintetizado que Android rechazaba con `ERR_NAME_NOT_RESOLVED`; responde A/AAAA directos del destino seguro y NODATA para HTTPS/SVCB.
- Validacion fisica en Samsung SM-S908E: busqueda Google `clima` cargo resultados y `example.com` abrio con Internet abierto y Solo resultados desactivado.
- DNS de navegadores ya no espera escrituras Room de telemetria; la cola de diagnostico es acotada y best-effort.
- Respuestas DNS upstream se validan por transaccion/opcode/estado y las respuestas truncadas usan fallback TCP.
- App Usuario conserva la proteccion VPN al iniciar un proceso, en lugar de deshabilitarla transitoriamente.
- Accessibility dejo de disparar full sync cada segundo; conserva observacion Room y solicitud programada.
- La allowlist tecnica de buscadores dejo de incluir el sufijo amplio `googleapis.com`; reglas manuales explicitas mantienen prioridad.
- Activacion y dispositivos ya vinculados reportan el versionCode instalado real.
- La lista dinamica respeta la comprobacion diaria; el boton DEV conserva la comprobacion forzada manual.
- Supabase DEV tiene 15 indices nuevos para claves foraneas y `anon` ya no ejecuta `revoke_device` ni `create_admin_pairing_code`.
- Commits funcionales del cierre: `29398af`, `9259d95`, `26f4452`, `983f263`, `4611fa6`, `f489780`, `758b5da`.

Publicacion DEV recomendada por app:

```bash
scripts/publicar_admin_dev.sh
scripts/publicar_usuario_dev.sh
```

`scripts/publicar_dev.sh` queda disponible para publicar ambas apps juntas, pero no usarlo para cambios Admin-only o Usuario-only.

Nota 2026-07-07: DEV 124 ordena la base visual Admin con iconos internos reutilizables, navegacion inferior con iconos reales, flechas reales y header integrado en Usuarios protegidos.
Nota 2026-07-07: App Admin DEV 125 unifica headers internos al estilo Comunidad, con flecha atras integrada y titulos sin duplicar. App Usuario queda en DEV 124.
Nota 2026-07-07: App Admin DEV 126 reemplaza iconos dibujados a mano por Material Icons Core liviano y corrige la card de Usuarios protegidos para que el estado no se corte.
Nota 2026-07-08: App Admin DEV 127 agrega padding de barra de estado en headers para que no se tapen con hora/senal/bateria en el telefono.
Nota 2026-07-08: App Admin DEV 128 rediseña detalle de usuario con nombre como header, flecha atras integrada y tabs acordes al resto.
Nota 2026-07-08: App Admin DEV 129 corrige Usuarios protegidos para que el contenido no quede bajo el header antes de actualizar y muestra el token de usuario dentro de la misma ventana.
Nota 2026-07-08: App Admin DEV 130 mejora Aplicaciones con filtros rapidos, busqueda compacta y orden confirmado al bloquear.
Nota 2026-07-08: App Admin DEV 131 deja fijo el header del usuario en Aplicaciones, mueve el chip de estado al lado del nombre, enfoca automaticamente la busqueda y quita la seleccion gris de filtros.
Nota 2026-07-08: App Admin DEV 132 usa el pez como icono y en Home, limpia el token al copiar y genera tokens de usuario validos por 3 horas en Supabase DEV.
Nota 2026-07-08: App Admin DEV 133 integra el pez en Home sin fondo blanco y mueve la accion borrar usuario a un menu de tres puntos.
Nota 2026-07-08: App Admin DEV 134 deja Todas como filtro inicial en apps, simplifica seleccion de filtros, compacta tarjetas y usa el banner superior para estados de acciones.
Nota 2026-07-08: App Admin DEV 135 compacta automaticamente el header del detalle de usuario al scrollear o abrir busqueda, sube el titulo y reduce el banner de accion.
Nota 2026-07-08: App Admin DEV 136 suaviza con animacion la compactacion del header y agrega flotacion sutil al pez del Home.
Nota 2026-07-08: App Admin DEV 137 hace que el header acompane directamente el scroll y cambia el pez del Home a saludo al entrar o tocar.
Nota 2026-07-08: App Admin DEV 138 suaviza la compactacion del header con resorte corto y agrega animacion avanzada del pez en Home con loop, parpadeo, escape/vuelta y reaccion al tocar.
Nota 2026-07-08: App Admin DEV 139 reemplaza el pez del Home por una version vectorial por capas, con parpadeo visible, aleteo real, cola animada y giro 360.
Nota 2026-07-08: App Admin DEV 140 refina el pez del Home con cara mas limpia, aletas mas visibles y volumen tipo 3D mediante gradientes, luces y sombras.
Nota 2026-07-08: App Admin DEV 141 reemplaza el pez artesanal por un asset Lottie 2D liviano, con capas animadas y layout del hero sin solapar texto.
Nota 2026-07-08: App Admin DEV 142 vuelve el pez del Home al diseno de DEV 139 y elimina Lottie del Admin para salir de la version rechazada.
Nota 2026-07-08: App Admin DEV 143 usa el pez premium del paquete en el Home, manteniendo la animacion de DEV 142 con aleteo visible de aletas y cola.
Nota 2026-07-08: App Admin DEV 144 convierte FeedbackBanner en banner premium con degradado turquesa/azul, texto blanco, pez animado por estado y Home sin giro 360.
Nota 2026-07-08: App Admin DEV 145 aplica el banner premium con pez al mensaje compacto del detalle de apps, donde antes seguia el banner celeste viejo.
Nota 2026-07-09: Se agregan scripts de publicacion DEV separados por app y el banner premium queda aplicado de forma explicita en Admin y Usuario.
Nota 2026-07-09: App Usuario DEV 125 y App Admin DEV 146 publicadas con banner premium compartido, scripts separados verificados y docs actualizadas.
Nota 2026-07-09: App Usuario DEV 126 agrega Home al estilo Admin, unifica estetica de pantallas de Usuario, y App Admin DEV 147 actualiza el banner premium compartido con el pez al fondo derecho.
Nota 2026-07-09: App Usuario DEV 127 mueve solicitudes a Home y estado a Ajustes, oculta nombres tecnicos de apps, mejora solicitudes con iconos/nombres amigables y agrega filtros/progreso en Mis apps. App Admin DEV 148 renombra grupos a Apps en grupo y corrige scroll del detalle de aplicaciones.
Nota 2026-07-09: App Usuario DEV 128 ajusta Mis apps al estilo visual del panel Admin, muestra Apps en grupo solo en su filtro, separa mas la pila de iconos y suma pez Home con burbujas/sombra/animacion.
Nota 2026-07-09: App Usuario DEV 129 agrega Web al menu, flechas atras con historial, refresh real en Mis apps, Ajustes mas moderno, iconos compartidos Material y banner mas compacto con pez patrullando.
Nota 2026-07-10: DEV 156-158 corrige reenlace Usuario tras borrar dispositivo remoto, mantiene formulario de activacion disponible y acepta codigos simples o pegados.
Nota 2026-07-10: DEV 159 separa errores reales de activacion/token en Supabase, reemplaza activacion local vieja y publica Usuario/Admin 159.
Nota 2026-07-10: Se reactiva licencia DEV de comunidad `Ajdut`; el fallo "token invalido" era licencia suspendida/vencida, no parser ni app.
Nota 2026-07-10: DEV 160 agrega switches Web basicos en Admin -> Web -> Usuario y tab Web Usuario muestra estados. Commit `4927a40`.

## Modulos principales

- `app-user`: app del usuario protegido.
- `app-admin`: app administrador.
- `core-domain`: modelos, repositorios y use cases.
- `core-data`: repositorios Room y outbox.
- `core-database`: Room, DAOs, entidades y migraciones.
- `core-network`: Supabase REST/Auth/Realtime.
- `core-sync`: SyncEngine, WorkManager, outbox y realtime.
- `core-policy`: motor puro de decisiones.
- `core-security`: activacion y sesion local.
- `core-update`: actualizaciones APK DEV.
- `core-telemetry`: diagnostico tecnico interno.
- `feature-vpn`: bloqueo de dominios/web.
- `feature-accessibility`: bloqueo de apps.
- `feature-activation`, `feature-requests`, `feature-status`, `feature-usage`, `feature-block`, `feature-onboarding`.

## Proxima fase sugerida

Fase web Super Admin.

Objetivo inicial sugerido:

1. Definir modelo minimo Super Admin -> Comunidad -> Administradores -> Usuarios/Dispositivos.
2. Crear web Super Admin separada de las apps Android.
3. Super Admin crea comunidad.
4. Super Admin crea administradores de una comunidad.
5. Admin activa App Admin con token asignado.
6. Admin genera tokens de Usuario dentro de su comunidad.

No empezar esa fase sin confirmar el alcance exacto del primer ticket web.

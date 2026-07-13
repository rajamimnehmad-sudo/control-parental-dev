# HANDOFF ACTUAL - Content Filter

Fecha de corte: 2026-07-13

Tomar este archivo como contexto oficial. No reanalizar arquitectura desde cero.

## Baseline estable actual

- Tag: `stable/dev-191-web-protection`.
- Alcance y recuperacion: `docs/BASELINES.md`.
- Para volver a este comportamiento desde una version futura, usar el codigo del tag y publicar con un `versionCode` superior; no desinstalar ni borrar datos.

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

Version publicada real al 2026-07-13:

```text
App Usuario versionCode 191
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
https://syeycayasyufedwoprea.supabase.co/storage/v1/object/public/dev-updates/app-user-dev-191-debug.apk
https://syeycayasyufedwoprea.supabase.co/storage/v1/object/public/dev-updates/app-admin-dev-181-debug.apk
```

SHA-256:

```text
Usuario DEV 191: c408e45e1f892d17d94018a4044b28a8a032aace7ae26b3c94c4e73091f4f3db
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
./gradlew --no-daemon :feature-vpn:test :feature-vpn:ktlintCheck :feature-vpn:detekt :core-policy:test :app-user:testDevDebugUnitTest :app-user:assembleDevDebug
scripts/publicar_usuario_dev.sh
```

Resultado actual: tests y build del area VPN OK, App Usuario DEV 191 publicada y App Admin DEV 181 sin cambios.

## Cierre 2026-07-13 - optimizacion de invalidaciones VPN

- Causa raiz: una pagina bloqueada puede consultar muchos subdominios en una rafaga. Cada primer bloqueo por dominio iniciaba `invalidateBrowserConnectionsThenStart`, que cancelaba y reemplazaba la invalidacion anterior. El resultado era una sucesion de reconstrucciones del tunel aunque todas pertenecieran a la misma navegacion.
- DEV 191 agrega una cola acotada y deduplicada de preparaciones de destinos bloqueados. Reune la rafaga, resuelve como maximo ocho destinos en paralelo y conserva una sola barrera estricta por lote.
- La primera invalidacion sigue siendo inmediata. Las olas posteriores respetan un intervalo minimo de 1,5 segundos para agrupar trabajo sin polling agresivo. La cola admite hasta 128 destinos; ante saturacion, error, cambio de policy o cambio de lista se libera el estado para permitir un reintento seguro.
- La optimizacion no cambia las decisiones de UT1, SafeSearch ni Solo resultados. `example.com` bloqueado bajo Solo resultados demuestra esa policy, no pertenencia a UT1.
- Privacidad: la cola existe solo en memoria y no agrega consultas, URL, titulos, HTML, contenido ni historial a logs o almacenamiento.
- Tests/build: `:feature-vpn:test`, `:feature-vpn:ktlintCheck`, `:feature-vpn:detekt`, `:core-policy:test`, `:app-user:testDevDebugUnitTest` y `:app-user:assembleDevDebug` OK; 517 tareas sin fallos.
- Validacion fisica en Samsung SM-A235M con DEV 191 instalada sin borrar datos: VPN foreground activa; el usuario confirmo busqueda Google funcional y bloqueo de resultados externos. La comprobacion automatizada final con `example.com` mantuvo decisiones de bloqueo y redujo la rafaga controlada a una sola invalidacion en 12 segundos.
- Commit funcional: `1347bdf`. Android CI `29275226239` completo build, tests, ktlint, Android lint y detekt. El workflow general de ambas apps se cancelo para no publicar App Admin.
- App Usuario DEV 191 publicada en `https://syeycayasyufedwoprea.supabase.co/storage/v1/object/public/dev-updates/app-user-dev-191-debug.apk`; manifiesto y descarga verificados con SHA-256 `c408e45e1f892d17d94018a4044b28a8a032aace7ae26b3c94c4e73091f4f3db`. App Admin permanecio en DEV 181.
- Limitaciones: una pagina ausente de UT1 no queda bloqueada por esa fuente en Internet abierto; eso es falta de cobertura y no bypass. Navegacion directa por una IP nunca resuelta y resolvers cifrados desconocidos siguen reservados para el Ticket 12.

## Cierre 2026-07-13 - Ticket 3 modelo comun de decisiones

- Se agrego `WebPolicyDecision`, contrato inmutable y sin dominio, URL, consulta ni contenido visible.
- Los resultados soportados son `Allow`, `Block`, `Uncertain` y `RequireReview`; todos exigen categoria, confianza, fuente, version, motivo tecnico, momento de evaluacion y vencimiento cuando corresponde.
- `WebPolicyDecisionResolver` aplica prioridades explicitas: politica de plataforma, regla del administrador, allowlist tecnica, lista firmada, clasificador local de dominios, clasificador local de busquedas y politica por defecto.
- El resolvedor selecciona una decision completa sin mezclar ni modificar datos de otra capa. Dentro de una misma fuente, una version vieja no reemplaza una nueva; decisiones vencidas se descartan y un conflicto con igual frescura prefiere revision.
- Alcance deliberado: el modelo queda listo para los tickets siguientes, pero no activa IA, fuentes complementarias, cache, reportes ni revision humana. `PolicyDecision` conserva el enforcement actual de licencia, salud, tiempo extra y VPN.
- Tests/build: `:core-domain:test`, `:core-policy:test`, ktlint y detekt de ambos modulos, `:app-user:testDevDebugUnitTest` y `:app-user:assembleDevDebug` OK.
- Validacion fisica en Samsung SM-S908E: actualizacion in-place a DEV 188 sin borrar datos; Android restauro la VPN siempre activa y `FilterVpnService` quedo en primer plano.
- Commit funcional: `7997d00`. Android CI `29248420096` completo build, tests, ktlint, lint y detekt.
- App Usuario DEV 188 publicada y descarga verificada con SHA-256 `145e78cce91fe336d41ac808cd73b3392f0febcf96727f4329a203e3a781cd2f`. App Admin permanecio en DEV 181.

## Saneamiento CI 2026-07-13

- `ktlintFormat` normalizo mecanicamente 33 archivos con deuda de formato previa, sin cambios funcionales ni de datos.
- Commit `68812db`; Android CI `29242948770` completo build, tests, ktlint, lint y detekt.
- Los workflows automaticos de publicacion fueron cancelados; el saneamiento no genero releases.

## Cierre 2026-07-13 - Ticket 1 bypass incógnito y DNS

- Hubo dos causas independientes. En Internet abierto la VPN enruta DNS y resolvers cifrados conocidos, no las IP finales; Chrome podía reutilizar una IP y un socket HTTP/2, HTTP/3 o QUIC ya resuelto después de recibir un bloqueo DNS. Además, Android usaba un valor incorrecto para el primer seed FNV-1a del Bloom UT1: el dominio autorizado estaba en el índice exacto firmado, pero fallaba el precheck Bloom y llegaba como `Allow / no-blocking-rule`.
- Normal e incógnito no usan una política distinta. Sus cachés DNS y pools de conexiones son independientes, por eso el mismo defecto podía verse en un modo y no en el otro. `DnsPacketParser`, `VpnDomainPolicyEvaluator` y `WebDomainListStore` sí recibían tráfico tradicional; Private DNS no tenía proveedor configurado y DoH/DoT no fueron la causa observada.
- La corrección inicial de DEV 187 era incompleta: levantaba una barrera de túnel completo durante 400 ms y luego volvía al túnel DNS. Chrome toleraba la pausa y podía reanudar el socket anterior; por eso DEV 188 mostró bloqueo intermitente tanto en normal como en incógnito.
- DEV 189 agregó rutas host persistentes y acotadas para cortar conexiones reutilizadas, pero seguía sin cubrir dominios UT1 afectados por el seed incorrecto. DEV 190 alinea el seed Bloom con el publicador y agrega un golden vector compartido por tests.
- Ante el primer `Block`, `FilterVpnService` mantiene la barrera estricta mientras resuelve A y AAAA por sockets protegidos, agrega rutas host para las IP bloqueadas y recién entonces restaura el túnel abierto. Las rutas permanecen hasta cambio de política/lista, reinicio del servicio o expulsión por el límite de 128 rutas.
- Los reintentos se deduplican con una huella SHA-256 sólo en memoria; no se conservan ni registran consultas o dominios. No hay polling.
- La VPN continúa limitada a navegadores; no corta Wi-Fi, datos ni otras aplicaciones. Chrome permanece abierto.
- SafeSearch tenía otra degradación observable: cuando el destino estricto no devolvía AAAA en el teléfono, la VPN respondía `SERVFAIL`, lo que permitía fallback a una respuesta IPv6 vieja. DEV 190 responde `NOERROR/NODATA`, conserva la respuesta A estricta y evita una barrera general. Google cargó sin errores y los sockets filtrados de Chrome confirmaron QUIC activo contra la IP del destino estricto.
- Una actualización in-place podía matar `FilterVpnService` dejando una marca local `Activa` obsoleta. DEV 190 reinicia idempotentemente al abrir la app y registra receptores acotados para `MY_PACKAGE_REPLACED` y `BOOT_COMPLETED`; la reinstalación física confirmó que la VPN volvió sola, foreground y con la política cargada sin abrir la app.
- Validación física automatizada y redactada en Samsung SM-S908E: `example.com` cargó en normal e incógnito y siguió cargando después del bloqueo; `coca.com` produjo decisiones `Block` en ambos modos y una ruta persistente; Google cargó/recargó con SafeSearch estricto. El usuario confirmó manualmente `ERR_TIMED_OUT` para el dominio autorizado presente en UT1 en incógnito. Ese dominio sólo se comprobó offline contra la lista y no se abrió automáticamente.
- Logs redactados confirmaron decisiones DNS, invalidación/ruta acotada, ausencia de fallos VPN y reactivación en frío. No se capturaron consultas, URL completas, títulos, HTML, contenido ni historial.
- Tests/build: `:feature-vpn:test`, `:feature-vpn:ktlintCheck`, `:feature-vpn:detekt`, `:core-policy:test`, `:app-user:assembleDevDebug` y `:app-user:testDevDebugUnitTest` OK.
- Commit funcional final: `1a83d6f`.
- Android CI `29261643262` completó build, unit tests, ktlint, Android lint y detekt. El workflow automático general `29261643256` se canceló para no publicar App Admin.
- App Usuario DEV 190 publicada en `https://syeycayasyufedwoprea.supabase.co/storage/v1/object/public/dev-updates/app-user-dev-190-debug.apk`; manifiesto y descarga verificados con SHA-256 `e7a4a9dfb8a7cdc101a98ab68137ac89e3b6d1ce375f692320df15324c8c1425`. App Admin permaneció en DEV 181.
- Limitaciones reales: una navegación directa por una IP nunca resuelta por la VPN y un resolver cifrado desconocido fuera de las rutas conocidas siguen perteneciendo al Ticket 12. Una IP compartida con un dominio bloqueado puede afectar temporalmente otro sitio de ese navegador; el límite de 128 evita crecimiento sin cota. La opción visual de SafeSearch puede seguir apareciendo en Google, pero la conexión de red quedó comprobada contra el destino estricto.

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

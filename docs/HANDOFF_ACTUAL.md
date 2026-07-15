# HANDOFF ACTUAL - Content Filter

Fecha de corte: 2026-07-15

Tomar este archivo como contexto oficial. No reanalizar arquitectura desde cero.

## Fuentes oficiales

- Este archivo es la verdad tecnica de lo implementado, probado y publicado.
- `docs/BACKLOG_PRODUCTO.md` es la fuente canonica de ideas, prioridades y tickets propuestos/aprobados.
- El Google Doc `Backlog Codex - Control Parental` es una bandeja de entrada historica, no una segunda verdad tecnica.
- Al cerrar un ticket, sincronizar handoff y backlog en el mismo commit cuando cambie el estado del producto.

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

Version publicada real al 2026-07-15:

```text
App Usuario versionCode 210
App Admin versionCode 210
versionName 1.0.1-dev
```

Manifiestos:

```text
https://syeycayasyufedwoprea.supabase.co/storage/v1/object/public/dev-updates/app-user-dev-manifest.json
https://syeycayasyufedwoprea.supabase.co/storage/v1/object/public/dev-updates/app-admin-dev-manifest.json
```

APKs:

```text
https://syeycayasyufedwoprea.supabase.co/storage/v1/object/public/dev-updates/app-user-dev-210-debug.apk
https://syeycayasyufedwoprea.supabase.co/storage/v1/object/public/dev-updates/app-admin-dev-210-debug.apk
```

SHA-256 publicados:

```text
Usuario e2959b015debf74166d0951141c9e0cd252c172f5e1f297a1617386aa1c4572c
Admin   a2c7113b1b707b996cf5b7ec13820160e98899b3192e38ef04de4e9da3f5ed60
```

## Trabajo en curso 2026-07-15 - DAG-SAFETY-01

- DEV 211 esta compilado localmente y pendiente de publicacion. No presentarlo como publico hasta verificar ambos manifiestos DEV.
- Corrige la sincronizacion de reglas al abrir DAG y comprueba automaticamente durante dos minutos una solicitud recien enviada. Supabase DEV confirma que la aprobacion de `easy.com.ar` y su regla `Allow` por dispositivo existian correctamente; el fallo estaba en el refresco del cliente.
- Las aprobaciones futuras reutilizan una regla `Allow` existente en vez de crear otro duplicado. No se borraron las reglas duplicadas preexistentes de YouTube ni ningun otro dato.
- Las imagenes lazy con origen HTTPS y AVIF estatico en Android 12+ pasan por el mismo clasificador local. La decision de pagina incorpora el balance de imagenes seguras, bloqueadas e inciertas; SVG, GIF, animacion, `data:` y `blob:` siguen cerrados.
- `imgsrc.ru` queda bloqueado antes de crear una navegacion WebView. Es una barrera preventiva no anulable mediante revision de dominio.
- Atras oculta primero el teclado. Prueba fisica SM-A235M: Wikipedia mostro fotos seguras de manzanas y flores; Atras cerro el teclado sin salir; `imgsrc.ru` mostro bloqueo sin solicitud WebView. Easy requiere validacion final en el celular cuyo dispositivo recibio la aprobacion.
- Validacion integral local: 895 tareas Gradle correctas con tests de dominio, Usuario y Admin; ktlint, Android lint, builds optimizados y detekt informativo. No se consumieron consultas Brave, no se toco Production, no se agregaron secretos ni Service Role Key a Android.

## Cierre 2026-07-15 - DAG-IMAGES-01 clasificacion visual local

- DEV 210 permite imagenes estaticas JPEG, PNG y WebP en DAG solo despues de clasificarlas completamente en el telefono. La pagina permanece oculta durante el analisis textual y cada imagen permanece sin entregar al WebView hasta obtener una decision local segura.
- El modelo OpenNSFW cuantizado se incluye dentro del APK Usuario y se ejecuta con TensorFlow Lite en CPU. SHA-256 del modelo `eb3b446a6a8c1a73998a76011b97cfc67bc01084c63ee195c774e71344a66442`; licencia y atribuciones incluidas en `app-user/src/main/assets/dag/THIRD_PARTY_NOTICES.txt`.
- Politica conservadora fail-closed: score seguro hasta `0.08`; bloqueado desde `0.20`; zona intermedia, error, formato no soportado, descarga incompleta o modelo invalido se bloquean. GIF, SVG, APNG, WebP animado, video y audio permanecen bloqueados.
- Limites: HTTPS obligatorio, 40 imagenes por pagina, 4 MiB por imagen, timeout de 8 segundos y dimension fuente validada. El proxy no sigue redirecciones HTTPS a HTTP y rechaza destinos locales, privados, link-local, multicast, Carrier NAT e IPv6 ULA para evitar SSRF.
- El WebView bloquea antes de los scripts de pagina la creacion de URLs `blob:` y el registro/uso previo de Service Workers; descargas, popups, camara, microfono, ubicacion, archivos, HTTP e intents externos conservan sus bloqueos anteriores.
- Privacidad: imagen, URL, score y resultado visual no se envian a Supabase, Brave ni al administrador. La revision humana sigue limitada a dominios inciertos; las imagenes inciertas se bloquean sin crear solicitud. Navegar o recargar una URL directa no consume una consulta Brave.
- Compatibilidad del APK Usuario: incluye bibliotecas ARM32 y ARM64 para telefonos Android estandar Samsung, Xiaomi, Motorola y Oppo; x86 de emulador queda excluido. El APK publico Usuario mide aproximadamente 15 MiB.
- Validacion fisica SM-A235M: los APK publicos DEV 210 se instalaron in-place; Wikipedia `Apple` mostro fotos seguras de manzanas y flores despues del analisis local; no hubo crash. Device Admin y Accessibility permanecieron activos y `FilterVpnService` en primer plano. No se consumieron consultas Brave.
- Validacion integral local: 1.829 tareas Gradle, builds Usuario/Admin, tests de todas las variantes, ktlint, lint y detekt exitosos. Commit funcional `dc6ad61`; Android CI `29415230021` y workflow `Publicar APKs DEV` `29415229996` exitosos; hashes publicos verificados.
- La clasificacion probabilistica reduce el riesgo pero no garantiza precision perfecta. Video clasificado no forma parte de este ticket: permanece bloqueado y requiere un ticket separado con muestreo temporal, presupuesto de CPU/bateria y validacion fisica.
- No se toco Production, no se borro ningun dato, no se modifico Supabase DEV y no se agregaron secretos ni Service Role Key a Android.

## Cierre 2026-07-15 - DAG-STANDALONE-01 navegador independiente

- DEV 209 convierte DAG en una experiencia independiente para el usuario sin crear otro APK: `DagActivity` tiene nombre e icono propios, afinidad de tarea separada y una tarjeta distinta de Content Filter en Recientes. Comparte proceso, firma, activacion, actualizacion, politica e historial cifrado con App Usuario.
- El inicio es minimalista: barra combinada, accion Ir y menu para Inicio/Historial. El aspecto usa identidad DAG propia inspirada en navegadores modernos. Atras recorre el WebView; sin pagina anterior vuelve a Inicio y desde Inicio cierra la tarea DAG.
- App Usuario conserva la entrada Web, pero `Abrir DAG` abre la tarea independiente. Ya no ofrece crear nuevos atajos fijados. El identificador del atajo historico DEV 204-208 se conserva solo para que los atajos ya fijados no queden rotos; Android no permite a una app eliminar silenciosamente un atajo fijado por el usuario.
- `DagLauncherController` combina activacion local y regla DAG. El componente launcher esta deshabilitado por defecto, aparece automaticamente en el cajon de apps solo cuando ambos estados permiten DAG y se deshabilita al cerrar. `DagActivity` tambien retira su tarea si la revocacion llega mientras esta abierta.
- Validacion fisica Samsung SM-A235M: App Usuario y DAG aparecieron como dos tareas con afinidades diferentes; el icono nuevo `DAG` aparecio en el cajon Samsung; cerrar desde Admin elimino el icono, deshabilito el lanzamiento explicito y retiro la tarea; reabrir hizo reaparecer el icono y permitio iniciar DAG sin reinstalar ni reactivar.
- Actualizacion in-place a los APK publicos DEV 209: activacion preservada, DAG abierto, Accessibility habilitado, Device Admin activo y `FilterVpnService` en primer plano. No se realizaron busquedas Brave durante este ticket.
- Tests: nueva matriz de disponibilidad del launcher, ciclo de disponibilidad del navegador, unit tests Usuario/Admin, ktlint, Android lint, detekt y builds DEV Usuario/Admin exitosos. Commit funcional `0aecea6`; `Publicar APKs DEV` `29412273313` exitoso y hashes publicos verificados. Android CI `29412201877` completo todas sus etapas tecnicas correctamente.
- No se toco Production, no se borro ningun dato, no se cambio Supabase y no se agregaron secretos ni Service Role Key a Android.

## Cierre 2026-07-14 - DEV 207 rendimiento y barrera Android

- App Usuario y App Admin tienen `versionCode 207` y `versionName 1.0.1-dev` publicados en Supabase DEV. Usuario se genera no depurable, con R8 y perfil de arranque; Admin se genera no depurable y conserva su comportamiento funcional.
- Causa principal de la lentitud restante: `ProtectorAccessibilityService` recorria hasta 200 nodos de la ventana para buscar acciones peligrosas en cada cambio de contenido, aun dentro de App Usuario y en aplicaciones comunes. El recorrido ahora solo se ejecuta para Ajustes, instaladores, desinstaladores resueltos y pantallas de accesibilidad Samsung.
- La lista `Mis apps` usa filas Android nativas reciclables, cache de iconos y decodificacion fuera del hilo principal. La carga local aparece antes de la sincronizacion y se evita el doble refresco de inicio/`ON_RESUME`.
- El inventario de 156 apps ya no se vuelve a detectar para publicarlo ni genera 156 solicitudes. Se envia en lotes de 20; el primer lote unico excedio el timeout de Supabase DEV, mientras que la estrategia final registro `installed apps published` sin errores HTTP.
- Medicion fisica final SM-A235M, Accessibility activo y diez desplazamientos automatizados: `Mis apps` paso del baseline DEV 206 de 26,05 % de cuadros lentos, p50 18 ms, p90 77 ms y p99 200 ms a 5,76 %, p50 19 ms, p90 20 ms y p99 27 ms. Web registro 2,44 % y Home 0,81 %.
- Seguridad fisica: tres aperturas consecutivas de Informacion de la app fueron expulsadas hasta el launcher; la ficha y la lista Samsung de servicios instalados no quedaron accesibles. La app siguio instalada, Accessibility enlazado y Device Admin activo.
- Tests/build: unit tests de `core-network`, `feature-accessibility`, App Usuario y App Admin; ktlint de los cuatro; Android lint Usuario/Admin y ensamblado de ambos APK DEV exitosos. Detekt conserva deuda historica informativa sin fallos nuevos bloqueantes.
- Commit funcional `8ae6c8d`. Android CI 29386364319 y `Publicar APKs DEV` 29386364309 finalizaron correctamente. Ambos APK publicos se descargaron y sus hashes coincidieron con los manifiestos: Usuario `9c48ad73bceac207e1782d1a14649f9ad10ecfb5b486cb1157c6d0d7f8827240`; Admin `8b1786f068b387eafca738a89fa36e4b76b603f7e51a046c14f096c5bef092c1`.
- No se toco Production, no se borraron datos, no se agregaron secretos ni Service Role Key a Android y no se consumieron consultas Brave en estas pruebas.

## Cierre 2026-07-14 - DAG-PERF-02 fluidez DAG y App Usuario

- DEV 206 elimina el repintado permanente del pez en Home, conserva la ilustracion estatica y cambia Home/Web a listas lazy para no componer todo el contenido desplazable a la vez.
- DAG usa la cache HTTP normal del WebView, combina saneamiento y extraccion de texto en una sola llamada JavaScript e inspecciona una sola vez cada carga. Imagenes y video permanecen bloqueados y la pagina sigue oculta hasta terminar la clasificacion local.
- El clasificador precompila normalizaciones y expresiones de terminos. Las altas, bajas y borrados del historial cifrado se ejecutan fuera del hilo principal.
- No se implemento cache de consultas ni resultados Brave: repetir una consulta consume nuevamente; navegar directamente, recargar y abrir una pagina desde el historial no consume una busqueda.
- Medicion fisica SM-A235M: el inicio en reposo paso de aproximadamente 183 cuadros generados en tres segundos a 3. La pantalla Web, con seis desplazamientos automatizados, registro 5,68 % de cuadros fuera de plazo, mediana 17 ms y percentil 90 21 ms.
- Pruebas: unit tests de App Usuario, ktlint de App Usuario/core-ui y build Usuario/Admin DEV exitosos. Instalacion in-place preserva datos y activacion.
- No cambio Supabase, Production, datos, secretos, VPN, Accessibility ni la barrera antimanipulacion.

## Cierre 2026-07-15 - DAG-BROWSER-01A navegador protegido

- DEV 208 esta publicado para Usuario y Admin. App Usuario incorpora barra combinada, resultados, navegador interno de una pestana, atras/adelante/recargar/inicio e historial local cifrado; App Admin muestra, aprueba o rechaza solicitudes de dominio incierto.
- Consulta, resultado y texto de pagina pasan por un clasificador local conservador en espanol, hebreo e ingles. Imagenes, video, descargas, popups, camara, microfono, ubicacion, archivos, HTTP e intents externos permanecen bloqueados.
- Solo una consulta localmente permitida puede llegar a `dag-search`. La Edge Function y sus migraciones quedaron desplegadas exclusivamente en Supabase DEV; autorizan por token de dispositivo y regla DAG activa, aplican 20 consultas por minuto y el cupo configurable de la comunidad, actualmente 100 por dispositivo/mes para Yeshurun Tora.
- Supabase guarda solo `device_id`, mes y contador. No guarda consultas, resultados ni historial; la tabla no es legible por `anon` ni `authenticated`. Android no contiene Brave API key ni Service Role Key.
- El plan Search de Brave quedo activo y `BRAVE_SEARCH_API_KEY` esta guardada solo como secreto de Supabase DEV. Una prueba directa devolvio HTTP 200 y resultados Web reales; la clave no se guarda en el repositorio, Android, logs ni documentacion.
- Modelo comercial aprobado para el piloto: USD 1 mensual con 100 busquedas por dispositivo y navegacion directa ilimitada. El cupo queda configurable por comunidad desde Super Web.
- Validacion fisica final en Samsung SM-A235M: una consulta bloqueada localmente no llego a Brave; una unica consulta permitida devolvio resultados Web reales; la pagina elegida quedo oculta al resultar incierta y ofrecio revision; una URL incierta genero en Admin una solicitud con dominio, titulo, categoria y modelo, sin consulta ni historial, y la solicitud de prueba fue rechazada.
- El historial local cifrado registro la unica busqueda permitida con fecha y permitio borrar solo ese elemento creado por la prueba. El texto de la consulta no aparecio en Logcat. No se realizaron mas consultas Brave.
- La revocacion remota cerro DAG en Usuario. El ciclo fisico detecto que DEV 207 conservaba el mensaje y candidato anteriores al reabrir con el mismo ViewModel; DEV 208 limpia la sesion visual al cerrar o reabrir, preserva el historial y tiene pruebas unitarias especificas. El ciclo final cerrar -> Usuario cerrado -> abrir -> inicio limpio paso en el dispositivo.
- Actualizacion in-place a DEV 208 verificada: Usuario y Admin conservan activacion; Device Admin sigue activo, Accessibility esta habilitado y enlazado y `FilterVpnService` permanece en primer plano. DAG quedo abierto.
- Commit funcional `4f2886a`. Android CI `29409620735` y `Publicar APKs DEV` `29409620788` finalizaron correctamente. Tests unitarios Usuario, ktlint y build Usuario/Admin pasaron localmente; los APK publicos descargados coinciden con los SHA-256 declarados arriba.
- No se toco Production, no se borraron datos remotos o preexistentes, no se agregaron secretos al repositorio ni Service Role Key a Android. Solo se elimino del historial local el elemento creado por esta validacion.
- La experiencia DAG independiente se implemento y valido en `DAG-STANDALONE-01` con DEV 209.

## Cierre tecnico 2026-07-14 - DAG-USAGE-01 contador mensual Super Web

- La migracion DEV `20260714231026_dag_usage_super_admin.sql` cambia el cupo inicial a 100 por dispositivo/mes, lo lee desde la licencia de cada comunidad y mantiene el incremento atomico. La Edge Function `dag-search` quedo activa en version 5 con mensaje de cupo generico.
- Super Web incorpora `/dag-usage` y acceso `Uso DAG` en la navegacion. Muestra consultas usadas, restantes, dispositivos activos, costo Brave neto estimado luego del credito global de USD 5 y proyeccion al cierre del mes.
- El panel se actualiza cada 10 segundos y agrupa por comunidad y dispositivo. Conserva el consumo del mes aunque DAG se cierre luego; nunca muestra ni recibe consultas, URLs, resultados o historial.
- El Super Admin puede cambiar el cupo mensual por comunidad entre 1 y 100.000. El valor inicial y vigente para Yeshurun Tora es 100.
- Seguridad verificada: `anon` recibe HTTP 401 al invocar el resumen; `anon` y `authenticated` no pueden leer la tabla de uso; las RPC de lectura/escritura exigen sesion autenticada y `require_super_admin()`.
- Validacion: resumen DEV devuelve Yeshurun Tora con 1 dispositivo DAG activo, 0/100 usadas; TypeScript, ESLint sobre `src`, build Next y bundle Cloudflare exitosos.
- Super Web Sites version 3 fue publicada en modo privado en `https://super-admin-content-filter.ramnehmad.chatgpt.site`. Es una vista previa secundaria. La web oficial confirmada por el usuario es `https://web-super-admin-nine.vercel.app/communities`; el contador aun debe publicarse alli. La prueba fisica final del navegador sigue pendiente por decision del usuario.

## Cierre 2026-07-14 - DAG-FOUNDATION-01 entrada, control y atajo

- DEV 204 agrega un control `Buscador DAG` por dispositivo en Comunidad -> Web de App Admin. DAG permanece cerrado salvo que exista una regla `Allow` explicita para `__dag_enabled__`.
- App Usuario muestra el estado en Web. Cuando DAG esta abierto permite entrar a una pantalla propia y solicitar a Android un atajo fijado; el atajo abre esa misma pantalla dentro de App Usuario y no instala una segunda app.
- La pantalla fundacional es preventiva: no ejecuta busquedas, no abre paginas y no muestra imagenes ni videos. La fuente de resultados, clasificacion local y revision de sitios inciertos quedan para tickets posteriores.
- El estado reutiliza `PolicyRule`, Room, outbox y sincronizacion DEV existentes. No se agrego tabla, migracion, borrado de datos ni credencial; no existe Service Role Key en Android.
- Tests de dominio, Admin y Usuario verifican cerrado por defecto, apertura solo explicita, revocacion e independencia respecto de las demas preferencias Web. Ktlint y compilacion de ambas apps pasan.
- Validacion fisica final en Samsung SM-A235M con la comunidad DEV `Yeshurun Tora`: Admin y Usuario quedaron enlazados, el control por dispositivo abrio DAG, la pantalla preventiva no mostro contenido, Android fijo el atajo, el atajo abrio DAG y luego reflejo `DAG esta cerrado` tras la revocacion. Se dejo DAG cerrado para los dos usuarios visibles; VPN, Accessibility y proteccion contra desinstalacion quedaron activas.

## Cierre 2026-07-14 - USER-PERF-01 fluidez y acciones de proteccion

- DEV 203 limita las suscripciones Compose de solicitudes, estado y proteccion a la pantalla que realmente las usa; ya no recomponen la raiz completa mientras otra seccion esta visible.
- `ProtectionViewModel` conserva la lectura local cada 30 segundos para reflejar autorizaciones, pero deja de duplicar el refresh remoto que ya ejecuta `UserApplication`.
- Ajustes reemplaza `Actualizar permisos` y `Pedir mantenimiento` por una sola accion contextual: `Solicitar acceso temporal` cuando todos los componentes estan sanos o `Reparar proteccion` cuando alguno esta degradado.
- Medicion fisica repetida con el mismo recorrido en Samsung SM-A235M: Home paso de 2,99 % a 0,26 % de frames lentos modernos despues de calentamiento; Ajustes paso de 0,66 % a 0,43 %. Home mantuvo percentil 90 de 22 ms y no registro frames mayores a 24 ms en la pasada final.
- Validacion defensiva: cinco aperturas de App Info y cinco intentos directos de desinstalacion fueron expulsados; la app continuo instalada, Accessibility enlazado, Device Admin activo y el proceso vivo.
- La instalacion de laboratorio por ADB desde DEV 194 activo la restriccion de Android para APKs no verificados y requirio habilitar `ACCESS_RESTRICTED_SETTINGS` antes de reautorizar Accessibility por la UI normal. Esta condicion pertenece al metodo de instalacion ADB y no se conto como validacion del actualizador interno.

## Estado funcional

- App Usuario y App Admin usan activacion real con Supabase DEV.
- App Admin se activa por token de administrador.
- App Usuario se activa solo con token generado desde Admin.
- Comunidad DEV activa actual de pruebas: `Yeshurun Tora`.
- Codigos numericos legacy 1-100 reemplazados por tokens aleatorios.
- Usuarios es la entrada unica del Admin para usuarios protegidos, aplicaciones y grupos de apps.
- La seccion separada Reglas ya no esta en navegacion.
- Bloqueo de apps por Accessibility funciona y cierra apps bloqueadas rapido.
- VPN esta siempre activa en App Usuario cuando hay permiso; Admin cambia politica Web, no prende/apaga VPN.
- Web Admin mantiene flujo real: Comunidad -> Web -> elegir usuario -> configurar Web.
- Web Admin usa selector Internet abierto/bloqueado y dos capas independientes: SafeSearch y Solo resultados.
- Web Usuario muestra esos mismos estados en modo solo lectura.
- DAG tiene control por dispositivo en Admin, entrada/atajo en Usuario, navegador protegido e historial local cifrado. DEV 208 y Brave en Supabase DEV quedaron validados fisicamente de principio a fin.
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

Verificacion ejecutada para DEV 202:

```bash
./gradlew --no-daemon --console=plain :feature-accessibility:testDebugUnitTest :core-network:test :app-user:testDevDebugUnitTest :app-admin:testDevDebugUnitTest :feature-accessibility:ktlintCheck :core-network:ktlintCheck :app-user:ktlintCheck :app-admin:ktlintCheck :app-user:assembleDevDebug :app-admin:assembleDevDebug -x uploadDevUpdatesToStorage -x prepareDevUpdatesForStorage
```

Resultado actual: tests, ktlint y build de ambas apps OK. DEV 202 publicada por GitHub Actions para Usuario y Admin.

## Cierre 2026-07-14 - barrera reforzada tipo Rimon sin MDM

- DEV 202 solicita una sola vez la exclusion de optimizacion de bateria, muestra el estado Bateria en Ajustes y conserva un boton para corregirlo. Esta capa reduce pausas agresivas del fabricante; no reemplaza Device Admin, Accessibility ni la VPN foreground.
- Validacion fisica in-place de DEV 202 en Samsung SM-S908E: se mostro la explicacion de la app y la confirmacion nativa de Samsung; la exclusion quedo en la whitelist del sistema y Device Admin, Accessibility y VPN continuaron activos.
- DEV 202 mejora las alertas Admin: usa mensajes FCM data-only de prioridad alta, conserva alertas simultaneas con IDs por evento y al tocarlas abre Comunidad -> Aplicaciones. El payload incluye event_id, device_id, device_name y alert_type; la Service Role Key sigue exclusivamente en la Edge Function DEV.
- Firebase DEV usa el proyecto `teacher-7b888` y la app Android `com.contentfilter.admin.dev`. La configuracion publica de Firebase se inyecta en CI mediante `FIREBASE_APPLICATION_ID`, `FIREBASE_API_KEY` y `FIREBASE_PROJECT_ID`; `google-services.json` permanece ignorado y no se guarda en Git.
- El emisor servidor usa la cuenta tecnica dedicada `content-filter-push@teacher-7b888.iam.gserviceaccount.com` con el rol acotado `roles/firebasecloudmessaging.admin`. La credencial privada existe solo como `FCM_SERVICE_ACCOUNT_JSON` en secretos de Supabase DEV; `FCM_PROJECT_ID` tambien es secreto DEV. Ninguna clave privada ni Service Role Key entra en Android.
- El registro del token FCM Admin ya no permite escrituras directas desde Android a `device_push_tokens`. La funcion `register_admin_push_token` identifica el dispositivo Admin exclusivamente por `x-device-token`, valida el token y hace el upsert del lado servidor; `anon` y `authenticated` no conservan permisos directos de INSERT/UPDATE sobre la tabla. Migracion DEV: `20260714122000_secure_admin_push_registration.sql`. No se borraron datos.
- Validacion fisica completa en Samsung SM-S908E con Usuario y Admin DEV 202 instaladas in-place: se abrio la ficha protegida de Content Filter, Accessibility la cerro, Supabase DEV creo el evento, Firebase entrego la alerta urgente `Intento de cambio bloqueado` y al tocarla se abrio Admin -> Apps. El canal `urgent_protection_alerts` quedo con importancia alta y la notificacion fue independiente por event ID.
- DEV 201 resuelve dinamicamente en cada Android el paquete que maneja la desinstalacion de Content Filter y reconoce los controles peligrosos de App Info por IDs estables y etiquetas ES/EN. Esto amplia cobertura OEM de Desinstalar, Desactivar y Forzar detencion sin bloquear la ficha de otras apps.
- Validacion fisica in-place de DEV 201 en Samsung SM-S908E: diez aperturas de App Info y diez intentos de desinstalacion directa terminaron en Launcher; la app continuo instalada, Device Admin y Accessibility activos y el tunel VPN real conectado.
- DEV 200 agrega un watchdog local cada 30 segundos. Contrasta el tunel VPN real con su estado persistido, Accessibility y Administrador del dispositivo; corrige el heartbeat, emite una alerta por componente caido y reinicia la VPN si el permiso sigue vigente y no existe una desactivacion intencional autorizada.
- Validacion fisica in-place de DEV 200 en Samsung SM-S908E, sin borrar datos: version instalada 200, Device Admin y Accessibility conservaron sus permisos y el tunel VPN real siguio conectado despues de un ciclo completo del watchdog, sin crash.
- DEV 199 bloquea las pantallas criticas de detalle de Accessibility y configuracion VPN desde el primer evento de ventana, sin esperar a que Android termine de dibujar el nombre de Content Filter. El permiso temporal de mantenimiento sigue habilitandolas de forma controlada.
- DEV 198 reemplaza la salida directa a Home por una secuencia segura: Atras inmediato, nueva comprobacion, segundo Atras y Home solo como ultimo respaldo si la pantalla protegida continua visible.
- La comprobacion diferida conserva la clase real de la ventana observada para distinguir una pantalla peligrosa de la pantalla anterior normal de Ajustes.
- Validacion fisica in-place en Samsung SM-S908E: diez aperturas rapidas de Administrador del dispositivo y diez intentos directos de desinstalacion volvieron a `Settings/SubSettings`; la app permanecio instalada y Device Admin y Accessibility siguieron activos.
- Tests de `feature-accessibility`, tests Usuario/Admin, ktlint y build de ambas apps OK.

- La barrera funciona en Android normal, sin restablecimiento de fabrica ni Device Owner. Es una defensa por capas: Administrador del dispositivo, Accessibility, VPN, control remoto y recuperacion de emergencia; no promete la imposibilidad absoluta de desinstalar que solo ofrece Device Owner/MDM.
- App Usuario registra `ProtectionDeviceAdminReceiver`, reporta su estado en el heartbeat y conserva las claves locales sensibles cifradas con Android Keystore AES-GCM.
- Con la barrera armada, Accessibility bloquea solo las pantallas de Ajustes que permiten detener, deshabilitar, quitar permisos o desinstalar la propia App Usuario. Ajustes normales siguen disponibles.
- Un intento de manipulacion vuelve a Home, muestra aviso local y crea una alerta remota deduplicada. La App Admin separa estado de conexion, componentes de proteccion y revision aplicada.
- App Admin puede armar/desarmar, permitir Ajustes protegidos por 10 minutos, autorizar desinstalacion por 10 minutos y generar un codigo de recuperacion de un solo uso. Supabase DEV guarda unicamente salt y verificador; el codigo no se persiste ni se documenta.
- La recuperacion offline limita cinco intentos, bloquea nuevos intentos durante 15 minutos y puede cancelarse desde App Usuario. Consumirla incrementa la revision consumida para impedir reutilizacion.
- Supabase DEV incorpora `device_protection_controls` con RLS, revisiones de comando/aplicacion, autorizaciones temporales, recuperacion y provision automatica para nuevos dispositivos Usuario. No se borraron datos.
- `send-protection-alert` fue desplegada solo en el proyecto DEV `syeycayasyufedwoprea`. La Service Role Key permanece exclusivamente del lado servidor y no existe en Android.
- Validacion fisica in-place en Samsung SM-A235M: DEV 192 -> 194 sin borrar datos; token y Room preservados; Device Admin activo y no `testOnly`; VPN y Accessibility activas; heartbeat y version instalada actualizados; armado remoto reconocido; bloqueo de App Info comprobado; Ajustes normales accesibles; alerta de manipulacion deduplicada; permiso remoto de retiro, revocacion y recuperacion offline de un solo uso comprobados. El telefono quedo armado y sin autorizacion local de retiro.
- Room queda en schema 11 con migracion in-place. No desinstalar ni borrar datos para actualizar.

## Cierre 2026-07-13 - Temas sensibles obligatorios

- Grupo de producto: adulto, apuestas, drogas y pirateria/torrents. Internamente `adult` y `mixed_adult` preservan los indices existentes; `adult`/`porn` y `piracy`/`torrent` son aliases y no duplican estadisticas.
- Fuentes: UT1 `adult`, `mixed_adult`, `gambling`, `drogue` y `warez`; The Block List Project `porn`, `gambling`, `drugs`, `piracy` y `torrent`.
- El formato binario 3 usa una tabla extensible de categorias y mantiene lectura compatible de los formatos 1 y 2. Cada coincidencia Bloom exige confirmacion exacta.
- Base firmada DEV `1783983396358`: 5.261.295 entradas, 47.352.139 bytes, checksum verificado y canario incluido. El Samsung SM-A235M adopto esa version sin borrar datos.
- Conteos: `adult` 4.942.201, `mixed_adult` 119, `gambling` 294.192, `drugs` 19.743 y `piracy_torrents` 5.040.
- Tests: 4 pruebas Python del publicador, `:feature-vpn:testDebugUnitTest`, `:core-policy:test`, `:feature-vpn:ktlintCheck`, `:app-user:assembleDevDebug`, `py_compile` y construccion completa con fuentes reales OK.
- App Usuario DEV 192: `https://syeycayasyufedwoprea.supabase.co/storage/v1/object/public/dev-updates/app-user-dev-192-debug.apk`, SHA-256 `bee58c2822e67fd8aad6064048aa1980c9410bda6f1b66fdf4e6bd66f74a09ae`. App Admin permanecio en DEV 181.
- Commits: `d25ccf2` funcional y `dc1f17e` formato. Android CI `29291439108` exitoso. Workflow de base `29291351239` exitoso.
- El workflow general de APKs falla deliberadamente al exigir aumento simultaneo de Admin; Usuario se publico por separado con staging atomico. No se modifico ni publico Admin.
- Validacion fisica final en el SM-A235M: Usuario 192 y base v3 activas, tunel VPN real conectado y policy Admin actualizada a Internet abierto. Google abre correctamente.
- El canario normalizado `www.coca.com` bloquea con `DNS_PROBE_FINISHED_NXDOMAIN` tanto en Chrome normal como incognito, antes de mostrar contenido util. Chrome permanece abierto.
- Validacion adicional en Samsung SM-S908E: inicialmente abria apuestas porque conservaba la base anterior `1783965909719` dentro del intervalo de seis horas entre comprobaciones. No era bypass ni falla del parser. La actualizacion manual instalo `1783983396358`; luego `betway.com` y `betway.es` bloquearon con `DNS_PROBE_FINISHED_NXDOMAIN`, y `betway.com` tambien bloqueo en incognito.
- Limitacion Android confirmada: despues de `force-stop` no hay proteccion hasta reabrir la app; no usar `force-stop` como estado operativo ni inferir VPN activa desde una preferencia persistida. Verificar siempre el tunel real.
- Proxima fase: buscador propio `DAG`, 100 % kosher, sin fotos ni videos y con IA local para intencion. No fue implementado en este cierre.

## Cierre 2026-07-13 - Ticket 2 cobertura complementaria de listas

- La base Web DEV combina UT1 con la categoria `porn` de The Block List Project. `adult` y `porn` se publican como aliases de una unica categoria interna `adult`; no existe categoria ni estadistica paralela.
- The Block List Project publica el repositorio bajo Unlicense / dedicacion al dominio publico y declara compilacion automatizada, validacion y actualizaciones regulares. Las fuentes, licencias y limites quedan documentados en `docs/WEB_DOMAIN_LIST_SOURCES.md`.
- El publicador normaliza y deduplica las dos fuentes. Tambien elimina de `mixed_adult` cualquier dominio ya presente en `adult`, y calcula contribuciones unicas reales por fuente.
- La fuente complementaria incorporo 342.912 entradas que no estaban en las categorias UT1 usadas. La version DEV activa contiene 4.942.201 entradas `adult`, 119 `mixed_adult` y 4.942.320 en total.
- Se conservan excepciones educativas, confirmacion exacta despues del Bloom Filter, archivo y manifiesto firmados, publicacion atomica del puntero y rollback local a la version anterior.
- Se agregaron pruebas del normalizador y de la deduplicacion entre fuentes, aliases y categorias. `python3 -m unittest discover -s scripts/web_domain_list -p 'test_*.py' -v`, `py_compile`, construccion completa real y `git diff --check` OK.
- Workflow `Actualizar base Web DEV` `29286087157` publico la version `1783977933114`. Manifiesto y archivo descargados: firmas ECDSA validas, tamano `44.481.260` bytes y SHA-256 `ef3c7574071d6c57b0eef425a66fdc83a0b09f49125918824cd993fc3af51b71`. El canario DEV permanece incluido.
- Commit funcional: `d22bc4a`.
- No cambio Android: App Usuario permanece en DEV 191 y App Admin en DEV 181; no se compilo ni publico APK.
- Limitacion real: cada version nueva descarga la base completa, ahora de aproximadamente 44,5 MB. No agregar mas fuentes ni aumentar frecuencia sin medir datos/bateria; si el costo resulta alto, el siguiente trabajo de infraestructura debe ser fragmentacion o deltas.

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

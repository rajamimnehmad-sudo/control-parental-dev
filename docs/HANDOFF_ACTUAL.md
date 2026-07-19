# HANDOFF ACTUAL - Content Filter

Fecha de corte: 2026-07-19

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

Version publicada real al 2026-07-19:

```text
App Usuario versionCode 261
App Admin versionCode 261
versionName 1.0.1-dev
```

Manifiestos:

```text
https://syeycayasyufedwoprea.supabase.co/storage/v1/object/public/dev-updates/app-user-dev-manifest.json
https://syeycayasyufedwoprea.supabase.co/storage/v1/object/public/dev-updates/app-admin-dev-manifest.json
```

APKs:

```text
https://syeycayasyufedwoprea.supabase.co/storage/v1/object/public/dev-updates/app-user-dev-261-debug.apk
https://syeycayasyufedwoprea.supabase.co/storage/v1/object/public/dev-updates/app-admin-dev-261-debug.apk
```

SHA-256 publicados:

```text
Usuario 1a62301719851abd82a556ec10f6e254db223455a0963115d65b296dc0b0c523
Admin   4c310e5860ac65e8a01bfad98ed4b28d1f5eff33265bdd1b170e5650ee2bb242
```

## Publicacion DEV 261 - Epic UX Admin, feedback y horarios - 2026-07-19

- App Admin incorpora navegación raíz `Home / Usuarios / Solicitudes / Cuenta`. Home muestra saludo del administrador, comunidad, avisos provenientes de Superweb, licencia separada del estado técnico, protección confirmada/pendiente/degradada, usuarios activos, pendientes y alta de usuario. Cuenta muestra identidad, rol, comunidad, sincronización, efecto de licencia, versión, actualización y cambios.
- Usuarios vuelve a abrir correctamente el detalle —el callback estaba vacío—, conserva header fijo, separa Aplicaciones/Web/Protección, muestra todas las apps sin el recorte literal de diez, elimina el switch para desarmar la protección obligatoria y reemplaza el falso borrado definitivo por archivo. Mantenimiento y desinstalación son autorizaciones separadas de 30 minutos con vencimiento automático.
- Solicitudes separa pendientes accionables de historial resuelto. DAG agrega `Modo Extra Kosher`: fotos de contenido difuminadas, logos/iconos/controles esenciales exceptuados y video/audio/canvas bloqueados. App Usuario y Admin reemplazan el banner visual por una línea de estado liviana bajo el header: punto pequeño y texto, sin fondo, tarjeta ni botón de cierre. El último estado reemplaza al anterior, el progreso usa puntos animados, los cambios entran y salen verticalmente y el texto largo corre horizontalmente. Éxito dura 2 segundos; aviso 2,5; error 3 y luego queda como texto rojo contextual hasta corregir o reintentar. Cuando no hay estado, la línea desaparece sin reservar altura.
- Alineación posterior de App Usuario: Avisos, Ajustes, Solicitudes y Enlace usan la misma línea de estado. Enlace prioriza el aviso de desvinculación sobre el estado ordinario y nunca agrupa dos estados; Solicitudes mantiene el estado visible al desplazar. Home adopta el mismo encabezado oscuro de Admin, a todo el ancho y con sólo las esquinas inferiores redondeadas, sin la tarjeta ilustrada gigante anterior. Mis apps conserva una lista nativa eficiente, fuerza todas las filas al ancho disponible y organiza sus cuatro filtros en una grilla 2 x 2 para evitar recortes.
- `POLICY-SCHEDULES-01` agrega franjas múltiples, selección de días, copia a todos los días, horario global de Apps/Web y excepciones más restrictivas por app o sitio. El fin es exclusivo: una franja `08:00–12:00` bloquea desde las 12:00. Accesibilidad y VPN evalúan siempre `America/Argentina/Buenos_Aires`; alcanzar el límite diario bloquea en el minuto exacto. El límite Web por DNS se etiqueta explícitamente como aproximación y no como tiempo real de lectura.
- Persistencia de horarios: Room 14, columnas nullable de inicio/fin y máscara semanal en `policy_rules`, DTO/outbox/sync actualizados y destinos reservados `__schedule_allowed__:` para que APK anteriores ignoren las reglas nuevas en vez de convertirlas en permisos permanentes. Migración `20260719161852_policy_rule_schedules` aplicada sólo en Supabase DEV `syeycayasyufedwoprea`: 3 columnas, 2 constraints, 1 índice, 0 reglas horarias existentes; no cambió usuarios actuales ni se borraron datos.
- `USER-ARCHIVE-RESTORE-02`: App Admin agrega `Ver usuarios anteriores`, búsqueda por nombre y token nuevo de restauración. Los archivos futuros capturan IDs y banderas originales antes del soft-delete; al consumir el token se reactiva atómicamente el mismo `device_id` con políticas, reglas, horarios, límites, grupos, inventario, membresías y protección originales. Activaciones, push y tokens viejos no reviven; cualquier permiso temporal de mantenimiento/desinstalación se cancela al archivar y al restaurar. Migraciones `protected_user_archive_restore` y `fix_protected_user_restore_qualification` aplicadas sólo en DEV.
- Validación de archivo/restauración: el primer ensayo transaccional detectó una columna ambigua y revirtió íntegramente; la migración correctiva calificó las columnas. El segundo ensayo creó un usuario técnico sólo dentro de una transacción, archivó, generó/consumió token, comprobó identidad, horario, flags habilitados/deshabilitados, límites, grupos, inventario, membresías, protección y auditoría, y terminó con `ROLLBACK`. DEV conserva 2 archivos legados, 0 snapshots nuevos, 0 tokens de restauración y 0 filas técnicas. Esos dos legados se muestran como `Revisión necesaria` y no se modificaron.
- Seguridad de publicación corregida: `:app-admin:assembleDevDebug` dependía erróneamente de `uploadDevUpdatesToStorage`. Ahora los `assemble` son locales y sólo la invocación explícita de `uploadDevUpdatesToStorage` construye/prepara/publica ambas apps. Los `dry-run` validan ambos grafos.
- Incidente controlado durante la detección: antes de corregir el grafo, una validación alcanzó a copiar dos manifiestos y dos APK nuevos a `dev-updates/.staging-bak-20260719-125542/`. Se interrumpió antes de promoción. Con confirmación específica del usuario se eliminaron exclusivamente esos cuatro objetos temporales mediante Storage API; el prefijo quedó vacío. En ese momento los manifiestos y APK públicos permanecieron intactos en DEV 260.
- Validación automatizada final: ktlint de los módulos afectados; tests de dominio, red, sync, política, Accesibilidad, VPN, Admin y Usuario; `assembleDevDebug` de ambas apps sin tareas de publicación. Matriz completa correcta: 873 tareas. No se cambió `versionCode`, no se publicó APK, no se tocó Production y no se agregó ningún secreto.
- Corrección visual posterior sobre el candidato: el detalle de Usuario ya no combina pesos con una fila de ancho ilimitado —causa de los botones partidos letra por letra—. Al abrir muestra un resumen permanente de Protección con VPN, Accesibilidad y Administrador del dispositivo; debajo quedan sólo Aplicaciones y Web, y Grupos vive dentro de Aplicaciones. Cuenta traduce el estado de Superweb y respeta el singular `1 día restante`. App Usuario recuerda `Luego` para no repetir el diálogo de Accesibilidad al cambiar de sección durante la misma sesión.
- Validación final de esta corrección: matriz de tests, ktlint y `assembleDevDebug` de ambas apps correcta, 929 tareas; Android Lint y Detekt terminaron correctamente, 720 tareas, conservando las advertencias históricas. Los APK locales DEV 260 se instalaron in-place en SM-A235M y conservaron `firstInstallTime` y `ceDataInode`. En el teléfono se validaron el detalle Admin sin textos partidos, el nuevo Home Usuario y la línea de estado sin bloque, sin X y sin hueco al desaparecer. La comprobación visual posterior de la lista quedó impedida por el aviso obligatorio `Actualización disponible` del manifiesto DEV; no se cambió `versionCode` ni se publicó para eludirlo.
- Publicación conjunta autorizada: ambos `versionCode` subieron a 261 en el commit `0a022cb`. El workflow `Publicar APKs DEV` `29698758791` publicó una sola vez y exclusivamente en Supabase DEV; Android CI `29698758829` completó build, tests, ktlint, Android Lint y Detekt. Los APK públicos descargados miden 51.569.870 bytes Usuario y 28.278.292 bytes Admin; sus SHA-256 coinciden con los manifiestos, `aapt` confirma paquetes DEV y versión 261, y ambos conservan el certificado esperado `d51bc0dabd280ce1b0f098ae168eb57758faeba301156cde835737835f8a8832`. No se tocó Production ni se borraron datos.
- Pendiente de este epic: prueba física. El flujo exacto para archivos futuros ya está implementado. Por decisión del usuario, los dos archivos legados sin snapshot no requieren recuperación: permanecen aislados como `Revisión necesaria`, sin restauración automática ni modificación de sus datos.

## Candidato local posterior a DEV 261 - Home dinamico de App Usuario - 2026-07-19

- `USER-HOME-UX-03` elimina de Home las tarjetas tecnicas `6 secciones`, ambiente/version y el acceso corporal duplicado a Avisos. Los avisos provenientes de Superweb quedan en una campanita dentro del encabezado, con contador de mensajes disponibles.
- La barra de estado de Android usa el mismo tono oscuro del inicio del encabezado para integrar la zona de camara. El resumen principal dice `Proteccion activa`, `Proteccion por revisar` o `Proteccion incompleta`; al tocarlo se expande en el mismo Home y detalla VPN, Accesibilidad, proteccion contra desinstalacion, sincronizacion y licencia. Cada estado faltante explica el arreglo y ofrece su accion existente. Al tocar nuevamente se cierra.
- Home muestra dinamicamente una actualizacion solo cuando existe una version disponible, una descarga esta en curso o la descarga/verificacion fallo. La accion `Actualizar ahora` reutiliza el flujo seguro existente y la confirmacion normal del instalador Android.
- Home muestra solamente apps y grupos habilitados que consumieron al menos 70 % de su limite diario. La vista compacta superpone iconos como una cola; al tocarla se expande en el mismo Home con minutos usados/restantes y barras de progreso. Solo Proteccion o Tiempos pueden estar abiertos simultaneamente. La seccion no aparece si no hay limites cercanos.
- La busqueda de `Mis apps` se aplica exclusivamente en esa pantalla; el estado compartido conserva el inventario completo para que una busqueda anterior no oculte elementos de la cola del Home. No se agregaron bibliotecas ni dependencias.
- Validacion local: `:app-user:ktlintCheck`, `:app-user:testDevDebugUnitTest`, Android Lint vital y `:app-user:assembleDevDebug` correctos; las pruebas nuevas cubren umbral, prioridad, grupos deshabilitados y tiempo extra activo. El telefono no estaba visible por ADB al terminar, por lo que falta la prueba visual fisica. No se uso Supabase, no se cambio `versionCode`, no se publico APK y Production no fue tocado.

## Publicacion DEV 257 - Calibracion DAG bidireccional - 2026-07-18

- `DAG-CALIBRATION-BIDIRECTIONAL-09` reemplaza el modo temporal de X por `Calibración DEV`, disponible solamente en el flavor DEV de App Usuario y apagado por defecto. Al activarlo, DAG recarga la pagina y entrega los originales ya clasificados sin blur exclusivamente durante esa sesion de prueba; al desactivarlo vuelve a recargar con la proteccion normal.
- Cada imagen raster visible consulta al puente nativo su decision local. Una imagen originalmente permitida muestra `X` para reportar que deberia bloquearse; una imagen que DAG habria difuminado muestra `R` para revisar un posible falso positivo. El reporte exige origen HTTPS, marco principal, URL presente en el mapa efimero y una clasificacion local compatible con la accion.
- El puente de calibracion ya no se registra en Beta ni Production. Ninguna pagina puede habilitar el modo, revelar originales o crear reportes fuera del build DEV. No se agregan secretos ni Service Role a Android.
- Backend y Superweb distinguen `manual_dag` de `manual_dag_false_positive`; el registro incluye `requested_outcome`, y la cola explica si la foto llego por X o R antes de que Super Admin elija la etiqueta y el motivo definitivos. La migracion `20260719022436_dag_bidirectional_dev_calibration` fue aplicada exclusivamente en Supabase DEV y `dag-calibration` v9 quedo activa. No se borro ningun dato.
- Validacion local: unit tests, compilacion, ktlint y builds DEV de Usuario/Admin correctos; Android Lint de Usuario, ESLint, TypeScript y build Next correctos. GitHub `Publicar APKs DEV` run `29670710720` finalizo correctamente en una sola publicacion y Android CI `29670710746` completo build, tests, ktlint, Android Lint y Detekt. La Superweb sirve el commit `eefc2b8` y confirma ambiente DEV y proyecto `syeycayasyufedwoprea`.
- Los manifiestos publicos declaran 257 y los APK descargados coinciden con los SHA-256 documentados arriba. `aapt` confirma paquetes DEV, `versionName 1.0.1-dev`, `minSdk 29` y `targetSdk 36`; ambos conservan el certificado SHA-256 `d51bc0dabd280ce1b0f098ae168eb57758faeba301156cde835737835f8a8832`. Falta la prueba fisica de activacion, originales visibles, X, R y restauracion del blur al apagar el modo.

## Publicacion DEV 258 - entrega directa de marcadores X/R - 2026-07-19

- Reporte fisico en el Samsung personal con 257: `Calibración DEV` recargaba correctamente y revelaba los originales, pero no mostraba X ni R. Esto aislo el fallo al recorrido que pedia cada decision desde JavaScript y esperaba la respuesta nativa antes de crear el marcador; el modo de revelado y la clasificacion local si funcionaban.
- Correccion: al terminar cada clasificacion, el cargador envia directamente URL normalizada y decision al WebView; al finalizar la pagina tambien sincroniza una instantanea acotada de decisiones ya disponibles. La consulta anterior queda como respaldo. X/R siguen requiriendo una candidata local clasificada para que el reporte sea aceptado.
- Validacion local: tests DEV, ktlint y build optimizado de App Usuario correctos, 690 tareas; la validacion final de Admin y ambos APK completo 756 tareas. El candidato 257 se instalo in-place en SM-A235M preservando datos; ese dispositivo tenia DAG deshabilitado por politica y no permitio cerrar el recorrido visual.
- GitHub `Publicar APKs DEV` run `29671696981` finalizo correctamente y publico una sola vez, exclusivamente en Supabase DEV. Android CI run `29671696980` completo build, tests, ktlint, Android Lint y Detekt. Los manifiestos publicos declaran 258 y las descargas coinciden con los SHA-256 documentados arriba. `aapt` confirma paquetes DEV, `versionName 1.0.1-dev`, `minSdk 29` y `targetSdk 36`; ambos APK conservan el certificado SHA-256 `d51bc0dabd280ce1b0f098ae168eb57758faeba301156cde835737835f8a8832`. Pendiente: prueba fisica de X/R en el Samsung personal.

## Publicacion DEV 259 - marcadores sincronizados desde page commit - 2026-07-19

- Reproduccion fisica en SM-A235M con DEV 258: la clasificacion local completaba mas de veinte decisiones y el modo revelaba originales, pero X/R no aparecian. La prueba requirio volver a enlazar el equipo de prueba con un token temporal y habilitar DAG mediante la politica normal de App Admin; no se borro ningun dato.
- Causa confirmada: el callback creado inicialmente por el WebView conservaba el valor `false` de Calibracion DEV. Al activar el modo se recargaba la pagina, pero los marcadores dependian de `onPageFinished` y de ese valor capturado. Sitios pesados como Samsung pueden no finalizar mientras mantienen recursos activos, por lo que las decisiones llegaban pero el motor visual permanecia apagado.
- Correccion: al preparar el viewport desde `onPageCommitVisible`, DAG aplica el estado actual de Calibracion DEV y sincroniza las decisiones ya disponibles; `onPageFinished` conserva el mismo respaldo usando tambien el estado actual. Las decisiones posteriores siguen llegando directamente desde el clasificador.
- Validacion fisica: el APK local 259 y luego exactamente el APK publico 259 se instalaron in-place en SM-A235M preservando datos. X quedo visible sobre las tarjetas Galaxy y el banner en samsung.com; despues de desplazar la pagina los marcadores se reposicionaron y aparecieron sobre contenido nuevo. No se pulso ninguna X y no se creo un pendiente de calibracion artificial.
- Validacion automatica: la matriz local de tests Usuario/Admin, ktlint y ambos APK completo 773 tareas. GitHub `Publicar APKs DEV` run `29672903978` publico una sola vez, exclusivamente en Supabase DEV; Android CI run `29672903983` completo build, tests, ktlint, Android Lint y Detekt. Los manifiestos publicos declaran 259 y las descargas coinciden con los SHA-256 documentados arriba. `aapt` confirma paquetes DEV, `versionName 1.0.1-dev`, `minSdk 29` y `targetSdk 36`; ambos APK conservan el certificado SHA-256 `d51bc0dabd280ce1b0f098ae168eb57758faeba301156cde835737835f8a8832`. Pendiente: confirmacion en el Samsung personal.

## Publicacion DEV 260 - Calibracion DAG de circuito cerrado - 2026-07-19

- `DAG-CALIBRATION-CLOSED-LOOP-10` corrige que una foto etiquetada pudiera volver a recibir la decision anterior. Las versiones de calibracion ahora conservan el conjunto exacto de revisiones que las origino; Android descarga los hashes permitidos/bloqueados y aplica la decision humana a la misma miniatura deterministica, ademas de los umbrales generales. La migracion recupero para la calibracion activa DEV 3 sus 22 casos vigentes: 5 permitidos y 17 bloqueados. No se borro ningun dato.
- El calculo incorpora todos los permitidos al control de falsos positivos —incluidos celulares u objetos— y utiliza cada bloqueado solo para las señales compatibles con su motivo. Reserva un grupo balanceado de validacion y registra sus falsos positivos, falsos negativos y error ponderado. La activacion y el rollback siguen siendo manuales y auditados; esto calibra umbrales, no reentrena pesos ni cambia el modelo embebido.
- Super Admin obliga a decidir primero `Permitir` o `Difuminar`; luego ofrece solamente motivos positivos o negativos, respectivamente, y la base rechaza combinaciones incompatibles. Sin decision el caso queda pendiente. Cada tarjeta agrega una explicacion breve basada en las señales reales y mantiene los puntajes tecnicos dentro del detalle colapsado.
- App Usuario agrega medicion tzniut local y calibrable de mangas por encima del codo y prendas por encima de la rodilla. MoveNet Lightning INT8, embebido y con artefacto fijado por SHA-256, se ejecuta solo con contexto femenino suficiente y combina sus 17 landmarks con piel local; los cruces fuertes se difuminan y los cercanos se envian a revision. Se reemplazo ML Kit Pose porque su unica distribucion Android es embebida y elevaba el APK a unos 89 MB, por encima del limite de Supabase Storage. MoveNet conserva la ejecucion local sin depender de Google Play y deja el APK en 51.520.718 bytes. En SM-A235M, Zara abrio y siguio navegable; la primera inferencia observada midio 90 ms y las siguientes 41-44 ms. No se activo Accessibility durante la prueba.
- La prueba fisica descubrio que la version activa 3 contenia umbrales extremos entre `0.0002` y `0.0058`, con `professional_safe` igual a `professional_block=0.05`; Android la descartaba y esos valores explicaban parte de la excesiva sensibilidad. DEV 260 limita cada familia de señal a un rango seguro en Android, Edge y RPC, conserva el numero/version y evita que futuras muestras pequeñas creen otro candidato degenerado. Tras el ajuste, el menu mostro `Calibración aplicada: #3` y samsung.com mostro sin blur las tarjetas Galaxy S y Z; la pagina proceso mas de veinte imagenes y siguio cargando contenido posterior.
- Backend aplicado solamente a `syeycayasyufedwoprea`: migracion `20260719113929_dag_calibration_closed_loop`, RLS activa sin lectura para anon/authenticated, asesores de seguridad y rendimiento sin errores, y Edge Function `dag-calibration` v11 activa. Una invocacion no autenticada/invalida fallo cerrada con HTTP 400. El primer workflow de publicacion 260 termino antes de subir archivos: Supabase rechazo el APK Usuario de unos 89 MB con HTTP 413, por lo que los manifiestos publicos siguieron intactos en 259. El hotfix MoveNet se publico una sola vez mediante workflow `29687527289`; Android CI `29687527287` completo build, tests, ktlint, Android Lint y Detekt. Los manifiestos y descargas publicos coinciden con los SHA-256 documentados, `aapt` confirma paquetes DEV, version 260, minSdk 29 y targetSdk 36, y ambos APK conservan el certificado SHA-256 `d51bc0dabd280ce1b0f098ae168eb57758faeba301156cde835737835f8a8832`. El APK Usuario publico exacto se reinstalo in-place en SM-A235M y mostro `Calibración aplicada: #3`.

## Publicacion DEV 261 - Politica DAG por audiencia - 2026-07-19

- `DAG-AUDIENCE-POLICY-11` separa contexto de primera infancia, masculino, femenino desde edad escolar, ropa interior y desconocido usando URL de pagina/imagen mas las señales reales `FACE_FEMALE`, `FACE_MALE` y `MALE_BREAST_EXPOSED` del modelo embebido. No afirma estimar una edad facial exacta: `bebe/baby/infant/toddler/newborn` habilita primera infancia; `niña/girl` conserva el criterio femenino y la ausencia de contexto queda cerrada como duda.
- Desnudez explicita, genitales, gluteos cubiertos y categorias de ropa interior siguen siendo sensibles para toda audiencia. En primera infancia se ignoran solamente brazos, vientre, manga y largo de pierna ordinarios. En contexto masculino se permiten torso/pecho y vientre; una salida generica del modelo profesional solo se corrige cuando hay evidencia masculina coherente. Una imagen ambigua continua difuminada.
- Causa corregida: `BELLY_EXPOSED` estaba incluido tambien dentro de las clases explicitas universales, por lo que un abdomen masculino podia bloquearse aunque el resto de la politica exigiera contexto femenino. Ahora solo actua bajo contexto femenino y `MALE_BREAST_EXPOSED` se usa como evidencia positiva, nunca como señal de bloqueo.
- Tests unitarios cubren bebe normal, bebe con ropa interior/desnudez, niña, hombre con torso, hombre con ropa interior y colisiones de palabras en URL. Tests, ktlint y APK Usuario local completaron correctamente. Validacion SM-A235M: H&M Bebe proceso diez imagenes como `youngchild/allowed`; Zara Hombre proceso sus imagenes como `male/allowed`. No se activo Accessibility.
- Estado: publicado junto con el epic UX en DEV 261 mediante el commit y los workflows verificados arriba. La validación física previa en SM-A235M se conserva; falta repetir el recorrido completo con el APK público 261 instalado in-place.

## Recuperacion segura de Super Admin - 2026-07-18

- `SUPERWEB-AUTH-RECOVERY-01` corrige la ausencia total de recuperacion de contraseña: Login incorpora `Olvidé mi contraseña`, solicitud por email, callback PKCE y pantalla para establecer una contraseña nueva de al menos 12 caracteres.
- El pedido devuelve el mismo mensaje exista o no la cuenta para evitar enumeracion. El callback acepta solo el destino fijo de actualizacion, intercambia el codigo con Supabase Auth y exige que la sesion pertenezca a un registro activo de `super_admins`; cualquier otro usuario es desconectado y rechazado.
- Al guardar, la accion vuelve a comprobar usuario y rol, actualiza mediante Supabase Auth —sin manipular hashes ni usar Service Role en cliente—, revoca las sesiones persistentes y obliga a ingresar nuevamente. Enlaces invalidos o vencidos vuelven al Login con un mensaje seguro.
- Causa adicional corregida exclusivamente en Supabase DEV: Auth todavia declaraba `http://localhost:3000` como Site URL y no tenia redirects publicos permitidos. Ahora la Site URL es `https://web-super-admin-nine.vercel.app` y la lista permite ese dominio y localhost para pruebas. Production no fue consultado ni modificado.
- Diagnostico de la cuenta DEV: existe un unico Super Admin activo, con contraseña configurada, email confirmado y sin bloqueo ni borrado. Su ultimo ingreso correcto fue el 2026-07-12; el registro Auth se actualizo el 2026-07-17, pero no existen entradas de auditoria Auth que permitan atribuir ese cambio a una modificacion de contraseña.
- Causa real del acceso fallido al 2026-07-18: Supabase Auth devuelve HTTP 402 tanto para Login como para recovery porque el proyecto DEV esta restringido por `exceed_egress_quota` y `exceed_cached_egress_quota`. El envio de prueba no genero correo. La Superweb ahora distingue esta respuesta de credenciales invalidas; restaurar el servicio requiere una decision del propietario sobre plan/tope de gasto o esperar la renovacion de cuota, no un cambio de codigo ni de contraseña.

## Publicacion DEV 256 - Marcacion visual para Calibracion DAG - 2026-07-17

- `DAG-CALIBRATION-IN-PAGE-08` agrega en el menu de DAG DEV el modo temporal `Marcar fotos inapropiadas`, apagado por defecto y precedido por una confirmacion clara. Al activarlo, cada raster visible ya clasificado por DAG recibe una X roja; tocarla difumina esa imagen inmediatamente y envia solo su miniatura acotada, hash, version y puntajes como reporte manual. Las imagenes sin X no se interpretan como permitidas.
- El puente WebView acepta mensajes solo del marco principal con origen HTTPS y la URL debe existir en el mapa local, acotado y efimero de recursos que DAG ya descargo y clasifico. El modo no existe en Beta ni Production, no expone una interfaz Android general a la pagina y no incorpora Service Role ni secretos al APK.
- Un reporte manual entra como `pending` y `submission_source=manual_dag`; no cambia umbrales ni cuenta como etiqueta revisada hasta que Super Admin elige un motivo y confirma Permitir/Difuminar. La Superweb muestra la insignia `Marcada desde DAG` y el registro de auditoria distingue `manual_image_reported`.
- Backend aplicado exclusivamente en Supabase DEV: migracion `20260717204621_dag_visual_calibration_reports` y Edge Function `dag-calibration` v8. La migracion preservo las 106 filas historicas, ya archivadas por la accion de borrado anterior. Una llamada invalida fue rechazada con HTTP 400.
- Validacion: TypeScript, ESLint y build Next correctos; matriz final Android de Usuario/Admin completo 903 tareas y la repeticion Usuario posterior al ajuste fisico completo 690. En SM-A235M ambas APK se instalaron in-place como 256. La prueba real mostro X sobre imagenes Samsung, difumino un telefono al tocarla y creo exactamente un pendiente manual auditable en DEV. Ese caso de prueba permanece para poder revisarlo desde Super Admin; no se borro ningun dato.
- Android CI run `29613319823` completo correctamente build, tests, ktlint, Android Lint y Detekt. La publicacion automatica `Publicar APKs DEV` run `29613319792` fue correcta y limitada a Supabase DEV. Los manifiestos publicos y las descargas declaran 256 y coinciden con los SHA-256 documentados arriba; `aapt` confirma paquetes DEV, `versionName 1.0.1-dev`, `minSdk 29` y `targetSdk 36`, y ambos APK conservan el certificado SHA-256 `d51bc0dabd280ce1b0f098ae168eb57758faeba301156cde835737835f8a8832`. La Superweb sirve `6b12a60` con salud DEV y rutas privadas verificadas.

## Hotfix Calibracion DAG - borrado autenticado - 2026-07-17

- Reporte: `Borrar todas` respondia `Edge Function returned a non-2xx status code` y no retiraba las fotos.
- Evidencia DEV: los logs de invocacion mostraron llamadas de la Superweb con JWT ES256 valido y `auth_user` correcto, pero Edge devolvia 403. La comprobacion paralela mediante un segundo cliente y `is_super_admin()` perdia el contexto de `auth.uid()` antes de Storage.
- Correccion: Edge valida explicitamente el Bearer JWT con Supabase Auth y comprueba el registro activo de `super_admins` mediante su cliente backend. La Service Role permanece exclusivamente en Edge y no se incorpora a Android ni a la Superweb. Los errores HTTP ahora muestran en la interfaz el motivo devuelto por el backend.
- Verificacion: el usuario de la llamada fallida existe como Super Admin activo; Edge Function `dag-calibration` v7 esta activa; una llamada sin JWT devuelve 401. Los intentos fallidos preservaron 106 revisiones visibles y dejaron cero archivadas, sin borrado parcial. TypeScript, ESLint y build Next correctos. No cambia Android, `versionCode` ni APK.

## Publicacion DEV 255 - Calibracion DAG solo para dudas reales - 2026-07-17

- `DAG-CALIBRATION-QUALITY-06` evita que evidencia visual claramente bloqueable llegue a la cola humana. El clasificador de modestia distingue ahora entre cruce leve del umbral, que sigue como incertidumbre, y evidencia fuerte, que se difumina localmente. La comprobacion se ejecuta tambien cuando el clasificador profesional queda incierto.
- La Edge Function DEV conserva una segunda barrera con los umbrales activos: solo acepta `initial_decision=uncertain` y rechaza casos claramente bloqueables antes de subir la miniatura. No se agregan datos de navegacion ni claves privilegiadas a Android.
- `DAG-CALIBRATION-CLEAR-07` agrega `Borrar todas` a Super Admin con confirmacion explicita. El borrado elimina las miniaturas mediante Storage API, archiva pendientes y revisadas para retirarlas de la cola y conserva un evento de auditoria; calibraciones versionadas ya creadas no se eliminan. Si la misma duda reaparece en una navegacion futura, puede ingresar nuevamente sin quedar bloqueada por la deduplicacion historica.
- Las migraciones `20260717191627_dag_calibration_clear_queue` y `20260717193420_dag_calibration_archive_indexes` se aplicaron exclusivamente en Supabase DEV. La primera preservo las 106 revisiones existentes: cero se archivaron o borraron durante el despliegue. La segunda agrega indices parciales para la cola activa y la nueva clave foranea. El endpoint de borrado rechazo correctamente una llamada no autenticada con HTTP 403.
- Validacion: la matriz local de tests unitarios, ktlint, Android Lint, Detekt y builds DEV de Usuario/Admin completo 903 tareas correctamente. TypeScript, ESLint y build Next tambien pasaron. Android CI `29608399118` completo build, tests, ktlint, lint y Detekt; `Publicar APKs DEV` `29608399103` publico una sola vez exclusivamente en Supabase DEV.
- Los dos manifiestos publicos declaran 255 y las descargas recalculan exactamente los SHA-256 documentados arriba. `aapt` confirma paquetes DEV, `versionName 1.0.1-dev`, `minSdk 29` y `targetSdk 36`; ambos APK conservan el certificado SHA-256 `d51bc0dabd280ce1b0f098ae168eb57758faeba301156cde835737835f8a8832`. Edge Function `dag-calibration` v6 esta activa. La Superweb sirve el commit `b54f7cd` con salud DEV y rutas privadas verificadas. No habia telefono ADB visible para una prueba fisica de 255.

## Publicacion DEV 254 - actualizaciones confiables sin habilitar Ajustes - 2026-07-17

- `UPDATE-TRUSTED-INSTALL-02` corrige el reporte de Samsung donde una actualizacion iniciada desde App Usuario o App Admin era expulsada por la barrera hasta autorizar Ajustes manualmente desde Admin.
- Causa: la entrega Admin -> Usuario dependia de un filtro redundante de permiso sobre el receptor que puede variar entre instalaciones OEM. Ademas, Samsung puede presentar el permiso por origen con una actividad generica `SubSettings`; la autorizacion confiable existente solo reconocia nombres explicitos de fuentes desconocidas.
- Correccion: el broadcast sigue dirigido al componente exacto y el receptor sigue exigiendo el permiso `signature`, pero la entrega ya no filtra adicionalmente al receptor. App Usuario declara tambien el uso del permiso. Accessibility reconoce indicadores especificos de `Instalar apps desconocidas` aun dentro de `SubSettings` y solo los permite durante la ventana confiable de cinco minutos iniciada por las apps Content Filter.
- Seguridad conservada: APK externos y fuentes desconocidas sin ventana confiable siguen bloqueados; VPN, Accessibility, Device Admin, ficha y desinstalacion de App Usuario no se habilitan por esta ventana. Android normal mantiene su confirmacion de instalacion y el permiso por origen cuando aun no fue concedido.
- Validacion: tests Debug/Release de `feature-accessibility`, ktlint de `feature-accessibility` y `core-update`, tests DEV y ktlint de ambas apps, manifiestos fusionados y builds optimizados Usuario/Admin correctos. Android CI `29606038884` completo build, tests, ktlint, lint y Detekt; `Publicar APKs DEV` `29606038832` publico una sola vez exclusivamente en Supabase DEV.
- Usuario y Admin 254 se instalaron in-place por ADB en SM-A235M sin borrar datos. Device Admin continuo registrado y Android concedio el permiso de firma a ambos paquetes. Accessibility estaba desactivado previamente, por lo que la prueba determinante del recorrido protegido queda pendiente en el Samsung personal.
- Los manifiestos publicos declaran 254 y sus hashes coinciden con las descargas. `aapt` confirma paquetes DEV, `versionName 1.0.1-dev`, `minSdk 29` y `targetSdk 36`; ambas APK conservan el certificado SHA-256 `d51bc0dabd280ce1b0f098ae168eb57758faeba301156cde835737835f8a8832`.

## Publicacion DEV 247 - Paridad UX de App Usuario - 2026-07-16

- `USER-UX-PARITY-01` fue aprobado con cuatro destinos principales: Inicio, Mis apps, Internet y Ajustes. Internet permanece separado de Mis apps.
- Cambiar una pestana principal limpia la pila secundaria como en App Admin. Mis apps, Internet y Ajustes dejan de mostrar Volver por ser raices; Solicitudes y Avisos conservan encabezado fijo y regreso interno.
- La iconografia deja de reutilizar conceptos: Mis apps tiene cuadricula, Solicitudes lista de pedidos, Internet globo, Avisos campana, Inicio casa y Ajustes engranaje. `Web` se presenta como `Internet` sin modificar el comportamiento de VPN, Accessibility, DAG o politicas.
- Usuario y Admin suben juntos a `versionCode 247` porque los iconos viven en `core-ui`, compartido por ambas APK. Antes del bump pasaron `core-ui:ktlintCheck`, tests unitarios DEV de ambas apps y builds optimizados: 764 tareas correctas.
- El commit `5f92ee1` fue publicado una sola vez exclusivamente en Supabase DEV por el workflow `29550036969`. Android CI `29550036945` completo build, tests, ktlint, lint y detekt correctamente.
- Ambos manifiestos publicos declaran 247; las descargas recalculan exactamente los SHA-256 registrados arriba. `aapt` confirma paquetes DEV correctos, `versionCode 247` y `minSdk 29`. Queda pendiente el recorrido visual fisico.

## Publicacion DEV 248 - Desinstalacion limitada solo a App Usuario - 2026-07-16

- Reporte grave: con la barrera armada, Samsung impedia desinstalar cualquier aplicacion. El comportamiento requerido es proteger exclusivamente App Usuario; Admin y todas las demas apps deben poder quitarse normalmente.
- Causa: algunos instaladores OEM reutilizan una clase generica `PackageInstallerActivity` para instalar y desinstalar. La politica la trataba como instalacion protegida antes de comprobar que la identidad visible perteneciera a App Usuario.
- Correccion: cualquier accion visible de desinstalar, desactivar o forzar cierre requiere ahora identidad positiva de App Usuario antes de escapar de la pantalla. Las instalaciones de terceros conservan su politica existente y la desactivacion del Device Admin de Usuario conserva su proteccion propia.
- Regresiones: el instalador generico Samsung permite quitar otra app y sigue bloqueando la remocion de Usuario. Tests de `feature-accessibility`, ktlint y builds DEV optimizados de Usuario/Admin correctos: 851 tareas.
- Usuario y Admin subieron juntos a `versionCode 248`. El commit `7e68177` se publico una sola vez exclusivamente en Supabase DEV mediante el workflow `29550738758`.
- Ambos manifiestos publicos declaran 248. Las descargas recalculan exactamente los SHA-256 registrados arriba y `aapt` confirma paquetes DEV correctos, `versionCode 248` y `minSdk 29`. Queda pendiente la prueba fisica de desinstalar una app ajena y Admin sin afectar la proteccion de Usuario.

## Validacion fisica SM-A235M DEV 248 - 2026-07-17

- Usuario y Admin actualizaron in-place de DEV 246 a DEV 248 por ADB, sin borrar datos. Despues de la actualizacion siguieron activos Accessibility, Device Admin y `FilterVpnService`; ambas apps informaron 248 y ultima version.
- La prueba no destructiva de desinstalacion abrio la confirmacion normal de Android para App Admin y WhatsApp y se cancelo sin quitar ninguna. El mismo intento sobre App Usuario fue expulsado al launcher antes de mostrar confirmacion. La ficha Android de Admin permanecio accesible y la ficha de Usuario fue interceptada.
- App Usuario mostro los cuatro destinos raiz sin Volver redundante: Inicio, Mis apps, Internet y Ajustes. Internet permanecio separado, con SafeSearch y DAG activos. Mis apps completo su carga y mostro 156 aplicaciones con iconos y estados; el cero inicial correspondio al estado transitorio `Buscando aplicaciones`.
- App Admin mostro Home, campana superior, tarjetas de Usuarios, Solicitudes, Alertas y Avisos; Ajustes mostro version 248 y Actualizaciones confirmo `Ya tenes la ultima version`. La bandeja de Alertas mostro su header con regreso y el banner premium completo.
- Esta validacion no confirma una desinstalacion efectiva de Admin/otra app porque se evito la accion destructiva. Siguen pendientes SM-S908E, bypass rapido de Accessibility, flujo de actualizacion iniciado dentro de cada app, Play Store/APK con aprobacion, licencia/renovacion y recorridos DAG que consumen Brave o requieren decisiones Admin/push.

## Publicacion DEV 249 - Refuerzo antimanipulacion Usuario - 2026-07-17

- `BARRIER-USER-HARDENING-01` refuerza exclusivamente App Usuario contra desactivacion de Accessibility, VPN, Device Admin y desinstalacion/desactivacion/cierre forzado. Admin y otras apps quedan fuera por la identidad objetivo ya validada en DEV 248.
- Causa: una pantalla protegida abierta por evento de ventana empezaba con `Atras` y reservaba Inicio para el segundo respaldo. Aunque la accion terminaba bloqueada, el control nativo podia quedar visible brevemente y permitir una carrera de toques.
- Correccion: desinstalacion, ficha Usuario, Device Admin, Accessibility y VPN usan Inicio desde el primer evento y repiten la salida cada 100 ms hasta tres veces si Android conserva la ventana. Instalaciones comunes y actualizaciones firmadas/autorizadas no se confunden con remocion.
- Limite honesto: Android normal no permite ocultar de manera garantizada el boton nativo de desinstalacion sin Device Owner, root o tienda administrada. La defensa evita utilizarlo y minimiza su exposicion, sin prometer que ningun OEM lo dibuje durante un cuadro.
- Usuario y Admin subieron juntos a `versionCode 249`. Tests Debug/Release, ktlint y builds optimizados de ambas APK correctos. El commit `06eb5a3` se publico una sola vez exclusivamente en Supabase DEV mediante el workflow `29578135318`.
- Los manifiestos publicos declaran 249; las descargas recalculan exactamente los SHA-256 registrados arriba y `aapt` confirma paquetes DEV correctos y `minSdk 29`.
- Validacion SM-A235M Android 13: ambas apps actualizaron in-place a 249 sin borrar datos; Accessibility, Device Admin y VPN siguieron activos. Admin conservo confirmacion normal de desinstalacion y su ficha; Usuario, su ficha, su detalle Accessibility y la configuracion VPN fueron expulsados al launcher en menos de la primera comprobacion a 300 ms. Tras dejar finalizar los reintentos, las fichas de WhatsApp y Chrome permanecieron disponibles. Pendiente repetir el bypass manual rapido en SM-S908E.

## Publicacion DEV 250 - Ruta VPN Samsung generica - 2026-07-17

- Reporte fisico personal con DEV 249: Ajustes permitia abrir la VPN y mover su switch; el watchdog recuperaba Content Filter al rato. Al iniciar Proton VPN, Android reemplazaba temporalmente Content Filter porque solo admite una VPN activa por usuario, y el watchdog restauraba despues la VPN protegida.
- Causa: algunos recorridos Samsung alojan el detalle VPN dentro de `com.android.settings.SubSettings`, sin `VpnSettings` en la clase. DEV 249 cubria la actividad directa pero no podia clasificar esa variante generica.
- Correccion: una `SubSettings` Android entra a proteccion y salida urgente solo si la ventana identifica positivamente App Usuario. Admin y otras apps/VPN no coinciden y permanecen accesibles. Tests cubren Usuario, otra identidad, Admin y salida inmediata.
- Usuario y Admin subieron juntos a `versionCode 250`. Tests Debug/Release, ktlint y builds optimizados correctos. El commit `655683d` se publico una sola vez exclusivamente en Supabase DEV mediante el workflow `29580417338`; Android CI `29580417480` completo build, tests, ktlint, lint y detekt.
- Los manifiestos publicos declaran 250; las descargas recalculan exactamente los SHA-256 registrados arriba y `aapt` confirma paquetes DEV correctos y `minSdk 29`.
- SM-A235M actualizo in-place a 250 sin borrar datos: la ruta VPN directa siguio expulsada, la ficha Admin permanecio disponible y Accessibility, Device Admin y VPN continuaron activos. La prueba determinante de `SubSettings` queda pendiente en el Samsung personal que reporto el bypass.

## Publicacion DEV 251 - imagenes continuas y decision adaptativa DAG - 2026-07-17

- `DAG-IMAGE-QUEUE-03` corrige el atasco observado despues de las primeras dos o tres fotos. La causa combinaba solo tres interceptores sincronicos con la activacion simultanea de todas las fuentes lazy al terminar la pagina. El candidato usa ocho cupos acotados, timeout de 15 segundos y un `IntersectionObserver` con precarga cercana para incorporar imagenes a medida que se desplaza.
- La compatibilidad incluye `picture`, variantes lazy de `srcset`, favicon ICO y SVG estatico con declaracion XML, simbolos internos y gradientes internos. Scripts, eventos, contenido embebido, estilos activos y referencias SVG externas siguen rechazados.
- `DAG-ADAPTIVE-DECISION-01` deja de convertir automaticamente toda incertidumbre en revision Admin. Una pagina incierta o con señal semantica moderada se muestra bajo las protecciones Web existentes; reglas explicitas, dominios prohibidos y evidencia semantica fuerte conservan bloqueo. Una pagina sin texto tras los reintentos tambien abre protegida, sin aprobacion persistente.
- Validacion local: ktlint, tests DEV y builds optimizados de Usuario/Admin correctos en una matriz de 773 tareas; instalacion in-place previa en SM-A235M correcta, conservando datos. Falta prueba visual manual con una pagina densa, desplazamiento prolongado, SVG/iconos y casos seguros/riesgosos.
- Usuario y Admin subieron juntos a `versionCode 251` en el commit `b2dad47`. `Publicar APKs DEV` `29583348841` publico una sola vez exclusivamente en Supabase DEV y Android CI `29583348861` completo build, tests, ktlint, Android Lint y Detekt.
- Ambos manifiestos publicos declaran 251. Los APK descargados recalculan exactamente los SHA-256 registrados arriba; `aapt` confirma paquetes DEV, `versionName 1.0.1-dev`, `minSdk 29` y `targetSdk 36`. Ambos comparten el certificado SHA-256 `d51bc0dabd280ce1b0f098ae168eb57758faeba301156cde835737835f8a8832`.

## Publicacion DEV 252 - viewport listo y clasificacion visual DAG - 2026-07-17

- `DAG-VIEWPORT-READY-01` impide revelar una pagina hasta completar el analisis textual y resolver las imagenes de la pantalla inicial. DAG precarga las dos pantallas siguientes por proximidad, difiere imagenes lejanas y oculta de forma terminal cualquier recurso visible que no resuelva dentro de ocho segundos.
- La barra de direccion muestra una animacion progresiva sutil mientras analiza. El texto comienza a evaluarse un segundo despues del primer contenido confirmado, con reintentos si el DOM sigue vacio, eliminando la espera fija anterior de cinco segundos.
- El modelo profesional pasa a ser la decision visual principal; el clasificador legado solo resuelve incertidumbre y el detector especifico de modestia sigue evaluando toda imagen permitida. Esto elimina los falsos desenfoques observados en telefonos sin debilitar la proteccion de escotes, ropa interior o trajes de bano.
- Validacion fisica SM-A235M, Android 13: Samsung abre con las fotos iniciales resueltas en aproximadamente 6-8 segundos, diez desplazamientos continuan incorporando imagenes posteriores y las fotos de telefonos dejan de aparecer desenfocadas. La categoria de trajes de bano de H&M conserva desenfoque fuerte. Las metricas locales observadas fueron 0,35-0,6 segundos por imagen luego del calentamiento y no contienen URL, imagen ni texto.
- No se consumieron consultas Brave: todas las pruebas fueron navegaciones directas. Con ambos `versionCode` en 252, la matriz final de tests unitarios DEV, ktlint y builds optimizados de Usuario/Admin completo 773 tareas correctamente.
- El commit `b8637d8` se publico una sola vez exclusivamente en Supabase DEV mediante `Publicar APKs DEV` `29590258165`. Android CI `29590258075` completo build, tests, ktlint, Android Lint y Detekt. Ambos manifiestos declaran 252; las descargas publicas recalculan exactamente los SHA-256 registrados arriba y `aapt` confirma paquetes DEV, `versionName 1.0.1-dev`, `minSdk 29` y `targetSdk 36`. Ambos APK comparten el certificado SHA-256 `d51bc0dabd280ce1b0f098ae168eb57758faeba301156cde835737835f8a8832`.

## Publicacion DEV 253 - Calibracion DAG - 2026-07-17

- Todo el bloque queda identificado como `Calibracion DAG` en Android, Supabase, Super Admin, tickets y documentacion. No es entrenamiento automatico del modelo: ajusta umbrales versionados sobre las respuestas humanas y mantiene separado el registro de modelos para una futura etapa de entrenamiento validado.
- App Usuario conserva la clasificacion en el dispositivo y solo para una decision visual `Incierta` genera una miniatura JPEG de hasta 512 px y 128 KiB. No envia URL, consulta, texto de pagina ni imagen original. La miniatura se autentica con el token del dispositivo, se deduplica por hash y queda en el bucket privado `dag-calibration`; no se usa Service Role en Android. El backend limita cada dispositivo a 100 casos nuevos por 24 horas y 250 pendientes vigentes para evitar saturacion.
- Super Admin incorpora `/dag-calibration`: cola de miniaturas privadas con URL firmada breve, decision `Permitir/Difuminar`, motivo estructurado, nota opcional, puntajes del modelo, historial de acciones, registro del modelo activo y versiones de calibracion candidatas/activas/retiradas.
- `Calibrar DAG` exige al menos 12 respuestas, con tres permitidas y tres bloqueadas. Calcula un candidato sin aplicarlo, pondera un falso negativo tres veces mas que un falso positivo y usa motivos relacionados para no ajustar escotes a partir de falsos positivos de productos. La activacion siempre es manual; reactivar una version anterior constituye la reversion auditable.
- Android consulta la calibracion activa al iniciar DAG, valida los limites recibidos y aplica los umbrales localmente sin descargar ni reemplazar el modelo. La barra de analisis usa etapas reales y el progreso de imagenes visibles, es monotona y expone porcentaje accesible; deja de usar el avance ficticio repetitivo.
- Backend aplicado exclusivamente a Supabase DEV `syeycayasyufedwoprea`: migraciones `20260717175505_dag_calibration`, `20260717181351_dag_calibration_model_scope` y `20260717181541_dag_calibration_service_auth`, Edge Function `dag-calibration` v3 y bucket privado. Las cuatro tablas tienen RLS y niegan lectura directa a `anon` y `authenticated`; la autorizacion de dispositivo tampoco es ejecutable por esos roles y Super Admin opera mediante RPC endurecidas. Los casos vencidos se ocultan de la cola pero no se borran automaticamente.
- Limite honesto: registrar y calibrar no cambia los pesos de la IA. Entrenar o cambiar un modelo requerira un corpus etiquetado suficiente, artefacto firmado, conjunto de validacion, metricas y compatibilidad Android; la interfaz no simula ese proceso antes de que exista.
- Validacion: la matriz local final completo tests Usuario/Admin, ktlint, Android Lint, Detekt y ambos builds; una segunda matriz dirigida repitio tests, formato, Detekt y builds despues de restaurar el fallo cerrado del detector de modestia. TypeScript, ESLint y Next fueron correctos y el build contiene `/dag-calibration`.
- Evidencia fisica SM-A235M: Usuario y Admin 253 se instalaron in-place sin borrar datos; Device Admin y la VPN siguieron activos. Accessibility ya estaba desactivado antes de la comprobacion y no se activo automaticamente. DAG abrio una URL directa sin consumir Brave, obtuvo la configuracion desde `dag-calibration` v3 con HTTP 200 y genero tres casos inciertos reales aceptados con HTTP 202; un dispositivo/token invalido recibio 403.
- Publicacion: commit `bfab78d`; workflow `Publicar APKs DEV` `29604076782` y Android CI `29604076758` correctos. Los manifiestos publicos declaran 253 y los APK recalculan exactamente los SHA-256 de este handoff; `aapt` confirma paquetes DEV, `minSdk 29`, `targetSdk 36` y ambas firmas coinciden con el certificado esperado `d51bc0dabd280ce1b0f098ae168eb57758faeba301156cde835737835f8a8832`.
- Superweb: Vercel publico `bfab78d` en estado `Ready`; health declara DEV y el commit esperado. `/dag-calibration` existe y redirige a Login sin sesion. Queda pendiente solamente recorrer y etiquetar visualmente los tres casos con una sesion Super Admin; no se usaron ni inventaron credenciales.

## Candidato agrupado DEV 246 - Superweb verificable y banners unificados - 2026-07-16

- El usuario aprobo ejecutar juntos `SUPERWEB-DEPLOY-SYNC-01`, `UI-BANNER-UNIFY-01`, `SUPERWEB-FUNCTIONAL-VERIFY-01` y `ANDROID-PHYSICAL-CLOSEOUT-01`, conservando tickets verificables y una sola publicacion final.
- Causa de la ausencia de funciones Super Admin: la fuente y `main` contienen Uso DAG, Alertas, Avisos, DAG premium, tokens auditables y estado de actualizaciones, pero la URL oficial Vercel sirve una compilacion anterior. `/dag-usage`, `/alerts` y `/announcements` devuelven 404 y los commits nuevos no muestran integracion Vercel.
- La Superweb ahora rechaza cualquier `NEXT_PUBLIC_SUPABASE_URL` distinta de DEV `syeycayasyufedwoprea`. `/api/health` devuelve exclusivamente entorno, project ref y commit; `scripts/verify_superweb.sh` comprueba ese contrato y que las rutas privadas existan y redirijan a Login. El Vercel antiguo falla correctamente la verificacion.
- Fuente Web validada con TypeScript, ESLint de `src`, build Next y bundle Sites. El build contiene `/communities`, `/dag-usage`, `/alerts`, `/announcements` y `/api/health`; localmente las rutas privadas redirigen a `/login` y health declara DEV. Las funciones DEV `dag-search` v8, `send-protection-alert` v13 y `send-announcement` v1 estan activas; las RPC sensibles comprobadas rechazan `anon`.
- Pendiente Web externo: el propietario debe iniciar sesion en Vercel para revisar/reconectar el proyecto `web-super-admin-nine`, publicar el commit final y ejecutar la verificacion autenticada de licencias, tokens, DAG premium, actualizaciones, alertas y avisos. Hasta entonces no se declara publicada la Superweb oficial.
- Android unifica el feedback: el `FeedbackBanner` legado delega en `PremiumFeedbackBanner`; Avisos Usuario/Admin, Alertas Admin, Actualizaciones Admin y reseteo Admin dejan de usar texto plano. El banner conserva 42 dp minimos pero crece para que mensajes largos no se recorten.
- Usuario y Admin subieron juntos a `versionCode 246` mediante una unica publicacion DEV. La matriz local termino correctamente con 1.837 tareas: tests de variantes, ktlint, detekt, lint DEV y builds optimizados de ambos APK. El trabajo remoto de publicacion termino correctamente y los APK publicos fueron verificados por version, package name, `minSdk 29`, firma comun y SHA-256 contra sus manifiestos. Al publicar no habia telefono ADB disponible; la validacion fisica parcial realizada despues se registra a continuacion y no sustituye los recorridos que siguen pendientes.
- Validacion fisica posterior SM-A235M, Android 13: Usuario y Admin actualizaron in-place de 245 a 246 sin borrar datos. Antes y despues quedaron activos Accessibility, Device Admin y la VPN foreground. Usuario y Admin muestran 246 y `Ya tenes la ultima version`; el banner premium de Alertas Admin mostro completo `No hay alertas confirmadas.` y la campana superior abrio la bandeja correcta. El header con Atras de Ajustes Usuario permanecio fijo tras desplazar contenido.
- Barrera fisica DEV 246: la ficha de Admin permanecio accesible. Samsung abre `Instalar apps desconocidas` como lista general aun cuando recibe el package de Admin; la lista quedo abierta y mostro `Content Filter Admin` con su control disponible. La ficha Usuario fue interceptada. La portada general de Accessibility puede abrirse, pero la entrada `Aplicaciones instaladas` fue interceptada antes de alcanzar el interruptor del servicio; intentos repetidos conservaron Accessibility habilitado. No se modifico ningun permiso durante la prueba.
- DAG fisico DEV 246 sin consumir Brave: Home integro el area de camara con el fondo tanto en tema claro como oscuro; se restauro `segun dispositivo` al terminar. El campo largo fue desplazable. Desde Historial se abrio Wikipedia Apple; tras desplazar, cargaron varias imagenes seguras, incluidas manzanas y flores, no solo la primera. La calibracion visual sensible, `Mas resultados`, aprobaciones, push y actualizacion desde una version futura siguen pendientes de un recorrido especifico.

## Superweb mobile-first y auditoria funcional - 2026-07-16

- La navegacion Super Admin pasa a ser adaptable: barra inferior fija con cinco destinos y estado activo en celular; navegacion horizontal separada en escritorio; header compacto y controles tactiles de al menos 44 px.
- Uso DAG deja de exigir desplazamiento horizontal en celular: cada dispositivo se presenta como tarjeta con estado, consumo, restantes y fechas. La tabla completa se conserva desde el breakpoint de escritorio.
- La base visual incorpora tipografia de sistema moderna, fondo suave, foco visible y controles redondeados sin cambiar contratos, datos ni permisos.
- Auditoria de funciones: Comunidades cubre alta, licencia, DAG premium, admins/tokens, usuarios y estado de actualizaciones; Uso DAG cubre cupos; Avisos cubre creacion, destinatarios, vencimiento e historial; Alertas cubre lectura reciente y severidad. Faltan busqueda/filtros operativos, ciclo de lectura/archivo de alertas y gestion de avisos vigentes. No existe borrado de alertas y no se agrega: son auditoria retenida y la politica de retencion sigue pendiente.
- Validacion local: TypeScript, ESLint de `src` y build Next correctos. La prueba visual autenticada sigue ligada a `SUPERWEB-FUNCTIONAL-VERIFY-01`; el servidor local falla cerrado sin variables DEV y no se crearon archivos `.env.local` ni se mostraron claves.
- No cambia Android, Supabase ni APK. La publicacion Vercel y su automatizacion siguen pendientes de `SUPERWEB-DEPLOY-SYNC-01`.

## Habilitacion DAG por usuario desde Superweb - 2026-07-16

- La ficha de cada Usuario protegido activado incorpora `Habilitar/Deshabilitar DAG`. Los tokens pendientes muestran el control deshabilitado hasta que exista un dispositivo real.
- El control reutiliza la regla canonica por dispositivo `Domain / __dag_enabled__ / Allow`; no agrega otra fuente de verdad ni requiere cambios Android. App Admin y Superweb observan el mismo estado.
- Habilitar exige simultaneamente licencia efectiva activa y `DAG premium` habilitado para la comunidad. Deshabilitar conserva reglas, historial y consumo para una futura reactivacion.
- La nueva RPC valida rol Super Admin, comunidad, dispositivo Usuario activo y licencia. Cada cambio efectivo incrementa la version de policy y queda registrado sin secretos en `device_policy_audit`; `anon` no ejecuta las RPC y la tabla tiene RLS.
- Migracion `20260717014053_super_admin_set_device_dag_enabled.sql` aplicada exclusivamente en Supabase DEV `syeycayasyufedwoprea`. No se cambio el estado DAG de ninguno de los 2 dispositivos Usuario activos durante la verificacion.
- TypeScript, ESLint de `src`, build Next y `git diff --check` correctos. Pendiente publicar la Superweb y probar el switch con sesion autenticada y autorizacion explicita sobre un usuario de prueba o real.
- No cambia Android, `versionCode` ni APK.

## Automatizacion GitHub -> Vercel confirmada - 2026-07-16

- El proyecto Vercel `web-super-admin` quedo conectado a `rajamimnehmad-sudo/control-parental-dev`, rama de produccion `main` y Root Directory `web-super-admin`.
- Las variables Production publicas fueron corregidas porque existian vacias. La URL apunta exclusivamente a Supabase DEV `syeycayasyufedwoprea` y se configuro una publishable key vigente sin imprimirla ni guardarla en Git.
- El push `685a3b3` inicio automaticamente un deployment Production desde GitHub. Vercel clono exactamente ese commit, compilo Next y actualizo `https://web-super-admin-nine.vercel.app`.
- `scripts/verify_superweb.sh` confirmo health DEV, commit y redireccion anonima a Login en Comunidades, Uso DAG, Alertas y Avisos.
- El workflow `Publicar APKs DEV` quedo `skipped` por `[skip dev publish]`; no se compilo ni publico Android. Este cambio documental posterior sirve como segunda prueba de despliegue automatico sin disparar workflows Android por `paths-ignore`.

## Superweb operativa por usuario - 2026-07-16

- El detalle de comunidad oculta el header global de identidad para que el nombre de la comunidad no quede debajo de otro `sticky`; conserva Volver y la navegacion principal.
- La actualizacion de App Usuario aparece dentro de la tarjeta del Usuario correspondiente. Las actualizaciones de App Admin permanecen en un bloque separado.
- Alertas se agrupan por dispositivo/Usuario: muestran cantidad total, `Intento N veces`, ultimo evento y una marca roja cuando existe desactivacion o degradacion confirmada.
- `Borrar alertas` archiva en conjunto los eventos visibles de ese Usuario. `Borrar aviso` archiva una entrada del historial. No existe `DELETE` fisico: se conservan fecha y actor, y las filas archivadas dejan de aparecer tanto en Superweb como en las bandejas Android.
- Migraciones DEV `20260717020618_superweb_archive_alerts_announcements.sql` y `20260717020938_hide_archived_alerts_from_admin.sql` aplicadas solo en `syeycayasyufedwoprea`. `anon` no ejecuta las acciones de archivo. La verificacion no archivo ninguna alerta ni aviso real.
- Base Web pasa a explicar primero estado, cantidad de sitios, ultima actualizacion y descarga por telefono; fuente, version, categorias y prueba interna quedan en detalles tecnicos desplegables.
- TypeScript, ESLint y build Next correctos. No cambia Android, versionCode ni APK.

## Correccion de identidad Admin DEV 245 - 2026-07-16

- Causa confirmada por el reporte del Samsung SM-S908E: la pantalla de Ajustes mostraba `Content Filter Admin`, pero el detector buscaba la etiqueta generica `Content Filter` y la confundia con App Usuario. La pantalla de permiso `Instalar apps desconocidas` tampoco distinguia para que aplicacion se estaba configurando.
- La identidad Admin se reconoce ahora por sus tres package names y por su etiqueta exacta. Su ficha de aplicacion y su permiso por origen permanecen accesibles sin autorizacion temporal; no se desarma ni se relaja la proteccion de App Usuario, Accessibility, VPN o Device Admin.
- Se agregaron regresiones para ficha Admin, permiso por origen Admin y separacion de etiquetas. App Usuario conserva el bloqueo de su propia ficha y de fuentes desconocidas de terceros.
- Usuario y Admin subieron juntos a `versionCode 245`. La matriz integral paso con 1.011 tareas y ambos APK se instalaron in-place en SM-A235M conservando Accessibility, Device Admin y VPN. La validacion funcional determinante queda pendiente en el SM-S908E personal.

## Correccion de actualizaciones Samsung DEV 244 - 2026-07-16

- Causa: la autorizacion interna se enviaba al paquete Usuario sin componente explicito, la instalacion pendiente no se retomaba al volver de `Permitir desde esta fuente` y la politica no contemplaba todas las clases y paquetes Samsung del instalador y de fuentes externas.
- Usuario y Admin dirigen ahora la autorizacion al receptor exacto protegido por permiso `signature`; Usuario tambien abre localmente la misma ventana oficial. La ventana dura cinco minutos y solo evita que la propia barrera intercepte el flujo de actualizacion iniciado desde Content Filter. Android conserva su permiso por origen y la confirmacion normal; no se promete instalacion silenciosa.
- Al regresar de Ajustes, ambas apps detectan el permiso concedido, recuperan el APK ya verificado y abren automaticamente el instalador. La cobertura incluye los paquetes Samsung Package Installer/Permission Controller y variantes OEM de las pantallas de fuentes desconocidas.
- Usuario y Admin subieron juntos a `versionCode 244`. La matriz integral termino correctamente con 1.011 tareas Gradle: tests unitarios, ktlint, detekt, lint DEV y ambos APK. Los SHA-256 publicos verificados son los indicados arriba.
- Prueba fisica auxiliar SM-A235M: ambos APK se instalaron in-place en 244 sin borrar datos y quedaron activos Accessibility, Device Admin y `FilterVpnService`. El telefono estaba bloqueado, por lo que no se declara prueba visual. La comprobacion determinante del flujo reportado queda pendiente en el Samsung SM-S908E personal.

## Publicacion UX agrupada DEV 243 - 2026-07-16

- App Usuario elimina de Inicio los accesos repetidos a `Mis apps`, `Web` y `Ajustes`, porque ya son destinos permanentes del nav inferior. Inicio conserva Solicitudes y Avisos, que no forman parte de ese nav.
- Las cabeceras con Volver quedan fuera del contenido desplazable mediante el componente comun de Usuario. App Admin ya mantenia fijas sus cabeceras de seccion y reglas; su campanita sube al encabezado principal, junto al saludo, con el contador existente.
- La ficha de comunidad Superweb incorpora una cabecera Volver fija y un bloque `Actualizaciones` que compara cada dispositivo activo con los manifiestos publicos DEV de Usuario/Admin. Es informativo: Android normal conserva el permiso por origen y la confirmacion de instalacion.
- Usuario y Admin subieron juntos a `versionCode 243`. La matriz local termino con 1.013 tareas Gradle: tests unitarios, ktlint, detekt, lint completo y APK optimizados. Superweb paso TypeScript, ESLint, Next y `build:sites`.
- Publicacion unica y atomica exclusivamente en Supabase DEV `syeycayasyufedwoprea`. Los manifiestos publicos declaran 243 y las descargas recalculan exactamente los SHA-256 anteriores.
- Prueba fisica SM-A235M: ambos APK se instalaron in-place en 243 sin borrar datos; Accessibility, Device Admin y los servicios de proteccion/VPN siguieron registrados. El telefono quedo bloqueado durante la comprobacion visual, por lo que no se declara una captura fisica de las pantallas UX.

## Publicacion correctiva DEV 242 - 2026-07-16

- El workflow `Publicar APKs DEV` `29524803991` termino correctamente en 5m32s y publico ambos APK una sola vez exclusivamente en Supabase DEV `syeycayasyufedwoprea`.
- Android CI `29524803097` termino correctamente: build DEV, unit tests, ktlint, lint y detekt.
- Los manifiestos y las descargas publicas coinciden exactamente con los SHA-256 anteriores. `aapt` confirmo version 242, paquetes DEV correctos y `minSdkVersion 29`; `apksigner` confirmo en ambos la firma `d51bc0dabd280ce1b0f098ae168eb57758faeba301156cde835737835f8a8832`.
- DEV 242 reemplazo a 241. El bootstrap Admin fue completado despues en el SM-S908E personal: Usuario descargo el APK oficial, Android confirmo la instalacion y Admin quedo instalado. La autorizacion temporal se cerro y DEV confirmo la proteccion rearmada con revision aplicada 5.

## Publicacion agrupada DEV 241 - 2026-07-16

- El workflow `Publicar APKs DEV` `29521944617` termino correctamente y publico una sola vez ambos APK exclusivamente en Supabase DEV `syeycayasyufedwoprea`.
- Los dos manifiestos publicos declaran `versionCode 241` y `versionName 1.0.1-dev`. Las descargas publicas volvieron a calcular exactamente los SHA-256 anteriores; `aapt` confirmo paquetes `com.contentfilter.user.dev` y `com.contentfilter.admin.dev` con `minSdkVersion 29`.
- `apksigner` confirmo en ambos APK el certificado SHA-256 `d51bc0dabd280ce1b0f098ae168eb57758faeba301156cde835737835f8a8832`.
- El primer Android CI de cierre `29521944840` completo build y tests pero encontro una unica deuda de formato en `EncryptedProtectionStateStore`. El commit correctivo `d86c465` pasa `ktlintCheck` y `detekt` locales y usa `[skip dev publish]`; el workflow `29523143482` quedo saltado, por lo que no existio una segunda publicacion.
- Superweb compila correctamente con TypeScript, ESLint de `src`, Next y el bundle Sites. La URL oficial Vercel seguia respondiendo HTTP 404 para `/announcements`, y el repositorio remoto de Sites rechazo repetidamente el push con HTTP 500. La fuente y el backend DEV estan listos, pero la publicacion Superweb queda bloqueada por hosting externo; no se la declara publicada.

## Implementacion 2026-07-16 - candidato DEV 241 SUPERADMIN-MSG-01

- Super Admin dispone de `/announcements` para guardar avisos por comunidad destinados a App Usuario, App Admin o ambas. Admite vencimiento opcional e historial y es deliberadamente unidireccional: no hay chat, respuestas ni recibos de lectura.
- App Usuario y App Admin incorporan una bandeja `Avisos`, refresco manual y apertura directa desde una notificacion normal. Las alertas de seguridad conservan su canal urgente separado.
- Android 13 o posterior solicita permiso de notificaciones. Rechazarlo impide el push pero no la consulta de la bandeja. El minimo del proyecto sigue siendo Android 10/API 29, compatible con la mayoria de telefonos Android actuales sin Device Owner, root ni funciones exclusivas de Samsung.
- `list_device_announcements` filtra por comunidad y rol usando el token del propio dispositivo. `register_device_push_token` registra Usuario o Admin sin exponer escritura directa a la tabla. No existe Service Role Key ni credencial privada FCM dentro de Android.
- `send-announcement` se desplego solo en Supabase DEV `syeycayasyufedwoprea`; usa credenciales servidor para seleccionar tokens y enviar datos FCM de prioridad normal. Si el push falla, el aviso ya guardado permanece disponible en la bandeja.
- Migraciones aplicadas solo en DEV: `20260716162000_super_admin_announcements.sql`, `20260716162500_device_push_registration_all_roles.sql` y `20260716181500_fix_super_admin_announcement_authorization.sql`. La prueba real detecto que las RPC llamaban a un helper inexistente; la tercera migracion agrega el alias endurecido a `is_super_admin`. `anon` no lo ejecuta y `authenticated` sigue sujeto al rol Super Admin.
- Validacion: tests unitarios, ktlint y builds DEV optimizados de ambos APK correctos; Superweb paso TypeScript, ESLint de `src` y build optimizado. El lint global de Superweb sigue incluyendo deuda ajena en `.open-next` generado.
- Prueba fisica SM-A235M: un aviso temporal para ambos roles fue creado por la RPC y aparecio en las bandejas Admin y Usuario al actualizar. La accion de apertura directa navega a Avisos. Falta confirmar el push FCM desde una sesion Superweb utilizable; el navegador integrado solo expuso pestañas vacias y no se extrajeron credenciales. Android DEV 241 quedo publicado en el cierre agrupado; Superweb sigue pendiente por el bloqueo de hosting documentado arriba.

## Correccion 2026-07-16 - DEV 242 acceso Usuario e instalacion Admin

- El reporte corresponde al Samsung SM-S908E personal: con la barrera armada, DEV 241 ocultaba el launcher de App Usuario y bloqueaba correctamente el instalador abierto desde el navegador. Como Admin aun no estaba instalado, no existia una ruta practica para autorizar mantenimiento y se producia un circulo cerrado.
- DEV 242 mantiene siempre visible el icono de App Usuario. Se revierte la estrategia visual de `BARRIER-LAUNCHER-01`; la proteccion posterior de desinstalacion mediante Accessibility y Device Admin permanece, sin prometer capacidad MDM en Android normal.
- Ajustes de App Usuario agrega `App Administrador`: consulta el manifiesto Admin oficial, descarga el APK, verifica SHA-256, packageName exacto de la variante y que la firma sea la misma que App Usuario. Solo entonces abre la ventana interna firmada de dos minutos; Android conserva el permiso por origen y la confirmacion normal de instalacion.
- Una descarga o instalacion iniciada directamente desde Chrome sigue bloqueada deliberadamente. No se habilitan APK arbitrarios, no se incorpora ningun secreto y no se usa Service Role en Android.
- Usuario y Admin subieron juntos a `versionCode 242` y fueron publicados. Tests de confianza para paquete/firma, tests Usuario, ktlint de Usuario/core-update, lint y builds DEV de ambos APK correctos. Prueba fisica en SM-A235M: actualizacion in-place sin borrar datos, ambos paquetes en 242, alias Usuario restaurado, MainActivity abre y la tarjeta Admin informa correctamente que la version instalada ya esta actualizada. El bootstrap real se completo luego en el SM-S908E y la proteccion quedo rearmada.

## Implementacion 2026-07-16 - candidato DEV 241 DATA-DELETE-01

- Causa raiz confirmada sin exponer identidades: App Admin llamaba `revoke_device`, RPC legado que compara `accounts.owner_user_id` con la sesion. Los 2 Usuarios activos de DEV estan correctamente vinculados a `community_admins.auth_user_id`, pero ambos difieren del propietario legado; por eso el flujo fallaba antes de modificar filas.
- App Admin llama ahora `admin_archive_protected_user`. La RPC exige sesion autenticada, resuelve un administrador de comunidad activo y solo acepta un dispositivo Usuario activo de la misma comunidad.
- La transaccion revoca activacion y token del dispositivo, marca como eliminados apps, solicitudes, grupos, membresias, reglas, limites, politicas, codigos y token push, y guarda un recibo en `protected_user_deletion_audit`. Alertas de seguridad, consumo agregado y auditoria se conservan; no se hace `DELETE` fisico de datos ni de la cuenta.
- La confirmacion de App Admin explica el alcance y la retencion de auditoria. Solo despues del exito remoto limpia la copia local y sincroniza la lista.
- Migraciones `20260716174500_admin_archive_protected_user.sql` y `20260716175000_admin_archive_protected_user_privileges.sql` aplicadas solo en DEV. `anon` no ejecuta la RPC; `authenticated` conserva el permiso sujeto a las validaciones internas.
- El usuario autorizo especificamente crear y archivar un unico usuario nuevo de prueba. El primer intento con una plataforma invalida revirtio la transaccion y dejo 0 cuentas de prueba. El segundo termino con 0 dispositivos de prueba activos, 1 archivado, activacion revocada, 1 recibo y 2 filas afectadas. Los 2 Usuarios reales permanecieron activos e intactos.
- DATA-DELETE-01 queda resuelto candidato DEV 241. Tests de red/Admin, ktlint y build DEV optimizado correctos.

## Validacion fisica 2026-07-16 - candidato DEV 241 en SM-A235M

- Actualizacion in-place desde Usuario 215/Admin 213 a 241 sin desinstalar ni limpiar datos. Ambas versiones quedaron en 241; VPN, Accessibility, Device Admin, activacion y control reforzado siguieron activos.
- BARRIER-A11Y-RACE-01: veinte recorridos rapidos a la pantalla protegida `Aplicaciones instaladas`, con segundo toque inmediato, conservaron Accessibility 20/20 y expulsaron la pantalla 20/20. La pagina principal de Accesibilidad queda disponible deliberadamente. Falta repetir en el SM-S908E donde se reporto el bypass original.
- USAGE-REAL-01: Calculadora foreground genero checkpoints/sesiones y avanzo a 2 minutos persistidos sin permiso Usage Stats. `Mis apps` conservo 156 filas y el filtro devolvio una Calculadora.
- OPS-METRICS-01: se elimino I/O diagnostico repetido cada 250 ms sin cambiar la frecuencia de enforcement. La repeticion paso de unas 40 escrituras cada 10 s a 2 por cambio significativo. `Mis apps`, diez desplazamientos: 245 cuadros, 2,04 % lentos, p50 17 ms, p90/p95 20 ms y p99 30 ms. Falta muestra de bateria/red de 24 h.
- UI-POLISH-01: Home Usuario muestra 6 secciones; saludo, Admin, Alertas, Avisos y DAG no presentaron recortes en 384 dp/Android 13. El lint completo previo de ambos APK no tuvo errores de accesibilidad.

## Implementacion 2026-07-16 - candidato DEV 241 SEC-LICENSE-01A

- Primer tramo del ciclo de licencia: Super Admin ya no confunde el estado manual almacenado con el estado efectivo. Una licencia activa con inicio futuro aparece `Programada`; al alcanzar `expires_at` aparece `Vencida`; una suspension manual conserva prioridad.
- La migracion `20260716151923_effective_community_license_status.sql` agrega una funcion pura de estado efectivo, hace que listado y detalle usen la misma decision y rechaza periodos cuya fecha de vencimiento no sea posterior al inicio. No actualiza licencias existentes, no borra comunidades, dispositivos, reglas ni configuracion.
- Renovar consiste en volver a `Activa` con un periodo valido; la fila existente se actualiza y toda la configuracion asociada se conserva. La UI muestra `Programada` sin intentar guardar ese estado derivado como si fuera un estado manual.
- Validacion: Supabase DEV `syeycayasyufedwoprea` devolvio correctamente los cuatro casos activo/programado/vencido/suspendido; la funcion auxiliar no es ejecutable por `anon`, mientras las RPC de Super Admin conservan acceso autenticado y su comprobacion interna de rol. `eslint src`, TypeScript y build optimizado de Superweb correctos. El lint global conserva deuda porque incluye artefactos generados `.open-next`; no corresponde al codigo fuente de este ticket.
- Pendiente de `SEC-LICENSE-01`: propagar el estado efectivo a dispositivos Usuario/Admin ya activados, aplicar el comportamiento offline y completar la restauracion automatica al renovar. Este tramo no publica APK ni Superweb.

## Implementacion 2026-07-16 - candidato DEV 241 SEC-LICENSE-01B

- El ciclo efectivo de licencia ya llega a los dispositivos activados mediante `get_device_license_entitlement`, autenticada con el token del propio dispositivo. Android no incorpora Service Role y la RPC no permite consultar otro dispositivo.
- Usuario y Admin guardan inicio, vencimiento y ultima verificacion en Room 12. Con esos datos recalculan localmente `Programada`, `Activa`, `Vencida` o `Suspendida`: el vencimiento se aplica tambien sin conexion y un fallo de red conserva el ultimo derecho conocido en vez de inventar una licencia activa.
- Solo `Activa`, `Por vencer` y `Periodo de gracia` habilitan proteccion. Una licencia programada, vencida o suspendida desactiva el enforcement sin borrar reglas, controles ni configuracion; una renovacion valida los restaura en la siguiente sincronizacion.
- Se elimino el atajo que convertia cualquier activacion preexistente en licencia activa. VPN comprueba la licencia antes de reglas explicitas, Accessibility respeta el mismo criterio y el monitor de salud actualiza el estado temporal en cada comprobacion.
- La migracion `20260716152914_device_license_entitlement.sql` fue aplicada exclusivamente en Supabase DEV `syeycayasyufedwoprea`. Se verificaron `SECURITY DEFINER`, revocacion a `PUBLIC` y acceso deliberado de `anon/authenticated` protegido por `device_token_matches_device`; no modifica ni borra filas.
- Validacion local correcta: tests de dominio, sincronizacion, Accessibility, VPN, Usuario y Admin; `ktlintCheck`; builds DEV optimizados de ambos APK, siempre excluyendo las tareas de publicacion intermedia. El cierre agrupado DEV 241 ya esta publicado. Falta prueba fisica de vencimiento/renovacion en Samsung SM-S908E.

## Implementacion 2026-07-16 - candidato DEV 241 SUPERADMIN-DAG-ENTITLEMENT-01

- DAG tiene ahora dos niveles independientes: el derecho premium de la licencia/comunidad en Super Admin y el permiso por dispositivo en App Admin. Una regla antigua `__dag_enabled__` no basta si falta el derecho superior.
- Superweb muestra el estado `DAG premium` en el detalle de la comunidad y permite habilitarlo o deshabilitarlo sin borrar reglas, historial ni configuracion. Cada cambio efectivo se registra en `community_license_audit` con actor, valor anterior, valor nuevo y fecha; la lectura directa queda restringida a Super Admin por RLS.
- App Admin deshabilita el switch y explica que DAG no esta incluido. App Usuario oculta la entrada y el launcher independiente; una sesion DAG abierta observa el estado local y se cierra cuando deja de estar habilitada.
- El derecho se entrega dentro de la misma RPC autenticada por token de dispositivo y se conserva en Room 13 para reinicios y periodos offline. Al renovar o reactivar vuelve en la siguiente sincronizacion sin reinstalar ni reconstruir reglas.
- `dag_search_authorized` exige simultaneamente licencia efectiva activa, `dag_entitled=true`, dispositivo Usuario valido y regla DAG activa antes de consumir cuota o llamar a Brave. Asi una sesion obsoleta tampoco elude la decision del servidor.
- Migraciones `20260716154242_dag_community_entitlement.sql` y `20260716154403_dag_entitlement_audit_read_policy.sql` aplicadas solo en Supabase DEV `syeycayasyufedwoprea`. La unica licencia existente conservo DAG habilitado; las nuevas licencias comienzan deshabilitadas. No se borro ni reinicio consumo.
- Validacion: tests Usuario/Admin/dominio y formato correctos; Superweb paso ESLint de `src`, TypeScript y build optimizado; funciones y privilegios verificados en DEV. Los advisors conservan deuda previa de RPC anon autenticadas por token y politicas multiples. Android quedo publicado en el cierre agrupado DEV 241; Superweb permanece bloqueada por hosting externo.

## Implementacion 2026-07-16 - candidato DEV 241 REQUESTS-UX-01

- App Usuario deja de ocultar solicitudes resueltas: muestra el historial local reciente de apps y tiempo extra, ordenado de mas nuevo a mas antiguo, con estados pendiente, aprobada, rechazada o expirada y las concesiones de tiempo asociadas.
- La pantalla incorpora `Actualizar solicitudes`, estado de carga y resultado explicito online/offline. El refresco sincroniza primero la salida pendiente y despues trae resultados; sin red conserva y muestra los datos guardados.
- Antes de crear, se rechaza otra solicitud pendiente del mismo tipo y destino. Esto evita duplicados por pulsaciones repetidas o por reintentar antes de la sincronizacion, sin borrar solicitudes anteriores.
- Las solicitudes de dominio DAG siguen en su bandeja especifica para no mezclar historial de navegacion con permisos de apps. App Admin ya tenia refresco manual y acciones con progreso; conserva su bandeja operativa de pendientes.
- Validacion local: formato, compilacion y tests de `feature-requests` y App Usuario correctos. Sin migracion ni publicacion intermedia.

## Implementacion 2026-07-16 - candidato DEV 241 USER-RESILIENCE-01

- Ajustes de App Usuario ofrece una accion directa para cada componente recuperable: abrir Accesibilidad, pedir/encender la VPN, activar Device Admin, quitar restricciones de bateria y volver a comprobar la barrera. Ya no agrupa fallos distintos bajo un unico boton ambiguo.
- Los estados de licencia programada, pendiente, vencida o suspendida explican la causa y, en el vencimiento, que la configuracion se conserva hasta renovar. No se invita al usuario a reparar localmente una decision que depende de Super Admin.
- La instalacion de actualizaciones conserva el comportamiento real de Android normal: App Usuario comprueba y descarga, pero Android solicita permiso y confirmacion para instalar; no se promete actualizacion silenciosa.
- Validacion local: formato, compilacion y tests DEV de App Usuario correctos. Falta recorrer las acciones en Samsung SM-S908E; no hubo publicacion intermedia.

## Implementacion 2026-07-16 - candidato DEV 241 SUPERADMIN-TOKEN-01

- El token de activacion Admin se entrega una sola vez y queda solo en memoria de la pestaña actual. Superweb ya no lo persiste en `localStorage` ni en cookies; al ocultarlo, recargar o cerrar la pestaña no puede recuperarlo. Supabase conserva exclusivamente el hash bcrypt preexistente.
- Hotfix Superweb 2026-07-18: el formulario conserva su referencia antes de esperar la respuesta HTTP. Esto evita que React invalide `event.currentTarget` y corte el flujo antes de mostrar el token recien generado. El token sigue siendo efimero, no se registra ni se persiste, y no se creo ningun token real durante la validacion.
- La ruta de generacion acepta solo la solicitud JSON autenticada de la interfaz; se elimino el fallback de formulario que guardaba el secreto en una cookie para poder mostrarlo despues.
- Super Admin puede revocar un token Admin pendiente sin borrar el administrador. La RPC valida comunidad y admin, solo invalida codigos Admin sin usar y no afecta dispositivos ya activados.
- La tabla privada `activation_code_audit` registra generacion, uso y revocacion con ID de codigo, rol, vencimiento, actor cuando existe y fecha; nunca guarda el codigo ni su hash. RLS permite lectura solo a Super Admin.
- Migracion `20260716155020_admin_token_audit_and_revocation.sql` aplicada exclusivamente en Supabase DEV. Se verificaron trigger, `SECURITY DEFINER`, revocacion a `PUBLIC/anon` y acceso autenticado solo para la RPC de Super Admin.
- Validacion: Superweb paso ESLint de `src`, TypeScript y build optimizado. No se genero ni revoco ningun token real durante la prueba y no hubo publicacion intermedia.

## Implementacion 2026-07-16 - candidato DEV 241 ALERT-ROUTING-01 / SUPERADMIN-ALERTS-01 / ADMIN-ALERTS-UX-01

- La politica queda diferenciada por resultado: `tamper_attempt` representa un intento que la barrera bloqueo y se conserva solo en la bandeja Super Admin; `web_disabled`, `apps_disabled`, `admin_disabled` e `incomplete` representan degradacion efectiva y permanecen visibles tanto para Super Admin como para App Admin. Los pedidos de mantenimiento tambien llegan a Admin.
- La Edge Function DEV `send-protection-alert` version 13 deja de enviar FCM a Admin para intentos bloqueados, pero primero crea/deduplica el evento para Super Admin. Las desactivaciones confirmadas conservan push urgente y se abren directamente en la nueva bandeja Admin.
- Superweb agrega `/alerts`, con hasta 200 eventos recientes, comunidad, dispositivo, fecha y distincion `Bloqueado/Confirmado`. La RPC `super_admin_list_protection_alerts` exige rol Super Admin y no expone consultas, URLs ni historial.
- App Admin incorpora campanita con contador de eventos disponibles, tarjeta de acceso separada de Solicitudes, bandeja con refresco manual y mensaje explicito de alcance. Su RLS excluye `tamper_attempt` aunque exista una sesion anterior o se consulte REST directamente con token Admin.
- La deduplicacion existente de cinco minutos por dispositivo/tipo se conserva, por lo que watchdog, reintentos y FCM no crean otro incidente dentro de esa ventana. No se borro ni modifico ningun evento historico.
- Migracion `20260716155413_protection_alert_routing_and_super_admin.sql` aplicada solo en Supabase DEV. Se verificaron la policy filtrada y Edge Function activa con SHA-256 `49734e6bc61cd981e8923bb2fcf94bfc4095f09a940c8911f2707104cc8e824b`.

## Implementacion 2026-07-16 - candidato DEV 241 USER-GREETING-01

- El encabezado del Home de App Usuario muestra `Hola, {nombre}` usando exclusivamente el `displayName` del dispositivo que coincide con la activacion local vigente. No elige por orden ni usa el nombre de otro dispositivo como fallback.
- Mientras la activacion, la fila sincronizada o un nombre no vacio no esten disponibles muestra el fallback neutro `Hola`. Los cambios de nombre se reflejan al actualizarse Room por el flujo normal de sincronizacion.
- El nombre solo aparece dentro del Home: no se copia a notificaciones ni pantalla bloqueada y no se agrega persistencia nueva. Los espacios externos se normalizan y se conservan caracteres especiales y el nombre completo; el encabezado puede ocupar mas de una linea.
- Validacion: tests unitarios de nombre activo, aislamiento entre dispositivos y fallbacks; compilacion DEV y `ktlintCheck` de App Usuario correctos. Falta prueba visual fisica y no hubo publicacion intermedia.

## Implementacion 2026-07-16 - candidato DEV 241 DAG-TABS-UX-02

- El selector DAG ya no usa peces: una pestaña sin miniatura muestra una superficie neutra rotulada `DAG`. La pantalla se identifica como `Pestañas recientes` y ordena las pestañas abiertas por ultimo uso.
- La fecha de ultimo uso se persiste con la sesion cifrada. Las sesiones de versiones anteriores, que no tienen ese campo, reciben un orden determinista al decodificarse; no se guardan ni reabren pestañas cerradas.
- `Nueva pestaña` reutiliza primero la pestaña vacia mas recientemente usada. Vacia significa estrictamente Home sin texto, consulta, resultados ni URL; una pagina o listado nunca se reemplaza por error. Si no hay vacia crea una nueva hasta el limite existente de ocho.
- Abrir el selector, reordenar visualmente o enfocar una pestaña no vuelve a buscar ni consume Brave. Se conservan aislamiento, resultados, restauracion cifrada y revalidacion de paginas.
- Validacion: tests del codec, persistencia de ultimo uso y definicion de pestaña vacia; compilacion DEV y `ktlintCheck` de App Usuario correctos. Falta prueba fisica y no hubo publicacion intermedia.

## Implementacion 2026-07-16 - candidato DEV 241 BARRIER-LAUNCHER-01

- App Usuario usa un `activity-alias` como unica entrada Launcher. El alias se oculta de forma reversible, sin detener la app ni sus servicios, solamente cuando coinciden activacion vigente, barrera armada, licencia que permite proteccion y VPN activa.
- Si la proteccion se degrada, se desarma o recibe autorizacion temporal de Ajustes/retiro, el icono reaparece. El vencimiento de la ventana vuelve a aplicar la decision automaticamente; el estado cifrado expone cambios reactivos sin polling constante.
- La notificacion foreground de VPN incorpora una accion interna del paquete que abre `MainActivity` aun cuando el alias Launcher esta oculto. Esto mantiene una ruta clara de acceso y recuperacion.
- El alcance elegido prioriza Android normal y la mayoria de fabricantes, sin Knox, root, Device Owner ni restablecimiento. No equivale al bloqueo de desinstalacion garantizado por MDM y debe comprobarse por launcher/OEM.
- Validacion: tests de la politica de visibilidad, core-security y App Usuario; manifiesto combinado, compilacion y formato Usuario/VPN correctos. Falta prueba fisica en Samsung SM-S908E y no hubo publicacion intermedia.
- Revision posterior: el reporte real del SM-S908E demostro que la ruta alternativa no era suficientemente visible y bloqueaba el bootstrap de Admin. DEV 242 conserva el icono Usuario permanentemente y agrega el instalador verificado de Admin descrito arriba.

## Implementacion 2026-07-16 - candidato DEV 241 APP-INSTALL-APPROVAL-01

- El alcance elegido es Android normal best-effort para la mayoria de fabricantes, sin Knox, Family Link, root, Device Owner ni restablecimiento. Play Store permanece visible para explorar.
- App Usuario establece una base local de paquetes existentes. Un `PACKAGE_ADDED` nuevo, no sistemico y que no sea Content Filter queda en cuarentena, se publica en el inventario normal y crea una sola solicitud `APP_ACCESS` con packageName, nombre, usuario y dispositivo; no incluye busquedas de Play Store.
- Accessibility envia al Home cualquier app en cuarentena. Solo una regla Allow explicita, habilitada y para el packageName exacto la libera; rechazar o no responder conserva la cuarentena. Actualizaciones de paquetes existentes no se confunden con instalaciones nuevas y una reinstalacion hereda exclusivamente una aprobacion Allow que siga vigente.
- Package Installer y las pantallas de fuentes desconocidas quedan protegidas mientras la barrera esta armada. DAG ya bloquea descargas. Canales externos, adjuntos, gestores y ADB tienen una ultima defensa comun: si materialmente logran instalar un paquete nuevo, este no puede abrirse sin aprobacion mientras Accessibility este activa.
- Para no bloquear las actualizaciones propias, Usuario y Admin solicitan una ventana interna de dos minutos mediante un broadcast dirigido protegido por permiso Android `signature`; luego Android sigue exigiendo permiso y confirmacion normal. Ninguna clave ni Service Role se incorpora a Android.
- Limite explicito: sin administracion empresarial no se garantiza evitar toda instalacion en todos los OEM; se garantiza el flujo implementado y la cuarentena observable, sujeto a Accessibility. La modalidad Device Owner queda fuera de este ticket.
- Validacion: tests de paquete nuevo/excluido, Allow exacto, instalador, fuentes desconocidas y ventana firmada; core-security, Accessibility y Usuario pasan tests/formato; Usuario y Admin compilan y los manifiestos combinados contienen receivers y permiso correctos. Falta prueba fisica integral y no hubo publicacion intermedia.
- Validacion local: tests/compilacion Admin, formato de Admin y red, ESLint de `src`, TypeScript y build Superweb correctos. Falta prueba fisica de los dos recorridos y publicacion final de APK/Superweb.

## Implementacion 2026-07-16 - DEV 241 BARRIER-DEFAULT-ON-01

- Un dispositivo Usuario con control de proteccion nunca configurado se arma automaticamente solo cuando App Usuario comprueba simultaneamente el tunel VPN real, Accessibility habilitado, Device Admin activo y ausencia de una desactivacion intencional de la VPN.
- La migracion conservadora usa `command_revision = 0` como señal de control nunca decidido. Un control ya armado o desarmado explicitamente por Admin no se modifica automaticamente; desarmar sigue siendo una accion administrativa excepcional y auditable.
- La RPC `auto_arm_device_protection` se autentica mediante el token del propio dispositivo, no acepta otro dispositivo y solo cambia `armed=false/revision=0` a `armed=true/revision=1`. No usa Service Role en Android, no borra datos y no altera autorizaciones vigentes decididas por Admin.
- La migracion `20260716150738_auto_arm_healthy_device_protection.sql` fue aplicada exclusivamente en Supabase DEV `syeycayasyufedwoprea`. Se verificaron existencia `SECURITY DEFINER`, revocacion a `PUBLIC` y acceso intencional de `anon/authenticated` protegido por `device_token_matches_device`. El advisor conserva una advertencia esperada por RPC anon con validacion personalizada y deuda previa no introducida por este ticket.
- Validacion local: 815 tareas correctas para `core-domain`, `core-network`, tests DEV Usuario/Admin, ktlint y builds de ambos APK. Pruebas cubren salud completa, componente faltante, VPN desactivada intencionalmente, control nuevo elegible y prohibicion de rearmar un desarmado explicito. Falta CI, publicacion de APK y prueba fisica de onboarding/reinicio.

## Implementacion 2026-07-16 - DEV 240 cierre tecnico BARRIER-A11Y-RACE-01

- Evidencia fisica de origen: el usuario confirmo que en Samsung SM-S908E podia apagar rapidamente Accessibility aunque la pantalla protegida terminara cerrandose.
- Causa tecnica: la configuracion y el filtro del servicio no solicitaban `TYPE_VIEW_CLICKED`. La barrera dependia de cambios posteriores de ventana, contenido o foco; una pulsacion rapida sobre el interruptor nativo podia adelantarse a esos eventos.
- Correccion: el servicio escucha clics solo para decidir sobre Ajustes, instaladores o desinstaladores protegibles. Un clic dentro de una pantalla critica y sin mantenimiento autorizado ejecuta una salida urgente a Home; los demas eventos conservan la secuencia Back/Back/Home. Los clics del resto de las aplicaciones se descartan antes de evaluar uso o politica para no reintroducir trabajo global por toque.
- Mantenimiento: una autorizacion vigente de Ajustes o retiro conserva prioridad y no dispara la salida urgente. Ajustes normales y pantallas de otras aplicaciones siguen disponibles.
- Validacion local: 789 tareas correctas para tests de `feature-accessibility`, tests DEV Usuario/Admin, ktlint de las areas y builds de ambos APK. Pruebas nuevas cubren la suscripcion a clics y la salida urgente. Commit funcional `f1f6bfc`; Android CI `29508703556` y workflow `Publicar APKs DEV` `29508704344` exitosos. APKs publicos descargados y verificados contra sus manifiestos: Usuario 47.828.636 bytes y Admin 27.917.808 bytes, con los SHA-256 documentados arriba.
- Pendiente fisico: instalar DEV 241 in-place en el SM-S908E y realizar veinte intentos rapidos de apagar Accessibility con barrera armada, mas un control positivo dentro de una ventana de mantenimiento autorizada. No se promete resistencia absoluta de Device Owner en Android normal.

## Implementacion 2026-07-16 - DEV 239 correcciones de prueba fisica DAG

- `DAG-THEME-01`: con `targetSdk 36`, asignar colores directamente a las barras del sistema ya no pintaba la zona de la camara bajo edge-to-edge. DAG ahora pinta su superficie hasta el borde, aplica el inset solo al contenido y conserva el contraste de los iconos. La direccion usa una tipografia mas compacta para evitar recortar busquedas como `yeshrun instagram`.
- `DAG-SEARCH-FP-02`: la consulta institucional cerrada `yeshrun`/`yeshurun` con destinos sociales conocidos se considera segura de forma determinista. Agregar vocabulario explicito o riesgoso conserva el bloqueo normal, por lo que la excepcion no enmascara busquedas peligrosas.
- `DAG-MODESTY-CHEST-02`: pecho, genitales o gluteos femeninos cubiertos activan blur fuerte aunque el detector no encuentre un rostro. Axila y abdomen siguen requiriendo contexto femenino. La pagina de bikinis H&M desenfocada aportada por el usuario es la referencia visual correcta que debe conservarse.
- `DAG-IMAGE-DELIVERY-02`: las imagenes de paginas densas esperan en una cola justa en vez de agotarse a los ocho segundos mientras el clasificador local serializado esta ocupado. La cola se cancela al abandonar la pagina; el presupuesto por pagina sube de 160 a 400 recursos y la cache efimera de 24/8 MiB a 64/16 MiB. Se mantienen HTTPS, defensa SSRF, limites de red y tamano, clasificacion local previa y fallo cerrado.
- Validacion local: 1.103 tareas correctas para tests de `core-domain`, tests DEV Usuario/Admin, ktlint, Android Lint, detekt y builds de ambos APK. Commit funcional `1da0cec`. Android CI `29506435216` y workflow `Publicar APKs DEV` `29506428228` exitosos. APKs publicos 239 descargados y verificados contra sus manifiestos: Usuario 47.828.636 bytes y Admin 27.917.808 bytes, con los SHA-256 documentados arriba.
- Pendiente fisico: actualizar Usuario y Admin; comprobar que no haya franja blanca alrededor de la camara, que se lea completa una busqueda larga, que `yeshrun instagram` no alterne entre incierto y bloqueado, que las fotos posteriores aparezcan al desplazarse por una tienda densa, que un escote reciba blur y que la referencia de bikinis H&M siga fuertemente desenfocada.

## Implementacion 2026-07-16 - DEV 238 cierre agrupado de pendientes DAG

- `DAG-RESULTS-PAGE-01`: Brave mantiene diez candidatos por pagina. DAG ofrece una unica pagina adicional solo cuando `query.more_results_available` es verdadero, muestra que consume otra busqueda, concatena y deduplica URLs y no permite una tercera pagina. La Edge Function `dag-search` version 8 esta activa solo en Supabase DEV, con JWT requerido.
- `DAG-AUTOCOMPLETE-02`, `DAG-SHARE-01` y `DAG-THEME-01`: tocar una sugerencia inicia una sola busqueda segura; una pagina HTTPS visible puede compartir solamente su URL mediante el selector Android; y las barras del sistema siguen el tema claro/oscuro de DAG y restauran sus colores al salir.
- `DAG-REQUEST-STATUS-01`: el envio confirma estado pendiente, evita duplicar el mismo dominio mientras exista una solicitud activa y agrega `Pendientes de revision` desde Home y navegacion. La bandeja usa solo solicitudes de dominio guardadas localmente, distingue estados y solo permite reabrir una aprobada, que vuelve a pasar por DAG.
- `DAG-APPROVAL-POLICY-01`: DEV conservaba la regla DAG en las aprobaciones recientes verificadas, sin registros que debieran repararse. La mutacion Admin ahora republica la politica local completa, incluida `__dag_enabled__`, junto con la regla aprobada, y sigue rechazando una politica incompleta antes de informar exito.
- `DAG-CAPTCHA-01`: la pagina oficial de infracciones CABA usa Google reCAPTCHA y DAG la ocultaba al eliminar todos los iframes. La excepcion permite solo rutas HTTPS `/recaptcha/` de `www.google.com` o `www.recaptcha.net` dentro de la ruta oficial de infracciones; otros iframes siguen cerrados y los recursos visuales conservan clasificacion local.
- Cuota DEV temporal: la licencia de `Yeshurun tora` quedo en 1.000 busquedas mensuales por dispositivo, sin borrar ni reiniciar consumo. Es un limite de producto; no agrega credito a Brave. Con el precio vigente de USD 5 por 1.000 solicitudes y USD 5 de credito mensual, las 1.000 solicitudes gratuitas son globales para la cuenta y cada pagina adicional exitosa cuenta otra vez.
- Actualizaciones: DAG comparte APK con App Usuario. Puede comprobar y descargar una version, pero Android normal exige confirmacion del instalador. No se promete instalacion silenciosa sin Device Owner/Android Enterprise, root o tienda administrada.
- Validacion local: 775 tareas correctas para tests de `core-domain`, tests DEV Usuario/Admin, ktlint y builds de ambos APK; luego el control global `ktlintCheck lint detekt` completo tambien finalizo correctamente. Commits `4908437`, `3d29a6d` y cierre de CI `51f2a63`. Android CI `29502441215` exitoso con build, unit tests, ktlint, Android Lint y detekt. Workflow `Publicar APKs DEV` `29501433942` exitoso; un segundo intento sobre el mismo `versionCode` fue rechazado por la proteccion anti-sobrescritura y no modifico Storage. APKs publicos 238 descargados y verificados: Usuario 47.828.636 bytes, Admin 27.917.808 bytes, con los SHA-256 documentados arriba.
- Pendiente fisico: probar una busqueda con `Mas resultados`, pulsaciones rapidas de sugerencias, compartir/cancelar, tema oscuro, bandeja de revisiones, ciclo solicitud-aprobacion-reinicio sin cerrar DAG y reCAPTCHA (checkbox, desafio, vencimiento y segundo plano).

## Implementacion 2026-07-16 - DEV 237 diagnostico agregado de resultados DAG

- Brave sigue recibiendo una unica consulta de hasta 10 resultados por busqueda. `dag-search` agrega a su respuesta solo las cantidades recibidas y rechazadas por titulo o URL HTTPS invalida; no registra ni devuelve diagnosticos con consultas, URLs, dominios, titulos o descripciones.
- App Usuario clasifica cada resultado en un unico destino agregado: bloqueado por lista de dominios, regla Admin, plataforma visual no anulable o clasificador local; incierto mostrado; o permitido mostrado. El resumen existe durante la busqueda activa y se escribe en Logcat solo como cantidades tecnicas.
- No cambia la UI, los umbrales, las reglas kosher, el cupo mensual ni la cantidad solicitada a Brave. No hay paginacion todavia: una futura pagina adicional contaria como otra consulta y requiere un ticket separado.
- Validacion local: tests DEV Usuario/Admin, ktlint y builds optimizados de ambas apps correctos (773 tareas). La prueba del embudo exige que una respuesta controlada de 10 elementos quede contabilizada exactamente una vez por destino. `dag-search` version 7 activa solo en DEV; workflow `Publicar APKs DEV` `29499140202` exitoso. APKs y manifiestos DEV 237 descargados y verificados por version, tamano y SHA-256.

## Implementacion 2026-07-16 - DEV 236 entrega visual estable y deduplicada

- DAG conserva en memoria solo durante la pagina activa hasta 24 respuestas visuales ya clasificadas, con limites de 1 MB por recurso y 8 MB totales. Una URL repetida por `lazy loading`, recomposicion del sitio o atributos dinamicos reutiliza exactamente la copia que ya paso por las IAs, evitando otra descarga y otra inferencia.
- La cache se borra al cambiar o reiniciar la pagina, no persiste en disco, no cruza paginas y no convierte una decision antigua en confianza futura. Historial, sesiones, aprobaciones y consumo Brave no cambian.
- Cuando una imagen no puede decodificarse, excede los limites, agota el turno de analisis o usa un formato inseguro, DAG devuelve una superficie raster neutra marcada como difuminada en lugar de un recurso vacio. Video y audio siguen bloqueados sin placeholder.
- El backlog deja de mostrar como `En progreso` o `Idea` tickets que sus propias fichas y el handoff ya registraban como resueltos en DEV 225, 233 y 234.
- Validacion: 60 tests DEV Usuario, ktlint y builds optimizados Usuario/Admin correctos (758 tareas). APKs/manifiestos DEV 236 publicados exclusivamente en Supabase DEV y verificados por version y SHA-256.

## Implementacion 2026-07-16 - DEV 235 modestia visual regional y blur reforzado

- DAG incorpora en ARM64 una tercera capa ONNX local para detectar regiones corporales cubiertas o expuestas en contexto femenino. Complementa los dos clasificadores NSFW generales porque estos daban por seguras las tres referencias de H&M aportadas por el usuario.
- La politica envia a blur fuerte las combinaciones de rostro femenino con pecho cubierto, genitalia cubierta, gluteos cubiertos, axilas expuestas o abdomen expuesto, ademas de regiones explicitas. Un rostro femenino aislado no alcanza para activar la regla, protegiendo fotos benignas como las del sitio de Yeshurun Tora.
- El modelo NudeNet YOLO cuantizado QUInt8 pesa aproximadamente 3,1 MB y se ejecuta solo en el telefono. El archivo incluido tiene SHA-256 `2e5d2b1471903c5bad06596dbb57bc991a489e27d5b475a62269321db42f123b`; su fuente y licencia Apache-2.0 quedan documentadas en `THIRD_PARTY_NOTICES.txt`.
- Las imagenes dudosas o no aptas conservan su espacio y se entregan como copia raster irreversible con detalle reducido a cuatro pixeles de muestra y blur CSS de 48 px cuando tambien existe evidencia contextual. Errores tecnicos de decodificacion y formatos inseguros siguen cerrados.
- Pruebas unitarias cubren las combinaciones regionales, la exclusion de rostro aislado y la integridad del modelo. Ktlint, unit tests DEV Usuario y builds DEV Usuario/Admin correctos (758 tareas). APKs y manifiestos DEV 235 publicados exclusivamente en Supabase DEV. Pendiente validacion fisica con H&M, Yeshurun Tora y paginas dinamicas.

## Implementacion 2026-07-16 - DEV 234 cierre UX y accesibilidad DAG

- Home conserva un unico buscador central, pero ahora ofrece arriba acciones compactas para nueva pestaña, selector de pestañas y menu. Desde ese menu se accede a historial, borrado de cache y tema sin iniciar una busqueda.
- Inicio, nueva pestaña, selector, menu, cerrar pestaña y borrar una entrada de historial tienen descripciones semanticas; los simbolos dejan de ser controles mudos para TalkBack.
- El estado Analizando respeta la escala global de animaciones de Android. Si el usuario desactiva animaciones, DAG conserva el texto de estado estatico y evita el borde giratorio y los puntos continuos.
- No cambia el buscador, el cupo Brave, la politica kosher, las sesiones, el cifrado ni Supabase. Ktlint, unit tests DEV Usuario y builds DEV Usuario/Admin correctos (758 tareas). APKs y manifiestos DEV 234 publicados exclusivamente en Supabase DEV.

## Implementacion 2026-07-16 - DEV 233 navegacion Atras por pestana

- Desde una pagina abierta mediante una busqueda, Atrás recorre primero el historial interno del WebView, luego vuelve a los mismos resultados y solo el siguiente Atrás regresa a Home. Una URL directa sin resultados vuelve a Home.
- El menu de tres puntos aplica la misma semantica y habilita Atrás cuando existen resultados aunque el WebView no tenga otra pagina. El teclado conserva prioridad y se cierra sin consumir navegacion.
- Los resultados permanecen asociados a su pestaña y sobreviven al proceso mediante el almacenamiento cifrado existente. Volver a ellos no repite la consulta Brave; una pagina restaurada sigue recargando y revalidandose.
- Pruebas cubren pagina a resultados a Home y persistencia de resultados en una pestaña Browser. Ktlint, unit tests DEV Usuario y builds DEV Usuario/Admin correctos (758 tareas). APKs y manifiestos DEV 233 publicados solo en Supabase DEV.

## Implementacion 2026-07-16 - DEV 232 cobertura visual general y continuidad de pagina

- DAG deja de entregar huecos cuando el ensemble visual considera una foto riesgosa: tanto riesgo como incertidumbre producen una copia raster local con blur muy fuerte e irreversible; solo un recurso ilegible o tecnicamente inseguro queda oculto.
- La capa de contexto reconoce rutas femeninas de ropa interior en español e ingles y tarjetas de producto genericas, incluidas variantes VTEX `product-summary`. Usa ruta, enlace, atributos y texto cercano por imagen, sin bloquear la tienda completa ni depender de un dominio concreto.
- Las imágenes íntimas detectadas por metadatos reciben blur de 28 px y desaturacion, reaplicados sobre contenido lazy/dinamico. Cabeceras, navegacion, logos e iconos no heredan el contexto sensible de la categoria.
- SVG pequeños y estaticos pueden mostrarse como iconos solo con MIME exacto y una lista cerrada de seguridad: se rechazan scripts, eventos, entidades, contenido embebido, animacion, referencias, URLs y datos externos. GIF y SVG no seguros siguen cerrados.
- Pruebas cubren rutas genericas mujer/ropa interior, exclusiones de hombre/ropa comun, SVG estatico seguro y SVG ejecutable o externo. Ktlint, unit tests DEV Usuario y builds DEV Usuario/Admin correctos (758 tareas). APKs y manifiestos DEV 232 publicados exclusivamente en Supabase DEV.

## Implementacion 2026-07-15 - DEV 231 calibracion visual y blur local

- DAG aplica tres salidas por imagen: normal cuando ambos modelos locales coinciden en seguridad; blur irreversible cuando discrepan o la imagen queda en zona dudosa; ocultamiento cuando ambos detectan riesgo o uno detecta riesgo y el otro no puede decidir. Ninguna imagen ni score sale del telefono.
- El blur se genera antes de entregar el recurso al WebView: reduce la imagen a 12 pixeles de detalle horizontal y la vuelve a ampliar hasta un maximo de 480 px. No existe accion del usuario para quitarlo ni se conserva la imagen original en la pagina.
- Los umbrales de ambos modelos pasan a una calibracion conservadora de tres zonas: seguro hasta 15 %, dudoso entre 15 % y 65 %, riesgo desde 65 %. La version visual sube a `marqo-nsfw-vit-tiny-384-2` y la politica de pagina a `dag-local-text-7`, invalidando aprobaciones rapidas incompatibles.
- H&M y tiendas similares ya no heredan palabras como `lingerie`, `swimwear` o `underwear` desde menus globales: solo cuentan atributos de la imagen y un contenedor de producto pequeno y cercano. `swimwear` generico deja de ocultar por metadatos y pasa por la IA visual; lenceria y ropa intima concreta siguen ocultas.
- Una pagina segura ya no se cierra porque contenga muchas imagenes rechazadas: cada recurso queda aislado. Plataformas o dominios prohibidos y el texto explicito continúan bloqueandose por sus reglas independientes. La calibracion usa Yeshurun Tora, H&M y contenido social como referencias para la prueba fisica.
- Ktlint, tests DEV completos de Usuario/Admin, lint vital y builds optimizados correctos (773 tareas Gradle). Android CI `29467699637` y workflow `Publicar APKs DEV` `29467699649` exitosos; APKs/manifiestos DEV 231 publicados solo en Supabase DEV y verificados por version, SHA-256, tamaño y HTTP 200. Usuario pesa 45.590.988 bytes y Admin 27.901.424 bytes. Falta validacion fisica con Yeshurun Tora, H&M y contenido social.

## Implementacion 2026-07-15 - DEV 230 filtrado selectivo y contraste DAG

- Las tiendas generales como Calvin Klein permanecen navegables: DAG ya no bloquea la pagina completa solo por palabras como `lenceria`, `ropa interior`, `bralette` o `swimwear` en titulo, resultado o texto visible.
- Cada imagen se decide por separado. Antes de exponerla, el ensemble visual local profesional conserva su politica conservadora; ademas, DAG oculta imagenes cuyo `alt`, titulo, URL o contexto inmediato de producto identifica lenceria, ropa intima o equivalentes. El observador vuelve a aplicar la regla sobre contenido lazy/dinamico.
- El filtro selectivo elimina fuentes y variantes `srcset` de una imagen rechazada y falla cerrado. No envia imagenes ni metadatos fuera del telefono. Esto mejora la politica kosher, pero sigue siendo clasificacion probabilistica y requiere validacion fisica con sitios reales.
- El tema DAG ahora establece `onBackground` para todo el arbol Compose; los textos que no declaran color dejan de heredar negro en modo oscuro. WebView recibe fondo blanco para evitar texto oscuro de paginas transparentes sobre el fondo oscuro de DAG.
- La version de politica de texto sube a `dag-local-text-6`, por lo que se invalidan aprobaciones rapidas incompatibles. Pruebas especificas cubren tienda permitida, contenido pornografico aun bloqueado y metadatos intimos/benignos. Ktlint, tests DEV completos de Usuario/Admin, lint vital y builds optimizados correctos (775 tareas Gradle).
- APKs y manifiestos DEV 230 publicados exclusivamente en Supabase DEV y verificados por version, SHA-256, tamaño y HTTP 200. Usuario pesa 45.574.608 bytes y Admin 27.901.424 bytes. Falta validacion visual en un telefono real con tienda general, imagen intima y ambos temas.

## Implementacion 2026-07-15 - DEV 229 IA visual kosher profesional

- ARM64 incorpora `Marqo/nsfw-image-detection-384`, ViT-Tiny local Apache-2.0 de 5,6 millones de parámetros entrenado sobre 220.000 imágenes; el model card reporta 98,56 % en su propio conjunto balanceado. La conversión ONNX cuantizada INT8 pesa 6.702.582 bytes, SHA-256 `0366969ece89f252f05fad2c730d6c7e3373000e1ff43e4cfab8425aad94405b`; una imagen benigna de control conservó la decisión entre FP32 e INT8.
- La decisión es un ensemble conservador: una imagen solo se muestra si el ViT profesional y el OpenNSFW anterior la consideran segura. Zona dudosa, error o salida inválida permanecen ocultos. ARM32 conserva OpenNSFW porque el runtime ONNX se excluye allí por compatibilidad/tamaño.
- En DEV 229, `lencería`, ropa íntima/interior y equivalentes se bloquearon a nivel de página. Esa conducta historica fue reemplazada en DEV 230 por filtrado selectivo de cada imagen para mantener abiertas las tiendas generales.
- Todo sucede en el teléfono: imagen, URL, probabilidad y decisión no se envían a Supabase, Brave ni al administrador. La precisión probabilística no equivale a garantía absoluta y requiere validación física con páginas reales.
- La variante FP32 inicial llevó el APK a 58 MiB y Supabase DEV rechazó el objeto por tamaño antes de cambiar los manifiestos públicos. La cuantización selectiva de MatMul/Gemm redujo el modelo de 22,5 a 6,7 MB y el APK a 45.574.608 bytes; el control benigno pasó de 5,76 % a 6,37 % NSFW, conservando la decisión segura con el umbral 8 %. Ktlint, tests DEV y builds optimizados Usuario/Admin correctos. APKs/manifiestos DEV 229 publicados solo en Supabase DEV y verificados por versión, hash declarado y HTTP 200.

## Implementacion 2026-07-15 - DEV 228 autocompletado y contraste DAG

- DAG ofrece hasta cinco sugerencias mientras se escribe, sin consultar Brave: usa el historial cifrado del telefono y reformulaciones locales de contexto inequivoco. Cada candidata se vuelve a clasificar en el dispositivo y un debounce corto evita trabajo por cada pulsacion.
- Consultas ambiguas como `coca` proponen contexto seguro y concreto, por ejemplo `Coca-Cola gaseosa`, sin relajar la decision final del clasificador.
- La barra superior baja de 58 a 52 dp y el buscador de Home de 64 a 56 dp. Los resúmenes de resultados y las sugerencias declaran colores de alto contraste para tema claro y oscuro.
- La actualización DEV ya se comprueba automáticamente al entrar en la pantalla Actualizaciones y puede descargar/verificar el APK; Android normal exige confirmación del usuario para instalar un APK externo. Instalación silenciosa requiere Device Owner, root o una tienda administrada y no se intenta eludir esa protección.
- Ktlint, pruebas unitarias DEV y builds optimizados de Usuario/Admin correctos. APKs y manifiestos DEV 228 publicados exclusivamente en Supabase DEV y verificados por versión, hash declarado y HTTP 200; falta validación visual en teléfono.

## Implementacion 2026-07-15 - DEV 227 falsos positivos, aprobacion DAG y Home

- Causa del bloqueo `comprar Coca-Cola`: el modelo compacto daba demasiado peso a `comprar` por la proporcion de ejemplos de drogas. Las consultas comerciales inequívocas de Coca-Cola se permiten antes del modelo; la excepcion usa vocabulario cerrado y no se aplica si aparece cualquier termino adicional desconocido/riesgoso. Pruebas cubren permitido, incierto por cocaína y bloqueo por contenido explícito.
- Causa del cierre al aprobar: App Admin podia aprobar un dominio sin haber cargado la politica completa del dispositivo; `RoomPolicyRepository` creaba entonces una politica activa nueva con solo la regla del dominio y sin `__dag_enabled__`, que App Usuario interpretaba correctamente como revocacion. Admin fuerza primero un pull dirigido de esa politica y el caso de uso rechaza la aprobacion si DAG no figura completo y habilitado, evitando reemplazar la politica.
- Una politica ya dañada por una version anterior no se modifica automaticamente: despues de actualizar, el administrador debe volver a habilitar DAG una vez para ese dispositivo. Las aprobaciones siguientes preservan la regla.
- Home ahora borra direccion, resultados, URL solicitada, carga, mensaje y candidato, e invalida la navegacion Web anterior. El buscador siempre aparece vacio al entrar a Inicio.
- Tema claro/oscuro declara colores de texto, superficie, variantes, contornos, cursor y placeholders con contraste explicito; no depende de valores derivados que podian dejar texto oscuro sobre fondo oscuro.
- `DagContentClassifier.ModelVersion` sube a `dag-local-text-4`, invalidando decisiones rapidas anteriores. Validacion: core-domain, ktlint Usuario/Admin, tests DEV de ambas apps y builds optimizados correctos. APKs/manifiestos DEV 227 publicados y verificados; falta prueba fisica del ciclo solicitud -> aprobacion -> DAG permanece abierto.

## Implementacion 2026-07-15 - DEV 226 cache segura, pestanas persistentes y calibracion DAG

- El estado de analisis ocupa solo el campo combinado: oculta la direccion anterior, muestra `Analizando` con uno a tres puntos animados y elimina `Preparando navegador` del centro de la pantalla. El contenido continua invisible hasta una decision.
- DAG conserva durante siete dias una aprobacion local cifrada para la URL exacta solo cuando tambien coinciden la huella del titulo/texto, el resumen visual, la version de politica y las versiones de clasificadores. Las reglas duras y el dominio se comprueban en cada carga; cambio, vencimiento, reloj retrocedido, modelo o politica invalidan el atajo.
- El menu permite borrar esta cache con confirmacion sin borrar historial, cookies/inicios de sesion ni pestanas. No se agrego cache persistente por imagen porque cifrar y escribir cada recurso podia empeorar el tiempo de carga; las imagenes siguen clasificandose y fallando cerradas.
- Hasta ocho pestanas sobreviven al cierre del proceso mediante almacenamiento local cifrado. Se guardan URL o resultados seguros, pero no miniaturas, HTML ni contenido de pagina. Una pestana Web restaurada recarga y vuelve a pasar por las barreras; no se restaura como visible por confianza previa.
- La calibracion reproducible del modelo compacto de respaldo sube de 557 a 636 ejemplos y pasa 69 controles separados. Corrige cinco evasiones de riesgo detectadas antes de tocar umbrales, principalmente frases hebreas; artefacto de 114.736 bytes, SHA-256 `8ffb797ab8976f4a4cd18728c8a64ff5973392e30562a3e4291783f00cb9e612`, version `dag-local-text-3`. El modelo neuronal profesional conserva prioridad y las reglas explicitas no se relajan.
- Validacion local: generacion determinista del modelo, ktlint Usuario, tests DEV Usuario/Admin y builds optimizados de ambos APK correctos. APKs y manifiestos DEV 226 publicados y verificados; falta prueba fisica de visual, reapertura y rendimiento.

## Implementacion 2026-07-15 - DEV 225 Home y revision por etapas

- Home elimina la mascota y la barra superior completa: queda un unico buscador central exclusivo de Inicio. Al aparecer el teclado conserva una posicion alta estable y al comenzar la busqueda oculta el teclado.
- Durante busqueda o analisis, el campo muestra `Analizando...` y un borde cian/violeta/rosa cuya direccion luminosa gira. La animacion existe solo mientras hay trabajo activo y se detiene al terminar.
- Los resultados inciertos por titulo o fragmento ya no piden aprobacion inmediata. DAG abre la pagina oculta, mantiene dominios/listas/barreras antes de WebView y decide con el contenido completo; solo si esa segunda etapa sigue incierta ofrece revision humana.
- Las paginas JavaScript sin texto inmediato reciben dos reintentos locales acotados antes de declararse ilegibles, reduciendo revisiones falsas por hidratacion tardia.
- Una palabra ambigua aislada dentro de una pagina extensa ya no fuerza revision por si sola: pasa al modelo contextual. En consultas cortas conserva el criterio conservador; terminos explicitos, categorias no anulables, dominios bloqueados e imagenes inseguras mantienen prioridad.
- Validacion local: ktlint, tests DEV Usuario/Admin y builds optimizados de ambos APK correctos, incluida una regresion para menciones ambiguas incidentales. APKs y manifiestos DEV 225 publicados y verificados; falta prueba fisica.

## Implementacion 2026-07-15 - DEV 224 paquete visual DAG

- Barra: Home y nueva pestana quedan visibles; Atras, Adelante y Actualizar pasan al menu con estados habilitados coherentes. El boton fisico Atras conserva su navegacion segura.
- Home: buscador central grande, mascota e identidad `Internet kosher con proteccion local`, sin prometer cobertura absoluta. La barra superior sigue disponible como omnibox.
- Pestanas: maximo de ocho, contador explicito, estado `Actual`, nueva pestana y cierre de todas con retorno a una pestana segura. Las miniaturas siguen siendo locales, efimeras y solo de contenido visible.
- Tema: opciones claro, oscuro y segun dispositivo persistidas localmente; se coordinan colores de DAG e iconos de barras del sistema. No cambia el tema del resto de App Usuario.
- Historial: lista local minimalista agrupada por fecha, hora, tipo, dominio y borrado por fila o total. No cambia el cifrado ni los datos almacenados.
- Analisis: el estado se integra en la barra con contraste cian/violeta y se elimina el mensaje separado durante la carga. No usa una animacion infinita ni muestra contenido antes de la decision.
- Validacion local: ktlint, tests DEV Usuario/Admin y builds optimizados de ambos APK correctos, incluida prueba de resolucion de tema. APKs y manifiestos DEV 224 publicados y verificados; falta prueba fisica.

## Implementacion 2026-07-15 - DEV 223 compatibilidad visual en sitios densos

- Fravega expone imagenes raster publicas PNG y WebP, pero DEV 221/222 bloqueaba inmediatamente toda solicitud que llegara mientras los tres turnos de clasificacion estaban ocupados. En paginas con muchas fotos casi todas desaparecian antes de ser analizadas.
- Las solicitudes visuales esperan ahora hasta ocho segundos por uno de tres turnos, en vez de fallar de inmediato. El presupuesto sube de 80 a 160 recursos por pagina y el timeout individual vuelve a ocho segundos.
- La deteccion se amplia por contenido decodificable a JPEG, PNG, WebP, AVIF, HEIF/HEIC y BMP; las extensiones HEIF/HEIC se reconocen aun sin cabecera `Accept`. SVG, GIF y variantes animadas siguen bloqueadas porque validar una sola representacion no garantiza todos sus cuadros o codigo vectorial.
- La revision seguida de apertura puede ser normal cuando una aprobacion remota llega durante la espera: la primera etapa marca incertidumbre y, al sincronizar la regla del administrador, DAG reintenta y abre. No se elimino este comportamiento seguro.
- Ktlint, tests DEV Usuario y builds optimizados Usuario/Admin correctos. Falta prueba fisica en Fravega porque no hay telefono disponible.

## Implementacion 2026-07-15 - DEV 222 correccion de hilo WebView

- DEV 221 siguio cerrandose al entrar a una pagina, por lo que el limite de carga no era la causa principal o no era la unica.
- Se encontro una infraccion concreta: `shouldInterceptRequest` se ejecuta fuera del hilo principal pero consultaba `WebView.getUrl()`. Android exige que las APIs de WebView se usen en su hilo de creacion y puede terminar la aplicacion al detectar esta llamada.
- El cliente conserva ahora la URL del frame principal en un estado volatil actualizado desde `onPageStarted`; la intercepcion de recursos lee solo esa copia segura y ya no toca el WebView desde el hilo de red.
- Se agrego una prueba unitaria del seguimiento de URL. Ktlint, tests DEV Usuario y builds optimizados Usuario/Admin correctos. Falta confirmacion fisica porque no hay telefono ni emulador disponible.

## Implementacion 2026-07-15 - DEV 221 estabilizacion del filtro visual

- El cierre informado al entrar a una pagina es compatible con saturacion del renderer de WebView: una pagina puede disparar muchas descargas y clasificaciones TFLite simultaneas. App Admin no es el destino intencional; queda visible porque comparte el telefono y estaba debajo de la tarea DAG cuando esta termina.
- El cargador visual admite como maximo tres clasificaciones concurrentes, 80 imagenes por pagina y cinco segundos por recurso. Si se agota el cupo, la imagen falla cerrada en vez de encolar mas trabajo y presionar memoria/hilos del renderer.
- No se relaja la seguridad: una imagen sin turno, dudosa, fallida o fuera del presupuesto permanece bloqueada. No cambia Supabase, Brave, historial, cookies ni reglas remotas.
- Validacion local: ktlint, tests DEV Usuario y builds optimizados Usuario/Admin correctos. No hay telefono ni emulador configurado para reproducir el cierre exacto; queda pendiente prueba fisica de DEV 221 y no se considera confirmada la causa hasta obtener evidencia en dispositivo.

## Implementacion 2026-07-15 - DEV 220 selector visual de pestañas

- El contador de pestañas abre un selector modal de ancho completo inspirado en Chrome, con cuadrícula de dos columnas, miniatura, nombre de búsqueda/dominio, pestaña activa, cierre individual y nueva pestaña.
- La miniatura se captura localmente en RGB_565 y baja resolución únicamente cuando la página ya está en estado `Visible`; páginas bloqueadas, inciertas o todavía ocultas muestran la mascota y nunca producen vista previa.
- Las vistas previas viven solo en memoria durante la sesión de DAG. No se sincronizan, no llegan al administrador, no se guardan en historial ni generan consultas Brave. Los WebView de pestañas inactivas siguen suspendidos para limitar memoria.
- Gmail: cookies propias y DOM storage de DAG persisten normalmente entre aperturas/actualizaciones si Google permite completar el login. No se comparte la sesión de Chrome y Google puede rechazar autenticación dentro de WebView por su política de user-agent embebido.
- Validación local: ktlint, tests DEV Usuario y APK Usuario optimizado correctos. Ambos APK 220 fueron publicados atomicamente en Supabase DEV y verificados por manifiesto/hash. Falta prueba física de DEV 220.

## Implementacion 2026-07-15 - DEV 219 descarga de actualizaciones resiliente

- El APK 218 público fue comprobado externamente: HTTP 200, `Content-Length 40.429.875`, rangos habilitados y primer MiB descargado correctamente. El 0 % observado ocurre después de obtener el manifiesto y antes de recibir bytes en Android.
- El cliente de actualización queda aislado en HTTP/1.1, fuerza cuerpo sin compresión, evita caché intermedia, reintenta fallos de conexión y conserva reanudación por `Range`. Los timeouts pasan a 60 segundos por operación y 10 minutos por descarga completa para conexiones móviles/VPN lentas.
- Validación local: ktlint y módulo `core-update`, build optimizado DEV Usuario y Admin correctos. Ambos APK 219 fueron publicados atomicamente en Supabase DEV y verificados por manifiesto/hash. No hay teléfono conectado para reproducir la ruta exacta de red; DEV 219 requiere instalación directa inicial y luego prueba del siguiente chequeo interno.

## Implementacion 2026-07-15 - DAG DEV 218 WebView resiliente y cierre de buscadores

- `DagWebViewClient` maneja `onRenderProcessGone` y consume la caída del renderer aislado. DAG abandona solo la pestaña dañada, libera su referencia y vuelve a Home con un mensaje recuperable, en lugar de permitir que Android cierre Content Filter.
- El presupuesto visual baja de 200 a 120 imágenes clasificadas por página para conservar la compatibilidad ampliada de DEV 217 con menor presión de memoria/renderer.
- La barrera no anulable `search_portal` cubre Google y dominios regionales, Bing, Yahoo, DuckDuckGo, Brave Search web, Yandex, Ecosia, Startpage, Qwant, Swisscows, Mojeek, AOL, Ask y Baidu. Gmail, Maps y otros subservicios que no sean portales de búsqueda continúan evaluándose normalmente.
- Validación local: ktlint, tests DEV Usuario con matriz de portales y APK Usuario optimizado correctos. Ambos APK 218 fueron publicados atomicamente en Supabase DEV y verificados por manifiesto/hash. Sin teléfono conectado no fue posible extraer el `logcat` del cierre mostrado por el usuario; la prueba física de recuperación WebView queda pendiente.

## Implementacion 2026-07-15 - DAG DEV 217 compatibilidad, Home y pestañas

- La superficie independiente respeta `statusBarsPadding`: barra y controles quedan debajo de cámara, hora, batería e iconos del sistema. La barra combinada vuelve a 56 dp para evitar recorte interno y al recibir foco vacía la dirección anterior.
- Home incorpora la mascota local y el texto `Buscá y navegá con protección local` sin cargar recursos externos.
- Pestañas funcionales: botón con cantidad, nueva pestaña, cambio y cierre. Cada pestaña conserva Home, resultados o URL; restaurar resultados no repite una consulta Brave. Las pestañas Web se reanudan desde su URL en lugar de mantener varios WebView/ONNX vivos, limitando memoria.
- Compatibilidad de imágenes: fuentes lazy adicionales, `<picture>` sin eliminar sus `<source>`, detección `Sec-Fetch-Dest`, Referer HTTPS de la página cuando falta y presupuesto de hasta 200 imágenes clasificadas por página. Cada recurso conserva HTTPS, 4 MiB, timeout, SSRF y decisión visual local; no significa permitir toda imagen sin clasificación.
- `google.com` raíz y `/search` se bloquean como portales de búsqueda no anulables para evitar saltarse Brave, clasificación y cupo. Servicios separados como `mail.google.com` y `maps.google.com` continúan evaluándose como sitios normales. Cookies son propias de DAG y no comparten automáticamente la sesión de Chrome.
- Validación local dirigida: ktlint, tests DEV Usuario —incluido bypass de buscadores— y APK Usuario optimizado correctos. Ambos APK DEV 217 fueron ensamblados, publicados atomicamente en Supabase DEV y verificados por manifiesto/hash. Falta prueba física de DEV 217.

## Cierre 2026-07-15 - DAG-UX-02 superficie completa tipo navegador

- DEV 216 concentra Atrás, Adelante, barra combinada, Recargar/Ir y menú en una única franja superior. La segunda fila de controles fue eliminada para recuperar altura útil.
- El WebView ocupa todo el ancho y alto restante, sin bordes ni márgenes laterales. Los resultados dejan las tarjetas redondeadas y usan filas continuas de ancho completo, separadas sutilmente, con jerarquía dominio, título y resumen inspirada en buscadores modernos.
- Brave sigue siendo el motor y el único consumo contabilizado. La presentación puede recordar a Google/Chrome, pero DAG no incrusta Google Search ni reenvía consultas a Google: así conserva control del filtrado, privacidad, revisión y cupo.
- Validacion local: ktlint, tests unitarios DEV Usuario y ensamblado DEV Usuario/Admin correctos. APK 216 publicado mediante el flujo atomico exclusivamente en Supabase DEV; manifiestos y hashes verificados.

## Cierre 2026-07-15 - DAG DEV 213-215 modelo neural, imagenes y UX compacta

- DEV 213 reemplaza el primer clasificador estadistico por un MiniLM multilingue ONNX profesional que corre localmente. ARM64 usa ONNX Runtime y ARM32 conserva un clasificador compacto compatible para no excluir telefonos estandar. El APK Usuario queda en aproximadamente 39 MiB.
- DEV 214 generaliza la visualizacion segura de imagenes HTTPS ya aprobadas localmente: reconoce fuentes lazy comunes y fondos CSS, entrega las respuestas filtradas con CORS y eleva el limite a 80 imagenes por pagina. La mejora no es especifica de Carrefour; aplica a sitios compatibles. SVG, GIF, video, `blob:`, recursos inseguros e imagenes no aprobadas permanecen ocultos.
- La prueba fisica en SM-A235M mostro correctamente imagenes de Carrefour despues de la clasificacion local. La marca exacta `carrefour` dejo de producir el falso positivo observado y una imagen ilegible ya no bloquea el texto seguro de toda la pagina.
- DEV 215 adopta una interfaz de navegador compacta inspirada en Chrome sin copiar su marca: barra combinada con accion integrada, resultados mas bajos con titulo/dominio/resumen truncados, controles de navegacion uniformes y cierre automatico del teclado al mostrar resultados o pagina.
- `dag-search` DEV solicita a Brave resultados sin decoraciones HTML. La funcion fue desplegada solo en el proyecto DEV `syeycayasyufedwoprea`; no hubo cambios en Production, borrado de datos ni secretos en Android.
- Validacion: tests dirigidos, ktlint y `assembleDevDebug` locales correctos; Android CI `29440595594` exitoso. La publicacion automatica `29440595539` encontro un fallo transitorio de resolucion DNS al consultar Supabase despues de completar 1.172 tareas; ambos APK se publicaron luego directamente mediante el flujo atomico DEV y se descargaron para verificar version 215 y SHA-256. Un reintento tardio de ese workflow archivo los manifiestos y choco con los APK 215 ya existentes; los manifiestos se restauraron y verificaron publicamente. El publicador ahora archiva tambien un APK homonimo antes del movimiento atomico para que una repeticion no vuelva a producir ese estado.
- Commit DEV 214 `758c447`; commit DEV 215 `ec9bff7`. La version 215 fue probada fisicamente antes de desconectar el telefono: busqueda `carrefour`, teclado oculto, aproximadamente siete resultados visibles y sin etiquetas `<strong>`.

## Publicacion 2026-07-15 - AI-SEARCH-01A pendiente de prueba fisica

- DEV 212 agrega a DAG un clasificador estadistico de intencion que corre enteramente en el telefono sobre consultas, metadatos de resultados y texto de paginas. Las reglas explicitas, la lista dinamica de dominios y las barreras de plataforma conservan prioridad; el modelo solo endurece la decision restante.
- No es un LLM ni un transformer: usa un modelo lineal compacto sobre n-gramas de caracteres, palabras y pares de palabras. El artefacto propio pesa 114.736 bytes, SHA-256 `bf75e837708793412060abda5eb56ef9b1adba8ef6b58a19986e45854a54a9c1`, y se reproduce con `scripts/dag_text_model/train_model.py` sin dependencias Python externas.
- Cobertura inicial: espanol, ingles y hebreo; categorias sexual, citas, apuestas, drogas, violencia, contexto sensible y uso general. Confianza riesgosa alta bloquea, la zona media o el contexto medico/educativo/religioso quedan inciertos y un modelo ausente, corrupto o invalido falla cerrado.
- El corpus controlado actual contiene 557 ejemplos de entrenamiento y 34 controles separados. Incluye negativos cotidianos de tramites, compras, correo, resultados deportivos, imagenes comunes y Tora. Es un primer corte conservador: reduce evasiones y sinonimos simples, pero no equivale a comprension humana ni garantiza 100 % de precision.
- Privacidad y costo: el modelo, sus scores y las decisiones permanecen locales; no agrega API, secreto, consulta Brave, escritura Supabase ni telemetria de contenido. La navegacion directa sigue sin consumir busquedas.
- Validacion local: 885 tareas Gradle correctas con tests Usuario/Admin, ktlint, Android lint, detekt informativo y ensamblado optimizado de ambos APK. Pruebas dirigidas cubren tres idiomas, contexto sensible, prioridad de dominios bloqueados, separacion entre galeria comun y contenido riesgoso y fallo cerrado del artefacto.
- Commit funcional `76bb7ed`; workflow `Publicar APKs DEV` `29430743977` y Android CI `29430747856` exitosos. Manifiestos DEV 212 y ambas descargas publicas se verificaron por SHA-256. Falta instalar DEV 212 en el telefono personal y medir busquedas seguras/riesgosas y fluidez antes de cerrar el ticket.
- No se toco Production, no se borraron datos, no se modifico el esquema de Supabase DEV y no se agregaron secretos ni Service Role Key a Android.

## Publicacion 2026-07-15 - DAG-SAFETY-01 pendiente de prueba final

- DEV 211 esta publicado para Usuario y Admin en Supabase DEV. Ambos manifiestos y descargas se verificaron por SHA-256. Commit funcional `06c306c`; workflow `Publicar APKs DEV` `29426319592` y Android CI `29426319513` exitosos.
- Corrige la sincronizacion de reglas al abrir DAG y comprueba automaticamente durante dos minutos una solicitud recien enviada. Supabase DEV confirma que la aprobacion de `easy.com.ar` y su regla `Allow` por dispositivo existian correctamente; el fallo estaba en el refresco del cliente.
- Las aprobaciones futuras reutilizan una regla `Allow` existente en vez de crear otro duplicado. No se borraron las reglas duplicadas preexistentes de YouTube ni ningun otro dato.
- Las imagenes lazy con origen HTTPS y AVIF estatico en Android 12+ pasan por el mismo clasificador local. La decision de pagina incorpora el balance de imagenes seguras, bloqueadas e inciertas; SVG, GIF, animacion, `data:` y `blob:` siguen cerrados.
- `imgsrc.ru` queda bloqueado antes de crear una navegacion WebView. Es una barrera preventiva no anulable mediante revision de dominio.
- Atras oculta primero el teclado. Prueba fisica SM-A235M: Wikipedia mostro fotos seguras de manzanas y flores; Atras cerro el teclado sin salir; `imgsrc.ru` mostro bloqueo sin solicitud WebView. Easy requiere validacion final en el celular cuyo dispositivo recibio la aprobacion.
- Validacion integral local: 895 tareas Gradle correctas con tests de dominio, Usuario y Admin; ktlint, Android lint, builds optimizados y detekt informativo. Falta instalar DEV 211 en el celular personal y confirmar que la regla aprobada de Easy se sincroniza y abre sus imagenes. No se consumieron consultas Brave, no se toco Production, no se agregaron secretos ni Service Role Key a Android.

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

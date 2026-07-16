# BACKLOG DE PRODUCTO

Ultima sincronizacion: 2026-07-15

Este archivo es la fuente canonica del backlog de producto versionado en Git. No reemplaza a `docs/HANDOFF_ACTUAL.md`, que sigue siendo la verdad tecnica de lo implementado y publicado.

## Fuentes y jerarquia

1. `docs/HANDOFF_ACTUAL.md`: estado tecnico real, versiones, pruebas y publicaciones.
2. Este archivo: ideas normalizadas, prioridades, tickets propuestos y decisiones de producto.
3. [Backlog Codex - Control Parental](https://docs.google.com/document/d/14wkeHKthWfVPvm1HFsKNeXpINLhCUZPLlpFOVAeNWsw/edit): bandeja de entrada historica para ideas y conversaciones.
4. Chats de Codex: espacio de entrevista y trabajo, nunca fuente permanente por si solos.

Si una version, prueba o capacidad difiere entre fuentes, prevalece `docs/HANDOFF_ACTUAL.md`. El Google Doc no se borra: las ideas nuevas se normalizan aqui y las repetidas se agrupan.

## Reglas del backlog

- Estados: `Idea`, `Propuesto`, `Aprobado`, `En progreso`, `Bloqueado`, `Resuelto` y `Archivado`.
- Prioridades: `P0` seguridad/operacion critica; `P1` siguiente fase importante; `P2` mejora relevante; `P3` exploracion.
- Cada ticket debe registrar tipo, prioridad, evidencia, esfuerzo, riesgo, dependencias y criterios de aceptacion.
- Ningun ticket autoriza codigo hasta que el usuario lo apruebe explicitamente.
- Un ticket destructivo, especialmente borrado definitivo, necesita una confirmacion especifica e independiente.
- Un item pasa a `Resuelto` solo con causa o alcance conocido, tests pertinentes, validacion fisica cuando corresponda, publicacion DEV verificada si cambia Android, CI aplicable y handoff actualizado.
- Las actualizaciones de backlog son documentales: no requieren build, `versionCode` ni publicacion de APK.
- Supabase: solo DEV `syeycayasyufedwoprea`; nunca Production, nunca borrar datos sin confirmacion y nunca Service Role Key en Android.

## Flujo vivo

1. Capturar la idea en el chat dedicado o en el Google Doc.
2. Entrevistar al usuario antes de convertirla en solucion.
3. Deduplicar, asignar ID y dejarla como `Propuesto`.
4. Esperar aprobacion explicita del primer ticket.
5. Implementar el ticket en un chat de ejecucion, con alcance pequeno.
6. Al cerrar, actualizar este archivo y `docs/HANDOFF_ACTUAL.md` en el mismo commit.

## Ancla tecnica actual

- Estado publicado: App Usuario DEV 212 y App Admin DEV 212, `1.0.1-dev`.
- Baseline de recuperacion Web: `stable/dev-191-web-protection` (no representa la ultima version publicada).
- FCM real y alertas de proteccion ya estan implementados y validados en DEV 202.
- Los detalles, hashes, commits y evidencias vigentes viven unicamente en `docs/HANDOFF_ACTUAL.md` y `docs/BASELINES.md`.

## Ultimos tickets trabajados

### AI-SEARCH-01A - Intencion semantica local compacta

- Estado: `En progreso`; aprobado por la continuacion explicita de IA local el 2026-07-15. DEV 212 publicado y verificado; falta prueba fisica en el celular personal.
- Tipo: IA local, seguridad Web, privacidad y rendimiento. Prioridad: P1. Esfuerzo: M. Riesgo: alto.
- Alcance implementado: segundo clasificador estadistico local para consultas, resultados y paginas en espanol, ingles y hebreo. Detecta sinonimos y evasiones simples de sexual, citas, apuestas, drogas y violencia; contexto sensible queda incierto y un artefacto ausente/corrupto falla cerrado.
- Arquitectura: modelo lineal propio de 112 KiB sobre n-gramas; 557 ejemplos de entrenamiento y 34 controles separados; reglas explicitas y dominios bloqueados siguen antes del modelo y ninguna salida semantica habilita una decision bloqueada por otra capa.
- Privacidad y costo: cero servicios o secretos nuevos; texto, scores y decisiones no salen del telefono; no cambia el consumo Brave ni guarda consultas en Supabase.
- Evidencia: pruebas en tres idiomas, contexto, negativos cotidianos, modelo corrupto y prioridad de bloqueos; 885 tareas Gradle correctas; commit `76bb7ed`; workflow DEV `29430743977` y Android CI `29430747856` exitosos; manifiestos y APK DEV 212 verificados por SHA-256. No se marca resuelto hasta validar calidad y fluidez fisicas.

### DAG-SAFETY-01 - Correcciones de imagenes, revisiones y navegacion

- Estado: `En progreso`; aprobado dentro de la continuacion explicita de DAG el 2026-07-15. DEV 211 publicado y verificado; falta la prueba final de la aprobacion de Easy en el celular personal.
- Tipo: seguridad Web, sincronizacion, UX y compatibilidad visual. Prioridad: P0. Esfuerzo: M. Riesgo: alto.
- Problemas reproducidos: fotos ausentes en paginas modernas; `easy.com.ar` seguia cerrado pese a una aprobacion activa; Atras podia navegar antes de ocultar el teclado; `imgsrc.ru` se permitia aunque es una plataforma visual no apta para este perfil.
- Alcance implementado: imagenes lazy HTTPS y AVIF estatico pasan por el clasificador local; la pagina incorpora el balance de decisiones visuales; `imgsrc.ru` se bloquea antes de WebView; DAG sincroniza al abrir y consulta temporalmente una aprobacion pendiente; futuras aprobaciones reutilizan reglas existentes; Atras cierra primero el teclado.
- Privacidad y costo: no se agregan servicios, secretos ni consultas Brave. Imagenes y decisiones permanecen en el telefono. No se borra ningun dato ni regla duplicada preexistente.
- Evidencia local y fisica: tests de clasificador/sincronizacion, validacion integral Gradle y build de ambos APK correctos; Samsung SM-A235M mostro fotos de Wikipedia, mantuvo la pagina al cerrar el teclado y rechazo `imgsrc.ru` antes de cargarlo. APKs publicos DEV 211 y hashes verificados; la aprobacion de Easy se verificara en el celular personal.

### DAG-IMAGES-01 - Clasificacion local de imagenes

- Estado: `Resuelto` el 2026-07-15 en DEV 210; ticket aprobado explicitamente por el usuario.
- Tipo: IA local, seguridad Web, privacidad y rendimiento. Prioridad: P1. Esfuerzo: L. Riesgo: alto.
- Resultado: DAG entrega al WebView solo imagenes estaticas JPEG, PNG y WebP clasificadas localmente como seguras. Toda duda, error, formato animado/no soportado o fallo de descarga se bloquea.
- Privacidad: imagenes, URLs, scores y decisiones no salen del telefono ni generan revision humana. Admin sigue revisando solo dominios inciertos.
- Barreras: HTTPS, limites de cantidad/tamano/tiempo, rechazo de redes privadas, sin downgrade, bloqueo temprano de `blob:` y Service Workers. Video y audio permanecen bloqueados.
- Evidencia fisica SM-A235M: fotos seguras de manzanas y flores visibles en Wikipedia tras el analisis; APK publico DEV 210 instalado in-place, sin crash y con Device Admin, Accessibility y VPN preservados. La navegacion directa no consumio Brave.
- Publicacion: commit `dc6ad61`, Android CI `29415230021`, workflow DEV `29415229996` y hashes publicos verificados. La clasificacion es probabilistica y no promete precision perfecta.

### DAG-STANDALONE-01 - Experiencia de navegador independiente

- Estado: `Resuelto` el 2026-07-15 en DEV 209.
- Resultado: DAG tiene icono, nombre y tarea propios dentro del mismo APK de App Usuario; comparte activacion, proteccion, actualizacion e historial cifrado.
- Control remoto: la regla DAG habilita o deshabilita el componente launcher. Al cerrar desaparece del cajon de apps y se retira cualquier tarea DAG abierta; al reabrir reaparece sin reinstalar.
- Evidencia fisica SM-A235M: dos afinidades/tareas separadas; icono visible con DAG abierto y ausente con DAG cerrado; lanzamiento bloqueado al cerrar; reaparicion y apertura correctas al habilitar; inicio minimalista y regreso a Content Filter al cerrar la tarea.
- Publicacion: commit `0aecea6`, Android CI `29412201877` y workflow DEV `29412273313`; APKs publicos DEV 209 verificados e instalados in-place con las protecciones preservadas.

### DAG-BROWSER-01A - Navegador protegido funcional

- Estado: `Resuelto` el 2026-07-15 en DEV 208.
- Tipo: producto, seguridad Web y UX. Prioridad: P1. Esfuerzo: XL. Riesgo: alto.
- Resultado: busqueda Web real mediante Brave del lado servidor, navegacion interna de una pestana, clasificacion local conservadora de consultas/resultados/paginas, historial local cifrado, revision de dominios inciertos y revocacion por dispositivo.
- Evidencia fisica SM-A235M: bloqueo local sin consumir Brave; una unica busqueda permitida; pagina incierta cerrada; solicitud recibida y rechazada por Admin sin consulta ni historial; historial local y borrado del elemento de prueba; consulta ausente de Logcat; cierre y reapertura remotos.
- Seguimiento DEV 208: al cerrar o reabrir DAG se limpia la sesion visual retenida sin borrar el historial. El ciclo final reabre en Inicio sin mensaje ni candidato obsoletos.
- Publicacion: commit funcional `4f2886a`, Android CI `29409620735` y workflow DEV `29409620788`, con hashes publicos verificados. Device Admin, Accessibility y VPN foreground permanecieron activos.

### USER-PERF-01 - Rendimiento y simplificacion de App Usuario

- Estado: `Resuelto` el 2026-07-14 en DEV 203.
- Tipo: rendimiento y UX.
- Prioridad: P1.
- Evidencia: scroll percibido como lento o trabado en el Samsung fisico; la pantalla principal usa contenido vertical eager, observa varios flujos globales y tiene refrescos periodicos. `Mis apps` ya usa lista lazy con claves estables.
- Esfuerzo: M.
- Riesgo: medio; una optimizacion incorrecta podria ocultar estados de proteccion.
- Alcance propuesto:
  - medir primero recomposiciones, frames lentos y trabajo en hilo principal;
  - reducir recomposiciones y trabajo repetido sin debilitar la barrera;
  - reemplazar los botones permanentes `Actualizar permisos` y `Pedir mantenimiento` por una accion contextual;
  - estado sano: `Solicitar acceso temporal`; estado degradado: `Reparar proteccion`;
  - conservar revocacion, mantenimiento y recuperacion solo en los estados donde realmente correspondan.
- Aceptacion propuesta:
  - medicion antes y despues en el mismo telefono y recorrido;
  - scroll sostenido sin trabas perceptibles ni operaciones de I/O en el hilo principal;
  - estados, bloqueo, alertas y barrera sin regresiones;
  - pruebas fisicas de scroll, permisos sanos/degradados y solicitud temporal;
  - actualizacion in-place, sin desinstalar ni borrar datos.

### DAG-PERF-02 - Fluidez del navegador y App Usuario

- Estado: `Resuelto` el 2026-07-14 en DEV 206-207; aprobado explicitamente por el usuario.
- Tipo: rendimiento y estabilidad visual. Prioridad: P1. Esfuerzo: M. Riesgo: medio.
- Causa: el inicio mantenia dos animaciones infinitas superpuestas; Home y Web componian todo su contenido desplazable de forma eager; DAG deshabilitaba la cache HTTP, ejecutaba dos puentes JavaScript por pagina, podia inspeccionar mas de una vez la misma carga y cifraba el historial en el hilo principal.
- Solucion: mascota estatica; listas lazy en Home y Web; cache HTTP normal del WebView; saneamiento y extraccion de texto en una sola operacion; inspeccion unica por carga; patrones del clasificador precompilados; cifrado y borrado del historial fuera del hilo principal.
- Alcance comercial: no se agrego cache de consultas ni resultados Brave. Repetir una busqueda sigue consumiendo una consulta; navegar, recargar o abrir una URL/historial no consume Brave.
- Evidencia fisica SM-A235M: el repintado inactivo del inicio bajo de aproximadamente 183 cuadros en tres segundos a 3. En Web, seis desplazamientos automatizados terminaron con 5,68 % de cuadros fuera de plazo, mediana de 17 ms y percentil 90 de 21 ms.
- Seguridad: imagenes y video siguen bloqueados; el contenido permanece oculto hasta completar el analisis local; no cambio Supabase, el cupo, la barrera, la VPN ni Accessibility.
- Seguimiento DEV 207 publicado y validado: la barrera escaneaba hasta 200 nodos en cada cambio de contenido, incluso fuera de Ajustes. El escaneo queda limitado a Ajustes, instaladores, desinstaladores y las pantallas propias de accesibilidad de Samsung; estas ultimas se agregan expresamente a la proteccion.
- Resultado fisico del seguimiento con Accessibility activo: Mis apps paso de 26,05 % de cuadros lentos y percentil 99 de 200 ms a 5,76 % y 27 ms; Web quedo en 2,44 % y Home en 0,81 %. Tres aperturas de Informacion de la app terminaron en el launcher, con APK instalada, Accessibility enlazado y Device Admin activo.
- DEV 207 tambien hace la build de Usuario no depurable y optimizada, agrega perfil de arranque, reemplaza la lista pesada de 156 apps por filas nativas reciclables y publica ese inventario en lotes de 20; Supabase DEV confirmo la publicacion sin timeout. No hay cache de resultados Brave ni consumo de consultas durante estas pruebas.

## Pendientes priorizados

| ID | Estado | Pri. | Ticket | Esfuerzo | Riesgo |
| --- | --- | --- | --- | --- | --- |
| SEC-LICENSE-01 | Idea | P0 | Ciclo de vida de comunidad y licencia: alta, renovacion, vencimiento y restauracion sin perder configuracion | L | Alto |
| DATA-DELETE-01 | Idea | P0 | Borrado definitivo y auditable de usuario; la accion actual falla para todos los usuarios | L | Muy alto |
| BARRIER-A11Y-RACE-01 | Idea | P0 | Bypass rapido permite apagar Accessibility aunque Ajustes protegidos se cierre | M | Critico |
| BARRIER-DEFAULT-ON-01 | Idea | P1 | Armar automaticamente la barrera al completar y verificar la configuracion de proteccion | S | Medio |
| OPS-METRICS-01 | Idea | P1 | Medicion prolongada de bateria, trafico y estabilidad | M | Medio |
| USAGE-REAL-01 | Idea | P1 | Uso real de app foreground y estabilidad de listas | L | Alto |
| REQUESTS-UX-01 | Idea | P2 | Historial, estados y refresco manual claro de solicitudes | M | Medio |
| SUPERADMIN-TOKEN-01 | Idea | P2 | Gestion segura y auditable de tokens desde Super Admin | L | Alto |
| UI-POLISH-01 | Idea | P2 | Consistencia visual y accesibilidad de ambas apps | M | Bajo |
| USER-RESILIENCE-01 | Idea | P2 | Recuperacion guiada de estados degradados sin confundir al usuario | M | Medio |
| SUPERADMIN-MSG-01 | Idea | P2 | Avisos push y bandeja interna, no chat libre | L | Medio |
| SUPERADMIN-ALERTS-01 | Idea | P2 | Visibilidad en Super Admin de intentos de desinstalacion o manipulacion de protecciones | M | Medio |
| ADMIN-ALERTS-UX-01 | Idea | P2 | Campanita y bandeja de alertas de seguridad en App Admin, separadas de Solicitudes | M | Medio |
| BARRIER-LAUNCHER-01 | Idea | P2 | Ocultar o neutralizar la accion rapida de desinstalacion sin Device Owner ni restablecer el telefono | M | Medio |
| DAG-NAV-UX-01 | En progreso | P2 | Simplificar barra DAG: Home y nueva pestana visibles; atras, adelante y actualizar en menu | M | Medio |
| DAG-HOME-UX-01 | En progreso | P2 | Home DAG con buscador central grande e identidad de Internet kosher | S | Bajo |
| DAG-TABS-UX-01 | En progreso | P2 | Mejorar manejo cotidiano de multiples pestanas DAG | M | Medio |
| DAG-THEME-01 | En progreso | P2 | Mejorar contraste y agregar tema claro, oscuro o segun dispositivo | M | Medio |
| DAG-HISTORY-UX-01 | En progreso | P2 | Redisenar historial DAG como lista minimalista | S | Bajo |
| DAG-ANALYSIS-UX-01 | En progreso | P2 | Mostrar el analisis dentro del buscador con iluminacion neon inteligente | S | Bajo |
| DAG-APPROVAL-CACHE-01 | Idea | P1 | Reutilizar temporalmente la aprobacion de paginas ya revisadas | M | Alto |
| DAG-REVIEW-STAGING-01 | En progreso | P1 | Analizar pagina completa antes de pedir revision por un resultado incierto | M | Medio |
| USER-GREETING-01 | Idea | P2 | Personalizar el saludo de App Usuario con el nombre definido por el administrador | S | Bajo |

### DATA-DELETE-01 - Borrado definitivo y auditable de usuario

- Estado: `Idea`; no aprobado para diagnostico tecnico, codigo ni pruebas destructivas.
- Tipo: bug, ciclo de vida de datos y seguridad.
- Prioridad: P0.
- Problema: App Admin muestra la opcion de borrar usuario, pero al usarla el banner informa `No se pudo borrar al usuario` y el usuario permanece visible.
- Solucion propuesta: diagnosticar el flujo actual y definir un borrado definitivo, consistente y auditable, con confirmacion destructiva separada.
- Evidencia: el usuario reporta el 2026-07-14 que el fallo ocurre con todos los usuarios probados y que ninguno se elimina.
- Esfuerzo: L.
- Riesgo: muy alto por perdida irreversible, relaciones entre entidades y posible inconsistencia entre datos locales y remotos.
- Dependencias: definicion exacta de que datos asociados se eliminan o conservan; permisos y flujo remoto; integridad de dispositivos, reglas, grupos, solicitudes y tokens; confirmacion especifica antes de borrar o probar con datos reales.
- Duplicados y relacion: consolida el fallo observado dentro del item existente `DATA-DELETE-01`; no se crea un ticket duplicado.
- Criterios de aceptacion propuestos:
  - la accion informa con claridad que datos se eliminaran y exige confirmacion destructiva explicita;
  - el resultado es consistente en App Admin y en las fuentes de datos aplicables;
  - los fallos no dejan eliminaciones parciales ni muestran exito incorrecto;
  - existe evidencia auditable sin exponer secretos;
  - las pruebas destructivas usan datos autorizados expresamente por el usuario.
- Decisiones pendientes: borrado completo, desvinculacion o desactivacion previa; alcance sobre dispositivos y datos asociados; retencion de auditoria; estrategia de prueba segura.

### BARRIER-A11Y-RACE-01 - Bypass rapido para apagar Accessibility

- Estado: `Idea` urgente; incidente confirmado por el usuario, no aprobado todavia para diagnostico tecnico ni codigo.
- Tipo: bug de seguridad y antimanipulacion.
- Prioridad: P0.
- Problema: es posible apagar el servicio de Accessibility de App Usuario actuando muy rapido en la pantalla protegida. La barrera cierra la pantalla, pero la accion de desactivacion llega a completarse.
- Solucion propuesta: diagnosticar la carrera exacta entre el primer evento observable, el cierre defensivo y la accion del sistema; agregar una barrera preventiva que impida confirmar la desactivacion antes de que Android aplique el cambio, sin bloquear Ajustes normales ni el mantenimiento autorizado.
- Evidencia: prueba fisica informada por el usuario el 2026-07-15. El usuario logro apagar Accessibility rapidamente aunque la pantalla se cerraba. Esta evidencia nueva prevalece sobre la validacion anterior para este recorrido especifico; todavia no se conoce fabricante, version exacta ni secuencia minima reproducible.
- Esfuerzo: M estimado, sujeto al diagnostico por fabricante y version Android.
- Riesgo: critico; Accessibility es una capa central para impedir manipulacion y aplicar bloqueos de apps. Un bypass puede debilitar otras defensas aunque VPN, Device Admin y watchdog permanezcan activos.
- Dependencias: `feature-accessibility`; deteccion temprana de pantallas criticas; watchdog y alertas de componente caido; mantenimiento autorizado; validacion fisica in-place sin borrar datos.
- Duplicados y relacion: es una regresion o hueco especifico posterior a `BARRIER-ANDROID-01`; no se marca como duplicado ni se cambia el estado historico de ese cierre hasta diagnosticar el nuevo recorrido.
- Criterios de aceptacion propuestos:
  - intentos rapidos y repetidos de apagar Accessibility no logran cambiar su estado mientras la barrera esta armada;
  - la defensa actua antes de que el control nativo confirme la desactivacion, sin depender solo de cerrar la pantalla despues;
  - el mantenimiento remoto autorizado permite acceder y cambiar el estado segun el flujo previsto;
  - Ajustes no relacionados siguen disponibles;
  - si Accessibility cae por cualquier otra via, watchdog, estado y alerta remota lo detectan sin informar falsamente que sigue activa;
  - pruebas repetidas en los dispositivos fisicos soportados, actualizacion in-place y sin borrar datos;
  - tests del area, regresion de barrera, CI y handoff actualizados antes de marcarlo `Resuelto`.
- Decisiones pendientes para la entrevista del ticket: dispositivo y version Android afectados; secuencia minima; estado de Device Admin/VPN tras el bypass; comportamiento de watchdog y alerta; matriz OEM y cantidad de repeticiones para aceptar el cierre.

### BARRIER-DEFAULT-ON-01 - Barrera armada de forma predeterminada

- Estado: `Idea`; no aprobado para codigo.
- Tipo: seguridad, configuracion predeterminada y UX de onboarding.
- Prioridad: P1.
- Problema: la barrera depende de una accion posterior del administrador para quedar armada, lo que puede dejar dispositivos configurados con una proteccion antimanipulacion menor a la esperada.
- Solucion propuesta: armar automaticamente la barrera cuando App Usuario complete la activacion y verifique que Accessibility, VPN y Administrador del dispositivo estan correctamente habilitados. Mantener el desarmado como accion excepcional, protegida y auditable.
- Evidencia: propuesta del usuario el 2026-07-15 al revisar el alcance real del boton `Activar barrera de proteccion`.
- Esfuerzo: S estimado.
- Riesgo: medio; armar antes de completar permisos puede bloquear onboarding o reparacion, y una migracion incorrecta puede cambiar inesperadamente el estado de dispositivos existentes.
- Dependencias: onboarding y activacion; estado real de Accessibility, VPN y Device Admin; controles remotos de proteccion; mantenimiento temporal; recuperacion offline; sincronizacion y auditoria.
- Duplicados y relacion: complementa `BARRIER-ANDROID-01` y no resuelve el bypass registrado en `BARRIER-A11Y-RACE-01`.
- Criterios de aceptacion propuestos:
  - un dispositivo nuevo queda armado automaticamente solo despues de completar y verificar los permisos requeridos;
  - reinicios, actualizaciones y fallos transitorios de sincronizacion no desarman la barrera;
  - un componente degradado mantiene la politica armada, muestra reparacion y genera el estado o alerta correspondiente;
  - el desarmado exige una accion administrativa explicita y queda identificado de forma auditable;
  - mantenimiento y recuperacion conservan sus ventanas controladas;
  - la migracion de dispositivos existentes no bloquea onboarding, reparacion ni acceso autorizado.
- Decisiones pendientes para la entrevista del ticket: politica de migracion para dispositivos existentes; momento exacto de armado; autoridad para desarmar; presentacion del control en App Admin; comportamiento offline y ante permisos incompletos.

### ADMIN-ALERTS-UX-01 - Campanita de alertas de seguridad en App Admin

- Estado: `Idea`; las preferencias se definiran cuando el usuario pida preparar el ticket.
- Tipo: seguridad, notificaciones y UX de App Admin.
- Prioridad: P2.
- Problema: las alertas de proteccion no tienen una bandeja visual dedicada y persistente dentro de App Admin.
- Solucion propuesta: agregar un boton de campanita con leyendas para eventos como intentos de desinstalacion y otros cambios de proteccion. Las solicitudes de acceso permanecen en la ubicacion actual y no se mezclan con esta bandeja.
- Evidencia: necesidad expresada por el usuario el 2026-07-14. El handoff confirma que DEV 202 ya entrega alertas urgentes de proteccion a App Admin; esta idea cambia su organizacion visual dentro de la app.
- Esfuerzo: M.
- Riesgo: medio; debe evitar duplicados, perdida de alertas importantes, exceso de ruido y confusion con Solicitudes.
- Dependencias: eventos y FCM ya implementados; modelo de lectura e historial; navegacion y permisos de App Admin.
- Duplicados y relacion: no duplica `REQUESTS-UX-01` porque las solicitudes seguiran en su seccion actual. Extiende la experiencia resuelta tecnicamente por `PUSH-REAL-01` sin reabrir su mecanismo de entrega.
- Criterios de aceptacion propuestos:
  - App Admin ofrece una campanita identificable para consultar alertas de seguridad;
  - la bandeja distingue con leyendas claras los tipos de intento de manipulacion;
  - las solicitudes permanecen en la ubicacion y flujo actuales;
  - una misma alerta no aparece duplicada por reintentos de entrega;
  - la experiencia no debilita las notificaciones urgentes ya existentes.
- Decisiones pendientes para la entrevista del ticket: ubicacion exacta de la campanita; punto o contador; tipos de eventos; destino al tocar; estados leida/no leida; historial, retencion, filtros y agrupacion.

### BARRIER-LAUNCHER-01 - Superficie profesional sin desinstalacion rapida

- Estado: `Idea`; no aprobado para codigo.
- Tipo: seguridad, antimanipulacion y UX de App Usuario.
- Prioridad: P2.
- Problema: al mantener presionado el icono de App Usuario, el launcher muestra la opcion `Desinstalar`. Aunque la barrera actual bloquea las rutas posteriores, la opcion visible transmite una proteccion menos integrada que otros filtros.
- Solucion propuesta principal: cuando la proteccion este armada, ocultar de forma reversible el componente o alias de launcher de App Usuario, sin deshabilitar la aplicacion ni sus servicios. Ofrecer un acceso controlado alternativo, por ejemplo desde la notificacion persistente o durante una ventana de mantenimiento autorizada.
- Alternativas investigadas:
  - interceptar con Accessibility el menu contextual del launcher y cerrarlo al detectar acciones peligrosas; conserva el icono, pero depende del launcher, fabricante, idioma y version;
  - integrar Samsung Knox SDK para equipos compatibles; requiere programa/licencia Knox y no es universal, y Android 15 restringe parte de las capacidades Knox en modo Device Admin sin Device Owner/Profile Owner;
  - usar `DevicePolicyManager.setUninstallBlocked`; ofrece control de sistema, pero exige Device Owner o Profile Owner y normalmente aprovisionamiento administrado;
  - aplicacion de sistema, root o firmware propio; fuera del alcance comercial normal.
- Evidencia: observacion del usuario el 2026-07-15 al comparar con MB Smart. La documentacion oficial de Android reserva el bloqueo de desinstalacion por politica a propietarios o administradores habilitados; Samsung documenta APIs Knox de gestion de aplicaciones sujetas a compatibilidad, licencia y restricciones de administracion.
- Esfuerzo: M para la opcion principal y validacion OEM; L/XL si se incorpora Knox o una modalidad administrada.
- Riesgo: medio; ocultar el launcher sin una ruta segura de acceso puede dejar al usuario sin entrada a la app, y una intercepcion Accessibility demasiado amplia puede bloquear menus de otras aplicaciones.
- Dependencias: componente de launcher de App Usuario; estado armado y mantenimiento remoto; notificacion foreground; validacion fisica en los Samsung soportados; politicas de Google Play si se distribuye publicamente.
- Duplicados y relacion: extiende `BARRIER-ANDROID-01`, ya resuelto para las rutas posteriores de manipulacion, sin afirmar que Android normal equivalga a MDM ni reabrir la garantia de Device Owner.
- Criterios de aceptacion propuestos:
  - con la proteccion armada no existe una accion rapida visible para desinstalar App Usuario desde su icono, o el menu se neutraliza antes de permitir una accion peligrosa;
  - la app, VPN, Accessibility, watchdog, sincronizacion y alertas siguen funcionando con el acceso de launcher oculto;
  - existe una ruta clara y autorizada para abrir la app o restaurar temporalmente el icono;
  - desarmar o conceder mantenimiento restaura el acceso previsto sin reinstalar ni borrar datos;
  - no se ocultan ni bloquean menus de otras aplicaciones;
  - el alcance y las limitaciones se validan fisicamente por fabricante y version Android.
- Decisiones pendientes para la entrevista del ticket: ocultar completamente el icono o conservarlo e interceptar el menu; ruta alternativa de apertura; comportamiento durante mantenimiento; fabricantes soportados; evaluar o descartar Knox como linea separada.

### Checklist de mejoras visuales y navegacion DAG

#### DAG-NAV-UX-01 - Barra de navegacion simplificada

- Estado: `En progreso`; aprobado por el usuario el 2026-07-15 e implementado en DEV 224, pendiente prueba fisica. Tipo: UX y navegacion. Prioridad: P2.
- Problema: la barra expone controles secundarios y resta espacio y claridad a las acciones principales.
- Solucion propuesta: ocultar `Atras`, `Adelante` y `Actualizar` de la barra y moverlos al menu de tres puntos; mostrar `Home` a la izquierda y reemplazar `Actualizar` por una accion visible de nueva pestana.
- Evidencia: checklist visual aportado por el usuario el 2026-07-15.
- Esfuerzo: M. Riesgo: medio; esconder acciones frecuentes puede reducir descubribilidad o accesibilidad.
- Dependencias: navegacion WebView, menu de tres puntos, Home DAG y gestion de pestanas.
- Duplicados y relacion: se relaciona con `DAG-TABS-02`, pero cambia la jerarquia de controles y no duplica el selector ya resuelto.
- Criterios de aceptacion propuestos: Home y nueva pestana son acciones visibles; atras, adelante y actualizar funcionan desde el menu; los estados no disponibles se representan correctamente; no se pierde historial ni estado de pestanas.
- Decisiones pendientes para el ticket: iconografia, orden del menu, gestos alternativos y comportamiento del boton fisico Atras.

#### DAG-HOME-UX-01 - Home con buscador central

- Estado: `En progreso`; aprobado por el usuario el 2026-07-15 e implementado en DEV 224, pendiente prueba fisica. Tipo: UX, identidad y Home DAG. Prioridad: P2.
- Problema: la entrada del navegador no comunica con suficiente claridad que DAG es un buscador propio de Internet kosher.
- Solucion propuesta: presentar un buscador grande y centrado, con marca `DAG` y una leyenda como `Internet kosher` u otra frase a definir.
- Evidencia: checklist visual aportado por el usuario el 2026-07-15.
- Esfuerzo: S. Riesgo: bajo; el texto elegido debe evitar promesas absolutas que excedan la proteccion tecnica real.
- Dependencias: Home DAG, identidad visual, accesibilidad y textos de producto.
- Duplicados y relacion: evoluciona la Home del navegador ya implementado; no modifica el motor de busqueda ni la clasificacion.
- Criterios de aceptacion propuestos: buscador central claro y enfocable; identidad DAG visible; buen comportamiento con teclado, tamanos de fuente y orientaciones soportadas; ninguna afirmacion engañosa sobre cobertura.
- Decisiones pendientes para el ticket: leyenda definitiva, tamanos, contenido auxiliar y estados vacio/cargando/error.

#### DAG-TABS-UX-01 - Manejo mejorado de pestanas

- Estado: `En progreso`; aprobado por el usuario el 2026-07-15 e implementado en DEV 224, pendiente prueba fisica. Tipo: UX y gestion de pestanas. Prioridad: P2.
- Problema: el manejo actual de multiples pestanas necesita una experiencia mas rapida y clara para crear, identificar, cambiar y cerrar pestanas.
- Solucion propuesta: evolucionar el selector visual existente y coordinarlo con el nuevo boton visible de nueva pestana.
- Evidencia: checklist visual aportado por el usuario el 2026-07-15.
- Esfuerzo: M. Riesgo: medio; cambios de ciclo de vida WebView pueden aumentar memoria o perder estado de navegacion.
- Dependencias: `DAG-TABS-02`, presupuesto de memoria, recuperacion del renderer, historial y barra de navegacion.
- Duplicados y relacion: seguimiento de `DAG-TABS-02`, que permanece resuelto; este item captura mejoras posteriores sin reabrir aquel alcance.
- Criterios de aceptacion propuestos: crear, cambiar y cerrar pestanas es evidente; la pestana activa se distingue; se preserva el limite de recursos; cerrar la ultima pestana vuelve a un estado seguro; no se mezclan historiales entre pestanas.
- Decisiones pendientes para el ticket: limite de pestanas, persistencia entre sesiones, orden, miniaturas, cierre masivo y recuperacion tras caida.

#### DAG-THEME-01 - Contraste y tema visual

- Estado: `En progreso`; aprobado por el usuario el 2026-07-15 e implementado en DEV 224, pendiente prueba fisica. Tipo: accesibilidad visual y personalizacion. Prioridad: P2.
- Problema: la barra ubicada en la zona superior cercana a la camara tiene contraste insuficiente y algunos iconos se distinguen poco.
- Solucion propuesta: corregir contraste, incluyendo iconos negros cuando corresponda, y agregar en el menu de tres puntos las opciones `Claro`, `Oscuro` y `Segun el dispositivo`.
- Evidencia: observacion visual del usuario el 2026-07-15.
- Esfuerzo: M. Riesgo: medio; el tema del navegador, barras del sistema y contenido WebView pueden quedar inconsistentes.
- Dependencias: tema Compose, barras del sistema, iconos, WebView y persistencia local de preferencia.
- Duplicados y relacion: puede agruparse con `UI-POLISH-01`, pero se conserva separado por afectar especificamente DAG y su menu.
- Criterios de aceptacion propuestos: contraste legible en claro y oscuro; iconos coherentes con el fondo y la zona de camara; preferencia persistente; `Segun el dispositivo` sigue cambios del sistema; contenido oculto durante analisis conserva su proteccion.
- Decisiones pendientes para el ticket: tema predeterminado, alcance sobre paginas web, colores finales y matriz de dispositivos con perforacion o notch.

#### DAG-HISTORY-UX-01 - Historial minimalista

- Estado: `En progreso`; aprobado por el usuario el 2026-07-15 e implementado en DEV 224, pendiente prueba fisica. Tipo: UX e historial local. Prioridad: P2.
- Problema: la presentacion actual del historial puede simplificarse para escanear y reabrir entradas con menos ruido visual.
- Solucion propuesta: mostrar una lista minimalista con jerarquia clara y acciones discretas, preservando el almacenamiento local cifrado y el borrado existente.
- Evidencia: checklist visual aportado por el usuario el 2026-07-15.
- Esfuerzo: S. Riesgo: bajo; una simplificacion excesiva puede ocultar fecha, sitio o acciones importantes.
- Dependencias: historial cifrado DAG, navegacion, tema y accesibilidad.
- Duplicados y relacion: mejora visual del historial ya implementado; no propone ampliar datos guardados ni telemetria.
- Criterios de aceptacion propuestos: lista facil de recorrer; apertura correcta en pestana; borrado claro y consistente; estados vacio y error; sin guardar consultas, contenido adicional ni datos fuera del alcance vigente.
- Decisiones pendientes para el ticket: campos visibles, agrupacion por fecha, favicon, acciones por fila y borrado individual o por periodo.

#### DAG-ANALYSIS-UX-01 - Analisis integrado en el buscador

- Estado: `En progreso`; aprobado por el usuario el 2026-07-15 e implementado en DEV 224, pendiente prueba fisica. Tipo: UX, feedback visual y marca DAG. Prioridad: P2.
- Problema: el mensaje separado `DAG esta analizando la pagina` ocupa espacio y no integra el estado de seguridad con la identidad del buscador.
- Solucion propuesta: durante el analisis, iluminar el propio buscador con colores neon y una animacion de aspecto inteligente, mostrando dentro de esa zona la leyenda `Analizando...`.
- Evidencia: propuesta visual del usuario el 2026-07-15.
- Esfuerzo: S. Riesgo: bajo; animaciones intensas pueden afectar rendimiento, accesibilidad o distraer.
- Dependencias: estado de analisis local, buscador/barra DAG, temas claro y oscuro, preferencias de movimiento reducido y optimizaciones de rendimiento vigentes.
- Duplicados y relacion: complementa `DAG-HOME-UX-01` y `DAG-THEME-01`; no cambia el clasificador ni permite mostrar contenido antes de finalizar el analisis.
- Criterios de aceptacion propuestos: el buscador comunica claramente `Analizando...`; el contenido sigue oculto hasta una decision segura; la animacion es fluida, acotada y legible en ambos temas; existe una presentacion reducida para accesibilidad; no agrega recomposiciones o trabajo continuo costoso.
- Decisiones pendientes para el ticket: paleta neon, intensidad, duracion, comportamiento fuera de Home y tratamiento de movimiento reducido.

#### DAG-REVIEW-STAGING-01 - Revision despues del analisis completo

- Estado: `En progreso`; aprobado y ejecutado por pedido del usuario el 2026-07-15, pendiente prueba fisica de DEV 225.
- Tipo: seguridad Web, precision local y UX. Prioridad: P1. Esfuerzo: M. Riesgo: medio.
- Causa: un resultado podia quedar incierto por su fragmento breve y pedir revision antes de inspeccionar la pagina; una unica palabra ambigua en miles de caracteres o una pagina JavaScript aun vacia tambien producia falsos positivos.
- Alcance: preanalisis oculto de resultados inciertos, dos reintentos acotados de extraccion y contexto de pagina para menciones ambiguas aisladas. No cambia listas, bloqueos explicitos, reglas Admin ni analisis visual.
- Aceptacion: una pagina normal no pide revision por un fragmento o hidratacion tardia; el contenido permanece oculto hasta decidir; una pagina realmente incierta sigue ofreciendo revision; bloqueos conocidos mantienen prioridad.

#### DAG-APPROVAL-CACHE-01 - Reutilizacion temporal de aprobaciones

- Estado: `Idea`; no aprobado para codigo ni cambios de datos. Tipo: seguridad, cache de decisiones y UX. Prioridad: P1.
- Problema: una pagina ya aprobada puede volver a exigir analisis o revision en cada apertura, generando espera y solicitudes repetidas aunque la aprobacion sea reciente.
- Solucion propuesta: conservar una aprobacion con vencimiento y permitir reaperturas directas durante un periodo configurable, por ejemplo una semana o un mes, sujeto a reglas claras de alcance e invalidacion.
- Evidencia: necesidad expresada por el usuario el 2026-07-15.
- Esfuerzo: M. Riesgo: alto; el contenido o propietario de una pagina puede cambiar durante la vigencia y una aprobacion demasiado amplia podria autorizar rutas no revisadas.
- Dependencias: modelo comun `WebPolicyDecision`; aprobaciones Admin; historial cifrado; reglas por dispositivo; version del clasificador y politica; reloj y vencimiento; privacidad y sincronizacion.
- Duplicados y relacion: se relaciona con `WEB-CACHE-01`, que propone cache local acotada de reputacion sin URL ni historial. No se marca como duplicado hasta definir si la aprobacion aplica a dominio, URL exacta o decision administrativa persistida.
- Criterios de aceptacion propuestos: una aprobacion vigente evita revisiones repetidas solo dentro de su alcance exacto; toda entrada tiene origen, momento y vencimiento; cambios de politica, revocacion, version incompatible o indicadores de riesgo invalidan la reutilizacion; al vencer se vuelve a analizar o revisar; no se guarda contenido, HTML ni consultas; el administrador puede conocer y revocar la aprobacion aplicable.
- Decisiones pendientes para el ticket: duracion predeterminada y maxima; dominio, subdominio o URL exacta; quien puede aprobar; almacenamiento local o sincronizado; invalidaciones por cambio de contenido/modelo/politica; reapertura inmediata o analisis local abreviado.

### USER-GREETING-01 - Saludo personalizado en App Usuario

- Estado: `Idea`; no aprobado para codigo. Tipo: UX y personalizacion de App Usuario. Prioridad: P2.
- Problema: el encabezado superior muestra un saludo generico y no identifica al usuario protegido que esta usando la aplicacion.
- Solucion propuesta: reemplazar `Hola` por `Hola, {nombre}`, usando el nombre del usuario definido por el administrador.
- Evidencia: solicitud del usuario el 2026-07-15.
- Esfuerzo: S. Riesgo: bajo; deben contemplarse nombre ausente, sincronizacion tardia, longitud, caracteres especiales y privacidad en pantalla.
- Dependencias: nombre del usuario protegido disponible en App Usuario; sincronizacion y estado local; encabezado de Home; accesibilidad y tamanos de fuente.
- Duplicados y relacion: puede agruparse visualmente con `UI-POLISH-01`, pero se conserva como entrada separada por depender del dato remoto definido por Admin.
- Criterios de aceptacion propuestos: el encabezado muestra el nombre correcto del usuario activo; los cambios realizados por el administrador se reflejan tras la sincronizacion prevista; existe un fallback simple si el nombre falta; nombres largos no rompen el layout; no se muestra el nombre de otro usuario o dispositivo.
- Decisiones pendientes para el ticket: puntuacion y texto exactos; fallback; longitud maxima o truncado; momento de actualizacion; tratamiento de privacidad en capturas y pantalla bloqueada.

### SUPERADMIN-ALERTS-01 - Alertas de manipulacion en Super Admin

- Estado: `Idea`; las preferencias se definiran al aprobar un futuro ticket que toque Super Admin.
- Tipo: seguridad, alertas y UX de Super Admin.
- Prioridad: P2.
- Problema: los intentos de borrar la App Usuario o modificar Ajustes protegidos deben poder advertirse tambien desde Super Admin, ademas de los canales ya implementados.
- Solucion propuesta: incorporar en Super Admin una representacion de los eventos de manipulacion existentes, sin definir todavia canal, destinatarios, inmediatez ni retencion.
- Evidencia: necesidad expresada por el usuario el 2026-07-14. El handoff confirma que DEV 202 ya crea eventos y envia alertas urgentes a App Admin ante intentos de manipulacion.
- Esfuerzo: M, sujeto a las preferencias y al alcance tecnico que se definan durante la entrevista del ticket.
- Riesgo: medio; una mala politica de destinatarios, retencion o frecuencia podria exponer informacion sensible o generar exceso de alertas.
- Dependencias: eventos de proteccion ya existentes; alcance y estado de la web Super Admin; definicion futura de permisos, destinatarios, canal, frecuencia y retencion.
- Duplicados y relacion: se relaciona con `SUPERADMIN-MSG-01`, que propone avisos push y bandeja interna. No se marca como duplicado hasta decidir si las alertas de seguridad forman parte de esa bandeja general o requieren un flujo separado.
- Criterios de aceptacion propuestos:
  - Super Admin puede identificar el intento de desinstalacion o cambio de Ajustes protegidos y asociarlo con la comunidad y el dispositivo correctos;
  - la visualizacion respeta los permisos y destinatarios que se definan al aprobar el ticket;
  - no se duplican eventos ni se debilitan las alertas existentes de App Admin;
  - el alcance final documenta canal, inmediatez, retencion y tratamiento de eventos repetidos.
- Decisiones pendientes: bandeja web, notificacion del navegador u otro canal; destinatarios; eventos exactos incluidos; aviso inmediato o diferido; retencion, lectura y agrupacion; integracion o separacion respecto de `SUPERADMIN-MSG-01`.

## Roadmap DAG, Web e IA local

### DAG-FOUNDATION-01 - Entrada, control y atajo seguro

- Estado: `Resuelto` el 2026-07-14 en DEV 204.
- Tipo: arquitectura y UX. Prioridad: P1. Esfuerzo: S. Riesgo: bajo.
- Alcance cerrado: control `Abierto/Cerrado` por dispositivo desde App Admin; entrada en Web de App Usuario; pantalla propia cerrada por defecto; atajo opcional fijado por Android que abre DAG dentro de la misma app.
- Seguridad: esta base no ejecuta busquedas, no abre sitios y no muestra imagenes ni videos. No incorpora WebView, fuente de resultados, modelo de IA ni revision humana.
- Persistencia: reutiliza las reglas de politica y la sincronizacion DEV existentes; no agrega tabla, migracion ni credencial nueva.
- Aceptacion cumplida: DAG solo abre con una regla `Allow` explicita; cerrar revoca el acceso; otros cambios Web no alteran su estado; tests de dominio, Admin y Usuario cubren el valor por defecto y la independencia de preferencias.
- Validacion fisica: ciclo Admin abierto -> Usuario Web -> pantalla preventiva -> atajo Android -> Admin cerrado -> atajo cerrado aprobado en Samsung SM-A235M; DAG quedo cerrado y las protecciones del dispositivo activas.

### DAG-BROWSER-01A - Navegador protegido funcional

- Estado: `Resuelto` el 2026-07-15 en DEV 208; aprobado explicitamente por el usuario el 2026-07-14.
- Prioridad: P1. Esfuerzo: XL. Riesgo: alto.
- Vision acordada: primer corte util del navegador propio DAG, con experiencia cotidiana parecida a Chrome pero conservadora y cerrada por defecto.
- Alcance aprobado: barra combinada para buscar o escribir URL; resultados reales; navegacion interna; atras, adelante, recargar e inicio; una pestaña; formularios e inicio de sesion.
- Privacidad: historial cifrado solo en el telefono con busquedas, paginas, titulo y fecha. Solo el usuario puede verlo y borrar elementos o todo el historial. No se sincroniza, no entra en backups y no se envia al administrador.
- Clasificacion: consulta y contenido textual se procesan localmente en espanol, hebreo e ingles. Salidas `Permitida`, `Bloqueada` o `Incierta`; ante error, modelo ausente o baja confianza se bloquea.
- Fuente: solo una consulta permitida puede llegar a Brave Search mediante una Edge Function autenticada en Supabase DEV. La credencial de Brave queda como secreto servidor y Android conserva solo credenciales publicas y su sesion/dispositivo.
- Modelo comercial del piloto: USD 1 mensual con 100 busquedas por dispositivo y navegacion directa ilimitada. El cupo queda configurable por comunidad y no existe sobreconsumo automatico.
- Costos y privacidad: Supabase conserva solo `device_id`, mes y contador para aplicar el cupo; no guarda consultas, resultados ni historial. Brave aplica USD 5 de credito global mensual a la cuenta; no es un credito por usuario.
- Revision humana: un sitio incierto puede generar una solicitud de dominio. La consulta y el historial no se incluyen; el administrador recibe solo dominio, titulo y motivo.
- Seguridad inicial: imagenes, video, descargas, popups, camara, microfono, ubicacion, archivos e intents externos permanecen bloqueados. El contenido no se hace visible antes de la evaluacion local.
- Revocacion: cerrar DAG desde App Admin impide buscar o navegar, incluso al entrar desde el atajo fijado.
- Fuera de alcance de este corte: imagenes y video clasificados, multiples pestanas, descargas, historial remoto, inspeccion de navegadores externos y analisis continuo de pantalla.
- Aceptacion cumplida: pruebas de dominio/UI/red, ausencia de consultas en logs, aislamiento del secreto, fallo cerrado, borrado local de historial, revision sin consulta ni historial, revocacion remota y validacion fisica completa en el Samsung conectado.

### DAG-STANDALONE-01 - Experiencia de navegador independiente

- Estado: `Resuelto` el 2026-07-15 en DEV 209; entrevista completada y ticket aprobado explicitamente por el usuario.
- Tipo: arquitectura Android y UX. Prioridad: P1. Esfuerzo: L. Riesgo: medio.
- Objetivo: que DAG se perciba y funcione como un navegador independiente, con icono propio en el launcher, tarea propia en Recientes y apertura directa, en lugar de sentirse como una pantalla mas de App Usuario.
- Arquitectura elegida por el usuario el 2026-07-15: un launcher y una tarea independientes dentro del mismo APK firmado de App Usuario. DAG comparte activacion, actualizacion, historial cifrado, regla por dispositivo, revocacion y barrera sin exigir instalar ni activar una segunda aplicacion.
- Alternativa descartada para este ticket: un APK y paquete Android separados. No se duplicaran instalacion, activacion, actualizaciones, comunicacion segura ni resistencia a desinstalacion.
- Decisiones cerradas en la entrevista:
  - nombre visible `DAG`;
  - icono nuevo con lenguaje visual parecido a un navegador moderno, pero con identidad DAG;
  - App Usuario conserva la entrada DAG dentro de Web;
  - el icono independiente se habilita automaticamente cuando DAG esta permitido y desaparece del launcher cuando el administrador lo cierra;
  - Inicio es minimalista y prioriza una unica barra de busqueda;
  - Atras navega primero dentro de la pagina, luego vuelve a Inicio y un nuevo Atras cierra DAG;
  - DAG aparece como tarjeta propia en Recientes, separada de App Usuario;
  - ambas entradas comparten el mismo historial local cifrado;
  - la apariencia se inspira en Chrome, sin copiar su marca, y usa colores e identidad DAG.
- Alcance implementado: icono DAG propio; identidad visual de navegador; apertura directa desde launcher; tarea separada en Recientes; acceso adicional desde App Usuario; visibilidad del icono ligada a la regla remota; cierre remoto inmediato; una sola fuente segura de politica e historial; sin duplicar consumo Brave.
- Fuera de alcance hasta otro ticket aprobado: habilitar imagenes o video, multiples pestanas, descargas y debilitar cualquiera de los bloqueos actuales.
- Criterios de aceptacion cumplidos:
  - DAG puede iniciarse desde su propio icono y aparece como experiencia separada en Recientes;
  - no requiere una segunda instalacion o activacion ni duplica credenciales;
  - cerrar DAG desde Admin bloquea tambien la entrada independiente sin demora ni estado obsoleto;
  - al cerrar DAG, su icono desaparece del launcher; al reabrirlo vuelve automaticamente sin reinstalar ni reactivar;
  - actualizacion in-place conserva activacion, historial, Device Admin, Accessibility y VPN;
  - el usuario reconoce visualmente Inicio, barra de direccion, historial y estado cerrado como partes de un navegador;
  - no aumenta el consumo Brave por relanzar, recargar o alternar entre App Usuario y DAG.
- Validacion fisica: Samsung actualizo el icono dentro del ciclo remoto probado; al cerrar desaparecio del cajon, el lanzamiento explicito quedo deshabilitado y la tarea abierta se retiro. Al reabrir reaparecio sin reinstalacion. Un atajo fijado manualmente en DEV 204-208 puede permanecer en la pantalla de inicio porque Android reserva su eliminacion al usuario; ya no se crean atajos nuevos y el acceso historico conserva fallo cerrado.

### DAG-IMAGES-01 - Clasificacion local de imagenes

- Estado: `Resuelto` el 2026-07-15 en DEV 210; aprobado explicitamente por el usuario.
- Objetivo cumplido: mostrar fotos dentro de DAG sin exponerlas antes de analizarlas ni enviarlas a un servicio externo.
- Alcance: clasificacion on-device de JPEG, PNG y WebP estaticos; score seguro `<= 0.08`; score bloqueado `>= 0.20`; zona incierta y cualquier error bloqueados. DEV 214 agrega fuentes lazy comunes, fondos CSS HTTPS y hasta 80 imagenes por pagina. Imagenes animadas, SVG, video y audio permanecen cerrados.
- Privacidad y revision: imagen, URL, score y decision nunca se sincronizan. Admin solo recibe solicitudes por sitios inciertos, no por imagenes.
- Seguridad y recursos: maximo 80 imagenes por pagina, 4 MiB por recurso, timeout de 8 segundos, solo HTTPS, destinos publicos, sin downgrade y barreras tempranas contra `blob:` y Service Workers.
- Evidencia: pruebas unitarias de politica/red, validacion Gradle integral y carga fisica de fotos seguras en SM-A235M con APK publico DEV 210. Actualizacion in-place con Device Admin, Accessibility y VPN preservados; cero consultas Brave.
- Limitacion explicita: el modelo es probabilistico y no puede garantizar 100 % de aciertos. Clasificar video requiere `DAG-VIDEO-01`, aun no aprobado.

### AI-SEARCH-01A - Intencion semantica local compacta

- Estado: `En progreso` en DEV 212; aprobado por el usuario al pedir continuar con IA local. Falta prueba fisica.
- Objetivo implementado: sumar comprension estadistica local de sinonimos y evasiones simples sin reemplazar las reglas duras ni enviar contenido fuera del telefono.
- Modelo: clasificador lineal propio de 112 KiB sobre n-gramas de caracteres y palabras, con siete categorias y cobertura inicial espanol/ingles/hebreo. No es un LLM ni un transformer y no promete comprension humana.
- Decision: una categoria riesgosa con confianza alta bloquea; confianza media o contexto sensible quedan inciertos; general permite solo si ninguna capa anterior bloqueo. Modelo ausente o invalido falla cerrado.
- Datos y costo: 557 ejemplos controlados, 34 casos separados de verificacion, entrenamiento reproducible sin dependencias externas y cero costo/API adicional. Consultas, texto, scores y decisiones permanecen locales.
- Aceptacion tecnica cumplida: pruebas dirigidas, build/test/lint local integral, publicacion DEV 212 y hashes publicos verificados. Aceptacion fisica pendiente: calidad en busquedas cotidianas/riesgosas, ausencia de falsos positivos graves y fluidez en el Samsung personal.

### DAG-USAGE-01 - Contador mensual en Super Web

- Estado: `Completado y publicado`; aprobado por el usuario el 2026-07-14.
- Objetivo: mostrar en Super Web el consumo mensual de DAG casi en tiempo real, con usados, limite y restantes por comunidad y dispositivo, total general, costo Brave estimado y alertas al 80% y 100%.
- Privacidad: no exponer consultas, URLs, resultados ni historial. Super Web consulta un agregado autorizado; la tabla de contadores permanece inaccesible para clientes comunes.
- Implementacion: refresco seguro cada 10 segundos, cupo configurable por comunidad, proyeccion mensual y costo estimado luego del credito Brave global. El piloto comercial aprobado es USD 1 con 100 busquedas por dispositivo/mes.
- Aceptacion tecnica: migracion DEV aplicada, Edge Function actualizada, RPCs Super Admin aisladas de `anon`, tabla sin lectura de clientes, builds Web exitosos y Super Web Sites version 3 publicada en modo privado. La prueba fisica completa de `DAG-BROWSER-01A` se cerro en DEV 208.

### Otros tickets de roadmap

| ID | Estado | Pri. | Idea |
| --- | --- | --- | --- |
| WEB-SOURCES-01 | Idea | P1 | Evaluar nuevas fuentes Web con licencia, calidad, deduplicacion, firma y rollback |
| WEB-CACHE-01 | Idea | P1 | Cache local acotada de reputacion sin URL ni historial |
| AI-DOMAIN-01 | Idea | P1 | Clasificador local de dominios desconocidos sin descargar paginas |
| AI-SEARCH-01B | Idea | P1 | Ampliar y medir el clasificador local con corpus revisado, precision/recall y calibracion por idioma |
| NET-EVASION-01 | Idea | P2 | DoH/DoT desconocido, IP directa, proxies y VPN externas |
| AI-VISUAL-01 | Idea | P3 | Evaluacion puntual de regiones visibles, sujeta a privacidad, bateria y politicas |
| AI-SENSITIVE-01 | Idea | P3 | Riesgos sensibles en chats como proyecto separado legal y de privacidad |
| DAG-VIDEO-01 | Idea | P2 | Clasificar video localmente mediante muestreo temporal, con limites de CPU, bateria y memoria; video sigue bloqueado hasta aprobar el ticket |
| POLICY-SCHEDULE-01 | Idea | P2 | Politicas adaptativas por horario o contexto, siempre bajo control del administrador |
| WEB-AMBIGUITY-01 | Idea | P2 | Fallback seguro a SafeSearch o Solo resultados ante decisiones ambiguas |
| POLICY-EXPLAIN-01 | Idea | P2 | Explicaciones simples y solicitud de autorizacion para decisiones bloqueadas o inciertas |
| POLICY-SUGGEST-01 | Idea | P3 | Sugerencias de categorias, dominios o reglas redundantes sin aplicacion automatica |
| COMMUNITY-REVIEW-01 | Idea | P3 | Revision humana y publicacion comunitaria firmada antes de decisiones globales |
| OPS-HEALTH-ALERTS-01 | Archivado | P2 | Alertas agregadas de salud sin historial; agrupadas en alertas Admin y Super Admin |

### Ideas historicas normalizadas con ID propio

#### POLICY-SCHEDULE-01 - Politicas por horario o contexto

- Estado: `Idea`. Tipo: politica y automatizacion. Prioridad: P2. Evidencia: Google Doc historico conciliado el 2026-07-15. Esfuerzo: L. Riesgo: alto.
- Problema y solucion propuesta: permitir variaciones explicitas por horario o contexto sin reemplazar reglas deterministicas ni aplicar cambios inferidos sin control Admin.
- Dependencias: modelo de politicas, reloj y zona horaria, reglas por dispositivo, offline y auditoria.
- Aceptacion propuesta: precedencia deterministica; comportamiento offline definido; cambios visibles y revocables; sin reglas sorpresa ni debilitamiento automatico.
- Decisiones pendientes: contextos admitidos, autoridad, zona horaria, conflictos y experiencia Admin/Usuario.

#### WEB-AMBIGUITY-01 - Fallback seguro ante ambiguedad

- Estado: `Idea`. Tipo: seguridad Web y UX. Prioridad: P2. Evidencia: Google Doc historico conciliado el 2026-07-15. Esfuerzo: M. Riesgo: medio.
- Problema y solucion propuesta: cuando una decision sea ambigua, aplicar temporalmente SafeSearch o Solo resultados segun una politica explicita, en vez de permitir contenido incierto o fallar de forma confusa.
- Dependencias: `WebPolicyDecision`, DAG, SafeSearch, Solo resultados y autorizaciones Admin.
- Aceptacion propuesta: fallback cerrado y explicable; no anula bloqueos; caduca o se revoca; no guarda consulta ni contenido.
- Decisiones pendientes: fallback por categoria/confianza, duracion, mensaje y posibilidad de solicitar autorizacion.

#### POLICY-EXPLAIN-01 - Explicaciones y autorizacion

- Estado: `Idea`. Tipo: UX, transparencia y solicitudes. Prioridad: P2. Evidencia: Google Doc historico conciliado el 2026-07-15. Esfuerzo: M. Riesgo: medio.
- Problema y solucion propuesta: explicar con lenguaje simple por que una accion fue bloqueada o quedo incierta y ofrecer solicitud de autorizacion solo cuando corresponda, sin exponer reglas evadibles ni detalles sensibles.
- Dependencias: decisiones Web/apps, solicitudes, textos neutrales, privacidad y accesibilidad.
- Aceptacion propuesta: motivo entendible; una accion siguiente clara; sin datos sensibles ni pistas de evasion; estados sincronizados y sin solicitudes duplicadas.
- Decisiones pendientes: nivel de detalle, categorias visibles, eventos autorizables y textos por edad/contexto.

#### POLICY-SUGGEST-01 - Sugerencias sin aplicacion automatica

- Estado: `Idea`. Tipo: asistencia Admin y calidad de reglas. Prioridad: P3. Evidencia: Google Doc historico conciliado el 2026-07-15. Esfuerzo: L. Riesgo: medio.
- Problema y solucion propuesta: detectar posibles categorias, dominios o reglas redundantes y presentarlos como sugerencias revisables; nunca aplicar cambios automaticamente.
- Dependencias: inventario de reglas, metricas agregadas sin navegacion, explicabilidad y auditoria.
- Aceptacion propuesta: toda sugerencia muestra fundamento y alcance; aceptar/rechazar es explicito; no cambia proteccion sin confirmacion; falsos positivos medidos.
- Decisiones pendientes: fuentes de evidencia, destinatarios, frecuencia, privacidad y ciclo de descarte.

#### COMMUNITY-REVIEW-01 - Revision y publicacion comunitaria firmada

- Estado: `Idea`. Tipo: gobernanza, seguridad de contenido e infraestructura. Prioridad: P3. Evidencia: Google Doc historico conciliado el 2026-07-15. Esfuerzo: XL. Riesgo: muy alto.
- Problema y solucion propuesta: permitir revision humana antes de convertir decisiones locales en conocimiento comunitario, con publicacion firmada, trazable y reversible.
- Dependencias: roles y comunidades, moderacion, firma, versionado, rollback, privacidad, licencias y proceso de apelacion.
- Aceptacion propuesta: ninguna decision local se publica automaticamente; revisores autorizados; artefactos firmados y versionados; rollback; ausencia de consultas, historial o datos personales.
- Decisiones pendientes: gobernanza, quorum, categorias, retencion, apelaciones y responsabilidad legal.

#### OPS-HEALTH-ALERTS-01 - Alertas agregadas de salud

- Estado: `Archivado`. Tipo: operaciones y alertas. Prioridad historica: P2. Evidencia: Google Doc historico conciliado el 2026-07-15. Esfuerzo estimado: M. Riesgo: medio.
- Motivo de archivo: la idea no se borra; se agrupa en `ADMIN-ALERTS-UX-01` para App Admin y `SUPERADMIN-ALERTS-01` para Super Admin, ambos con requisito de no incluir historial de navegacion.
- Dependencias y aceptacion heredadas: eventos de salud/proteccion, deduplicacion, permisos, destinatarios y privacidad; las alertas deben ser agregadas, atribuibles al dispositivo correcto y no contener navegacion.
- Decisiones pendientes: se resolveran en los dos tickets receptores; este ID no autoriza implementacion separada.

## Conciliacion completa del Google Doc historico

Revision realizada el 2026-07-15 sobre `Backlog Codex - Control Parental`. El contenido tecnico desactualizado no reemplaza al handoff; cada idea de producto quedo mapeada sin borrarse.

| Bloque historico | Destino canonico | Resultado de conciliacion |
| --- | --- | --- |
| Ciclo de vida de comunidad y licencia | `SEC-LICENSE-01` | Conservado como P0 |
| Borrado definitivo de usuario | `DATA-DELETE-01` | Conservado y ampliado con la falla actual |
| Medicion sostenida de bateria, datos y estabilidad | `OPS-METRICS-01` | Conservado como P1 |
| Tiempo real de uso de apps y lista estable | `USAGE-REAL-01` | Conservado como P1 |
| Push real FCM | `PUSH-REAL-01` | Resuelto; estado tecnico en handoff |
| Push manual y mensajeria desde Super Admin | `SUPERADMIN-MSG-01` | Conservado como P2 |
| Solicitudes y actualizacion manual | `REQUESTS-UX-01` | Conservado como P2 |
| Super Admin: token inmediato | `SUPERADMIN-TOKEN-01` | Conservado como P2 |
| Pulido visual | `UI-POLISH-01` | Conservado como P2 |
| Resiliencia de App Usuario | `USER-RESILIENCE-01` | Conservado como P2 |
| Fuentes complementarias Web | `WEB-SOURCES-01` y `WEB-LISTS-A` | Seguimiento conservado; primera incorporacion resuelta |
| Cache local de reputacion | `WEB-CACHE-01` | Conservado como P1 |
| Clasificador local de dominios | `AI-DOMAIN-01` | Conservado como P1 |
| Clasificador local de busquedas | `AI-SEARCH-01A` y `AI-SEARCH-01B` | Primera fase en progreso; ampliacion conservada |
| Evasiones, idiomas y dominios espejo | `NET-EVASION-01` y `AI-SEARCH-01B` | Separado entre red y clasificacion |
| IA visual local | `AI-VISUAL-01` y `DAG-IMAGES-01` | Exploracion general conservada; imagenes DAG resueltas |
| Chats y riesgos sensibles | `AI-SENSITIVE-01` | Conservado como proyecto separado P3 |
| Buscador/navegador propio DAG | `DAG-FOUNDATION-01`, `DAG-BROWSER-01A` y `DAG-STANDALONE-01` | Implementado por fases; no queda como idea duplicada |
| Politicas por horario o contexto | `POLICY-SCHEDULE-01` | Normalizado con ID propio |
| Fallback SafeSearch/Solo resultados | `WEB-AMBIGUITY-01` | Normalizado con ID propio |
| Explicaciones y autorizacion | `POLICY-EXPLAIN-01` | Normalizado con ID propio |
| Sugerencias sin aplicacion automatica | `POLICY-SUGGEST-01` | Normalizado con ID propio |
| Revision humana y publicacion comunitaria firmada | `COMMUNITY-REVIEW-01` | Normalizado con ID propio |
| Alertas agregadas sin historial | `OPS-HEALTH-ALERTS-01` | Archivado por agrupacion, con destinos explicitos |
| Decisiones comerciales: familias, escuelas, comunidades, pagos externos y multiples admins | Decisiones de producto vigentes | Conservadas como decisiones, no como tickets duplicados |
| Baseline, versiones, APK, FCM y cierres DEV 188-192 | `docs/HANDOFF_ACTUAL.md` y resueltos relevantes | Reconciliados como historia tecnica; prevalece el handoff |
| Bloquear imagenes | Regla de exclusion historica | Eliminado previamente y no debe reaparecer |

## Decisiones de producto vigentes

- El producto apunta a familias, escuelas y comunidades independientes.
- Los pagos se reciben fuera de la plataforma; Super Admin habilita o renueva licencias manualmente.
- Una comunidad puede tener varios administradores.
- Al vencer una licencia se desactiva la proteccion sin borrar configuraciones; renovar debe restaurarla.
- La resistencia tipo Rimon reforzada es la estrategia para Android normal. Sin Device Owner no existe garantia absoluta contra modo seguro, ADB autorizado previamente o restablecimiento de fabrica.

## Limitaciones conocidas

- UT1 y fuentes complementarias no cubren todo Internet; ausencia no equivale a bypass.
- Resolvers cifrados desconocidos, IP directa y evasiones avanzadas requieren un ticket de red propio.
- Otros navegadores pueden quedar en modo best effort.
- IP compartida con un dominio bloqueado puede afectar temporalmente otro sitio.
- La opcion visual de SafeSearch puede aparecer aunque la red fuerce el destino estricto.
- Falta medicion prolongada de bateria y trafico.
- El baseline congela codigo/APK, no datos ni una copia de Supabase; las listas Web son dinamicas.

## Resueltos relevantes

| ID | Resuelto | Evidencia resumida |
| --- | --- | --- |
| DAG-IMAGE-COMPAT-02 | 2026-07-15, DEV 223 | Sitios densos esperan turno visual; 160 recursos, formatos raster por contenido y HEIF/HEIC; SVG/animaciones continúan cerrados; pendiente prueba física en Frávega |
| DAG-WEBVIEW-THREAD-01 | 2026-07-15, DEV 222 | Intercepcion de recursos deja de consultar WebView desde el hilo de red; usa URL principal volatil y prueba unitaria; pendiente confirmacion fisica |
| DAG-IMAGE-STABILITY-01 | 2026-07-15, DEV 221 | Clasificacion visual limitada a tres trabajos concurrentes, 80 recursos y timeout de 5 s; build/tests correctos, pendiente validacion fisica sin telefono disponible |
| DAG-TABS-02 | 2026-07-15, DEV 220 | Selector visual de dos columnas con miniaturas locales solo de páginas aprobadas, cierre/nueva pestaña y sin persistencia ni consumo Brave |
| DAG-RESILIENCE-01 | 2026-07-15, DEV 218 | Caída del renderer WebView contenida por pestaña y retorno recuperable a Home; presupuesto visual reducido para limitar presión de memoria |
| DAG-SEARCH-BYPASS-01 | 2026-07-15, DEV 218 | Bloqueo no anulable de Google y principales buscadores web alternativos; Gmail/Maps permanecen separados |
| DAG-TABS-01 | 2026-07-15, DEV 217 | Pestañas con alta, cambio y cierre; resultados se restauran sin otra consulta Brave y páginas suspendidas no conservan múltiples WebView en memoria |
| DAG-COMPAT-01 | 2026-07-15, DEV 217 | Área segura superior, barra sin recorte, Home con mascota y compatibilidad ampliada para imágenes lazy/picture sin relajar clasificación local |
| DAG-UX-02 | 2026-07-15, DEV 216 | Una sola franja con navegacion, barra, recarga y menu; WebView y resultados de ancho completo sin bordes laterales; Brave permanece como motor |
| DAG-UX-01 | 2026-07-15, DEV 215 | Barra combinada compacta, resultados tipo navegador, teclado automatico y controles uniformes; prueba fisica en SM-A235M y Android CI exitoso |
| DAG-IMAGES-01 | 2026-07-15, DEV 210 | Fotos estaticas clasificadas localmente antes de mostrarse; fallo cerrado, privacidad on-device y prueba fisica con APK publico sin consumir Brave |
| DAG-STANDALONE-01 | 2026-07-15, DEV 209 | Icono y tarea DAG propios dentro del mismo APK; cierre remoto oculta launcher y retira tarea; reapertura fisica sin reinstalar |
| DAG-BROWSER-01A | 2026-07-15, DEV 208 | Busqueda y navegacion protegidas, historial local cifrado, revision incierta y revocacion remota; ciclo fisico completo y reapertura limpia |
| DAG-FOUNDATION-01 | 2026-07-14, DEV 204 | Control por dispositivo, entrada Usuario, pantalla cerrada por defecto y atajo Android; sin abrir contenido ni agregar esquema |
| PUSH-REAL-01 | 2026-07-14, DEV 202 | FCM real con tokens seguros y credenciales servidor dedicadas; alerta fisica con Admin cerrado y apertura correcta en Apps |
| USER-PERF-01 | 2026-07-14, DEV 203 | Suscripciones aisladas por pantalla, refresco remoto duplicado eliminado y una unica accion contextual; Home 2,99 % -> 0,26 % y Ajustes 0,66 % -> 0,43 % de frames lentos en SM-A235M |
| DAG-PERF-02 | 2026-07-14, DEV 206-207 | Home/Web lazy y DAG sin trabajo duplicado; DEV 207 elimina el escaneo global de Accessibility y reduce Mis apps de 26,05 % a 5,76 % de cuadros lentos con la barrera activa |
| BARRIER-ANDROID-01 | DEV 198-202 | Barrera reforzada, navegacion defensiva, recuperacion y alertas; alcance real documentado en el handoff |
| WEB-LISTS-A | 2026-07-13 | The Block List Project agregado, deduplicado, firmado, publicado atomicamente y validado |
| WEB-SENSITIVE-A1 | DEV 192 | Adulto, apuestas, drogas y pirateria/torrents en formato extensible firmado y con validacion fisica |
| WEB-BASELINE-191 | DEV 188-191 | Decisiones Web comunes, UT1, SafeSearch, recuperacion VPN, conexiones e invalidaciones estabilizadas |

`Bloquear imagenes` fue eliminado y no debe reaparecer como pendiente. El historial tecnico detallado queda en `docs/HANDOFF_ACTUAL.md`.

## Prompt para el chat dedicado de backlog

Copiar desde aqui:

> Trabaja unicamente en `/Users/yejielnehmad/Developer/content-filter`. No uses ni revises copias en Documents. Antes de actuar lee completos `AGENTS.md`, `START_HERE.md`, `docs/HANDOFF_ACTUAL.md` y `docs/BACKLOG_PRODUCTO.md`, y sigue la jerarquia de fuentes definida alli. Este chat es exclusivo para capturar, entrevistar, clasificar, deduplicar y mantener ideas y tickets de producto. El Google Doc `Backlog Codex - Control Parental` es una bandeja de entrada historica; `docs/BACKLOG_PRODUCTO.md` es el backlog canonico y `docs/HANDOFF_ACTUAL.md` es la verdad tecnica. Primero entrevistame. No escribas codigo, no modifiques Android, no uses Supabase ni publiques APKs hasta que yo apruebe explicitamente un ticket. Para cada idea asigna ID, estado, tipo, prioridad, evidencia, esfuerzo, riesgo, dependencias y criterios de aceptacion. No borres ideas: agrupalas, reclasificalas o archivalas con motivo. Si un dato tecnico entra en conflicto, prevalece el handoff. Cuando confirmemos una entrada, actualiza el backlog del repo y sube solamente ese cambio documental con `[skip ci]`; no hagas build ni cambies `versionCode`. Usa solo Supabase DEV `syeycayasyufedwoprea` si un futuro ticket aprobado lo requiere; nunca Production, nunca borres datos sin mi confirmacion especifica y nunca pongas Service Role Key en Android. Al cerrar, mostra exactamente que entrada cambiaste y el estado de Git.

# BACKLOG DE PRODUCTO

Ultima sincronizacion: 2026-07-22

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

Modalidad del chat dedicado de backlog:

- El usuario puede volcar ideas desordenadas, incompletas o encadenadas sin responder una entrevista en ese momento.
- Codex captura cada idea, asigna un ID estable, registra evidencia y decisiones pendientes, detecta duplicados y la deja en estado `Idea`.
- Codex no interpreta la captura como aprobacion para codigo ni interrumpe el flujo de ideas con preguntas de detalle.
- Cuando el usuario pide preparar o elegir trabajo, Codex agrupa las ideas relacionadas en tickets pequenos, propone orden y dependencias, y recien entonces entrevista las decisiones que cambian alcance o aceptacion.

Flujo de una entrada:

1. Capturar la idea en el chat dedicado o importarla desde una fuente historica.
2. Normalizarla como `Idea`, sin exigir definiciones prematuras.
3. Al preparar trabajo, deduplicar y agrupar por tickets pequenos; entrevistar lo necesario.
4. Pasar a `Propuesto` y esperar aprobacion explicita del primer ticket.
5. Implementar el ticket aprobado en un chat de ejecucion, con alcance pequeno.
6. Al cerrar, actualizar este archivo y `docs/HANDOFF_ACTUAL.md` en el mismo commit.

## Ancla tecnica actual

- Estado publicado: App Usuario DEV 271 y App Admin DEV 271, `1.0.1-dev`.
- Baseline de recuperacion Web: `stable/dev-191-web-protection` (no representa la ultima version publicada).
- FCM real y alertas de proteccion ya estan implementados y validados en DEV 202.
- Los detalles, hashes, commits y evidencias vigentes viven unicamente en `docs/HANDOFF_ACTUAL.md` y `docs/BASELINES.md`.

## Ultimos tickets trabajados

### ANDROID-FLAT-LISTS-UX-01 - Listas simples, compactas y modernas

- Estado: `Validacion fisica parcial ampliada en SM-A235M DEV 270`. Aprobado explicitamente el 2026-07-21. Tipo: UX Android compartida. Prioridad: P2. Esfuerzo: M. Riesgo: bajo.
- Alcance: reemplazar pilas de tarjetas repetidas por filas continuas con separadores sutiles en Usuarios Admin, Solicitudes y Avisos de ambas apps. En Usuario, `Mis apps` conserva el `ListView` nativo reciclable y cambia solamente su presentacion.
- Decision visual: mantener tarjetas solo cuando expresan una unidad importante, como proteccion, licencia, alertas o formularios complejos. No agregar una biblioteca externa: Compose Material 3 y los componentes nativos actuales cubren el patron.
- Aceptacion alcanzada: matriz local completa de 1.845 tareas y Android CI correctos; APK Usuario/Admin DEV 268 publicados juntos mediante el workflow manual oficial y verificados por manifiesto, hash, paquete, version y certificado. En DEV 270/SM-A235M se recorrieron Usuarios, Solicitudes, Cuenta, Web, Mis apps, Internet y Ajustes con filas continuas y sin superposicion; faltan Avisos y listas con sitios, grupos u horarios configurados. No se tocaron backend, datos ni Production.

### ANDROID-COMPAT-FOUNDATION-01 - Matriz y certificación Android

- Estado: `Base fusionada en PR #11; Test Lab virtual bloqueado por configuracion externa`. Aprobado explícitamente el 2026-07-21. Tipo: compatibilidad Android, pruebas e infraestructura. Prioridad: P0. Esfuerzo: L. Riesgo: medio.
- Alcance: documentación de niveles/evidencia, matriz representativa API 29-36 y familias OEM de Argentina/Latinoamérica, smoke instrumentado para Usuario/Admin, Gradle Managed Devices y Firebase Test Lab exclusivamente manual y sin ejecución.
- Restricciones: sin adaptación funcional OEM, sin Supabase/Production, sin dispositivos pagos y sin reutilizar el proyecto Firebase de la app. La base no cambió `versionCode` ni publicó APK.
- Bloqueo virtual confirmado: faltan las variables `FIREBASE_TEST_PROJECT_ID` y `FIREBASE_TEST_RESULTS_BUCKET`, y los secretos `FIREBASE_TEST_WIF_PROVIDER` y `FIREBASE_TEST_SERVICE_ACCOUNT`; tampoco hay una sesión `gcloud` local autorizada para crearlos. No se creó matriz, no hubo costo ni resultados. Evidencia en `docs/compatibility/results/test-lab-virtual-2026-07-21/`.
- Pendientes independientes: `ANDROID-COMPAT-SAMSUNG-PHYSICAL-02`, `ANDROID-COMPAT-FTL-PHYSICAL-03`, `ANDROID-COMPAT-XIAOMI-04`, `ANDROID-COMPAT-MOTOROLA-05`, `ANDROID-COMPAT-HONOR-06`, `ANDROID-COMPAT-OPPO-REALME-07`, `ANDROID-COMPAT-TRANSSION-08`, `ANDROID-COMPAT-TCL-09`, `ANDROID-COMPAT-BATTERY-10`, `ANDROID-COMPAT-UPGRADE-11`, `ANDROID-COMPAT-OFFLINE-12` y `ANDROID-COMPAT-UNINSTALL-ALERT-13`.
- Aceptación: builds DEV de ambas apps, unitarios relacionados, compilación de androidTest, ktlint, Android Lint, detekt, sintaxis de scripts/workflow y `git diff --check`; el smoke virtual/físico solo cambia de estado cuando exista evidencia real.

### ADMIN-USER-SECTIONS-UX-05 - Detalle compacto y controles persistentes

- Estado: `Resuelto y validado fisicamente en DEV 267`. Aprobado explicitamente el 2026-07-20. Tipo: UX Admin. Prioridad: P1. Esfuerzo: M. Riesgo: medio.
- Alcance: `Apps / Web / Seguridad` usa tres segmentos de igual ancho que siempre entran en pantalla. Al desplazar, el selector se transforma con una transicion sutil en una etiqueta compacta junto al nombre; tocarla vuelve al inicio y recupera el selector completo.
- Aplicaciones: `Configurar horarios` queda como accion simple y abre una pantalla completa dedicada; `Grupos` pasa a `Crear grupo de apps`. El titulo, actualizar, buscar y los filtros rapidos permanecen fijos mientras se desplaza solamente el inventario.
- Aceptacion alcanzada: tests unitarios Admin, ktlint y ensamblado DEV de Usuario/Admin correctos, 756 tareas; Android CI completo correcto. En SM-A235M los tres segmentos entran completos, la etiqueta compacta aparece al desplazar y vuelve al inicio, los controles de Apps permanecen fijos y horarios/grupos abren pantallas dedicadas. No hubo crash ni ANR de Content Filter. Ambos APK publicos fueron verificados por manifiesto, hash, paquete, version y certificado. No se toco Production.

### ADMIN-RULES-REFACTOR-01 - Responsabilidades internas de Reglas

- Estado: `Resuelto y fusionado en PR #8`. Aprobado explicitamente el 2026-07-21. Tipo: deuda tecnica Admin. Prioridad: P1. Esfuerzo: L. Riesgo: alto.
- Resultado: se conserva un unico `RulesViewModel` publico como fachada de `RulesUiState`; aplicaciones/grupos, Web, proteccion, archivo/reenlace y coordinacion de operaciones quedan en colaboradores pequenos e inyectables. `RulesScreen` conserva ruta y navegacion, con lista, archivo, detalle y toolbar extraidos por responsabilidad real.
- Correcciones confirmadas: mensajes Web aislados por dispositivo, tracker de refresco liberado aun ante excepciones y archivo remoto exitoso distinguido de reparacion local pendiente. Las pruebas cubren cambio A -> B durante una operacion, segundo refresco tras excepcion y fallo local posterior al archivo remoto.
- Aceptacion: matriz solicitada de tests Admin, build, ktlint, lint y detekt correcta; Android CI del PR correcto; fusion `906f162`. Sin cambio de UI, backend, contratos remotos, `versionCode` ni APK.

### PROTECTION-POSSIBLE-UNINSTALL-01 - Alerta maxima persistente

- Estado: `Publicado DEV 266; pendiente provocar y verificar un episodio real controlado`. Aprobado explicitamente el 2026-07-20. Tipo: seguridad, alertas y recuperacion. Prioridad: P0. Esfuerzo: M. Riesgo: alto.
- Alcance: si el administrador del dispositivo queda desactivado y App Usuario deja de comunicarse durante mas de 30 minutos, Admin muestra una `ALERTA MAXIMA` persistente, deduplicada por episodio, sin afirmar como hecho una desinstalacion que Android no puede confirmar remotamente.
- Recuperacion: explica verificar si la app sigue instalada, reinstalar/reenlazar si falta y reactivar administrador, Accesibilidad y VPN. La alerta se genera en Supabase DEV aunque el telefono ya no pueda reportar.

### PROTECTION-OFFLINE-RECOVERY-02 - Kit sin conexion de un solo uso

- Estado: `Publicado DEV 266; UI fisica validada, pendiente ciclo offline real de un solo uso`. Aprobado explicitamente el 2026-07-20. Tipo: seguridad y continuidad offline. Prioridad: P0. Esfuerzo: L. Riesgo: alto.
- Alcance: Admin prepara cinco codigos por dispositivo mientras tiene conexion y revela el siguiente aun offline. Usuario valida un codigo no consumido sin Internet, habilita diez minutos para desinstalar y sincroniza el consumo al reconectar.
- Seguridad: Supabase conserva solo verificadores con salt y slots consumidos; Admin cifra los codigos legibles con Android Keystore. Cada codigo se usa una vez, rotar invalida el kit anterior y cinco intentos fallidos bloquean durante 15 minutos.

### HELP-CONTEXTUAL-CHAT-01 - Asistente privado de Content Filter

- Estado: `Publicado DEV 266; chat Usuario validado fisicamente, pendiente recorrido Admin y offline`. Aprobado explicitamente el 2026-07-20. Tipo: ayuda, UX y privacidad. Prioridad: P1. Esfuerzo: M. Riesgo: medio.
- Alcance: chat interactivo disponible en Usuario y Admin, con preguntas sugeridas segun el estado real propio o agregado de todos los dispositivos y acciones directas hacia la seccion correcta.
- Decision: motor local determinista, universal y gratuito; funciona offline, no envia preguntas ni promete conocimiento general. Rechaza temas ajenos a Content Filter y conserva solo contexto corto de la conversacion visible.

### ADMIN-USER-SECTIONS-UX-04 - Apps, Web y Seguridad separadas

- Estado: `Resuelto y validado fisicamente en DEV 267`. Aprobado explicitamente el 2026-07-20. Tipo: UX Admin. Prioridad: P1. Esfuerzo: M. Riesgo: medio.
- Alcance: selector horizontal moderno y translucido; Aplicaciones y Web contienen solo sus reglas; Seguridad concentra proteccion, reenlace, desinstalacion, archivo y recuperacion. Web queda sin foto ni tarjeta exterior.
- Evidencia fisica: selector completo y adaptable en SM-A235M; Web sin foto ni tarjeta exterior promocional; Seguridad separada con estado, barrera y opciones avanzadas. Se recorrieron reenlace, desinstalacion temporal, recuperacion offline y archivo sin generar secretos ni ejecutar acciones destructivas.

### USER-APPS-REFRESH-FEEDBACK-01 - Refresco sin vaciar el inventario

- Estado: `Validado fisicamente en SM-A235M DEV 270 para refresco con inventario existente`. Tipo: UX Usuario. Prioridad: P2. Esfuerzo: S. Riesgo: bajo.
- Evidencia: en DEV 267, al tocar actualizar en Mis apps el inventario paso de 161 a 163 aplicaciones, pero durante la carga la lista quedo transitoriamente vacia sin una indicacion de progreso clara.
- Aceptacion propuesta: conservar el inventario anterior mientras se refresca o mostrar un estado de carga inequívoco; bloquear doble tap; reemplazar la lista solo al completar; conservar datos y mensaje anterior si el refresco falla.
- Causa confirmada: el ViewModel ya conserva el inventario y bloquea refrescos simultaneos; la ambiguedad provenia del primer escaneo, cuando la lista aun vacia se mostraba sin progreso ni un texto de carga correcto.
- Implementacion publicada: indicador lineal durante el escaneo y mensaje explicito mientras el inventario inicial esta vacio; sin cambiar PackageManager, datos ni contratos remotos.
- Evidencia fisica DEV 270: con 161 apps visibles, actualizar mostro `Actualizando…`, mantuvo el inventario y termino en `Actualizado ahora`; dos desplazamientos largos no superpusieron ni cortaron la lista. El primer escaneo de una instalacion nueva no se repitio para no borrar datos.

### ADMIN-WEB-ADD-SITE-UX-01 - Formulario Web plano y compacto

- Estado: `Publicado DEV 269; pendiente recorrido visual`. Tipo: UX Admin. Prioridad: P2. Esfuerzo: S. Riesgo: bajo.
- Evidencia: el pedido del usuario fue dejar Web sin foto ni tarjeta. DEV 267 elimino correctamente la foto y la tarjeta exterior promocional, pero el formulario `Agregar sitio` sigue dentro de una tarjeta grande y visualmente pesada.
- Aceptacion propuesta: presentar dominio, minutos DNS opcionales y accion como formulario plano, compacto y claramente separado por espaciado; conservar textos, validaciones y comportamiento Web sin tocar contratos remotos.
- Causa confirmada: el peso visual provenia exclusivamente del `ProductCard` que envolvia `DomainRuleEditor`; los campos, validaciones y acciones ya eran correctos.
- Implementacion publicada: formulario plano con espaciado compacto y separadores sutiles; sin cambios funcionales ni remotos.

### ANDROID-BRAND-ICONS-01 - Iconos oficiales diferenciados

- Estado: `Publicado DEV 264; pendiente comprobacion visual en launcher`. Aprobado explicitamente por el usuario el 2026-07-20. Tipo: marca y UX Android. Prioridad: P2. Esfuerzo: S. Riesgo: bajo.
- Alcance: App Usuario usa el signo verde oficial sobre fondo blanco y App Admin el signo blanco sobre fondo verde. Ambas variantes conservan el mismo recurso para el recorte redondo del launcher; los iconos monocromos de notificacion siguen separados.
- Aceptacion alcanzada: fuentes convertidas sin redibujo generativo, recursos compilados dentro de ambos APK, matriz integral correcta y APK DEV 264 publicos verificados por version, paquete, hash y firma. Falta confirmar visualmente ambos recortes en el telefono.

### PROTECTION-HOME-SIMPLIFY-03 - Reparacion central y emergencia visible

- Estado: `Publicado DEV 263; pendiente prueba fisica`. Aprobado explicitamente por el usuario el 2026-07-20. Tipo: UX y proteccion. Prioridad: P0. Esfuerzo: M. Riesgo: alto.
- Alcance Usuario: Inicio es la unica superficie de diagnostico y reparacion de proteccion; Ajustes mantiene siempre visible el codigo de emergencia y deja de duplicar botones tecnicos. La desinstalacion autorizada y su cancelacion aparecen contextualmente dentro de la tarjeta expandida de Proteccion.
- Alcance Admin: el estado se llama `Proteccion contra desinstalacion`; se retira el permiso general de mantenimiento y queda solo la autorizacion de desinstalacion por 30 minutos, mas el codigo de emergencia offline.
- Seguridad: cancelar revoca el permiso local y el remoto mediante una RPC limitada al dispositivo autenticado por su token y al alcance `removal`. Sin token falla cerrada; no puede conceder permisos, cambiar otro dispositivo ni borrar datos.
- Aceptacion: matriz local completa de 1.068 tareas y publicador oficial con 1.184 tareas correctos; migracion aplicada y verificada solo en Supabase DEV. APK Usuario/Admin DEV 263 publicados juntos y verificados por manifiesto, hash, paquete, version y certificado. Pendiente prueba fisica.

### ANNOUNCEMENTS-INBOX-UX-02 - Lectura y ocultado por dispositivo

- Estado: `Publicado DEV 263; pendiente prueba fisica`. Aprobado explicitamente por el usuario el 2026-07-19. Tipo: UX y comunicacion operativa. Prioridad: P2. Esfuerzo: M. Riesgo: medio.
- Alcance: en Usuario y Admin se quita el refresco manual, la bandeja carga al abrir, los avisos no leidos se distinguen en negrita, abrirlos apaga el contador rojo y cada fila se puede ocultar deslizando a la izquierda con opcion de deshacer. La campanita pasa al icono de contorno compartido.
- Regla de producto: ocultar afecta solo a ese dispositivo; no archiva, borra ni modifica el aviso de Superweb para otros equipos. El contador representa avisos no leidos, no el total historico disponible.
- Arquitectura: recibos por `(device_id, announcement_id)` detras de RLS y RPC autenticadas con token de dispositivo; Android no tiene escritura directa ni secretos. El RPC de listado anterior permanece intacto para APK publicados.
- Aceptacion: ktlint, compilacion, tests DEV, Android Lint, Detekt y ensamblado local de ambas apps correctos. La migracion esta aplicada y verificada exclusivamente en Supabase DEV con 0 recibos iniciales y sin tocar datos existentes; APK Usuario/Admin DEV 263 publicados y verificados. Pendiente probar el ciclo real en dispositivo.

### USER-HOME-UX-03 - Home dinamico de App Usuario

- Estado: `Publicado DEV 262; pendiente prueba fisica`. Aprobado explicitamente por el usuario el 2026-07-19. Tipo: UX, estado de proteccion, uso y actualizaciones. Prioridad: P2. Esfuerzo: M. Riesgo: medio.
- Alcance: integrar la zona de camara con el header oscuro y mostrar el nombre de la comunidad; mover Avisos a una campanita; retirar las tarjetas tecnicas; mostrar la actualizacion solo cuando corresponda; expandir/cerrar en Home el diagnostico de proteccion con pasos de reparacion; mostrar apps y grupos cerca del limite mediante una cola superpuesta de iconos y barras de progreso; y simplificar `Mis apps` a un solo titulo con filtros en una fila horizontal desplazable.
- Comportamiento: solo se muestran limites diarios con al menos 70 % consumido; grupos deshabilitados y apps con tiempo extra activo quedan fuera. Proteccion y Tiempos son mutuamente excluyentes al expandirse, y las secciones dinamicas desaparecen sin dejar bloques vacios.
- Arquitectura: reutiliza ViewModels, estados, iconos, flujo de actualizacion y acciones de reparacion existentes; no agrega biblioteca ni dependencia. La busqueda queda localizada en `Mis apps` para no recortar el inventario compartido que consume Home.
- Aceptacion alcanzada: matriz local y Android CI correctas; APK Usuario DEV 262 publicado y verificado por hash, paquete, version y certificado. Falta revision visual en telefono conectado.

### USER-INTERNET-UX-04 - Estado Web unificado

- Estado: `Publicado DEV 262; pendiente prueba fisica`. Aprobado explicitamente por el usuario el 2026-07-19. Tipo: UX, proteccion Web y horarios. Prioridad: P2. Esfuerzo: M. Riesgo: medio.
- Alcance: una unica tarjeta panoramica, visualmente distinta del Home, reemplaza las tarjetas separadas de Internet, SafeSearch, DAG y advertencias duplicadas. Usa una imagen original local sin marcas ni contenido real, cambia tono segun el estado y expande/cierra el detalle al tocar.
- Estado y acciones: refleja bloqueo administrativo, horario global, VPN, SafeSearch obligatorio, Solo resultados, entitlement/estado DAG y una sola accion contextual. DAG abre desde la tarjeta cuando esta habilitado; VPN degradada conduce a reparacion.
- Limpieza tecnica: se retira de la interfaz la comprobacion manual de la base Web y sus datos diagnosticos; la actualizacion automatica existente permanece activa. El horario se evalua cada minuto en zona Argentina, incluyendo proxima apertura y franjas nocturnas. No se presenta un tiempo Web global inexistente o inexacto.
- Aceptacion alcanzada: imagen optimizada a 68 KiB, matriz local y Android CI correctas, con tests de horarios diurnos/nocturnos y proxima apertura; APK Usuario DEV 262 publicado y verificado. Falta revision visual fisica.

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
| ANDROID-FLAT-LISTS-UX-01 | Validacion fisica parcial ampliada en SM-A235M DEV 270 | P2 | Listas continuas, compactas y modernas en ambas apps | M | Bajo |
| PROTECTION-POSSIBLE-UNINSTALL-01 | Publicado DEV 266; pendiente episodio real controlado | P0 | Alerta maxima persistente y pasos de restablecimiento ante posible desinstalacion | M | Alto |
| PROTECTION-OFFLINE-RECOVERY-02 | Publicado DEV 266; UI fisica validada, pendiente ciclo offline real | P0 | Cinco codigos de recuperacion de un solo uso preparados para operar sin Internet | L | Alto |
| HELP-CONTEXTUAL-CHAT-01 | Publicado DEV 266; Usuario validado fisicamente, pendiente Admin y offline | P1 | Chat privado de ayuda, acotado a la app y contextual a todos los dispositivos | M | Medio |
| ADMIN-USER-SECTIONS-UX-04 | Resuelto y validado fisicamente en DEV 267 | P1 | Separar Aplicaciones, Web y Seguridad con selector horizontal moderno | M | Medio |
| ADMIN-USER-SECTIONS-UX-05 | Resuelto y validado fisicamente en DEV 267 | P1 | Selector adaptable, encabezado compacto, horario dedicado y controles de Apps persistentes | M | Medio |
| USER-APPS-REFRESH-FEEDBACK-01 | Validado fisicamente en SM-A235M DEV 270 con inventario existente | P2 | Refrescar Apps sin vaciar el inventario ni ocultar el progreso | S | Bajo |
| ADMIN-WEB-ADD-SITE-UX-01 | Publicado DEV 269; pendiente recorrido visual | P2 | Formulario Agregar sitio plano y compacto | S | Bajo |
| SEC-LICENSE-01 | Implementado candidato DEV 241; pendiente prueba fisica | P0 | Ciclo de vida de comunidad y licencia: alta, renovacion, vencimiento y restauracion sin perder configuracion | L | Alto |
| DATA-DELETE-01 | Resuelto y publicado DEV 241; prueba destructiva aislada correcta | P0 | Borrado definitivo y auditable de usuario; la accion actual falla para todos los usuarios | L | Muy alto |
| BARRIER-A11Y-RACE-01 | Validado candidato DEV 241 en SM-A235M; pendiente repetir en SM-S908E | P0 | Bypass rapido permite apagar Accessibility aunque Ajustes protegidos se cierre | M | Critico |
| BARRIER-DEFAULT-ON-01 | Implementado DEV 241; pendiente prueba fisica | P1 | Armar automaticamente la barrera al completar y verificar la configuracion de proteccion | S | Medio |
| OPS-METRICS-01 | Candidato DEV 241 optimizado y con linea base corta; pendiente muestra de 24 h | P1 | Medicion prolongada de bateria, trafico y estabilidad | M | Medio |
| USAGE-REAL-01 | Validado fisicamente y publicado en DEV 241 | P1 | Uso real de app foreground y estabilidad de listas | L | Alto |
| REQUESTS-UX-01 | Implementado candidato DEV 241; pendiente prueba fisica | P2 | Historial, estados y refresco manual claro de solicitudes | M | Medio |
| SUPERWEB-DEPLOY-SYNC-01 | Resuelto; GitHub conectado, Production automatizada y health verificado | P0 | Publicar en la URL oficial todas las funciones Super Admin ya implementadas | M | Alto |
| SUPERWEB-VERSION-01 | Resuelto; fusionado y desplegado | P2 | Mostrar en la interfaz la version o build actualmente publicada de Superweb | S | Bajo |
| UI-BANNER-UNIFY-01 | Publicado y validado visualmente en SM-A235M DEV 246 | P2 | Unificar feedback de Usuario/Admin con el banner premium sin recortar textos largos | S | Bajo |
| UI-BANNER-DYNAMIC-02 | Publicado DEV 263; pendiente prueba física | P1 | Línea de estado sutil con duración adaptada a textos largos y error contextual | M | Medio |
| ANNOUNCEMENTS-INBOX-UX-02 | Publicado DEV 263; pendiente prueba física | P2 | Avisos leídos/no leídos, contador real y ocultado por dispositivo con deshacer | M | Medio |
| ADMIN-UX-NAV-HOME-01 | Pulido publicado DEV 265; pendiente recorrido físico completo | P1 | Navegación Home/Usuarios/Solicitudes/Cuenta y Home orientado a salud, licencia y avisos Superweb | M | Medio |
| ADMIN-USERS-UX-02 | Pulido publicado DEV 265; cierre visual físico parcial | P1 | Detalle con Protección siempre visible, Apps/Web y grupos dentro de Aplicaciones | L | Alto |
| USER-ARCHIVE-RESTORE-02 | Implementado y validado para archivos nuevos; 2 legados quedan bloqueados a revisión por falta de snapshot | P1 | Usuarios anteriores, archivo reversible, restauración y reenlace seguro con token nuevo | L | Alto |
| PROTECTION-MAINTENANCE-UX-01 | Reordenado y publicado DEV 263; pendiente prueba física | P0 | Desinstalación temporal por 30 minutos, sin permiso general de mantenimiento | M | Alto |
| PROTECTION-HOME-SIMPLIFY-03 | Publicado DEV 263; pendiente prueba física | P0 | Reparación central en Inicio, desinstalación contextual y código de emergencia siempre visible | M | Alto |
| POLICY-SCHEDULES-01 | Publicado DEV 261 y backend DEV aplicado; pendiente prueba física | P1 | Límites de uso real y horarios múltiples por día en zona Argentina para Apps y Web | XL | Alto |
| ADMIN-REQUESTS-ACCOUNT-01 | Publicado DEV 261; pendiente prueba física | P1 | Solicitudes raíz y Cuenta con identidad, Superweb, licencia, versión y novedades | M | Medio |
| USER-UX-ALIGN-02 | Publicado DEV 261; validación local completa y cierre físico parcial | P2 | Reacomodar App Usuario con componentes visuales compartidos sin exponer controles Admin | L | Medio |
| USER-HOME-UX-03 | Publicado DEV 262; pendiente prueba física | P2 | Home dinámico con protección reparable, campanita Superweb, actualización contextual y cola de apps/grupos cerca del límite | M | Medio |
| USER-INTERNET-UX-04 | Publicado DEV 262; pendiente prueba física | P2 | Internet unificado en una tarjeta visual con estado real, DAG, reparación y horario dinámico | M | Medio |
| SUPERWEB-FUNCTIONAL-VERIFY-01 | Validacion automatizada DEV correcta; pendiente sesion autenticada publicada | P1 | Comprobar licencias, tokens, DAG, actualizaciones, alertas y avisos desde la Superweb oficial | M | Alto |
| SUPERWEB-AUTH-RECOVERY-01 | Publicado; recovery bloqueado externamente por cuota Supabase DEV | P0 | Recuperar de forma segura la contraseña del propietario desde el Login | S | Medio |
| SUPERWEB-MOBILE-UX-01 | Publicado; pendiente validar autenticado en celular | P1 | Navegacion mobile-first, controles tactiles y Uso DAG sin tabla horizontal en celular | S | Bajo |
| SUPERWEB-OPS-UX-01 | Archivo seguro, agrupacion y claridad Base implementados; busqueda/filtros pendientes | P2 | Busqueda, filtros y ciclo seguro de lectura/archivo para alertas y avisos | M | Medio |
| APP-UPDATE-CHANGELOG-01 | Pulido publicado DEV 265; pendiente prueba fisica | P2 | Mostrar en Usuario y Admin las novedades especificas de la actualizacion ofrecida | M | Medio |
| ANDROID-PHYSICAL-CLOSEOUT-01 | Cierre parcial ampliado en SM-A235M DEV 270; quedan recorridos especificos y SM-S908E | P1 | Cerrar en un recorrido fisico los candidatos Android pendientes sin publicar por ticket | M | Alto |
| SUPERADMIN-TOKEN-01 | Implementado DEV 241 y hotfix de visualizacion 2026-07-18; pendiente prueba funcional autenticada | P2 | Gestion segura y auditable de tokens desde Super Admin | L | Alto |
| SUPERADMIN-ADMIN-RELINK-01 | Backend y Android publicados DEV 264; Superweb en fuente; pendiente prueba | P1 | Volver a enlazar un Admin desvinculado con un token generado desde su tarjeta Superweb | M | Alto |
| USER-RELINK-01 | Backend y Android publicados DEV 264; Superweb en fuente; pendiente prueba | P1 | Reenlazar un Usuario con token de reemplazo desde App Admin o Superweb | L | Alto |
| ANDROID-BRAND-ICONS-01 | Publicado DEV 264; pendiente comprobacion visual | P2 | Iconos oficiales diferenciados para App Usuario y App Admin | S | Bajo |
| UI-POLISH-01 | Publicado DEV 243; pendiente comprobacion visual desbloqueada | P2 | Consistencia visual y accesibilidad de ambas apps y Superweb | M | Bajo |
| UI-ICON-SYSTEM-01 | Validacion visual tema claro SM-A235M DEV 270; faltan variantes | P2 | Catalogo coherente Material Symbols Rounded, sin mezclar familias ni agregar una dependencia pesada | S | Bajo |
| UI-SYSTEM-BAR-CONTINUITY-01 | Validado fisicamente en tema claro SM-A235M DEV 270 | P1 | Integrar barra de estado y zona de camara con el color efectivo de cada pantalla | S | Medio |
| UI-MOTION-SMOOTH-01 | Validacion fisica principal SM-A235M DEV 270; falta reduccion de movimiento | P2 | Transiciones sobrias de 200-250 ms, sin saltos y respetando reduccion de movimiento | M | Medio |
| ADMIN-USERS-HUB-UX-02 | Validado fisicamente en SM-A235M DEV 270 | P1 | Lista Usuarios moderna, alta con foco y estado de refresco persistente en el encabezado | M | Medio |
| ADMIN-USER-SECURITY-BADGES-01 | Sano y rojo validados en SM-A235M DEV 270; falta amarillo y reparacion | P1 | Indicadores amarillos/rojos accesibles solo segun evidencia real y burbuja en Seguridad | M | Alto |
| ADMIN-SECONDARY-LISTS-UX-02 | Validacion fisica parcial SM-A235M DEV 270 | P2 | Listas secundarias Admin continuas, blancas y sin tarjetas repetidas | M | Medio |
| USER-SECONDARY-LISTS-UX-02 | Validacion fisica principal SM-A235M DEV 270; faltan estados con datos | P1 | Solicitudes y Mis apps con estado persistente, filtros fijos y desplazamiento estable | M | Medio |
| ADMIN-WEB-SETTINGS-UX-02 | Validacion fisica principal SM-A235M DEV 270; faltan filas con sitios | P2 | Web y Ajustes Admin con filas continuas y jerarquia simple; Agregar sitio queda fuera | M | Medio |
| USER-INTERNET-SETTINGS-UX-02 | Validacion fisica principal SM-A235M DEV 270; falta estado degradado | P2 | Internet y Ajustes Usuario con una sola pieza visual y listas de estado legibles | M | Medio |
| USER-DAG-LAUNCHER-PREFERENCE-01 | Validado fisicamente en SM-A235M DEV 270 | P2 | Preferencia local para mostrar DAG como app separada sin cambiar permiso ni APK | M | Medio |
| USER-RESILIENCE-01 | Implementado candidato DEV 241; pendiente prueba fisica | P2 | Recuperacion guiada de estados degradados sin confundir al usuario | M | Medio |
| PROTECTION-ONBOARDING-HEALTH-01 | Implementado parcial candidato DEV 264 | P1 | Salud coherente y reparacion guiada; onboarding completo queda separado | L | Alto |
| DEVICE-CONNECTIVITY-ALERTS-01 | Backend DEV y Android publicados DEV 270; pendiente comprobacion fisica | P1 | Alerta ordinaria tras 100 horas sin comunicacion para Admin y Superweb | M | Medio |
| DEV-OFFLINE-ALERT-100H-01 | Aplicado y verificado exclusivamente en Supabase DEV | P1 | Alinear la alerta remota ordinaria al margen de 100 horas sin cambiar posible desinstalacion | S | Medio |
| ADMIN-DEVICE-OFFLINE-100H-01 | Publicado DEV 270; pendiente comprobacion fisica | P1 | Unificar en 100 horas el aviso de falta de comunicacion de App Admin sin demorar componentes caidos | S | Medio |
| SUPERADMIN-MSG-01 | Bandejas y creacion resueltas en DEV 241; pendiente push FCM con sesion Superweb | P2 | Avisos push y bandeja interna, no chat libre | L | Medio |
| SUPERADMIN-ALERTS-01 | Implementado candidato DEV 241; pendiente prueba funcional | P2 | Visibilidad en Super Admin de intentos de desinstalacion o manipulacion de protecciones | M | Medio |
| ADMIN-ALERTS-UX-01 | Validado visualmente en SM-A235M DEV 248; pendiente evento real | P2 | Campanita y bandeja de alertas de seguridad en App Admin, separadas de Solicitudes | M | Medio |
| ALERT-ROUTING-01 | Implementado backend DEV; pendiente prueba fisica | P1 | Intentos bloqueados solo en Super Admin; desactivaciones efectivas en Super Admin y Admin | M | Alto |
| APP-INSTALL-APPROVAL-01 | Implementado candidato DEV 241; pendiente prueba fisica | P1 | Play Store visible con aprobacion por app y bloqueo de descarga/instalacion de APK externos | L | Alto |
| SUPERADMIN-DAG-ENTITLEMENT-01 | Implementado candidato DEV 241; pendiente prueba funcional | P1 | Habilitar o deshabilitar DAG como funcion premium desde Super Admin | M | Alto |
| SUPERADMIN-DAG-USER-01 | Publicado en Superweb y backend DEV; pendiente prueba funcional autenticada | P1 | Habilitar o deshabilitar DAG por Usuario desde Superweb | S | Alto |
| BARRIER-LAUNCHER-01 | Resuelto y validado DEV 242 en SM-S908E | P2 | Mantener acceso Usuario sin debilitar la instalacion protegida en Android normal | M | Medio |
| BARRIER-SETTINGS-VISIBILITY-01 | Idea | P1 | Ocultar o neutralizar controles para eliminar apps y acceder a la configuracion VPN | M | Alto |
| DAG-NAV-UX-01 | Resuelto DEV 234 | P2 | Simplificar barra DAG: Home y nueva pestana visibles; atras, adelante y actualizar en menu | M | Medio |
| DAG-WEB-INTERACTION-02 | Publicado DEV 271; mejora parcial, seguimiento abierto | P1 | Evitar recorridos profundos ante cambios de atributos en paginas permitidas | M | Medio |
| DAG-WEB-INTERACTION-03 | Propuesto; diagnostico 2026-07-22 | P1 | Procesar subarboles dinamicos por lotes sin congelar menus ni relajar barreras | M | Alto |
| DAG-SEARCH-CONTINUITY-03 | Propuesto; causa confirmada | P1 | Buscar tambien ante incertidumbre y filtrar resultados/paginas sin relajar bloqueos duros | M | Alto |
| DAG-PROTECTED-MODE-UX-04 | Propuesto; causa confirmada | P2 | Quitar o explicar claramente el mensaje tecnico de proteccion adicional | S | Bajo |
| DAG-HOME-UX-01 | Resuelto DEV 234 | P2 | Home DAG con buscador central grande e identidad de Internet kosher | S | Bajo |
| DAG-TABS-UX-01 | Resuelto DEV 226 | P2 | Mejorar manejo cotidiano de multiples pestanas DAG | M | Medio |
| DAG-THEME-01 | Corregido DEV 239; pendiente prueba fisica | P2 | Integrar la zona de camara con DAG y evitar recortar el texto de busqueda | S | Bajo |
| DAG-THEME-02 | Resuelto DEV 230 | P1 | Corregir texto negro sobre fondo negro y fondo transparente del WebView | S | Bajo |
| DAG-VISUAL-KOSHER-03 | Resuelto DEV 230 | P0 | Abrir tiendas normales y ocultar selectivamente imagenes intimas o visualmente no aptas | M | Alto |
| DAG-VISUAL-CALIBRATION-01 | Resuelto DEV 231 | P0 | Reducir falsos positivos con salida normal, blur fuerte u ocultamiento por imagen | M | Alto |
| DAG-MODESTY-REGIONS-01 | Resuelto DEV 235 | P0 | Blur local fuerte para calzas, shorts, escotes, manga corta y regiones femeninas cubiertas o expuestas | M | Alto |
| DAG-IMAGE-DELIVERY-01 | Resuelto DEV 236 | P1 | Evitar huecos tecnicos y reclasificacion duplicada de imagenes dinamicas en la pagina activa | S | Medio |
| DAG-SEARCH-FP-02 | Implementado DEV 239; pendiente prueba fisica | P1 | Evitar el falso positivo de Yeshurun social sin ocultar vocabulario riesgoso | S | Medio |
| DAG-MODESTY-CHEST-02 | Implementado DEV 239; pendiente prueba fisica | P0 | Desenfocar pecho y regiones cubiertas aunque no se detecte un rostro | S | Alto |
| DAG-IMAGE-DELIVERY-02 | Implementado DEV 239; pendiente prueba fisica | P1 | Procesar tambien las fotos posteriores de paginas densas sin abandonarlas por espera interna | M | Medio |
| DAG-LOCAL-IMAGE-PERF-03 | Implementado candidato DEV 271; pendiente medicion fisica | P1 | Acelerar localmente la primera carga visual sin enviar fotos fuera del telefono ni mostrarlas antes de decidir | M | Alto |
| DAG-CALIBRATION-BIDIRECTIONAL-09 | Publicado DEV 259; validado en SM-A235M | P1 | Modo temporal DEV que revela originales y permite X para falsos negativos o R para posibles falsos positivos, con trazabilidad separada | M | Alto |
| DAG-CALIBRATION-CLOSED-LOOP-10 | Publicado DEV 260; validado en SM-A235M | P0 | Hacer persistentes las decisiones calibradas, separar motivos positivos/negativos y agregar criterio local de mangas/corte sobre rodillas | L | Alto |
| DAG-AUDIENCE-POLICY-11 | Publicado DEV 261; validación física previa correcta, pendiente repetir APK público | P0 | Permitir imagenes normales de bebes y hombres, aplicar criterio femenino a niñas y mantener ropa interior/desnudez como bloqueo universal | M | Alto |
| DAG-ULTRA-KOSHER-01 | Publicado DEV 261; pendiente prueba física completa | P1 | Modo Extra Kosher: fotos difuminadas salvo logos/controles esenciales y videos totalmente bloqueados | L | Alto |
| DAG-RESULTS-DIAG-01 | Resuelto DEV 237 | P1 | Contabilizar localmente el embudo de resultados Brave y los descartes DAG sin guardar contenido | S | Bajo |
| DAG-RESULTS-PAGE-01 | Implementado DEV 238; pendiente prueba fisica | P1 | Ofrecer una unica pagina adicional cuando Brave informa mas resultados, con costo explicito | S | Medio |
| DAG-HISTORY-UX-01 | Resuelto DEV 234 | P2 | Redisenar historial DAG como lista minimalista | S | Bajo |
| DAG-ANALYSIS-UX-01 | Resuelto DEV 226 | P2 | Mostrar el analisis dentro del buscador con iluminacion neon inteligente | S | Bajo |
| DAG-APPROVAL-CACHE-01 | Resuelto DEV 226 | P1 | Reutilizar temporalmente la aprobacion de paginas ya revisadas | M | Alto |
| DAG-REVIEW-STAGING-01 | Resuelto DEV 225 | P1 | Analizar pagina completa antes de pedir revision por un resultado incierto | M | Medio |
| DAG-BACK-NAV-01 | Resuelto DEV 233 | P2 | Atras respeta paginas y resultados antes de volver a Home | S | Medio |
| DAG-APPROVAL-POLICY-01 | Implementado DEV 238; pendiente prueba fisica | P0 | Evitar que aprobar un sitio desde App Admin desactive DAG | M | Alto |
| DAG-REQUEST-STATUS-01 | Implementado parcial DEV 238; ampliacion pendiente | P1 | Deduplicar pedidos, restringir reenvio y avisar visualmente/push cuando Admin resuelve | L | Medio |
| DAG-SHARE-01 | Implementado DEV 238; pendiente prueba fisica | P2 | Compartir de forma segura el enlace de la pagina actual desde DAG | S | Medio |
| DAG-AUTOCOMPLETE-02 | Implementado DEV 238; pendiente prueba fisica | P2 | Ejecutar directamente la busqueda al tocar una sugerencia DAG | S | Bajo |
| DAG-CAPTCHA-01 | Publicado DEV 271; iframe visible pero flujo aun falla | P1 | Mostrar CAPTCHAs seguros necesarios para completar sitios y tramites permitidos | M | Alto |
| DAG-CAPTCHA-02 | Propuesto; diagnostico 2026-07-22 | P1 | Completar una sesion CAPTCHA temporal y aislada sin abrir iframes generales | L | Alto |
| DAG-CALIBRATION-DELIVERY-11 | Propuesto; causa de perdida confirmada en cliente | P0 | Garantizar entrega, confirmacion y reintento de revisiones DAG privadas | L | Alto |
| DAG-UPDATE-01 | Decision cerrada DEV 238 | P1 | DAG se actualiza con App Usuario y Android normal confirma la instalacion | S | Bajo |
| DAG-TABS-UX-02 | Implementado candidato DEV 241; pendiente prueba fisica | P2 | Quitar peces del selector, mostrar recientes y evitar pestanas vacias duplicadas | M | Medio |
| DAG-CALIBRATION-PROGRESS-01 | Resuelto DEV 253 | P1 | Mostrar progreso real y accesible del analisis DAG | S | Bajo |
| DAG-CALIBRATION-QUEUE-02 | Resuelto DEV 253 | P0 | Enviar solo miniaturas inciertas a una cola privada y deduplicada | M | Alto |
| DAG-CALIBRATION-REVIEW-03 | Publicado DEV 253; recorrido autenticado pendiente | P0 | Etiquetar criterio visual con motivo y auditoria en Super Admin | M | Alto |
| DAG-CALIBRATION-VERSIONS-04 | Resuelto DEV 253 | P0 | Calcular, activar y revertir umbrales versionados de Calibracion DAG | L | Alto |
| DAG-CALIBRATION-MODELS-05 | Resuelto DEV 253 | P1 | Registrar modelos y separar calibracion de entrenamiento real | M | Alto |
| DAG-CALIBRATION-IN-PAGE-08 | Resuelto y validado fisicamente DEV 256 | P1 | Marcar una foto inapropiada desde DAG con X, blur inmediato y revision posterior auditable | M | Medio |
| USER-GREETING-01 | Implementado candidato DEV 241; pendiente prueba fisica | P2 | Personalizar el saludo de App Usuario con el nombre definido por el administrador | S | Bajo |

### ADMIN-UX-EPIC-2026-07-19 - Reorganización integral de App Admin

- Estado: `Publicado en DEV 261; pulido de flujo publicado en DEV 265`; feedback, navegación/Home, Usuarios, permisos temporales, horarios, DAG Extra Kosher, Solicitudes, Cuenta, archivo/restauración segura y alineación de App Usuario están incluidos. Los dos archivos legados sin snapshot quedan aislados como `Revisión necesaria` y no se recuperarán, por decisión del usuario. Continúan pendientes los recorridos físicos específicos señalados en la tabla.
- Orden aprobado: banner dinámico; navegación y Home; Usuarios y detalle; archivo/restauración/reenlace; mantenimiento y desinstalación; límites y horarios; DAG Extra Kosher; Solicitudes y Cuenta; validación integral; adaptación posterior de App Usuario.
- Feedback compartido: deja de ser un banner. Cada pantalla muestra como máximo una línea de 32 dp bajo su header, con punto pequeño y texto, sin fondo, tarjeta ni X. El último estado reemplaza al anterior y no acompaña el cambio de sección; usa transición vertical rápida, puntos animados y marquee para textos largos. Progreso permanece mientras trabaja; éxito dura 2 segundos, aviso 2,5 y error 3. Después, el error queda en rojo junto a la acción hasta corregir o reintentar con éxito. Sin estado no se reserva altura.
- Home Admin: header superior con sólo esquinas inferiores redondeadas, saludo `Hola, {nombre} (ADM)`, comunidad, campana exclusiva de avisos Superweb y resumen de licencia; cuerpo con estado real de protección, acceso a usuarios afectados, agregar usuario, usuarios activos y solicitudes pendientes.
- Usuarios: lista, búsqueda, actualización y alta; detalle con header fijo, resumen de Protección visible apenas se abre y acceso a su detalle, Aplicaciones/Web como únicas secciones principales y grupos dentro de Aplicaciones. Conserva estado de VPN/Accesibilidad/Administrador del dispositivo, protección obligatoria sin control para desarmarla, archivo reversible y reenlace con token nuevo.
- Archivo seguro: App Admin nunca hace borrado físico. `Usuarios anteriores` permite buscar por nombre y generar un token de restauración ligado al mismo usuario. Los archivos nuevos guardan un snapshot de banderas y conservan en sus filas soft-deleted identidad, categorías, reglas, límites, horarios, grupos e inventario; la restauración atómica recupera el mismo `device_id` recién al consumir el token. La eliminación definitiva queda exclusivamente en Superweb y sigue requiriendo confirmación específica. Los dos archivos anteriores a este mecanismo aparecen como `Revisión necesaria` y no se restauran automáticamente.
- Permisos temporales: mantenimiento y desinstalación son autorizaciones separadas de 30 minutos, vencen y reactivan automáticamente; Admin muestra qué permiso sigue activo y hasta cuándo.
- Límites y horarios: tiempo por uso real de aplicación o Web y ventanas horarias múltiples por día, zona `America/Argentina/Buenos_Aires`; límite de tiempo y reloj se aplican juntos y gana la restricción más fuerte; configuración global con excepciones por app o sitio.
- DAG Extra Kosher: todas las fotos se difuminan, excepto logos, iconos y controles esenciales; videos y miniaturas de video quedan bloqueados/difuminados y nunca se reproducen.
- Solicitudes: destino raíz con pendientes accionables y resueltos como historial; tipos Web, descargas y aplicaciones; App Usuario podrá cancelar únicamente sus propias solicitudes pendientes en el ticket posterior.
- Cuenta: identidad Admin, comunidad, estado con Superweb, licencia completa y su efecto, versión, actualización y novedades específicas de App Admin.
- Criterio transversal: un estado verde sólo aparece con protección confirmada; estados desconocidos o sin conexión reciente son amarillos/pendientes y una licencia que no permite proteger se muestra separadamente, sin atribuir falsamente una falla a VPN o Accesibilidad.
- Pulido publicado en DEV 265: Home abre el alta directamente; la lista de Usuarios queda compacta, sin etiquetas de estado, con luz verde/amarilla; el detalle usa Proteccion expandible, Aplicaciones/Web, configuracion global antes de Grupos y apps, y `Mas opciones` al final. Los iconos de campana y salud de usuarios se simplifican sin cambiar contratos ni datos.

### SUPERWEB-DEPLOY-SYNC-01 - Publicacion oficial verificable

- Estado: `En progreso`; aprobado explicitamente junto con los cuatro tickets de cierre el 2026-07-16.
- Tipo: operacion, despliegue y seguridad Super Admin. Prioridad: P0. Esfuerzo: M. Riesgo: alto.
- Causa confirmada: `main` contiene `/dag-usage`, `/alerts`, `/announcements`, DAG premium y estado de actualizaciones, pero `https://web-super-admin-nine.vercel.app` sirve una compilacion anterior: las tres rutas nuevas devuelven 404 y los commits recientes no registran una comprobacion Vercel.
- Alcance: recuperar la conexion del proyecto Vercel oficial con `main` y `web-super-admin`, publicar una sola vez la fuente validada y dejar una prueba publica de entorno y commit. No se reemplaza ni elimina la URL oficial ni se apunta a Production.
- Seguridad implementada: la Superweb falla cerrada si `NEXT_PUBLIC_SUPABASE_URL` no es exactamente DEV `syeycayasyufedwoprea`; `/api/health` publica solo nombre de servicio, entorno, project ref y commit, nunca claves o sesion.
- Verificacion implementada: `scripts/verify_superweb.sh` exige health DEV, commit esperado y existencia protegida de Comunidades, Uso DAG, Alertas y Avisos. El Vercel viejo falla correctamente esta comprobacion.
- Pendiente externo: iniciar sesion en Vercel como propietario, revisar proyecto/rama/root directory, reconectar GitHub si corresponde, desplegar y ejecutar la verificacion contra la URL oficial.
- Aceptacion: URL oficial sin 404 en rutas nuevas; anonimo redirigido a Login; health coincide con el commit publicado y DEV; futuros pushes de `main` vuelven a generar estado/despliegue visible; ninguna variable de Production ni Service Role.

### SUPERWEB-VERSION-01 - Version publicada visible

- Estado: `Resuelto; fusionado y desplegado`; `/api/health` confirma la build `d73c68a`. Tipo: UX operativa, soporte y trazabilidad de despliegue. Prioridad: P2.
- Problema: Superweb expone internamente el commit y entorno mediante `/api/health`, pero la interfaz no permite que el propietario identifique facilmente qué version o build esta usando.
- Solucion propuesta: mostrar una referencia breve y visible de la version publicada, por ejemplo `Superweb DEV · build 6b12a60`, obtenida del mismo dato verificable del despliegue. Puede acompañarse con fecha de publicacion o estado actualizado, sin mostrar project ref, variables, claves ni informacion de sesion.
- Evidencia: pedido explicito del usuario del 2026-07-18.
- Esfuerzo: S estimado. Riesgo: bajo; una version calculada desde una fuente distinta de `/api/health` podria quedar desactualizada o confundir código fuente con despliegue real.
- Dependencias: `SUPERWEB-DEPLOY-SYNC-01`; `/api/health`; commit inyectado por Vercel; layout autenticado y Login; diseño mobile-first.
- Duplicados y relacion: complementa la comprobacion automatizada de despliegue, pero no la duplica: este ticket hace visible el dato para el usuario.
- Implementacion candidata: una unica fuente de build alimenta tanto `/api/health` como la etiqueta compacta de Login y del layout autenticado; muestra solo los siete primeros caracteres del commit o `local`, sin project ref, variables ni datos de sesion.
- Criterios de aceptacion propuestos:
  - la interfaz muestra la build realmente servida por Vercel y coincide con `/api/health`;
  - el dato es legible en celular y escritorio sin ocupar espacio principal;
  - una build desconocida muestra un fallback neutro y no rompe Login ni navegación;
  - actualizar `main` y desplegar cambia automaticamente la version visible;
  - no se exponen claves, variables, project ref completo, credenciales, sesión ni información sensible;
  - soporte puede copiar o comunicar la referencia para diagnosticar una Superweb desactualizada.
- Decisiones pendientes para la entrevista del ticket: ubicación —Login, menú, pie o sección Acerca de—; mostrar commit corto, numero propio o ambos; fecha/hora; acción para copiar; visibilidad antes o despues de iniciar sesión.

### SUPERWEB-AUTH-RECOVERY-01 - Recuperacion segura del propietario

- Estado: `Publicado; recovery bloqueado externamente por cuota Supabase DEV`; aprobado explicitamente el 2026-07-18.
- Causa: el Login solo admitia email/password y mostraba un error generico; no existian solicitud de recuperacion, callback ni pantalla de contraseña nueva. Ademas, Supabase Auth DEV conservaba localhost como Site URL y no autorizaba el dominio Vercel.
- Resultado: `Olvidé mi contraseña` envia un enlace sin revelar si la cuenta existe; el callback usa PKCE, limita el destino, comprueba el rol Super Admin activo y la nueva contraseña exige al menos 12 caracteres. El cambio usa Supabase Auth, revoca sesiones persistentes y vuelve al Login.
- Seguridad: sin SQL sobre `encrypted_password`, sin Service Role en navegador, sin secretos nuevos y sin acceso para usuarios Auth que no sean Super Admin. Site URL y redirects fueron corregidos solo en `syeycayasyufedwoprea`.
- Diagnostico: el Super Admin DEV sigue activo, confirmado, no bloqueado y con contraseña configurada. Auth registra una actualizacion el 2026-07-17 y el ultimo login exitoso el 2026-07-12, pero la auditoria Auth esta vacia. La causa demostrada del acceso actual es HTTP 402 de Auth por `exceed_egress_quota` y `exceed_cached_egress_quota`; Login y recovery fallan antes de validar la contraseña. La interfaz publicada ya lo informa sin atribuirlo a credenciales.
- Aceptacion: solicitar desde el Login, recibir el correo, abrir un enlace valido una sola vez, guardar contraseña nueva, cerrar sesiones previas e ingresar nuevamente; enlaces vencidos y usuarios no autorizados fallan cerrados.

### SUPERADMIN-ADMIN-RELINK-01 - Reenlace de un administrador existente

- Estado: `Backend y Android publicados DEV 264; Superweb implementado en fuente; pendiente prueba integral`; aprobado explicitamente el 2026-07-20. Tipo: recuperacion de acceso, activacion y seguridad Super Admin. Prioridad: P1.
- Problema: si App Admin pierde o elimina su activacion local, el administrador puede quedar sin acceso aunque su identidad, comunidad y permisos sigan vigentes. Crear otro administrador produciria duplicados y fragmentaria la auditoria.
- Solucion propuesta: desde la tarjeta del administrador existente en Superweb, ofrecer `Volver a enlazar` y generar un token nuevo, de un solo uso y vencimiento corto, asociado exactamente a ese administrador y comunidad. App Admin usa ese token para recuperar el vinculo sin crear otra identidad administrativa.
- Evidencia: propuesta explicita del usuario del 2026-07-18.
- Esfuerzo: M estimado. Riesgo: alto; un token emitido para la identidad equivocada, reutilizable o que no revoque correctamente el acceso anterior podria habilitar dos dispositivos no autorizados o tomar control de una comunidad.
- Dependencias: `SUPERADMIN-TOKEN-01`; tarjeta de Admin en Superweb; activacion App Admin; hash y vencimiento de codigos; sesiones/tokens de dispositivo anteriores; licencia y pertenencia a comunidad; auditoria y revocacion.
- Duplicados y relacion: seguimiento de `SUPERADMIN-TOKEN-01`, que cubre creacion y revocacion de tokens pendientes. No lo reabre porque este caso recupera un administrador ya existente y conserva su identidad.
- Criterios de aceptacion propuestos:
  - la accion aparece en la tarjeta del administrador correcto y muestra claramente comunidad y estado actual;
  - el token solo puede activar App Admin para ese administrador, se visualiza una vez, vence y no puede reutilizarse;
  - reenlazar conserva identidad, comunidad, permisos y auditoria, sin crear otra fila de administrador;
  - la politica definida para el acceso anterior se aplica atomicamente: se revoca o se conserva de forma explicita, nunca por accidente;
  - generar, revocar, vencer, usar y volver a generar quedan auditados sin almacenar el token en claro;
  - un Super Admin no autorizado, un administrador común o un usuario anonimo no puede emitir ni consultar el token;
  - errores de red o activacion no consumen falsamente el token ni dejan dos estados contradictorios;
  - no se borra comunidad, usuarios protegidos, reglas, solicitudes, licencias ni historial operativo.
- Decision aplicada: token de un solo uso por 30 minutos. El Admin conserva una sola identidad/dispositivo logico; el acceso anterior se revoca atomicamente sólo despues de la primera sincronizacion completa correcta del nuevo enlace.

### USER-RELINK-01 - Reenlace de un Usuario existente

- Estado: `Backend y Android publicados DEV 264; Superweb implementado en fuente; pendiente prueba integral`; aprobado explicitamente el 2026-07-20. Tipo: recuperacion de acceso, activacion Usuario y seguridad. Prioridad: P1.
- Problema: App Usuario puede perder su vinculacion local por un fallo, limpieza de datos, cambio de telefono u otra incidencia, aunque el Usuario protegido y su configuracion remota sigan existiendo. Crear otro Usuario duplicaria identidad, reglas, grupos, solicitudes y auditoria.
- Solucion propuesta: ofrecer `Volver a enlazar` o `Reemplazar enlace` tanto en la tarjeta del Usuario dentro de App Admin como en su tarjeta Superweb. La accion genera un token nuevo, de un solo uso y vencimiento corto, asociado exactamente al Usuario, comunidad y alcance autorizados; App Usuario lo utiliza para recuperar el vínculo sin crear otra identidad.
- Evidencia: propuesta explicita del usuario del 2026-07-18.
- Esfuerzo: L estimado. Riesgo: alto; una recuperacion incorrecta podria entregar el control de un Usuario a otro dispositivo, mantener activo el vínculo anterior o mezclar configuraciones entre personas.
- Dependencias: activacion de App Usuario; generación actual de tokens desde App Admin; Superweb y permisos Super Admin; dispositivo/token anterior; licencia; grupos, reglas, limites, solicitudes y política; auditoria; sincronizacion inicial segura.
- Duplicados y relacion: complementa `SUPERADMIN-ADMIN-RELINK-01`, pero recupera App Usuario y admite dos autoridades. No duplica la creación normal de un Usuario nuevo ni `DATA-DELETE-01`; reenlazar no archiva ni borra la identidad existente.
- Criterios de aceptacion propuestos:
  - App Admin y Superweb ofrecen la accion únicamente sobre el Usuario y comunidad autorizados;
  - ambas superficies generan tokens equivalentes en seguridad, alcance, vencimiento y auditoria;
  - el token se muestra una sola vez, vence, solo puede usarse una vez y no queda almacenado en claro;
  - reenlazar conserva identidad, nombre, comunidad, grupos, reglas, limites, solicitudes y configuración remota sin crear un Usuario duplicado;
  - la sesión o token anterior se revoca o conserva únicamente según una política explícita y atómica;
  - un token viejo, revocado, vencido o emitido para otro Usuario falla cerrado;
  - fallos de red o sincronización no consumen falsamente el token ni dejan dos dispositivos con estados contradictorios;
  - la primera sincronización aplica la protección antes de considerar completado el reenlace;
  - la interfaz aclara que datos exclusivamente locales perdidos —por ejemplo historial local cifrado de DAG— no pueden restaurarse desde el servidor;
  - generar, revocar, vencer y consumir el token queda auditado sin borrar datos del Usuario.
- Decision aplicada: mismo Usuario y `device_id`, sea el mismo telefono reparado o uno nuevo; token de un solo uso por 30 minutos. El enlace anterior sigue operativo hasta que el nuevo completa la primera sincronizacion segura y entonces se revoca atomicamente. Los datos exclusivamente locales perdidos no se prometen como recuperables.

### UI-ICON-SYSTEM-01 - Iconos coherentes y modernos

- Estado: `Validacion visual tema claro en SM-A235M DEV 270; faltan tema oscuro y variantes`; definido con el usuario el 2026-07-21. Tipo: sistema visual Android. Prioridad: P2. Esfuerzo: S. Riesgo: bajo.
- Objetivo: unificar iconos de Usuario y Admin con Material Symbols Rounded seleccionados como vectores locales, reutilizando Material Icons Core ya presente y sin incorporar una dependencia pesada.
- Criterio: contorno redondeado para navegacion y acciones; relleno solo para seleccion o alerta; mismo peso, caja optica y tamaño; DAG conserva identidad propia adaptada al sistema. No se usan emojis, clipart, copias de Mercado Pago ni familias mezcladas.
- Resultado en fuente: el catalogo compartido usa las variantes Rounded/AutoMirrored Rounded disponibles en `material-icons-core` y la navegacion inferior Admin adopta el mismo `ProductNavGlyph` ya usado por Usuario. No se agregaron dependencias ni se altero el icono propio de DAG.
- Aceptacion: iconos legibles en claro/oscuro, objetivos tactiles de al menos 44 dp, descripcion accesible cuando comunica una accion o estado y ausencia de aumento innecesario del APK.
- Evidencia fisica: navegacion, acciones de actualizar/buscar/agregar, DAG y escudo de alerta se vieron coherentes y legibles en tema claro del SM-A235M/API 34. No se declara cobertura de tema oscuro, fuente ampliada u otros OEM.

### UI-SYSTEM-BAR-CONTINUITY-01 - Barra superior integrada

- Estado: `Validado fisicamente en tema claro SM-A235M DEV 270`. Tipo: visual, edge-to-edge y compatibilidad Android. Prioridad: P1. Esfuerzo: S. Riesgo: medio.
- Problema: la zona de camara puede quedar blanca y cortar un encabezado turquesa/azul.
- Solucion: barra de estado y zona de recorte toman el color efectivo que exista debajo en cada pantalla; los iconos del sistema cambian entre claros y oscuros para conservar contraste.
- Resultado en fuente: ambas apps sincronizan edge-to-edge con el destino activo. Inicio prolonga el encabezado azul oscuro y usa iconos claros; activacion, listas y secciones secundarias usan superficie blanca e iconos oscuros. DAG conserva su control de tema propio.
- Aceptacion: continuidad sin franjas en Usuario/Admin, navegacion, tema y recreacion; no se promete validacion OEM sin dispositivo real y no se modifica la infraestructura de compatibilidad.
- Evidencia fisica: los dos Home prolongaron el azul oscuro por la zona de camara con iconos claros; Usuarios, Solicitudes, Web, Internet, Cuenta y Ajustes usaron superficie/barra blanca con iconos oscuros, sin franja discordante. Faltan tema oscuro y otros OEM.

### UI-MOTION-SMOOTH-01 - Movimiento sin saltos

- Estado: `Validacion fisica principal en SM-A235M DEV 270; falta reduccion de movimiento`. Tipo: UX, animacion y accesibilidad. Prioridad: P2. Esfuerzo: M. Riesgo: medio.
- Alcance: expansiones, cierres, cambios de seccion, insercion de estados y desplazamiento de contenido usan transiciones sobrias de 200-250 ms, sin rebote ni animacion continua.
- Resultado en fuente: las tarjetas expandibles de Inicio e Internet eliminan la doble animacion de contenedor/contenido y usan una sola transicion de 220 ms; Apps/Web/Seguridad suaviza cabecera y seleccion sin resortes. Si Android desactiva la escala de animador, estas duraciones pasan a cero.
- Aceptacion: cerrar tarjetas de Home no produce saltos; `Apps / Web / Seguridad` cambia suavemente; listas conservan posicion y claves estables; reduccion de movimiento de Android elimina o acorta efectos; no se agrega trabajo costoso constante.
- Evidencia fisica: se abrio y cerro la proteccion de Inicio y se cambiaron Apps/Web/Seguridad sin salto visible; el inventario se desplazo y refresco de forma estable. No se cambio la escala de animacion del telefono.

### ADMIN-USERS-HUB-UX-02 - Usuarios y alta simplificados

- Estado: `Validado fisicamente en SM-A235M DEV 270`. Tipo: UX App Admin. Prioridad: P1. Esfuerzo: M. Riesgo: medio.
- Alcance: superficie blanca continua, filas icono/texto/flecha y toque completo para abrir detalle; desaparece el menu de tres puntos y sus acciones quedan en el detalle correspondiente. `Agregar usuario` enfoca el primer input.
- Estado de refresco: junto a `+`, Buscar y el icono circular de actualizar se muestra `Actualizando`, `Actualizado ahora/hace X min` o error persistente. No se reserva un hueco vacio y no aparece otro banner debajo.
- Resultado en fuente: Usuarios usa una superficie blanca continua, avatar neutro y fila completa con flecha; el archivo permanece en el detalle y se retiro el menu contextual duplicado. El alta solicita foco al campo de nombre y el refresco conserva progreso, error o tiempo relativo en la barra de acciones.
- Aceptacion: busqueda, archivo, reenlace y recuperacion conservan comportamiento; carga/vacio/error no superponen contenido; iconos siguen `UI-ICON-SYSTEM-01`.
- Evidencia fisica: lista continua sin menu de tres puntos, toque de fila, foco y teclado en el primer input del alta, y refresco `Actualizando…` -> `Actualizado ahora` persistente en la barra. No se creo ni archivo ningun usuario.

### ADMIN-USER-SECURITY-BADGES-01 - Indicadores de seguridad con evidencia

- Estado: `Estado sano y falla roja validados en SM-A235M DEV 270; falta amarillo y limpieza posterior`. Tipo: seguridad visible y UX App Admin. Prioridad: P1. Esfuerzo: M. Riesgo: alto.
- Semantica: sin icono verde cuando todo esta bien; advertencia amarilla para nunca verificado, `Unknown` o mas de 100 horas sin comunicacion; rojo solo para VPN, Accesibilidad o Device Admin `Disabled`, o posible desinstalacion.
- Detalle: la fila del Usuario y el segmento `Seguridad` muestran una burbuja roja hasta corregir un problema confirmado. Color, icono y descripcion accesible comunican juntos el estado.
- Resultado en fuente: el mapeo conserva por separado falla confirmada y verificacion pendiente; lista y encabezado omiten toda marca cuando esta sano y muestran un escudo accesible amarillo/rojo solo por la evidencia definida. El selector `Seguridad` replica el nivel con una burbuja sin convertir todo el boton en alerta.
- Aceptacion: no confundir atraso con proteccion caida; conservar la regla de posible desinstalacion; una nueva sincronizacion elimina el indicador aplicable sin borrar auditoria.
- Evidencia fisica: un usuario sano no mostro marca. Al quedar Accessibility realmente inactiva despues del update, el usuario afectado mostro escudo rojo en lista/encabezado, burbuja roja en `Seguridad` y detalle `Accesibilidad: Inactiva`, mientras VPN y antidesinstalacion siguieron activas. No se provoco `Unknown`, 100 horas ni posible desinstalacion.

### ADMIN-SECONDARY-LISTS-UX-02 - Listas Admin continuas

- Estado: `Validacion fisica parcial en SM-A235M DEV 270`. Tipo: sistema de listas App Admin. Prioridad: P2. Esfuerzo: M. Riesgo: medio.
- Alcance: Solicitudes, Avisos, apps, sitios, grupos, horarios y actualizaciones adoptan superficie blanca continua, separadores sutiles, icono, titulo, subtitulo opcional y flecha solo si navega. Switch/boton se conserva para accion directa.
- Limites: proteccion, licencia, alertas criticas, formularios y tarjetas principales de Home permanecen como estan. No cambia logica, datos ni sincronizacion.
- Resultado en fuente de esta seccion: Solicitudes integra el refresco circular y su estado persistente en la barra, con selector de usuarios continuo; Avisos usa fondo blanco continuo; apps y filas internas de grupos pierden tarjetas/bloques repetidos y conservan switch, reloj, formularios y acciones directas. Sitios, horarios y actualizaciones se resuelven en los tickets especificos de Web/Ajustes sin tocar `Agregar sitio`.
- Aceptacion: encabezados/controles fijos donde corresponda, listas sin bloques repetidos, estados de carga/vacio/error coherentes y objetivos tactiles accesibles.
- Evidencia fisica: Solicitudes sostuvo el estado junto a actualizar; Cuenta y Web usaron filas continuas y jerarquia legible. Quedan Avisos y filas reales de sitios, grupos y horarios para cerrar todos los estados.

### USER-SECONDARY-LISTS-UX-02 - Solicitudes y Mis apps estables

- Estado: `Validacion fisica principal en SM-A235M DEV 270; faltan estados con datos`. Tipo: UX y rendimiento App Usuario. Prioridad: P1. Esfuerzo: M. Riesgo: medio.
- Solicitudes: icono circular de actualizar y estado persistente en la misma linea; no aparece un banner separado ni queda espacio vacio.
- Mis apps: titulo, Buscar, Actualizar, estado y filtros permanecen fijos; los filtros bajan a una segunda linea y solo se desplaza el inventario. La lista empieza debajo de controles y termina antes del nav inferior, sin superposicion ni corte abrupto.
- Rendimiento: conservar la lista nativa reciclable. Refrescar mantiene posicion; cambiar busqueda o filtro vuelve arriba; claves/orden estables evitan saltos.
- Resultado en fuente: Solicitudes y Mis apps integran progreso, error y tiempo relativo junto al refresco; Mis apps fija busqueda/actualizacion en la primera linea y filtros compactos en la segunda. La lista nativa solo vuelve arriba cuando cambia busqueda o filtro, no durante un refresco del inventario.
- Aceptacion: superficie blanca continua, ultima fila visible, carga inicial/refresco/error correctos y ninguna alteracion de reglas, medicion o bloqueo.
- Evidencia fisica: Mis apps mantuvo encabezado, acciones y filtros fijos durante dos desplazamientos largos, sin solaparse con el nav; el refresco con 161 apps conservo el inventario y termino en `Actualizado ahora`. Solicitudes vacia repitio `Actualizando…` -> `Actualizado ahora`. Faltan busqueda/filtros y solicitudes con datos.

### ADMIN-WEB-SETTINGS-UX-02 - Web y Ajustes Admin

- Estado: `Validacion fisica principal en SM-A235M DEV 270; faltan sitios configurados`. Tipo: UX App Admin. Prioridad: P2. Esfuerzo: M. Riesgo: medio.
- Web: resumen compacto y grupos por Navegacion, Busqueda segura, DAG, horarios/limites y sitios; filas continuas, switch directo y flecha solo para navegacion. Estado de refresco queda junto al icono circular.
- Ajustes: Cuenta/comunidad, Apariencia, Notificaciones, Actualizaciones, Ayuda/privacidad y acciones sensibles mediante grupos con titulo y filas continuas. Acciones destructivas quedan al final y requieren confirmacion.
- Resultado en fuente: Cuenta/comunidad, licencia y accesos a Panel/Actualizaciones/Ayuda usan grupos blancos continuos con iconos del catalogo. Web simplifica el selector de acceso, alinea switches con iconos y presenta sitios existentes como filas planas, conservando confirmaciones y acciones.
- Fuera de alcance: `Agregar sitio` no se mueve, rediseña ni recibe foco automatico en este ticket.
- Evidencia fisica: Web mostro selector compacto, SafeSearch/DAG con iconos y switches, y Cuenta agrupo identidad/licencia y Panel/Actualizaciones/Ayuda en filas continuas. No habia sitios configurados para inspeccionar sus filas; `Agregar sitio` no se modifico ni uso.

### USER-INTERNET-SETTINGS-UX-02 - Internet y Ajustes Usuario

- Estado: `Validacion fisica principal en SM-A235M DEV 270; falta estado degradado de VPN`. Tipo: UX App Usuario. Prioridad: P2. Esfuerzo: M. Riesgo: medio.
- Internet conserva una unica pieza panoramica compacta; debajo usa filas de solo lectura para VPN, SafeSearch, modo de resultados, DAG y horario. No muestra switch cuando el Usuario no tiene autoridad; una degradacion ofrece una sola reparacion clara.
- Ajustes comparte el lenguaje de grupos y filas de Admin, mostrando solo opciones del rol Usuario. Version queda en Actualizaciones; Ayuda reutiliza su flujo; acciones sensibles aparecen al final.
- Resultado en fuente: Internet usa una unica pieza panoramica compacta y una lista blanca de VPN, SafeSearch, resultados, DAG y horario; solo VPN degradada ofrece Reparar. Ajustes integra estado, version, activacion y Ayuda como filas continuas, preservando como bloques los formularios y flujos de instalacion complejos.
- Aceptacion: lenguaje no tecnico, estados reales, superficie blanca continua, navegacion/Volver coherentes y sin cambiar politica remota.
- Evidencia fisica: Internet mostro una pieza panoramica compacta y filas legibles de VPN activa, SafeSearch, Solo resultados y DAG; Ajustes mostro actualizacion, version 270, activacion y Ayuda, conservando Codigo de emergencia como bloque complejo. No se desactivo VPN para probar Reparar.

### USER-DAG-LAUNCHER-PREFERENCE-01 - DAG como app separada opcional

- Estado: `Validado fisicamente en SM-A235M DEV 270`. Tipo: UX App Usuario. Prioridad: P2. Esfuerzo: M. Riesgo: medio.
- Internet muestra el switch local `DAG como app separada` solo cuando DAG esta habilitado y contemplado por la licencia. El valor predeterminado es activo para conservar el comportamiento de instalaciones existentes.
- Al apagarlo se oculta unicamente la entrada independiente del launcher Android; DAG sigue disponible dentro de Content Filter y no se modifica ninguna politica remota, permiso, paquete ni APK adicional.
- Implementacion: la actividad interna queda siempre habilitada y un `activity-alias` separado controla solo el icono externo; al actualizar tambien se repara el estado deshabilitado que pudiera conservar el componente historico.

- Interfaz: Internet muestra icono DAG moderno y el switch `Mostrar DAG como app separada` solo cuando DAG esta autorizado.
- Comportamiento: encendido muestra el launcher independiente existente; apagado lo oculta pero DAG sigue accesible desde Usuario. No instala otro APK ni cambia permiso, historial, consumo, reglas o actualizaciones.
- Autoridad: Admin/licencia prevalecen y ocultan DAG cuando no esta permitido. Al reabrir, se restaura la preferencia local; usuarios existentes comienzan encendidos para evitar una desaparicion inesperada.
- Aceptacion: cambio reversible sin reinicio/reinstalacion, acceso interno siempre coherente y launcher historico sin bypass cuando DAG esta cerrado.
- Evidencia fisica: el switch comenzo encendido; al apagarlo PackageManager quito solo `DagLauncherAlias` y DAG siguio abriendo desde Internet. Al reencenderlo el alias reaparecio; la preferencia quedo restaurada y no cambiaron permisos, reglas ni APK.

### UI-BANNER-UNIFY-01 - Feedback premium compartido

- Estado: `Publicado y validado visualmente en SM-A235M DEV 246`; aprobado explicitamente el 2026-07-16.
- Tipo: UX y accesibilidad Android. Prioridad: P2. Esfuerzo: S. Riesgo: bajo.
- Causa: coexistian el banner premium y el banner legado; Mis apps seguia usando el legado y las bandejas nuevas de Avisos/Alertas, Actualizaciones Admin y reseteo Admin mostraban feedback como texto plano.
- Resultado: el API legado delega en el banner premium y las superficies nuevas lo usan directamente. La altura deja de ser fija: conserva 42 dp minimos y crece para mensajes largos, con degradado, texto blanco, pez y estado de error consistente.
- Alcance deliberado: los mensajes accionables internos de DAG conservan su contenedor con `Pedir revision`; no se convierte informacion estatica o contenido de tarjetas en banners.
- Aceptacion: no quedan dos estilos de feedback; errores, progreso y resultados transitorios usan el componente compartido; textos largos no se recortan; Usuario y Admin compilan y pasan formato/lint. En SM-A235M, Alertas Admin mostro completo el estado vacio en el banner premium y Actualizaciones Usuario/Admin mostro completo el estado de ultima version.

### USER-UX-PARITY-01 - Navegacion Usuario alineada con Admin

- Estado: `Validado fisicamente en SM-A235M DEV 248`. Aprobado explicitamente por el usuario el 2026-07-16, con Internet separado de Mis apps y mejora de iconos. Pendiente el recorrido en SM-S908E.
- Causa: App Usuario ya compartia tarjetas, fondo y banners premium con Admin, pero sus destinos principales acumulaban historial entre pestanas, mostraban Volver en superficies raiz y reutilizaban la lupa o la campana para funciones distintas.
- Resultado: el nav conserva cuatro destinos independientes —Inicio, Mis apps, Internet y Ajustes—; cambiar de pestana limpia la pila secundaria como en Admin; Mis apps, Internet y Ajustes son raices sin Volver redundante; Solicitudes y Avisos siguen como secciones internas con regreso fijo.
- Iconografia: Mis apps usa una cuadricula propia, Solicitudes una lista de pedidos, Internet el globo y Avisos la campana. El destino Web pasa a mostrarse como `Internet` sin cambiar VPN, Accessibility, DAG, politicas ni datos.
- Validacion: `core-ui:ktlintCheck`, tests DEV Usuario/Admin y builds optimizados de ambas APK correctos; Android CI completo correcto. La publicacion DEV fue unica y los manifiestos/hashes publicos 247 coinciden. Riesgo residual: recorrido visual y navegacion fisica en Samsung.
- Aceptacion: cuatro destinos claros sin duplicados; Internet separado; iconos distinguibles; las raices no muestran Volver; secciones internas, notificaciones y Atrás conservan su destino; Android 10 o posterior sigue soportado.

### SUPERWEB-FUNCTIONAL-VERIFY-01 - Recorrido funcional del propietario

- Estado: `Validacion automatizada DEV correcta; pendiente sesion autenticada publicada`; aprobado explicitamente el 2026-07-16.
- Tipo: QA funcional, permisos y operacion. Prioridad: P1. Esfuerzo: M. Riesgo: alto.
- Cobertura automatizada: TypeScript, ESLint, Next y bundle Sites incluyen Comunidades, Uso DAG, Alertas, Avisos y health; las rutas privadas redirigen a Login. DEV confirma `dag-search` v8, `send-protection-alert` v13 y `send-announcement` v1 activas. Las RPC sensibles rechazan `anon` y los manifiestos publicos de Usuario/Admin son legibles.
- Pendiente autenticado: desde la URL oficial publicada, comprobar lectura de licencia efectiva, token de una sola visualizacion/revocacion, DAG premium, estado de versiones, alertas, creacion de aviso y resultado del push. No se alteraran comunidades reales para simular casos ni se borraran datos.
- Aceptacion: cada modulo carga con el rol Super Admin correcto; acciones muestran exito o fallo real; un usuario anonimo o autenticado sin rol no accede; Avisos conserva el registro aunque FCM no este disponible; toda prueba usa exclusivamente DEV.

### SUPERWEB-MOBILE-UX-01 - Superweb mobile-first

- Estado: `Implementado en fuente; pendiente publicar y validar autenticado`; solicitado explicitamente por el usuario el 2026-07-16.
- Tipo: UX responsive y accesibilidad. Prioridad: P1. Esfuerzo: S. Riesgo: bajo.
- Causa: la navegacion completa ocupaba una fila horizontal en el header, no indicaba el destino activo y Uso DAG dependia de una tabla ancha con desplazamiento lateral.
- Resultado: barra inferior segura en celular, navegacion separada en escritorio, header compacto, objetivos tactiles de 44 px o mas y tarjetas de dispositivo DAG en mobile; escritorio conserva su tabla.
- Aceptacion tecnica: TypeScript, ESLint de `src` y build Next correctos. Pendiente: recorrido visual con sesion Super Admin en la URL oficial publicada, sin modificar datos reales.

### SUPERWEB-OPS-UX-01 - Operacion cotidiana de alertas y avisos

- Estado: `Propuesto tras auditoria funcional`. Tipo: UX operativa y ciclo de vida de auditoria. Prioridad: P2. Esfuerzo: M. Riesgo: medio.
- Faltantes confirmados: busqueda y filtros por comunidad/dispositivo/severidad/fecha; estado leido/no leido; archivo reversible; filtros de avisos vigentes/vencidos y accion de expirar un aviso vigente. Las listas actuales estan acotadas a 200 alertas y 100 avisos sin paginacion visible.
- Decision de seguridad: no implementar `borrar alerta` como eliminacion fisica. Los eventos son auditoria retenida y los tickets de alertas dejan pendiente la politica de retencion. La alternativa propuesta es lectura y archivo reversible, manteniendo trazabilidad.
- Requiere aprobacion separada antes de tocar Supabase: definir retencion, quien puede archivar/restaurar, si el archivo es global o por Super Admin, paginacion y tratamiento de avisos ya entregados.
- Aceptacion propuesta: operar listas grandes desde celular; filtros no cambian datos; archivar no borra evidencia; toda mutacion queda auditada; ninguna vista expone historial de navegacion, consultas o secretos.

### APP-UPDATE-CHANGELOG-01 - Novedades de actualizacion por aplicacion

- Estado: `Pulido publicado DEV 265; pendiente prueba fisica`; aprobado explicitamente el 2026-07-20. Tipo: UX de actualizaciones, comunicacion de producto y publicacion DEV. Prioridad: P2.
- Problema: App Usuario y App Admin pueden informar que existe una version nueva, pero no explican de forma clara que cambia para la aplicacion correspondiente antes de instalarla.
- Solucion propuesta: mostrar en la pantalla de Actualizaciones un bloque `Novedades` asociado a la version ofrecida. App Usuario recibe exclusivamente cambios relevantes para Usuario y DAG; App Admin recibe exclusivamente cambios de administracion. Si se saltaron varias versiones, presentar un resumen acumulado y legible sin mezclar detalles tecnicos internos.
- Evidencia: pedido explicito del usuario del 2026-07-17.
- Esfuerzo: M estimado. Riesgo: medio; notas desactualizadas, cruzadas entre apps o no vinculadas criptograficamente al APK pueden inducir a instalar una version con informacion incorrecta.
- Dependencias: `core-update`; pantallas Actualizaciones de Usuario/Admin; manifiestos publicos DEV; scripts y workflow de publicacion; versionCode/versionName; integridad SHA-256; textos accesibles y compatibilidad con clientes anteriores.
- Duplicados y relacion: complementa `DAG-UPDATE-01` y el bloque de versiones de Superweb, pero no duplica el mecanismo de descarga/instalacion. Esta entrada define comunicacion de cambios por APK.
- Criterios de aceptacion propuestos:
  - Usuario muestra solo novedades de App Usuario y DAG aplicables a la version ofrecida;
  - Admin muestra solo novedades de App Admin aplicables a la version ofrecida;
  - las notas declaran versionCode y corresponden al mismo manifiesto, URL, hash y paquete del APK verificado;
  - saltar varias versiones produce un resumen acumulado sin duplicados ni una lista tecnica inmanejable;
  - una version sin notas usa un mensaje neutro y no bloquea la actualizacion;
  - las notas pueden leerse antes de descargar y siguen disponibles durante el flujo de instalacion;
  - no incluyen secretos, nombres internos sensibles, datos de usuarios ni promesas que excedan lo realmente publicado;
  - clientes antiguos ignoran el campo nuevo sin romper la comprobacion de actualizaciones.
- Decision aplicada: notas fuente separadas por `versionCode` y app, incorporadas al manifiesto firmado operativamente por URL y SHA-256. La pantalla las muestra antes de descargar; el publicador aborta si falta el archivo correspondiente.
- Pulido posterior: ambas pantallas muestran `Ver novedades` junto a la version actual y conservan las notas del manifiesto aun cuando no hay una version nueva. El control expande y contrae el contenido y no altera la decision de actualizacion.

### ANDROID-PHYSICAL-CLOSEOUT-01 - Cierre agrupado sin publicaciones intermedias

- Estado: `Cierre parcial SM-A235M ampliado correctamente en DEV 270; quedan recorridos especificos y SM-S908E`; aprobado explicitamente el 2026-07-16. El telefono auxiliar estuvo disponible despues de la publicacion.
- Tipo: QA Android, compatibilidad y cierre de candidatos. Prioridad: P1. Esfuerzo: M. Riesgo: alto.
- Alcance automatizado completado: tests, ktlint, detekt, lint y builds DEV de Usuario/Admin pasaron localmente con el mismo versionCode. La unica publicacion DEV termino correctamente; manifiestos y APK publicos 246 coinciden en version, paquetes, `minSdk 29`, firma y SHA-256.
- Alcance fisico planificado: banners y fuentes/modo oscuro; actualizacion Usuario/Admin; Play Store/APK/aprobacion; bypass rapido Accessibility; licencia/renovacion; estados DAG candidatos y notificacion push. Cada recorrido se declara solo cuando existe evidencia en el dispositivo correspondiente.
- Evidencia fisica SM-A235M: actualizacion in-place 245 a 246 de ambas apps; firma/datos y las tres protecciones preservados; banner premium, campana Admin y header fijo correctos; ficha y control de fuentes desconocidas Admin accesibles; ficha Usuario y ruta hacia el interruptor Accessibility interceptadas. DAG cargo desde Historial varias imagenes seguras de Wikipedia sin consumir Brave.
- Evidencia fisica DEV 270/API 34: actualizacion in-place de ambas apps con datos y activacion preservados; Android apago Accessibility y la recuperacion guiada/alerta Admin roja respondieron de forma coherente, mientras VPN y Device Admin siguieron activos. Se recorrieron las secciones principales de Usuario/Admin, refrescos persistentes, scroll largo, barras del sistema y el switch reversible de DAG sin crash ni ANR.
- Pendiente fisico: repetir la instalacion iniciada dentro de cada app cuando exista 247 o superior; flujo Play Store/APK/aprobacion; licencia/renovacion; `Mas resultados`; aprobaciones/rechazos DAG y push; calibracion visual sensible; recorrido determinante en SM-S908E.
- Aceptacion: matriz automatizada y CI correctos; publicacion DEV unica; manifiestos/hashes verificables; una lista reproducible conserva cada recorrido fisico pendiente para el proximo acceso al Samsung.

### DATA-DELETE-01 - Borrado definitivo y auditable de usuario

- Estado: `Resuelto y publicado en DEV 241`. El usuario autorizo expresamente crear y archivar un unico usuario nuevo de prueba en DEV el 2026-07-16.
- Tipo: bug, ciclo de vida de datos y seguridad.
- Prioridad: P0.
- Problema: App Admin muestra la opcion de borrar usuario, pero al usarla el banner informa `No se pudo borrar al usuario` y el usuario permanece visible.
- Causa confirmada sin identificar personas: los 2 dispositivos Usuario activos tienen un `community_admins.auth_user_id` valido, pero su `accounts.owner_user_id` no coincide. La RPC legado `revoke_device` exigia el propietario de cuenta y por eso rechazaba ambos casos antes de cambiar datos.
- Solucion implementada: `admin_archive_protected_user` autentica al administrador de comunidad, valida que el Usuario pertenezca a esa comunidad y ejecuta una unica transaccion. Revoca activacion y token, oculta apps, solicitudes, grupos, reglas, limites, codigos y push operativos, y conserva incidentes/consumo junto con un recibo inmutable en `protected_user_deletion_audit`.
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
- Seguridad: `anon` no puede ejecutar la RPC; `authenticated` solo llega al borrado despues de resolver un administrador activo y su comunidad. No se expone Service Role en Android.
- Validacion: un primer intento con plataforma invalida demostro rollback completo y dejo 0 cuentas de prueba. El segundo creo unicamente `Codex archive test 2026-07-16` y lo archivo por la RPC: 0 dispositivos de prueba activos, 1 dispositivo archivado, 1 activacion revocada, 1 recibo y 2 filas afectadas. Los 2 Usuarios reales permanecieron activos. Tests, ktlint y build Admin correctos.
- Decision aplicada: eliminacion logica inmediata de datos operativos y retencion de auditoria, alertas y contadores. No se hace `DELETE` fisico ni se borra la cuenta/comunidad.
- Cierre: la cuenta tecnica se conserva como exige la decision de retencion; dispositivo y datos operativos quedan archivados. No se borro fisicamente ninguna fila.

### BARRIER-UNINSTALL-SCOPE-01 - Proteger solo la desinstalacion de Usuario

- Estado: `Validacion fisica no destructiva correcta en SM-A235M DEV 248; pendiente SM-S908E y desinstalacion efectiva autorizada`. Aprobado por el reporte correctivo explicito del usuario el 2026-07-16. Prioridad: P0. Riesgo: alto.
- Problema: Samsung bloqueaba la desinstalacion de cualquier app cuando la barrera estaba armada, aunque la proteccion debe alcanzar exclusivamente App Usuario.
- Causa: instaladores OEM pueden reutilizar una clase generica de Package Installer para instalar y quitar; esa clase omitia el filtro posterior por identidad de la app objetivo.
- Resultado: una accion de quitar, desactivar o forzar cierre solo se intercepta cuando la pantalla identifica positivamente App Usuario. Admin y otras apps quedan fuera. La proteccion propia de Device Admin Usuario y la politica separada de instalaciones permanecen.
- Aceptacion: desinstalar una app ajena abre la confirmacion normal; Admin puede quitarse; Usuario sigue protegido sin autorizacion de remocion; con autorizacion vigente puede quitarse; tests Samsung genericos, Accessibility, formato y builds de ambas APK correctos.

### BARRIER-USER-HARDENING-01 - Refuerzo de Accessibility, VPN y desinstalacion Usuario

- Estado: `Publicado DEV 250 y validado auxiliarmente; pendiente prueba de SubSettings en el Samsung personal`. Aprobado explicitamente por el usuario el 2026-07-17. Prioridad: P0. Riesgo: alto.
- Causa: la barrera ya impedia completar acciones peligrosas, pero al abrir una pantalla protegida intentaba primero `Atras` y usaba Inicio como respaldo. Ese intervalo podia dejar visible brevemente el boton nativo y conservar una ventana para un toque rapido.
- Resultado: las pantallas protegidas de desinstalacion, desactivacion, cierre forzado, Device Admin, Accessibility y VPN pasan directamente a Inicio desde el primer evento. Si Android conserva la ventana, se repite Inicio cada 100 ms hasta tres veces. Instalaciones normales, actualizaciones autorizadas, mantenimiento y fichas de Admin u otras apps conservan sus rutas separadas.
- Limite Android: sin Device Owner, root o administracion empresarial no existe una API para quitar el boton nativo de Ajustes. El refuerzo reduce su exposicion y neutraliza su uso; no promete que todos los OEM eviten dibujarlo durante cada cuadro de la transicion.
- Seguimiento DEV 250: el usuario confirmo en su Samsung personal con DEV 249 que la ruta manual de VPN permitia mover el switch y el watchdog la reactivaba despues. Causa: esa variante usa una clase generica Android `SubSettings`, no `VpnSettings`. Se protege ahora solo cuando la misma ventana identifica positivamente App Usuario; una `SubSettings` de Admin u otra app sigue disponible.
- Aceptacion: solo Usuario queda protegido; Admin y otras apps mantienen ficha/desinstalacion; Accessibility, VPN y Device Admin no pueden apagarse con toques rapidos; autorizaciones vigentes funcionan; tests cubren salida urgente y separacion del instalador normal; prueba fisica Samsung sin borrar datos.

### BARRIER-A11Y-RACE-01 - Bypass rapido para apagar Accessibility

- Estado: `Implementado en DEV 240; pendiente prueba fisica`. El usuario aprobo avanzar el 2026-07-16 y confirmo que el incidente ocurre en Samsung SM-S908E.
- Tipo: bug de seguridad y antimanipulacion.
- Prioridad: P0.
- Problema: es posible apagar el servicio de Accessibility de App Usuario actuando muy rapido en la pantalla protegida. La barrera cierra la pantalla, pero la accion de desactivacion llega a completarse.
- Causa y solucion DEV 240: el servicio no solicitaba ni procesaba `TYPE_VIEW_CLICKED`, por lo que esperaba cambios posteriores de ventana, contenido o foco. Ahora los clics se aceptan unicamente para pantallas protegibles y provocan salida urgente a Home cuando la barrera aplica; los clics de aplicaciones comunes se descartan de inmediato y el mantenimiento autorizado conserva acceso.
- Evidencia: prueba fisica informada por el usuario el 2026-07-15 y dispositivo confirmado el 2026-07-16: Samsung SM-S908E. El usuario logro apagar Accessibility rapidamente aunque la pantalla se cerraba. La regresion automatizada cubre suscripcion a clics y salida urgente; falta repeticion fisica con el APK publicado.
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
- Validacion fisica candidato DEV 241: actualizacion in-place en Samsung SM-A235M Android 13, con control reforzado, VPN, Accessibility y Device Admin activos. Veinte recorridos a `Aplicaciones instaladas` con un segundo toque inmediato conservaron Accessibility activa 20/20 y expulsaron la pantalla protegida 20/20. La pagina principal de Accesibilidad permanecio accesible deliberadamente. Falta repetir en el SM-S908E donde se reporto el bypass original; hasta entonces no se marca `Resuelto` global.

### BARRIER-DEFAULT-ON-01 - Barrera armada de forma predeterminada

- Estado: `Implementado en DEV 241; pendiente prueba fisica`. Aprobado por el usuario dentro de la ejecucion consecutiva de pendientes el 2026-07-16.
- Tipo: seguridad, configuracion predeterminada y UX de onboarding.
- Prioridad: P1.
- Problema: la barrera depende de una accion posterior del administrador para quedar armada, lo que puede dejar dispositivos configurados con una proteccion antimanipulacion menor a la esperada.
- Resultado DEV 241: App Usuario intenta el armado automatico solo con VPN real, Accessibility y Device Admin habilitados y sin desactivacion intencional. La RPC por token de dispositivo modifica exclusivamente controles nunca decididos (`command_revision = 0`); una decision previa del administrador nunca se revierte automaticamente.
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
- Decisiones cerradas: dispositivos existentes con revision cero son elegibles al quedar completamente sanos; cualquier revision administrativa se respeta; Admin conserva la autoridad para desarmar; offline o permisos incompletos mantienen el control previo y reintentan en una comprobacion de salud futura. Falta validar fisicamente activacion, reinicio, actualizacion in-place y desarmado explicito.

### OPS-METRICS-01 - Bateria, trafico y estabilidad

- Estado: `Candidato DEV 241 optimizado y con linea base corta; pendiente muestra de 24 h`.
- Hallazgo fisico: Accessibility reevaluaba correctamente la app foreground cada 250 ms, pero en DEV tambien insertaba y recortaba un diagnostico Room en cada ciclo aunque nada cambiara. En una ventana de 10 s se observaban unas 40 escrituras equivalentes.
- Solucion: la frecuencia de enforcement permanece en 250 ms; el log y diagnostico se emiten solo cuando cambian paquete, decision, minuto observado o cantidad de reglas/limites. La repeticion fisica produjo 2 diagnosticos en 10 s (inicio y cambio de minuto) y guardo la sesion al salir.
- Linea base corta SM-A235M: `Mis apps`, diez desplazamientos, 245 cuadros, 2,04 % lentos, p50 17 ms, p90/p95 20 ms y p99 30 ms. Sin ANR ni crash de Usuario/Admin durante la sesion; VPN, Accessibility y Device Admin siguieron activos.
- Pendiente honesto: una sesion interactiva corta no demuestra consumo prolongado. Ejecutar una muestra de 24 h con bateria inicial/final, bytes por UID, reinicios de proceso y ANR, sin recopilar consultas, URLs ni contenido.

### USAGE-REAL-01 - Uso foreground y listas estables

- Estado: `Validado fisicamente y publicado en DEV 241`.
- Arquitectura comprobada: no depende de `PACKAGE_USAGE_STATS`; Accessibility mide con reloj monotono, crea checkpoints y guarda sesiones en Room. Esto evita pedir otro acceso especial de Android.
- Evidencia SM-A235M: Calculadora Samsung permanecio foreground mas de un minuto, el servicio guardo sesiones y reporto 2 minutos persistidos. `Mis apps` mantuvo 156 aplicaciones y el filtro `Calculadora` devolvio una unica fila estable.
- Rendimiento: la deduplicacion de OPS-METRICS elimina escrituras diagnosticas repetidas sin cambiar tracking, limite ni tiempo de reaccion del bloqueo.

### UI-POLISH-01 - Consistencia visual y accesibilidad

- Estado: `Publicado DEV 243; pendiente comprobacion visual con el telefono desbloqueado`.
- Correccion: Home Usuario ahora informa 6 secciones, coherente con la incorporacion de Avisos.
- Evidencia fisica: saludo personalizado, tarjetas, navegacion inferior, DAG claro, Admin, campanita, Alertas y Avisos sin recortes en 384 dp/Android 13. Lint completo de Usuario/Admin sin errores de accesibilidad; conserva advertencias tecnicas conocidas no bloqueantes.
- Seguimiento DEV 243: las cabeceras con Volver quedan fijas; la campanita Admin sube al encabezado; Inicio Usuario deja de repetir destinos del nav; Superweb muestra en la ficha de comunidad el estado de actualizacion contra los manifiestos DEV y fija su cabecera Volver.
- Validacion: matriz integral de 1.013 tareas Gradle, TypeScript, ESLint, Next y bundle Sites correctos. Ambos APK se instalaron in-place en SM-A235M y conservaron Accessibility, Device Admin y servicios de proteccion/VPN. Falta solo la comprobacion visual con pantalla desbloqueada y la matriz adicional de tamanos de fuente/modo oscuro.

### ADMIN-ALERTS-UX-01 - Campanita de alertas de seguridad en App Admin

- Estado: `Validado visualmente en SM-A235M DEV 248; pendiente comprobar un evento confirmado real`. Aprobado por el usuario al ordenar ejecutar todos los tickets el 2026-07-16.
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

- Estado: `Resuelto y validado DEV 242 en SM-S908E`. Aprobado por el usuario al ordenar ejecutar todos los tickets y redefinido tras el reporte fisico del 2026-07-16.
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
- Implementacion: un `activity-alias` reversible contiene la unica entrada Launcher de App Usuario. Se oculta sin matar el proceso solamente cuando hay activacion, proteccion armada, licencia efectiva y VPN activa; reaparece si falta alguna condicion o existe autorizacion temporal de Ajustes/retiro.
- Acceso alternativo: la notificacion foreground de la VPN abre directamente `MainActivity` mediante una accion interna del paquete aunque el alias Launcher este oculto. El vencimiento de mantenimiento vuelve a ocultar el alias automaticamente.
- Compatibilidad elegida: Android normal desde el minimo soportado, sin Knox, root, Device Owner ni restablecimiento. El comportamiento visual exacto del launcher puede variar por fabricante y no se promete un bloqueo de sistema equivalente a MDM.
- Validacion previa: tests de decision visible/oculto, compilacion, manifiesto combinado, tests DEV y formato de Usuario/VPN correctos. La estrategia de ocultamiento se sustituyo en la revision siguiente por el launcher siempre visible y el bootstrap oficial.
- Revision DEV 242: ocultar el alias dejo al SM-S908E sin una ruta obvia a Usuario y, al no existir Admin todavia, el bloqueo correcto del instalador de Chrome impidio completar el alta de Admin. El alias queda siempre visible. App Usuario incorpora un bootstrap Admin que valida manifiesto, SHA-256, packageName y firma antes de abrir la autorizacion interna; Android sigue mostrando su confirmacion normal. El instalador directo del navegador permanece bloqueado.
- Validacion SM-S908E: el bootstrap oficial instalo Admin sin borrar datos. La autorizacion temporal se retiro al terminar y el control DEV quedo `armed=true`, sin alcance de mantenimiento y con revision 5 aplicada.

### BARRIER-SETTINGS-VISIBILITY-01 - Controles peligrosos fuera de alcance

- Estado: `Idea`; no aprobado para diagnostico tecnico ni codigo. Tipo: seguridad, antimanipulacion y UX de Ajustes. Prioridad: P1.
- Problema: en la superficie identificada por el usuario como `Seguridad` siguen visibles botones para eliminar aplicaciones y un acceso a la configuracion de VPN. Aunque la barrera ya protege recorridos posteriores, mostrar o permitir alcanzar esos controles facilita intentos de desactivacion y transmite una proteccion menos integrada.
- Solucion propuesta: cuando la barrera este armada, ocultar esos controles si pertenecen a una interfaz propia de Content Filter. Si son controles nativos de Android que no pueden retirarse con la autoridad disponible, neutralizar el acceso antes de que se dibuje o pueda accionarse y ofrecerlos solo durante mantenimiento administrativo autorizado.
- Evidencia: solicitud del usuario del 2026-07-16. El handoff confirma que la barrera ya bloquea pantallas criticas de desinstalacion, App Info y configuracion VPN; esta idea endurece su visibilidad y acceso, no reemplaza esas defensas.
- Esfuerzo: M estimado. Riesgo: alto; una deteccion demasiado amplia puede impedir ajustes legitimos, bloquear VPN ajenas o dejar sin ruta de reparacion al administrador.
- Dependencias: `BARRIER-ANDROID-01`, `BARRIER-A11Y-RACE-01` y mantenimiento temporal; Accessibility; resolucion de paquetes y pantallas de Ajustes por OEM; estado real de VPN; flujos de reparacion y recuperacion offline.
- Duplicados y relacion: seguimiento de la barrera ya resuelta para bloqueo funcional. No duplica `BARRIER-LAUNCHER-01`, que trata la accion rapida desde el icono de App Usuario.
- Criterios de aceptacion propuestos:
  - con la barrera armada, el usuario no puede ver o alcanzar acciones que eliminen, desinstalen o deshabiliten las aplicaciones protegidas dentro del alcance acordado;
  - el acceso a la configuracion de la VPN protegida no queda disponible fuera de una ventana de mantenimiento autorizada;
  - si Android no permite ocultar un control nativo, la barrera actua antes de que pueda confirmarse la accion y no depende solamente de reparar el estado despues;
  - Ajustes de seguridad no relacionados, otras aplicaciones y VPN ajenas permanecen disponibles segun la politica acordada;
  - mantenimiento y recuperacion ofrecen una ruta clara para diagnosticar, reparar o cambiar la VPN sin desarmar permanentemente la proteccion;
  - intentos bloqueados conservan la auditoria y alertas vigentes sin generar duplicados.
- Decisiones pendientes para la entrevista del ticket: pantalla exacta llamada `Seguridad`; aplicaciones alcanzadas por `eliminar`; si se trata de controles propios o Ajustes Android; alcance sobre otras VPN; autoridad Admin o Super Admin; comportamiento durante mantenimiento; dispositivos y versiones Android soportados.

### Checklist de mejoras visuales y navegacion DAG

#### DAG-NAV-UX-01 - Barra de navegacion simplificada

- Estado: `Resuelto` en DEV 234; aprobado por el usuario e implementado progresivamente desde DEV 224. Pendiente prueba fisica. Tipo: UX y navegacion. Prioridad: P2.
- Problema: la barra expone controles secundarios y resta espacio y claridad a las acciones principales.
- Solucion propuesta: ocultar `Atras`, `Adelante` y `Actualizar` de la barra y moverlos al menu de tres puntos; mostrar `Home` a la izquierda y reemplazar `Actualizar` por una accion visible de nueva pestana. El menu de tres puntos tambien debe estar disponible en Home.
- Evidencia: checklist visual aportado por el usuario el 2026-07-15.
- Esfuerzo: M. Riesgo: medio; esconder acciones frecuentes puede reducir descubribilidad o accesibilidad.
- Dependencias: navegacion WebView, menu de tres puntos, Home DAG y gestion de pestanas.
- Duplicados y relacion: se relaciona con `DAG-TABS-02`, pero cambia la jerarquia de controles y no duplica el selector ya resuelto.
- Criterios de aceptacion propuestos: Home y nueva pestana son acciones visibles; atras, adelante y actualizar funcionan desde el menu; Home conserva acceso al menu de tres puntos; los estados no disponibles se ocultan o representan correctamente; no se pierde historial ni estado de pestanas.
- Decisiones pendientes para el ticket: iconografia, orden y acciones disponibles del menu en Home; gestos alternativos y comportamiento del boton fisico Atras.

#### DAG-WEB-INTERACTION-02 - Menus y controles dinamicos de paginas permitidas

- Estado: `Publicado en DEV 271; mejora parcial y seguimiento abierto`. Tipo: bug funcional, rendimiento del hilo principal y compatibilidad WebView. Prioridad: P1.
- Problema: menus y otros controles propios de algunas paginas permitidas no abren dentro de DAG. Zara Argentina es el caso de referencia aportado por el usuario; su menu funciona en un navegador normal.
- Causa: el observador de seguridad reaccionaba a cada cambio de `class` o `style` recorriendo nuevamente todos los descendientes del nodo y calculando sus fondos. En aplicaciones Web grandes y dinamicas, una interaccion puede provocar trabajo sincronico masivo antes de que el panel se dibuje.
- Alcance: en mutaciones de atributos, volver a revisar solamente el elemento que cambio. El recorrido profundo se conserva para el documento inicial y para subarboles nuevos, por lo que no se relaja la inspeccion de imagenes, fondos, video, audio, canvas o iframes incorporados dinamicamente.
- Privacidad y seguridad: la correccion no debe permitir que la pagina capture acciones del menu, superponerse a controles de seguridad ni exponer URL, historial o contenido fuera del dispositivo.
- Esfuerzo: M estimado. Riesgo: medio; una correccion de capas o foco puede introducir regresiones tactiles, visuales o de navegacion.
- Dependencias: saneamiento DOM, MutationObserver, WebView, aplicaciones Web de pagina unica, carga dinamica e intercepcion visual.
- Duplicados y relacion: corrige la interaccion dentro del contenido Web; no cambia el menu de tres puntos de DAG ni reabre `DAG-NAV-UX-01`.
- Criterios de aceptacion:
  - el menu propio de Zara abre y cierra sin congelar la pagina;
  - cambios repetidos de clase o estilo no disparan recorridos profundos del subarbol;
  - nodos nuevos siguen siendo inspeccionados profundamente antes de quedar disponibles;
  - menus, acordeones, selectores y dialogos de paginas permitidas conservan interaccion sin habilitar video, audio, canvas o iframes ordinarios;
  - las pruebas automaticas cubren la diferencia entre mutacion de atributos y agregado de subarboles, y la comprobacion funcional incluye Zara y otra aplicacion Web dinamica.
- Resultado DEV 271: las mutaciones de atributos revisan solo el nodo afectado. El usuario reporto despues de la publicacion que el menu de Zara y otros botones siguen fallando; el recorrido profundo de subarboles nuevos queda fuera del fix original y pasa a `DAG-WEB-INTERACTION-03`.

#### DAG-WEB-INTERACTION-03 - Subarboles dinamicos sin bloqueo del hilo principal

- Estado: `Propuesto`; diagnostico preparado el 2026-07-22, pendiente aprobacion explicita para codigo. Tipo: bug funcional, rendimiento WebView y seguridad de contenido. Prioridad: P1.
- Evidencia: el usuario confirma que DEV 271 no resolvio todos los botones. En un navegador normal, el menu actual de Zara Argentina abre correctamente y crea un dialogo grande con pestañas, acordeones y muchos enlaces; no depende de canvas. En DAG, cada nodo agregado puede activar `dagSecureNode(node, true)`, que recorre sincronamente videos, audios, canvas, iframes, imagenes y fondos de todo el subarbol.
- Diagnostico: DEV 271 corrigio la ruta de cambios de atributos, pero no la insercion o hidratacion de subarboles grandes. Registros solapados del `MutationObserver` pueden volver a recorrer descendientes y competir con el evento tactil y el pintado del menu. Service Workers bloqueados, iframes eliminados y otras restricciones pueden explicar sitios diferentes y deben distinguirse mediante codigos diagnosticos locales, no relajarse en conjunto.
- Alcance propuesto: cola unica de saneamiento por pagina, deduplicada por raiz; bloqueo inmediato por CSS/intercepcion para video, audio, canvas, iframes e imagenes mientras el trabajo se procesa; recorrido acotado por lote fuera del callback del observador; cancelacion por navegacion; y diagnostico local por capacidad bloqueada. No agregar excepciones por dominio para Zara.
- Esfuerzo: M estimado. Riesgo: alto; diferir demasiado el saneamiento podria mostrar contenido antes de decidir y hacerlo completamente sincronico mantiene el congelamiento.
- Dependencias: `DAG-WEB-INTERACTION-02`, `DagWebViewComponents`, `MutationObserver`, intercepcion local de imagenes, ciclo de vida de pagina y pruebas WebView/instrumentadas.
- Criterios de aceptacion:
  - el menu actual de Zara abre y cierra, permite cambiar categoria y desplegar un acordeon sin congelamiento ni doble toque;
  - otra aplicacion Web dinamica con dialogo o drawer pasa el mismo recorrido para evitar una excepcion puntual;
  - subarboles agregados se deduplican y procesan por lotes con un presupuesto medible del hilo principal;
  - video, audio, canvas, iframes no autorizados e imagenes no clasificadas permanecen invisibles desde su insercion;
  - cambiar de pagina cancela la cola anterior y no mezcla nodos ni decisiones;
  - pruebas automaticas cubren raices solapadas, rafagas de mutaciones, cancelacion y barreras inmediatas; la aceptacion final incluye prueba fisica con DEV publico.

#### DAG-SEARCH-CONTINUITY-03 - Buscar aunque la consulta sea incierta

- Estado: `Propuesto`; causa confirmada el 2026-07-22, pendiente aprobacion explicita para codigo. Tipo: comportamiento de busqueda, precision local y UX. Prioridad: P1.
- Problema: DAG a veces no consulta el buscador y muestra `no tiene suficiente certeza`, obligando a reformular incluso consultas legitimas. El usuario rechazo expresamente este comportamiento.
- Causa confirmada: `DagBrowserViewModel.search` cancela la busqueda antes de `DagSearchRepository.search` cuando `classifyQuery` devuelve `Uncertain`. Esa salida incluye terminos ambiguos, contexto sensible, confianza semantica media y modelo no disponible; hoy todos reciben el mismo tratamiento aunque no exista una regla dura de bloqueo.
- Decision de producto propuesta: una consulta incierta pero no bloqueada debe continuar una sola vez hacia Brave. DAG filtra cada resultado y vuelve a analizar cada pagina; resultados bloqueados no se muestran y paginas ambiguas abren con las barreras preventivas existentes. Reglas Admin, dominios prohibidos, terminos explicitos y evidencia semantica alta siguen bloqueando antes de consumir una consulta.
- Privacidad y costo: continuar envia a Brave la consulta que el usuario decidio buscar y consume el cupo normal; no agrega telemetria, cache remoto ni historial en Supabase. Un estado tecnico de modelo ausente debe registrarse sin incluir la consulta.
- Esfuerzo: M estimado. Riesgo: alto; permitir indiscriminadamente toda incertidumbre podria enviar o mostrar una intencion que debia bloquearse, y reintentos pueden duplicar consumo.
- Dependencias: clasificador de consulta, embudo de resultados, contador Brave, `DagSearchRequestTracker`, politica adaptativa de pagina y mensajes DAG.
- Criterios de aceptacion:
  - una consulta cotidiana clasificada `Uncertain` ejecuta exactamente una busqueda y nunca pide reformular por falta de certeza;
  - resultados se filtran individualmente y una pagina incierta se analiza de forma preventiva antes de mostrarse;
  - reglas duras, categorias explicitas, dominios y plataformas bloqueadas siguen cerrando antes de Brave;
  - doble toque, sugerencias y cambio de pestaña no duplican consultas ni mezclan resultados;
  - modelo ausente o corrupto tiene una politica explicita y probada, sin permitir por accidente ni exponer la consulta en logs;
  - tests incluyen español, ingles y hebreo, contexto educativo/medico, terminos ambiguos y riesgo explicito.

#### DAG-PROTECTED-MODE-UX-04 - Estado preventivo entendible

- Estado: `Propuesto`; causa confirmada el 2026-07-22, pendiente aprobacion explicita para codigo. Tipo: claridad UX y explicabilidad. Prioridad: P2.
- Problema: el mensaje `Abierto con proteccion adicional` no explica qué ocurrio, qué cambia ni si el usuario debe hacer algo.
- Significado tecnico actual: aparece cuando el texto esta vacio, la clasificacion es incierta o la evidencia semantica no alcanza el bloqueo. La pagina se muestra con las barreras Web normales y no se guarda como aprobacion rapida completa. No activa un segundo antivirus, una VPN distinta ni una revision humana automatica.
- Solucion recomendada: no mostrar un aviso persistente cuando no hay una accion necesaria. Mantener `Analizando` durante la compuerta y luego abrir la pagina normalmente; si se conserva una explicacion, usar una ayuda breve y accesible como `Modo preventivo: DAG seguirá revisando esta página`, sin prometer proteccion total.
- Esfuerzo: S estimado. Riesgo: bajo; ocultar demasiado contexto puede dificultar soporte, mientras que un mensaje tecnico permanente confunde y parece un error.
- Dependencias: `DAG-SEARCH-CONTINUITY-03`, estado de pagina, linea de feedback y accesibilidad.
- Criterios de aceptacion:
  - una pagina abierta no muestra `proteccion adicional` como error ni exige una accion inexistente;
  - el usuario puede entender en lenguaje simple que DAG sigue filtrando contenido;
  - `Analizando`, `Bloqueado`, `Sin conexion` y `Pendiente de aprobacion` permanecen diferenciados;
  - el mensaje no tapa controles ni reaparece en cada mutacion o navegacion interna;
  - TalkBack y fuente grande comunican el estado sin texto recortado.

#### DAG-HOME-UX-01 - Home con buscador central

- Estado: `Resuelto` en DEV 234; aprobado por el usuario e implementado progresivamente desde DEV 224. Pendiente prueba fisica. Tipo: UX, identidad y Home DAG. Prioridad: P2.
- Problema: la entrada del navegador no comunica con suficiente claridad que DAG es un buscador propio de Internet kosher.
- Solucion propuesta: presentar un buscador grande y centrado, con marca `DAG` y una leyenda como `Internet kosher` u otra frase a definir.
- Evidencia: checklist visual aportado por el usuario el 2026-07-15.
- Esfuerzo: S. Riesgo: bajo; el texto elegido debe evitar promesas absolutas que excedan la proteccion tecnica real.
- Dependencias: Home DAG, identidad visual, accesibilidad y textos de producto.
- Duplicados y relacion: evoluciona la Home del navegador ya implementado; no modifica el motor de busqueda ni la clasificacion.
- Criterios de aceptacion propuestos: buscador central claro y enfocable; identidad DAG visible; buen comportamiento con teclado, tamanos de fuente y orientaciones soportadas; ninguna afirmacion engañosa sobre cobertura.
- Decisiones pendientes para el ticket: leyenda definitiva, tamanos, contenido auxiliar y estados vacio/cargando/error.

#### DAG-TABS-UX-01 - Manejo mejorado de pestanas

- Estado: `Resuelto` en DEV 226; pendiente prueba fisica. Tipo: UX y gestion de pestanas. Prioridad: P2.
- Problema: el manejo actual de multiples pestanas necesita una experiencia mas rapida y clara para crear, identificar, cambiar y cerrar pestanas.
- Solucion propuesta: evolucionar el selector visual existente y coordinarlo con el nuevo boton visible de nueva pestana.
- Evidencia: checklist visual aportado por el usuario el 2026-07-15.
- Esfuerzo: M. Riesgo: medio; cambios de ciclo de vida WebView pueden aumentar memoria o perder estado de navegacion.
- Dependencias: `DAG-TABS-02`, presupuesto de memoria, recuperacion del renderer, historial y barra de navegacion.
- Duplicados y relacion: seguimiento de `DAG-TABS-02`, que permanece resuelto; este item captura mejoras posteriores sin reabrir aquel alcance.
- Criterios de aceptacion propuestos: crear, cambiar y cerrar pestanas es evidente; la pestana activa se distingue; se preserva el limite de recursos; cerrar la ultima pestana vuelve a un estado seguro; no se mezclan historiales entre pestanas.
- Decisiones cerradas: maximo ocho; orden local; miniaturas solo efimeras; cierre individual/masivo; metadatos y resultados cifrados sobreviven al proceso; paginas restauradas recargan y se revalidan sin conservar WebViews inactivos.

#### DAG-THEME-01 - Contraste, zona de camara y texto de busqueda

- Estado: `Corregido en DEV 239; pendiente prueba fisica`. Tipo: bug visual, accesibilidad visual y personalizacion. Prioridad: P2.
- Problema: la barra superior del sistema ubicada alrededor de la camara puede permanecer blanca y cortar visualmente la interfaz. No debe tener un color fijo distinto de la pantalla DAG que se encuentra debajo.
- Solucion propuesta: hacer que toda la zona superior alrededor de la camara coincida con el color efectivo de la interfaz DAG en la pantalla y tema actuales, y elegir iconos claros u oscuros segun ese fondo. Se conservan las opciones `Claro`, `Oscuro` y `Segun el dispositivo`.
- Resultado DEV 239: DAG pinta su propia superficie detras de la barra edge-to-edge y aplica el inset solo al contenido; ya no depende de una asignacion de color ignorada por Android moderno. Conserva iconos legibles y usa texto compacto en la barra para que una consulta larga no quede cortada por Home, nueva pestana, contador y menu.
- Evidencia: observacion inicial del usuario el 2026-07-15 y prueba fisica negativa informada el 2026-07-16: la zona superior de la camara sigue blanca y no queda integrada. El usuario preciso el mismo dia que debe coincidir con el color de DAG, no limitarse a cambiar entre blanco y negro.
- Esfuerzo: S estimado. Riesgo: bajo; una correccion incompleta puede dejar iconos ilegibles o comportamientos distintos segun version Android, recorte o fabricante.
- Dependencias: tema Compose, barras del sistema, iconos, WebView y persistencia local de preferencia.
- Duplicados y relacion: la evidencia fisica invalido el cierre de DEV 238. El recorte del texto se agrupa en el mismo ticket por compartir la barra superior; puede relacionarse con `UI-POLISH-01`, pero se conserva separado por afectar especificamente DAG.
- Criterios de aceptacion propuestos: la zona superior alrededor de la camara coincide visualmente con el fondo efectivo de DAG en Home, resultados, pagina Web, historial, selector de pestanas y estados de analisis; los iconos del sistema permanecen legibles; `Segun el dispositivo` sigue el cambio del sistema sin reiniciar DAG; la preferencia persiste; no aparecen franjas ni destellos blancos al navegar; se valida fisicamente en el dispositivo afectado y al menos con los tipos de recorte soportados.
- Evidencia DEV 239: captura fisica del usuario con franja blanca y `yeshrun` recortado; regresion corregida mediante edge-to-edge compatible con `targetSdk 36`. Falta repetir la matriz visual en el Samsung afectado.

#### DAG-SEARCH-FP-02 - Yeshurun social sin falso positivo

- Estado: `Implementado en DEV 239; pendiente prueba fisica`. Tipo: precision del clasificador local. Prioridad: P1. Esfuerzo: S. Riesgo: medio.
- Causa: el modelo neuronal y el respaldo compacto no daban la misma decision para la consulta institucional breve `yeshrun instagram`, produciendo primero incertidumbre y luego bloqueo en intentos equivalentes.
- Alcance: vocabulario cerrado para `yeshrun`/`yeshurun` y destinos institucionales o sociales conocidos. No cambia umbrales ni permite que palabras explicitas, drogas u otros terminos riesgosos queden ocultos por la excepcion.
- Evidencia y pruebas: controles automatizados permiten `yeshrun instagram` y `Yeshurun pagina oficial`, y bloquean `yeshrun instagram videos porno`.
- Criterios de aceptacion: la consulta segura tiene una decision estable; variantes con riesgo siguen bloqueadas; no se altera la prioridad de dominios, reglas Admin ni plataformas visuales bloqueadas.

#### DAG-MODESTY-CHEST-02 - Regiones cubiertas sin rostro

- Estado: `Implementado en DEV 239; pendiente prueba fisica`. Tipo: seguridad visual y calibracion local. Prioridad: P0. Esfuerzo: S. Riesgo: alto.
- Causa: pecho, genitales o gluteos cubiertos solo activaban la politica de modestia si la misma imagen tambien detectaba un rostro femenino; recortes de producto y escotes sin rostro escapaban del blur.
- Alcance: esas tres regiones cubiertas activan blur fuerte por si mismas. Axila y abdomen conservan la exigencia de contexto femenino para limitar falsos positivos. No se relaja ninguna decision NSFW existente.
- Referencia aceptada: el desenfoque fuerte mostrado en la pagina de bikinis H&M aportada por el usuario es correcto y debe permanecer.
- Criterios de aceptacion: escotes y recortes de pecho se desenfocan aunque no haya rostro; bikinis siguen fuertemente desenfocados; rostros benignos aislados y ropa comun sin regiones detectadas no se bloquean por esta regla.

#### DAG-IMAGE-DELIVERY-02 - Cola completa para paginas densas

- Estado: `Implementado en DEV 239; pendiente prueba fisica`. Tipo: confiabilidad de recursos Web y rendimiento. Prioridad: P1. Esfuerzo: M. Riesgo: medio.
- Causa: solo tres imagenes se analizaban en paralelo y el clasificador era serializado; las solicitudes posteriores abandonaban la espera del turno a los ocho segundos y recibian un recurso neutro, por eso parecian cargar solo las primeras fotos.
- Alcance: espera justa sin vencimiento interno del turno, cancelacion por generacion al cambiar o cerrar pagina, presupuesto de 400 recursos y cache efimera de 64 entradas/16 MiB. Persisten el timeout de red de ocho segundos, HTTPS, defensa SSRF, maximo de descarga, clasificacion previa y fallo cerrado.
- Riesgos: una pagina muy grande puede tardar mas en completar su cola y usar mas memoria efimera; los topes siguen acotados y abandonar la pagina cancela el trabajo pendiente.
- Criterios de aceptacion: al desplazarse por una tienda densa se procesan tambien las fotos posteriores; ninguna imagen se muestra antes de clasificarse; cambiar de pagina no mezcla resultados ni deja trabajo viejo; fallos reales de red o formato permanecen neutros y seguros.

#### DAG-LOCAL-IMAGE-PERF-03 - Apertura local rapida de fotos

- Estado: `Implementado candidato DEV 271; pendiente CI, publicacion y medicion fisica`. Tipo: rendimiento, privacidad y seguridad visual. Prioridad: P1.
- Problema: una pagina con varias fotos puede tardar aproximadamente entre 6 y 8 segundos en completar su primera carga visual porque la descarga, decodificacion y clasificacion local forman una cola costosa y parte del trabajo puede repetirse.
- Decision aprobada: mantener todo el analisis de imagenes dentro del telefono. No se usara un modelo de IA online, no se enviaran fotos a terceros y no se persistiran originales para acelerar cargas futuras.
- Solucion propuesta: dos clasificadores locales con limite estricto; prioridad para recursos visibles; una sola decodificacion por imagen; cache efimera de decisiones por hash de contenido, audiencia y version de calibracion; precarga maxima de la siguiente pantalla; y XNNPACK o NNAPI solo cuando una medicion controlada del dispositivo demuestre que mejora el tiempo con fallback seguro.
- Objetivo de rendimiento, no promesa: intentar reducir una primera carga comparable de 6-8 segundos a aproximadamente 2,5-3,5 segundos, y una repeticion dentro de la misma sesion a aproximadamente 1-2 segundos. El resultado real debe medirse en hardware objetivo y documentarse con percentiles, cantidad y tamano de imagenes.
- Guardas de seguridad: ninguna imagen se entrega al WebView antes de una decision local; errores, cancelaciones y formatos no admitidos fallan cerrados; cambiar de pagina invalida trabajo viejo; los limites de CPU, memoria, red y concurrencia prevalecen sobre la velocidad.
- Privacidad de diagnostico: las metricas pueden registrar tiempos, conteos, tamanos, aciertos de cache y tipo de acelerador, pero no URLs, consultas, fotos, miniaturas ni pixeles.
- Esfuerzo: M estimado. Riesgo: alto; demasiada concurrencia puede empeorar memoria, temperatura o latencia, y una cache mal versionada podria reutilizar una decision obsoleta.
- Dependencias: `DAG-IMAGE-DELIVERY-02`, politica por audiencia, versiones de calibracion, ciclo de vida de pagina, presupuesto de memoria y modelos TFLite locales.
- Duplicados y relacion: extiende el rendimiento de la entrega ya implementada; no cambia criterios kosher, umbrales, reglas Admin ni el modo Extra Kosher.
- Criterios de aceptacion:
  - las imagenes visibles se atienden antes que la precarga y no quedan bloqueadas detras de recursos fuera de pantalla;
  - cada recurso se descarga y decodifica como maximo una vez por intento, salvo reintento explicito seguro;
  - la cache se invalida por cambio de contenido, audiencia o calibracion y se borra al cerrar la sesion o por presion de memoria;
  - la concurrencia queda limitada a dos clasificaciones y conserva una ruta serial de respaldo;
  - se comparan antes/despues en la misma pagina y dispositivo, incluyendo primera carga, repeticion, scroll, cambio de pagina, memoria y temperatura;
  - no se agregan servicios externos, claves, costos por IA ni salida de imagenes del telefono.
- Resultado candidato DEV 271: dos clasificadores locales independientes atienden la cola acotada; la preparacion inicial prioriza una pantalla y precarga como maximo la siguiente; el asentamiento visual baja de 1.200 a 300 ms. La cache de respuestas efimera existente sigue evitando reclasificar una URL ya resuelta dentro de la pagina. La cache por hash entre paginas y la seleccion adaptativa NNAPI quedan fuera de este primer candidato hasta contar con una invalidacion de calibracion y benchmarks seguros.

#### DAG-ULTRA-KOSHER-01 - Modos de imagenes administrables

- Estado: `Idea`; no aprobado para codigo. Tipo: seguridad visual, configuracion Admin y UX DAG. Prioridad: P1.
- Problema: el filtrado visual probabilistico mejora la navegacion, pero no puede asegurar 100 % de aciertos. Eliminar imagenes por completo deja huecos y produce una experiencia visual pobre; algunas comunidades o familias necesitan una opcion mas estricta sin romper la estructura de las paginas.
- Solucion propuesta: ofrecer al administrador dos modos excluyentes por Usuario o dispositivo: `Fotos filtradas`, que conserva el analisis local actual y muestra una nota clara de que DAG no garantiza 100 %; y `Ultra kosher`, que mantiene los espacios y dimensiones de la pagina pero presenta todas las fotografias con blur fuerte e irreversible, sin accion del Usuario para revelarlas.
- Evidencia: propuesta explicita del usuario del 2026-07-17.
- Esfuerzo: M estimado. Riesgo: alto; distinguir fotografias de logos, iconos, mapas, CAPTCHA y controles funcionales puede afectar navegacion o permitir excepciones inconsistentes. Un blur aplicado solo por CSS podria ser reversible y no cumple el objetivo.
- Dependencias: politica visual DAG; rasterizacion/blur irreversible existente; carga lazy y recursos dinamicos; App Admin y reglas por dispositivo; sincronizacion y auditoria; accesibilidad; CAPTCHA; SVG/iconos seguros; rendimiento y memoria.
- Duplicados y relacion: no revive la regla historica `Bloquear imagenes`, eliminada porque ocultaba recursos y empeoraba la experiencia. Es un modo opcional nuevo que conserva el diseño mediante blur. Complementa `DAG-IMAGES-01` y las capas de modestia existentes; no reemplaza los bloqueos de video, descargas o dominios.
- Criterios de aceptacion propuestos:
  - Admin puede elegir exactamente un modo de imagenes para el Usuario o dispositivo autorizado;
  - `Fotos filtradas` mantiene el comportamiento actual y muestra una advertencia breve y honesta de que la clasificacion no garantiza 100 %;
  - `Ultra kosher` desenfoca todas las fotografias antes de entregarlas al WebView y conserva su espacio, proporcion y flujo de pagina;
  - el Usuario no puede quitar el blur, recuperar el raster original desde la pagina ni cambiar el modo localmente;
  - cambiar el modo se sincroniza, queda auditable y se aplica a nuevas cargas sin borrar historial, sesiones ni preferencias ajenas;
  - recursos dudosos, fallidos o tecnicamente inseguros continúan fallando cerrados y nunca se vuelven visibles por elegir un modo;
  - se validan paginas densas, lazy loading, tiendas, noticias, CAPTCHA, logos, iconos, formularios, temas y varias pestanas sin huecos ni mezcla de politicas.
- Decisiones pendientes para la entrevista del ticket: alcance por comunidad o dispositivo; modo predeterminado; tratamiento de logos, iconos, mapas, QR, CAPTCHA y dibujos; intensidad exacta del blur; texto y ubicacion de la advertencia; autoridad Admin o Super Admin; comportamiento offline y migracion de dispositivos actuales.

#### DAG-HISTORY-UX-01 - Historial minimalista

- Estado: `Resuelto` en DEV 234; aprobado por el usuario e implementado progresivamente desde DEV 224. Pendiente prueba fisica. Tipo: UX e historial local. Prioridad: P2.
- Problema: la presentacion actual del historial puede simplificarse para escanear y reabrir entradas con menos ruido visual.
- Solucion propuesta: mostrar una lista minimalista con jerarquia clara y acciones discretas, preservando el almacenamiento local cifrado y el borrado existente.
- Evidencia: checklist visual aportado por el usuario el 2026-07-15.
- Esfuerzo: S. Riesgo: bajo; una simplificacion excesiva puede ocultar fecha, sitio o acciones importantes.
- Dependencias: historial cifrado DAG, navegacion, tema y accesibilidad.
- Duplicados y relacion: mejora visual del historial ya implementado; no propone ampliar datos guardados ni telemetria.
- Criterios de aceptacion propuestos: lista facil de recorrer; apertura correcta en pestana; borrado claro y consistente; estados vacio y error; sin guardar consultas, contenido adicional ni datos fuera del alcance vigente.
- Decisiones pendientes para el ticket: campos visibles, agrupacion por fecha, favicon, acciones por fila y borrado individual o por periodo.

#### DAG-ANALYSIS-UX-01 - Analisis integrado en el buscador

- Estado: `Resuelto` en DEV 226; pendiente prueba fisica. Tipo: UX, feedback visual y marca DAG. Prioridad: P2.
- Problema: el mensaje separado `DAG esta analizando la pagina` ocupa espacio y no integra el estado de seguridad con la identidad del buscador.
- Solucion propuesta: durante el analisis, iluminar el propio buscador con colores neon y una animacion de aspecto inteligente, mostrando dentro de esa zona la leyenda `Analizando...`.
- Evidencia: propuesta visual del usuario el 2026-07-15.
- Esfuerzo: S. Riesgo: bajo; animaciones intensas pueden afectar rendimiento, accesibilidad o distraer.
- Dependencias: estado de analisis local, buscador/barra DAG, temas claro y oscuro, preferencias de movimiento reducido y optimizaciones de rendimiento vigentes.
- Duplicados y relacion: complementa `DAG-HOME-UX-01` y `DAG-THEME-01`; no cambia el clasificador ni permite mostrar contenido antes de finalizar el analisis.
- Criterios de aceptacion propuestos: el buscador comunica claramente `Analizando...`; el contenido sigue oculto hasta una decision segura; la animacion es fluida, acotada y legible en ambos temas; existe una presentacion reducida para accesibilidad; no agrega recomposiciones o trabajo continuo costoso.
- Resultado DEV 226: la direccion previa y el texto central desaparecen durante carga; queda solo `Analizando` con puntos animados dentro del campo y el borde acotado al trabajo activo. No altera la decision ni muestra contenido antes de aprobar.

#### DAG-REVIEW-STAGING-01 - Revision despues del analisis completo

- Estado: `En progreso`; aprobado y ejecutado por pedido del usuario el 2026-07-15, pendiente prueba fisica de DEV 225.
- Tipo: seguridad Web, precision local y UX. Prioridad: P1. Esfuerzo: M. Riesgo: medio.
- Causa: un resultado podia quedar incierto por su fragmento breve y pedir revision antes de inspeccionar la pagina; una unica palabra ambigua en miles de caracteres o una pagina JavaScript aun vacia tambien producia falsos positivos.
- Alcance: preanalisis oculto de resultados inciertos, dos reintentos acotados de extraccion y contexto de pagina para menciones ambiguas aisladas. No cambia listas, bloqueos explicitos, reglas Admin ni analisis visual.
- Aceptacion: una pagina normal no pide revision por un fragmento o hidratacion tardia; el contenido permanece oculto hasta decidir; una pagina realmente incierta sigue ofreciendo revision; bloqueos conocidos mantienen prioridad.

#### DAG-APPROVAL-CACHE-01 - Reutilizacion temporal de aprobaciones

- Estado: `Resuelto` en DEV 226; aprobado por el usuario el 2026-07-15, pendiente prueba fisica. Tipo: seguridad, cache de decisiones y UX. Prioridad: P1.
- Problema: una pagina ya aprobada puede volver a exigir analisis o revision en cada apertura, generando espera y solicitudes repetidas aunque la aprobacion sea reciente.
- Solucion propuesta: conservar una aprobacion con vencimiento y permitir reaperturas directas durante un periodo configurable, por ejemplo una semana o un mes, sujeto a reglas claras de alcance e invalidacion.
- Evidencia: necesidad expresada por el usuario el 2026-07-15.
- Esfuerzo: M. Riesgo: alto; el contenido o propietario de una pagina puede cambiar durante la vigencia y una aprobacion demasiado amplia podria autorizar rutas no revisadas.
- Dependencias: modelo comun `WebPolicyDecision`; aprobaciones Admin; historial cifrado; reglas por dispositivo; version del clasificador y politica; reloj y vencimiento; privacidad y sincronizacion.
- Duplicados y relacion: se relaciona con `WEB-CACHE-01`, que propone cache local acotada de reputacion sin URL ni historial. No se marca como duplicado hasta definir si la aprobacion aplica a dominio, URL exacta o decision administrativa persistida.
- Criterios de aceptacion propuestos: una aprobacion vigente evita revisiones repetidas solo dentro de su alcance exacto; toda entrada tiene origen, momento y vencimiento; cambios de politica, revocacion, version incompatible o indicadores de riesgo invalidan la reutilizacion; al vencer se vuelve a analizar o revisar; no se guarda contenido, HTML ni consultas; el administrador puede conocer y revocar la aprobacion aplicable.
- Decisiones cerradas: siete dias; URL exacta; solo decisiones locales permitidas; almacenamiento cifrado en el dispositivo; huella de URL/titulo/texto/resumen visual; invalidacion por contenido, tiempo/reloj, politica o modelo; comprobacion dura de dominio y reglas en cada carga. Se puede borrar por separado sin borrar historial, sesiones ni pestanas.

#### DAG-APPROVAL-POLICY-01 - Aprobacion sin desactivar DAG

- Estado: `Implementado en DEV 238; pendiente prueba fisica`. La aprobacion conserva el rechazo seguro de politicas incompletas y ahora republica en una sola mutacion todas las reglas locales vigentes, incluida `__dag_enabled__`, junto con la nueva regla de dominio.
- Tipo: regresion funcional y consistencia de politica. Prioridad: P0.
- Problema: cuando App Admin permite un sitio solicitado, DAG se desactiva en App Usuario. La aprobacion de un dominio no debe reemplazar, eliminar ni revocar la regla que habilita DAG para el dispositivo.
- Solucion propuesta: diagnosticar el recorrido real de aprobacion y sincronizacion en la version publicada, garantizar que toda mutacion preserve la politica completa del dispositivo y rechazar de forma segura cualquier aprobacion basada en un estado incompleto, sin informar exito falso.
- Evidencia: reporte fisico del usuario del 2026-07-16. El handoff registra que DEV 227 corrigio exactamente este cierre al aprobar mediante una carga previa de la politica completa; la nueva evidencia invalida el cierre operativo para el recorrido observado, aunque no permite asumir todavia la misma causa raiz.
- Esfuerzo: M estimado. Riesgo: alto; una correccion superficial puede conservar estados parciales, duplicar reglas o desactivar otras protecciones del dispositivo.
- Dependencias: flujo de solicitudes de dominio en App Admin; politica por dispositivo y regla `__dag_enabled__`; pull dirigido, Room, outbox y sincronizacion; aprobaciones existentes; estado publicado DEV vigente.
- Duplicados y relacion: se conserva el ID historico porque el sintoma coincide con `DAG-APPROVAL-POLICY-01`; no se mezcla con `DAG-APPROVAL-CACHE-01`, que solo reutiliza decisiones locales ya aprobadas.
- Criterios de aceptacion propuestos:
  - aprobar un sitio conserva DAG habilitado y permite abrir el sitio dentro del alcance autorizado;
  - rechazar o fallar una aprobacion tampoco cambia el estado de DAG;
  - una politica local o remota incompleta no se publica ni reemplaza la politica vigente;
  - App Admin no muestra exito hasta confirmar una mutacion consistente;
  - no se crean reglas duplicadas ni se alteran otras preferencias Web o protecciones;
  - el ciclo solicitud -> aprobacion -> sincronizacion -> apertura se repite fisicamente varias veces y tras reiniciar ambas apps;
  - una actualizacion in-place conserva DAG y las aprobaciones existentes sin borrar datos.
- Decisiones pendientes para la entrevista del ticket: version instalada en ambas apps; dispositivo afectado; sitio usado; si DAG desaparece, figura cerrado o solo deja de abrir; momento exacto del cambio; estado tras volver a habilitarlo; alcance de logs y datos de prueba autorizados.

#### DAG-REQUEST-STATUS-01 - Confirmacion y pendientes de revision

- Estado: `Implementado parcialmente en DEV 238; ampliacion pendiente, no aprobada todavia para codigo`. Tipo: UX de solicitudes, navegacion DAG y notificaciones. Prioridad: P1.
- Problema: despues de pedir revision de una pagina debe quedar inequívocamente claro que la solicitud fue enviada y no debe poder duplicarse. Cuando Admin aprueba una pagina, DAG necesita comunicar que existe una novedad, identificar visualmente la pagina habilitada y avisar al usuario aunque DAG no este abierto.
- Solucion propuesta: deduplicar solicitudes equivalentes; restringir el boton de pedir aprobacion mientras exista una solicitud activa y reemplazarlo por un estado `Solicitud enviada`; mantener una seccion `Pedidos` en el menu; encender un indicador verde de novedades en DAG cuando cambie un estado; mostrar en verde la pagina aprobada dentro de esa seccion; y enviar un push al dispositivo Usuario cuando Admin la habilite.
- Resultado DEV 238: DAG confirma `Solicitud enviada y pendiente de revision`, observa solo las solicitudes locales de dominio, ofrece la bandeja desde Home y navegacion, distingue pendiente de envio, pendiente remota, aprobada, rechazada o expirada y evita otro envio del mismo dominio mientras exista una equivalente pendiente. Solo una aprobada permite volver a abrir el dominio, que se analiza nuevamente.
- Evidencia: observacion de uso del usuario y ampliacion funcional definidas el 2026-07-16.
- Esfuerzo: L estimado para completar interfaz, estado de novedad y push. Riesgo: medio; la pantalla puede exponer mas historial del necesario, mostrar estados obsoletos, repetir notificaciones o confundir una solicitud enviada con una aprobacion efectiva.
- Dependencias: solicitudes de dominio DAG; sincronizacion y estados pendiente/aprobada/rechazada; menu de tres puntos; navegacion por pestana; privacidad del historial local; `DAG-APPROVAL-POLICY-01`; token y permiso FCM de App Usuario; entrega deduplicada y apertura profunda hacia `Pedidos`.
- Duplicados y relacion: especializa `REQUESTS-UX-01` para la experiencia dentro de DAG y se relaciona con `POLICY-EXPLAIN-01`; se mantiene separado de la bandeja de solicitudes de App Admin porque aqui el destinatario visual es el usuario que envio la pagina.
- Criterios de aceptacion propuestos:
  - al enviar una solicitud, DAG confirma claramente que fue enviada y que esta pendiente;
  - el boton queda restringido y muestra el estado enviado mientras exista una solicitud equivalente activa;
  - toques repetidos, reintentos, reinicios y sincronizaciones no generan pedidos duplicados;
  - el menu de tres puntos ofrece `Pedidos` aun desde Home;
  - la lista distingue al menos pendiente, aprobada y rechazada sin prometer aprobacion antes de sincronizarla;
  - cuando llega una novedad, DAG muestra un indicador verde visible pero no invasivo hasta que el usuario abre o reconoce `Pedidos`;
  - una pagina aprobada se identifica en verde dentro de `Pedidos`, sin aplicar ese color a pendientes o rechazos;
  - la aprobacion genera un unico push al dispositivo correcto, con texto claro y apertura directa de `Pedidos`;
  - si el permiso de notificaciones esta rechazado o el push falla, la novedad y el estado aprobado siguen disponibles al abrir DAG;
  - tocar una entrada permite volver al contexto seguro correspondiente sin mostrar contenido no aprobado;
  - los cambios de estado se actualizan de forma consistente y sobreviven al reinicio previsto;
  - no se expone la consulta completa, contenido de pagina ni historial adicional al administrador o a servicios remotos.
- Decisiones pendientes para la entrevista del ticket: forma y ubicacion de la luz verde; momento de marcar la novedad como vista; texto exacto del push; si tambien se notifica rechazo; datos visibles por fila; retencion; accion al tocar una aprobada o rechazada; agrupacion por dominio o URL; comportamiento offline y entre pestanas.

#### DAG-SHARE-01 - Compartir enlace actual

- Estado: `Implementado en DEV 238; pendiente prueba fisica`. Tipo: UX de navegacion e interoperabilidad Android. Prioridad: P2.
- Problema: DAG no ofrece una accion clara para compartir el enlace de la pagina actual con otra persona o aplicacion.
- Solucion propuesta: agregar `Compartir enlace` al menu de tres puntos y abrir el selector nativo de Android con la URL segura de la pagina visible. La accion comparte el enlace, no el HTML, capturas, consultas, historial ni decisiones internas del filtro.
- Resultado DEV 238: la accion aparece solo para una pagina HTTPS visible y comparte unicamente la URL valida de la pestana activa mediante el selector nativo. Home, resultados, carga, bloqueos y revisiones no habilitan la accion.
- Evidencia: solicitud del usuario del 2026-07-16.
- Esfuerzo: S estimado. Riesgo: medio; compartir puede sacar una URL del entorno protegido, exponer parametros sensibles o habilitar destinos externos no controlados por DAG.
- Dependencias: menu DAG; URL efectiva y navegacion por pestana; intents Android; normalizacion y saneamiento de URL; reglas para paginas internas, bloqueadas, inciertas o pendientes.
- Duplicados y relacion: no existe un ticket previo equivalente. Complementa `DAG-NAV-UX-01`, sin modificar el analisis ni la politica de apertura dentro de DAG.
- Criterios de aceptacion propuestos:
  - una pagina HTTPS permitida ofrece `Compartir enlace` desde el menu;
  - se comparte solamente la URL normalizada de la pestana activa y, si se acuerda, un titulo no sensible;
  - Home, resultados internos, estados de analisis, bloqueos y solicitudes pendientes no comparten URLs internas o incompletas;
  - cancelar el selector no cambia la pagina ni el estado de DAG;
  - compartir no marca el destino como aprobado ni evita que vuelva a analizarse al abrirlo;
  - no se registran ni sincronizan la aplicacion elegida, el destinatario o el contenido compartido.
- Decisiones pendientes para la entrevista del ticket: ubicacion exacta; compartir solo URL o URL y titulo; tratamiento de parametros y sesiones; paginas aprobadas temporalmente; aviso de que el receptor puede abrir fuera de DAG; disponibilidad desde resultados e historial.

#### DAG-AUTOCOMPLETE-02 - Buscar al tocar una sugerencia

- Estado: `Implementado en DEV 238; pendiente prueba fisica`. Tipo: UX de busqueda. Prioridad: P2.
- Problema: al tocar una sugerencia de DAG, el texto puede quedar solamente cargado en el buscador y exigir una segunda accion, en vez de iniciar la busqueda esperada.
- Solucion propuesta: tratar el toque sobre una sugerencia como confirmacion: completar el campo, cerrar el teclado y ejecutar inmediatamente el mismo flujo seguro de busqueda que usa el boton o la tecla Buscar.
- Resultado DEV 238: el toque reserva atomicamente una sola busqueda, cierra sugerencias/teclado y vuelve a ejecutar clasificacion local antes de consultar Brave; una segunda pulsacion mientras carga no duplica consumo.
- Evidencia: solicitud del usuario del 2026-07-16.
- Esfuerzo: S estimado. Riesgo: bajo; una ejecucion duplicada puede consumir dos consultas Brave o buscar una sugerencia distinta de la mostrada si el estado cambia entre pulsacion y envio.
- Dependencias: `DAG-AUTOCOMPLETE-01`; buscador combinado; clasificacion local; debounce; contador Brave; teclado y estado por pestana.
- Duplicados y relacion: seguimiento de `DAG-AUTOCOMPLETE-01`, resuelto en DEV 228 para generar y mostrar sugerencias. No reabre aquel alcance porque esta entrada define la accion posterior al toque.
- Criterios de aceptacion propuestos:
  - tocar una sugerencia ejecuta inmediatamente una unica busqueda con el texto exacto mostrado;
  - el teclado y la lista de sugerencias se cierran al comenzar;
  - la consulta vuelve a pasar por todas las reglas y clasificadores locales;
  - una sugerencia bloqueada o que cambie a incierta no evita la politica de seguridad;
  - cada toque aceptado consume como maximo una consulta Brave;
  - pulsaciones rapidas, recomposiciones y cambios de pestana no duplican ni cruzan busquedas.
- Decisiones pendientes para la entrevista del ticket: si todas las sugerencias buscan directamente o si las del historial pueden abrir su URL; comportamiento con pulsacion larga; feedback durante la transicion; accesibilidad y accion del teclado.

#### DAG-CAPTCHA-01 - Compatibilidad segura y general con CAPTCHA

- Estado: `Publicado en DEV 271; iframe habilitado pero flujo real aun falla`. Tipo: bug de compatibilidad Web y seguridad. Prioridad: P1.
- Problema: DAG no muestra el CAPTCHA de la pagina de multas de CABA, por lo que el usuario no puede completar el tramite aunque la pagina principal sea accesible.
- Solucion propuesta: incorporar un tratamiento acotado para desafios CAPTCHA utilizados por paginas permitidas, cargando solamente los recursos, scripts, iframes y comunicaciones indispensables del proveedor validado. El desafio y su resultado deben permanecer dentro de la navegacion protegida y no convertirse en una excepcion general para contenido externo.
- Resultado DEV 238: la primera excepcion quedo limitada a Google reCAPTCHA y a una URL exacta de CABA. La ampliacion aprobada permite iframes HTTPS con rutas cerradas de Google reCAPTCHA, hCaptcha y Cloudflare Turnstile dentro de cualquier pagina principal que DAG ya haya permitido; todos los iframes ordinarios siguen eliminados y las imagenes del desafio conservan la intercepcion/clasificacion local. MiBA es el caso de referencia aportado por el usuario.
- Evidencia: prueba fisica informada por el usuario el 2026-07-16; la inspeccion posterior de la URL oficial confirmo un iframe Google reCAPTCHA que DAG eliminaba por su bloqueo general de iframes.
- Esfuerzo: M estimado. Riesgo: alto; los CAPTCHAs suelen depender de scripts, iframes, cookies y dominios externos, y una habilitacion demasiado amplia podria crear una ruta de contenido no analizado, rastreo adicional o navegacion externa.
- Dependencias: intercepcion de recursos WebView; politica de iframes, JavaScript, cookies y dominios secundarios; clasificacion de imagenes; navegacion y sesiones; listas dinamicas; proveedores reCAPTCHA, hCaptcha, Turnstile u otros a identificar.
- Duplicados y relacion: no existe una entrada previa equivalente. Se relaciona con compatibilidad de recursos e imagenes DAG, pero se mantiene separado porque un CAPTCHA es un flujo interactivo externo y sensible, no una imagen comun.
- Criterios de aceptacion propuestos:
  - el CAPTCHA del tramite de multas de CABA se muestra, permite interacción y valida correctamente dentro de DAG;
  - solo se habilitan los proveedores, dominios y tipos de recursos estrictamente necesarios;
  - el contenido del desafio no queda visible antes de aplicar las barreras compatibles y los errores fallan cerrados con un mensaje entendible;
  - resolver el CAPTCHA no abre ventanas, aplicaciones externas, descargas ni navegaciones no autorizadas;
  - cookies o tokens del desafio tienen alcance y persistencia minimos y no se incorporan al historial ni a solicitudes Admin;
  - sitios bloqueados o inciertos no usan el soporte CAPTCHA para evitar su decision;
  - se prueban desafio correcto, vencido, recarga, error de red, modo claro/oscuro y regreso desde segundo plano.
- Validacion pendiente: comprobar en el telefono MiBA y el tramite de infracciones, incluyendo checkbox, desafio visual cuando aparezca, vencimiento, recarga y regreso desde segundo plano. Proveedores nuevos requieren ampliar explicitamente la lista cerrada; ningun sitio hereda permiso para iframes ordinarios.

#### DAG-CAPTCHA-02 - Sesion CAPTCHA temporal y aislada

- Estado: `Propuesto`; diagnostico preparado el 2026-07-22, pendiente aprobacion explicita para codigo. Tipo: compatibilidad Web, privacidad y seguridad. Prioridad: P1.
- Evidencia: el usuario confirma que el CAPTCHA de MiBA sigue sin funcionar con DEV 271. La ampliacion anterior solo evita eliminar el iframe inicial de reCAPTCHA, hCaptcha o Turnstile; no garantiza el protocolo completo del desafio.
- Diagnostico: WebView rechaza todas las cookies de terceros, elimina todo canvas, bloquea `blob:`, registro de Service Workers, ventanas auxiliares y navegaciones externas. Un proveedor puede necesitar parte de esas capacidades, rutas o hosts durante la sesion y fallar aunque su iframe sea visible. Habilitarlas globalmente seria una regresion de privacidad y una ruta de contenido no analizado.
- Alcance propuesto: detectar un desafio cerrado de proveedor conocido dentro de una pagina HTTPS ya permitida; crear una sesion efimera por pagina/proveedor; permitir solo las capacidades minimas demostradas por el flujo real; mantener la imagen del desafio dentro de la politica acordada; aceptar el token de respuesta en la pagina principal; y limpiar cookies/estado temporal al completar, vencer, navegar o cerrar la pestaña.
- No alcance: iframes generales, video/audio, ventanas arbitrarias, descargas, permisos de camara/microfono/ubicacion, persistencia de seguimiento o excepciones abiertas por dominio principal.
- Esfuerzo: L estimado. Riesgo: alto; una lista incompleta rompe el tramite y una lista amplia permite rastreo o contenido externo fuera de control.
- Dependencias: `DAG-CAPTCHA-01`, CookieManager/WebView, saneamiento DOM, hosts/rutas por proveedor, ciclo de vida de pestaña y pruebas instrumentadas. MiBA y el tramite de infracciones son referencias, no allowlists de producto.
- Criterios de aceptacion:
  - MiBA y el tramite oficial de infracciones completan checkbox, desafio cuando aparezca y envio del formulario dentro de DAG;
  - desafio correcto, incorrecto, vencido, recargado y retomado desde segundo plano tienen estados claros;
  - solo el proveedor detectado recibe capacidades temporales y todos los iframes ordinarios siguen bloqueados;
  - terminar, vencer, cambiar de sitio o cerrar pestaña elimina el estado temporal sin afectar otras paginas;
  - un sitio bloqueado o incierto no usa CAPTCHA para abrir contenido ni navegacion externa;
  - pruebas registran solo proveedor, etapa y codigo de fallo; nunca token, respuesta, imagen, URL completa ni datos del formulario.

#### DAG-TABS-UX-02 - Selector reciente, limpio y sin vacias duplicadas

- Estado: `Implementado candidato DEV 241; pendiente prueba fisica`. Aprobado al ordenar ejecutar todos los tickets el 2026-07-16. Tipo: UX, identidad visual y gestion de pestanas. Prioridad: P2.
- Problema: el selector de pestanas conserva ilustraciones de peces aunque DAG ya elimino esa mascota de las demas superficies; tampoco presenta claramente las pestanas recientes y permite crear nuevas pestanas cuando ya existe una vacia, acumulando estados redundantes.
- Solucion propuesta: eliminar todos los peces del selector y reemplazar estados sin miniatura por una representacion neutra de DAG; incorporar una vista o agrupacion de pestanas recientes; y hacer que `Nueva pestana` enfoque una pestana vacia existente en vez de crear otra.
- Evidencia: observacion y propuesta del usuario del 2026-07-16. El handoff confirma que Home elimino la mascota en DEV 225, mientras que el selector historico usa la mascota para paginas sin vista previa.
- Esfuerzo: M estimado. Riesgo: medio; ordenar o reutilizar pestanas puede cambiar la pestana activa, perder contexto esperado o mezclar estados entre Home, resultados y paginas suspendidas.
- Dependencias: `DAG-TABS-UX-01` y `DAG-TABS-02`; selector de pestanas; miniaturas efimeras; persistencia cifrada; limite de ocho; estado Home/resultados/Web; accion visible de nueva pestana.
- Duplicados y relacion: seguimiento de los tickets de pestanas ya resueltos; no reabre sus cierres porque agrega reglas y presentacion nuevas. La eliminacion de peces se limita a DAG y no afecta la identidad visual de App Usuario o App Admin.
- Criterios de aceptacion propuestos:
  - ninguna tarjeta, estado vacio, carga o miniatura del selector DAG muestra peces;
  - una pestana sin vista previa usa una superficie neutra, clara y accesible;
  - el usuario puede identificar y volver rapidamente a sus pestanas recientes;
  - `Nueva pestana` enfoca una pestana vacia existente y no crea otra mientras esa vacia siga disponible;
  - si existen varias vacias heredadas, el comportamiento es determinista y permite cerrarlas sin afectar paginas abiertas;
  - el limite de ocho, el aislamiento de historial y la restauracion cifrada continuan funcionando;
  - cambiar el orden o abrir una reciente no recarga resultados ni consume una consulta Brave innecesaria.
- Implementacion: el selector se titula `Pestañas recientes`, ordena las pestañas abiertas por ultimo uso y persiste esa marca junto al estado cifrado. No conserva ni reabre pestañas cerradas. Las tarjetas sin miniatura muestran una superficie neutra con `DAG`, sin peces.
- Reutilizacion: se considera vacia solamente una pestaña en Home sin texto, consulta, resultados ni URL. `Nueva` enfoca la vacia usada mas recientemente —incluida la activa— antes de crear otra; las vacias heredadas restantes se pueden cerrar normalmente. El limite continua en ocho.
- Compatibilidad: sesiones cifradas anteriores sin fecha se restauran con un orden determinista; resultados y paginas no se recargan al abrir el selector ni se ejecuta una consulta Brave.
- Validacion: codec de sesiones y compatibilidad de marca de uso, definicion estricta de vacia, tests DEV, compilacion y `ktlintCheck` de App Usuario correctos. Falta prueba visual y de restauracion fisica en Samsung SM-S908E; no hubo publicacion intermedia.

#### DAG-BACK-NAV-01 - Atras respeta pagina, resultados y Home

- Estado: `Resuelto` en DEV 233; aprobado por el usuario al pedir continuar con los pendientes DAG el 2026-07-16. Pendiente prueba fisica.
- Tipo: bug/UX de navegacion DAG.
- Prioridad: P2.
- Problema: al usar Atras desde una pagina, DAG puede volver directamente a Home y omitir el listado de resultados desde el que se abrio.
- Solucion propuesta: respetar la pila de navegacion visible. Desde una pagina, Atras vuelve una pagina; cuando el origen fue una busqueda, debe volver primero al listado de resultados y solo el siguiente Atras debe regresar a Home.
- Evidencia: comportamiento esperado definido por el usuario el 2026-07-15.
- Esfuerzo: S.
- Riesgo: medio; WebView, resultados Compose, multiples pestanas y cierre del teclado mantienen historiales distintos que pueden desincronizarse.
- Dependencias: historial WebView por pestana; estado de resultados; Home DAG; boton fisico y accion del menu; teclado; `DAG-NAV-UX-01` y `DAG-TABS-UX-01`.
- Duplicados y relacion: precisa la semantica de Atras pendiente en `DAG-NAV-UX-01`; se conserva como entrada separada para no perder el fallo observable.
- Criterios de aceptacion propuestos:
  - pagina abierta desde resultados -> Atras muestra esos mismos resultados -> Atras muestra Home;
  - varias paginas navegadas retroceden en orden antes de volver a resultados;
  - cada pestana conserva su propia pila y no muestra resultados de otra;
  - cerrar el teclado tiene prioridad cuando corresponde y no consume un paso de historial;
  - recarga, aprobacion, bloqueo o recuperacion del renderer no crean saltos ni bucles;
  - Home sin historial aplica el comportamiento de salida definido para DAG.
- Decisiones cerradas: URL directa vuelve a Home; pagina desde resultados vuelve primero a esos resultados; historial WebView tiene prioridad; cada pestaña conserva su propio origen; el teclado se cierra antes; la restauracion conserva resultados pero revalida paginas.

#### DAG-RESULTS-DIAG-01 - Diagnostico agregado del embudo de resultados

- Estado: `Resuelto` en DEV 237; aprobado explicitamente por el usuario el 2026-07-16.
- Tipo: diagnostico local, privacidad y calidad de busqueda. Prioridad: P1. Esfuerzo: S. Riesgo: bajo.
- Causa: Brave entregaba como maximo 10 resultados y DAG ocultaba los bloqueados, pero no existia evidencia para separar resultados ausentes en origen, rechazados por contrato o descartados por cada capa local.
- Resultado: la Edge Function devuelve solamente cantidades agregadas de recibidos y rechazados; Android asigna cada candidato a lista de dominios, regla Admin, plataforma prohibida, clasificador local, incierto mostrado o permitido mostrado. Logcat contiene exclusivamente esos conteos.
- Privacidad y costo: no se guardan ni registran consultas, URLs, dominios, titulos o descripciones. No cambia el cupo, los umbrales, la UI ni el consumo de una busqueda.
- Aceptacion: una prueba controlada contabiliza exactamente 10 de 10 resultados una sola vez; tests DEV Usuario/Admin, ktlint y builds de ambas apps correctos. La paginacion quedo luego implementada por separado en `DAG-RESULTS-PAGE-01` porque cada pagina adicional consume otra consulta Brave.

#### DAG-RESULTS-PAGE-01 - Una pagina adicional con costo explicito

- Estado: `Implementado en DEV 238; pendiente prueba fisica`. Aprobado por el usuario el 2026-07-16 al pedir completar los pendientes DAG.
- Causa: la busqueda inicial solicitaba diez resultados y no existia paginacion, aun cuando Brave podia informar otra pagina disponible.
- Resultado: la Edge Function acepta solo pagina 0 o 1, usa el `offset` por pagina documentado por Brave y devuelve `has_more_results`. Android ofrece `Mas resultados · consume 1 busqueda` unicamente cuando Brave confirma disponibilidad, concatena y deduplica por URL y conserva como maximo una pagina adicional.
- Costo y limites: la primera pagina consume una consulta Brave y tocar `Mas resultados` consume otra. El tope funcional es de veinte candidatos brutos por busqueda; no hay paginacion automatica ni tercera pagina.
- Persistencia y privacidad: la consulta y la pagina activa sobreviven en la sesion cifrada de pestanas existente. No se agregan consultas, URLs, titulos ni contenido a diagnosticos remotos.
- Validacion tecnica: tests de paginacion y acciones DAG, tests DEV Usuario/Admin, ktlint global, Android Lint, detekt y builds de ambas apps correctos. `dag-search` version 8 activa solo en Supabase DEV con verificacion JWT.

#### DAG-UPDATE-01 - Actualizacion compartida con App Usuario

- Estado: `Decision cerrada en DEV 238`; no requirio un mecanismo de instalacion nuevo.
- Decision: DAG forma parte del mismo APK y paquete de App Usuario. La app puede comprobar el manifiesto DEV y descargar una actualizacion, pero en Android normal el instalador del sistema exige confirmacion de la persona para instalarla.
- Limite explicito: no se promete actualizacion silenciosa. Una instalacion sin confirmacion requiere un dispositivo aprovisionado como Device Owner/Android Enterprise, acceso root o una tienda administrada con politica compatible.
- Persistencia: la actualizacion debe ser in-place y conservar activacion, politica, historial cifrado, estado de protecciones y launcher DAG; no se desinstala ni se borran datos para actualizar.

### USER-GREETING-01 - Saludo personalizado en App Usuario

- Estado: `Implementado candidato DEV 241; pendiente prueba fisica`. Aprobado al ordenar ejecutar todos los tickets el 2026-07-16. Tipo: UX y personalizacion de App Usuario. Prioridad: P2.
- Problema: el encabezado superior muestra un saludo generico y no identifica al usuario protegido que esta usando la aplicacion.
- Solucion propuesta: reemplazar `Hola` por `Hola, {nombre}`, usando el nombre del usuario definido por el administrador.
- Evidencia: solicitud del usuario el 2026-07-15.
- Esfuerzo: S. Riesgo: bajo; deben contemplarse nombre ausente, sincronizacion tardia, longitud, caracteres especiales y privacidad en pantalla.
- Dependencias: nombre del usuario protegido disponible en App Usuario; sincronizacion y estado local; encabezado de Home; accesibilidad y tamanos de fuente.
- Duplicados y relacion: puede agruparse visualmente con `UI-POLISH-01`, pero se conserva como entrada separada por depender del dato remoto definido por Admin.
- Implementacion: el Home observa activacion y dispositivos locales sincronizados, cruza exclusivamente por el `deviceId` activo y muestra `Hola, {nombre}`. Si la activacion, el dispositivo o el nombre todavia no estan disponibles muestra `Hola`; nunca toma el primer nombre de otra fila como reemplazo.
- Criterios de aceptacion cubiertos: el encabezado muestra el nombre correcto del usuario activo; los cambios se reflejan cuando Room recibe la sincronizacion; existe fallback simple; el encabezado admite salto de linea para nombres largos; no se muestra el nombre de otro usuario o dispositivo.
- Decision aplicada: no se copia el nombre a notificaciones ni pantalla bloqueada y no se agrega otra persistencia. Se conserva completo, con espacios externos normalizados, para no alterar nombres ni caracteres especiales.
- Validacion: tests del cruce seguro, fallback y nombre normalizado; compilacion y `ktlintCheck` de App Usuario correctos. Falta prueba visual en Samsung SM-S908E y no hubo publicacion intermedia.

### PROTECTION-ONBOARDING-HEALTH-01 - Instalacion, salud y reparacion de proteccion

- Estado: `Idea`; no aprobado para diagnostico tecnico ni codigo. Tipo: onboarding, seguridad, observabilidad y UX multiplataforma. Prioridad: P1.
- Problema: la activacion y la recuperacion de App Usuario estan repartidas entre permisos y pantallas distintas. Aunque ya existen comprobaciones y acciones individuales, el administrador necesita una lectura coherente de VPN, Accessibility, Device Admin, sincronizacion, licencia y barrera, con una reparacion clara para cada degradacion.
- Solucion propuesta: unificar las ideas seleccionadas por el usuario como (1) asistente de instalacion y proteccion, (2) estado de salud por dispositivo y (15) diagnostico y reparacion guiada. El alta terminaria con una verificacion real de cada capa; App Admin y Superweb mostrarian un estado agregado `Protegido`, `Incompleto` o `En riesgo`; cada fallo abriria una reparacion especifica y volveria a comprobar el resultado sin declarar exito por el solo hecho de abrir Ajustes.
- Evidencia: seleccion explicita del usuario el 2026-07-19 luego de comparar Content Filter con FamilyTime. El handoff confirma que ya existen watchdog, heartbeat, acciones individuales para recuperar VPN/Accessibility/Device Admin, barrera predeterminada y alertas de componentes caidos.
- Esfuerzo: L estimado. Riesgo: alto; una salud agregada incorrecta puede declarar protegido un dispositivo degradado, bloquear el onboarding, generar reparaciones circulares o exponer recorridos de Ajustes sin mantenimiento autorizado.
- Dependencias: `BARRIER-DEFAULT-ON-01`, `BARRIER-A11Y-RACE-01`, `USER-RESILIENCE-01`, watchdog y heartbeat existentes, autorizacion temporal de Ajustes, estado de licencia y vinculo, sincronizacion App Usuario -> App Admin/Superweb, comportamiento offline y diferencias OEM.
- Duplicados y relacion: agrupa deliberadamente las ideas 1, 2 y 15 de la seleccion; no duplica las reparaciones tecnicas ni el watchdog ya implementados. Los reutiliza como fuente de verdad y extiende su presentacion, navegacion y verificacion. No sustituye los tickets de bypass ni promete garantias de Device Owner/MDM.
- Criterios de aceptacion propuestos:
  - un alta nueva enumera todas las capas requeridas, explica para que sirve cada una y verifica su estado real antes de declarar la configuracion completa;
  - Usuario, Admin y Superweb derivan el mismo estado agregado a partir de componentes y datos con antiguedad visible, sin contradicciones silenciosas;
  - cada componente degradado ofrece una reparacion especifica, autorizada y reversible, seguida de una nueva comprobacion;
  - reinicios, actualizaciones, perdida temporal de red y permisos incompletos no producen falsos estados sanos;
  - la barrera no abre ni deja accesibles Ajustes protegidos fuera del recorrido administrativo permitido;
  - la interfaz distingue claramente proteccion incompleta, componente efectivamente caido y dispositivo simplemente sin comunicacion;
  - no se recopilan consultas, URLs, mensajes ni contenido para calcular la salud.
- Decisiones pendientes para la entrevista del ticket: componentes obligatorios por modelo de telefono; autoridad y visibilidad Admin/Super Admin; textos y colores; antiguedad maxima del heartbeat; comportamiento durante onboarding offline; orden de reparacion; ventanas de mantenimiento; recordatorios, escalamiento y criterio exacto para los tres estados agregados.

### ADMIN-DEVICE-OFFLINE-100H-01 - Aviso Admin despues de 100 horas

- Estado: `Publicado DEV 270; pendiente comprobacion fisica`; aprobado explicitamente por el usuario el 2026-07-21. Tipo: correccion operativa y UX de App Admin. Prioridad: P1. Esfuerzo: S. Riesgo: medio.
- Causa raiz: Home usaba 15 minutos para enviar un Usuario sano a `Pendientes de verificacion`, mientras la ficha usaba 24 horas para declararlo sin comunicacion. Android puede diferir WorkManager con la pantalla apagada aunque exista red, de modo que ambos criterios confundian demora de heartbeat con una degradacion confirmada.
- Solucion publicada: compartir un plazo de 100 horas en Home y ficha. Hasta entonces se conserva el ultimo estado sano; al vencer se informa falta de comunicacion sin afirmar que la proteccion fue desactivada.
- Limites de seguridad: `Disabled` alerta inmediatamente; `Unknown` o un dispositivo nunca verificado sigue pendiente inmediatamente; `possible_uninstall` conserva su regla especial de Device Admin desactivado y mas de 30 minutos sin comunicacion.
- Aceptacion automatizada: unitarios completos, ktlint y build DEV de App Admin correctos; pruebas antes y despues de 100 horas en Dashboard y Rules y regresion de posible desinstalacion cubiertas. Publicado en DEV 270; resta validar el comportamiento real durante reposo prolongado.

### DEV-OFFLINE-ALERT-100H-01 - Umbral remoto de 100 horas

- Estado: `Aplicado y verificado exclusivamente en Supabase DEV`; aprobado explicitamente por el usuario el 2026-07-21. Tipo: backend de alertas operativas. Prioridad: P1. Esfuerzo: S. Riesgo: medio.
- Causa raiz: el generador remoto conservaba 24 horas, un plazo demasiado corto frente a retrasos posibles del heartbeat durante reposo Android y distinto del nuevo criterio solicitado para App Admin.
- Resultado DEV: migracion `20260721194835_device_offline_alert_100_hours.sql`; futuros eventos ordinarios `device_offline` requieren 100 horas y el cron horario, la deduplicacion por episodio y los permisos restringidos permanecen iguales.
- Seguridad y datos: no se llamo al generador para fabricar alertas, no se borraron eventos historicos y `possible_uninstall` conserva 30 minutos con Device Admin desactivado. Asesores DEV de seguridad y rendimiento sin errores; Production no fue tocado.
- Cierre: migracion integrada en `main` y complemento Android publicado en DEV 270. El margen efectivo remoto es de 100 a 101 horas por la cadencia horaria del cron; Production no fue tocado.

### DEVICE-CONNECTIVITY-ALERTS-01 - Dispositivo sin comunicacion

- Estado: `Backend DEV y Android publicados DEV 270; pendiente prueba fisica integral`; aprobado explicitamente el 2026-07-20 y ajustado a 100 horas por decision del 2026-07-21. Bateria queda fuera por decision expresa del usuario. Tipo: alertas operativas, seguridad y continuidad. Prioridad: P1.
- Problema: un telefono apagado, sin red o con el proceso detenido puede dejar de reportar sin que el administrador sepa que perdio comunicacion.
- Solucion implementada: despues de 100 horas sin heartbeat, crear como maximo una alerta por episodio para las bandejas existentes de App Admin y Superweb. Muestra el ultimo contacto y nunca afirma que la proteccion fue desactivada.
- Evidencia: idea 10 seleccionada explicitamente por el usuario el 2026-07-19 luego de revisar las mejores funciones no invasivas de FamilyTime. Content Filter ya transmite heartbeat y alertas de componentes; se propone aprovechar esa evidencia sin agregar vigilancia de contenido.
- Esfuerzo: M estimado. Riesgo: medio; umbrales agresivos, Doze, falta de red, telefono apagado o retrasos de sincronizacion pueden producir ruido y falsas alarmas.
- Dependencias: heartbeat y watchdog; bandejas existentes; `ALERT-ROUTING-01`; deduplicacion y estado de dispositivo; zona horaria, Doze y funcionamiento offline.
- Duplicados y relacion: complementa `SUPERADMIN-ALERTS-01`, `ADMIN-ALERTS-UX-01` y `ALERT-ROUTING-01`; no redefine sus destinatarios ni mezcla una desconexion con un intento bloqueado. Se relaciona con `PROTECTION-ONBOARDING-HEALTH-01`, que consumiria el mismo ultimo estado para el semaforo de salud.
- Criterios de aceptacion propuestos:
  - la falta de heartbeat solo se alerta despues del periodo acordado y muestra la hora exacta del ultimo contacto;
  - volver a comunicarse resuelve el estado sin duplicar notificaciones;
  - la interfaz nunca presenta `proteccion desactivada` cuando la unica evidencia es falta de comunicacion;
  - destinatarios, severidad y canales respetan las reglas de ruteo vigentes;
  - no se transmiten ubicacion, mensajes, historial ni contenido de navegacion.
- Decisiones aplicadas: 100 horas, destinatarios App Admin y Superweb, sin bateria y sin notificacion push nueva en este corte. Al volver a comunicarse, un episodio futuro puede generar una nueva alerta sin duplicar el anterior.

### SUPERADMIN-ALERTS-01 - Alertas de manipulacion en Super Admin

- Estado: `Implementado candidato DEV 241; pendiente prueba funcional`. Aprobado por el usuario al ordenar ejecutar todos los tickets el 2026-07-16.
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

### SUPERADMIN-MSG-01 - Avisos por comunidad

- Estado: `Bandejas y creacion resueltas en DEV 241; pendiente push FCM con sesion Superweb`. Aprobado al ordenar ejecutar todos los tickets el 2026-07-16.
- Tipo: comunicacion operativa y UX. Prioridad: P2.
- Alcance cerrado: Super Admin crea un aviso para una comunidad y elige Administradores, Usuarios o ambos. Es un canal unidireccional y no hay chat ni respuestas. `ANNOUNCEMENTS-INBOX-UX-02` agrega estado de lectura y ocultado exclusivamente por dispositivo, sin exponerlo como analitica global de Superweb.
- Superweb incorpora formulario, vencimiento opcional e historial. El aviso queda guardado aunque FCM no este disponible y la UI distingue ese caso.
- App Usuario y App Admin incorporan bandeja; el candidato `ANNOUNCEMENTS-INBOX-UX-02` reemplaza el refresco manual por carga al abrir. Un push normal abre esa bandeja; no usa el canal urgente reservado para incidentes de proteccion.
- Seguridad: las apps leen solo avisos de su comunidad y rol mediante token de dispositivo. El registro FCM generico acepta solo el token del propio dispositivo. La Service Role y credenciales FCM permanecen en `send-announcement`, nunca en Android.
- Backend DEV: migraciones `20260716162000_super_admin_announcements.sql` y `20260716162500_device_push_registration_all_roles.sql`; Edge Function `send-announcement` desplegada solo en `syeycayasyufedwoprea`.
- Compatibilidad: Android 10 o posterior, igual que las apps existentes. Android 13 o posterior pide permiso de notificaciones; si se rechaza, la bandeja sigue funcionando al abrir la app.
- Correccion posterior: la prueba autenticada descubrio que las RPC referenciaban el helper inexistente `is_current_user_super_admin`. La migracion DEV `20260716181500_fix_super_admin_announcement_authorization.sql` agrega un alias endurecido sobre `is_super_admin`; `anon` no puede ejecutarlo y `authenticated` sigue sujeto a la comprobacion real de Super Admin.
- Validacion fisica: se creo por RPC un aviso `Prueba DEV 241` para ambos roles con vencimiento automatico de 30 minutos. Admin y Usuario lo recibieron al actualizar y la accion de apertura directa mostro la bandeja correcta. No se borro el aviso; queda como historial vencido.
- Pendiente: el navegador integrado no logro navegar fuera de una pestaña vacia, por lo que no se extrajeron sesiones ni credenciales. Falta publicar la fuente Superweb cuando el hosting externo vuelva a aceptar despliegues, invocar `send-announcement` desde una sesion firmada y confirmar la notificacion FCM normal; las bandejas Android DEV 241 ya estan publicadas y comprobadas.

### ALERT-ROUTING-01 - Enrutamiento por intento o desactivacion efectiva

- Estado: `Implementado backend DEV y candidato Android DEV 241; pendiente prueba fisica`. Aprobado por el usuario al ordenar ejecutar todos los tickets el 2026-07-16.
- Tipo: seguridad, alertas y politica de destinatarios.
- Prioridad: P1.
- Problema: intentos bloqueados y desactivaciones efectivas tienen distinta gravedad y no deberian necesariamente notificarse a los mismos destinatarios.
- Solucion propuesta: mostrar los intentos bloqueados unicamente en Super Admin; cuando una app o componente de proteccion se desactive realmente, avisar tanto a Super Admin como a App Admin.
- Evidencia: decision de producto expresada por el usuario el 2026-07-15.
- Esfuerzo: M.
- Riesgo: alto; ocultar intentos a Admin cambia el comportamiento urgente ya implementado y una definicion imprecisa de desactivacion puede omitir incidentes reales o generar falsas alarmas.
- Dependencias: eventos de proteccion, FCM, bandejas `SUPERADMIN-ALERTS-01` y `ADMIN-ALERTS-UX-01`, watchdog, estado real de Accessibility/VPN/Device Admin y permisos por comunidad.
- Duplicados y relacion: no duplica las bandejas visuales; define su politica de destinatarios. Entra en tension con `PUSH-REAL-01`, que actualmente entrega intentos de manipulacion a App Admin, y con la formulacion inicial de `ADMIN-ALERTS-UX-01`; no se reescriben ni archivan esos registros hasta preparar el ticket.
- Criterios de aceptacion propuestos:
  - un intento bloqueado queda visible para Super Admin y no genera aviso de intento en App Admin;
  - una desactivacion efectiva genera un unico evento atribuible al dispositivo correcto y llega a Super Admin y Admin;
  - reintentos, watchdog y FCM no duplican el incidente;
  - ningun aviso contiene historial de navegacion, consultas ni datos sensibles;
  - el estado distingue intento impedido, componente degradado y desactivacion confirmada.
- Decisiones pendientes para el ticket: significado exacto de `app desactivada`; componentes y apps incluidos; canal e inmediatez; responsables por comunidad; retencion, lectura, agrupacion y escalamiento si no se repara.

### APP-INSTALL-APPROVAL-01 - Aprobacion previa para instalar apps

- Estado: `Publicado DEV 254; correccion definitiva del flujo confiable validada tecnica y auxiliarmente, pendiente prueba final en SM-S908E`. Aprobado por el usuario al ordenar todos los tickets y definido el 2026-07-16 para Android normal compatible con la mayoria de equipos, sin Android Enterprise ni cambios de cuenta Google.
- Tipo: control de aplicaciones, seguridad y solicitudes.
- Prioridad: P1.
- Problema: se quiere conservar Play Store disponible para explorar aplicaciones, pero impedir que una app se instale hasta recibir permiso explicito del administrador. Tambien se deben impedir la descarga y la instalacion lateral de archivos APK.
- Solucion propuesta de producto: al intentar instalar desde Play Store, identificar la app y crear una solicitud para Admin; solo una aprobacion vigente habilita la instalacion de ese paquete. Para APK externos, bloquear la descarga cuando el canal sea observable y bloquear siempre el acceso al instalador o la ejecucion de paquetes no autorizados. Mantener una defensa posterior que bloquee o ponga en cuarentena una app instalada sin autorizacion.
- Alternativas tecnicas:
  - Android Enterprise/Device Owner con Managed Google Play y politica allowlist: garantia del sistema, pero requiere dispositivo administrado y aprovisionamiento compatible;
  - cuenta infantil supervisada con Family Link: Google Play ofrece aprobacion de descargas, pero el flujo y la autoridad pertenecen a Google/Family Link, no a Content Filter;
  - arquitectura actual sin MDM: Accessibility intercepta el boton o flujo de instalacion de Play Store, envia la solicitud y permite temporalmente el paquete aprobado; es best-effort y depende de version, idioma y fabricante;
  - bloqueo de APK: DAG puede impedir descargas por extension, MIME y respuesta; navegadores y apps externas requieren VPN/Accessibility y siguen siendo best-effort. El instalador de paquetes y el permiso `Instalar apps desconocidas` constituyen la barrera final;
  - respaldo post-instalacion: detectar paquetes nuevos y bloquear su ejecucion hasta aprobacion; evita uso no autorizado, pero no impide que el APK llegue a instalarse.
- Evidencia: idea expresada por el usuario el 2026-07-15. Android Management API permite definir apps `AVAILABLE` o `BLOCKED` y un modo allowlist en dispositivos administrados; Family Link admite aprobaciones de descargas para cuentas supervisadas.
- Esfuerzo: L.
- Riesgo: alto; Play Store cambia su UI, instalaciones pueden iniciarse desde web u otras tiendas, las actualizaciones deben distinguirse de instalaciones nuevas y una ventana demasiado amplia podria autorizar otro paquete.
- Dependencias: Accessibility y barrera; VPN y DAG; inventario de apps instaladas; solicitudes y FCM; packageName confiable; Package Installer, gestores de archivos, navegadores, mensajeria, tiendas alternativas y permiso de fuentes desconocidas; autorizaciones temporales por dispositivo; posible linea futura Device Owner/Android Enterprise.
- Duplicados y relacion: complementa `USAGE-REAL-01`, solicitudes existentes y la barrera antimanipulacion; no duplica bloqueo de uso porque agrega aprobacion previa a la instalacion.
- Criterios de aceptacion propuestos:
  - Play Store permanece accesible para buscar y revisar fichas;
  - una app no autorizada no puede completar su instalacion o, en el modo best-effort, no puede ejecutarse y queda claramente en cuarentena;
  - la solicitud identifica app, packageName, usuario y dispositivo sin incluir historial de busqueda de Play Store;
  - aprobar habilita unicamente el paquete solicitado y por una ventana acotada;
  - rechazar mantiene el bloqueo y no genera solicitudes duplicadas;
  - instalaciones desde web, tiendas alternativas, APK local y ADB tienen una politica explicita;
  - DAG no descarga archivos APK y los canales externos cubiertos bloquean la descarga o el acceso al instalador;
  - el usuario no puede habilitar `Instalar apps desconocidas` ni completar Package Installer mientras la proteccion esta armada y no existe autorizacion;
  - actualizaciones de apps ya aprobadas no se confunden con instalaciones nuevas.
- Implementacion compatible: Play Store permanece visible. `PACKAGE_ADDED` distingue una app nueva de una actualizacion, pone el paquete nuevo no sistemico en cuarentena local y crea una unica solicitud `APP_ACCESS` con packageName, nombre, usuario y dispositivo. Accessibility impide abrirlo hasta que exista una regla Allow explicita para ese paquete.
- Barrera APK: DAG ya bloqueaba todas las descargas. Accessibility bloquea Package Installer y la habilitacion de fuentes desconocidas mientras la proteccion esta armada. Una autorizacion interna protegida por permiso `signature` abre por cinco minutos exclusivamente el flujo de actualizacion iniciado por App Usuario o App Admin, manteniendo la confirmacion normal de Android.
- Decisiones aplicadas: mismo comportamiento para apps gratuitas o pagas; actualizaciones de paquetes existentes no solicitan permiso; una reinstalacion conserva una aprobacion Allow previa y, sin ella, vuelve a cuarentena; tiendas, adjuntos, gestores de archivos y ADB quedan cubiertos por bloqueo observable del instalador mas cuarentena posterior. Apps del sistema y los dos paquetes Content Filter quedan excluidos.
- Limite real: es best-effort en Android normal. Sin Device Owner no se promete impedir materialmente toda instalacion en todos los OEM, pero una app nueva detectada no puede usarse sin aprobacion mientras Accessibility funciona. La garantia de sistema queda como modalidad empresarial futura y requeriria aprovisionamiento administrado.
- Correccion DEV 244: la autorizacion usa el componente exacto del receptor firmado, el APK pendiente se retoma al volver del permiso por origen y la politica reconoce variantes Samsung/OEM del instalador y de fuentes desconocidas. La matriz integral de 1.011 tareas fue correcta y ambos APK se instalaron in-place en SM-A235M conservando Accessibility, Device Admin y VPN.
- Correccion DEV 245: el detector separa la identidad exacta de App Admin de la etiqueta generica de Usuario. La ficha de aplicacion y el permiso por origen de Admin quedan accesibles permanentemente; los Ajustes sensibles de Usuario y las fuentes desconocidas de terceros siguen protegidos. Las regresiones, la matriz integral y la instalacion in-place auxiliar fueron correctas.
- Correccion DEV 254 (`UPDATE-TRUSTED-INSTALL-02`): la entrega firmada Admin -> Usuario deja de depender de un filtro redundante sobre el receptor y Samsung `SubSettings` se reconoce mediante indicadores especificos de fuentes desconocidas. La ventana de cinco minutos no habilita VPN, Accessibility, Device Admin ni remocion, y sin ventana confiable la fuente permanece bloqueada.
- Validacion DEV 254: tests, formato, builds, CI, publicacion unica, manifiestos, hashes y firma correctos; ambas APK se instalaron in-place en SM-A235M y recibieron el permiso de firma. Pendiente instalar 254 una ultima vez en el Samsung personal y confirmar que las actualizaciones siguientes se inician desde Usuario y Admin sin autorizar Ajustes manualmente. Sigue pendiente la prueba completa Play Store/APK/aprobacion del alcance mayor del ticket.

### SUPERADMIN-DAG-ENTITLEMENT-01 - DAG como funcion premium

- Estado: `Backend DEV y Android DEV 241 publicados; Superweb pendiente de hosting y prueba fisica`. Aprobado por el usuario al ordenar ejecutar todos los tickets el 2026-07-16.
- Tipo: monetizacion, licencias y control Super Admin.
- Prioridad: P1.
- Problema: DAG es una funcion premium y su disponibilidad no debe depender unicamente del control por dispositivo que usa App Admin.
- Solucion propuesta: agregar en Super Admin una habilitacion de producto para DAG a nivel de comunidad o licencia. Solo cuando la comunidad tenga DAG habilitado, sus administradores pueden abrirlo o cerrarlo para usuarios/dispositivos.
- Evidencia: decision comercial expresada por el usuario el 2026-07-15.
- Esfuerzo: M.
- Riesgo: alto; una validacion solo visual podria permitir acceso por sesiones o reglas antiguas, y una deshabilitacion incorrecta podria borrar configuracion o historial local.
- Dependencias: `SEC-LICENSE-01`; Super Admin Web; modelo de comunidad/licencia; reglas DAG por dispositivo; App Admin, App Usuario y sincronizacion; comportamiento offline; costos y cupos Brave.
- Duplicados y relacion: no duplica el switch DAG existente; agrega una capa superior de entitlement premium. La jerarquia propuesta es Super Admin/comunidad -> Admin/dispositivo -> App Usuario.
- Criterios de aceptacion propuestos:
  - Super Admin puede habilitar o deshabilitar DAG para la comunidad correcta;
  - App Admin solo muestra o permite el control DAG cuando el entitlement premium esta activo;
  - App Usuario y el launcher DAG rechazan acceso si la comunidad no tiene entitlement, aunque exista una regla antigua `Allow` por dispositivo;
  - deshabilitar conserva configuracion, historial local y reglas para una futura renovacion, salvo una politica distinta aprobada expresamente;
  - renovar o reactivar restaura la capacidad sin reinstalar ni borrar datos;
  - estado offline, sesiones anteriores, realtime, full sync y reinicios no permiten eludir el entitlement;
  - el cambio queda auditable sin exponer consultas ni historial.
- Decisiones pendientes para el ticket: nivel comunidad, plan o licencia; precio y cupo incluidos; periodo de gracia; comportamiento al vencer; visibilidad del upsell en Admin; quien puede activar; soporte offline y migracion de comunidades actuales.
- Decision implementada: booleano en la licencia de comunidad, separado del cupo mensual. Las comunidades existentes conservan el acceso; las nuevas comienzan sin DAG. Super Admin es la unica autoridad para cambiarlo; una licencia no activa o sin entitlement cierra DAG conservando toda la configuracion.

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

### DAG-IMAGE-QUEUE-03 - Entrega continua, lazy, iconos y SVG seguro

- Estado: `Completado y publicado en DEV 252`; aprobado por el usuario el 2026-07-17.
- Causa: tres interceptores sincronicos y la promocion simultanea de todas las fuentes lazy podian ocupar el pool de recursos WebView y dejar en blanco las imagenes posteriores al primer grupo.
- Implementacion: ocho trabajos acotados, timeout de 15 segundos y carga por proximidad al viewport. Se cubren `picture`, fuentes lazy/srcset adicionales, favicon ICO y SVG estatico con simbolos o gradientes internos.
- Seguridad: cada raster conserva clasificacion local antes de mostrarse; SVG con scripts, eventos, embeds, estilos activos o referencias externas falla cerrado; video y audio siguen bloqueados.
- Aceptacion: Samsung se recorrio durante diez desplazamientos; las imagenes posteriores continuaron resolviendo, los telefonos seguros quedaron visibles y la categoria sensible de H&M permanecio desenfocada. Iconos y SVG seguros conservan el soporte publicado en DEV 251.

### DAG-VIEWPORT-READY-01 - Apertura con pantalla inicial resuelta

- Estado: `Completado y publicado en DEV 252`; aprobado explicitamente por el usuario el 2026-07-17.
- Causa: DAG revelaba el documento cuando terminaba el texto, sin coordinar las imagenes visibles; ademas descargaba recursos lejanos antes de priorizar la pantalla actual y esperaba cinco segundos fijos para iniciar el analisis del DOM.
- Implementacion: compuerta conjunta de texto e imagenes iniciales; precarga de las dos pantallas siguientes; diferimiento de imagenes lejanas; limite terminal de ocho segundos; animacion de progreso en la barra; evaluacion temprana del DOM con reintentos; metricas locales sin contenido.
- Clasificacion: el modelo profesional decide primero, el modelo legado queda como fallback de incertidumbre y el detector de modestia conserva la ultima palabra sobre imagenes que de otro modo serian permitidas.
- Aceptacion fisica SM-A235M: apertura Samsung con fotos iniciales listas en aproximadamente 6-8 segundos, sin falsos desenfoques de telefonos; diez desplazamientos cargan imagenes posteriores; H&M trajes de bano conserva desenfoque fuerte; cero consultas Brave al usar URL directa.

### Calibracion DAG - cola, criterio humano, versiones y modelos

- Estado conjunto: `Publicado en DEV 255`; aprobado explicitamente por el usuario el 2026-07-17 para ejecutar los tickets seguidos y publicar una sola vez al final. Solo queda el recorrido visual autenticado de la cola Super Admin.
- `DAG-CALIBRATION-PROGRESS-01`: reemplaza la animacion ficticia por etapas reales de busqueda, inicio de pagina, analisis de texto e imagenes visibles. El porcentaje nunca retrocede, llega a 100 % solamente al resolver la compuerta y se comunica a accesibilidad.
- `DAG-CALIBRATION-QUEUE-02`: solamente una clasificacion visual incierta produce una miniatura JPEG de hasta 512 px y 128 KiB. Se envia autenticada y deduplicada por hash al bucket privado DEV; no contiene URL, consulta, texto ni la imagen original. Una falla de red no bloquea ni modifica la decision local. El backend limita 100 casos nuevos por dispositivo cada 24 horas y 250 pendientes vigentes.
- `DAG-CALIBRATION-REVIEW-03`: Super Admin muestra los casos mediante URL firmada de cinco minutos y exige `Permitir` o `Difuminar`, motivo estructurado y nota opcional. Puntajes, actor, fecha, decision y motivo quedan auditados. No existe lectura directa de tablas para clientes.
- `DAG-CALIBRATION-VERSIONS-04`: con 12 etiquetas y al menos tres de cada decision, `Calibrar DAG` calcula umbrales candidatos ponderando falsos negativos 3x. Los motivos limitan cada ajuste a señales relacionadas. Ningun candidato cambia Android hasta activacion manual; cualquier version retirada puede reactivarse como rollback registrado.
- `DAG-CALIBRATION-MODELS-05`: registra el modelo profesional actualmente embebido y reserva estados, hash de artefacto y metricas para modelos futuros. Calibrar ajusta umbrales, no pesos. Entrenar o cambiar la IA queda bloqueado hasta disponer de corpus suficiente, validacion independiente, artefacto firmado y compatibilidad Android; la Superweb explica esa diferencia y no simula un entrenamiento inexistente.
- `DAG-CALIBRATION-QUALITY-06`: solo los cruces leves de umbral entran a revision. Evidencia fuerte de desnudez o modestia se bloquea localmente incluso si el modelo profesional queda incierto; el backend DEV repite la comprobacion antes de almacenar para proteger la cola frente a clientes anteriores o inconsistentes.
- `DAG-CALIBRATION-CLEAR-07`: Super Admin incorpora `Borrar todas` con confirmacion. Elimina fisicamente miniaturas privadas mediante Storage API, archiva las filas visibles pendientes y revisadas, excluye esos ejemplos de futuras calibraciones y registra actor, cantidad de casos y objetos en auditoria. No borra calibraciones ya versionadas ni ejecuta el borrado automaticamente.
- `DAG-CALIBRATION-IN-PAGE-08`: el menu DEV incorpora un modo de prueba apagado por defecto. Agrega una X a las fotos visibles ya clasificadas; tocarla aplica blur inmediato y crea un caso manual pendiente con origen separado. No toma la ausencia de X como permiso y no incorpora el caso a una calibracion hasta que Super Admin confirme el motivo. El puente exige marco principal HTTPS y coincidencia con el mapa local efimero del cargador. Beta y Production no muestran la funcion.
- `DAG-CALIBRATION-BIDIRECTIONAL-09`: publicado en DEV 259 y validado fisicamente con el APK publico en SM-A235M. Renombra la herramienta como `Calibración DEV`, recarga temporalmente la pagina con todos los raster originales ya evaluados y asigna `X` a los originalmente permitidos o `R` a los que DAG habria difuminado. X propone un falso negativo; R propone un falso positivo. Ambos llegan pendientes y separados a Super Admin, donde la etiqueta y el motivo humanos siguen siendo obligatorios antes de calibrar. DEV 258 entregaba directamente cada decision, pero el modo visual podia conservar el estado inicial apagado y esperar indefinidamente `onPageFinished`. DEV 259 sincroniza el estado actual y las decisiones desde el page commit; X aparece y se reposiciona al desplazar en samsung.com. El puente nativo se registra solo en DEV; desactivar el modo recarga la pagina y restaura el blur normal. La migracion esta aplicada solo en DEV, Edge v9 activa y Superweb desplegada.
- `DAG-CALIBRATION-CLOSED-LOOP-10`: publicado DEV 260 y validado en SM-A235M. Cada version guarda explicitamente las revisiones humanas que la originaron y Android descarga sus hashes junto con los umbrales. La misma miniatura deterministica recibe entonces la decision exacta revisada; un bloqueo exacto prevalece sobre un permiso si existiera conflicto. La migracion recupero las 22 revisiones de la calibracion activa DEV 3 (5 permitidas y 17 bloqueadas) sin borrar datos. Los permisos ayudan a bajar falsos positivos de todas las señales, mientras que cada bloqueo entrena solamente el detector coherente con su motivo; una etiqueta de escote ya no puede modificar mangas y un celular permitido si corrige el detector que lo confundio. Se reserva un grupo balanceado para validacion y se registran errores separados antes de activar manualmente.
- La calibracion activa 3 tenia umbrales degenerados (`0.0002–0.0058` y rango profesional sin separacion), causa adicional de sensibilidad y de que Android informara `base`. `CLOSED-LOOP-10` aplica limites seguros por señal en App Usuario, Edge y RPC: un conjunto pequeño puede afinar dentro de rangos profesionales, pero no llevar la decision a cero ni invertir el margen seguro/bloqueo. La prueba fisica posterior mostro `Calibración aplicada: #3` y telefonos Galaxy sin blur en samsung.com.
- La revision Super Admin de `CLOSED-LOOP-10` obliga a elegir primero `Permitir` o `Difuminar` y muestra despues solo motivos coherentes: positivos para permitir y negativos para bloquear. Dejar sin respuesta conserva el caso pendiente. Cada tarjeta explica en lenguaje breve las señales que causaron blur o revision; los puntajes tecnicos siguen disponibles en un detalle cerrado. La base rechaza combinaciones incompatibles aunque se evada la interfaz.
- App Usuario agrega dos señales tzniut locales y calibrables: manga por encima del codo y falda, short o pantalon por encima de la rodilla. Usa MoveNet Lightning INT8 embebido solo cuando ya existe contexto femenino suficiente y combina sus 17 landmarks de hombro/codo/muñeca o cadera/rodilla/tobillo con piel local. El artefacto oficial queda fijado por SHA-256 y la inferencia no depende de Google Play. Reemplaza ML Kit Pose, cuya distribucion embebida elevaba el APK Usuario a unos 89 MB y excedia el limite de Supabase; MoveNet deja el APK en 51.520.718 bytes. Es un detector con umbral conservador: los casos cercanos van a revision y la mejora continua depende de etiquetas humanas representativas, no se presenta como reentrenamiento de pesos.
- Hotfix 2026-07-17: las primeras llamadas de la Superweb llegaron con JWT valido pero Edge perdio el contexto de `auth.uid()` en la comprobacion duplicada de rol y devolvio 403 antes de Storage. Edge v7 valida el JWT directamente con Auth y autoriza contra `super_admins`; la interfaz muestra el error concreto del backend. Los intentos fallidos no borraron ni archivaron ninguna de las 106 revisiones.
- Privacidad actualizada: las versiones historicas de `DAG-IMAGES-01` no sincronizaban ningun dato visual. Este bloque posterior, aprobado por el usuario, agrega exclusivamente miniaturas inciertas bajo el contrato anterior; la clasificacion completa y las imagenes normales permanecen locales.
- Backend DEV: migraciones `20260717175505_dag_calibration`, `20260717181351_dag_calibration_model_scope`, `20260717181541_dag_calibration_service_auth`, `20260717191627_dag_calibration_clear_queue`, `20260717193420_dag_calibration_archive_indexes` y `20260717204621_dag_visual_calibration_reports`, cuatro tablas con RLS, RPC Super Admin, bucket privado `dag-calibration` y Edge Function `dag-calibration` con autenticacion propia del dispositivo. La RPC de autorizacion queda reservada al backend; cada calibracion usa etiquetas del mismo modelo. No se borra informacion automaticamente; `expires_at` solo retira casos vencidos de la cola operativa y el borrado total exige una accion confirmada del Super Admin.
- Cierre DEV 253: tests Android y Web correctos; una sola publicacion Supabase DEV; manifiestos, hashes, paquetes y firma verificados; Edge v3 recibio lectura autenticada y casos reales; Superweb Vercel `bfab78d` desplego `/dag-calibration` y el acceso anonimo redirigio a Login.
- Cierre DEV 255: matriz local de 903 tareas Android y controles Web correctos; Android CI `29608399118` y publicacion unica `29608399103` exitosos. Ambos manifiestos, hashes, paquetes, SDK y firma fueron verificados publicamente en 255. Edge v6 y las dos migraciones nuevas estan activas solo en DEV, sin archivar ni borrar las 106 revisiones existentes. Vercel sirve `b54f7cd` y sus rutas privadas pasaron la verificacion anonima. Pendiente el recorrido autenticado de `Borrar todas` y Calibracion DAG; no habia telefono ADB visible para la prueba fisica.
- Cierre DEV 256: la matriz Android final completo 903 tareas y la repeticion Usuario posterior al hallazgo fisico completo 690; TypeScript, ESLint y build Next correctos. SM-A235M con ambas APK 256 confirmo el menu, dialogo, X visibles, blur inmediato y un pendiente `manual_dag` real. Edge v8 y la migracion nueva estan activas solo en DEV. El ejemplo de telefono marcado durante QA queda pendiente y no se borro, para verificar su insignia y revision desde la Superweb.

#### DAG-CALIBRATION-DELIVERY-11 - Entrega confirmada y recuperable

- Estado: `Propuesto`; causa de perdida confirmada en el cliente el 2026-07-22, pendiente aprobacion explicita para codigo. Tipo: confiabilidad Android, privacidad, Storage y operacion Super Admin. Prioridad: P0.
- Evidencia: el usuario reporta que fotos dudosas o marcadas para revision no aparecen en Calibracion DAG. Una tarjeta con miniatura fallida seguiria apareciendo como `Miniatura no disponible`; la ausencia total apunta principalmente a que la fila nunca se inserto, fue filtrada/vencio o el envio se perdio antes de confirmarse. No se consultaron filas DEV ni se modificaron datos durante este diagnostico.
- Causas confirmadas en Android: el envio automatico envuelve `submitReview` en `runCatching` pero ignora un `RemoteResult.Failure`; no hay outbox persistente ni reintento. En el modo manual, `takeManualCalibrationCandidate` retira el candidato de memoria antes del acuse remoto; si la URL JavaScript no coincide con la normalizada, no encuentra candidato y no envia. Un 403, 429, 503 o corte de red puede perder el caso y no existe estado recuperable.
- Alcance propuesto Android: outbox local cifrado y acotado para miniatura, hash, modelo, señales, puntajes, origen y resultado solicitado; identidad estable por hash en vez de URL mutable; estados `Enviando`, `Enviado` y `Pendiente de enviar`; WorkManager con backoff y restricciones de red; conservar el candidato hasta un acuse valido; deduplicacion e idempotencia; expiracion y borrado seguro.
- Alcance propuesto backend/Superweb: respuesta estructurada distingue `accepted`, deduplicado, rechazo de evidencia clara, limite y error recuperable; Android no interpreta cualquier HTTP 2xx como entrega; Super Admin permite diagnosticar conteos por estado sin exponer contenido; las miniaturas privadas siguen usando URL firmada breve. Cualquier cambio de tabla o RPC debe mantener RLS y autorizacion por dispositivo y Super Admin; nunca Service Role en Android.
- Privacidad: no enviar original, URL, consulta, texto de pagina ni historial. La cola local debe cifrarse, tener maximo de tamaño/edad, borrarse tras acuse o vencimiento y excluirse de backups si corresponde.
- Esfuerzo: L estimado. Riesgo: alto; reintentos no idempotentes pueden duplicar casos, una cola sin limites retiene datos sensibles y un acuse ambiguo puede declarar enviada una revision inexistente.
- Dependencias: `DagCalibrationRepository`, `DagBrowserViewModel`, `DagImageResourceLoader`, Room/WorkManager, Edge Function `dag-calibration`, Storage privado, RPC Super Admin y version de calibracion. Solo Supabase DEV `syeycayasyufedwoprea`; no Production.
- Criterios de aceptacion:
  - una revision automatica, X o R no desaparece hasta recibir un acuse estructurado y verificable;
  - sin red, tras reinicio de app/proceso y ante 429/503, queda `Pendiente de enviar` y se reintenta sin duplicar;
  - 400/403 no se reintentan indefinidamente y muestran una accion simple mientras Logcat conserva solo un codigo no sensible;
  - una URL DOM distinta para el mismo contenido se resuelve por identidad estable y no pierde el candidato;
  - `accepted:false` por evidencia clara o limite no se presenta como `Enviado`; cada resultado tiene un estado entendible;
  - el caso aceptado aparece en Super Admin, su miniatura privada abre con URL firmada y el conteo coincide con el acuse;
  - pruebas cubren online, offline, reinicio, duplicado, token invalido, limite, Storage fallido, DB fallida, vencimiento y limpieza local;
  - asesores de seguridad/rendimiento, RLS y denegacion a `anon`/`authenticated` se verifican antes de aplicar exclusivamente en DEV.

### AI-SEARCH-01A - Intencion semantica local compacta

- Estado: `En progreso` en DEV 212; aprobado por el usuario al pedir continuar con IA local. Falta prueba fisica.
- Objetivo implementado: sumar comprension estadistica local de sinonimos y evasiones simples sin reemplazar las reglas duras ni enviar contenido fuera del telefono.
- Modelo: clasificador lineal propio de 112 KiB sobre n-gramas de caracteres y palabras, con siete categorias y cobertura inicial espanol/ingles/hebreo. No es un LLM ni un transformer y no promete comprension humana.
- Decision: una categoria riesgosa con confianza alta bloquea; confianza media o contexto sensible quedan inciertos; general permite solo si ninguna capa anterior bloqueo. Modelo ausente o invalido falla cerrado.
- Datos y costo: 636 ejemplos controlados, 69 casos separados de verificacion, entrenamiento reproducible sin dependencias externas y cero costo/API adicional. DEV 226 corrige cinco evasiones detectadas por el control, principalmente en hebreo, y publica `dag-local-text-3` con SHA-256 `8ffb797ab8976f4a4cd18728c8a64ff5973392e30562a3e4291783f00cb9e612`. Consultas, texto, scores y decisiones permanecen locales.
- Aceptacion tecnica cumplida: pruebas dirigidas, build/test/lint local integral, publicacion DEV 212 y hashes publicos verificados. Aceptacion fisica pendiente: calidad en busquedas cotidianas/riesgosas, ausencia de falsos positivos graves y fluidez en el Samsung personal.

### DAG-ADAPTIVE-DECISION-01 - Decision local sin revision Admin obligatoria

- Estado: `Publicado en DEV 251; pendiente prueba fisica de calidad`; aprobado por el usuario el 2026-07-17.
- Problema: la zona incierta del modelo y las paginas con poco texto terminaban en `necesita revision`, aun sin una regla explicita ni evidencia fuerte.
- Implementacion: resultado adaptativo `permitido/protegido/bloqueado`. Incertidumbre y señal semantica moderada abren con filtrado visual, sin video, descargas, ventanas nuevas ni persistir aprobacion completa; reglas Admin, dominios prohibidos, categorias explicitas y evidencia semantica fuerte bloquean.
- Consulta Admin: deja de ser la salida automatica de una duda. La navegacion protegida decide localmente; una revision puede mantenerse como accion manual separada donde corresponda.
- Aceptacion pendiente: paginas cotidianas ambiguas o con poco texto abren protegidas; contenido explicito y reglas duras siguen bloqueados; validar español, ingles y hebreo en el telefono.

### DAG-USAGE-01 - Contador mensual en Super Web

- Estado: `Completado y publicado`; aprobado por el usuario el 2026-07-14.
- Objetivo: mostrar en Super Web el consumo mensual de DAG casi en tiempo real, con usados, limite y restantes por comunidad y dispositivo, total general, costo Brave estimado y alertas al 80% y 100%.
- Privacidad: no exponer consultas, URLs, resultados ni historial. Super Web consulta un agregado autorizado; la tabla de contadores permanece inaccesible para clientes comunes.
- Implementacion: refresco seguro cada 10 segundos, cupo configurable por comunidad, proyeccion mensual y costo estimado luego del credito Brave global. El piloto comercial aprobado es USD 1 con 100 busquedas por dispositivo/mes.
- Excepcion DEV temporal 2026-07-16: la licencia de `Yeshurun tora` quedo en 1.000 busquedas mensuales por dispositivo para continuar pruebas, sin reiniciar ni borrar consumo. Este cupo de producto no amplía el credito global de la cuenta Brave: cada pagina exitosa sigue contando como una solicitud facturable y los USD 5 mensuales gratuitos equivalen a 1.000 solicitudes totales de la cuenta al precio vigente.
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
| DAG-VISUAL-KOSHER-02 | 2026-07-15, DEV 229 | Ensemble local Marqo ViT-Tiny + OpenNSFW, umbrales conservadores y bloqueo explícito de lencería/ropa íntima; imágenes y scores permanecen en el teléfono |
| DAG-VISUAL-COVERAGE-03 | 2026-07-16, DEV 232 | Blur fuerte para riesgo/incertidumbre, contexto genérico de categorías femeninas íntimas y tarjetas VTEX, más SVG estático seguro para logos e iconos; sin reglas por dominio |
| DAG-AUTOCOMPLETE-01 | 2026-07-15, DEV 228 | Sugerencias durante escritura desde historial cifrado local y reformulaciones seguras; clasificación local, debounce y cero consultas Brave por pulsación |
| DAG-THEME-01 | 2026-07-15, DEV 228 | Barra más compacta y colores explícitos para resúmenes y sugerencias en tema oscuro/claro |
| DAG-APPROVAL-POLICY-01 | 2026-07-15, DEV 227 | Admin carga la politica completa antes de aprobar y rechaza una aprobacion DAG sobre politica incompleta, evitando cerrar el navegador |
| DAG-SEARCH-FP-01 | 2026-07-15, DEV 227 | `Coca-Cola` comercial se permite con vocabulario cerrado sin enmascarar cocaína ni contenido explícito; regresiones automatizadas |
| DAG-HOME-RESET-01 | 2026-07-15, DEV 227 | Home limpia buscador, URL/resultados/carga y navegacion previa; contraste explicito en tema claro y oscuro |
| DAG-APPROVAL-CACHE-01 | 2026-07-15, DEV 226 | Cache cifrada por URL/contenido/resumen visual/politica/modelo durante siete dias, invalidacion segura y borrado separado |
| DAG-TABS-UX-01 | 2026-07-15, DEV 226 | Hasta ocho pestanas cifradas sobreviven al proceso; sin HTML/miniaturas persistidas y con recarga/revalidacion al restaurar |
| DAG-ANALYSIS-UX-01 | 2026-07-15, DEV 226 | Durante carga queda solo `Analizando` con puntos animados dentro del campo; sin direccion vieja ni texto central |
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

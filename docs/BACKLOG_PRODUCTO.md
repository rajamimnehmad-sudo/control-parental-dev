# BACKLOG DE PRODUCTO

Ultima sincronizacion: 2026-07-14

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

- Estado preparado para publicacion: App Usuario DEV 204 y App Admin DEV 204, `1.0.1-dev`.
- Baseline de recuperacion Web: `stable/dev-191-web-protection` (no representa la ultima version publicada).
- FCM real y alertas de proteccion ya estan implementados y validados en DEV 202.
- Los detalles, hashes, commits y evidencias vigentes viven unicamente en `docs/HANDOFF_ACTUAL.md` y `docs/BASELINES.md`.

## Ultimo ticket resuelto

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

- Estado: `Resuelto` el 2026-07-14 en DEV 206; aprobado explicitamente por el usuario.
- Tipo: rendimiento y estabilidad visual. Prioridad: P1. Esfuerzo: M. Riesgo: medio.
- Causa: el inicio mantenia dos animaciones infinitas superpuestas; Home y Web componian todo su contenido desplazable de forma eager; DAG deshabilitaba la cache HTTP, ejecutaba dos puentes JavaScript por pagina, podia inspeccionar mas de una vez la misma carga y cifraba el historial en el hilo principal.
- Solucion: mascota estatica; listas lazy en Home y Web; cache HTTP normal del WebView; saneamiento y extraccion de texto en una sola operacion; inspeccion unica por carga; patrones del clasificador precompilados; cifrado y borrado del historial fuera del hilo principal.
- Alcance comercial: no se agrego cache de consultas ni resultados Brave. Repetir una busqueda sigue consumiendo una consulta; navegar, recargar o abrir una URL/historial no consume Brave.
- Evidencia fisica SM-A235M: el repintado inactivo del inicio bajo de aproximadamente 183 cuadros en tres segundos a 3. En Web, seis desplazamientos automatizados terminaron con 5,68 % de cuadros fuera de plazo, mediana de 17 ms y percentil 90 de 21 ms.
- Seguridad: imagenes y video siguen bloqueados; el contenido permanece oculto hasta completar el analisis local; no cambio Supabase, el cupo, la barrera, la VPN ni Accessibility.

## Pendientes priorizados

| ID | Estado | Pri. | Ticket | Esfuerzo | Riesgo |
| --- | --- | --- | --- | --- | --- |
| SEC-LICENSE-01 | Idea | P0 | Ciclo de vida de comunidad y licencia: alta, renovacion, vencimiento y restauracion sin perder configuracion | L | Alto |
| DATA-DELETE-01 | Idea | P0 | Borrado definitivo y auditable de usuario; la accion actual falla para todos los usuarios | L | Muy alto |
| OPS-METRICS-01 | Idea | P1 | Medicion prolongada de bateria, trafico y estabilidad | M | Medio |
| USAGE-REAL-01 | Idea | P1 | Uso real de app foreground y estabilidad de listas | L | Alto |
| REQUESTS-UX-01 | Idea | P2 | Historial, estados y refresco manual claro de solicitudes | M | Medio |
| SUPERADMIN-TOKEN-01 | Idea | P2 | Gestion segura y auditable de tokens desde Super Admin | L | Alto |
| UI-POLISH-01 | Idea | P2 | Consistencia visual y accesibilidad de ambas apps | M | Bajo |
| USER-RESILIENCE-01 | Idea | P2 | Recuperacion guiada de estados degradados sin confundir al usuario | M | Medio |
| SUPERADMIN-MSG-01 | Idea | P2 | Avisos push y bandeja interna, no chat libre | L | Medio |
| SUPERADMIN-ALERTS-01 | Idea | P2 | Visibilidad en Super Admin de intentos de desinstalacion o manipulacion de protecciones | M | Medio |
| ADMIN-ALERTS-UX-01 | Idea | P2 | Campanita y bandeja de alertas de seguridad en App Admin, separadas de Solicitudes | M | Medio |

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

- Estado: `En progreso`; aprobado explicitamente por el usuario el 2026-07-14.
- Prioridad: P1. Esfuerzo: XL. Riesgo: alto.
- Vision acordada: primer corte util del navegador propio DAG, con experiencia cotidiana parecida a Chrome pero conservadora y cerrada por defecto.
- Alcance aprobado: barra combinada para buscar o escribir URL; resultados reales; navegacion interna; atras, adelante, recargar e inicio; una pestaña; formularios e inicio de sesion.
- Privacidad: historial cifrado solo en el telefono con busquedas, paginas, titulo y fecha. Solo el usuario puede verlo y borrar elementos o todo el historial. No se sincroniza, no entra en backups y no se envia al administrador.
- Clasificacion: consulta y contenido textual se procesan localmente en espanol, hebreo e ingles. Salidas `Permitida`, `Bloqueada` o `Incierta`; ante error, modelo ausente o baja confianza se bloquea.
- Fuente: solo una consulta permitida puede llegar a Brave Search mediante una Edge Function autenticada en Supabase DEV. La credencial de Brave queda como secreto servidor y Android conserva solo credenciales publicas y su sesion/dispositivo.
- Modelo comercial en revision: el usuario evalua ofrecer DAG por USD 1 mensual con un cupo inicial candidato de 100 busquedas por dispositivo y navegacion directa ilimitada. El despliegue actual conserva temporalmente el limite tecnico de 200 y no tiene sobreconsumo automatico.
- Costos y privacidad: Supabase conserva solo `device_id`, mes y contador para aplicar el cupo; no guarda consultas, resultados ni historial. Brave aplica USD 5 de credito global mensual a la cuenta; no es un credito por usuario.
- Revision humana: un sitio incierto puede generar una solicitud de dominio. La consulta y el historial no se incluyen; el administrador recibe solo dominio, titulo y motivo.
- Seguridad inicial: imagenes, video, descargas, popups, camara, microfono, ubicacion, archivos e intents externos permanecen bloqueados. El contenido no se hace visible antes de la evaluacion local.
- Revocacion: cerrar DAG desde App Admin impide buscar o navegar, incluso al entrar desde el atajo fijado.
- Fuera de alcance de este corte: imagenes y video clasificados, multiples pestanas, descargas, historial remoto, inspeccion de navegadores externos y analisis continuo de pantalla.
- Aceptacion: pruebas de dominio/UI/red, ausencia de consultas en logs, aislamiento del secreto, fallo cerrado, borrado local de historial, revocacion remota y validacion fisica completa en el Samsung conectado.

### DAG-USAGE-01 - Contador mensual en Super Web

- Estado: `Completado y publicado`; aprobado por el usuario el 2026-07-14.
- Objetivo: mostrar en Super Web el consumo mensual de DAG casi en tiempo real, con usados, limite y restantes por comunidad y dispositivo, total general, costo Brave estimado y alertas al 80% y 100%.
- Privacidad: no exponer consultas, URLs, resultados ni historial. Super Web consulta un agregado autorizado; la tabla de contadores permanece inaccesible para clientes comunes.
- Implementacion: refresco seguro cada 10 segundos, cupo configurable por comunidad, proyeccion mensual y costo estimado luego del credito Brave global. El piloto comercial aprobado es USD 1 con 100 busquedas por dispositivo/mes.
- Aceptacion tecnica: migracion DEV aplicada, Edge Function actualizada, RPCs Super Admin aisladas de `anon`, tabla sin lectura de clientes, builds Web exitosos y Super Web Sites version 3 publicada en modo privado. La prueba fisica completa de DAG permanece como ultimo paso de `DAG-BROWSER-01A`.

### Otros tickets de roadmap

| ID | Estado | Pri. | Idea |
| --- | --- | --- | --- |
| WEB-SOURCES-01 | Idea | P1 | Evaluar nuevas fuentes Web con licencia, calidad, deduplicacion, firma y rollback |
| WEB-CACHE-01 | Idea | P1 | Cache local acotada de reputacion sin URL ni historial |
| AI-DOMAIN-01 | Idea | P1 | Clasificador local de dominios desconocidos sin descargar paginas |
| AI-SEARCH-01 | Idea | P1 | Clasificador local medido para intencion y evasiones de busqueda |
| NET-EVASION-01 | Idea | P2 | DoH/DoT desconocido, IP directa, proxies y VPN externas |
| AI-VISUAL-01 | Idea | P3 | Evaluacion puntual de regiones visibles, sujeta a privacidad, bateria y politicas |
| AI-SENSITIVE-01 | Idea | P3 | Riesgos sensibles en chats como proyecto separado legal y de privacidad |

Ideas conservadas: politicas por horario/contexto; fallback SafeSearch para ambiguedad; explicaciones y autorizacion; sugerencias sin aplicacion automatica; navegador propio futuro; revision humana y publicacion comunitaria firmada; alertas agregadas sin historial de navegacion.

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
| DAG-FOUNDATION-01 | 2026-07-14, DEV 204 | Control por dispositivo, entrada Usuario, pantalla cerrada por defecto y atajo Android; sin abrir contenido ni agregar esquema |
| PUSH-REAL-01 | 2026-07-14, DEV 202 | FCM real con tokens seguros y credenciales servidor dedicadas; alerta fisica con Admin cerrado y apertura correcta en Apps |
| USER-PERF-01 | 2026-07-14, DEV 203 | Suscripciones aisladas por pantalla, refresco remoto duplicado eliminado y una unica accion contextual; Home 2,99 % -> 0,26 % y Ajustes 0,66 % -> 0,43 % de frames lentos en SM-A235M |
| BARRIER-ANDROID-01 | DEV 198-202 | Barrera reforzada, navegacion defensiva, recuperacion y alertas; alcance real documentado en el handoff |
| WEB-LISTS-A | 2026-07-13 | The Block List Project agregado, deduplicado, firmado, publicado atomicamente y validado |
| WEB-SENSITIVE-A1 | DEV 192 | Adulto, apuestas, drogas y pirateria/torrents en formato extensible firmado y con validacion fisica |
| WEB-BASELINE-191 | DEV 188-191 | Decisiones Web comunes, UT1, SafeSearch, recuperacion VPN, conexiones e invalidaciones estabilizadas |

`Bloquear imagenes` fue eliminado y no debe reaparecer como pendiente. El historial tecnico detallado queda en `docs/HANDOFF_ACTUAL.md`.

## Prompt para el chat dedicado de backlog

Copiar desde aqui:

> Trabaja unicamente en `/Users/yejielnehmad/Developer/content-filter`. No uses ni revises copias en Documents. Antes de actuar lee completos `AGENTS.md`, `START_HERE.md`, `docs/HANDOFF_ACTUAL.md` y `docs/BACKLOG_PRODUCTO.md`, y sigue la jerarquia de fuentes definida alli. Este chat es exclusivo para capturar, entrevistar, clasificar, deduplicar y mantener ideas y tickets de producto. El Google Doc `Backlog Codex - Control Parental` es una bandeja de entrada historica; `docs/BACKLOG_PRODUCTO.md` es el backlog canonico y `docs/HANDOFF_ACTUAL.md` es la verdad tecnica. Primero entrevistame. No escribas codigo, no modifiques Android, no uses Supabase ni publiques APKs hasta que yo apruebe explicitamente un ticket. Para cada idea asigna ID, estado, tipo, prioridad, evidencia, esfuerzo, riesgo, dependencias y criterios de aceptacion. No borres ideas: agrupalas, reclasificalas o archivalas con motivo. Si un dato tecnico entra en conflicto, prevalece el handoff. Cuando confirmemos una entrada, actualiza el backlog del repo y sube solamente ese cambio documental con `[skip ci]`; no hagas build ni cambies `versionCode`. Usa solo Supabase DEV `syeycayasyufedwoprea` si un futuro ticket aprobado lo requiere; nunca Production, nunca borres datos sin mi confirmacion especifica y nunca pongas Service Role Key en Android. Al cerrar, mostra exactamente que entrada cambiaste y el estado de Git.

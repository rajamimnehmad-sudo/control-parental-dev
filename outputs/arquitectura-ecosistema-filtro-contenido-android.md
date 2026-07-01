# Arquitectura e implementacion de un ecosistema de filtro de contenido comunitario para Android

Fecha: 2026-06-29  
Estado: documento de arquitectura, sin implementacion

## 1. Validacion de viabilidad tecnica

El proyecto es viable en dispositivos Android normales si se acepta una premisa central: el sistema no puede controlar todo el dispositivo ni inspeccionar todo el trafico. La arquitectura debe tratar la VPN local y el AccessibilityService como sensores y puntos de aplicacion parciales, no como mecanismos absolutos.

Viable oficialmente:

- VPN local con `VpnService` para enrutar trafico a traves de la app, aplicar reglas por dominio/IP cuando la informacion este disponible, filtrar DNS tradicional si el dispositivo usa la VPN como resolvedor, bloquear conexiones por destino conocido y exponer estado de proteccion.
- AccessibilityService para detectar la app activa, medir tiempo de uso por paquete, mostrar una pantalla de bloqueo propia, guiar al usuario y ejecutar acciones permitidas por Android cuando el usuario habilita el servicio.
- Politicas offline con Room, cache local, WorkManager y sincronizacion incremental.
- Administracion remota con Supabase Realtime y respaldo periodico con WorkManager.
- Seguridad razonable con Android Keystore, cifrado local, HTTPS, R8 y Play Integrity API.

No viable de forma garantizada:

- Inspeccionar contenido HTTPS sin instalar certificados, romper TLS o usar tecnicas incompatibles con privacidad y Google Play.
- Forzar DoH o DoT de terceros desde una app normal en todo el sistema. La app puede observar menos cuando otras apps usan DNS cifrado propio.
- Impedir que el usuario desactive VPN o AccessibilityService. Sin Device Owner, MDM, root o controles parentales de plataforma, Android conserva control del usuario.
- Garantizar bloqueo perfecto ante apps que usan QUIC, DNS propio, IPs directas, certificados fijados o trafico cifrado extremo a extremo.
- Hacer que un APK fuera de Google Play use Play Integrity con la misma experiencia final que una app distribuida por Play. La API puede integrarse, pero el valor operativo pleno aparece al publicar y asociar correctamente la app.

Decision principal: construir un sistema de control comunitario robusto por capas. La VPN cubre red; AccessibilityService cubre app activa y tiempo; la base local permite decisiones offline; Supabase sincroniza; el PolicyEngine decide todo.

## 2. Arquitectura completa

### Principios

- Clean Architecture: dominio sin Android, sin Room, sin Supabase, sin Compose.
- MVVM en las apps: ViewModel orquesta casos de uso, no decide reglas.
- Unico cerebro: toda decision de negocio pasa por PolicyEngine.
- Offline-first: la fuente de verdad operativa es local.
- Sync eventual: Supabase distribuye cambios, pero el dispositivo puede operar dias sin internet.
- Bajo consumo: eventos, diffs y trabajos programados; evitar loops, polling frecuente y wake locks manuales.
- Seguridad pragmatica: cifrado y atestacion donde aportan valor, sin mecanismos costosos de mantener.

### Capas

- Presentacion: Compose, ViewModel, StateFlow, navegacion, pantallas de estado, bloqueo, solicitudes y administracion.
- Dominio: entidades, reglas, decisiones, PolicyEngine, casos de uso, contratos de repositorios.
- Datos: repositorios, mappers, Room, sync, Supabase, almacenamiento seguro.
- Plataforma: VpnService, AccessibilityService, WorkManager, Play Integrity, Keystore, notificaciones.

### Apps

- `app-user`: aplicacion del dispositivo protegido.
- `app-admin`: aplicacion del administrador.

Ambas comparten dominio, red, seguridad, UI base y modelos. No comparten pantallas especificas salvo componentes genericos.

## 3. Diagrama de modulos

Diagrama textual:

- `app-user` depende de `feature-vpn`, `feature-accessibility`, `feature-blocking`, `feature-requests`, `feature-onboarding`, `feature-status`, `core-ui`, `core-domain`, `core-data` y `core-security`.
- `app-admin` depende de `feature-admin-rules`, `feature-admin-requests`, `feature-admin-devices`, `feature-admin-history`, `feature-status`, `core-ui`, `core-domain`, `core-data` y `core-security`.
- `core-data` depende de `core-database`, `core-network`, `core-security`, `core-policy` y `core-domain`.
- `core-policy` depende solo de `core-domain`.
- `feature-vpn` depende de `core-policy` y `core-domain`.
- `feature-accessibility` depende de `core-policy` y `core-domain`.

Modulos adicionales recomendados:

- `feature-status`: justificado porque el monitoreo de salud sera usado por usuario y administrador.
- `feature-requests`: separa solicitudes de desbloqueo/tiempo de la pantalla de bloqueo.
- `feature-blocking`: contiene pantalla de bloqueo y flujos de advertencia.
- `core-sync`: opcional. Si la sincronizacion crece, conviene separarla de `core-data`. En fase 1 puede vivir dentro de `core-data`.

## 4. Flujo completo del sistema

1. El usuario instala `app-user` mediante APK firmado.
2. Onboarding explica permisos, limitaciones y privacidad.
3. El usuario activa VPN local mediante el flujo oficial de Android.
4. El usuario activa AccessibilityService manualmente desde ajustes del sistema.
5. La app descarga politica inicial si hay internet; si no, crea una politica local minima.
6. Room almacena reglas, limites, horarios, permisos, solicitudes y eventos.
7. VPN recibe eventos de red y consulta al PolicyEngine.
8. AccessibilityService recibe cambios de ventana/app activa y consulta al PolicyEngine.
9. PolicyEngine devuelve una decision: permitir, bloquear, advertir, solicitar autorizacion, registrar evento o actualizar contador.
10. La app aplica la decision usando capacidades oficiales: bloquear trafico por VPN, mostrar pantalla propia, registrar evento, crear solicitud o sincronizar.
11. Supabase Realtime empuja cambios cuando hay conexion.
12. WorkManager ejecuta sync incremental como respaldo, con intervalo minimo practico de 15 minutos para trabajo periodico.
13. El modulo de estado evalua VPN, AccessibilityService, sync, integridad y base de datos.

## 5. Flujo entre Usuario y Administrador

Solicitud de desbloqueo:

1. Usuario intenta abrir app o dominio bloqueado.
2. PolicyEngine devuelve `RequestAuthorization`.
3. `app-user` crea `UnlockRequest` local con estado `PENDING_LOCAL`.
4. Si hay internet, SyncEngine sube la solicitud a Supabase.
5. `app-admin` recibe evento Realtime.
6. Administrador aprueba, rechaza o concede tiempo limitado.
7. Cambio se guarda en Supabase con version, autor y timestamp.
8. `app-user` recibe Realtime o lo recoge por WorkManager.
9. Cambio se aplica localmente.
10. PolicyEngine reevalua y desbloquea o mantiene bloqueo.

Cambio de reglas:

1. Administrador modifica regla, horario o limite.
2. `app-admin` valida formato, pero no decide efectos finales.
3. El cambio se guarda como `PolicyChange`.
4. Dispositivos reciben cambio incremental.
5. Cada dispositivo reconcilia y actualiza Room.
6. PolicyEngine usa la nueva politica en la siguiente evaluacion.

## 6. Flujo del PolicyEngine

Entradas:

- contexto de red: dominio, IP, puerto, protocolo estimado, app si Android lo permite;
- contexto de app activa: packageName, timestamp, ventana, categoria local;
- contexto temporal: horario, dia, zona horaria, calendario escolar/familiar;
- estado de limites: uso diario, sesiones, cooldowns;
- politica local: allowlist, blocklist, categorias, reglas por dispositivo;
- estado de solicitudes: aprobadas, expiradas, pendientes;
- estado del sistema: VPN activa, AccessibilityService activo, integridad, sync.

Salidas:

- `Allow`
- `Block`
- `Warn`
- `RequestAuthorization`
- `GrantExtraTime`
- `RecordEvent`
- `SyncStateChanged`
- `HealthWarning`

Regla arquitectonica: VPN, AccessibilityService, WorkManager, Supabase y UI no contienen reglas de negocio. Solo construyen un `PolicyContext`, invocan PolicyEngine y aplican una `PolicyDecision`.

Prioridad de decisiones:

1. seguridad e integridad critica;
2. reglas explicitas del administrador;
3. aprobaciones temporales vigentes;
4. horarios;
5. limites diarios;
6. categorias;
7. configuracion por defecto.

## 7. Modelo de datos

Entidades principales:

- `UserProfile`: usuario protegido, edad/rango, configuracion base.
- `AdminProfile`: administrador, rol, permisos.
- `Device`: id local, nombre, plataforma, version de app, ultimo estado.
- `Policy`: version activa, dispositivo, propietario, revision.
- `Rule`: tipo, alcance, accion, prioridad, vigencia.
- `AppRule`: packageName, accion, limite, horario.
- `DomainRule`: dominio, categoria, accion, SafeSearch requerido.
- `Schedule`: ventanas permitidas/bloqueadas.
- `DailyLimit`: limite por app/categoria/global.
- `UsageSession`: app, inicio, fin, duracion, fuente.
- `NetworkEvent`: dominio/IP/protocolo estimado, decision, timestamp.
- `AccessibilityEventRecord`: app activa, decision, duracion.
- `UnlockRequest`: recurso, motivo, estado, expiracion.
- `ExtraTimeGrant`: alcance, minutos, validez, administrador.
- `SyncCursor`: tabla, ultima version remota, ultimo sync.
- `OutboxOperation`: cambios locales pendientes de subir.
- `SystemHealthSnapshot`: VPN, AccessibilityService, sync, integridad, DB.
- `IntegrityVerdict`: resultado Play Integrity, fecha, estado.

Campos transversales:

- `id`: UUID.
- `deviceId`.
- `createdAt`, `updatedAt`.
- `version`: contador monotono por entidad o revision logica.
- `deletedAt`: borrado logico para sync incremental.
- `source`: local, admin, server.

Modelo recomendado de conflictos:

- Reglas administrativas ganan sobre cambios locales.
- Solicitudes del usuario son append-only.
- Eventos son append-only y se compactan localmente.
- Limites y uso se calculan localmente y se sincronizan como resumen.
- Si dos administradores editan la misma regla, gana mayor `version`; si hay empate, gana `updatedAt` del servidor.

## 8. Arquitectura de sincronizacion

Componentes:

- `RealtimeListener`: escucha cambios de Supabase Realtime.
- `SyncWorker`: respaldo con WorkManager.
- `OutboxProcessor`: sube operaciones locales.
- `ConflictResolver`: aplica reglas deterministas.
- `SyncRepository`: expone estado via StateFlow.

Estrategia:

- Offline-first con outbox local.
- Pull incremental por `updatedAt/version` y `deviceId/familyId`.
- Push de operaciones idempotentes.
- Realtime para baja latencia, WorkManager para confiabilidad.
- Sync inicial completa, luego incremental.
- Backoff exponencial ante fallos.
- Sync de eventos historicos con compactacion para reducir costo.

Supabase:

- Usar plan gratuito al inicio.
- Tablas con Row Level Security.
- Cada dispositivo solo lee politicas de su familia/grupo.
- Los eventos voluminosos se conservan localmente y se suben resumidos.
- Realtime solo en tablas criticas: reglas, solicitudes, aprobaciones, estado del dispositivo.

## 9. Estrategia offline

El dispositivo debe seguir funcionando sin internet durante varios dias:

- La politica activa siempre vive en Room.
- La ultima politica valida se conserva aunque falle sync.
- Las aprobaciones temporales tienen expiracion local.
- Las solicitudes se guardan en outbox.
- Los eventos se guardan localmente y se compactan.
- El estado de salud muestra "con advertencias" si no hubo sync reciente, no "desprotegido" inmediatamente.

Politica por defecto:

- Si no existe politica inicial, permitir funciones basicas del dispositivo y bloquear solo reglas preconfiguradas por onboarding.
- Si existe politica previa, mantenerla hasta recibir una version nueva.
- Nunca cambiar a "permitir todo" por ausencia temporal de red.

## 10. Seguridad

Almacenamiento:

- Usar Room para datos estructurados.
- Recomendacion: SQLCipher para cifrado completo de base si el modelo de amenaza incluye extraccion fisica o backups no confiables.
- Android Keystore protege la clave maestra o material de envoltura.
- Para configuraciones pequenas, usar EncryptedSharedPreferences/DataStore cifrado; para politicas y eventos, Room cifrado es mas coherente.

Red:

- HTTPS obligatorio.
- Cert pinning solo si hay capacidad real de operar rotacion de certificados; de lo contrario puede dañar disponibilidad.
- Tokens de Supabase con alcance minimo y expiracion.
- Row Level Security en Supabase.

Integridad:

- Play Integrity API para señales de integridad cuando el canal de distribucion lo soporte bien.
- No usar Play Integrity como unica barrera; tratarla como señal de riesgo.
- En APK inicial fuera de Play, usar firma propia, checks locales ligeros y validacion de backend donde aplique.

Proteccion de claves:

- No guardar service role key de Supabase en el cliente.
- Usar claves anon/public con RLS, autenticacion y politicas estrictas.
- Separar secretos de build y configuracion publica.

Endurecimiento:

- R8/ProGuard activado.
- Minimizacion de logs.
- Sin registrar URLs completas sensibles salvo que sea necesario.
- Auditoria de permisos.
- Exported components cerrados por defecto.

## 11. Consumo esperado

Objetivo: menos de 3% diario en condiciones normales es alcanzable solo si el trafico VPN se procesa de forma simple y el AccessibilityService reacciona a eventos sin polling.

Medidas:

- PolicyEngine puro y rapido, sin I/O directo.
- Cache en memoria de reglas activas.
- Consultas Room fuera del camino caliente; snapshots precompilados de politica.
- VPN con reglas por dominio/IP y DNS cuando sea posible, evitando inspeccion profunda.
- AccessibilityService con filtro de eventos minimo.
- WorkManager con constraints de red.
- Realtime activo solo cuando sea util; reconexion con backoff.
- Eventos agregados por lotes.
- Sin wake locks manuales salvo caso puntual y medido.

Riesgos de consumo:

- VPN local siempre activa puede aumentar consumo si procesa demasiado trafico.
- Realtime permanente puede consumir mas en redes inestables.
- Registrar cada paquete/evento sin agregacion puede crecer en CPU, disco y bateria.

## 12. Riesgos tecnicos

- Usuario desactiva VPN o AccessibilityService.
- Fabricantes matan procesos en segundo plano agresivamente.
- Apps usan DNS cifrado propio o IPs directas.
- HTTPS impide inspeccion de contenido.
- Google Play puede rechazar o pedir justificacion fuerte por AccessibilityService.
- Supabase Free puede quedar corto si se sincronizan demasiados eventos.
- Politicas mal modeladas pueden generar conflictos dificiles.
- AccessibilityService puede percibirse como invasivo si la divulgacion no es excelente.
- VPN y AccessibilityService juntos elevan sensibilidad de privacidad.

Mitigaciones:

- Modulo de estado claro: protegido, con advertencias, desprotegido.
- Notificacion persistente de proteccion cuando corresponda.
- Onboarding transparente y consentimiento granular.
- Sync de eventos resumidos.
- Test matrix por fabricantes y versiones.
- Politicas simples y deterministas.

## 13. Limitaciones reales de Android

VPN:

- `VpnService` requiere consentimiento del usuario.
- Android permite una VPN activa por perfil; otra VPN puede desplazar esta.
- No hay garantia de ver contenido cifrado.
- DoH/DoT dentro de apps puede ocultar dominios.
- Per-app VPN completa es mas fuerte en contextos gestionados; en dispositivo normal se debe operar dentro de lo que `VpnService.Builder` permita.

SafeSearch:

- Implementacion robusta: aplicar DNS/rutas para mecanismos oficiales de SafeSearch cuando la resolucion pase por la VPN.
- Para Google Search, usar el metodo documentado por Google para bloquear SafeSearch a nivel de red cuando sea posible.
- No garantizar SafeSearch en todos los buscadores, apps embebidas o trafico cifrado propio.

AccessibilityService:

- El usuario debe habilitarlo manualmente.
- Android puede mostrar advertencias del sistema.
- No debe usarse para capturar datos sensibles innecesarios.
- No concede control absoluto del sistema.
- La automatizacion debe ser estrecha, comprensible y deterministica.

Segundo plano:

- Android 10 a Android 16 refuerzan limites de ejecucion en background.
- WorkManager es el camino recomendado para tareas diferibles y persistentes.
- Foreground services requieren justificacion, tipo adecuado y notificacion.

## 14. Compatibilidad con Google Play

Permisos y APIs sensibles:

- AccessibilityService: alto riesgo de revision. Requiere declaracion, divulgacion prominente y consentimiento. Si la app no es principalmente una herramienta para discapacidad, no debe declararse como `isAccessibilityTool=true`.
- VpnService: requiere proposito claro, privacidad transparente y no debe usarse para abusos de red, monetizacion opaca ni recoleccion excesiva.
- Query de apps instaladas: evitar `QUERY_ALL_PACKAGES` si no es imprescindible. Preferir eventos del AccessibilityService y allowlist declarada por usuario/admin.
- Notificaciones: pedir permiso en Android moderno solo cuando aporta valor claro.
- REQUEST_INSTALL_PACKAGES: evitar para la "tienda de aplicaciones permitidas"; mejor abrir enlaces externos o listar apps recomendadas sin instalarlas directamente.

Riesgos de aprobacion:

- App de control parental/comunitario con AccessibilityService puede ser aceptable, pero requiere explicacion impecable.
- Si se presenta como vigilancia general, automatizacion amplia o monitoreo oculto, el riesgo sube mucho.
- El uso de VPN mas AccessibilityService exige una politica de privacidad muy clara.

Alternativa oficial si Play rechaza AccessibilityService:

- Reducir funciones a VPN + uso declarado por el usuario + notificaciones.
- Para control fuerte familiar, evaluar integracion futura con plataformas de Family/Enterprise solo si se cumplen requisitos, sin asumir Device Owner.

## 15. Roadmap por fases

Fase 0: decisiones base

- Definir alcance exacto de filtrado.
- Definir politica de privacidad y datos minimos.
- Definir si se usara SQLCipher desde el inicio.
- Crear modelo de amenazas realista.

Fase 1: esqueleto offline

- Modulos Gradle.
- Dominio puro.
- PolicyEngine inicial.
- Room.
- Pantallas Compose basicas.
- Estado del sistema local.

Fase 2: app-user MVP

- Onboarding.
- VPN local basica.
- AccessibilityService basico.
- Bloqueo por app activa.
- Limites diarios locales.
- Solicitudes locales.
- Historial local.

Fase 3: app-admin MVP

- Login.
- Ver dispositivos.
- Editar reglas.
- Aprobar/rechazar solicitudes.
- Ver historial resumido.

Fase 4: sync

- Supabase schema con RLS.
- Outbox local.
- Realtime.
- WorkManager periodico.
- Resolucion de conflictos.

Fase 5: seguridad y hardening

- Keystore.
- SQLCipher o cifrado equivalente decidido.
- R8.
- Integridad.
- Auditoria de logs.
- Pruebas de manipulacion basicas.

Fase 6: rendimiento

- Medicion bateria.
- Perfilado CPU/memoria.
- Reduccion de eventos.
- Compactacion de historiales.
- Test en Android 10, 12, 14, 15 y 16.

Fase 7: compatibilidad Google Play

- Pantallas de divulgacion.
- Privacy policy.
- Permission Declaration Forms.
- Video de revision para AccessibilityService.
- Revision de Data Safety.

Fase 8: escalabilidad

- Multiples dispositivos.
- Roles de administracion.
- Politicas por grupos.
- Exportacion de historial.
- Panel web opcional si Supabase Free sigue siendo suficiente.

## Cambio recomendado a la arquitectura original

Recomiendo agregar `feature-status`, `feature-requests` y eventualmente `core-sync`. La propuesta original es buena, pero el estado del sistema y la sincronizacion son suficientemente importantes como para aislarlos antes de que crezcan dentro de `app-user` o `core-data`.

No recomiendo agregar un modulo de "interceptacion HTTPS", "anti-desinstalacion", "anti-desactivacion" o "control absoluto", porque en Android normal no hay garantias oficiales para eso sin Device Owner, MDM o root.

## Fuentes oficiales consultadas

- Android `VpnService`: https://developer.android.com/reference/android/net/VpnService
- Android `AccessibilityService`: https://developer.android.com/reference/android/accessibilityservice/AccessibilityService
- Google Play, uso de AccessibilityService API: https://support.google.com/googleplay/android-developer/answer/10964491
- Android WorkManager, definicion de trabajos: https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work
- Android 16 behavior changes: https://developer.android.com/about/versions/16/behavior-changes-all
- Play Integrity API: https://developer.android.com/google/play/integrity/overview
- Google SafeSearch para redes administradas: https://support.google.com/websearch/answer/186669
- Supabase Realtime: https://supabase.com/docs/guides/realtime

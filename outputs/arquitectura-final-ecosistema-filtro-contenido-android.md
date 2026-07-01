# Arquitectura final del ecosistema de filtro de contenido comunitario para Android

Fecha: 2026-06-29  
Estado: revision final de arquitectura, sin implementacion

## 1. Decision ejecutiva

La arquitectura base es correcta, pero necesita cinco ajustes antes de implementar:

- Agregar `core-update` para actualizaciones por APK firmado sin depender de Google Play.
- Agregar `core-license` para licencias, activacion, renovacion, vencimiento y multiples dispositivos.
- Convertir la sincronizacion con Supabase en la base real de backup/restore de usuario, no en un agregado separado.
- Agregar `core-telemetry` para diagnostico tecnico, sin analitica de marketing.
- Simplificar cifrado local: empezar con Android Keystore, cifrado selectivo y almacenamiento minimo; dejar SQLCipher como opcion configurable para una fase posterior o despliegues de mayor riesgo.

La arquitectura final sigue siendo simple. No se agregan modulos para control absoluto, inspeccion HTTPS, anti-desinstalacion o instalacion silenciosa, porque Android no lo garantiza en dispositivos normales.

## 2. Arquitectura final recomendada

### Apps

- `app-user`: dispositivo protegido.
- `app-admin`: administracion desde Android.

### Core

- `core-domain`: entidades, contratos, errores de dominio y casos de uso puros.
- `core-policy`: PolicyEngine y evaluacion de reglas.
- `core-data`: repositorios y mappers.
- `core-database`: Room, DAOs, migraciones, cache local.
- `core-network`: Supabase, APIs, DTOs, conectividad.
- `core-sync`: outbox, pull incremental, Realtime, reconciliacion y restore.
- `core-security`: Keystore, cifrado, integridad, firmas y utilidades seguras.
- `core-license`: estado de licencia, activacion, renovacion, gracia, limites por cuenta.
- `core-update`: deteccion, descarga, verificacion e instalacion asistida de APKs.
- `core-telemetry`: crashes, rendimiento, bateria, errores tecnicos y sync diagnostics.
- `core-ui`: componentes Compose compartidos, tema y patrones visuales.

### Features de usuario

- `feature-onboarding`: activacion inicial, consentimiento y permisos.
- `feature-vpn`: integracion oficial con VpnService.
- `feature-accessibility`: integracion oficial con AccessibilityService.
- `feature-blocking`: pantalla de bloqueo, advertencias y experiencia de usuario.
- `feature-requests`: solicitudes de desbloqueo y tiempo adicional.
- `feature-status`: protegido, con advertencias o desprotegido.
- `feature-store`: tienda/lista de apps permitidas o recomendadas, sin instalar apps automaticamente.
- `feature-update`: pantallas y avisos de actualizacion.
- `feature-license`: pantallas de licencia, vencimiento y renovacion.

### Features de administrador

- `feature-admin-rules`: reglas, horarios y limites.
- `feature-admin-requests`: aprobaciones y rechazos.
- `feature-admin-devices`: dispositivos asociados y activaciones.
- `feature-admin-history`: historial tecnico y eventos resumidos.
- `feature-admin-license`: estado de cuenta, cupos y renovaciones.
- `feature-status`: estado de dispositivos.

### Modulos eliminados o evitados

- No crear `core-backup` separado en la primera version. Backup/restore debe ser una capacidad de `core-sync` porque usa los mismos datos, cursor, versionado, RLS y reconciliacion.
- No crear `core-payment` todavia. `core-license` debe exponer contratos para pagos futuros sin acoplarse a Stripe, Mercado Pago, Google Play Billing u otro proveedor.
- No crear modulo de analitica de producto. Solo telemetria tecnica.
- No crear backend separado por app. El backend debe ser unico, multi-cliente y preparado para Android, iPhone y Web.

## 3. Diagrama textual de dependencias

- `app-user` depende de features de usuario, `core-ui`, `core-domain`, `core-data`, `core-sync`, `core-security`, `core-license`, `core-update` y `core-telemetry`.
- `app-admin` depende de features de administrador, `core-ui`, `core-domain`, `core-data`, `core-sync`, `core-security`, `core-license` y `core-telemetry`.
- `feature-vpn` y `feature-accessibility` dependen de `core-policy`, `core-domain` y contratos de `core-data`.
- `core-policy` depende solo de `core-domain`.
- `core-data` depende de `core-database`, `core-network`, `core-security` y `core-domain`.
- `core-sync` depende de `core-data`, `core-network`, `core-database`, `core-security` y `core-domain`.
- `core-update` depende de `core-network`, `core-security`, `core-database` y APIs oficiales de instalacion Android.
- `core-license` depende de `core-domain`, `core-data`, `core-sync` y `core-security`.
- `core-telemetry` no puede depender de features. Las features solo emiten eventos tecnicos ya sanitizados.

## 4. PolicyEngine como centro del sistema

El PolicyEngine sigue siendo el unico punto de decision de negocio para bloqueo, permisos, tiempos, solicitudes y estado funcional.

Entradas nuevas:

- `LicenseState`: activa, por vencer, vencida, en gracia, suspendida.
- `UpdateState`: version actual, version minima soportada, update recomendado u obligatorio.
- `BackupRestoreState`: dispositivo nuevo, restauracion pendiente, restauracion completa.
- `HealthState`: VPN, AccessibilityService, sync, integridad, base local, licencia.

Decisiones nuevas:

- `RequireUpdate`: la version instalada ya no es compatible.
- `WarnUpdateAvailable`: hay version recomendada.
- `DegradeForExpiredLicense`: aplicar modo limitado tras vencimiento y fin de gracia.
- `AllowGracePeriod`: permitir funcionamiento temporal.
- `RequireDeviceActivation`: dispositivo no activado.

Regla importante: `core-update`, `core-license` y `core-sync` calculan estados tecnicos, pero no deciden consecuencias de negocio. El PolicyEngine decide como afecta eso al usuario.

## 5. Sistema de actualizacion automatica por APK

### Objetivo

Permitir actualizaciones fuera de Google Play sin instalar silenciosamente y sin usar APIs privadas.

Android permite iniciar procesos oficiales de instalacion, pero en dispositivos normales el usuario debe autorizar la instalacion. No se debe prometer instalacion invisible.

### Modulo

Agregar `core-update`.

Responsabilidades:

- Consultar un manifiesto remoto de version.
- Comparar version instalada contra version disponible y version minima soportada.
- Descargar APK firmado solo cuando haya red adecuada y bateria razonable.
- Verificar integridad antes de mostrar la instalacion.
- Verificar que el APK corresponde al package esperado y al certificado de firma esperado.
- Guardar estado de descarga, version, hash, errores y reintentos.
- Notificar al usuario y abrir el flujo oficial de instalacion.
- Reportar estado tecnico a `core-telemetry`.

### Manifiesto remoto

Debe vivir en Supabase Storage, Supabase Database o un hosting estatico gratuito. Debe incluir:

- app: `app-user` o `app-admin`;
- channel: stable, beta o internal;
- versionName;
- versionCode;
- minSupportedVersionCode;
- apkUrl;
- apkSha256;
- signingCertificateSha256;
- sizeBytes;
- releaseNotes;
- mandatory;
- publishedAt;
- rolloutPercent opcional.

### Flujo

1. WorkManager consulta el manifiesto de version con baja frecuencia.
2. `core-update` valida si aplica a esta app, canal y arquitectura.
3. Si hay version nueva, descarga el APK mediante HTTPS.
4. Verifica SHA-256 del archivo.
5. Verifica package name y certificado de firma esperado.
6. Si la version es opcional, muestra aviso no intrusivo.
7. Si la version es obligatoria, el PolicyEngine puede limitar funciones no criticas hasta actualizar.
8. La app abre el instalador oficial de Android.
9. El usuario confirma la instalacion.
10. Tras reinicio de app, `core-update` registra version actualizada.

### APIs y permisos

- Usar `PackageInstaller` o intent oficial de instalacion segun version y experiencia deseada.
- En Android 8+, el usuario puede tener que permitir "instalar apps desconocidas" para esta fuente mediante la pantalla oficial `ACTION_MANAGE_UNKNOWN_APP_SOURCES`.
- Si la app declara `REQUEST_INSTALL_PACKAGES`, en Google Play existe riesgo de rechazo salvo que la instalacion de paquetes sea una funcion principal permitida. Para distribucion por APK es util; para publicacion en Play conviene tener una variante sin self-update por APK y usar Play In-App Updates.

### Seguridad

- Nunca instalar desde URL arbitraria.
- Nunca aceptar APK sin hash.
- Mantener pin logico de package name y certificado.
- Rotacion de certificado solo mediante una lista de certificados permitidos firmada o publicada desde backend confiable.
- Descargar en almacenamiento interno privado.
- Borrar APKs obsoletos.

### Costos

Supabase Storage puede servir al inicio. Si hay miles de usuarios y APKs grandes, el ancho de banda puede volverse el primer costo relevante. Para escalar barato, mover APKs a GitHub Releases, Cloudflare R2, Backblaze B2 o CDN con buen free tier, manteniendo el manifiesto controlado.

## 6. Sistema de licencias

### Modulo

Agregar `core-license`.

No procesa pagos en la primera version. Modela derechos de uso.

Responsabilidades:

- Activar dispositivo.
- Asociar dispositivo a cuenta/familia/comunidad.
- Validar licencia anual.
- Manejar renovacion.
- Manejar vencimiento.
- Manejar periodo de gracia.
- Limitar cantidad de dispositivos por licencia.
- Exponer estado offline.
- Preparar integracion futura con multiples proveedores de pago.

### Modelo conceptual

- `Account`: entidad propietaria de licencias y dispositivos.
- `License`: plan, fecha de inicio, fecha de fin, estado.
- `Entitlement`: capacidades habilitadas por la licencia.
- `DeviceActivation`: dispositivo activado, fecha, estado, huella no sensible.
- `GracePeriod`: ventana de funcionamiento tras falla de renovacion o falta de conexion.
- `PaymentProviderRef`: referencia externa futura, sin logica de pago en cliente.

### Estados de licencia

- activa;
- por vencer;
- vencida;
- en gracia;
- suspendida;
- pendiente de activacion;
- dispositivo excede cupo.

### Offline

La app debe conservar el ultimo estado firmado o validado de licencia. Si no hay internet:

- licencia activa sigue funcionando;
- si llega fecha de vencimiento, entra en gracia;
- al terminar la gracia, el PolicyEngine limita funciones de administracion y cambios remotos, pero no debe dejar el dispositivo infantil en un estado inseguro;
- las reglas locales existentes siguen aplicandose.

### Pagos futuros

`core-license` no debe saber si el pago viene de Stripe, Mercado Pago, transferencia, voucher comunitario, Google Play Billing o App Store. El backend recibe eventos de pago y actualiza licencias. Las apps solo leen entitlements.

## 7. Backup y restore con Supabase

### Decision

No crear un sistema paralelo de backup. La sincronizacion offline-first sera tambien el mecanismo de restauracion de usuario.

### Que se restaura

- reglas;
- horarios;
- limites;
- configuraciones;
- solicitudes;
- dispositivos asociados;
- aprobaciones vigentes;
- perfiles;
- estado de licencia;
- historial resumido necesario.

### Que no se restaura por defecto

- logs tecnicos voluminosos;
- capturas o datos sensibles innecesarios;
- eventos de red detallados antiguos;
- cache derivada;
- estado local efimero de VPN o AccessibilityService.

### Flujo de reinstalacion o cambio de telefono

1. Usuario instala la app.
2. Inicia sesion o usa codigo de vinculacion.
3. Backend valida cuenta, licencia y cupo de dispositivos.
4. Se crea o reactiva `DeviceActivation`.
5. `core-sync` descarga snapshot inicial de politica.
6. Se descargan cambios incrementales posteriores al snapshot.
7. Room queda reconstruido.
8. Onboarding pide activar VPN y AccessibilityService en el nuevo dispositivo.
9. El dispositivo anterior queda activo, pausado o revocado segun decision del administrador.

### Backend

Supabase debe guardar la verdad canonica de politicas y asociaciones. Las copias locales son replicas operativas.

Para evitar costos:

- no subir eventos detallados sin limite;
- compactar historiales;
- usar snapshots por familia/dispositivo;
- mantener tablas append-only solo donde aporten auditoria real;
- aplicar RLS estricta por cuenta/familia.

### Backup del proyecto Supabase

Esto es distinto del restore de usuario. En plan gratuito, Supabase recomienda exportaciones regulares con CLI. Para produccion grande, los backups administrados y PITR pueden requerir planes pagos. Debe asumirse desde el principio que el restore de usuario vive en las tablas normales, y el backup del proyecto es una tarea operativa del desarrollador.

## 8. Preparacion para Android, iPhone y Panel Web

La primera implementacion es Android, pero el backend no debe codificar conceptos exclusivos de Android.

Reglas:

- Usar entidades neutrales: `Device`, `Platform`, `Policy`, `Rule`, `Entitlement`.
- Guardar `platform` como Android, iOS o Web, pero no mezclar packageName con identificadores universales.
- En reglas de apps, separar `AndroidAppTarget` de futuros `IosAppTarget`.
- Las reglas de dominio, horarios, limites y solicitudes deben ser comunes.
- El backend no debe depender de Compose, Room, Kotlin ni Android-specific enums.
- Las decisiones del PolicyEngine Android deben poder tener un equivalente conceptual en otras plataformas, aunque la aplicacion de la decision sea distinta.

iPhone tendra limitaciones diferentes y probablemente requerira APIs de Apple como Screen Time/Family Controls si el caso de uso encaja. No se debe prometer paridad total.

## 9. Panel Web y backend compartido

Conviene separar conceptualmente el backend desde ahora, pero sin crear infraestructura costosa.

Decision:

- Backend unico: Supabase Postgres, Auth, RLS, Realtime, Storage y Edge Functions cuando sean necesarias.
- Clientes multiples: `app-user`, `app-admin`, futuro panel web y futura app iOS.
- Contratos compartidos: tablas, vistas, RPCs y reglas de autorizacion.

El Panel Web futuro debe administrar exactamente las mismas reglas y dispositivos que `app-admin`. Por eso la app Android Administrador no debe tener logica exclusiva que el backend no pueda representar.

Para mantener simplicidad:

- no crear un backend custom separado en fase 1;
- usar Supabase directamente con RLS;
- agregar Edge Functions solo para operaciones que requieren autoridad de servidor, como activar licencia, validar pago, firmar manifiestos de update o emitir codigos de vinculacion.

## 10. SQLCipher: decision final

### Analisis

SQLCipher aporta cifrado completo de base de datos. Es valioso si el atacante puede extraer archivos locales del dispositivo o si se quiere proteger de backups no confiables. Pero aumenta complejidad:

- dependencia nativa;
- impacto en tamano y builds;
- passphrase lifecycle;
- migraciones mas delicadas;
- posibles problemas por ABI/dispositivos;
- mas superficie de test;
- recuperacion mas dificil ante perdida de clave.

Android ya ofrece sandbox por app, cifrado de almacenamiento del sistema y Android Keystore para claves no exportables. Para este proyecto, la mayor parte del riesgo real esta en permisos sensibles, backend, RLS, logs y sincronizacion, no en un atacante fisico avanzado.

### Decision recomendada

Fase 1: no usar SQLCipher por defecto.

Usar:

- Android Keystore para claves y tokens;
- cifrado selectivo para secretos y blobs sensibles;
- Room normal para politicas operativas;
- minimizacion de datos;
- logs tecnicos sanitizados;
- backups automaticos de Android deshabilitados o cuidadosamente controlados para datos sensibles.

Mantener una abstraccion `DatabaseEncryptionStrategy` en arquitectura, sin implementarla todavia, para poder activar SQLCipher en una variante futura si el modelo de amenaza lo exige.

Justificacion: mejora mantenibilidad y reduce riesgo de implementacion. SQLCipher queda como opcion profesional, no como complejidad inicial obligatoria.

## 11. Telemetria tecnica

### Modulo

Agregar `core-telemetry`.

Alcance permitido:

- Crash Reporting.
- ANR y errores no fatales.
- rendimiento de arranque y pantallas criticas.
- consumo aproximado de bateria asociado a VPN, sync y servicios.
- errores de sincronizacion.
- errores de actualizacion.
- estado de licencia tecnico.
- fallos de integridad.

No incluir:

- analitica de marketing;
- tracking publicitario;
- perfiles comerciales;
- funnels de conversion;
- captura de contenido visitado;
- URLs completas sensibles.

### Estrategia de costo cero

Fase 1:

- logs tecnicos locales con rotacion;
- exportacion manual de diagnostico por el usuario/admin;
- tabla Supabase de errores resumidos y muestreados;
- metricas agregadas, no eventos infinitos.

Fase 2:

- Sentry, Firebase Crashlytics u opcion self-hosted/open-source solo si el costo y la privacidad encajan.

Para miles de usuarios, la telemetria puede generar costos si se suben demasiados eventos. Debe haber muestreo, rate limits y agregacion local desde el dia uno.

## 12. Consumo economico

La arquitectura puede operar casi sin costo en desarrollo y comunidades pequenas si se controla el volumen.

Gratis o bajo costo:

- Supabase Free para Auth, Postgres, Realtime y Storage inicial.
- GitHub Releases o hosting estatico para APKs.
- WorkManager, Room, Compose, Hilt, Coroutines y StateFlow.
- Logs locales y reportes tecnicos muestreados.
- R8 y herramientas oficiales.

Costos que pueden crecer:

- Realtime: conexiones concurrentes y mensajes por segundo.
- Storage/CDN: descarga de APKs.
- Base de datos: eventos historicos y logs voluminosos.
- Backups administrados y PITR de Supabase.
- Telemetria SaaS.
- Edge Functions si se usan intensivamente.

Medidas de control:

- sincronizar reglas y solicitudes en Realtime, no todo el historial;
- compactar eventos;
- usar snapshots;
- descargar APK solo bajo demanda o rollout;
- mantener logs locales;
- activar telemetria remota con muestreo;
- separar ambientes dev/staging/prod solo cuando haga falta.

## 13. Distribucion por APK firmado

### Estrategia inicial

- Firmar APKs con clave protegida offline.
- Publicar manifiesto de version.
- Publicar APK en Storage/CDN.
- La app detecta, descarga, verifica y abre instalador oficial.
- El usuario confirma instalacion.

### Estrategia futura compatible con Google Play

Mantener dos canales:

- canal APK directo: `core-update` habilitado;
- canal Google Play: `core-update` por APK deshabilitado y reemplazado por Play In-App Updates.

Esto reduce riesgo de rechazo en Play por `REQUEST_INSTALL_PACKAGES`.

### Limite real

Sin Device Owner ni tienda privilegiada, no hay actualizacion silenciosa garantizada. El sistema puede insistir, explicar, bloquear funciones no criticas o marcar estado "con advertencias", pero no debe simular control total.

## 14. Seguridad final

Prioridades:

- RLS correcta en Supabase.
- No guardar service role key en clientes.
- Keystore para tokens y claves.
- HTTPS obligatorio.
- Politica de privacidad clara.
- Minimizacion de datos.
- Update con hash y firma.
- Licencia validada por backend.
- Play Integrity como senal, no como unica defensa.
- R8 activo.
- Componentes Android no exportados salvo necesidad.
- Logs sanitizados.

No hacer en fase 1:

- certificate pinning rigido sin plan de rotacion;
- anti-debug excesivo;
- ofuscacion compleja de reglas;
- deteccion agresiva de root como bloqueo absoluto;
- inspeccion HTTPS.

## 15. Rendimiento y bateria

Objetivo de menos de 3% diario sigue siendo razonable si:

- VPN procesa reglas simples y cacheadas.
- AccessibilityService escucha solo eventos necesarios.
- PolicyEngine usa snapshots en memoria.
- Room no se consulta en cada paquete/evento.
- Realtime se limita a tablas criticas.
- WorkManager respeta constraints.
- Telemetria y eventos se agregan localmente.
- Update checks son poco frecuentes.
- License checks remotos no se hacen en cada decision.

`core-update`, `core-license` y `core-telemetry` no deben despertar el dispositivo frecuentemente. Deben integrarse al mismo scheduler de mantenimiento.

## 16. Compatibilidad Android 10 a Android 16

- Android 10: buena base para VpnService, AccessibilityService, WorkManager y PackageInstaller, con restricciones de background ya relevantes.
- Android 11 a 13: mayor sensibilidad a permisos, visibilidad de paquetes y notificaciones.
- Android 14 a 16: mas restricciones en servicios en primer plano, seguridad de intents, permisos y comportamiento en segundo plano.
- En todas las versiones: instalacion de APK requiere accion del usuario y confianza en la fuente.

La arquitectura debe tener `PlatformCapabilities` para consultar capacidades reales por version y fabricante.

## 17. Google Play

Riesgos principales:

- AccessibilityService requiere justificacion fuerte, divulgacion prominente y uso limitado.
- VpnService requiere proposito claro y politica de privacidad estricta.
- `REQUEST_INSTALL_PACKAGES` puede ser rechazado si la app publicada en Play no tiene instalacion de paquetes como core purpose permitido.
- Query amplia de apps instaladas debe evitarse.

Decision:

- Version APK directa: puede incluir self-update.
- Version Play: quitar self-update por APK, usar mecanismo oficial de Play y revisar permisos declarados.

## 18. Modelo de datos final

Entidades principales:

- `Account`
- `AdminProfile`
- `ProtectedUserProfile`
- `Device`
- `DeviceActivation`
- `License`
- `Entitlement`
- `Policy`
- `PolicyRevision`
- `Rule`
- `AppRule`
- `DomainRule`
- `Schedule`
- `DailyLimit`
- `UsageSession`
- `UnlockRequest`
- `ExtraTimeGrant`
- `SystemHealthSnapshot`
- `SyncCursor`
- `OutboxOperation`
- `UpdateManifestRecord`
- `UpdateAttempt`
- `IntegrityVerdict`
- `TechnicalDiagnostic`

Reglas:

- Politicas y licencias son canonicas en Supabase.
- Room mantiene replica local.
- Eventos detallados son locales por defecto.
- Historial remoto debe ser resumido.
- Borrado logico para sincronizacion incremental.
- Versionado monotono por entidad critica.

## 19. Roadmap final

Fase 0: fundamentos

- Alcance exacto.
- Modelo de amenazas.
- Politica de privacidad.
- Esquema Supabase inicial.
- Decision formal de no SQLCipher por defecto.

Fase 1: arquitectura local

- Modulos core.
- Dominio puro.
- PolicyEngine.
- Room.
- Estado del sistema.
- Telemetria local minima.

Fase 2: app-user MVP

- Onboarding.
- VPN.
- AccessibilityService.
- Bloqueo por app.
- Limites diarios.
- Solicitudes locales.

Fase 3: app-admin MVP

- Administrar reglas.
- Aprobar solicitudes.
- Ver dispositivos.
- Ver estado.

Fase 4: sync, backup y restore

- Supabase RLS.
- Outbox.
- Realtime.
- WorkManager.
- Restore por cuenta/dispositivo.
- Compactacion de eventos.

Fase 5: licencias

- Activacion de dispositivo.
- Licencia anual.
- Gracia offline.
- Cupos por cuenta.
- Contratos para pagos futuros.

Fase 6: actualizaciones

- Manifiesto remoto.
- Descarga segura.
- Verificacion de hash y firma.
- Instalacion asistida.
- Canal APK directo.

Fase 7: seguridad y hardening

- Keystore.
- Integridad.
- R8.
- Auditoria de permisos.
- Revision de logs.
- Pruebas de manipulacion.

Fase 8: rendimiento y bateria

- Perfilado.
- Optimizacion VPN.
- Optimizacion AccessibilityService.
- Muestreo de telemetria.
- Pruebas Android 10 a 16.

Fase 9: preparacion Play y plataforma web

- Variante Google Play sin APK self-update.
- Formularios de permisos.
- Data Safety.
- Contratos backend para panel web.

Fase 10: escalabilidad

- Multiples administradores.
- Miles de dispositivos.
- Roles.
- Auditoria resumida.
- Panel web.
- Evaluacion de plan pago solo si el uso real lo exige.

## 20. Conclusion final

La arquitectura final queda preparada para crecer sin rehacer los cimientos. El cambio mas importante es separar capacidades transversales reales (`core-sync`, `core-license`, `core-update`, `core-telemetry`) y evitar modulos que parezcan poderosos pero no esten garantizados por Android.

La implementacion debe empezar por dominio, PolicyEngine, persistencia local y estado del sistema. Actualizaciones, licencias y telemetria ya quedan disenadas, pero se implementan por fases para no contaminar el MVP con complejidad prematura.

## Fuentes oficiales y tecnicas consultadas

- Android `VpnService`: https://developer.android.com/reference/android/net/VpnService
- Android `AccessibilityService`: https://developer.android.com/reference/android/accessibilityservice/AccessibilityService
- Android `PackageInstaller`: https://developer.android.com/reference/android/content/pm/PackageInstaller
- Android `REQUEST_INSTALL_PACKAGES`: https://developer.android.com/reference/android/Manifest.permission#REQUEST_INSTALL_PACKAGES
- Android `ACTION_MANAGE_UNKNOWN_APP_SOURCES`: https://developer.android.com/reference/android/provider/Settings#ACTION_MANAGE_UNKNOWN_APP_SOURCES
- Google Play policy sobre `REQUEST_INSTALL_PACKAGES`: https://support.google.com/googleplay/android-developer/answer/12085295
- Android Keystore: https://developer.android.com/privacy-and-security/keystore
- WorkManager: https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work
- Play Integrity API: https://developer.android.com/google/play/integrity/overview
- Supabase Realtime limits: https://supabase.com/docs/guides/realtime/limits
- Supabase backups: https://supabase.com/docs/guides/platform/backups

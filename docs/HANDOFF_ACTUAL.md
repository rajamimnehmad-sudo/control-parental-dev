# HANDOFF ACTUAL - Glosh y DAG Browser

Fecha de corte: 2026-07-30

Tomar este archivo como contexto oficial. No reconstruir el estado desde
documentos o commits históricos.

## Fuentes oficiales

- Este archivo contiene la verdad técnica vigente.
- `docs/BACKLOG_PRODUCTO.md` contiene ideas, prioridades y tickets.
- `docs/BASELINES.md` contiene puntos de recuperación.
- El historial Git conserva evidencia antigua, pero no define el runtime actual.

## Carpeta y seguridad

Trabajar únicamente en:

```text
/Users/yejielnehmad/Developer/content-filter
```

- Usar solamente Supabase DEV `syeycayasyufedwoprea`.
- No tocar Production.
- No borrar datos sin confirmación específica.
- No incluir secretos ni Service Role Key en Android o Git.
- App Usuario y App Admin versionan y publican de forma independiente.
- El flujo vigente es local: cada lote terminado se integra y confirma en
  `main`; no hacer push, PR ni publicación remota sin el `OK` explícito del
  usuario.

## Estado publicado de Glosh

Última publicación pública verificada:

```text
App Usuario versionCode 279
App Admin versionCode 275
versionName 1.0.1-dev
```

App Usuario `versionCode 281` fue además un candidato físico para validar el
puente hacia el navegador separado; no reemplazó el manifiesto público
documentado arriba.

## Lote local 5 de 6: Ajustes por pantallas

- El commit local `cf88a28` convierte Ajustes de Usuario y Admin en índices
  simples. Cada fila abre un único destino y Atrás vuelve primero al índice.
- Usuario separa `Protección y activación`, `Actualizaciones e instalaciones`,
  `Ayuda` y `Tu opinión`. El código de emergencia queda dentro de Protección y
  no se expone en la lista. Actualizaciones conserva App Usuario, App Admin y
  DAG sin duplicar esos controles en la raíz.
- Admin separa `Cuenta y comunidad`, `Contacto adulto`, `Panel administrador`,
  `Actualizaciones`, `Ayuda`, `Tu opinión` y `Administrador de este teléfono`.
  Cambiar administrador sale de Actualizaciones y mantiene su confirmación
  dentro de la sección sensible final.
- No cambiaron autoridades, reglas, activación, repositorios, sincronización,
  Supabase ni barreras antimanipulación.
- Validación local correcta: Ktlint, 42 unitarios de Usuario, 57 unitarios de
  Admin y ambos APK DEV. Admin queda en `versionCode 286`; su APK mide
  28.801.620 bytes y tiene SHA-256
  `93967cde19a83094bb4558302d4c131799b9161ff97ef0570eeff74ee48e57a8`.
- El build de Usuario observado declara `versionCode 302`, pero ese incremento
  y el APK de 28.518.877 bytes incluyen cambios locales preexistentes del lote
  GloshIA todavía no integrados. El commit de Ajustes no tomó propiedad de esos
  archivos; el artefacto canónico de Usuario se documentará al cerrar el lote
  6.
- Por pedido del usuario no se instaló ningún APK. Faltan el recorrido físico
  de Atrás, rotación, scroll, texto grande y lector de pantalla cuando terminen
  los seis lotes.

## Runtime único de navegador

- El único navegador del proyecto es `app-dag-browser`.
- Es una APK GeckoView separada, fail-closed y conectada con Glosh mediante un
  puente DEV explícito.
- No existe fallback hacia implementaciones retiradas.
- La instalación física vigente en el SM-S908E es DAG 36. El candidato local
  declara `versionCode 36`, `versionName 0.26.0-dev`; no fue publicado.
- V21 cerró el primer lote de estabilidad: `Analizando` se desmonta cuando la
  presentación queda resuelta, los corazones de Favoritos conservan una
  representación funcional segura aunque su sprite permanezca bloqueado y
  Atrás sólo sale de DAG desde Home. Build, Lint, 71 unitarios e instalación
  física aprobados en SM-A235M.
- V22 implementa el segundo lote: captura la página segura antes de cambiar de
  pestaña, liga cada miniatura a la revisión exacta del documento y descarta
  respuestas tardías. Contraseñas, pagos y CAPTCHA mantienen tarjeta neutra;
  las capturas quedan sólo en memoria y se invalidan al navegar o pasar la UI a
  segundo plano.
- V23 implementa el tercer lote: runtime y persistencia admiten hasta 50
  pestañas, el organizador ocupa la pantalla y agrega `Cerrar todo` con
  confirmación. Las miniaturas bajan a `200 x 300`, con presupuesto máximo
  teórico de 12 MB para 50 capturas; al alcanzar el techo DAG abre el
  organizador para cerrar una.
- V24 implementa el cuarto lote, primera etapa de descargas seguras. Sólo acepta
  PDF de hasta 20 MB iniciado por gesto desde una página HTTPS ya visible;
  muestra nombre, dominio, MIME y tamaño antes de confirmar. Requiere
  coincidencia entre extensión, MIME y firma real, rechaza tamaño desconocido,
  respuesta insegura, descarga paralela y redirección entre orígenes. Guarda
  mediante archivo parcial dentro del espacio privado, permite cancelar,
  reintentar, abrir con URI temporal de solo lectura y administrar/borrar los
  PDF desde el menú `Descargas`. APK, ejecutables, scripts, comprimidos y demás
  formatos continúan bloqueados. La política Admin y nuevos formatos quedan
  fuera de esta primera etapa.
- V25 corrige dos gates físicos del lote integrado. Registra el callback moderno
  de Atrás requerido por Android 13 o posterior y distingue Home real de un URL
  interno desactualizado mientras la página protegida continúa visible. En el
  SM-S908E con Android 16, el primer Atrás desde una página volvió a Home y el
  segundo cerró DAG. La captura de pestañas espera la elegibilidad del documento,
  reintenta una vez y conserva sólo capturas efímeras de páginas seguras: una
  página neutra mostró miniatura real y Frávega mantuvo tarjeta neutra al pasar a
  estado restringido/CAPTCHA. Formato, 92 unitarios, build y lint correctos.
- El modelo y su umbral `0.4` no cambiaron: el ajuste siguiente debe incorporar
  ejemplos actuales de falsos permisos y falsos filtros, no bajar el umbral a
  ciegas.
- La APK físicamente validada V21 mide 121.111.194 bytes y tiene SHA-256
  `9532ca6e0a451cc0cddf7bb673e852b98e61f55aad7d1cef025cfc4c9ceaef66`.
- El build automático de V22 desde `main` mide 121.119.378 bytes y tiene
  SHA-256
  `f44de29cd1659e2e0946fb18bc01ad6f5944971d1ed1a850e3e3522f8afa4a73`.
  Pasó `node --check`, Ktlint, 76 unitarios, APK y Lint; no fue instalado ni
  probado físicamente.
- El build automático de V23 desde `main` mide 121.122.946 bytes y tiene
  SHA-256
  `bb42718aa8890304ccd2077e34db2311c21d99a63bb13d76e0926af84c3c42a3`.
  Pasó Ktlint, 80 unitarios, APK y Lint; no fue instalado ni probado
  físicamente.
- El build automático de V24 desde `main` mide 121.168.082 bytes y tiene
  SHA-256
  `f9af2e56bd3a8df066edae0c466ef5a757efd52dcc2e03bfac07d30b432a586a`.
  Pasó Ktlint, 91 unitarios, APK y Lint. El manifiesto empaquetado conserva
  únicamente permisos propios del navegador/GeckoView: no agrega almacenamiento
  amplio ni instalación de paquetes; su `FileProvider` no es exportado. Por la
  orden de cerrar seis lotes antes de instalar, todavía no tuvo matriz física.
- En SM-A235M se verificó apertura/regreso y ausencia de destellos para raster,
  video, canvas, SVG y fondos.
- Mientras no exista una decisión visual válida, cada recurso permanece en
  `block / analyzer_unavailable`.
- El navegador no usa infraestructura, código, modelos ni datos de runtimes
  retirados.
- Matriz física final en SM-A235M/Android 14, con perfil DEV borrado y cache
  bust: Frávega `20.258 / 20.487 / 1.347 ms`, Mimo
  `5.638 / 6.224 / 594 ms`; Cheeky Home quedó visible en `1.671 ms` pero no
  emitió quietud dentro de 45 s. El recorrido de categoría Cheeky verificó
  físicamente corazones visibles y ausencia de indicadores residuales.
- Evidencia detallada:
  `docs/compatibility/results/dag-browser-v21-stability-sm-a235m-2026-07-29.md`.

## Candidato local de DAG Browser

El lote integrado se desarrolló y validó en:

```text
/Users/yejielnehmad/Developer/content-filter-dag-browser-v3
```

- Ese worktree contiene la evolución del candidato `versionCode 18`,
  `0.9.0-dev`, con mejoras de
  pestañas, interfaz, compatibilidad y análisis local.
- El selector usa dos columnas con miniaturas efímeras de páginas ya filtradas,
  nueva pestaña, cierre por botón o deslizamiento y reordenamiento por pulsación
  larga. Sólo persiste URL, título, orden e índice activo; una única sesión se
  restaura con red y las demás quedan diferidas.
- El filtro mantiene una caché acotada de decisiones por SHA-256 y deduplica
  pedidos simultáneos. La barrera incorpora fuentes lazy frecuentes y corrige
  el fallback de mensajes de GeckoView que dejaba algunos recursos
  transparentes.
- Evidencia SM-S908E: Google Imágenes visible en 144-187 ms, primera quietud
  visual en 1.787 ms y recarga en 989 ms; cinco pestañas restauradas usaron
  226.383 KiB PSS con una sola sesión abierta. No hubo crash ni ANR.
- `ktlintCheck`, 53 unitarios y `assembleDevDebug` fueron correctos. La APK
  local mide 121.088.287 bytes y su SHA-256 es
  `6cfe35c70a69ed5d0db0a0e64a21ca55939f8c14ba9ad0a9d5f43d22d8af8b08`.
- Evidencia detallada:
  `docs/compatibility/results/dag-browser-v3-optimization-sm-s908e-2026-07-29.md`.
- El cierre del lote concilia estos cambios con `main`; no volver a abrir una
  línea DAG paralela ni restaurar runtimes retirados.
- Los builds desde worktrees deben recibir `SUPABASE_URL` y
  `SUPABASE_ANON_KEY` DEV mediante el entorno. Un worktree no hereda `.env`
  ignorados por Git; un APK con valores vacíos queda offline aunque el código y
  el `versionCode` sean nuevos.

## Investigación visual

- El contrato de 21 señales, preprocesado RGB 224 x 224, manifiesto, validadores
  y guía de anotación pertenecen al navegador actual.
- El piloto humano produjo un único clasificador binario `allow/filter` afinado
  sobre 197 ejemplos. La validación congelada contiene 21 casos y el holdout
  independiente 4 permitidos.
- Candidato elegido: `tinyclip-bounded-finetune-r1-int8.onnx`, SHA-256
  `2d52bd9e5eb4cd448cb0d64a784b2ee6f761ad20e890c57b898fd7991d29a9ee`,
  umbral `0.4`, inferencia ONNX íntegramente local.
- En la validación congelada registró recall de filtro `1.0`, cero falsos
  permisos, un falso filtro y exactitud `0.952381`; el holdout quedó `4/4`.
- Esto demuestra el piloto y habilita el candidato DEV, no prueba cobertura
  poblacional ni autoriza Production. Nuevos pesos o ampliaciones de corpus
  requieren otro ticket y validación independiente.

## Dirección de producto acordada

- DAG debe ser el navegador predeterminado cuando esté habilitado, mediante la
  confirmación oficial que exige Android.
- Glosh conserva instalación y configuración remota sobre teléfonos personales.
- No usar Device Owner, MDM, Knox ni restablecimiento de fábrica.
- Glosh debe impedir navegadores alternativos mediante Accessibility y las
  barreras existentes, declarando la cobertura como best-effort.
- App Admin necesita una activación guiada de DAG, mostrar navegadores
  detectados y distinguir `Protegido`, `Configuración incompleta` y
  `Requiere atención`.
- Solicitudes de apps deben mostrar icono, nombre, usuario, dispositivo y estado,
  conservando packageName como identidad confiable.
- DAG debe distinguir visualmente imagen cargando, imagen bloqueada y error sin
  revelar píxeles antes de la decisión.

## Candidato integrado v18

- DAG se registra como navegador HTTP/HTTPS, acepta enlaces externos mediante
  la misma política segura y solicita el rol oficial de navegador
  predeterminado con confirmación local de Android.
- La política Web remota incorpora `__dag_browser_required__`; viaja por el
  snapshot existente, sin una API ni tabla paralela.
- Cuando DAG es obligatorio, Accesibilidad permite DAG y expulsa a Home
  navegadores alternativos conocidos o detectados como handlers Web. Es una
  barrera best-effort compatible con teléfonos personales, sin Device Owner.
- Admin agrega activación guiada, instalación detectada, salud de Glosh,
  recordatorio del rol predeterminado y lista de navegadores alternativos.
- Usuario muestra si DAG es obligatorio, está instalado y permite abrirlo para
  completar la confirmación.
- Candidato Usuario `294` agrega instalación de DAG desde Internet o Ajustes:
  descarga el manifiesto separado, verifica SHA-256, packageName y firma de
  Content Filter, y delega la confirmación final al instalador oficial Android.
- El APK y manifiesto de DAG `18` están preparados sólo en
  `build/dev-updates`; todavía no fueron publicados, por lo que la descarga
  remota queda pendiente de autorización.
- Las fotos diferencian `Analizando`, `Protegida por Glosh` y `Imagen no
  disponible`; espera y error siguen sin revelar píxeles.
- Solicitudes Admin presentan icono grande, nombre confiable, packageName, tipo
  y estado.
- Candidatos independientes instalados in-place en SM-S908E: Usuario `293`,
  Admin `284`, DAG `18`. Compilación, tests, Lint, apertura fría, inferencia
  ONNX, rol predeterminado y `ACTION_VIEW` HTTPS fueron correctos.
- Supabase DEV confirmó licencia efectiva activa hasta el 03/08/2026. Admin
  refresca la licencia al abrir y muestra `Licencia por vencer · 5 días
  restantes`.
- La política `Usar navegador DAG` se aplicó físicamente: Chrome y Brave
  regresaron a Home con motivo `dag-browser-required`; DAG permaneció permitido
  con motivo `protected-browser`.
- Android desactivó Accessibility después de la actualización in-place; la
  recuperación guiada funcionó y el servicio quedó habilitado otra vez.
- Usuario y Admin extienden el encabezado oscuro bajo la barra de estado; la
  franja blanca superior observada en el candidato anterior quedó corregida.
- Los candidatos todavía no están publicados en DEV; instalación física no
  equivale a distribución.

## Candidato local DAG 19

- DAG 19 agrega AVIF estático al mismo preprocesador acotado y análisis ONNX
  local; HEIC, SVG remoto, animaciones y errores de decodificación continúan
  cerrados.
- Una falla técnica ya no se presenta como decisión del modelo. `Analizando`,
  foto filtrada con escudo pequeño e `Imagen no disponible` son estados
  distintos; el escudo queda contenido dentro de la foto y no atraviesa
  modales del sitio.
- En SM-S908E H&M dejó de rechazar sus fotos AVIF: se observaron 10
  `model_allow`, 3 `model_filter` y 1 dimensión insegura. Cheeky y Frávega
  conservaron navegación e imágenes; Mimo cargó después de repetir una entrada
  afectada por errores JavaScript propios de VTEX.
- Instagram abre su página oficial, controles e inicio de sesión. Un SVG
  decorativo de Meta permanece oculto; no se observó bloqueo de navegación.
- `ktlintCheck`, 61 tareas del lote (unitarios, APK y Lint vital) y la
  reinstalación in-place fueron correctos. DAG sigue como navegador
  predeterminado y Accessibility de Glosh quedó restaurado y enlazado.
- APK local: 121.096.554 bytes, SHA-256
  `aa221b1ed17e4b9c13709fc90eb6ccb5bb63be8ea3ff0c9fead6d661880fdc8d`.
  No se publicó ni se modificaron Usuario, Admin, Supabase o Production.
- Evidencia:
  `docs/compatibility/results/dag-browser-v3-avif-sm-s908e-2026-07-29.md`.

## Cierre del lote local recuperado

- Se auditó el commit local suelto `cd4ab38` y se recuperaron selectivamente
  sus mejoras vigentes sobre el `main` actual. No se restauraron versiones,
  runtimes DAG ni ajustes de barras de sistema que ya estaban reemplazados por
  una solución posterior.
- Usuario abre la configuración de Accessibility con intento directo al
  servicio y fallback seguro; el texto de recuperación explica qué control
  activar. La navegación física desde Ajustes vuelve a Inicio.
- Admin restablece correctamente la lista de usuarios al tocar la pestaña,
  ofrece encabezado y regreso coherentes en Solicitudes y usa selector de hora
  de 24 horas con detección de franjas superpuestas, incluso nocturnas.
- La fecha operativa, consumos y horarios usan explícitamente
  `America/Argentina/Buenos_Aires`.
- Accessibility detecta una app de terceros desconocida al verla en primer
  plano, la deja pendiente y solicita aprobación mediante el flujo local
  existente. Mis apps recorta correctamente su lista nativa.
- Validación local: `ktlintFormat`; tests de `core-domain`,
  `feature-accessibility`, `feature-vpn`, Usuario y Admin; y ambos
  `assembleDevDebug`, todos correctos. Se añadieron pruebas de solapamiento
  normal y nocturno.
- Gate físico SM-S908E: Usuario `293`, Admin `284` y DAG `18` instalados;
  Accessibility habilitada y vinculada; DAG conserva el rol de navegador.
  Usuario volvió desde Ajustes a Inicio y Admin mostró el nuevo encabezado de
  Solicitudes con regreso por usuario.
- APK Usuario: 4.726.793 bytes, SHA-256
  `4ff20a6228b9ea3785f312a11bd1dee9107aad92155618fae4342aa25f78c2e2`.
  APK Admin: 28.685.724 bytes, SHA-256
  `2431ef8e730a7101d2cc92f27507c15bff4d86a9212a6d4c441e0323a7f43b0a`.
- No se hizo push ni publicación DEV; GitHub continúa como respaldo anterior
  hasta que el usuario autorice un hito remoto.

## Estado funcional de Glosh

- Activación real contra Supabase DEV.
- App Admin se activa con token de administrador.
- App Usuario se activa con token generado desde Admin.
- VPN foreground aplica política Web y SafeSearch.
- Accessibility bloquea aplicaciones y protege rutas de manipulación.
- Device Admin, watchdog, alertas FCM y recuperación siguen siendo capas
  independientes.
- Solicitudes, límites individuales, tiempo adicional y grupos de apps están
  integrados.
- Historial y estados de navegación sensibles permanecen locales/cifrados cuando
  corresponde.
- La Superweb legacy sigue siendo necesaria y no debe eliminarse sin ticket
  específico.

## Gate físico SM-A235M del candidato actual

- Instalación in-place verificada: Usuario `295`, Admin `284` y DAG `20`, sin
  pérdida de datos ni publicación remota.
- DAG quedó como navegador predeterminado y Accessibility habilitada y
  vinculada.
- Se corrigió una doble evaluación de Accessibility: DAG era reconocido como
  `protected-browser`, pero luego podía entrar al flujo genérico de aprobación
  y ser expulsado a Inicio. Las variantes `release`, `dev` y `beta` de DAG
  pertenecen ahora a una allowlist única y probada.
- Después de la corrección DAG permaneció en primer plano. Google, H&M,
  Cheeky, Instagram y YouTube abrieron dentro del navegador; Inicio y Atrás no
  expulsaron la aplicación; el selector de ocho pestañas permitió seleccionar,
  crear y cerrar tarjetas.
- Google Imágenes mostró contenido en menos de un segundo en la corrida con
  caché. H&M mostró la página en `1.056 s` y completó análisis en `7.533 s`;
  Cheeky mostró la página en `2.167 s` y completó análisis en `8.307 s`.
- H&M conservó un hero promocional difuminado y Cheeky registró algunos
  recursos diminutos/no raster como `unsupported_image`; son observaciones de
  calibración/compatibilidad, no fallas de navegación.
- Evidencia:
  `docs/compatibility/results/dag-browser-v20-sm-a235m-2026-07-29.md`.

## Candidato local GloshIA Ayuda y reportes seguros

- App Usuario `296` y App Admin `285` reutilizan el chat contextual existente y
  agregan respuestas especializadas para DAG, imágenes, Accessibility,
  aplicaciones, actualizaciones, activación y sincronización.
- El motor sigue siendo local, determinista, gratuito y funcional sin Internet.
  No usa una API de IA ni envía el texto de la conversación.
- Cuando una pregunta expresa una falla, la app genera un resumen técnico
  predeterminado y lo envía a Supabase DEV con categoría, versión, fabricante,
  modelo, Android y códigos de estado permitidos. No envía contraseñas, fotos,
  búsquedas ni el texto escrito por el usuario.
- Supabase DEV incorpora `support_reports`, RLS y RPC separadas para envío desde
  dispositivos y lectura exclusiva de Super Admin. La Superweb oficial publica
  la bandeja `Reportes GloshIA` en la build `0fbeed5`.
- Prueba física SM-A235M: el chat respondió a una pregunta libre que contenía
  una contraseña sintética; Supabase recibió solamente
  `DAG presentó un problema al cargar, analizar o mostrar imágenes.`, categoría
  `dag-images`, modelo `SM-A235M`, Android `14` y versión `296`.
- El puente con DAG V3 queda habilitado por defecto solamente en DEV para evitar
  que futuras compilaciones locales vuelvan a diagnosticar como ausente una APK
  instalada. Beta y Production permanecen apagados.
- Validación: unitarios y ktlint Android, APK Usuario/Admin, typecheck y build de
  Superweb, lint y asesores de seguridad de Supabase, y prueba física del flujo
  Usuario a backend. La URL oficial confirmó entorno DEV, commit `0fbeed5`,
  autenticación en `/support` y navegación visible a `Reportes GloshIA`.
- Instalación física adicional SM-S908E: Usuario `296`, Admin `285` y DAG `20`;
  Accessibility siguió enlazada y DAG conservó el rol de navegador.
- El código se respaldó en `main` y la Superweb quedó publicada. Las APK no se
  publicaron al canal remoto de actualizaciones en este cierre; Supabase
  Production no se modificó.

### Extensión conversacional local Usuario 297

- La causa de las respuestas rígidas era que el supuesto chat aceptaba texto
  libre pero resolvía únicamente reglas y palabras clave.
- Usuario 297 mantiene esa capa como verdad de estados, acciones y reportes, y
  agrega generación local con LiteRT-LM 0.14 y Qwen2 0.5B Instruct. No usa una
  API de IA y el texto de la conversación no sale del teléfono.
- El modelo no viene dentro del APK: se descarga una sola vez, admite
  reanudación y se acepta únicamente si coinciden sus `647377840` bytes y el
  SHA-256
  `0f01cc004b8eb62b92ba6be85ed05a248ba0d2f78af94c4949b313eccfb4c157`.
- La conversación conserva hasta ocho turnos. El prompt queda limitado al
  proyecto y la salida vuelve a redactar secretos detectados; los reportes
  continúan construyéndose desde datos deterministas permitidos.
- La ayuda básica sigue disponible si el modelo falta, no es compatible o no
  inicia. El candidato soporta ARM64 y no declara disponible el modelo en
  teléfonos con memoria insuficiente.
- Toolchain actualizado a Java 21, Kotlin 2.3.20, KSP 2.3.0, Hilt 2.58 y R8
  8.13.19, conservando bytecode objetivo Java 17. Detalle:
  `docs/help/GLOSHIA_LOCAL_CHAT.md`.
- Validación local correcta: ktlint, unitarios de Usuario y APK DEV minificada.
  APK: `26552638` bytes; SHA-256
  `f5fc27b103ae1cb11aaf9cd2df28ad37305cf700f9bc4bc1d894b1be076e884b`.
- Pendiente: ADB no detectaba un teléfono al cerrar el build; falta instalar
  Usuario 297, copiar el modelo verificado y ejecutar el gate conversacional
  físico. No se hizo push ni publicación remota.

## Límites vigentes

- Sin Device Owner no existe garantía absoluta contra modo seguro, ADB
  previamente autorizado, caída de Accessibility o restablecimiento físico.
- Ser navegador predeterminado no impide por sí solo abrir otro navegador.
- Accessibility puede bloquear o expulsar otras apps, pero no insertar de forma
  fiable el clasificador dentro de Chrome para modificar cada imagen.
- Una VPN/DNS no puede aplicar blur individual dentro de HTTPS sin una
  interceptación TLS que no está autorizada ni recomendada.

## Lote 6 de 6: chat de ayuda integrado

- Commit local `ce98ca5`: Usuario 302 y Admin 287 comparten el contrato
  determinista de ayuda, con saludos, capacidades, agradecimientos,
  repreguntas, sugerencias contextuales y acciones que sólo navegan.
- Ambas pantallas identifican su contexto: Usuario muestra que usa este
  teléfono y Admin que presenta el resumen agregado con cantidad de usuarios.
  El botón `Borrar chat` elimina los mensajes locales; al cerrar la pantalla no
  se conserva historial.
- Usuario conserva LiteRT-LM 0.14 y el modelo Qwen2 0.5B opcional fuera del APK.
  La salida generativa rechaza respuestas largas, repetidas, no fundamentadas o
  con patrones inválidos, y vuelve a la respuesta determinista segura.
- Admin no incorpora el modelo de 647 MB: funciona offline con el mismo
  contrato confiable sin duplicar almacenamiento ni memoria. No se mezclan
  estados, repositorios, permisos o acciones de ambos roles.
- Una falla de página como `No me abre una página` queda clasificada como DAG y
  genera sólo el resumen técnico permitido. El texto libre y los secretos no
  forman parte del reporte.
- Validación automática correcta: 142 pruebas entre contrato, Usuario y Admin,
  ktlint de los cuatro módulos y ambos `assembleDevDebug`, incluyendo R8 y lint
  vital. APK Usuario: 26.569.022 bytes, SHA-256
  `45beb84ec2ae9a114f414fb6e75b8ae9196498dce08135944bd6da201e2e969e`.
  APK Admin: 28.818.004 bytes, SHA-256
  `2b15a09d7e61fe334bc70e58e7894553ee7174c9c6b08dbe5a3cf6e36197b648`.
- No se instaló, publicó ni hizo push durante el lote. Supabase y Production no
  se modificaron.

### Correctivo local Usuario 304

- Usuario 304 está instalado in-place en el SM-S908E; conservó el modelo local
  verificado de 647.377.840 bytes, Accessibility y la VPN activa.
- LiteRT-LM permanece en 0.14.0, pero la generación usa su llamada completa
  fuera del hilo visual. Esto evita el choque ABI del flujo `sendMessageAsync`
  que producía `NoSuchMethodError` y cerraba el proceso.
- La prueba física completó inferencia sin caída ni pérdida de protección. La
  primera salida no superó la barrera de calidad y la pantalla usó correctamente
  la respuesta determinista segura. El candidato registra sólo tipo de error o
  motivo de descarte y longitud; nunca pregunta, respuesta, URL ni secretos.
- Ktlint, unitarios, R8, APK y lint DEV correctos. La prueba final identificó
  `ungrounded` como motivo seguro de descarte. Por decisión del usuario, la
  mejora conversacional queda diferida hasta que las funciones y el contexto de
  la app estén más cerrados; no se redujo la barrera de calidad.
- APK final Usuario 304: 26.569.022 bytes, SHA-256
  `4802a51b2fde3b208a3efca04c0f29e0e0e10aad0b8d39ae9741eac04c2b4ab9`.
  APK final DAG 25: 121.172.970 bytes, SHA-256
  `ec7f47f72d5f8347fd505b487a5e939cbfcca7227f000c95cfe272bd76426151`.
  Ambas quedaron instaladas desde esos artefactos en el SM-S908E; Admin 287 no
  fue recompilado ni modificado.

### Línea base profesional DAG 25 iniciada

- El 2026-07-30 se midió DAG 25 sobre SM-S908E sin cambiar APK, datos ni
  configuración. Una página simple quedó visible en 198 ms y con viewport listo
  en 456 ms; Google web en 178/3.440 ms; Google Imágenes en 412/966 ms;
  Instagram en 288/1.956 ms; Frávega en 1.053/9.830 ms. No hubo crash ni ANR.
- Cheeky fue visible en 1.852 ms y Mimo en 267 ms, pero no emitieron quietud
  dentro de 15 segundos. H&M emitió análisis/viewport en 841/1.094 ms sin una
  señal coherente de página visible. La causa general es que la extensión
  condiciona la quietud del documento actual a estado y colas globales que
  también reciben carga dinámica o trabajo de otras pestañas.
- El usuario confirmó un falso permiso real en una foto raster de la portada
  pública de Instagram. No se retuvo la captura porque coincidió con una
  notificación personal. Antes de tocar umbrales debe distinguirse score del
  modelo, caché o bypass de transporte.
- `DagMediaTransport` registra actualmente URL, texto alternativo y estado DOM
  en DEV. Es una regresión respecto de la evidencia privada anterior y queda
  como `DAG-V3-PRIVATE-DIAGNOSTICS-06`.
- La matriz dejó 15 pestañas y aproximadamente 310.924 KiB PSS/487.696 KiB RSS.
  El presupuesto de 12 MB sólo cubre miniaturas; las sesiones Gecko abiertas en
  la misma ejecución requieren `DAG-V3-TAB-HIBERNATION-09` antes de aprobar 50.
- Tickets de causa general capturados: `DAG-V3-PRIVATE-DIAGNOSTICS-06`,
  `DAG-V3-DOCUMENT-ISOLATION-07`, `DAG-V3-FALSE-ALLOW-08` y
  `DAG-V3-TAB-HIBERNATION-09`. Ninguno agrega excepciones por sitio. No se
  escribió código, incrementó versión, compiló, instaló ni publicó durante esta
  etapa.
- Después de cerrar la línea base se implementó el ticket ya aprobado
  `DAG-ABOUT-VERSION-05`: DAG 26 (`0.16.0-dev`) agrega `Acerca de DAG` y lee
  `versionName`/`versionCode` del paquete instalado. Formato, 93 unitarios, APK
  y Lint DEV son correctos. APK de 121.174.874 bytes, SHA-256
  `09ddebacf4af0f29e5ed7860a5b6991be05e76125433136562bb75535d40fbcb`,
  instalado in-place en SM-S908E con certificado DEV esperado; DAG conservó el
  rol de navegador. El diálogo mostró físicamente `Versión 0.16.0-dev (26)` y
  Atrás volvió a los controles del navegador sin recargar la página. No se
  publicó.
- El lote general aprobado después de esa línea base quedó validado físicamente
  como DAG 29 (`0.19.0-dev`), extensión `1.22.0`, instalado in-place en
  SM-S908E y sin publicación remota. `DAG-V3-PRIVATE-DIAGNOSTICS-06` elimina
  URL, texto alternativo y estados DOM del protocolo/log de presentación;
  quedan acción, frame y cantidad de coincidencias. La matriz de Frávega, Mimo,
  Cheeky, Google Imágenes e Instagram no dejó contenido de navegación en
  `logcat`.
- `DAG-V3-FALSE-ALLOW-08` no baja el umbral global: durante la reproducción se
  observaron scores de permiso alrededor de `0,27-0,28` y existen permisos
  humanos correctos en esa zona; la variante rotativa exacta aún debe
  correlacionarse físicamente. Sólo las imágenes con relación extrema desde
  `2:1` reciben tres vistas regionales del mismo modelo, con umbral `0,50`;
  fotos normales siguen con una inferencia. No hay regla por Instagram, API,
  segundo modelo ni persistencia de píxeles.
- `DAG-V3-FRAME-STABILITY-10` reemplaza recorridos DOM globales repetidos por
  raíces incrementales, ignora mutaciones de estilo propias, espera 160 ms de
  quietud después del scroll y mantiene un índice efímero fuente-elementos para
  aplicar cada decisión sólo donde corresponde. El CSS fail-closed de
  `document_start` no cambió. Frávega midió 83 cuadros sin tardíos y Cheeky 127
  sin tardíos; Mimo registró dos tardíos sobre una muestra corta de 20.
- `DAG-V3-MEDIA-PRESENTATION-11` reconcilia respuestas de fuentes reemplazadas,
  limpia esperas heredadas al aparecer un visual permitido y sustituye
  `Analizando…` por un brillo barrido neutro. Frávega y Mimo quedaron sin
  leyendas residuales; filtradas conservan desenfoque y escudo.
- Validación local de DAG 29: `node --check`, 99 unitarios, `ktlintCheck`,
  `lintDevDebug` y `assembleDevDebug` correctos. APK de 121.182.526 bytes,
  SHA-256
  `c9d4b616a7be18daea1e758750a5913d18717530d1b3763001e8b60e02d0997a`.
  Quedó instalada en SM-S908E, como navegador predeterminado y con
  Accessibility activa. `Acerca de DAG` mostró `Versión 0.19.0-dev (29)`.
  `DAG-V3-DOCUMENT-ISOLATION-07`, `DAG-V3-TAB-HIBERNATION-09` y la reproducción
  exacta del falso permiso rotativo siguen pendientes.
- `DAG-WEB-PROMPTS-01` quedó resuelto en DAG 30 (`0.20.0-dev`). La causa de que
  los desplegables HTML recibieran el toque sin abrir era la ausencia del
  `GeckoSession.PromptDelegate`. DAG ahora presenta selectores nativos simples y
  múltiples, conserva selección, grupos y opciones deshabilitadas, y cancela
  de forma segura al cambiar de pestaña o salir de la app.
- Validación de DAG 30: 99 unitarios, `ktlintCheck`, `lintDevDebug` y
  `assembleDevDebug` correctos. APK SHA-256
  `875aa162c9c2aebbe6cf4fd4d8c0499bd77a43760d1e224cca059169c070508b`,
  instalado in-place en SM-S908E. Un selector HTML real abrió y el usuario
  confirmó físicamente que funciona. La variante múltiple queda cubierta por
  la implementación y pendiente de un recorrido físico específico.
- DAG 31 (`0.21.0-dev`) agrega historial local acotado a 100 páginas, acceso
  para reabrir y borrado integrado tanto en Historial como en `Borrar datos de
  navegación`. El botón `+` ocupa el lugar del escudo estático dentro de la
  barra y crea una pestaña nueva; se retiró su duplicado del menú.
- Las miniaturas siguen viviendo sólo en memoria. La restricción ahora examina
  contraseñas, pagos y CAPTCHA que sean realmente visibles en el viewport, en
  vez de invalidar una tienda completa por formularios ocultos. Una página
  HTTPS neutral mostró físicamente su captura real; contenido sensible visible
  conserva tarjeta neutra.
- Validación de DAG 31: `node --check`, 102 unitarios, `ktlintCheck`,
  `lintDevDebug` y `assembleDevDebug` correctos. APK SHA-256
  `51fc7b2720473693a0f545d61e28ea9f681a6f72ee7b986fc1c247b2111fdf4f`,
  instalado in-place en SM-S908E. DAG conservó el rol de navegador y
  Accessibility de Usuario permaneció activa. Los datos técnicos de historial
  usados en la prueba se borraron al finalizar.
- `DAG-V3-REGIONAL-FP-12` quedó resuelto localmente en DAG 32
  (`0.22.0-dev`). El modelo y el umbral global `0,40` no cambiaron. En
  panorámicas, una única vista regional apenas superior a `0,50` ya no bloquea
  toda la imagen: se requieren dos vistas sobre `0,50` o una única señal fuerte
  desde `0,70`. Los errores técnicos continúan cerrados por seguridad.
- Validación de DAG 32: 104 unitarios, `ktlintCheck`, `lintDevDebug` y
  `assembleDevDebug` correctos. APK de 121.208.374 bytes, SHA-256
  `d96919c6590448e78e9f6ab21ec975097e5492ead016a4fccf65d27e0f38ba7e`,
  instalada in-place en SM-S908E. Cheeky permitió físicamente una imagen con
  máximo regional `0,5366`, que la regla anterior habría bloqueado, y completó
  `5.333 / 5.625 / 1.791 ms`. Frávega y Mimo quedaron visibles pero no
  completaron la quietud visual dentro de 35 segundos; se conserva como
  problema separado de entrega/quietud. No hubo crash y se preservaron
  navegador predeterminado y Accessibility. Evidencia:
  `docs/compatibility/results/dag-browser-v32-regional-consensus-sm-s908e-2026-07-30.md`.
- `DAG-V3-FILTERED-OVERLAY-13` quedó resuelto localmente en DAG 33
  (`0.23.0-dev`), extensión `1.23.0`. Las fotos rechazadas conservan solamente
  el desenfoque fuerte y la descripción accesible; se retiraron el escudo/✓ y
  el rastreo de su contenedor después de la decisión final. El brillo de espera
  y el estado de error técnico no cambiaron.
- Validación de DAG 33: `node --check`, 104 unitarios, `ktlintCheck`,
  `lintDevDebug` y `assembleDevDebug` correctos. APK de 121.208.250 bytes,
  SHA-256
  `6e6810ba1e562664ef75d67493c10a620620ee47ab1af21791d901529963444d`,
  instalada in-place en SM-S908E. Frávega, Mimo y Cheeky completaron la matriz
  sin caché ni crash. Una búsqueda visual de control produjo 22 rechazos del
  modelo y 17 presentaciones bloqueadas, físicamente sin escudo. Se preservaron
  navegador predeterminado y Accessibility. Evidencia:
  `docs/compatibility/results/dag-browser-v33-filtered-overlay-sm-s908e-2026-07-30.md`.
- DAG 34 (`0.24.0-dev`), extensión `1.24.0`, refuerza fuentes dinámicas:
  un cambio de `src`, `srcset`, `data-src` o `poster` vuelve a ocultar el medio,
  invalida la decisión anterior y exige analizar la fuente activa antes de
  presentarla. El cambio quedó integrado en `main` local.
- `DAG-V3-FALSE-ALLOW-08` quedó resuelto localmente en DAG 36
  (`0.26.0-dev`). El umbral global continúa en `0,40`. Sólo una imagen ordinaria
  ya dudosa, con score desde `0,30`, recibe cuatro vistas regionales del tensor
  ya preparado; una región desde `0,45` la filtra. Las fotos claras por debajo
  de `0,30` conservan una inferencia y las panorámicas mantienen su consenso
  separado.
- Validación de DAG 36: 111 unitarios, `ktlintCheck`, `lintDevDebug` y
  `assembleDevDebug` correctos. APK de 121.210.914 bytes, SHA-256
  `5bed28cf235007c5622b54f241c01d4dd6ac40c0679f5b09812eac73faa9f3ce`,
  instalada in-place en SM-S908E. La fuente reproducida de Google Imágenes que
  el candidato intermedio permitió con máximo `0,4600` terminó
  `model_filter 0,4600` y siguió difuminada a los 12 segundos. Cheeky y Mimo
  completaron sus señales; Frávega quedó visible y analizada, con quietud de
  fotos incompleta en la muestra de 12 segundos. No hubo crash ni ANR y se
  preservaron navegador predeterminado y Accessibility. Evidencia:
  `docs/compatibility/results/dag-browser-v36-uncertain-collage-sm-s908e-2026-07-30.md`.

### Matriz conjunta Usuario 304, Admin 287 y DAG 29

- El 2026-07-30 se recorrieron físicamente las tres apps en SM-S908E/Android 16
  sin borrar datos. Usuario y Admin conservaron sus secciones independientes de
  Ajustes y ambos chats funcionaron offline.
- Usuario respondió con estado propio y no repitió una clave sintética; Admin
  mostró solamente el resumen agregado de dos usuarios. `Borrar chat` funcionó
  y `Cambiar administrador` exigió confirmación, que fue cancelada.
- La calidad contextual avanzada sigue diferida: Usuario explicó la activación
  sin informar el estado concreto de la licencia; Admin clasificó una pregunta
  de identidad como ayuda de bloqueo de apps. Fueron respuestas seguras pero
  poco pertinentes, sin crash ni fuga.
- El organizador DAG funcionó con 20 pestañas y las dos pestañas técnicas se
  cerraron. `DAG-DOWNLOADS-01` no pasó la prueba física: un PDF inline entró al
  visor PDF.js, donde la barrera cerró seguro; una descarga real por formulario
  `POST target=_blank` fue reabierta por DAG como `GET` y el servidor respondió
  `Method Not Allowed`. No quedó archivo ni parcial.
- Causa de descarga confirmada: `onLoadRequest` crea manualmente la nueva
  pestaña con `loadUri(request.uri)` y pierde método/cuerpo del formulario. El
  visor PDF inline es un segundo recorrido pendiente. Evidencia:
  `docs/compatibility/results/joint-user304-admin287-dag29-sm-s908e-2026-07-30.md`.

## Laboratorio local GloshIA visual

- `tools/gloshia_lab` evalúa fuera de Android exactamente el modelo visual
  integrado en DAG 36, con SHA-256
  `2d52bd9e5eb4cd448cb0d64a784b2ee6f761ad20e890c57b898fd7991d29a9ee`,
  el mismo preprocesamiento y la misma política de umbrales. No entrenó,
  reemplazó ni modificó el modelo o las APK.
- El banco local vigente está en
  `.codex-tmp/gloshia-lab-current-1000`, fuera de Git. Contiene 1.000
  miniaturas públicas actuales de Wikimedia Commons, 1.000 SHA-256 únicos y
  214.967.752 bytes de imágenes. La distribución es 450 casos de borde
  actuales, 250 permitidos difíciles, 150 grupos/collages, 100 menores en
  contextos normales orientados a edad escolar desde aproximadamente seis años
  —sin afirmar una edad exacta a partir de la foto— y 50 controles sensibles.
- La división por serie es `600 main_eval / 200 difficult / 200 final_sealed`.
  Ninguna serie cruza splits o categorías. El bloque final permanece sellado y
  no tiene predicciones. La adquisición registra procedencia y licencia, pero
  todo el corpus conserva `training_authorized: false`; CC BY-SA se admite sólo
  para esta evaluación local hasta una revisión legal independiente.
- El modelo procesó las 800 muestras no selladas sin errores: 499 decisiones de
  filtro y 301 permisos. Latencia local mediana `59,675 ms`, p95 `278,622 ms` y
  máxima `285,539 ms`. Son tiempos de la Mac y no reemplazan las mediciones
  físicas Android.
- Se generó una cola ciega de 200 casos y 13 hojas de contacto. La interfaz
  escucha sólo en `127.0.0.1`, no usa Supabase ni sube fotografías, oculta
  predicción/score/estrato hasta que exista decisión humana y exporta la
  revisión como JSON descargable. La inspección visual confirmó contenido
  contemporáneo; además se retiró de forma recuperable una fotografía de la
  década de 1890 detectada por la auditoría reforzada.
- Todavía no hay métricas de precisión contra verdad humana. La revisión corta
  debe medir sobre-filtro en hombres, grupos y personas cubiertas, además de
  falsos permisos con personas pequeñas o lejanas. No ajustar umbral ni abrir
  el examen final hasta congelar esas decisiones.

## Próximo trabajo autorizado

El próximo paso del laboratorio visual es la revisión humana ciega de su cola de
200, sin modificar todavía DAG. El siguiente correctivo Android recomendado
sigue siendo `DAG-DOWNLOADS-01`: preservar semántica `POST` al abrir una
descarga en nueva pestaña y definir el tratamiento seguro de PDF inline sin
debilitar la barrera. La inteligencia conversacional avanzada de GloshIA queda
para el ticket posterior ya diferido. `DAG-V3-DOCUMENT-ISOLATION-07` y
`DAG-V3-TAB-HIBERNATION-09` continúan como tickets separados sin autorización
de código en este lote. La quietud incompleta de Frávega observada en la muestra
acotada de DAG 36 debe tratarse como entrega/quietud, no corrigiendo nuevamente
la calibración regional. No publicar sin un nuevo OK explícito.

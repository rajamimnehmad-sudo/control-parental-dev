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

## Runtime único de navegador

- El único navegador del proyecto es `app-dag-browser`.
- Es una APK GeckoView separada, fail-closed y conectada con Glosh mediante un
  puente DEV explícito.
- No existe fallback hacia implementaciones retiradas.
- La última instalación física sigue siendo DAG 21. El candidato local
  integrado en `main` declara `versionCode 24`, `versionName 0.14.0-dev`; por
  pedido del usuario no debe instalarse ningún APK hasta completar los seis
  lotes planificados.
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

## Próximo trabajo autorizado

Completar el gate físico de GloshIA Ayuda Usuario 297 cuando ADB detecte el
teléfono. Después, el siguiente lote recomendado sigue siendo ampliar la
calibración visual con ejemplos actuales, priorizando el hero de H&M y falsos
filtros de ropa masculina, sin reducir el umbral global a ciegas.

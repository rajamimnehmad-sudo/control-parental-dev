# CONTROL CENTRAL DEL PROYECTO

Actualizado: 2026-08-21. Responsable: Direccion General Tecnica.

## Estado ejecutivo

- `GLOSH-DEVICE-OWNER-INSTALLER-00` esta **blocked**, owner unico
  Proteccion Android / Codex, en `work/glosh-device-owner-installer-00` sobre
  `1d45a74c`. Su alcance es crear y ejecutar un asistente macOS seguro para
  convertir el A23 actual en Device Owner de Glosh mediante la excepcion ADB de
  laboratorio, sin factory reset ni eliminacion automatica de cuentas, usuarios
  o datos. El preflight y el checkpoint son read-only; cualquier cuenta debe ser
  retirada y restaurada manualmente por el usuario. La candidata preservada es
  DEV 319, SHA-256
  `ba612fe2f23c5633e7041bf6c233d1ed435db3bcc7f43e6d47dfb03d7b7cf14b`;
  no se recompila. `CHROME-PHOTOS-DATA-PLANE-00` permanece bloqueado unicamente
  por Device Owner hasta que este gate termine. El asistente modular y sus siete
  pruebas de seguridad estan verdes. El preflight real A23 confirmo un unico
  usuario 0, ningun owner, Glosh DEV 318 como Device Admin y 16 cuentas; genero
  un checkpoint redactado con proveedores y conteos. Una excepcion puntual del
  usuario permitio retirar cuentas desde la UI oficial: quedan tres registros,
  dos de Samsung y uno huerfano de Mi Argentina. La app Mi Argentina fue
  desinstalada y Android no expone una eliminacion ADB segura para ese registro;
  cerrar Samsung ahora causaria perdida temporal sin alcanzar cuenta cero. DEV
  319 no fue instalada y `set-device-owner` no fue intentado. Siguiente paso
  seguro inicialmente intentado: reinstalar Mi Argentina desde su fuente
  oficial. La version vigente no registra el authenticator legado y la cuenta
  persiste. Se verifico fuera del telefono Mi Argentina 5.17.3 (182): package
  exacto, certificado SHA-256 oficial `223bc7c7...f59e6a`, servicio
  `AuthenticatorService` y accountType legado exacto. El unico intento
  `adb install -r -d` fue rechazado antes de modificar estado con
  `INSTALL_FAILED_VERSION_DOWNGRADE`; la app permanece 7.21.0 (295), con el
  mismo `ceDataInode`, y la cuenta huerfana persiste. No se intento
  desinstalacion, limpieza, reset ni `set-device-owner`. Siguiente paso seguro:
  usar otro telefono laboratorio sin cuentas o autorizar reprovisionamiento del
  A23 despues de respaldar. La recuperacion `uninstall -k` autorizada se ejecuto
  limitada a Mi Argentina 7.21.0 (295), pero PackageManager conservo el
  versionCode junto con los datos y rechazo nuevamente 5.17.3 (182) con
  `INSTALL_FAILED_VERSION_DOWNGRADE`. Se restauro inmediatamente la copia 7.21.0
  firmada del propio A23: instalacion `Success`, mismo `ceDataInode=1211472`,
  cuatro cuentas intactas y ningun owner. No se limpiaron datos ni se tocaron
  fotos u otras aplicaciones. La unica variante local restante requiere
  autorizacion nueva para desinstalacion completa de Mi Argentina (perderia solo
  sus datos internos) antes de instalar el authenticator legado. El usuario
  autorizo explicitamente esa perdida puntual. La desinstalacion completa
  permitio instalar 5.17.3, registrar el authenticator legado y retirar con exito
  la cuenta huerfana; luego se restauro 7.21.0 (295) con `Success`. La cuenta
  Google temporal tambien fue retirada mediante Ajustes. Permanecen exactamente
  dos entradas del mismo Samsung account (`com.osp.app.signin` y
  `com.samsung.android.mobileservice`), 549 paquetes y ningun owner. El gate esta
  detenido antes de cerrar Samsung porque el telefono advierte que desactivara
  Samsung Cloud, Find My Mobile y Samsung Pass localmente. El usuario autorizo
  explicitamente cerrar esa cuenta. Se marco `Mantener el perfil` y se concedio
  el permiso de Contactos necesario para preservar el perfil local. Antes de
  completar, Samsung advirtio que el futuro acceso requerira un codigo enviado
  al numero enmascarado terminado en `4168`; la pantalla de confirmacion queda
  detenida hasta que el usuario confirme acceso a ese numero. Solo tras cuentas
  cero puede continuar DEV 319 y el unico intento de `set-device-owner`.
- `CHROME-PHOTOS-PROTECTED-SURFACE-00` quedo **PASS final** en A23/API 34/
  Chrome 151 con DEV 318 y commit local `2b01280f`: host unico persistente,
  rotacion continua, cero exposicion, cero marcador faltante y cero stale.
- `CHROME-PHOTOS-DATA-PLANE-00` esta **blocked**, owner unico Proteccion
  Android / Codex, en `work/chrome-photos-data-plane-00` sobre `2b01280f`. La
  arquitectura inicial usa proxy HTTPS loopback aplicado solo a Chrome, CA/hoja
  efimeras en memoria, regla por SHA-256, cache efimero y ruta VPN exclusiva
  para medir/bloquear bypass de la fixture. Unitarios iniciales App Usuario DEV
  y feature-vpn verdes. Se autorizo una excepcion exclusiva del spike DEV: una
  lease de presentacion efimera, no persistente, ligada a sesion/Chrome/fixture/
  ventana/viewport/epoch y condicionada a proxy, VPN y politica administrada
  saludables. El host unico sigue adjunto y `NOT_TOUCHABLE`; vencimiento, error,
  perdida de atestacion o cambio de contexto revocan la lease y restauran la
  cobertura opaca. Nunca habilita screenshots crudos, Production ni fallback.
  Unitarios, ktlint, lint y build App Usuario DEV estan verdes; existe una unica
  candidata local DEV 319 (`ba612fe2f23c5633e7041bf6c233d1ed435db3bcc7f43e6d47dfb03d7b7cf14b`).
  El gate fisico se detuvo antes de instalar: el A23 conectado es SM-A235M,
  Android 14/API 34 y Chrome 151.0.7922.137, pero `dpm list-owners` responde
  `no owners`. Glosh figura solo como Device Admin. El contrato exige Device
  Owner y este ticket no autoriza reprovisionar/factory reset; no activar proxy,
  CA ni transparencia hasta restaurar esa precondicion por un flujo autorizado.
- `CHROME-GLOSHIA-A23` termino **FAILED** sobre Chrome real en A23/API 34. El
  motor fue GloshIA Visual R3.1 ONNX real: permitio contenido seguro y bloqueo
  regiones contrastantes, sin crash/ANR. El gate fallo porque scroll/lazy produjo
  esperas negras de varios segundos, cobertura amplia/desalineada y ausencia de
  garantia continua; las decisiones iniciales concluyentes tardaron 4,08–7,02 s.
  No hubo cambio de codigo. No avanzar a video/DRM ni repetir hardware hasta un
  ticket acotado de cobertura atomica con replay anti-flash. Evidencia:
  `docs/areas/protection/EVIDENCE_2026-08-21_CHROME-GLOSHIA-A23.md`.
- Rama: `main`, 32 commits locales por delante de `origin/main`.
- El lote DAG de video conserva cambios locales no publicados; no mezclar ni
  descartar hasta integrarlo de forma coherente.
- App Usuario local: versionCode 311. App Admin local: 293.
- DAG local modificado: DEV 229 / 0.70.31 es el siguiente candidato; Diagnostic
  114 / extension 2.0.63 validan el lote local. Los reportes S22 de DEV 228
  demostraron que una reapertura a 150 ms dividia un mismo seek en dos cierres y
  el segundo podia terminar en `revoke_timeout`. El seek ahora espera 750 ms
  estables y coalesce el gesto completo. Se eliminaron boton, CSS, mensajes y
  modulo de fullscreen artificial: YouTube conserva controles y fullscreen web
  originales; Gecko/Android cubren, revocan y rearman la transicion exacta. A23
  confirmo dos YouTube consecutivos sin limpiar datos y
  `fullscreen_transition -> revoke_ack -> revision 2 -> smooth_started`, con
  imagen/blur vivo y sin negro. En A23, dos gestos rapidos sobre la barra original
  de YouTube produjeron cierres con `revoke_ack`, nunca `revoke_timeout`, y la
  revision final volvio a `smooth_started`. JS 102/102, unitarios
  DEV/Diagnostic, ktlint y builds DEV/Diagnostic verdes. DEV 229 queda como
  candidato para validacion humana, no como version general aprobada.
- `DAG-VIDEO-PREMIUM-CONTINUITY-03`
  reemplaza el corte automatico por blur vivo opaco con audio continuo, exige
  dos muestras seguras para retirarlo y ofrece salto manual despues de dos
  segundos. Scroll del mismo video suspende solo la captura, reubica geometria
  tras 150 ms y no pausa ni retira. Pantalla completa presenta el video exacto
  en todo el viewport mediante handshake JS/Android, sin forzar una rotacion que
  reemplace el elemento dinamico. JS 103/103, unitarios
  DEV/Diagnostic, ktlint, lint y assemble DEV verdes; falta validacion fisica.
  El negro persistente anterior no era
  cache: un cierre apagaba globalmente el laboratorio del background conservado.
  Ahora el cierre y su acuse son por documento exacto. Pantalla completa usa un
  modo nativo de DAG sin simular controles del proveedor. Los fallos compatibles
  con video negro envian diagnostico sanitizado automatico, nunca en privado y
  con cooldown de 15 minutos. Gate completo verde. A23 con datos preservados
  paso primer video, pantalla completa, segundo video y reinicio de app con
  imagen, audio y muestreo continuo. DAG Video premium queda en 92%; video
  general sigue NO-GO para URLs directas, iframes, Shorts, anuncios y redes.
- La causa del negro posterior al salto seguro quedo corregida: sobrevivía el
  timeout del cuadro rechazado y cerraba 2,5 s despues la reproduccion ya
  recuperada. Diagnostic 104 cancela ese timeout al entrar al salto y presenta
  una nota breve dentro del video. Gates completos verdes; A23 con datos
  preservados mantuvo visible el segundo YouTube, restauro audio y siguio
  muestreando sin `frame_result_timeout`, crash ni ANR.
- La auditoria DAG detecto y corrigio un riesgo de privacidad: los diagnosticos
  de medios ahora se autorizan por documento exacto y nunca por la pestaña
  visible global. Identidad desconocida o privada falla cerrada; JS 95/95,
  unitarios DEV/Diagnostic y ktlint verdes.
- `DAG-STRUCTURE-02` separo eventos y configuracion del coordinador de video,
  que bajo de 832 a 799 lineas sin cambio funcional. JS 95/95, unitarios
  DEV/Diagnostic 213/213 y lint de ambas variantes verdes.
- `DAG-UI-PREMIUM-01` modernizo chrome, inicio, incognito, pestañas y listas sin
  tocar filtrado. DEV 224 paso gates y validacion visual A23 sin crash ni ANR;
  la cobertura de video mantiene color y alcance de seguridad propios.
- El CI de DAG usa el mismo punto de entrada aislado que el trabajo local y
  verifica JS, unitarios DEV/Diagnostic, ktlint, lint de ambas variantes y APK
  DEV. Production sigue deliberadamente sin flavor hasta cerrar la matriz real.
- Super Admin compila y pasa typecheck/lint; queda un warning de imagen y no hay
  cobertura automatica suficiente.
- Supabase DEV esta operativo y las migraciones locales/remotas estan alineadas.
  La caja negra DAG esquema 3 quedo reparada y recibio reportes de A23 y S22.
  Los asesores muestran deuda de permisos/RLS e indices que requiere auditoria
  dirigida, no cambios masivos.

## Decisiones de arquitectura operativa

- `Jefe` es la interfaz principal del usuario. Puede delegar tickets a agentes
  temporales o coordinar chats especializados cuando eso aporte valor real.
- Las ocho areas de `docs/AREAS.md` organizan responsabilidades; no obligan a
  mantener ocho conversaciones activas.
- `Jefe` prioriza, prepara tickets, resuelve cruces y mantiene este archivo.
- Los chats especializados mantienen solo su `HANDOFF.md` y evidencia de su area.
- El backlog e handoff gigantes actuales son legado de consulta, no contexto de
  arranque. Se archivaran despues de extraer lo vigente.
- Si el chat `Jefe` crece hasta perjudicar precision o eficiencia, primero se
  consolida el presente en este documento y en los handoffs; luego se compacta
  el contexto y se continua sin reanalizar lo ya cerrado.

## Control de creditos Codex

- Objetivo: gastar lo necesario para obtener evidencia y calidad, sin trabajo
  duplicado ni ceremonias.
- Sol y esfuerzo bajo son el modo normal de ejecucion. Un nivel mayor requiere
  motivo concreto y aviso previo.
- Maximo habitual: dos frentes activos. Los agentes temporales se usan solo si
  reducen tiempo o contexto total.
- Validacion dirigida durante el ticket; gates amplios solo al cerrar hitos.
- Reutilizar handoffs, resultados, scripts y caches. No reauditar por falta de
  lectura ni producir informes repetidos.
- `Jefe` debe señalar cuando una tarea, prueba o delegacion tenga costo probable
  alto y proponer primero la alternativa suficiente mas economica.
- Durante una delegacion, `Jefe` mantiene el turno abierto mediante espera pasiva
  y avisa al finalizar. No hacer polling frecuente ni generar actualizaciones si
  el estado no cambio.
- Modo economia vigente: contexto minimo para agentes, un agente por lote, replay
  local antes del telefono, una APK por hito y revision final dirigida de Jefe.
- Si tres pruebas fisicas consecutivas no producen avance claro, detener la
  iteracion, auditar causa y plan, y no generar otra APK hasta corregir el diseño.
- Ningun hito de experiencia visible o audible se aprueba solo por logs: requiere
  comprobacion humana final en un telefono representativo.

## Decisión DAG APK

- **DEV** (`com.contentfilter.dagbrowser.dev`): unica APK candidata de producto
  durante desarrollo.
- **Diagnostic** (`com.contentfilter.dagbrowser.diagnostic.dev`): herramienta
  temporal para investigar fallos que DEV no explica. No es una tercera app para
  usuarios y debe retirarse del telefono al cerrar la investigacion.
- **LAB** (`com.contentfilter.dagbrowser.lab`): retirado. No existe flavor, APK,
  runner ni confianza activa en App Usuario. Una regresion verifica que el
  paquete antiguo vuelva a tratarse como tercero.
- Produccion no tiene hoy un flavor operativo de DAG. No fingir que existe; se
  definira recien cuando haya un candidato aprobado.

## Prioridad recomendada

1. Validar humanamente DEV 229 en S22: primer y segundo video sin borrar datos,
   seek y scroll con controles originales, blur/audio y entrada/salida del
   fullscreen original. La automatizacion A23 de dos videos, fullscreen y seek
   repetido ya paso sin negro ni `revoke_timeout`.
2. Ejecutar auditoria de seguridad Supabase DEV por grupos, sin romper tokens de
   dispositivo.
3. Dividir archivos criticos gigantes en tickets funcionalmente neutros.
4. Agregar validacion automatica minima a Super Admin y Edge Functions.
5. Clasificar los 5,4 GB de `.codex-tmp` y retirar solo cache reproducible con OK.

GIF esta cerrado para el formato GIF; WebP/AVIF animados quedan pendientes.

Idea futura registrada: `DAG-PARTIAL-REDACTION-01`, mascara visual para blur,
pixelado o cobertura parcial en fotos/video. No forma parte de R3.1 ni del lote
actual y necesita segmentacion espacial con validacion de fuga cero.

`DAG-VIDEO-SAFE-SKIP-01` quedo implementado con cobertura localizada, limites
anti-bucle, nota breve y sin excepciones por proveedor. Falta aprobacion humana
final del candidato DEV 226 en S22.

## Deuda estructural confirmada

- `DagBrowserActivity.kt` supera 4.000 lineas.
- `RulesViewModel.kt` supera 2.000 lineas.
- `background.js`, Accessibility y VPN superan o rondan 900. DAG-STRUCTURE-01
  separo diagnostico, geometria, presentacion, estado, mutaciones, aislamiento,
  ciclo de vida, reproduccion, captura, viewport y bootstrap. El cableado del
  salto seguro dejo `video-lab.js` en 832 lineas; DAG-STRUCTURE-02 extrajo
  eventos/configuracion y lo redujo a 799 sin cambio funcional. El cableado de
  la nota lo deja en 803 y obliga a `DAG-STRUCTURE-03` antes de otra funcion.
- `docs/BACKLOG_PRODUCTO.md` y `docs/HANDOFF_ACTUAL.md` mezclan presente e historia.
- Existe un worktree DAG historico, limpio y sin commits unicos, muy atrasado.
- Falta CI web y pruebas sistematicas de funciones Supabase.
- Ticket abierto `DAG-STRUCTURE-03`: antes de sumar otra responsabilidad de
  video, extraer de Activity el coordinador nativo y separar en `background.js`
  el diario durable. Debe ser un refactor neutro, con los gates actuales.

## Prohibido interpretar como autorizado

Esta auditoria y sus tickets no autorizan borrar, refactorizar codigo, publicar,
hacer push, tocar Production ni cerrar el trabajo DAG pausado. Cada ejecución
necesita el OK del ticket correspondiente.

Las autorizaciones sensibles solo son validas cuando el usuario las da en el chat
actual `Jefe`; no se heredan de tickets ni chats anteriores. Los commits locales
si estan permitidos cuando guardan trabajo verificable. Hallazgos fuera de alcance
se documentan sin desviar el ticket, salvo que lo bloqueen directamente.

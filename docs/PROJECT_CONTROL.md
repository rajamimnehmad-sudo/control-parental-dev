# CONTROL CENTRAL DEL PROYECTO

Actualizado: 2026-08-18. Responsable: Direccion General Tecnica.

## Estado ejecutivo

- `CHROME-VISUAL-03-VIDEO` implemento un nucleo Android universal sobre regiones
  visibles: muestreo a 500 ms cuando hay dinamismo, identidad estricta, audio
  intacto y recuperacion solo tras dos muestras seguras. La fixture A23 demostro
  bloqueo y recuperacion, pero YouTube hizo fallar el gate de experiencia: una
  tormenta de Accessibility repetia la cobertura de toda la ventana. La causa
  se corrigio despues con un baseline unico y verificaciones incrementales; los
  gates automaticos estan verdes, pero falta confirmar esa correccion en
  hardware. Chrome Visual Video sigue DEV-only/NO-GO y no se inicia el fallback
  hasta pasar ese gate.
- Chrome Visual inicio en `work/chrome-visual` desde el snapshot local
  `833d5ad8`. `CHROME-VISUAL-00-PROBE` es GO en A23/API 34: screenshot de Chrome
  1080 x 2408, overlay excluido correctamente en 3/3 comparaciones, frecuencia
  estable 2,02–2,09 capturas/s y sin crash/ANR. `GLOSHIA-SHARED-CORE-01` tambien
  esta verde: un unico modulo AAR contiene modelo, preprocessing y politica R3.1;
  DAG y App Usuario lo consumen con paridad bit a bit y score fisico A23. Chrome
  Visual es ARM64-only de forma explicita; DAG permanece intacto como fallback.
- `CHROME-VISUAL-01-IMAGES` es GO controlado: R3.1 bloqueo una imagen completa
  dentro de Chrome original en A23 y la identidad evita resultados viejos. La
  sesion demostro que sitios reales no ofrecen nodos de imagen de manera
  confiable; la auditoria enfocada dejo un fallback universal de ocho mosaicos,
  cobertura inmediata, cache efimero y espera maxima acotada. Sigue DEV-only,
  opaco y no apto para usuarios hasta cerrar la matriz web dinamica.
- `CHROME-VISUAL-02-REAL-WEB` cerro la matriz dinamica inicial en A23: scroll,
  lazy load, cambio visual, teclado, rotacion y salida de Chrome mantienen
  identidad y cobertura regional sin reglas por sitio. La captura dinamica usa
  mosaicos bounded y cache solo efimero. Sigue DEV-only: la carga inicial tarda
  1,6–2,9 s y un cambio sin evento puede exponerse hasta ~1 s mas decision.
- Rama: `main`, 32 commits locales por delante de `origin/main`.
- El lote DAG de video conserva cambios locales no publicados; no mezclar ni
  descartar hasta integrarlo de forma coherente.
- App Usuario local: versionCode 311. App Admin local: 293.
- DAG local modificado: DEV 230 / 0.70.32 es el siguiente candidato; Diagnostic
  115 / extension 2.0.64 contienen el lote local. Los reportes S22 de DEV 228
  demostraron que una reapertura a 150 ms dividia un mismo seek en dos cierres y
  el segundo podia terminar en `revoke_timeout`. El seek ahora espera 750 ms
  estables y coalesce el gesto completo. Se eliminaron boton, CSS, mensajes y
  modulo de fullscreen artificial: YouTube conserva controles y fullscreen web
  originales; Gecko/Android cubren, revocan y rearman la transicion exacta. A23
  confirmo dos YouTube consecutivos sin limpiar datos y
  `fullscreen_transition -> revoke_ack -> revision 2 -> smooth_started`, con
  imagen/blur vivo y sin negro. El diagnostico S22 `DAG-4ZJH2LP5` descubrio que
  DEV 229 habia conectado por error los 750 ms a la seleccion general, mientras
  seek conservaba 150 ms: el segundo evento del gesto podia cerrar en
  `revoke_timeout`. DEV 230 conecta 750 ms al controlador de seek y devuelve la
  seleccion normal a 150 ms, con regresion contractual. JS 102/102, unitarios
  DEV/Diagnostic, ktlint, lint DEV y assemble DEV verdes; falta confirmacion S22.
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

1. Validar humanamente DEV 230 en S22: primer y segundo video sin borrar datos,
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

# CONTROL CENTRAL DEL PROYECTO

Actualizado: 2026-08-16. Responsable: Direccion General Tecnica.

## Estado ejecutivo

- Rama: `main`, 31 commits locales por delante de `origin/main`.
- El lote DAG de video conserva cambios locales no publicados; no mezclar ni
  descartar hasta integrarlo de forma coherente.
- App Usuario local: versionCode 311. App Admin local: 293.
- DAG local modificado: DEV 225 / 0.70.27 es candidata integrada; Diagnostic 104 / extension
  2.0.59 son herramientas locales. El reporte S22 demostro que el segundo video
  fallaba por falta de rearme tras `viewport_changed`, no por cache. En A23,
  Diagnostic 101 reprodujo primer video, scroll y segundo video sin limpiar
  datos: ambos llegaron a `smooth_started`, con cierre durable entre autoridades
  y sin el bucle de scroll previo. Diagnostic 102 corrigio la carrera de
  superficie inicial: HTML5 alcanzo `cover_armed` y YouTube volvio a
  `smooth_started` con imagen visible y muestreo continuo. La fuente HTML5
  externa elegida no entrego cuadros, por lo que esa fila sigue pendiente. JS
  88/88. La auditoria de cierre acepta la evidencia HTML5 fisica anterior de
  120/120 cuadros mas el replay actual; DEV 221 paso todos los gates y en A23
  mostro YouTube fluido con salida AAudio estereo 48 kHz. El protocolo de video
  quedo separado del coordinador (799 lineas), con JS 89/89, unitarios 211/211
  por variante, lint completo y nueva comprobacion A23 de imagen en movimiento,
  audio estereo 48 kHz y cero crash/ANR. El reporte `DAG-HY6MNGK2` aislo ademas
  que `frame_blocked` dejaba terminal el documento. DEV 223 agrega salto seguro
  localizado de hasta cinco pasos de 2 s y cuarentena solo de la generacion
  rechazada; prevalidacion JS 95/95 y unitarios verdes, con prueba fisica S22
  pendiente. GIF seguro conserva animacion. DAG Video premium queda en 90%; video general sigue NO-GO para
  URLs directas, iframes, Shorts, anuncios y redes sociales.
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

1. Construir DEV 225 integrado y probar en S22: tramo filtrado, nota, salto
   seguro y segundo video sin limpiar datos.
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
final del candidato DEV 225 en S22.

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

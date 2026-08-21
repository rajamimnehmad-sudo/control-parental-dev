# CHROME-VISUAL-CONCURRENT-AUDIT-06

Fecha: 2026-08-21. Resultado de auditoria: **PASS**.

## Identidad y alcance

- Owner del area: Proteccion Android.
- Lote principal auditado: `4bbc8ad02376120e16ac9f931b8e563c7d7d1d43..7cd1e633090d9670c0fd7f8514500662544de981`.
- Arquitectura de comparacion:
  `36b7c004f0f19a77439cd90c819b1195ee02cb49` en
  `investigation/chrome-visual-atomic-scroll-05`.
- PR auditado: [#97 — Chrome Visual closure batch 04](https://github.com/rajamimnehmad-sudo/control-parental-dev/pull/97).
- Base declarada por PR #97: `preserve/local-main-2026-08-20` en
  `9e41c309bbf0adceb4a25e817e0a0dc8419d8ac2`.
- Al comenzar esta auditoria PR #97 estaba en `bc38dfa2`; durante la lectura
  avanzo a `102ec195764428c4a57e6840b09167574df3db88`. Este ultimo se registra
  como HEAD observado, pero el objeto principal del ticket siguen siendo los
  siete commits que terminan en `7cd1e633`.
- No se modifico codigo, no se compilo, no se uso ADB/A23, no se hizo merge y
  no se modifico Glosh Central / Control Center.

### Estado observado de PR #97

- Abierto y Draft; GitHub lo informo `MERGEABLE` pero `UNSTABLE`.
- Sin reviews ni threads tecnicos. Los comentarios existentes son el bloqueo
  fisico S22 anterior y el bot de Vercel.
- El gate `Verificar navegador protegido` termino en failure porque
  `:verifyDagDiagnosticUploadConfig` rechazo empaquetar una APK diagnostica sin
  `DAG_DIAGNOSTIC_UPLOAD_TOKEN`. Es un bloqueo de configuracion del workflow:
  no demuestra fallo ni PASS de los cambios Chrome y deja el gate integral sin
  aprobar. `Build, tests, lint, detekt` seguia pendiente al ultimo control.
- Vercel fallaba en `web-super-admin`, area no modificada por este lote. Esta
  auditoria no interpreta checks pendientes o bloqueados por configuracion como
  seguridad visual aprobada.
- El body del PR sigue declarando HEAD de producto `88ca10f6` y un Controller de
  exactamente 500 lineas; ambos datos quedaron desactualizados por los commits
  concurrentes posteriores.

## Decision ejecutiva

Los siete commits corrigen problemas reales del FAILED: enrutan scroll,
precubren los ocho mosaicos tras mutaciones observadas, invalidan buena parte
del trabajo viejo, dejan de hacer remove/add cuando una ventana regional ya
existe y agregan bookkeeping de replay.

No implementan, sin embargo, la arquitectura estricta de `36b7c004`. La
proteccion sigue reaccionando **despues** de que Chrome renderiza, sigue
presentando directamente los pixeles crudos cuando retira overlays y mantiene
ocho o mas ventanas independientes. Las decisiones se aplican region por
region, no mediante una generacion saneada y un swap atomico. El nombre
`AtomicReplay` describe coordinacion logica, no atomicidad de presentacion.

PR #97 puede servir como fuente de piezas, pero no conviene convertir su HEAD
actual en la base de la nueva frontera: el controlador crecio de 500 a 555
lineas, la rama sigue recibiendo commits concurrentes y una evolucion limpia
exigiria reemplazar gran parte de los mismos cambios. Se recomienda un frente
nuevo desde `36b7c004`, conservando dos commits completos y portando conceptos
seleccionados del resto.

## Tabla commit por commit

| Commit | Que hace | Veredicto | Reutilizacion / limite |
|---|---|---|---|
| `689de7c0` `coordinate atomic scroll replay` | Agrega clasificacion de mutaciones, revision activa y seleccion de todos los fallback tiles cuando hay replay | **MODIFY** | Conservar clasificacion de eventos y concepto de revision. Reemplazar coordinador por FSM/epoch monotono ligado a buffers; `clear()` reinicia el contador y el replay no autoriza commits visuales |
| `50b3c97f` `track replay completion safely` | Agrega `completed`, `processedRegionIds` y cancelacion de baseline por contexto | **MODIFY** | Conservar progreso explicito y rechazo de trabajo incompleto. Moverlo a un dirty ledger/generacion; `cancelIfActive()` no es una barrera de presentacion |
| `d4069c67` `update safe overlays in place` | Usa `updateViewLayout()` para cambiar una ventana regional existente sin remove/add | **DROP** como arquitectura | Es una mejora interina valida, pero conserva muchas ventanas. Los paths Allow, `retain`, clip y cierre aun usan `removeViewImmediate`; al siguiente scroll las regiones permitidas deben volver a `addView()` |
| `555f672f` `route scroll accessibility events` | Agrega `TYPE_VIEW_SCROLLED` al filtro y label | **KEEP** | Correcto y reusable como senal de invalidez/actividad; nunca como garantia previa al render |
| `9a8f8c58` `subscribe to scroll events` | Solicita `typeViewScrolled` en el XML DEV | **KEEP** | Correcto para el alcance DEV/API 34. Debe alimentar la nueva FSM, no revelar contenido por si solo |
| `c782a5c6` `precover and replay scroll mutations` | Precubre tras scroll/content event, invalida trabajos, procesa los ocho tiles durante replay y avanza firmas solo procesadas | **MODIFY** | Portar invalidacion temprana, cobertura completa y ledger procesado. Reemplazar el controlador reactivo: la senal llega tarde, precubre con ocho `addView`, analiza serial y retira por region |
| `7cd1e633` `cover atomic replay policy` | Agrega unitarios para event policy, revision stale, cancelacion de baseline y seleccion de fallback tiles | **MODIFY** | Conservarlos como semillas de contrato. No son gates de seguridad: prueban objetos puros, no interleavings del controlador, WindowManager, frames, flashes ni commits de buffers |

## Delta posterior observado en PR #97

No forma parte de los siete, pero condiciona la decision sobre el PR vivo:

| Commit | Evaluacion |
|---|---|
| `f4cc4747` | Distingue scroll, que reinicia geometria, de content change. Concepto reusable en la FSM |
| `4b7358ae` | Evita cancelar una inferencia de baseline por cada lazy event; invalida, precubre y coalesce. Reduce starvation, pero conserva trabajo nativo viejo y arquitectura reactiva |
| `bc38dfa2` | Solo prueba el booleano scroll/content; no prueba starvation ni seguridad visual |
| `102ec195` | Mueve chequeo de identidad y aplicacion de decision al mismo bloque Main, corrigiendo la carrera TOCTOU mas directa detectada en `7cd1e633`. Sigue aplicando/removiendo overlays por region; no es un swap atomico de generacion y no agrega test del interleaving |

Estos commits demuestran actividad concurrente real. No deben mezclarse con el
nuevo frente sin una seleccion explicita del owner.

## Hallazgos tecnicos y riesgos

### Criticos

1. **Exposicion anterior a la cobertura.**
   `TYPE_VIEW_SCROLLED` y `TYPE_WINDOW_CONTENT_CHANGED` llegan luego del cambio
   visible. `precover()` se ejecuta despues y adjunta secuencialmente hasta ocho
   ventanas que ya no existen cuando la generacion anterior fue Allow.

2. **No existe superficie persistente.**
   `ChromeVisualOverlay` sigue siendo un mapa de ventanas por region. No hay
   bitmap saneado, doble buffer, gris neutral ni un host que impida ver Chrome
   crudo durante toda la sesion protegida.

3. **“Complete” no significa presentado atomicamente.**
   Cada region llama `overlay.remove()` o `show()` dentro del `for` y solo al
   terminar se ejecuta `atomicReplayCoordinator.complete(revision)`. El usuario
   observa estados parciales; la revision no gobierna el commit visual.

4. **Carrera stale en el lote exacto `7cd1e633`.**
   Alli se comprueba identidad fuera de Main y luego se entra a Main para mutar
   overlays. Un lazy event puede invalidar y reprecubrir entre ambos pasos; el
   trabajo viejo puede retirar esa cobertura. `102ec195` mueve el chequeo dentro
   del mismo bloque Main y corrige esta TOCTOU concreta, pero no la atomicidad de
   toda la generacion.

5. **ABA posible en la revision.**
   `ChromeVisualAtomicReplayCoordinator.clear()` vuelve `nextRevision` a cero.
   Una revision vieja y otra posterior a cambio de pagina pueden compartir el
   mismo numero. La identidad reduce la ventana, pero una generacion de
   seguridad debe ser monotona durante toda la vida del controlador o combinar
   epoch + sequence sin reutilizacion.

### Altos

6. **Cuatro tiles no reinician autoridad dinamica.**
   Durante replay se analizan los ocho fallback tiles, pero `visuallyChanged`
   todavia proviene de `changedFallbackTiles().take(4)`. Solo esos cuatro llegan
   en `observedChanges`; los otros cuatro pueden conservar `safeSamples=2` y
   revelar una mutacion Allow con una sola muestra, contradiciendo la politica
   de dos confirmaciones.

7. **Persisten remove/add y flashes.**
   `d4069c67` elimina el remove/add solo para una entrada existente. Los caminos
   Allow eliminan la ventana; `retain()` y `clipBottom()` tambien eliminan. El
   siguiente replay vuelve a adjuntarla. No hay transaccion conjunta entre
   regiones y `updateViewLayout()` puede presentar posiciones en frames
   distintos.

8. **Cobertura no confirmada.**
   `precover()` ignora el booleano de `overlay.show()`. Incluso si un add/update
   falla, el log declara `result=success`. No existe ACK de superficie ni
   fallback que conserve un frame seguro.

9. **Navegacion e identidad siguen debiles.**
   `pageIdentity()` solo hashea el titulo. Navegacion SPA o pagina nueva con el
   mismo titulo/viewport puede no requerir baseline; `TYPE_WINDOW_STATE_CHANGED`
   no es mutacion atomica en la nueva policy.

10. **Latencia y cobertura negra no mejoran estructuralmente.**
    Un replay fuerza los ocho mosaicos y la inferencia continua serial bajo el
    mismo `Mutex`. Puede corregir la cobertura posterior al evento, pero repite
    los 4–7 s negros/bordo y no agrega telemetria `prepare/inference/compose`.

11. **Backpressure incompleto.**
    El delta posterior evita cancelar baseline en cada lazy event, pero una
    inferencia ONNX vieja sigue no cancelable. Tormentas fuera de baseline aun
    reinician settle/trabajo; no hay mailbox de ultima generacion ni deadline de
    overload que decida DAG.

12. **Controlador sobredimensionado.**
    En el HEAD observado `102ec195`, `ChromeVisualController.kt` tiene 555
    lineas. El PR aun afirma que tiene exactamente 500 y su justificacion quedo
    obsoleta. Replay/presentacion ya es una responsabilidad separable.

### Tests: que prueban y que no

Prueban correctamente:

- scroll y ciertos content changes se clasifican para replay;
- una revision vieja no puede completar directamente una nueva en el objeto
  aislado, mientras no se llame `clear()`;
- un replay selecciona la lista completa de fallback tiles;
- un baseline puede cancelarse o coalescerse segun la policy.

No prueban:

- que la cobertura exista antes del primer pixel nuevo;
- que `show()`/`updateViewLayout()` hayan sido presentados por SurfaceFlinger;
- que un solo frame visual contenga una generacion coherente;
- TOCTOU event -> identity -> Main commit ni el arreglo de `102ec195`;
- ABA tras `clear()`;
- reset de dos muestras en los ocho dirty tiles;
- add/remove/clip, huecos, fallos de WindowManager o geometria/IME;
- lazy-load continuo, same-title navigation, watchdog o ausencia de evento;
- latencia, CPU, RAM, bateria o cero frames sentinel.

Por eso son tests funcionales utiles, pero no evidencia de seguridad visual.

## Piezas a conservar

- Enrutado y suscripcion de `TYPE_VIEW_SCROLLED` (`555f672f`, `9a8f8c58`).
- Policy que diferencia scroll, content mutation, navegacion y cambio geometrico.
- Epoch/revision monotona y chequeo de autoridad dentro del mismo commit Main;
  tomar la correccion conceptual de `102ec195`.
- `processedRegionIds` y resultado `completed` como insumos del dirty ledger.
- Invalidacion antes de trabajo asincrono y rechazo de resultados viejos.
- Coalescing distinto para fling y lazy-load; una sola captura al estabilizar.
- Seleccion de todos los dirty tiles, sin hacer transparentes los pendientes.
- `updateViewLayout()` solo como conocimiento de geometria, no como superficie.

No hay telemetria nueva reutilizable mas alla del label `replay=...`; debe
implementarse por fase.

## Piezas a reemplazar

- `ChromeVisualOverlay` completo como mapa de ventanas regionales.
- `ChromeVisualAtomicReplayCoordinator` como autoridad final; reemplazar por FSM
  `DISARMED/COVERED/MOTION/SETTLING/CAPTURING/ANALYZING/COMMIT_READY/PRESENTED`.
- El commit por region dentro de `evaluateRegions()`.
- `precover()` basado en ocho add/update independientes.
- Revision reiniciable y page identity basada solo en titulo.
- `changedFallbackTiles().take(4)` como ledger de cambios protegidos.
- Cache cuya firma incorpora coordenadas de pantalla.
- Polling fijo y cancelacion de coroutine como unico backpressure.

## Base exacta recomendada

Crear un frente nuevo desde:

`36b7c004f0f19a77439cd90c819b1195ee02cb49`

Ese commit contiene la base de producto `88ca10f6`, la evidencia fisica
`4bbc8ad` y la decision arquitectonica, sin los commits reactivos concurrentes.

Secuencia recomendada:

1. Cherry-pick limpio de `555f672f` y `9a8f8c58`.
2. Portar, no cherry-pickear, las ideas de `689de7c0`, `50b3c97f`,
   `c782a5c6`, `f4cc4747`, `4b7358ae` y `102ec195` a componentes nuevos.
3. No incorporar `d4069c67`; el nuevo host hace innecesaria la coleccion de
   ventanas regionales.
4. Transformar `7cd1e633`/`bc38dfa2` en replay de FSM e interleavings, no usarlos
   como gate suficiente.

No se recomienda seguir sobre PR #97: seria posible tecnicamente, pero dejaria
historia reactiva a revertir, un controller creciente y una rama con owner
concurrente. PR #97 debe permanecer como experimento/evidencia hasta que
Direccion decida cerrarlo o supersederlo; esta auditoria no realiza esa accion.

## Rutas afectadas

Por los siete commits:

- `app-user/src/dev/res/xml/accessibility_service_config.xml`
- `feature-accessibility/.../service/AccessibilityEventFilter.kt`
- `feature-accessibility/.../chromevisual/ChromeVisualAtomicReplay.kt`
- `ChromeVisualController.kt`
- `ChromeVisualDynamicPolicy.kt`
- `ChromeVisualOverlay.kt`
- `feature-accessibility/src/test/.../ChromeVisualDynamicPolicyTest.kt`

Para la arquitectura nueva tambien se preven cambios dirigidos en:

- `ChromeVisualContract.kt`, `ChromeVisualFrameSignature.kt`,
  `ChromeVisualWindowInspector.kt` y `ChromeVisualRegionAnalyzer.kt`;
- nuevos componentes `ChromeVisualPresentationState`,
  `ChromeVisualProtectedSurface` y `ChromeVisualDirtyLedger`;
- tests/replays nuevos bajo el mismo paquete `chromevisual`.

GloshIA R3.1 y DAG no requieren cambios.

## Owner recomendado

- Owner unico de implementacion: **Proteccion Android**.
- Review obligatorio de invariantes: Direccion Tecnica/Jefe antes de APK.
- DAG solo valida el contrato de fallback; no debe compartir archivos ni asumir
  ownership de la superficie Chrome.
- PR #97 queda congelado para este frente mientras el nuevo owner implementa;
  no mezclar commits posteriores automaticamente.

## Plan minimo de implementacion

1. Contratos puros: FSM, epoch monotono no reiniciable, dirty ledger, mailbox de
   ultima generacion y telemetria por fase.
2. Un `TYPE_ACCESSIBILITY_OVERLAY` persistente con doble buffer; gris neutral o
   ultimo frame saneado, sin transparencia a Chrome crudo.
3. Captura subyacente, dirty tiles completos, analisis incremental y composicion
   offscreen; swap unico con chequeo de epoch dentro de Main.
4. Replay de scroll: trasladar solo pixeles saneados cuando el delta sea valido
   y pintar gris la franja nueva; congelar si no es confiable.
5. Backpressure lazy: coalescer, no cancelar ONNX inutilmente, nunca revelar por
   deadline; overload -> `dag_required`.
6. Gates automaticos; recien entonces una APK y una sesion A23 aprobada.

## Gates PASS / FAIL

### Automaticos

- Un solo host se adjunta una vez por sesion Chrome; durante movimiento/analisis
  no existen `removeViewImmediate/addView` ni ventanas regionales.
- Cada invalidacion cambia epoch antes de trabajo y todo swap verifica el epoch
  dentro del mismo bloque Main; replay determinista cubre todos los
  interleavings stale, incluido lazy durante inferencia y ABA.
- Todos los dirty tiles quedan grises hasta autoridad nueva; no existe limite
  que deje pendientes transparentes y los ocho reinician confirmacion.
- Fallo de captura, composicion, swap, geometria o memoria conserva el ultimo
  frame saneado/gris y produce `dag_required` cuando corresponde.
- Same-title navigation, SPA, scroll rapido/reverso, lazy sin scroll, cambio sin
  evento y watchdog tienen replay determinista.
- Telemetria separa `invalidate/cover/capture/plan/prepare/inference/compose/swap`
  y demuestra ausencia de cola creciente.
- PSS incremental <= 45 MB, sin crecimiento monotono; captura p95 <= 200 ms;
  primer tile safe warm p95 <= 1.200 ms; viewport p95 <= 3.000 ms o permanece
  cerrado con degradacion explicita.

### Fisicos A23

- Fixture sentinel a 60 fps: cero frames de contenido crudo no aprobado, cero
  huecos, flashes y desalineaciones.
- Wikipedia, Google Images permitido/contrastante y Unsplash: carga, diez
  flings, reversa, arriba/abajo, lazy, mutacion sin scroll y same-title nav.
- Frame seguro continuo, franja nueva gris, cache sin reanalisis espurio, Chrome
  operable y sin input fantasma.
- Sin crash/ANR, presion termica sostenida ni degradacion de memoria.

**PASS de implementacion:** todos los gates, en especial cero sentinel frames.
**FAIL:** un frame crudo, swap stale, overlay removido durante proteccion,
dirty transparente, desalineacion, timeout con reveal, crash o ANR.
**BLOCKED:** la captura de la ventana subyacente o el host persistente no puede
mantenerse de forma estable; el resultado debe ser `dag_required`, no retorno al
overlay reactivo.

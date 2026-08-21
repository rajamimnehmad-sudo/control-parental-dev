# CHROME-VISUAL-ATOMIC-SCROLL-05 — investigacion

Fecha: 2026-08-21. Estado de la investigacion: **PASS**.

## Alcance e identidad

- Owner: Proteccion Android.
- Rama de publicacion aislada: `investigation/chrome-visual-atomic-scroll-05`.
- HEAD/base documental inspeccionada: `4bbc8ad02376120e16ac9f931b8e563c7d7d1d43`.
- Base exacta del codigo y APK del FAILED: `88ca10f605ea297c0e303bc35e04ab45937ec636`.
- Evidencia fisica de entrada:
  [`EVIDENCE_2026-08-21_CHROME-GLOSHIA-A23.md`](EVIDENCE_2026-08-21_CHROME-GLOSHIA-A23.md).
- Motor confirmado y fuera de discusion: GloshIA Visual R3.1 ONNX real.
- No se modifico codigo, no se compilo, no se uso ADB/A23 y no se modifico
  Glosh Central / Control Center.

### Colision detectada al cierre

La rama de evidencia estaba en `4bbc8ad` al comenzar, pero avanzo durante esta
investigacion hasta `7cd1e633` con siete commits concurrentes de implementacion
sobre `ChromeVisualController`, `ChromeVisualOverlay`, eventos, replay y tests.
Este ticket no los genero, no los integro y no los audito. Para no mezclar ni
pisar ese trabajo, el informe se publica desde la base documental original en
una rama separada. Antes de implementar la propuesta se debe asignar un owner y
comparar expresamente esos commits con las invariantes y gates de este informe.

## Conclusion ejecutiva

El FAILED no esta en GloshIA. La barrera actual de Chrome es reactiva y usa
ventanas opacas independientes sobre coordenadas de pantalla. En scroll con la
misma ventana, titulo y viewport no precubre: primero Chrome presenta los
pixeles nuevos y despues llegan settle, captura, comparacion e inferencia. Aun
peor, solo toma cuatro de ocho mosaicos cambiados y los cubre uno por uno dentro
del bucle de analisis.

No alcanza con agregar un evento de scroll ni con mover mejor los rectangulos.
`TYPE_VIEW_SCROLLED`, `TYPE_WINDOW_CONTENT_CHANGED` y una captura periodica son
senales posteriores al render. Android 14 tampoco ofrece observacion tactil
pasiva util para este caso: `AccessibilityService.onMotionEvent` retira las
fuentes solicitadas del resto del sistema, y `TouchInteractionController`
requiere touch exploration y cambia la interaccion. Fuentes oficiales:
[AccessibilityService](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService#onMotionEvent(android.view.MotionEvent)),
[AccessibilityServiceInfo](https://developer.android.com/reference/android/accessibilityservice/AccessibilityServiceInfo#setMotionEventSources(int)),
[AccessibilityEvent](https://developer.android.com/reference/android/view/accessibility/AccessibilityEvent#TYPE_VIEW_SCROLLED) y
[TouchInteractionController](https://developer.android.com/reference/android/accessibilityservice/TouchInteractionController).

Por lo tanto, la unica arquitectura dentro de Chrome capaz de sostener la
invariante visual fuerte es una **superficie de presentacion protegida,
persistente y de una sola ventana**: el usuario nunca mira directamente los
pixeles crudos del contenido de Chrome mientras la proteccion esta armada. Ve la
ultima generacion saneada, una traslacion segura de ella durante scroll, o gris
neutral. La siguiente generacion se captura por debajo, se analiza y se cambia
en un unico commit atomico. Si este costo funcional no es aceptable, la opcion
segura es DAG; el overlay transparente/reactivo actual no debe declararse
proteccion estricta.

## Causa raiz exacta en el flujo actual

1. **Scroll estable no exige baseline.**
   `ChromeVisualEventModePolicy.requiresBaseline()` solo devuelve verdadero si
   cambia pagina, ventana, viewport o faltan firmas. Un scroll conserva esos
   valores; `onAccessibilityEvent()` deja `coverageTiles` vacio y espera 150 ms
   antes de verificar
   ([`ChromeVisualController.kt`](../../../feature-accessibility/src/main/java/com/contentfilter/feature/accessibility/chromevisual/ChromeVisualController.kt)).

2. **Ni siquiera se solicita el evento explicito de scroll.**
   Los XML y `AccessibilityEventFilter` omiten `TYPE_VIEW_SCROLLED`. Chrome
   Visual depende de content/window events y del sondeo posterior de 500–1000
   ms. Agregarlo mejora actividad y delta, pero no lo convierte en una senal
   previa.

3. **La cobertura de un cambio llega despues de verlo.**
   `verifyVisualChanges()` captura primero, calcula firmas y recien llama a
   `evaluateRegions()`. Esta ultima hace `overlay.show(Pending)` dentro del
   `for`, justo antes de analizar cada mosaico. Hasta ese punto el mosaico puede
   estar visible.

4. **La mitad de un viewport completamente cambiado queda para otra vuelta.**
   `changedFallbackTiles()` aplica `take(4)` sobre una grilla de ocho. Los cuatro
   restantes conservan la firma anterior para un replay futuro, pero no quedan
   cubiertos preventivamente. Cada vuelta suma al menos otra captura y 500 ms de
   agenda, ademas del analisis.

5. **Una tormenta durante baseline puede validar un frame vencido.**
   `coalesceIfActive()` retorna antes de `identityGate.invalidate()` y antes de
   precubrir. Solo marca `rescanRequested`; el analisis anterior puede seguir
   siendo `current` y retirar cobertura sobre contenido que ya cambio. El
   replay se agenda al finalizar, no invalida inmediatamente.

6. **Las ventanas de overlay no son atomicas.**
   Cada region es un `TYPE_ACCESSIBILITY_OVERLAY` separado. Cualquier cambio de
   geometria o estado ejecuta `removeViewImmediate()` y luego `addView()`. Un
   bloque no se mueve con el documento; queda en coordenadas de pantalla, y el
   remove/add abre una posible ventana sin cobertura. `clipBottom()` repite el
   mismo patron.

7. **La identidad de pagina es debil.**
   `ChromeVisualWindowInspector.pageIdentity()` solo hashea el titulo de la
   ventana. SPA, navegacion con titulo repetido y mutaciones lazy dentro del
   mismo documento pueden heredar ledger/cache/epoch.

8. **La latencia es serial y no cancelable en el punto caro.**
   `evaluateRegions()` recorre regiones en serie y
   `ChromeVisualRegionAnalyzer` agrega otro `Mutex`. Cada region recorta,
   prepara 224x224 y ejecuta R3.1; una decision incierta puede disparar hasta
   cuatro vistas regionales adicionales. Cancelar la coroutine no interrumpe un
   `OrtSession.run()` nativo ya iniciado.

9. **La grilla fija tambien crea huecos conceptuales.**
   Empieza 96 dp debajo del viewport y presupone dos columnas por cuatro filas.
   Toolbar, fullscreen, zoom, insets y geometria accesible variable no estan
   representados por una frontera de contenido demostrada.

## Flujo y origen de los 4–7 segundos

Flujo relevante actual:

`evento -> baseline opcional -> settle -> nodos -> overlays -> captura -> 8/9
regiones -> crop/preproceso/R3.1 serial -> decision y remove/add por region ->
confirmacion posterior de mosaicos`.

La evidencia permite separar lo siguiente:

| Caso | Captura | Total logueado | Regiones | Resto serial |
|---|---:|---:|---:|---:|
| Google Images contrastante | 112 ms | 4.080 ms | 9 | 3.968 ms |
| Unsplash | 118 ms | 7.019 ms | 8 | 6.901 ms |

`totalMs` empieza justo antes de la captura, por lo que no incluye el settle ni
la precobertura inicial. La captura explica solo 2,7 % y 1,7 % de esos lotes.
El resto demostrado incluye crop/preproceso, creacion perezosa de la primera
sesion, una a cinco inferencias por region, chequeos de identidad y commits de
overlay, todo serial. El codigo actual no mide esas subfases por separado: no es
posible atribuir honestamente un porcentaje exacto entre preproceso e inferencia
sin agregar telemetria. El rango residual equivale a aproximadamente 441–863 ms
por region en esos dos lotes, con warm-up y revisiones regionales mezclados.

Los 150 ms de settle no explican el problema principal. Los 500–1000 ms de
verificacion y las dos muestras seguras exigidas por mosaico si prolongan el
tiempo visual despues del primer lote. La creacion/remocion de overlays explica
inestabilidad y flash, no varios segundos de CPU por si sola.

## Alternativas consideradas

| Alternativa | Ventajas | Desventajas | Decision |
|---|---|---|---|
| Agregar `TYPE_VIEW_SCROLLED` y precubrir al recibirlo | Cambio chico; aporta delta y fin de scroll | El evento es posterior; JS/lazy puede cambiar sin scroll; no garantiza el primer frame | Solo senal auxiliar |
| Reposicionar los overlays actuales | Menor cambio; mejora algunos desalineamientos | Ocho o mas ventanas, remove/add, carreras y mosaicos no procesados siguen abiertos | Rechazada como frontera |
| Escudo opaco completo durante eventos | Simple y fail-closed despues de la senal | Puede llegar tarde; tapa todo y repite esperas de 4–7 s | Fallback, no solucion completa |
| Mosaicos/regiones dentro de un unico host | Commit atomico, menos churn, revelado incremental | Si el host deja ver Chrome por transparencia, un cambio autonomo aun se filtra | Componente reutilizable |
| Superficie persistente con frames saneados y doble buffer | Cero pixel crudo mientras esta armada; scroll/replay atomicos; errores quedan cerrados | Cambio arquitectonico; mas RAM; scroll escalonado; riesgo de vista/interaccion desfasada | **Recomendada para Chrome estricto** |
| DAG | Barrera por DOM/respuesta antes de presentar, identidad por recurso, mejor UX por imagen | No es Chrome y posee APIs/ciclo de vida distintos | Fallback obligatorio si se rechaza el compositor |

## Arquitectura recomendada

### Invariante

`presentedGeneration` solo puede ser una generacion completamente saneada. Un
evento, cambio geometrico, captura fallida, epoch nuevo o trabajo vencido nunca
hace visible el bitmap crudo: conserva el ultimo frame seguro o gris.

### Flujo paso a paso

1. Mantener un solo `TYPE_ACCESSIBILITY_OVERLAY` persistente sobre el viewport
   de contenido de Chrome. No retirar/adjuntar vistas por region. Usar dos
   buffers inmutables y un unico swap en main/frame callback.
2. Modelar estados `DISARMED -> COVERED -> MOTION -> SETTLING -> CAPTURING ->
   ANALYZING(epoch) -> COMMIT_READY -> PRESENTED`. Toda entrada invalida primero
   el epoch, incluso si se coalesce con un baseline activo.
3. Solicitar y clasificar `TYPE_VIEW_SCROLLED`, `TYPE_WINDOW_CONTENT_CHANGED`,
   `TYPE_WINDOWS_CHANGED`, `TYPE_WINDOW_STATE_CHANGED`, cambios de bounds/insets
   y un watchdog visual adaptativo. Son disparadores de invalidez, no autoridad
   para descubrir contenido.
4. Durante scroll conservar el frame seguro. Si Chrome entrega un delta
   confiable, trasladar esos pixeles ya aprobados y pintar de gris la franja
   nueva; si no, congelar o cubrir todo. Nunca trasladar overlays ligados al
   documento como si sus coordenadas siguieran siendo validas.
5. Coalescer scroll continuo sin capturar/inferir cada evento. Reiniciar quiet
   time y hacer una captura al estabilizar; aplicar deadline para evitar hambre,
   pero seguir mostrando la generacion segura durante el burst.
6. Capturar una vez el viewport subyacente. Construir una generacion nueva con
   grilla dirty conservadora; todos los dirty quedan grises desde el primer
   buffer. No limitar a cuatro si los demas se vuelven transparentes.
7. Usar mosaicos como frontera de seguridad y nodos accesibles como refinamiento,
   no como unica deteccion. Un mosaico bloqueado puede subdividirse de forma
   acotada para recuperar zonas seguras; solapar bordes evita costuras.
8. Analizar solo firmas nuevas. La clave de cache debe depender del contenido y
   version de politica/modelo, no de la coordenada de pantalla. Preparar el
   siguiente crop en paralelo con la inferencia actual. Empezar con una sola
   cola ONNX; habilitar dos lanes solo si un benchmark A23 prueba mejora sin
   oversubscription ni presion termica.
9. Componer fuera de pantalla: gris para desconocido/no disponible, cobertura
   neutra para bloqueado y pixeles capturados solo para mosaicos permitidos.
   Cada resultado puede producir un frame parcial saneado y atomico; una
   coroutine vieja nunca puede commitear otro epoch.
10. Ante captura segura/no disponible, ambiguedad geometrica, timeout, memoria o
    identidad dudosa: conservar cobertura y declarar `dag_required`.
11. Armar la superficie antes de revelar una navegacion iniciada dentro de
    Chrome y mantenerla entre documentos. El primer ingreso externo a Chrome no
    tiene senal previa garantizada; si tambien se exige cero frame en ese borde,
    Chrome debe abrirse mediante una compuerta Glosh o redirigirse a DAG.

## Paralelismo e incrementalidad segura

- Seguro ahora: firmas, plan de dirty regions y preproceso del proximo crop
  mientras una inferencia corre; composicion offscreen; cache por contenido;
  commits parciales que mantienen gris todo lo pendiente.
- Condicional: dos inferencias concurrentes, solo con contrato explicito de
  sesion/modelo y benchmark A23. R3.1 ya usa dos threads intra-op; mas workers
  pueden empeorar latencia, bateria y memoria.
- No seguro: revelar una region solo porque su nodo no cambio; aplicar resultados
  de un epoch vencido; omitir dirty tiles para cumplir un limite de trabajo;
  hacer transparente el host durante scroll.

## Reutilizacion de DAG

Reutilizar:

- `GloshiaPreparedRasterPolicy`, R3.1, umbrales y fail-closed;
- identidad por generacion, work guard/cancelacion logica y telemetria por etapa;
- principio barrera-antes-de-reveal, decisiones atomicas y replay determinista;
- prioridades e incrementalidad conservando desconocidos ocultos.

No copiar:

- CSS `visibility:hidden`, `MutationObserver`, `IntersectionObserver`, identidad
  por URL/recurso ni el response gate: Chrome externo no expone DOM/red;
- acuses JS/native y lifecycle Gecko;
- la UX por elemento exacto como supuesto de seguridad. En Chrome la frontera
  disponible es un raster de ventana y geometria accesible incompleta.

## Impacto esperado en A23

- **RAM:** el frame 1080x2408 observado ocupa 10.402.560 bytes. Captura mas dos
  buffers ARGB agrega aproximadamente 30 MB brutos; con bitmap de trabajo/crops,
  el pico cualitativo es +30–45 MB sobre el PSS final observado de 180.791 KB.
  Se deben reciclar generaciones vencidas inmediatamente y limitar subdivision.
- **CPU:** una superficie unica reduce WindowManager churn y el sondeo inutil.
  La inferencia sigue siendo el costo dominante. Cache por contenido,
  preproceso pipelineado y no inferir durante el fling deben bajar trabajo total.
- **Bateria/termica:** mejor que capturar cada 500 ms en una pagina estable si se
  usa evento + watchdog adaptativo; peor durante paginas que mutan continuamente.
  El watchdog puede ser lento sin fuga porque se sigue mostrando el frame seguro.
- **Fluidez:** el gesto llega a Chrome, pero la presentacion sera congelada o
  trasladada por deltas y luego saltara a una generacion nueva. Es mas segura y
  estable, aunque no iguala el scroll nativo. Un desfase visual/tactil durante
  analisis es un riesgo explicito y debe tener gate propio.

## Componentes previstos

Cambios de implementacion, no ejecutados:

- `ChromeVisualController.kt`: reemplazar coordinacion por FSM/epoch atomico.
- `ChromeVisualOverlay.kt`: reemplazar ventanas por region por host persistente
  y compositor doble-buffer.
- `ChromeVisualContract.kt` y `ChromeVisualDynamicPolicy.kt`: generaciones,
  dirty ledger, replay y politica de backpressure.
- `ChromeVisualFrameSignature.kt`: hash de contenido separado de geometria.
- `ChromeVisualWindowInspector.kt`: identidad robusta e insets de contenido.
- `ChromeVisualRegionAnalyzer.kt`: telemetria por fase y pipeline acotado.
- `ProtectorAccessibilityService.kt`, `AccessibilityEventFilter.kt` y ambos
  `accessibility_service_config.xml`: enrutar scroll/invalidaciones.
- Nuevos tests/replays bajo `feature-accessibility/src/test/.../chromevisual/`.

No se propone tocar GloshIA R3.1 ni DAG en esta implementacion.

## Plan de implementacion por etapas

1. **Contratos y replay, sin APK:** FSM, epochs, invariantes, trazas sinteticas
   de scroll/lazy/event storm y telemetria separada `cover/capture/plan/prepare/
   inference/compose/commit`.
2. **Host atomico:** una ventana persistente, doble buffer gris y commits sin
   remove/add. Gates automaticos completos.
3. **Captura y analisis incremental:** dirty grid completa, cache sin geometria,
   pipeline de preproceso y una sola inferencia; benchmark local/replay.
4. **Replay de scroll:** delta cuando sea confiable, franja nueva gris, fallback
   congelado; navegacion/SPA/insets/IME.
5. **Una APK y una sesion fisica A23:** solo cuando todo lo anterior este verde.
   Dos lanes ONNX se evaluan despues, no son condicion inicial.

## Gates PASS/FAIL de la siguiente prueba

### Automaticos

- Replay prueba que ningun resultado de epoch viejo se presenta y que toda
  invalidacion deja el viewport en una generacion saneada.
- Event storm durante inferencia invalida antes de coalescer.
- Ocho de ocho dirty tiles quedan cubiertos; backlog no implica transparencia.
- Captura fallida, timeout, cambio de pagina con igual titulo, SPA, bounds/IME y
  cancelacion nativa conservan cobertura.
- Un solo host permanece adjunto; ningun camino usa remove/add para cambiar
  region/estado.
- Telemetria separa todas las fases. Objetivos iniciales A23: commit de cobertura
  dentro de un frame del host ya armado; captura p95 <= 200 ms; primer mosaico
  seguro warm p95 <= 1.200 ms; viewport estable p95 <= 3.000 ms o degradacion
  explicita sin reveal.
- Prueba de 5 minutos: PSS incremental <= 45 MB, sin crecimiento monotono, sin
  cola de epochs, CPU estable sin captura fija a 2 Hz y sin excepciones.

### Fisicos A23

- Fixture con sentinel visual y lazy programado, filmado a 60 fps: **cero frames
  con pixeles raw no aprobados**, cero huecos y cero overlay desplazado.
- Wikipedia, Google Images permitido/contrastante y Unsplash: carga, scroll lento,
  diez flings, reversa inmediata, arriba/abajo, lazy, mutacion sin scroll y
  navegacion con titulo repetido.
- El frame seguro se mantiene continuo; las franjas nuevas son grises hasta
  decision; volver a contenido cacheado no reanaliza sin cambio de firma.
- Sin ANR/crash, sin input fantasma por frame desfasado, sin presion termica
  sostenida y con Chrome aun operable.

**PASS tecnico:** todos los gates anteriores, en especial cero sentinel frames.
**FAIL:** un solo frame crudo, hueco, commit vencido, desalineacion, pagina nueva
sin cobertura, ANR/crash o reveal por timeout. **BLOCKED:** Android/Chrome no
permite mantener la superficie persistente o la captura subyacente de forma
estable; en ese caso la decision segura es `dag_required`, no volver al overlay
reactivo.

## Riesgo arquitectonico

Es inconveniente seguir extendiendo el enfoque actual como una lista de
rectangulos transparentes sobre Chrome. Puede mejorarse visualmente, pero no
convertirse en una barrera previa general porque no posee el pipeline de render
ni una senal universal anterior a cambios autonomos. La siguiente etapa debe
ser un reemplazo acotado de la **capa de presentacion Chrome Visual**, conservando
GloshIA y captura; no otro parche sobre `requiresBaseline`, polling o offsets.
Los commits concurrentes detectados al cierre no deben darse por correctos hasta
pasar esta comparacion.

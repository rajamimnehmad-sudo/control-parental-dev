# DAG v2 Browser Foundation - decisiones

Estado: candidato DEV local del ticket `DAG-V2-BROWSER-FOUNDATION-02`. No implica aprobación
externa ni autoriza Calibración DEV v2, dataset, entrenamiento o Lote 3.

## Evidencia de partida

Se consultó el checkpoint diagnóstico no final
`9f55b6cfd230e020cf7693a4b30a2b665a256c66` sólo mediante `git show` y `git diff`.
No se hizo cherry-pick, merge ni copia masiva.

Hallazgos adoptados:

- los recursos funcionales salen inmediatamente del router y nunca esperan trabajo visual;
- WebView y Service Worker comparten un router puro;
- las imágenes conservan su espacio mediante una respuesta neutra creada localmente;
- el runtime observa sólo imágenes, medios y atributos de fuente;
- no se recorren clases o estilos ni se usa `getComputedStyle`;
- no se reescriben globalmente `src` o `srcset`;
- no se registra, desregistra ni bloquea el ciclo de vida de Service Workers;
- la caché normal y el modo DEV `LOAD_NO_CACHE` son estados explícitos y verificables;
- WebView debugging no forma parte del candidato.

Hallazgos descartados:

- no se copió el loader visual de v1 porque mezclaría modelos, thresholds, cachés y decisiones;
- no se conservó blur o pixelado porque deriva visualmente de píxeles no aprobados;
- no se descargan SVG ni raster porque en este lote no existe ninguna ruta visual aprobada;
- no se incorporó el bloqueo de anuncios de v1: la matriz base debe medir el router v2 sin esa
  variable y sin una dependencia nueva hacia el motor activo;
- no se parchea `history.pushState` o `replaceState`; se usa Navigation API cuando está disponible,
  más `hashchange`, `popstate` y una única comprobación acotada de `location.href` cada 500 ms
  cuando Navigation API no existe, sin modificar estado React o Next.js.

## Frontera e ingreso DEV

- El módulo es `:feature-dag2` y su namespace es `com.contentfilter.user.dag2`.
- App Usuario lo incorpora exclusivamente con `devImplementation`.
- `BuildConfig.DAG_V2_BROWSER_AVAILABLE` vale `true` en DEV y `false` en Beta/Production.
- La Activity `DagV2LabActivity` es `exported=false`, no tiene `intent-filter` y se abre únicamente
  desde `Laboratorio DAG v2` en el menú interno de DAG DEV.
- DAG v1 sigue siendo el navegador predeterminado. Su motor, modelos, thresholds, cachés y
  calibración no se importan ni se modifican.
- El Lab vive en `:dag2`, configura el sufijo de datos WebView `dag2` antes de crear WebView y corta
  la inicialización propia del proceso principal.

## Sesión, análisis y cancelación

Cada documento mantiene un `DagV2DocumentRequestContext` inmutable con `sessionId`,
`navigationToken`, URL principal, origen e inicio, además de `fullAnalysisStarted`,
`fullAnalysisCompleted`, `fullPageAnalysisCount` y `cancelled`.

Una navegación principal, recarga o back/forward cancela el comando anterior, marca la sesión
anterior como cancelada, cancela trabajo visual y crea un token nuevo. Hash, Navigation API,
menús, filtros, modales, carruseles, botones, scroll y lazy loading conservan la sesión. El análisis
completo sólo puede comenzar una vez.

Una solicitud no hereda la sesión vigente. Cada navegación principal crea un WebView y un
`WebViewClient` ligados a su contexto inmutable; una navegación posterior libera ese WebView y crea
otro. Así, incluso una respuesta tardía conserva la generación del WebView que la originó. La carga
principal agrega además un header nativo de generación. El registro de Service Workers, que no
dispone de un WebView propio, atribuye por URL principal exacta, `Referer`, `Origin` y alias SPA
previamente validados. El token identifica la generación; no se trata como secreto. Si la evidencia
es ambigua, el recurso funcional conserva su bypass sin contadores y el visual recibe un
placeholder sin contadores. Los contextos cancelados se conservan durante la vida del Lab para
reconocer trabajo tardío en vez de presentarlo como actual. El puente JavaScript envía ambos
identificadores, debe coincidir con el contexto del WebView y sólo acepta el frame principal y el
origen del documento. Los callbacks duplicados, de iframes o anteriores no cambian visibilidad,
URL, métricas ni `fullPageAnalysisCount`. Redirects principales se detienen y vuelven a entrar por
la validación completa como una generación nueva.

## Recursos e imágenes

La política textual del Lab no mantiene una lista adulta reducida propia. `:feature-dag2` define
un contrato neutral y App Usuario DEV lo adapta al `DagContentClassifier` vigente para consultas,
URLs, resultados, dominios y texto de página. El corpus de paridad comprueba casos seguros,
explícitos, inciertos y dominios listados. La prohibición textual y las categorías adultas de la
lista dinámica se evalúan antes de una regla Admin `Allow`, por lo que esa regla no puede
reemplazarlas. Una consulta, resultado, ruta SPA o página incierta no se aprueba. El adaptador no
importa modelos visuales, thresholds ni calibración v1.

El router decide por `Sec-Fetch-Dest`, después `Accept` y finalmente extensión. HTML, CSS,
JavaScript, JSON, XHR, `fetch`, fuentes, manifests, módulos, RSC y scripts de Service Worker
devuelven `null` inmediatamente. Una URL sin extensión y sin evidencia visual no entra al gateway.

Todo raster y SVG usa `DagV2FailClosedImageDecisionProvider`, cuya única decisión es `Hide`.
`DagV2NeutralImageFactory` genera un SVG gris constante sin leer, descargar, difuminar, pixelar ni
copiar el recurso original. El runtime bloquea `data:`, `blob:`, canvas, video, audio, objetos y SVG
inline; sólo permite iframes CAPTCHA HTTPS cerrados. Los fondos remotos se protegen por el mismo
router visual. Se retiró la regla global `html * { background-image: none !important; }` porque
rompía iconos y controles funcionales.

El `ServiceWorkerClient` existe únicamente dentro del proceso aislado. Sin una sesión DAG v2 activa
devuelve `null`; con una sesión activa exige atribución por `Origin`, `Referer`, URL/origen y
contextos registrados. Un recurso funcional no atribuible devuelve `null` sin métricas; uno visual
no atribuible queda neutro. Una solicitud atribuida a una generación cancelada no cambia la nueva.
No guarda referencias a Compose, Activity o WebView. Permanece instalado durante la vida del
proceso `:dag2`: retirarlo al destruir una Activity producía una carrera con una reapertura
inmediata del Lab, mientras que sin sesión activa ya es inerte.

## Destinos de red

`PublicNetworkDestinationGuard` es un contrato neutral compartido por el loader de imágenes v1 y
la base v2. Rechaza de inmediato esquemas no HTTPS y literales IPv4/IPv6 privados, loopback,
link-local, multicast, CGNAT, documentación, benchmark, traducción IPv4 especial, hosts numéricos
ambiguos, nombres locales o especiales y rangos reservados cubiertos. La navegación principal
resuelve DNS fuera del hilo de WebView, con timeout, y exige que todas las respuestas sean
públicas. Navegaciones y redirects que no pueden atribuirse con seguridad fallan cerrados.
Recursos WebView y Service Worker aplican el control inmediato sin DNS síncrono dentro de
`shouldInterceptRequest`.

Riesgo residual explícito: WebView resuelve por su cuenta los hostnames de subrecursos funcionales
que deben continuar sin proxy. El chequeo DNS previo de la navegación no fija esa resolución a la
conexión posterior, por lo que esta base no declara mitigación completa de DNS rebinding para esos
subrecursos. Las imágenes no tienen ese riesgo en este lote porque el router responde localmente
sin descargar el original. Cerrar el riesgo funcional exige una capa de red que pueda fijar y
validar la conexión sin romper HTML, scripts, JSON, fuentes, React, Next.js o Service Workers; queda
fuera de este ticket y no se presenta como resuelto.

## Lifecycle de WebView

Cada documento usa un WebView nuevo, por lo que su cliente, bridge y callbacks conservan la
generación de origen. Al bloquear, volver a resultados, cambiar de documento o cerrar el Lab,
Compose libera el WebView mediante `onRelease`; además, un host propiedad de la Activity ejecuta la
misma liberación si Android destruye la pantalla antes de que Compose alcance ese callback.
La secuencia detiene la carga, navega a `about:blank`, retira script y puente, neutraliza clientes y
descargas, cancela análisis pendientes y destruye el WebView. De ese modo el documento anterior no
continúa ejecutándose ni puede alcanzar una sesión posterior.

La apertura del Lab adquiere una generación de lifecycle y reinicia estado, historial y callbacks.
Una Activity anterior que termina tarde no puede cerrar la generación nueva. Esto evita conservar
la URL o el WebView anterior, elimina la pantalla blanca observada al cerrar y reabrir rápidamente,
y garantiza una sola liberación aun cuando coincidan `onRelease` y `onDestroy`.

## Correcciones surgidas de la validación física

La primera carga física quedó detenida antes del análisis porque una navegación principal sin
`Sec-Fetch-Dest` llevaba un `Accept` amplio que incluía formatos de imagen. El router priorizaba
esa señal y devolvía un placeholder como documento HTML. La regla general corregida es que
`isForMainFrame` siempre clasifica como `MainDocument`; el test de regresión cubre el `Accept`
mixto real.

La segunda corrección fue de lifecycle: cerrar y reabrir el Lab podía conservar estado o dejar una
pantalla blanca por la destrucción tardía de la Activity anterior, y cerrar el router de recursos
dejaba el pipeline inutilizable para la reapertura. El host explícito, el gate de generación y el
reset completo de sesión corrigen la causa general sin condiciones por sitio.

## Corrección de Android CI

El run `30180080622`, job `89735049544`, falló en `Calidad compartida` porque
`DagV2LabEntryTest` estaba en `app-user/src/test` y afirmaba que el flag DEV era `true`. La tarea
global `test` también ejecuta Beta Debug, Beta Compatibility y Beta Release, donde el flag debe ser
`false`; por eso fallaron esas variantes. El test se movió a `src/testDev`, sin cambiar flags ni
manifiestos. La comprobación común de aislamiento continúa verificando que Beta y Production no
incluyen el Lab.

El run correctivo `30182570727`, job `89741575612`, completó correctamente build DEV y
`Calidad compartida` en el head `1714235431961d4f093a3f64cdc6f2c446e19e51`. El comando local
equivalente `./gradlew testDevDebugUnitTest test ktlintCheck lintDevDebug detekt` y las tareas
específicas de App Usuario y `:feature-dag2` también finalizaron correctamente.

## Validación física SM-A235M

Se instaló in-place la APK DEV local en el Samsung SM-A235M `R58T34V31AE`, Android 14/API 34,
build `UP1A.231005.007.A235MUBSAEYB1`, sin desinstalar ni borrar datos. La APK conserva
`versionCode 279`, `versionName 1.0.1-dev` y SHA-256
`ea6403d607b259ddde431fbad399315e6f94297b73e2156c450a24cc8d2e614a`.

La matriz completa ejecutó 10 aperturas normales y 10 con `Sin caché DEV` por sitio, 60 aperturas
en total, manteniendo cada una al menos 20 segundos. Los tiempos son desde `document_started` hasta
`structure_visible`:

| Sitio | Modo | Aperturas estables | Estructura mín./prom./máx. | Placeholders aprox. | `console_error` |
| --- | --- | ---: | ---: | ---: | ---: |
| Frávega | Normal | 10/10 | 3,539 / 3,886 / 4,504 s | 1.887 | 154 |
| Frávega | Sin caché | 10/10 | 2,838 / 3,163 / 3,705 s | 844 | 795 |
| Mimo | Normal | 10/10 | 0,288 / 0,417 / 1,363 s | 228 | 277 |
| Mimo | Sin caché | 10/10 | 0,460 / 0,605 / 1,331 s | 232 | 275 |
| Cheeky | Normal | 10/10 | 1,410 / 1,989 / 4,069 s | 694 | 62 |
| Cheeky | Sin caché | 10/10 | 1,812 / 2,119 / 2,672 s | 693 | 61 |

Cada grupo registró 10 `document_started`, 10 `document_committed`, 10
`full_page_analysis_started`, 10 `full_page_analysis_completed`, 10
`full_page_analysis_count`, 10 `structure_visible` y 10 `functional_stable_20s`.
`stale_result_discarded` y `renderer_gone` fueron 0 en los seis grupos. El cambio entre aperturas
produjo 10 `session_cancelled` por grupo, salvo Cheeky normal con 9 porque la primera apertura no
tenía una sesión previa.

Los contadores de consola son eventos internos sanitizados del sitio; no guardan mensaje, URL,
consulta ni contenido. No hubo error fatal asociado, crash, ANR ni `renderer_gone`. En Frávega no
apareció `Cannot read properties of undefined (reading 'length')`, `#__next` no colapsó y la
estructura continuó visible después de 20 segundos. La temperatura de batería pasó de 29,0 °C a
30,0 °C durante la secuencia prolongada conectada por USB, sin síntoma térmico.

En los 60 documentos `full_page_analysis_count` fue exactamente `1`, hubo
`functional_stable_20s` y ninguna respuesta tardía cambió una sesión nueva. Menús, categorías,
tabs, modal, carrusel, scroll, lazy loading, enlaces, back, forward y recarga continuaron
respondiendo. Mimo abrió y aplicó su drawer de filtros y acordeones como ruta SPA sin repetir el
análisis. Frávega abrió y cerró el modal de ubicación, navegó categorías y conservó scripts, JSON y
RSC; el rótulo `FILTRAR` de la categoría probada no abrió un panel al toque, mientras las demás
interacciones y enlaces sí respondieron. Cheeky navegó inicio/categoría y back/forward; los grandes
placeholders de la vista móvil limitaron el acceso visual a algunos controles inferiores, sin
colapsar la estructura.

Todas las superficies raster observadas permanecieron como rectángulos neutros; no apareció una
fotografía real ni siquiera brevemente. Las imágenes lazy recibieron placeholders individuales sin
loader o reanálisis global. WebView debugging permaneció desactivado y no existió socket devtools.
No existe condición, allowlist ni excepción específica para Frávega, Mimo o Cheeky.

## Modo sin caché

`Sin caché DEV` cambia WebView y Service Worker a `LOAD_NO_CACHE`, ejecuta `clearCache(true)` y crea
una navegación nueva. El estado sólo existe en el Lab, que no se compila en Beta/Production.

## Rollback

Mientras el PR siga sin fusionar, dejar de usar la rama devuelve inmediatamente al `origin/main`
sin DAG v2. Si se fusionara en el futuro, el rollback debe hacerse mediante un ticket aprobado que
revierta el merge de PR #68 o, como contención DEV, ponga `DAG_V2_BROWSER_AVAILABLE=false` y retire
`devImplementation(project(":feature-dag2"))`. No borrar DAG v1, modelos, datos ni muestras.

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
No guarda referencias a Compose, Activity o WebView y se retira al cerrar el Lab.

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
Compose libera el WebView mediante `onRelease`.
La secuencia detiene la carga, navega a `about:blank`, retira script y puente, neutraliza clientes y
descargas, cancela análisis pendientes y destruye el WebView. De ese modo el documento anterior no
continúa ejecutándose ni puede alcanzar una sesión posterior.

## Corrección de Android CI

El run `30180080622`, job `89735049544`, falló en `Calidad compartida` porque
`DagV2LabEntryTest` estaba en `app-user/src/test` y afirmaba que el flag DEV era `true`. La tarea
global `test` también ejecuta Beta Debug, Beta Compatibility y Beta Release, donde el flag debe ser
`false`; por eso fallaron esas variantes. El test se movió a `src/testDev`, sin cambiar flags ni
manifiestos. La comprobación común de aislamiento continúa verificando que Beta y Production no
incluyen el Lab.

## Matriz Frávega A-F

La matriz física no se ejecutó porque ADB detectó sólo un SM-A235M, que no se instaló ni modificó,
y no había un SM-S908E conectado. La evidencia local separa las variables sin declarar resultados
de sitio:

| Caso | Estado local | Evidencia |
| --- | --- | --- |
| A. WebView mínimo | Diseñado, físico pendiente | Los recursos funcionales usan WebView normal |
| B. Runtime sin intercepción | Unitario correcto | Runtime idempotente, un observer, sin patch de history; fallback SPA único |
| C. Runtime + router de test | Unitario correcto | HTML/CSS/JS/JSON/fuentes evitan el gateway |
| D. Fail-closed real | Unitario correcto | No existe ruta `approved`; raster y SVG son neutros |
| E. Service Worker | Unitario correcto | Router común; atribución inmutable y descarte tardío |
| F. Bloqueo de anuncios | Excluido deliberadamente | No se importó la política v1 ni una excepción de sitio |

No existe condición, allowlist ni excepción específica para Frávega, Mimo o Cheeky.

## Modo sin caché y prueba física pendiente

`Sin caché DEV` cambia WebView y Service Worker a `LOAD_NO_CACHE`, ejecuta `clearCache(true)` y crea
una navegación nueva. El estado sólo existe en el Lab, que no se compila en Beta/Production.

Con el SM-S908E conectado y autorizado:

```bash
adb -s SERIAL install -r app-user/build/outputs/apk/dev/debug/app-user-dev-debug.apk
adb -s SERIAL shell am start -n com.contentfilter.user.dev/.dag.DagLauncherAlias
adb -s SERIAL logcat -c
adb -s SERIAL logcat -s DagV2Metrics:I chromium:E AndroidRuntime:E
```

Desde el menú de DAG elegir `Laboratorio DAG v2`. Para cada sitio ejecutar diez aperturas con caché
normal y diez con `Sin caché DEV: activo`, esperar 20 segundos y recorrer menú, categoría, filtro,
acordeón/modal, carrusel, lazy loading, back/forward y recarga. No usar `pm clear`, no desinstalar y
no abrir la Activity interna directamente.

Métricas esperadas por sesión:

```text
document_started
document_committed
full_page_analysis_started
full_page_analysis_completed
full_page_analysis_count
structure_visible
visual_placeholder_ready
stale_result_discarded
session_cancelled
functional_stable_20s
```

## Rollback

Mientras el PR siga sin fusionar, dejar de usar la rama devuelve inmediatamente al `origin/main`
sin DAG v2. Si se fusionara en el futuro, el rollback debe hacerse mediante un ticket aprobado que
revierta el merge de PR #68 o, como contención DEV, ponga `DAG_V2_BROWSER_AVAILABLE=false` y retire
`devImplementation(project(":feature-dag2"))`. No borrar DAG v1, modelos, datos ni muestras.

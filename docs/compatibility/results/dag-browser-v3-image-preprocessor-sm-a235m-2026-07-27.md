# DAG Browser V3 image preprocessor - Samsung SM-A235M - 2026-07-27

## Cortes probados

Preprocesador:

- codigo integrado: `ecd7a33a225a597246d4dfbc99ead00c82a8eeeb` (`#79`);
- aplicacion: `com.contentfilter.dagbrowser.dev`;
- version: `0.3.0-dev` (`versionCode 4`);
- APK DEV firmada: `102598878` bytes;
- SHA-256: `cc550545200a37d9a10051d03014209f468d26c468b54f90881bfe636a57e8e4`;
- workflow firmado: GitHub Actions `30311835989`.

Instrumentacion usada para la matriz fija:

- codigo integrado: `dab405aec12200c75a33abbad35ed003f2cff54d` (`#80`);
- version: `0.3.0-dev` (`versionCode 5`);
- extension incorporada: `Glosh DAG Protection 1.2.0`;
- APK DEV firmada: `102605578` bytes;
- SHA-256: `72a976dcafe1512f8afe8381936fc4ebebfadf4e66efff716ada0a73786e86c8`;
- certificado SHA-256:
  `d51bc0dabd280ce1b0f098ae168eb57758faeba301156cde835737835f8a8832`;
- workflow firmado: GitHub Actions `30312994839`.

Ambos cortes se probaron sobre el mismo Samsung SM-A235M `R58T34V31AE`, `arm64-v8a`, con Android
14/API 34. Production y las aplicaciones Glosh Usuario/Admin no se modificaron.

## Verificacion automatica

Pasaron:

- unitarios del contrato de geometria, limites y cierres del preprocesador;
- `ktlintCheck`, `testDevDebugUnitTest`, `assembleDevDebug` y Android Lint;
- checks de pull request `Build, tests, lint, detekt` y `Verificar navegador protegido`;
- sintaxis de la extension;
- simulacion del evento de rendimiento: no informa imagenes listas mientras queda un filtro o pedido
  nativo activo, y solo lo hace despues de 250 ms sin trabajo.

La instrumentacion no participa en la decision visual y se registra solamente en DEV, sin URL,
texto, Base64 ni pixeles.

## Preprocesador fisico

| Caso | Resultado |
| --- | --- |
| Google Imagenes `bosques`, primer lote | Los dos primeros trabajos paralelos demoraron 40 y 48 ms; los siguientes, entre 5 y 16 ms |
| Google Imagenes `bosques`, scroll rapido | 30 imagenes, 1364889 bytes comprimidos; ninguna foto visible |
| Latencia de esas 30 imagenes | 8,33 ms promedio; p50 8 ms; p95 16 ms; maximo 21 ms |
| JPEG de 35588 bytes | RGB preparado; `analyzer_unavailable` en 7 ms; bloqueado |
| PNG de 8090 bytes | RGB preparado; `analyzer_unavailable` en 6 ms; bloqueado |
| WebP de 10568 bytes | RGB preparado; `analyzer_unavailable` en 9 ms; bloqueado |
| GIF estatico de 43 bytes | RGB preparado; `analyzer_unavailable` en 3 ms; bloqueado |
| GIF animado de 2145 bytes | `animated_image` en 8 ms; bloqueado |
| Cuerpo corrupto de 1024 bytes | `unsupported_image` en 8 ms; bloqueado |
| Arranque frio con Google Imagenes `paisajes` | Los dos primeros trabajos paralelos demoraron 43 y 42 ms; los siguientes, entre 4 y 9 ms |

La captura posterior al scroll rapido mostro:

- PSS total: `228476 KiB`;
- RSS total: `316804 KiB`;
- Java heap PSS: `5596 KiB`;
- native heap PSS: `11604 KiB`;
- graphics PSS: `43828 KiB`.

Es una fotografia puntual del proceso. El estado termico fue `0`, sin limitacion: AP `24,8 C`,
bateria `26,9 C` y piel `28,7 C`. No se observaron crash, ANR ni `OutOfMemoryError`.

## Matriz fija en frio

`pm clear --cache-only` no termino en este Samsung y se interrumpio sin cambiar de paquete. Para no
presentar una medicion asistida por cache, antes de cada sitio se ejecuto
`pm clear com.contentfilter.dagbrowser.dev`. Esta alternativa mas estricta borro solamente el perfil
local de la aplicacion DEV, conservo el APK firmado y obligo a Gecko y a la extension a iniciar desde
cero.

Cada recorrido uso proceso frio, un `codexperf` superior unico, registro limpio y la misma red y
telefono. Se espero la marca `viewport_images_ready`, se tomo una captura y luego se hicieron tres
desplazamientos para forzar imagenes tardias.

Las tres cifras de tiempo son `pagina / imagenes iniciales / estructura visible`:

| Sitio | Tiempos | Imagenes procesadas, incluido scroll | Latencia local p50 / p95 / maximo | Resultado visual |
| --- | ---: | ---: | ---: | --- |
| Fravega | 13172 / 19385 / 1361 ms | 65 | 9 / 29 / 64 ms | Pagina, modal, productos y precios utilizables; raster ausente |
| Mimo | 6237 / 14280 / 596 ms | 21 | 1 / 21 / 25 ms | Promociones y pie utilizables; raster ausente |
| Cheeky | 6346 / 29550 / 2122 ms | 16 | 1 / 47 / 47 ms | Busqueda, texto y registro utilizables; raster ausente |

Las tres navegaciones informaron `page_analysis_ready success=true` y las tres completaron
`viewport_images_ready`. En total se procesaron 102 respuestas y 2345058 bytes comprimidos. Todas
terminaron en cierre: 79 `analyzer_unavailable`, 22 `unsupported_image` y una `animated_image`. No
hubo `allow`, `blur`, `analyzer_busy`, timeout ni fotografia o destello visible.

Cheeky necesito 29,55 segundos para que el documento y la actividad de imagenes quedaran quietos,
pero la estructura protegida ya era utilizable a los 2,12 segundos. Es una muestra operativa
sensible al sitio y a la red, no un percentil del producto.

## Memoria y temperatura de la matriz

| Sitio | PSS total | RSS total | Native heap PSS | Graphics PSS |
| --- | ---: | ---: | ---: | ---: |
| Fravega | 292262 KiB | 360276 KiB | 7824 KiB | 43724 KiB |
| Mimo | 250527 KiB | 366140 KiB | 11604 KiB | 43884 KiB |
| Cheeky | 276025 KiB | 395440 KiB | 11276 KiB | 43644 KiB |

Al terminar la matriz el estado termico seguia en `0`: AP `25,3 C`, bateria `27,1 C`, PA `27,9 C`
y piel `29,6 C`. Los registros limpios de los tres sitios no contienen `FATAL EXCEPTION`, ANR,
`OutOfMemoryError`, `renderer gone` ni senal fatal.

## Lectura del gate

El gate del preprocesador local queda completo en el telefono objetivo:

- reduce y normaliza respuestas reales con memoria y concurrencia acotadas;
- rechaza animacion, contenido corrupto y formatos no admitidos;
- no guarda ni sube imagenes;
- cualquier error o ausencia del modelo sigue bloqueando;
- la matriz fija mantuvo estructura y controles sin exponer pixeles fotograficos.

El corte todavia no contiene un clasificador y por eso no muestra ninguna foto. El siguiente gate es
comparar modelos dirigidos con el mismo corpus independiente y calibrar umbrales; `allow` y `blur`
permanecen prohibidos hasta cerrar precision, latencia y pruebas de fuga.

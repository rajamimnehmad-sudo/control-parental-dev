# DAG 153 - seguridad, scheduler acotado y A/B ONNX

Fecha: 2026-08-06
Dispositivo: Samsung SM-S908E, Android 16, arm64-v8a
APK: `0.69.57-dev`, `versionCode 153`
Extensión: `1.73.0`
APK SHA-256: `48eb4fc7d6ac4b86cf328b1c6932cbfe166baa7cad6c85f0dc1b9d2bdd204f46`
Modelo: GloshIA Visual R3.1, sin cambios de modelo, umbral o política

## Alcance

El lote auditó cinco puntos concretos del navegador y mantuvo como base el
arreglo general de DAG 147. No agregó excepciones por sitio, URL, dominio o
dispositivo. No tocó Supabase, Production, GitHub ni datos del usuario.

## 1. Navegación HTTP

Permitir HTTP en el flavor DEV era una regresión de seguridad: el tráfico en
texto claro puede ser observado o modificado en tránsito. La entrada explícita
`http://` vuelve a convertirse a HTTPS y una navegación superior no HTTPS falla
cerrada. El fixture loopback HTTP permanece limitado por
`BuildConfig.GLOSHIA_LAB_FIXTURE` al flavor LAB aislado.

Los unitarios fijan tres contratos: upgrade de entrada HTTP, rechazo de
navegación superior HTTP y excepción sólo para el fixture LAB.

## 2. Cola del scheduler

La cola `pending` del guard podía crecer sin límite si el productor superaba la
extracción de una señal cada 16 ms. Se agregó un máximo de 64 señales. Al
alcanzarlo, el guard cancela el temporizador, entrega la cola completa en orden
con `MessagePort.prototype.postMessage` nativo, desactiva el yield y entrega el
mensaje corriente. Es un fail-open de scheduling, no de contenido: no pierde,
duplica ni reordena mensajes, y no interviene en la compuerta de imágenes.

Una prueba conductual entrega una ráfaga de 65 señales, comprueba orden exacto,
ausencia de pérdida y que no quede temporizador pendiente. Otra prueba lee el
manifest real y fija la instalación en `MAIN`, sólo en el frame superior.

## 3. Política sin integración

`DagLowInformationRasterPolicy.kt` sólo era referenciado por su propio test. No
existía ninguna llamada desde preprocesamiento, analizador, transporte o
actividad. Se retiraron la clase y el test en vez de mantener una política
paralela que no afectaba decisiones reales.

## 4. Entradas incrementales de Gradle

`testDagProtectionJs` ahora declara `ads.js`, `runaway-scheduler-guard.js` y
`manifest.json`, además de las entradas anteriores. Los tests JVM declaran el
directorio `src/main/assets/dag-protection`, porque sus contratos leen esos
assets dinámicamente. Un cambio de extensión ya no puede reutilizar un resultado
de test que corresponda a otro contenido.

## 5. Competencia de CPU y decisión A/B

Se compararon dos APK sobre el mismo perfil y el mismo S22, con dos workers
Android y ONNX intra-op 2/inter-op 1:

| Variante Frávega | FPS activo | Frame p95 | Inferencia p95 | Resultado |
| --- | ---: | ---: | ---: | --- |
| Spinning ON, configuración anterior | 58,65 | 21,18 ms | 213,5 ms | Conservada |
| Spinning intra-op OFF | 60,00 | 21,98 ms | 271 ms | Retirada |

Desactivar spinning mejoró el promedio gráfico sólo 1,35 fps, mientras el p95
de inferencia empeoró aproximadamente 27 %. Como el usuario ya había observado
fotos tardías con menos capacidad de análisis, no se redujo a un worker ni se
conservó la variante. La configuración final sigue en dos workers Android,
intra-op 2 e inter-op 1.

## Recorrido físico final

- Frávega: Ofertas Únicas mostró tarjetas y fotos reales, sin rectángulos negros
  o grises generados por DAG. El carrusel dio 58,65 fps, p95 21,18 ms, un cuadro
  activo mayor a 33 ms y máximo 68,17 ms.
- Mimo: después de bajar, el menú abrió completo. La ventana medida dio 55,42
  fps, p95 30,08 ms y tres cuadros mayores a 33 ms.
- Cheeky: banner, menú, cuenta, favoritos y bolsa terminaron visibles.
- No aparecieron crash, ANR ni OOM.

El APK final se instaló con `adb install -r`; versión y datos del perfil se
preservaron. GloshIA Visual R3.1, `final_sealed`, el umbral y la política visual
permanecen intactos.

## Validación automatizada

- `testDagProtectionJs`: 21/21.
- `testDevDebugUnitTest`: correcto.
- `ktlintCheck`: correcto.
- `lintDevDebug`: correcto.
- `assembleDevDebug`: correcto.

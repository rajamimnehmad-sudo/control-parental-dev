# DAG Browser V3 - pipeline visual vigente

## Autoridad

GloshIA es la unica autoridad para raster. DAG intercepta los bytes que Gecko
iba a consumir, los analiza localmente y solo devuelve el original exacto ante
`model_allow`. `model_filter`, error, timeout, saturacion o formato invalido
devuelven un PNG neutro proporcional sin pixeles del recurso rechazado.

No hay API, Supabase, segunda descarga ni decision por URL. El modelo, pesos y
umbrales son los documentados en el handoff vigente.

## Contrato de decisión `dag-36`

La decisión oficial es única en Android y en el laboratorio local de GloshIA:

- imagen completa `>= 0,40`: filtrar inmediatamente;
- imagen ordinaria entre `0,30` y `0,40`: revisar cuatro cuadrantes y filtrar
  si cualquiera alcanza `0,45`;
- imagen panorámica: filtrar por una región `>= 0,70` o por dos regiones
  `>= 0,50`;
- cualquier valor inválido, modelo ausente o trabajo vencido: fallar cerrado.

La marca diagnóstica `full_strong` distingue scores completos `>= 0,95`, pero
no es otro umbral de decisión: está contenido dentro del bloqueo canónico
`>= 0,40`. DEV nunca permite que cuadrantes débiles veten una señal completa
ya bloqueada. No existe un modo de compatibilidad que permita raster sin
clasificar ni una redacción visual alternativa en el APK.

## Recorrido de red

1. `onBeforeRequest` abre `filterResponseData` para `image` e `imageset`.
2. `onHeadersReceived` aplica la misma compuerta a cualquier respuesta con MIME
   raster, incluida una imagen solicitada mediante `fetch`/XHR o como pagina
   principal.
3. Cada stream retiene como maximo 2 MiB y el conjunto hasta 8 MiB.
4. SHA-256 deduplica decisiones efimeras por contenido.
5. Como maximo dos inferencias nativas trabajan simultaneamente y 144 esperan
   en la cola JavaScript acotada; Android mantiene una cola adicional de ocho.
6. Android valida sobre, Base64, formato y dimensiones; preprocesa localmente y
   ejecuta un unico modelo ONNX.
7. Background escribe el original exacto solo ante `model_allow`; cualquier
   otro resultado escribe un placeholder neutro y cierra el stream.

SVG e iconos seguros usan la politica vectorial aislada. Audio, video, canvas,
object y embed permanecen bloqueados por contratos separados.

## Primer pintado

`webRequest` solo observa HTTP(S). Por eso `barrier.css`, inyectado en
`document_start`, mantiene neutrales las fuentes `data:`/`blob:` y los raster
que aun no alcanzaron estabilidad.

Una pagina puede cargar varias resoluciones sucesivas de una misma tarjeta.
Cada respuesta HTTP(S) atraviesa la compuerta antes de llegar a Gecko, por lo
que `barrier.js` marca un `img` completo como estable sin espera artificial
(`0 ms`). Una imagen HTTP(S) ya estable permanece visible cuando la pagina
cambia `src`, `srcset` o `sizes`: los nuevos bytes siguen retenidos por la
compuerta y Gecko conserva el recurso anterior hasta recibir el original
permitido o el placeholder filtrado. Las fuentes inline `data:`/`blob:` se
ocultan de nuevo porque no atraviesan `webRequest`; `<img>` y `srcset` acotados
se convierten en bytes y llegan a la misma compuerta R3.1. Cada frame registra
su identidad documental para que una fuente inline dentro de un iframe no se
confunda con la pagina superior.

El observador:

- atiende solo nodos de imagen nuevos y esos tres atributos;
- no reescribe `src` ni `srcset`;
- no asocia decisiones de GloshIA con elementos;
- no consulta geometria, estilos ni el DOM durante scroll;
- no contiene dominios, comercios o telefonos especiales;
- deja SVG e iconos vectoriales seguros fuera de la espera.

Al refrescar el mismo documento, DAG conserva visible la version ya protegida
mientras espera el nuevo `barrier-ready`. Una URL distinta, un primer ingreso o
un fallo de barrera siguen usando cobertura total. Esto elimina el apagado de
aproximadamente 1,2 segundos medido en Google Imagenes sin relajar la compuerta
de raster.

## Limites y fallo seguro

- recurso: 2 MiB;
- captura agregada: 8 MiB;
- streams activos: 128;
- cola JS: 144;
- inferencias nativas simultaneas: 2;
- captura: 5.000 ms;
- respuesta nativa: 2.250 ms;
- cache efimera: 512 hashes.

Animaciones no autorizadas, recursos demasiado grandes, modelo ausente,
decodificacion invalida, desconexion o timeout fallan cerrados. No existe un
flavor Android LAB ni una excepcion HTTP de loopback; cualquier fixture de
navegador debe usar HTTPS confiable.

## Variantes y diagnostico

- DEV es la aplicacion de uso normal y no crea trazas ni logs por imagen.
- Diagnostic ejecuta exactamente el mismo modelo y politica, pero habilita
  trazas nativas y resumenes acotados de saturacion, timeout, bytes, decode y
  carrier. Esos resumenes no incluyen URL, consulta ni pixeles.
- GloshIA Lab permanece como herramienta local en `tools/gloshia_lab/`; no es
  otro navegador ni otra politica instalable.

## Limite de cobertura pendiente

Los raster `data:`/`blob:` declarados dentro de una hoja CSS o pseudo-elemento
no pasan por `webRequest`. Bloquear o inspeccionar globalmente todos los estilos
computados agregaria trabajo de DOM durante carga y scroll. Antes de adoptar
esa estrategia se debe medir el carrier en Diagnostic y validar una compuerta
general sin destellos ni regresion. No se acepta una excepcion por sitio.

## Evidencia

- estado tecnico: `docs/HANDOFF_ACTUAL.md`;
- metricas: `docs/dag/v3/DAG_BROWSER_V3_PERFORMANCE_METRICS.md`;
- resultado DAG 68:
  `docs/compatibility/results/dag-browser-v68-zero-delay-refresh-sm-s908e-2026-08-02.md`;
- historial: `docs/compatibility/results/dag-performance-history.md`.

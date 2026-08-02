# DAG Browser V3 - transporte seguro de imagenes

## Decision vigente

DAG intercepta la respuesta original mediante
`webRequest.filterResponseData`, conserva la barrera visual de
`document_start` y analiza localmente los mismos bytes que Gecko iba a mostrar.
No vuelve a descargar imagenes HTTP(S) desde Android, no usa una URL como
permiso y no consulta un servicio remoto.

Solo `allow` escribe la respuesta original. `block`, error, timeout o estado
obsoleto cierran el stream sin escribir un formato sustituto. La superficie
filtrada pertenece al content script y es sintetica, opaca y estatica.

## Flujo

1. El content script indexa solamente `img`, `image` e `input[type=image]` y
   aplica un estado visual cerrado hasta conocer la decision.
2. El background abre un filtro para `image`/`imageset`, incluidos fondos CSS
   HTTP(S), y retiene el cuerpo antes de que Gecko pueda pintarlo.
3. El cuerpo se limita por recurso y por presupuesto agregado.
4. Background enlaza la solicitud al `tabId` y token exacto del documento
   superior.
5. SHA-256 permite deduplicar por contenido en memoria; la URL no autoriza.
6. Los bytes se codifican Base64 y cruzan el canal nativo privilegiado.
7. Android valida sobre, URL, tamaño, Base64, formato y dimensiones.
8. El preprocesador genera RGB 224 x 224 y, solo cuando la politica lo exige,
   vistas regionales acotadas.
9. El unico ONNX local devuelve probabilidad binaria `allow/filter`.
10. El lease se comprueba antes de cada etapa costosa y antes de responder.
11. Background vuelve a comprobar que el documento siga vigente justo antes de
    escribir un `allow`.
12. Content aplica la decision solo a la fuente exacta conocida y reconcilia
    cambios dinamicos, nodos retirados y atributos hostiles.

## Arquitectura de presentacion

La autoridad para raster HTTP(S) es el stream de red, no una reconstruccion del
DOM. Una pagina conserva su CSS, pseudo-elementos, iconos, fondos, listas,
bordes y lazy loading originales. La capa de presentacion no borra
`background-image`, `content`, `list-style-image` ni `border-image` de toda la
pagina y no intenta recrearlos despues.

El trabajo queda dividido en dos scripts sin autoridad compartida:

- `barrier.js` mantiene el indice fuente-elemento, los estados
  `hidden/allow/block/error`, SVG de interfaz pasivo y el fallback acotado para
  visuales generados `data:`/`blob:` que no atraviesan `webRequest`;
- `ads.js` oculta anuncios con semantica explicita. No analiza imagenes, no
  altera decisiones de GloshIA y no ejecuta barridos al desplazar.

El `MutationObserver` procesa solo altas, bajas y atributos relevantes. Al
retirar un subarbol libera indices, hosts y registros generados; no conserva
referencias fuertes a imagenes viejas de una pagina infinita. Los atributos de
estado, incluido el cierre de la barrera inicial, se revalidan ante intentos de
la pagina de falsificarlos.

La barrera nativa se abre apenas quedan instalados el filtro de respuesta y el
observador protegido; no espera `DOMContentLoaded`. Los nodos que el parser o
la pagina agregan despues se cierran en el `MutationObserver`, antes del
siguiente render. `DOMContentLoaded` se conserva solo como metrica de ciclo de
pagina y no como permiso para mostrar estructura.

## Presupuestos

| Recurso | Limite |
| --- | ---: |
| Cuerpo por imagen | 2 MiB |
| Bytes HTTP retenidos entre todos los streams | 8 MiB |
| Handles de respuesta activos | 128 |
| Decisiones nativas JS simultaneas | 4 |
| Fallbacks activos / esperando | 2 / 256 |
| Hilos ONNX Android / cola | 2 / 8 |
| Captura de respuesta | 5.000 ms |
| Respuesta JS nativa | 2.500 ms |
| Lease Android | 2.250 ms |
| Cache de decisiones por hash | 512 |
| Pistas de prioridad por documento | 512 |

Los 128 handles no equivalen a `128 x 2 MiB`: el presupuesto global de 8 MiB
impide esa acumulacion. Presion de memoria, timeout o cupo agotado cierran
seguro.

## Prioridad y fluidez

- visible y cercano mantienen FIFO dentro de su clase;
- una pista de viewport autenticada puede promover cercano a visible;
- un `IntersectionObserver` acotado a los nodos visuales indexados anticipa
  hasta 640 px del desplazamiento sin instalar un listener de scroll;
- despues de cuatro visibles se da oportunidad al cercano para evitar hambre;
- DAG conserva `loading=lazy` y `fetchpriority` elegidos por el sitio;
- solo agrega `decoding=async` cuando el sitio no definio el atributo;
- desplazar la pagina no dispara consultas globales del DOM ni
  `getComputedStyle`;
- lecturas de geometria y escrituras de prioridad se agrupan en una sola fase
  por cuadro, con respaldo temporal para pestañas en segundo plano;
- los fondos HTTP(S) siguen el render nativo del sitio y quedan protegidos por
  sus bytes retenidos; la inspeccion de estilo se reserva a `data:`/`blob:`;
- navegar o cerrar pestaña purga trabajo viejo antes de admitir la pagina nueva;
- reconectar el puerto invalida generacion, documentos y cola anteriores;
- telemetria por imagen, presentacion y viewport solo se emite cuando Android
  negocia `diagnostics-config` para la variante DEV.

Esto reduce competencia de red/CPU sin aumentar hilos ni relajar el filtro.

## Validacion nativa

- URL HTTP(S), hasta 4.096 caracteres;
- JPEG, PNG, WebP, AVIF y GIF estatico admitidos por contrato;
- maximo 4.096 por lado y 16.777.216 pixeles;
- entrada preparada exacta: RGB 224 x 224;
- salida finita entre 0 y 1;
- cualquier excepcion o salida invalida se transforma en bloqueo tecnico;
- buffers fuente, preparados, regionales y normalizados se limpian.

SVG de interfaz usa un validador separado, pequeno y estructural. `blob:` y
`data:` confiables se enlazan al documento superior, entregan sus bytes por el
canal autenticado y usan una identidad HTTPS interna solo dentro del sobre
nativo; esa identidad nunca autoriza contenido ni genera una solicitud de red.
Un iframe no puede crear autoridad con su propio token.

## Presentacion terminal

- `allow`: original exacto, sin blur ni reemplazo;
- `block`: cero bytes del original y host `filtered` opaco/estatico;
- error/timeout: cero bytes y host tecnico distinto;
- un evento `error` del decoder nunca degrada un `block` autenticado;
- un cambio de `src`, `srcset`, `data-src` o `poster` vuelve a espera y exige una
  decision para la fuente nueva;
- un host con varias imagenes conserva espera mientras alguna hermana sigue
  pendiente, eleva las permitidas por encima de esa superficie y deja de cubrir
  el host cuando todas estan resueltas y existe al menos una permitida.

Una imagen sin geometria previa puede no reservar espacio al quedar vacia. Es
un limite visual conocido, no una fuga.

## Modelo

- artefacto:
  `tinyclip-bounded-finetune-r1-int8.onnx`;
- SHA-256:
  `2d52bd9e5eb4cd448cb0d64a784b2ee6f761ad20e890c57b898fd7991d29a9ee`;
- umbral global: `0.4`;
- sin API, red, Supabase ni persistencia de pixeles.

El piloto habilita DEV, no Production ni cobertura universal. Cambiar backend
CPU/XNNPACK/NNAPI requiere benchmark fisico contrabalanceado, salida numerica
compatible y paridad sobre corpus congelado; una microprueba sintetica no basta.

## Evidencia

- metricas: `docs/dag/v3/DAG_BROWSER_V3_PERFORMANCE_METRICS.md`;
- historial: `docs/compatibility/results/dag-performance-history.md`;
- laboratorio controlado: `tools/dag_perf_lab/`;
- contrato de datos:
  `docs/dag/v3/DAG_BROWSER_V3_MODEL_DATASET_CONTRACT.md`.

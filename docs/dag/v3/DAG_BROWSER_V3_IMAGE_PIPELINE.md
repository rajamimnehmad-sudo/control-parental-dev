# DAG Browser V3 - pipeline visual vigente

## Autoridad

GloshIA es la unica autoridad para raster. DAG intercepta los bytes que Gecko
iba a consumir, los analiza localmente y solo devuelve el original exacto ante
`model_allow`. `model_filter`, error, timeout, saturacion o formato invalido
devuelven un PNG neutro proporcional sin pixeles del recurso rechazado.

No hay API, Supabase, segunda descarga ni decision por URL. El modelo, pesos y
umbrales son los documentados en el handoff vigente.

## Recorrido de red

1. `onBeforeRequest` abre `filterResponseData` para `image` e `imageset`.
2. `onHeadersReceived` aplica la misma compuerta a cualquier respuesta con MIME
   raster, incluida una imagen solicitada mediante `fetch`/XHR o como pagina
   principal.
3. Cada stream retiene como maximo 2 MiB y el conjunto hasta 8 MiB.
4. SHA-256 deduplica decisiones efimeras por contenido.
5. Como maximo dos inferencias nativas trabajan simultaneamente y 24 esperan.
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
Aunque cada respuesta tenga una decision valida, una miniatura provisoria
permitida no debe verse antes de una fuente definitiva filtrada. `barrier.js`
espera 350 ms sin cambios en `src`, `srcset` o `sizes` antes de marcar un `img`
como estable. Un cambio reinicia la espera.

El observador:

- atiende solo nodos de imagen nuevos y esos tres atributos;
- no reescribe `src` ni `srcset`;
- no asocia decisiones de GloshIA con elementos;
- no consulta geometria, estilos ni el DOM durante scroll;
- no contiene dominios, comercios o telefonos especiales;
- deja SVG e iconos vectoriales seguros fuera de la espera.

La espera puede retrasar hasta 350 ms la aparicion de un raster. No suma
inferencias, solicitudes, Base64 ni trabajo por cuadro de desplazamiento.

## Limites y fallo seguro

- recurso: 2 MiB;
- captura agregada: 8 MiB;
- streams activos: 32;
- cola JS: 24;
- inferencias nativas simultaneas: 2;
- captura: 5.000 ms;
- respuesta nativa: 2.250 ms;
- cache efimera: 512 hashes.

Animaciones no autorizadas, recursos demasiado grandes, modelo ausente,
decodificacion invalida, desconexion o timeout fallan cerrados. El fixture local
requiere HTTPS con certificado confiable; nunca se relaja TLS para probar.

## Evidencia

- estado tecnico: `docs/HANDOFF_ACTUAL.md`;
- metricas: `docs/dag/v3/DAG_BROWSER_V3_PERFORMANCE_METRICS.md`;
- resultado DAG 67:
  `docs/compatibility/results/dag-browser-v67-first-paint-stability-sm-s908e-2026-08-02.md`;
- historial: `docs/compatibility/results/dag-performance-history.md`.

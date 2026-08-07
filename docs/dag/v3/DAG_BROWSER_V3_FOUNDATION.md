# DAG Browser V3 - foundation vigente

## Objetivo

DAG es una APK GeckoView separada para navegar con proteccion visual local. El
runtime actual vive unicamente en `app-dag-browser`; no usa DAG 1, DAG 2,
WebView, Brave Search ni una API paga por consulta.

Paquete DEV: `com.contentfilter.dagbrowser.dev`.

## Aislamiento

- DAG no depende de modulos Gradle de App Usuario, App Admin, VPN o
  Accessibility.
- No contiene Service Role Key, entrenamiento, fotos del corpus ni llamadas a
  Supabase para clasificar.
- App Usuario puede detectar/instalar la APK separada; App Admin puede habilitar
  la politica. Esa integracion no convierte las tres apps en un mismo paquete.
- Los APK finales se construyen desde `main` local integrado, nunca desde el
  worktree historico `content-filter-dag-browser-v3`.

## Invariantes de seguridad

1. La hoja de barrera se inyecta en `document_start` y oculta medios antes de
   que la pagina pueda pintarlos.
2. El fondo privilegiado retiene los bytes HTTP(S) originales.
3. Solo un `allow` autenticado con razon admitida escribe esos bytes a Gecko.
4. `block`, error, timeout, formato invalido, cola llena, puerto perdido o
   documento obsoleto terminan cerrados y escriben cero bytes.
5. Cada solicitud nativa incluye `tabId` y token exacto del documento superior.
   Navegar, cerrar pestaña, reconectar el canal o destruir la Activity invalida
   el trabajo anterior.
6. Una prioridad visible solo reordena trabajo; nunca autoriza contenido.
7. Una pagina no puede liberar medios modificando atributos `data-glosh-*`.
8. Bytes y buffers preparados se limpian al terminar.

El contrato detallado esta en
`docs/dag/v3/DAG_BROWSER_V3_IMAGE_PIPELINE.md`.

## Navegacion

- Solo se admite navegacion superior HTTPS.
- Una consulta sin URL usa Google con SafeSearch activo.
- Redirecciones de Google vuelven a exigir SafeSearch.
- Nuevas ventanas permanecen dentro de sesiones DAG controladas.
- DAG usa el rol oficial de navegador Android; no puede asignarselo en silencio.
- Historial, favoritos, pestañas y miniaturas seguras tienen contratos locales
  separados. Descargas y PDF fueron retirados en DAG 159 por decision de
  producto.

## Presentacion visual

- Espera: superficie neutra con shimmer; se desactiva con
  `prefers-reduced-motion`.
- Permitida: se entrega el recurso original sin filtros.
- Filtrada: el recurso permanece oculto y se dibuja una superficie opaca,
  estatica y sin texto/icono. No reutiliza pixeles rechazados.
- Error tecnico: superficie estatica diferente, tambien sin revelar contenido.
- La explicacion de filtro/error permanece disponible para accesibilidad.
- Controles funcionales seguros pueden mostrarse por encima de la superficie;
  eso no autoriza el raster asociado.

## Medios no cubiertos por el modelo de fotos

- Video, audio, `object` y `embed` permanecen bloqueados.
- Canvas y SVG no confiable permanecen ocultos. SVG pequeno y estructural de UI
  solo se permite mediante el validador acotado.
- Fondos CSS y pseudo-elementos descubiertos pasan por el mismo contrato de
  decision.
- Clasificar video por fotogramas es un ticket posterior: no declarar que el
  modelo actual ya filtra YouTube, Instagram o Shorts.

## Extension incorporada

La extension tiene ID estable `dag-protection@glosh.local`. Como Gecko la
persiste en el perfil, cada cambio de `background.js`, `barrier.js`,
`barrier.css` o `manifest.json` debe incrementar su version. DAG usa
`ensureBuiltIn` para omitir reinstalaciones cuando la version coincide y aplicar
una version nueva durante una actualizacion in-place.

## Gates de entrega

Un candidato que modifica carga, Gecko o imagenes exige:

1. pruebas JS de retencion/liberacion y contrato DOM;
2. unitarios Kotlin, Ktlint, Lint y build;
3. APK identificada por version, firma y SHA-256;
4. fixture local controlado en el telefono objetivo;
5. matriz fisica Mimo, Cheeky y una URL estable de Fravega;
6. frio/caliente, scroll, memoria, temperatura, crash/ANR y salida inesperada;
7. evidencia con `page_visible`, `viewport_images_ready` y
   `page_analysis_ready` correctamente interpretadas.

Una prueba omitida, un sitio roto o una metrica ausente no cuenta como exito.
Production requiere una autorizacion y gate independientes.

## Limite conocido

Una imagen sin dimensiones HTML/CSS ni relacion de aspecto puede no reservar
espacio cuando una respuesta bloqueada termina con cero bytes. Transportar
dimensiones autenticadas para ese caso es una mejora futura; no justifica
reintroducir pixeles rechazados, blobs falsos o reglas por sitio.

# DAG Browser 65 - base multimedia simple

Fecha: 2026-08-02

## Objetivo

Validar el navegador sin GloshIA y sin el pipeline residual de imagenes. En
esta variante DEV, las imagenes comunes pertenecen completamente a Gecko. DAG
no intercepta sus respuestas ni modifica sus elementos en la pagina.

## Candidato

- paquete: `com.contentfilter.dagbrowser.dev`;
- version: `65` / `0.45.0-dev`;
- extension incorporada: `1.34.0`;
- dispositivo: Samsung SM-S908E `R5CT717BZTZ`, Android 16;
- instalacion: in-place sobre DAG 64, sin borrar perfil;
- APK SHA-256:
  `d3194fb2ff987f799c27d0743534cbf86fa789c408e9ee7e65310dc789de014d`;
- tamaño: `121359017` bytes.

## Causa raiz y simplificacion

DAG 64 devolvia los bytes permitidos, pero despues aun debia asociar cada URL
con el elemento DOM exacto y actualizar su estado visual. Las paginas modernas
reemplazan iconos e imagenes durante hidratacion, carruseles y recargas. Si ese
reemplazo ocurria entre la decision y la presentacion, el elemento nuevo podia
quedar oculto aunque la respuesta hubiese sido permitida.

DAG 65 elimina ese segundo sistema cuando GloshIA esta desconectada. No hay
`filterResponseData` para imagenes, mensajes nativos de pixeles, estados
`data-glosh-dag-media`, observadores multimedia ni CSS sobre imagenes o SVG. El
analizador ONNX tampoco se inicializa. Las tres piezas activas de red, puente y
CSS bajaron de 2.563 a 156 lineas. No se agregaron excepciones por sitio.

## Resultado fisico

- Cheeky: primera carga y cinco de cinco recargas consecutivas con menu,
  usuario, logo, favorito, bolsa, banner y carrusel visibles. No reaparecieron
  rectangulos negros ni iconos transparentes.
- Mimo: logo, menu, buscador, controles, hero y carrusel visibles.
- Fravega: menu, logo, buscador, carrito, banner, categorias y miniaturas
  visibles.
- YouTube: shell, logo, busqueda y navegacion visibles; el bloqueo de recursos
  de video/audio permanece.
- Organizador de pestañas: abre y muestra las miniaturas que ya estaban
  disponibles; no se modifico su politica de captura.
- No se observaron crash, ANR, OOM ni salida inesperada.

Los eventos `page_visible` de las navegaciones posteriores estuvieron
normalmente entre 78 y 683 ms; una muestra fria llego a 1.130 ms. Son datos
diagnosticos de este dispositivo y red, no un porcentaje universal ni una
comparacion controlada contra Chrome. Con siete pestañas acumuladas se observo
un PSS de 376132 KiB; no se usa como medicion aislada del pipeline.

## Gates automaticos

- WebExtension: 9 aprobadas, 0 fallos;
- Kotlin/JVM: 144 aprobadas, 0 fallos;
- Ktlint: correcto;
- Lint: correcto;
- APK: correcto;
- `git diff --check`: correcto.

## Limite

DAG 65 permite todas las imagenes y por eso no es publicable como filtro. Es
una base de navegador y compatibilidad para comprobar que Gecko funciona bien
sin interferencia. GloshIA debe volver luego como una unica compuerta aislada y
medible; el pipeline retirado no debe reactivarse.

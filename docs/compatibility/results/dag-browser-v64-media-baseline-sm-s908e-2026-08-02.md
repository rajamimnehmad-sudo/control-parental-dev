# DAG Browser 64 - base multimedia sin clasificador

Fecha: 2026-08-02

## Alcance

Se valido por separado la entrega y presentacion de imagenes de DAG. GloshIA
visual permanece empaquetada, pero la variante DEV 64 no ejecuta
preprocesamiento ni inferencia: una respuesta HTTP(S) capturada y con sobre
valido se devuelve sin modificar a Gecko. Este modo no es publicable ni
representa la proteccion final.

La separacion encontro la causa raiz de los huecos: Android devolvia `allow`,
pero la extension solo autenticaba los motivos `model_allow` y
`safe_ui_vector`. El motivo DEV `classifier_bypassed_dev` se convertia en
`error`, por lo que los bytes originales no volvian a Gecko. Se unifico el
contrato y se agrego una prueba automatica del recorrido exacto.

## Candidato

- paquete: `com.contentfilter.dagbrowser.dev`;
- version: `64` / `0.44.0-dev`;
- extension incorporada: `1.33.0`;
- dispositivo: Samsung SM-S908E, Android 16;
- instalacion: in-place, sin borrar perfil;
- APK SHA-256:
  `02a88ff6af9a9271b9757fd86d3d156c0975645ee31cfcf3f0952cccd773c3db`;
- tamaño: `121375985` bytes.

## Resultado fisico

- Mimo: logo, controles, hero y contenido del carrusel visibles; las
  respuestas pasan a `allow` y recuperan sus bytes originales.
- Cheeky: menu, cuenta, logo, favorito, bolsa y banner visibles sin reglas por
  dominio.
- Fravega: logo, buscador, carrito, categorias y miniaturas visibles.
- No se observaron crash, ANR, salida inesperada ni elementos hermanos
  ocultados por el estado de una foto.
- La decision DEV nativa tarda normalmente entre 0 y 2 ms; el recorrido total
  observado depende del tamaño/Base64 y alcanzo decenas de milisegundos en
  recursos grandes. Estas muestras diagnostican el contrato, no son un
  benchmark de pagina ni se extrapolan como mejora porcentual general.

## Gates automaticos

- harness WebExtension: 23 aprobadas, 0 fallos y 1 DOM opcional omitida;
- Kotlin/JVM: 151 aprobadas, 0 fallos;
- `ktlintCheck`, `lintDevDebug` y `assembleDevDebug`: correctos;
- `git diff --check`: correcto.

## Limite y siguiente gate

DAG 64 DEV muestra todas las imagenes capturadas que superan el contrato de
transporte. No filtra contenido visual y no debe publicarse en DEV remoto ni en
Production. El siguiente lote debe volver a activar GloshIA mediante
`DagMediaClassificationMode.Enabled` y repetir la misma matriz, verificando
que `model_allow` libere los mismos bytes y que `model_filter` no los libere y
presente solamente el estado exacto de esa imagen.

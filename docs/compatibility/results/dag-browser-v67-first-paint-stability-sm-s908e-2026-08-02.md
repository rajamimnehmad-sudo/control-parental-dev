# DAG Browser 67 - estabilidad del primer pintado en SM-S908E

Fecha: 2026-08-02

## Alcance

- paquete: `com.contentfilter.dagbrowser.dev`;
- version: `67` / `0.47.0-dev`;
- extension incorporada: `1.36.0`;
- dispositivo: Samsung SM-S908E, serial ADB `R5CT717BZTZ`;
- instalacion: in-place, sin borrar perfil ni tocar App Usuario o App Admin;
- canal: candidato local, sin push ni publicacion.

## Causa reproducida

Una navegacion limpia de Google Imagenes se capturo cuadro por cuadro. La
pagina podia presentar una miniatura provisoria permitida y reemplazarla en el
cuadro siguiente por una resolucion distinta que GloshIA filtraba. No era una
liberacion de bytes rechazados: eran dos recursos sucesivos de una misma
tarjeta con decisiones diferentes.

Las variantes inline y las respuestas raster solicitadas como `fetch`/XHR
tambien quedaban fuera del contrato visual completo de DAG 66.

## Correccion

- `data:` y `blob:` permanecen neutrales desde `document_start`;
- cualquier respuesta con MIME raster usa la misma compuerta, aunque el tipo de
  solicitud no sea `image`/`imageset`;
- `img` se revela tras 350 ms con `src`, `srcset` y `sizes` estables;
- el observador no reescribe fuentes, no decide contenido y no se ejecuta por
  desplazamiento;
- SVG e iconos vectoriales seguros conservan presentacion directa;
- no hay reglas por Google, comercio, telefono o modelo de dispositivo.

## Validacion automatica

- `node --check`: correcto;
- 12 pruebas WebExtension: aprobadas;
- 147 pruebas unitarias Kotlin: aprobadas;
- `ktlintCheck`: correcto;
- `lintDevDebug`: correcto;
- `assembleDevDebug`: correcto;
- `git diff --check`: correcto.

## Validacion fisica

Google Imagenes se abrio desde proceso detenido y se registraron 40 capturas
consecutivas. Las tarjetas filtradas pasaron de superficie neutral al
placeholder final sin mostrar primero sus pixeles. Una tarjeta permitida se
revelo y permanecio visible, demostrando que la barrera no equivale a ocultar
todas las fotos.

Smokes adicionales, 12 segundos por sitio:

- Mimo: estructura e iconos visibles; raster filtrado con placeholder;
- Cheeky: banner, cuenta, corazon, bolsa y contenido permitido visibles;
- Fravega: buscador, categorias, iconos y raster permitido visibles;
- Logcat: sin crash, ANR ni `OutOfMemoryError` de DAG.

El fixture HTTPS local no se contabiliza como exito: conserva un certificado no
confiable y DAG no relajo TLS.

## Artefacto

- APK: `app-dag-browser/build/outputs/apk/dev/debug/DagBrowser-dev-debug.apk`;
- tamaño: `121359549` bytes;
- SHA-256:
  `8477abc6f539aacef1423c6736d35defc736e26bc17c82cb707de70bbf2c7e8d`.

## Costo conocido

Una imagen raster nueva o cuya fuente cambia espera hasta 350 ms antes de
revelarse. No agrega inferencias, descargas ni trabajo durante scroll. Es el
margen deliberado para que una miniatura provisoria no se presente como
decision definitiva.

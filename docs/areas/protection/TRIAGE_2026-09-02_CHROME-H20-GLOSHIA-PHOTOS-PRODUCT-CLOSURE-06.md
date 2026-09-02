# CHROME-H20-GLOSHIA-PHOTOS-PRODUCT-CLOSURE-06 — BLOCKED triage

## Anchors

- Base funcional: `8abd7762bc18261cff9c7512f9821da7b233a4b7`.
- Governance SHA fijado para el lote: `9323b126dde3b91e21e2136a11d087fa3c1f0ca1`.
- Review 05B previamente aceptada: `f21c8964e24c1b671df420e3a029f8755d163955`.
- Central reconciliado y publicado antes de escribir: `7c7691f5b0065126719fad2ec8e861e0e5841a55`.

## Resultado

`BLOCKED` antes de batería y aceptación final. No existe candidato de producto 06.

La comparación física OFF/ON confirmó un `GLOSH_UI_COMPAT_BUG`: en Wikipedia
mobile, el icono original de descarga aparece con H20 OFF y desaparece con H20
ON. No depende del hostname. El mecanismo genérico es una imagen SVG `data:`
usada desde CSS como `mask-image`/`background-image`.

La envolvente CSP de H20 agrega actualmente:

```text
img-src https: http:
```

Por eso Chrome deniega el SVG `data:` aun cuando es iconografía legítima. CSP
decide por esquema y no puede expresar “SVG de UI acotado sí; foto/raster data
no”. Agregar `data:` a `img-src` preservaría esos iconos, pero también habilitaría
media local sin autoridad de bytes y violaría el contrato fail-close. No se hizo.

## Reproducción y evidencia

Dispositivo: Samsung A23 `SM_A235M`, serial `R58T34V31AE`, Chrome oficial
`152.0.7977.64`, sin borrar datos/cache.

Página: `https://en.wikipedia.org/wiki/Web_performance`.

- DEV420, H20 ON: el icono de descarga no aparece. Captura local SHA-256
  `aea8c77d6f4368d974765692595e856f2121f0654222cede39a84ddc05e787a7`.
- DEV420, H20 OFF: el icono de descarga original aparece. Captura local SHA-256
  `ab364cdcccc1bc778ba02430fea52fdb47022df63c7e92448bf3303da88daae0`.
- DEV421 diagnóstico, H20 ON: preservar el SVG inline original no cambia este
  defecto CSS. Captura local SHA-256
  `b3995b26d4cc7c93192be758a03f50c72a9e7220a630e542f06ff7fefa00d29e`.
- HTML móvil observado: SHA-256
  `7137dfb9619db7fe8cbef8149bfd0dadcab9a84f9df7ddbf691f0b223723e247`.
- CSS de ResourceLoader observado: SHA-256
  `111c041a15abc4786954be824f60d7586fd9ecb1b1f0ff21d5f3fcb8f07d4637`;
  contiene SVG `data:image/svg+xml` en `mask-image` y `background-image` para
  iconos ordinarios.

Las capturas sólo son evidencia de compatibilidad, no autoridad de seguridad.

## Diagnóstico inline SVG

La auditoría encontró además que el camino `safeIcon` existente no preserva la
UI original: `lockIconGeometry` elimina estilos y reconstruye paths con
`all:initial`, color negro, geometría forzada y `pointer-events:none`.

La rama de triage conserva un experimento **no candidato** que:

- preserva atributos, pintura, geometría y eventos del SVG inline original;
- mantiene la gramática acotada y rechaza referencias/filtros/masks;
- exige dimensiones intrínsecas y renderizadas acotadas;
- vuelve a inspeccionar cambios de `class` y atributos SVG;
- elimina la reconstrucción y el lock visual de H20.

Ese experimento pasó los 27 tests focales de bootstrap/fixture y el conjunto
completo de unit tests, `compileDevDebugKotlin`, `lintDevDebug` y
`assembleDevDebug`. No resuelve iconos CSS `data:` y por eso no es publicable.

El check ktlint de las fuentes modificadas pasó. El check global de `testDev`
permanece rojo por violaciones preexistentes en
`ChromeHttp1ResponseWriterTest.kt`, `ChromeMediaShieldReadyEndpointTest.kt` y
`ChromeMediaShieldRendererMetricsTest.kt`; el test modificado no figura en el
reporte.

## Frontera arquitectónica

La corrección segura necesita una autoridad genérica nueva para recursos de UI
embebidos en CSS que cubra, como una sola unidad coherente:

1. respuestas externas `text/css` y sus codificaciones;
2. bloques `<style>` y atributos `style` iniciales;
3. inserciones y reemplazos CSSOM;
4. hojas adoptadas y Shadow DOM;
5. mutaciones dinámicas y estados responsive;
6. validación canónica y acotada del SVG original antes de permitirlo;
7. una CSP que habilite únicamente recursos ya admitidos sin abrir raster/data.

Implementar sólo una parte deja rutas de bypass. La alternativa es cambiar la
semántica vigente del Byte Gate para SVG/data. Ambas opciones requieren una
decisión material de arquitectura/seguridad expresamente fuera del lote. No se
agregó allowlist por dominio, no se habilitó `data:` global y no se recreó UI.

## Gates no ejecutados

Al no existir candidato seguro, se detuvieron la matriz amplia, lifecycle final,
batería A/B, tramo background, security regression final y long-run. Ejecutarlos
sobre el experimento habría producido evidencia no válida.

## Rollback físico

- Lab detenido; proxy global `null`; CA/VPN removidos; cache del lab limpiado.
- Chrome data inode antes/después: `6090`; no se borraron datos/cache.
- Accessibility permanece habilitada y vinculada.
- `bootstrapResetCount=3`.
- Android no permitió downgrade DEV421→DEV420 porque la variante instalada no
  es `debuggable`. Para no dejar el experimento, se instaló in-place código
  funcional exacto de la review 05B con override efímero de versión DEV422.
- APK de rollback funcional DEV422 SHA-256:
  `295b28f5560b73c5fdd44681490d54bc043a4506869e81bf152acefb6557d82c`.
- El override no modificó la rama 05B ni su código; el servicio quedó `Stopped`.


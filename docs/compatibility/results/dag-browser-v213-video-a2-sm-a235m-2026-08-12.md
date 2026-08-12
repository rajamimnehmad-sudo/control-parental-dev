# DAG Browser 213 — gate fisico DAG-VIDEO-01A2 en SM-A235M

Fecha: 2026-08-12
Dispositivo: Samsung SM-A235M (`R58T34V31AE`), Android 14, USB
Variante probada: Diagnostic 13 (`0.70.17-diagnostic`)
Extension: `2.0.16`
Resultado: `GO CON CONDICIONES` — A23 aprobado; S22 pendiente

## Alcance y aislamiento

Se probo video web real exclusivamente en la variante Diagnostic. DAG normal
permanece en 213, conserva el bloqueo total de video y no recibio un APK nuevo.
No se modificaron GloshIA Visual R3.1, pesos, umbral `0,40`, politica visual,
corpus, publicidad, Supabase ni Production.

El laboratorio queda apagado en cada arranque y se arma manualmente por sesion
desde el menu. La cobertura Android opaca se confirma antes de que la extension
inyecte una concesion CSS `user` para un token aleatorio, una pestaña y el
documento HTTPS superior exactos. Navegacion, desactivacion, retiro del video o
desconexion revocan CSS, red y trabajo nativo. Audio declarado se cancela y el
elemento de video se mantiene mudo, volumen cero, incluso ante eventos `play` o
`volumechange` de la pagina.

La implementacion sigue las APIs oficiales de WebExtensions para CSS de origen
usuario y bloqueo de respuestas:

- `https://developer.mozilla.org/en-US/docs/Mozilla/Add-ons/WebExtensions/API/tabs/insertCSS`;
- `https://developer.mozilla.org/en-US/docs/Mozilla/Add-ons/WebExtensions/API/webRequest/onHeadersReceived`.

No hay excepciones por proveedor, sitio, URL, dominio ni dispositivo. YouTube e
Instagram recorren la misma ruta general que cualquier documento HTTPS superior.

## Validacion automatizada

Comando ejecutado mediante el Gradle aislado de DAG Browser:

```text
scripts/dag_gradle.sh testDagProtectionJs testDevDebugUnitTest
  testDiagnosticDebugUnitTest ktlintCheck lintDevDebug lintDiagnosticDebug
  assembleDiagnosticDebug
```

Resultado final:

- JavaScript: 36/36;
- unitarios DEV: 171/171;
- unitarios Diagnostic: 171/171;
- Ktlint: correcto;
- Lint DEV y Diagnostic: correcto;
- APK Diagnostic: correcto.

Los contratos cubren concesion exacta, HTTPS y frame superior, bloqueo de audio
y subframes, revocacion en navegacion/desactivacion, desconexion fail-closed,
CSS privilegiado, cola acotada, silencio sostenido y ausencia de excepciones.

## Latencia fisica A23

Primera preparacion cubierta en video web real, en milisegundos:

| Corrida | Cobertura | Decode | Captura | Preproceso | Cola | R3.1 | Total |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| YouTube caliente | 112 | 305 | 13 | 2 | 1 | 186,51 | 619,51 |
| YouTube cache limpia | 65 | 301 | 7 | 1 | 1 | 354,63 | 729,63 |
| Instagram caliente | 135 | 606 | 24 | 1 | 0 | 201,67 | 967,67 |
| Instagram caliente con muestreo | 94 | 544 | 4 | 1 | 178 | 179,54 | 1.000,54 |
| Instagram restaurado en frio | 193 | 1.610 | 22 | 2 | 45 | 313,70 | 2.185,70 |

Con cinco muestras, mediana/p50 `967,67 ms`; p95 por rango mas cercano y peor
caso `2.185,70 ms`. Todas quedan debajo del gate A23 de `4 s`. El decode y la
red de la pagina, no R3.1, dominaron el peor caso.

Sobre el APK final exacto se repitieron:

- fixture: cobertura 31 ms, decode 50 ms, captura 6 ms, preproceso 1 ms, cola
  2 ms e inferencia 145,36 ms;
- YouTube: tras el gesto de reproduccion exigido por el sitio, primera muestra
  cubierta en aproximadamente 438 ms; las dos siguientes en aproximadamente
  204 y 164 ms.

Una pagina de YouTube pausada puede no producir `HAVE_CURRENT_DATA` sin gesto.
En ese estado vence acotadamente y falla cerrada; no se expone un fotograma.

## Recursos, retiro e interaccion

- Durante una corrida de Instagram, el proceso principal y cada subprocess de
  Gecko observados no superaron 7 % de CPU; la suma instantanea de componentes
  del paquete fue aproximadamente 31 % con cuatro pestañas preexistentes.
- El PSS agregado bajo video fue 664.740 KiB. Tras navegar y retirar el estado
  bajo a 595.814 KiB: diferencia de 68.926 KiB, sin bitmap o captura retenidos.
  El absoluto incluye todas las pestañas y subprocess, no solo el laboratorio.
- Temperatura: 25,4 °C durante la corrida y 25,0 °C al retirarla; lectura final
  24,8 °C con el equipo cargando.
- Cerrar el modal propio de Instagram funciono con la cobertura activa, por lo
  que el overlay no absorbio el gesto. La pagina publica sin sesion no permitio
  usar el cambio de reel como benchmark confiable.
- No hubo crash, ANR, resultado tardio aceptado, segunda sesion ONNX ni fuga de
  captura. Cambios de fuente generaron revisiones nuevas y retiraron la anterior.

## Regresion con laboratorio apagado

Tras desarmarlo no se emitieron eventos `DagVideoLab` en las paginas normales:

- Mimo: visible en 1.322 ms, imagenes iniciales listas en 2.504 ms; scroll y menu
  posteriores respondieron.
- Cheeky: visible en 3.325 ms; el cierre de sus numerosas imagenes termino en
  17.672 ms. Hubo decisiones y timeouts propios de la ruta de imagen, pero cero
  actividad del laboratorio; queda como evidencia separada de rendimiento de
  imagenes, no como regresion de `01A2`.
- Fravega volvio a responder con su propia pantalla `La pagina no existe`, igual
  que en `01A1`; no cuenta como benchmark de portada.

El laboratorio se dejo apagado y se cerraron solamente las cinco pestañas
creadas por la prueba. El dispositivo volvio a sus cuatro pestañas originales;
no se borraron perfil, historial ni datos del usuario.

## Resultado y artefacto

El A23 cumple latencia, cobertura, cancelacion, recursos e interaccion: `GO` en
ese dispositivo. El ticket completo queda `GO CON CONDICIONES` hasta repetir el
gate en S22. Esto no autoriza `DAG-VIDEO-01B`, revelar video ni publicar DAG
normal.

- APK Diagnostic 13: `DagBrowser-diagnostic-debug.apk`;
- SHA-256: `bc47f59f2a555fc52c861be1ed28fc90f10d2d332c1135c5b8e3551834cf19b4`;
- tamano aproximado: 116 MiB.

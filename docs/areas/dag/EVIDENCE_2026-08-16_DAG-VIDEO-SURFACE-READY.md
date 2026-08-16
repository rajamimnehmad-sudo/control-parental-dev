# DAG-VIDEO-SURFACE-READY — evidencia local

Fecha: 2026-08-16. Dispositivo: Samsung A23 `SM-A235M`, Android 14.

## Causa y cambio

Un video con backing disponible durante el arranque podia pedir cobertura antes
de que GeckoView expusiera una matriz cliente-superficie utilizable. Android lo
cerraba inmediatamente como `invalid_surface_rect`.

Diagnostic 102 espera de forma acotada esa superficie: maximo 10 reintentos de
50 ms. Durante la espera el aislamiento de medios sigue activo. Una identidad
tardia, una pestaña distinta o una geometria que nunca aparece siguen cerrando.

## Validacion automatica

- Unitarios completos DEV y Diagnostic: PASS.
- ktlint: PASS.
- lint Diagnostic: PASS.
- assemble Diagnostic: PASS.
- `git diff --check`: PASS.

## Validacion A23

- W3Schools HTML5: desaparecio `invalid_surface_rect`; alcanzo
  `cover_requested -> cover_armed`. La fuente remota quedo en
  `NETWORK_NO_SOURCE` y cerro `frame_ready_timeout`; no se cuenta como PASS de
  HTML5 ni hubo exposicion.
- YouTube normal, Big Buck Bunny: `cover_requested` en 109 ms, dos cuadros
  iniciales `model_allow`, `smooth_started`, imagen visible y muestreo continuo.
  Captura de cuadros posterior entre 3 y 9 ms; inferencia observada entre 133 y
  169 ms. Sin crash ni ANR.
- Un MP4 directo no es hoy una pagina HTML normal de Gecko y la barrera lo cerro;
  permanece en el backlog del adaptador de URLs multimedia directas.
- El ejemplo tabulado oficial de MDN cargo sus fuentes, pero renderiza la salida
  fuera del documento superior elegible; DAG mantuvo `scan_no_candidate`. No se
  uso como PASS ni se agrego una excepcion para iframes/proveedor.

Estado de esa fase: cambio local validado; la decision de promocion se resolvio
en la auditoria de cierre siguiente.

## Promocion local DEV 221

La auditoria de cierre conservo la prueba fisica HTML5 anterior de 120/120
cuadros y el replay determinista actual, porque las fuentes nuevas fallaron fuera
del filtro. DEV 221 / 0.70.23 paso JS 88/88, unitarios DEV/Diagnostic, ktlint,
lint DEV y assemble. En A23, la APK DEV normal mostro YouTube en movimiento y
Android registro AAudio de medios iniciado, estereo 48 kHz. Sin crash ni ANR.

Estado final del lote: matriz minima de documento superior aceptada y candidata
DEV integrada; video general permanece NO-GO para URLs directas, iframes,
Shorts, anuncios y redes sociales.

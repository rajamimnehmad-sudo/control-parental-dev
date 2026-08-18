# CHROME-VISUAL-02-REAL-WEB — evidencia

Fecha: 2026-08-18. Dispositivo fisico: Samsung A23 `SM-A235M`, Android 14 / API 34.

## Resultado

**GO controlado para continuar con video y fallback; no es un candidato de producto.**

- La geometria se vincula a la ventana de Chrome y se transforma entre pantalla
  y screenshot sin asumir pantalla completa.
- Scroll, lazy load, cambio visual, teclado, rotacion y salida de Chrome
  invalidan trabajo viejo y mantienen los overlays dentro de la ventana valida.
- Un muestreo visual periodico detecta canvas, fondos CSS, iframes y contenido
  no expuesto por Accessibility como cambios de mosaicos acotados. No inspecciona
  DOM, URLs ni proveedores.
- Las firmas y decisiones existen solo en memoria; nunca se persisten capturas.
- Un bloqueo confirmado permanece cubierto ante cambios de geometria hasta que
  cambia la identidad efimera de pagina.

## Prueba fisica unica

- Fixture dinamica controlada: cobertura inicial de ocho mosaicos; captura de
  222 ms, lote inicial de 2.868 ms y un cambio posterior detectado y actualizado.
- Scroll rapido/lazy: captura final de 243 ms, lote de 2.889 ms, sin crash/ANR.
- Sonda bloqueable completa: captura de 54 ms, lote de 1.598 ms,
  `allowed=8 blocked=1`.
- Teclado Samsung detectado desde y=1400; la cobertura se recorto para no tapar
  la ventana del teclado.
- Rotacion: 2342 x 1080 en 85 ms y regreso 1080 x 2408 en 78 ms, sin crash/ANR.
- Salida al launcher: trabajos cancelados y overlays retirados.
- Cierre: PSS 110.292 KB, RSS 98.104 KB, sin crash/ANR.
- Accessibility y rotacion quedaron restaurados a sus valores iniciales.

## Ajustes posteriores a la sesion

La sesion mostro costo excesivo a 2 Hz estaticos y riesgo de perder cobertura al
cambiar geometria. Se corrigieron sin otra corrida fisica: backoff de 500 ms a
1.000 ms, ledger de bloqueo por pagina, recorte sistematico por teclado,
cobertura inicial retenida durante la captura y debounce que no pierde rafagas
largas. Las correcciones pasaron todos los gates automaticos.

## Gates finales

- `:feature-accessibility:testDebugUnitTest`: PASS.
- `:feature-accessibility:testReleaseUnitTest`: PASS.
- `:feature-accessibility:ktlintCheck`: PASS.
- `:app-user:lintDevDebug`: PASS.
- `:app-user:assembleDevDebug`: PASS.
- `git diff --check`: PASS.

## Limites abiertos

- Un cambio puramente visual sin evento puede quedar visible hasta alrededor de
  un segundo mas captura/decision; el ticket de video necesita una politica
  propia y cobertura regional continua.
- La primera carga de ocho mosaicos aun tarda entre 1,6 y 2,9 segundos en el A23.
- La identidad por titulo es deliberadamente conservadora: una SPA que conserve
  titulo puede mantener un mosaico bloqueado mas tiempo del necesario.
- Multiventana tiene pruebas automaticas de mapeo, pero no evidencia fisica.
- La matriz no demuestra todos los motores posibles de canvas, iframe o shadow
  DOM; el fallback visual los trata como pixeles, no como casos por sitio.
- S22 no estuvo disponible. Chrome Visual sigue DEV-only, API 34+ y ARM64; DAG
  permanece como fallback fuerte.

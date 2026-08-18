# CHROME-VISUAL-00-PROBE — evidencia

Fecha: 2026-08-18. Dispositivo: Samsung A23 `SM-A235M`, Android 14 / API 34.

## Resultado

**GO** para continuar con la extraccion del motor visual compartido.

- Chrome se identifico por `windowId` y paquete exacto.
- `takeScreenshotOfWindow()` capturo 1080 x 2408 sin persistir ni transmitir pixeles.
- El accessibility overlay fue pequeno, opaco y no intercepto gestos.
- La captura posterior siguio viendo el contenido de Chrome debajo del overlay:
  3/3 comparaciones efimeras `PASS`.
- Pasaron pagina estable, cambio de pagina y scroll.
- No hubo crash ni ANR.

## Metricas

- Captura base: 52–229 ms en las tres corridas validas.
- Captura bajo overlay: 25–342 ms.
- Frecuencia estable observada: 2,02–2,09 capturas/s.
- Memoria temporal estimada durante comparacion: 20.805.120 bytes.
- Proceso completo al cierre: PSS 163.155 KB; RSS 169.968 KB.
- Muestra puntual de CPU al cierre: 6,0 %.

## Gates automaticos

- `:feature-accessibility:test`: PASS en Debug y Release.
- `:feature-accessibility:ktlintCheck`: PASS.
- `:app-user:lintDevDebug`: PASS.
- `:app-user:assembleDevDebug`: PASS.
- `git diff --check`: PASS.

## Hallazgo OEM y alcance

El host moderno `SurfaceControlViewHost` lanzo `UnsupportedOperationException`
en este Samsung. El probe usa el accessibility overlay publico tradicional,
posicionado sobre la ventana activa; `takeScreenshotOfWindow()` lo excluyo como
esperado. Esto no bloquea el GO, pero el adapter de producto debe conservar una
abstraccion de overlay y validar posicionamiento por ventana en multiventana.

No se probaron cambio real de pestaña, rotacion ni multiventana en este ticket.
Accessibility quedo restaurado al estado previo del A23 al finalizar.

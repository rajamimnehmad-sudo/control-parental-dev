# CHROME-VISUAL-01-IMAGES — evidencia

Fecha: 2026-08-18. Dispositivo fisico: Samsung A23 `SM-A235M`, Android 14 / API 34.

## Resultado

**GO controlado para continuar con endurecimiento; no es un candidato de producto.**

- App Usuario identifico la ventana exacta de Chrome y capturo exclusivamente
  su contenido mediante la API publica de Accessibility.
- El motor compartido R3.1 analizo crops efimeros y bloqueo fisicamente la sonda
  controlada cuando se mostro completa: `allowed=2`, `blocked=1`.
- Las decisiones quedaron ligadas a `windowId`, epoch, secuencia, region y firma
  visual. Un resultado viejo no puede modificar una captura nueva.
- El overlay cubre solo regiones/mosaicos bajo la barra de Chrome, no intercepta
  gestos y nunca almacena ni transmite screenshots.
- Chrome, scroll, lazy load, Google Images, una tienda y una pagina de noticias
  se ejercieron sin crash ni ANR.

## Auditoria enfocada durante la sesion

Chrome no expuso nodos de imagen de forma estable en las tres paginas reales.
El primer fallback solo reaccionaba despues de una captura previa y usaba cuatro
franjas demasiado grandes. Esa arquitectura dejaba una ventana inicial sin
cobertura suficiente y se considero FAIL aunque la inferencia exacta funcionara.

La correccion agrupada reemplazo ese fallback por ocho mosaicos acotados (dos
columnas por cuatro filas), cobertura inmediata durante carga/scroll, debounce
con espera maxima de 500 ms, cache efimero por firma y regiones semanticas cuando
Chrome si las ofrece. Las inferencias siguen siendo universales: no hay reglas
por sitio, dominio, formato ni proveedor. El cambio corregido paso gates
automaticos; no se genero una tercera microversion ni se repitio hardware solo
para una etiqueta.

## Metricas fisicas

- Screenshot: 46–433 ms en las muestras validas.
- Decision completa observada: 84–1.854 ms; la primera carga incluye abrir ONNX.
- Bloqueo controlado completo: captura 75 ms, lote 690 ms.
- Captura: 1080 x 2408, memoria temporal estimada 10.402.560 bytes.
- Proceso al cierre: PSS 159.476 KB; RSS 166.636 KB.
- Sin crash/ANR. Accessibility se restauro exactamente a `null` / deshabilitado.

## Gates automaticos finales

- `:feature-accessibility:testDebugUnitTest`: PASS.
- `:feature-accessibility:testReleaseUnitTest`: PASS.
- `:feature-accessibility:ktlintCheck`: PASS.
- `:app-user:lintDevDebug`: PASS.
- `:app-user:assembleDevDebug`: PASS.
- `git diff --check`: PASS.

## Limites abiertos

- La cobertura opaca inicial es deliberadamente conservadora y todavia no tiene
  calidad visual premium.
- Falta validar fisicamente el fallback final en una matriz dinamica, rotacion,
  teclado, pestañas, iframes/canvas y multiventana.
- R3.1 sigue siendo clasificador, no detector. Los mosaicos son un fallback
  bounded y pueden producir cobertura mas amplia que una imagen individual.
- Chrome Visual permanece DEV-only, API 34+ y ARM64; DAG sigue siendo fallback.

# DAG Browser 87 - iconos inline en SM-A235M

Fecha: 2026-08-02. Dispositivo: Samsung SM-A235M `R58T34V31AE`, Android 14.
El telefono tenia DAG 67; se instalo primero el rollback DAG 86 y despues el
candidato DAG 87, conservando datos. DAG quedo como navegador predeterminado.
No hubo push ni publicacion.

## Causa y alcance

La barrera inicial ocultaba permanentemente todo raster `data:` o `blob:` al
no poder interceptarlo mediante `webRequest`. En Google esto dejaba vacio el
espacio de favicons y miniaturas rapidas. Los raster HTTP(S) seguian llegando a
la compuerta normal, por lo que permitir mas URLs no resolvia la causa.

DAG 87 conserva `blob:` cerrado y habilita solamente `data:image` visible y
acotado: 48 KiB, 128 px por borde natural, 96 px renderizados y 16 fuentes
unicas como maximo por documento. Cada contenido usa la misma decision nativa,
se deduplica y solo `model_allow` agrega el estado visual estable. El elemento
y la fuente se vuelven a verificar antes de revelar. No hay dominios especiales
ni reescritura de `src`/`srcset`.

## Evidencia funcional

- DAG 86: favicon de Fravega ausente en resultados Google.
- DAG 87: favicon de Fravega visible.
- Segunda busqueda: favicons Moov y Sporting y miniaturas de filtros rapidos
  visibles.
- Los raster rechazados o no compatibles permanecieron en placeholder.
- Mimo abrio el menu completo con sus controles e iconos.

## Matriz

Orden: pagina / fotos iniciales / visible.

- Mimo: `2.975 / 3.068 / 273 ms`; PSS 198.533 KiB, RSS 288.208 KiB.
- Cheeky: `10.299 / 11.262 / 2.803 ms`; PSS 281.536 KiB, RSS 283.740 KiB.
- Fravega: `20.024 / no completo en 20 s / 1.187 ms`; PSS 289.665 KiB,
  RSS 299.088 KiB.

No hubo crash, ANR ni OOM. Fravega mantuvo actividad visual y su quietud
incompleta no se cuenta como aprobada. El fixture HTTPS autofirmado conserva el
bloqueo TLS conocido y no se conto.

## Verificacion

- 14 pruebas WebExtension.
- 154 pruebas unitarias Kotlin.
- Ktlint, Lint y APK DEV correctos.
- APK: `121373233` bytes.
- SHA-256: `c61e8320033bf24f0e9f80e4e268b2f9e3accc2c7b75770b4fb827c695a808c0`.
- Rollback exacto: DAG 86, commit local `3c1da59`.

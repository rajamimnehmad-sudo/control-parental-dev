# DAG Browser 198 - aislamiento documental y compuerta inline

Fecha: 2026-08-11

Dispositivo: Samsung SM-S908E

Paquete: `com.contentfilter.dagbrowser.dev`

Version: `198` / `0.70.02-dev`

Extension: `2.0.3`

APK SHA-256:
`be11b9905901a604a80a3bf028e1357a009b3c5647ca384b74f43ce1157421c7`

## Resultado

DAG 198 es un candidato local validado, instalado in-place con el perfil y los
datos conservados. No se lo declara nuevo baseline aceptado hasta completar la
prueba visual del propietario.

- El trabajo visual queda asociado a la identidad exacta de pestaña y
  documento. Navegar, retirar un documento o reutilizar un elemento invalida
  decisiones y trabajos tardíos de la generación anterior.
- `data:` y `blob:` atraviesan la misma compuerta nativa que los raster de red.
  La captura está limitada por una LRU de 64 fuentes, 1 MiB de texto retenido y
  8 MiB de bytes capturados por documento.
- SVG, ICO y MIME vectoriales ya no tienen un bypass visible general. Sólo un
  SVG pasivo, pequeño y saneable se admite como interfaz; animación, contenido
  activo, formato desconocido o fallo de decode permanecen cerrados.
- Se retiró la política de sprites raster porque podía revelar píxeles de un
  PNG grande sin una decisión de GloshIA.
- El contrato estático reconoce HEIC/HEIF; GIF/WebP animados continúan
  bloqueados por la política vigente.
- No cambiaron GloshIA Visual R3.1, su umbral `0,40`, el preprocesamiento, la
  política regional, ONNX intra/inter-op ni `final_sealed`. No hay excepciones
  por sitio, URL, dominio o dispositivo.

## Validación automatizada

- Harness WebExtension: 25/25.
- `testDagProtectionJs`: correcto.
- `testDevDebugUnitTest`: correcto.
- `ktlintCheck`: correcto.
- `assembleDevDebug`: correcto.
- `assembleLabDebug`: correcto.
- Sintaxis Python del laboratorio y `git diff --check`: correctos.
- Los SHA-256 de R3.1 y R1 fallback permanecen sin cambios.

## Validación física

La variante LAB verificó cuatro raster inline reales: los dos seguros (`data:`
y `blob:`) fueron permitidos y las dos sondas de filtro fueron bloqueadas.

Dos corridas deterministas en el SM-S908E obtuvieron:

| Señal | Corrida 1 | Corrida 2 |
| --- | ---: | ---: |
| Carga crítica, 4/4 sin error | 364 ms | 381 ms |
| Lazy, 20/20 sin error | 8.940 ms | 8.839 ms |
| Cola nativa p95 | 1 ms | 1 ms |
| Inferencia p50 | 129,48 ms | 131,36 ms |
| PSS | 276.673 KiB | 266.439 KiB |
| Frames lentos | 5,71 % | 2,86 % |
| Crash / ANR | 0 | 0 |

Matriz viva con cinco swipes y datos preservados:

| Sitio | Recursos interceptados | Cola p95 | Inferencia p50 | Frames lentos | Crash / ANR |
| --- | ---: | ---: | ---: | ---: | ---: |
| Frávega | 244 | 47 ms | 41,45 ms | 5,41 % | 0 |
| Cheeky | 151 | 74 ms | 40,00 ms | 5,13 % | 0 |
| Mimo | 93 | 33 ms | 33,82 ms | 7,32 % | 0 |

Los únicos cierres técnicos registrados fueron GIF animados, recursos
vectoriales no saneables y un píxel 1x1 inválido. No apareció una decisión
`model_allow` seguida de corrupción o descarte de sus bytes.

## Límites

- Un GIF de banner continúa bloqueado porque la política trata animación como
  video; no es una regresión de carga.
- Los fondos inline `data:`/`blob:` definidos dentro de una hoja CSS externa no
  cuentan todavía con una prueba física específica. No se agregó un barrido DOM
  costoso sin evidencia de un fallo real.
- Las cifras de sitios públicos son diagnósticas: contenido, red y caché pueden
  cambiar. El fixture local sigue siendo la comparación reproducible.
- No hubo push, publicación, Supabase ni cambios en Production.

# DAG Browser V3 - auditoría general SM-S908E

Fecha: 2026-07-29  
Dispositivo: Samsung SM-S908E  
Base física: DAG `versionCode 19`, `versionName 0.10.0-dev`  
Candidato correctivo local: DAG `versionCode 20`, extensión `1.18.0`

## Resultado de la matriz física sobre v19

No hubo crash ni ANR. El navegador y la barrera permanecieron activos.

| Página | Visible | Análisis | Imágenes |
| --- | ---: | ---: | ---: |
| Argentina.gob.ar | 243 ms | 899 ms | 1.153 ms |
| Google Imágenes, hombres | 143 ms | 4.320 ms | 4.569 ms |
| Google Imágenes, mujeres | 139 ms | 1.366 ms | 2.045 ms |
| Google búsqueda normal | 272 ms | 6.401 ms | 6.911 ms |
| Infobae | 248 ms | 7.351 ms | 8.214 ms |
| MercadoLibre | 784 ms | 2.107 ms | 2.936 ms |
| Wikipedia Argentina | 547 ms | 7.992 ms | 8.242 ms |
| YouTube | 636 ms | 3.056 ms | 3.427 ms |

El PSS llegó a aproximadamente 384 MiB después de toda la matriz; tras reiniciar
el proceso bajó a 249 MiB. No se observó crecimiento irreversible en esta prueba.

## Causas encontradas

- YouTube podía continuar audio y reproducción mediante MSE aunque el elemento
  visual estuviera oculto.
- Enlaces `intent://` de Instagram y MercadoLibre terminaban en una dirección
  bloqueada o intentaban abandonar el navegador protegido.
- Atrás podía cerrar DAG cuando una pestaña no tenía historial.
- Una fuente anterior ya aprobada podía conservar visible o pendiente un
  elemento cuya fuente activa acababa de cambiar.
- Capas alternativas de una misma imagen podían dejar `Analizando…` sobre una
  imagen ya permitida.
- Google Imágenes y overlays publicitarios explícitos no estaban cubiertos por
  los selectores acotados iniciales.
- La búsqueda de mujeres mostró falsos permisos de borde, mientras HYM mostró
  falsos filtros de hombres. Un cambio global del umbral empeoraría uno de los
  dos lados.

## Correcciones locales en v20

- Cancelación de respuestas con MIME de audio, video, DASH o HLS, además del
  bloqueo por tipo de recurso.
- Pausa, silencio y eliminación de fuentes reproducibles desde el content
  script como segunda barrera.
- Atrás vuelve a Inicio cuando no existe historial.
- Los enlaces a aplicaciones permanecen en DAG y sólo usan un fallback HTTPS
  saneado cuando existe.
- Cada elemento se vincula únicamente a su fuente activa.
- Estados del host priorizan `filtrada`, después `permitida`, y sólo mantienen
  `Analizando…` cuando no existe una imagen permitida activa.
- Ocultamiento de contenedores patrocinados conocidos de Google y de iframes
  declarados explícitamente como publicidad; sólo se expande al ancestro cuando
  es un overlay grande y fijo.
- El log DEV agrega el score numérico del modelo sin registrar URL ni píxeles,
  para reunir una ronda corta de calibración actual.

## Validación disponible

- `node --check` correcto para ambos scripts de la extensión.
- `testDevDebugUnitTest`: 59 pruebas, cero fallos.
- `ktlintCheck` correcto.
- `lintDevDebug` correcto; además se corrigió el uso de insets para Android 10.
- `assembleDevDebug` correcto.
- APK: 121.101.910 bytes.
- SHA-256:
  `338f3d8feccf14b6945ecd5eb23a37575940f299e395d409600adc3d88812f1a`.
- Firma igual a la del candidato v18 disponible, certificado SHA-256:
  `d51bc0dabd280ce1b0f098ae168eb57758faeba301156cde835737835f8a8832`.

## Pendiente físico

Instalar v20 y repetir especialmente YouTube, Instagram, Google Imágenes,
Infobae, HYM, Atrás y pestañas. La calibración del modelo se decide después de
recoger scores de ejemplos actuales confirmados por el usuario.

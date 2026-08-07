# DAG Browser 176 - baseline seguro local

Fecha: 2026-08-07

Dispositivo: Samsung SM-S908E

Android: 16

Paquete: `com.contentfilter.dagbrowser.dev`

Version: `176` / `0.69.80-dev`

Extension: `1.90.0`

APK SHA-256: `022f191baa272d12b51487fc55e5d05e34da7e4a7ec62868410a6ff620c015f5`

APK: 129.442.806 bytes

## Estado del checkpoint

El propietario acepto DAG 176 como nuevo punto seguro de partida local. La APK
se instalo con `adb install --no-streaming -r`, preservando perfil y datos, y el
proceso quedo activo. No se hizo push, publicacion DEV, cambio en Supabase ni
modificacion de Production.

Este punto no reemplaza los gates de publicacion. Fixture LAB y matriz completa
Mimo/Cheeky/Fravega deben repetirse antes de promoverlo o publicarlo.

## Cambios incluidos desde DAG 169

- El trabajo tardio de la barrera recorre solamente imagenes aun no resueltas y
  termina sus observers al estabilizarlas.
- Un cambio de `src`, `srcset` o `sizes` reinicia siempre el estado de un
  elemento `img` reutilizado antes de volver a observarlo.
- El placeholder gris de un bloqueo real se conserva y su PNG proporcional se
  reutiliza mediante una cache Android acotada a 32 tamanos.
- La cache JS sigue limitada por cantidad de decisiones y agrega un presupuesto
  maximo de 2 MiB para bytes de reemplazo.
- Las miniaturas restringidas se invalidan aunque una captura asincrona anterior
  termine tarde.
- Se retiraron el `ScrollDelegate`, el estado de scroll no consumido y el
  `SwipeRefreshLayout` deshabilitado.
- El frame de navegacion toma propiedad del bitmap capturado sin realizar una
  copia completa adicional.
- La apertura del analizador R3.1 sale del hilo principal; las pestanas
  protegidas no se restauran hasta que el analizador queda disponible.
- Los patrocinados iniciales y dinamicos se ocultan con CSS de primer pintado y
  un observer acotado a documentos de resultados, sin excepciones por dominio.

No cambiaron el modelo R3.1, sus pesos, umbral, preprocesamiento, politica
visual, hilos de analisis, ONNX intra/inter-op ni `final_sealed`.

## Experimentos descartados

- R8/minificacion de DAG 171: emitio incompatibilidad de metadata Kotlin y la
  APK se cerraba. Se retiro; DAG 176 no esta minificado.
- Transicion interactiva parcial de DAG 173: no libero el scroll y afecto la
  carga de fotos. Se retiro completa.
- Saneamiento previo a `DOMContentLoaded` de DAG 175: dejo areas de fotos
  negras/ocultas. Se retiro completa; la extension 1.90 fuerza la restauracion
  del script estable en actualizaciones in-place.

## Diagnostico de la transicion Google

Recorrido automatizado en el S22 sobre DAG 174, perfil conservado y tres swipes
inmediatamente despues de `Todo -> Imagenes`:

- `page_load_started`: 0 ms;
- `viewport_images_ready`: 971 ms;
- `page_visible`: 2.343 ms;
- `page_analysis_ready`: 2.730 ms;
- frames: 23;
- jank: 3/23 (`13,04 %`);
- alta latencia de entrada: 12 eventos;
- inferencia habitual: aproximadamente 29-66 ms; un recorrido regional de 162
  ms no explica la espera total.

La pagina anterior visible durante esa ventana es un bitmap de navegacion
seguro, por lo que no puede desplazarse. La pagina nueva vuelve a responder
cuando los tres gates permiten el reveal. Mantener la pagina anterior realmente
interactiva exigiria una segunda sesion Gecko preparada en paralelo, con costo
de RAM, complejidad y superficie de fallos. El propietario decidio no incorporar
ese costo por ahora.

## Validacion

- `testDagProtectionJs`: 23/23.
- `testDevDebugUnitTest`: correcto.
- `ktlintCheck`: correcto.
- `lintVitalDevDebug`: correcto dentro de `assembleDevDebug`.
- `assembleDevDebug`: correcto.
- Instalacion in-place y apertura en SM-S908E: correctas.
- Google Imagenes volvio a mostrar fotos despues de retirar DAG 175.
- Cero excepciones por sitio, URL, dominio o dispositivo.

## Rollback local

El commit local que incorpora este informe, el handoff y `app-dag-browser`
completo es el punto canonico de retorno. Para recuperar el baseline no se deben
copiar archivos sueltos ni restaurar DAG 173/175; se debe volver al commit
completo y generar la APK desde `main` local.

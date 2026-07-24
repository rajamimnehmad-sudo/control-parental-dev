# Archivo verificable de DAG v1

## Estado del archivo

- DAG v1 queda archivado como respaldo y no se elimina.
- Commit exacto de `main` usado como referencia: `486c564be62ab336cfc815b223343b9419370f14`.
- Fecha de generación del inventario: `2026-07-24T19:36:55Z`.
- Manifiesto verificable: `docs/dag/v1/dag-v1-model-manifest.json`.
- Verificador de sólo lectura: `scripts/dag/verify-dag-v1-archive.sh`.
- Este archivo no copia modelos ni assets. Los referencia en su ubicación original y fija tamaño y SHA-256.

## Versión DEV publicada de referencia

Al momento del archivo, la versión publicada es:

- App Usuario: `versionCode 279`, `versionName 1.0.1-dev`.
- APK Usuario: `app-user-dev-279-debug.apk`.
- SHA-256 del APK Usuario: `5d98a83b992042477059a7809df140b6545ded03f8e52704c883a8add0aee2cc`.
- App Admin: `versionCode 275`, sin cambios por DAG v1 DEV 279.

La versión DEV 279 incorpora carga progresiva, dos clasificadores visuales concurrentes y Calibración DEV binaria. El commit archivado es posterior a la fusión de ese lote en `main`.

## Archivos principales del navegador DAG

- Estado, navegación, búsqueda, precedencia y aprobación de páginas: `app-user/src/main/java/com/contentfilter/user/dag/DagBrowserViewModel.kt`.
- WebView, scripts de protección, carga progresiva y puente de calibración: `app-user/src/main/java/com/contentfilter/user/dag/DagWebViewComponents.kt`.
- Intercepción HTTPS, defensa contra destinos privados, descarga, cache y clasificación de imágenes: `app-user/src/main/java/com/contentfilter/user/dag/DagImageResourceLoader.kt`.
- Orquestación de los clasificadores visuales: `app-user/src/main/java/com/contentfilter/user/dag/DagImageClassifier.kt`.
- Política textual y de dominios: `app-user/src/main/java/com/contentfilter/user/dag/DagContentClassifier.kt`.
- Acceso a búsqueda: `app-user/src/main/java/com/contentfilter/user/dag/DagSearchRepository.kt`.
- Interfaz y activación de Calibración DEV: `app-user/src/main/java/com/contentfilter/user/dag/DagBrowserScreen.kt`.
- Entrega y reintento de etiquetas: `app-user/src/main/java/com/contentfilter/user/dag/DagCalibrationRepository.kt` y `DagCalibrationOutbox.kt`.

Los hashes de estos archivos de política están fijados en el manifiesto.

## Modelos visuales actuales

| Función | Archivo | Runtime | Entrada comprobable | SHA-256 |
| --- | --- | --- | --- | --- |
| Profesional | `app-user/src/main/assets/dag/nsfw_marqo_vit_tiny_384.onnx` | ONNX Runtime | `1 × 3 × 384 × 384`, `float32` | `0366969ece89f252f05fad2c730d6c7e3373000e1ff43e4cfab8425aad94405b` |
| Legado/fallback | `app-user/src/main/assets/dag/nsfw_open_nsfw_quantized.tflite` | LiteRT/TFLite | `1 × 224 × 224 × 3`, `float32` | `eb3b446a6a8c1a73998a76011b97cfc67bc01084c63ee195c774e71344a66442` |
| Modestia/regiones | `app-user/src/main/assets/dag/nudenet_modesty_320n_uint8.onnx` | ONNX Runtime | `1 × 3 × 320 × 320`, `float32` | `2e5d2b1471903c5bad06596dbb57bc991a489e27d5b475a62269321db42f123b` |
| Pose | `app-user/src/main/assets/dag/movenet_singlepose_lightning_int8.tflite` | LiteRT/TFLite | `1 × 192 × 192 × 3`, `uint8` | `cd7cc22fa946e5d146a7b98d496853e1923e22828d3972d579973f27f91bb105` |

### Modelo profesional

`DagProfessionalImageClassifier` declara la versión `marqo-nsfw-vit-tiny-384-2`. Produce dos logits y convierte el primero en probabilidad insegura. Se ejecuta primero en `arm64-v8a`. Una salida válida decide `Allowed`, `Uncertain` o `Blocked` con los umbrales activos.

### Modelo legado

La conversión cuantizada de Yahoo OpenNSFW es el respaldo local. Se usa cuando el modelo profesional no está disponible o queda incierto. El repositorio no declara un identificador de versión independiente para este artefacto.

### Modelo de modestia

El detector NudeNet aporta señales de rostro femenino/masculino, pecho masculino expuesto, pecho femenino cubierto, genitales femeninos cubiertos, glúteos cubiertos, axilas, abdomen y regiones explícitas. El repositorio no declara un identificador de versión independiente para este artefacto.

### Modelo de pose

MoveNet Lightning INT8 aporta 17 puntos corporales. DAG combina hombro/codo/muñeca y cadera/rodilla/tobillo con una heurística local de piel para producir `sleeves_above_elbow` y `hem_above_knee`. El repositorio no declara un identificador de versión independiente para este artefacto.

## Modelos de texto

- Modelo compacto embebido: `dag_text_intent_v1.bin`, SHA-256 `8ffb797ab8976f4a4cd18728c8a64ff5973392e30562a3e4291783f00cb9e612`. Es un clasificador lineal multinomial determinista sobre 4.096 buckets de n-gramas, con siete clases. El runtime declara `dag-local-text-3`; el contenedor binario declara formato `1`.
- Política textual por términos: `DagContentClassifier.ModelVersion = dag-local-text-7`.
- Modelo neuronal descargable sólo en DEV: `dag-multilingual-minilm-v1.onnx`, versión `dag-neural-minilm-1`, SHA-256 esperado `641b05b3775dbb94ba7291c7fe607d0bfa304b844cdf6551271018fb287c3627`. No está dentro del repositorio; Android lo reconstruye desde tres partes fijadas por tamaño y hash y sólo lo acepta después de verificar el hash final. El manifiesto registra esas partes como artefacto externo no verificable por el script del repositorio.

Las clases textuales son `general`, `sexual`, `dating`, `gambling`, `drugs`, `violence` y `sensitive_context`.

## Funcionamiento actual de búsquedas

1. La consulta se clasifica localmente antes de salir del teléfono.
2. Una consulta bloqueada no llama a Brave.
3. Una consulta permitida o incierta continúa hacia la Edge Function autenticada `dag-search`.
4. La Edge Function autoriza el dispositivo, aplica cuota y consulta Brave Search con `safesearch=strict`.
5. Android vuelve a clasificar dominio, título y descripción de cada resultado.
6. Resultados bloqueados por lista de dominios, regla Admin, plataforma no negociable o clasificador local no se muestran. Los inciertos pueden mostrarse y se reevalúan al abrir.
7. La navegación directa sólo admite HTTPS.

## Funcionamiento actual de páginas

- Dominio y URL se evalúan antes de navegar.
- Después del commit de página se extraen hasta 24.000 caracteres visibles y se clasifica dominio, título y texto.
- Una aprobación de página puede reutilizarse sólo si coinciden URL, fingerprint, versión de política y versión compuesta de modelos.
- En DEV 279 la estructura funcional se revela al quedar aprobada/protegida la página; no espera que todas las imágenes terminen.
- El DOM dinámico se procesa por lotes de hasta 48 nodos por frame.
- WebView conserva Android Safe Browsing, bloquea certificados inválidos, navegación no HTTPS, descargas, archivos, cámara, micrófono y medios. Sólo existe una excepción acotada para CAPTCHA HTTPS conocidos.
- DAG v1 modifica atributos de imágenes y `picture`, bloquea `blob:` y deshabilita Service Workers. Estas intervenciones son parte del comportamiento archivado, pero también una frontera que DAG v2 no debe heredar.

## Funcionamiento actual de imágenes

- Cada raster probable se intercepta antes de ser entregado a WebView.
- Sólo se descarga por HTTPS; DNS debe resolver exclusivamente a direcciones públicas y no se permiten redirects a HTTP.
- Límites principales: 400 imágenes por página, 4 MiB por raster, timeout de red/llamada de 15 segundos, ocho entregas simultáneas y dos pilas de clasificación.
- El modelo profesional decide primero; el legado sólo respalda ausencia o incertidumbre profesional.
- Si la decisión NSFW no es bloqueada, se ejecuta modestia. Pose se agrega cuando hay contexto femenino suficiente.
- Un bloqueo de cualquiera de las barreras prevalece; después prevalece la incertidumbre; sólo la combinación aprobada se entrega sin blur.
- Formato, error, cancelación o decisión incierta fallan de forma cerrada mediante blur o placeholder neutro.
- Se prioriza el viewport, se preserva la relación de aspecto cuando puede conocerse y los recursos inferiores se activan al acercarse mediante `IntersectionObserver`.
- SVG estático sólo se admite bajo una lista cerrada de condiciones de seguridad; GIF, animaciones, video y audio permanecen bloqueados.
- La cache de respuestas es efímera, por URL, con hasta 64 entradas y 16 MiB; se limpia al cambiar la página.

## Calibración DEV actual

- Sólo el flavor DEV compila `DAG_VISUAL_CALIBRATION_AVAILABLE=true`; Beta y Production lo compilan en `false`.
- Requiere activación explícita desde el menú y una confirmación.
- No recarga la página. Superpone `✓` y `×` en imágenes visibles ya clasificadas.
- `✓` envía una etiqueta `allow`; `×` envía una etiqueta `block`. No se pide motivo.
- La etiqueta no cambia la decisión ni crea un bloqueo exacto de esa imagen.
- Se genera una miniatura JPEG de hasta 512 px y 128 KiB; no se envía la imagen original, URL, consulta ni texto.
- Se deduplica por SHA-256 de miniatura, dispositivo y versión de modelo. El outbox local se cifra con Android Keystore y reintenta por WorkManager.
- Las clasificaciones inciertas también pueden generar casos automáticos.
- Supabase DEV puede proponer el primer candidato con 40 etiquetas, al menos 10 `allow` y 10 `block`, y luego sólo con 10 etiquetas adicionales. Ningún candidato se activa automáticamente.
- La activación y el rollback de thresholds son manuales desde Super Web.

## Thresholds actuales

Estos son los defaults efectivos declarados en `DagImageCalibration`. Una calibración activa de Supabase DEV puede reemplazarlos dentro de los límites indicados.

| Señal | Default | Límite seguro |
| --- | ---: | --- |
| `professional_safe` | 0,15 | 0,05–0,75 y al menos 0,05 por debajo de `professional_block` |
| `professional_block` | 0,65 | 0,35–0,90 |
| `female_face` | 0,30 | 0,12–0,65 |
| `male_face` | 0,30 | 0,12–0,65 |
| `male_breast_exposed` | 0,55 | 0,25–0,90 |
| `female_breast_covered` | 0,18 | 0,08–0,65 |
| `female_genitalia_covered` | 0,18 | 0,08–0,65 |
| `buttocks_covered` | 0,18 | 0,08–0,65 |
| `armpits_exposed` | 0,20 | 0,08–0,65 |
| `belly_exposed` | 0,20 | 0,08–0,65 |
| `explicit_region` | 0,20 | 0,08–0,65 |
| `sleeves_above_elbow` | 0,72 | 0,45–0,95 |
| `hem_above_knee` | 0,72 | 0,45–0,95 |

Una señal de modestia se considera bloqueo fuerte al alcanzar `threshold + 0,15`, con máximo 1,00. MoveNet exige además confianza mínima de keypoint 0,48 y proporción de piel 0,58.

Para texto semántico, bloqueo requiere confianza `>= 0,55` y margen `>= 0,15`; revisión requiere confianza `>= 0,30` y margen `>= 0,05`. Un bloqueo semántico de página por debajo de 0,82 se trata como página protegida.

El repositorio no contiene una instantánea autenticada del JSON de thresholds activo en Supabase DEV. Por eso este archivo no atribuye valores externos no comprobables: fija defaults, límites y código exacto. La versión activa remota es estado mutable y debe exportarse por separado si se requiere una restauración byte a byte de esa base.

## Precedencia de decisiones

### Sitios y páginas

1. Una regla Admin `Block` aplicable bloquea.
2. `unsafe_visual_platform` y `search_portal` son categorías no reemplazables por una regla Admin `Allow`.
3. La lista dinámica de dominios bloqueados y las reglas explícitas de términos se evalúan antes del modelo semántico.
4. Una regla Admin `Allow` puede reemplazar otras decisiones locales que no sean no negociables.
5. `Blocked` bloquea; `Uncertain` se muestra como resultado o página protegida según el flujo; errores y ausencia de evidencia suficiente no generan una aprobación.

### Imágenes

1. Decisión profesional válida.
2. Legado sólo si la profesional no está disponible o es incierta.
3. Modestia y pose pueden elevar a incierto o bloqueado.
4. Cualquier `Blocked` prevalece; luego cualquier `Uncertain`; sólo `Allowed + Allowed` permite.
5. Contexto de niño pequeño o pecho exclusivamente masculino puede relajar la salida NSFW general únicamente cuando modestia no detectó una prohibición universal.

## Infraestructura Supabase utilizada

Sólo DEV: proyecto `syeycayasyufedwoprea`.

- Edge Function `dag-search`: autorización por dispositivo, cuota, secreto servidor de Brave y SafeSearch estricto.
- RPC principales: `authorize_and_consume_dag_search` y `authorize_dag_suggestions`.
- Edge Function `dag-calibration`: lectura de calibración activa, recepción autenticada, deduplicación, límites, creación de candidatos y limpieza administrativa.
- Tablas con RLS: `dag_calibration_reviews`, `dag_calibration_versions`, `dag_calibration_models`, `dag_calibration_audit` y relaciones agregadas por migraciones posteriores.
- Bucket privado `dag-calibration`, limitado a JPEG de 128 KiB.
- Las migraciones DAG desde `20260714180000_dag_search_authorization.sql` hasta `20260724124019_dag_binary_manual_calibration.sql` forman la historia reproducible del backend.
- El Service Role Key queda exclusivamente dentro de Edge Functions; no está en Android.

## Limitaciones conocidas

- La política visual v1 emerge de varios modelos, heurísticas, contexto derivado de URL y thresholds superpuestos; no representa directamente el contrato de producto completo.
- El contexto de edad y género se infiere parcialmente desde URL/metadatos y detección de rostro; no existe una señal entrenada específica para el corte de 10 años requerido por DAG v2.
- No hay señales propias para ropa ceñida, bodycon, transparencia, silueta o grupos; se aproximan con regiones, pose y metadatos.
- DAG v1 difumina algunos bloqueos en vez de usar siempre un placeholder neutro.
- La instrumentación del DOM modifica `src`, `srcset`, estilos y clases, y deshabilita Service Workers; puede interferir con React, Next.js, lazy loaders y carruseles.
- El análisis textual completo y las mutaciones del DOM pueden repetirse en ciclos del sitio.
- La cache de imagen usa URL y no hash de contenido; una URL que cambia de bytes dentro de la misma página puede ser problemática.
- El modelo neuronal de texto descargable no está archivado dentro del repositorio, aunque sus hashes y partes sí están fijados en código.
- El JSON de la calibración activa remota no está versionado en Git.
- La clasificación probabilística no garantiza cero falsos positivos ni cero falsos negativos.

## Problemas de rendimiento conocidos

Matriz física sin cache del 2026-07-24 en Samsung SM-S908E, Android 16. Cada celda es `página/texto / imágenes del viewport / página visible`:

- Frávega: `968 / 436 / 968 ms`.
- Mimo: `1.635 / 2.125 / 2.125 ms`.
- Cheeky: `1.741 / 8.486 / 8.486 ms`.

Cheeky queda limitada por la cola visual. Un prefiltro previo fue descartado porque elevó Frávega a 11.502 ms y ejecutaba casi siempre el recorrido completo. Los modelos se preparan en paralelo, pero dos pilas completas aumentan memoria nativa y CPU. Las cifras son evidencia operativa del dispositivo objetivo, no percentiles de laboratorio.

## Rollback exacto

El rollback de código debe preservar datos y usar una nueva versión Android; no se debe desinstalar la app ni borrar datos.

```bash
git fetch origin
git worktree add ../content-filter-dag-v1-rollback 486c564be62ab336cfc815b223343b9419370f14
cd ../content-filter-dag-v1-rollback
bash scripts/dag/verify-dag-v1-archive.sh
git switch -c codex/dag-v1-rollback-486c564
```

Luego, sólo mediante un ticket Android aprobado:

1. Incrementar App Usuario a un `versionCode` mayor que el publicado.
2. Ejecutar las validaciones Android proporcionales.
3. Commit y push del rollback explícito.
4. Publicar exclusivamente DEV por el workflow oficial para Usuario.
5. Verificar manifiesto, hash, paquete, versión y firma.
6. Instalar in-place; no desinstalar ni borrar datos.

La calibración remota no se revierte con Git. Si fuera necesario restaurarla, se debe reactivar manualmente una versión histórica desde el mecanismo auditado de Super Web. Production no forma parte de este procedimiento.

## Declaración de preservación

DAG v1 permanece completo en el commit de referencia, en sus assets originales y en la historia Git. DAG v2 vivirá separado hasta su validación y activación final. No se moverá ni eliminará DAG v1 antes de demostrar que v2 es estable y que el rollback está probado.

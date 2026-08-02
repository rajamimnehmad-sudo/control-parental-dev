# DAG Browser 66 - GloshIA como compuerta unica de imagenes

Fecha: 2026-08-02

## Objetivo

Reconectar el unico modelo local GloshIA sobre la base simple de DAG 65 sin
restaurar el sistema anterior de observadores, estados DOM, remapeo de fuentes
o excepciones por sitio. Una respuesta raster de red queda retenida una sola
vez, recibe una unica decision nativa y recien entonces llega a Gecko.

## Candidato

- paquete: `com.contentfilter.dagbrowser.dev`;
- version: `66` / `0.46.0-dev`;
- extension incorporada: `1.35.2`;
- dispositivo: Samsung SM-S908E `R5CT717BZTZ`, Android 16;
- instalacion: in-place, sin borrar el perfil;
- APK: `app-dag-browser/build/outputs/apk/dev/debug/DagBrowser-dev-debug.apk`;
- tamaño: `121358681` bytes;
- SHA-256:
  `5fc74af7f5b962415fc756f04002df76b7ec48bb15360c708b0965aae2b6b397`.

No se hizo push, PR, publicacion DEV ni Production. Supabase no participa.

## Arquitectura activa

- `image`, `imageset` y un raster abierto como pagina principal pasan por
  `filterResponseData` antes del render;
- SVG e iconos vectoriales seguros siguen directamente en Gecko;
- cada raster admite hasta 2 MiB, con 8 MiB capturados en total, 32 respuestas
  abiertas, 24 analisis esperando y dos decisiones nativas simultaneas;
- la caché efimera de 512 entradas se identifica por SHA-256 de los bytes, no
  por URL;
- solo `model_allow` libera exactamente los bytes originales;
- `model_filter`, animacion, error, timeout, cola llena o respuesta invalida
  fallan cerrados;
- un bloqueo del modelo se reemplaza por un PNG neutro proporcional, con borde
  maximo de 320 px y sin reutilizar pixeles rechazados;
- no hay `MutationObserver`, atributos de estado multimedia, asociacion
  URL-elemento ni CSS que seleccione `img`, `image` o `svg`;
- anuncios y video/audio/object continúan en rutas separadas.

El puente activo de extension y pagina suma 449 lineas. Android recibe un solo
mensaje `media-bytes` y devuelve una sola `media-decision`; se retiraron del
recorrido vigente mensajes de presentacion, diagnostico y metricas del sistema
anterior.

## Gates automaticos

- WebExtension: 11 aprobadas, 0 fallos;
- Kotlin/JVM: 147 aprobadas, 0 fallos y 0 omitidas;
- `ktlintCheck`: correcto;
- `lintDevDebug`: correcto;
- `assembleDevDebug`: correcto;
- `git diff --check`: correcto;
- unica advertencia: override Gecko deprecado ya existente, sin fallo de gate.

Las pruebas cubren permiso byte a byte, reemplazo sin pixeles rechazados,
vectores, imagen principal, caché por contenido, desconexion, exceso de tamaño,
aislamiento de video/anuncios, ausencia de decisiones DOM y limites de trabajo.

## Matriz fisica

Tiempos locales del SM-S908E y esta red; no son porcentajes universales ni una
comparacion controlada contra Chrome.

| Caso | `page_visible` | `page_analysis_ready` | `viewport_images_ready` | Resultado |
| --- | ---: | ---: | ---: | --- |
| Mimo, arranque limpio del APK final | 374 ms | 1.462 ms | 1.805 ms | Header e imagen permitida visibles; animacion y una foto filtrada usan reemplazo neutro |
| Cheeky | 1.801 ms | 3.409 ms | 3.670 ms | Menu, cuenta, logo, favorito, bolsa y portada completos |
| Fravega | 834 ms | 8.621 ms | 8.943 ms | Header, controles, categorias e imagenes visibles; pagina con gran rafaga de recursos |
| Raster directo permitido | 84 ms | 328 ms | 571 ms | Bytes originales visibles; decision nativa en 162 ms |
| Raster directo filtrado, APK final | 260 ms | 328 ms | 576 ms | Reemplazo neutro; decision nativa en 48 ms |

Cheeky se recargo tres veces consecutivas antes de la limpieza final del puente
y dio 3/3 resultados visuales identicos. La limpieza solo elimino mensajes sin
emisor/receptor y el APK final repitio Mimo y el bloqueo directo. No aparecieron
crash, ANR, OOM ni salida inesperada.

En caliente, una inferencia comun observada estuvo normalmente en 35-100 ms;
la revision regional de cuatro o cinco recortes estuvo aproximadamente en
160-300 ms. El arranque frio puede ser mayor: Mimo registro una revision
regional de 444 ms. Con trece pestañas acumuladas, la muestra de memoria dio
308249 KiB PSS y 481748 KiB RSS; no representa el costo aislado de GloshIA.

## Limites conocidos

- imagenes animadas fallan cerradas porque este modelo no revisa todos los
  fotogramas;
- una respuesta mayor a 2 MiB o sin dimensiones recuperables usa el reemplazo
  tecnico minimo y puede conservar peor el espacio que un bloqueo clasificado;
- `canvas` permanece oculto; medios creados enteramente como `data:` o `blob:`
  no tienen todavia una cobertura equivalente demostrada por esta compuerta de
  respuesta de red;
- el fixture HTTP local fue rechazado correctamente por la politica HTTPS. No
  se relajo navegacion ni TLS para convertirlo artificialmente en un exito;
- la matriz DEV no convierte el modelo piloto ni su corpus en una aprobacion
  para Production y no demuestra cobertura universal.

## Conclusion

DAG 66 vuelve a filtrar con GloshIA local y conserva la compatibilidad que se
recupero en DAG 65: una sola autoridad antes del render y ningun segundo sistema
que deba encontrar el elemento correcto en el DOM. Es un candidato DEV local
validado, no una version autorizada para publicacion.

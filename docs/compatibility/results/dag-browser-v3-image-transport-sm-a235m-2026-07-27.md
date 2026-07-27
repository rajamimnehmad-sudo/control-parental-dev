# DAG Browser V3 image transport - Samsung SM-A235M - 2026-07-27

## Corte probado

- Rama: `codex/dag-bounded-image-transport`.
- Codigo probado: `db5dcb153829aa939a60870c2ac822969f3c0722`.
- Pull request: `#78`.
- Aplicacion: `com.contentfilter.dagbrowser.dev`.
- Version: `0.3.0-dev` (`versionCode 3`).
- Extension incorporada: `Glosh DAG Protection 1.1.1`.
- Dispositivo: Samsung SM-A235M, `arm64-v8a`.
- Android: 14, API 34.
- APK DEV firmada: `102587418` bytes.
- SHA-256: `607233dfcfbeb69baabb801da060eb781ba71e8b9141812ee3fc25c95f164d27`.
- Workflow firmado: GitHub Actions `30309362721`.

El workflow verifico la firma con `apksigner` antes de publicar el artefacto. La suma descargada
coincidio exactamente con la suma generada en la nube. No se uso ni modifico Production.

## Verificacion automatica

Pasaron:

- sintaxis de `background.js` y `barrier.js`;
- `ktlintCheck`, `testDevDebugUnitTest` y `assembleDevDebug`;
- los checks de pull request `Build, tests, lint, detekt` y `Verificar navegador protegido`;
- una simulacion del fondo con 16 filtros activos, 10 pedidos nativos simultaneos, cola llena,
  cuerpo mayor a 256 KiB, respuesta lenta y URL excesiva.

Los tests nativos mantienen `block` para Base64 invalido, longitud alterada, formato no soportado,
dimensiones peligrosas y analizador ausente.

## Verificacion fisica

| Caso | Resultado |
| --- | --- |
| Actualizacion sobre el corte anterior | Gecko instalo `1.1.1` y registro que deshabilito la version anterior |
| Google Imagenes `bosques`, uso normal | 80 respuestas, 3113366 bytes; ninguna foto visible |
| Latencia de validacion preliminar | 0,85 ms promedio; 7 ms maximo; cuerpo maximo observado 96264 bytes |
| Google Imagenes `paisajes`, scroll extremo | Los limites globales permanecieron activos; ninguna foto visible y ningun crash |
| JPEG de 35588 bytes | Llego a Android; `analyzer_unavailable`; bloqueado |
| PNG de 8090 bytes | Llego a Android; `analyzer_unavailable`; bloqueado |
| WebP de 10568 bytes | Llego a Android; `analyzer_unavailable`; bloqueado |
| SVG de 8984 bytes | Llego a Android; `unsupported_image`; bloqueado |
| 1024 bytes corruptos | Llego a Android; `unsupported_image`; bloqueado |
| JPEG fijo de 766063 bytes | Cortado antes del canal nativo por superar 256 KiB; invisible |
| Respuesta de 8 segundos | Cortada antes del canal nativo al vencer 5 segundos; invisible |
| JPEG abierto como pagina principal | No atraveso la tuberia de subimagenes y la pagina completa quedo cerrada; sin fuga |
| Arranque frio y regreso al frente | Correctos; proteccion lista y sin crash |
| Wi-Fi y datos desactivados | La superficie quedo cerrada al no confirmar la barrera |
| Recuperacion de red | Recarga correcta; texto visible e imagenes ausentes |

Despues de 80 respuestas y desplazamiento normal, una captura de memoria mostro:

- PSS total: `240393 KiB`;
- RSS total: `360004 KiB`;
- Java heap: `3800 KiB`;
- native heap: `9344 KiB`;
- graphics: `43648 KiB`.

Es una fotografia puntual del proceso Gecko y no una afirmacion de consumo estable. No se observaron
`FATAL EXCEPTION`, ANR ni `OutOfMemoryError`.

Los logs DEV usados para medir contienen solamente cantidad de bytes, motivo y tiempo. No contienen
URL, imagen, Base64 ni contenido de pagina.

## Lectura del gate

El gate de transporte local y acotado queda correcto en el Samsung objetivo:

- los bytes examinados son los de la respuesta original;
- nunca se escriben hacia la pagina antes de la decision;
- formato invalido, exceso, demora, saturacion o ausencia del analizador terminan en bloqueo;
- ninguna prueba mostro una foto o destello.

Este corte no contiene un clasificador y no habilita `allow` ni `blur`. El siguiente gate es reducir
la imagen localmente a la entrada del modelo y comparar candidatos con el mismo conjunto de
evaluacion. Los limites de concurrencia tambien deben formar parte de ese benchmark: bajo carga
extrema pueden producir espacios vacios, pero nunca deben relajarse de una forma que permita una
fuga.

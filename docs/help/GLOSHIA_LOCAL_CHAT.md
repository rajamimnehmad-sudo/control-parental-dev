# GloshIA Ayuda local

## Alcance

App Usuario combina dos capas:

1. La ayuda determinista existente conserva el estado real, las acciones
   permitidas y los reportes técnicos seguros.
2. Un modelo generativo local convierte esa guía en una conversación breve y
   natural, con contexto corto entre mensajes.

Si el modelo falta, no es compatible o falla, la ayuda determinista continúa
funcionando. El modelo nunca decide acciones ni construye el contenido enviado
a Superweb.

## Runtime y modelo

- Runtime Android: `com.google.ai.edge.litertlm:litertlm-android:0.14.0`.
- Modelo: `litert-community/Qwen2-0.5B-Instruct`.
- Artefacto: `Qwen2_0.5B_Instruct.litertlm`.
- Revisión fija:
  `f2949f79a8154234747a794348d77554ae0e1fb0`.
- Tamaño: `647377840` bytes.
- SHA-256:
  `0f01cc004b8eb62b92ba6be85ed05a248ba0d2f78af94c4949b313eccfb4c157`.
- Licencia del modelo: Apache-2.0.
- Arquitectura habilitada: ARM64 con al menos 256 MB de `memoryClass`.

El APK no incorpora los 647 MB. La app descarga el modelo una vez, permite
reanudar la descarga y valida tamaño y SHA-256 antes de confiar en él.

## Privacidad y seguridad

- La generación ocurre dentro del teléfono y no usa una API de IA.
- El texto del chat no se envía a Supabase ni a otro servidor.
- La conexión se usa solamente para descargar el artefacto fijo del modelo.
- El prompt prohíbe solicitar o repetir contraseñas, códigos, tokens, fotos,
  búsquedas o información íntima.
- La salida se limita y vuelve a redactar secretos detectados en la consulta.
- Los reportes automáticos continúan usando exclusivamente resúmenes y códigos
  deterministas permitidos.

## Toolchain

LiteRT-LM 0.14 usa bytecode Java 21 y metadatos Kotlin 2.3. El proyecto usa:

- Java 21 para ejecutar Gradle.
- Kotlin `2.3.20`.
- KSP `2.3.0`.
- Hilt `2.58`.
- R8 `8.13.19`, versión mínima oficial para metadatos Kotlin 2.3.
- Bytecode de aplicación objetivo Java 17.

`gradle/gradle-daemon-jvm.properties` fija Java 21 para el daemon. En la Mac de
desarrollo, Gradle descubre el JDK de Homebrew mediante la configuración local
de `~/.gradle/gradle.properties`.

## Validación mínima

```bash
./gradlew --no-daemon \
  :app-user:ktlintCheck \
  :app-user:testDevDebugUnitTest \
  :app-user:assembleDevDebug \
  -x uploadDevUpdatesToStorage \
  -x prepareDevUpdatesForStorage
```

Además del build, cada candidato debe probarse en un teléfono ARM64 con el
modelo verificado: saludo, pregunta del proyecto, repregunta contextual,
consulta ajena al proyecto y una entrada con un secreto sintético.

# DAG Browser V3 - foundation aislada

## Objetivo autorizado

Construir y validar las etapas 0 a 2 del navegador nuevo:

1. crear una APK de navegador separada solo para DEV;
2. demostrar una barrera visual fail-closed antes de integrar IA;
3. exigir que no exista ningún runtime o fallback anterior antes de habilitar el puente con Glosh.

## Baseline y aislamiento

- Base oficial: `origin/main` en `105c93c`.
- Rama: `codex/dag-browser-v3-foundation-01`.
- Worktree limpio y persistente: `/Users/yejielnehmad/Developer/content-filter-dag-browser-v3`.
- El navegador no importa código, modelos, tablas ni Edge Functions de implementaciones retiradas.
- La aplicacion nueva es tambien un build Gradle independiente: no fuerza a Glosh a cambiar AGP,
  Kotlin, AndroidX o GeckoView.
- No depende de ningun modulo del proyecto. Solo usa AndroidX Core y una version fijada de
  GeckoView.
- La integración posterior sólo modifica el acceso desde App Usuario. No cambia VPN,
  Accessibility, Device Owner ni Production.

## Contrato del primer prototipo

- APK distinta: `com.contentfilter.dagbrowser.dev`.
- Solo ABI `arm64-v8a` durante el gate fisico inicial.
- Solo navegacion HTTPS.
- Las consultas sin URL usan Google con `safe=active`.
- Toda navegacion a Google Search vuelve a exigir `safe=active`.
- Ventanas emergentes no crean otra sesion sin control.
- La superficie Gecko permanece invisible hasta recibir un handshake valido de la extension
  incorporada desde el marco principal.
- Si el handshake falta, falla o vence, la pagina permanece cerrada.
- Imagenes, media y objetos se cancelan en la capa de red.
- Raster, `picture`, video, audio, canvas, SVG, fondos, pseudo-elementos y recursos agregados
  dinamicamente permanecen ocultos en el documento.
- No existe clasificador, permiso remoto, historial sincronizado, carga visual o calibracion.

## Gates

### Gate 0 - aislamiento

- `app-dag-browser` no contiene dependencias ni imports del runtime retirado.
- `app-dag-browser` no tiene dependencias `project(...)`.
- No hay claves, URL de Supabase ni funciones remotas en la APK.

### Gate 1 - foundation local

- Tests unitarios de normalizacion, HTTPS y SafeSearch correctos.
- Contrato estatico de la extension correcto.
- `app-dag-browser/gradlew assembleDevDebug` correcto.
- Lint/Ktlint del modulo correctos.

### Gate 2 - prueba fisica

En el mismo Samsung objetivo y sin cache asistente:

- Google;
- Fravega;
- Mimo;
- Cheeky;
- navegacion atras, recarga y enlace con `target=_blank`;
- conexion lenta, offline y recuperacion;
- background/foreground y reinicio del proceso.

Aceptacion:

- nunca se observa una foto, video, canvas, SVG o fondo fotografico;
- el texto y la navegacion basica siguen siendo utilizables;
- una falla de extension deja la superficie cerrada;
- no hay crash, ANR ni apertura de otra aplicacion;
- se registra dispositivo, Android, commit, APK, fecha y evidencia.

No se agrega IA ni se conecta Glosh hasta cerrar este gate fisico.

Estado del primer corte, 2026-07-27:

- correcto en Samsung SM-A235M con Android 14;
- Google con SafeSearch, Fravega, Mimo y Cheeky correctos sin imagenes visibles;
- recarga observada en ocho capturas consecutivas sin destello visual;
- sin conexion queda cerrado y, al recuperar la red, recarga correctamente;
- background/foreground y reinicio del proceso correctos;
- `target=_blank` correcto dentro de la misma sesion protegida;
- queda pendiente repetir sobre la APK firmada candidata.

Evidencia:
`docs/compatibility/results/dag-browser-v3-foundation-sm-a235m-2026-07-27.md`.

Puente controlado:
`docs/dag/v3/DAG_BROWSER_V3_GLOSH_BRIDGE.md`.

## Etapa 3 - contrato de analisis visual

La extension ya puede registrar una imagen candidata y consultar al componente Android mediante
un protocolo local versionado. El mensaje contiene identificador, URL, pagina, texto alternativo y
dimensiones; no sube la foto ni usa Supabase.

El contrato inicial es deliberadamente cerrado:

- el unico resultado aceptado es `block`;
- una respuesta ausente, invalida, alterada o de otra version no cambia el estado oculto;
- una URL local, metadatos excesivos o dimensiones invalidas tambien se bloquean;
- se limita la cantidad de candidatas por documento para evitar abuso;
- la barrera de red sigue cancelando los recursos visuales.

Esta etapa no afirma que exista un clasificador. Su objetivo es preparar y probar el limite seguro
antes de transportar pixeles o incorporar un modelo. Las acciones `allow` y `blur` no se habilitan
hasta completar el benchmark en el Samsung objetivo y cerrar las pruebas contra fugas visuales.

Plan de seleccion y calibracion:
`docs/dag/v3/DAG_BROWSER_V3_MODEL_BENCHMARK.md`.

Transporte de imagenes:
`docs/dag/v3/DAG_BROWSER_V3_IMAGE_PIPELINE.md`.

## Publicacion e instalacion

La publicación DEV usa dos caminos separados:

- App Usuario sigue usando su publicador DEV habitual, con el puente habilitado sólo en el
  candidato autorizado.
- El navegador protegido se compila y firma en un workflow separado y se entrega como artefacto
  privado de GitHub Actions. No reutiliza el manifiesto de App Usuario ni toca Production.

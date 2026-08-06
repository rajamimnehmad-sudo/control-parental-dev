# GloshIA partial redaction — laboratorio

Fecha: 2026-08-05  
Estado: APK local de prueba; no es una publicación ni reemplaza R3.1

## Alcance

El flavor `lab` prueba una capa de presentación independiente del modelo. R3.1
continúa siendo el único modelo oficial y el flavor `dev` mantiene el flujo
normal `allow/block`.

La capa usa las vistas regionales que ya produce el preprocesador. Si hay una
sola región con riesgo moderado (`>= 0,60`) y la probabilidad global es menor
que `0,72`, intenta generar una imagen PNG con difuminado fuerte tipo vidrio
esmerilado sobre esa región. No se entrena otro modelo ni se cambia el umbral
oficial `0,40`.

Se bloquea la imagen completa cuando:

- la probabilidad global es `>= 0,72`;
- hay dos o más regiones de riesgo;
- una región alcanza `0,72`;
- la imagen no permite localizar una región o no se puede generar un reemplazo;
- hay error de decodificación, timeout, runtime o formato.

El redactor no decide por género, sitio, URL o campaña. Es una prueba de
presentación: la política final debe validarse con revisión humana antes de
cualquier uso real.

## APK

- Variante: `labDebug`.
- Application ID: `com.contentfilter.dagbrowser.lab`.
- Version code: `111`.
- Version name: `0.69.13-lab`.
- Artefacto local: `app-dag-browser/build/outputs/apk/lab/debug/DagBrowser-lab-debug.apk`.
- SHA-256: `c7741e504d400bee235531d8996eb2a236401ae7c9363552b255b2c47fd6a17d`.
- Tamaño: aproximadamente `124 MB`.

No instalar encima de DAG DEV: es otra aplicación por el sufijo `.lab`.
La APK no fue publicada, subida ni instalada automáticamente.

## Validación ejecutada

- `testLabDebugUnitTest`: OK.
- `testDevDebugUnitTest`: OK; la variante oficial conserva su flujo.
- `ktlintCheck`: OK.
- WebExtension `node --test src/test/js/dag-protection.test.mjs`: 17/17 OK.
- Test específico: una redacción entrega el reemplazo validado y los fallos
  permanecen bloqueados.

## Limitaciones conocidas

Las vistas regionales no son una segmentación anatómica exacta. Por eso el
difuminado puede cubrir una región más grande de la necesaria y los casos
ambiguos se bloquean completos. Este APK sirve para evaluar la experiencia
visual y la seguridad del fallback, no para declarar una mejora de precisión
ni para integrar la función en DAG oficial.

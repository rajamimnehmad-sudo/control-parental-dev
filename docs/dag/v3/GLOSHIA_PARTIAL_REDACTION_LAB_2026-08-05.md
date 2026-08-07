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
- SHA-256: `a6bef885002da151270690087da1bc9e6fb739411e15f4cbcf6bc2d81d67b586`.
- Tamaño: aproximadamente `124 MB`.

No instalar encima de DAG DEV: es otra aplicación por el sufijo `.lab`.
La APK fue instalada localmente en el S22 sólo para la prueba y quedó abierta;
no fue publicada ni subida.

## Validación ejecutada

- `testLabDebugUnitTest`: OK.
- `testDevDebugUnitTest`: OK; la variante oficial conserva su flujo.
- `ktlintCheck`: OK.
- WebExtension `node --test src/test/js/dag-protection.test.mjs`: 17/17 OK.
- Android instrumentado en S22 Ultra (`SM-S908E`, Android 16): 6 pruebas
  completadas, 2 escenarios opcionales omitidos, sin fallos; el modelo hizo
  22/22 inferencias con p50 `30,86 ms`, p95 `32,93 ms`, PSS `116.416 KiB`.
- Test específico: una redacción entrega el reemplazo validado y los fallos
  permanecen bloqueados.
- Reparación verificada en carga real: LAB ahora genera tres vistas regionales
  también para fotos verticales y horizontales normales; antes sólo lo hacía
  con proporciones de al menos 2:1. En el S22 se observó `action=redact` con un
  reemplazo PNG y la captura mostró el panel de vidrio, mientras otras imágenes
  permitidas siguieron visibles.

## Limitaciones conocidas

Las vistas regionales no son una segmentación anatómica exacta. Por eso el
difuminado puede cubrir una región más grande de la necesaria y los casos
ambiguos se bloquean completos. Este APK sirve para evaluar la experiencia
visual y la seguridad del fallback, no para declarar una mejora de precisión
ni para integrar la función en DAG oficial.

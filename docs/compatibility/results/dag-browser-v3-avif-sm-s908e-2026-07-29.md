# DAG Browser V3 v19 - AVIF y estados de imagen en SM-S908E

Fecha: 2026-07-29
Dispositivo: Samsung SM-S908E
Android: 16
Paquete: `com.contentfilter.dagbrowser.dev`
APK: `versionCode 19`, `versionName 0.10.0-dev`
Extensión incorporada: `1.17.0`

## Alcance

- AVIF estático bajo el preprocesador acotado y el clasificador ONNX local;
- separación entre carga, decisión `model_filter` y error técnico;
- escudo pequeño para blur, contenido dentro del apilado de su propia foto;
- recorrido físico de H&M, Cheeky, Frávega, Mimo e Instagram.

No se cambió el modelo, el umbral `0.4`, la política visual ni el fallo cerrado.
SVG remoto, animaciones, payloads inválidos y fallas de decodificación continúan
sin mostrar píxeles.

## Resultado físico

| Destino | Resultado |
| --- | --- |
| H&M Hombre | 10 `model_allow`, 3 `model_filter`, 1 `unsafe_dimensions`; las fotos AVIF permitidas aparecen |
| Cheeky | Portada, buscador y fotos de niños visibles; recursos SVG técnicos permanecen cerrados |
| Frávega | 78 `model_allow`, 1 `model_filter`, 1 `decode_failed`; estructura y productos utilizables |
| Mimo | La repetición cargó fotos permitidas, una filtrada y errores técnicos separados; el primer intento registró errores JavaScript de VTEX |
| Instagram | Abre página oficial, navegación e inicio de sesión; sólo un SVG decorativo de Meta queda oculto |

El escudo visual reemplaza la franja `Protegida por Glosh`. La descripción
accesible se conserva únicamente para una decisión filtrada. El error técnico
usa `Imagen no disponible` y nunca se cachea como una clasificación.

## Validación y artefacto

- `node --check` correcto para ambos scripts;
- `ktlintCheck`, unitarios, `assembleDevDebug` y `lintVitalDevDebug` correctos;
- 61 tareas Gradle correctas;
- instalación in-place correcta, sin crash ni ANR;
- DAG conserva el rol de navegador predeterminado;
- Accessibility de Glosh fue restaurado después de que Android lo desactivara
  durante la actualización y quedó enlazado.

```text
app-dag-browser/build/outputs/apk/dev/debug/DagBrowser-dev-debug.apk
121096554 bytes
SHA-256 aa221b1ed17e4b9c13709fc90eb6ccb5bb63be8ea3ff0c9fead6d661880fdc8d
Signer SHA-256 d51bc0dabd280ce1b0f098ae168eb57758faeba301156cde835737835f8a8832
```

No hubo publicación remota ni cambios en App Usuario, App Admin, Supabase,
Production o iCloud.

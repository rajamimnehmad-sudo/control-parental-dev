# Matriz representativa de dispositivos

## Base técnica

- App Usuario y App Admin: `minSdk 29`, `targetSdk 36`, `compileSdk 36`.
- Rango diseñado actual: Android 10/API 29 hasta Android 16/API 36.
- App Usuario DEV: `arm64-v8a` y `armeabi-v7a`. El runtime neuronal ONNX está excluido en ARM32; ese caso conserva el clasificador compacto. La variante exclusiva `compatibility` agrega `x86_64` para laboratorio sin cambiar el APK DEV.
- App Admin: sin filtro ABI explícito y sin dependencia nativa propia identificada.
- Dependencias nativas relevantes de Usuario: TensorFlow Lite, ONNX Runtime y ONNX Runtime Extensions.

Las APIs se derivan del rango realmente declarado. Al cambiar `minSdk` o `targetSdk`, se debe revisar esta matriz y confirmar que existan imágenes de sistema/catálogo antes de ejecutar.

## Matriz Nivel 1

| Celda | API | Forma | Fuente | Apps | Objetivo | Estado inicial |
| --- | ---: | --- | --- | --- | --- | --- |
| V1 | 29 (mínima) | teléfono pequeño, 4.7–5.0\" | normal | Usuario/Admin | compatibilidad mínima, arranque y recreación | Automatizado; ejecución pendiente |
| V2 | 32 (intermedia) | teléfono normal, ~6\" | grande 1.3x | Usuario/Admin | configuración, orientación y layout | Automatizado; ejecución pendiente |
| V3 | 36 (máxima declarada) | tablet, ~10\" | normal | Usuario/Admin | último Android soportado y ventana grande | Automatizado; ejecución pendiente |

`V1–V3` son una matriz pequeña de riesgo, no una afirmación de cobertura de todos los modelos. La configuración Gradle se limita a estos tres perfiles y no corre por defecto en CI.

## Familias físicas/laboratorio

| Familia | Representación recomendada | Android/gama | Riesgo principal | Prioridad | Estado |
| --- | --- | --- | --- | --- | --- |
| Samsung / One UI | Galaxy A (media/baja) y S (alta) | mínimo vigente y reciente | batería, Ajustes restringidos, actualización, Accessibility | P0 | SM-A235M Android 13 validado parcialmente en DEV 267 |
| Motorola | Moto G y Edge | media y reciente | batería, procesos en segundo plano, rutas de Ajustes | P0 | Pendiente |
| Xiaomi/Redmi/POCO | Redmi Note/POCO y Xiaomi principal | media y reciente | HyperOS/MIUI, autoinicio, batería, permisos | P0 | Pendiente |
| Honor / MagicOS | serie X y Number/Magic | media/alta | batería, intents OEM, segundo plano | P1 | Pendiente |
| Oppo/Realme/OnePlus | serie A/C/Nord o equivalente | baja/media/alta | ColorOS derivadas, batería, autoinicio | P1 | Pendiente |
| Transsion | Tecno Spark/Camon, Infinix Hot/Note, itel A/S | entrada/media | memoria, batería agresiva, Android Go cuando aplique | P1 | Pendiente |
| TCL | serie 30/40/50 | entrada/media | variación regional, batería y Settings | P2 | Pendiente |
| Google Pixel | Pixel soportado por catálogo | Android reciente | referencia AOSP y último API | P0 | Laboratorio pendiente |

## Regla de selección

Por publicación importante se elige, como mínimo: una celda virtual por API límite, Pixel de referencia, Samsung de gama media y dos familias OEM priorizadas por riesgo/cambios. La certificación agrega hardware real para todo componente que dependa de servicios del sistema. Un modelo adicional solo entra como excepción permanente después de evidencia confirmada y reproducible.

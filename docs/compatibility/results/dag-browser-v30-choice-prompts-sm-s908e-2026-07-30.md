# DAG 30 - selectores web nativos

Fecha: 2026-07-30

Dispositivo: Samsung SM-S908E

Android: 16

## Problema

Un `<select>` HTML aparecía habilitado y recibía el toque, pero no mostraba sus
opciones. DAG no había registrado el `PromptDelegate` requerido por GeckoView.
Era una incompatibilidad general del navegador y no una regla de GloshIA ni una
excepción del sitio observado.

## Correctivo

- DAG registra un manejador nativo de `ChoicePrompt` por sesión.
- Admite selección simple y múltiple.
- Conserva opciones seleccionadas, deshabilitadas y agrupadas.
- Atrás, cambio de pestaña y salida cancelan el prompt sin aplicar una elección.
- Un prompt de una sesión inactiva se descarta de forma segura.

## Validación

- DAG `versionCode 30`, `versionName 0.20.0-dev`.
- 99 pruebas unitarias.
- Ktlint, Lint y APK DEV correctos.
- SHA-256 del APK:
  `875aa162c9c2aebbe6cf4fd4d8c0499bd77a43760d1e224cca059169c070508b`.
- Instalación in-place correcta en SM-S908E.
- Un formulario HTTPS neutral expuso su selector simple y el usuario confirmó
  físicamente que abrió y funcionó.
- La variante múltiple está implementada y queda pendiente de recorrido físico
  específico.

No se borraron datos ni pestañas. No se hizo push, publicación DEV ni cambios
en Production.

# DAG 31 - historial, nueva pestaña y miniaturas

Fecha: 2026-07-30

Dispositivo: Samsung SM-S908E

Android: 16

## Resultado

- `Historial` quedó disponible desde el menú.
- Conserva localmente hasta 100 páginas HTTPS válidas, deduplicadas por URL.
- Una entrada puede reabrirse y el historial puede borrarse por separado.
- `Borrar datos de navegación` también elimina el historial.
- El botón `+` reemplaza al escudo estático dentro de la barra de dirección.
- El acceso duplicado a nueva pestaña se retiró del menú.
- El organizador mostró una captura real de una página HTTPS neutral.
- Contraseñas, pagos y CAPTCHA visibles siguen impidiendo una captura.
- Formularios sensibles ocultos o fuera del viewport ya no convierten toda la
  página en una tarjeta neutra.
- Las miniaturas permanecen únicamente en memoria y no se guardan en disco.

## Validación

- DAG `versionCode 31`, `versionName 0.21.0-dev`.
- `node --check` correcto.
- 102 pruebas unitarias.
- Ktlint, Lint y APK DEV correctos.
- SHA-256:
  `51fc7b2720473693a0f545d61e28ea9f681a6f72ee7b986fc1c247b2111fdf4f`.
- Instalación in-place correcta.
- DAG conservó el rol de navegador predeterminado.
- Accessibility de App Usuario permaneció activa.
- Las pestañas técnicas y sus entradas de historial se eliminaron al terminar.

No se hizo push, publicación DEV ni cambios en Production.

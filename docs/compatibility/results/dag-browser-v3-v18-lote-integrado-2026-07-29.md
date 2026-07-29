# DAG Browser V3 v18 - lote integrado

Fecha: 2026-07-29

## Alcance

- modelo TinyCLIP afinado y cuantizado con identidad canónica;
- estados visuales seguros para espera, filtro y error;
- rol oficial de navegador Android y recepción de enlaces HTTP/HTTPS;
- activación remota desde Admin por la política Web existente;
- bloqueo best-effort de navegadores alternativos mediante Accesibilidad;
- guía de configuración en Admin y estado visible en Usuario;
- presentación mejorada de solicitudes con identidad visual de la app;
- pestañas, caché y borrado de datos del candidato v17 preservados.

No usa Device Owner, MDM, Knox, restablecimiento, iCloud ni Production. No se
creó una API ni una tabla adicional para DAG.

## Versiones candidatas

| APK | versionCode | versionName |
| --- | ---: | --- |
| Usuario DEV | 292 | 1.0.1-dev |
| Admin DEV | 283 | 1.0.1-dev |
| DAG Browser DEV | 18 | 0.9.0-dev |

Extensión incorporada: `1.16.0`.

## Validación automatizada

- DAG: unitarios, `ktlintCheck`, ensamblado y Lint vital.
- Glosh: `core-domain:test`, `feature-accessibility:test`,
  `app-user:testDevDebugUnitTest` y `app-admin:testDevDebugUnitTest`.
- JavaScript: `node --check` sobre barrera y background.
- JSON/XML: manifiestos y recursos estructuralmente válidos.
- El test de modelo fija nombre, tamaño, umbral y SHA-256 del artefacto.

Artefactos:

| APK | Bytes | SHA-256 |
| --- | ---: | --- |
| Usuario 292 | 4.726.773 | `a2ffb844bce07025bc40eb1f066eac58c3fe43dae00091363d38cef5b332c9d0` |
| Admin 283 | 28.669.340 | `a004f03fcb17a70a1a8b9cf7aae9aa146c4ceef84076184586962f1e02fa88a2` |
| DAG 18 | 121.096.170 | `1c9b63527ec69e3c7c8700fcbee3d6dabaa5b8c1b7a131be1d08d2399954ded1` |

## Gate físico SM-S908E

- Las tres APK se instalaron in-place correctamente; no se borraron datos.
- Glosh conservó habilitado
  `ProtectorAccessibilityService`.
- DAG abrió en frío en 368 ms, restauró pestañas y ejecutó inferencia ONNX sin
  crash.
- Un `ACTION_VIEW` HTTPS explícito fue entregado a la instancia `singleTask`,
  pasó la política segura y mostró la página en 179 ms; quietud de imágenes en
  441 ms.
- El manifiesto instalado registra DAG para `http` y `https`.
- Android mostró correctamente la explicación previa a solicitar el rol de
  navegador.
- El usuario confirmó el rol oficial: `cmd role` devuelve
  `com.contentfilter.dagbrowser.dev`.
- Un enlace HTTPS sin paquete explícito resolvió DAG como predeterminado, abrió
  la actividad en caliente en 89 ms y entregó la URL correcta a `onNewIntent`.

Gate remoto y enforcement:

- Supabase DEV informó licencia efectiva `active`, con vencimiento
  03/08/2026. La primera compilación desde el worktree tenía configuración
  remota vacía porque `.env` es privado y no se hereda entre worktrees; se
  descartó y reconstruyó cargando la configuración DEV únicamente en memoria.
- Admin 283 solicita sincronización inmediata al volver al foreground. El
  teléfono reemplazó el valor local vencido y mostró `Licencia por vencer · 5
  días restantes`.
- `Usar navegador DAG` quedó activo para el usuario físico. El snapshot local
  informó `protectedBrowserRequired=true`.
- Chrome y Brave fueron enviados a Home. Logcat registró
  `action=GoHome reason=dag-browser-required` para ambos.
- DAG siguió abierto y Logcat registró
  `action=Allow reason=protected-browser`.
- La actualización in-place preservó cuentas y datos. Android desactivó
  Accessibility durante el update; la recuperación guiada la restauró y el
  servicio quedó habilitado.
- La franja blanca superior de Usuario/Admin se corrigió haciendo coherente el
  fondo del `Scaffold` con el encabezado bajo la barra de estado.

Pendiente no bloqueante para otra sesión:

1. ampliar el recorrido visual semántico en sitios actuales y conservar sólo
   los errores reales para calibración;
2. repetir pestañas, caché y estados de imagen en otras familias Android;
3. publicar los tres candidatos en DEV únicamente con aprobación separada.

La barrera de Accesibilidad es deliberadamente best-effort en un teléfono
personal. Sin Device Owner Android no permite garantizar bloqueo absoluto ante
toda app futura o manipulación del sistema.

# CHROME-PHOTOS-CONNECTIVITY-INCIDENT-06A

Fecha: 2026-08-24

Estado: **DONE / CONFIRMED_ALLOWLIST_BLOCK**.

## Contexto

Durante el uso físico de DEV 327 en el A23, Chrome mostró “sin conexión” en navegación general. Se ejecutó diagnóstico read-only sobre `work/chrome-photos-full-reset-bootstrap-05` @ `aecdcd35a0736326ef2b27db8ea06114212184b9`, worktree limpio, sin cambios de código/configuración/datos, sin push/PR/merge y sin modificar Device Owner.

## Resultado

La conectividad global del teléfono está sana:

- `ping 1.1.1.1`: PASS 2/2, 0% pérdida.
- `ping google.com`: PASS 2/2, DNS correcto.
- Wi‑Fi validada.
- Content Filter VPN activa/validada.
- App Usuario DEV 327 instalada.
- Device Owner activo y Affiliated.
- Accessibility habilitada y bound al inicio.
- `bootstrapResetCount=1`.
- proceso App Usuario activo y `ChromePhotosDataPlaneLabService` foreground.
- `ProxySettings` aplicado y proxy/CA de sesión sanos.

Prueba controlada:

- `https://httpbingo.org/html`: PASS; `phase=tls_ready host=httpbingo.org`, upstream HTTP/2, status 200.
- `https://example.com/`: FAIL CLOSED intencional; `decision=fail_closed scope=connect_not_allowed`.

Conclusión: la build DEV actual no tiene una caída global de Internet ni un proxy huérfano. El proxy de laboratorio sólo acepta hosts exactos de `ChromePhotosRealWebLabConfig`; cualquier host general fuera de esa allowlist se rechaza por diseño. Esto confirma físicamente P0-1 de `CHROME-PHOTOS-GENERAL-WEB-AUDIT-06`.

## Incidente secundario durante diagnóstico

Una consulta de UI mediante `uiautomator` produjo pérdida momentánea del binding de Accessibility. 05A respondió correctamente:

- `bootstrap=chrome_blocked reason=accessibility_lost`;
- `phase=fail_closed reason=accessibility_lost`;
- estado final `ready=false`, `chromeSuspended=true`;
- Accessibility volvió a aparecer enabled/bound;
- no se ejecutó STOP→START porque el ticket sólo lo autorizaba para clasificación B;
- `bootstrapResetCount` permaneció en 1.

Esto agrega evidencia física de que el fail-close dinámico 05A funciona.

## Decisión

No aplicar un workaround ampliando ciegamente la allowlist. La navegación general requiere resolver de forma coordinada:

1. routing/autoridad dinámica para todo Chrome;
2. semántica HTTP real (métodos, cookies, headers, CORS/CSP, compresión, ranges, streaming);
3. bloqueo de transportes directos para destinos no pre-resueltos;
4. clasificación de imágenes por bytes/decode además de `Content-Type`;
5. brechas de procedencia renderer-side (`data:`, `blob:`, canvas, Service Worker, etc.).

`FULL-RESET-BOOTSTRAP-05/05A` no se reabre: sigue PASS FINAL DEV para su alcance. “Chrome oficial normal filtrado en cualquier web” continúa BLOCKED/NO READY hasta cerrar los gates de generalización.

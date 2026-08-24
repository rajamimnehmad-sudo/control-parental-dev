# CHROME-PHOTOS-CONNECTIVITY-INCIDENT-06A

Fecha: 2026-08-24

Estado: **BLOCKED / DIAGNÓSTICO PRIORITARIO**.

Tras cerrar `FULL-RESET-BOOTSTRAP-05/05A` y comenzar la auditoría general, el usuario reportó físicamente que Chrome muestra “sin conexión” en navegación general.

La fuente compartida confirma una causa de alta probabilidad: el proxy DEV actual aplica una política global de proxy a Chrome, pero su `ChromePhotosRealWebLabConfig` sólo admite los hosts exactos de laboratorio (`httpbingo.org`, `www.gstatic.com`, `github.com`, `raw.githubusercontent.com` y fixture) y `ChromePhotosConnectTarget` rechaza otros destinos. Por lo tanto Google, Wikipedia, noticias, tiendas y otros hosts generales no forman parte del routing permitido y pueden aparecer como sin conexión aunque la conectividad del dispositivo esté sana.

Esto confirma físicamente el hallazgo P0-1 de `CHROME_PHOTOS_GENERAL_WEB_AUDIT_06`: la implementación actual es un laboratorio de hosts acotados, no un proxy de Internet general.

No se autoriza borrar datos de Chrome, repetir el reset, factory reset ni retirar protecciones a ciegas.

Próximo paso inmediato: gate read-only A23 para distinguir con evidencia entre (a) rechazo esperado por allowlist exacta, (b) proxy/policy huérfano, y (c) problema global de VPN/red. Si se confirma (a), priorizar compatibilidad/routing general antes del spike de procedencia. Si se confirma (b), limpiar/rearmar usando el controlador existente sin nuevo reset. Si se confirma (c), detenerse y aislar la regresión de VPN.

`FULL-RESET-BOOTSTRAP-05/05A` conserva PASS FINAL DEV para su alcance; este incidente bloquea la afirmación de navegación web general.
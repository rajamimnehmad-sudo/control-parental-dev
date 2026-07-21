# Samsung SM-A235M — Android 13 — DEV 267

- Fecha: 2026-07-21
- Fuente: `docs/HANDOFF_ACTUAL.md`
- Dispositivo: Samsung SM-A235M (`R58T34V31AE`)
- Android: 13
- Build: App Usuario DEV 267 / App Admin DEV 267
- Nivel atribuible: validación física parcial, no certificación

## Hechos registrados

- Usuario se instaló in-place sobre DEV 266 desde el APK público con SHA-256 verificado. Se conservaron `firstInstallTime` y `ceDataInode`; no se borraron datos.
- Device Admin y el túnel VPN permanecieron activos y la VPN siguió no eludible.
- Android desactivó Accessibility por la restricción propia de una actualización ADB/sideload. Usuario mostró `Accesibilidad apagada`, explicó la causa y condujo a reparar. Después de autorizar el ajuste restringido y habilitar el servicio en la UI nativa, volvió a `Protección activa`.
- Usuario recorrió Inicio, Mis apps, Internet, Ajustes y Ayuda sin crash ni ANR observado. Mis apps refrescó de 161 a 163; la lista quedó vacía transitoriamente sin progreso visual claro.
- Admin recorrió Home, Usuarios, detalle de ambos usuarios, Apps/Web/Seguridad, horarios y grupos sin mutaciones. Se verificó la disposición y navegación descriptas en el handoff.
- Reenlace, recuperación offline, archivo y desinstalación temporal solo se inspeccionaron visualmente; no se generaron códigos/tokens ni se ejecutaron acciones destructivas.

## No probado / no inferible

- batería prolongada, reinicio y notificaciones;
- ciclo offline real y actualización desde versiones distintas de 266;
- desinstalación/recuperación completas;
- otros modelos Samsung u otras familias OEM.

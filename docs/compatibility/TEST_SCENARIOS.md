# Escenarios de compatibilidad

Cada ejecución registra build/commit, app, instalación, API, fabricante, modelo, ABI, locale, tamaño/fuente, resultado y artefactos. Las pruebas automatizadas no usan credenciales ni Supabase DEV.

## Nivel 1 — cada PR

| ID | Escenario | Automatización inicial | Aceptación |
| --- | --- | --- | --- |
| L1-01 | Arranque de actividad Usuario/Admin | Instrumentado | actividad llega a `RESUMED` sin crash |
| L1-02 | Recreación por configuración/proceso | Instrumentado | recrea sin excepción ni estado inválido |
| L1-03 | Rotación portrait/landscape | Instrumentado básico | actividad continúa; si el producto fija orientación se documenta |
| L1-04 | Pantalla pequeña/normal/tablet | Gradle Managed Devices | suite sin crash ni layout fatal |
| L1-05 | `es-AR`, fuente 1.0x/1.3x | Locale de matriz + instrumentado para 1.3x | no hay crash; recortes se revisan con captura |
| L1-06 | Captura técnica | Instrumentado | log contiene API, manufacturer, brand, model, device, ABI y resolución, sin identificadores personales |
| L1-07 | Apps/Web/Seguridad y navegación principal | Manual hasta contar con grafo UI inyectable | destinos abren sin crash y Atrás conserva ruta |
| L1-08 | Offline con fakes | Unitarios existentes + manual | UI usa datos locales/estado offline sin backend real |
| L1-09 | doble tap, cambio rápido Admin, simultaneidad protegida | Unitarios dirigidos existentes + manual | una operación efectiva, estado del usuario seleccionado aislado |

La navegación completa requiere hoy sesión, Room y colaboradores Hilt de producción. No se agrega una arquitectura paralela solo para automatizarla; se mantiene manual hasta poder reemplazar el grafo con fakes pequeños y reutilizables.

## Nivel 2 — publicación importante

- Instalación limpia de ambos APK de prueba.
- Actualización in-place desde la última build certificada, verificando datos y estado cuando el laboratorio lo permita.
- Recorrido de navegación, orientación, background/foreground, modo avión y recuperación de red.
- Revisión de logcat, crashes, ANR, XML y capturas.
- Conservación de estado tras recreación y relanzamiento.
- Firebase virtual más Pixel, Samsung y otras familias físicas disponibles. Un dispositivo no disponible se reemplaza por otra celda equivalente y queda anotado; nunca se marca como ejecutado el original.

## Nivel 3 — certificación

- VPN real y persistencia del túnel.
- Accessibility: activación, reporte de estado, reparación y comportamiento tras actualización.
- Device Admin y protección contra desinstalación.
- optimización de batería, segundo plano, reinicio y funcionamiento prolongado;
- actualización in-place, Ajustes restringidos, desinstalación autorizada/cancelada y recuperación;
- notificaciones con app abierta/cerrada y recuperación de conectividad.

No se debilita una protección para facilitar una prueba. Si ADB, emulador o laboratorio no pueden operar sin desarmarla, el escenario se marca `Bloqueado por entorno` y pasa a hardware controlado.

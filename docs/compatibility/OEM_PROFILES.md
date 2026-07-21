# Diseño futuro de perfiles OEM

No hay lógica OEM implementada. La resolución futura seguirá este orden:

1. capacidades observables del dispositivo;
2. perfil Android genérico;
3. perfil por fabricante/familia;
4. excepción por modelo solo con evidencia confirmada.

## Señales permitidas

- `Build.MANUFACTURER`, `Build.BRAND`, `Build.MODEL`, `Build.DEVICE`;
- Android/API y ABI;
- resolución de intents de Ajustes mediante `PackageManager`;
- estado de optimización de batería;
- disponibilidad/estado de Device Admin y Accessibility;
- permiso, preparación y estado real de VPN.

Los valores `Build.*` sirven para clasificar evidencia y elegir instrucciones, nunca para declarar una capacidad sin comprobarla. Los identificadores se normalizarán en minúsculas y se conservará el valor original solo en diagnóstico local controlado.

## Adaptaciones permitidas

- instrucciones y orden de pasos;
- ruta de Ajustes e intents alternativos resolubles;
- mensajes de reparación;
- tiempos de espera y reintentos prudentes, acotados y observables.

## Límites inviolables

Un perfil nunca puede desactivar protecciones, relajar reglas, declarar sano algo que Android reporta desactivado, autorizar desinstalación, modificar contratos/reglas remotas ni depender únicamente del nombre comercial.

## Hipótesis a validar

| Familia | Hipótesis de prueba, no comportamiento asumido |
| --- | --- |
| Samsung/One UI | Ajustes restringidos tras sideload, batería y rutas de servicios especiales. |
| Motorola | administración de batería y supervivencia de procesos. |
| Xiaomi/HyperOS-MIUI | autoinicio, ahorro de batería y permisos especiales. |
| Honor/MagicOS | ejecución en segundo plano e intents alternativos. |
| Oppo/Realme/OnePlus | variantes de menús ColorOS y administración de batería. |
| Transsion | memoria/batería agresiva, Android Go y variación de Settings. |
| TCL | disponibilidad regional de intents y segundo plano. |
| Pixel | comportamiento Android de referencia y cambios del último API. |

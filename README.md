# Content Filter

Proyecto Android multi-modulo para control parental en canal DEV.

Contexto operativo oficial para continuar trabajo:

```text
docs/HANDOFF_ACTUAL.md
```

No reanalizar arquitectura desde cero salvo pedido explicito.

## Ruta De Trabajo

Usar solo esta carpeta local:

```text
/Users/yejielnehmad/Developer/content-filter
```

No compilar ni revisar la carpeta vieja en `Documents`. Si aparece un problema por duplicados de build/caches, revisar primero que no existan:

```text
.gradle
.gradle-home
app-user/build
```

## Modulos Principales

- `app-user`: app del usuario protegido.
- `app-admin`: app administrador.
- `core-domain`: modelos, repositorios y use cases.
- `core-data`: repositorios Room y outbox.
- `core-database`: Room, DAOs, entidades, migraciones y schemas.
- `core-network`: Supabase REST/Auth/Realtime.
- `core-sync`: SyncEngine, WorkManager, outbox y realtime.
- `core-policy`: motor puro de decisiones.
- `core-security`: activacion y sesion local.
- `core-update`: descarga e instalacion de APKs DEV.
- `core-telemetry`: diagnostico tecnico interno no visible en UI normal.
- `feature-vpn`: bloqueo de dominios/web.
- `feature-accessibility`: bloqueo de apps.

## Reglas De Trabajo

- Trabajar solo con Supabase DEV.
- No tocar produccion.
- No guardar secretos en Git.
- No usar Service Role Key dentro de Android.
- No borrar datos/tablas sin confirmacion explicita.
- Mantener Clean Architecture: UI -> ViewModel -> UseCase -> Repository.
- Priorizar reglas por dispositivo cuando haya duda entre global y device-scoped.
- El usuario prueba Admin y Usuario en el mismo celular Samsung.

## Build Y Tests

Compilar DEV:

```bash
./gradlew :app-admin:compileDevDebugKotlin :app-user:compileDevDebugKotlin
```

Tests relevantes:

```bash
./gradlew :app-admin:testDevDebugUnitTest :feature-vpn:testDebugUnitTest :core-policy:test
```

Publicar APKs DEV:

```bash
scripts/publicar_admin_dev.sh
scripts/publicar_usuario_dev.sh
```

Los scripts compilan la app elegida, corren su test DEV, preparan manifiesto, validan que suba el `versionCode` y publican en Supabase Storage DEV. `scripts/publicar_dev.sh` queda solo para publicar ambas apps juntas.

## Actualizaciones DEV

Manifiestos publicados:

```text
https://syeycayasyufedwoprea.supabase.co/storage/v1/object/public/dev-updates/app-user-dev-manifest.json
https://syeycayasyufedwoprea.supabase.co/storage/v1/object/public/dev-updates/app-admin-dev-manifest.json
```

APKs publicadas:

```text
https://syeycayasyufedwoprea.supabase.co/storage/v1/object/public/dev-updates/app-user-dev-debug.apk
https://syeycayasyufedwoprea.supabase.co/storage/v1/object/public/dev-updates/app-admin-dev-debug.apk
```

Version DEV actual:

```text
App Usuario versionCode 126
App Admin versionCode 147
versionName 1.0.1-dev
```

Actualizar este README cuando cambie:

- ruta oficial de trabajo
- flujo de build/publicacion
- version DEV publicada
- reglas operativas importantes
- modulos principales o comandos de prueba

## Estado Funcional Reciente

- Reglas Admin se aplican por dispositivo.
- Solicitudes y aprobaciones usan `deviceId`.
- Modo web cerrado bloquea dominios via VPN.
- La gestion de Internet es por lista blanca: si Internet esta cerrado, solo pasan dominios permitidos.
- Toggle "Permitir buscadores" controla Google, variantes regionales de Google, Bing, Yahoo y DuckDuckGo cuando modo web esta cerrado.
- Buscadores bloqueados tambien bloquea DNS seguro conocido y fuerza reconexion del VPN ante cambios de reglas de dominio.
- Accessibility saca al usuario de pantallas de buscador detectadas en navegadores cuando buscadores esta bloqueado.
- Admin muestra el estado de buscadores en verde/rojo y mantiene la logica como lista blanca.
- Diagnostico interno por capas para buscadores: VPN/DNS y Accessibility registran motivo tecnico en Logcat.
- Accessibility tambien revisa cambios de contenido de ventana para detectar resultados que cargan sin cambiar de pantalla.
- El filtro interno de Accessibility cubre `TYPE_WINDOW_CONTENT_CHANGED` y los logs indican el tipo de evento procesado.
- Admin no muestra error falso al cambiar buscadores si la escritura local ya fue confirmada y el sync queda diferido.
- Admin bloquea controles de Internet durante guardado para evitar requests cruzadas/doble tap y registra `requestId`.
- Con modo web abierto, buscadores quedan abiertos.
- Las reglas de internet bloquean dominios por VPN/DNS sin cerrar el navegador; Chrome/Samsung Internet solo se cierra por reglas o limites de app.
- Las apps se actualizan manualmente desde la pantalla `Actualizaciones`.
- Bloqueo de apps: Accessibility detecta la app en primer plano y manda `HOME` cuando la politica final es bloquear. Hace pocos reintentos cortos y cancela al cambiar de app para evitar cierres falsos o trabas en Inicio. Android no garantiza latencia identica en todos los eventos, por eso el cierre puede ser instantaneo o tardar 1-2 segundos.
- Limites de tiempo de apps: si una app permitida agota su limite diario, queda bloqueada por tiempo. El usuario puede pedir `Pedir tiempo` o `Acceso completo`.
- `Pedir tiempo`: Admin concede minutos extra, por defecto 15 o el valor manual indicado. El extra vence y luego vuelve a aplicar el limite diario.
- `Acceso completo`: Admin aprueba acceso completo, se desactivan bloqueos directos de esa app, se crea una regla Allow y se elimina el limite diario de esa app. La app queda sin limite hasta que Admin vuelva a configurarlo.
- Comunidad DEV activa: `Comunidad Primero Año`.
- Los codigos numericos legacy 1-100 quedaron reemplazados por tokens aleatorios.
- App Admin queda activada en el celular por token de administrador; no debe pedir login repetido.
- App Usuario se activa solo con token generado desde Admin.
- Solicitudes Admin se revisan por usuario y cada solicitud muestra icono de app con acciones directas.
- DEV 108 limpia UI tecnica: el Panel Admin ya no muestra botones de reset/borrado/diagnostico DEV y la pantalla Actualizaciones del Usuario ya no muestra herramientas DEV.
- El paquete Admin viejo `devices` fue eliminado; `Mis dispositivos` usa la pantalla unificada de reglas/apps.
- Se elimino el modulo vacio `core-license`; el estado de activacion/licencia funcional queda en `core-domain`, `core-security` y repositorios existentes.
- El Panel Admin muestra `Responsable` en lugar de `Guia`.
- En solicitudes, `Rechazar` queda visible como boton de ancho completo.
- El tiempo extra concedido se refleja en listas de Admin y Usuario con minutos restantes.
- Grupos de apps: Admin puede armar un grupo por usuario/dispositivo o aplicarlo a varios usuarios destino con limite diario compartido; Usuario ve barra de progreso del grupo. La ventana diaria reinicia a las 12 PM.
- Supabase DEV incluye `app_groups`, `app_group_apps` y base para grupos de usuarios.
- App Admin DEV 122 agrega primera capa visual moderna: Home con hero, tarjetas grandes, menu inferior mas visual y accesos a Comunidad/Ajustes.
- App Admin DEV 124 ordena la base visual con iconos internos reutilizables, navegacion inferior con iconos reales, flechas reales y header integrado en Usuarios protegidos.
- App Admin DEV 125 unifica headers internos al estilo Comunidad, con flecha atras integrada y titulos sin duplicar.
- App Admin DEV 126 reemplaza iconos dibujados a mano por Material Icons Core liviano y corrige la card de Usuarios protegidos.
- App Admin DEV 127 agrega padding de barra de estado en headers para que no se tapen con hora/senal/bateria.
- App Admin DEV 128 rediseña detalle de usuario con nombre como header, flecha atras integrada y tabs acordes al resto.
- App Admin DEV 129 corrige Usuarios protegidos para que el contenido no quede bajo el header antes de actualizar y muestra el token de usuario dentro de la misma ventana.
- App Admin DEV 130 mejora Aplicaciones con filtros rapidos, busqueda compacta y orden confirmado al bloquear.
- App Admin DEV 131 deja fijo el header del usuario en Aplicaciones, mueve el chip de estado al lado del nombre, enfoca automaticamente la busqueda y quita la seleccion gris de filtros.

## Verificacion Rapida Antes De Trabajar

```bash
pwd
git status --short
find . -maxdepth 2 \( -path './.gradle' -o -path './.gradle-home' -o -path './app-user/build' \) -print
```

Si `git status --short` muestra cambios no esperados, no revertirlos sin confirmacion.

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
scripts/publicar_dev.sh
```

El script compila, corre tests, prepara manifiestos, valida que suba el `versionCode` y publica en Supabase Storage.

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
versionCode 75
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
- Con modo web abierto, buscadores quedan abiertos.
- Las reglas de internet bloquean dominios por VPN/DNS sin cerrar el navegador; Chrome/Samsung Internet solo se cierra por reglas o limites de app.
- Las apps se actualizan manualmente desde la pantalla `Actualizaciones`.

## Verificacion Rapida Antes De Trabajar

```bash
pwd
git status --short
find . -maxdepth 2 \( -path './.gradle' -o -path './.gradle-home' -o -path './app-user/build' \) -print
```

Si `git status --short` muestra cambios no esperados, no revertirlos sin confirmacion.

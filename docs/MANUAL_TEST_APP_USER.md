# Manual Test - App Usuario MVP

Este checklist valida el APK `app-user` en un dispositivo Android real. No cubre App Admin ni funciones futuras.

## APK a probar

Ruta del APK generado:

```text
app-user/build/outputs/apk/dev/debug/app-user-dev-debug.apk
```

Ruta absoluta:

```text
/Users/yejielnehmad/Documents/Codex/2026-06-29/files-mentioned-by-the-user-arquitectura/app-user/build/outputs/apk/dev/debug/app-user-dev-debug.apk
```

## Preparacion del telefono

1. Activar Opciones de desarrollador.
2. Activar Depuracion USB.
3. Conectar el telefono por USB.
4. Aceptar el dialogo de confianza RSA si Android lo muestra.
5. Confirmar que el dispositivo aparece:

```bash
adb devices
```

## Instalacion del APK

Instalar o reemplazar la version existente:

```bash
adb install -r app-user/build/outputs/apk/dev/debug/app-user-dev-debug.apk
```

Si aparece un error por downgrade o datos incompatibles durante pruebas tempranas, desinstalar la app y reinstalar:

```bash
adb uninstall com.contentfilter.user.dev
adb install app-user/build/outputs/apk/dev/debug/app-user-dev-debug.apk
```

Resultado esperado:

- La instalacion termina con `Success`.
- La app aparece en el launcher como app de prueba/dev.
- La app abre sin crash inmediato.

## Checklist funcional

### 1. Apertura inicial

- Abrir la app desde el launcher.
- Verificar que se muestra la pantalla inicial/onboarding.
- Confirmar que no hay pantalla en blanco permanente.
- Rotar el telefono una vez si se desea validar estabilidad basica.

Resultado esperado:

- La app inicia correctamente.
- No se cierra sola.
- No pide permisos no relacionados antes de que el usuario los solicite.

### 2. Navegacion entre pantallas

- Ir a la pantalla de activacion.
- Volver o navegar a estado.
- Ir a solicitudes.
- Ir a uso diario.
- Abrir la pantalla de bloqueo basica si esta disponible desde la navegacion.

Resultado esperado:

- Todas las pantallas cargan.
- La navegacion no duplica pantallas de forma evidente.
- El boton atras de Android no rompe el flujo.

### 3. Activacion en modo offline/desarrollo

- Probar la pantalla de activacion sin configurar Supabase.
- Ingresar datos de prueba:
  - email: `test@example.com`
  - password: cualquier valor de prueba
  - codigo: `DEV-CODE`
  - nombre del dispositivo: `S22 Ultra`
- Enviar la activacion.

Resultado esperado:

- La app no crashea si Supabase no esta configurado.
- Debe quedar claro que esta en modo offline/desarrollo o que no pudo sincronizar.
- Los datos locales deben seguir funcionando.

### 4. Pantalla de estado

- Abrir Estado.
- Revisar:
  - VPN activa/inactiva.
  - Accesibilidad activa/inactiva.
  - Sincronizacion.
  - Activacion.
  - Version.

Resultado esperado:

- La pantalla carga sin error.
- Los estados reflejan permisos reales cuando sea posible.
- Si Supabase no esta configurado, sincronizacion debe mostrarse como offline/desarrollo o no disponible, sin crash.

### 5. Permiso de VPN

- Desde la app, iniciar el flujo de VPN si hay boton disponible.
- Android debe mostrar el dialogo oficial de permiso VPN.
- Aceptar el permiso.

Resultado esperado:

- El permiso se solicita mediante UI oficial de Android.
- No se usan permisos privados ni pantallas invasivas.
- Si el servicio no queda activo por una limitacion del MVP, la app debe reportarlo sin cerrarse.

### 6. Encendido y apagado de VPN

- Encender VPN desde la app.
- Verificar que Android muestra el indicador de VPN si el servicio queda activo.
- Apagar VPN desde la app o desde el sistema.
- Repetir una vez.

Resultado esperado:

- No hay crash al iniciar/detener.
- No queda un indicador de VPN activo despues de apagar.
- El estado de la app se actualiza razonablemente.

### 7. Permiso de Accesibilidad

- Abrir Ajustes de Accesibilidad desde la app si hay acceso disponible, o manualmente desde Ajustes de Android.
- Buscar el servicio de la app.
- Activarlo.
- Volver a la app.

Resultado esperado:

- Android muestra la pantalla oficial de autorizacion.
- La app no intenta activar el permiso automaticamente.
- El estado de Accesibilidad cambia a activo cuando Android lo habilita.

### 8. Deteccion basica de app activa

- Con Accesibilidad habilitada, abrir 2 o 3 apps comunes, por ejemplo:
  - Chrome.
  - YouTube.
  - Ajustes.
- Permanecer algunos segundos en cada una.
- Volver a la app de prueba.

Resultado esperado:

- La app no crashea mientras se cambian apps.
- El servicio no muestra comportamiento agresivo.
- La deteccion se limita al paquete/app activa, sin leer contenido personal.

### 9. Registro de uso diario

- Abrir una app durante 30 a 60 segundos.
- Cambiar a otra app.
- Volver a la app de prueba.
- Abrir la pantalla de uso diario.

Resultado esperado:

- Se muestra uso acumulado por app cuando exista informacion suficiente.
- El contador puede no ser exacto al segundo, pero debe crecer de forma razonable.
- Reiniciar la app no debe borrar sesiones ya persistidas.

### 10. Creacion de solicitud offline

- Abrir Solicitudes.
- Crear una solicitud de acceso o tiempo adicional.
- Usar un texto simple de prueba.
- Confirmar la solicitud.

Resultado esperado:

- La solicitud se crea localmente.
- No requiere Supabase para existir en modo offline.
- Si no hay conexion/configuracion remota, queda pendiente sin crash.

### 11. Verificacion general de estabilidad

- Abrir y cerrar la app varias veces.
- Cambiar entre Wi-Fi/datos si se desea.
- Activar y desactivar VPN/Accesibilidad una vez.
- Navegar entre pantallas despues de cada cambio.

Resultado esperado:

- No hay cierres inesperados.
- No hay pantalla negra persistente.
- No hay consumo visible anormal durante una prueba corta.

## Placeholders conocidos

- App Admin no esta implementada todavia.
- No existe tienda permitida/lista permitida final.
- El diseno visual es MVP, no version final.
- Supabase puede no estar configurado en esta prueba.
- Pagos y Super Admin siguen pendientes. Comunidades ya tienen base DEV.
- La pantalla de bloqueo es basica.
- El flujo automatico completo desde Accessibility hacia bloqueo puede estar parcial.
- SafeSearch queda preparado como extension, sin implementacion final.

## Recoleccion de logs

Limpiar logs antes de probar:

```bash
adb logcat -c
```

Recolectar logs mientras se reproduce el problema:

```bash
adb logcat
```

Guardar logs en archivo:

```bash
adb logcat -d > app-user-manual-test.log
```

Filtrar errores basicos:

```bash
adb logcat -d AndroidRuntime:E ContentFilter:E '*:S'
```

Recolectar informacion del APK instalado:

```bash
adb shell dumpsys package com.contentfilter.user.dev
```

Verificar si el proceso esta vivo:

```bash
adb shell pidof com.contentfilter.user.dev
```

## Criterio de aprobacion manual

La prueba manual queda aprobada si:

- El APK instala correctamente.
- La app abre sin crash.
- La navegacion principal funciona.
- Modo offline/desarrollo no rompe la app.
- Estado, solicitudes y uso diario cargan.
- VPN y Accesibilidad usan permisos oficiales.
- La app sigue estable despues de activar/desactivar permisos.

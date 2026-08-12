# Servidor local Wi-Fi de APK DAG

## Objetivo

Entregar el APK DEV local de DAG al telefono sin ADB y sin publicar cada
candidato en Supabase. Es una herramienta de desarrollo de la Mac; no forma
parte de Android, GloshIA, Gradle ni Production.

## Contrato

- `launchd` lo inicia al ingresar a la Mac y lo reinicia si termina.
- Escucha en el puerto `8787`, separado del fixture DAG del puerto `8765`.
- Sirve directamente el ultimo `DagBrowser-dev-debug.apk` construido en
  `main`; no crea otra copia.
- La ruta contiene un token aleatorio persistente con permisos `0600`.
- No existe listado de carpetas ni acceso a otros archivos del repositorio.
- El APK usa `Cache-Control: no-store` y la pagina agrega una clave por
  modificación/tamaño para evitar una descarga anterior en cache.
- Admite `HEAD` y un unico rango HTTP para reanudar descargas.
- Los logs omiten la ruta secreta.

La red local no reemplaza la firma del APK. Android sigue mostrando su
confirmacion normal de instalacion; una pagina web no puede instalar en silencio.

## Uso

Instalar o actualizar el servicio de esta Mac:

```bash
python3 scripts/dag_diagnostics/dag_apk_wifi_server.py install
```

Mostrar las direcciones privadas para el telefono:

```bash
python3 scripts/dag_diagnostics/dag_apk_wifi_server.py urls
```

La direccion `.local` permanece estable aunque cambie la IP. Si Android no
resuelve mDNS, usar la direccion IPv4 que muestra el mismo comando. Ambos
equipos deben estar en la misma LAN y la Mac debe permanecer encendida y
despierta.

Detener y retirar el inicio automatico:

```bash
python3 scripts/dag_diagnostics/dag_apk_wifi_server.py uninstall
```

El retiro conserva el token privado para que una reinstalacion mantenga el
mismo favorito del telefono.

## Archivos locales fuera de Git

- token: `~/.config/glosh/dag-apk-wifi-token`;
- agente: `~/Library/LaunchAgents/com.glosh.dag-apk-wifi.plist`;
- logs: `~/Library/Logs/DagApkWifiServer*.log`.

No contiene credenciales de Supabase ni expone el APK fuera de la red a traves
de un servicio remoto. La accesibilidad desde otra subred, red de invitados o
datos moviles depende del router y no está garantizada.

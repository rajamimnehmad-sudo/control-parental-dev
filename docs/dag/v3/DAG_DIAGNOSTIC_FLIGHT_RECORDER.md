# DAG Diagnostic Flight Recorder

Fecha: 2026-08-11. Alcance: DAG Browser DEV/Diagnostic y Supabase DEV
`syeycayasyufedwoprea`. Production queda fuera.

## Objetivo

Conservar evidencia tecnica suficiente para diagnosticar imagenes ausentes,
bloqueos, expiraciones y transiciones de pagina despues de que ocurren, sin
mantener ADB conectado y sin convertir la telemetria en parte del camino critico
de GeckoView o GloshIA.

## Flujo

1. DAG agrega estructuras pequenas y tipadas a una cola de memoria de hasta 512
   entradas; el hilo visual no calcula hashes ni serializa JSON.
2. Un executor propio de prioridad minima calcula los tokens, serializa y
   escribe lotes cada dos segundos o al alcanzar 32 eventos.
3. Dos archivos privados de 512 KiB forman el anillo persistente. Al llenarse,
   el mas antiguo se reemplaza.
4. La pantalla `Mas opciones > Diagnostico DAG` arma como maximo 4.096 eventos.
5. Solo al tocar `Enviar diagnostico`, el informe se comprime con gzip y se
   envia por HTTPS a una Edge Function de DEV.
6. La funcion valida tamaño y esquema, rechaza campos libres, aplica un limite
   global y guarda JSON privado por 14 dias.
7. Codex lista o recupera informes con
   `python3 scripts/dag_diagnostics/fetch_reports.py [--code DAG-XXXXXXXX]`.

No se ejecuta ninguna solicitud remota por evento o por imagen. El envio no
borra la copia local.

## Datos admitidos

- tipo y secuencia del evento;
- relojes de pared y monotono;
- identificador numerico local de pestaña no privada;
- token de candidata salado por sesion;
- version de esquema y `versionCode` en cada evento nuevo, para separar con
  certeza eventos conservados antes y despues de una actualizacion;
- carrier `network`/`inline`, prioridad, accion y razon cerrada;
- bytes, dimensiones, score del modelo y tiempos acotados;
- pagina y recurso reducidos a `https://host/ruta?nombre_de_clave`, sin valores
  de query, fragmentos ni `userinfo`, mas tokens opacos que permiten
  correlacionar el mismo recurso entre red, GloshIA y DOM;
- `requestId` convertido en token, tipo de recurso, frame, MIME, estado HTTP,
  procedencia de cache y presion acotada de streams/cola/memoria capturada;
- estado terminal de cada `img` observado (`shown`/`hidden`) y sus dimensiones
  naturales/renderizadas, sin selector, HTML ni contenido de la imagen;
- version de APK, SDK, fabricante y modelo de telefono.

## Datos prohibidos

- fotos, miniaturas, pixeles, Base64 o reemplazos visuales;
- valores de busqueda/query, fragmentos, `userinfo` o parametros secretos;
- texto, selectores, clases, ID, HTML o formularios de la pagina;
- cookies, headers, tokens, correos, nombres o credenciales;
- cualquier evento de una pestaña incognito.

Android usa almacenamiento interno con backup deshabilitado. El encoder local
es tipado, limita direcciones a 768 caracteres y sanitiza antes de escribir. La
Edge Function repite la validacion, acepta solamente HTTP(S), exige consultas
sin valores y rechaza campos desconocidos.

## Seguridad remota

- RLS activa y cero permisos directos para `anon` y `authenticated`;
- Service Role Key solo existe dentro de la Edge Function;
- el APK contiene una credencial DEV limitada exclusivamente a subir informes;
- la credencial de lectura existe solo en el entorno local ignorado por Git;
- Supabase conserva unicamente hashes SHA-256 de ambas credenciales;
- acceso anonimo y token incorrecto devuelven 401;
- retencion automatica: 14 dias mediante `pg_cron`;
- limite actual: 120 informes por hora en todo DEV y 768 KiB comprimidos por
  informe.

La credencial de subida de un APK puede extraerse mediante ingenieria inversa;
por eso su autoridad se limita a insertar payloads estrictamente validados en
DEV. Antes de Production debe reemplazarse por identidad real de dispositivo o
attestation y rotarse la credencial DEV.

## Uso del propietario

1. Usar DAG normalmente.
2. Despues de observar un problema, abrir `Mas opciones > Diagnostico DAG`.
3. Tocar `Enviar diagnostico`.
4. Informar a Codex solamente que ya se envio. El codigo `DAG-XXXXXXXX` sirve
   para distinguir telefonos o multiples pruebas, pero no hace falta copiar logs.

`Borrar diagnostico` elimina solo el anillo local. No borra pestañas, historial,
favoritos, cache ni datos de navegacion. Desinstalar la app o borrar todos sus
datos Android tambien elimina el anillo.

## Limites conocidos

El recorder explica el pipeline controlado por DAG. ADB/Perfetto sigue siendo
necesario para un crash o ANR nativo, fallos internos de Gecko fuera del puente,
perfil de CPU/GPU o jank a nivel sistema. Un informe remoto no sustituye esas
herramientas cuando el proceso muere antes de poder enviar.

## Correlacion disponible desde el esquema 2

Para una imagen de red, `request`, `resource` y `page` permiten reconstruir la
secuencia sin guardar secretos:

1. `media_resource`: respuesta HTTP/MIME/cache recibida por Gecko;
2. `media_decision`: bytes entregados al pipeline y resultado terminal de
   GloshIA, con tiempos y presion de trabajo;
3. `media_element`: estado final y tamaño del elemento visible;
4. `media_drop`: motivo exacto y contexto si el recurso no alcanzo una decision.

Los eventos anteriores a DAG 207 siguen siendo legibles, pero se reconocen como
legacy porque no incluyen `event_schema` ni `app_version_code`. Desde DAG 207
cada evento nuevo queda identificado aunque el anillo sobreviva una actualizacion.

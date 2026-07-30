# Matriz conjunta Usuario 304, Admin 287 y DAG 29

Fecha: 2026-07-30

Dispositivo: Samsung SM-S908E

Android: 16

## Builds

- App Usuario: `versionCode 304`, `1.0.1-dev`
- App Admin: `versionCode 287`, `1.0.1-dev`
- DAG: `versionCode 29`, `0.19.0-dev`

La instalación existente se conservó. No se borraron datos, permisos,
configuración, descargas ni pestañas.

## App Usuario

- Ajustes mostró secciones independientes para Protección y activación,
  Actualizaciones e instalaciones, Ayuda y Tu opinión.
- Ayuda respondió offline a `hola` y usó el estado propio: indicó que la
  protección principal estaba activa.
- `Borrar chat` eliminó los turnos locales.
- Ante una clave sintética, el valor apareció una sola vez en el mensaje del
  usuario y no fue repetido por GloshIA. La respuesta se limitó al proyecto.
- La consulta `mi licencia está activa` no respondió el estado concreto del
  teléfono: explicó genéricamente activación, token y sincronización. Es una
  limitación de contexto/calidad, no una fuga ni un crash.

## App Admin

- Ajustes mostró Cuenta y comunidad, Contacto adulto, Panel administrador,
  Actualizaciones, Ayuda, Tu opinión y Administrador de este teléfono.
- Ayuda funcionó offline e identificó un resumen agregado de dos usuarios.
- No mostró nombres individuales. Una consulta fuera de las intenciones
  disponibles se clasificó erróneamente como ayuda de bloqueo de apps. Es una
  respuesta segura pero poco pertinente.
- `Cambiar administrador` permaneció dentro de la sección sensible y mostró
  confirmación antes de borrar el admin local. La operación fue cancelada.
- No se cambiaron reglas, usuarios, permisos ni autoridades.

## DAG

- El organizador mostró 20 pestañas, cuadrícula desplazable, miniaturas o
  reemplazos neutros, cierre individual, nueva pestaña y `Cerrar todo`.
- La prueba abrió dos pestañas técnicas y las cerró; el teléfono volvió a las
  20 pestañas previas.
- La matriz de imágenes de DAG 29 ya había validado Frávega, Mimo, Cheeky y
  Google Imágenes en el mismo dispositivo.
- La pantalla Descargas abrió correctamente y confirmó que no existían
  documentos descargados.

## Descarga PDF no aprobada

Se probaron dos recorridos HTTPS:

1. Un PDF inline de W3C abrió el visor PDF.js interno. La extensión no pudo
   confirmar allí la barrera visual y DAG cerró la página de forma segura.
2. La página de prueba de `web-scraping.dev` envió una descarga PDF mediante
   formulario `POST` con `target=_blank` y `Content-Disposition: attachment`.
   DAG interceptó la nueva ventana y la reabrió con `loadUri`, perdiendo el
   método `POST`. El servidor recibió `GET` y respondió `Method Not Allowed`.

No se descargó ni dejó un archivo parcial. La conducta fue fail-closed, pero la
aceptación física de `DAG-DOWNLOADS-01` falló.

## Estado final

- Modo avión restaurado a apagado.
- Wi-Fi y datos móviles restaurados.
- DAG continúa como navegador predeterminado.
- Accessibility de App Usuario continúa activa.
- Sin crash ni ANR observados durante los recorridos.
- No hubo build, incremento de versión, instalación nueva, push ni publicación.


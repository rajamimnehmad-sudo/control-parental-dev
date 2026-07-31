# DAG Browser 45 — optimización profesional en SM-A235M

## Alcance

- Versión Android: `versionCode 45`, `versionName 0.27.0-dev`.
- Extensión interna: `1.27.0`.
- Dispositivo: Samsung SM-A235M, Android 14.
- APK: `DagBrowser-dev-debug.apk`, 121.319.919 bytes.
- SHA-256: `4826daecc0020342d7222ea4e9362e59fe87e70e283ce14164160d836dd363b3`.
- Instalación in-place desde `main` local; DAG continuó como navegador
  predeterminado. No se hizo push ni publicación remota.

## Correcciones generales

- Cada documento y pestaña conserva su propia generación, cola, contadores y
  decisiones. Una navegación vieja no puede presentar una decisión ni cerrar
  las métricas de la página activa.
- La señal del viewport inicial ya no espera que un sitio dinámico termine toda
  su actividad de red. `DOMContentLoaded` abre una ventana acotada de 750 ms y
  el resto de los medios continúa protegido y procesado progresivamente.
- Sólo la pestaña activa y las dos inactivas más recientes conservan sesión
  Gecko. Las anteriores se hibernan y se revalidan al volver; sus miniaturas
  seguras viven solamente en memoria.
- El scroll dejó de recorrer todo el DOM para fondos y el detector de anuncios
  limita sus búsquedas a contenedores conocidos. Las listas de Historial,
  Descargas y pestañas actualizan únicamente las filas modificadas.
- Los buffers de entrada del modelo se reutilizan por hilo y se sobrescriben
  después de la inferencia.
- Una ventana nueva conserva la solicitud original de Gecko, incluido su método
  y cuerpo. DAG ya no reemplaza una navegación `POST` válida por un `GET`.
- Un PDF inline permanece visualmente cerrado, se copia mediante el guardador
  de PDF de Gecko a un archivo privado parcial, se verifica por MIME, tamaño,
  cabecera y cierre, y sólo después ofrece confirmación y descarga.
- Se retiraron recursos sin uso, un peso anidado del toolbar y varias
  actualizaciones completas de listas. Lint bajó de 57 a 31 advertencias; las
  restantes son principalmente recomendaciones de dependencias, KTX y
  atributos de compatibilidad, sin errores.

## Validación automática

- `node --check` correcto para `background.js` y `barrier.js`.
- 120 pruebas unitarias, 0 fallos, 0 errores y 0 omitidas.
- `ktlintCheck`, `lintDevDebug` y `assembleDevDebug`: correctos.
- `git diff --check`: correcto.

## Matriz física

Las celdas de tiempo siguen el orden `página / fotos del viewport / visible`.
La caché Gecko se borró desde el menú propio de DAG antes de la matriz.

| Sitio | Resultado | Observación |
| --- | --- | --- |
| Frávega | No completó / No completó / 1.097 ms | El origen devolvió su propia pantalla “La página no existe”; no se registra como éxito de contenido. |
| Mimo | 4.511 / 5.543 / 625 ms | Portada operativa; imágenes permitidas visibles y hero filtrado. |
| Cheeky | No emitió `page_analysis_ready` / 8.414 / 2.149 ms | La página quedó operativa y el viewport cerró aunque el sitio siguió cargando; confirma el arreglo de ciclo de vida. |

Farmacity, usada como control adicional durante el diagnóstico, registró
`5.802 / 6.817 / 990 ms`. No hubo crash ni ANR.

## Pestañas y memoria

- Se recorrieron diez pestañas en el selector de pantalla completa.
- Las pestañas recientes mostraron miniatura real filtrada; páginas sensibles,
  antiguas o hibernadas usaron tarjeta neutra.
- Una pestaña Cheeky hibernada se restauró y revalidó sin crash: visible en
  1.645 ms, análisis en 6.042 ms y viewport en 6.429 ms.
- Después de forzar cierre y restaurar diez entradas se cargó solamente la
  activa. El proceso registró aproximadamente 319.465 KiB PSS, 455.544 KiB RSS
  y 365 KiB swap. La matriz física de 50 pestañas continúa pendiente.

## PDF y solicitudes de nueva ventana

- El PDF público de Mozilla quedó oculto con el estado “PDF listo para guardar”.
- DAG mostró nombre, origen, MIME y tamaño real de 1,7 MB; la descarga terminó y
  el archivo de prueba se borró luego desde `Descargas`.
- La preservación de `POST` está cubierta por contrato automatizado y por el
  uso de la sesión nueva entregada a Gecko. Falta una reproducción física con
  un formulario real y controlado que abra su resultado en otra pestaña.

## Límites declarados

- El APK sigue pesando unos 121 MB. La mayor parte corresponde a GeckoView,
  ONNX Runtime y el modelo local; quitarlos o reducirlos sin una migración
  medida comprometería compatibilidad o filtrado.
- No se afirma validación física de 50 pestañas ni de un `POST` real.
- El error propio de Frávega debe repetirse cuando el sitio vuelva a entregar
  una portada normal.

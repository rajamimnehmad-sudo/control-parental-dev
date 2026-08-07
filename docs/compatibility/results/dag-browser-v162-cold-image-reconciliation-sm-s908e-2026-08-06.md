# DAG Browser 162 - primera carga y reconciliacion de imagenes

Fecha: 2026-08-06

Dispositivo: Samsung SM-S908E, Android 16, arm64-v8a

## Causa raiz

La falla aparecia despues de `model_allow`: Fravega podia mostrar placeholders
aunque el pipeline registrara mas de cien permisos y ningun filtro visual.

La instrumentacion temporal conto el DOM sin registrar URLs ni contenido. A
los 5 segundos habia 753 elementos `img`, 688 lazy, 53 completos, 45
decodificados y solo 6 marcados estables por DAG. A los 10 segundos habia 124
completos, 116 decodificados y 116 estables. Las fotos ya aprobadas permanecian
ocultas esperando eventos tardios del sitio.

Ademas, desde DAG 158 cualquier cambio de `versionCode` ejecutaba
`ALL_CACHES`, obligando a reconstruir toda la carga de imagenes despues de cada
APK aunque el contrato visual no hubiera cambiado.

## Correccion

- La limpieza de cache usa `InterceptedMediaCacheRevision`, independiente de
  `versionCode`. El valor legado se migra sin borrar datos.
- La barrera realiza cinco reconciliaciones a 100, 400, 1000, 2000 y 4000 ms.
  Cada una solo revisa imagenes completas sin marca estable y la serie termina
  automaticamente.
- El PNG neutral bloqueado de 1x1 no se marca como imagen renderizable.
- Toda la instrumentacion de diagnostico fue retirada.

No cambiaron GloshIA Visual R3.1, modelo, umbral, politica, preprocesamiento,
workers Android, ONNX, scheduler, publicidad ni video. No se agregaron
excepciones por sitio.

## Validacion

- `testDagProtectionJs`: 21/21.
- `testDevDebugUnitTest`: correcto.
- `ktlintCheck`: correcto.
- `assembleDevDebug`: correcto.
- Instalacion in-place: correcta, datos preservados.
- Fravega con cache fria: categorias completas en las capturas temprana y
  final; `123 allow`, un `invalid_payload`, pagina visible en 4.411 ms y cola
  visual quieta en 9.761 ms.
- Mimo: menu completo despues de desplazar la pagina.
- Google: sin patrocinados en capturas a 2 y 5 segundos; mapa e imagenes
  terminaron visibles.
- Sin crash, ANR ni OOM observados.

## Memoria observada

Con siete pestañas persistidas, el proceso reporto 376.717 KiB PSS. La politica
actual conserva como maximo tres sesiones Gecko abiertas, aunque Gecko mantuvo
seis procesos de contenido. Optimizar ese limite requiere un A/B separado para
no introducir recargas o regresiones de fluidez en este lote.

No se hizo push, publicacion remota ni cambio en Supabase o Production.

## Seguimiento DAG 163

Google Imagenes decodificaba fotos reales despues del ultimo barrido de DAG
162. El diagnostico conto 108 imagenes decodificadas y solo 58 estables a 10
segundos; las 50 restantes no eran bloqueos de R3.1. DAG 163 (`0.69.67-dev`,
extension `1.81.0`) agrega reconciliaciones acotadas a 6, 8 y 12 segundos.

En la repeticion final las miniaturas ya estaban presentes a 5 segundos y se
mantuvieron a 7 y 12 segundos. La pagina fue visible en 1.049 ms y la cola
visible quedo quieta en 1.346 ms. La instrumentacion temporal fue retirada y
no cambiaron modelo, umbral, politica, publicidad, video, scheduler o CPU.

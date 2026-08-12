# HANDOFF ACTUAL - Glosh y DAG Browser

Fecha de corte: 2026-08-11

Este archivo contiene solo el estado tecnico vigente. El historial vive en
`docs/BACKLOG_PRODUCTO.md`, `docs/compatibility/results/` y Git. No reconstruir
el runtime actual desde versiones o worktrees historicos.

## LIC-REVOCATION-01A: propagacion confiable de licencia

- Backend aplicado en Supabase DEV y candidato Android Usuario 311/Admin 293:
  un cambio relevante de licencia emite
  una invalidacion Realtime dirigida a cada dispositivo de la comunidad. El
  mensaje no contiene estado, fechas ni permisos; obliga a consultar nuevamente
  `get_device_license_entitlement` con el token propio del dispositivo.
- Supabase procesa cada minuto vencimientos y comienzos programados usando hora
  del servidor. Android conserva el ultimo estado verificado y ya no permite
  que adelantar o atrasar el reloj local active o venza una licencia.
- Unitarios de dominio, datos, red y sync correctos; Lint Vital y APK DEV locales
  de ambas apps correctos. La migracion
  `20260811004228_broadcast_license_invalidation.sql` quedo aplicada en DEV y el
  dry-run final esta limpio. No hubo instalacion, push ni publicacion.
- Verificacion DEV: licencia vigente sin cambios; cuatro dispositivos recibieron
  una invalidacion de prueba; trigger y cron activos; ciclos programados
  exitosos; helpers sin ejecucion de clientes y sin nuevos hallazgos asociados
  en los asesores de seguridad o rendimiento.
- Pendiente: recorrido fisico de cambio manual, vencimiento, renovacion y modo
  offline. La liberacion de permisos y el flujo de desinstalacion pertenecen a
  `LIC-REVOCATION-01B`.

## LIC-RELINK-02: cierre seguro del reenlace

- El reenlace de Usuario o Admin conserva el mismo dispositivo logico y ahora
  exige una licencia vigente sin volver a consumir un cupo ya ocupado.
- Cada dispositivo admite una sola sesion de reenlace abierta. Emitir o consumir
  un token nuevo revoca la sesion provisional anterior y su activacion asociada;
  los tokens vencidos, revocados o reemplazados fallan cerrados.
- Android ahora lee el booleano real de `complete_own_device_relink`: solo
  `true` limpia el estado pendiente; un HTTP correcto con `false` ya no se toma
  como reenlace completado.
- Unitarios de `core-network` y `core-sync`, Lint Vital y APK DEV locales de
  Usuario 310 y Admin 292 correctos. El Ktlint global de `core-network` sigue
  bloqueado por formato previo en `SupabaseAppFeedbackRepository.kt`, fuera del
  ticket; los archivos modificados no aparecen entre las infracciones.
- La migracion `20260810174945_fix_device_relink_completion.sql` quedo aplicada
  en Supabase DEV. El historial local/remoto coincide y el dry-run final informa
  que la base remota esta actualizada. No hubo instalacion, push ni publicacion
  de APK.
- La comprobacion remota confirma `revoked_at`, el indice unico parcial, cero
  sesiones abiertas y los nueve RPC/helpers esperados con `search_path` vacio.
  Los helpers internos no son ejecutables por clientes y la tabla de sesiones
  no concede acceso directo a `anon` ni `authenticated`. Queda pendiente el
  recorrido fisico integral de reenlace en Usuario y Admin.
- `LIC-SUPABASE-HISTORY-01` quedo cerrado en DEV: se alinearon localmente 24
  timestamps, se recuperaron dos migraciones Gloshia que existian solo en el
  historial remoto y se registraron como `applied`, sin ejecutar sus SQL, las
  diez versiones historicas revisadas.
- Dos de esas diez versiones son de DAG y sus objetos ya no existen en DEV. La
  reconciliacion de licencias no los recreo: el drift funcional sigue separado
  para el chat responsable de DAG.

## LIC-RELINK-01: tokens de reenlace Usuario admitidos

- App Usuario 309 acepta los tokens normales de seis caracteres y los tokens
  de reenlace de ocho caracteres emitidos por App Admin o Superweb.
- La causa era una validacion local limitada exactamente a seis caracteres; el
  backend de reenlace no llegaba a recibir el token de ocho caracteres.
- Unitarios dirigidos, Ktlint del modulo, Lint Vital y APK DEV de Usuario
  correctos. Es un candidato local: no hubo instalacion, push ni publicacion.
- Este cierre corrige solo la entrada del token. La confirmacion atomica del
  reenlace, el limite de dispositivos y las sesiones pendientes pertenecen al
  ticket separado `LIC-RELINK-02`.

## Integracion local App Usuario/Admin con DAG Browser

- App Usuario 308 reconoce los paquetes DAG Browser DEV, LAB y produccion como
  componentes protegidos. No los incorpora a la linea base de aplicaciones ni
  genera una solicitud de aprobacion al instalarlos.
- App Admin 291 conserva como `Pendiente` una aplicacion que tenga una solicitud
  abierta; ya no la presenta como permitida mientras espera la decision.
- Pruebas unitarias dirigidas correctas. Lint y APK DEV de ambas apps correctos.
  El Ktlint global de App Usuario sigue bloqueado por formato previo en
  `UserAnnouncementsScreen.kt` y `UserFeedbackViewModel.kt`, fuera de este lote;
  los archivos modificados en este ticket no aparecen entre las infracciones.
- Es un cierre local: no hubo instalacion fisica, push ni publicacion DEV. Las
  versiones DEV publicadas continúan siendo Usuario 307 y Admin 290.

## Entrega local Wi-Fi de APK DAG

- La Mac de desarrollo tiene activo el LaunchAgent
  `com.glosh.dag-apk-wifi` en el puerto `8787`. Sirve solamente el APK DEV
  local de DAG mediante una ruta privada persistente y no depende de ADB ni de
  Supabase.
- El servidor no lista carpetas, no copia el APK, no registra la ruta secreta,
  usa `no-store` y admite descarga reanudable. El token queda fuera de Git con
  permisos `0600`; la URL vigente se obtiene con
  `python3 scripts/dag_diagnostics/dag_apk_wifi_server.py urls`.
- Verificacion local: cinco unitarios correctos, LaunchAgent en estado
  `running`, pagina accesible por la IPv4 Wi-Fi y descarga integral de DAG 206
  con 121.839.665 bytes y SHA-256
  `62a5b3787892c515dfc76eca929bca16ac4df66c66df15fb0c3b062b8aa6feca`,
  identico al artefacto construido.
- No cambia Android, GloshIA, Gradle, Supabase ni Production. Android conserva
  su confirmacion manual de instalacion. La Mac debe estar encendida, despierta
  y en la misma LAN que el telefono.

## GloshIA Visual R4: lote de entrenamiento cerrado en NO-GO

- R3.1 continúa como único modelo oficial, con umbral `0,40`, política visual y
  pipeline intactos. No se exportó ONNX ni APK y `frozen_test`/`final_sealed`
  permanecen cerrados.
- Sobre 162 grupos revisados por el propietario, el reentrenamiento limpio tipo
  R1 redujo falsos permisos de 31 a 9, pero elevó falsos filtros de 54 a 56 y
  degradó todas las validaciones originales; fue rechazado.
- Los pilotos acotados posteriores también quedaron `NO-GO`: interpolación de
  pesos, cabeza residual sobre R3.1, MobileNetV4 Conv Small y TinyCLIP 40M/32.
  Los backbones alternativos captaron positivos, pero sobrebloquearon el control
  fijo (18 a 35 falsos filtros, con límite 5).
- Se eliminaron checkpoints y código experimental rechazado; sólo permanecen
  informes JSON privados. No repetir variantes locales de pesos o arquitectura
  con este mismo corpus.
- Próximo paso autorizado sólo como propuesta: corpus independiente de positivos
  y negativos cercanos, agrupado por identidad/origen, seguido por entrenamiento
  o destilación con GPU bajo los gates ya congelados. Requiere autorización
  nueva. Evidencia y detalle:
  `docs/dag/v3/GLOSHIA_R4_THUMBNAIL_REPAIR_PILOT_2026-08-09.md`.
- Se preparó después un corpus realmente independiente con 508 imágenes nuevas
  para train (396 allow/112 filter) y 97 retenidas (76/21), excluyendo 18 dudas,
  dos duplicaciones/contaminaciones exactas y manteniendo nueve series mixtas
  dentro de un único split. R3.1 obtuvo 2 permisos peligrosos y 8 falsos filtros
  en el nuevo holdout.
- Cuatro pilotos sobre el mismo TinyCLIP —cabeza, tres capas con dos balances y
  encoder visual completo— fallaron la validación original con 2 o 3 permisos
  peligrosos frente al máximo 1. Se retiraron código y checkpoints fallidos.
  No seguir ajustando localmente el mismo estudiante.
- Próximo paso posible: destilación GPU hacia un único TinyCLIP del mismo costo
  Android. Requiere autorización separada de proveedor, presupuesto y licencia;
  no hay gasto ni candidato pendiente de integración.
- Prueba previa de profesores congelados: DINOv2-Small quedó en originales 3/3,
  holdout 4/7 y dirigido 28/22; SigLIP Base en 3/2, 3/7 y 38/9. Ambos son
  `NO-GO`: recuperan inocentes pero permiten demasiados positivos. No destilar
  sus salidas actuales.
- Siguiente ticket propuesto: ajustar primero un profesor en RunPod Secure Cloud
  RTX 4090 y sólo destilarlo si pasa los tres gates. Límite sugerido USD 3 y
  cuatro horas con apagado automático. Subir imágenes privadas, crear recursos
  o gastar requiere autorización explícita. El propietario autorizó el camino,
  pero eligió no alquilar GPU antes de crear cuenta, subir imágenes o gastar;
  no hubo operación externa.
- Se probó entonces el último camino local fundado en el MacBook Air M2 de 8 GB:
  SigLIP Base balanceado, SigLIP Base sin peso de clase y DINOv2-Small con peso
  filter 1,5. Los tres quedaron `NO-GO`. El mejor SigLIP mantuvo seguridad pero
  dejó 22 falsos filtros en las 112 vistas y 19 en el holdout; DINOv2 dejó 58 y
  51 respectivamente. No destilar ni repetir balances/capas/épocas contra los
  mismos gates.
- MPS sí fue técnicamente viable: los smokes usaron aproximadamente 622 MB para
  SigLIP y 207 MB para DINOv2. El límite fue generalización, no memoria ni falta
  de aceleración local. R3.1, Android, ONNX, umbral, política y `final_sealed`
  permanecen intactos. Los entrenadores fallidos se retiraron; sólo se conservan
  informes privados y la evidencia detallada en el documento R4.

## Candidato local en validacion: DAG Browser 206 — captura progresiva y formatos estaticos

- DAG 206 (`0.70.10-dev`, extension `2.0.9`) corrige dos causas generales
  observadas en el informe real `DAG-EA8DTGZM`, sin excepciones por sitio y sin
  cambiar R3.1, el umbral, la politica visual, los hilos ni ONNX.
- La captura de red ya no usa un vencimiento absoluto de cinco segundos: admite
  hasta quince segundos para comenzar y, una vez iniciada, renueva un limite de
  inactividad de cinco segundos con cada bloque recibido. Conserva los topes de
  2 MiB por imagen, 8 MiB globales y 128 streams activos.
- `ImageDecoder` puede ahora reconocer de forma autoritativa una imagen que
  `BitmapFactory` no identifica en la lectura preliminar. Se agregaron BMP e ICO
  estaticos a los MIME admitidos; dimensiones inseguras, animaciones, contenido
  corrupto, respuestas vacias y errores de red siguen cerrados.
- El flight recorder conserva `viewport_images_ready` aunque FCP ya haya hecho
  visible el documento. Es una correccion de observabilidad; no retrasa ni
  libera la navegacion.
- Evidencia del informe previo: 431 decisiones de medios, cola nativa promedio
  13 ms, inferencia promedio 62 ms y ningun trabajo superior a un segundo. Los
  29 `unsupported_image` y 36 cierres de transporte concentraban el problema;
  CPU/ONNX no era el cuello de botella de esa sesion.
- Validacion local: 157 unitarios DEV y 157 Diagnostic, barrera JS 27/27,
  Ktlint, Lint de ambos flavors, compilacion instrumentada y APK DEV/Diagnostic
  correctos. APK DEV: 121.839.665 bytes, SHA-256
  `62a5b3787892c515dfc76eca929bca16ac4df66c66df15fb0c3b062b8aa6feca`;
  firma DEV estable `d51bc0da…f8a8832`. APK Diagnostic: 121.844.721 bytes,
  SHA-256
  `edfd355bf4e0fca4fadfaafeaf67ca5970a27bf2f33a7f7132ee054503c093d3`.
- Pendiente antes de promover: instalar el APK normal preservando datos, probar
  primera carga y scroll rapido en Mimo, Cheeky y Fravega, y enviar un informe
  nuevo para confirmar formatos recuperados y distinguir timeout de inicio de
  timeout por inactividad. No se actualizo el manifiesto publico.
- Artefacto DEV candidato inmutable y descarga publica verificada byte a byte:
  `dev-updates/candidates/dag-browser-dev-206-0153586-62a5b378.apk`.

## Candidato local en validacion: DAG Browser 205 — caja negra privada

- DAG 205 (`0.70.09-dev`, extension `2.0.8`) incorpora un flight recorder
  local acotado sin cambiar R3.1, `dag-36`, umbrales, politica, hilos u ONNX.
- Registra eventos estructurados de navegacion y el resultado terminal de cada
  decision nativa: carrier, prioridad, bytes, dimensiones, razon, score y
  tiempos acotados. No persiste fotos, pixeles, URL, consultas, texto, HTML,
  cookies, headers ni credenciales; cada candidata usa un token salado de sesion.
- El camino visual solo encola una estructura pequena; el hash, el JSON y la
  escritura por lotes ocurren fuera del hilo UI y del executor visual. Conserva
  dos archivos internos de 512 KiB y limita cada informe a 4.096 eventos. Sobre
  el limite reemplaza lo mas antiguo. Las pestañas privadas no se registran.
- `Mas opciones > Diagnostico DAG` permite revisar cantidad, borrar solo el
  registro tecnico o enviarlo. Subir no borra el registro. Borrar cache o datos
  de navegacion no lo toca; desinstalar o borrar los datos Android si.
- Supabase DEV `syeycayasyufedwoprea` contiene el receptor
  `dag-diagnostic-report` y la tabla RLS sin acceso de `anon`/`authenticated`.
  La credencial limitada del APK solo sube; la credencial de lectura queda fuera
  del APK y del Git. Los informes vencen a los 14 dias mediante `pg_cron`.
- Validacion remota: lectura y subida separadas correctas; acceso anonimo 401;
  metadato con forma de URL rechazado 400; informe sintetico recuperado por
  `DAG-BEAKMQRU`; asesores sin hallazgos asociados a los objetos nuevos.
- Validacion fisica parcial: DAG 204 guardo 16 eventos, pero su APK se habia
  construido sin credencial de subida porque el proyecto Gradle independiente
  buscaba `.env` dentro de `app-dag-browser`. DAG 204 queda invalidado. DAG 205
  resuelve la ruta y hace fallar el empaquetado si URL HTTPS o token faltan.
- Validacion Android local de DAG 205: 153 unitarios DEV y 153 Diagnostic,
  barrera JS
  26/26, Ktlint, Lint DEV/Diagnostic, APK de ambos flavors y compilacion del APK
  instrumentado correctos. Falta prueba fisica de la UI y una subida desde el
  APK antes de promover DAG 205 sobre DAG 203.
- APK DEV: 121.838.725 bytes, SHA-256
  `6849635811da5025de5572c253e7f82ead879112282dda62dd0dae47bbcf024f`;
  firma DEV estable `d51bc0da…f8a8832`. APK Diagnostic: 121.843.777 bytes,
  SHA-256
  `bf6df70454fb62e1c77e9fddedb2d8586b9257611a1752371fae522b57a741b0`.
- Artefacto DAG 204 invalidado y no promovido:
  `dev-updates/candidates/dag-browser-dev-204-906aba8-84c25d1c.apk`. No se
  actualizo el manifiesto publico.
- Artefacto DAG 205 normal inmutable y verificado por descarga:
  `dev-updates/candidates/dag-browser-dev-205-1c3b1c9-68496358.apk`.
- Runbook y contrato de privacidad:
  `docs/dag/v3/DAG_DIAGNOSTIC_FLIGHT_RECORDER.md`.

## Candidato local en validacion: DAG Browser 203 — runtime visual unico

- DAG 203 (`0.70.07-dev`, extension `2.0.7`) conserva R3.1, `dag-36`, todos
  los umbrales y la configuracion CPU/ONNX de DAG 202. No cambia pesos, corpus
  ni politica visual.
- DEV ya no activa trazas, cronometros ni logs por imagen por coincidir con el
  sufijo `.dev`; una bandera de compilacion explicita reserva ese trabajo para
  Diagnostic. Las decisiones de ambas variantes son identicas.
- Diagnostic recibe resumenes acotados de saturacion, cola, bytes, timeout,
  stream y decode. No registra URL, consulta ni pixeles. Esto permite separar
  falsos filtros del modelo de imagenes ausentes por transporte.
- Un censo de estilos se ejecuta una sola vez y sólo en Diagnostic, en tiempo
  ocioso y con tope de 2.048 elementos. Informa únicamente cantidades de
  carriers `data:`/`blob:` en estilos y pseudo-elementos para decidir el cierre
  de esa cobertura con evidencia, sin cargar DEV.
- Las fuentes inline `data:`/`blob:` dentro de iframes registran ahora su
  identidad de frame y atraviesan la misma compuerta R3.1. Antes fallaban
  cerradas por compararse con el token del documento superior.
- Android contiene un unico modelo visual R3.1. Se retiro el fallback
  silencioso R1, el bypass de clasificacion, el flavor LAB instalable, la
  redaccion parcial experimental y tests/politicas historicos sin uso. Si R3.1
  no abre, el pipeline falla cerrado.
- GloshIA Lab permanece como herramienta local en `tools/gloshia_lab/`. El
  runtime vigente queda registrado en
  `docs/dag/v3/DAG_GLOSHIA_RUNTIME_REGISTRY.md`.
- Validacion local correcta: unitarios DEV/Diagnostic, Ktlint, Lint de ambas
  variantes, barrera JS 26/26, `assembleDevDebug`, `assembleDiagnosticDebug` y
  compilacion del APK de tests instrumentados. Queda la validacion fisica S22
  antes de promover el candidato sobre DAG 202.
- APK DEV: 121.784.309 bytes, SHA-256
  `c40e82c7fa7dc32d78008c8683df408a0e18f42186d91c6d3cad28880027dc1c`.
  Firma DEV estable `d51bc0da…f8a8832` confirmada.
- APK Diagnostic: 121.789.661 bytes, SHA-256
  `f0615c2a183833b88d4fad7c53b4b73906e47f38e245f6904fdbe5dd3d8b99cd`.
- El APK DEV candidato se subio como objeto inmutable independiente a Storage
  publico de Supabase DEV:
  `dev-updates/candidates/dag-browser-dev-203-c82ff72-c40e82c7.apk`. No se
  modifico el manifiesto de actualizacion ni se reemplazo el APK DEV publicado.
  Una descarga completa desde la URL publica confirmo 121.784.309 bytes y el
  mismo SHA-256 `c40e82c7…27dc1c`. No hubo push ni contacto con Production.
- Limite conocido separado: raster `data:`/`blob:` declarados dentro de hojas
  CSS o pseudo-elementos no atraviesan `webRequest`. No se agrego un barrido
  global de estilos sin evidencia porque podria degradar carga y scroll; se
  medira como carrier general en Diagnostic antes de elegir una compuerta.

## Baseline fisico anterior: DAG Browser 202 — restauración canónica `dag-36`

- La auditoría integral encontró una regresión de política introducida por
  `4213d87` el 2026-08-06: Android dejó de bloquear directamente la señal
  completa R3.1 `>= 0,40`, exigió corroboración de cuadrantes hasta `0,95` y
  dejó sin uso real los umbrales ordinarios `0,30/0,45`. GloshIA Lab y la
  documentación seguían ejecutando la política canónica, aunque Android aún se
  identificaba incorrectamente como `dag-36`.
- DAG 202 (`0.70.06-dev`) restaura el contrato oficial sin cambiar pesos,
  modelo, normalización, umbrales ni política: completa `>=0,40` bloquea con una
  inferencia; ordinaria `0,30–0,40` revisa cuatro cuadrantes y bloquea cualquiera
  `>=0,45`; panoramas conservan una región `>=0,70` o consenso de dos `>=0,50`.
- Se eliminó el bloqueo tardío redundante y la traza distingue
  `full_threshold`, `full_strong`, `uncertain_regional`, `regional_strong` y
  `regional_consensus`. El test cruzado del Lab ahora verifica no sólo las
  constantes Android, sino también estas tres conexiones semánticas.
- En el examen dirigido de 162 decisiones del propietario, la simulación del
  runtime divergente produjo 80 permisos peligrosos y 1 falso filtro; el
  contrato canónico produjo 39 y 50. Es una muestra deliberadamente difícil,
  no una estimación de producción, pero demuestra que el veto regional estaba
  anulando señales completas y multiplicando trabajo.
- Beneficio de rendimiento: cualquier completa `>=0,40` vuelve a terminar con
  una inferencia en lugar de hasta cinco. No se modificaron colas, timeouts,
  scheduler, hilos Android/ONNX ni barrera web porque logs físicos recientes
  descartaron saturación: sus colas p95 quedaron muy por debajo de 2.250 ms.
- Validación local correcta: unitarios DEV, Ktlint, Lint Vital, barrera JS
  25/25 y `assembleDevDebug`. APK local versionCode `202`, SHA-256
  `0a18f4c156b15bce5236a9982180c9b437420ca744b4a294209db881ed982cdd`.
  Unitarios LAB/Diagnostic y contrato GloshIA Lab 5/5 también correctos.
- APK instalado in-place sobre DAG 199 en el SM-S908E, preservando perfil y
  datos. Google Imágenes, Frávega, Mimo y Cheeky procesaron 123/297/110/137
  raster con cola p95 `32/38/45/31 ms`, cero crash/ANR y recorrido vertical
  completo. Google mostró placeholders neutrales; Mimo abrió correctamente el
  menú después de seis desplazamientos. El modal propio de Frávega reaparece en
  cada recarga y se excluyó de la evaluación de gestos.
- R3.1 sigue como único modelo oficial. No hay excepción por sitio, URL,
  dominio, tamaño o dispositivo; `final_sealed` permanece cerrado. El candidato
  TinyCLIP multiescala local fue `NO-GO` y quedó completamente retirado.
- Evidencia física:
  `docs/compatibility/results/dag-browser-v202-dag36-runtime-restoration-sm-s908e-2026-08-11.md`.

## Histórico supersedido: DAG Browser 199

- DAG 199 (`0.70.03-dev`) agregó una compuerta parcial: una señal completa
  R3.1 `>= 0,95` bloquea sin que regiones débiles puedan vetarla. Por debajo de
  `0,95` la política regional anterior permanece intacta.
- El A/B sobre 536 decisiones binarias del propietario corrigió 13 permisos
  erróneos sin agregar falsos filtros en ese examen. La decisión fuerte usa una
  sola inferencia y queda identificada en diagnóstico como `full_strong`.
- Diagnostic 2 reprodujo una miniatura de bikini `137x137` en `0,9453`. Una
  compuerta compacta experimental `0,94` se retiró al comprobar que otra
  variante `62x82` de la misma búsqueda bajó a `0,7847-0,9166` y siguió visible.
- Las miniaturas de señal media o baja no se parcharon: regiones forzadas y
  cinco representaciones no ofrecieron una mejora segura. Bloquear sólo por
  tamaño afectaría fotos permitidas, avatares, productos, logos y controles.
  Continúan siendo una limitación del modelo.
- R3.1, su hash, hilos y ONNX permanecieron sin cambios. La auditoría posterior
  de DAG 202 comprobó que la semántica Android ya había divergido de `dag-36`;
  por eso este estado queda como antecedente, no como baseline vigente.
- Unitarios DEV/Diagnostic, Ktlint, Lint Vital y APK DEV/Diagnostic correctos.
  APK DEV SHA-256:
  `d3a299431d2a24a18bbf5a9a863d71c14800847f7f22df63802ae46d8607b191`.
  El flavor Diagnostic pasa a versionCode `2`.
- Los APK DEV y Diagnostic se instalaron in-place en el SM-S908E. El propietario
  confirmó en DAG normal que el caso de la tienda de bikinis corregido por la
  compuerta fuerte ya no aparece. Google Diagnostic confirmó que las miniaturas
  circulares restantes sí pasan por el pipeline pero R3.1 las permite con señal
  insuficiente; no es una omisión del interceptor.
- Mimo y Cheeky recorrieron hasta el pie en vertical sin crash/ANR. Mimo obtuvo
  `page_visible=1562 ms`, cola p95 `31 ms` y 1/42 cuadros lentos; Cheeky
  `page_visible=2656 ms`, cola p95 `43 ms` y 2/42 cuadros lentos. Frávega cargó
  tarjetas e imágenes, pero su modal propio de ubicación interceptó los gestos;
  esa muestra no certifica scroll ni carrusel.
- `run_live_site.sh` usa ahora el `logicalFrame` activo y registra
  `gesture_display_size`; antes un cambio a horizontal podía colocar los swipes
  fuera de la pantalla y producir una muestra engañosa.
- Siguiente trabajo autorizado: candidato compacto separado, entrenado con
  pares original/miniatura y negativos cercanos. R3.1 continúa oficial y no se
  reemplaza hasta que el candidato supere seguridad, falsos filtros, latencia y
  tamaño. Evidencia:
  `docs/dag/v3/GLOSHIA_R31_RUNTIME_GATE_2026-08-11.md`.
- Cierre posterior: R1 como veto adicional quedó `NO-GO` (56/85 frente a
  85/28 FP/FF de R3.1 en 536 miniaturas). Un MobileNetV4 compacto entrenado con
  577 grupos limpios también quedó `NO-GO`: 22/256 en el gate y 1/54 en el
  holdout independiente. No hay checkpoint, ONNX ni integración. El próximo
  paso no es otro ajuste de pesos sobre los mismos gates.
- Diagnostic 4 cerró la alternativa de fuente responsiva: los ocho raster
  físicos no inline observados en Google declararon `srcset=false`, cero
  candidatos mayores y cero fuentes `picture`. Doce miniaturas de `56-82 px`
  sí cruzaron el pipeline; cuatro se bloquearon y ocho se permitieron con
  puntajes entre `0,0283` y `0,8419`. Es pérdida de señal del raster, no un
  bypass. La instrumentación queda limitada al flavor Diagnostic y no registra
  URL, consulta, bytes ni píxeles. Los recursos inline y placeholders internos
  se descartan antes de emitir para evitar ruido. DEV normal instalado continúa
  en DAG 199.
- DAG 201 (`0.70.05`) contiene sólo esa herramienta diagnóstica opt-in; no
  cambia decisiones en DEV/LAB. Próxima decisión de producto: corpus compacto
  independiente o cierre explícito por incertidumbre aceptando falsos filtros.
  Evidencia: `docs/compatibility/results/dag-browser-v201-compact-source-diagnostic-sm-s908e-2026-08-11.md`.

## Candidato validado local: DAG Browser 198

- Herramienta diagnóstica local: existe el flavor separado `diagnostic`, paquete
  `com.contentfilter.dagbrowser.diagnostic.dev`, versionCode `1`. Usa el mismo
  R3.1, umbral y política que DEV, pero no comparte perfil, pestañas ni proceso.
  `run_live_site.sh --diagnostic` fija el PID exacto y genera
  `diagnostic-session.jsonl` sin URL, consulta, bytes ni píxeles. La primera
  corrida válida de Google produjo 32 decisiones aisladas, cero crash/ANR y
  reprodujo cinco permisos con señal completa alta (`0,8529-0,9903`) no
  confirmada por regiones. La herramienta no cambia la decisión visual.
- DAG 198 (`0.70.02-dev`) está instalado in-place en el SM-S908E con los datos
  preservados; la extensión integrada es `2.0.3`. Todavía requiere aceptación
  visual del propietario antes de reemplazar al baseline seguro DAG 176.
- La cola, cancelación, quietud y entrega quedan ligadas a la identidad exacta
  de pestaña/documento. Un documento retirado ya no puede publicar decisiones,
  completar el viewport ni contaminar la vista siguiente.
- `data:` y `blob:` pasan por la compuerta nativa con presupuestos acotados; no
  existe el límite histórico de 16 fuentes que dejaba miniaturas posteriores
  sin decisión. La prueba física permitió dos raster seguros y bloqueó dos
  sondas de filtro.
- SVG/ICO ya no tienen bypass visible general. Sólo SVG pasivo, pequeño y
  saneable se permite como interfaz; animación, decode fallido y formato
  desconocido fallan cerrados. Se retiró la liberación heurística de sprites
  raster sin decisión GloshIA.
- Dos corridas deterministas cargaron 4/4 imágenes críticas en `364/381 ms` y
  20/20 lazy en `8.940/8.839 ms`; cola p95 `1 ms`, PSS `266-277 MiB`, cero
  crash/ANR. Frávega, Cheeky y Mimo registraron `5,41/5,13/7,32 %` de frames
  lentos y cola p95 `47/74/33 ms` bajo ráfagas reales.
- Se preservaron R3.1, umbral `0,40`, preprocesamiento, política regional,
  hilos ONNX y `final_sealed`. No hay excepciones por sitio, URL, dominio o
  dispositivo. El banner GIF sigue bloqueado por la política de animación.
- Validación: harness JS 25/25, `testDagProtectionJs`, unitarios DEV, Ktlint,
  APK DEV/LAB y sintaxis del laboratorio correctos. APK DEV SHA-256:
  `be11b9905901a604a80a3bf028e1357a009b3c5647ca384b74f43ce1157421c7`.
- Evidencia:
  `docs/compatibility/results/dag-browser-v198-document-inline-media-gate-sm-s908e-2026-08-11.md`.

## Candidato validado local: DAG Browser 183

- DAG 183 (`0.69.87-dev`) esta instalado en el SM-S908E con datos preservados;
  la extension integrada es `1.94.0`.
- Durante un gesto tactil, el executor visual baja una sola vez a un worker y
  restaura dos workers 250 ms despues de `UP`/`CANCEL`. Ya no cancela y vuelve a
  publicar el mismo temporizador en cada evento `MOVE`.
- La rafaga acotada admite hasta 128 respuestas y 144 analisis JS pendientes,
  manteniendo los presupuestos globales de bytes y dos decisiones nativas en
  vuelo. El A/B con 32 volvio a dejar fotos vacias y fue retirado.
- Se corrigio una corrupcion intermitente posterior a `model_allow`: la
  extension escribia el `Uint8Array` permitido en `StreamFilter` y lo llenaba
  con ceros inmediatamente. Como `write()` no garantiza una copia sincrona, un
  decode diferido de Gecko podia recibir bytes alterados. Los bytes permitidos
  ahora quedan bajo propiedad del stream; los originales bloqueados se siguen
  limpiando.
- Fravega `Ofertas Unicas`: en DAG 182, lavarropas y heladera quedaron blancos
  aunque los logs registraron `model_allow`; en DAG 183 ambas fotos aparecieron
  despues del mismo desplazamiento. El propietario confirmo el resultado.
- Mimo conserva el banner raster principal. El rectangulo gris inferior es un
  GIF/video bloqueado por la politica vigente, no una foto fallida.
- El A/B de memoria descarto los limites 32/128 y el control tactil como causa
  del consumo de Mimo. DAG uso aproximadamente 0,85-1,06 GiB PSS y Chrome cerca
  de 1,04 GiB en la misma pagina; no se agrego ningun ajuste de memoria a ciegas.
- Modelo, pesos, umbrales, preprocesamiento, politica visual, ONNX y
  `final_sealed` permanecen intactos. No hay excepciones por sitio, URL, dominio
  o dispositivo.
- Validacion automatica: JS 23/23, unitarios, Ktlint, Lint Vital y APK DEV
  correctos. APK SHA-256:
  `4fa03dd963e7a41c4365bb2a71ec38e6a2fb50e45868d04a3b89c232c041e50e`.
- Evidencia:
  `docs/compatibility/results/dag-browser-v183-touch-and-stream-delivery-sm-s908e-2026-08-08.md`.

## Baseline seguro local: DAG Browser 176

- DAG 176 (`0.69.80-dev`) queda aceptado por el propietario como nuevo punto
  de partida local. Esta instalado en el SM-S908E con datos preservados; la
  extension integrada es `1.90.0`.
- Conserva GloshIA Visual R3.1, umbral `0,40`, politica visual, dos workers de
  analisis, ONNX intra-op 2 / inter-op 1 y `final_sealed` cerrado. No contiene
  excepciones por sitio, URL, dominio o dispositivo.
- El lote acota el trabajo tardio de imagenes pendientes, reinicia correctamente
  elementos `img` reutilizados, conserva el placeholder gris bloqueado con cache
  acotada, limita por bytes la cache JS de reemplazos, evita copias redundantes
  del frame de navegacion, protege miniaturas restringidas y retira scroll/pull
  refresh muertos.
- La deteccion inicial y dinamica de patrocinados permanece antes del reveal.
  El escaneo inicial sigue esperando `DOMContentLoaded`: adelantarlo en DAG 175
  redujo una espera pero dejo fotos ocultas/negras y fue retirado por completo.
- Tambien se retiraron completamente el R8 experimental de DAG 171 y la
  transicion interactiva parcial de DAG 173; ninguno forma parte del baseline.
- Diagnostico Google `Todo -> Imagenes` en DAG 174: fotos visibles a 971 ms,
  pagina protegida visible a 2.343 ms y `onPageStop` a 2.730 ms; 3/23 cuadros
  tardios y 12 eventos de alta latencia de entrada. La pausa corresponde a la
  captura segura de la pagina anterior, no a saturacion principal de CPU o
  GloshIA. Por decision de producto no se agrega por ahora una segunda sesion
  Gecko solo para desplazar esa pagina anterior durante la transicion.
- El propietario confirmo DAG 176 como una version general muy buena. Este es
  un checkpoint local aceptado, no una certificacion de publicacion: antes de
  publicar sigue pendiente repetir fixture y matriz Mimo/Cheeky/Fravega.
- Validacion automatica: JS 23/23, unitarios, Ktlint, Lint y APK DEV correctos.
  APK SHA-256:
  `022f191baa272d12b51487fc55e5d05e34da7e4a7ec62868410a6ff620c015f5`.
- Evidencia:
  `docs/compatibility/results/dag-browser-v176-safe-baseline-sm-s908e-2026-08-07.md`.

## DAG Browser 169: restauracion determinista de imagenes tras cambios de cache

- DAG 169 (`0.69.73-dev`) esta instalado en el SM-S908E con datos preservados;
  la extension integrada es `1.86.0`.
- La eliminacion de descargas/PDF de DAG 159 no causo la regresion: no cambio
  la barrera, `webRequest`, GeckoView ni el pipeline visual. La intermitencia
  aparecio despues, al separar en DAG 162 la limpieza de cache del
  `versionCode` de cada APK.
- El diagnostico reprodujo una carga vacia sin decisiones nativas de imagen y
  una carga correcta despues de reiniciar/limpiar cache. La causa era estado de
  cache de Gecko anterior al comportamiento vigente de los listeners: podia
  reutilizar una respuesta sin volver a activar el filtro actualizado.
- La extension registra primero ambos listeners y luego invoca una sola vez
  `browser.webRequest.handlerBehaviorChanged()` al iniciar. Es la API oficial
  de Firefox para vaciar la cache en memoria cuando cambia el comportamiento de
  `webRequest`; no se ejecuta por pagina, gesto ni imagen.
- La revision de cache interceptada sube de 4 a 5 para retirar una sola vez la
  cache persistente ya contaminada en instalaciones existentes. No se borran
  pestanas, historial, cookies, perfiles ni datos del usuario.
- Fixture LAB frio/caliente: 4/4 raster criticos cargados sin errores;
  estabilidad visual en 537 ms y 218 ms, respectivamente; cola p95 fria de
  1 ms; cero crash/ANR. En DEV, Fravega conservo banner, categorias y productos
  tras dos recargas calientes. Google `Todo -> Imagenes -> Todo -> Imagenes`
  mostro las fotos completas en ambas transiciones. Mimo mostro sus productos y
  Cheeky cargo su pagina sin crash/ANR.
- La instrumentacion temporal fue retirada. GloshIA R3.1, modelo, umbral,
  politica visual, hilos, ONNX, publicidad, video y scheduler permanecen
  intactos. No hay excepciones por sitio, URL o dominio.
- Tests JS 22/22, unitarios, Ktlint, APK DEV y APK LAB correctos. El APK DEV
  instalado tiene SHA-256
  `9aa0496d8d1ed4955e592030f0f85f6b1a7f4482c5f042c087480cc86a72a231`.
- Evidencia:
  `docs/compatibility/results/dag-browser-v169-gecko-cache-listener-refresh-sm-s908e-2026-08-07.md`.

## DAG Browser 164: miniaturas de pestanas bajo demanda

- DAG 164 (`0.69.68-dev`) esta instalado en el SM-S908E con datos preservados;
  la extension integrada permanece en `1.81.0`.
- La causa del consumo evitable era Android, no Gecko ni GloshIA: al iniciar,
  la actividad restauraba en memoria miniaturas de todas las pestanas aunque
  el selector estuviera cerrado. Una restauracion asincrona tambien podia
  terminar despues del cierre y volver a retener su bitmap.
- Las miniaturas persistidas ahora se cargan solo al abrir el selector y se
  liberan al cerrarlo o cuando la actividad deja de estar visible. Los
  resultados asincronos tardios se descartan si el selector ya no las necesita.
- El frame de navegacion que evita destellos queda separado y no se libera al
  cerrar el selector. Se mantienen tres sesiones Gecko abiertas para no
  degradar el cambio entre pestanas.
- Medicion fisica con 15 pestanas: selector cerrado, 1 bitmap / 8.505 KiB;
  abierto, 5 / 25.713 KiB; vuelto a cerrar, 2 / 8.516 KiB. Google Imagenes
  continuo mostrando fotos despues del cambio.
- GloshIA R3.1, umbral, politica, extension, publicidad, video, scheduler,
  hilos y ONNX permanecen intactos. Tests JS 21/21, unitarios, Ktlint y APK son
  correctos.
- Evidencia:
  `docs/compatibility/results/dag-browser-v164-tab-thumbnail-residency-sm-s908e-2026-08-06.md`.

## DAG Browser 163: Google Imagenes y reconciliacion tardia

- DAG 163 (`0.69.67-dev`) corrigio Google Imagenes; DAG 164 conserva esa
  extension y ese comportamiento.
- Google Imagenes podia conservar vacias miniaturas ya aprobadas: a 10 segundos
  habia 108 imagenes decodificadas y solo 58 estables; las 50 fotos reales
  restantes habian terminado despues del ultimo barrido de 4 segundos.
- La reconciliacion sigue siendo acotada, pero agrega barridos a 6, 8 y 12
  segundos. No queda ningun intervalo, observer global adicional ni proceso
  permanente.
- En la validacion final las fotos de Google Imagenes ya estaban visibles en la
  captura de 5 segundos y permanecieron a 7 y 12 segundos. La pagina fue
  visible en 1.049 ms y la cola visible quedo quieta en 1.346 ms.
- La instrumentacion temporal fue retirada. GloshIA R3.1, umbral, politica,
  scheduler, publicidad, video, hilos y ONNX permanecen intactos.
- Tests JS 21/21, unitarios, Ktlint y APK son correctos.

## DAG Browser 162: primera carga estable y cache visual versionada

- DAG 162 (`0.69.66-dev`) introdujo la cache visual versionada y la primera
  reconciliacion acotada; DAG 163 extiende su ventana para Google Imagenes.
- La regresion reciente quedo localizada en dos puntos generales introducidos
  con DAG 158: cada `versionCode` borraba toda la cache de Gecko y la barrera
  podia conservar ocultas durante segundos imagenes que ya estaban aprobadas y
  decodificadas.
- La cache interceptada ahora tiene una revision propia. Una actualizacion
  normal de la APK no vuelve a vaciarla; solo un cambio futuro y explicito del
  contrato de entrega puede incrementar esa revision. No se borra historial,
  perfil, cookies ni datos del usuario.
- La barrera ejecuta cinco reconciliaciones acotadas durante los primeros
  cuatro segundos. Revelan solamente imagenes completas y decodificadas y
  terminan solas; el placeholder bloqueado de 1x1 no se considera renderizable.
- El diagnostico temporal demostro la causa: a 5 segundos Fravega tenia 45
  imagenes decodificadas pero solo 6 estables; a 10 segundos eran 116/116. La
  instrumentacion fue retirada antes del cierre.
- En la prueba final con cache fria las dos filas de categorias de Fravega
  aparecieron completas en la captura de aproximadamente 5 segundos. Mimo
  abrio su menu completo despues de desplazar la pagina. Google no mostro
  patrocinados a 2 ni 5 segundos; mapa e imagenes terminaron visibles.
- No cambian GloshIA R3.1, umbral, politica visual, hilos, ONNX, scheduler ni
  bloqueo de publicidad/video. No hay excepciones por dominio, URL o sitio.
- Tests JS 21/21, unitarios, Ktlint y APK son correctos. Evidencia:
  `docs/compatibility/results/dag-browser-v162-cold-image-reconciliation-sm-s908e-2026-08-06.md`.

## DAG Browser 159: limpieza estructural y retiro de descargas

- DAG 159 (`0.69.63-dev`) esta instalado en el SM-S908E con datos preservados.
- Por decision explicita del propietario se retiro por completo la funcion de
  descargas/PDF: integracion Gecko, politica, estados, ejecutor, FileProvider,
  menu, dialogs, layouts, iconos, strings y tests asociados.
- `DagBrowserActivity.kt` bajo de 3.309 a 2.599 lineas. La normalizacion
  recursiva de opciones de formularios se movio a `DagChoicePromptPolicy` y se
  retiro `androidx.core`, que ya no tenia consumidores.
- El lote elimina 1.571 lineas netas y lint no informa recursos sin uso. No
  modifica GloshIA R3.1, umbral, politica visual, barrera, extension, hilos ni
  scheduler.
- Tests JS, unitarios, Ktlint, Lint y APK son correctos; no hubo crash, ANR ni
  OOM en Google, Mimo, Fravega y Cheeky.
- La observacion de imagenes vacias con `model_allow` quedo diagnosticada y
  corregida en DAG 162; no fue causada por el refactor de descargas.

## DAG Browser 158: primera revelacion saneada y fotos restauradas

- DAG 158 (`0.69.62-dev`) esta instalado en el SM-S908E con datos preservados.
- La pagina nueva se revela solo despues de barrera, contenido protegido y
  primer barrido publicitario; esto elimina la rafaga inicial de patrocinados.
- La regresion de DAG 156 quedo localizada: ejecutar `ads.js` antes de
  `barrier.js` permitia ocultar contenedores antes de registrar sus imagenes.
  DAG 157/158 restaura barrera primero y conserva el handshake de saneamiento.
- En la validacion fisica reaparecieron el hero de Mimo y las categorias e
  imagenes de Fravega. DAG 158 mantuvo Mimo visible en `1.068 ms`, viewport
  quieto en `901 ms` y pagina completa en `2.115 ms`, sin crash ni ANR.
- El puente de mensajes de la extension fue refactorizado en funciones
  cohesivas y nombradas; no cambia protocolo, politica, modelo, umbral ni
  concurrencia.
- Evidencia:
  `docs/compatibility/results/dag-browser-v158-sanitized-reveal-and-image-order-sm-s908e-2026-08-06.md`.

## Estado vigente al 2026-08-05: GloshIA Visual R3.1

- El modelo activo en `main` es GloshIA Visual R3.1, promovido desde el
  candidato hibrido INT8 del round30.
- Archivo: `tinyclip-r3-head-hybrid-int8.onnx`.
- SHA-256: `c8b64af8092d3718c58736a511c996d0d443dacf3eaa74620b1e5af439a3cd48`.
- Fallback de apertura: R1, sin ejecutar dos modelos por fotografia.
- Se preservan RGB 224x224, letterbox DAG, umbral `0,40`, ONNX Runtime
  Android 1.27.0 y CPU local.
- En el mismo examen, los falsos permisos no aumentaron y los falsos filtros
  bajaron de `3/21` a `2/21` y de `2/15` a `0/15`.
- La APK local de trabajo pasa a versionCode `109`, versionName
  `0.69.13-dev`. No se publico ni se hizo push.
- `final_sealed` permanece cerrado. Corpus, splits, informes y checkpoints
  privados se conservan para futuros entrenamientos.
- El APK DEV local de DAG 109 conserva R3.1 y agrega únicamente optimización de
  entrega: prioridad asíncrona para raster visibles y deduplicación de
  decisiones raster idénticas en vuelo. No cambia pesos, umbral, vistas,
  política ni el contrato fail-closed. Evidencia:
  `docs/compatibility/results/dag-browser-v109-performance-gloshia-2026-08-05.md`.
- Se preparó un flavor aislado `lab` (versionCode `110`) para medir el fixture
  determinista por HTTP de loopback sin alterar TLS ni navegación del flavor
  DEV. Android envía la Activity a Home durante el lanzamiento, por lo que no
  hay métricas físicas válidas de la página; esto no afecta al modelo. El test
  directo en el S22 abrió R3.1 y ejecutó `22/22` inferencias sin errores: en la
  repetición final p50 `30,92 ms`, p95 `33,75 ms`, PSS `114.405 KiB`. No se publicó. Su estado está
  en `docs/compatibility/results/dag-browser-v110-lab-fixture-harness-2026-08-05.md`.

Se corrigió la fuente de verdad del laboratorio Python: estaba fijada al hash
histórico de R3 (`0aaa…`) aunque DAG usa R3.1 (`c8b64…`). Se reejecutaron los
mismos exámenes de gate 27 y round 30 con R3.1, sin sobrescribir los informes
históricos y sin abrir `final_sealed`. En las 536 decisiones binarias
combinadas, R3.1 obtuvo 26 falsos permisos, 56 falsos filtros, balanced
accuracy `84,98 %` y PR-AUC `89,22 %`. El R3 histórico tuvo 27, 67, `83,15 %`
y `82,14 %`, respectivamente. Es una mejora de baseline, no una autorización
para entrenar o integrar otra candidata.
Informe reproducible:
`docs/dag/v3/GLOSHIA_R3_1_BASELINE_REVALIDATION_2026-08-05.md`.

## Repositorio y flujo vigente

- Carpeta canonica: `/Users/yejielnehmad/Developer/content-filter`.
- Rama de trabajo: `main` local.
- `origin/main` conserva DAG 96 publicado. `main` local contiene DAG 107 como
  punto seguro confirmado: navegador de DAG 95, GloshIA R3 y transiciones de
  navegacion sin destellos ni cierres falsos por una señal Gecko ausente.
- App Usuario 307, App Admin 290 y DAG 96 estan publicadas en DEV. Production
  no fue modificada.
- DAG 96 (`0.69.0-dev`) es el canary DEV reversible de GloshIA R3. R1
  permanece empaquetado como fallback de apertura; no se ejecutan dos modelos
  por fotografia.
- DAG 107 (`0.69.11-dev`) esta instalado y confirmado por el propietario en
  SM-S908E. Conserva el retiro del autoactualizador y agrega una captura
  efimera de la pagina protegida durante navegaciones, mas una terminacion de
  carga que acepta el primer dibujo de Gecko o la cola visual protegida quieta.
  Todavia no fue publicado.
- Los APK finales se construyen solo desde `main` local integrado.
- Las apps Usuario/Admin no fueron modificadas. Supabase Storage DEV recibio
  unicamente la APK y el manifiesto de DAG 96.

### Almacenamiento local e iCloud

- La unica copia canonica esta en
  `/Users/yejielnehmad/Developer/content-filter`, fuera de iCloud Drive.
- No existe una copia del proyecto bajo `Documents` ni bajo
  `Library/Mobile Documents/com~apple~CloudDocs`. Codex conserva unicamente su
  carpeta de trabajo vacia en `Documents/Codex`; no contiene codigo, APKs ni
  datos del proyecto.
- La limpieza del 2026-08-03 redujo la carpeta canonica de aproximadamente
  `9,4 GB` a `1,4 GB`. Builds, caches, dependencias reinstalables y candidatos
  R2 descartados se movieron a
  `~/.Trash/content-filter-cleanup-20260803` (`6,4 GB`), recuperables hasta
  vaciar la Papelera.
- Se preservaron siete conjuntos privados necesarios para continuar GloshIA:
  corpus actual de 1.000, revision balanceada, hard negatives usados por R3,
  revision historica referenciada, etiquetas multisenal, revision enfocada y
  candidato R3. El split R3 conserva `526/526` imagenes disponibles.
- Los builds locales fueron retirados; la proxima compilacion los regenera. La
  APK DAG 96 publicada, GitHub, Supabase y los modelos R3/R1 versionados no se
  modificaron.

| Aplicacion | versionCode | versionName DEV | Estado |
| --- | ---: | --- | --- |
| App Usuario | 307 | 1.0.1-dev | Publicada en DEV |
| App Admin | 290 | 1.0.1-dev | Publicada en DEV |
| DAG Browser | 96 | 0.69.0-dev | Canary R3 publicado y verificado en DEV |

Candidato local confirmado: DAG Browser 107 (`0.69.11-dev`), instalado en
SM-S908E y no publicado. Evidencia:
`docs/compatibility/results/dag-browser-v107-safe-navigation-sm-s908e-2026-08-04.md`.

El SM-A235M `R58T34V31AE` conserva DAG 95 porque el propietario omitio la
repeticion A23. DAG 96 puede obtenerse desde Actualizaciones de App Usuario y,
una vez instalado, las versiones siguientes tambien desde el menu propio de
DAG. No se registra una instalacion fisica de DAG 96 en este cierre.

## DAG Browser vigente

### DAG 155: transición estable antes del reflow del sitio

DAG 155 (`0.69.59-dev`) conserva la captura protegida de la página anterior
desde el instante en que Gecko acepta una navegación y permite reemplazar esa
captura por otra más reciente de la misma revisión. Corrige la ráfaga visible al
cambiar entre Imágenes y Todo en Google sin agregar demoras, animaciones ni
excepciones por sitio.

El diagnóstico cuadro por cuadro aisló dos causas: Google reacomodaba el DOM
antes de `onPageStart`, cuando DAG todavía mostraba la superficie viva, y la
primera captura de una navegación quedaba congelada aunque se hubiera tomado
durante un layout intermedio. DAG 154 adelantó la cobertura y retiró el primer
problema, pero no eliminó el cuadro comprimido porque la captura era vieja. DAG
155 conserva ese adelanto y permite que las capturas seguras posteriores
actualicen el frame de navegación. La ráfaga final eliminó el cuadro comprimido
y el propietario confirmó que la transición quedó correcta.

La corrección es general y no modifica GloshIA Visual R3.1, el umbral, la
política visual, `final_sealed` ni la compuerta fail-closed. El APK instalado con
datos preservados tiene SHA-256
`4ac86de40d0a971d247b93aba2bf8135029b93f0bebf38e821d04416e864a3c7`.
Evidencia:
`docs/compatibility/results/dag-browser-v155-stable-navigation-snapshot-sm-s908e-2026-08-06.md`.

### DAG 153: cierre de seguridad, scheduler acotado y A/B de CPU

DAG 153 (`0.69.57-dev`) con extensión `1.73.0` conserva el comportamiento
visual confirmado de DAG 147 y cierra cinco hallazgos generales sin cambiar
GloshIA Visual R3.1, el umbral, el preprocesamiento ni la política visual.

- La navegación DEV vuelve a convertir entradas HTTP a HTTPS y rechaza una
  navegación superior no HTTPS. Sólo el flavor LAB aislado admite su fixture
  loopback HTTP.
- `runaway-scheduler-guard.js` conserva como máximo 64 señales pendientes. Si
  alcanza el límite, vacía en orden mediante el método nativo y desactiva el
  yield: no pierde mensajes ni permite crecimiento de memoria sin límite.
- `DagLowInformationRasterPolicy` y sus tests fueron retirados: no tenían
  ninguna llamada desde el pipeline real y no corresponde conservar una
  política paralela que sólo exista en pruebas.
- `testDagProtectionJs` declara como entradas `ads.js`, el guard y
  `manifest.json`; los tests JVM declaran el directorio completo de assets de
  protección, por lo que Gradle repite los contratos cuando cambia la extensión.
- Se evaluó físicamente desactivar el spinning intra-op de ONNX con el mismo
  S22 y recorrido de Frávega. El promedio gráfico pasó de 58,65 a 60 fps, pero
  el p95 de inferencia empeoró de 213,5 a 271 ms (aproximadamente 27 %). El
  experimento fue retirado; se conservan dos workers Android, ONNX intra-op 2 e
  inter-op 1, que priorizan la carga de fotos frente a una diferencia marginal
  de frames.

Con la configuración final, el carrusel de Frávega midió 58,65 fps, p95 de
21,18 ms y un cuadro activo mayor a 33 ms; las tarjetas de Ofertas Únicas
mostraron fotos reales sin negros/grises generados por DAG. El menú de Mimo,
después de scroll, abrió completo a 55,42 fps y p95 de 30,08 ms. Cheeky cargó
banner y controles completos. No hubo crash, ANR ni OOM. El APK fue instalado
con `adb install -r`, preservando perfil y datos; SHA-256
`48eb4fc7d6ac4b86cf328b1c6932cbfe166baa7cad6c85f0dc1b9d2bdd204f46`.

Evidencia:
`docs/compatibility/results/dag-browser-v153-security-scheduler-onnx-sm-s908e-2026-08-06.md`.

### DAG 152: rollback del experimento geométrico

DAG 152 (`0.69.56-dev`) con extensión `1.72.0` vuelve al comportamiento de DAG
149/147. DAG 150 retuvo trabajos 24 ms y produjo imágenes rotas; DAG 151
conservó sólo geometría horizontal, pero el propietario no percibió una mejora
y pidió no conservar código sin efecto visible. Ambos experimentos fueron
retirados. Los números avanzan sólo para reemplazar extensiones ya instaladas
sin borrar perfiles o datos.

### DAG 149: rollback completo del experimento de interacción

DAG 149 (`0.69.53-dev`) con extensión `1.69.0` reproduce el código y el
comportamiento confirmado de DAG 147. DAG 148 intentó pausar la admisión visual
durante gestos, pero el propietario no percibió mejora y pidió volver atrás; se
retiraron por completo listeners, estado, mensajes, prueba y documentación de
ese experimento. Los números avanzan únicamente porque Android bloqueó instalar
`versionCode 147` sobre 148 y Gecko necesita una extensión superior para
reemplazar la 1.68 sin borrar datos.

### DAG 147 confirmado: event loop de Gecko, prioridad real y ráfagas sin negros

DAG 147 (`0.69.51-dev`) con extensión `1.67.0` está instalado y confirmado
visualmente por el propietario en el SM-S908E. El problema
del menú de Mimo no era una falta general de potencia ni una regresión de
GloshIA: el sitio sostenía un ciclo de React y su `setImmediate` basado en
`MessageChannel`, mientras el drawer de VTEX dependía de
`requestAnimationFrame`. En GeckoView, el trabajo continuo de `MessagePort`
postergaba los frames del menú después del scroll. El proceso de contenido
quedaba aproximadamente en `48-60 %` de CPU; con la corrección y la misma
secuencia de scroll/apertura quedó en tres muestras de `15,6 / 15,0 / 15,0 %`.

La corrección es general: instrumenta `MessagePort.prototype.postMessage`,
reconoce únicamente señales de scheduler numéricas o `null` sostenidas y,
recién después de `document.readyState=complete`, intercala una cesión de
`16 ms`. Mensajes normales, transferencias y la carga inicial usan el método
nativo. No contiene dominios, URLs, sitios ni dispositivos. El primer governor
experimental que no mejoró el caso se retiró por completo.

La falta o demora de fotos tenía causas separadas. Primero, un único ejecutor de
análisis formaba cola en páginas con ráfagas grandes. DAG 146 conserva dos
hilos internos intra-op y uno inter-op de ONNX Runtime, pero vuelve a dos
trabajos Android de análisis. En el A/B frío de Frávega contra DAG 145, la cola
p50/p90/p95 bajó de `147,5/192/198 ms` a `0/1/1 ms`; la vista inicial quieta,
de `19.394` a `9.240 ms`; y se procesaron `136` raster frente a `118`.

Después se aisló una cola anterior a Android en `background.js`: era FIFO,
admitía 24 análisis aunque podían existir 32 streams y sustituía silenciosamente
el excedente por un PNG negro opaco. La prioridad visible llegaba a Android
pero no gobernaba esa cola. DAG 147 amplía el límite total acotado a 48
(32 streams más 16 inline, todavía bajo el presupuesto global de 8 MiB), extrae
visible/cercano/fondo y actualiza la prioridad de trabajos pendientes. El
fallback técnico vuelve a ser transparente; un filtro real conserva su
placeholder proporcional y nunca se liberan píxeles rechazados.

La corrida física final de Frávega procesó 126 raster: 124 `model_allow` y dos
fallos técnicos mínimos (`67` y `43` bytes), con página visible en `1.277 ms`,
viewport quieto en `9.659 ms`, cola nativa p50/p90/p95 `0/2/2 ms` y jank
`4,00 %`. Cheeky procesó `98` raster con cola `0/1/2 ms`, jank `4,58 %` y sin
crash ni ANR. El propietario confirmó después que la carga y los negros
quedaron corregidos en DAG 147.

No cambiaron GloshIA Visual R3.1, su SHA-256, el umbral `0,40`, el
preprocesamiento, la política visual ni `final_sealed`. Tampoco se publicó, se
hizo push ni se tocó Supabase o Production. Evidencia, hitos e intentos
retirados:
`docs/compatibility/results/dag-browser-v147-gecko-scheduler-sm-s908e-2026-08-06.md`.

### DAG 109 local: prioridad visual y deduplicación con extensión 1.51.0

DAG 109 (`0.69.13-dev`) es la continuación local de DAG 108. La extensión
incorporada queda versionada como `1.51.0` para que GeckoView actualice los
scripts modificados. Conserva R3.1, R1 como fallback de apertura, el mismo
pipeline y la misma política; solo agrega prioridad asíncrona de raster visibles
y deduplicación de decisiones idénticas en vuelo. La matriz fría/caliente del
S22 terminó sin crash ni ANR; la evidencia está en
`docs/compatibility/results/dag-browser-v109-performance-gloshia-2026-08-05.md`.
No se publicó ni se hizo push.

El flavor `lab` no es una versión de usuario: existe únicamente para aislar la
carga visual del fixture local. El flavor DEV normal sigue en HTTPS estricto y
no acepta el endpoint HTTP del laboratorio.

### DAG 107 local confirmado: DAG 95, GloshIA R3.1 y navegacion estable

DAG 107 parte del navegador de DAG 95 y conserva R3.1 como modelo activo, con R1
como fallback si R3.1 no abre. El autoactualizador, permisos y recursos
agregados por DAG 96 permanecen retirados. La extension historica de DAG 107
era `1.50.0`;
solo cambio el archivo ONNX activo y su metadata, sin cambiar umbral,
politica ni decisiones de GloshIA.

La transicion conserva en memoria una captura de la pestaña activa ya
protegida y la muestra hasta que la pagina nueva queda segura. La captura se
invalida al cambiar de pestaña, navegar, pasar a segundo plano o liberar
memoria; no se persiste. La pagina nueva se revela solo con barrera confirmada
y una de dos señales: primer dibujo de Gecko o cola de imagenes protegidas
quieta. Esto evita el cierre incorrecto observado en Mimo cuando Gecko omite
`onFirstContentfulPaint`, sin liberar imagenes pendientes: cada raster mantiene
su compuerta individual fail-closed.

El propietario confirmo en SM-S908E que las transiciones mejoraron, la busqueda
desde DAG dejo de producir el destello previo y Mimo abre y funciona. Ktlint,
unitarios, Lint y build aprobaron. DAG 107 es el punto seguro local; DAG 96
sigue siendo la version DEV remota.

Pendiente GloshIA separado: R3 filtra incorrectamente tres banners comerciales
seguros observados en Mimo (bebe vestido, niño vestido y banner Mercado/Pagos).
El piloto humano de 40 muestras ya terminó: 39 binarias y 1 `doubt`; R1 y R3
tuvieron 0 falsos permisos y 6 falsos filtros, con accuracy 33/39 (84,62 %) y
balanced accuracy 55/60 (91,67 %) en ambos. Los seis falsos filtros de R3 se
concentraron en `retail_catalog_fashion`, incluidos maniquíes/catálogos
permitibles; no hay mejora global demostrada. Son hard negatives generales para
una candidata posterior; no crear reglas por Mimo, dominio, URL o edad y no
bajar directamente el umbral global.

El gate `GLOSHIA-R3-BALANCED-CORPUS-REVIEW-GATE-27` terminó la revisión de 295
muestras: 194 allow, 91 filter y 10 doubt. Sobre 285 binarias, R3 obtuvo 13
falsos permisos y 32 falsos filtros, balanced accuracy 84,61 % y PR-AUC
0,835803. El lote sirve para justificar la preparación de TRAIN-28, pero no
para entrenar todavía: quedó en `directed_review`, sin splits independientes,
con `training_rights_uncertain` y sin autorización de entrenamiento. La
recomendación es `GO` condicionado para proponer TRAIN-28 y `NO-GO` para usar
este lote directamente. `final_sealed` sigue cerrado.
Evidencia: `docs/dag/v3/GLOSHIA_R3_BALANCED_CORPUS_REVIEW_GATE_27_2026-08-04.md`.

La propuesta `GLOSHIA-R3-TRAIN-28` definió un pool nuevo aproximado de 400
muestras con splits agrupados y el examen gate 27 congelado como referencia
externa. Fue autorizada y ejecutada localmente; el cierre fue `NO-GO`.
Evidencia: `docs/dag/v3/GLOSHIA_R3_TRAIN_28_PROPOSAL_2026-08-04.md`.

TRAIN-28 fue ejecutado localmente y terminó `NO-GO`: 193 muestras útiles, 190
binarias, 3 dudas, 133/28/29 en train/validation/frozen_test y contaminación
aprobada. Los tres ensayos R3.1 no redujeron los falsos filtros de R3 en
validation; no se abrió el frozen_test de candidato, no se exportó ONNX y R3
permanece oficial. Evidencia:
`docs/dag/v3/GLOSHIA_R3_TRAIN_28_NO_GO_2026-08-04.md`.

El piloto privado `GLOSHIA-R3-HARD-NEGATIVE-REPAIR-PILOT-29` terminó con
234 binarias nuevas de round29, 36 hard cases ponderados y splits
`367/28/29` train/validation/frozen_test sin contaminación. El mejor candidato
visual FP32 redujo los falsos filtros de R3 de `3/15` a `1/15` en frozen_test,
con balanced accuracy `93,10 %` frente a `86,43 %` y PR-AUC `0,957952` frente a
`0,943326`, manteniendo `1/14` falsos permisos. Sin embargo, FP32 pesa
33.220.815 bytes frente a 10.469.698 de R3; INT8 dinámico falla con
`ConvInteger`, QDQ/QLinearOps agregan falsos permisos y las variantes híbridas
cambian decisiones fronterizas. Resultado obligatorio: `NO-GO` para canary o
reemplazo; R3 permanece oficial, `final_sealed` sigue cerrado y no se modificó
DAG 107. Evidencia:
`docs/dag/v3/GLOSHIA_R3_HARD_NEGATIVE_REPAIR_29_2026-08-05.md`.

El experimento privado `GLOSHIA-R3-ROUND30-BINARY-CANDIDATE` incorporó 251
decisiones binarias nuevas de round30 al train, excluyendo 4 `doubt`, y
preservó exactamente el examen histórico: 618 train, 28 validation y 29
frozen_test. La prueba de contaminación pasó para ID, SHA-256, pHash, grupo y
URL; `final_sealed` permaneció cerrado. Se ejecutaron tres pilotos CPU locales
con selección únicamente por validation. El piloto 03 fue el seleccionado:
validation mejoró de 3/21 a 1/21 falsos filtros y frozen_test de 2/15 a 1/15,
manteniendo 1/7 y 1/14 falsos permisos, respectivamente. Balanced accuracy y
PR-AUC también subieron en ambos exámenes.

La mejora visual no es todavía desplegable: el FP32 abre con ONNX checker y
ORT CPU pero pesa 33.220.815 bytes frente a 10.469.698 de R3; el INT8 dinámico
contiene `ConvInteger` y no abre en ORT CPU; la variante híbrida compacta cambia
2 de 57 decisiones frente a FP32. Resultado `NO-GO`: R3 continúa oficial e
intacto en DAG 107, sin APK, Android, umbral, política, Supabase, publicación
ni push. Informe reproducible:
`.codex-tmp/gloshia-r3-round30-binary-candidate-20260805/round30-binary-candidate-report.json`.

Un seguimiento de exportación encontró una candidata híbrida INT8 compacta del
mismo piloto: `9.668.603` bytes, SHA-256
`c8b64af8092d3718c58736a511c996d0d443dacf3eaa74620b1e5af439a3cd48`. Abre en
ORT CPU local y, contra el R3 oficial actual, mantiene los falsos permisos en
`1/7` validation y `1/14` frozen_test mientras reduce falsos filtros de `3/21`
a `2/21` y de `2/15` a `0/15`. Es `GO` condicionado para un harness Android
aislado; no está integrado ni aprobado para DAG. Evidencia:
`docs/dag/v3/GLOSHIA_R3_ROUND30_HYBRID_EXPORT_GATE_2026-08-05.md`.

El ticket `GLOSHIA-R3.2-DIRECTED-REPAIR-TRAIN-10` terminó `NO-GO`. El split
privado agrupado quedó en `642/28/29` train/validation/frozen_test, con seed
`3201`; la contaminación detectada por pHash 0 y URL compartida fue excluida
y la prueba final pasó. El ensayo 03 redujo validation de `2/21` a `1/21`
falsos filtros manteniendo `1/7` falsos permisos, pero en `frozen_test` empató
exactamente a R3.1: `1/14` falsos permisos, `0/15` falsos filtros, balanced
accuracy `96,43 %` y PR-AUC `0,963054`. Su FP32 abre en ORT CPU pero pesa
`33.220.815` bytes; su INT8 dinámico pesa `8.735.126` bytes, contiene
`ConvInteger` y falla con `NOT_IMPLEMENTED` en ORT CPU. R3.1 continúa oficial,
`final_sealed` cerrado y no se modificaron DAG, Android, APK, umbral, política,
Supabase, Production ni GitHub. Evidencia:
`docs/dag/v3/GLOSHIA_R3_2_DIRECTED_REPAIR_TRAIN_2026-08-05.md`.

El piloto local `GLOSHIA-R4-THUMBNAIL-REPAIR` confirmó una regresión general de
R3.1 ante miniaturas: sobre 7 positivos de validation detectó 6 originales,
pero solo 2 variantes con máscara elíptica contenida; hubo 8 degradaciones peligrosas `filter` a
`allow` en 84 pares. Se generaron 360 variantes train y 84 validation,
agrupadas sin contaminación; `frozen_test` y `final_sealed` permanecieron
cerrados. Dos fine-tunes conservadores redujeron falsos permisos globales de
12/28 a 11/28, pero elevaron falsos filtros de 5/84 a 7/84 y 10/84, sin
corregir ninguna variante enmascarada peligrosa. Un entrenador posterior dio
el mismo peso a cada familia, ancló originales al ONNX R3.1 oficial e impuso
consistencia explícita. Redujo degradaciones peligrosas de 8 a 4, pero dejó
5/7 falsos permisos enmascarados, creó 2/21 falsos filtros y empeoró originales
de 1/7 a 2/7 falsos permisos; también queda `NO-GO`. La transformación histórica
`circle128_q45` preserva aspecto y genera elipses, no un recorte circular real.
Próximo paso: generar recortes cuadrados centrados y revisar una cola humana
mínima antes de otro entrenamiento; no más ajustes de peso. No se exportó ni
integró modelo y R3.1 continúa oficial. Evidencia:
`docs/dag/v3/GLOSHIA_R4_THUMBNAIL_REPAIR_PILOT_2026-08-09.md`.

La cola mínima ya fue preparada: 642 recortes train cuadrados/circulares reales
se puntuaron localmente y se redujeron a 24 casos informativos, balanceados
12/12 por etiqueta padre, bajo
`.codex-tmp/gloshia-r4-thumbnail-repair-20260809/circle-review-01/`. La
inspección confirmó que varios recortes eliminan la señal o persona original y
otros la enfatizan; por eso el manifiesto es solo `review_only`, no contiene
`target` entrenable y conserva `training_authorized: false`. Falta la revisión
humana de esas 24 vistas antes de cualquier nuevo entrenamiento.

La revisión humana terminó: 10 allow, 11 filter y 3 doubt excluidas. El
propietario confirmó política privada desde aproximadamente 5 años o edad
incierta para presentación femenina, sin aplicar esas señales automáticamente
a presentación masculina; el escote no necesita ser grande/profundo y una
persona relevante no evaluable en miniatura queda fail-closed. En las 21
binarias R3.1 produce 5/10 falsos filtros y 6/11 falsos permisos. El pool
autorizado quedó en
`.codex-tmp/gloshia-r4-thumbnail-repair-20260809/circle-review-01/reviewed-pool.json`,
todavía sin split y no aprobado para ONNX/APK. El contraste solicitado con R1
descartó un rollback: sobre esas 21 binarias R1 estimó 7/10 falsos filtros y
6/11 falsos permisos, frente a 5/10 y 6/11 de R3.1. Dos salidas R1 son
limítrofes por la compatibilidad ORT de macOS, pero aun si ambas se corrigieran
en Android R1 solo empataría a R3.1. En las 112 vistas validation R1 redujo
falsos permisos estimados de 12 a 9, pero elevó falsos filtros de 5 a 26. R3.1
permanece oficial; R1 queda solo como comparador de desacuerdos. Próximo paso:
A/B cruzado por grupos para un candidato nuevo, con gates congelados para
miniaturas, cuerpos/escotes y fotos completas.

El A/B agrupado ya se ejecutó y ambos candidatos quedaron `NO-GO`. Los 21
recortes forman 21 grupos únicos y 10 cambian legítimamente de etiqueta frente
a su foto padre, causa que invalida la consistencia automática anterior. Una
cabeza lineal redujo OOF de 6 a 4 falsos permisos, pero subió falsos filtros de
5 a 6 y deterioró fotos completas en los cinco folds. Un ajuste mínimo de la
última capa visual preservó fotos completas, redujo falsos permisos sintéticos
de 11 a 9 y degradaciones peligrosas de 8 a 6, pero creó un falso filtro
circular y no generalizó: OOF quedó exactamente en 6/11 falsos permisos y 5/10
falsos filtros. No se exportó ni integró modelo. Se preparó `circle-review-02`
con 24 casos nuevos de 621, excluyendo los 21 grupos previos; falta una única
revisión del propietario sobre `review-sheet.png` antes de cualquier otro
entrenamiento.

La segunda revisión ya terminó. El propietario aceptó la propuesta asistida y
corrigió el caso 17 a `filter` porque son piernas de mujer. Quedaron 8 allow,
13 filter y 3 doubt; la unión tiene 42 grupos claros (18/24) y seis dudas
excluidas. R3.1 obtuvo 16/24 falsos permisos y 11/18 falsos filtros. La cabeza
lineal redujo falsos permisos OOF a 10, pero subió falsos filtros a 13 y dañó
validation. El ajuste visual quedó exactamente igual que R3.1 fuera de muestra
(16/11), por lo que ambos son `NO-GO`; sus checkpoints fueron eliminados. No
seguir variando pesos. El próximo paso propuesto es un corpus semántico dirigido
de piernas femeninas, escotes, hombros, sujetos pequeños y negativos cercanos,
preetiquetado de forma asistida y agrupado por identidad visual/origen. No se
modificó R3.1, Android, umbral, política, APK ni `final_sealed`.

El propietario autorizó el corpus semántico dirigido. Se congeló un plan de
120 vistas: 36 ropa moderna, 30 deportes/sensibles, 18 personas comerciales,
16 sujetos parciales/pequeños y 20 negativos cercanos. Tras excluir 45 grupos
ya revisados, R3.1 puntuó 597 recortes y se seleccionaron 120 grupos únicos,
repartidos en cinco tandas balanceadas de 24 bajo `targeted-review-v1/`. La
revisión terminó en 59 allow y 61 filter, sin dudas ni grupos repetidos. R3.1
obtuvo 15/61 falsos permisos y 43/59 falsos filtros; los errores aparecen en
las cinco tandas y no admiten una corrección simultánea moviendo el umbral. El
propietario autorizó el A/B privado y se unieron esos casos con los 42 previos:
162 grupos, 77 allow y 85 filter. R3.1 quedó en 31 falsos permisos y 54 falsos
filtros. El único ajuste cruzado de representación quedó `NO-GO`: OOF 27/54,
frente a gates 24/43, y un fold empeoró fotos completas a 2/7 falsos permisos.
Los cinco checkpoints fueron eliminados. No repetir pesos sobre la misma última
capa; el próximo paso requiere estudiar una representación compacta distinta.
R3.1, Android, APK, `frozen_test` y `final_sealed` permanecen intactos.

Por decisión posterior del propietario se ejecutó además un único A/B limpio
con la receta original de R1: TinyCLIP preentrenado, cabeza logística nueva y
ocho épocas acotadas de última capa/proyección/cabeza, sin cargar R3.1, sin
destilación, sin pesos especiales y excluyendo las 360 variantes automáticas de
train. OOF sobre los mismos 162 grupos quedó en 9 falsos permisos y 56 falsos
filtros. Redujo fuertemente los permisos peligrosos frente a R3.1 (`31/54`),
pero agregó dos bloqueos inocentes, falló el gate `24/43` y los cinco folds
degradaron las fotos completas a 2/7 falsos permisos. El R1 oficial compatible
de laboratorio estimó `30/57` en los mismos casos. Resultado `NO-GO`: los seis
checkpoints, incluido el piloto, y el entrenador específico fueron retirados;
se conservaron sólo informes y splits reproducibles. R3.1 continúa oficial y
no se abrieron `frozen_test` ni `final_sealed`.

Se preparó un APK local aislado para probar redacción parcial de imágenes bajo
el ticket experimental `GLOSHIA-PARTIAL-REDACTION-LAB`. Sólo el flavor
`com.contentfilter.dagbrowser.lab` usa las vistas regionales existentes para
aplicar un difuminado fuerte tipo vidrio cuando hay una única región moderada.
En LAB las vistas también se generan para proporciones normales; DEV conserva
la optimización anterior y no añade esa carga regional.
varias regiones, riesgo global alto o cualquier error bloquean la imagen
completa. R3.1, el flavor DEV y el flujo oficial `allow/block` permanecen sin
cambios. El APK lab es `versionCode 111`, `0.69.13-lab`, SHA-256
`a6bef885002da151270690087da1bc9e6fb739411e15f4cbcf6bc2d81d67b586`; fue
instalado sólo en el S22 para validación local y no fue publicado ni subido.
La prueba real observó decisiones `redact` y reemplazos PNG. Evidencia:
`docs/dag/v3/GLOSHIA_PARTIAL_REDACTION_LAB_2026-08-05.md`.

### DAG 96 publicado en DEV: canary reversible de GloshIA R3

DAG 96 conserva sin cambios el pipeline de navegador y la extension `1.50.0`
de DAG 95. Cambia solamente el modelo activo a R3 hibrida, mantiene el umbral
`0,40` y conserva R1 como fallback si ORT no puede abrir R3. Tambien agrega la
actualizacion manual propia con verificacion de HTTPS, SHA-256, package name y
firma. Evidencia:
`docs/dag/v3/GLOSHIA_R3_REVERSIBLE_DEV_CANARY_25_2026-08-03.md`.

### Base heredada de DAG 95: rollback confirmado

DAG 95 revierte completamente el cambio funcional de DAG 94 y restaura el
pipeline de imagenes de DAG 92, ultimo punto aceptado por el usuario. Android no
permite instalar un `versionCode` menor sobre 94; por eso el rollback se publica
como 95 aunque su comportamiento vuelva a 92. GloshIA R1, sus pesos, umbrales y
decisiones no cambiaron.

DAG 94 queda retirado como version vigente. Su informe se conserva solamente
como evidencia historica y esta marcado `REVERTIDO`: la mejora de laboratorio
no resolvio el comportamiento observado por el usuario. Extension incorporada
en DAG 95: `1.50.0`. Evidencia actual:
`docs/compatibility/results/dag-browser-v95-rollback-sm-a235m-2026-08-03.md`.

DAG 96 conserva la unica compuerta GloshIA previa al render. Los bytes raster se
capturan una vez y solo `model_allow` devuelve el original exacto. Filtro,
error, timeout, saturacion, animacion o entrada invalida producen un PNG neutro
sin pixeles rechazados. SVG e iconos vectoriales seguros quedan fuera de la
espera visual.

La presentacion vigente es global:

- `data:` y `blob:` quedan neutrales desde `document_start`;
- cualquier respuesta con MIME raster cruza la compuerta, incluso por
  `fetch`/XHR;
- un `img` HTTP(S) completo se revela sin espera artificial (`0 ms`);
- una imagen HTTP(S) ya estable no se vuelve a ocultar cuando el sitio rota su
  fuente; los nuevos bytes siguen retenidos antes del render;
- una fuente inline `data:`/`blob:` se vuelve a cerrar porque no atraviesa
  `webRequest`;
- el observador no reescribe fuentes, no decide contenido, no trabaja durante
  scroll y no contiene excepciones por sitio o telefono.

En un refresh del mismo documento DAG mantiene visible la pagina ya protegida
mientras espera la nueva barrera. Primera carga, URL distinta o fallo siguen
cerrados por la cobertura total. Extension incorporada: `1.50.0`.

Los `data:image` raster visibles que parecen controles o iconos ya no quedan
ocultos para siempre: si pesan hasta 48 KiB, tienen bordes naturales de hasta
128 px, se muestran en hasta 96 px y entran dentro de un maximo de 16 fuentes
unicas por documento, atraviesan la misma compuerta local. Solo `model_allow`
los revela. Rechazo, SVG inline, `blob:`, exceso, formato invalido, timeout o
fuente cambiada permanecen cerrados. La decision se deduplica por contenido y
no hay reglas por Google, comercio o telefono.

La interfaz sustituye la transicion azul por fondo blanco y una barra fina de
progreso. Los sprites PNG pasivos, extremos, transparentes y de pocos colores
pueden sanitizarse sin liberar fotografias ni crear excepciones por comercio.
La interfaz de DAG 95 conserva el boton de nueva pagina blanco, miniaturas
visibles en pestañas e historial, iconos en el menu y Descargas en pantalla
completa. La barra superior queda fija durante el desplazamiento: se retiro el
ocultamiento que cambiaba la altura util y producia un salto visible.
El bloqueo de anuncios por red sigue vigente, pero ya no existe un
`MutationObserver` global que recorra cada cambio de todas las paginas: los
selectores explicitos se revisan una vez y la busqueda textual exacta se limita
a documentos con ruta o parametros de buscador. Gecko activa marcado paralelo
del recolector mediante su ajuste oficial de rendimiento.

Limites vigentes: 2 MiB por recurso, 8 MiB capturados, 32 streams, cola de 24,
dos inferencias nativas y cache efimera de 512 hashes. Video, audio, canvas,
object y embed permanecen bloqueados por contratos separados.

## Validacion y artefacto

- 14 pruebas WebExtension y las unitarias Kotlin aprobadas; DAG 95 repitio
  Ktlint, Lint y build.
- Ktlint, Lint, build y `git diff --check` correctos.
- En Mimo, la interaccion temprana del menu paso de `26,7 fps` en la base y
  `21,8 fps` en un candidato descartado a `47,8-48,6 fps` en dos repeticiones
  DAG 86. Chrome dio `62,5 fps` en la misma accion y equipo; queda una brecha
  inicial de Gecko y algun cuadro largo aislado.
- Ya asentado, el carrusel de Mimo midio `55,2-56,2 fps`; menu y expansion de
  categoria midieron aproximadamente `50,2-54,5 fps`. No hubo inferencias de
  GloshIA durante esas interacciones.
- Fravega fue visible en `1.657 ms`, termino pagina en `6.949 ms` y fotos
  iniciales en `7.722 ms`; frente a DAG 78 observado, pagina termino 13,5 % y
  fotos 21,2 % antes, con visibilidad equivalente (`+0,8 %`).
- Cheeky fue visible en `2.024 ms` y termino pagina en `4.577 ms`; el evento de
  fotos fue prematuro y no se usa como resultado. Frente a DAG 78 observado,
  visibilidad fue 10,5 % mas lenta y fin de pagina 11 % mas rapido.
- Google busqueda se recorrio despues de retirar el observador; no mostro
  rotulos `Patrocinado` ni crashes. Los resultados comerciales sin ese rotulo
  no se clasificaron como anuncios por su apariencia.
- Sin crash, ANR ni OOM en Mimo, Cheeky o Fravega. El fixture HTTPS
  autofirmado sigue bloqueado por TLS y no se conto.
- En el A23, DAG 86 reprodujo el hueco del favicon de Fravega. DAG 87 mostro
  los favicons de Fravega, Moov y Sporting y las miniaturas de filtros rapidos
  de Google, todos despues de la decision local.
- Matriz DAG 87/A23: Mimo `2.975 / 3.068 / 273 ms`, Cheeky
  `10.299 / 11.262 / 2.803 ms` y Fravega `20.024 / no completo en 20 s /
  1.187 ms`. Mimo abrio su menu completo; no se observó una regresion general.
- Google Imagenes: el usuario confirmo el raster en `0 ms` sin escape de
  contenido rechazado y el refresh sin apagado general.
- Antes del arreglo, dos rafagas reprodujeron tres cuadros negros consecutivos;
  la segunda toma cronometrada estimo aproximadamente 1,2 segundos de cobertura
  total. La causa era el estado Android `Loading`, no GloshIA.
- DAG 86 no cambia pesos, umbrales ni la compuerta de bytes de DAG 68; la
  matriz nueva evalua presentacion, carga e interaccion dinamica.

APK local:

- ruta: `app-dag-browser/build/outputs/apk/dev/debug/DagBrowser-dev-debug.apk`;
- tamaño: `129945445` bytes;
- SHA-256:
  `0bdfb98cee3b3d7693f8a6d110321f75578209427ce46d835ece2cee6a0b2c9e`.

Evidencia del punto local vigente:
`docs/compatibility/results/dag-browser-v107-safe-navigation-sm-s908e-2026-08-04.md`.
Contrato vigente: `docs/dag/v3/DAG_BROWSER_V3_IMAGE_PIPELINE.md`.

## Metricas

- `page_visible`: la estructura protegida ya puede usarse.
- `viewport_images_ready`: termino el trabajo visual de la ventana inicial y
  permanecio quieto 250 ms; no representa toda una pagina infinita.
- `page_analysis_ready`: `GeckoSession.onPageStop`; mide el ciclo de pagina y
  texto, no la inferencia de GloshIA.

Definicion: `docs/dag/v3/DAG_BROWSER_V3_PERFORMANCE_METRICS.md`.

## Estado de GloshIA visual

- Hay un unico modelo local; DAG 95 no cambia sus pesos ni umbrales.
- El experimento local autorizado `GLOSHIA-VISUAL-CANDIDATE-TRAIN-08` produjo
  R2 Candidate 01 con 203 train, 47 validation y 72 frozen_test, agrupados sin
  cruces de hashes ni clusters.
- R2 mejoró validation y redujo falsos filtros en frozen_test, pero introdujo
  un falso permiso nuevo (`1/10` frente a `0/10` de R1) y la cuantización no
  pudo abrirse con el ORT Python local. Resultado `NO-GO`; R1 permanece intacto.
- `final_sealed` sigue cerrado. No se autoriza canary Android hasta que un
  candidato posterior pase seguridad, cuantización y rendimiento.
- `GLOSHIA-VISUAL-R2-HARD-NEGATIVE-REPAIR-09` (2026-08-02): se preparó un
  lote privado de 50 imágenes públicas independientes, 25/25 en estratos de
  muestreo filter-like/allow-like. Los estratos no son etiquetas. La revisión
  terminó con 26 filter, 23
  allow y 1 doubt. Sobre las 49 binarias, R1 tuvo 8/26 falsos permisos y 7/23
  falsos filtros, balanced accuracy 69,40% y PR-AUC 79,46%; en el estrato
  filter-like hubo 7/18 falsos permisos. El servidor local fue
  `http://127.0.0.1:8770/`; `final_sealed` sigue cerrado. Evidencia:
  `docs/dag/v3/GLOSHIA_VISUAL_R2_HARD_NEGATIVE_REPAIR_09_2026-08-02.md`.
- `GLOSHIA-VISUAL-R2.1-HARD-NEGATIVE-TRAIN-10` (2026-08-03): la ronda nueva
  quedó autorizada por el propietario para un experimento privado local:
  `owner_authorized_private_experiment`. Las 49 binarias pasaron a train y se
  mantuvieron validation (47) y frozen_test (72) independientes; no se declaró
  `training_rights_clear`. R2.1 FP32 redujo los falsos filtros de 44/101 a
  12/101 y no agregó falsos permisos, pero su exportación INT8 no abrió con
  ONNX Runtime CPU local por `ConvInteger`; por el gate obligatorio el resultado
  es `NO-GO`. R1, `final_sealed`, Android y DAG permanecen intactos. Evidencia:
  `docs/dag/v3/GLOSHIA_VISUAL_R2_1_HARD_NEGATIVE_TRAIN_10_2026-08-03.md`.
- `GLOSHIA-VISUAL-R2.1-ANDROID-EXPORT-GATE-11` (2026-08-03): se probaron
  QDQ INT8, QLinearOps INT8, FP16 y FP32 optimizado desde el FP32 congelado,
  con calibración sólo en train. QDQ/QLinearOps abren en ORT Python pero
  agregan falsos permisos; FP16 produce no finitos; FP32 optimizado conserva
  decisiones pero aumenta aproximadamente 24,5 MB y no tuvo ejecución directa
  en Android. Resultado `NO-GO`; R1, `final_sealed`, Android y DAG permanecen
  intactos. Evidencia:
  `docs/dag/v3/GLOSHIA_VISUAL_R2_1_ANDROID_EXPORT_GATE_11_2026-08-03.md`.
- `GLOSHIA-R2.1-ORT-ANDROID-HARNESS-12` (2026-08-03): el candidato INT8
  dinámico exacto de EXPORT-GATE-11 abrió y ejecutó `ConvInteger` directamente
  con ORT Android 1.27.0 CPU en el Samsung A23 (SM-A235M, Android 14), con
  salidas finitas y cierre de sesión correcto. Sobre los mismos 119 tensores
  congelados tuvo 0/18 falsos permisos, 11/101 falsos filtros y un desacuerdo
  de decisión frente a FP32 (1/119, en dirección de falso filtro de FP32 a
  allow). La tolerancia de equivalencia quedó fijada en cero, por lo que el
  resultado es `NO-GO` para canary aunque la compatibilidad de ConvInteger haya
  sido confirmada. R1, `final_sealed`, Android productivo y DAG permanecen
  intactos. Evidencia:
  `docs/dag/v3/GLOSHIA_R2_1_ORT_ANDROID_HARNESS_12_2026-08-03.md`.
- `GLOSHIA-R2.1-ANDROID-CROSS-DEVICE-GATE-13` (2026-08-03): el mismo APK,
  candidato y examen congelado se ejecutaron en el Samsung S22 Ultra
  (SM-S908E, Android 16). Las 119 probabilidades y decisiones del candidato
  fueron idénticas a las del A23: 0/18 falsos permisos, 11/101 falsos filtros y
  el mismo desacuerdo favorable frente a FP32. La latencia observada fue p50
  47,75 ms y p95 63,89 ms. Resultado: `GO` de compatibilidad entre dispositivos
  y `HOLD` para integración o apertura de `final_sealed` hasta congelar el
  criterio final de aceptación. El harness fue retirado del S22; R1 y DAG
  permanecen intactos. Evidencia:
  `docs/dag/v3/GLOSHIA_R2_1_ANDROID_CROSS_DEVICE_GATE_13_2026-08-03.md`.
- `GLOSHIA-R2.1-FINAL-SEALED-GATE-14` (2026-08-03): después de congelar el
  candidato, umbral, membresía y gates en el commit `0f18c86`, se abrió una
  única vez el examen final de 108 muestras. Hubo 77 allow, 30 filter y 1 doubt.
  R2.1 redujo falsos filtros de 24/77 a 8/77 y subió accuracy de 73,83 % a
  85,05 %, pero aumentó falsos permisos de 4/30 a 8/30 y redujo recall de
  `filter` de 86,67 % a 73,33 %. Resultado obligatorio: `NO-GO`; R1 continúa
  oficial. El examen queda consumido y no puede usarse para entrenamiento,
  calibración ni un nuevo gate desconocido. Evidencia:
  `docs/dag/v3/GLOSHIA_R2_1_FINAL_SEALED_GATE_14_2026-08-03.md`.
- `GLOSHIA-VISUAL-R2.2-TARGETED-REPAIR-15` y
  `GLOSHIA-R2.2-ANDROID-HARNESS-16` (2026-08-03): la auditoría confirmó que
  cuatro falsos permisos R2.1 eran equipos femeninos de ciclismo ocultos bajo
  la categoría genérica de grupos. R2.2 B, entrenada con 44 preetiquetas
  binarias nuevas y seis dudas excluidas, corrigió las cuatro escenas. En un
  holdout ciego nuevo de 40 muestras mantuvo 1 falso permiso como R1 y redujo
  falsos filtros de 5 a 3, con accuracy 90 % frente a 85 % de R1. El INT8
  exacto abrió en A23 y tuvo 0 falsos permisos sobre 119 tensores, pero agregó
  un falso filtro frente a FP32; el gate previo exigía equivalencia cero.
  Resultado: `NO-GO` técnico para integración; R1 y DAG 95 siguen oficiales.
  Evidencia:
  `docs/dag/v3/GLOSHIA_VISUAL_R2_2_TARGETED_REPAIR_2026-08-03.md`.
- `GLOSHIA-R2.2-EXPORT-EQUIVALENCE-17` (2026-08-03): sin reentrenar ni mover
  el umbral, se dejó en FP32 únicamente el `k_proj` del primer bloque y se
  mantuvo el resto de la cuantización dinámica. El artefacto selectivo pesa
  8.950.584 bytes, se reproduce byte por byte y pasó 119/119 decisiones frente
  a FP32 en A23 y S22, con 0 falsos permisos y salidas finitas. Latencia p50:
  323,62 ms en A23 y 35,65 ms en S22, comparable con R1 en las mismas sesiones.
  Estado: `GO` de exportación y compatibilidad; canary productivo aún pendiente.
  R1 y DAG 95 continúan oficiales. Evidencia:
  `docs/dag/v3/GLOSHIA_R2_2_EXPORT_EQUIVALENCE_17_2026-08-03.md`.
- `GLOSHIA-R2.2-REVERSIBLE-CANARY-18` (2026-08-03): el candidato selectivo se
  ejecuto en el S22 sobre 40 imagenes reales mediante la decodificacion,
  preprocesamiento, regiones y politica exactos de DAG. R1 y R2.2 acertaron
  34/40; R2.2 redujo falsos filtros de 4 a 3, pero aumento falsos permisos de 2
  a 3 y bajo el recall de `filter` de 83,33 % a 75,00 %. La politica p95 fue
  comparable (181,42 ms frente a 184,96 ms) y no hubo errores, pero el gate de
  seguridad obliga `NO-GO`. El APK, modelo e imagenes de laboratorio fueron
  retirados; R1 y DAG 95 siguen oficiales. Evidencia:
  `docs/dag/v3/GLOSHIA_R2_2_REVERSIBLE_CANARY_18_2026-08-03.md`.
- `GLOSHIA-R2.3-REGIONAL-SAFETY-REPAIR-19` (2026-08-03): se adquirieron 57
  escenas grupales independientes y, antes de entrenar, se fijo que personas
  diminutas o lejanas tipo "buscar a Wally" no deben provocar filtrado. Las
  preetiquetas quedaron 41 `allow`, 14 `filter` y 2 `doubt`. R2.3 B mantuvo
  0/10 falsos permisos en frozen_test, pero subio falsos filtros de 11 a 12.
  En un holdout regional nuevo repitio exactamente a R2.2: 2/4 falsos permisos
  y 2/10 falsos filtros, con balanced accuracy 65 %. Resultado `NO-GO`; no se
  exporto a Android ni se toco DAG. R1 sigue oficial. Evidencia:
  `docs/dag/v3/GLOSHIA_R2_3_REGIONAL_SAFETY_REPAIR_19_2026-08-03.md`.
- `GLOSHIA-R2.4-REGION-AWARE-TRAINING-GATE-20` (2026-08-03): se alineo el
  entrenamiento del mismo TinyCLIP con la topologia y los umbrales regionales
  de DAG. La candidata A mantuvo 0 falsos permisos y redujo falsos
  filtros de 20 a 16 en validation y de 24 a 18 en frozen_test. Luego se abrio
  un holdout nuevo de 40 casos binarios, deduplicado y sin series repetidas:
  R1 obtuvo 0/16 falsos permisos y 14/24 falsos filtros; R2.4 obtuvo 2/16 y
  11/24. Resultado obligatorio `NO-GO`; el examen queda consumido, R1 sigue
  oficial y no se toco Android. Evidencia:
  `docs/dag/v3/GLOSHIA_R2_4_REGION_AWARE_TRAINING_GATE_20_2026-08-03.md`.
- `GLOSHIA-R3-MULTI-SIGNAL-DATA-CONTRACT-21` (2026-08-03): se recuperaron 176
  revisiones historicas utilizables como etiquetas parciales para diez motivos.
  Las 77 decisiones `allow` aportan negativos; en las 99 decisiones de filtro
  solo los motivos marcados son positivos y los omitidos permanecen
  desconocidos. Escote/pecho, hombro/axila y codo alcanzan el piso piloto; las
  otras siete senales todavia no. Estado: `GO` para reetiquetado focalizado y
  `NO-GO` para entrenar. R1 continua oficial. Evidencia:
  `docs/dag/v3/GLOSHIA_R3_MULTISIGNAL_DATA_CONTRACT_21_2026-08-03.md`.
- `GLOSHIA-R3-FOCUSED-RELABEL-22` (2026-08-03): se completaron 88/88
  revisiones focalizadas y se resolvieron todas sus senales desconocidas. El
  propietario corrigio las revisiones 17, 36 y 83 a `allow`; el cierre queda
  en 85 `filter` y 3 `allow`. Las tres correcciones deben conservarse para
  reducir filtros de mas. La exportacion privada fue verificada, el enlace
  vencido y las 88 fotos temporales retiradas de Supabase. Estado: `GO` para
  preparar una candidata R3 balanceada y `NO-GO` para reemplazar R1. Evidencia:
  `docs/dag/v3/GLOSHIA_R3_FOCUSED_RELABEL_22_2026-08-03.md`.
- `GLOSHIA-R3-BOUNDED-HEAD-TRAIN-23` (2026-08-03): se entreno `R3 Head 01`
  con 407 muestras, preservando como `allow` las revisiones 17, 36 y 83. En
  validation redujo falsos filtros de 19 a 3 y en frozen test de 25 a 6, con
  cero falsos permisos en ambos. El INT8 selectivo mide 8.950.584 bytes; queda
  pendiente equivalencia y rendimiento en Android. Estado: `GO` de laboratorio,
  R1 continua oficial. Evidencia:
  `docs/dag/v3/GLOSHIA_R3_BOUNDED_HEAD_TRAIN_23_2026-08-03.md`.
- `GLOSHIA-R3-ANDROID-EQUIVALENCE-24` (2026-08-03): ejecutado en S22. INT8
  dinámico tuvo un falso permiso; FP32 fue exacto pero demasiado lento. La
  exportación híbrida de 10,47 MB quedó con 0 falsos permisos, 10 falsos
  filtros frente a 42 de R1 y p50 186,25 ms frente a 188,18 ms. Una única
  diferencia contra FP32 fue conservadora (`allow` a `filter`). Estado:
  `CONDITIONAL-GO` para repetir en A23; todavía no integrar en DAG. El APK y
  temporales fueron retirados del S22. No se tocó DAG.
  Evidencia:
  `docs/dag/v3/GLOSHIA_R3_ANDROID_EQUIVALENCE_24_PREP_2026-08-03.md`.
- `GLOSHIA-R3-COMMERCIAL-HARD-NEGATIVES-26` (2026-08-04): se ejecutó el
  diagnóstico de datos sin entrenar. La búsqueda pública devolvió material
  histórico para consultas modernas; 25 candidatos quedaron en cuarentena por
  actualidad primaria no demostrada y 1 variante se excluyó por pHash canónico.
  Quedaron 40 muestras evaluables de Wikimedia Commons, 26 clusters, todas
  `internal_evaluation_ok` pero `training_rights_uncertain`. La revisión humana
  terminó con 36 allow, 3 filter y 1 doubt. Sobre las 39 binarias, R1 y R3
  tuvieron la misma matriz: 0 falsos permisos y 6 falsos filtros; el error de
  R3 se concentró en catálogos de moda/maniquíes. Estado: `GO` para cerrar el
  diagnóstico y `NO-GO` para entrenamiento; `final_sealed` permanece cerrado.
  El siguiente paso requiere un lote independiente y balanceado, no reutilizar
  este examen como evaluación y no ajustar umbrales. Evidencia:
  `docs/dag/v3/GLOSHIA_R3_COMMERCIAL_HARD_NEGATIVES_26_DIAGNOSTIC_2026-08-04.md`.

Documentos vigentes:

- `docs/dag/v3/DAG_BROWSER_V3_FOUNDATION.md`;
- `docs/dag/v3/DAG_BROWSER_V3_IMAGE_PIPELINE.md`;
- `docs/dag/v3/DAG_BROWSER_V3_MODEL_DATASET_CONTRACT.md`;
- `docs/dag/v3/GLOSHIA_LAB_CALIBRATION_2026-07-31.md`;
- `docs/dag/v3/GLOSHIA_VISUAL_CANDIDATE_TRAIN_08_2026-08-02.md`;
- `docs/dag/v3/GLOSHIA_VISUAL_R2_HARD_NEGATIVE_REPAIR_09_2026-08-02.md`;
- `docs/dag/v3/GLOSHIA_VISUAL_R2_1_HARD_NEGATIVE_TRAIN_10_2026-08-03.md`;
- `docs/dag/v3/GLOSHIA_VISUAL_R2_1_ANDROID_EXPORT_GATE_11_2026-08-03.md`;
- `docs/dag/v3/GLOSHIA_R2_1_ORT_ANDROID_HARNESS_12_2026-08-03.md`;
- `docs/dag/v3/GLOSHIA_R2_1_ANDROID_CROSS_DEVICE_GATE_13_2026-08-03.md`;
- `docs/dag/v3/GLOSHIA_R2_1_FINAL_SEALED_GATE_14_FREEZE_2026-08-03.md`;
- `docs/dag/v3/GLOSHIA_R2_1_FINAL_SEALED_GATE_14_2026-08-03.md`;
- `docs/dag/v3/GLOSHIA_VISUAL_R2_2_TARGETED_REPAIR_2026-08-03.md`;
- `docs/dag/v3/GLOSHIA_R2_2_EXPORT_EQUIVALENCE_17_2026-08-03.md`;
- `docs/dag/v3/GLOSHIA_R2_2_REVERSIBLE_CANARY_18_2026-08-03.md`;
- `docs/dag/v3/GLOSHIA_R2_3_REGIONAL_SAFETY_REPAIR_19_2026-08-03.md`;
- `docs/dag/v3/GLOSHIA_R2_4_REGION_AWARE_TRAINING_GATE_20_2026-08-03.md`;
- `docs/dag/v3/GLOSHIA_R3_MULTISIGNAL_DATA_CONTRACT_21_2026-08-03.md`;
- `docs/dag/v3/GLOSHIA_R3_FOCUSED_RELABEL_22_2026-08-03.md`;
- `docs/dag/v3/GLOSHIA_R3_BOUNDED_HEAD_TRAIN_23_2026-08-03.md`;
- `docs/dag/v3/GLOSHIA_R3_ANDROID_EQUIVALENCE_24_PREP_2026-08-03.md`;
- `docs/compatibility/results/dag-performance-history.md`.

## Decisiones de producto vigentes

- DAG es el unico navegador; no restaurar DAG 1 ni DAG 2.
- Glosh es el sistema completo, DAG su navegador protegido y GloshIA el
  analizador visual local.
- DAG usa el rol oficial de navegador con confirmacion Android.
- No usar Device Owner, MDM, Knox ni restablecimiento de fabrica.
- Video permanece bloqueado; clasificar fotogramas es otro ticket.
- No hacer push, PR, publicacion DEV ni Production sin un OK nuevo y explicito.

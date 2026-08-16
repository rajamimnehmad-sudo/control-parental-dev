# Evidencia fisica DAG-VIDEO-01B-CLOSE — 2026-08-13

## Entorno

- Dispositivo dedicado: Samsung A23, `SM-A235M` (`a23`).
- APK: Diagnostic 16, `0.70.20-diagnostic`.
- Application ID: `com.contentfilter.dagbrowser.diagnostic.dev`.
- Instalacion local por ADB exitosa; sin publicacion.

## Reproduccion

Se abrio el laboratorio local `DAG Video Lab 01A0` y se capturaron por ADB la
jerarquia UI, una captura de pantalla y `logcat` con tag `DagVideoLab`.

Secuencia observada:

1. `config_enabled`: 1 vez.
2. `candidate_selected`: 7.771 veces.
3. `unsafe_presentation`: 7.771 veces.
4. `cover_posted`: 0 veces.
5. `cover_requested`: 0 veces.

Todos los mensajes informaron `active=true`, `armed=true`, `sender=true` y
`document=true`. Por lo tanto, el corte ocurre en JS despues de seleccionar el
video y antes de publicar la solicitud de cobertura hacia Android. No corresponde
atribuirlo al contrato de version 2.0.18 ni a la validacion Android.

## Estado visual

La captura mostro el titulo y texto de la fixture sobre fondo claro, sin la
cobertura nativa `Video cubierto para diagnostico`. El video no fue abierto.

## Riesgo adicional

El retiro por `unsafe_presentation` vuelve a programar la seleccion y produce un
bucle rapido. La señal actual agrupa varias subcondiciones de presentación, por
lo que esta evidencia no permite afirmar cual de ellas se activa en GeckoView.

Al terminar se desactivo el laboratorio por ADB y se limpio el buffer temporal de
`logcat`. No se modifico codigo funcional.

## DAG-VIDEO-01C — discriminacion Diagnostic

Se agrego una etiqueta cerrada por cada subcondicion de presentacion insegura y
un bloqueo que impide reseleccionar el video hasta una nueva configuracion o
navegacion. No cambia DEV: Android solo habilita este laboratorio cuando
`BuildConfig.DAG_DIAGNOSTICS` es verdadero.

Tras revision de Jefe, el aislamiento se hizo explicito en el protocolo:
Android envia `diagnostics=BuildConfig.DAG_DIAGNOSTICS`; el asset solo emite las
etiquetas y activa el bloqueo persistente cuando esa bandera es verdadera. Una
prueba de regresion confirma que el modo no-Diagnostic conserva el retiro
`unsafe_presentation` fail-closed y su comportamiento previo de reseleccion.

Validacion automatica:

- JS: 48/48.
- DEV unit: 175/175.
- Diagnostic unit: 175/175.
- `ktlintCheck` y `lintDiagnosticDebug`: correctos.
- APK Diagnostic 16 reconstruida e instalada localmente por ADB.

Nueva reproduccion fisica en el mismo A23:

1. `config_enabled`: 1 vez.
2. `candidate_selected`: 1 vez.
3. `unsafe_picture_in_picture`: 1 vez.
4. `cover_posted`: 0 veces.
5. `cover_requested`: 0 veces.

No hubo nueva seleccion durante la observacion. La subcondicion concreta es
`document.pictureInPictureElement !== null` dentro de GeckoView, aunque la app
permanecio visualmente en pantalla normal. Esto habilita investigar un posible
falso positivo de GeckoView en un ticket funcional separado; no autoriza a
ignorar PiP real ni a abrir video.

## DAG-VIDEO-01D — falso PiP corregido

La causa raiz fue una diferencia de API: GeckoView no expone
`document.pictureInPictureElement`, por lo que el valor es `undefined`. La
comparacion estricta contra `null` interpretaba esa ausencia como PiP activo. Se
cambio la comprobacion a no-nula (`!= null`): `undefined` y `null` representan
ausencia, mientras un elemento real sigue cerrando el flujo de forma segura.

Regresiones automaticas:

- La API PiP ausente alcanza la solicitud de cobertura.
- Un elemento PiP real produce `unsafe_picture_in_picture` y permanece cerrado.
- JS: 48/48.
- DEV unit: 175/175.
- Diagnostic unit: 175/175.
- `ktlintCheck` y `lintDiagnosticDebug`: correctos.

Se genero e instalo Diagnostic 17 local en el mismo A23. La reproduccion
automatizada alcanzo por primera vez:

1. `config_enabled`.
2. `candidate_selected`.
3. `cover_requested` en Android.
4. `cover_posted` en JS.
5. `cover_armed` despues del compromiso nativo de dos frames.

La jerarquia UI confirmo visible la cobertura nativa `Video cubierto para
diagnostico`. En la primera ejecucion del mismo codigo aparecio
`closing reason=reveal_denied`; la repeticion ya identificada como Diagnostic 17
cerro por `viewport_changed`. Ambas terminaron de forma estable en
`blocked reason=revoke_timeout`. El nuevo punto de investigacion es posterior a
la cobertura y requiere separar la causa inicial variable de la falta de
confirmacion de revocacion. Este ticket no modifico ese flujo. Diagnostic fue
cerrada por ADB al terminar y video permanece NO-GO.

## DAG-VIDEO-01E — cierre posterior a cobertura

Diagnostic 18 agrego etiquetas finitas, exclusivas del modo Diagnostic, para
separar la negativa de apertura, el origen del cambio de viewport y la falta de
acuse de revocacion. No cambio decisiones ni habilito video.

Dos ejecuciones desde una maquina de estados limpia reprodujeron la misma
secuencia en el A23:

1. `cover_requested`, `cover_posted` y `cover_armed` correctos.
2. `reveal_denied_insert_failed` entre 83 y 137 ms despues.
3. `closing reason=reveal_denied`.
4. `revoke_no_grant_no_proof` entre 2 y 14 ms despues.
5. `blocked reason=revoke_timeout` al vencer 1,5 segundos.

La causa primaria es el rechazo de `browser.tabs.insertCSS` al intentar abrir la
fixture interna `moz-extension://.../video-lab-fixture.html`; esa API no puede
inyectar la hoja de usuario en una pagina privilegiada de la propia extension.
`revoke_timeout` es una consecuencia distinta: al fallar la insercion no queda
un grant, pero el cierre tampoco encuentra la prueba de no-insercion que exige
para acusar recibo, y Android conserva correctamente la cobertura.

La condicion antes llamada `viewport_changed` quedo identificada como
`viewport_resize`. Aparecio en una repeticion hecha sin reiniciar despues de que
la maquina Android ya estaba bloqueada; por eso no es la causa del primer cierre.
Tambien se observo una seleccion residual finita despues de desarmar el
laboratorio, rechazada por Android. No produjo apertura ni un bucle sostenido.

Proximo arreglo propuesto, en ticket separado: servir la fixture en un origen
HTTPS local/equivalente que atraviese el mismo contrato autorizado, o definir un
mecanismo de apertura compatible para la fixture sin excepcionar sitios reales;
ademas conservar de forma atomica la prueba de insercion fallida hasta el acuse
exacto de cierre. No cambiar R3.1, politica ni cobertura nativa.

## DAG-VIDEO-01F — fixture HTTPS y siguiente corte

Diagnostic 19 monta la fixture determinista desde el content script unicamente
cuando Android arma explicitamente el laboratorio Diagnostic. La navegacion usa
un origen HTTPS normal y `insertCSS` atraviesa el mismo contrato que cualquier
sitio: no se agrego una excepcion de autorizacion por dominio, URL o proveedor.
La prueba de insercion fallida sigue registrada antes del trabajo asincrono y se
conserva hasta el acuse exacto de cierre.

Validacion automatica: JS 48/48, DEV 175/175, Diagnostic 175/175, `ktlintCheck`,
`lintDiagnosticDebug` y ensamblado correctos. Diagnostic 19 se instalo localmente
en el A23.

La reproduccion fisica alcanzo `cover_requested`, `cover_posted` y `cover_armed`;
la cobertura nativa quedo visible y ya no aparecio
`reveal_denied_insert_failed`. El siguiente corte es `play_rejected`, antes de
capturar el primer frame. Durante ese cierre volvio a observarse
`revoke_no_grant_no_proof` seguido de `revoke_timeout`; por lo tanto, la prueba
estatica de insercion fallida quedo cubierta, pero el cierre posterior a una
insercion exitosa requiere un diagnostico separado. No se aplico otro arreglo.

## DAG-VIDEO-01G — causa exacta de `play_rejected`

Diagnostic 20 agrego etiquetas finitas, exclusivas de Diagnostic, antes de
`play()` y al rechazar su promesa. No cambio la decision funcional ni DEV.

La ejecucion automatizada en el A23 produjo:

1. `play_ready_nothing`.
2. `play_video_tracks_single`.
3. `play_track_live` y `play_track_unmuted`.
4. `play_error_not_allowed`.

Gecko registro simultaneamente su advertencia explicita de politica de autoplay
en `video-lab-fixture.js:44`. Por lo tanto, el `MediaStream` de canvas existe,
tiene un track de video vivo y no esta terminado; el rechazo no es
`NotSupportedError`, `AbortError` ni una promesa perdida. La causa exacta es
`NotAllowedError` de autoplay antes de que el elemento haya recibido datos. El
intento prematuro de `play()` de la fixture tambien atraviesa esa misma politica.

Arreglo minimo propuesto para un ticket funcional separado: eliminar el intento
prematuro de la fixture y conceder la autorizacion de autoplay solo a la sesion
Diagnostic y al documento exacto del laboratorio, conservando la llamada
protegida posterior a la cobertura como unico inicio. No cambiar la politica de
autoplay global ni DEV. Despues se debe comprobar que aparece el primer callback
de frame y detenerse en el siguiente corte.

Validacion automatica: JS 48/48, DEV 175/175, Diagnostic 175/175, `ktlintCheck`,
`lintDiagnosticDebug` y ensamblado correctos. Diagnostic 20 se instalo localmente
en el A23; sin publicacion.

## DAG-VIDEO-01H — autoplay Diagnostic exacto

Se elimino el `play()` prematuro de la fixture. Gecko solicita y cachea los
permisos de autoplay audible e inaudible durante la carga, antes de que exista la
autoridad cubierta; por eso la concesion se limita a Diagnostic, laboratorio
armado, pestaña activa y URL exacta de la fixture. La reproduccion real sigue
siendo iniciada unicamente por `video-lab.js` despues de `cover_armed`, y DEV
continua denegando el permiso.

Diagnostic 21 en el A23 confirmo ambos permisos concedidos, cobertura solicitada
y armada, stream con un track vivo y ausencia de `play_error_not_allowed`. El
siguiente corte es `viewport_resize` aproximadamente 170 ms despues de
`cover_armed`: cierra por `viewport_changed` antes del primer callback/frame y
termina fail-closed en `revoke_timeout`. No hubo bucle ni se aplico otro arreglo.

Validacion automatica: JS 48/48, unitarios DEV y Diagnostic verdes,
`ktlintCheck`, `lintDiagnosticDebug` y ensamblado correctos. Diagnostic 21 se
instalo localmente; sin commit, push ni publicacion.

## DAG-VIDEO-02 — primer fotograma protegido

Diagnostic 22–26 discriminaron el `resize` de arranque. Gecko cambia la altura
del viewport al iniciar la fixture; se fijo el rectangulo de prueba y se tolero
esa transicion solo cuando `fixtureEnabled` es verdadero y el rectangulo exacto
del video no cambia. DEV y paginas reales conservan el cierre geometrico.

Diagnostic 26 alcanzo `frame_requested`. La primera captura revelo un acceso
vertical fuera de rango en la comprobacion de la fixture: usaba el ancho del
bitmap para las coordenadas Y. Diagnostic 27 separo X/Y y elimino el crash.

En A23 se observaron varios ciclos completos: cobertura armada, captura con
`fixture_pattern_ok`, `conceal_removed`, una inferencia R3.1 `model_allow` con
score aproximado 0.019 y `frame_allowed`. Esto demuestra el primer fotograma
protegido, analizado por R3.1 y presentado solo despues de allow. Video general
permanece NO-GO.

El cierre encontro un limite distinto. El background registra
`revoke_proof_marked`, pero puede perder ese estado de proceso antes de recibir
el cierre nativo. Diagnostic 31 espera 100 ms de forma fail-closed y confirma
que la prueba no reaparece; Android conserva la cobertura y termina en
`revoke_timeout`. No se acepto ausencia de grant como prueba. Resolver el
contrato ante reinicio del background requiere diseno de seguridad con esfuerzo
medio. Sin commit, push ni publicacion.

## DAG-VIDEO-02 — cierre durable, pausa segura

Se implemento un diario local acotado (16 registros) que se escribe antes de
`insertCSS` y vincula tab, documento, video, revision, viewport epoch, secuencia
y token. Al recuperar un registro `active`, el background intenta retirar el
CSS exacto; solo una retirada confirmada produce estado `revoked`. Ausencia de
memoria o storage nunca se acepta como prueba.

Los tests reproducen reinicio y contextos concurrentes: JS 50/50, unitarios DEV
y Diagnostic, ktlint, lint y builds correctos. Diagnostic 33 alcanzo varios
ciclos protegidos con `fixture_pattern_ok`, R3.1 `model_allow` (~0.019),
`conceal_removed` y prueba durable marcada.

La prueba fisica separo el limite de GeckoView: storage persiste
(`recovery_proof_loaded`), pero distintos contextos/puertos del background ven
snapshots divergentes. Refrescar storage en el cierre no permitio correlacionar
la prueba exacta en el contexto receptor; Android mantuvo cobertura y termino en
`revoke_timeout`.

Se probo un handshake previo a `insertCSS` para trasladar la identidad exacta a
Android, que ya posee la cobertura. Diagnostic 37 lo rechazo antes de abrir con
`grant_active_invalid` y `native_journal_unavailable`; no hubo CSS ni exposicion.
La discriminacion finita no identifica que campo del mensaje nativo se pierde.
Se pausa para no encadenar hipotesis: el arbol queda experimental, fail-closed y
sin commit. Video general continua NO-GO.

## DAG-VIDEO-02 — handshake Android exacto

Diagnostic 38 reemplazo la etiqueta agrupada por una clasificacion finita de
presencia, formato y vinculacion de cada campo, exclusiva de Diagnostic y sin
registrar valores, secretos, URLs ni contenido. La primera ejecucion en el A23
identifico `grant_active_invalid_tab_id_unknown` antes de CSS y permanecio
fail-closed. Fue la unica hipotesis fisica de este tramo.

La causa demostrada es un cruce incorrecto de namespaces: el `tabId` del API
WebExtension no es el id interno de `BrowserTab` en Android. Diagnostic 39
conserva ese id como correlacion exacta del diario background, exige su presencia
y formato, y resuelve en Android una unica pestaña mediante el `documentToken`
exacto que Android habia emitido para la carga protegida. Una regresion confirma
que ids de namespaces distintos vinculan el documento correcto y que dos
pestañas con el mismo token fallan cerradas.

La ejecucion fisica de Diagnostic 39 produjo repetidamente
`grant_active_ack_true`, seguida de `revoke_proof_marked` y
`revoke_proof_received`. Dos fotogramas atravesaron captura
`fixture_pattern_ok`, R3.1 `model_allow` (~0,019) y retiro de la concesion exacta.
Al desactivar el laboratorio, Android uso `revoke_local_durable` y termino en
`revoke_ack`; despues el background recupero `recovery_proof_loaded`. La
cobertura permanecio fail-closed ante una tercera captura
`fixture_pattern_mismatch`, que queda fuera de este ticket.

Validacion automatica: JS 50/50, unitarios DEV y Diagnostic, `ktlintCheck`,
`lintDiagnosticDebug` y `assembleDiagnosticDebug` correctos. Diagnostic 39 se
instalo solo en el A23 dedicado. Sin commit, push, PR, publicacion ni Production.

## DAG-VIDEO-02 — estabilidad de fixture

Diagnostic 39 reprodujo el corte tardio despues de decenas de capturas correctas.
La identidad, geometria y fuente permanecieron estables; una captura inmediata
ocasional todavia contenia el frame anterior del compositor Gecko. No era una
decision de R3.1. El cierre permanecio fail-closed.

Diagnostic 41 agrega unicamente para la fixture Diagnostic hasta tres reintentos
espaciados 50 ms. La cobertura nativa permanece colocada durante todos los
intentos; DEV y paginas reales no reintentan ni cambian su conducta.

Dos ejecuciones independientes desde cero en el A23 completaron el limite de
120/120 fotogramas cada una. Recuperaron respectivamente 9 y 7 carreras
transitorias, sin `capture_failed`; los 240 fotogramas terminaron en
`model_allow`, cada corrida alcanzo `capture_limit` y cerro con `revoke_ack`.

Gates: JS 50/50, unitarios DEV/Diagnostic, `ktlintCheck`,
`lintDiagnosticDebug` y `assembleDiagnosticDebug` correctos. El intento de
iniciar una matriz HTML5 revelo que el control actual siempre navega primero a
la fixture; se detuvo sin afirmar soporte real. Hace falta un arnes Diagnostic
separado antes de evaluar HTML5, YouTube, Shorts, Instagram, TikTok, anuncios o
DRM. Video general continua NO-GO y no esta listo para promover a DEV.

## DAG-VIDEO-02 — arnes de pagina real

Diagnostic 43 separa dos modos explicitos: fixture y pagina HTTPS actual. El
segundo se vincula a una sola pestaña, recarga ese documento sin navegar a la
fixture y no contiene excepciones por URL, dominio o proveedor. DEV no expone
el control. El autoplay temporal se concede solo al documento Diagnostic
armado; el resto conserva la denegacion.

En A23, W3Schools selecciono el video y armo la cobertura, pero un cambio real
de viewport durante el arranque aborto la reproduccion y permanecio cubierto.
El ejemplo tabulado de MDN no expuso un video elegible en el documento superior.
La pagina HTML5 de W3C selecciono candidato y armo cobertura, pero el diario
recupero un grant de una pestaña anterior ya cerrada. `removeCSS` no pudo operar
sobre esa pestaña y dejo `recovery_remove_failed`; por eso la nueva apertura se
nego con `reveal_denied_journal_unavailable`. No hubo exposicion.

Gates de Diagnostic 43: JS 50/50, unitarios DEV/Diagnostic, `ktlintCheck` y
`assembleDiagnosticDebug` correctos. Resolver de forma segura la inexistencia de
la pestaña original requiere revisar el contrato durable; no se aplico una
excepcion ni un arreglo especifico de proveedor. Video general sigue NO-GO.

## DAG-VIDEO-02 — recuperacion de pestaña cerrada

Diagnostic 44 separa una falla ambigua de `removeCSS` de dos pruebas positivas de
irrecuperabilidad. Durante recuperacion, solo una enumeracion completa y valida
de `browser.tabs.query({})` que no contiene el id WebExtension original permite
marcar el grant como `retired`. Una consulta rechazada, un resultado malformado o
una pestaña presente conservan `active` y bloquean. Para ids reutilizados, el
`documentToken` nuevo emitido por el content script superior y ya registrado en
el background demuestra reemplazo del documento viejo; el mismo token no puede
retirarse a si mismo. La ausencia de memoria nunca se acepta como prueba.

Las regresiones JS cubren pestaña confirmada ausente, consulta ambigua, reemplazo
de documento y documento coincidente. Gates: JS 54/54, unitarios DEV/Diagnostic,
`ktlintCheck`, lint DEV/Diagnostic y ensamblado DEV/Diagnostic correctos.

Diagnostic 44 se instalo conservando datos en el A23. La pagina HTML5 de W3C ya
no termino en `reveal_denied_journal_unavailable`: alcanzo `candidate_selected`,
`cover_requested`, `cover_posted` y `cover_armed`. El corte siguiente fue el
cambio real de viewport (`viewport_change_window`, `viewport_change_visual`,
altura y escala) y cerro fail-closed con `viewport_changed`; luego la solicitud
de cierre no pudo entregarse y Android mantuvo la cobertura. Esto confirma que
la deuda del journal dejo de bloquear la matriz, pero no aprueba HTML5 general.
No hubo excepciones por sitio, cambios de R3.1, push ni publicacion.

## DAG-VIDEO-02 — viewport real y primer corte YouTube

Diagnostic 45 confirmo que el primer `play()` de HTML5 modifica unicamente la
altura/escala del window y visual viewport; el rect del video permanece igual.
La transicion queda limitada a 1 segundo y 8 eventos, solo en Diagnostic, con la
cobertura nativa ya armada y antes de enviar un frame. El permiso CSS temporal
se revoca, se esperan 150 ms estables y se revalida geometria antes de reabrir.
Scroll, rect distinto, frame ya enviado, revocacion fallida o inestabilidad
siguen cerrando.

En A23, W3C completo 120/120 solicitudes, capturas y decisiones `model_allow`.
El cierre termino en `capture_limit`, `revoke_local_durable` y `revoke_ack`.
Diagnostic 46 elimino una reseleccion local del mismo milisegundo del limite y
repitio el cierre sin trabajo tardio. JS 54/54, unitarios DEV/Diagnostic,
`ktlintCheck`, lint DEV/Diagnostic y ensamblado Diagnostic quedaron correctos.

YouTube normal con Big Buck Bunny alcanzo candidato, cobertura y autoplay
Diagnostic. Supero el cambio de viewport bajo cobertura, pero el reproductor
modifico el video activo durante el arranque y el contrato cerro con
`active_video_mutated`/`revoke_ack`. No se expuso video ni se agrego una regla
por proveedor. Ese es el primer corte nuevo; Shorts, anuncios y otros sitios no
se probaron todavia.

### Mutacion exacta de YouTube

Diagnostic 47 y 48 clasificaron la mutacion sin URL, valores DOM ni contenido.
En dos corridas del video normal Big Buck Bunny sobre el A23, el unico cambio
terminal fue el atributo de capacidad `controlslist` del mismo nodo. La identidad
de fuente permanecio estable: no cambiaron `currentSrc`, atributo `src`, presencia
de `srcObject` ni hijos `<source>`. La ultima secuencia anterior al cierre fue
`viewport_transition_stable`, `mutation_video_attribute_capability`,
`mutation_capability_controlslist`, `mutation_source_identity_stable`.

Diagnostic 49 agrego una tolerancia generica acotada, solo Diagnostic, solo bajo
cobertura y antes del primer frame. Permite como maximo cuatro cambios de
`controlslist` y exige restaurar `nofullscreen`/`noremoteplayback`, mantener las
demas capacidades preventivas, fuente exacta, geometria exacta y ausencia de una
presentacion insegura. En el A23 esa revalidacion no completo:
`mutation_capability_revalidation_failed`. El flujo cerro por
`active_video_mutated`; la cobertura se mantuvo y el cierre termino bloqueado por
`revoke_timeout`. No se alcanzo un frame de YouTube y no se avanzo a Shorts ni
anuncios. JS 54/54, unitarios DEV/Diagnostic, ktlint, lint de ambos flavors y
ensamblado Diagnostic estaban verdes antes de la corrida fisica.

Diagnostic 50 separo las fallas posteriores de fuente, viewport, cada capacidad
preventiva y presentacion insegura. Los gates completos volvieron a quedar
verdes. En la unica corrida fisica posterior, la mutacion `controlslist` ocurrio
antes de que Gecko notificara el reflow observado en corridas anteriores. No se
emitio ninguna etiqueta de revalidacion, por lo que una precondicion de entrada
la rechazo antes de ejecutar esas comprobaciones. La fuente siguio estable y el
cierre completo `active_video_mutated`/`revoke_ack`; no hubo exposicion. La
hipotesis principal es una carrera entre geometria y notificacion de viewport,
pero no se considera demostrada sin instrumentar esas precondiciones. Se pauso
sin repetir YouTube y sin avanzar a Shorts o anuncios.

Diagnostic 51 agrego categorias finitas para entrada a la recuperacion:
cobertura ausente, frame ya enviado, limite agotado, fuente cambiada, geometria
cambiada, presentacion insegura o cierre en curso. El test JS demuestra que una
mutacion `controlslist` bajo cobertura y geometria estable se revalida, mientras
que el mismo cambio con rect distinto cierra y emite
`mutation_entry_geometry_changed`. En la unica corrida A23, la recuperacion entro
y termino en `mutation_capability_revalidation_failed`, pero ninguna subcausa
llego al log Android. La revision encontro que esas etiquetas superaban el
contrato `^[a-z_]{1,40}$`; Android las descartaba. Diagnostic 52 usa etiquetas de
hasta 40 caracteres y agrega una asercion para impedir esa regresion. JS 54/54,
unitarios DEV/Diagnostic, ktlint, lint de ambos flavors y assemble Diagnostic
estan verdes. No se repitio YouTube despues de corregir el contrato, evitando una
segunda corrida fisica sin nueva evidencia funcional.

La corrida Diagnostic 52 confirmo `mutation_entry_geometry_changed`: el cambio
`controlslist` sucede mientras la ventana de reflow cubierta ya esta activa. La
fuente siguio estable y el cierre fue `active_video_mutated`/`revoke_ack`.
Diagnostic 53 permitio que la mutacion entrara solo durante esa ventana ya
acotada (1 segundo, 8 eventos, antes del primer frame), restauro capacidades y
volvio a exigir fuente estable, ausencia de presentacion insegura y cierre
inactivo. En la unica confirmacion A23, esa restauracion fallo con
`mutation_capability_defer_failed`; el cierre siguio limpio y no hubo exposicion.
Diagnostic 54 agrega etiquetas finitas para fuente, cada capacidad preventiva,
presentacion insegura o cierre durante ese punto. Los gates completos estan
verdes.

La unica corrida Diagnostic 54 identifico la capacidad exacta:
`mutation_reval_no_fullscreen`. La fuente y el nodo siguieron estables; la
restauracion mediante dos operaciones `controlsList.add()` no dejo
`nofullscreen` verificable. No hubo primer frame y el cierre fue protegido.

Diagnostic 55 reemplazo esas operaciones por una reconstruccion atomica del
atributo `controlslist`, conservando tokens existentes y agregando
`nofullscreen` y `noremoteplayback`. JS 54/54, unitarios DEV/Diagnostic, ktlint,
lint de ambos flavors y assemble Diagnostic quedaron verdes. En la unica
confirmacion A23, YouTube alcanzo cobertura, autoplay y cuatro secuencias
`mutation_capability_revalidated`: la correccion restauro la capacidad. El
siguiente corte fue `mutation_entry_limit_reached`. En ese momento se planteo
como hipotesis que la escritura propia despertaba el observador; Diagnostic 56
separo esa escritura de una mutacion externa y refuto la hipotesis. El contrato
cerro con `active_video_mutated`; no se libero un frame ni se avanzo a Shorts o
anuncios.

Diagnostic 56 registro una unica escritura propia esperada durante cada
revalidacion y solo la consumio si coincidieron atributo, valor anterior y valor
nuevo exactos. Las regresiones cierran ante valor distinto y repeticion. En la
unica corrida A23, las escrituras propias fueron consumidas, pero YouTube retiro
externamente la capacidad cuatro veces mas: cada una entro como una nueva
`mutation_capability_controlslist`, mantuvo fuente estable y completo
`mutation_capability_revalidated`; la quinta alcanzo
`mutation_entry_limit_reached`. Por lo tanto, el bucle no era autocausado por el
laboratorio. El flujo cerro protegido con `active_video_mutated`, sin primer
frame, y no se repitio la prueba.

Diagnostic 57 alinea el contrato con la decision conservadora: permite una sola
transicion externa cubierta antes del primer frame. La escritura propia exacta
puede consumirse una vez; cualquier valor distinto, repeticion o segunda
mutacion externa cierra. JS 54/54, unitarios DEV/Diagnostic, ktlint, lint de
ambos flavors y assemble Diagnostic estan verdes. No hubo otra corrida fisica,
Shorts ni anuncios.

Diagnostic 59 agrego telemetria finita de generacion, paused/ended, readyState,
networkState, tracks, identidad de fuente y eventos `play`, `playing`, `pause`,
`abort`, `emptied`, `waiting` y `stalled`, sin URL ni contenido. JS 55/55,
unitarios DEV/Diagnostic, ktlint, lint de ambos flavors y assemble Diagnostic
quedaron verdes antes de la unica corrida fisica.

En A23, la generacion inicial entro `HAVE_NOTHING`/`NETWORK_EMPTY`, emitio
`play`/`waiting` y fue pausada por el conceal cubierto del reflow; su rechazo
quedo correctamente clasificado `play_aborted_for_viewport`. Tras
`viewport_transition_stable`, la segunda generacion mantuvo fuente estable,
pausado/no terminado, `HAVE_NOTHING`/`NETWORK_EMPTY`, sin tracks, y volvio a
emitir `play`/`waiting`. No hubo `playing`, `abort`, `emptied`, `stalled` ni
mutacion de fuente antes del corte. A los 2,5 s vencio `frame_ready_timeout`;
`retireRecord` ejecuto la pausa fail-closed, esa pausa cancelo la promesa y por
eso aparecio `play_error_abort` inmediatamente antes del cierre durable
`revoke_local_durable`/`revoke_ack`. El `AbortError` es consecuencia local del
timeout. El corte causal es que el candidato sigue sin fuente utilizable despues
del reflow. No se justifico alargar el timeout ni forzar `load()`, y no hubo otra
corrida, primer frame, exposicion, Shorts o anuncios.

Diagnostic 60 clasifico backing media sin registrar valores: presencia de
atributo `src`, `currentSrc`, `srcObject` y tracks, hijos `<source>`, categoria de
esquema y eventos `loadstart`, `durationchange`, `loadedmetadata` y `canplay`.
En la unica corrida A23, ambas generaciones tuvieron atributo `src` ausente,
`currentSrc` ausente, `srcObject` ausente, cero hijos `<source>` y esquema
ausente. No aparecio ningun evento de preparacion antes del cierre. Esto descarta
MediaSource/Blob pendiente dentro de la ventana y clasifica el elemento como
visual sin backing media observable.

Diagnostic 61 agrego una elegibilidad generica: solo selecciona un video visible
cuando existe `currentSrc`, `src` no vacio, `srcObject` o un hijo `<source>` con
fuente. Mientras espera, audio y video siguen aislados; una asignacion DOM o los
eventos estandar programan un nuevo escaneo. No fuerza `load()` ni alarga un
timeout. JS 56/56, unitarios DEV/Diagnostic, ktlint, lint de ambos flavors y
assemble Diagnostic quedaron verdes. En la unica confirmacion A23, durante toda
la ventana hubo `scan_no_candidate`; YouTube solicito autoplay pero no aparecio
backing ni evento estandar, por lo que no hubo cover, grant, frame o exposicion.
El siguiente corte queda separado: backing fuera del `<video>` observable o
asignacion impedida por el aislamiento temprano de playback. No se repitio ni se
avanzo a Shorts o anuncios.

Diagnostic 58 reemplazo la dependencia de `controlslist` por autoridades
genericas. Fullscreen conserva los callbacks nativos Android que muestran la
cobertura, cierran el grant exacto y ejecutan `exitFullScreen()`. Un guard
MAIN-world en `document_start`, todos los frames y `about:blank`, rechaza
`requestPictureInPicture`, Document-PiP, presentacion WebKit fullscreen/PiP y
`RemotePlayback.prompt`; sus metodos quedan no configurables y no escribibles.
El guard es defensa preventiva, no autoridad: si no puede verificarse, o aparece
un estado PiP/remote inseguro, la extension cierra. Continuan como precondiciones
`disablePictureInPicture`, `disableRemotePlayback` y `playsInline`; sus
mutaciones externas son terminales. `controlslist` ya no se escribe, observa ni
revalida.

JS 55/55 cubre intentos de API, reemplazo de metodos, guard ausente, mutacion de
booleanos y regresion `controlslist`. Unitarios DEV/Diagnostic, ktlint, lint de
ambos flavors y assemble Diagnostic estan verdes. En la unica corrida A23 de
YouTube normal se alcanzaron cobertura, autoplay, conceal/reopen del reflow y
`viewport_transition_stable`, sin nuevas mutaciones `controlslist`. El siguiente
corte fue `play_error_abort`, seguido de `frame_ready_timeout`; el cierre durable
completo `revoke_local_durable`/`revoke_ack`. No hubo primer frame, exposicion,
Shorts ni anuncios.

## DAG-VIDEO-02 — bootstrap consolidado y YouTube normal estable

Diagnostic 62-68 localizaron una secuencia de arranque valida pero asincronica:
el backing blob/MediaSource y `loadstart` pueden aparecer antes de que el ack
nativo termine `backgroundReady()`, y el rect cambia una vez antes del primer
frame. Diagnostic 69 permitio solo esa transicion cubierta, ligada al mismo
video, documento, fuente y viewport, sin grant ni frame previo. Al completar la
espera revalida fuente, geometria, capacidades, presentacion y sender. Una
segunda transicion o cualquier diferencia material sigue cerrando.

Diagnostic 69 alcanzo por primera vez en YouTube normal `frame_requested`,
`frame_captured` y `model_allow`. Una diferencia posterior revelo que el chequeo
de frame confundia el historial de una transicion ya completada con una
transicion pendiente; se corrigio usando el estado pendiente actual.

Diagnostic 70 y extension 2.0.28 consolidan el flujo en
`video-bootstrap-state.js`, una maquina finita separada. El replay JS cubre
`loadstart` antes/despues del ack, espera de background, MediaSource ready,
orden resize/frame-check, duplicados, tardios, segunda transicion y diferencias
de fuente, geometria o capacidades, todas fail-closed. JS 58/58, unitarios
DEV/Diagnostic, ktlint, lint DEV/Diagnostic y assemble Diagnostic quedaron
verdes.

En la segunda y ultima corrida de consolidacion sobre el A23, YouTube normal
completo bootstrap y una transicion de viewport estable. Registro exactamente
120 `frame_requested`, 120 `frame_captured` y 120
`frame_allowed reason=model_allow`. El final fue el esperado
`closing reason=capture_limit`, seguido de `retired reason=revoke_ack`; no hubo
cierre por viewport, fuente, capacidad o presentacion insegura. No se hizo una
tercera corrida ni se avanzo a Shorts o anuncios.

## DAG-VIDEO-02 — primer corte de matriz Shorts

El replay local se amplio a 59/59 casos con una matriz neutral al proveedor:
ambos ordenes validos de ack/backing llegan a estable; reemplazo de fuente,
segunda generacion y capacidad insegura permanecen terminales. No cambio el
runtime ni se genero otra APK; se reutilizo Diagnostic 70 instalado en el A23.

Se consumieron las dos corridas fisicas presupuestadas para este corte. La
primera URL publica elegida devolvio 404 antes de armar el laboratorio y se
descarto sin eludir el sitio. La segunda, un Short publico de canal oficial,
cargo y se armo por el flujo normal. Solicito autoplay, pero durante toda la
ventana solo expuso un video visual sin backing seleccionable:
`timeline_video_seen_no_backing` y `scan_no_candidate`. No hubo cover, grant,
captura ni frame; el contenido permanecio oculto y pausado. El corte coincide
con una categoria generica ya fail-closed y no justifica una excepcion para
Shorts o YouTube. No se probaron anuncios por agotarse el presupuesto fisico.

### Shorts — backing posterior al gesto y reemplazo de candidato

Un sublote posterior reutilizo Diagnostic 70 y la misma maquina/replay, sin
cambio runtime ni APK. En la primera de dos corridas, un tap normal sobre el
player antes de armar produjo `loadstart`, `durationchange`, `loadedmetadata`,
`canplay` y `timeline_video_seen_backing`. La recarga obligatoria al armar creo
un documento nuevo que volvio a comenzar sin backing.

En la segunda corrida, con la pestaña ya armada, el mismo gesto produjo backing
real `blob`/MediaSource en el video top-level, seleccion de candidato y
`cover_requested`/`cover_armed`. Inmediatamente aparecio otro candidato visible:
la comprobacion generica de identidad detecto que no era el mismo elemento DOM
y cerro con `authority_changed`. Como todavia no existia frame/grant durable, el
cierre termino bloqueado en `revoke_request_not_delivered`; no se libero ningun
pixel. No hubo evidencia de iframe o shadow DOM. Transferir autoridad entre
elementos distintos seria una arquitectura de seguridad nueva, por lo que no se
implemento. Shorts permanece fail-closed y no alcanzo captura ni R3.1.

## DAG-VIDEO-02 — observacion natural de anuncios

El gate local se reutilizo sin cambios y quedo 59/59 verde. La matriz existente
mantiene terminales una segunda generacion, cambio de fuente o reemplazo del
elemento DOM; no se agregaron etiquetas ni reglas de proveedor.

Se hizo una apertura natural en A23 del video normal Big Buck Bunny con
Diagnostic 70. No hubo backing automatico; despues de pulsar el control normal
Reproducir, el mismo elemento obtuvo backing `blob`/MediaSource y mantuvo fuente
estable. Completo 120 `frame_requested`, 120 `frame_captured` y 120 decisiones
R3.1 `model_allow`, con cierre `capture_limit`/`revoke_ack`. No aparecieron una
segunda generacion, reemplazo DOM ni transicion anuncio-contenido. Por el limite
del sublote no se forzo una campaña ni se gasto la segunda corrida. Anuncios
quedan no probados; el resultado solo reconfirma contenido YouTube normal.

## DAG-VIDEO-02 — matriz social publica

Se reutilizo Diagnostic 70 sin cambios runtime, APK ni repeticion de gates. Se
hizo como maximo una apertura por plataforma y no se usaron credenciales ni
evasiones.

Instagram expuso publicamente el perfil oficial de NASA y su cuadricula de
Reels. Al cerrar el aviso automatizable y abrir un Reel mediante un toque normal,
el sitio redirigio a Google Play para instalar la app. No quedo una pagina de
Reel armable: no hubo candidato, backing, cover ni frame. No se instalo la app
ni se intento eludir la redireccion. Instagram Reels queda no probado.

El reproductor web publico embebible de TikTok cargo sin login, captcha ni
consentimiento. Armado y despues de un toque normal solo registro solicitudes de
autoplay y `scan_no_candidate`; no aparecio ningun `<video>` top-level ni las
señales `timeline_video_seen_*`. No hubo backing, cover, grant, captura ni R3.1.
Un reproductor indirecto o iframe es posible, pero no quedo demostrado por esta
evidencia y no se modifico la arquitectura. TikTok permanecio oculto, pausado y
fail-closed; queda no probado.

## DAG-VIDEO-03 — Gate A de video fluido

Diagnostic 71 y extension 2.0.29 se construyeron desde `main` local integrado.
Los gates quedaron verdes: JS 63/63, unitarios DEV/Diagnostic, ktlint, lint de
ambos flavors y assemble Diagnostic. APK SHA-256:
`b11871ddf45cd1c57a4a1759579f9d5c4957eac1c9dfead0e861a00408871d97`.

Se instalo con `-r` en el A23 `SM-A235M` y se armo una sola pestaña HTTPS con el
video publico normal Big Buck Bunny. Tras el gesto normal de reproducir y bajo
cobertura se observaron exactamente las capacidades esperadas:

- `fluid_capture_standard`;
- `fluid_recorder_available`;
- `fluid_webm_supported`;
- `fluid_audio_single`;
- `fluid_video_single`;
- `fluid_probe_available`.

El probe no inicio MediaRecorder, no genero URL, no copio ni registro contenido
y detuvo sus tracks inmediatamente. Luego el laboratorio anterior continuo su
flujo protegido; no se uso como evidencia de fluidez. Diagnostic fue cerrada por
ADB al terminar. Gate A queda GO; Gate B debe demostrar segmentos finitos,
continuidad A/V, transporte acotado y buffer aprobado antes de otra afirmacion.

## DAG-VIDEO-03 — Gate B de Port JSON/base64

Diagnostic 72 y extension 2.0.30 se construyeron desde `main` local integrado.
Gates finales: JS 84/84; unitarios DEV/Diagnostic; ktlint; lint DEV/Diagnostic;
assemble Diagnostic y `git diff --check` verdes. APK SHA-256:
`8f892cc307cf26f7b4f77d13d32666afa7d4c4a2678cdf37ecdfd16aea2111a2`.

Se instalo con `-r` en el A23 `SM-A235M`. Sobre una pestaña HTTPS neutra se
ejecuto una unica carga sintetica, sin video, audio, imagenes, URL ni bytes en
logs. Resultado exacto:

- 45.000.000 bytes; 687/687 chunks verificados y reconocidos;
- 119.941 ms, aproximadamente 3 Mbps sostenidos;
- cola pico 1; ACK JS p95 32 ms y p99 36 ms;
- encode/hash/base64 JS p95 53 ms;
- decode+canonicalizacion+SHA nativo p95 8,413645 ms, 687 muestras;
- PSS 257.864 KiB antes, 248.202 KiB durante y 246.965 KiB al final: sin
  crecimiento; la primera muestra incluia el popup del menu;
- 61 frames medidos despues del reset, p99 117 ms y dos frames por encima de
  100 ms; 9 janky frames;
- sin ANR, crash ni OOM; un GC explicito de 20,976 ms al final.

Integridad, caudal, ACK y memoria pasan. Encode JS, verificacion nativa y pausa
UI fallan los umbrales 8 ms, 4 ms y cero frames de 100 ms. Por lo tanto el Port
base64 queda NO-GO. No se bajaron limites ni se conecto contenido real. La app
se cerro por ADB y se eliminaron solo los XML temporales de automatizacion.
## DAG-VIDEO-03 — auditoria posterior a Gate B

El loopback HTTP fue descartado sin otra APK. En GeckoView 153 el acceso a
`127.0.0.1` atraviesa Local Network Access y el permiso `loopback-network` se
atribuye al sitio superior. No se autorizara a paginas web acceso persistente a
localhost ni se desactivara la proteccion globalmente.

La revision del AAR y su `omni.ja` encontro un camino mas acotado. La conexion
GeckoView usa structured clone; `GeckoBundleUtils` convierte propiedades
`Uint8Array` en `byte[]`. El wrapper Java publico de `WebExtension.Port` llama
despues a `GeckoBundle.toJSONObject()` en UI. Una clase auxiliar compilada en el
paquete `org.mozilla.geckoview` accedio correctamente al dispatcher y a
`GeckoBundle.getByteArray()` contra el AAR exacto 153, sin modificar el motor.

Se abre un unico lote Diagnostic 73: adaptador binario versionado, una sola
autoridad, dos chunks maximos, hash/orden fuera de UI y el mismo benchmark de
45 MB. Maximo dos corridas fisicas. No se generara una sucesion de APK por
telemetria.

## DAG-VIDEO-03 — Gate B binario

Diagnostic 73 y extension 2.0.31 se construyeron desde `main` local integrado.
El adaptador queda fijado al AAR GeckoView 153.0.20260715202819 y toma el
`byte[]` de structured clone antes de que el Port publico convierta el mensaje
a JSON. No agrega red, Base64, archivos ni permisos. Gates finales: JS 84/84;
unitarios DEV/Diagnostic; ktlint; lint DEV/Diagnostic; assemble Diagnostic y
`git diff --check` verdes. APK SHA-256:
`252bd2fff9bbcf5612c8744181e1d90ff559b4c9c2a60f50e9998c7da697fdf3`.

Se instalo con `-r` en el A23 `SM-A235M` y se ejecuto una sola corrida sobre
`https://example.com/`, sin contenido audiovisual ni bytes en logs:

- 45.000.000 bytes; 687/687 chunks verificados y reconocidos;
- 119.905 ms, aproximadamente 3 Mbps, cola pico 1;
- ACK JS p95 17 ms y p99 23 ms;
- preparacion/hash JS p95 6 ms;
- verificacion SHA nativa p95 0,72526 ms, 687 muestras;
- PSS 261.769 KiB antes, 263.132 KiB durante y 260.763 KiB al final;
- 60 frames UI, p99 77 ms, cero frames superiores a 100 ms;
- sin ANR, crash, OOM ni cola creciente;
- `transport_thresholds=true` sin reducir limites.

Gate B binario queda GO. La app se cerro por ADB. No se conecto video real ni se
afirma fluidez: el siguiente gate debe integrar una ventana WebM real, decoder,
continuidad A/V, detector temporal y buffer aprobado.

## DAG-VIDEO-03 — modo adaptativo fluido

Diagnostic 74 reemplazo el pause/capture/play continuo por dos cuadros iniciales
cubiertos y muestreo cada 500 ms durante reproduccion normal. En A23/YouTube
alcanzo `smooth_started` y mantuvo decenas de inferencias sin pausas. Un
`model_filter` real revelo que la revocacion usaba la identidad de la muestra
actual y no la del grant persistente; Android cubrio y pauso, pero
`conceal_failed`. No se aprobo esa APK.

Diagnostic 75 conserva una instantanea exacta del grant fluido. Segunda y ultima
corrida A23:

- `smooth_started=1`;
- 83 `frame_requested`, 83 `frame_captured`;
- 82 `frame_allowed`;
- un `frame_blocked model_filter`, score 0,5150242;
- una pausa, ocurrida despues del bloqueo;
- `revoke_proof_marked`, `revoke_proof_received` y `conceal_removed`;
- cero `conceal_failed`, `revoke_timeout`, `capture_failed`, cambio de viewport,
  cambio de fuente o presentacion insegura.

Los scripts de benchmark/runner binario se retiraron del manifest y del barrier;
sus prototipos y tests quedan aislados. Gates: JS 93/93, unitarios DEV y
Diagnostic, ktlint, lint Diagnostic, assemble Diagnostic y diff-check verdes.
Diagnostic 75, extension 2.0.33, SHA-256
`92ca053af2878060179bf9e3fcef782f680dbd4760411efbc882f7f0fbb241da`.

### Auditoria de experiencia posterior

La prueba manual del usuario rechazo Diagnostic 75: el modo elegido reprodujo
sin imagen visible ni audio. La telemetria anterior solo demostraba el pipeline
y el cierre, no la experiencia humana. La auditoria encontro captura tardia del
estado de audio original y una liberacion que podia conservar `opacity:0`.

El candidato Diagnostic 76 / extension 2.0.34 guarda el audio antes de aislar,
restaura y verifica mute/volumen, exige visibilidad efectiva y fuerza opacidad
visible solo durante el grant exacto. El menu Diagnostic deja una sola accion de
video real; el patron interno ya no aparece al usuario. Prevalidacion local:
escenario completo aprobado tres veces, JS 94/94, unitarios DEV/Diagnostic,
ktlint, lint DEV/Diagnostic, assemble Diagnostic y diff-check verdes. APK de
116,5 MiB, SHA-256
`fd28fe7668108934ce4f6712a850fb9cfe7e0b9e3c348fa3ce7715b88ea4c740`.
Sin emulador instalado ni telefono conectado, imagen visible, fluidez y salida
real de audio quedan obligatoriamente pendientes de A23/S22.

### Limpieza posterior sin nueva APK

La auditoria detecto que `video-fluid-capability.js` aun estaba en el manifest y
ejecutaba un probe `captureStream()` al primer play, pese a que la arquitectura
WebM habia sido descartada. Se retiro del runtime y se dejo solo como evidencia
de investigacion. Tambien se retiro de `DagBrowserActivity` el adaptador binario
privado y el benchmark imposible de activar; el Port de contenido vuelve a la API
publica de GeckoView. JS 94/94 y unitarios DEV/Diagnostic + ktlint quedaron verdes.
La APK 76 anterior a esta limpieza queda invalidada y no debe entregarse. No se
genero otra APK.

## DAG-VIDEO-03 — prevalidacion y A23 Diagnostic 77

El prelaboratorio Android 15/API 35 ARM64 reprodujo HTML5 real y YouTube con
imagen visible, audio Android activo y muestreo adaptativo. Tambien encontro una
brecha general: `seeking` no cerraba la autoridad. Se agrego un cierre universal
`seek_requested`, sin regla por proveedor; una busqueda simulada en YouTube
confirmo cobertura y retiro seguro. JS 94/94, unitarios Diagnostic, ktlint, lint
y assemble Diagnostic quedaron verdes.

Diagnostic 77 / extension 2.0.35 se instalo en A23 `SM-A235M` Android 14. Una
corrida automatizada sobre YouTube normal Big Buck Bunny dio:

- seleccion a cobertura armada: 16 ms; seleccion a `smooth_started`: 1.871 ms;
- dos capturas de pantalla distintas con video visible;
- salida AAudio de medios iniciada, stereo 48 kHz;
- 78 `frame_requested`, 78 `frame_captured`, 77 `frame_allowed`;
- captura p50 6 ms/p95 7 ms; inferencia p50 144,6 ms/p95 168,1 ms;
- intervalo de muestra p50 713 ms/p95 740 ms;
- un `model_filter` real, score 0,44008914, despues de 53,8 s fluidos;
- CPU puntual 13,3%, PSS 257.144 KiB, RSS 274.640 KiB;
- cero crash y cero ANR.

La app se cerro por ADB al finalizar. APK SHA-256:
`072e98c9292feefa531bd8ddb0587f952887787dc9998f92e66db6281293b64d`.
Esto aprueba la ruta tecnica automatica, no sustituye la comprobacion humana de
fluidez percibida y sonido audible.

# REGLAS DE INGENIERIA GLOSH

`AGENTS.md` manda. Este documento agrega criterios de calidad.

## Alcance y decisiones

- Un ticket, un area responsable y un resultado verificable.
- Las preguntas, comentarios o requisitos adicionales del usuario no detienen una
  tarea activa. Continuarla en paralelo salvo que el usuario diga explicitamente
  `para`, aparezca un limite de seguridad/autorizacion o seguir pueda causar dano.
- En el chat actual `Jefe`, una respuesta `dale` a una propuesta concreta cuenta
  como autorizacion explicita de ese ticket o lote completo, incluso si es grande,
  y solo dentro del alcance y limites resumidos inmediatamente antes.
- Para lotes grandes, `Jefe` debe exponer objetivo, limites y condiciones de pausa
  antes de pedir autorizacion; dentro del lote puede aprobar microtickets sin
  volver a consultar al usuario.
- Diagnosticar causa raiz antes de escribir codigo.
- No tocar areas vecinas por conveniencia. Si el arreglo cruza areas, Direccion
  Tecnica divide o coordina los tickets.
- Preferir soluciones simples, mantenibles y reversibles. No agregar capas,
  dependencias o abstracciones sin un beneficio concreto.
- Investigar fuentes oficiales cuando la decision dependa de tecnologia, precio,
  seguridad, licencia o comportamiento que pueda haber cambiado.
- Si aparece un problema fuera del ticket, documentarlo y continuar. Ampliar el
  alcance solo cuando bloquee directamente el resultado actual.

## Codigo y tamaño

- Una clase o archivo debe tener una responsabilidad clara.
- Objetivo habitual: menos de 400 lineas de codigo de produccion.
- Desde 500 lineas se debe justificar por que sigue unido.
- Desde 800 lineas es obligatorio abrir un ticket de division antes de seguir
  agregando responsabilidades.
- Excepciones: codigo generado, migraciones, datos o fixtures; deben estar
  identificados y no editarse como codigo normal.
- No hacer refactors masivos junto con un arreglo funcional. Dividir, probar y
  migrar en lotes pequenos.

## Documentacion

- Cada area tiene un solo `HANDOFF.md`, idealmente menor a 200 lineas.
- El handoff contiene presente: objetivo, estado, riesgos, ticket activo y
  siguientes tickets. No acumula conversaciones ni cierres antiguos.
- La evidencia extensa y los experimentos van a documentos fechados o archivo.
- No duplicar versiones o estados en varios lugares. El estado global vive en
  `docs/PROJECT_CONTROL.md`; el detalle vive en el handoff del area.

## Contexto y tokens

- Usar solo el contexto necesario para el ticket; no cargar todo el repositorio,
  backlog historico o conversaciones completas.
- Esfuerzo bajo por defecto. Subirlo solo cuando seguridad, arquitectura, riesgo
  o incertidumbre real lo justifiquen y despues de avisar al usuario.
- No delegar por rutina. Crear un agente solo si puede trabajar de forma
  independiente y ahorra tiempo o contexto neto; evitar agentes duplicados.
- Mantener como maximo dos frentes activos, salvo una razon concreta documentada.
- Preferir una busqueda dirigida, una prueba pequena y caches existentes antes de
  auditorias globales, builds completos o investigaciones repetidas.
- No repetir pruebas verdes si ningun archivo relevante cambio. Ejecutar el gate
  completo solo al cerrar un hito o preparar una entrega.
- Detener el trabajo cuando la siguiente accion dependa de una decision o prueba
  humana; no gastar creditos especulando alrededor del bloqueo.
- Los reportes deben ser breves y referenciar evidencia existente, no regenerarla.
- Cuando `Jefe` delegue trabajo, mantener el turno abierto con espera pasiva y
  avisar al usuario al terminar. Evitar sondeos frecuentes, mensajes sin cambios
  y relecturas completas; consultar el estado solo cuando aporte informacion.
- No dejar agentes o procesos vivos sin trabajo. Cerrar la delegacion cuando el
  resultado haya sido recibido y revisado.
- Modo economia por defecto: agentes nuevos reciben solo el ticket y las rutas de
  documentos canonicos; no heredan la conversacion completa salvo necesidad
  demostrada.
- Un agente por lote, no un agente por microcorte. Reutilizar el mismo agente
  mientras conserve contexto util y cerrar al completar el hito.
- Jefe no repite la investigacion del agente: verifica diff, gates e invariantes
  criticas mediante una revision dirigida.
- No emitir actualizaciones periodicas sin cambio. Informar solo hito, bloqueo,
  cambio porcentual o decision necesaria.
- Antes de iniciar un lote potencialmente costoso, definir un presupuesto
  operativo: maximo de corridas fisicas, builds y ciclos de hipotesis.
- Aplicar la regla de tres intentos: si el tercero no hace pasar el hito ni
  cambia claramente la decision tecnica, detener nuevas variantes y hacer una
  auditoria enfocada de causa raiz, arquitectura y supuestos. Una etiqueta de
  telemetria nueva no cuenta por si sola como avance de producto.
- Despues de activar esa auditoria, no generar otra APK hasta consolidar un plan
  distinto, sus invariantes y las pruebas locales que discriminen el lote
  completo. Agrupar la telemetria necesaria antes de volver a compilar.
- Si la longitud de un chat empieza a perjudicar precision, velocidad o costo,
  actualizar primero el control central y el handoff del area y luego compactar.
- Tras compactar, continuar desde esos documentos. No repetir auditorias ni
  reabrir decisiones cerradas salvo que aparezca evidencia nueva.
- La compactacion debe conservar objetivo, autorizaciones, cambios sin commit,
  pruebas, riesgos, decisiones y siguiente accion.

## Escalamiento a Direccion

- Al cerrar una fase/ticket, ante un bloqueo o una decision, actualizar primero
  el handoff del area.
- Si existe coordinacion saliente, enviar a `Jefe` solo ticket, resultado, pruebas,
  Git, pendientes y decision. No asumir que ese contacto esta disponible.
- Si no existe, pedir al usuario solo: `Decile a Jefe que revise <AREA>`; nunca
  hacerle copiar el reporte. `Jefe` leera el chat y podra responder directamente.
- Direccion revisa antes de considerar cerrado cualquier trabajo importante.

## Validacion y entrega

- Automatizar primero las pruebas repetibles: ADB, scripts, capturas, logs,
  jerarquia UI y recoleccion de evidencia. No usar al usuario como operador si
  Codex puede ejecutar u observar el paso de forma segura.
- Un dispositivo fisico confirma hitos; no debe ser el depurador principal de
  cada microestado. Convertir primero toda secuencia fisica reproducible en un
  replay/test local determinista y resolver el lote alli.
- Para carreras o protocolos con varios eventos, preferir una maquina de estados
  central con invariantes y tests de orden, duplicados y eventos tardios. Evitar
  acumular parches condicionales dispersos.
- Generar una nueva APK Diagnostic por hito, no por etiqueta o microhallazgo.
  Limitar por defecto a dos corridas fisicas por lote despues de gates locales.
- Pedir intervencion humana solo para desbloqueos, permisos o confirmaciones del
  sistema no automatizables, y valoraciones visuales que la evidencia capturada
  no permita resolver. Agrupar esas acciones en un unico mensaje breve.
- Ejecutar primero pruebas dirigidas. Evitar tareas Gradle genericas que disparen
  todas las variantes sin necesidad.
- Android: versionar solo la app afectada y validar el ticket proporcionalmente.
- DAG es Gradle aislado: usar siempre `scripts/dag_gradle.sh`.
- Cambios de navegador, medios, seguridad o compatibilidad requieren prueba
  fisica cuando el handoff lo indique; nunca inferirla.
- Un fallo previo ajeno se documenta con evidencia; no se oculta ni se arregla
  fuera de alcance.
- No declarar publicado algo que solo esta construido localmente.

## Seguridad y datos

- Supabase permitido: solo DEV `syeycayasyufedwoprea`, salvo autorizacion nueva.
- Nunca Service Role Key en Android, secretos en Git ni datos borrados sin OK.
- `.codex-tmp` contiene corpus y evidencia: no se borra en bloque. Primero se
  clasifica en canonico, reproducible, cache o descartable.
- La Mac M2/8 GB sirve para desarrollo y pruebas acotadas, no para asumir
  entrenamiento pesado sin limites de memoria, tiempo y costo.

## Git y operaciones remotas

- Trabajar sobre el `main` local vigente y preservar cambios del usuario.
- Se permiten commits locales para guardar trabajo verificable y coherente.
- Push, PR, publicacion DEV y toda mutacion sensible requieren autorizacion
  explicita dada en el chat actual `Jefe`. No heredar permisos de tickets o chats
  anteriores.
- Production queda fuera de alcance por defecto.

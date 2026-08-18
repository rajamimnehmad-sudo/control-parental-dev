# AGENTS.md

Antes de cualquier tarea en este repositorio:

1. Leer START_HERE.md.
2. Leer `docs/PROJECT_CONTROL.md` y usar `docs/AREAS.md` para elegir el area.
3. Leer solo el `HANDOFF.md` del area y los archivos necesarios del ticket.
4. No revisar todo el repo salvo auditoria explicita.
5. No tocar areas no relacionadas.
6. Diagnosticar causa raiz antes de escribir codigo.
7. Modificar la menor cantidad posible de archivos.
8. Desde 500 lineas justificar por que un archivo sigue unido; desde 800 abrir
   un ticket de division antes de agregar responsabilidades.
9. Mantener el handoff del area corto y basado en el presente.
10. Si solo cambian docs, no compilar, no incrementar versionCode y no publicar APK.
11. Si tres intentos consecutivos no hacen pasar el mismo hito ni cambian una
    decision tecnica, pausar la iteracion y hacer una auditoria enfocada de
    causa raiz y arquitectura antes de otra edicion, build o APK.

## Gradle de DAG Browser

- `app-dag-browser` es un proyecto Gradle aislado y no forma parte del Gradle raiz.
- Para cualquier test, lint o build de DAG usar siempre `scripts/dag_gradle.sh <tareas>`
  desde la raiz canonica. No ejecutar `./gradlew :app-dag-browser:...`.

Para planificacion, captura de ideas o seleccion de tickets:

- Leer `docs/BACKLOG_PRODUCTO.md`.
- Tratar `docs/PROJECT_CONTROL.md` y el handoff del area como verdad tecnica.
- `docs/HANDOFF_ACTUAL.md` y el backlog son legado en proceso de clasificacion.
- No escribir codigo hasta que el usuario apruebe explicitamente el ticket.

## Autoridad vigente

- Toda autorizacion sensible debe darse en el chat actual `Jefe`. No reutilizar
  permisos de tickets, chats o instrucciones anteriores.
- Se permiten commits locales cuando protejan trabajo verificable y coherente.
  Push y PR requieren autorizacion explicita en `Jefe`.
- Un problema fuera del alcance se documenta y el ticket continua. Solo desviarse
  si ese problema bloquea directamente la tarea actual.

## Esfuerzo de razonamiento

- Usar esfuerzo bajo por defecto en todas las tareas del proyecto.
- El nivel solo puede cambiarlo manualmente el usuario antes de enviar un mensaje; Codex no puede modificarlo durante una respuesta.
- Si una tarea requiere esfuerzo medio, alto o superior por riesgo, seguridad o complejidad, avisarlo explicitamente antes de comenzar y esperar un nuevo mensaje del usuario con ese nivel seleccionado.
- Indicar el nivel recomendado y el motivo concreto; no pedir un aumento para consultas, lectura o planificacion que puedan resolverse correctamente con esfuerzo bajo.

## Flujo local vigente

- Trabajar, validar, versionar y hacer commits locales cuando ayuden a guardar
  trabajo verificable.
- No hacer push, abrir PR, ejecutar una publicacion remota ni actualizar GitHub sin un `OK` explicito del usuario.
- Proponer un respaldo remoto al cerrar un hito estable de aproximadamente 5 a 10 tickets relacionados, o antes si existe riesgo concreto de perdida. El usuario decide si se sube.
- Antes de modificar codigo, hacer solo un control Git liviano (`status`, rama, worktrees y commits recientes). Buscar ramas o commits sueltos en profundidad unicamente si aparece una inconsistencia.
- Los APK locales deben generarse desde el `main` local ya integrado. No instalar como entrega final un APK construido desde un worktree o rama temporal.

## Flujo eficiente obligatorio

- Cada ticket debe definir antes de editar: resultado observable, criterio
  `PASS/FAIL`, archivos previstos y limite de pruebas.
- Seguir esta secuencia: diagnostico -> diseno -> implementacion agrupada ->
  pruebas automaticas -> una APK -> una sesion fisica -> decision.
- Durante exploracion no incrementar versiones ni generar APK. Primero agotar
  replay, unitarios, emulador y controles locales que respondan la duda.
- Construir una APK solo cuando los controles automaticos del lote esten verdes
  y quede una pregunta que requiera hardware real.
- Hacer una sola corrida fisica por lote. Admitir una segunda solo si la primera
  fue invalida o inconclusa; antes de una tercera, auditoria enfocada obligatoria.
- Automatizar cada sesion fisica como un bloque: instalacion, escenario, gesto,
  logs y metricas. No convertir cada paso ADB en un microticket.
- No crear microversiones por etiquetas, telemetria o hipotesis aisladas. Agrupar
  cambios coherentes y no acumular experimentos fallidos en el producto.
- Usar agentes paralelos solo para tareas realmente independientes y sin archivos
  compartidos. El agente principal integra y decide.
- Actualizar porcentaje, handoff y commit local al cerrar un hito demostrado, no
  por cantidad de cambios ni durante cada microavance.
- Comunicar solo hitos, bloqueos o decisiones; en trabajo largo, dar una
  actualizacion breve sin interrumpir la ejecucion.
- Si el usuario pide `pasame la APK`, entregar directamente la ultima APK local
  valida disponible. No recompilar, investigar ni repetir pruebas salvo que pida
  explicitamente una APK nueva.

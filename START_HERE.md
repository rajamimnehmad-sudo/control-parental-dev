# GLOSH — EMPEZAR AQUI

Este archivo es la entrada obligatoria para cualquier chat o agente.

## Lectura minima

1. `AGENTS.md`: reglas no negociables.
2. `docs/PROJECT_CONTROL.md`: estado presente, prioridades y bloqueos.
3. `docs/AREAS.md`: elegir una sola area responsable.
4. `docs/areas/<area>/HANDOFF.md`: contexto operativo de esa area.

Abrir `docs/BACKLOG_PRODUCTO.md` solo para buscar ideas o historia de producto.
Abrir `docs/HANDOFF_ACTUAL.md` solo como legado historico hasta archivarlo.
No cargar esos dos archivos completos en cada tarea.

## Higiene de contexto

- Si un chat se vuelve tan largo que reduce precision, velocidad o eficiencia,
  consolidar primero el estado vigente en `docs/PROJECT_CONTROL.md` y en el
  `HANDOFF.md` del area.
- Despues solicitar o permitir la compactacion del contexto. Continuar desde los
  documentos, sin reconstruir toda la historia ni repetir auditorias terminadas.
- Conservar solo decisiones, evidencia, cambios, riesgos y pendientes vigentes;
  descartar del contexto conversacion repetida y razonamientos ya resueltos.
- La compactacion nunca reemplaza actualizar los documentos antes de perder
  contexto importante.

## Flujo de un ticket

1. Confirmar area, alcance y criterio de terminado.
2. Revisar Git de forma liviana y proteger cambios ajenos.
3. Diagnosticar la causa raiz antes de editar.
4. Trabajar con la menor cantidad posible de archivos.
5. Validar de forma dirigida y proporcional.
6. Actualizar el `HANDOFF.md` del area y, si cambia una prioridad global,
   `docs/PROJECT_CONTROL.md`.
7. Cerrar con resultado, pruebas reales y pendiente exacto. Sin diario narrativo.

## Reporte a Direccion

- Al necesitar una decision, quedar bloqueado, cambiar un riesgo global o terminar
  una fase/ticket, actualizar primero el handoff del area.
- Si el chat dispone de coordinacion saliente, enviar un resumen a `Jefe`. No
  asumir que todos los chats pueden iniciar mensajes hacia `Jefe`.
- Si no dispone de ella, pedir al usuario solamente: `Decile a Jefe que revise
  <AREA>`. El usuario no debe copiar reportes ni conversaciones.
- `Jefe` inspecciona el chat y el repositorio, y puede responder directamente.

## Limites

- Un chat especializado mantiene una sola area y un ticket activo por vez.
- Direccion Tecnica decide prioridades, cruces entre areas y orden de trabajo.
- Ningun ticket propuesto autoriza codigo hasta el OK explicito del usuario.
- Docs solamente: no compilar, versionar ni publicar.
- DAG usa exclusivamente `scripts/dag_gradle.sh <tareas>`.
- No push, PR, publicacion, Production ni borrado sin OK explicito.

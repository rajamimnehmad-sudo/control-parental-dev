# AI CODEX HANDOFF

## CHROME-VISUAL-ATOMIC-SCROLL-05 — INVESTIGACION PASS

- Fecha: 2026-08-21.
- Owner: Proteccion Android.
- Rama documental aislada: `investigation/chrome-visual-atomic-scroll-05`.
- Base documental: `4bbc8ad02376120e16ac9f931b8e563c7d7d1d43`.
- Base de producto investigada: `88ca10f605ea297c0e303bc35e04ab45937ec636`.
- Evidencia de entrada: FAILED fisico A23 de Chrome + GloshIA R3.1 real.
- Alcance ejecutado: lectura de coordinacion, codigo y evidencia; sin codigo,
  build, APK, ADB, A23, Production, merge ni cambios en Control Center.
- Colision al cierre: la rama de evidencia avanzo de `4bbc8ad` a `7cd1e633`
  con siete commits concurrentes de Chrome Visual. No pertenecen a este ticket,
  no fueron integrados ni auditados; la publicacion se aislo desde `4bbc8ad`.

### Causa raiz

Chrome Visual no precubre scroll con ventana/viewport estables. Captura y
detecta despues del render; cubre dentro de un `for` serial, procesa como maximo
cuatro de ocho mosaicos cambiados y usa ventanas separadas con remove/add. Un
evento coalescido durante baseline tampoco invalida el epoch antes de retornar.
La identidad de pagina se basa solo en el titulo.

Los lotes de 4.080 y 7.019 s gastaron solo 112 y 118 ms en captura. El resto fue
el pipeline serial de crop/preproceso/R3.1/commits, con hasta cinco inferencias
por region incierta; falta telemetria para separar esas subfases con exactitud.

### Decision

Agregar `TYPE_VIEW_SCROLLED` o reposicionar rectangulos no garantiza cero
exposicion: las senales disponibles son posteriores al render y Android no
ofrece observacion tactil pasiva sin alterar el input.

La solucion implementable para Chrome estricto es reemplazar la capa de overlays
por una unica superficie persistente, doble-buffer, que presente solo la ultima
generacion saneada o gris; captura y analiza por debajo y hace commits atomicos.
Mosaicos dirty completos son la frontera; nodos accesibles solo refinan. Si este
costo de RAM/scroll escalonado no es aceptable, el fallback correcto es DAG.

### Siguiente paso minimo — no ejecutado

Implementar primero contratos/FSM/replay y telemetria por fase; luego el host
atomico y analisis incremental. No generar APK ni repetir A23 hasta que los gates
automaticos demuestren cero commits vencidos, ocho de ocho dirty cubiertos y
ningun camino remove/add. Video/DRM permanece fuera de alcance.

Informe completo:
`docs/areas/protection/INVESTIGATION_2026-08-21_CHROME-VISUAL-ATOMIC-SCROLL-05.md`.

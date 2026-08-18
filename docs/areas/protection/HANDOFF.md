# PROTECCION ANDROID — HANDOFF

## Mision

Aplicar reglas de forma resistente: Accessibility, VPN/DNS, timers, policy y
antimanipulacion.

## Estado

- `GLOSHIA-SHARED-CORE-01` esta verde: R3.1 ahora tiene un unico modulo AAR
  compartido por DAG y App Usuario, con paridad bit a bit de tensor, decisiones
  doradas y score fisico A23. ARM32 conserva App Usuario pero Chrome Visual queda
  explicitamente no disponible y debe degradar a DAG. Evidencia:
  `EVIDENCE_2026-08-18_GLOSHIA-SHARED-CORE-01.md`.
- `CHROME-VISUAL-00-PROBE` es GO en A23/API 34: Chrome se identifica, la captura
  de ventana funciona a ~2 Hz y sigue viendo el contenido debajo de un
  accessibility overlay. Tres comparaciones, incluido scroll, pasaron sin
  crash/ANR. Evidencia: `EVIDENCE_2026-08-18_CHROME-VISUAL-00-PROBE.md`.
- El host SurfaceControl moderno falla en Samsung; el overlay publico tradicional
  funciona. Multiventana y rotacion quedan para endurecimiento.
- Accessibility y VPN tienen archivos cercanos a 1.000 lineas.
- La politica debe seguir evaluable y testeable sin depender de UI.

## Siguientes tickets

1. Crear `ChromeVisualController` y filtrar imagenes de una fixture controlada.
2. Endurecer scroll, identidad y regiones dinamicas sin reglas por sitio.
3. Construir matriz de fallos cerrados y recuperacion por OEM.

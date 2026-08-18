# PROTECCION ANDROID — HANDOFF

## Mision

Aplicar reglas de forma resistente: Accessibility, VPN/DNS, timers, policy y
antimanipulacion.

## Estado

- `CHROME-VISUAL-00-PROBE` es GO en A23/API 34: Chrome se identifica, la captura
  de ventana funciona a ~2 Hz y sigue viendo el contenido debajo de un
  accessibility overlay. Tres comparaciones, incluido scroll, pasaron sin
  crash/ANR. Evidencia: `EVIDENCE_2026-08-18_CHROME-VISUAL-00-PROBE.md`.
- El host SurfaceControl moderno falla en Samsung; el overlay publico tradicional
  funciona. Multiventana y rotacion quedan para endurecimiento.
- Accessibility y VPN tienen archivos cercanos a 1.000 lineas.
- La politica debe seguir evaluable y testeable sin depender de UI.

## Siguientes tickets

1. Extraer GloshIA R3.1 a un motor compartido sin cambiar decisiones de DAG.
2. Crear el adapter Chrome Visual fuera del servicio gigante.
3. Construir matriz de fallos cerrados y recuperacion por OEM.

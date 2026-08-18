# PROTECCION ANDROID — HANDOFF

## Mision

Aplicar reglas de forma resistente: Accessibility, VPN/DNS, timers, policy y
antimanipulacion.

## Estado

- `CHROME-VISUAL-01-IMAGES` es GO controlado: App Usuario capturo Chrome y R3.1
  bloqueo fisicamente una sonda completa sin guardar pixeles. Chrome real no
  expone siempre nodos de imagen; una auditoria reemplazo el fallback inicial
  por ocho mosaicos acotados, cobertura inmediata, cache efimero e identidad
  estricta, sin reglas por sitio. Sigue DEV-only y no es candidato de producto.
  Evidencia: `EVIDENCE_2026-08-18_CHROME-VISUAL-01-IMAGES.md`.
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

1. Endurecer scroll, lazy load, SPA, canvas, iframes y cambios de ventana sin
   reglas por sitio (`CHROME-VISUAL-02-REAL-WEB`).
2. Validar fisicamente la matriz final de mosaicos y reducir cobertura amplia
   solo con evidencia de fuga cero.
3. Construir matriz de fallos cerrados y recuperacion por OEM.

# Registro del candidato vigente de DAG y GloshIA

Fecha: 2026-08-11. Este archivo describe el runtime local candidato; los candidatos
historicos permanecen en Git y en sus informes de laboratorio.

## Produccion local

- navegador normal: flavor `dev`, paquete `com.contentfilter.dagbrowser.dev`;
- navegador de diagnostico: flavor `diagnostic`, paquete
  `com.contentfilter.dagbrowser.diagnostic.dev`;
- ambos ejecutan la misma decision visual; Diagnostic solo agrega
  observabilidad acotada;
- no existe un flavor Android LAB ni una politica de redaccion parcial.

## Modelo unico

- nombre publico: GloshIA Visual;
- version funcional: R3.1;
- asset: `dag-model/tinyclip-r3-head-hybrid-int8.onnx`;
- SHA-256: `c8b64af8092d3718c58736a511c996d0d443dacf3eaa74620b1e5af439a3cd48`;
- runtime: ONNX Runtime Android 1.27.0, CPU, intra-op 2, inter-op 1;
- politica: `dag-36`, sin cambios de umbral;
- si R3.1 no abre o falla, la decision es cerrada. El APK no cambia
  silenciosamente a R1 ni contiene otro modelo visual.

## Laboratorio local

`tools/gloshia_lab/` es la entrada vigente para informes, corpus adjudicado y
evaluacion. `scripts/dag_v3_model/` contiene herramientas y antecedentes; un
informe historico no se interpreta como runtime Android. `final_sealed`
permanece cerrado.

## Contrato operativo

Todo raster estatico soportado que alcance la compuerta produce `model_allow`,
`model_filter` o un cierre seguro con razon estructurada. No existen reglas por
sitio, URL, dominio, comercio, telefono o tamaño renderizado. Los limites de
bytes, dimensiones, tiempo y memoria son defensas fail-closed, no permisos.

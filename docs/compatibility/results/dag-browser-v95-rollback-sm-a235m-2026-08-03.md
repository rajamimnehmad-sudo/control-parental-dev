# DAG 95 - rollback de DAG 94

Fecha: 2026-08-03  
Dispositivo: Samsung SM-A235M (A23), Android 14  
APK: DEV, `versionCode=95`, `versionName=0.68.1-dev`  
Extension: `1.50.0`

## Motivo

El usuario informo que DAG 94 no resolvio el comportamiento esperado y solicito
revertirlo. Android no permite instalar un `versionCode` menor encima de 94, por
lo que el codigo funcional se restauro al punto DAG 92 y se empaqueto como DAG
95.

## Alcance

- Reversion completa del commit funcional de DAG 94.
- `barrier.js`, `background.js` y `barrier.css` coinciden con el pipeline de
  DAG 92.
- Sin cambios en GloshIA R1, pesos, umbrales o decisiones.
- Sin cambios en App Usuario o App Admin.
- DAG 94 queda retirado como version vigente.

## Verificacion

- 14 pruebas WebExtension aprobadas.
- Unit tests Android, Ktlint, Lint y build DEV aprobados.
- Android confirmo DAG 95 instalado en el SM-A235M.
- APK: `121377265` bytes.
- SHA-256:
  `b3308326d9af8bf84e808f319739344f51db04603e55bfd60c39724a389d6b8e`.
- Publicacion DEV actualizada atomicamente; Production intacta.

Resultado: `ROLLBACK PUBLICADO`. DAG 95 es el nuevo punto vigente; DAG 94 queda
solamente como registro historico.

# DAG Browser 58 - gate local previo al A23

Fecha: 2026-07-31

## Candidato

- fuente: `main` local integrado;
- paquete: `com.contentfilter.dagbrowser.dev`;
- versionCode: `58`;
- versionName: `0.38.0-dev`;
- WebExtension incorporada: `1.28.0`;
- APK: `app-dag-browser/build/outputs/apk/dev/debug/DagBrowser-dev-debug.apk`;
- tamaño: `121375543` bytes;
- SHA-256:
  `1246fb68e45ce5af422b48a417641456b22ae5291098e27737dc5411369a1444`;
- certificado SHA-256:
  `d51bc0dabd280ce1b0f098ae168eb57758faeba301156cde835737835f8a8832`.

`apksigner` verifico firma v2, un firmante y el certificado historico esperado
para DAG DEV.

## Gates aprobados

- WebExtension: 19 pruebas aprobadas, cero fallos;
- DOM externo: una prueba opt-in omitida y no contada como aprobada;
- Android: 147 unitarios aprobados, cero fallos y cero omitidos despues del
  contrato global que prohibe excepciones por comercio o modelo fisico;
- `ktlintCheck`: correcto;
- `lintDevDebug`: correcto;
- `compileDevDebugAndroidTestKotlin`: correcto;
- `assembleDevDebugAndroidTest`: correcto;
- `assembleDevDebug`: correcto;
- sintaxis del laboratorio Bash/Python: correcta;
- `git diff --check`: correcto.

La advertencia de SDK XML v4 frente a tooling que entiende hasta v3 no detuvo
ningun gate ni cambia el artefacto; queda como mantenimiento del SDK local.

## Estado posterior del gate fisico

Este documento conserva el corte estatico previo al telefono. El 2026-08-01 se
recupero ADB, se instalo el mismo APK, se borro exclusivamente el perfil DEV de
DAG con autorizacion, se confirmo el rol de navegador y se completo la matriz
fisica y el benchmark ONNX. El cierre y las limitaciones estan en
`dag-browser-v58-physical-gate-sm-a235m-2026-08-01.md`.

No se toco Chrome ni otra app y no se hizo push, PR ni publicacion.

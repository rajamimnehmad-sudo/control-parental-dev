# CALIDAD Y RELEASES — HANDOFF

## Mision

Pruebas, CI, compatibilidad, versionado, artefactos y publicaciones controladas.

## Estado

- El comando Gradle raiz generico dispara variantes beta/prod/compat y no sirve
  como validacion cotidiana.
- Tests DEV dirigidos de Usuario/Admin pasaron durante la auditoria.
- DAG debe validarse con su wrapper aislado.
- Prelaboratorio local disponible: `Glosh_DAG_API_35`, Android 15/API 35 AOSP
  ARM64, arranque headless acelerado por Apple M2 y snapshot rapido verificado.
  Red, ADB, captura de pantalla y OpenGL ES 3.0 quedaron operativos. Ocupa
  aproximadamente 3,4 GB entre imagen y AVD; quedaron 13 GB libres, por lo que
  no se agregaran otras imagenes Android sin revisar espacio.
- Web y Edge Functions necesitan cobertura y CI.
- Existe un worktree DAG historico limpio, 226 commits atras y sin commits unicos.

## Siguientes tickets

1. Crear matriz de comandos rapidos por area y gate completo por release.
2. Agregar CI web y pruebas de contratos Edge.
3. Proponer retiro del worktree y caches solo con confirmacion.

## Regla

Construido, instalado y publicado son estados distintos; registrar evidencia de
cada uno. El emulador sirve como prevalidacion repetible, no como aprobacion de
audio, fluidez, compositor ni rendimiento fisico. Ninguna operacion remota sin OK.

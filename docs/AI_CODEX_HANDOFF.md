# AI CODEX HANDOFF

## CHROME-GLOSHIA-A23 — FAILED

- Fecha: 2026-08-20/21.
- Area owner: Proteccion Android.
- Rama publicada: `review/chrome-visual-closure-batch-04`.
- Worktree probado: `/private/tmp/glosh-chrome-visual-closure.7v8qi8`.
- HEAD del worktree durante la prueba: `2ce17b312ae0ef2278c74c78e5a2f61aae41e62f` (solo agrega un handoff documental previo).
- Base y codigo de producto probados: `88ca10f605ea297c0e303bc35e04ab45937ec636`.
- APK: `app-user/build/outputs/apk/dev/debug/app-user-dev-debug.apk`, `versionCode=311`, SHA-256 `e412dea28859f52743151bffd8a66256bdb23e7dece4eee73437169b9ca1c536`.
- Dispositivo: Samsung A23 `SM-A235M`, Android 14 / API 34, `arm64-v8a`.
- Chrome: `151.0.7922.137`.
- Motor: GloshIA Visual R3.1 ONNX real, compartido; probe deshabilitado. Modelo SHA-256 `c8b64af8092d3718c58736a511c996d0d443dacf3eaa74620b1e5af439a3cd48`.

### Resultado

**FAILED.** La implementacion detecta, cubre y clasifica contenido real, pero no
demuestra proteccion usable sin exposicion visual inaceptable durante scroll y
lazy-load.

### Matriz ejecutada

- Wikipedia `Cat`: carga estatica, tres scrolls hacia abajo y dos hacia arriba.
- Google Images `mountain landscape photography`: multiples imagenes permitidas,
  dos scrolls hacia abajo y uno hacia arriba.
- Google Images `bikini fashion catalog`: contenido contrastante y un scroll.
- Unsplash `nature`: pagina dinamica/lazy, dos scrolls hacia abajo y uno hacia
  arriba.
- En cada bloque se recolectaron captura nativa, screenrecord, Logcat dirigido y
  al cierre meminfo/cpuinfo; Accessibility se restauro a su estado inicial.

### Funciono

- Captura de la ventana de Chrome y overlay inicial.
- GloshIA R3.1 real permitio paisajes (`allowed=9 blocked=0`) y bloqueo regiones
  del conjunto contrastante (`allowed=3 blocked=6`).
- Unsplash seguro recupero visibilidad y los gestos siguieron llegando a Chrome.
- Sin crash ni ANR en los controles recolectados.

### Fallo

- Wikipedia mantuvo grandes mosaicos negros/bordo sin decision terminal antes
  de los scrolls.
- Google Images seguro tardo `4.249 s` en la decision inicial; los scrolls y el
  regreso dejaron casi todo el viewport negro durante varios segundos.
- La consulta contrastante tardo `4.080 s`; despues del scroll quedaron imagenes
  visibles arriba y una cobertura bordo amplia/desalineada abajo.
- Unsplash seguro tardo `7.019 s`; el verificador dejo de emitir antes de los
  scrolls lazy, por lo que no se demostro proteccion continua del contenido nuevo.
- La cobertura actual es por mosaicos, no el placeholder gris por imagen de DAG.

### Causa demostrada

En scroll con ventana, pagina y viewport estables no se exige un nuevo baseline
y no se precubre. Los overlays existentes conservan coordenadas de pantalla
mientras el contenido se desplaza; los mosaicos cambiados se cubren recien
despues de settle, captura, deteccion y evaluacion secuencial. Esto coincide con
la exposicion, desalineacion y espera negra registradas.

### Cambios del ticket

- Codigo de producto: ninguno.
- Build/version: ninguna modificacion; se reutilizo el APK existente.
- Publicacion: solo handoff, evidencia, screenshots y logs sanitizados.

### Siguiente paso minimo — no ejecutado

Abrir un ticket de Proteccion Android para cobertura atomica de scroll/cambio
visual: precobertura antes de presentar contenido nuevo, actualizacion geometrica
sin retirar primero la cobertura segura y replay automatico anti-flash. No
avanzar a video/DRM ni repetir hardware hasta cerrar ese gate.

Evidencia detallada:
`docs/areas/protection/EVIDENCE_2026-08-21_CHROME-GLOSHIA-A23.md`.

# CHROME-STOCK-PRE-RENDER-SHIELD-18-CLOSURE-01

Fecha: 2026-08-28. Dispositivo: Samsung A23 `SM-A235M`, Android 14/API 34,
Chrome oficial `152.0.7977.64` (`797706404`).

## STATUS

**BLOCKED — ARCHITECTURAL SECURITY/COMPATIBILITY CONFLICT.**

El gate de factibilidad encontró una ruta visual normal que no atraviesa el
Byte Gate y sigue disponible después de que el bootstrap parser-first terminó:
HTML ordinario (`div`/`span`) más CSS ordinario (`display:grid` y
`background-color`) puede codificar y presentar cualquier raster. No usa body
de imagen, `data:`, `blob:`, Canvas, SVG, WebGL, worker ni otra API visual
especial.

El mismo A23 demostró las dos mitades causales:

- perfil compatible: conserva CSS/JS normales, pero el raster prohibido queda
  visible post-`BOOT_READY`;
- perfil estricto: elimina el raster sólo bloqueando también el CSS y el
  JavaScript normales del sitio.

Stock Chrome no expone al proxy/bootstrap una frontera semántica que distinga
"CSS legítimo" de "CSS que codifica una fotografía". Reescribir o prohibir
toda capacidad equivalente (`background-color`, bordes, sombras, gradientes,
geometría y mutación de DOM) cerraría el escape, pero deja de cumplir el
requisito de Chrome normal. Analizar el raster resultante volvería a depender
de screenshot/compositor, ruta ya cerrada por el ticket.

Por el STOP obligatorio no se ejecutaron R3.1 selective, Google Images,
Frávega, Mimo ni normalidad real-web. El blocker aparece antes y no corresponde
maquillarlo con más hooks, temporización o tuning.

## Git y artefacto

- Base funcional: `a5440ce43157130a1ba25914d73ca588cb89c1ca`.
- R2A preservado: `999a5cc2a3982bad0fad3f34baeba54b30d4fd8f`.
- R2B authority preservada: `342ff3b2af485a4bc70d7243615a8be97b432ffa`.
- Functional proof HEAD: `17cac82e581e69c81fe5f435498269b313daa059`.
- Commits funcionales:
  - `c62a7fa41b91dedd772b362cc7630c4c36828883`, Byte Gate de auditoría,
    transformer/fixture DEV, telemetría, tests y analizador;
  - `17cac82e581e69c81fe5f435498269b313daa059`, corrección localizada de
    sintaxis del bootstrap encontrada por el primer gate inválido.
- Rama de trabajo: `work/chrome-stock-prerender-shield-18-closure-01`.
- Review: `review/chrome-stock-prerender-shield-18-closure-01-triage`.
- DEV381, package `com.contentfilter.user.dev`, `versionName=1.0.1-dev`.
- APK: `159156077` bytes; SHA-256
  `399265e502a4dbd10c8c74be342d0c228d40eaa67bd4b61d01261390ef1753c7`.
- APK instalado update-in-place con el mismo SHA-256; certificado debug
  SHA-256 `d51bc0dabd280ce1b0f098ae168eb57758faeba301156cde835737835f8a8832`.
- Modelo preservado: GloshIA Visual R3.1,
  `c8b64af8092d3718c58736a511c996d0d443dacf3eaa74620b1e5af439a3cd48`.

## Arquitectura/proof implementado

El delta es DEV y falsificable; no habilita comportamiento productivo.

### Byte Gate

`REPLACE_ALL_NETWORK_VISUALS` actúa después de que 11B identifica un candidato
visual y antes del writer hacia Chrome. Devuelve un PNG de auditoría local,
distinto del placeholder normal, sin fingir un verdict R3.1. La métrica
`networkVisualRawDelivered` se toma en la frontera final de entrega.

Contrato observado en la corrida final:

```text
networkVisualCandidates=6
networkVisualReplaced=6
networkVisualRawDelivered=0
fixture network sentinel requests=3
audit placeholder SHA-256=74d4467bbf6833db4eeab998495b7dfc50db3b1f04f407704b839ad3f4b25582
raw network sentinel SHA-256=ee17e3f62edde61823ce35732ea168f4f5986bff347ccfac063eca7f4009ee1b
```

Los tres frames controlados mostraron el placeholder de auditoría; ninguno
mostró el PNG raw. Esto valida el gate para los candidatos identificados, no
convierte recursos passthrough desconocidos en candidatos por decreto.

### Document transformer / CSP / bootstrap

El proof transforma sólo documentos DEV UTF-8 acotados y controlados:

- conserva doctype y exige un `<head>` seguro antes de tokens ejecutables;
- inserta CSS + script parser-blocking con nonce aleatorio por documento;
- invalida headers de entidad y entrega `no-store`;
- instala antes del sitio los hooks de sinks locales expresamente incluidos;
- emite `BOOT_READY:<PROFILE>:<sequence>` después de terminar el bootstrap;
- publica además un ready token aleatorio en closed shadow para el posterior
  experimento AX, sin loggear el token completo.

El perfil `compatible` conserva `script-src 'self'`, `style-src 'self'`, XHR y
fetch. El perfil `strict` sólo permite el bootstrap/style nonce. Esa diferencia
es deliberada: prueba si el escape puede cerrarse sin destruir normalidad.

El binding AX foreground productivo no se implementó ni se acreditó: el gate
arquitectónico falló antes. `BOOT_READY` sí demuestra que el bootstrap del
documento servido terminó antes de observar el frame post-ready.

## Matriz adversarial mínima decisiva

Patrón fijo: bitmask de 64 celdas
`1101001110100101110001101001111001011010001111001001011011100001`,
rojo `#dc1430`, negro `#000000`, borde amarillo `#ffee00`. El fixture estático
no contiene JavaScript original y su stylesheet no contiene `url()`, imagen,
gradiente, Canvas, SVG, `data:` ni `blob:`.

| Caso | Bootstrap | Red | CSS/JS normal | Raster detectado | Resultado |
| --- | --- | --- | --- | --- | --- |
| compatible static | `BOOT_READY:COMPATIBLE:1` | placeholder; raw 0 | CSS externo permitido; sin JS original | 64/64, bbox `[39,951,802,1714]` | ESCAPE |
| compatible dynamic | `BOOT_READY:COMPATIBLE:2` | placeholder; raw 0 | script + fetch + `SITE_JS_NORMAL_DYNAMIC_CSS` | 64/64, mismo bbox | ESCAPE |
| strict dynamic | `BOOT_READY:STRICT:3` | placeholder; raw 0 | CSS/JS del sitio bloqueados | 0/64, sin bbox | seguro pero incompatible |

Hashes de los frames temporales de evidencia:

- compatible static:
  `33c4b15c9dc9bc737ab83fb9d843024c030c3c9f39807b053f62414bb08d0c11`;
- compatible dynamic:
  `2ab47b00618345804b44e1e4f6a1148d29a3d615477cfd4f9c5fb6c22bc519da`;
- strict:
  `2094a1ca68564755889e99105e746f1488404b78e8e95f2bf44a5a9cde176bb5`.

El analizador determinista registró sólo dimensiones, SHA, conteos de colores,
bbox y bitmask. Las capturas se usaron exclusivamente como evidencia del gate,
nunca como autoridad de release, y se eliminaron después de extraer esas
métricas.

`sentinelVisibleFrames` no puede ser cero: existen al menos dos frames reales
post-ready con el sentinel completo. Un solo frame bastaba para FAIL.

## Escapes encontrados y fixes

1. El primer intento físico no emitió `BOOT_READY` por una comilla no escapada
   en el literal CSS del bootstrap. Se corrigió, se añadió regresión y se
   reconstruyó/reinstaló DEV381. Ese intento no se usó como gate canónico.
2. Gate canónico: ordinary DOM/CSS raster post-ready. No es un hook faltante de
   Canvas/blob/shadow; es una capacidad normal del renderer.
3. La variante estricta cierra el raster, pero bloquea el stylesheet, el script
   y el fetch normales. Por tanto no satisface la normalidad exigida.

No se intentaron más rAF, sleeps, polling, screenshots como authority, markers,
thresholds ni otro modelo.

## Validación automática

Exit code `0` antes del gate físico:

```text
:app-user:testDevDebugUnitTest (H18 focalizados + 11B/13A/R2A relevantes)
:feature-accessibility:testDebugUnitTest
:gloshia-visual-core:testDebugUnitTest
:app-user:runKtlintCheckOverDevSourceSet
:app-user:runKtlintCheckOverTestDevSourceSet
:app-user:compileDevDebugKotlin
:app-user:lintDevDebug
:app-user:assembleDevDebug
python3 -m py_compile tools/chrome_stock_prerender_shield/analyze_h18_frame.py
git diff --check
```

Después del fix de sintaxis se repitieron en el HEAD funcional final:

```text
ChromePreRenderDocumentTransformerTest
ChromeStockPreRenderShieldFixtureTest
ktlint dev/testDev
compileDevDebugKotlin
lintDevDebug
assembleDevDebug
git diff --check
```

El Byte Gate mantiene outcomes separados (`AuditReplaced` no es BLOCK de
GloshIA) y sus tests cubren selective, replace-all y detección de entrega raw.
R2A/R2B/11B/GloshIA no fueron modificados.

## R3.1 selective y real-web

**No ejecutados por STOP arquitectónico.** Activarlos no cambia el hecho de que
la página puede generar el mismo raster sin recurso visual de red. No hubo
tuning de fixture, modelo, threshold o label, ni navegación de H18 a Google
Images/Frávega/Mimo.

## Health, ownership y rollback

Ventana canónica final, session `4ae7b33a`, Chrome foreground oficial, sin
CDP/DevTools.

```text
failures=0
proxyQueueRejects=0
protectFailure=0
quicAttempts=0
directTcpAttempts=0
crash/ANR/OOM=0/0/0
phase=stopped rollback=complete cache=cleared
status=inactive
ownedFdResources=0
activeProtectedUdpSockets=0
transportRuntime=ready
chromeSuspended=true
```

- No se invocó el pipeline de screenshot/region: no quedaron full frames,
  crops ni prepared RGB.
- Device Owner y estado `Affiliated` preservados.
- Accessibility habilitada/bound preservada.
- Datos/firma preservados por update-in-place; `ceDataInode=1239519` antes y
  después.
- Rotación restaurada a `lock 0`.
- Crash buffer vacío y sin FATAL/ANR/OOM en la ventana canónica.

## Clases fail-close / no evaluadas

El delta no relaja las clases ya fail-close de 11B (formatos/encoding/partial/
304 sin authority). SVG, animación, PDF, video/GIF/DRM y Service Worker no se
productizaron ni se declararon resueltos en H18. La matriz adversarial extensa,
cache/SW, managed policies, latencias y Chrome normality quedaron correctamente
sin ejecutar tras el STOP temprano.

## Residual y decisión arquitectónica

El Byte Gate pre-render sigue siendo valioso y demostrado para cuerpos visuales
de red identificados. No alcanza para el objetivo terminal porque el documento
puede sintetizar píxeles con primitivas indispensables para la web normal.

Para control visual completo con compatibilidad normal se necesita una frontera
de autoridad dentro del renderer que observe/controle el raster antes de
presentarlo, es decir poseer o modificar el navegador/renderer (Route B o
equivalente). Esto es un trigger técnico, no una decisión de producto y no se
implementó en este ticket.


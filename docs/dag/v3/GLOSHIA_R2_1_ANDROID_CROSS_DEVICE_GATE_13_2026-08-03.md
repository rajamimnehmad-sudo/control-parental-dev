# GloshIA Visual R2.1 — gate Android en segundo dispositivo

Ticket: `GLOSHIA-R2.1-ANDROID-CROSS-DEVICE-GATE-13`

Fecha: 2026-08-03

Resultado: **GO de compatibilidad entre dispositivos; HOLD para abrir
`final_sealed` o integrar**

## Alcance

Se ejecutó sin modificaciones el mismo APK de laboratorio, candidato INT8,
R1, metadatos y tensores congelados del ticket
`GLOSHIA-R2.1-ORT-ANDROID-HARNESS-12`. No se reentrenó, no se cuantizó, no se
cambió el umbral `0,4`, no se abrió `final_sealed` y no se modificaron DAG,
Android productivo, Supabase ni Production.

Dispositivo nuevo: Samsung S22 Ultra `SM-S908E`, Android 16 / API 36. Runtime:
ONNX Runtime Android CPU `1.27.0`.

## Identidades verificadas

- Candidato `r2.1-candidate-02-int8.onnx`: 8.756.367 bytes; SHA-256
  `c212d005db271bebfb3fb80aade4c056334e0f4f07f2f1543976050f8c8afa3c`.
- R1: SHA-256
  `2d52bd9e5eb4cd448cb0d64a784b2ee6f761ad20e890c57b898fd7991d29a9ee`.
- Tensores congelados: SHA-256
  `f0e63a2b688ae81f8759dd1d027e6484f14483d781b5dfa3589acdf53b97b9b3`.
- Resultado privado S22: SHA-256
  `9af38b73f858b3cee51c4da9700c6fab54b7cb522a6cbbc5cad268f4702170a1`.

## Resultado semántico

El candidato produjo en el S22 exactamente las mismas probabilidades,
decisiones, matriz y desacuerdos que en el A23, al excluir únicamente métricas
dependientes del dispositivo. La comparación normalizada fue idéntica.

| Resultado candidato | S22 |
|---|---:|
| Muestras | 119 |
| allow→allow | 90 |
| allow→filter | 11 |
| filter→allow | 0 |
| filter→filter | 18 |
| Falsos permisos | 0/18 |
| Falsos filtros | 11/101 |
| Salidas no finitas | 0 |
| Desacuerdos frente a FP32 | 1/119 |

El único desacuerdo volvió a ser `wikimedia:159527259`: etiqueta humana
`allow`, FP32 `filter` con 0,404408 e INT8 Android `allow` con 0,384544. No es
un falso permiso humano y se reprodujo de forma idéntica en ambos teléfonos.

## Rendimiento observado

| Ejecución S22 | p50 | p95 | máximo | PSS pico |
|---|---:|---:|---:|---:|
| R2.1 candidato, 119 | 47,75 ms | 63,89 ms | 94,76 ms | 191.046 KB |
| R1, 119 | 203,00 ms | 254,62 ms | 369,11 ms | 176.890 KB |

El candidato fue además más rápido en el S22 que en el A23, donde había dado
p50 132,50 ms y p95 137,72 ms. La comparación R2.1/R1 del S22 es evidencia de
laboratorio secuencial, no una promesa de rendimiento productivo: el teléfono
tenía carga de otras aplicaciones y se observó a 35,1 °C. La conclusión fuerte
de este gate es compatibilidad y determinismo entre dispositivos.

## Cierre

- `ConvInteger` ejecuta en A23/Android 14 y S22/Android 16 con ORT 1.27.0 CPU.
- Las decisiones R2.1 fueron idénticas entre ambos dispositivos.
- R1 continúa oficial; R2.1 no se integró ni se publicó.
- `final_sealed` continúa cerrado.
- El APK de laboratorio fue desinstalado y los cuatro archivos temporales se
  retiraron del S22.

Próxima decisión separada: congelar formalmente artefacto, umbral y criterio de
aceptación orientado a seguridad antes de autorizar la única apertura de
`final_sealed`. La equivalencia bit a bit con FP32 no debe modificarse de forma
retroactiva dentro de este ticket.

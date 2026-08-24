# CHROME-GLOSHIA-REALTIME-SCHEDULER-14A — diseño preparado

Fecha: 2026-08-24
Estado: **PREPARED / NO EJECUTAR TODAVÍA**

Objetivo: que Chrome se sienta normal aun con páginas densas en imágenes, sin sacrificar la propiedad de seguridad `BLOCK/UNKNOWN original delivered = 0`.

Este diseño NO cambia el modelo GloshIA R3.1 ni sus thresholds. Organiza cómo se priorizan, deduplican y ejecutan las inferencias.

---

## 1. Problema

En una página real pueden aparecer decenas o cientos de candidatos visuales a la vez. Si cada recurso se procesa estrictamente en orden de llegada:

- una imagen enorme fuera de viewport puede retrasar una pequeña visible;
- el scroll puede acumular trabajo obsoleto;
- varias URLs pueden devolver los mismos bytes;
- la misma imagen puede pedirse varias veces por CSS/srcset/cache/retry;
- el usuario percibe placeholders/esperas aunque el throughput total sea suficiente.

La meta no es maximizar imágenes/segundo a cualquier costo. Es minimizar **latencia visible del viewport actual** manteniendo bounded memory y cero bypass.

---

## 2. Invariantes

1. Nunca liberar un original antes de decisión SAFE.
2. BLOCK/UNKNOWN nunca se vuelven SAFE por timeout.
3. Un trabajo stale se cancela o descarta, no publica resultado a una generación nueva.
4. Colas bounded; nunca crecimiento ilimitado.
5. El scheduler no toca thresholds/model/preprocessing.
6. La prioridad afecta orden, no decisión.
7. La cache sólo aplica si content identity + generations coinciden.

---

## 3. Pipeline

```text
ImageContentAuthority
       |
       v
ImageWorkAdmission
       |
       +--> cache hit SAFE/BLOCK/UNKNOWN
       |
       +--> dedupe in-flight by content identity
       |
       v
ViewportAwarePriorityQueue
       |
       v
DecodePool (bounded)
       |
       v
InferenceScheduler
       |
       v
GloshIA R3.1 sessions
       |
       v
DecisionPublisher
```

---

## 4. Identidad de trabajo

Dedupe por una identidad fuerte, no URL sola.

Preferencia:

```text
contentHash
+ modelSha
+ policyVersion
+ preprocessingVersion
+ contentAuthorityVersion
```

Antes de conocer hash completo puede existir una identidad provisional:

```text
finalUrl + validators + generation
```

Al completar body/hash, fusionar con trabajo equivalente si existe.

---

## 5. Prioridades

Definir clases, no números mágicos dispersos.

### P0 — visible/critical

- imagen candidata dentro del viewport actual;
- imagen de hero/above-the-fold;
- recurso que bloquea transición de superficie/lease.

### P1 — near viewport

- próximo viewport por scroll probable;
- lazy image ya solicitada pero aún fuera por margen pequeño.

### P2 — background page

- imágenes cargadas de pestaña activa fuera de viewport.

### P3 — background tab/prefetch

- pestaña no visible;
- speculative/prefetch.

Si no existe señal fiable de viewport desde data-plane, usar prioridad provisional y corregir mediante eventos de Accessibility/presentation epoch.

---

## 6. Aging y fairness

No permitir starvation permanente de P2/P3.

Aplicar aging bounded:

- un trabajo espera cierto máximo;
- puede subir una clase;
- nunca desplaza un P0 recién visible si existe capacidad limitada crítica.

Fairness debe medirse; no adivinar thresholds finales.

---

## 7. Cancelación/stale

Cada trabajo lleva:

- sessionId;
- navigationGeneration;
- presentationEpoch;
- content generation;
- model/policy/preprocess generation.

Cancelar o no publicar cuando:

- navigation nueva hace irrelevante el recurso;
- tab/window cambia;
- viewport epoch lo vuelve obsoleto;
- proxy/session revocada;
- model/policy generation cambia.

Un trabajo ya en inferencia puede terminar si cancelar el runtime es inseguro; el resultado se descarta al publicar si generation no coincide.

---

## 8. Decode pool

Decode consume memoria y CPU distinta de inferencia.

Separar pool bounded:

- pocos workers;
- memoria total presupuestada;
- semaphore por decoded pixel budget;
- no decodificar simultáneamente varias imágenes gigantes.

Admission considera:

```text
estimatedDecodedBytes = width * height * channels
```

con overflow-safe arithmetic.

---

## 9. Inference concurrency

No asumir que más sesiones ONNX = más rápido.

Benchmarkear en A23/S22:

- 1 sesión / 1 in-flight;
- 1 sesión serial con decode paralelo;
- 2 sesiones;
- micro-batching si el modelo/export lo soporta sin cambiar outputs.

Elegir configuración por:

- viewport p95;
- throughput;
- PSS/native memory;
- temperatura;
- batería.

No usar batching que espere deliberadamente si empeora first visible image.

---

## 10. Batching policy

Si batch es técnicamente soportado:

- P0 puede ejecutar inmediatamente sin esperar batch completo;
- P1/P2 pueden agruparse por ventana de pocos ms medida;
- mismo tensor shape/preprocessing;
- resultado individual asociado por immutable workId.

Si no existe beneficio físico claro, mantener inferencia individual.

---

## 11. Backpressure

Admission queue bounded.

Cuando está llena:

- nunca liberar original;
- cancelar primero trabajos stale/P3;
- después P2 más antiguos si todavía no son visibles;
- P0 que no puede admitirse => placeholder/fail-close temporal, métrica explícita.

No bloquear indefinidamente threads del proxy.

Métricas:

- admissionRejected;
- staleCancelled;
- dedupeHits;
- cacheHits;
- priorityPromotions;
- queuePeak;
- wait p50/p95/p99.

---

## 12. Cache de decisión

Antes de decode/inference:

1. resolver content identity;
2. consultar cache generation-bound;
3. SAFE válido => entrega original aprobada;
4. BLOCK/UNKNOWN válido => placeholder;
5. miss => scheduler.

La cache debe ahorrar decode+inference; idealmente ApprovedRepresentationCache también evita redescarga cuando la semántica HTTP lo permite.

---

## 13. Viewport coordination

La capa de presentación puede enviar hints sanitizados:

```text
windowId
presentationEpoch
viewportRect
scrollDirection
velocity bucket
```

No necesita identificar DOM ni extraer contenido privado.

El scheduler correlaciona candidatos cuando exista mapping suficiente. Si no puede correlacionar una imagen individual, no inventar precisión: priorizar por llegada y page activity.

---

## 14. Tabs

Pestaña activa:
prioridad normal.

Pestaña background:

- trabajo puede continuar bounded si ya está avanzado;
- nuevos candidatos P3;
- no consumir toda la capacidad mientras active tab tiene P0/P1.

Al volver a la pestaña:
subir candidatos relevantes.

BFCache/navigation generation debe invalidar resultados incompatibles.

---

## 15. Scroll rápido

Caso crítico para UX.

Durante fling rápido:

- viewport cambia más rápido que inferencia;
- cancelar P0 antiguos que ya no son visibles si no comenzaron;
- trabajos ya iniciados pueden terminar pero no desplazar nuevos visibles indefinidamente;
- near-viewport prediction sólo como hint;
- superficie nunca muestra original no decidido.

Métrica física:

- visibleCandidateWait p50/p95/p99;
- staleInferenceWaste;
- imagesSkippedByViewport;
- placeholder dwell time.

---

## 16. Error policy

Decode exception:
UNKNOWN placeholder.

Inference exception:
UNKNOWN placeholder + circuit metric.

OOM risk/admission denied:
UNKNOWN placeholder.

Scheduler shutdown:
no nuevos SAFE; invalidate leases si la autoridad requerida deja de estar sana.

No fallback a original por disponibilidad.

---

## 17. Circuit breaker

Evitar crash-loop del engine.

Si N errores de inferencia/decoder en ventana corta:

- marcar image authority degraded;
- dejar de admitir trabajo costoso;
- BLOCK/UNKNOWN placeholder;
- health false;
- si producto requiere GloshIA healthy para liberar Chrome, activar fail-close según contrato del guard.

Recovery bounded y observable.

---

## 18. Métricas clave

Por work:

```text
admissionToStartUs
queueWaitUs
decodeUs
normalizeUs
inferenceUs
publishUs
totalDecisionUs
priorityClass
cacheHit
dedupeHit
stale
visibleAtDecision
```

Global:

```text
queuePeak
activeDecodes
activeInferences
P0Backlog
staleCancelled
staleCompletedDiscarded
memoryBudgetUsed
```

Sin URL/payload en métricas.

---

## 19. Benchmark de scheduler

Fixtures:

- 1 imagen visible;
- 10 visibles;
- 30 mixtas;
- 100 con lazy-load;
- 100 duplicadas parcialmente;
- hero pequeña detrás de background gigante;
- scroll rápido;
- back/forward;
- tabs múltiples.

Comparar estrategias:

A. FIFO serial baseline.
B. decode paralelo + inference serial.
C. priority scheduler.
D. 2 inference sessions si viable.
E. batching si viable.

Elegir por experiencia visible, no throughput bruto.

---

## 20. Gates provisionales

No fijar SLA final antes de baseline, pero aspiraciones:

- raw original exposure = 0;
- stale publish = 0;
- P0 nunca espera detrás de backlog P3 ilimitado;
- queue bounded;
- no crecimiento lineal PSS/native;
- no ANR/OOM;
- first visible SAFE p50 adicional ideal <300 ms A23;
- p95 ideal <800 ms para recurso aislado;
- scroll rápido sin pantallas negras prolongadas.

Los números se confirman/rechazan en PERF-14.

---

## 21. Tests unitarios

- priority ordering;
- FIFO dentro de misma prioridad;
- aging;
- stale cancellation;
- result from old generation discarded;
- dedupe provisional→hash merge;
- cache hit bypasses scheduler;
- bounded admission;
- P3 evicted before P0;
- P0 rejection never releases original;
- memory semaphore;
- shutdown invalidates pending;
- circuit breaker;
- active/background tab priority.

---

## 22. Modularidad

Piezas sugeridas:

```text
GloshiaImageScheduler
ImageWorkAdmission
ImageWorkIdentity
ImagePriorityQueue
ImageDecodeBudget
ImageInferenceExecutor
ImageDecisionPublisher
ImageSchedulerHealth
ImageSchedulerMetrics
```

No incorporar scheduler dentro de proxy socket loops.

---

## 23. Dependencias y orden

Implementar sólo después de:

- transporte estable;
- proxy semantics;
- image content authority básica.

Puede desarrollarse junto con PERF-14 en rama aislada.

No depende conceptualmente del cierre de provenance 13A, aunque el fallback visual futuro puede reutilizar el mismo scheduler/GloshIA executor.

---

## 24. Handoff

Este documento deja preparada la estrategia para acercarse a la experiencia objetivo “como DAG”: fotos seguras aparecen rápido, las no permitidas nunca se muestran y el scroll prioriza lo que el usuario está viendo.

Antes de ejecutar, ChatGPT debe fijar base SHA/owner/worktree y revisar métricas reales de 11B.
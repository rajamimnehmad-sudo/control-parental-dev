# Roadmap cerrado de DAG v2

## Regla de avance

Las fases se ejecutan estrictamente en el orden indicado. Completar una fase no inicia automáticamente la siguiente. Cada fase requiere un ticket propio, aprobación explícita y evidencia de su criterio de aceptación.

## 1. Archivo y contrato

- Dependencia: ninguna; parte de `origin/main`.
- Riesgo: perder trazabilidad de DAG v1 o fijar datos no comprobados.
- Resultado: archivo verificable de v1, contrato de producto, frontera arquitectónica y roadmap de v2.
- Criterio de aceptación: hashes y tamaños verifican; rollback está documentado; no cambia código funcional, APK, versión ni backend.
- Rollback: revertir exclusivamente el commit documental; DAG v1 continúa sin cambios.
- Puerta: queda prohibido iniciar automáticamente la fase 2.

## 2. Navegador base

- Dependencia: fase 1 aprobada y fusionada.
- Riesgo: romper navegación, formularios, sitios dinámicos o aislamiento de recursos.
- Resultado: base separada de v2 que carga páginas funcionales y enruta documentos, datos e imágenes por pipelines independientes.
- Criterio de aceptación: React, Next.js, Service Workers, menús, inputs y scripts funcionan; ningún recurso visual no aprobado se expone; DAG v1 sigue operativo.
- Rollback: deshabilitar la entrada v2 y volver a la ruta v1 sin migrar ni borrar datos.
- Puerta: queda prohibido iniciar automáticamente la fase 3.

## 3. Calibración DEV

- Dependencia: navegador base v2 estable en DEV.
- Riesgo: confundir una etiqueta con una decisión activa o contaminar datos globales.
- Resultado: `✓ Mostrar`, `× Ocultar` y `? No estoy seguro`, activación explícita, deduplicación SHA-256/perceptual y estados separados.
- Criterio de aceptación: no hay recarga, cambio inmediato de filtro ni activación automática; sólo DEV contiene la capacidad; existe auditoría y rollback.
- Rollback: deshabilitar la función DEV y conservar las muestras sin activarlas ni borrarlas.
- Puerta: queda prohibido iniciar automáticamente la fase 4.

## 4. Dataset

- Dependencia: calibración DEV validada y contrato de datos aprobado.
- Riesgo: etiquetas incorrectas, duplicados, sesgo, pérdida de procedencia o material no autorizado.
- Resultado: dataset versionado, deduplicado, trazable, balanceado y separado en entrenamiento, validación y prueba.
- Criterio de aceptación: cada muestra tiene procedencia, política aplicable, etiqueta concluyente o estado incierto; no hay cruces entre particiones por duplicado visual.
- Rollback: retirar la versión candidata del dataset y volver a la última versión aprobada sin borrar evidencia histórica.
- Puerta: queda prohibido iniciar automáticamente la fase 5.

## 5. Entrenamiento desde cero

- Dependencia: dataset aprobado y congelado para la corrida.
- Riesgo: baja cobertura, sobreajuste o incorporación accidental de pesos preentrenados.
- Resultado: modelo visual propio con pesos iniciales aleatorios que implementa las señales mínimas del contrato.
- Criterio de aceptación: procedencia reproducible, ausencia verificada de pesos externos, métricas por señal y errores críticos dentro de límites aprobados.
- Rollback: retirar el artefacto candidato y conservar el modelo anterior; DAG v1 continúa activo.
- Puerta: queda prohibido iniciar automáticamente la fase 6.

## 6. Optimización Android

- Dependencia: modelo candidato aprobado fuera del producto.
- Riesgo: degradar precisión al cuantizar, aumentar memoria/temperatura o saturar el teléfono.
- Resultado: artefacto Android reproducible y pipeline con concurrencia acotada.
- Criterio de aceptación: paridad de seguridad dentro del margen aprobado; mediciones de latencia, PSS, CPU, temperatura y batería en Samsung SM-S908E; fallo cerrado.
- Rollback: deshabilitar el artefacto optimizado y volver al candidato previo o a DAG v1.
- Puerta: queda prohibido iniciar automáticamente la fase 7.

## 7. Modo sombra

- Dependencia: build Android v2 optimizado y verificable.
- Riesgo: telemetría insuficiente, exposición de datos o impacto de recursos aun sin controlar la decisión.
- Resultado: v2 evalúa en paralelo sin modificar lo que ve el usuario ni la decisión activa de v1.
- Criterio de aceptación: comparación trazable v1/v2, privacidad aprobada, impacto de rendimiento aceptable y cero decisiones visibles tomadas por v2.
- Rollback: apagar el modo sombra remotamente o por configuración local sin modificar v1 ni datos históricos.
- Puerta: queda prohibido iniciar automáticamente la fase 8.

## 8. Pruebas físicas y reentrenamiento

- Dependencia: evidencia suficiente del modo sombra.
- Riesgo: perseguir casos aislados, contaminar el conjunto de prueba o introducir regresiones al reentrenar.
- Resultado: matriz física reproducible, errores etiquetados y nuevas versiones de dataset/modelo cuando correspondan.
- Criterio de aceptación: pruebas sin cache en Frávega, Mimo y Cheeky sobre SM-S908E; corpus de política visual; regresión, rendimiento y temperatura documentados; conjunto de prueba permanece independiente.
- Rollback: retirar el último dataset/modelo candidato y volver a la última pareja aprobada; v1 sigue activo.
- Puerta: queda prohibido iniciar automáticamente la fase 9.

## 9. Canary

- Dependencia: modo sombra y pruebas físicas aprobados.
- Riesgo: falsos negativos reales, incompatibilidad no observada o rollback lento.
- Resultado: activación limitada y reversible de v2 para un alcance DEV/canary explícito.
- Criterio de aceptación: cohortes y duración definidas, métricas de seguridad y estabilidad, botón de corte probado y ninguna expansión automática.
- Rollback: devolver inmediatamente la cohorte a DAG v1 y conservar evidencia para diagnóstico.
- Puerta: queda prohibido iniciar automáticamente la fase 10.

## 10. Retiro de DAG v1

- Dependencia: canary estable y decisión explícita de producto, seguridad y operación.
- Riesgo: eliminar el único respaldo probado o perder capacidad de recuperación.
- Resultado: v2 pasa a ser el motor estable y v1 se retira mediante un ticket independiente.
- Criterio de aceptación: v2 validado y estable, rollback final probado, archivo v1 verificable, período de observación cumplido y aprobación manual registrada.
- Rollback: restaurar DAG v1 desde el commit archivado y publicar una versión superior mediante el flujo oficial.
- Puerta: el retiro no autoriza borrar inmediatamente archivo, historia, modelos o datos; cualquier borrado requiere otro ticket explícito.

# Frontera arquitectónica DAG v2

## Principio

DAG v2 vive separado de DAG v1 hasta su activación final. La separación debe permitir comparar ambos motores, ejecutar v2 en modo sombra, volver a v1 y retirar v1 sólo después de una validación explícita.

No se agregarán dependencias nuevas desde DAG v2 hacia las clases internas de decisión visual de DAG v1. Los contratos compartidos deberán ser neutrales y no codificar estados, thresholds ni precedencia propios de v1.

## Reutilizar

DAG v2 reutilizará, mediante interfaces estables cuando corresponda:

- Brave Search;
- SafeSearch;
- autorización por dispositivo;
- reglas administrativas;
- HTTPS;
- certificados;
- Android Safe Browsing;
- protección contra destinos privados;
- historial;
- pestañas;
- infraestructura segura de Supabase;
- datos históricos válidos.

Reutilizar significa conservar una capacidad segura ya probada; no significa acoplar el nuevo motor visual a implementaciones internas de DAG v1.

## Reconstruir

DAG v2 reconstruirá:

- motor de decisiones visuales;
- pipeline de imágenes;
- modelo visual;
- calibración;
- evaluador;
- dataset;
- estados internos que hoy estén superpuestos.

La reconstrucción debe eliminar la dependencia funcional de la combinación actual de modelo profesional, fallback legado, detector de modestia, pose, heurísticas de URL y thresholds remotos.

## Límite de red y contenido

El navegador base debe enrutar por tipo de recurso:

- documento principal: análisis de sitio/página una vez por documento;
- imágenes y nuevos recursos visuales: pipeline visual individual;
- HTML, CSS, JavaScript, JSON, XHR, `fetch`, fuentes y datos funcionales: flujo normal del navegador, sin clasificador de imágenes;
- video, audio, descargas, permisos y destinos especiales: política explícita independiente, no inferida por el modelo visual.

El pipeline visual no modifica globalmente `src`, `srcset`, clases ni estilos y no deshabilita React, Next.js o Service Workers.

## Límite de seguridad

- Una prohibición adulta no negociable no puede ser reemplazada por una regla común de permitir dominio.
- Los bytes de una imagen no aprobada no llegan a una superficie visible.
- Fallo, timeout, cancelación, formato desconocido o confianza insuficiente mantienen un placeholder neutro.
- La concurrencia y los timeouts pueden afectar latencia, nunca reducir la política.
- Ningún secreto de backend se incorpora a Android.

## Límite de datos y calibración

- Las etiquetas DEV son globales y versionadas.
- SHA-256 identifica bytes; un hash perceptual detecta duplicados visuales.
- Muestras, datasets, candidatos, evaluaciones y activaciones son estados separados.
- Ninguna muestra ni candidato se activa automáticamente.
- El modelo candidato debe pasar por evaluación y modo sombra antes de una activación manual.
- Toda activación conserva un rollback verificable.
- La capacidad de etiquetado visual se retira del producto final al terminar la etapa de entrenamiento.

## Convivencia v1/v2

Durante la transición:

- DAG v1 permanece intacto como motor activo y respaldo.
- DAG v2 usa namespace, almacenamiento, versiones y telemetría diferenciados.
- El modo sombra de v2 no modifica la decisión visible de v1.
- Los datos históricos válidos pueden importarse mediante un proceso explícito, trazable y reversible; no se consumen directamente como estado mutable de v1.
- Ninguna fase elimina assets, modelos, tablas o rutas de rollback de v1.

## Puerta de activación final

La activación final de v2 requiere:

1. contrato y archivo verificados;
2. navegador base funcional sin regresiones;
3. calibración DEV controlada;
4. dataset trazable;
5. modelo entrenado desde cero;
6. optimización comprobada en Android objetivo;
7. modo sombra satisfactorio;
8. pruebas físicas y reentrenamiento cerrados;
9. canary reversible;
10. decisión manual separada para retirar v1.

Este documento no autoriza ninguna de esas implementaciones.

# DAG 108 - medición de carga y GloshIA

Fecha: 2026-08-05
Dispositivo: Samsung SM-S908E, Android 16, arm64-v8a
APK: `0.69.12-dev`, `versionCode 108`
Modelo: GloshIA Visual R3.1, SHA-256 `c8b64af8092d3718c58736a511c996d0d443dacf3eaa74620b1e5af439a3cd48`

## Diagnóstico

La carga no está limitada principalmente por la cola: en el recorrido base la
espera nativa fue p50 `0-1 ms`. El coste dominante es el número de raster que
cada página solicita y, en imágenes panorámicas o verticales, las vistas
regionales adicionales. El modelo R3.1 no cambió de contrato ni de política.

Muestras frías controladas sobre sitios vivos; por la variabilidad de red y de
contenido no se presentan como porcentajes causales:

| Sitio | Pipeline | Inferencia p50/p90 | Native p50/p90 | Página visible | Vista inicial quieta | Raster |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Mimo base | 74 | 41,34 / 86,08 ms | 54,5 / 116 ms | 523 ms | 5.439 ms | 4,62 % jank |
| Cheeky base | 47 | 48,57 / 142,10 ms | 62 / 193 ms | 1.454 ms | 893 ms | 5,56 % jank |
| Frávega base | 133 | 145,58 / 186,18 ms | 160 / 203 ms | 1.656 ms | 10.769 ms | medido sin crash/ANR |

La prueba A/B de ORT con una sola hebra fue descartada: en Mimo la inferencia
pasó de p50 `41,34 ms` a `165,73 ms` y de p90 `86,08 ms` a `243,41 ms`.
Se conserva `intra_op_num_threads=2`.

## Cambios locales aplicados

1. La página informa al fondo privilegiado si una imagen está visible, cercana
   o fuera de la ventana mediante `IntersectionObserver`. La cola nativa ya
   existente adelanta las visibles; el límite es 256 sugerencias por documento
   y 256 URLs en memoria.
2. Dos respuestas raster idénticas que llegan mientras la primera está siendo
   evaluada comparten la misma promesa de decisión. No se relaja el fail-closed,
   no se comparte una decisión no cacheable y no se cambia la política.
3. Se eliminó la lectura sincrónica de geometría en cada cambio de `src`; el
   cálculo queda en el callback asíncrono del observador para no forzar layout.

La verificación física mostró prioridades `visible` antes que `background` en
el log de `DagMediaTransport`. Las páginas vivas son dinámicas y no permiten
atribuir una mejora de tiempo total a una única corrida; la evidencia física se
conserva en `.codex-tmp/dag-perf-lab/live-runs/` fuera de Git.

## Seguridad y validación

- R3.1, umbral, preprocesamiento, vistas regionales y política permanecen sin cambios.
- El modelo sigue ejecutándose localmente en CPU y no se añadieron APIs ni servidores.
- 16 pruebas WebExtension, unitarios Kotlin, Ktlint, Lint y APK DEV pasan.
- No hubo crash ni ANR en las corridas registradas.
- `final_sealed`, Supabase, Production y publicación no fueron tocados.

Próximo benchmark recomendado: repetir cada sitio dos veces con el mismo perfil
caliente y comparar sólo p50/p95 de `page_visible`, `viewport_images_ready`,
`native_ms` y jank antes de decidir cualquier ajuste adicional.

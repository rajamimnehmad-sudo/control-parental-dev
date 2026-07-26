# Colector de Calibración DAG v2

## Alcance

`DAG-V2-CALIBRATION-COLLECTOR-03` agrega una herramienta exclusiva de DEV para registrar evidencia humana con tres decisiones:

- `✓ Mostrar` → `show`
- `× Ocultar` → `hide`
- `? No estoy seguro` → `unsure`

La herramienta no cambia el filtro, la página, un threshold, una versión de modelo ni DAG v1. `DagV2FailClosedImageDecisionProvider` continúa devolviendo únicamente `Hide`.

## Disponibilidad y activación

- App Usuario incluye `:feature-dag2` solamente mediante `devImplementation`.
- `DAG_V2_CALIBRATION_AVAILABLE=true` sólo en DEV; Beta y Production usan `false`.
- Un verificador de artefactos confirma que Beta y Production no exponen Activity, componentes ni constantes habilitadas.
- La calibración comienza apagada cada vez que se abre el Lab.
- Activarla requiere una confirmación que explica que la página seguirá mostrando placeholders y que las etiquetas no cambian el filtro.
- Activar o desactivar no recarga, no crea un documento y no aumenta `fullPageAnalysisCount`.

## Arquitectura

`DagV2CalibrationController` coordina responsabilidades separadas:

1. `DagV2CalibrationCandidateQueue` conserva únicamente candidatos raster atribuidos a la generación vigente. Deduplica el recurso normalizado dentro de la sesión, prioriza los recientes, limita la cola a 100 y limpia las URL al cambiar o cerrar sesión.
2. `DagV2CalibrationImageFetcher` descarga sólo después de una selección explícita. Valida HTTPS, cada redirect y destinos públicos mediante el guard neutral compartido; fija DNS público para la conexión, limita redirects, tiempo y 4 MiB, y rechaza MIME o firmas no raster.
3. `DagV2CalibrationImageNormalizer` decodifica con límites de dimensiones/píxeles, toma una representación estática, aplica orientación, crea un bitmap sRGB nuevo, elimina metadatos, reduce el lado mayor a 768 px y produce JPEG de hasta 512 KiB.
4. `DagV2CalibrationFingerprint` calcula SHA-256 sobre el JPEG normalizado y dHash perceptual de 64 bits. La distancia máxima documentada para casi duplicados es 5 bits.
5. `DagV2CalibrationOutboxStore` cifra cada pendiente con AES-256-GCM y Android Keystore dentro de `noBackupFilesDir/dag-v2-calibration-outbox-v1`.
6. `SupabaseDagV2CalibrationGateway` envía multipart exclusivamente a la Edge Function DEV. El payload no contiene URL, query, texto, cookies, Referer ni headers.

## Visor aislado

La WebView siempre recibe el mismo placeholder neutro. La imagen normalizada se decodifica en un `Image` nativo dentro de un diálogo de revisión, nunca se inyecta en HTML ni reemplaza el placeholder.

El flujo es:

`cola → selección explícita → descarga/validación → normalización → preview nativa → etiqueta → outbox`

Cerrar o avanzar cancela trabajo pendiente, libera el bitmap y sobrescribe las referencias a bytes disponibles. Volver a la página no recarga el documento.

## Outbox y entrega

- Namespace completamente separado de Calibración v1.
- Máximo 50 pendientes, 20 MiB totales y 30 días.
- Deduplicación local por SHA-256 y decisión antes de escribir.
- Envío inmediato y tres intentos acotados mientras el Lab está abierto.
- Un éxito elimina sólo el pendiente aceptado.
- Un fallo temporal conserva la copia cifrada.
- Un fallo permanente se muestra con un motivo sanitizado.
- Cerrar el Lab no descarta una etiqueta ya confirmada.

No se usa WorkManager dentro de `:dag2`, para conservar la garantía de que ese proceso no inicia workers o sincronizaciones del proceso principal. El reintento equivalente se ejecuta al abrir el Lab.

## Backend DEV

La migración `20260726041736_dag_v2_calibration_collector.sql` y la Edge Function `dag-v2-calibration` existen sólo en Supabase DEV `syeycayasyufedwoprea`.

La función:

- autoriza el dispositivo con el token DEV existente;
- recalcula SHA-256, dimensiones y formato;
- limita 30 intentos por hora y 100 por día por revisor seudónimo;
- registra muestra exacta o casi duplicada antes de subir;
- usa un bucket privado y una ruta derivada del hash;
- conserva auditoría de creación, deduplicación, relabel y rechazo;
- no expone lectura ni borrado;
- no escribe tablas de DAG v1.

## Evidencia física

Dispositivo: Samsung SM-A235M `R58T34V31AE`, Android 14/API 34. APK DEV instalada in-place, `versionCode 279`, SHA-256 `50a7b2ea23ae99d291fce86e38b3076f780aac913d91b93407b6443659f82ece`.

- Frávega, Mimo y Cheeky cargaron con calibración apagada, estructura permitida, raster neutro y `full_page_analysis_count == 1`.
- Activar no recargó ni cambió el contador.
- Scroll o una recarga explícita en Cheeky agregaron candidatos sin análisis global adicional de la misma interacción.
- Los previews se abrieron sólo tras seleccionar un candidato y aparecieron en el visor nativo.
- Cerrar devolvió al documento vigente, que conservó placeholders y controles funcionales.
- Desactivar retiró la cola visible sin crear otra navegación.
- No hubo crash, ANR ni `renderer_gone`.
- No se observó ninguna fotografía real dentro de WebView.

Incidencia de validación: el primer APK del worktree se compiló sin importar el `.env` local y devolvió `config_unavailable`. Sus tres outboxes cifrados sobrevivieron y fueron entregados al instalar el APK correctamente configurado. Una comprobación adicional creó una cuarta muestra `unsure`, excediendo en una el máximo de tres solicitado. DEV contiene cuatro muestras privadas listas, cuatro etiquetas `unsure` y su auditoría. No se borró ni alteró ninguna porque este ticket prohíbe borrados; corregir esa evidencia requeriría un ticket administrativo destructivo explícito.

## Rollback

1. Poner `DAG_V2_CALIBRATION_AVAILABLE=false`.
2. Retirar la interfaz y el gateway sin cambiar el navegador v2.
3. Mantener muestras, etiquetas y auditoría remotas.
4. Mantener el outbox cifrado hasta una decisión explícita.
5. Mantener DAG v1 sin cambios.

No se elimina información automáticamente. Dataset, entrenamiento, modo sombra y Lote 4 no comenzaron.

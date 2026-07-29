# HANDOFF ACTUAL - Glosh y DAG Browser

Fecha de corte: 2026-07-29

Tomar este archivo como contexto oficial. No reconstruir el estado desde
documentos o commits históricos.

## Fuentes oficiales

- Este archivo contiene la verdad técnica vigente.
- `docs/BACKLOG_PRODUCTO.md` contiene ideas, prioridades y tickets.
- `docs/BASELINES.md` contiene puntos de recuperación.
- El historial Git conserva evidencia antigua, pero no define el runtime actual.

## Carpeta y seguridad

Trabajar únicamente en:

```text
/Users/yejielnehmad/Developer/content-filter
```

- Usar solamente Supabase DEV `syeycayasyufedwoprea`.
- No tocar Production.
- No borrar datos sin confirmación específica.
- No incluir secretos ni Service Role Key en Android o Git.
- App Usuario y App Admin versionan y publican de forma independiente.

## Estado publicado de Glosh

Última publicación pública verificada:

```text
App Usuario versionCode 279
App Admin versionCode 275
versionName 1.0.1-dev
```

App Usuario `versionCode 281` fue además un candidato físico para validar el
puente hacia el navegador separado; no reemplazó el manifiesto público
documentado arriba.

## Runtime único de navegador

- El único navegador del proyecto es `app-dag-browser`.
- Es una APK GeckoView separada, fail-closed y conectada con Glosh mediante un
  puente DEV explícito.
- No existe fallback hacia implementaciones retiradas.
- El código de `main` declara `versionCode 5`, `versionName 0.3.0-dev`.
- La APK del gate físico midió 102.605.578 bytes y tuvo SHA-256
  `72a976dcafe1512f8afe8381936fc4ebebfadf4e66efff716ada0a73786e86c8`.
- En SM-A235M se verificó apertura/regreso y ausencia de destellos para raster,
  video, canvas, SVG y fondos.
- Mientras no exista una decisión visual válida, cada recurso permanece en
  `block / analyzer_unavailable`.
- El navegador no usa infraestructura, código, modelos ni datos de runtimes
  retirados.

## Candidato local de DAG Browser

Existe trabajo posterior sin commit en:

```text
/Users/yejielnehmad/Developer/content-filter-dag-browser-v3
```

- Ese worktree contiene el candidato `versionCode 17`, `0.8.0-dev`, mejoras de
  pestañas, interfaz, compatibilidad y análisis local.
- El selector usa dos columnas con miniaturas efímeras de páginas ya filtradas,
  nueva pestaña, cierre por botón o deslizamiento y reordenamiento por pulsación
  larga. Sólo persiste URL, título, orden e índice activo; una única sesión se
  restaura con red y las demás quedan diferidas.
- El filtro mantiene una caché acotada de decisiones por SHA-256 y deduplica
  pedidos simultáneos. La barrera incorpora fuentes lazy frecuentes y corrige
  el fallback de mensajes de GeckoView que dejaba algunos recursos
  transparentes.
- Evidencia SM-S908E: Google Imágenes visible en 144-187 ms, primera quietud
  visual en 1.787 ms y recarga en 989 ms; cinco pestañas restauradas usaron
  226.383 KiB PSS con una sola sesión abierta. No hubo crash ni ANR.
- `ktlintCheck`, 53 unitarios y `assembleDevDebug` fueron correctos. La APK
  local mide 121.088.287 bytes y su SHA-256 es
  `6cfe35c70a69ed5d0db0a0e64a21ca55939f8c14ba9ad0a9d5f43d22d8af8b08`.
- Evidencia detallada:
  `docs/compatibility/results/dag-browser-v3-optimization-sm-s908e-2026-07-29.md`.
- También contiene archivos nuevos y modificaciones todavía no conciliadas con
  `main`.
- No borrar, limpiar, resetear ni sobrescribir ese worktree.
- Antes de integrar cualquier cambio se debe revisar su diff, separar producto
  de experimentos y validar proporcionalmente.

## Investigación visual

- El contrato de 21 señales, preprocesado RGB 224 x 224, manifiesto, validadores
  y guía de anotación pertenecen al navegador actual.
- El lote de adquisición/entrenamiento queda `Pausado por decisión de producto`.
- No se descargaron imágenes, no se entrenó ningún modelo y no se habilitó
  ninguna salida visual.
- No continuar corpus, etiquetado, GPU, entrenamiento, modo sombra o canary sin
  una nueva aprobación explícita.

## Dirección de producto acordada

- DAG debe ser el navegador predeterminado cuando esté habilitado, mediante la
  confirmación oficial que exige Android.
- Glosh conserva instalación y configuración remota sobre teléfonos personales.
- No usar Device Owner, MDM, Knox ni restablecimiento de fábrica.
- Glosh debe impedir navegadores alternativos mediante Accessibility y las
  barreras existentes, declarando la cobertura como best-effort.
- App Admin necesita una activación guiada de DAG, mostrar navegadores
  detectados y distinguir `Protegido`, `Configuración incompleta` y
  `Requiere atención`.
- Solicitudes de apps deben mostrar icono, nombre, usuario, dispositivo y estado,
  conservando packageName como identidad confiable.
- DAG debe distinguir visualmente imagen cargando, imagen bloqueada y error sin
  revelar píxeles antes de la decisión.

## Estado funcional de Glosh

- Activación real contra Supabase DEV.
- App Admin se activa con token de administrador.
- App Usuario se activa con token generado desde Admin.
- VPN foreground aplica política Web y SafeSearch.
- Accessibility bloquea aplicaciones y protege rutas de manipulación.
- Device Admin, watchdog, alertas FCM y recuperación siguen siendo capas
  independientes.
- Solicitudes, límites individuales, tiempo adicional y grupos de apps están
  integrados.
- Historial y estados de navegación sensibles permanecen locales/cifrados cuando
  corresponde.
- La Superweb legacy sigue siendo necesaria y no debe eliminarse sin ticket
  específico.

## Límites vigentes

- Sin Device Owner no existe garantía absoluta contra modo seguro, ADB
  previamente autorizado, caída de Accessibility o restablecimiento físico.
- Ser navegador predeterminado no impide por sí solo abrir otro navegador.
- Accessibility puede bloquear o expulsar otras apps, pero no insertar de forma
  fiable el clasificador dentro de Chrome para modificar cada imagen.
- Una VPN/DNS no puede aplicar blur individual dentro de HTTPS sin una
  interceptación TLS que no está autorizada ni recomendada.

## Próximo trabajo autorizado

No hay implementación nueva autorizada por el solo hecho de figurar en el
backlog. El siguiente paso es elegir y aprobar un ticket pequeño del lote
UX/seguridad de DAG y App Admin.

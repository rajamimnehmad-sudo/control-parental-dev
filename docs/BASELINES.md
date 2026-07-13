# Baselines estables

Este archivo registra puntos de recuperacion probados. Un baseline congela codigo, documentacion y artefactos publicados, pero no autoriza borrar datos ni instalar un `versionCode` inferior sobre una aplicacion mas nueva.

## stable/dev-191-web-protection

Fecha: 2026-07-13

Estado: estable para continuar el desarrollo por tickets.

### Alcance congelado

- App Usuario DEV 191.
- App Admin DEV 181, sin cambios durante el cierre Web.
- Ticket 1 del epic Web cerrado: bypass de incognito/DNS, conexiones reutilizadas y precheck Bloom UT1 corregidos.
- Invalidaciones de destinos bloqueados agrupadas y acotadas para evitar reconstrucciones repetidas del tunel.
- SafeSearch obligatorio, Google operativo, Solo resultados y UT1 conservados.
- Privacidad: sin consultas, URL completas, titulos, HTML, contenido visible ni historial.

### Referencias

- Commit funcional del cierre VPN: `1347bdf`.
- Commit de handoff verificado: `06a74bb`.
- Tag inmutable: `stable/dev-191-web-protection`.
- Android CI exitoso: `29275226239`.
- App Usuario: `https://syeycayasyufedwoprea.supabase.co/storage/v1/object/public/dev-updates/app-user-dev-191-debug.apk`.
- SHA-256 Usuario DEV 191: `c408e45e1f892d17d94018a4044b28a8a032aace7ae26b3c94c4e73091f4f3db`.
- App Admin DEV 181: `https://syeycayasyufedwoprea.supabase.co/storage/v1/object/public/dev-updates/app-admin-dev-181-debug.apk`.
- SHA-256 Admin DEV 181: `327e0c65ea18412e0eef34f09bd2b7efa073ab46a44ab928b028867ec7f7c616`.

### Validacion registrada

- Tests de `feature-vpn`, `core-policy` y App Usuario.
- Ktlint, detekt, Android lint y Android CI completos.
- Prueba fisica en Samsung con Chrome normal e incognito.
- VPN activa en primer plano despues de actualizacion in-place.
- Rafaga controlada reducida a una sola invalidacion en 12 segundos.
- APK publicado descargado y SHA-256 verificado.

### Recuperacion segura

1. Crear una rama de recuperacion desde `stable/dev-191-web-protection`; no mover ni reutilizar el tag.
2. Revisar compatibilidad con migraciones y contratos remotos agregados despues del baseline.
3. Mantener el codigo funcional del baseline, pero incrementar App Usuario a un `versionCode` mayor que el ultimo publicado.
4. Ejecutar nuevamente los tests relevantes, compilar App Usuario DEV y validar en telefono fisico.
5. Publicar solamente la app afectada y verificar manifiesto, descarga y SHA-256.
6. No desinstalar la app, no limpiar sus datos y no revertir Supabase mediante borrados.

### Limites del baseline

- Congela el codigo y el APK, no una copia de datos de usuarios ni un snapshot de Supabase.
- La lista UT1 es dinamica y firmada; el APK queda congelado, pero el contenido remoto de la lista puede evolucionar.
- Una navegacion directa por una IP nunca resuelta y resolvers cifrados desconocidos siguen fuera de este cierre.
- Una pagina ausente de UT1 representa falta de cobertura, no bypass.

## Regla para futuros baselines

- Crear un tag nuevo; nunca mover o borrar un tag estable publicado.
- Registrar versiones, commits, CI, prueba fisica, URLs, SHA-256 y limitaciones.
- Si hay cambios de base de datos, documentar compatibilidad hacia adelante antes de declarar el baseline.
- Los rollbacks Android se publican con codigo anterior y `versionCode` nuevo superior.

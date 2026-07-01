# Dev APK Updates

La App Usuario y la App Admin incluyen una actualizacion manual por APK para pruebas internas del canal `dev`.

Este flujo no instala silenciosamente. La app descarga el APK, verifica SHA-256 y abre el instalador oficial de Android. El usuario siempre confirma manualmente.

## Configuracion local

Agregar en `.env`:

```text
UPDATE_MANIFEST_URL_USER=https://example.com/app-user-dev-manifest.json
UPDATE_MANIFEST_URL_ADMIN=https://example.com/app-admin-dev-manifest.json
```

No son secretos. Pueden apuntar a Supabase Storage, GitHub Releases, GitHub Pages o cualquier archivo estatico HTTPS.

`UPDATE_MANIFEST_URL` se mantiene como fallback de compatibilidad.

## Formato del manifiesto

```json
{
  "versionCode": 2,
  "versionName": "1.0.1-dev",
  "apkUrl": "https://example.com/app-user-dev-debug.apk",
  "apkSha256": "REEMPLAZAR_CON_SHA256_DEL_APK",
  "releaseNotes": "Notas breves para pruebas internas."
}
```

Campos:

- `versionCode`: debe ser mayor que el `versionCode` instalado para mostrar actualizacion.
- `versionName`: texto visible para el usuario.
- `apkUrl`: URL publica HTTPS del APK firmado.
- `apkSha256`: hash SHA-256 hexadecimal del APK.
- `releaseNotes`: resumen corto de cambios.

## Donde subir los archivos

Opciones simples:

- Supabase Storage: crear un bucket publico solo para artefactos dev y subir el APK y el JSON.
- GitHub Releases: subir el APK como asset y publicar el manifiesto como asset o archivo estatico.
- GitHub Pages o hosting estatico: publicar ambos archivos con URLs HTTPS.

No usar Service Role Key ni secretos dentro de Android.

Para Supabase Storage DEV, el bucket publico esperado es `dev-updates`.

La forma mas simple es:

```bash
scripts/upload_dev_updates.sh
```

Con Supabase CLI 2.x, `storage cp` requiere `--experimental`:

```bash
supabase storage cp --experimental --linked --content-type application/json build/dev-updates/app-user-dev-manifest.json ss:///dev-updates/app-user-dev-manifest.json
supabase storage cp --experimental --linked --content-type application/json build/dev-updates/app-admin-dev-manifest.json ss:///dev-updates/app-admin-dev-manifest.json
supabase storage cp --experimental --linked --content-type application/vnd.android.package-archive build/dev-updates/app-user-dev-debug.apk ss:///dev-updates/app-user-dev-debug.apk
supabase storage cp --experimental --linked --content-type application/vnd.android.package-archive build/dev-updates/app-admin-dev-debug.apk ss:///dev-updates/app-admin-dev-debug.apk
```

## Generar hash SHA-256

```bash
scripts/prepare_dev_updates.sh
```

El script copia ambos APKs, genera ambos manifiestos y muestra los SHA-256.

## Probar en Android

1. Recompilar ambas APKs dev.
2. Ejecutar `scripts/prepare_dev_updates.sh`.
3. Subir los 4 archivos de `build/dev-updates` al bucket `dev-updates`.
4. Instalar una build anterior en el telefono.
5. Abrir App Usuario o App Admin.
6. Ir a `Actualizaciones`.
7. Tocar `Buscar actualizacion`.
8. Tocar `Actualizar`.
9. Si Android lo pide, permitir instalar APKs desde esta app.
10. Confirmar la instalacion en el instalador oficial.

## Limitaciones conocidas

- Solo existe canal `dev`.
- No hay rollout progresivo.
- No hay actualizaciones silenciosas.
- No hay firma avanzada ni pinning del host.
- No se implementa beta/release todavia.
- Si se publica en Google Play, `REQUEST_INSTALL_PACKAGES` puede requerir una variante sin self-update por APK o una justificacion de politica.

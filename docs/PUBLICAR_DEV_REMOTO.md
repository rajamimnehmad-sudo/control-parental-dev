# Trabajar y publicar DEV desde la nube

Objetivo: trabajar con Codex cloud sobre GitHub y publicar desde un runner alojado por GitHub. La Mac puede quedar apagada; solo hace falta para pruebas Android fisicas por cable.

## Una sola vez

1. Subir este proyecto a un repositorio de GitHub.

2. En GitHub, abrir el repositorio y crear estos Secrets:

- `SUPABASE_URL`
- `SUPABASE_ANON_KEY`
- `UPDATE_MANIFEST_URL_USER`
- `UPDATE_MANIFEST_URL_ADMIN`
- `SUPABASE_ACCESS_TOKEN` recomendado para que el runner pueda subir a Storage sin depender del login local

No crear ni guardar `SERVICE_ROLE_KEY`.

3. Conectar el repositorio a Codex en `https://chatgpt.com/codex` y crear un entorno cloud para la rama de trabajo.

4. No hace falta configurar un runner propio. `Publicar APKs DEV` usa `ubuntu-latest`, instala JDK, Android SDK, Gradle y Supabase CLI en cada ejecucion.

## Trabajo cotidiano desde el Samsung

1. Abrir Codex web desde Chrome e iniciar una tarea sobre este repositorio.
2. Trabajar en una rama y abrir un PR; Android CI valida la rama sin publicar APKs.
3. Revisar el diff y los checks desde GitHub o Codex.
4. Fusionar el PR a `main` solo cuando el cambio este aprobado.

Un push o merge ya no publica automaticamente. La publicacion DEV es una accion manual separada.

## Cada actualizacion

Desde el Samsung:

1. Abrir GitHub.
2. Entrar al repositorio.
3. Abrir `Actions`.
4. Elegir `Publicar APKs DEV`.
5. Tocar `Run workflow`.
6. Marcar `Confirmo que quiero publicar ambas APK en Supabase DEV`.
7. Tocar nuevamente `Run workflow` y esperar que termine verde.
8. Abrir App Usuario o App Admin.
9. Tocar `Actualizaciones`.

## Que hace automaticamente

- Compila App Usuario DEV.
- Compila App Admin DEV.
- Ejecuta tests.
- Comprueba que ambos `versionCode` sean mayores que los publicados.
- Genera manifiestos.
- Sube ambas APK exclusivamente a Supabase Storage DEV mediante staging atomico.
- Actualiza manifiestos.

No usa Production ni guarda una Service Role Key. El token de acceso a Supabase vive solo como GitHub Actions Secret.

## Si falla

Abrir el run fallido en GitHub Actions y mirar el paso rojo. El error queda ahi con el detalle exacto.

Si falla antes de promocionar los manifiestos, no volver a ejecutar a ciegas: comprobar primero las versiones publicas y el prefijo de staging indicado por el log.

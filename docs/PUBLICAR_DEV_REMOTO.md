# Publicar APKs DEV sin ir a la Mac

Objetivo: usar la Mac encendida como runner de GitHub Actions. Desde el Samsung se toca un boton en GitHub y la Mac compila, testea, sube APKs a Supabase Storage y actualiza manifiestos.

## Una sola vez

1. Subir este proyecto a un repositorio de GitHub.

2. En GitHub, abrir el repositorio y crear estos Secrets:

- `SUPABASE_URL`
- `SUPABASE_ANON_KEY`
- `UPDATE_MANIFEST_URL_USER`
- `UPDATE_MANIFEST_URL_ADMIN`
- `SUPABASE_ACCESS_TOKEN` recomendado para que el runner pueda subir a Storage sin depender del login local

No crear ni guardar `SERVICE_ROLE_KEY`.

3. En GitHub, abrir:

`Settings -> Actions -> Runners -> New self-hosted runner`

Elegir:

- macOS
- la arquitectura de tu Mac

4. GitHub va a mostrar comandos para descargar y configurar el runner. Ejecutarlos en la Mac una sola vez.

5. Cuando pregunte labels, dejar los defaults. El workflow usa:

`self-hosted, macOS`

6. Dejar el runner corriendo en la Mac.

## Cada actualizacion

Desde el Samsung:

1. Abrir GitHub.
2. Entrar al repositorio.
3. Abrir `Actions`.
4. Elegir `Publicar APKs DEV`.
5. Tocar `Run workflow`.
6. Esperar que termine verde.
7. Abrir App Usuario o App Admin.
8. Tocar `Actualizaciones`.

## Que hace automaticamente

- Compila App Usuario DEV.
- Compila App Admin DEV.
- Ejecuta tests.
- Genera manifiestos.
- Sube APKs a Supabase Storage.
- Actualiza manifiestos.

## Si falla

Abrir el run fallido en GitHub Actions y mirar el paso rojo. El error queda ahi con el detalle exacto.

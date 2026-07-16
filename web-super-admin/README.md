# Content Filter Super Admin Web

Panel privado para el dueno de la app. Permite administrar comunidades, licencias, administradores y dispositivos desde una web separada de las apps Android.

## Deploy recomendado

Recomendacion: publicar en Vercel.

1. Crear un proyecto en Vercel conectado a este repo.
2. Configurar `Root Directory` como:

```text
web-super-admin
```

3. Configurar variables de entorno en Vercel:

```text
NEXT_PUBLIC_SUPABASE_URL=https://syeycayasyufedwoprea.supabase.co
NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY=sb_publishable_...
```

Usar exclusivamente la publishable key de Supabase DEV `syeycayasyufedwoprea`. La aplicacion falla cerrada si la URL apunta a otro proyecto. No usar Service Role Key.

4. Build settings:

```text
Install Command: pnpm install --frozen-lockfile
Build Command: pnpm run build
```

Vercel detecta Next.js automaticamente.

Después de publicar, verificar desde la raíz del repositorio:

```bash
scripts/verify_superweb.sh https://web-super-admin-nine.vercel.app COMMIT_SHA
```

La comprobación exige que `/api/health` declare DEV y el commit esperado, y que las rutas privadas nuevas existan y redirijan al login en vez de devolver 404.

5. En Supabase, configurar Auth:

```text
Authentication > URL Configuration
Site URL: https://TU-DOMINIO
Redirect URLs:
https://TU-DOMINIO/**
```

Aunque el login actual usa email/password, esta configuracion deja listo el panel para confirmaciones, recuperacion de password u OAuth si se agregan despues.

6. Crear o confirmar el usuario en Supabase Auth y dar permiso Super Admin:

```sql
insert into public.super_admins (user_id, display_name)
values ('AUTH_USER_UID', 'Yejiel')
on conflict (user_id) do update
set enabled = true,
    display_name = excluded.display_name,
    deleted_at = null;
```

## Desarrollo local

```bash
pnpm install
pnpm run dev
```

Abrir:

```text
http://127.0.0.1:3000
```

## Seguridad

- No guardar secretos en Git.
- No usar Service Role Key en frontend.
- Esta instalacion acepta unicamente Supabase DEV `syeycayasyufedwoprea` como backend del panel.
- El panel usa `sb_publishable_...` y RLS/RPCs con chequeo `is_super_admin()`.
- El sitio envia headers `noindex` y `robots.txt` bloquea indexacion.
- Las RPCs Super Admin no son ejecutables por `anon`.

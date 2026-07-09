alter table public.community_admins
add column if not exists auth_user_id uuid references auth.users(id) on delete set null;

create unique index if not exists idx_community_admins_auth_user_active
on public.community_admins(auth_user_id)
where auth_user_id is not null and deleted_at is null;

create or replace function public.pair_admin_device_with_password(
    pairing_code text,
    admin_email text,
    admin_password text,
    device_display_name text,
    device_app_version_code integer
)
returns table (
    account_id uuid,
    device_id uuid,
    activation_id uuid,
    device_token text
)
language plpgsql
security definer
set search_path = public, auth, extensions
as $$
declare
    matched_code public.activation_codes%rowtype;
    matched_account public.accounts%rowtype;
    matched_admin public.community_admins%rowtype;
    existing_user auth.users%rowtype;
    admin_user_id uuid;
    inserted_device_id uuid;
    inserted_activation_id uuid;
    safe_email text;
    safe_display_name text;
    raw_device_token text;
begin
    safe_email := lower(nullif(trim(admin_email), ''));
    safe_display_name := coalesce(nullif(trim(device_display_name), ''), 'Administrador');

    if safe_email is null or safe_email !~ '^[^@\s]+@[^@\s]+\.[^@\s]+$' then
        raise exception 'Email invalido';
    end if;

    if admin_password is null or length(admin_password) < 8 then
        raise exception 'La contraseña debe tener al menos 8 caracteres';
    end if;

    select *
    into matched_code
    from public.activation_codes
    where used_at is null
      and deleted_at is null
      and expires_at > now()
      and code_hash = crypt(upper(trim(pairing_code)), code_hash)
      and intended_app_role = 'admin'
      and community_admin_id is not null
    order by created_at asc
    limit 1;

    if matched_code.id is null then
        raise exception 'Token de administrador invalido';
    end if;

    select *
    into matched_account
    from public.accounts
    where id = matched_code.account_id
      and deleted_at is null
    limit 1;

    if matched_account.id is null then
        raise exception 'Comunidad no encontrada';
    end if;

    select *
    into matched_admin
    from public.community_admins
    where id = matched_code.community_admin_id
      and deleted_at is null
    limit 1;

    if matched_admin.id is null then
        raise exception 'Administrador no encontrado';
    end if;

    if matched_admin.auth_user_id is not null then
        raise exception 'Este administrador ya fue activado';
    end if;

    if not public.license_allows_activation(matched_code.account_id, 'admin') then
        raise exception 'La licencia no permite activar mas administradores';
    end if;

    select *
    into existing_user
    from auth.users
    where lower(email) = safe_email
      and deleted_at is null
    limit 1;

    if existing_user.id is not null then
        if existing_user.encrypted_password is null
           or existing_user.encrypted_password <> crypt(admin_password, existing_user.encrypted_password) then
            raise exception 'Email o contraseña invalidos';
        end if;

        if exists (
            select 1
            from public.community_admins
            where auth_user_id = existing_user.id
              and id <> matched_admin.id
              and deleted_at is null
        ) then
            raise exception 'Ese email ya pertenece a otro administrador';
        end if;

        admin_user_id := existing_user.id;
    else
        admin_user_id := gen_random_uuid();

        insert into auth.users (
            instance_id,
            id,
            aud,
            role,
            email,
            encrypted_password,
            email_confirmed_at,
            invited_at,
            confirmation_token,
            confirmation_sent_at,
            recovery_token,
            recovery_sent_at,
            email_change_token_new,
            email_change,
            email_change_sent_at,
            last_sign_in_at,
            raw_app_meta_data,
            raw_user_meta_data,
            is_super_admin,
            created_at,
            updated_at,
            phone,
            phone_confirmed_at,
            phone_change,
            phone_change_token,
            phone_change_sent_at,
            email_change_token_current,
            email_change_confirm_status,
            banned_until,
            reauthentication_token,
            reauthentication_sent_at,
            is_sso_user,
            deleted_at,
            is_anonymous
        ) values (
            '00000000-0000-0000-0000-000000000000'::uuid,
            admin_user_id,
            'authenticated',
            'authenticated',
            safe_email,
            crypt(admin_password, gen_salt('bf')),
            now(),
            null,
            '',
            null,
            '',
            null,
            '',
            '',
            null,
            null,
            jsonb_build_object('provider', 'email', 'providers', array['email']),
            jsonb_build_object('name', safe_display_name, 'email_verified', true),
            false,
            now(),
            now(),
            null,
            null,
            '',
            '',
            null,
            '',
            0,
            null,
            '',
            null,
            false,
            null,
            false
        );

        insert into auth.identities (
            provider_id,
            user_id,
            identity_data,
            provider,
            last_sign_in_at,
            created_at,
            updated_at
        ) values (
            admin_user_id::text,
            admin_user_id,
            jsonb_build_object(
                'sub', admin_user_id::text,
                'email', safe_email,
                'email_verified', true,
                'phone_verified', false
            ),
            'email',
            null,
            now(),
            now()
        ) on conflict (provider_id, provider) do nothing;
    end if;

    update public.community_admins
    set auth_user_id = admin_user_id,
        email = safe_email,
        display_name = coalesce(nullif(trim(display_name), ''), safe_display_name)
    where id = matched_admin.id
      and deleted_at is null
      and auth_user_id is null;

    if not found then
        raise exception 'No se pudo vincular el administrador';
    end if;

    raw_device_token := encode(gen_random_bytes(24), 'hex');

    insert into public.devices (
        account_id,
        platform,
        display_name,
        app_role,
        app_version_code,
        last_seen_at,
        device_token_hash,
        community_admin_id
    ) values (
        matched_code.account_id,
        'android',
        safe_display_name,
        'admin',
        device_app_version_code,
        now(),
        crypt(raw_device_token, gen_salt('bf')),
        matched_code.community_admin_id
    ) returning id into inserted_device_id;

    insert into public.device_activations (
        account_id,
        device_id,
        activated_by_user_id,
        activation_code_id
    ) values (
        matched_code.account_id,
        inserted_device_id,
        admin_user_id,
        matched_code.id
    ) returning id into inserted_activation_id;

    update public.activation_codes
    set used_at = now(),
        consumed_device_id = inserted_device_id
    where id = matched_code.id;

    return query select matched_code.account_id, inserted_device_id, inserted_activation_id, raw_device_token;
end;
$$;

revoke execute on function public.pair_admin_device_with_password(text, text, text, text, integer) from public;
grant execute on function public.pair_admin_device_with_password(text, text, text, text, integer) to anon, authenticated;

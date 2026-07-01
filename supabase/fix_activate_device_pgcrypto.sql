-- Fix activate_device() resolving pgcrypto functions in Supabase.
-- Safe for dev: does not delete tables or data.

create schema if not exists extensions;
create extension if not exists "pgcrypto" with schema extensions;

create or replace function public.activate_device(
    activation_code text,
    device_display_name text,
    device_app_version_code integer,
    device_app_role text default 'user'
)
returns table (
    account_id uuid,
    device_id uuid,
    activation_id uuid
)
language plpgsql
security definer
set search_path = public, extensions
as $$
declare
    matched_code public.activation_codes%rowtype;
    inserted_device_id uuid;
    inserted_activation_id uuid;
begin
    if device_app_role not in ('user', 'admin') then
        raise exception 'Invalid device role';
    end if;

    select *
    into matched_code
    from public.activation_codes
    where used_at is null
      and deleted_at is null
      and expires_at > now()
      and code_hash = crypt(activation_code, code_hash)
      and exists (
          select 1
          from public.accounts
          where accounts.id = activation_codes.account_id
            and accounts.owner_user_id = auth.uid()
      )
    limit 1;

    if matched_code.id is null then
        raise exception 'Invalid activation code';
    end if;

    insert into public.devices (
        account_id,
        platform,
        display_name,
        app_role,
        app_version_code,
        last_seen_at
    )
    values (
        matched_code.account_id,
        'android',
        device_display_name,
        device_app_role,
        device_app_version_code,
        now()
    )
    returning id into inserted_device_id;

    insert into public.device_activations (
        account_id,
        device_id,
        activated_by_user_id,
        activation_code_id
    )
    values (
        matched_code.account_id,
        inserted_device_id,
        auth.uid(),
        matched_code.id
    )
    returning id into inserted_activation_id;

    update public.activation_codes
    set used_at = now(),
        consumed_device_id = inserted_device_id
    where id = matched_code.id;

    return query select matched_code.account_id, inserted_device_id, inserted_activation_id;
end;
$$;

revoke all on function public.activate_device(text, text, integer, text) from public;
grant execute on function public.activate_device(text, text, integer, text) to authenticated;

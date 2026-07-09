create or replace function public.create_device_pairing_code(ttl_minutes integer default 180)
returns table (
    activation_code text,
    expires_at timestamptz
)
language plpgsql
security definer
set search_path = public, extensions
as $$
declare
    owner_account_id uuid;
    creator_admin_id uuid;
    device_token text;
    raw_code text;
    expiration timestamptz;
begin
    if auth.uid() is not null then
        select id
        into owner_account_id
        from public.accounts
        where owner_user_id = auth.uid()
          and deleted_at is null
        order by created_at asc
        limit 1;
    end if;

    device_token := public.request_device_token();
    if device_token is not null then
        select devices.account_id,
               devices.community_admin_id
        into owner_account_id,
             creator_admin_id
        from public.devices
        where devices.deleted_at is null
          and devices.app_role = 'admin'
          and devices.device_token_hash is not null
          and devices.device_token_hash = crypt(device_token, devices.device_token_hash)
        order by devices.created_at asc
        limit 1;
    end if;

    if owner_account_id is null then
        raise exception 'Admin device not found';
    end if;

    raw_code := upper(substr(replace(gen_random_uuid()::text, '-', ''), 1, 6));
    expiration := now() + make_interval(mins => greatest(1, least(coalesce(ttl_minutes, 180), 180)));

    insert into public.activation_codes (
        account_id,
        code_hash,
        intended_app_role,
        community_admin_id,
        expires_at
    )
    values (
        owner_account_id,
        crypt(raw_code, gen_salt('bf')),
        'user',
        creator_admin_id,
        expiration
    );

    return query select raw_code, expiration;
end;
$$;

revoke all on function public.create_device_pairing_code(integer) from public;
grant execute on function public.create_device_pairing_code(integer) to anon, authenticated;

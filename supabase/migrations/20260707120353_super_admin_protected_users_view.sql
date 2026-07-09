create or replace function public.super_admin_list_protected_users(target_community_id uuid)
returns table (
    protected_user_id uuid,
    display_name text,
    status text,
    activation_code_id uuid,
    device_id uuid,
    app_version_code integer,
    token_created_at timestamptz,
    token_expires_at timestamptz,
    activated_at timestamptz,
    last_seen_at timestamptz,
    updated_at timestamptz
)
language plpgsql
security definer
stable
set search_path = public
as $$
begin
    perform public.require_super_admin();

    return query
    select
        devices.id as protected_user_id,
        devices.display_name,
        'activated'::text as status,
        null::uuid as activation_code_id,
        devices.id as device_id,
        devices.app_version_code,
        null::timestamptz as token_created_at,
        null::timestamptz as token_expires_at,
        devices.activated_at,
        devices.last_seen_at,
        devices.updated_at
    from public.devices
    join public.accounts on accounts.id = devices.account_id
    where accounts.community_id = target_community_id
      and accounts.deleted_at is null
      and devices.deleted_at is null
      and devices.app_role = 'user'

    union all

    select
        activation_codes.id as protected_user_id,
        coalesce(nullif(trim(activation_codes.intended_display_name), ''), 'Usuario pendiente') as display_name,
        case
            when activation_codes.expires_at <= now() then 'expired'
            else 'pending'
        end as status,
        activation_codes.id as activation_code_id,
        null::uuid as device_id,
        null::integer as app_version_code,
        activation_codes.created_at as token_created_at,
        activation_codes.expires_at as token_expires_at,
        null::timestamptz as activated_at,
        null::timestamptz as last_seen_at,
        activation_codes.updated_at
    from public.activation_codes
    join public.accounts on accounts.id = activation_codes.account_id
    where accounts.community_id = target_community_id
      and accounts.deleted_at is null
      and activation_codes.deleted_at is null
      and activation_codes.used_at is null
      and activation_codes.consumed_device_id is null
      and coalesce(activation_codes.intended_app_role, 'user') = 'user'

    order by updated_at desc nulls last, token_created_at desc nulls last;
end;
$$;

revoke execute on function public.super_admin_list_protected_users(uuid) from public, anon;
grant execute on function public.super_admin_list_protected_users(uuid) to authenticated;

alter table public.device_relink_sessions
add column if not exists revoked_at timestamptz;

update public.device_relink_sessions
set revoked_at = now()
where completed_at is null
  and revoked_at is null
  and expires_at <= now();

with ranked_pending as (
    select
        id,
        row_number() over (
            partition by device_id
            order by created_at desc, id desc
        ) as pending_rank
    from public.device_relink_sessions
    where completed_at is null
      and revoked_at is null
)
update public.device_relink_sessions relink
set revoked_at = now()
from ranked_pending ranked
where relink.id = ranked.id
  and ranked.pending_rank > 1;

drop index if exists public.device_relink_sessions_pending_device_idx;

create index device_relink_sessions_pending_device_idx
on public.device_relink_sessions(device_id, expires_at desc)
where completed_at is null and revoked_at is null;

create unique index if not exists device_relink_sessions_one_open_per_device_idx
on public.device_relink_sessions(device_id)
where completed_at is null and revoked_at is null;

create or replace function public.license_allows_relink(
    target_account_id uuid,
    target_app_role text
)
returns boolean
language sql
security definer
stable
set search_path = ''
as $$
    select
        coalesce(target_app_role in ('user', 'admin'), false)
        and exists (
            select 1
            from public.accounts account
            join public.community_licenses license
              on license.community_id = account.community_id
             and license.deleted_at is null
            where account.id = target_account_id
              and account.deleted_at is null
              and license.status = 'active'
              and license.starts_at <= now()
              and (license.expires_at is null or license.expires_at > now())
        );
$$;

revoke all on function public.license_allows_relink(uuid, text) from public, anon, authenticated;

create or replace function public.revoke_open_device_relinks(target_device_id uuid)
returns void
language plpgsql
security definer
set search_path = ''
as $$
begin
    with superseded as (
        update public.device_relink_sessions relink
        set revoked_at = now()
        where relink.device_id = target_device_id
          and relink.completed_at is null
          and relink.revoked_at is null
        returning relink.activation_id
    )
    update public.device_activations activation
    set revoked_at = now(), updated_at = now()
    where activation.id in (select activation_id from superseded)
      and activation.revoked_at is null
      and activation.deleted_at is null;
end;
$$;

revoke all on function public.revoke_open_device_relinks(uuid) from public, anon, authenticated;

create or replace function public.device_token_matches(target_account_id uuid)
returns boolean
language sql
security definer
stable
set search_path = ''
as $$
    select exists (
        select 1
        from public.devices device
        where device.account_id = target_account_id
          and device.deleted_at is null
          and (
              (
                  device.device_token_hash is not null
                  and device.device_token_hash = extensions.crypt(public.request_device_token(), device.device_token_hash)
              )
              or exists (
                  select 1
                  from public.device_relink_sessions relink
                  where relink.device_id = device.id
                    and relink.account_id = device.account_id
                    and relink.completed_at is null
                    and relink.revoked_at is null
                    and relink.expires_at > now()
                    and relink.pending_device_token_hash =
                        extensions.crypt(public.request_device_token(), relink.pending_device_token_hash)
              )
          )
    );
$$;

create or replace function public.device_token_matches_device(target_device_id uuid)
returns boolean
language sql
security definer
stable
set search_path = ''
as $$
    select exists (
        select 1
        from public.devices device
        where device.id = target_device_id
          and device.deleted_at is null
          and (
              (
                  device.device_token_hash is not null
                  and device.device_token_hash = extensions.crypt(public.request_device_token(), device.device_token_hash)
              )
              or exists (
                  select 1
                  from public.device_relink_sessions relink
                  where relink.device_id = device.id
                    and relink.account_id = device.account_id
                    and relink.completed_at is null
                    and relink.revoked_at is null
                    and relink.expires_at > now()
                    and relink.pending_device_token_hash =
                        extensions.crypt(public.request_device_token(), relink.pending_device_token_hash)
              )
          )
    );
$$;

revoke all on function public.device_token_matches(uuid) from public, anon, authenticated;
revoke all on function public.device_token_matches_device(uuid) from public, anon, authenticated;
grant execute on function public.device_token_matches(uuid) to anon, authenticated;
grant execute on function public.device_token_matches_device(uuid) to anon, authenticated;

create or replace function public.admin_create_device_relink_code(
    target_device_id uuid,
    ttl_minutes integer default 30
)
returns table (
    activation_code text,
    expires_at timestamptz
)
language plpgsql
security definer
set search_path = ''
as $$
declare
    requesting_admin record;
    target_device record;
    raw_code text;
    expiration timestamptz;
begin
    select device.account_id, device.community_admin_id
    into requesting_admin
    from public.devices device
    where device.app_role = 'admin'
      and device.deleted_at is null
      and public.device_token_matches_device(device.id)
    order by device.created_at asc
    limit 1;

    if requesting_admin.account_id is null then
        raise exception 'Admin device not authorized';
    end if;

    select device.id, device.account_id, device.display_name, device.app_role, device.community_admin_id
    into target_device
    from public.devices device
    where device.id = target_device_id
      and device.account_id = requesting_admin.account_id
      and device.app_role = 'user'
      and device.deleted_at is null
    for update;

    if target_device.id is null then
        raise exception 'Protected user not found';
    end if;

    perform public.revoke_open_device_relinks(target_device.id);

    update public.activation_codes code
    set deleted_at = now(), updated_at = now()
    where code.relink_device_id = target_device.id
      and code.used_at is null
      and code.deleted_at is null;

    raw_code := upper(substr(replace(gen_random_uuid()::text, '-', ''), 1, 8));
    expiration := now() + make_interval(mins => greatest(1, least(coalesce(ttl_minutes, 30), 30)));

    insert into public.activation_codes (
        account_id,
        code_hash,
        intended_app_role,
        intended_display_name,
        community_admin_id,
        relink_device_id,
        expires_at
    ) values (
        target_device.account_id,
        extensions.crypt(raw_code, extensions.gen_salt('bf')),
        target_device.app_role,
        target_device.display_name,
        requesting_admin.community_admin_id,
        target_device.id,
        expiration
    );

    return query select raw_code, expiration;
end;
$$;

create or replace function public.super_admin_create_device_relink_code(
    target_device_id uuid,
    ttl_minutes integer default 30
)
returns table (
    activation_code text,
    expires_at timestamptz
)
language plpgsql
security definer
set search_path = ''
as $$
declare
    target_device record;
    raw_code text;
    expiration timestamptz;
begin
    perform public.require_super_admin();

    select device.id, device.account_id, device.display_name, device.app_role, device.community_admin_id
    into target_device
    from public.devices device
    where device.id = target_device_id
      and device.app_role in ('user', 'admin')
      and device.deleted_at is null
    for update;

    if target_device.id is null then
        raise exception 'Device not found';
    end if;

    perform public.revoke_open_device_relinks(target_device.id);

    update public.activation_codes code
    set deleted_at = now(), updated_at = now()
    where code.relink_device_id = target_device.id
      and code.used_at is null
      and code.deleted_at is null;

    raw_code := upper(substr(replace(gen_random_uuid()::text, '-', ''), 1, 8));
    expiration := now() + make_interval(mins => greatest(1, least(coalesce(ttl_minutes, 30), 30)));

    insert into public.activation_codes (
        account_id,
        code_hash,
        intended_app_role,
        intended_display_name,
        community_admin_id,
        relink_device_id,
        expires_at
    ) values (
        target_device.account_id,
        extensions.crypt(raw_code, extensions.gen_salt('bf')),
        target_device.app_role,
        target_device.display_name,
        target_device.community_admin_id,
        target_device.id,
        expiration
    );

    return query select raw_code, expiration;
end;
$$;

revoke all on function public.admin_create_device_relink_code(uuid, integer) from public, anon, authenticated;
grant execute on function public.admin_create_device_relink_code(uuid, integer) to anon, authenticated;
revoke all on function public.super_admin_create_device_relink_code(uuid, integer) from public, anon, authenticated;
grant execute on function public.super_admin_create_device_relink_code(uuid, integer) to authenticated;

create or replace function public.pair_device_with_code(
    pairing_code text,
    device_display_name text,
    device_app_version_code integer,
    device_app_role text default 'user'
)
returns table (
    account_id uuid,
    device_id uuid,
    activation_id uuid,
    device_token text,
    relink_pending boolean
)
language plpgsql
security definer
set search_path = ''
as $$
declare
    normalized_code text;
    matched_code public.activation_codes%rowtype;
    target_device public.devices%rowtype;
    new_activation_id uuid;
    raw_device_token text;
    relink_expiration timestamptz;
begin
    normalized_code := upper(regexp_replace(coalesce(pairing_code, ''), '[^A-Za-z0-9]', '', 'g'));

    select code.*
    into matched_code
    from public.activation_codes code
    where code.code_hash = extensions.crypt(normalized_code, code.code_hash)
    order by code.created_at desc
    limit 1;

    if matched_code.relink_device_id is null then
        return query
        select paired.account_id, paired.device_id, paired.activation_id, paired.device_token, false
        from public.pair_device_with_code_new_or_restore_internal(
            pairing_code,
            device_display_name,
            device_app_version_code,
            device_app_role
        ) paired;
        return;
    end if;

    select code.*
    into matched_code
    from public.activation_codes code
    where code.id = matched_code.id
    for update;

    if matched_code.deleted_at is not null or matched_code.used_at is not null or matched_code.expires_at <= now() then
        raise exception 'Relink code invalid, expired or already used';
    end if;
    if matched_code.intended_app_role is distinct from device_app_role then
        raise exception 'Relink code role mismatch';
    end if;

    select device.*
    into target_device
    from public.devices device
    where device.id = matched_code.relink_device_id
      and device.account_id = matched_code.account_id
      and device.app_role = device_app_role
      and device.deleted_at is null
    for update;

    if target_device.id is null then
        raise exception 'Relink target not available';
    end if;
    if not public.license_allows_relink(target_device.account_id, target_device.app_role) then
        raise exception 'Community license does not allow this relink';
    end if;

    perform public.revoke_open_device_relinks(target_device.id);

    raw_device_token := encode(extensions.gen_random_bytes(24), 'hex');
    relink_expiration := least(matched_code.expires_at, now() + interval '30 minutes');

    insert into public.device_activations (
        account_id,
        device_id,
        activated_by_user_id,
        activation_code_id
    )
    select target_device.account_id, target_device.id, account.owner_user_id, matched_code.id
    from public.accounts account
    where account.id = target_device.account_id
      and account.deleted_at is null
    returning id into new_activation_id;

    insert into public.device_relink_sessions (
        activation_code_id,
        account_id,
        device_id,
        activation_id,
        pending_device_token_hash,
        expires_at
    ) values (
        matched_code.id,
        target_device.account_id,
        target_device.id,
        new_activation_id,
        extensions.crypt(raw_device_token, extensions.gen_salt('bf')),
        relink_expiration
    );

    update public.activation_codes
    set used_at = now(), consumed_device_id = target_device.id, updated_at = now()
    where id = matched_code.id;

    return query select target_device.account_id, target_device.id, new_activation_id, raw_device_token, true;
end;
$$;

revoke all on function public.pair_device_with_code(text, text, integer, text) from public, anon, authenticated;
grant execute on function public.pair_device_with_code(text, text, integer, text) to anon, authenticated;

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
    device_token text,
    relink_pending boolean
)
language plpgsql
security definer
set search_path = ''
as $$
declare
    normalized_code text;
    matched_code public.activation_codes%rowtype;
    target_device public.devices%rowtype;
    target_admin public.community_admins%rowtype;
    new_activation_id uuid;
    raw_device_token text;
    relink_expiration timestamptz;
begin
    normalized_code := upper(regexp_replace(coalesce(pairing_code, ''), '[^A-Za-z0-9]', '', 'g'));

    select code.*
    into matched_code
    from public.activation_codes code
    where code.code_hash = extensions.crypt(normalized_code, code.code_hash)
    order by code.created_at desc
    limit 1;

    if matched_code.relink_device_id is null then
        return query
        select paired.account_id, paired.device_id, paired.activation_id, paired.device_token, false
        from public.pair_admin_device_with_password_new_internal(
            pairing_code,
            admin_email,
            admin_password,
            device_display_name,
            device_app_version_code
        ) paired;
        return;
    end if;

    if auth.uid() is null then
        raise exception 'Admin authentication required for relink';
    end if;

    select code.*
    into matched_code
    from public.activation_codes code
    where code.id = matched_code.id
    for update;

    if matched_code.deleted_at is not null or matched_code.used_at is not null or matched_code.expires_at <= now() then
        raise exception 'Relink code invalid, expired or already used';
    end if;
    if matched_code.intended_app_role is distinct from 'admin' then
        raise exception 'Relink code role mismatch';
    end if;

    select device.*
    into target_device
    from public.devices device
    where device.id = matched_code.relink_device_id
      and device.account_id = matched_code.account_id
      and device.app_role = 'admin'
      and device.deleted_at is null
    for update;

    select admin.*
    into target_admin
    from public.community_admins admin
    where admin.id = target_device.community_admin_id
      and admin.auth_user_id = auth.uid()
      and lower(admin.email) = lower(trim(admin_email))
      and admin.deleted_at is null;

    if target_device.id is null or target_admin.id is null then
        raise exception 'Admin relink target not authorized';
    end if;
    if not public.license_allows_relink(target_device.account_id, 'admin') then
        raise exception 'Community license does not allow this relink';
    end if;

    perform public.revoke_open_device_relinks(target_device.id);

    raw_device_token := encode(extensions.gen_random_bytes(24), 'hex');
    relink_expiration := least(matched_code.expires_at, now() + interval '30 minutes');

    insert into public.device_activations (
        account_id,
        device_id,
        activated_by_user_id,
        activation_code_id
    ) values (
        target_device.account_id,
        target_device.id,
        auth.uid(),
        matched_code.id
    ) returning id into new_activation_id;

    insert into public.device_relink_sessions (
        activation_code_id,
        account_id,
        device_id,
        activation_id,
        pending_device_token_hash,
        expires_at
    ) values (
        matched_code.id,
        target_device.account_id,
        target_device.id,
        new_activation_id,
        extensions.crypt(raw_device_token, extensions.gen_salt('bf')),
        relink_expiration
    );

    update public.activation_codes
    set used_at = now(), consumed_device_id = target_device.id, updated_at = now()
    where id = matched_code.id;

    return query select target_device.account_id, target_device.id, new_activation_id, raw_device_token, true;
end;
$$;

revoke all on function public.pair_admin_device_with_password(text, text, text, text, integer)
from public, anon, authenticated;
grant execute on function public.pair_admin_device_with_password(text, text, text, text, integer)
to anon, authenticated;

create or replace function public.complete_own_device_relink()
returns boolean
language plpgsql
security definer
set search_path = ''
as $$
declare
    request_token text;
    relink_session public.device_relink_sessions%rowtype;
begin
    request_token := public.request_device_token();
    if request_token is null then
        raise exception 'Device token not authorized';
    end if;

    select relink.*
    into relink_session
    from public.device_relink_sessions relink
    join public.devices device on device.id = relink.device_id and device.account_id = relink.account_id
    where relink.completed_at is null
      and relink.revoked_at is null
      and relink.expires_at > now()
      and device.deleted_at is null
      and relink.pending_device_token_hash =
          extensions.crypt(request_token, relink.pending_device_token_hash)
    order by relink.created_at desc
    limit 1
    for update of relink, device;

    if relink_session.id is null then
        return false;
    end if;

    update public.devices
    set device_token_hash = relink_session.pending_device_token_hash,
        last_seen_at = now(),
        updated_at = now()
    where id = relink_session.device_id
      and account_id = relink_session.account_id
      and deleted_at is null;

    update public.device_activations
    set revoked_at = now(), updated_at = now()
    where device_id = relink_session.device_id
      and account_id = relink_session.account_id
      and id <> relink_session.activation_id
      and revoked_at is null
      and deleted_at is null;

    update public.device_relink_sessions
    set completed_at = now()
    where id = relink_session.id
      and revoked_at is null;

    return true;
end;
$$;

revoke all on function public.complete_own_device_relink() from public, anon, authenticated;
grant execute on function public.complete_own_device_relink() to anon, authenticated;

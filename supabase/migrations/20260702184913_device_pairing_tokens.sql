alter table public.devices
add column if not exists device_token_hash text;

create or replace function public.request_device_token()
returns text
language plpgsql
stable
as $$
declare
    headers json;
begin
    begin
        headers := current_setting('request.headers', true)::json;
    exception when others then
        return null;
    end;
    return nullif(headers ->> 'x-device-token', '');
end;
$$;

create or replace function public.device_token_matches(target_account_id uuid)
returns boolean
language plpgsql
security definer
stable
set search_path = public, extensions
as $$
declare
    token text;
begin
    token := public.request_device_token();
    if token is null then
        return false;
    end if;

    return exists (
        select 1
        from public.devices
        where account_id = target_account_id
          and deleted_at is null
          and device_token_hash is not null
          and device_token_hash = crypt(token, device_token_hash)
    );
end;
$$;

create or replace function public.create_device_pairing_code(ttl_minutes integer default 15)
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
    raw_code text;
    expiration timestamptz;
begin
    if auth.uid() is null then
        raise exception 'Authentication required';
    end if;

    select id
    into owner_account_id
    from public.accounts
    where owner_user_id = auth.uid()
      and deleted_at is null
    order by created_at asc
    limit 1;

    if owner_account_id is null then
        raise exception 'Account not found';
    end if;

    raw_code := upper(substr(replace(gen_random_uuid()::text, '-', ''), 1, 6));
    expiration := now() + make_interval(mins => greatest(1, least(coalesce(ttl_minutes, 15), 60)));

    insert into public.activation_codes (
        account_id,
        code_hash,
        expires_at
    )
    values (
        owner_account_id,
        crypt(raw_code, gen_salt('bf')),
        expiration
    );

    return query select raw_code, expiration;
end;
$$;

revoke all on function public.create_device_pairing_code(integer) from public;
grant execute on function public.create_device_pairing_code(integer) to authenticated;

drop function if exists public.pair_device_with_code(text, text, integer, text);

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
    device_token text
)
language plpgsql
security definer
set search_path = public, extensions
as $$
declare
    matched_code public.activation_codes%rowtype;
    matched_account public.accounts%rowtype;
    inserted_device_id uuid;
    inserted_activation_id uuid;
    safe_display_name text;
    raw_device_token text;
begin
    if device_app_role not in ('user', 'admin') then
        raise exception 'Invalid device role';
    end if;

    safe_display_name := nullif(trim(device_display_name), '');
    if safe_display_name is null then
        raise exception 'Device name required';
    end if;
    raw_device_token := encode(gen_random_bytes(24), 'hex');

    select *
    into matched_code
    from public.activation_codes
    where used_at is null
      and deleted_at is null
      and expires_at > now()
      and code_hash = crypt(upper(trim(pairing_code)), code_hash)
    order by created_at asc
    limit 1;

    if matched_code.id is null then
        raise exception 'Invalid pairing code';
    end if;

    select *
    into matched_account
    from public.accounts
    where id = matched_code.account_id
      and deleted_at is null
    limit 1;

    if matched_account.id is null then
        raise exception 'Account not found';
    end if;

    insert into public.devices (
        account_id,
        platform,
        display_name,
        app_role,
        app_version_code,
        last_seen_at,
        device_token_hash
    )
    values (
        matched_code.account_id,
        'android',
        safe_display_name,
        device_app_role,
        device_app_version_code,
        now(),
        crypt(raw_device_token, gen_salt('bf'))
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
        matched_account.owner_user_id,
        matched_code.id
    )
    returning id into inserted_activation_id;

    update public.activation_codes
    set used_at = now(),
        consumed_device_id = inserted_device_id
    where id = matched_code.id;

    return query select matched_code.account_id, inserted_device_id, inserted_activation_id, raw_device_token;
end;
$$;

revoke all on function public.pair_device_with_code(text, text, integer, text) from public;
grant execute on function public.pair_device_with_code(text, text, integer, text) to anon, authenticated;

create or replace function public.revoke_device(target_device_id uuid)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
    owner_account_id uuid;
begin
    if auth.uid() is null then
        raise exception 'Authentication required';
    end if;

    select account_id
    into owner_account_id
    from public.devices
    join public.accounts on accounts.id = devices.account_id
    where devices.id = target_device_id
      and accounts.owner_user_id = auth.uid()
      and accounts.deleted_at is null
    limit 1;

    if owner_account_id is null then
        raise exception 'Device not found';
    end if;

    update public.device_activations
    set revoked_at = now(),
        deleted_at = now()
    where device_id = target_device_id
      and account_id = owner_account_id
      and deleted_at is null;

    update public.devices
    set deleted_at = now(),
        last_seen_at = now()
    where id = target_device_id
      and account_id = owner_account_id
      and deleted_at is null;
end;
$$;

revoke all on function public.revoke_device(uuid) from public;
grant execute on function public.revoke_device(uuid) to authenticated;

drop policy if exists "devices_device_token_select" on public.devices;
create policy "devices_device_token_select" on public.devices
for select
using (public.device_token_matches(account_id));

drop policy if exists "policies_device_token_select" on public.policies;
create policy "policies_device_token_select" on public.policies
for select
using (public.device_token_matches(account_id));

drop policy if exists "policy_rules_device_token_select" on public.policy_rules;
create policy "policy_rules_device_token_select" on public.policy_rules
for select
using (public.device_token_matches(account_id));

drop policy if exists "daily_limits_device_token_select" on public.daily_limits;
create policy "daily_limits_device_token_select" on public.daily_limits
for select
using (public.device_token_matches(account_id));

drop policy if exists "access_requests_device_token_all" on public.access_requests;
create policy "access_requests_device_token_all" on public.access_requests
for all
using (public.device_token_matches(account_id))
with check (public.device_token_matches(account_id));

drop policy if exists "extra_time_grants_device_token_select" on public.extra_time_grants;
create policy "extra_time_grants_device_token_select" on public.extra_time_grants
for select
using (public.device_token_matches(account_id));

drop policy if exists "device_apps_device_token_all" on public.device_apps;
create policy "device_apps_device_token_all" on public.device_apps
for all
using (public.device_token_matches(account_id))
with check (public.device_token_matches(account_id));

grant execute on function public.request_device_token() to anon, authenticated;
grant execute on function public.device_token_matches(uuid) to anon, authenticated;

grant select on public.devices to anon;
grant select on public.policies to anon;
grant select on public.policy_rules to anon;
grant select on public.daily_limits to anon;
grant select on public.extra_time_grants to anon;
grant select, insert, update on public.access_requests to anon;
grant select, insert, update on public.device_apps to anon;

alter table public.community_licenses
add column if not exists dag_entitled boolean not null default true;

-- Existing communities keep the capability they already had. New licenses start without DAG.
alter table public.community_licenses
alter column dag_entitled set default false;

create table if not exists public.community_license_audit (
    id uuid primary key default gen_random_uuid(),
    community_id uuid not null references public.communities(id),
    actor_user_id uuid references auth.users(id),
    action text not null,
    previous_value jsonb not null default '{}'::jsonb,
    new_value jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default now()
);

alter table public.community_license_audit enable row level security;
revoke all on table public.community_license_audit from public, anon, authenticated;

create index if not exists idx_community_license_audit_community_created
on public.community_license_audit(community_id, created_at desc);

create or replace function public.super_admin_get_dag_entitlement(target_community_id uuid)
returns boolean
language plpgsql
security definer
stable
set search_path = public
as $$
declare
    entitled boolean;
begin
    perform public.require_super_admin();

    select coalesce(licenses.dag_entitled, false)
    into entitled
    from public.communities communities
    left join public.community_licenses licenses
      on licenses.community_id = communities.id
     and licenses.deleted_at is null
    where communities.id = target_community_id
      and communities.deleted_at is null;

    if not found then
        raise exception 'Community not found';
    end if;

    return entitled;
end;
$$;

revoke all on function public.super_admin_get_dag_entitlement(uuid) from public, anon, authenticated;
grant execute on function public.super_admin_get_dag_entitlement(uuid) to authenticated;

create or replace function public.super_admin_set_dag_entitlement(
    target_community_id uuid,
    new_dag_entitled boolean
)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
    previous_dag_entitled boolean;
begin
    perform public.require_super_admin();

    if new_dag_entitled is null then
        raise exception 'DAG entitlement is required';
    end if;

    select dag_entitled
    into previous_dag_entitled
    from public.community_licenses
    where community_id = target_community_id
      and deleted_at is null
    for update;

    if not found then
        raise exception 'Community license not found';
    end if;

    if previous_dag_entitled = new_dag_entitled then
        return;
    end if;

    update public.community_licenses
    set dag_entitled = new_dag_entitled,
        updated_at = now()
    where community_id = target_community_id
      and deleted_at is null;

    insert into public.community_license_audit (
        community_id,
        actor_user_id,
        action,
        previous_value,
        new_value
    ) values (
        target_community_id,
        auth.uid(),
        'dag_entitlement_changed',
        jsonb_build_object('dag_entitled', previous_dag_entitled),
        jsonb_build_object('dag_entitled', new_dag_entitled)
    );
end;
$$;

revoke all on function public.super_admin_set_dag_entitlement(uuid, boolean) from public, anon, authenticated;
grant execute on function public.super_admin_set_dag_entitlement(uuid, boolean) to authenticated;

create or replace function public.get_device_license_entitlement(p_device_id uuid)
returns jsonb
language plpgsql
security definer
stable
set search_path = public
as $$
declare
    entitlement jsonb;
begin
    if not public.device_token_matches_device(p_device_id) then
        raise exception 'Invalid device authorization';
    end if;

    select jsonb_build_object(
        'status', public.effective_community_license_status(
            licenses.status,
            licenses.starts_at,
            licenses.expires_at
        ),
        'starts_at', licenses.starts_at,
        'expires_at', licenses.expires_at,
        'dag_entitled', coalesce(licenses.dag_entitled, false),
        'evaluated_at', now()
    )
    into entitlement
    from public.devices
    join public.accounts
      on accounts.id = devices.account_id
     and accounts.deleted_at is null
    left join public.community_licenses licenses
      on licenses.community_id = accounts.community_id
     and licenses.deleted_at is null
    where devices.id = p_device_id
      and devices.deleted_at is null;

    if entitlement is null then
        raise exception 'Device license not found';
    end if;

    return entitlement;
end;
$$;

revoke all on function public.get_device_license_entitlement(uuid) from public, anon, authenticated;
grant execute on function public.get_device_license_entitlement(uuid) to anon, authenticated;

create or replace function public.dag_search_authorized(p_device_id uuid)
returns boolean
language plpgsql
security definer
stable
set search_path = public, extensions
as $$
begin
    if not public.device_token_matches_device(p_device_id) then
        return false;
    end if;

    return exists (
        select 1
        from public.devices devices
        join public.accounts accounts
          on accounts.id = devices.account_id
         and accounts.deleted_at is null
        join public.community_licenses licenses
          on licenses.community_id = accounts.community_id
         and licenses.deleted_at is null
         and licenses.dag_entitled = true
         and public.effective_community_license_status(
             licenses.status,
             licenses.starts_at,
             licenses.expires_at
         ) = 'active'
        join public.policies policies
          on policies.device_id = devices.id
         and policies.account_id = devices.account_id
         and policies.active = true
         and policies.deleted_at is null
        join public.policy_rules rules
          on rules.policy_id = policies.id
         and rules.account_id = policies.account_id
         and rules.scope = 'Domain'
         and rules.target = '__dag_enabled__'
         and rules.action = 'Allow'
         and rules.enabled = true
         and rules.deleted_at is null
        where devices.id = p_device_id
          and devices.app_role = 'user'
          and devices.deleted_at is null
    );
end;
$$;

revoke all on function public.dag_search_authorized(uuid) from public, anon, authenticated;
-- Kept private: authorize_and_consume_dag_search is the only client quota entry point.

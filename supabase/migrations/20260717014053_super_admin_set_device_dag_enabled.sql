create table if not exists public.device_policy_audit (
    id uuid primary key default gen_random_uuid(),
    community_id uuid not null references public.communities(id),
    device_id uuid not null references public.devices(id),
    actor_user_id uuid references auth.users(id),
    action text not null,
    previous_value jsonb not null default '{}'::jsonb,
    new_value jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default now()
);

alter table public.device_policy_audit enable row level security;
revoke all on table public.device_policy_audit from public, anon, authenticated;

create index if not exists idx_device_policy_audit_device_created
on public.device_policy_audit(device_id, created_at desc);

drop policy if exists device_policy_audit_super_admin_select on public.device_policy_audit;
create policy device_policy_audit_super_admin_select
on public.device_policy_audit
for select
to authenticated
using (public.is_super_admin());

grant select on public.device_policy_audit to authenticated;

create or replace function public.super_admin_list_community_dag_devices(target_community_id uuid)
returns table (device_id uuid, dag_enabled boolean)
language plpgsql
security definer
stable
set search_path = ''
as $$
begin
    perform public.require_super_admin();

    return query
    select
        devices.id,
        exists (
            select 1
            from public.policies
            join public.policy_rules
              on policy_rules.policy_id = policies.id
             and policy_rules.account_id = policies.account_id
             and policy_rules.scope = 'Domain'
             and policy_rules.target = '__dag_enabled__'
             and policy_rules.action = 'Allow'
             and policy_rules.enabled = true
             and policy_rules.deleted_at is null
            where policies.device_id = devices.id
              and policies.account_id = devices.account_id
              and policies.active = true
              and policies.deleted_at is null
        )
    from public.devices
    join public.accounts
      on accounts.id = devices.account_id
     and accounts.deleted_at is null
    where accounts.community_id = target_community_id
      and devices.app_role = 'user'
      and devices.deleted_at is null
    order by devices.display_name asc;
end;
$$;

revoke all on function public.super_admin_list_community_dag_devices(uuid) from public, anon, authenticated;
grant execute on function public.super_admin_list_community_dag_devices(uuid) to authenticated;

create or replace function public.super_admin_set_device_dag_enabled(
    target_community_id uuid,
    target_device_id uuid,
    new_dag_enabled boolean
)
returns boolean
language plpgsql
security definer
set search_path = ''
as $$
declare
    target_account_id uuid;
    target_policy_id uuid;
    target_rule_id uuid;
    previous_dag_enabled boolean;
begin
    perform public.require_super_admin();

    if new_dag_enabled is null then
        raise exception 'DAG state is required';
    end if;

    select devices.account_id
    into target_account_id
    from public.devices
    join public.accounts
      on accounts.id = devices.account_id
     and accounts.deleted_at is null
    where devices.id = target_device_id
      and devices.app_role = 'user'
      and devices.deleted_at is null
      and accounts.community_id = target_community_id
    for update of devices;

    if not found then
        raise exception 'Active user device not found in community';
    end if;

    if new_dag_enabled and not exists (
        select 1
        from public.community_licenses
        where community_id = target_community_id
          and deleted_at is null
          and dag_entitled = true
          and public.effective_community_license_status(status, starts_at, expires_at) = 'active'
    ) then
        raise exception 'DAG is not available for this community';
    end if;

    select exists (
        select 1
        from public.policies
        join public.policy_rules
          on policy_rules.policy_id = policies.id
         and policy_rules.account_id = policies.account_id
         and policy_rules.scope = 'Domain'
         and policy_rules.target = '__dag_enabled__'
         and policy_rules.action = 'Allow'
         and policy_rules.enabled = true
         and policy_rules.deleted_at is null
        where policies.device_id = target_device_id
          and policies.account_id = target_account_id
          and policies.active = true
          and policies.deleted_at is null
    ) into previous_dag_enabled;

    if previous_dag_enabled = new_dag_enabled then
        return previous_dag_enabled;
    end if;

    select id
    into target_policy_id
    from public.policies
    where device_id = target_device_id
      and account_id = target_account_id
      and active = true
      and deleted_at is null
    order by updated_at desc, id
    limit 1
    for update;

    if target_policy_id is null then
        insert into public.policies (account_id, device_id, version, active)
        values (target_account_id, target_device_id, 1, true)
        returning id into target_policy_id;
    end if;

    update public.policy_rules
    set enabled = false,
        updated_at = now()
    where policy_id = target_policy_id
      and account_id = target_account_id
      and scope = 'Domain'
      and target = '__dag_enabled__'
      and action = 'Allow'
      and enabled = true
      and deleted_at is null;

    if new_dag_enabled then
        select id
        into target_rule_id
        from public.policy_rules
        where policy_id = target_policy_id
          and account_id = target_account_id
          and scope = 'Domain'
          and target = '__dag_enabled__'
          and action = 'Allow'
          and deleted_at is null
        order by updated_at desc, id
        limit 1
        for update;

        if target_rule_id is null then
            insert into public.policy_rules (
                account_id, policy_id, scope, target, action, priority, enabled
            ) values (
                target_account_id, target_policy_id, 'Domain', '__dag_enabled__', 'Allow', 130, true
            );
        else
            update public.policy_rules
            set enabled = true,
                priority = 130,
                updated_at = now()
            where id = target_rule_id;
        end if;
    end if;

    update public.policies
    set version = version + 1,
        updated_at = now()
    where id = target_policy_id;

    insert into public.device_policy_audit (
        community_id, device_id, actor_user_id, action, previous_value, new_value
    ) values (
        target_community_id,
        target_device_id,
        auth.uid(),
        'dag_enabled_changed',
        jsonb_build_object('dag_enabled', previous_dag_enabled),
        jsonb_build_object('dag_enabled', new_dag_enabled)
    );

    return new_dag_enabled;
end;
$$;

revoke all on function public.super_admin_set_device_dag_enabled(uuid, uuid, boolean) from public, anon, authenticated;
grant execute on function public.super_admin_set_device_dag_enabled(uuid, uuid, boolean) to authenticated;

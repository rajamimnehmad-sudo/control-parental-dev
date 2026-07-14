alter table public.community_licenses
add column if not exists dag_search_monthly_limit integer not null default 100;

do $$
begin
    if not exists (
        select 1
        from pg_constraint
        where conname = 'community_licenses_dag_search_monthly_limit_check'
          and conrelid = 'public.community_licenses'::regclass
    ) then
        alter table public.community_licenses
        add constraint community_licenses_dag_search_monthly_limit_check
        check (dag_search_monthly_limit between 1 and 100000);
    end if;
end
$$;

alter table public.dag_search_monthly_usage
drop constraint if exists dag_search_monthly_usage_request_count_check;

do $$
begin
    if not exists (
        select 1
        from pg_constraint
        where conname = 'dag_search_monthly_usage_request_count_nonnegative'
          and conrelid = 'public.dag_search_monthly_usage'::regclass
    ) then
        alter table public.dag_search_monthly_usage
        add constraint dag_search_monthly_usage_request_count_nonnegative
        check (request_count >= 0);
    end if;
end
$$;

create index if not exists idx_dag_search_usage_month_device
on public.dag_search_monthly_usage(month_start, device_id)
include (request_count, updated_at);

create index if not exists idx_policy_rules_dag_enabled
on public.policy_rules(policy_id, account_id)
where scope = 'Domain'
  and target = '__dag_enabled__'
  and action = 'Allow'
  and enabled = true
  and deleted_at is null;

create or replace function public.authorize_and_consume_dag_search(p_device_id uuid)
returns text
language plpgsql
security definer
volatile
set search_path = public, extensions
as $$
declare
    current_month date := date_trunc('month', timezone('UTC', now()))::date;
    device_monthly_limit integer;
    consumed boolean := false;
begin
    if not public.dag_search_authorized(p_device_id) then
        return 'unauthorized';
    end if;

    select coalesce(licenses.dag_search_monthly_limit, 100)
    into device_monthly_limit
    from public.devices devices
    join public.accounts accounts
      on accounts.id = devices.account_id
     and accounts.deleted_at is null
    left join public.community_licenses licenses
      on licenses.community_id = accounts.community_id
     and licenses.deleted_at is null
    where devices.id = p_device_id
      and devices.app_role = 'user'
      and devices.deleted_at is null
    limit 1;

    if device_monthly_limit is null or device_monthly_limit < 1 then
        return 'quota';
    end if;

    insert into public.dag_search_monthly_usage (device_id, month_start, request_count, updated_at)
    values (p_device_id, current_month, 1, now())
    on conflict (device_id, month_start) do update
    set request_count = public.dag_search_monthly_usage.request_count + 1,
        updated_at = excluded.updated_at
    where public.dag_search_monthly_usage.request_count < device_monthly_limit
    returning true into consumed;

    return case when coalesce(consumed, false) then 'allowed' else 'quota' end;
end;
$$;

revoke all on function public.authorize_and_consume_dag_search(uuid) from public, anon, authenticated;
grant execute on function public.authorize_and_consume_dag_search(uuid) to anon, authenticated;

create or replace function public.super_admin_get_dag_usage_summary()
returns table (
    community_id uuid,
    community_name text,
    monthly_limit integer,
    active_dag_devices bigint,
    request_count bigint,
    total_capacity bigint,
    remaining_count bigint,
    last_usage_at timestamptz
)
language plpgsql
security definer
stable
set search_path = ''
as $$
begin
    perform public.require_super_admin();

    return query
    with device_usage as (
        select
            accounts.community_id,
            devices.id as device_id,
            exists (
                select 1
                from public.policies policies
                join public.policy_rules rules
                  on rules.policy_id = policies.id
                 and rules.account_id = policies.account_id
                 and rules.scope = 'Domain'
                 and rules.target = '__dag_enabled__'
                 and rules.action = 'Allow'
                 and rules.enabled = true
                 and rules.deleted_at is null
                where policies.device_id = devices.id
                  and policies.account_id = devices.account_id
                  and policies.active = true
                  and policies.deleted_at is null
            ) as dag_enabled,
            coalesce(usage.request_count, 0) as used_count,
            usage.updated_at as usage_updated_at
        from public.devices devices
        join public.accounts accounts
          on accounts.id = devices.account_id
         and accounts.deleted_at is null
        left join public.dag_search_monthly_usage usage
          on usage.device_id = devices.id
         and usage.month_start = date_trunc('month', timezone('UTC', now()))::date
        where devices.app_role = 'user'
          and devices.deleted_at is null
    )
    select
        communities.id,
        communities.name,
        coalesce(licenses.dag_search_monthly_limit, 100),
        count(device_usage.device_id) filter (where device_usage.dag_enabled),
        coalesce(sum(device_usage.used_count), 0)::bigint,
        (
            count(device_usage.device_id) filter (where device_usage.dag_enabled)
            * coalesce(licenses.dag_search_monthly_limit, 100)
        )::bigint,
        coalesce(
            sum(
                case
                    when device_usage.dag_enabled
                        then greatest(coalesce(licenses.dag_search_monthly_limit, 100) - device_usage.used_count, 0)
                    else 0
                end
            ),
            0
        )::bigint,
        max(device_usage.usage_updated_at)
    from public.communities communities
    left join public.community_licenses licenses
      on licenses.community_id = communities.id
     and licenses.deleted_at is null
    left join device_usage
      on device_usage.community_id = communities.id
    where communities.deleted_at is null
    group by communities.id, communities.name, licenses.dag_search_monthly_limit
    order by communities.name asc;
end;
$$;

revoke all on function public.super_admin_get_dag_usage_summary() from public, anon, authenticated;
grant execute on function public.super_admin_get_dag_usage_summary() to authenticated;

create or replace function public.super_admin_list_dag_usage_devices()
returns table (
    community_id uuid,
    device_id uuid,
    display_name text,
    dag_enabled boolean,
    monthly_limit integer,
    request_count integer,
    remaining_count integer,
    last_usage_at timestamptz,
    last_seen_at timestamptz
)
language plpgsql
security definer
stable
set search_path = ''
as $$
begin
    perform public.require_super_admin();

    return query
    with device_usage as (
        select
            accounts.community_id,
            devices.id as device_id,
            devices.display_name,
            devices.last_seen_at,
            coalesce(licenses.dag_search_monthly_limit, 100) as monthly_limit,
            exists (
                select 1
                from public.policies policies
                join public.policy_rules rules
                  on rules.policy_id = policies.id
                 and rules.account_id = policies.account_id
                 and rules.scope = 'Domain'
                 and rules.target = '__dag_enabled__'
                 and rules.action = 'Allow'
                 and rules.enabled = true
                 and rules.deleted_at is null
                where policies.device_id = devices.id
                  and policies.account_id = devices.account_id
                  and policies.active = true
                  and policies.deleted_at is null
            ) as dag_enabled,
            coalesce(usage.request_count, 0) as used_count,
            usage.updated_at as usage_updated_at
        from public.devices devices
        join public.accounts accounts
          on accounts.id = devices.account_id
         and accounts.deleted_at is null
        left join public.community_licenses licenses
          on licenses.community_id = accounts.community_id
         and licenses.deleted_at is null
        left join public.dag_search_monthly_usage usage
          on usage.device_id = devices.id
         and usage.month_start = date_trunc('month', timezone('UTC', now()))::date
        where devices.app_role = 'user'
          and devices.deleted_at is null
    )
    select
        device_usage.community_id,
        device_usage.device_id,
        device_usage.display_name,
        device_usage.dag_enabled,
        device_usage.monthly_limit,
        device_usage.used_count,
        case
            when device_usage.dag_enabled
                then greatest(device_usage.monthly_limit - device_usage.used_count, 0)
            else 0
        end,
        device_usage.usage_updated_at,
        device_usage.last_seen_at
    from device_usage
    where device_usage.dag_enabled or device_usage.used_count > 0
    order by device_usage.community_id, device_usage.used_count desc, device_usage.display_name asc;
end;
$$;

revoke all on function public.super_admin_list_dag_usage_devices() from public, anon, authenticated;
grant execute on function public.super_admin_list_dag_usage_devices() to authenticated;

create or replace function public.super_admin_set_dag_search_monthly_limit(
    target_community_id uuid,
    new_monthly_limit integer
)
returns void
language plpgsql
security definer
set search_path = ''
as $$
begin
    perform public.require_super_admin();

    if new_monthly_limit is null or new_monthly_limit < 1 or new_monthly_limit > 100000 then
        raise exception 'Invalid DAG monthly limit';
    end if;

    update public.community_licenses
    set dag_search_monthly_limit = new_monthly_limit,
        updated_at = now()
    where community_id = target_community_id
      and deleted_at is null;

    if not found then
        raise exception 'Community license not found';
    end if;
end;
$$;

revoke all on function public.super_admin_set_dag_search_monthly_limit(uuid, integer) from public, anon, authenticated;
grant execute on function public.super_admin_set_dag_search_monthly_limit(uuid, integer) to authenticated;
